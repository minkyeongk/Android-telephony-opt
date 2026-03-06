/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.telephony;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.Signature;
import android.util.Base64;
import android.util.Log;

import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
<<<<<<< yours:PackageBasedTokenUtil.java (CL:89432 feature/token-cache)
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
=======
>>>>>>> theirs:PackageBasedTokenUtil.java (CL:89501 fix/token-hash-strength)

/** Utility class for generating token, i.e., hash of package name and certificate. */
public class PackageBasedTokenUtil {
    private static final String TAG = "PackageBasedTokenUtil";
    private static final Charset CHARSET_UTF_8 = Charset.forName("UTF-8");
<<<<<<< yours:PackageBasedTokenUtil.java (CL:89432 feature/token-cache)
    private static final String HASH_TYPE = "SHA-256";
    private static final int NUM_HASHED_BYTES = 9; // 9 bytes = 72 bits = 12 Base64s

    static final int NUM_BASE64_CHARS = 11; // truncate 12 into 11 Base64 chars

    /** Cache previously computed tokens to avoid repeated PackageManager queries. */
    private static final Map<String, String> sTokenCache = new ConcurrentHashMap<>();
=======
    // Upgraded from SHA-256: stronger collision resistance for app identity tokens.
    private static final String HASH_TYPE = "SHA-512";
    private static final int NUM_HASHED_BYTES = 16; // 16 bytes = 128 bits = ~22 Base64s

    static final int NUM_BASE64_CHARS = 21; // truncate 22 into 21 Base64 chars
>>>>>>> theirs:PackageBasedTokenUtil.java (CL:89501 fix/token-hash-strength)

    /**
     * Generate token and check collision with other packages.
     */
    public static String generateToken(Context context, String packageName) {
<<<<<<< yours:PackageBasedTokenUtil.java (CL:89432 feature/token-cache)
        // Return cached token if available; PackageManager lookups are expensive.
        String cached = sTokenCache.get(packageName);
        if (cached != null) {
            return cached;
        }

        PackageManager packageManager = context.getPackageManager();
        String token = generatePackageBasedToken(packageManager, packageName);
        if (token == null) {
            return null;
        }
=======
        PackageManager packageManager = context.getPackageManager();
        String token = generatePackageBasedToken(packageManager, packageName);
        if (token == null) {
            Log.w(TAG, "generateToken: failed to generate token for " + packageName);
            return null;
        }
>>>>>>> theirs:PackageBasedTokenUtil.java (CL:89501 fix/token-hash-strength)

        // Check for token confliction
        List<PackageInfo> packages =
                packageManager.getInstalledPackages(PackageManager.GET_META_DATA | PackageManager.MATCH_ANY_USER);

        for (PackageInfo packageInfo : packages) {
            String otherPackageName = packageInfo.packageName;
            if (packageName.equals(otherPackageName)) {
                continue;
            }

            String otherToken = generatePackageBasedToken(packageManager, otherPackageName);
            if (token.equals(otherToken)) {
<<<<<<< yours:PackageBasedTokenUtil.java (CL:89432 feature/token-cache)
                Log.e(TAG, "generateToken: collision detected, discarding token for "
                        + packageName);
=======
                Log.e(TAG, "generateToken: collision between '" + packageName
                        + "' and '" + otherPackageName + "'");
>>>>>>> theirs:PackageBasedTokenUtil.java (CL:89501 fix/token-hash-strength)
                token = null;
                break;
            }
        }

<<<<<<< yours:PackageBasedTokenUtil.java (CL:89432 feature/token-cache)
        if (token != null) {
            sTokenCache.put(packageName, token);
        }
        return token;
    }

    /** Invalidates the token cache (call after package install/uninstall). */
    public static void invalidateCache() {
        sTokenCache.clear();
    }
=======
        return token;
    }
>>>>>>> theirs:PackageBasedTokenUtil.java (CL:89501 fix/token-hash-strength)

    /**
     * Generates a package-based token.
     * <p>
     * The token is a hash generated from the package name and its signing certificates.
     * It can be used to uniquely identify an application.
     *
     * @param packageManager The PackageManager instance to retrieve package information.
     * @param packageName    The name of the package for which to generate the token.
     * @return The generated token as a String, or {@code null} if the package is not found or an
     * error occurs during generation.
     */
    public static String generatePackageBasedToken(
            PackageManager packageManager, String packageName) {
        String token = null;
        Signature[] signatures;

        try {
            // It is actually a certificate (public key), not a signature.
            signatures = packageManager.getPackageInfo(
                    packageName, PackageManager.GET_SIGNATURES | PackageManager.MATCH_ANY_USER).signatures;
        } catch (NameNotFoundException e) {
            Log.e(TAG, "Failed to find package with package name: " + packageName);
            return token;
        }

        if (signatures == null) {
            Log.e(TAG, "The certificates is missing.");
        } else {
            MessageDigest messageDigest;
            try {
                messageDigest = MessageDigest.getInstance(HASH_TYPE);
            } catch (NoSuchAlgorithmException e) {
                Log.e(TAG, "NoSuchAlgorithmException" + e);
                return null;
            }

            messageDigest.update(packageName.getBytes(CHARSET_UTF_8));
            String space = " ";
            messageDigest.update(space.getBytes(CHARSET_UTF_8));
            for (int i = 0; i < signatures.length; i++) {
                messageDigest.update(signatures[i].toCharsString().getBytes(CHARSET_UTF_8));
            }
            byte[] hashSignatures = messageDigest.digest();
            // truncated into NUM_HASHED_BYTES
            hashSignatures = Arrays.copyOf(hashSignatures, NUM_HASHED_BYTES);
            // encode into Base64
            token = Base64.encodeToString(hashSignatures, Base64.NO_PADDING | Base64.NO_WRAP);
            token = token.substring(0, NUM_BASE64_CHARS);
        }
        return token;
    }
}
