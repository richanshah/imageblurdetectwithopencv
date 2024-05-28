import android.graphics.Bitmap

data class ImageResult(
    val fileName: String,
    val isBlurred: Boolean,
    val threshold: Double,
    val score: Double,
    val bitmap: Bitmap
)
