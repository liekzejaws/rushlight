package net.osmand.plus.lampp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import net.osmand.PlatformUtil;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;
import net.osmand.plus.activities.MapActivity;

import org.apache.commons.logging.Log;

/**
 * Phase 12: First-launch onboarding overlay.
 *
 * Shows a semi-transparent overlay with descriptions of the 5 survival features.
 * Displayed once on first launch, dismissed with a "Got it" button.
 * Uses LAMPP_ONBOARDING_SHOWN preference to track display state.
 */
public class OnboardingOverlay {

	private static final Log LOG = PlatformUtil.getLog(OnboardingOverlay.class);
	private static final int FADE_DURATION = 300;

	/**
	 * Show the onboarding overlay if it hasn't been shown before.
	 *
	 * @param mapActivity The map activity to attach the overlay to
	 */
	public static void showIfNeeded(@NonNull MapActivity mapActivity) {
		OsmandApplication app = (OsmandApplication) mapActivity.getApplication();
		if (app.getSettings().LAMPP_ONBOARDING_SHOWN.get()) {
			return; // Already shown
		}

		show(mapActivity, app);
	}

	/**
	 * Display the onboarding overlay.
	 */
	private static void show(@NonNull MapActivity mapActivity, @NonNull OsmandApplication app) {
		// Find the root view to overlay
		ViewGroup rootView = mapActivity.findViewById(android.R.id.content);
		if (rootView == null) {
			LOG.error("Cannot show onboarding: no root view");
			return;
		}

		// Inflate the overlay
		View overlay = LayoutInflater.from(mapActivity)
				.inflate(R.layout.lampp_onboarding_overlay, rootView, false);

		// Set up dismiss button
		Button dismissButton = overlay.findViewById(R.id.onboarding_dismiss);
		dismissButton.setOnClickListener(v -> {
			dismiss(overlay, rootView, app);
		});

		// Also dismiss on tap anywhere on the overlay
		overlay.setOnClickListener(v -> {
			dismiss(overlay, rootView, app);
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

		LOG.info("Onboarding overlay shown");
	}

	/**
	 * Dismiss the overlay with a fade-out animation.
	 */
	private static void dismiss(@NonNull View overlay, @NonNull ViewGroup parent,
	                              @NonNull OsmandApplication app) {
		// Mark as shown so it won't appear again
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
