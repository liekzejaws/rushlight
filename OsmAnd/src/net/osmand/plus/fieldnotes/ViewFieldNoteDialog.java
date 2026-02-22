package net.osmand.plus.fieldnotes;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;

import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Dialog for viewing a FieldNote's details.
 * Shown when tapping a FieldNote pin on the map.
 *
 * Shows: category, title, note text, timestamp, expiry, author hash, location.
 * Delete button only visible for notes authored by this device.
 */
public class ViewFieldNoteDialog extends DialogFragment {

	public static final String TAG = "ViewFieldNoteDialog";

	private static final String ARG_NOTE_ID = "note_id";

	private String noteId;

	public static ViewFieldNoteDialog newInstance(@NonNull String noteId) {
		ViewFieldNoteDialog dialog = new ViewFieldNoteDialog();
		Bundle args = new Bundle();
		args.putString(ARG_NOTE_ID, noteId);
		dialog.setArguments(args);
		return dialog;
	}

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (getArguments() != null) {
			noteId = getArguments().getString(ARG_NOTE_ID);
		}
	}

	@NonNull
	@Override
	public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
		FragmentActivity activity = requireActivity();
		OsmandApplication app = (OsmandApplication) activity.getApplication();
		FieldNotesManager manager = app.getFieldNotesManager();

		FieldNote note = manager.getNoteById(noteId);

		View view = LayoutInflater.from(activity).inflate(
				R.layout.dialog_view_fieldnote, null);

		if (note != null) {
			populateView(view, note, manager);
		}

		AlertDialog.Builder builder = new AlertDialog.Builder(activity);
		builder.setView(view);
		return builder.create();
	}

	private void populateView(@NonNull View view, @NonNull FieldNote note,
			@NonNull FieldNotesManager manager) {

		FieldNote.Category category = note.getCategory();

		// Category header
		ImageView icon = view.findViewById(R.id.fieldnote_view_icon);
		icon.setImageResource(category.getIconRes());
		icon.setColorFilter(category.getColor());

		TextView categoryText = view.findViewById(R.id.fieldnote_view_category);
		categoryText.setText(category.getDisplayName());
		categoryText.setTextColor(category.getColor());

		// Title
		TextView titleText = view.findViewById(R.id.fieldnote_view_title);
		titleText.setText(note.getTitle());

		// Note text
		TextView noteText = view.findViewById(R.id.fieldnote_view_note);
		if (note.getNote() != null && !note.getNote().isEmpty()) {
			noteText.setText(note.getNote());
			noteText.setVisibility(View.VISIBLE);
		} else {
			noteText.setVisibility(View.GONE);
		}

		// Timestamp
		TextView timestampText = view.findViewById(R.id.fieldnote_view_timestamp);
		timestampText.setText(formatRelativeTime(note.getTimestamp()));

		// Expiry
		TextView expiryText = view.findViewById(R.id.fieldnote_view_expiry);
		if (note.getTtlHours() <= 0) {
			expiryText.setText("Never (permanent)");
		} else {
			long expiryTime = note.getExpiryTime();
			long remaining = expiryTime - System.currentTimeMillis();
			if (remaining <= 0) {
				expiryText.setText("Expired");
				expiryText.setTextColor(0xFFFF4444);
			} else {
				expiryText.setText("in " + formatDuration(remaining));
			}
		}

		// Author
		TextView authorText = view.findViewById(R.id.fieldnote_view_author);
		authorText.setText(note.getAuthorId());

		// Verified (crypto signature status)
		ImageView verifiedIcon = view.findViewById(R.id.fieldnote_view_verified_icon);
		TextView verifiedText = view.findViewById(R.id.fieldnote_view_verified);
		if (FieldNoteSigner.isSigned(note)) {
			boolean valid = FieldNoteSigner.verify(note);
			if (valid) {
				verifiedIcon.setImageResource(R.drawable.ic_action_done);
				verifiedIcon.setColorFilter(0xFF4CAF50); // green
				verifiedText.setText("Signature verified");
				verifiedText.setTextColor(0xFF4CAF50);
			} else {
				verifiedIcon.setImageResource(R.drawable.ic_action_alert);
				verifiedIcon.setColorFilter(0xFFFF4444); // red
				verifiedText.setText("Signature invalid");
				verifiedText.setTextColor(0xFFFF4444);
			}
		} else {
			verifiedIcon.setImageResource(R.drawable.ic_action_remove);
			verifiedIcon.setColorFilter(0xFF999999); // grey
			verifiedText.setText("Unsigned");
			verifiedText.setTextColor(0xFF999999);
		}

		// Confirmations
		TextView confirmsText = view.findViewById(R.id.fieldnote_view_confirms);
		confirmsText.setText(String.valueOf(note.getConfirmations()));

		// Score & voting
		TextView scoreText = view.findViewById(R.id.fieldnote_view_score);
		scoreText.setText(String.valueOf(note.getScore()));
		// Color: green for positive, red for negative, neutral for zero
		if (note.getScore() > 0) {
			scoreText.setTextColor(0xFF4CAF50); // green
		} else if (note.getScore() < 0) {
			scoreText.setTextColor(0xFFFF4444); // red
		}

		ImageButton upBtn = view.findViewById(R.id.fieldnote_vote_up);
		ImageButton downBtn = view.findViewById(R.id.fieldnote_vote_down);

		if (manager.hasVoted(note.getId())) {
			// Already voted — disable buttons
			upBtn.setEnabled(false);
			downBtn.setEnabled(false);
			upBtn.setAlpha(0.3f);
			downBtn.setAlpha(0.3f);
		} else {
			upBtn.setOnClickListener(v -> {
				manager.upvoteNote(note.getId());
				scoreText.setText(String.valueOf(note.getScore()));
				scoreText.setTextColor(note.getScore() > 0 ? 0xFF4CAF50
						: note.getScore() < 0 ? 0xFFFF4444 : 0xFFCCCCCC);
				upBtn.setEnabled(false);
				downBtn.setEnabled(false);
				upBtn.setAlpha(0.3f);
				downBtn.setAlpha(0.3f);
			});
			downBtn.setOnClickListener(v -> {
				manager.downvoteNote(note.getId());
				scoreText.setText(String.valueOf(note.getScore()));
				scoreText.setTextColor(note.getScore() > 0 ? 0xFF4CAF50
						: note.getScore() < 0 ? 0xFFFF4444 : 0xFFCCCCCC);
				upBtn.setEnabled(false);
				downBtn.setEnabled(false);
				upBtn.setAlpha(0.3f);
				downBtn.setAlpha(0.3f);
			});
		}

		// Location
		TextView locationText = view.findViewById(R.id.fieldnote_view_location);
		locationText.setText(String.format(Locale.US, "%.5f, %.5f",
				note.getLat(), note.getLon()));

		// Delete button — only for own notes
		Button deleteButton = view.findViewById(R.id.fieldnote_delete_button);
		String deviceAuthorId = manager.getDeviceAuthorId();
		if (note.getAuthorId().equals(deviceAuthorId)) {
			deleteButton.setVisibility(View.VISIBLE);
			deleteButton.setOnClickListener(v -> {
				new AlertDialog.Builder(requireContext())
						.setTitle("Delete FieldNote?")
						.setMessage("This will permanently remove this note.")
						.setPositiveButton("Delete", (dialog, which) -> {
							manager.deleteNote(note.getId());
							Toast.makeText(requireContext(),
									"FieldNote deleted", Toast.LENGTH_SHORT).show();
							dismiss();
						})
						.setNegativeButton("Cancel", null)
						.show();
			});
		} else {
			deleteButton.setVisibility(View.GONE);
		}

		// Close button
		Button closeButton = view.findViewById(R.id.fieldnote_close_button);
		closeButton.setOnClickListener(v -> dismiss());
	}

	/**
	 * Format a timestamp as a human-readable relative time.
	 * e.g., "3 minutes ago", "2 hours ago", "Yesterday", or a date.
	 */
	@NonNull
	private static String formatRelativeTime(long timestamp) {
		long diff = System.currentTimeMillis() - timestamp;

		if (diff < TimeUnit.MINUTES.toMillis(1)) {
			return "Just now";
		} else if (diff < TimeUnit.HOURS.toMillis(1)) {
			long minutes = TimeUnit.MILLISECONDS.toMinutes(diff);
			return minutes + (minutes == 1 ? " minute ago" : " minutes ago");
		} else if (diff < TimeUnit.DAYS.toMillis(1)) {
			long hours = TimeUnit.MILLISECONDS.toHours(diff);
			return hours + (hours == 1 ? " hour ago" : " hours ago");
		} else if (diff < TimeUnit.DAYS.toMillis(7)) {
			long days = TimeUnit.MILLISECONDS.toDays(diff);
			return days + (days == 1 ? " day ago" : " days ago");
		} else {
			SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy HH:mm", Locale.US);
			return sdf.format(new Date(timestamp));
		}
	}

	/**
	 * Format a duration in milliseconds as a human-readable string.
	 * e.g., "5 hours", "3 days", "2 weeks"
	 */
	@NonNull
	private static String formatDuration(long millis) {
		if (millis < TimeUnit.HOURS.toMillis(1)) {
			long minutes = TimeUnit.MILLISECONDS.toMinutes(millis);
			return minutes + (minutes == 1 ? " minute" : " minutes");
		} else if (millis < TimeUnit.DAYS.toMillis(1)) {
			long hours = TimeUnit.MILLISECONDS.toHours(millis);
			return hours + (hours == 1 ? " hour" : " hours");
		} else if (millis < TimeUnit.DAYS.toMillis(14)) {
			long days = TimeUnit.MILLISECONDS.toDays(millis);
			return days + (days == 1 ? " day" : " days");
		} else {
			long weeks = TimeUnit.MILLISECONDS.toDays(millis) / 7;
			return weeks + (weeks == 1 ? " week" : " weeks");
		}
	}
}
