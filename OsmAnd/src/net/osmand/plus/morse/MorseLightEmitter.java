/*
 * This file is part of Rushlight, licensed under the GNU Affero General
 * Public License v3.0 or later. See the LICENSE file in the project root.
 */

package net.osmand.plus.morse;

import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;

import net.osmand.PlatformUtil;

import org.apache.commons.logging.Log;

/**
 * LAMPP Morse Code: Controls the device camera flash (torch) for Morse transmission.
 *
 * Uses CameraManager.setTorchMode() for on/off control.
 * CAMERA permission is already in the manifest. Runtime permission check
 * is handled by MorseFragment before enabling flash mode.
 */
public class MorseLightEmitter {

    private static final Log LOG = PlatformUtil.getLog(MorseLightEmitter.class);

    private CameraManager cameraManager;
    private String cameraId;
    private boolean flashAvailable;
    private boolean torchOn;

    /**
     * Initialize the light emitter by finding a camera with flash capability.
     *
     * @param context Android context
     * @return true if a flash-capable camera was found
     */
    public boolean init(Context context) {
        try {
            cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            if (cameraManager == null) {
                LOG.warn("CameraManager not available");
                return false;
            }

            // Find the first rear-facing camera with flash
            String[] cameraIds = cameraManager.getCameraIdList();
            for (String id : cameraIds) {
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(id);

                // Check if this camera faces the back
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing == null || facing != CameraCharacteristics.LENS_FACING_BACK) {
                    continue;
                }

                // Check if flash is available
                Boolean hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (hasFlash != null && hasFlash) {
                    cameraId = id;
                    flashAvailable = true;
                    LOG.info("Flash available on camera: " + id);
                    return true;
                }
            }

            LOG.warn("No rear camera with flash found");
            return false;
        } catch (CameraAccessException e) {
            LOG.error("Error accessing camera for flash", e);
            return false;
        }
    }

    /**
     * Check if the flash hardware is available.
     */
    public boolean isFlashAvailable() {
        return flashAvailable;
    }

    /**
     * Turn the torch/flash ON.
     */
    public void turnOn() {
        if (!flashAvailable || cameraManager == null || cameraId == null) return;

        try {
            cameraManager.setTorchMode(cameraId, true);
            torchOn = true;
        } catch (CameraAccessException e) {
            LOG.error("Failed to turn on torch", e);
        }
    }

    /**
     * Turn the torch/flash OFF.
     */
    public void turnOff() {
        if (!flashAvailable || cameraManager == null || cameraId == null) return;

        try {
            cameraManager.setTorchMode(cameraId, false);
            torchOn = false;
        } catch (CameraAccessException e) {
            LOG.error("Failed to turn off torch", e);
        }
    }

    /**
     * Release resources and ensure torch is off.
     */
    public void release() {
        if (torchOn) {
            turnOff();
        }
        cameraManager = null;
        cameraId = null;
        flashAvailable = false;
    }

    /**
     * Check if the torch is currently on.
     */
    public boolean isTorchOn() {
        return torchOn;
    }
}
