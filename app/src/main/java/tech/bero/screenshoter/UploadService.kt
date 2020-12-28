package tech.bero.screenshoter

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener
import com.amazonaws.mobileconnectors.s3.transferutility.TransferObserver
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility
import java.io.File


class UploadService : Service() {

    private var transferUtility: TransferUtility? = null

    override fun onCreate() {
        super.onCreate()
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        val (first, second) = NotificationUtils.getNotification(this)
        startForeground(first, second)
        val key = intent.getStringExtra(INTENT_KEY_NAME)
        val file: File? = intent.getSerializableExtra(INTENT_FILE) as File?
        val transferObserver: TransferObserver?

        transferUtility = AwsClient.getTransferUtility(this)

        transferObserver = transferUtility!!.upload(key, file)

        transferObserver.setTransferListener(UploadListener())

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private class UploadListener : TransferListener {
        private val TAG: String = UploadService::class.java.getSimpleName()

        // Simply updates the list when notified.
        override fun onError(id: Int, e: Exception) {
            Log.e(TAG, "onError: $id", e)
        }

        override fun onProgressChanged(id: Int, bytesCurrent: Long, bytesTotal: Long) {
            Log.d(
                TAG, String.format(
                    "onProgressChanged: %d, total: %d, current: %d",
                    id, bytesTotal, bytesCurrent
                )
            )

        }

        override fun onStateChanged(id: Int, state: TransferState) {
            Log.d(TAG, "onStateChanged: $id, $state")

        }
    }

    companion object {
        const val INTENT_KEY_NAME = "key"
        const val INTENT_FILE = "file"
    }
}