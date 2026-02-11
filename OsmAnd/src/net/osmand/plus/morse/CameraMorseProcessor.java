package net.osmand.plus.morse;

import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Size;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.PlatformUtil;

import org.apache.commons.logging.Log;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * LAMPP Morse Code: Camera frame brightness analysis for flash/light detection.
 *
 * Uses Camera2 API to capture low-resolution preview frames and analyze the
 * average brightness of a central region of interest. Adaptive thresholding
 * accounts for varying ambient light conditions.
 *
 * State transitions (light on/off) are reported to a MorseDecoder.
 *
 * Requires CAMERA permission (runtime check done by MorseFragment).
 *
 * Reference: MORSE-CODE-SPEC.md section 5.1
 */
public class CameraMorseProcessor {

    private static final Log LOG = PlatformUtil.getLog(CameraMorseProcessor.class);

    // Camera config
    private static final int PREVIEW_WIDTH = 320;
    private static final int PREVIEW_HEIGHT = 240;
    private static final int MAX_IMAGES = 2;

    // Adaptive thresholding
    private static final float BASELINE_ALPHA = 0.05f;      // Slow adaptation for ambient light
    private static final int CALIBRATION_FRAMES = 30;         // ~1 second at 30fps
    private static final int MIN_TRANSITION_MS = 20;           // Debounce
    private static final float DEFAULT_BRIGHTNESS_DELTA = 30f; // Default delta for flash detection

    private CameraManager cameraManager;
    private String cameraId;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private ImageReader imageReader;
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;

    @Nullable
    private MorseDecoder decoder;
    private volatile boolean running;
    private boolean available;

    // Light detection state
    private boolean lightDetected;
    private long lastTransitionTime;

    // Adaptive brightness baseline
    private float baselineBrightness;
    private float brightnessThreshold;
    private float sensitivity = 0.5f;
    private int calibrationCount;
    private float calibrationSum;
    private boolean calibrated;

    // Frame dimensions (actual, may differ from requested)
    private int frameWidth = PREVIEW_WIDTH;
    private int frameHeight = PREVIEW_HEIGHT;

    public CameraMorseProcessor() {
    }

    /**
     * Set the decoder that will receive timing events.
     */
    public void setDecoder(@NonNull MorseDecoder decoder) {
        this.decoder = decoder;
    }

    /**
     * Initialize the camera. Finds a rear-facing camera and prepares for preview.
     *
     * @param context Android context
     * @return true if a suitable camera was found
     */
    public boolean init(@NonNull Context context) {
        try {
            cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            if (cameraManager == null) {
                LOG.warn("CameraManager not available");
                return false;
            }

            // Find rear-facing camera
            String[] cameraIds = cameraManager.getCameraIdList();
            for (String id : cameraIds) {
                CameraCharacteristics chars = cameraManager.getCameraCharacteristics(id);
                Integer facing = chars.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    cameraId = id;

                    // Get a supported preview size close to our target
                    android.hardware.camera2.params.StreamConfigurationMap map =
                            chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                    if (map != null) {
                        Size[] sizes = map.getOutputSizes(ImageFormat.YUV_420_888);
                        if (sizes != null && sizes.length > 0) {
                            Size best = chooseBestSize(sizes);
                            frameWidth = best.getWidth();
                            frameHeight = best.getHeight();
                        }
                    }

                    available = true;
                    LOG.info("Camera found: " + id + " (" + frameWidth + "x" + frameHeight + ")");
                    return true;
                }
            }

            LOG.warn("No rear-facing camera found");
            return false;
        } catch (CameraAccessException e) {
            LOG.error("Error accessing camera", e);
            return false;
        }
    }

    /**
     * Start capturing and analyzing camera frames.
     */
    public void start() {
        if (cameraManager == null || cameraId == null || running) return;

        // Reset state
        lightDetected = false;
        calibrated = false;
        calibrationCount = 0;
        calibrationSum = 0;
        baselineBrightness = 0;
        lastTransitionTime = System.currentTimeMillis();
        running = true;

        // Start background thread
        backgroundThread = new HandlerThread("CameraMorseThread");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());

        // Create ImageReader
        imageReader = ImageReader.newInstance(frameWidth, frameHeight,
                ImageFormat.YUV_420_888, MAX_IMAGES);
        imageReader.setOnImageAvailableListener(this::onImageAvailable, backgroundHandler);

        // Open camera
        try {
            cameraManager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice camera) {
                    cameraDevice = camera;
                    createCaptureSession();
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice camera) {
                    LOG.warn("Camera disconnected");
                    camera.close();
                    cameraDevice = null;
                    running = false;
                }

                @Override
                public void onError(@NonNull CameraDevice camera, int error) {
                    LOG.error("Camera error: " + error);
                    camera.close();
                    cameraDevice = null;
                    running = false;
                }
            }, backgroundHandler);

            LOG.info("CameraMorseProcessor starting...");
        } catch (CameraAccessException | SecurityException e) {
            LOG.error("Failed to open camera", e);
            running = false;
        }
    }

    /**
     * Stop capturing and release camera resources.
     */
    public void stop() {
        running = false;

        if (captureSession != null) {
            try {
                captureSession.close();
            } catch (Exception ignored) {
            }
            captureSession = null;
        }

        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }

        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }

        if (backgroundThread != null) {
            backgroundThread.quitSafely();
            try {
                backgroundThread.join(1000);
            } catch (InterruptedException ignored) {
            }
            backgroundThread = null;
            backgroundHandler = null;
        }

        LOG.info("CameraMorseProcessor stopped");
    }

    /**
     * Release all resources.
     */
    public void release() {
        stop();
        cameraManager = null;
        cameraId = null;
        available = false;
    }

    /**
     * Check if a suitable camera is available.
     */
    public boolean isAvailable() {
        return available;
    }

    /**
     * Set detection sensitivity.
     *
     * @param sensitivity 0.0 (needs bright flash) to 1.0 (detects subtle changes)
     */
    public void setSensitivity(float sensitivity) {
        this.sensitivity = Math.max(0.0f, Math.min(1.0f, sensitivity));
        recalculateThreshold();
    }

    // ==================== Camera Pipeline ====================

    private void createCaptureSession() {
        if (cameraDevice == null || imageReader == null) return;

        try {
            cameraDevice.createCaptureSession(
                    Arrays.asList(imageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            captureSession = session;
                            startRepeatingCapture();
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            LOG.error("Capture session configuration failed");
                            running = false;
                        }
                    },
                    backgroundHandler);
        } catch (CameraAccessException e) {
            LOG.error("Failed to create capture session", e);
            running = false;
        }
    }

    private void startRepeatingCapture() {
        if (cameraDevice == null || captureSession == null) return;

        try {
            CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(
                    CameraDevice.TEMPLATE_PREVIEW);
            builder.addTarget(imageReader.getSurface());

            // Disable auto-exposure and auto-focus for consistent brightness readings
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
            // Set a fixed, short exposure to avoid motion blur
            builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, 10000000L); // 10ms
            builder.set(CaptureRequest.SENSOR_SENSITIVITY, 100); // Low ISO

            captureSession.setRepeatingRequest(builder.build(), null, backgroundHandler);
            LOG.info("Camera capture started");
        } catch (CameraAccessException e) {
            LOG.error("Failed to start repeating capture", e);
            running = false;
        }
    }

    private void onImageAvailable(ImageReader reader) {
        if (!running) return;

        Image image = null;
        try {
            image = reader.acquireLatestImage();
            if (image == null) return;

            // Extract Y plane (luminance) from YUV_420_888
            Image.Plane yPlane = image.getPlanes()[0];
            ByteBuffer yBuffer = yPlane.getBuffer();
            int rowStride = yPlane.getRowStride();

            // Calculate average brightness of center ~20% ROI
            float avgBrightness = calculateCenterBrightness(yBuffer, rowStride,
                    image.getWidth(), image.getHeight());

            processFrame(avgBrightness);
        } catch (Exception e) {
            LOG.error("Error processing camera frame", e);
        } finally {
            if (image != null) {
                image.close();
            }
        }
    }

    private float calculateCenterBrightness(ByteBuffer yBuffer, int rowStride,
                                              int width, int height) {
        // Define center ROI (~20% of frame)
        int roiSize = Math.min(width, height) / 5;
        int centerX = width / 2;
        int centerY = height / 2;
        int startX = centerX - roiSize / 2;
        int startY = centerY - roiSize / 2;
        int endX = startX + roiSize;
        int endY = startY + roiSize;

        // Clamp to frame bounds
        startX = Math.max(0, startX);
        startY = Math.max(0, startY);
        endX = Math.min(width, endX);
        endY = Math.min(height, endY);

        long sum = 0;
        int count = 0;

        for (int y = startY; y < endY; y++) {
            int rowOffset = y * rowStride;
            for (int x = startX; x < endX; x++) {
                // Y plane values are unsigned bytes (0-255)
                int pixel = yBuffer.get(rowOffset + x) & 0xFF;
                sum += pixel;
                count++;
            }
        }

        return count > 0 ? (float) sum / count : 0f;
    }

    private void processFrame(float avgBrightness) {
        // Calibration phase
        if (!calibrated) {
            calibrationSum += avgBrightness;
            calibrationCount++;
            if (calibrationCount >= CALIBRATION_FRAMES) {
                baselineBrightness = calibrationSum / calibrationCount;
                recalculateThreshold();
                calibrated = true;
                LOG.info("Camera calibrated: baseline=" + baselineBrightness
                        + ", threshold=" + brightnessThreshold);
            }
            return;
        }

        // Update baseline with slow adaptation
        baselineBrightness = BASELINE_ALPHA * avgBrightness
                + (1.0f - BASELINE_ALPHA) * baselineBrightness;

        // Detect light
        long now = System.currentTimeMillis();
        boolean lightOn = avgBrightness > brightnessThreshold;

        if (lightOn && !lightDetected) {
            // Transition: OFF → ON
            long silenceDuration = now - lastTransitionTime;
            if (silenceDuration < MIN_TRANSITION_MS) return;

            if (decoder != null && lastTransitionTime > 0) {
                decoder.onSilence(silenceDuration);
                decoder.onSignalStart();
            }
            lightDetected = true;
            lastTransitionTime = now;

        } else if (!lightOn && lightDetected) {
            // Transition: ON → OFF
            long lightDuration = now - lastTransitionTime;
            if (lightDuration < MIN_TRANSITION_MS) return;

            if (decoder != null) {
                decoder.onSignalEnd(lightDuration);
            }
            lightDetected = false;
            lastTransitionTime = now;
        }

        // Recalculate threshold as baseline drifts
        recalculateThreshold();
    }

    private void recalculateThreshold() {
        // sensitivity: 0.0 → needs big brightness delta, 1.0 → detects subtle changes
        float delta = DEFAULT_BRIGHTNESS_DELTA + (0.5f - sensitivity) * 40f;
        delta = Math.max(10f, delta); // Never below 10 brightness units
        brightnessThreshold = baselineBrightness + delta;
    }

    /**
     * Choose the smallest preview size that's at least PREVIEW_WIDTH × PREVIEW_HEIGHT.
     * Smaller = less processing overhead.
     */
    private Size chooseBestSize(Size[] sizes) {
        Size best = sizes[0];
        int bestArea = best.getWidth() * best.getHeight();

        for (Size size : sizes) {
            int area = size.getWidth() * size.getHeight();
            if (size.getWidth() >= PREVIEW_WIDTH && size.getHeight() >= PREVIEW_HEIGHT) {
                if (area < bestArea || bestArea < PREVIEW_WIDTH * PREVIEW_HEIGHT) {
                    best = size;
                    bestArea = area;
                }
            }
        }

        // If no size meets minimum, use the smallest available
        if (best.getWidth() < PREVIEW_WIDTH || best.getHeight() < PREVIEW_HEIGHT) {
            for (Size size : sizes) {
                int area = size.getWidth() * size.getHeight();
                if (area < bestArea) {
                    best = size;
                    bestArea = area;
                }
            }
        }

        return best;
    }
}
