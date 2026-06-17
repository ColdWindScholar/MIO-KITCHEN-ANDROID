package com.mio.kitchen.ui.modern

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.mio.kitchen.FirmwareOperationService
import com.mio.kitchen.R
import com.omarea.common.firmware.FirmwareSource
import com.omarea.common.storage.AndroidStorageGateway
import com.omarea.common.storage.StorageResolveOptions
import com.omarea.common.ui.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * RU: Современный экран анализа прошивки.
 *
 * Это пример того, как новый UI-слой должен использовать все компоненты
 * из этапов 5-14:
 *
 *   1. RuntimePermissionHelper — запросить разрешения (Stage 13).
 *   2. OpenDocumentHelper — выбрать файл прошивки через SAF (Stage 8).
 *   3. AndroidStorageGateway — превратить content:// URI в shell-path (Stage 4).
 *   4. FirmwareAnalysisViewModel — проанализировать прошивку (Stage 7+8).
 *   5. FirmwareProfileFormatter — показать результат (Stage 8).
 *   6. FirmwareOperationService — держать foreground во время операции (Stage 13).
 *
 * Существующие активности (MainActivity, ActionPage, ActivityFileSelector)
 * НЕ затронуты — они продолжают работать через legacy PageConfigReader.
 * Эта активность — рекомендуемый путь для нового UI-кода.
 *
 * EN: Modern firmware-analysis screen.
 *
 * Example of how the new UI layer should use every component from stages
 * 5-14:
 *
 *   1. RuntimePermissionHelper — request permissions (Stage 13).
 *   2. OpenDocumentHelper — pick a firmware file via SAF (Stage 8).
 *   3. AndroidStorageGateway — turn the content:// URI into a shell path (Stage 4).
 *   4. FirmwareAnalysisViewModel — analyze the firmware (Stage 7+8).
 *   5. FirmwareProfileFormatter — display the result (Stage 8).
 *   6. FirmwareOperationService — hold foreground during the operation (Stage 13).
 *
 * Existing activities (MainActivity, ActionPage, ActivityFileSelector) are
 * NOT touched — they keep using the legacy PageConfigReader. This activity is
 * the recommended path for new UI code.
 */
class FirmwareAnalysisActivity : AppCompatActivity() {

    private lateinit var viewModel: FirmwareAnalysisViewModel
    private lateinit var pickFileButton: Button
    private lateinit var analyzeButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var resultText: TextView
    private lateinit var statusText: TextView

    private val storageGateway: AndroidStorageGateway by lazy {
        AndroidStorageGateway(applicationContext)
    }
    private val openDocumentHelper = OpenDocumentHelper(this) { uri ->
        if (uri != null) onFirmwarePicked(uri) else showStatus("No file selected")
    }

    private var resolvedShellPath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // RU: минимальный layout, чтобы активность можно было запускать в isolation.
        //     В реальном продукте здесь будет layout-XML.
        // EN: minimal layout so the activity can run in isolation. Real product
        //     will use a layout XML.
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        statusText = TextView(this).apply {
            text = "Ready. Pick a firmware file to begin."
        }
        pickFileButton = Button(this).apply {
            text = "Pick firmware file"
            setOnClickListener {
                if (!RuntimePermissionHelper.areAllGranted(this@FirmwareAnalysisActivity)) {
                    RuntimePermissionHelper.requestMissing(
                        this@FirmwareAnalysisActivity,
                        PERMISSION_REQUEST_CODE
                    )
                } else {
                    launchPicker()
                }
            }
        }
        analyzeButton = Button(this).apply {
            text = "Analyze firmware"
            isEnabled = false
            setOnClickListener { analyzeFirmware() }
        }
        progressBar = ProgressBar(this).apply {
            visibility = View.GONE
        }
        resultText = TextView(this)
        root.addView(statusText)
        root.addView(pickFileButton)
        root.addView(analyzeButton)
        root.addView(progressBar)
        root.addView(ScrollView(this).apply {
            addView(resultText)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        })
        val scroll = ScrollView(this)
        setContentView(root)

        viewModel = ViewModelProvider(this)[FirmwareAnalysisViewModel::class.java]
        observeState()
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    when (state) {
                        is UiState.Idle -> {
                            progressBar.visibility = View.GONE
                            resultText.text = ""
                        }
                        is UiState.Loading -> {
                            progressBar.visibility = View.VISIBLE
                            statusText.text = state.message ?: "Working…"
                            resultText.text = ""
                        }
                        is UiState.Success -> {
                            progressBar.visibility = View.GONE
                            statusText.text = "Analysis complete"
                            resultText.text = FirmwareProfileFormatter.detailed(state.data)
                        }
                        is UiState.Error -> {
                            progressBar.visibility = View.GONE
                            statusText.text = "Analysis failed: ${state.message}"
                        }
                    }
                }
            }
        }
    }

    private fun launchPicker() {
        openDocumentHelper.launch(arrayOf(
            "application/zip",
            "application/octet-stream",
            "application/x-android-ota-package",
            "*/*"
        ))
    }

    private fun onFirmwarePicked(uri: Uri) {
        lifecycleScope.launch {
            try {
                showStatus("Resolving file…")
                val result = withContext(Dispatchers.IO) {
                    storageGateway.resolveUriForShell(
                        uri,
                        StorageResolveOptions(computeSha256 = true)
                    )
                }
                when (result) {
                    is com.omarea.common.storage.StorageResolveResult.Resolved -> {
                        resolvedShellPath = result.shellPath
                        showStatus("File ready: ${result.shellPath}")
                        analyzeButton.isEnabled = true
                    }
                    is com.omarea.common.storage.StorageResolveResult.Failed -> {
                        showStatus("Failed to resolve file: ${result.message}")
                        analyzeButton.isEnabled = false
                    }
                }
            } catch (e: Throwable) {
                showStatus("Error: ${e.message}")
            }
        }
    }

    private fun analyzeFirmware() {
        val path = resolvedShellPath ?: run {
            showStatus("No file resolved yet")
            return
        }
        startForegroundService()
        viewModel.analyzeFile(path)
    }

    private fun startForegroundService() {
        val intent = Intent(this, FirmwareOperationService::class.java).apply {
            putExtra(FirmwareOperationService.EXTRA_TITLE, getString(R.string.app_name))
            putExtra(FirmwareOperationService.EXTRA_TEXT, "Analyzing firmware…")
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun showStatus(message: String) {
        statusText.text = message
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (RuntimePermissionHelper.areAllGranted(grantResults)) {
                launchPicker()
            } else {
                showStatus("Required permissions denied")
            }
        }
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 0x4D49_4F01
    }
}
