package com.mediacover.capture

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager

class MainActivity : AppCompatActivity() {
    private lateinit var tvPath: TextView
    private lateinit var btnSelect: Button
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button

    private val pickFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        val pref = PreferenceManager.getDefaultSharedPreferences(this).edit()
        pref.putString("save_uri", uri.toString())
        pref.apply()
        refreshText()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        tvPath = findViewById(R.id.tv_path)
        btnSelect = findViewById(R.id.btn_select_dir)
        btnStart = findViewById(R.id.btn_start)
        btnStop = findViewById(R.id.btn_stop)
        refreshText()

        btnSelect.setOnClickListener { pickFolder.launch(null) }
        btnStart.setOnClickListener { startService(Intent(this, MediaListenService::class.java)) }
        btnStop.setOnClickListener { stopService(Intent(this, MediaListenService::class.java)) }
    }

    private fun refreshText() {
        val uriStr = PreferenceManager.getDefaultSharedPreferences(this).getString("save_uri",null)
        tvPath.text = if(uriStr.isNullOrEmpty()) "未自定义目录，默认保存到Download" else "已选择目录：$uriStr"
    }
}

        btnSelectDir.setOnClickListener {
            selectDirLauncher.launch(null)
        }
        btnStart.setOnClickListener {
            startForegroundService(Intent(this, MediaListenService::class.java))
        }
        btnStop.setOnClickListener {
            stopService(Intent(this, MediaListenService::class.java))
        }
    }

    private fun refreshPathText(){
        val uriStr = PreferenceManager.getDefaultSharedPreferences(this)
            .getString("save_uri",null)
        if(uriStr.isNullOrEmpty()){
            tvPath.text = "未自定义目录，默认使用Download文件夹"
        }else{
            tvPath.text = "已选择SAF目录：$uriStr"
        }
    }
}
