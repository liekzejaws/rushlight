package net.osmand.plus.security;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import net.osmand.PlatformUtil;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.activities.MapActivity;

import org.apache.commons.logging.Log;

/**
 * Phase 15: BroadcastReceiver for intercepting outgoing calls that match the stealth dialer code.
 *
 * When the user dials the secret code (e.g., *#73784#), this receiver:
 * 1. Aborts the call (prevents it from going through to the phone)
 * 2. Launches MapActivity (opens Rushlight)
 *
 * This receiver listens for android.intent.action.NEW_OUTGOING_CALL.
 * It only activates when stealth mode is enabled.
 */
public class StealthDialerReceiver extends BroadcastReceiver {

	private static final Log LOG = PlatformUtil.getLog(StealthDialerReceiver.class);

	@Override
	public void onReceive(Context context, Intent intent) {
		if (intent == null || context == null) {
			return;
		}

		// Only handle NEW_OUTGOING_CALL
		if (!"android.intent.action.NEW_OUTGOING_CALL".equals(intent.getAction())) {
			return;
		}

		String dialedNumber = getResultData();
		if (dialedNumber == null) {
			dialedNumber = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER);
		}

		if (dialedNumber == null || dialedNumber.isEmpty()) {
			return;
		}

		// Check if app context is available
		OsmandApplication app;
		try {
			app = (OsmandApplication) context.getApplicationContext();
		} catch (ClassCastException e) {
			return;
		}

		StealthManager stealthManager = app.getSecurityManager().getStealthManager();

		// Only intercept if stealth mode is enabled
		if (!stealthManager.isStealthEnabled()) {
			return;
		}

		// Check if the dialed number matches our code
		if (stealthManager.matchesDialerCode(dialedNumber)) {
			LOG.info("Stealth code matched — launching Rushlight");

			// Abort the call (prevent it from going to the phone dialer)
			setResultData(null);

			// Launch MapActivity
			Intent launchIntent = new Intent(context, MapActivity.class);
			launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
			context.startActivity(launchIntent);
		}
		// If no match, do nothing — the call proceeds normally
	}
}
