package com.example.recruitment.ui.home

import android.app.Activity
import android.util.Log
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import com.itextpdf.kernel.pdf.canvas.parser.listener.SimpleTextExtractionStrategy
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.recruitment.R
import com.example.recruitment.databinding.FragmentHomeBinding
import com.google.android.gms.auth.api.signin.*
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import android.Manifest
import android.content.pm.PackageManager
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private var currentFileUri: Uri? = null
    private lateinit var database: FirebaseDatabase
    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private var currentUserId: String? = null
    private var currentCvText: String = ""
    private var pendingJobTitle: String? = null
    private var pendingCvText: String? = null
    private var pendingFileUri: Uri? = null
    private var currentFileName: String? = null
    private val REQUEST_CODE_PERMISSIONS = 1234
    private var signInRetryCount = 0
    private val MAX_RETRY_COUNT = 3
    private var shouldRetrySignIn = false

    private val filePicker =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { originalUri ->
                try {
                    currentFileUri = copyUriToTempFile(originalUri)
                    currentFileName = getFileName(originalUri)
                    showPreview(currentFileUri!!)
                    currentCvText = extractTextFromPdf(currentFileUri!!).toString()
                    val jobTitle =
                        binding.etJobTitle.text.toString().takeIf { it.isNotBlank() } ?: return@let
                    if (!jobTitle.isNullOrBlank()) {
                        pendingCvText = currentCvText
                        pendingJobTitle = jobTitle
                    }
                } catch (e: Exception) {
                    Toast.makeText(
                        context,
                        "Error processing file: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

    private suspend fun extractKeywordsWithNlp(text: String): List<String> =
        withContext(Dispatchers.IO) {
            try {
                val url = "https://api.textrazor.com"
                val requestBody = FormBody.Builder()
                    .add("text", text)
                    .add("extractors", "entities,topics")
                    .build()

                val request = Request.Builder()
                    .url(url)
                    .addHeader(
                        "x-textrazor-key",
                        "0460e2960deb10a3c369095a89616e34f85eadffe12513501e2a8bd9"
                    )
                    .post(requestBody)
                    .build()

                val client = OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .writeTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful || response.body == null) return@withContext emptyList()
                val json = JSONObject(response.body!!.string())
                val keywords = mutableSetOf<String>()
                val entityArray = json.getJSONObject("response").optJSONArray("entities")
                for (i in 0 until (entityArray?.length() ?: 0)) {
                    val entity = entityArray!!.getJSONObject(i)
                    val entityName = entity.optString("entityId")
                    if (!entityName.isNullOrBlank()) {
                        keywords.add(entityName)
                    }
                }
                val topicArray = json.getJSONObject("response").optJSONArray("topics")
                for (i in 0 until (topicArray?.length() ?: 0)) {
                    val topic = topicArray!!.getJSONObject(i).optString("label")
                    if (!topic.isNullOrBlank()) {
                        keywords.add(topic)
                    }
                }
                return@withContext keywords.shuffled().take(5)
            } catch (e: Exception) {
                Log.e("TextRazor", "Keyword extraction failed: ${e.message}")
                return@withContext emptyList()
            }
        }

    private fun saveKeywordsWithNlp(cvText: String, jobTitle: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        lifecycleScope.launch {
            val extracted = extractKeywordsWithNlp(cvText).toMutableList()
            if (!extracted.contains(jobTitle)) {
                extracted.add(0, jobTitle)
            }
            val keywordData = mapOf(
                "keywords" to extracted,
                "jobTitle" to jobTitle,
                "timestamp" to FieldValue.serverTimestamp()
            )
            FirebaseFirestore.getInstance().collection("users")
                .document(userId)
                .collection("keywords")
                .document("fromCv")
                .set(keywordData)
                .addOnSuccessListener {
                    Log.d("Keywords", "Saved keywords to Firestore")
                }
                .addOnFailureListener {
                    Log.e("Keywords", "Failed to save keywords", it)
                }
        }
    }


    private fun getFileName(uri: Uri): String {
        var name = "cv.pdf"
        context?.contentResolver?.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) {
                name = cursor.getString(nameIndex)
            }
        }
        return name
    }

    private val googleSignInLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                try {
                    val account = task.getResult(ApiException::class.java)
                    pendingFileUri?.let { uri ->
                        pendingJobTitle?.let { jobTitle ->
                            uploadCvToDrive(account, uri, jobTitle)
                            pendingFileUri = null
                            pendingJobTitle = null
                        }
                    }
                    enableCvUi()
                } catch (e: ApiException) {
                    Toast.makeText(
                        context,
                        "Google sign-in failed: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                signInRetryCount++
                if (signInRetryCount < MAX_RETRY_COUNT) {
                    shouldRetrySignIn = true
                } else {
                    Toast.makeText(
                        context,
                        "Sign-in attempts exceeded. Please try again later.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    private fun enableCvUi() {
        val account = GoogleSignIn.getLastSignedInAccount(requireContext())

        if (account == null || !account.grantedScopes.contains(Scope(DriveScopes.DRIVE_FILE))) {
            Log.w("DriveScope", "DRIVE_FILE scope NOT granted. Requesting sign-in again.")
            googleSignInLauncher.launch(googleSignInClient.signInIntent)
            return
        }
        binding.btnUpload.isEnabled = true
        binding.btnSave.isEnabled = true
        checkDriveFileExists()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            if (ActivityCompat.shouldShowRequestPermissionRationale(
                    requireActivity(),
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) ||
                ActivityCompat.shouldShowRequestPermissionRationale(
                    requireActivity(),
                    Manifest.permission.POST_NOTIFICATIONS
                )
            ) {
                AlertDialog.Builder(requireContext())
                    .setMessage("We need access to your storage and notifications to allow you to upload and save your CV.")
                    .setPositiveButton("OK") { _, _ ->
                        requestPermissions(
                            arrayOf(
                                Manifest.permission.READ_EXTERNAL_STORAGE,
                                Manifest.permission.POST_NOTIFICATIONS
                            ),
                            REQUEST_CODE_PERMISSIONS
                        )
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            } else {
                requestPermissions(
                    arrayOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.POST_NOTIFICATIONS
                    ),
                    REQUEST_CODE_PERMISSIONS
                )
            }
        }

        database = FirebaseDatabase.getInstance()
        auth = FirebaseAuth.getInstance()
        currentUserId = auth.currentUser?.uid
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_FILE))
            .build()
        googleSignInClient = GoogleSignIn.getClient(requireActivity(), gso)
        if (currentUserId == null) {
            Toast.makeText(context, "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }
        val account = GoogleSignIn.getLastSignedInAccount(requireContext())
        if (account == null) {
            binding.btnUpload.isEnabled = false
            binding.btnSave.isEnabled = false
            if (!shouldRetrySignIn) {
                googleSignInLauncher.launch(googleSignInClient.signInIntent)
            }
        } else {
            checkExistingCv()
            enableCvUi()
        }
        binding.btnUpload.setOnClickListener {
            filePicker.launch("application/pdf")
        }
        binding.btnSave.setOnClickListener {
            val jobTitle = binding.etJobTitle.text.toString()
            if (jobTitle.isBlank()) {
                binding.tilJobTitle.error = "Job title is required"
                return@setOnClickListener
            }
            currentFileUri?.let { uri ->
                pendingFileUri = uri
                pendingJobTitle = jobTitle
                val account = GoogleSignIn.getLastSignedInAccount(requireContext())
                if (account != null) {
                    uploadCvToDrive(account, uri, jobTitle)
                } else {
                    binding.btnUpload.isEnabled = false
                    binding.btnSave.isEnabled = false
                    if (!shouldRetrySignIn) {
                        googleSignInLauncher.launch(googleSignInClient.signInIntent)
                    }
                }
            } ?: Toast.makeText(context, "Please upload a CV first", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkExistingCv() {
        database.reference.child("users/$currentUserId/cv").get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                val jobTitle = snapshot.child("jobTitle").getValue(String::class.java)
                val cvTitle = snapshot.child("CVTitle").getValue(String::class.java)
                binding.etJobTitle.setText(jobTitle ?: "")
                binding.tvFileName.text = cvTitle ?: "No CV uploaded"
                val fileId = snapshot.child("fileId").getValue(String::class.java)
                if (!fileId.isNullOrBlank()) {
                    checkDriveFileExists()
                }
            }
        }.addOnFailureListener {
            Toast.makeText(context, "Failed to load CV data", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkDriveFileExists() {
        val account = GoogleSignIn.getLastSignedInAccount(requireContext()) ?: return
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val credential = GoogleAccountCredential.usingOAuth2(
                    requireContext(), listOf(DriveScopes.DRIVE_FILE)
                ).apply {
                    selectedAccount = account.account
                }
                val drive = Drive.Builder(
                    NetHttpTransport(),
                    GsonFactory.getDefaultInstance(),
                    credential
                ).setApplicationName("Recruitment App").build()
                val snapshot = withContext(Dispatchers.IO) {
                    Firebase.firestore.collection("users")
                        .document(userId)
                        .collection("uploadedCVs")
                        .limit(1)
                        .get()
                        .await()
                }
                if (snapshot.isEmpty) return@launch
                val document = snapshot.documents[0]
                val fileName = document.getString("fileName") ?: return@launch
                val jobTitle = document.getString("jobTitle") ?: ""
                if (isAdded) binding.etJobTitle.setText(jobTitle)
                else return@launch

                val query = "name = '${fileName.replace("'", "\\'")}' and trashed = false"
                val result = withContext(Dispatchers.IO) {
                    drive.files().list()
                        .setQ(query)
                        .setSpaces("drive")
                        .setFields("files(id, name)")
                        .execute()
                }
                val file = result.files.firstOrNull()
                if (file == null) {
                    context?.let {
                        Toast.makeText(
                            it,
                            "CV file not found in Drive",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    return@launch
                }
                val outputFile =
                    File.createTempFile("cv_preview", ".pdf", requireContext().cacheDir)
                withContext(Dispatchers.IO) {
                    drive.files().get(file.id)
                        .executeMediaAndDownloadTo(FileOutputStream(outputFile))
                }
                val uri = FileProvider.getUriForFile(
                    requireContext(),
                    "${requireContext().packageName}.fileprovider",
                    outputFile
                )
                if (isAdded) {
                    currentFileUri = uri
                    currentFileName = file.name
                    showPreview(uri)
                }

            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("DriveCheck", "Error loading CV", e)
                context?.let {
                    Toast.makeText(it, "Failed to load CV from Drive", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun copyUriToTempFile(uri: Uri): Uri {
        val inputStream = requireContext().contentResolver.openInputStream(uri)
        val tempFile = File.createTempFile("temp_cv", ".pdf", requireContext().cacheDir).apply {
            createNewFile()
        }
        FileOutputStream(tempFile).use { output ->
            inputStream?.copyTo(output)
        }
        inputStream?.close()
        return FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.fileprovider",
            tempFile
        )
    }

    private fun showPreview(uri: Uri) {
        binding.pdfView.visibility = View.VISIBLE
        binding.btnSave.visibility = View.VISIBLE
        binding.tvFileName.text = currentFileName ?: "cv.pdf"
        binding.buttonViewMyApplications.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_viewMyApplicationsFragment)
        }
        try {
            context?.grantUriPermission(
                context?.packageName,
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            binding.pdfView.fromUri(uri)
                .enableSwipe(true)
                .swipeHorizontal(false)
                .enableDoubletap(true)
                .defaultPage(0)
                .enableAnnotationRendering(false)
                .load()
        } catch (e: Exception) {
            Toast.makeText(context, "Error loading PDF: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun uploadCvToDrive(account: GoogleSignInAccount, uri: Uri, jobTitle: String) {
        showLoading(true)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val credential = GoogleAccountCredential.usingOAuth2(
                    requireContext(),
                    listOf(DriveScopes.DRIVE_FILE)
                ).apply {
                    selectedAccount = account.account
                }
                val drive = Drive.Builder(
                    NetHttpTransport(),
                    GsonFactory.getDefaultInstance(),
                    credential
                )
                    .setApplicationName("Recruitment App")
                    .build()

                val db = Firebase.firestore
                val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return@launch
                val cvDocRef = db.collection("users")
                    .document(userId)
                    .collection("uploadedCVs")
                    .document("currentCv")
                val existingDoc = cvDocRef.get().await()
                existingDoc.getString("driveFileId")?.let { oldFileId ->
                    if (oldFileId.isNotEmpty()) {
                        try {
                            drive.files().delete(oldFileId).execute()
                            Log.d("Drive", "Deleted old file: $oldFileId")
                        } catch (e: Exception) {
                            Log.e("Drive", "Error deleting old file: ${e.message}")
                        }
                    }
                }
                val inputStream = requireContext().contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    withContext(Dispatchers.Main) {
                        showLoading(false)
                        Toast.makeText(context, "Failed to open file", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }
                val fileName = "CV_${System.currentTimeMillis()}.pdf"
                val fileMetadata = com.google.api.services.drive.model.File().apply {
                    name = fileName
                    parents = listOf("root")
                }
                val content =
                    com.google.api.client.http.InputStreamContent("application/pdf", inputStream)
                val uploadedFile = withTimeout(30_000L) {
                    drive.files().create(fileMetadata, content)
                        .setFields("id, name")
                        .execute()
                }
                val newCvData = mapOf(
                    "jobTitle" to jobTitle,
                    "driveFileId" to uploadedFile.id,
                    "fileName" to uploadedFile.name,
                    "timestamp" to FieldValue.serverTimestamp()
                )
                cvDocRef.set(newCvData).await()
                pendingCvText?.let { saveKeywordsWithNlp(it, jobTitle) }
                withContext(Dispatchers.Main) {
                    showLoading(false)
                    Toast.makeText(
                        context,
                        "CV uploaded successfully with ID: ${uploadedFile.id}",
                        Toast.LENGTH_SHORT
                    ).show()
                    binding.btnSave.isEnabled = true
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showLoading(false)
                    Toast.makeText(
                        context,
                        "Error: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun extractTextFromPdf(uri: Uri): String? {
        return try {
            val parcelFileDescriptor = requireContext().contentResolver.openFileDescriptor(uri, "r")
            parcelFileDescriptor?.use { pfd ->
                FileInputStream(pfd.fileDescriptor).use { inputStream ->
                    val pdfReader = PdfReader(inputStream)
                    val pdfDocument = PdfDocument(pdfReader)
                    val text = StringBuilder()
                    for (i in 1..pdfDocument.numberOfPages) {
                        val page = pdfDocument.getPage(i)
                        val strategy = SimpleTextExtractionStrategy()
                        val pageText = PdfTextExtractor.getTextFromPage(page, strategy)
                        text.append(pageText)
                    }
                    pdfDocument.close()
                    text.toString()
                }
            }
        } catch (e: Exception) {
            Log.e("ExtractText", "Error reading PDF", e)
            null
        }
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.btnUpload.isEnabled = !show
        binding.btnSave.isEnabled = !show
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}