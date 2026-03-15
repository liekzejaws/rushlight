/*
 * This file is part of Rushlight, licensed under the GNU Affero General
 * Public License v3.0 or later. See the LICENSE file in the project root.
 */

package net.osmand.plus.morse;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;

import net.osmand.PlatformUtil;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.ai.LlmManager;
import net.osmand.plus.lampp.LamppPanelFragment;
import net.osmand.plus.lampp.LamppThemeUtils;
import net.osmand.plus.settings.backend.OsmandSettings;
import net.osmand.plus.widgets.chips.ChipItem;
import net.osmand.plus.widgets.chips.HorizontalChipsView;

import org.apache.commons.logging.Log;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * LAMPP Morse Code: Main panel UI for sending and receiving Morse code.
 *
 * Extends LamppPanelFragment. Orchestrates:
 * - Send mode: encoding, audio generation, flashlight control (Phase 1)
 * - Receive mode: mic/camera capture, tone/brightness detection, decoding (Phase 2)
 * - Self-test: audio loopback verification
 * - Message history: sent and received messages
 *
 * Core constraint: LLM is NOT in the decode path.
 */
public class MorseFragment extends LamppPanelFragment {

    public static final String TAG = "MorseFragment";
    private static final Log LOG = PlatformUtil.getLog(MorseFragment.class);
    private static final int CAMERA_PERMISSION_REQUEST = 2001;
    private static final int RECORD_AUDIO_PERMISSION_REQUEST = 2002;

    // Panel modes
    public enum PanelMode { SEND, RECEIVE }
    public enum SendMode { FLASH, AUDIO, BOTH }
    public enum ReceiveMode { MICROPHONE, CAMERA }

    // ==================== Send UI ====================
    private MaterialButton panelSendButton, panelReceiveButton;
    private View sendContainer, receiveContainer;

    private MaterialButton modeFlashButton, modeAudioButton, modeBothButton;
    private SeekBar wpmSeekbar;
    private TextView wpmLabel;
    private TextView morsePreview;
    private TextView morseStatus;
    private EditText morseInput;
    private MaterialButton sendButton;

    // ==================== Receive UI ====================
    private MaterialButton receiveMicButton, receiveCameraButton;
    private SeekBar sensitivitySeekbar;
    private TextView sensitivityLabel;
    private TextView decodeOutput, decodeBuffer, receiveStatus;
    private MaterialButton receiveStartButton, selfTestButton;

    // ==================== History UI ====================
    private View historySection;
    private TextView historyHeader, historyLog;
    private ScrollView historyScroll;
    private boolean historyExpanded;

    // ==================== State ====================
    private PanelMode panelMode = PanelMode.SEND;
    private SendMode currentSendMode = SendMode.AUDIO;
    private ReceiveMode currentReceiveMode = ReceiveMode.MICROPHONE;
    private int currentWpm = MorseTimingManager.DEFAULT_WPM;
    private int currentSensitivity = 50;

    // Send state
    private volatile boolean isTransmitting;
    private Thread transmitThread;
    private MorseAudioGenerator audioGenerator;
    private MorseLightEmitter lightEmitter;

    // Receive state
    private volatile boolean isReceiving;
    private MorseDecoder decoder;
    private MicMorseProcessor micProcessor;
    private CameraMorseProcessor cameraProcessor;

    // History
    private MorseHistoryManager historyManager;

    // Self-test
    private volatile boolean isSelfTesting;

    // Settings
    private OsmandSettings settings;

    // ==================== Quick Messages (Phase 3) ====================
    private HorizontalChipsView quickMessageChips;

    // ==================== AI Assist (Phase 3) ====================
    private MaterialButton aiAssistToggle;
    private View aiProcessingIndicator;
    private TextView aiSuggestionText;
    private boolean aiAssistEnabled;
    private MorseLlmHelper llmHelper;
    private LlmManager llmManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // v0.7: Full-width panel — Morse needs space for waveform and controls
    @Override
    protected float getPartialWidthRatio() {
        return 1.0f;
    }

    @Override
    protected int getPanelLayoutId() {
        return R.layout.fragment_morse;
    }

    @NonNull
    @Override
    public String getPanelTag() {
        return TAG;
    }

    @Override
    protected void onPanelViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        OsmandApplication app = (OsmandApplication) requireActivity().getApplication();
        settings = app.getSettings();

        currentWpm = MorseTimingManager.clampWpm(settings.LAMPP_MORSE_WPM.get());
        currentSensitivity = settings.LAMPP_MORSE_RECEIVE_SENSITIVITY.get();

        historyManager = new MorseHistoryManager(requireContext());

        // Phase 3: LLM helper (optional — may not have model loaded)
        llmManager = new LlmManager(app);
        llmHelper = new MorseLlmHelper(llmManager);
        aiAssistEnabled = false;

        initViews(view);
        setupPanelToggle();
        setupSendUI();
        setupReceiveUI();
        setupHistoryUI();
        setupQuickMessages();
        setupAiAssist();

        // Default to Send mode
        switchToSendMode();
        selectSendMode(SendMode.AUDIO);
        selectReceiveMode(ReceiveMode.MICROPHONE);
    }

    // ==================== View Init ====================

    private void initViews(View view) {
        // v0.7: Back button to close full-width panel
        View backButton = view.findViewById(R.id.morse_back_button);
        if (backButton != null) {
            backButton.setOnClickListener(v -> {
                MapActivity ma = getMapActivity();
                if (ma != null) {
                    ma.getLamppPanelManager().closeActivePanel(true);
                }
            });
        }

        // Panel toggle
        panelSendButton = view.findViewById(R.id.panel_send_button);
        panelReceiveButton = view.findViewById(R.id.panel_receive_button);
        sendContainer = view.findViewById(R.id.send_container);
        receiveContainer = view.findViewById(R.id.receive_container);

        // Send views
        modeFlashButton = view.findViewById(R.id.mode_flash_button);
        modeAudioButton = view.findViewById(R.id.mode_audio_button);
        modeBothButton = view.findViewById(R.id.mode_both_button);
        wpmSeekbar = view.findViewById(R.id.wpm_seekbar);
        wpmLabel = view.findViewById(R.id.wpm_label);
        morsePreview = view.findViewById(R.id.morse_preview);
        morseStatus = view.findViewById(R.id.morse_status);
        morseInput = view.findViewById(R.id.morse_input);
        sendButton = view.findViewById(R.id.morse_send_button);

        // Receive views
        receiveMicButton = view.findViewById(R.id.receive_mic_button);
        receiveCameraButton = view.findViewById(R.id.receive_camera_button);
        sensitivitySeekbar = view.findViewById(R.id.sensitivity_seekbar);
        sensitivityLabel = view.findViewById(R.id.sensitivity_label);
        decodeOutput = view.findViewById(R.id.decode_output);
        decodeBuffer = view.findViewById(R.id.decode_buffer);
        receiveStatus = view.findViewById(R.id.receive_status);
        receiveStartButton = view.findViewById(R.id.receive_start_button);
        selfTestButton = view.findViewById(R.id.self_test_button);

        // History views
        historySection = view.findViewById(R.id.history_section);
        historyHeader = view.findViewById(R.id.history_header);
        historyLog = view.findViewById(R.id.history_log);
        historyScroll = view.findViewById(R.id.history_scroll);

        // Quick message chips (Phase 3)
        quickMessageChips = view.findViewById(R.id.quick_message_chips);

        // AI assist views (Phase 3)
        aiAssistToggle = view.findViewById(R.id.ai_assist_toggle);
        aiProcessingIndicator = view.findViewById(R.id.ai_processing_indicator);
        aiSuggestionText = view.findViewById(R.id.ai_suggestion_text);

        // Initialize displays
        wpmSeekbar.setProgress(currentWpm);
        updateWpmLabel();
        morseStatus.setText(R.string.morse_status_idle);
        sensitivitySeekbar.setProgress(currentSensitivity);
        updateSensitivityLabel();
    }

    // ==================== Panel Toggle ====================

    private void setupPanelToggle() {
        panelSendButton.setOnClickListener(v -> switchToSendMode());
        panelReceiveButton.setOnClickListener(v -> switchToReceiveMode());
    }

    private void switchToSendMode() {
        if (isReceiving) stopReceiving();
        panelMode = PanelMode.SEND;
        sendContainer.setVisibility(View.VISIBLE);
        receiveContainer.setVisibility(View.GONE);
        panelSendButton.setAlpha(1.0f);
        panelReceiveButton.setAlpha(0.5f);
    }

    private void switchToReceiveMode() {
        if (isTransmitting) stopTransmission();
        panelMode = PanelMode.RECEIVE;
        sendContainer.setVisibility(View.GONE);
        receiveContainer.setVisibility(View.VISIBLE);
        panelSendButton.setAlpha(0.5f);
        panelReceiveButton.setAlpha(1.0f);
    }

    // ==================== Send UI Setup ====================

    private void setupSendUI() {
        // Mode buttons
        modeFlashButton.setOnClickListener(v -> {
            if (checkCameraPermission()) selectSendMode(SendMode.FLASH);
        });
        modeAudioButton.setOnClickListener(v -> selectSendMode(SendMode.AUDIO));
        modeBothButton.setOnClickListener(v -> {
            if (checkCameraPermission()) selectSendMode(SendMode.BOTH);
        });

        // WPM slider
        wpmSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                currentWpm = MorseTimingManager.clampWpm(progress);
                updateWpmLabel();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (settings != null) settings.LAMPP_MORSE_WPM.set(currentWpm);
            }
        });

        // Send button
        sendButton.setOnClickListener(v -> {
            if (isTransmitting) stopTransmission();
            else startTransmission();
        });
    }

    private void selectSendMode(SendMode mode) {
        currentSendMode = mode;
        modeFlashButton.setAlpha(mode == SendMode.FLASH ? 1.0f : 0.5f);
        modeAudioButton.setAlpha(mode == SendMode.AUDIO ? 1.0f : 0.5f);
        modeBothButton.setAlpha(mode == SendMode.BOTH ? 1.0f : 0.5f);
    }

    private void updateWpmLabel() {
        if (wpmLabel != null) {
            wpmLabel.setText(getString(R.string.morse_wpm_label, currentWpm));
        }
    }

    // ==================== Receive UI Setup ====================

    private void setupReceiveUI() {
        // Receive mode buttons
        receiveMicButton.setOnClickListener(v -> selectReceiveMode(ReceiveMode.MICROPHONE));
        receiveCameraButton.setOnClickListener(v -> selectReceiveMode(ReceiveMode.CAMERA));

        // Sensitivity slider
        sensitivitySeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                currentSensitivity = progress;
                updateSensitivityLabel();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (settings != null) settings.LAMPP_MORSE_RECEIVE_SENSITIVITY.set(currentSensitivity);
                // Update active processor sensitivity
                float sens = currentSensitivity / 100f;
                if (micProcessor != null) micProcessor.setSensitivity(sens);
                if (cameraProcessor != null) cameraProcessor.setSensitivity(sens);
            }
        });

        // Start/stop receive
        receiveStartButton.setOnClickListener(v -> {
            if (isReceiving) stopReceiving();
            else startReceiving();
        });

        // Self-test
        selfTestButton.setOnClickListener(v -> runSelfTest());
    }

    private void selectReceiveMode(ReceiveMode mode) {
        if (isReceiving) stopReceiving();
        currentReceiveMode = mode;
        receiveMicButton.setAlpha(mode == ReceiveMode.MICROPHONE ? 1.0f : 0.5f);
        receiveCameraButton.setAlpha(mode == ReceiveMode.CAMERA ? 1.0f : 0.5f);
    }

    private void updateSensitivityLabel() {
        if (sensitivityLabel != null) {
            sensitivityLabel.setText(getString(R.string.morse_sensitivity_label, currentSensitivity));
        }
    }

    // ==================== History UI Setup ====================

    private void setupHistoryUI() {
        historyExpanded = false;
        historyHeader.setOnClickListener(v -> {
            historyExpanded = !historyExpanded;
            historyScroll.setVisibility(historyExpanded ? View.VISIBLE : View.GONE);
        });

        historyManager.setListener(new MorseHistoryManager.HistoryListener() {
            @Override
            public void onMessageAdded(@NonNull MorseMessage message) {
                updateHistoryDisplay();
            }
            @Override
            public void onHistoryCleared() {
                if (historyLog != null) historyLog.setText(R.string.morse_history_empty);
            }
        });
    }

    private void updateHistoryDisplay() {
        if (historyLog == null) return;
        List<MorseMessage> messages = historyManager.getMessages();
        if (messages.isEmpty()) {
            historyLog.setText(R.string.morse_history_empty);
            return;
        }

        // Color-code sent vs received messages using theme colors (Phase 3)
        int sentColor = 0xFF4CAF50;    // fallback green
        int receivedColor = 0xFF90A4AE; // fallback grey
        if (getActivity() != null && getContext() != null) {
            OsmandApplication osmApp = (OsmandApplication) getActivity().getApplication();
            sentColor = LamppThemeUtils.getPrimaryColor(getContext(), osmApp);
            receivedColor = LamppThemeUtils.getTextSecondaryColor(getContext(), osmApp);
        }

        SpannableStringBuilder ssb = new SpannableStringBuilder();
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

        for (MorseMessage msg : messages) {
            int start = ssb.length();
            String prefix = msg.isSent() ? "TX" : "RX";
            String line = "[" + sdf.format(new Date(msg.getTimestamp())) + "] "
                    + prefix + ": " + msg.getText() + "\n";
            ssb.append(line);
            int color = msg.isSent() ? sentColor : receivedColor;
            ssb.setSpan(new ForegroundColorSpan(color), start, ssb.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        historyLog.setText(ssb);
    }

    // ==================== Quick Messages (Phase 3) ====================

    private void setupQuickMessages() {
        if (quickMessageChips == null) return;

        List<MorseQuickMessages.QuickMessage> messages = MorseQuickMessages.getDefaultMessages();
        List<ChipItem> chips = new ArrayList<>();
        for (MorseQuickMessages.QuickMessage msg : messages) {
            ChipItem chip = new ChipItem(msg.id);
            chip.title = msg.displayText;
            chip.contentDescription = msg.displayText;
            chip.tag = msg;
            chips.add(chip);
        }
        quickMessageChips.setItems(chips);
        quickMessageChips.setOnSelectChipListener(chip -> {
            MorseQuickMessages.QuickMessage msg = (MorseQuickMessages.QuickMessage) chip.tag;
            if (msg != null && morseInput != null) {
                if ("custom".equals(msg.id)) {
                    morseInput.requestFocus();
                    morseInput.setText("");
                } else {
                    boolean appendGps = settings != null && settings.LAMPP_MORSE_GPS_APPEND.get();
                    OsmandApplication osmApp = getActivity() != null
                            ? (OsmandApplication) getActivity().getApplication() : null;
                    String text = MorseQuickMessages.resolveMessageText(msg, appendGps, osmApp);
                    morseInput.setText(text);
                    morseInput.setSelection(text.length());
                }
            }
            return true;
        });
    }

    // ==================== AI Assist (Phase 3) ====================

    private void setupAiAssist() {
        if (aiAssistToggle == null) return;

        aiAssistToggle.setAlpha(0.5f); // Off by default

        aiAssistToggle.setOnClickListener(v -> {
            aiAssistEnabled = !aiAssistEnabled;
            aiAssistToggle.setAlpha(aiAssistEnabled ? 1.0f : 0.5f);

            if (aiAssistEnabled && !llmHelper.isAvailable()) {
                Toast.makeText(getContext(), R.string.morse_ai_unavailable, Toast.LENGTH_SHORT).show();
                aiAssistEnabled = false;
                aiAssistToggle.setAlpha(0.5f);
            }

            if (!aiAssistEnabled) {
                if (aiSuggestionText != null) aiSuggestionText.setVisibility(View.GONE);
                if (aiProcessingIndicator != null) aiProcessingIndicator.setVisibility(View.GONE);
            }
        });
    }

    private void runAiCorrection(@NonNull String decodedText) {
        if (aiProcessingIndicator != null) aiProcessingIndicator.setVisibility(View.VISIBLE);
        if (aiSuggestionText != null) aiSuggestionText.setVisibility(View.GONE);

        llmHelper.correctMessage(decodedText, new LlmManager.LlmCallback() {
            @Override
            public void onPartialResult(String partialText) {
                if (aiSuggestionText != null) {
                    aiSuggestionText.setVisibility(View.VISIBLE);
                    aiSuggestionText.setText(getString(R.string.morse_ai_correction_label)
                            + " " + partialText);
                }
            }

            @Override
            public void onComplete(String fullResponse) {
                if (aiProcessingIndicator != null) aiProcessingIndicator.setVisibility(View.GONE);
                if (aiSuggestionText != null) {
                    aiSuggestionText.setVisibility(View.VISIBLE);
                    aiSuggestionText.setText(getString(R.string.morse_ai_correction_label)
                            + " " + fullResponse.trim());
                }
            }

            @Override
            public void onError(String error) {
                if (aiProcessingIndicator != null) aiProcessingIndicator.setVisibility(View.GONE);
                LOG.warn("AI correction failed: " + error);
                // Silent fail — AI is optional
            }
        });
    }

    // ==================== Send Transmission ====================

    private void startTransmission() {
        String text = morseInput.getText().toString().trim();
        if (text.isEmpty()) {
            Toast.makeText(getContext(), R.string.morse_empty_message, Toast.LENGTH_SHORT).show();
            return;
        }

        List<MorseEncoder.MorseEvent> events = MorseEncoder.encode(text, currentWpm);
        if (events.isEmpty()) {
            Toast.makeText(getContext(), R.string.morse_empty_message, Toast.LENGTH_SHORT).show();
            return;
        }

        boolean needsAudio = (currentSendMode == SendMode.AUDIO || currentSendMode == SendMode.BOTH);
        boolean needsFlash = (currentSendMode == SendMode.FLASH || currentSendMode == SendMode.BOTH);

        if (needsAudio) {
            int freq = settings != null ? settings.LAMPP_MORSE_AUDIO_FREQ.get()
                    : MorseAudioGenerator.DEFAULT_FREQUENCY;
            audioGenerator = new MorseAudioGenerator(freq);
            if (!audioGenerator.init()) {
                audioGenerator = null;
                if (!needsFlash) {
                    Toast.makeText(getContext(), "Audio initialization failed", Toast.LENGTH_SHORT).show();
                    return;
                }
            }
        }

        if (needsFlash) {
            lightEmitter = new MorseLightEmitter();
            if (!lightEmitter.init(requireContext())) {
                lightEmitter = null;
                if (!needsAudio || audioGenerator == null) {
                    Toast.makeText(getContext(), R.string.morse_flash_unavailable, Toast.LENGTH_SHORT).show();
                    return;
                }
            }
        }

        isTransmitting = true;
        sendButton.setText(R.string.morse_stop);
        morseStatus.setText(R.string.morse_status_transmitting);
        morsePreview.setText("");
        morseInput.setEnabled(false);

        if (audioGenerator != null) audioGenerator.start();

        String fullPattern = MorseEncoder.toDisplayString(events);
        LOG.info("Transmitting: \"" + text + "\" -> " + fullPattern
                + " (" + MorseEncoder.totalDurationMs(events) + "ms at " + currentWpm + " WPM)");

        // Add to history
        historyManager.addMessage(MorseMessage.sent(text));

        transmitThread = new Thread(() -> {
            try {
                StringBuilder previewBuilder = new StringBuilder();
                for (int i = 0; i < events.size() && isTransmitting; i++) {
                    MorseEncoder.MorseEvent event = events.get(i);
                    String symbol = event.displaySymbol();
                    if (!symbol.isEmpty()) {
                        previewBuilder.append(symbol);
                        final String previewText = previewBuilder.toString();
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                if (morsePreview != null) morsePreview.setText(previewText);
                            });
                        }
                    }
                    if (event.isSignalOn()) {
                        if (lightEmitter != null) lightEmitter.turnOn();
                        if (audioGenerator != null) {
                            audioGenerator.playTone(event.getDurationMs());
                        } else {
                            Thread.sleep(event.getDurationMs());
                        }
                        if (lightEmitter != null) lightEmitter.turnOff();
                    } else {
                        if (audioGenerator != null) {
                            audioGenerator.playSilence(event.getDurationMs());
                        } else {
                            Thread.sleep(event.getDurationMs());
                        }
                    }
                }
                if (getActivity() != null && isTransmitting) {
                    getActivity().runOnUiThread(() -> onTransmissionComplete(true));
                }
            } catch (InterruptedException e) {
                if (lightEmitter != null) lightEmitter.turnOff();
            } catch (Exception e) {
                LOG.error("Transmission error", e);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> onTransmissionComplete(false));
                }
            } finally {
                releaseEmitters();
            }
        }, "MorseTransmitThread");
        transmitThread.start();
    }

    private void stopTransmission() {
        isTransmitting = false;
        if (transmitThread != null) {
            transmitThread.interrupt();
            transmitThread = null;
        }
        releaseEmitters();
        onTransmissionComplete(false);
    }

    private void onTransmissionComplete(boolean success) {
        isTransmitting = false;
        if (sendButton != null) sendButton.setText(R.string.morse_send);
        if (morseInput != null) morseInput.setEnabled(true);
        if (morseStatus != null) {
            morseStatus.setText(success ? R.string.morse_status_complete : R.string.morse_status_idle);
        }
        if (success && morseStatus != null) {
            morseStatus.postDelayed(() -> {
                if (morseStatus != null && !isTransmitting) {
                    morseStatus.setText(R.string.morse_status_idle);
                }
            }, 3000);
        }
    }

    private void releaseEmitters() {
        if (audioGenerator != null) {
            audioGenerator.release();
            audioGenerator = null;
        }
        if (lightEmitter != null) {
            lightEmitter.release();
            lightEmitter = null;
        }
    }

    // ==================== Receive ====================

    private void startReceiving() {
        // Check permissions
        if (currentReceiveMode == ReceiveMode.MICROPHONE) {
            if (!checkMicPermission()) return;
        } else {
            if (!checkCameraPermission()) return;
        }

        // Create decoder
        decoder = new MorseDecoder(new MorseDecoder.MorseDecoderListener() {
            @Override
            public void onCharDecoded(char c) {
                mainHandler.post(() -> {
                    if (decodeOutput != null) decodeOutput.append(String.valueOf(c));
                });
            }
            @Override
            public void onWordSpace() {
                mainHandler.post(() -> {
                    if (decodeOutput != null) decodeOutput.append(" ");
                });
            }
            @Override
            public void onBufferUpdate(@NonNull String dotsDashes) {
                mainHandler.post(() -> {
                    if (decodeBuffer != null) decodeBuffer.setText(dotsDashes);
                });
            }
            @Override
            public void onWpmEstimate(int estimatedWpm) {
                mainHandler.post(() -> {
                    if (receiveStatus != null && isReceiving) {
                        receiveStatus.setText("~" + estimatedWpm + " WPM detected");
                    }
                });
            }
        });

        float sens = currentSensitivity / 100f;

        if (currentReceiveMode == ReceiveMode.MICROPHONE) {
            int freq = settings != null ? settings.LAMPP_MORSE_AUDIO_FREQ.get()
                    : MorseAudioGenerator.DEFAULT_FREQUENCY;
            micProcessor = new MicMorseProcessor(freq, 44100);
            micProcessor.setDecoder(decoder);
            micProcessor.setSensitivity(sens);
            if (!micProcessor.init()) {
                Toast.makeText(getContext(), "Microphone initialization failed", Toast.LENGTH_SHORT).show();
                return;
            }
            micProcessor.start();
        } else {
            cameraProcessor = new CameraMorseProcessor();
            cameraProcessor.setDecoder(decoder);
            cameraProcessor.setSensitivity(sens);
            if (!cameraProcessor.init(requireContext())) {
                Toast.makeText(getContext(), "Camera initialization failed", Toast.LENGTH_SHORT).show();
                return;
            }
            cameraProcessor.start();
        }

        isReceiving = true;
        if (decodeOutput != null) decodeOutput.setText("");
        if (decodeBuffer != null) decodeBuffer.setText("");
        receiveStartButton.setText(R.string.morse_receive_stop);
        receiveStatus.setText(R.string.morse_receive_listening);
        LOG.info("Receiving started (" + currentReceiveMode + ")");
    }

    private void stopReceiving() {
        isReceiving = false;

        if (micProcessor != null) {
            micProcessor.release();
            micProcessor = null;
        }
        if (cameraProcessor != null) {
            cameraProcessor.release();
            cameraProcessor = null;
        }

        // Flush decoder to decode any remaining buffer
        if (decoder != null) {
            decoder.flush();

            // Save decoded message to history
            String decoded = decoder.getDecodedText().trim();
            if (!decoded.isEmpty()) {
                historyManager.addMessage(MorseMessage.received(decoded));

                // Phase 3: Optional AI post-processing
                if (aiAssistEnabled && llmHelper != null && llmHelper.isAvailable()) {
                    runAiCorrection(decoded);
                }
            }
            decoder = null;
        }

        if (receiveStartButton != null) receiveStartButton.setText(R.string.morse_receive_start);
        if (receiveStatus != null) receiveStatus.setText(R.string.morse_receive_idle);
        LOG.info("Receiving stopped");
    }

    // ==================== Self-Test ====================

    private void runSelfTest() {
        if (isSelfTesting || isReceiving || isTransmitting) return;

        // Check mic permission
        if (!checkMicPermission()) return;

        isSelfTesting = true;
        selfTestButton.setEnabled(false);
        receiveStatus.setText(R.string.morse_self_test_running);
        if (decodeOutput != null) decodeOutput.setText("");
        if (decodeBuffer != null) decodeBuffer.setText("");

        final String testMessage = "SOS";
        final int testWpm = 13;

        // Create decoder for self-test
        decoder = new MorseDecoder(new MorseDecoder.MorseDecoderListener() {
            @Override
            public void onCharDecoded(char c) {
                mainHandler.post(() -> {
                    if (decodeOutput != null) decodeOutput.append(String.valueOf(c));
                });
            }
            @Override
            public void onWordSpace() {
                mainHandler.post(() -> {
                    if (decodeOutput != null) decodeOutput.append(" ");
                });
            }
            @Override
            public void onBufferUpdate(@NonNull String dotsDashes) {
                mainHandler.post(() -> {
                    if (decodeBuffer != null) decodeBuffer.setText(dotsDashes);
                });
            }
            @Override
            public void onWpmEstimate(int estimatedWpm) {
                // Ignore during self-test
            }
        });

        // Initialize mic processor
        int freq = settings != null ? settings.LAMPP_MORSE_AUDIO_FREQ.get()
                : MorseAudioGenerator.DEFAULT_FREQUENCY;
        micProcessor = new MicMorseProcessor(freq, 44100);
        micProcessor.setDecoder(decoder);
        micProcessor.setSensitivity(currentSensitivity / 100f);

        if (!micProcessor.init()) {
            Toast.makeText(getContext(), "Mic init failed for self-test", Toast.LENGTH_SHORT).show();
            endSelfTest(testMessage);
            return;
        }

        // Initialize audio generator
        audioGenerator = new MorseAudioGenerator(freq);
        if (!audioGenerator.init()) {
            Toast.makeText(getContext(), "Audio init failed for self-test", Toast.LENGTH_SHORT).show();
            micProcessor.release();
            micProcessor = null;
            endSelfTest(testMessage);
            return;
        }

        // Start recording first
        micProcessor.start();

        // Encode and play after a short delay to let mic settle
        List<MorseEncoder.MorseEvent> events = MorseEncoder.encode(testMessage, testWpm);

        new Thread(() -> {
            try {
                // Wait for mic calibration (~600ms)
                Thread.sleep(600);

                // Play the tone sequence
                audioGenerator.start();
                for (MorseEncoder.MorseEvent event : events) {
                    if (!isSelfTesting) break;
                    if (event.isSignalOn()) {
                        audioGenerator.playTone(event.getDurationMs());
                    } else {
                        audioGenerator.playSilence(event.getDurationMs());
                    }
                }

                // Wait for final decoding
                Thread.sleep(1000);
            } catch (InterruptedException ignored) {
            } finally {
                audioGenerator.release();
                audioGenerator = null;

                if (micProcessor != null) {
                    micProcessor.release();
                    micProcessor = null;
                }

                mainHandler.post(() -> endSelfTest(testMessage));
            }
        }, "MorseSelfTestThread").start();
    }

    private void endSelfTest(String expectedMessage) {
        isSelfTesting = false;

        String decoded = "";
        if (decoder != null) {
            decoder.flush();
            decoded = decoder.getDecodedText().trim();
            decoder = null;
        }

        if (selfTestButton != null) selfTestButton.setEnabled(true);

        String result = getString(R.string.morse_self_test_result, expectedMessage, decoded);
        if (receiveStatus != null) receiveStatus.setText(result);
        LOG.info("Self-test result: expected=\"" + expectedMessage + "\", got=\"" + decoded + "\"");

        // Add to history
        historyManager.addMessage(MorseMessage.received("[TEST] " + decoded));
    }

    // ==================== Permissions ====================

    private boolean checkCameraPermission() {
        if (getContext() == null) return false;
        if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            return true;
        }
        requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST);
        return false;
    }

    private boolean checkMicPermission() {
        if (getContext() == null) return false;
        if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            return true;
        }
        requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, RECORD_AUDIO_PERMISSION_REQUEST);
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;

        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (granted) {
                if (panelMode == PanelMode.SEND) {
                    selectSendMode(currentSendMode == SendMode.BOTH ? SendMode.BOTH : SendMode.FLASH);
                } else {
                    selectReceiveMode(ReceiveMode.CAMERA);
                }
            } else {
                Toast.makeText(getContext(), R.string.morse_camera_permission_denied,
                        Toast.LENGTH_SHORT).show();
                if (panelMode == PanelMode.SEND) {
                    selectSendMode(SendMode.AUDIO);
                } else {
                    selectReceiveMode(ReceiveMode.MICROPHONE);
                }
            }
        } else if (requestCode == RECORD_AUDIO_PERMISSION_REQUEST) {
            if (granted) {
                selectReceiveMode(ReceiveMode.MICROPHONE);
            } else {
                Toast.makeText(getContext(), R.string.morse_audio_permission,
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    // ==================== Lifecycle ====================

    @Override
    public void onDestroyView() {
        // Stop active operations
        if (isTransmitting) {
            isTransmitting = false;
            if (transmitThread != null) {
                transmitThread.interrupt();
                transmitThread = null;
            }
        }
        if (isReceiving) {
            isReceiving = false;
        }
        isSelfTesting = false;

        releaseEmitters();

        if (micProcessor != null) {
            micProcessor.release();
            micProcessor = null;
        }
        if (cameraProcessor != null) {
            cameraProcessor.release();
            cameraProcessor = null;
        }
        decoder = null;

        if (historyManager != null) {
            historyManager.setListener(null);
        }

        // Null out view references
        panelSendButton = null;
        panelReceiveButton = null;
        sendContainer = null;
        receiveContainer = null;
        modeFlashButton = null;
        modeAudioButton = null;
        modeBothButton = null;
        wpmSeekbar = null;
        wpmLabel = null;
        morsePreview = null;
        morseStatus = null;
        morseInput = null;
        sendButton = null;
        receiveMicButton = null;
        receiveCameraButton = null;
        sensitivitySeekbar = null;
        sensitivityLabel = null;
        decodeOutput = null;
        decodeBuffer = null;
        receiveStatus = null;
        receiveStartButton = null;
        selfTestButton = null;
        historySection = null;
        historyHeader = null;
        historyLog = null;
        historyScroll = null;

        // Phase 3 cleanup
        quickMessageChips = null;
        aiAssistToggle = null;
        aiProcessingIndicator = null;
        aiSuggestionText = null;
        if (llmManager != null) {
            llmManager.close();
            llmManager = null;
        }
        llmHelper = null;

        super.onDestroyView();
    }
}
