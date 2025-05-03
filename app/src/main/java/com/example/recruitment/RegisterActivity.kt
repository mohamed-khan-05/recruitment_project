package com.example.recruitment

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Locale

class RegisterActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_register)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.registerLayout)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(16, systemBars.top, 16, systemBars.bottom)
            insets
        }
        auth = FirebaseAuth.getInstance()
        val fullNameField = findViewById<EditText>(R.id.editTextFullName)
        val emailField = findViewById<EditText>(R.id.editTextEmail)
        val passwordField = findViewById<EditText>(R.id.editTextPassword)
        val radioGroupUserType = findViewById<RadioGroup>(R.id.radioGroupUserType)
        val registerButton = findViewById<Button>(R.id.buttonRegister)

        registerButton.setOnClickListener {
            val fullName = fullNameField.text.toString().trim()
            val email = emailField.text.toString().trim().lowercase(Locale.getDefault())
            val password = passwordField.text.toString().trim()

            if (fullName.isEmpty() || email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val selectedRadioButtonId = radioGroupUserType.checkedRadioButtonId
            if (selectedRadioButtonId == -1) {
                Toast.makeText(this, "Please select a user type", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val selectedRadioButton = findViewById<RadioButton>(selectedRadioButtonId)
            val userType = selectedRadioButton.text.toString()
            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val user = auth.currentUser
                        val db = FirebaseFirestore.getInstance()

                        val userMap = hashMapOf(
                            "fullName" to fullName,
                            "email" to email,
                            "userType" to userType
                        )
                        user?.let {
                            db.collection("users")
                                .document(it.uid)
                                .set(userMap)
                                .addOnSuccessListener {
                                    Toast.makeText(
                                        this,
                                        "User registered and profile created!",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    startActivity(Intent(this, LoginActivity::class.java))
                                    finish()
                                }
                                .addOnFailureListener { e ->
                                    Toast.makeText(
                                        this,
                                        "Failed to save user: ${e.message}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                        }
                    } else {
                        Toast.makeText(
                            this,
                            "Registration failed: ${task.exception?.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
        }
    }
}
