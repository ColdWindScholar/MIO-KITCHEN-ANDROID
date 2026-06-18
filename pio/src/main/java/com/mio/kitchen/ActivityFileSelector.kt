package com.mio.kitchen

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.view.KeyEvent
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.PermissionChecker
import com.mio.kitchen.databinding.ActivityFileSelectorBinding
import com.mio.kitchen.ui.AdapterFileSelector
import com.mio.kitchen.ui.modern.RuntimePermissionHelper
import com.omarea.common.ui.ProgressBarDialog
import java.io.File

// FILE_MODE = 0 FOLDER_MODE=1
class ActivityFileSelector : AppCompatActivity() {

    private lateinit var binding: ActivityFileSelectorBinding
    private var adapterFileSelector: AdapterFileSelector? = null
    private var extension = ""
    private var mode = 0

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageConfig.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // TODO:ThemeSwitch.switchTheme(this)

        super.onCreate(savedInstanceState)
        binding = ActivityFileSelectorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val toolbar = findViewById<View>(R.id.toolbar) as Toolbar
        setSupportActionBar(toolbar)
        // setTitle(R.string.app_name)

        // 显示返回按钮
        supportActionBar!!.setHomeButtonEnabled(true)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { _ ->
            finish()
        }

        intent.extras?.run {
            if (containsKey("extension")) {
                extension = "" + intent.extras!!.getString("extension")
                if (!extension.startsWith(".")) {
                    extension = ".$extension"
                }
                if (extension.isNotEmpty()) {
                    title = "$title($extension)"
                }
            }
            if (containsKey("mode")) {
                mode = getInt("mode")
                if (mode == 1) {
                    title = getString(R.string.title_activity_folder_selector)
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && adapterFileSelector != null && adapterFileSelector!!.goParent()) {
            return true
        } else {
            setResult(Activity.RESULT_CANCELED, Intent())
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        // RU: Stage 20 — делегируем проверку результата в RuntimePermissionHelper,
        //     который учитывает targetSdk 35 (POST_NOTIFICATIONS, capped
        //     READ_EXTERNAL_STORAGE).
        // EN: Stage 20 — delegate result verification to RuntimePermissionHelper,
        //     which respects targetSdk 35 (POST_NOTIFICATIONS, capped
        //     READ_EXTERNAL_STORAGE).
        if (requestCode == 111) {
            if (RuntimePermissionHelper.areAllGranted(grantResults)) {
                loadData()
            } else {
                Toast.makeText(this@ActivityFileSelector, R.string.file_read_permission_denied, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun checkPermission(permission: String): Boolean = PermissionChecker.checkSelfPermission(this, permission) == PermissionChecker.PERMISSION_GRANTED

    /**
     * RU: Stage 20 — заменяем устаревший запрос `READ_EXTERNAL_STORAGE` +
     *     `WRITE_EXTERNAL_STORAGE` на RuntimePermissionHelper. На Android 13+
     *     эти разрешения больше не выдаются системой (capped в манифесте),
     *     так что старый код постоянно показывал Toast "permission denied".
     *
     * EN: Stage 20 — replace the legacy `READ_EXTERNAL_STORAGE` +
     *     `WRITE_EXTERNAL_STORAGE` request with RuntimePermissionHelper. On
     *     Android 13+ these permissions are no longer granted by the system
     *     (capped in the manifest), so the legacy code kept showing the
     *     "permission denied" Toast.
     */
    private fun requestPermissions() {
        RuntimePermissionHelper.requestMissing(this, 111)
    }

    override fun onResume() {
        super.onResume()
        loadData()
    }

    private fun loadData() {
        // RU: Stage 20 — на Android 13+ READ_EXTERNAL_STORAGE не выдаётся,
        //     но legacy file-selector всё ещё хочет обычный file-path.
        //     Если разрешение не выдано — показываем Toast и предлагаем
        //     пользователю открыть FirmwareAnalysisActivity, где используется
        //     SAF (StorageGateway + FirmwareWorkspace).
        // EN: Stage 20 — on Android 13+ READ_EXTERNAL_STORAGE is not granted,
        //     but the legacy file selector still expects a regular file path.
        //     If the permission is not granted, show a Toast and direct the
        //     user to FirmwareAnalysisActivity where SAF (StorageGateway +
        //     FirmwareWorkspace) is used.
        val canReadLegacy = checkPermission(Manifest.permission.READ_EXTERNAL_STORAGE) &&
            checkPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        if (canReadLegacy) {
            val sdcard = File(Environment.getExternalStorageDirectory().absolutePath)
            if (sdcard.exists() && sdcard.isDirectory) {
                val list = sdcard.listFiles()
                if (list == null) {
                    Toast.makeText(this@ActivityFileSelector, R.string.file_list_load_failed, Toast.LENGTH_LONG).show()
                    return
                }
                val onSelected =  Runnable {
                    val file: File? = adapterFileSelector!!.selectedFile
                    if (file != null) {
                        this.setResult(Activity.RESULT_OK, Intent().putExtra("file", file.absolutePath))
                        this.finish()
                    }
                }
                adapterFileSelector = if (mode == 1) {
                    AdapterFileSelector.FolderChooser(this@ActivityFileSelector, sdcard, onSelected, ProgressBarDialog(this))
                } else {
                    AdapterFileSelector.FileChooser(this@ActivityFileSelector, sdcard, onSelected, ProgressBarDialog(this), extension)
                }

                binding.fileSelectorList.adapter = adapterFileSelector
            }
        } else if (RuntimePermissionHelper.areAllGranted(this)) {
            // RU: Все runtime-разрешения выданы, но legacy READ/WRITE_EXTERNAL_STORAGE
            //     не доступны на Android 13+. В этом случае предлагаем пользователю
            //     использовать новый SAF-based flow.
            // EN: All runtime permissions are granted, but legacy
            //     READ/WRITE_EXTERNAL_STORAGE is not available on Android 13+.
            //     In that case, direct the user to the new SAF-based flow.
            Toast.makeText(
                this@ActivityFileSelector,
                "On Android 13+, please use the new file picker (FirmwareAnalysisActivity)",
                Toast.LENGTH_LONG
            ).show()
        } else {
            requestPermissions()
        }
    }
}
