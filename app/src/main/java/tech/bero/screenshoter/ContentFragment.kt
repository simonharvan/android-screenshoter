package tech.bero.screenshoter

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings.Global.DEVICE_NAME
import android.view.LayoutInflater
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import tech.bero.screenshoter.databinding.FragmentContentBinding



/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class ContentFragment : Fragment() {


    private var mScreenSharing: Boolean = false
    private var _binding: FragmentContentBinding? = null

    private val mProjectionManager: MediaProjectionManager? = null
    private var mMediaProjection: MediaProjection? = null
    private val mSurface: Surface? = null
    private var mVirtualDisplay: VirtualDisplay? = null

    private val PERMISSION_CODE = 1

    private val mDisplayWidth = 0
    private val mDisplayHeight = 0
    private val mScreenDensity = 0

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        _binding = FragmentContentBinding.inflate(inflater, container, false)

        return binding.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setServiceRunning(ScreenshotService.RUNNING)
        setRememberedValues()

        binding.buttonStart.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked && !isValid()) {
                Snackbar
                    .make(view, R.string.validation_error, Snackbar.LENGTH_LONG)
                    .show()
                binding.buttonStart.isChecked = false
                return@setOnCheckedChangeListener
            }
            if (isChecked) {
                startClient()
                startProjection()
            } else {
                stopProjection()
                stopClient()
            }
        }

        binding.deviceName.addTextChangedListener { text ->
            PrefUtils.saveToPrefs(context, PrefUtils.DEVICE_NAME, text.toString())
        }
        binding.bucketName.addTextChangedListener { text ->
            PrefUtils.saveToPrefs(context, PrefUtils.BUCKET_NAME, text.toString())
        }
        binding.accessKey.addTextChangedListener { text ->
            PrefUtils.saveToPrefs(context, PrefUtils.ACCESS_KEY, text.toString())
        }
        binding.secretKey.addTextChangedListener { text ->
            PrefUtils.saveToPrefs(context, PrefUtils.SECRET_KEY, text.toString())
        }
    }

    private fun setRememberedValues() {
        binding.deviceName.setText(PrefUtils.getFromPrefs(context, PrefUtils.DEVICE_NAME,""))
        binding.bucketName.setText(PrefUtils.getFromPrefs(context, PrefUtils.BUCKET_NAME,""))
        binding.accessKey.setText(PrefUtils.getFromPrefs(context, PrefUtils.ACCESS_KEY,""))
        binding.secretKey.setText(PrefUtils.getFromPrefs(context, PrefUtils.SECRET_KEY,""))
    }

    private fun setServiceRunning(running: Boolean) {
        if (running) {
            binding.running.text = getString(R.string.running, "YES")
        } else {
            binding.running.text = getString(R.string.running, "NO")
        }
        binding.buttonStart.isChecked = running
    }

    private fun startClient() {
        AwsClient.initClient(
            context,
            binding.accessKey.text.toString(),
            binding.secretKey.text.toString(),
            binding.bucketName.text.toString()
        )
    }

    private fun stopClient() {
        AwsClient.stopClient()
    }

    private fun isValid():Boolean {
        if (binding.deviceName.text.toString().equals("")) {
            return false
        }
        if (binding.accessKey.text.toString().equals("")) {
            return false
        }
        if (binding.secretKey.text.toString().equals("")) {
            return false
        }
        if (binding.bucketName.text.toString().equals("")) {
            return false
        }
        return true
    }


    private fun startProjection() {
        val mProjectionManager =
            context?.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager?
        startActivityForResult(mProjectionManager!!.createScreenCaptureIntent(), PERMISSION_CODE)
        binding.running.text = context?.getString(R.string.running, "YES")
    }

    private fun stopProjection() {
        context?.startService(
            ScreenshotService.getStopIntent(context)
        )
        binding.running.text = context?.getString(R.string.running, "NO")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == PERMISSION_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                context?.startService(
                    ScreenshotService.getStartIntent(
                        context,
                        resultCode,
                        data,
                        binding.deviceName.text.toString()
                    )
                )
            } else {
                binding.buttonStart.isChecked = false
            }
        }
    }
}