
package com.jobalertzng.app;

import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.HashMap;
import java.util.Map;

public class FirebaseTokenManager {

    private static final String TAG = "JA_FirebaseToken";

    public static void register() {

        FirebaseAuth auth = FirebaseAuth.getInstance();

        if (auth.getCurrentUser() != null) {
            saveToken();
            return;
        }

        auth.signInAnonymously()
                .addOnSuccessListener(result -> saveToken())
                .addOnFailureListener(error ->
                        Log.e(TAG, "Anonymous authentication failed", error)
                );
    }

    private static void saveToken() {

        FirebaseAuth auth = FirebaseAuth.getInstance();

        if (auth.getCurrentUser() == null) {
            return;
        }

        String userId = auth.getCurrentUser().getUid();

        FirebaseMessaging.getInstance().getToken()
                .addOnSuccessListener(token -> {

                    if (token == null || token.length() <= 50) {
                        Log.e(TAG, "Invalid Firebase token");
                        return;
                    }

                    Map<String, Object> subscriber = new HashMap<>();

                    subscriber.put("token", token);
                    subscriber.put("updatedAt", FieldValue.serverTimestamp());
                    subscriber.put("platform", "android");

                    FirebaseFirestore.getInstance()
                            .collection("subscribers")
                            .document(userId)
                            .set(subscriber, SetOptions.merge())
                            .addOnSuccessListener(unused ->
                                    Log.d(TAG, "Device registered for notifications")
                            )
                            .addOnFailureListener(error ->
                                    Log.e(TAG, "Token registration failed", error)
                            );
                })
                .addOnFailureListener(error ->
                        Log.e(TAG, "Unable to obtain FCM token", error)
                );
    }
}
