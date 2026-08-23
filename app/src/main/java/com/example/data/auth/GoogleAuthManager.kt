package com.example.data.auth

import android.accounts.Account
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class GoogleUserInfo(
    val email: String,
    val displayName: String?,
    val photoUrl: Uri?,
    val account: Account? = null
)

class GoogleAuthManager(private val context: Context) {
    companion object {
        private const val TAG = "GoogleAuthManager"
        const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
        const val DRIVE_FULL_SCOPE = "https://www.googleapis.com/auth/drive"
        val SCOPES_STRING = "oauth2:$DRIVE_FILE_SCOPE $DRIVE_FULL_SCOPE"
    }

    private val gso: GoogleSignInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail()
        .requestProfile()
        .requestScopes(Scope(DRIVE_FILE_SCOPE), Scope(DRIVE_FULL_SCOPE))
        .build()

    val client: GoogleSignInClient = GoogleSignIn.getClient(context, gso)

    fun getSignInIntent(): Intent {
        return client.signInIntent
    }

    fun getCurrentAccount(): GoogleUserInfo? {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return null
        return GoogleUserInfo(
            email = account.email.orEmpty(),
            displayName = account.displayName ?: account.givenName ?: account.email,
            photoUrl = account.photoUrl,
            account = account.account
        )
    }

    suspend fun getAccessToken(accountEmail: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val account = Account(accountEmail, "com.google")
            val token = GoogleAuthUtil.getToken(context, account, SCOPES_STRING)
            if (!token.isNullOrBlank()) {
                Result.success(token)
            } else {
                Result.failure(Exception("Token vacío retornado por Google Play Services"))
            }
        } catch (e: UserRecoverableAuthException) {
            Log.e(TAG, "User recoverable auth exception: ${e.message}", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching token: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun clearToken(token: String) = withContext(Dispatchers.IO) {
        try {
            GoogleAuthUtil.clearToken(context, token)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clear token: ${e.message}")
        }
    }

    suspend fun signOut(): Unit = withContext(Dispatchers.IO) {
        try {
            client.signOut()
            client.revokeAccess()
        } catch (e: Exception) {
            Log.e(TAG, "Error signing out: ${e.message}", e)
        }
    }
}
