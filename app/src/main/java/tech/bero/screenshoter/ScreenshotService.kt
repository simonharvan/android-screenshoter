package tech.bero.screenshoter

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.text.format.DateFormat
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import android.view.OrientationEventListener
import android.view.WindowManager
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.lang.Math.abs
import java.nio.ByteBuffer
import java.util.*


class ScreenshotService : Service() {

    private var lastTimestamp: Long = -1
    private var mMediaProjection: MediaProjection? = null
    private var mStoreDir: String? = null
    private var mImageReader: ImageReader? = null
    private var mHandler: Handler? = null
    private var mDisplay: Display? = null
    private var mVirtualDisplay: VirtualDisplay? = null
    private var mDensity = 0
    private var mWidth = 0
    private var mHeight = 0
    private var mRotation = 0
    private var mDeviceName: String? = ""
    private var mOrientationChangeCallback: OrientationChangeCallback? = null

    private inner class ImageAvailableListener : ImageReader.OnImageAvailableListener {
        override fun onImageAvailable(reader: ImageReader?) {
            val timestamp = System.currentTimeMillis()
            var image: Image? = null
            if (lastTimestamp > 0 && abs(timestamp - lastTimestamp) < TIME_GAP) {
                image = mImageReader?.acquireLatestImage()
                image?.close()
                return
            }

            lastTimestamp = timestamp

            var fos: FileOutputStream? = null
            var bitmap: Bitmap? = null
            try {
                image = mImageReader?.acquireLatestImage()
                if (image != null) {
                    val planes: Array<Image.Plane> = image.getPlanes()
                    val buffer: ByteBuffer = planes[0].getBuffer()
                    val pixelStride: Int = planes[0].getPixelStride()
                    val rowStride: Int = planes[0].getRowStride()
                    val rowPadding = rowStride - pixelStride * mWidth

                    // create bitmap
                    bitmap = Bitmap.createBitmap(
                        mWidth + rowPadding / pixelStride,
                        mHeight,
                        Bitmap.Config.ARGB_8888
                    )
                    bitmap.copyPixelsFromBuffer(buffer)

                    // write bitmap to a file
                    var fileName = mDeviceName + "_" + DateFormat.format("yyyy-MM-dd_HH:mm:ss", Date()) + ".png"
                    fileName = fileName.toLowerCase(Locale.ROOT).replace(" ", "_")
                    fos = FileOutputStream(mStoreDir + "/" + fileName);
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);

                    uploadInBackground(fileName, File(mStoreDir + "/" + fileName))

                    IMAGES_PRODUCED++
                    Log.e(TAG, "captured image: " + IMAGES_PRODUCED)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                if (fos != null) {
                    try {
                        fos.close()
                    } catch (ioe: IOException) {
                        ioe.printStackTrace()
                    }
                }
                bitmap?.recycle()
                if (image != null) {
                    image.close()
                }
            }
        }
    }

    private fun uploadInBackground(name: String, file: File) {

        val intent = Intent(applicationContext, UploadService::class.java)
        intent.putExtra(UploadService.INTENT_KEY_NAME, name)
        intent.putExtra(UploadService.INTENT_FILE, file)
        startService(intent)
    }

    private inner class OrientationChangeCallback internal constructor(context: Context?) :
        OrientationEventListener(context) {
        override fun onOrientationChanged(orientation: Int) {
            val rotation = mDisplay!!.rotation
            if (rotation != mRotation) {
                mRotation = rotation
                try {
                    // clean up
                    if (mVirtualDisplay != null) mVirtualDisplay!!.release()
                    if (mImageReader != null) mImageReader?.setOnImageAvailableListener(null, null)

                    // re-create virtual display depending on device width / height
                    createVirtualDisplay()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private inner class MediaProjectionStopCallback : MediaProjection.Callback() {
        override fun onStop() {
            Log.e(TAG, "stopping projection.")
            mHandler?.post(Runnable {
                mVirtualDisplay?.release()
                mImageReader?.setOnImageAvailableListener(null, null)
                mOrientationChangeCallback?.disable()
                mMediaProjection?.unregisterCallback(this@MediaProjectionStopCallback)
            })
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        // create store dir
        val externalFilesDir: File? = getExternalFilesDir(null)
        if (externalFilesDir != null) {
            mStoreDir = externalFilesDir.getAbsolutePath().toString() + "/screenshots/"
            val storeDirectory = File(mStoreDir)
            if (!storeDirectory.exists()) {
                val success: Boolean = storeDirectory.mkdirs()
                if (!success) {
                    Log.e(TAG, "failed to create file storage directory.")
                    stopSelf()
                }
            }
        } else {
            Log.e(TAG, "failed to create file storage directory, getExternalFilesDir is null.")
            stopSelf()
        }

        // start capture handling thread
        object : Thread() {
            override fun run() {
                Looper.prepare()
                mHandler = Handler()
                Looper.loop()
            }
        }.start()
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        if (isStartCommand(intent)) {
            mDeviceName = intent.getStringExtra(DEVICE_NAME)
            // create notification
            val (first, second) = NotificationUtils.getNotification(this)
            startForeground(first, second)
            // start projection
            val resultCode = intent.getIntExtra(RESULT_CODE, Activity.RESULT_CANCELED)
            val data = intent.getParcelableExtra<Intent>(DATA)
            startProjection(resultCode, data)
        } else if (isStopCommand(intent)) {
            stopProjection()
            stopSelf()
        } else {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startProjection(resultCode: Int, data: Intent?) {
        val mpManager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager?
        if (mMediaProjection == null) {
            mMediaProjection = mpManager!!.getMediaProjection(resultCode, data!!)
            if (mMediaProjection != null) {
                // display metrics
                val metrics: DisplayMetrics = getResources().getDisplayMetrics()
                mDensity = metrics.densityDpi
                val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager?
                mDisplay = windowManager!!.defaultDisplay

                // create virtual display depending on device width / height
                createVirtualDisplay()

                // register orientation change callback
                mOrientationChangeCallback = OrientationChangeCallback(this)
                if (mOrientationChangeCallback!!.canDetectOrientation()) {
                    mOrientationChangeCallback!!.enable()
                }

                // register media projection stop callback
                mMediaProjection!!.registerCallback(MediaProjectionStopCallback(), mHandler)
            }
        }
    }

    private fun stopProjection() {
        mHandler?.post(Runnable {
            if (mMediaProjection != null) {
                mMediaProjection!!.stop()
            }
        })

    }

    override fun onStart(intent: Intent?, startId: Int) {
        super.onStart(intent, startId)
        RUNNING = true
    }

    override fun onDestroy() {
        super.onDestroy()
        RUNNING = false
    }

    @SuppressLint("WrongConstant")
    private fun createVirtualDisplay() {
        // get width and height
        val size = Point()
        mDisplay!!.getSize(size)
        mWidth = size.x
        mHeight = size.y

        // start capture reader
        mImageReader = ImageReader.newInstance(mWidth, mHeight, PixelFormat.RGBA_8888, 2)
        mVirtualDisplay = mMediaProjection!!.createVirtualDisplay(
            SCREENCAP_NAME, mWidth, mHeight,
            mDensity, virtualDisplayFlags, mImageReader?.getSurface(), null, mHandler
        )

        mImageReader?.setOnImageAvailableListener(ImageAvailableListener(), mHandler)
    }



    companion object {
        const val TIME_GAP = 3000
        var RUNNING = false
        private const val TAG = "ScreenCaptureService"
        private const val RESULT_CODE = "RESULT_CODE"
        private const val DATA = "DATA"
        private const val DEVICE_NAME = "DEVICE_NAME"
        private const val ACTION = "ACTION"
        private const val START = "START"
        private const val STOP = "STOP"
        private const val SCREENCAP_NAME = "screencap"

        private const val TIME_OF_LAST_SCREENSHOT = "screenshot_time"

        private var IMAGES_PRODUCED = 0
        fun getStartIntent(context: Context?, resultCode: Int, data: Intent?, deviceName: String?): Intent {
            val intent = Intent(context, ScreenshotService::class.java)
            intent.putExtra(ACTION, START)
            intent.putExtra(RESULT_CODE, resultCode)
            intent.putExtra(DATA, data)
            intent.putExtra(DEVICE_NAME, deviceName)
            return intent
        }

        fun getStopIntent(context: Context?): Intent {
            val intent = Intent(context, ScreenshotService::class.java)
            intent.putExtra(ACTION, STOP)
            return intent
        }

        private fun isStartCommand(intent: Intent): Boolean {
            return (intent.hasExtra(RESULT_CODE) && intent.hasExtra(DATA)
                    && intent.hasExtra(ACTION) && intent.getStringExtra(ACTION) == START)
        }

        private fun isStopCommand(intent: Intent): Boolean {
            return intent.hasExtra(ACTION) && intent.getStringExtra(ACTION) == STOP
        }

        private val virtualDisplayFlags: Int
            private get() = DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
    }
}