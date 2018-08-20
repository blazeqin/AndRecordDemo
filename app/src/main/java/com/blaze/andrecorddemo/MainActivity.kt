package com.blaze.andrecorddemo

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.support.v7.app.AppCompatActivity
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.text.SimpleDateFormat

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        btn_start_system.setOnClickListener {
            openSystemCameraRecord()
        }

        btn_start_diy.setOnClickListener {

        }
    }

    //record by system
    private fun openSystemCameraRecord() {
        val fileUri = Uri.fromFile(getOutputMediaFile())
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        intent.putExtra(MediaStore.EXTRA_OUTPUT, fileUri)
        intent.putExtra(MediaStore.EXTRA_DURATION_LIMIT, 10)//限制的录制时长 以秒为单位
        intent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 0.5)//设置拍摄的质量0~1
        intent.putExtra("camerasensortype", 2); // 调用前置摄像头
        startActivityForResult(intent, RECORD_SYSTEM_VIDEO)
    }

    //create a file to store generate videos
    private fun getOutputMediaFile(): File? {
        if (!Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
            Toast.makeText(this, "sdcard error", Toast.LENGTH_LONG).show()
            return null
        }
        val mediaStorageDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), VIDEO_DIR)
        if (mediaStorageDir.exists().not()) {
            mediaStorageDir.mkdirs()
        }
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(System.currentTimeMillis())
        val mediaFile = File(mediaStorageDir.path + File.separator + "VID_" + timeStamp + ".mp4")
        return mediaFile
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK) return
        when (requestCode) {
            RECORD_SYSTEM_VIDEO -> data?.apply {
                videoView.setVideoURI(this.data)
                videoView.start()
            }
            else -> {}
        }
    }
    companion object {
        const val RECORD_SYSTEM_VIDEO = 1
        const val RECORD_CUSTOM_VIDEO = 2

    }
}
