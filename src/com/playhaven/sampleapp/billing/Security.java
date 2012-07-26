// Copyright 2010 Google Inc. All Rights Reserved.

package com.playhaven.sampleapp.billing;


import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.text.TextUtils;
import android.util.Log;

import android.util.Base64;
import com.playhaven.sampleapp.billing.Consts.PurchaseState;

import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.HashSet;

/**
 * Security-related methods. For a secure implementation, all of this code
 * should be implemented on a server that communicates with the
 * application on the device. For the sake of simplicity and clarity of this
 * example, this code is included here and is executed on the device. If you
 * must verify the purchases on the phone, you should obfuscate this code to
 * make it harder for an attacker to replace the code with stubs that treat all
 * purchases as verified.
 */
public class Security {
    private static final String TAG = "Security";

    private static final String KEY_FACTORY_ALGORITHM = "RSA";
    private static final String SIGNATURE_ALGORITHM = "SHA1withRSA";
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * This keeps track of the nonces that we generated and sent to the
     * server.  We need to keep track of these until we get back the purchase
     * state and send a confirmation message back to Android Market. If we are
     * killed and lose this list of nonces, it is not fatal. Android Market will
     * send us a new "notify" message and we will re-generate a new nonce.
     * This has to be "static" so that the {@link BillingReceiver} can
     * check if a nonce exists.
     */
    private static HashSet<Long> sKnownNonces = new HashSet<Long>();

    /**
     * A class to hold the verified purchase information.
     */
    public static class VerifiedPurchase {
        public PurchaseState purchaseState;
        public String notificationId;
        public String productId;
        public String orderId;
        public long purchaseTime;
        public String developerPayload;

        public VerifiedPurchase(PurchaseState purchaseState, String notificationId,
                String productId, String orderId, long purchaseTime, String developerPayload) {
            this.purchaseState = purchaseState;
            this.notificationId = notificationId;
            this.productId = productId;
            this.orderId = orderId;
            this.purchaseTime = purchaseTime;
            this.developerPayload = developerPayload;
        }
    }

    /** Generates a nonce (a random number used once). */
    public static long generateNonce() {
        long nonce = RANDOM.nextLong();
        sKnownNonces.add(nonce);
        return nonce;
    }

    public static void removeNonce(long nonce) {
        sKnownNonces.remove(nonce);
    }

    public static boolean isNonceKnown(long nonce) {
        return sKnownNonces.contains(nonce);
    }

    /**
     * Verifies that the data was signed with the given signature, and returns
     * the list of verified purchases. The data is in JSON format and contains
     * a nonce (number used once) that we generated and that was signed
     * (as part of the whole data string) with a private key. The data also
     * contains the {@link PurchaseState} and product ID of the purchase.
     * In the general case, there can be an array of purchase transactions
     * because there may be delays in processing the purchase on the backend
     * and then several purchases can be batched together.
     * @param signedData the signed JSON string (signed, not encrypted)
     * @param signature the signature for the data, signed with the private key
     */
    public static ArrayList<VerifiedPurchase> verifyPurchase(String signedData, String signature) {
        if (signedData == null) {
            Log.e(TAG, "data is null");
            return null;
        }
        if (Consts.DEBUG) {
            Log.i(TAG, "signedData: " + signedData);
        }
        boolean verified = false;
        if (!TextUtils.isEmpty(signature)) {
            /**
             * Compute your public key (that you got from the Android Market publisher site).
             *
             * Instead of just storing the entire literal string here embedded in the
             * program,  construct the key at runtime from pieces or
             * use bit manipulation (for example, XOR with some other string) to hide
             * the actual key.  The key itself is not secret information, but we don't
             * want to make it easy for an adversary to replace the public key with one
             * of their own and then fake messages from the server.
             *
             * Generally, encryption keys / passwords should only be kept in memory
             * long enough to perform the operation they need to perform.
             */
            String base64EncodedPublicKey = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAj4d6s1uq7G068thJNzCoTKSJOCIZ" +
						            		"1MAr4czazeXMDSJoR4vaIvQlAQBNCHV5h/HwYkPLXNAM5EXX4CuXYzaTS/GQ7W0omfu" +
						            		"QwLUN0bpH3Toa3O66dhNCAz7TACKREvn2rczIWZrtHJRe+9FcPPX0f49YCb/v9" +
						            		"F5QZz+Fi4+YQq9HzgWM99Xxv3R6Fr0TFDynzXWfjSmQjBGBCul7iWE33c7pylvGwkl9fLJKh5WY" +
						            		"Ecywtv2xYE36MataGJ88WSQ437mMcWzfZofe0sXFDvbiIy6hqB8lzdaj7J84pcSUjzeSo3kHCb2" +
						            		"Q3pjrxHRIxM2zmTTacKv875qu4PEbaKDybwIDAQAB";
            
            PublicKey key = Security.generatePublicKey(base64EncodedPublicKey);
            verified = Security.verify(key, signedData, signature);
            if (!verified) {
                Log.w(TAG, "signature does not match data.");
                return null;
            }
        }

        JSONObject jObject;
        JSONArray jTransactionsArray = null;
        int numTransactions = 0;
        long nonce = 0L;
        try {
            jObject = new JSONObject(signedData);

            // The nonce might be null if the user backed out of the buy page.
            nonce = jObject.optLong("nonce");
            jTransactionsArray = jObject.optJSONArray("orders");
            if (jTransactionsArray != null) {
                numTransactions = jTransactionsArray.length();
            }
        } catch (JSONException e) {
            return null;
        }

        if (!Security.isNonceKnown(nonce)) {
            Log.w(TAG, "Nonce not found: " + nonce);
            return null;
        }

        ArrayList<VerifiedPurchase> purchases = new ArrayList<VerifiedPurchase>();
        try {
            for (int i = 0; i < numTransactions; i++) {
                JSONObject jElement = jTransactionsArray.getJSONObject(i);
                int response = jElement.getInt("purchaseState");
                PurchaseState purchaseState = PurchaseState.valueOf(response);
                String productId = jElement.getString("productId");
                String packageName = jElement.getString("packageName");
                long purchaseTime = jElement.getLong("purchaseTime");
                String orderId = jElement.optString("orderId", "");
                String notifyId = null;
                if (jElement.has("notificationId")) {
                    notifyId = jElement.getString("notificationId");
                }
                String developerPayload = jElement.optString("developerPayload", null);

                // If the purchase state is PURCHASED, then we require a
                // verified nonce.
                if (purchaseState == PurchaseState.PURCHASED && !verified) {
                    continue;
                }
                purchases.add(new VerifiedPurchase(purchaseState, notifyId, productId,
                        orderId, purchaseTime, developerPayload));
            }
        } catch (JSONException e) {
            Log.e(TAG, "JSON exception: ", e);
            return null;
        }
        removeNonce(nonce);
        return purchases;
    }

    /**
     * Generates a PublicKey instance from a string containing the
     * Base64-encoded public key.
     *
     * @param encodedPublicKey Base64-encoded public key
     * @throws IllegalArgumentException if encodedPublicKey is invalid
     */
    public static PublicKey generatePublicKey(String encodedPublicKey) {
        try {
        	byte[] pubKeyBytes = encodedPublicKey.getBytes("UTF8");
            byte[] decodedKey = Base64.decode(pubKeyBytes, Base64.NO_PADDING);
            KeyFactory keyFactory = KeyFactory.getInstance(KEY_FACTORY_ALGORITHM);
            return keyFactory.generatePublic(new X509EncodedKeySpec(decodedKey));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        } catch (InvalidKeySpecException e) {
            Log.e(TAG, "Invalid key specification.");
            throw new IllegalArgumentException(e);
        } catch (UnsupportedEncodingException e) {
			Log.e(TAG, "Could not decode Base64 data");
			e.printStackTrace();
		} 
        
        return null;
    }

    /**
     * Verifies that the signature from the server matches the computed
     * signature on the data.  Returns true if the data is correctly signed.
     *
     * @param publicKey public key associated with the developer account
     * @param signedData signed data from server
     * @param signature server signature
     * @return true if the data and signature match
     */
    public static boolean verify(PublicKey publicKey, String signedData, String signature) {
        if (Consts.DEBUG) {
            Log.i(TAG, "signature: " + signature);
        }
        Signature sig;
        try {
            sig = Signature.getInstance(SIGNATURE_ALGORITHM);
            sig.initVerify(publicKey);
            sig.update(signedData.getBytes());
            
            byte[] sigBytes = signature.getBytes("UTF8");
            if (!sig.verify(Base64.decode(sigBytes, Base64.NO_PADDING))) {
                Log.e(TAG, "Signature verification failed.");
                return false;
            }
            return true;
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "NoSuchAlgorithmException.");
        } catch (InvalidKeyException e) {
            Log.e(TAG, "Invalid key specification.");
        } catch (SignatureException e) {
            Log.e(TAG, "Signature exception.");
        } catch (UnsupportedEncodingException e) {
			Log.e(TAG, "Could not encode to Base64");
			e.printStackTrace();
		}
        return false;
    }
}
