package tech.bero.screenshoter

import android.content.Context
import android.util.Log
import com.amazonaws.auth.AWSCredentials
import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.mobile.client.AWSMobileClient
import com.amazonaws.mobile.client.Callback
import com.amazonaws.mobile.client.UserStateDetails
import com.amazonaws.mobile.config.AWSConfiguration
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility
import com.amazonaws.regions.Region
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.AmazonS3Client
import org.json.JSONException
import java.util.concurrent.CountDownLatch


object AwsClient: AWSCredentials {

    private var sBucketName: String = ""
    private var sSecretKey: String = ""
    private var sAccessKey: String = ""
    private var sMobileClient: AWSMobileClient? = null
    private var sS3Client: AmazonS3Client? = null
    private var sTransferUtility: TransferUtility? = null

    private const val TAG = "AwsClient"

    /**
     * Gets an instance of a S3 client which is constructed using the given
     * Context.
     *
     * @param context Android context
     * @return A default S3 client.
     */
    fun getS3Client(): AmazonS3Client? {
        if (sS3Client == null) {
            try {

                val region: Region = Region.getRegion(Regions.EU_WEST_1)
                sS3Client = AmazonS3Client(this, region)
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }
        return sS3Client
    }

    /**
     * Gets an instance of the TransferUtility which is constructed using the
     * given Context
     *
     * @param context Android context
     * @return a TransferUtility instance
     */
    fun getTransferUtility(context: Context?): TransferUtility? {
        if (sTransferUtility == null) {
            sTransferUtility = TransferUtility.builder()
                .context(context)
                .s3Client(getS3Client())
                .defaultBucket(sBucketName)
                .build()
        }
        return sTransferUtility
    }

    fun initClient(context: Context?, accessKey: String, secretKey: String, bucketName: String) {
        sAccessKey = accessKey
        sSecretKey = secretKey
        sBucketName = bucketName
        getTransferUtility(context)
    }

    fun stopClient() {
        sTransferUtility = null
        sMobileClient = null
        sS3Client = null
    }

    override fun getAWSAccessKeyId(): String {
        return sAccessKey
    }

    override fun getAWSSecretKey(): String {
        return sSecretKey
    }


}