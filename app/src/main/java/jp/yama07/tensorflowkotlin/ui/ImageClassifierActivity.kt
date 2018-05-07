package jp.yama07.tensorflowkotlin.ui

import android.graphics.*
import android.os.SystemClock
import android.util.Size
import android.util.TypedValue
import jp.yama07.tensorflowkotlin.service.Classifier
import jp.yama07.tensorflowkotlin.service.TensorFlowImageClassifier
import jp.yama07.tensorflowkotlin.util.BorderedText
import jp.yama07.tensorflowkotlin.util.ImageUtils
import jp.yama07.tensorflowkotlin.view.OverlayView
import kotlinx.android.synthetic.main.fragment_camera_connection.*
import timber.log.Timber

class ImageClassifierActivity : CameraActivity() {
  companion object {
    var SAVE_PREVIEW_BITMAP = false
    const val INPUT_SIZE = 224
    const val IMAGE_MEAN = 117
    const val IMAGE_STD = 1f
    const val INPUT_NAME = "input"
    const val OUTPUT_NAME = "output"
    const val MODEL_FILE = "file:///android_asset/tensorflow_inception_graph.pb"
    const val LABEL_FILE = "file:///android_asset/imagenet_comp_graph_label_strings.txt"
    const val MAINTAIN_ASPECT = true
    val DESIRED_PREVIEW_SIZE = Size(640, 480)
    private const val TEXT_SIZE_DIP = 10f
  }

  private var rgbFrameBitmap: Bitmap? = null
  private var croppedBitmap: Bitmap? = null
  private var cropCopyBitmap: Bitmap? = null

  private var lastProcessingTimeMs: Long = 0L

  private var sensorOrientation: Int = 0

  private lateinit var classifier: Classifier
  private lateinit var frameToCropTransform: Matrix
  private lateinit var cropToFrameTransform: Matrix

  private lateinit var borderedText: BorderedText

  override fun getDesiredPreviewFrameSize(): Size = DESIRED_PREVIEW_SIZE

  override fun onPreviewSizeChosen(size: Size, rotation: Int) {
    val textSizePx = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, resources.displayMetrics)
    borderedText = BorderedText(textSizePx)
    borderedText.setTypeface(Typeface.MONOSPACE)

    classifier = TensorFlowImageClassifier.create(
        assetManager = assets,
        modelFilename = MODEL_FILE,
        labelFilename = LABEL_FILE,
        inputSize = INPUT_SIZE,
        imageMean = IMAGE_MEAN,
        imageStd = IMAGE_STD,
        inputName = INPUT_NAME,
        outputName = OUTPUT_NAME)
    previewWidth = size.width
    previewHeight = size.height

    sensorOrientation = rotation - getScreenOrientation()
    Timber.i("Camera orientation relative to screen canvas: $sensorOrientation")

    Timber.i("Initializing at size $previewWidth x $previewHeight")
    rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888)
    croppedBitmap = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888)

    frameToCropTransform = ImageUtils.getTransformationMatrix(
        previewWidth, previewHeight,
        INPUT_SIZE, INPUT_SIZE,
        sensorOrientation, MAINTAIN_ASPECT)

    cropToFrameTransform = Matrix()
    frameToCropTransform.invert(cropToFrameTransform)

    addCallback(object : OverlayView.DrawCallback {
      override fun drawCallback(canvas: Canvas) {
        renderDebug(canvas)
      }
    })
  }

  override fun processImage() {
    rgbFrameBitmap?.setPixels(getRgbBytes(), 0, previewWidth, 0, 0, previewWidth, previewHeight)
    val canvas = Canvas(croppedBitmap)
    canvas.drawBitmap(rgbFrameBitmap, frameToCropTransform, null)

    if (SAVE_PREVIEW_BITMAP) {
      ImageUtils.saveBitmap(croppedBitmap!!)
    }
    runInBackground(Runnable {
      val startTime = SystemClock.uptimeMillis()
      val results = classifier.recognizeImage(croppedBitmap!!)
      lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime
      Timber.i("Detect: $results")
      cropCopyBitmap = Bitmap.createBitmap(croppedBitmap)
      results_view.setResults(results)
      requestRender()
      readyForNextImage()
    })
  }

  override fun onSetDebug(debug: Boolean) {
    classifier.enableStatLogging(debug)
  }

  private fun renderDebug(canvas: Canvas) {
    if (!isDebug) {
      return
    }

    val copy = cropCopyBitmap ?: return

    val scaleFactor = 2f
    val matrix = Matrix().also {
      it.postScale(scaleFactor, scaleFactor)
      it.postTranslate(canvas.width - copy.width * scaleFactor, canvas.height - copy.height * scaleFactor)
    }
    canvas.drawBitmap(copy, matrix, Paint())

    val statLines = classifier.getStatString().split("\n")
    val infoLines = listOf(
        "Frame: ${previewWidth}x$previewHeight",
        "Crop: ${copy.width}x${copy.height}",
        "View: ${canvas.width}x${canvas.height}",
        "Rotation: $sensorOrientation",
        "Inference time: ${lastProcessingTimeMs}ms"
    )
    val lines = statLines + infoLines
    borderedText.drawLines(canvas, 20f, canvas.height - 20f, lines)
  }
}