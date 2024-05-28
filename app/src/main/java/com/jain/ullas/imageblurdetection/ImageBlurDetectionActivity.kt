package com.jain.ullas.imageblurdetection

import ImageResult
import ImageResultAdapter
import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
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

class ImageBlurDetectionActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val BLUR_THRESHOLD = 200.0
    }

    private lateinit var binding: ActivityImageBlurDetectionBinding
    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())

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
            selectPhotos()
        }
    }

    private fun selectPhotos() {
        // Request permission to read external storage
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 1)
        } else {
            // Launch the gallery to pick multiple images
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
            galleryActivityResultLauncher.launch(intent)
        }
    }


    private val galleryActivityResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val data = result.data
                val clipData = data!!.clipData
                val uris = mutableListOf<Uri>()

                if (clipData != null) {
                    // Handle multiple images selected
                    for (i in 0 until clipData.itemCount) {
                        uris.add(clipData.getItemAt(i).uri)
                        if (uris.size == 50) break  // Limit to 50 images
                    }
                } else {
                    // Handle single image selected
                    data.data?.let { uris.add(it) }
                }

                if (uris.size > 50) {
                    Toast.makeText(this, "You can select up to 50 images.", Toast.LENGTH_SHORT).show()
                    return@registerForActivityResult
                }

                // Process the selected images
                processImages(uris)
            }
        }

    private fun processImages(uris: List<Uri>) {
        coroutineScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            val results = withContext(Dispatchers.IO) {
                uris.map { uri ->
                    async {
                        val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
                        val score = getSharpnessScoreFromOpenCV(bitmap)
                        val isBlurred = score < BLUR_THRESHOLD
                        val fileName = getFileNameFromUri(uri)
                        ImageResult(fileName, isBlurred, BLUR_THRESHOLD, score, bitmap)
                    }
                }.awaitAll()
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
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.cancel()
    }
}
