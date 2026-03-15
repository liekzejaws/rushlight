/*
 * This file is part of Rushlight, licensed under the GNU Affero General
 * Public License v3.0 or later. See the LICENSE file in the project root.
 */

package net.osmand.plus.lampp;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Build;
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
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

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

	// v0.5: Step 3 description uses string resource and adds action buttons
	private static final String[] STEP_DESCRIPTIONS = {
			"Rushlight is your offline survival computer. Maps, AI assistant, Wikipedia, peer-to-peer sharing, and Morse code — all working without internet.\n\nDesigned for journalists, activists, and aid workers in environments where connectivity fails.",
			"Download an offline map for your region before you go. Maps use GPS positioning — no cell towers needed.\n\nTap the menu button to access map downloads, or use the P2P tab to receive maps from a nearby device.",
			null, // v0.5: Loaded from string resource + action buttons added dynamically
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
			if (STEP_DESCRIPTIONS[step] != null) {
				descView.setText(STEP_DESCRIPTIONS[step]);
			} else {
				// v0.5: Step 3 uses string resource
				descView.setText(app.getString(R.string.onboarding_knowledge_desc));
			}
		}
		if (iconView != null) {
			iconView.setImageResource(STEP_ICONS[step]);
			iconView.setColorFilter(Color.parseColor("#4CAF50"), PorterDuff.Mode.SRC_IN);
		}

		// v0.5: Add action buttons for step 3 (Add Knowledge)
		if (step == 2) {
			addKnowledgeActionButtons(overlay, mapActivity, app, rootView);
		}

		// Action button
		actionButton.setText(STEP_ACTIONS[step]);
		actionButton.setOnClickListener(v -> {
			if (step < TOTAL_STEPS - 1) {
				// Move to next step
				rootView.removeView(overlay);
				show(mapActivity, app, step + 1);
			} else {
				// Final step — request permissions, then dismiss
				requestAllPermissions(mapActivity);
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
	 * v0.5: Add "Get AI Model" and "Get Wikipedia" action buttons to step 3.
	 * These dismiss onboarding and navigate directly to the relevant tab.
	 */
	private static void addKnowledgeActionButtons(@NonNull View overlay,
	                                               @NonNull MapActivity mapActivity,
	                                               @NonNull OsmandApplication app,
	                                               @NonNull ViewGroup rootView) {
		// Find the description view and add buttons below it
		TextView descView = overlay.findViewById(R.id.onboarding_description);
		if (descView == null) return;
		ViewGroup parent = (ViewGroup) descView.getParent();
		if (parent == null) return;

		int descIndex = parent.indexOfChild(descView);

		// Create a horizontal button container
		LinearLayout buttonRow = new LinearLayout(mapActivity);
		buttonRow.setOrientation(LinearLayout.HORIZONTAL);
		buttonRow.setGravity(android.view.Gravity.CENTER);
		LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT);
		int marginPx = (int) (12 * mapActivity.getResources().getDisplayMetrics().density);
		rowParams.topMargin = marginPx;
		buttonRow.setLayoutParams(rowParams);

		// "Get AI Model" button
		Button aiButton = new Button(mapActivity);
		aiButton.setText(R.string.onboarding_get_ai_model);
		aiButton.setTextSize(14);
		aiButton.setAllCaps(false);
		aiButton.setBackgroundColor(Color.parseColor("#1B5E20")); // dark green
		aiButton.setTextColor(Color.WHITE);
		int padPx = (int) (8 * mapActivity.getResources().getDisplayMetrics().density);
		aiButton.setPadding(padPx * 2, padPx, padPx * 2, padPx);
		LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
				0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
		btnParams.setMarginEnd(padPx);
		aiButton.setLayoutParams(btnParams);
		aiButton.setOnClickListener(v -> {
			dismiss(overlay, rootView, app);
			mapActivity.getLamppPanelManager().openPanel(LamppTab.AI_CHAT);
		});

		// "Get Wikipedia" button
		Button wikiButton = new Button(mapActivity);
		wikiButton.setText(R.string.onboarding_get_wikipedia);
		wikiButton.setTextSize(14);
		wikiButton.setAllCaps(false);
		wikiButton.setBackgroundColor(Color.parseColor("#1B5E20"));
		wikiButton.setTextColor(Color.WHITE);
		wikiButton.setPadding(padPx * 2, padPx, padPx * 2, padPx);
		LinearLayout.LayoutParams btnParams2 = new LinearLayout.LayoutParams(
				0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
		btnParams2.setMarginStart(padPx);
		wikiButton.setLayoutParams(btnParams2);
		wikiButton.setOnClickListener(v -> {
			dismiss(overlay, rootView, app);
			mapActivity.getLamppPanelManager().openPanel(LamppTab.WIKI);
		});

		buttonRow.addView(aiButton);
		buttonRow.addView(wikiButton);

		// Insert after description
		parent.addView(buttonRow, descIndex + 1);
	}

	/**
	 * Request all Rushlight-specific permissions in a single batch.
	 * Front-loads permission prompts during onboarding to prevent interruptions
	 * during actual usage or demo recording.
	 */
	private static void requestAllPermissions(@NonNull MapActivity mapActivity) {
		java.util.List<String> needed = new java.util.ArrayList<>();

		// Location (for P2P, RAG POI search)
		if (ContextCompat.checkSelfPermission(mapActivity, Manifest.permission.ACCESS_FINE_LOCATION)
				!= PackageManager.PERMISSION_GRANTED) {
			needed.add(Manifest.permission.ACCESS_FINE_LOCATION);
		}

		// Camera (for Morse receive via camera)
		if (ContextCompat.checkSelfPermission(mapActivity, Manifest.permission.CAMERA)
				!= PackageManager.PERMISSION_GRANTED) {
			needed.add(Manifest.permission.CAMERA);
		}

		// Microphone (for Morse receive via mic)
		if (ContextCompat.checkSelfPermission(mapActivity, Manifest.permission.RECORD_AUDIO)
				!= PackageManager.PERMISSION_GRANTED) {
			needed.add(Manifest.permission.RECORD_AUDIO);
		}

		// Bluetooth permissions (API 31+, for P2P)
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
			if (ContextCompat.checkSelfPermission(mapActivity, Manifest.permission.BLUETOOTH_SCAN)
					!= PackageManager.PERMISSION_GRANTED) {
				needed.add(Manifest.permission.BLUETOOTH_SCAN);
			}
			if (ContextCompat.checkSelfPermission(mapActivity, Manifest.permission.BLUETOOTH_CONNECT)
					!= PackageManager.PERMISSION_GRANTED) {
				needed.add(Manifest.permission.BLUETOOTH_CONNECT);
			}
			if (ContextCompat.checkSelfPermission(mapActivity, Manifest.permission.BLUETOOTH_ADVERTISE)
					!= PackageManager.PERMISSION_GRANTED) {
				needed.add(Manifest.permission.BLUETOOTH_ADVERTISE);
			}
		}

		// Nearby WiFi Devices (API 33+, for P2P WiFi Direct)
		if (Build.VERSION.SDK_INT >= 33) {
			if (ContextCompat.checkSelfPermission(mapActivity, Manifest.permission.NEARBY_WIFI_DEVICES)
					!= PackageManager.PERMISSION_GRANTED) {
				needed.add(Manifest.permission.NEARBY_WIFI_DEVICES);
			}
		}

		if (!needed.isEmpty()) {
			LOG.info("Requesting " + needed.size() + " permissions during onboarding");
			ActivityCompat.requestPermissions(mapActivity,
					needed.toArray(new String[0]), 9001);
		}
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
