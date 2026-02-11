# Claude Code Prompt: Morse Code Module Implementation

**Project Context:**
You are working on LAMPP (io.lampp.app), an offline-first Android survival app forked from OsmAnd. The app integrates offline maps, a local LLM (llama.cpp), offline Wikipedia (ZIM/libkiwix), and peer-to-peer content sharing (Bluetooth/WiFi Direct). The UI is built around a custom "LAMPP Panel System" which overlays the OsmAnd map. Key packages include `net.osmand.plus.lampp.*` (panel system), `net.osmand.plus.ai.*` (LLM/RAG), `net.osmand.plus.wikipedia.Zim*.java` (ZIM browser), and `net.osmand.plus.plugins.p2pshare.*` (P2P sharing).

**Goal:**
Implement a new "Morse Code Module" that enables phone-to-phone communication via flashlight/camera and speaker/microphone.

**Core Constraint:**
**LLM is NOT in the real-time decode path.** Use basic Digital Signal Processing (DSP) and lookup tables for real-time Morse code encoding/decoding. The LLM will be used **optionally, after decode**, for error correction, translation, or contextual interpretation.

**Phased Approach:**

**Phase 1: Send Only (MVP)**
Implement the core Morse code encoding logic and transmission capabilities (flashlight and audio).

**Phase 2: Receive (Core Functionality)**
Implement the core Morse code decoding logic and reception capabilities (camera and microphone).

**Phase 3: LLM Integration (Enhancement)**
Integrate the optional LLM features for post-decode processing.

---

## Detailed Implementation Plan for Claude Code

### **Phase 1: Send Only**

**1. UI Integration (LAMPP Panel System):**
   *   **Create `net.osmand.plus.morse.MorseFragment.java`:**
        *   This will be the main UI for the Morse module.
        *   It *must* extend `net.osmand.plus.lampp.LamppPanelFragment`.
        *   Include a multi-line `EditText` for user input, and buttons/toggles for "Flashlight" and "Audio" send modes.
        *   Add a visual preview area (e.g., `TextView` showing `.` and `-` or a simple `View` that changes color) for the Morse sequence as it's generated/transmitted.
        *   Implement a speed (WPM) slider/control.
   *   **Update `net.osmand.plus.lampp.LamppTab.java`:**
        *   Add a new enum entry: `MORSE`.
        *   Assign a suitable icon from `res/drawable` (find an existing flashlight or communication icon, or create a placeholder).
   *   **Update `net.osmand.plus.lampp.LamppSideTabBar.java`:**
        *   Modify `setupTabs()` to include the new `MORSE` tab.
   *   **Update `net.osmand.plus.lampp.LamppPanelManager.java`:**
        *   Add logic to instantiate and manage `MorseFragment` when the `MORSE` tab is selected. Follow the pattern used for `LlmChatFragment` and `P2pShareFragment`.
   *   **Create `res/layout/fragment_morse.xml`:**
        *   Define the layout for `MorseFragment`, including the `EditText`, send controls, visual preview, and WPM slider.
   *   **Add strings to `res/values/strings.xml`:**
        *   `morse_tab_title`, `morse_hint_text_input`, `morse_send_flashlight`, `morse_send_audio`, `morse_wpm_label`, etc.

**2. Core Encoding Logic:**
   *   **Create `net.osmand.plus.morse.MorseAlphabet.java`:**
        *   A `public static final Map<Character, String>` or similar lookup table containing standard ITU Morse code mappings (e.g., `{'A', ".-"}`, `{'B', "-..."}`).
        *   Handle alphanumeric characters and common punctuation.
   *   **Create `net.osmand.plus.morse.MorseEncoder.java`:**
        *   Takes a `String` message as input.
        *   Uses `MorseAlphabet` to convert the string into a sequence of Morse elements (dit, dah, inter-element space, inter-character space, inter-word space).
        *   Returns a list of `MorseEvent` objects (e.g., `new MorseEvent(Type.DIT, Duration.SHORT)`, `new MorseEvent(Type.SPACE_CHAR, Duration.MEDIUM)`).
   *   **Create `net.osmand.plus.morse.MorseTimingManager.java`:**
        *   Calculates absolute durations for dit (1 unit), dah (3 units), inter-element (1 unit), inter-character (3 units), inter-word (7 units) based on a given Words Per Minute (WPM) setting.
        *   Provides methods like `getDitDurationMs(wpm)`, `getDahDurationMs(wpm)`, etc.

**3. Transmission Mechanisms:**
   *   **Create `net.osmand.plus.morse.MorseLightEmitter.java`:**
        *   Manages camera flash control using `android.hardware.camera2.CameraManager`.
        *   Methods: `turnFlashOn(durationMs)`, `turnFlashOff(durationMs)`.
        *   Requires `CAMERA` and `FLASHLIGHT` permissions.
   *   **Create `net.osmand.plus.morse.MorseAudioGenerator.java`:**
        *   Generates a continuous tone (default 800Hz, configurable) using `android.media.AudioTrack` or `ToneGenerator`.
        *   Methods: `startTone(frequency)`, `stopTone()`.
        *   Plays the tone for `dah` and `dit` durations, and silences for space durations.
   *   **Integrate Emitters into `MorseFragment`:**
        *   When the "Send" button is pressed, the `MorseEncoder` converts the text.
        *   A `Handler` or `CoroutineScope` will iterate through the `MorseEvent` sequence, calling `MorseLightEmitter` and/or `MorseAudioGenerator` with the calculated durations from `MorseTimingManager`.

**4. Permissions:**
   *   **Update `AndroidManifest.xml`:**
        *   Add `<uses-permission android:name="android.permission.CAMERA" />`
        *   Add `<uses-permission android:name="android.permission.FLASHLIGHT" android:required="false" />`
        *   Add `<uses-permission android:name="android.permission.RECORD_AUDIO" />` (Needed for Phase 2, but can add now)
        *   Add `<uses-feature android:name="android.hardware.camera" />`
        *   Add `<uses-feature android:name="android.hardware.camera.flash" android:required="false" />`
        *   Add `<uses-feature android:name="android.hardware.microphone" />` (Needed for Phase 2)
   *   **Runtime Permission Handling:**
        *   In `MorseFragment`, implement `ActivityCompat.requestPermissions()` for `CAMERA` and `RECORD_AUDIO` when the user attempts to use a send/receive mode requiring them. Follow the pattern used in `P2pShareFragment`.

### **Phase 2: Receive**

**1. UI Enhancements for Receive Mode:**
   *   **Update `MorseFragment` (`fragment_morse.xml`):**
        *   Add a "Receive" mode toggle/button.
        *   Add sub-selectors for "Camera" and "Microphone" receive modes.
        *   Add a `TextView` or similar UI element to display real-time decoded characters.
        *   Add a scrollable `TextView` or `RecyclerView` for a "History Log" of received messages.
   *   **Create `net.osmand.plus.morse.MorseHistoryManager.java`:**
        *   Manages a list of `MorseMessage` objects (see below) in memory.
        *   Provides methods to add new messages, retrieve history, and potentially persist (e.g., to `SharedPreferences` for simplicity or a local SQLite DB for more robustness, similar to how OsmAnd handles favorites).
   *   **Create `net.osmand.plus.morse.MorseMessage.java`:**
        *   A simple data class: `String text`, `long timestamp`, `MessageType type` (SENT, RECEIVED).

**2. Core Decoding Logic:**
   *   **Create `net.osmand.plus.morse.MorseDecoder.java`:**
        *   Receives timing events (duration of ON/OFF for light/sound).
        *   Uses `MorseTimingManager` to determine if a duration corresponds to a dit, dah, or various spaces.
        *   Accumulates Morse elements (dots and dashes) into a buffer.
        *   Uses `MorseAlphabet` (in reverse lookup if possible, or iterating) to convert completed Morse sequences into characters.
        *   Outputs decoded characters in real-time via a callback interface.
        *   Implements adaptive speed detection (e.g., average shortest detected "on" or "off" duration to establish a unit length).

**3. Reception Mechanisms:**
   *   **Create `net.osmand.plus.morse.MorseSignalProcessor.java` (abstract class/interface):**
        *   Defines common methods for starting/stopping signal processing and a callback for detected signal ON/OFF events with durations.
   *   **Create `net.osmand.plus.morse.CameraMorseProcessor.java`:**
        *   Extends `MorseSignalProcessor`.
        *   Uses `android.hardware.camera2.CameraManager` to get a preview stream.
        *   Processes `ImageReader` callbacks for each frame.
        *   **Brightness Delta Detection:** Calculates average brightness in a central region of interest.
        *   **Adaptive Thresholding:** Dynamically adjusts the "light on" detection threshold based on recent average brightness to handle ambient light changes.
        *   Reports `light_on_event(durationMs)` and `light_off_event(durationMs)` to `MorseDecoder`.
        *   **Edge Case:** Handle quick changes that might not register as full frames.
   *   **Create `net.osmand.plus.morse.MicMorseProcessor.java`:**
        *   Extends `MorseSignalProcessor`.
        *   Uses `android.media.AudioRecord` to capture audio from the microphone.
        *   **FFT/Goertzel Filter:** Implement the Goertzel algorithm (preferred for efficiency in detecting a single frequency) to detect the presence of the default (800Hz) or user-configured audio frequency.
        *   **Amplitude Thresholding:** Filter out low-amplitude background noise.
        *   Reports `tone_on_event(durationMs)` and `tone_off_event(durationMs)` to `MorseDecoder`.
        *   **Edge Case:** Background noise, multiple tones.

**4. Testing Approach:**
   *   **Self-Test Mode (Audio Loopback):**
        *   Implement a setting in `MorseSettingsFragment` to enable an "Audio Loopback Test".
        *   When enabled, `MorseAudioGenerator` output is internally routed to `MicMorseProcessor` on the same device. This allows full encode-decode testing for audio without external hardware.
        *   This can be triggered from the `MorseFragment` UI.

### **Phase 3: LLM Integration**

**1. LLM Integration Points:**
   *   **Update `MorseFragment`:**
        *   Add an "Ask LLM" button or a context menu option on received messages in the history log.
        *   Add a settings toggle (e.g., in `MorseSettingsFragment`) for "Auto-correct with LLM" or "Translate with LLM".
   *   **Integrate `LlmManager`:**
        *   When "Ask LLM" is triggered, construct a prompt for `LlmManager` (from `net.osmand.plus.ai.LlmManager`) using the decoded Morse text.
        *   Example prompts:
            *   "The user received this Morse code message: '{decoded_text}'. Please check for potential errors and suggest corrections."
            *   "The user received this Morse code message: '{decoded_text}'. Translate it into English and provide any contextual interpretation."
        *   Display the LLM's response in a `AlertDialog` or a dedicated `BottomSheetDialog`.
   *   **Update `net.osmand.plus.morse.MorseMessage.java` (optional):**
        *   Could add fields to store LLM suggestions or translations if persistence is desired.

**2. Settings:**
   *   **Create `net.osmand.plus.morse.MorseSettingsFragment.java`:**
        *   Extends `LamppSettingsFragment` or a similar base.
        *   Include preferences for:
            *   Audio frequency (default 800Hz)
            *   Camera sensitivity/threshold adjustment
            *   WPM range (min/max)
            *   Toggle for LLM error correction/translation
            *   Toggle for Audio Loopback Test.

---

**General Guidelines:**
*   Follow existing LAMPP code style, package naming conventions, and UI patterns.
*   Prioritize Android native APIs for signal processing. Avoid heavy external libraries unless absolutely necessary for performance.
*   Make sure UI updates are on the main thread.
*   Handle `null` checks and edge cases for hardware availability (e.g., no flash, no microphone).
*   Ensure efficient resource management for camera and audio (start/stop correctly to avoid battery drain and resource leaks).
*   Use `ViewModel` and `LiveData` where appropriate for UI state management.
*   Consider using `WorkManager` for any potentially long-running background tasks (though real-time decode might need foreground services if processing continues when the app is not active). For now, assume foreground operation within `MorseFragment`.

**Reference Existing Implementations for Patterns:**
*   **LAMPP Panel Integration:** `net.osmand.plus.ai.LlmChatFragment`, `net.osmand.plus.plugins.p2pshare.ui.P2pShareFragment`.
*   **Permission Handling:** `net.osmand.plus.plugins.p2pshare.P2pShareManager`.
*   **Settings Management:** `net.osmand.plus.lampp.LamppSettingsFragment`, `net.osmand.plus.OsmandSettings.java` (for new preferences).
*   **LLM Integration:** `net.osmand.plus.ai.LlmManager` and `net.osmand.plus.ai.rag.RagManager`.

This prompt should provide enough detail for Claude Code to begin methodical implementation.

at any time refer to D:\OsmAnd\OsmAnd\MORSE-CODE-SPEC.md for guidance.