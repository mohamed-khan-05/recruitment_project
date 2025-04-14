package com.example.recruitment

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.recruitment.databinding.ActivityLoginBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var loadingDialog: AlertDialog

    private fun showLoadingDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setView(R.layout.layout_loading_dialog)
        builder.setCancelable(false)
        loadingDialog = builder.create()
        loadingDialog.show()
    }

    private fun hideLoadingDialog() {
        if (::loadingDialog.isInitialized && loadingDialog.isShowing) {
            loadingDialog.dismiss()
        }
    }

    override fun onStart() {
        super.onStart()
        val currentUser = auth.currentUser
        if (currentUser != null) {
            showLoadingDialog()
            checkUserTypeAndRedirect()
        }
    }

    private fun checkUserTypeAndRedirect() {
        val user = auth.currentUser
        user?.let {
            val db = FirebaseFirestore.getInstance()
            db.collection("users").document(user.uid).get()
                .addOnSuccessListener { document ->
                    hideLoadingDialog()
                    if (document.exists()) {
                        val userType = document.getString("userType")
                        val intent =
                            if (userType?.trim()?.equals("Employer", ignoreCase = true) == true) {
                                Intent(this, EmployerActivity::class.java)
                            } else {
                                Intent(this, MainActivity::class.java)
                            }
                        startActivity(intent)
                        finish()
                    } else {
                        Toast.makeText(this, "User data not found.", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { e ->
                    hideLoadingDialog()
                    Toast.makeText(
                        this,
                        "Failed to retrieve user data: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        binding.textRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
        auth = FirebaseAuth.getInstance()
        binding.buttonLogin.setOnClickListener {
            val email = binding.editTextEmail.text.toString().trim()
            val password = binding.editTextPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.d("LoginActivity", "signInWithEmail:success")
                        val user = auth.currentUser
                        if (user != null) {
                            val db = FirebaseFirestore.getInstance()
                            db.collection("users").document(user.uid).get()
                                .addOnSuccessListener { document ->
                                    if (document.exists()) {
                                        val userType = document.getString("userType")
                                        if (userType?.trim()
                                                .equals("Employer", ignoreCase = true)
                                        ) {
                                            startActivity(
                                                Intent(
                                                    this,
                                                    EmployerActivity::class.java
                                                )
                                            )
                                        } else {
                                            Log.d(
                                                "LoginActivity",
                                                "Redirecting to MainActivity as userType = '$userType'"
                                            )
                                            startActivity(Intent(this, MainActivity::class.java))
                                        }
                                        finish()
                                    } else {
                                        Toast.makeText(
                                            this,
                                            "User data not found.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                                .addOnFailureListener { e ->
                                    Toast.makeText(
                                        this,
                                        "Failed to retrieve user data: ${e.message}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                        }
                    } else {
                        Log.w("LoginActivity", "signInWithEmail:failure", task.exception)
                        Toast.makeText(
                            this,
                            "Authentication failed.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
        }
    }
}
