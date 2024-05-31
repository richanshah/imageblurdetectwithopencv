package com.jain.ullas.imageblurdetection

import ImageResult
import ImageResultAdapter
import android.Manifest
import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.jain.ullas.imageblurdetection.databinding.ActivityImageBlurDetectionBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.imgproc.Imgproc
import java.text.DecimalFormat


class ComposeImageBlurDetectionActivity : AppCompatActivity() {

    companion object {
        private val TAG = ComposeImageBlurDetectionActivity::class.java.simpleName
        private const val BLUR_THRESHOLD = 200.0
        private const val REQUEST_PERMISSION_CODE = 123
    }

    private lateinit var binding: ActivityImageBlurDetectionBinding
    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImageBlurDetectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize OpenCV
        if (!OpenCVLoader.initDebug()) {
            Log.e(TAG, "OpenCV initialization failed")
        }

        // Set up RecyclerView
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.setHasFixedSize(true)


        // Set up the button to select photos
        binding.selectButton.setOnClickListener {
            if (isPermissionGrantedForStorage()) {
                CoroutineScope(Dispatchers.IO).launch {
                    /// If os is greater then 13 then get all images
                    fetchBlurredImages()
                }
            }

        }

        /// Delete Photos
        binding.deleteButton.setOnClickListener {
            if (isPermissionGranted()) {
                CoroutineScope(Dispatchers.IO).launch {
                    fetchBlurredImagesAndDeleteAll()
                }
            }

        }

    }

    private fun isPermissionGranted(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                // Request permission from the user
                val intent: Intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.setData(Uri.parse("package:$packageName"))
                startActivity(intent)
            } else {
                return true
            }
        }
        return true
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun isPermissionGrantedForStorage(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if ((ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_MEDIA_IMAGES
                ) != PackageManager.PERMISSION_GRANTED)
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(
                        Manifest.permission.READ_MEDIA_IMAGES,
                    ),
                    REQUEST_PERMISSION_CODE
                )
            } else {
                return true
            }
        } else {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ),
                    REQUEST_PERMISSION_CODE
                )
            } else {
                return true
            }

        }
        return true
    }


    private val deleteImageLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                Toast.makeText(this, "Image deleted successfully", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Image deletion failed", Toast.LENGTH_SHORT).show()
            }
        }

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun deleteBlurredImagesFromStorage(blurredImageUris: List<Uri>) {
        val chunkSize = 100 // Adjust the chunk size as needed
        blurredImageUris.chunked(chunkSize).forEach { chunk ->
            withContext(Dispatchers.IO) {
                chunk.forEach { uri ->
                    try {
                        contentResolver.delete(uri, null, null)
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                            // For devices running Android 9 (Pie) or lower
                            sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri))
                        }
                    } catch (e: RecoverableSecurityException) {
                        Log.e(TAG, "RecoverableSecurityException", e)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            val intentSender = e.userAction.actionIntent.intentSender
                            withContext(Dispatchers.Main) {
                                deleteImageLauncher.launch(
                                    IntentSenderRequest.Builder(intentSender).build()
                                )
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error deleting image", e)
                    }
                }
            }
        }
    }


    @RequiresApi(Build.VERSION_CODES.R)
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                CoroutineScope(Dispatchers.IO).launch {
                    fetchBlurredImagesAndDeleteAll()
                }
            } else {
                // Handle permission denial
                Toast.makeText(this, "Permission denied rr ", Toast.LENGTH_SHORT).show()
            }
        }
    }


    private suspend fun fetchBlurredImages() {
        val imageUris = getImagesFromStorage()

        withContext(Dispatchers.IO) {
            processImages(imageUris)
        }

    }

    /// Fetch Blurred Images
    // Delete all blurred images from storage
    // Show success message
    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun fetchBlurredImagesAndDeleteAll() {
        withContext(Dispatchers.Main) {
            binding.progressBar.visibility = android.view.View.VISIBLE
        }

        val imageUris = getImagesFromStorage()
        val blurredImageUris = mutableListOf<Uri>()

        withContext(Dispatchers.IO) {
            imageUris.forEach { uri ->
                if (isImageBlurred(contentResolver, uri)) {
                    blurredImageUris.add(uri)
                }
            }
        }

        withContext(Dispatchers.Main) {
            binding.progressBar.visibility = android.view.View.GONE

            if (blurredImageUris.isEmpty()) {
                Toast.makeText(
                    this@ComposeImageBlurDetectionActivity,
                    "No blurred images found",
                    Toast.LENGTH_SHORT
                ).show()
                return@withContext
            }

            AlertDialog.Builder(this@ComposeImageBlurDetectionActivity)
                .setTitle("Delete Blurred Images")
                .setMessage("Are you sure you want to delete all blurred images?")
                .setPositiveButton("Yes") { _, _ ->
                    CoroutineScope(Dispatchers.IO).launch {
                        deleteBlurredImagesFromStorage(blurredImageUris)
                    }
                }
                .setNegativeButton("No", null)
                .show()
        }
    }


    private suspend fun getImagesFromStorage(): List<Uri> {
        val imageUris = mutableListOf<Uri>()
        withContext(Dispatchers.IO) {
            val projection = arrayOf(MediaStore.Images.Media._ID)
            val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
            val query = contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )

            query?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val contentUri =
                        ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    imageUris.add(contentUri)
                }
            }
        }
        return imageUris
    }


    private fun processImages(uris: List<Uri>) {
        val chunkSize = 30 // Adjust the chunk size as needed
        coroutineScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            val results = mutableListOf<ImageResult>()

            uris.chunked(chunkSize).forEach { chunk ->
                val chunkResults = withContext(Dispatchers.IO) {
                    chunk.map { uri ->
                        async {
                            val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
                            val score = getSharpnessScoreFromOpenCV(bitmap)
                            val isBlurred = score < BLUR_THRESHOLD
                            val fileName = getFileNameFromUri(uri)
                            ImageResult(fileName, isBlurred, BLUR_THRESHOLD, score, bitmap)
                        }
                    }.awaitAll()
                }
                results.addAll(chunkResults)
            }

            binding.progressBar.visibility = View.GONE
            displayResults(results)
        }
    }

    private fun getFileNameFromUri(uri: Uri): String {
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val columnIndex = it.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                if (columnIndex != -1) {
                    return it.getString(columnIndex)
                }
            }
        }
        return "Unknown"
    }

    private fun isImageBlurred(contentResolver: ContentResolver, uri: Uri): Boolean {
        val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
        val score = getSharpnessScoreFromOpenCV(bitmap)
        return score < BLUR_THRESHOLD
    }

    private fun getSharpnessScoreFromOpenCV(bitmap: Bitmap): Double {
        val mat = Mat()
        val matGray = Mat()
        Utils.bitmapToMat(bitmap, mat)
        Imgproc.cvtColor(mat, matGray, Imgproc.COLOR_BGR2GRAY)
        Imgproc.Laplacian(matGray, mat, 3)
        val median = MatOfDouble()
        val std = MatOfDouble()
        Core.meanStdDev(mat, median, std)
        return DecimalFormat("0.00").format(Math.pow(std.get(0, 0)[0], 2.0)).toDouble()
    }

    private fun displayResults(results: List<ImageResult>) {
        val adapter = ImageResultAdapter(results)
        binding.recyclerView.adapter = adapter
        binding.txtViewTime.text = "Total Images: ${results.size}"
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.cancel()
    }
}
