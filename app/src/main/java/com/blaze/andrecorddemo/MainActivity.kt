package com.blaze.andrecorddemo

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.support.v4.content.FileProvider
import android.support.v7.app.AppCompatActivity
import com.yanzhenjie.permission.AndPermission
import com.yanzhenjie.permission.Permission
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.text.SimpleDateFormat

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        btn_start_system.setOnClickListener {
            requestPermission(RECORD_SYSTEM_VIDEO)
        }

        btn_start_diy.setOnClickListener {

        }
    }

    //get permissions
    private fun requestPermission(type: Int) {
        AndPermission.with(this)
                .runtime()
                .permission(Permission.WRITE_EXTERNAL_STORAGE, Permission.READ_EXTERNAL_STORAGE, Permission.CAMERA, Permission.RECORD_AUDIO)
                .onGranted{
                    when (type) {
                        RECORD_SYSTEM_VIDEO -> openSystemCameraRecord()
                        else -> {}
                    }
                }
                .onDenied{}
                .start()
    }

    //record by system
    private fun openSystemCameraRecord() {
        val file = getOutputMediaFile()
        var fileUri:Uri?
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            fileUri = file?.let { FileProvider.getUriForFile(this,packageName+".fileprovider", it) }
        }else{
            fileUri = Uri.fromFile(file)
        }

        val intent = Intent(MediaStore.ACTION_VIDEO_CAPTURE)
        intent.putExtra(MediaStore.EXTRA_OUTPUT, fileUri)
        intent.putExtra(MediaStore.EXTRA_DURATION_LIMIT, 60)//限制的录制时长 以秒为单位
        intent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1)//设置拍摄的质量0~1
//        intent.putExtra("autofocus", false); // 自动对焦
//        intent.putExtra("camerasensortype", 2); // 调用前置摄像头  无效果
//        intent.putExtra("android.intent.extras.CAMERA_FACING_FRONT", 1);
        startActivityForResult(intent, RECORD_SYSTEM_VIDEO)
    }

    //create a file to store generate videos
    private fun getOutputMediaFile(): File? {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmm").format(System.currentTimeMillis())
        val mediaFile = File(this.filesDir.path + File.separator + "galaxy" ,"VID_"+ timeStamp + ".mp4")
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
