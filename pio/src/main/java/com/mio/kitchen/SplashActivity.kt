package com.mio.kitchen

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import com.mio.kitchen.databinding.ActivitySplashBinding
import com.mio.kitchen.permissions.CheckRootStatus
import com.mio.kitchen.ui.modern.AppRuntimeStore
import com.omarea.common.shell.ShellTranslation
import com.omarea.krscript.executor.ScriptEnvironmen
import com.omarea.krscript.runtime.LegacyShellBridge
import java.io.BufferedReader
import java.io.DataOutputStream

class SplashActivity : Activity() {
    private lateinit var binding: ActivitySplashBinding

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageConfig.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            // RU: Stage 20 — инициализируем AppRuntimeStore как можно раньше,
            //     чтобы новые активности (FirmwareAnalysisActivity и др.) могли
            //     читать DeviceProfile сразу.
            // EN: Stage 20 — initialise AppRuntimeStore as early as possible so
            //     that new activities (FirmwareAnalysisActivity and friends) can
            //     read DeviceProfile immediately.
            AppRuntimeStore.init(rootAvailable = null)
        } catch (e: Throwable) {
            android.util.Log.e("SplashActivity", "AppRuntimeStore.init failed", e)
        }

        try {
            // RU: Stage 22 — инициализируем LegacyShellBridge. Ранее это делал
            //     KrScriptConfig.init -> ScriptEnvironmen.init. Теперь
            //     KrScriptConfig только хранит конфигурацию, а bridge
            //     инициализируется явно.
            // EN: Stage 22 — initialise LegacyShellBridge. Previously this was
            //     done by KrScriptConfig.init -> ScriptEnvironmen.init. Now
            //     KrScriptConfig only stores configuration, and the bridge is
            //     initialised explicitly.
            LegacyShellBridge.init(this)
        } catch (e: Throwable) {
            android.util.Log.e("SplashActivity", "LegacyShellBridge.init failed", e)
        }

        if (ScriptEnvironmen.isInited()) {
            if (isTaskRoot) {
                gotoHome()
            }
            return
        }

        try {
            binding = ActivitySplashBinding.inflate(layoutInflater)
            setContentView(binding.root)
            updateThemeStyle()
        } catch (e: Throwable) {
            android.util.Log.e("SplashActivity", "Layout inflation failed", e)
            // RU: Если layout не inflated, пропускаем splash и идём прямо
            //     в MainActivity, чтобы пользователь хотя бы увидел UI.
            // EN: If layout inflation fails, skip splash and go straight
            //     to MainActivity so the user sees something.
            gotoHome()
            return
        }

        checkPermissions()
    }

    /**
     * 界面主题样式调整
     */
    private fun updateThemeStyle() {
        window.navigationBarColor = getColorAccent()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.navigationBarColor = getColor(R.color.splash_bg_color)
        } else {
            window.navigationBarColor = resources.getColor(R.color.splash_bg_color)
        }

        //  得到当前界面的装饰视图
        val decorView = window.decorView
        //让应用主题内容占用系统状态栏的空间,注意:下面两个参数必须一起使用 stable 牢固的
        val option = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        decorView.systemUiVisibility = option
        //设置状态栏颜色为透明
        window.statusBarColor = Color.TRANSPARENT
    }

    private fun getColorAccent(): Int {
        val typedValue = TypedValue()
        this.theme.resolveAttribute(R.attr.colorAccent, typedValue, true)
        return typedValue.data
    }

    /**
     * 开始检查必需权限
     */
    private fun checkPermissions() {
        binding.startLogo.visibility = View.VISIBLE
        checkRoot(Runnable {
            binding.startStateText.text = getString(R.string.pio_permission_checking)
            hasRoot = true
            // RU: Stage 20 — после проверки root обновляем AppRuntimeStore.
            //     Legacy-путём остаётся `CheckRootStatus.lastCheckResult`.
            // EN: Stage 20 — after the root check, refresh AppRuntimeStore.
            //     The legacy path still uses `CheckRootStatus.lastCheckResult`.
            AppRuntimeStore.updateRootStatus(hasRoot = true)

            /*
            checkFileWrite(Runnable {
                startToFinish()
            })
            */
            startToFinish()
        })
    }

    private var hasRoot = false

    private fun checkRoot(next: Runnable) {
        CheckRootStatus(this, next).forceGetRoot()
    }

    /**
     * 启动完成
     */
    private fun startToFinish() {
        binding.startStateText.text = getString(R.string.pop_started)
        val config = KrScriptConfig().init(this)
        if (config.beforeStartSh.isNotEmpty()) {
            BeforeStartThread(this, config, UpdateLogViewHandler(binding.startStateText, Runnable {
                gotoHome()
            })).start()
        } else {
            gotoHome()
        }
    }

    private fun gotoHome() {
        if (this.intent != null && this.intent.hasExtra("JumpActionPage") && this.intent.getBooleanExtra("JumpActionPage", false)) {
            val actionPage = Intent(this.applicationContext, ActionPage::class.java)
            actionPage.putExtras(this.intent)
            startActivity(actionPage)
        } else {
            val home = Intent(this.applicationContext, MainActivity::class.java)
            startActivity(home)
        }
        finish()
    }

    private class UpdateLogViewHandler(private var logView: TextView, private val onExit: Runnable) {
        private val handler = Handler(Looper.getMainLooper())
        private var notificationMessageRows = ArrayList<String>()
        private var someIgnored = false

        fun onLogOutput(log: String) {
            handler.post {
                synchronized(notificationMessageRows) {
                    if (notificationMessageRows.size > 6) {
                        notificationMessageRows.remove(notificationMessageRows.first())
                        someIgnored = true
                    }
                    notificationMessageRows.add(log)
                    logView.text =
                        notificationMessageRows.joinToString("\n", if (someIgnored) "……\n" else "").trim()
                }
            }
        }

        fun onExit() {
            handler.post { onExit.run() }
        }
    }

    private class BeforeStartThread(private var context: Context, private val config: KrScriptConfig, private var updateLogViewHandler: UpdateLogViewHandler) : Thread() {
        val params: HashMap<String, String> = config.variables

        override fun run() {
            try {
                // RU: Stage 23 — используем ScriptEnvironmen.getRuntime() (теперь
                //     возвращает Process через Runtime.exec) вместо
                //     ShellExecutor.getSuperUserRuntime/getRuntime. Это убирает
                //     зависимость от common/shell/ShellExecutor.
                // EN: Stage 23 — use ScriptEnvironmen.getRuntime() (now returns
                //     a Process via Runtime.exec) instead of
                //     ShellExecutor.getSuperUserRuntime/getRuntime. This removes
                //     the dependency on common/shell/ShellExecutor.
                val process = ScriptEnvironmen.getRuntime()
                if (process != null) {
                    val outputStream = DataOutputStream(process.outputStream)

                    // RU: Stage 23 — строим full streaming command через
                    //     LegacyShellBridge и пишем в Process.stdin.
                    // EN: Stage 23 — build the full streaming command via
                    //     LegacyShellBridge and write into Process.stdin.
                    val fullCommand = LegacyShellBridge.buildStreamingCommand(
                        context = context,
                        script = config.beforeStartSh,
                        nodeInfoBase = null,
                        tag = "pio-splash"
                    )
                    outputStream.write(fullCommand.toByteArray(Charsets.UTF_8))
                    outputStream.flush()

                    val shellTranslation = ShellTranslation(context)
                    StreamReadThread(process.inputStream.bufferedReader(), updateLogViewHandler, shellTranslation).start()
                    StreamReadThread(process.errorStream.bufferedReader(), updateLogViewHandler, shellTranslation).start()

                    process.waitFor()
                    updateLogViewHandler.onExit()
                } else {
                    updateLogViewHandler.onExit()
                }
            } catch (ex: Exception) {
                updateLogViewHandler.onExit()
            }
        }
    }

    private class StreamReadThread(
        private var reader: BufferedReader,
        private var updateLogViewHandler: UpdateLogViewHandler,
        private val shellTranslation: ShellTranslation
    ) : Thread() {
        override fun run() {
            var line: String?
            while (true) {
                line = reader.readLine()
                if (line == null) {
                    break
                } else {
                    updateLogViewHandler.onLogOutput(shellTranslation.resolveRow(line))
                }
            }
        }
    }
}
