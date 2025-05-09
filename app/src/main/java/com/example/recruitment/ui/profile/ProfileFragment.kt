package com.example.recruitment.ui.profile

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.recruitment.LoginActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.example.recruitment.databinding.FragmentProfileBinding
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private lateinit var currentUser: FirebaseUser
    private lateinit var firestore: FirebaseFirestore

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        val root: View = binding.root
        val auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        currentUser = auth.currentUser!!
        binding.textEmail.text = currentUser.email
        fetchUserFullName()

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .build()
        val googleSignInClient = GoogleSignIn.getClient(requireActivity(), gso)

        binding.buttonLogout.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            googleSignInClient.revokeAccess().addOnCompleteListener {
                startActivity(Intent(activity, LoginActivity::class.java))
                activity?.finish()
            }
        }

        val submitButton: Button = binding.buttonSubmit
        submitButton.isEnabled = false
        val fullNameEditText: EditText = binding.editFullName
        val oldPasswordEditText: EditText = binding.editOldPassword
        val newPasswordEditText: EditText = binding.editNewPassword
        val confirmPasswordEditText: EditText = binding.editConfirmPassword

        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                submitButton.isEnabled = hasChanges()
            }
        }

        fullNameEditText.addTextChangedListener(textWatcher)
        oldPasswordEditText.addTextChangedListener(textWatcher)
        newPasswordEditText.addTextChangedListener(textWatcher)
        confirmPasswordEditText.addTextChangedListener(textWatcher)

        submitButton.setOnClickListener {
            val newFullName = fullNameEditText.text.toString()
            val oldPassword = oldPasswordEditText.text.toString()
            val newPassword = newPasswordEditText.text.toString()
            val confirmPassword = confirmPasswordEditText.text.toString()

            if (newFullName.isEmpty()) {
                Toast.makeText(context, "Full name cannot be empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (oldPassword.isNotEmpty() && newPassword.isNotEmpty() && confirmPassword.isNotEmpty()) {
                if (newPassword == confirmPassword) {
                    val credential =
                        EmailAuthProvider.getCredential(currentUser.email!!, oldPassword)
                    currentUser.reauthenticate(credential).addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            currentUser.updatePassword(newPassword)
                                .addOnCompleteListener { updateTask ->
                                    if (updateTask.isSuccessful) {
                                        Toast.makeText(
                                            context,
                                            "Password updated successfully!",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        oldPasswordEditText.text.clear()
                                        newPasswordEditText.text.clear()
                                        confirmPasswordEditText.text.clear()
                                    } else {
                                        Toast.makeText(
                                            context,
                                            "Error updating password: ${updateTask.exception?.message}",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                        } else {
                            Toast.makeText(
                                context,
                                "Re-authentication failed: ${task.exception?.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } else {
                    Toast.makeText(
                        context,
                        "New password and confirmation do not match",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                updateFullName(newFullName)
            }
        }

        return root
    }

    private fun fetchUserFullName() {
        val userEmail = currentUser.email ?: return
        firestore.collection("users")
            .whereEqualTo("email", userEmail)
            .get()
            .addOnSuccessListener { documents ->
                if (documents != null && !documents.isEmpty) {
                    val user = documents.documents[0]
                    val fullName = user.getString("fullName")
                    binding.editFullName.setText(fullName)
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Error fetching data: ${e.message}", Toast.LENGTH_SHORT)
                    .show()
            }
    }

    private fun updateFullName(newFullName: String) {
        val userUid = currentUser.uid
        val userRef = firestore.collection("users").document(userUid)
        userRef.update("fullName", newFullName)
            .addOnSuccessListener {
                Toast.makeText(context, "Full name updated successfully!", Toast.LENGTH_SHORT)
                    .show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(
                    context,
                    "Error updating full name: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun hasChanges(): Boolean {
        val fullName = binding.editFullName.text.toString()
        val oldPassword = binding.editOldPassword.text.toString()
        val newPassword = binding.editNewPassword.text.toString()
        val confirmPassword = binding.editConfirmPassword.text.toString()
        return fullName != currentUser.displayName ||
                oldPassword.isNotEmpty() ||
                newPassword.isNotEmpty() ||
                confirmPassword.isNotEmpty()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}