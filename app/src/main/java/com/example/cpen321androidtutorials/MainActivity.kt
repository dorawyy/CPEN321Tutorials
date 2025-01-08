package com.example.cpen321androidtutorials

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat.checkSelfPermission
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.PasswordCredential
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.GetCredentialException
import com.example.cpen321androidtutorials.BuildConfig.WEB_CLIENT_ID
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.util.UUID

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "MainActivity"
    }

    private val activityScope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        findViewById<Button>(R.id.location_permission_button).setOnClickListener {
            Log.d(TAG, "Location permission button is clicked")
            Toast.makeText(this, "Location permission button is clicked", Toast.LENGTH_SHORT).show()

            checkLocationPermissions()
        }

        findViewById<Button>(R.id.google_maps_button).setOnClickListener {
            val intent = Intent(this, MapsActivity::class.java)
            startActivity(intent)
        }

        findViewById<Button>(R.id.signin_button).setOnClickListener {
            Log.d(TAG, "Sign in button is clicked")
            Log.d(TAG, "Web client id: ${WEB_CLIENT_ID}")

            // instantiate a google sign in request
            val credentialManager = CredentialManager.create(this)
            val googleIdOption: GetSignInWithGoogleOption = GetSignInWithGoogleOption.Builder(WEB_CLIENT_ID)
                .setNonce(generateHashedNonce())
                .build()

            // Instantiate a GetCredentialRequest, then add the previously created googleIdOption using addCredentialOption() to retrieve the credentials.
            val request: GetCredentialRequest = GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build()

            //Pass this request to getCredential() (Kotlin) or getCredentialAsync() (Java) call to retrieve the user's available credentials.
            activityScope.launch {
                try {
                    val result = credentialManager.getCredential(
                        request = request,
                        context = this@MainActivity,
                    )
                    handleSignIn(result)
                } catch (e: GetCredentialException) {
                    handleFailure(e)
                }
            }

        }

        findViewById<Button>(R.id.signout_button).setOnClickListener {
            Log.d(TAG, "Sign out button is clicked")

            val credentialManager = CredentialManager.create(this)
            activityScope.launch {
                try {
                    credentialManager.clearCredentialState(ClearCredentialStateRequest())
                    Toast.makeText(this@MainActivity, "Sign out successful", Toast.LENGTH_SHORT).show()
                    updateWelcomeMessage("")
                } catch (e:Exception) {
                    Log.e(TAG, "Sign out failed", e)
                    Toast.makeText(this@MainActivity, "Sign out failed", Toast.LENGTH_SHORT).show()
                }
            }
        }

    }

    private fun handleSignIn(result: GetCredentialResponse) {
        // Handle the successfully returned credential.
        val credential = result.credential

        when (credential) {
            // Passkey credential
            is PublicKeyCredential -> {
                // Share responseJson such as a GetCredentialResponse on your server to
                // validate and authenticate
                val responseJson = credential.authenticationResponseJson
            }

            // Password credential
            is PasswordCredential -> {
                // Send ID and password to your server to validate and authenticate.
                val username = credential.id
                val password = credential.password
            }

            // GoogleIdToken credential
            is CustomCredential -> {
                if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    try {
                        // Use googleIdTokenCredential and extract the ID to validate and
                        // authenticate on your server.
                        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                        Log.d(TAG, "Received Google ID Token : ${googleIdTokenCredential.idToken.take(10)}")
                        updateWelcomeMessage(googleIdTokenCredential.displayName.toString())
                    } catch (e: GoogleIdTokenParsingException) {
                        Log.e(TAG, "Received an invalid google id token response", e)
                    }
                } else {
                    // Catch any unrecognized custom credential type here.
                    Log.e(TAG, "Unexpected type of credential")
                }
            }

            else -> {
                // Catch any unrecognized credential type here.
                Log.e(TAG, "Unexpected type of credential")
            }
        }
    }

    private fun updateWelcomeMessage(name: String) {
        val welcomeTextView = findViewById<TextView>(R.id.welcome_textview)
        if (name.isEmpty()) {
            welcomeTextView.text = "Hello, CPEN321!"
        } else {
            welcomeTextView.text = "Hello, $name!"
        }
    }

    private fun generateHashedNonce(): String {
        val rawNonce = UUID.randomUUID().toString()
        val bytes = rawNonce.toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.fold("") {str, it -> str + "%02x".format(it)}
    }


    private fun handleFailure(e: GetCredentialException) {
        Log.e(TAG, "Sign in failed", e)
        Toast.makeText(this, "Sign in failed", Toast.LENGTH_SHORT).show()
    }


    private fun checkLocationPermissions() {
        when {
            // check if permissions are granted
            checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED && checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED -> {
                // proceed with whatever action we wanted
                Log.d(TAG, "Location permissions are already granted")
            }

            // if users already see our permission request and denied it
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION) || shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                Log.d(TAG, "Show request permission rationale")
                showLocationPermissionRationale()
            }
            else -> {
                Log.d(TAG, "Requesting location permissions")
                requestLocationPermissions()
            }
        }
    }

    private fun requestLocationPermissions() {
        requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 1)
    }

    private fun showLocationPermissionRationale() {
        AlertDialog.Builder(this).setMessage("Location permission is required to show your location")
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
                requestLocationPermissions()
            }.setNegativeButton("Cancel") {dialog, _ ->
                dialog.dismiss()
                Toast.makeText(this, "Please grant the location permissions", Toast.LENGTH_SHORT).show()
            }.show()
    }

    override fun onDestroy() {
        super.onDestroy()
        activityScope.cancel() // cancel all coroutines when the activity is destroyed
    }
}