package net.osmand.plus.lampp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import net.osmand.PlatformUtil;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;
import net.osmand.plus.activities.MapActivity;

import org.apache.commons.logging.Log;

/**
 * Multi-step onboarding flow for first-time Rushlight users.
 *
 * 4-step guided setup:
 * 1. "Welcome to Rushlight" — introduction + Get Started
 * 2. "Download a Map" — explain offline maps
 * 3. "Add Knowledge" — explain AI model + Wikipedia
 * 4. "You're Ready" — summary + Start Exploring
 *
 * Each step has: icon + title + description + action button + skip option.
 * Progress dots shown at bottom.
 * Re-accessible from Tools > "Show Setup Guide".
 */
public class OnboardingOverlay {

	private static final Log LOG = PlatformUtil.getLog(OnboardingOverlay.class);
	private static final int FADE_DURATION = 300;
	private static final int TOTAL_STEPS = 4;

	// Step content (title, description, icon resource, action button text)
	private static final String[] STEP_TITLES = {
			"Welcome to Rushlight",
			"Download a Map",
			"Add Knowledge",
			"You're Ready"
	};

	private static final String[] STEP_DESCRIPTIONS = {
			"Rushlight is your offline survival computer. Maps, AI assistant, Wikipedia, peer-to-peer sharing, and Morse code — all working without internet.\n\nDesigned for journalists, activists, and aid workers in environments where connectivity fails.",
			"Download an offline map for your region before you go. Maps use GPS positioning — no cell towers needed.\n\nTap the menu button to access map downloads, or use the P2P tab to receive maps from a nearby device.",
			"Download an AI model for offline questions and a Wikipedia database for reference.\n\nThe AI Chat tab provides an on-device assistant. The Wiki tab gives you full Wikipedia access offline.",
			"Rushlight is ready. Here's what you can do:\n\n• Navigate offline with GPS maps\n• Ask the AI assistant anything\n• Browse Wikipedia offline\n• Share content device-to-device\n• Send Morse code signals\n• Access security features in Tools"
	};

	private static final int[] STEP_ICONS = {
			R.drawable.ic_action_world_globe,   // Welcome
			R.drawable.ic_action_world_globe,   // Map download
			R.drawable.ic_action_help,          // Knowledge
			R.drawable.ic_action_done           // Ready
	};

	private static final String[] STEP_ACTIONS = {
			"Get Started",
			"Next",
			"Next",
			"Start Exploring"
	};

	/**
	 * Show the onboarding overlay if it hasn't been shown before.
	 */
	public static void showIfNeeded(@NonNull MapActivity mapActivity) {
		OsmandApplication app = (OsmandApplication) mapActivity.getApplication();
		if (app.getSettings().LAMPP_ONBOARDING_SHOWN.get()) {
			return;
		}

		show(mapActivity, app, 0);
	}

	/**
	 * Force-show the onboarding overlay (for "Show Setup Guide" from Tools).
	 */
	public static void showFromTools(@NonNull MapActivity mapActivity) {
		OsmandApplication app = (OsmandApplication) mapActivity.getApplication();
		show(mapActivity, app, 0);
	}

	/**
	 * Display a specific step of the onboarding overlay.
	 */
	private static void show(@NonNull MapActivity mapActivity, @NonNull OsmandApplication app, int step) {
		ViewGroup rootView = mapActivity.findViewById(android.R.id.content);
		if (rootView == null) {
			LOG.error("Cannot show onboarding: no root view");
			return;
		}

		// Remove any existing overlay
		View existing = rootView.findViewWithTag("onboarding_overlay");
		if (existing != null) {
			rootView.removeView(existing);
		}

		// Inflate the overlay
		View overlay = LayoutInflater.from(mapActivity)
				.inflate(R.layout.lampp_onboarding_overlay, rootView, false);
		overlay.setTag("onboarding_overlay");

		// Set up content for current step
		TextView titleView = overlay.findViewById(R.id.onboarding_title);
		TextView descView = overlay.findViewById(R.id.onboarding_description);
		ImageView iconView = overlay.findViewById(R.id.onboarding_icon);
		Button actionButton = overlay.findViewById(R.id.onboarding_dismiss);
		TextView skipButton = overlay.findViewById(R.id.onboarding_skip);
		LinearLayout dotsContainer = overlay.findViewById(R.id.onboarding_dots);

		// Set step content
		if (titleView != null) {
			titleView.setText(STEP_TITLES[step]);
		}
		if (descView != null) {
			descView.setText(STEP_DESCRIPTIONS[step]);
		}
		if (iconView != null) {
			iconView.setImageResource(STEP_ICONS[step]);
		}

		// Action button
		actionButton.setText(STEP_ACTIONS[step]);
		actionButton.setOnClickListener(v -> {
			if (step < TOTAL_STEPS - 1) {
				// Move to next step
				rootView.removeView(overlay);
				show(mapActivity, app, step + 1);
			} else {
				// Final step — dismiss and mark complete
				dismiss(overlay, rootView, app);
			}
		});

		// Skip button (hidden on last step)
		if (skipButton != null) {
			if (step < TOTAL_STEPS - 1) {
				skipButton.setVisibility(View.VISIBLE);
				skipButton.setText("Skip (" + (step + 1) + "/" + TOTAL_STEPS + ")");
				skipButton.setOnClickListener(v -> dismiss(overlay, rootView, app));
			} else {
				skipButton.setVisibility(View.GONE);
			}
		}

		// Progress dots
		if (dotsContainer != null) {
			dotsContainer.removeAllViews();
			for (int i = 0; i < TOTAL_STEPS; i++) {
				View dot = new View(mapActivity);
				LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(16, 16);
				params.setMargins(8, 0, 8, 0);
				dot.setLayoutParams(params);

				if (i == step) {
					// Active dot
					dot.setBackgroundResource(R.drawable.bg_circle_active);
				} else {
					// Inactive dot
					dot.setBackgroundResource(R.drawable.bg_circle_inactive);
				}

				dotsContainer.addView(dot);
			}
		}

		// Consume taps on overlay background
		overlay.setOnClickListener(v -> {
			// Prevent taps from reaching map
		});

		// Add overlay with fade-in
		overlay.setAlpha(0f);
		rootView.addView(overlay, new FrameLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.MATCH_PARENT));

		overlay.animate()
				.alpha(1f)
				.setDuration(FADE_DURATION)
				.setInterpolator(new DecelerateInterpolator())
				.start();

		LOG.info("Onboarding step " + (step + 1) + "/" + TOTAL_STEPS + " shown");
	}

	/**
	 * Dismiss the overlay with a fade-out animation.
	 */
	private static void dismiss(@NonNull View overlay, @NonNull ViewGroup parent,
	                              @NonNull OsmandApplication app) {
		app.getSettings().LAMPP_ONBOARDING_SHOWN.set(true);

		overlay.animate()
				.alpha(0f)
				.setDuration(FADE_DURATION)
				.setInterpolator(new DecelerateInterpolator())
				.setListener(new AnimatorListenerAdapter() {
					@Override
					public void onAnimationEnd(Animator animation) {
						parent.removeView(overlay);
					}
				})
				.start();

		LOG.info("Onboarding overlay dismissed");
	}
}
