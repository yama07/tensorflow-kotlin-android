package jp.yama07.tensorflowkotlin.service

import android.content.res.AssetManager
import android.graphics.Bitmap
import org.tensorflow.contrib.android.TensorFlowInferenceInterface
import timber.log.Timber
import java.util.*

class TensorFlowImageClassifier private constructor(
    private val inferenceInterface: TensorFlowInferenceInterface,
    override val labels: List<String>,
    val inputName: String,
    val outputName: String,
    val inputSize: Int,
    val imageMean: Int,
    val imageStd: Float) : Classifier {

  override var isStatLoggingEnabled: Boolean = false
  override var maxResults: Int = labels.size
  override var threshold: Float = 0.0f

  private var intValues: IntArray
  private var floatValues: FloatArray
  private var outputs: FloatArray
  private var outputNames: Array<String>

  init {
    val numClasses: Int = inferenceInterface
        .graphOperation(outputName).output<Float>(0).shape().size(1).toInt()
    Timber.d("Read ${labels.size} labels. output layer size is $numClasses")

    intValues = IntArray(inputSize * inputSize)
    floatValues = FloatArray(inputSize * inputSize * 3)
    outputs = FloatArray(numClasses)
    outputNames = Array(1, { outputName })
  }

  companion object {
    fun create(assetManager: AssetManager,
               modelFilename: String,
               labelFilename: String,
               inputSize: Int,
               imageMean: Int,
               imageStd: Float,
               inputName: String,
               outputName: String): Classifier {
      // read labels
      val actualFilename = labelFilename.split("file:///android_asset/")[1]
      Timber.d("Reading labels from: $actualFilename")
      val labels = assetManager.open(actualFilename).bufferedReader().useLines { it.toList() }

      // create TensorFlow instance
      val inferenceInterface = TensorFlowInferenceInterface(assetManager, modelFilename)

      return TensorFlowImageClassifier(
          inferenceInterface = inferenceInterface,
          labels = labels,
          inputName = inputName,
          outputName = outputName,
          inputSize = inputSize,
          imageMean = imageMean,
          imageStd = imageStd)
    }
  }

  override fun recognizeImage(bitmap: Bitmap): List<Classifier.Recognition> {
    bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    for ((i, v) in intValues.withIndex()) {
      floatValues[i * 3 + 0] = (((v shr 16) and 0xFF) - imageMean) / imageStd
      floatValues[i * 3 + 1] = (((v shr 8) and 0xFF) - imageMean) / imageStd
      floatValues[i * 3 + 2] = ((v shr 0xFF) - imageMean) / imageStd
    }

    // Copy the input date into TensorFlow
    inferenceInterface.feed(inputName, floatValues, 1, inputSize.toLong(), inputSize.toLong(), 3)

    // Run the inference call
    inferenceInterface.run(outputNames, isStatLoggingEnabled)

    // Copy the output Tensor back into the output array
    inferenceInterface.fetch(outputName, outputs)

    // Find the best classifications
    val pq = PriorityQueue<Classifier.Recognition>(labels.size, Comparator { lhs, rhs -> compareValues(rhs.confidence, lhs.confidence) })
    outputs.withIndex().filter { threshold < it.value }.forEach {
      pq.add(Classifier.Recognition(
          it.index.toString(), labels.getOrElse(it.index, { "unknown" }), it.value, null))
    }

    val recognitions = ArrayList<Classifier.Recognition>()
    for (i in 0 until Math.min(pq.size, maxResults)) {
      recognitions.add(pq.poll())
    }

    return recognitions
  }

  override fun getStatString(): String {
    return inferenceInterface.statString
  }

  override fun close() {
    inferenceInterface.close()
  }
}