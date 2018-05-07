package jp.yama07.tensorflowkotlin.service

import android.content.res.AssetManager
import android.graphics.Bitmap
import org.tensorflow.contrib.android.TensorFlowInferenceInterface
import timber.log.Timber
import java.util.*

class TensorFlowImageClassifier private constructor(
    private var inputName: String,
    private var outputName: String,
    private var inputSize: Int,
    private var imageMean: Int,
    private var imageStd: Float) : Classifier {

  companion object {
    private const val MAX_RESULTS = 3
    private const val THRESHOLD = 0.1f

    fun create(assetManager: AssetManager,
               modelFilename: String,
               labelFilename: String,
               inputSize: Int,
               imageMean: Int,
               imageStd: Float,
               inputName: String,
               outputName: String
    ): Classifier {
      val classifier = TensorFlowImageClassifier(
          inputName = inputName,
          outputName = outputName,
          inputSize = inputSize,
          imageMean = imageMean,
          imageStd = imageStd)

      val actualFilename = labelFilename.split("file:///android_asset/")[1]
      Timber.d("Reading labels from: $actualFilename")

      classifier.also {
        it.labels = assetManager.open(actualFilename).bufferedReader().useLines { it.toList() }
        it.inferenceInterface = TensorFlowInferenceInterface(assetManager, modelFilename)
      }

      val operation = classifier.inferenceInterface.graphOperation(outputName)
      val numClasses: Int = operation.output<Float>(0).shape().size(1).toInt()
      Timber.d("Read ${classifier.labels.size} labels. output layer size is $numClasses")

      classifier.also {
        it.intValues = IntArray(inputSize * inputSize)
        it.floatValues = FloatArray(inputSize * inputSize * 3)
        it.outputs = FloatArray(numClasses)
        it.outputNames = Array(1, { outputName })
      }

      return classifier
    }
  }

  private lateinit var labels: List<String>
  private lateinit var inferenceInterface: TensorFlowInferenceInterface
  private lateinit var intValues: IntArray
  private lateinit var floatValues: FloatArray
  private lateinit var outputs: FloatArray
  private lateinit var outputNames: Array<String>

  private var logStats = false

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
    inferenceInterface.run(outputNames, logStats)

    // Copy the output Tensor back into the output array
    inferenceInterface.fetch(outputName, outputs)

    // Find the best classifications
    val pq = PriorityQueue<Classifier.Recognition>(3, Comparator { lhs, rhs -> compareValues(lhs.confidence, rhs.confidence) })
    outputs.withIndex().filter { it.value > THRESHOLD }.forEach {
      pq.add(Classifier.Recognition(
          "" + it.index, labels.getOrElse(it.index, { "unknown" }), it.value, null))
    }

    val recognitions = ArrayList<Classifier.Recognition>()
    for (i in 0 until Math.min(pq.size, MAX_RESULTS)) {
      recognitions.add(pq.poll())
    }

    return recognitions
  }

  override fun enableStatLogging(debug: Boolean) {
    logStats = debug
  }

  override fun getStatString(): String {
    return inferenceInterface.statString
  }

  override fun close() {
    inferenceInterface.close()
  }
}