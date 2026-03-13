package net.osmand.plus.plugins.p2pshare;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.PlatformUtil;

import org.apache.commons.logging.Log;

import java.io.File;
import java.security.MessageDigest;

/**
 * Verifies that received APKs are signed by the same key as the running app.
 *
 * Prevents a compromised device from spreading tampered APKs through the P2P
 * mesh network. Without this check, a single compromised device could propagate
 * malware to every connected peer.
 */
public class ApkSignatureVerifier {

	private static final Log LOG = PlatformUtil.getLog(ApkSignatureVerifier.class);

	private final Context context;

	/** Cached SHA-256 fingerprint of this app's signing certificate */
	@Nullable
	private String trustedFingerprint;

	public ApkSignatureVerifier(@NonNull Context context) {
		this.context = context.getApplicationContext();
	}

	/**
	 * Get the SHA-256 fingerprint of the currently running app's signing certificate.
	 * This is the "trusted" fingerprint that received APKs must match.
	 */
	@Nullable
	public String getTrustedFingerprint() {
		if (trustedFingerprint != null) {
			return trustedFingerprint;
		}

		try {
			Signature[] signatures = getAppSignatures(context.getPackageName());
			if (signatures != null && signatures.length > 0) {
				trustedFingerprint = computeFingerprint(signatures[0]);
			}
		} catch (Exception e) {
			LOG.error("Failed to get trusted APK fingerprint: " + e.getMessage());
		}
		return trustedFingerprint;
	}

	/**
	 * Verify that a received APK file is signed by the same key as this app.
	 *
	 * @param apkFile the APK file to verify
	 * @return true if the APK is signed by the same key, false otherwise
	 */
	public boolean verifyApk(@NonNull File apkFile) {
		if (!apkFile.exists() || !apkFile.canRead()) {
			LOG.warn("APK file does not exist or is not readable: " + apkFile.getName());
			return false;
		}

		String trusted = getTrustedFingerprint();
		if (trusted == null) {
			LOG.error("Cannot verify APK: unable to determine trusted fingerprint");
			return false;
		}

		try {
			PackageManager pm = context.getPackageManager();
			PackageInfo apkInfo;
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
				apkInfo = pm.getPackageArchiveInfo(apkFile.getAbsolutePath(),
						PackageManager.GET_SIGNING_CERTIFICATES);
			} else {
				apkInfo = pm.getPackageArchiveInfo(apkFile.getAbsolutePath(),
						PackageManager.GET_SIGNATURES);
			}

			if (apkInfo == null) {
				LOG.warn("Failed to parse APK info: " + apkFile.getName());
				return false;
			}

			Signature[] apkSignatures = getSignaturesFromPackageInfo(apkInfo);
			if (apkSignatures == null || apkSignatures.length == 0) {
				LOG.warn("APK has no signatures: " + apkFile.getName());
				return false;
			}

			String apkFingerprint = computeFingerprint(apkSignatures[0]);
			if (apkFingerprint == null) {
				LOG.warn("Failed to compute APK fingerprint: " + apkFile.getName());
				return false;
			}

			boolean match = trusted.equals(apkFingerprint);
			if (match) {
				LOG.info("APK signature verified: " + apkFile.getName());
			} else {
				LOG.warn("APK signature MISMATCH: " + apkFile.getName()
						+ " expected=" + trusted.substring(0, 12)
						+ " got=" + apkFingerprint.substring(0, 12));
			}
			return match;

		} catch (Exception e) {
			LOG.error("APK signature verification failed: " + e.getMessage());
			return false;
		}
	}

	/**
	 * Get signing certificates from the running app.
	 */
	@Nullable
	@SuppressWarnings("deprecation")
	private Signature[] getAppSignatures(@NonNull String packageName) throws PackageManager.NameNotFoundException {
		PackageManager pm = context.getPackageManager();
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
			PackageInfo info = pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES);
			if (info.signingInfo != null) {
				if (info.signingInfo.hasMultipleSigners()) {
					return info.signingInfo.getApkContentsSigners();
				} else {
					return info.signingInfo.getSigningCertificateHistory();
				}
			}
			return null;
		} else {
			PackageInfo info = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES);
			return info.signatures;
		}
	}

	/**
	 * Extract signatures from a PackageInfo, handling API level differences.
	 */
	@Nullable
	@SuppressWarnings("deprecation")
	private Signature[] getSignaturesFromPackageInfo(@NonNull PackageInfo info) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && info.signingInfo != null) {
			if (info.signingInfo.hasMultipleSigners()) {
				return info.signingInfo.getApkContentsSigners();
			} else {
				return info.signingInfo.getSigningCertificateHistory();
			}
		}
		return info.signatures;
	}

	/**
	 * Compute SHA-256 fingerprint of a signing certificate.
	 */
	@Nullable
	static String computeFingerprint(@NonNull Signature signature) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(signature.toByteArray());
			StringBuilder hex = new StringBuilder(hash.length * 2);
			for (byte b : hash) {
				hex.append(String.format("%02x", b));
			}
			return hex.toString();
		} catch (Exception e) {
			LOG.error("Failed to compute signature fingerprint: " + e.getMessage());
			return null;
		}
	}
}
