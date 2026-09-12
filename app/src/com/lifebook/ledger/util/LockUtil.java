package com.lifebook.ledger.util;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Build;
import android.os.CancellationSignal;

/**
 * 指纹解锁：读取开关状态、检测手机是否具备指纹条件、弹出系统指纹框。
 * 使用系统自带的 FingerprintManager（各版本稳定，与真机行为一致），
 * 触发的就是手机里录入的内置指纹（含屏下指纹）。
 * 仅依赖系统框架，不引入任何第三方库。
 */
public final class LockUtil {

    private LockUtil() {
    }

    // 指纹错误码（框架 BiometricPrompt / FingerprintManager 共用同一套值）
    public static final int ERROR_CANCELED = 5;
    public static final int ERROR_USER_CANCELED = 10;
    public static final int ERROR_NEGATIVE_BUTTON = 13;
    public static final int ERROR_LOCKOUT = 7;

    private static final String PREFS = "lifebook_prefs";
    private static final String KEY_ENABLED = "fingerprint_lock";

    /** 指纹解锁是否已开启。 */
    public static boolean enabled(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_ENABLED, false);
    }

    /** 保存指纹解锁开关状态。 */
    public static void setEnabled(Context c, boolean on) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_ENABLED, on).apply();
    }

    /**
     * 手机是否已具备指纹解锁条件：已设锁屏密码 + 有指纹硬件 + 已录入指纹。
     * 满足任一不满足，开启后也无法真正验证，需提示用户。
     */
    public static boolean canUse(Context c) {
        KeyguardManager km = (KeyguardManager) c.getSystemService(Context.KEYGUARD_SERVICE);
        if (km == null || !km.isDeviceSecure()) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            FingerprintManager fm =
                    (FingerprintManager) c.getSystemService(Context.FINGERPRINT_SERVICE);
            if (fm != null && fm.isHardwareDetected() && fm.hasEnrolledFingerprints()) {
                return true;
            }
        }
        return false;
    }

    /** 指纹验证结果回调。 */
    public interface Result {
        void onSuccess();

        void onError(int code, CharSequence msg);

        void onUnavailable();
    }

    /** 弹出系统指纹框，触发手机内置指纹传感器。 */
    public static void prompt(Activity act, String title, String subtitle, Result cb) {
        FingerprintManager fm =
                (FingerprintManager) act.getSystemService(Context.FINGERPRINT_SERVICE);
        if (fm == null) {
            cb.onUnavailable();
            return;
        }
        CancellationSignal cancel = new CancellationSignal();
        FingerprintManager.AuthenticationCallback fc =
                new FingerprintManager.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(FingerprintManager.AuthenticationResult result) {
                        cb.onSuccess();
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        cb.onError(0, "指纹识别失败，请重试");
                    }

                    @Override
                    public void onAuthenticationError(int code, CharSequence errString) {
                        cb.onError(code, errString);
                    }
                };
        try {
            fm.authenticate(null, cancel, 0, fc, null);
        } catch (Throwable t) {
            cb.onUnavailable();
        }
    }
}
