package com.blaze.andrecorddemo.record

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.util.Size
import android.util.SparseIntArray
import android.view.Surface
import android.view.SurfaceHolder
import android.view.TextureView
import android.widget.Toast
import com.blaze.andrecorddemo.R
import kotlinx.android.synthetic.main.activity_custom_record.*
import java.io.File
import java.io.IOException
import java.util.*
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

class CustomRecordActivity : AppCompatActivity() {
    private val SENSOR_ORIENTATION_DEFAULT_DEGREES = 90
    private val SENSOR_ORIENTATION_INVERSE_DEGREES = 270
    private val DEFAULT_ORIENTATIONS = SparseIntArray().apply {
        append(Surface.ROTATION_0, 90)
        append(Surface.ROTATION_90, 0)
        append(Surface.ROTATION_180, 270)
        append(Surface.ROTATION_270, 180)
    }
    private val INVERSE_ORIENTATIONS = SparseIntArray().apply {
        append(Surface.ROTATION_0, 270)
        append(Surface.ROTATION_90, 180)
        append(Surface.ROTATION_180, 90)
        append(Surface.ROTATION_270, 0)
    }
    /**
     * A [Semaphore] to prevent the app from exiting before closing the camera.
     */
    private val cameraOpenCloseLock = Semaphore(1)
    private var captureSession:CameraCaptureSession? = null
    private lateinit var mSurfaceHolder: SurfaceHolder
    private var cameraDevice:CameraDevice? = null
    private var mMediaRecorder: MediaRecorder? = null

    /**
     * The [android.util.Size] of camera preview.
     */
    private lateinit var previewSize: Size

    /**
     * The [android.util.Size] of video recording.
     */
    private lateinit var videoSize: Size
    private lateinit var previewRequestBuilder: CaptureRequest.Builder
    private var backgroundHandler: Handler? = null
    private var backgroundThread:HandlerThread? = null
    private var isRecordingVideo = false
    private var videoLocalPath:String? = null
    /**
     * Orientation of the camera sensor
     */
    private var sensorOrientation = 0

    private val stateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(cameraDevice: CameraDevice?) {
            cameraOpenCloseLock.release()
            this@CustomRecordActivity.cameraDevice = cameraDevice
            startPreview()
            configureTransform(record_textureView.width, record_textureView.height)
        }

        override fun onDisconnected(cameraDevice: CameraDevice?) {
            cameraOpenCloseLock.release()
            cameraDevice?.close()
            this@CustomRecordActivity.cameraDevice = null
        }

        override fun onError(cameraDevice: CameraDevice?, error: Int) {
            cameraOpenCloseLock.release()
            cameraDevice?.close()
            this@CustomRecordActivity.cameraDevice = null
        }

    }

    private val surfaceTextureListener = object :TextureView.SurfaceTextureListener{
        override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture?, width: Int, height: Int) {
            configureTransform(width,height)
        }

        override fun onSurfaceTextureUpdated(texture: SurfaceTexture?) {

        }

        override fun onSurfaceTextureDestroyed(texture: SurfaceTexture?): Boolean = true

        override fun onSurfaceTextureAvailable(texture: SurfaceTexture?, width: Int, height: Int) {
            openCamera(width, height)
        }

    }
    /**
     * Configures the necessary [android.graphics.Matrix] transformation to `textureView`.
     * This method should not to be called until the camera preview size is determined in
     * openCamera, or until the size of `textureView` is fixed.
     * 横屏时需要的方法
     * @param viewWidth  The width of `textureView`
     * @param viewHeight The height of `textureView`
     */
    private fun configureTransform(width: Int, height: Int) {
        val rotation = windowManager.defaultDisplay.rotation
        val matrix = Matrix()
        val viewRect = RectF(0f, 0f, width.toFloat(), height.toFloat())
        val bufferRect = RectF(0f,0f,previewSize.height.toFloat(),previewSize.width.toFloat())//the order of width and height???
        val centerX = viewRect.centerX()
        val centerY = viewRect.centerY()

        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
            val scale = Math.max(
                    height.toFloat() / previewSize.height,
                    width.toFloat() / previewSize.width)
            with(matrix) {
                postScale(scale, scale, centerX, centerY)
                postRotate((90 * (rotation - 2)).toFloat(), centerX, centerY)
            }
        }
        record_textureView.setTransform(matrix)
    }

    private fun startPreview() {
        if (cameraDevice == null || record_textureView.isAvailable.not()) return
        try {
            closePreviewSession()
            val texture = record_textureView.surfaceTexture
            texture.setDefaultBufferSize(previewSize.width, previewSize.height)
            previewRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            val previewSurface = Surface(texture)
            previewRequestBuilder.addTarget(previewSurface)

            cameraDevice?.createCaptureSession(listOf(previewSurface),
                    object :CameraCaptureSession.StateCallback(){
                        override fun onConfigureFailed(session: CameraCaptureSession?) {
                            Toast.makeText(this@CustomRecordActivity,"failed", Toast.LENGTH_LONG).show()
                        }

                        override fun onConfigured(session: CameraCaptureSession?) {
                            captureSession = session
                            updatePreview()
                        }

                    },backgroundHandler)
        }catch (exp: CameraAccessException){
            exp.printStackTrace()
        }
    }

    private fun closePreviewSession() {
        captureSession?.close()
        captureSession = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_custom_record)

        initEvent()
    }

    private fun initEvent() {
        record_pause.isEnabled = false
        //start or stop record
        record_control.setOnClickListener {
            if (isRecordingVideo) stopRecordingVideo() else startRecordingVideo()
        }
        //pause record
        record_pause.setOnClickListener {

        }
    }

    private fun startRecordingVideo() {
        if (cameraDevice == null || record_textureView.isAvailable.not()) return
        try {
            closePreviewSession()
            setUpMediaRecorder()
            val texture =  record_textureView.surfaceTexture.apply {
                setDefaultBufferSize(previewSize.width, previewSize.height)
            }

            val previewSurface = Surface(texture)
            val recorderSurface = mMediaRecorder!!.surface
            val surfaces = ArrayList<Surface>().apply {
                add(previewSurface)
                add(recorderSurface)
            }
            previewRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                addTarget(previewSurface)
                addTarget(recorderSurface)
            }

            cameraDevice?.createCaptureSession(surfaces,
                    object :CameraCaptureSession.StateCallback(){
                        override fun onConfigureFailed(session: CameraCaptureSession?) {
                            Toast.makeText(this@CustomRecordActivity,"failed", Toast.LENGTH_LONG).show()
                        }
                        override fun onConfigured(session: CameraCaptureSession?) {
                            captureSession = session
                            updatePreview()
                            runOnUiThread {
                                record_control.setImageResource(R.mipmap.recordvideo_stop)
                                isRecordingVideo = true
                                mMediaRecorder?.start()
                            }
                        }

                    },backgroundHandler)
        }catch (e: CameraAccessException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun updatePreview() {
        if (cameraDevice == null) return

        try {
            setUpCaptureRequestBuilder(previewRequestBuilder)
            HandlerThread("CameraPreview").start()
            captureSession?.setRepeatingRequest(previewRequestBuilder.build(),null,backgroundHandler)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun setUpCaptureRequestBuilder(previewRequestBuilder: CaptureRequest.Builder?) {
        previewRequestBuilder?.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
    }

    private fun setUpMediaRecorder() {
        if (videoLocalPath.isNullOrEmpty()) {
            videoLocalPath = getVideoFilePath()
        }

        val rotation = this.windowManager.defaultDisplay.rotation
        when (sensorOrientation) {
            SENSOR_ORIENTATION_DEFAULT_DEGREES -> {
                mMediaRecorder?.setOrientationHint(DEFAULT_ORIENTATIONS.get(rotation))
            }
            SENSOR_ORIENTATION_INVERSE_DEGREES -> {
                mMediaRecorder?.setOrientationHint(INVERSE_ORIENTATIONS.get(rotation))
            }
        }

        mMediaRecorder?.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setOutputFile(videoLocalPath)
            setVideoEncodingBitRate(10000000)
            setVideoFrameRate(30)
            setVideoSize(videoSize.width, videoSize.height)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            prepare()
        }
    }

    private fun getVideoFilePath(): String? {
        val fileName = "${System.currentTimeMillis()}.mp4"
        val dir = this.filesDir.path + File.separator + "galaxy"
        return dir + fileName
    }

    private fun stopRecordingVideo() {
        isRecordingVideo = false
        record_control.setImageResource(R.mipmap.recordvideo_start)
        mMediaRecorder?.apply {
            stop()
            reset()
        }

//        videoLocalPath = null
//        startPreview()
        val data = Intent()
        data.putExtra("path", videoLocalPath!!)
        setResult(Activity.RESULT_OK, data)
        finish()
    }

    override fun onResume() {
        super.onResume()
        startBackgroundThread()
        if (record_textureView.isAvailable) openCamera(record_textureView.width, record_textureView.height)
        else record_textureView.surfaceTextureListener = surfaceTextureListener
    }

    override fun onPause() {
        closeCamera()
        stopBackgroundThread()
        super.onPause()
    }

    private fun closeCamera() {
        try {
            cameraOpenCloseLock.acquire()
            closePreviewSession()
            cameraDevice?.close()
            cameraDevice = null
            mMediaRecorder?.release()
            mMediaRecorder = null
        } catch (ext: InterruptedException) {
            throw RuntimeException("interrupted while trying to lock camera closing.", ext)
        }finally {
            cameraOpenCloseLock.release()
        }
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()//把指定的线程加入到当前线程
            backgroundThread = null
            backgroundHandler = null
        } catch (exp: InterruptedException) {
            exp.printStackTrace()
        }
    }

    @SuppressLint("MissingPermission")
    private fun openCamera(width: Int, height: Int) {
        val manager = this.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            if (cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS).not()) {
                throw RuntimeException("time out waiting to lock camera opening.")
            }
            val cameraId = manager.cameraIdList[0]
            val characteristics = manager.getCameraCharacteristics(cameraId)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    ?: throw RuntimeException("Cannot get available preview/video sizes")
            sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)
            videoSize = chooseVideoSize(map.getOutputSizes(MediaRecorder::class.java))
            previewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture::class.java), width, height, videoSize)

            if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                record_textureView.setAspectRatio(previewSize.width, previewSize.height)
            }else{
                record_textureView.setAspectRatio(previewSize.height, previewSize.width)
            }
            configureTransform(width, height)
            mMediaRecorder = MediaRecorder()
            manager.openCamera(cameraId, stateCallback, null)

        } catch (e: CameraAccessException) {
            e.printStackTrace()
        } catch (e: NullPointerException) {
            e.printStackTrace()
        } catch (e: InterruptedException) {
            throw RuntimeException("Interrupted while trying to lock camera opening.")
        }
    }

    private fun chooseOptimalSize(outputSizes: Array<Size>, width: Int, height: Int, aspectRatio: Size): Size {
        val w = aspectRatio.width
        val h = aspectRatio.height
        val bigEnough = outputSizes.filter {
            it.height == it.width * h/w && it.width >= width && it.height >= height
        }
        return if (bigEnough.isNotEmpty()) {
            Collections.min(bigEnough, CompareSizesByArea())
        }else outputSizes[0]
    }

    /**
     * width: height =4:3, width <= 1080
     */
    private fun chooseVideoSize(outputSizes: Array<Size>): Size = outputSizes.firstOrNull {
        it.width == it.height * 4 / 3 && it.width <= 1080
    } ?: outputSizes[outputSizes.size - 1]

    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground")
        backgroundThread?.start()
        backgroundHandler = Handler(backgroundThread?.looper)
    }
}
