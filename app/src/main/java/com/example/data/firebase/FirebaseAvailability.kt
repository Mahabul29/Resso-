package com.example.data.firebase

import com.google.firebase.auth.FirebaseAuth

/**
 * Returns FirebaseAuth, or null when Firebase is not configured
 * (for example when google-services.json was not added to the build).
 *
 * Calling FirebaseAuth.getInstance() without a configured Firebase app throws an
 * IllegalStateException, which would crash the app on launch. Use this instead so the
 * app keeps working (music, likes, history, local comments) without Firebase.
 */
fun firebaseAuthOrNull(): FirebaseAuth? = try {
    FirebaseAuth.getInstance()
} catch (_: Exception) {
    null
}
