import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.jain.ullas.imageblurdetection.R

class ImageResultAdapter(private val imageResults: List<ImageResult>) :
    RecyclerView.Adapter<ImageResultAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.imageView)
        val fileNameTextView: TextView = view.findViewById(R.id.fileNameTextView)
        val statusTextView: TextView = view.findViewById(R.id.statusTextView)
        val thresholdTextView: TextView = view.findViewById(R.id.thresholdTextView)
        val scoreTextView: TextView = view.findViewById(R.id.scoreTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_image_result, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val imageResult = imageResults[position]
        holder.imageView.setImageBitmap(imageResult.bitmap)
        holder.fileNameTextView.text = imageResult.fileName
        holder.statusTextView.text = "Status: ${if (imageResult.isBlurred) "Blurred" else "Not Blurred"}"
        holder.thresholdTextView.text = "Threshold: ${imageResult.threshold}"
        holder.scoreTextView.text = "Score: ${imageResult.score}"
    }

    override fun getItemCount() = imageResults.size
}
