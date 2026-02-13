package net.osmand.plus.plugins.p2pshare.ui;

import android.app.Dialog;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import net.osmand.plus.R;
import net.osmand.plus.utils.AndroidUtils;

/**
 * Dialog explaining why P2P sharing requires Bluetooth and location permissions.
 * Shown before the system permission dialog for better user understanding.
 */
public class P2pPermissionRationaleDialog extends DialogFragment {

	public static final String TAG = "P2pPermissionRationale";

	@Nullable
	private static Runnable pendingOnGrantAction;

	/**
	 * Show the rationale dialog.
	 *
	 * @param fragmentManager Fragment manager to use
	 * @param onGrantAction   Action to run when user taps "Grant Permissions"
	 */
	public static void showInstance(@NonNull FragmentManager fragmentManager,
	                                 @NonNull Runnable onGrantAction) {
		if (AndroidUtils.isFragmentCanBeAdded(fragmentManager, TAG)) {
			pendingOnGrantAction = onGrantAction;
			P2pPermissionRationaleDialog dialog = new P2pPermissionRationaleDialog();
			dialog.show(fragmentManager, TAG);
		}
	}

	@NonNull
	@Override
	public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
		View view = LayoutInflater.from(getContext())
				.inflate(R.layout.dialog_p2p_permission_rationale, null);

		TextView explanationText = view.findViewById(R.id.explanation_text);
		TextView detailsText = view.findViewById(R.id.details_text);

		// Set API-level-appropriate explanation
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
			explanationText.setText(R.string.p2p_share_permission_rationale_api31);
			detailsText.setText(R.string.p2p_share_permission_details_api31);
		} else {
			explanationText.setText(R.string.p2p_share_permission_rationale_legacy);
			detailsText.setText(R.string.p2p_share_permission_details_legacy);
		}

		AlertDialog.Builder builder = new AlertDialog.Builder(requireContext())
				.setTitle(R.string.p2p_share_permission_title)
				.setView(view)
				.setPositiveButton(R.string.p2p_share_grant_permissions, (dialog, which) -> {
					if (pendingOnGrantAction != null) {
						pendingOnGrantAction.run();
						pendingOnGrantAction = null;
					}
				})
				.setNegativeButton(R.string.shared_string_cancel, (dialog, which) -> {
					pendingOnGrantAction = null;
					dismiss();
				});

		return builder.create();
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();
		// Don't null out pendingOnGrantAction here — it may still be needed
	}
}
