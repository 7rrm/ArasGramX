package tw.nekomimi.nekogram;

import tw.nekomimi.nekogram.MeeroStrings;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;

import org.telegram.messenger.R;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;

import java.security.MessageDigest;
import java.util.Locale;

/**
 * MeeroX v165 (approved Pack A - "درع MeeroX الأساسي"): self-integrity
 * guard.
 *
 * The app knows its own release signing fingerprint. Once per process,
 * {@link #checkAndWarn(BaseFragment)} compares the actually-running APK's
 * signing certificate against that embedded fingerprint. A mismatch means
 * this file was re-built/re-signed by someone else - the classic stolen-
 * build clone - so the user gets a hard red warning with both fingerprints
 * and can exit, instead of trusting an impostor with their account.
 *
 * The official fingerprint is ALSO shown to users in MeeroX settings
 * (Security section) so it can be compared against the fingerprint the
 * author publishes on his channel - the public-owner proof loop.
 *
 * Honest limitation (disclosed in the pack): if an attacker edits the
 * source and removes this check before rebuilding, that is beyond any
 * in-app guard. The real hard wall for that is Play Integrity (Pack B).
 *
 * No switch: this is passive protection that fires only on a tampered
 * build (an official build never sees or hears it), exactly like a seat
 * belt - per the approved pack spec.
 */
public final class MeeroSignatureGuard {

    private MeeroSignatureGuard() {
    }

    /**
     * SHA-256 of the MeeroX release signing certificate, colon-separated
     * uppercase hex, taken from the real shipped APK (v164 artifact). If
     * the signing identity is ever rotated on purpose, update this to the
     * new certificate's fingerprint in the same release.
     */
    public static final String OFFICIAL_FINGERPRINT =
            "29:F7:3E:38:D2:13:8B:73:72:05:AC:C2:25:1B:28:45:2F:5A:50:99:66:FE:64:17:3D:B2:09:FE:DA:A7:9E:F7";

    /** One warning per process - not a nag screen every resume. */
    private static boolean warnedThisProcess;

    /**
     * Colon-separated uppercase SHA-256 fingerprint of the certificate that
     * signed the RUNNING apk, or null when it cannot be read.
     */
    public static String currentFingerprint(Context context) {
        try {
            Signature sig = firstSignature(context);
            if (sig == null) {
                return null;
            }
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(sig.toByteArray());
            StringBuilder sb = new StringBuilder(digest.length * 3 - 1);
            for (int i = 0; i < digest.length; i++) {
                if (i > 0) {
                    sb.append(':');
                }
                sb.append(String.format(Locale.US, "%02X", digest[i]));
            }
            return sb.toString();
        } catch (Throwable t) {
            return null;
        }
    }

    /** True only when the running apk carries the official MeeroX key. */
    public static boolean isOfficial(Context context) {
        String current = currentFingerprint(context);
        return current != null && current.equals(OFFICIAL_FINGERPRINT);
    }

    /**
     * One-shot-per-process tamper check. On a genuine build this costs a
     * single boolean compare and returns immediately; on a clone it shows
     * the red warning. Call from the first screen the user reaches.
     */
    public static void checkAndWarn(final BaseFragment fragment) {
        if (warnedThisProcess || fragment == null || fragment.getParentActivity() == null) {
            return;
        }
        warnedThisProcess = true;
        if (isOfficial(fragment.getParentActivity())) {
            return; // official build - the guard stays invisible forever
        }
        final Context ctx = fragment.getParentActivity();
        final android.app.Activity host = fragment.getParentActivity();
        final String current = currentFingerprint(ctx);
        try {
            // Telegram's own Builder has no setCancelable - apply the two
            // flags on the shown dialog itself instead.
            final AlertDialog dlg = new AlertDialog.Builder(ctx)
                    .setTitle(MeeroStrings.s(234))
                    .setMessage(MeeroStrings.f(233, current == null ? "?" : current, OFFICIAL_FINGERPRINT))
                    .setPositiveButton(MeeroStrings.s(232), (d, w) -> host.finishAffinity())
                    .setNegativeButton(MeeroStrings.s(231), null)
                    .show();
            dlg.setCancelable(false);
            dlg.setCanceledOnTouchOutside(false);
        } catch (Throwable ignore) {
            // dialog host may be transient - the flag still fired once
        }
    }

    @SuppressLint("PackageManagerGetSignatures")
    private static Signature firstSignature(Context context) throws Exception {
        final PackageManager pm = context.getPackageManager();
        final String pkg = context.getPackageName();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageInfo pi = pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES);
            if (pi.signingInfo == null) {
                return null;
            }
            Signature[] sigs = pi.signingInfo.hasMultipleSigners()
                    ? pi.signingInfo.getApkContentsSigners()
                    : pi.signingInfo.getSigningCertificateHistory();
            return sigs != null && sigs.length > 0 ? sigs[0] : null;
        } else {
            PackageInfo pi = pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES);
            return pi.signatures != null && pi.signatures.length > 0 ? pi.signatures[0] : null;
        }
    }
}
