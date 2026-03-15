/*
 * This file is part of Rushlight, licensed under the GNU Affero General
 * Public License v3.0 or later. See the LICENSE file in the project root.
 */

package net.osmand.plus.morse;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.PlatformUtil;
import net.osmand.plus.ai.LlmManager;
import net.osmand.plus.ai.LlmManager.LlmCallback;
import net.osmand.plus.ai.rag.LocationContext;

import org.apache.commons.logging.Log;

import java.util.Locale;

/**
 * LAMPP Morse Code: Optional LLM integration for post-decode processing.
 *
 * Wraps LlmManager with Morse-specific prompt engineering for:
 * - Error correction of garbled decoded text
 * - Translation of decoded messages
 * - Emergency message interpretation with GPS context
 *
 * CRITICAL: This is NEVER in the real-time decode path. Pure DSP + lookup
 * tables handle all decoding. LLM is only used for optional post-processing
 * after reception stops.
 *
 * All methods handle "no model loaded" gracefully — they call onError()
 * immediately and return, never blocking.
 *
 * Reference: MORSE-CODE-SPEC.md section 7.
 */
public class MorseLlmHelper {

	private static final Log LOG = PlatformUtil.getLog(MorseLlmHelper.class);

	@Nullable
	private final LlmManager llmManager;

	/**
	 * Create a new MorseLlmHelper.
	 *
	 * @param llmManager the LLM manager instance, or null if AI is not available
	 */
	public MorseLlmHelper(@Nullable LlmManager llmManager) {
		this.llmManager = llmManager;
	}

	/**
	 * Check if LLM is available and a model is loaded.
	 * Always check this before calling any LLM method.
	 *
	 * @return true if a model is loaded and ready for inference
	 */
	public boolean isAvailable() {
		return llmManager != null
				&& LlmManager.isAiAvailable()
				&& llmManager.isModelLoaded();
	}

	/**
	 * Attempt error correction on decoded Morse text.
	 *
	 * Morse decoding can produce garbled characters from timing errors,
	 * noise interference, or adaptive WPM misclassification. The LLM
	 * attempts to reconstruct the likely intended message.
	 *
	 * @param decodedText the raw decoded text from MorseDecoder
	 * @param callback    streaming callback for corrected text
	 */
	public void correctMessage(@NonNull String decodedText, @NonNull LlmCallback callback) {
		if (!isAvailable()) {
			callback.onError("AI not available");
			return;
		}
		if (decodedText.trim().isEmpty()) {
			callback.onError("No text to correct");
			return;
		}

		String prompt = "The following text was decoded from Morse code and may contain "
				+ "errors from timing or detection issues. Fix any obvious errors and return "
				+ "ONLY the corrected message, nothing else. If it looks correct, return it "
				+ "unchanged.\n\nDecoded text: " + decodedText;

		LOG.info("MORSE AI: Requesting error correction for: " + decodedText);
		llmManager.generateResponseAsync(prompt, callback);
	}

	/**
	 * Translate a decoded message to a target language.
	 *
	 * Useful when communicating across language barriers in
	 * survival/emergency scenarios.
	 *
	 * @param text       the text to translate
	 * @param targetLang the target language (e.g., "Spanish", "French", "Arabic")
	 * @param callback   streaming callback for translated text
	 */
	public void translateMessage(@NonNull String text, @NonNull String targetLang,
	                             @NonNull LlmCallback callback) {
		if (!isAvailable()) {
			callback.onError("AI not available");
			return;
		}
		if (text.trim().isEmpty()) {
			callback.onError("No text to translate");
			return;
		}

		String prompt = "Translate the following message to " + targetLang
				+ ". Return ONLY the translation, nothing else.\n\nMessage: " + text;

		LOG.info("MORSE AI: Translating to " + targetLang + ": " + text);
		llmManager.generateResponseAsync(prompt, callback);
	}

	/**
	 * Interpret a received message in emergency/survival context.
	 *
	 * Provides urgency assessment and suggested response.
	 * GPS location is included if available for spatial context.
	 *
	 * @param text     the received message text
	 * @param location the receiver's current location, or null
	 * @param callback streaming callback for interpretation
	 */
	public void interpretMessage(@NonNull String text, @Nullable LocationContext location,
	                             @NonNull LlmCallback callback) {
		if (!isAvailable()) {
			callback.onError("AI not available");
			return;
		}
		if (text.trim().isEmpty()) {
			callback.onError("No text to interpret");
			return;
		}

		StringBuilder prompt = new StringBuilder();
		prompt.append("A Morse code message was received in a survival/emergency context. ");
		prompt.append("Briefly assess urgency and suggest an appropriate response. ");
		prompt.append("Keep it under 3 sentences.\n\n");
		prompt.append("Message: ").append(text);
		if (location != null) {
			prompt.append(String.format(Locale.US, "\nReceiver location: %.4f, %.4f",
					location.getLatitude(), location.getLongitude()));
		}

		LOG.info("MORSE AI: Interpreting message: " + text);
		llmManager.generateResponseAsync(prompt.toString(), callback);
	}
}
