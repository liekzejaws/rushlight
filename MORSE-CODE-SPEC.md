# Morse Code Module Technical Specification

## 1. Introduction
This document outlines the technical specification for the Morse Code module within the Rushlight app (codenamed LAMPP). This module will enable phone-to-phone communication via visual (flashlight) and auditory (speaker) Morse code, with corresponding reception via camera and microphone. It's a critical feature for "zero infrastructure" communication.

## 2. Architecture Overview
The Morse Code module will be integrated into the existing LAMPP Panel System. It will reside as a new dedicated tab (`LamppTab.MORSE`) alongside existing tabs like `AI_CHAT`, `WIKI`, `P2P`, and `TOOLS`. This provides a clear, distinct user interface for this critical communication feature.

**Integration Points:**
*   **`LamppPanelManager.java`**: Will manage the lifecycle of the new `MorseFragment` and its associated tab.
*   **`LamppSideTabBar.java`**: Will have a new icon for the Morse Code module.
*   **`LamppTab.java`**: A new `MORSE` enum entry will be added.
*   **`MorseFragment.java`**: This will be the main UI fragment, extending `LamppPanelFragment`.

## 3. Core Design Principle: LLM is NOT in the Hot Path
The fundamental design principle for real-time Morse code encoding and decoding is to **avoid LLM involvement in the immediate signal processing path.**
*   **Encoding/Decoding:** Basic Digital Signal Processing (DSP) for audio and image processing for visual, combined with a standard Morse code lookup table (ITU-R M.1677-1 standard) will handle real-time conversion.
*   **LLM Role:** After a message has been decoded (or before sending, for advanced features), the LLM can be optionally engaged for:
    *   Error correction ("Did you mean...?").
    *   Natural language translation.
    *   Contextual interpretation or summarization.

## 4. Send Modes

### 4.1 Flashlight Morse
*   **Mechanism:** Utilizes the device's camera flash LED.
*   **Implementation:** Control `CameraManager` to toggle the `FLASH_MODE_TORCH` for a specific duration.
*   **Timing:** Adheres to ITU Morse timing standards (see Section 6).

### 4.2 Speaker/Audio Morse
*   **Mechanism:** Generates a specific tone frequency (default 800Hz) through the device speaker.
*   **Implementation:** Use `AudioTrack` or `ToneGenerator` to produce sine waves.
*   **Timing:** Adheres to ITU Morse timing standards (see Section 6).

## 5. Receive Modes

### 5.1 Camera Flash Detection
*   **Mechanism:** Analyzes the video stream from the device's camera to detect light intensity changes.
*   **Implementation:**
    *   **Frame Brightness Delta Detection:** Process incoming camera frames (e.g., using `Camera2` API and an `ImageReader` callback). Calculate the average brightness of a central region of interest within each frame.
    *   **Adaptive Thresholding:** Implement an adaptive thresholding algorithm to account for varying ambient light conditions. The threshold for distinguishing "on" from "off" should dynamically adjust based on recent average brightness levels. This prevents false positives/negatives in changing environments.
    *   **Timing:** Records durations of light "on" and "off" states for Morse code unit timing.

### 5.2 Microphone Audio Morse Detection
*   **Mechanism:** Analyzes the audio input from the device's microphone to detect a specific tone frequency.
*   **Implementation:**
    *   **FFT/Goertzel Filter:** Use Fast Fourier Transform (FFT) or, preferably, the Goertzel algorithm for efficient, single-frequency tone detection. The Goertzel algorithm is computationally less intensive for detecting a specific frequency.
    *   **Configurable Frequency:** Allow users to configure the target frequency (default 800Hz), to account for variations or interference.
    *   **Thresholding:** Implement an amplitude threshold to differentiate signal from background noise.
    *   **Timing:** Records durations of tone "present" and "absent" states for Morse code unit timing.

## 6. Signal Processing Approach & Timing

### 6.1 ITU Morse Timing Standard
*   **Dit (dot) duration:** 1 unit
*   **Dah (dash) duration:** 3 units
*   **Inter-element space (between dits/dahs within a character):** 1 unit
*   **Inter-character space (between characters):** 3 units
*   **Inter-word space (between words):** 7 units

### 6.2 Adaptive Speed Detection for Receive
*   The system will dynamically estimate the "unit" duration based on incoming signal patterns (e.g., averaging the shortest detected "on" and "off" durations to establish a baseline dit).
*   This allows the receiver to adapt to varying transmission speeds (Words Per Minute - WPM).
*   A configurable WPM range will be provided for fine-tuning.

## 7. UI Design

The Morse Code module UI (`MorseFragment`) will feature:
*   **Text Input:** A multi-line text area for users to type messages.
*   **Morse Encode & Output:**
    *   A button to trigger Morse code transmission.
    *   **Visual Preview:** As text is typed, or as Morse is transmitted, a visual representation (e.g., dots and dashes, or flashing/tone indicator) will be displayed.
    *   **Output Control:** Buttons/toggles for "Flashlight", "Audio", or "Both".
*   **Real-time Decode Display:**
    *   A dedicated area to display incoming Morse characters as they are decoded in real-time.
    *   A buffer to show the partially formed word or sentence.
*   **Speed (WPM) Control:** A slider or numeric input to set the transmission speed (WPM) for sending and to fine-tune the detection speed for receiving.
*   **Mode Selector:** Radio buttons or toggles for "Send" and "Receive" modes, and sub-selectors for "Flashlight" / "Audio" (for send) and "Camera" / "Mic" (for receive).
*   **History Log:** A scrollable log of sent and received messages, timestamped.
*   **Settings Icon:** Access to Morse-specific settings (e.g., audio frequency, camera sensitivity).

## 8. LLM Integration (Optional, Post-Decode)

*   After a message is successfully decoded by the DSP+lookup table, the user will have an option to pass the decoded text to the local LLM.
*   **Error Correction:** The LLM can suggest corrections for garbled or ambiguous segments ("Did you mean 'SOS' instead of 'SDS'?").
*   **Translation:** Translate the message into another language (if a relevant LLM model is loaded).
*   **Contextual Interpretation:** For short, cryptic messages, the LLM could offer contextual interpretation based on current location, time, or other LAMPP data (e.g., "Given your proximity to the river, 'WATER' might refer to purification needs.").

## 9. Files to Create

Following the `net.osmand.plus.lampp.*` and `net.osmand.plus.plugins.*` conventions:

```
OsmAnd/src/net/osmand/plus/
  morse/
    MorseFragment.java             # Main UI fragment (extends LamppPanelFragment)
    MorseEncoder.java              # Converts text to Morse code signals (dit/dah sequence)
    MorseDecoder.java              # Processes timing events into Morse characters
    MorseSignalProcessor.java      # Abstract base for signal processing (camera/mic)
    CameraMorseProcessor.java      # Implements camera flash detection
    MicMorseProcessor.java         # Implements microphone audio detection
    MorseAudioGenerator.java       # Generates audio tones for transmission
    MorseLightEmitter.java         # Controls camera flash for transmission
    MorseTimingManager.java        # Manages ITU timing units and WPM conversion
    MorseAlphabet.java             # Static lookup table for Morse characters
    MorseSettingsFragment.java     # Settings for Morse module (frequency, sensitivity, WPM)
    MorseHistoryManager.java       # Manages persistent history log
    MorseMessage.java              # Data model for a single Morse message (text, type, timestamp)
    
  lampp/
    LamppTab.java                  # Add MORSE enum entry
    LamppSideTabBar.java           # Update with new Morse icon
    LamppPanelManager.java         # Update to instantiate MorseFragment
```

## 10. Dependencies

*   **Android APIs:**
    *   `android.hardware.camera2` (for `CameraManager` and image processing)
    *   `android.media.AudioRecord` (for microphone input)
    *   `android.media.AudioTrack` or `android.media.ToneGenerator` (for speaker output)
    *   `android.media.MediaRecorder` (potentially for simpler audio capture, though `AudioRecord` offers more control)
    *   `android.Manifest.permission.CAMERA`
    *   `android.Manifest.permission.RECORD_AUDIO`
*   **External Libraries (optional, but could simplify DSP):**
    *   Consider a small, efficient DSP library if Android's native audio processing proves too complex or inefficient for Goertzel/FFT (e.g., Apache Commons Math for signal processing utilities, though this might add bloat. Prioritize native Android APIs first).

## 11. Permission Requirements

The following permissions will need to be declared in `AndroidManifest.xml` and handled at runtime:
*   `<uses-permission android:name="android.permission.CAMERA" />`
*   `<uses-permission android:name="android.permission.FLASHLIGHT" android:required="false" />` (Flashlight permission, though `CAMERA` generally implies flash control)
*   `<uses-permission android:name="android.permission.RECORD_AUDIO" />`
*   `<uses-feature android:name="android.hardware.camera" />`
*   `<uses-feature android:name="android.hardware.camera.flash" android:required="false" />`
*   `<uses-feature android:name="android.hardware.microphone" />`

## 12. Testing Approach

Testing will focus on unit, integration, and simulated environment tests.
*   **Self-Test Mode (Audio Loopback):**
    *   **Mechanism:** Implement a special "loopback" mode where the `MorseAudioGenerator` directly feeds its output into the `MicMorseProcessor` on the *same device*.
    *   **Verification:** This allows for testing the entire audio encode-decode pipeline without a second device.
    *   **Limitations:** This does NOT test the physical acoustics of the speaker/microphone or real-world noise interference.
*   **Flashlight/Camera Simulation:**
    *   Direct flash-to-camera on the same device is generally unreliable due to physical limitations and focus issues.
    *   **Simulated Input:** For camera flash detection, consider injecting synthetic brightness delta sequences into `CameraMorseProcessor` for unit testing logic.
    *   **JUnit/Robolectric Tests:** Extensive unit tests for `MorseEncoder`, `MorseDecoder`, `MorseTimingManager`, and the signal processing logic with mocked inputs.
*   **Manual Testing:** Crucial with two physical devices in various lighting and noise conditions to validate real-world performance.

## 13. Edge Cases & Considerations

*   **Ambient Light Interference (Camera):**
    *   Bright sunlight or fluctuating artificial light could cause false positives or obscure the flash.
    *   Adaptive thresholding is key, but may have limits. Consider user guidance (e.g., "Point camera away from direct light").
*   **Background Noise (Microphone):**
    *   Loud environments will make tone detection difficult.
    *   Goertzel filter's narrow-band detection helps, but a robust noise reduction pre-processing step might be needed (though adds complexity). User guidance on quiet environments.
*   **Battery Drain (Camera/Flash):**
    *   Continuous camera preview and flash usage are significant power consumers.
    *   Optimize camera preview frame rate and resolution.
    *   Alert users about potential battery drain during extended use.
    *   Flashlight mode should be limited to necessary "on" durations.
*   **Device Variations:**
    *   Flash brightness, camera quality, microphone sensitivity, and speaker volume vary significantly across Android devices.
    *   Calibration options in settings (e.g., gain control for mic, brightness threshold for camera) may be necessary.
*   **User Focus:** Ensure the app remains in the foreground during critical transmit/receive operations. Background processing of audio/camera streams for an extended duration might be restricted by Android.
*   **Orientation Changes:** Camera/mic processing needs to be robust to device rotation.
*   **Morse Speed Variability:** The adaptive speed detection must be robust across a wide range of WPM (e.g., 5 WPM to 20 WPM).
*   **Character Set:** Initially support standard alphanumeric and common punctuation. Consider extending to international characters or pro-signs if needed.
