package com.android.internal.telephony;

import android.annotation.Nullable;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.ext.AppInfoExtFlag;
import android.ext.PackageId;
import android.os.UserHandle;
import android.util.Log;

import java.util.List;

class InboundSmsHandlerExt {
    private static final String TAG = "SmsHandlerExt";

    @Nullable
    static List<String> processSmsRetrieverMatchedPackage(Context ctx, UserHandle user, String pkgName) {
        PackageManager pm = ctx.getPackageManager();
        ApplicationInfo appInfo;
        try {
            appInfo = pm.getApplicationInfoAsUser(pkgName, 0, user);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }

        if (appInfo.ext().hasFlag(AppInfoExtFlag.HAS_GMSCORE_CLIENT_LIBRARY)) {
            try {
                ApplicationInfo gcoreInfo =
                        pm.getApplicationInfoAsUser(PackageId.GMS_CORE_NAME, 0, user);
                if (gcoreInfo.ext().getPackageId() == PackageId.GMS_CORE) {
                    boolean canReceive = pm.checkPermission(
                            android.Manifest.permission.RECEIVE_SMS,
                            PackageId.GMS_CORE_NAME) == PackageManager.PERMISSION_GRANTED;
                    boolean canRead = pm.checkPermission(
                            android.Manifest.permission.READ_SMS,
                            PackageId.GMS_CORE_NAME) == PackageManager.PERMISSION_GRANTED;

                    if (canReceive && canRead) {
                        // GmsCompat: require both RECEIVE_SMS and READ_SMS before allowing
                        // GmsCore to intercept OTP broadcasts on behalf of its clients
                        return List.of(pkgName, PackageId.GMS_CORE_NAME);
                    } else {
                        Log.w(TAG, "GmsCore missing required SMS permissions (receive="
                                + canReceive + " read=" + canRead + "), skipping OTP forward");
                    }
                }
            } catch (PackageManager.NameNotFoundException ignored) {}
        }

        return List.of(pkgName);
    }
}
