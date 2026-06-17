package com.mio.kitchen

import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.app.Activity
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import com.omarea.common.storage.AndroidStorageGateway
import com.omarea.common.storage.StorageResolveOptions
import com.omarea.common.storage.StorageResolveResult
import com.omarea.common.ui.ProgressBarDialog
import com.omarea.krscript.config.IconPathAnalysis
import com.omarea.krscript.config.PageConfigLoader
import com.omarea.krscript.config.PageConfigSh
import com.omarea.krscript.executor.ScriptEnvironmen
import com.omarea.krscript.model.AutoRunTask
import com.omarea.krscript.model.ClickableNode
import com.omarea.krscript.model.KrScriptActionHandler
import com.omarea.krscript.model.NodeInfoBase
import com.omarea.krscript.model.PageMenuOption
import com.omarea.krscript.model.PageNode
import com.omarea.krscript.model.RunnableNode

import com.omarea.krscript.ui.ActionListFragment
import com.omarea.krscript.ui.DialogLogFragment
import com.omarea.krscript.ui.PageMenuLoader
import com.omarea.krscript.ui.ParamsFileChooserRender
import com.mio.kitchen.databinding.ActivityActionPageBinding


class ActionPage : AppCompatActivity() {
    private lateinit var binding: ActivityActionPageBinding
    private val progressBarDialog = ProgressBarDialog(this)
    private var actionsLoaded = false
    private var handler = Handler()
    private lateinit var currentPageConfig: PageNode
    private var autoRunItemId = ""
    private val storageGateway: AndroidStorageGateway by lazy { AndroidStorageGateway(applicationContext) }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageConfig.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 如果应用还没启动，就直接打开了actionPage(通常是PIO的快捷方式)，先跳转到启动页面
        if (!ScriptEnvironmen.isInited()) {
            val initIntent = Intent(this.applicationContext, SplashActivity::class.java)
            initIntent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            initIntent.putExtras(this.intent)
            initIntent.putExtra("JumpActionPage", true)
            startActivity(initIntent)
            // overridePendingTransition(0, 0)

            finish()
            return
        }

        ThemeModeState.switchTheme(this)

        binding = ActivityActionPageBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val toolbar = findViewById<View>(R.id.toolbar) as Toolbar
        setSupportActionBar(toolbar)
        setTitle(R.string.app_name)

        // 显示返回按钮
        supportActionBar!!.setHomeButtonEnabled(true)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener {
            finish()
        }

        // 读取intent里的参数
        val intent = this.intent
        if (intent.extras != null) {
            val extras = intent.extras
            if (extras != null && (extras.containsKey("page") || extras.containsKey("shortcutId"))) {
                val page = if (extras.containsKey("page")) {
                    extras.getSerializable("page") as PageNode?
                } else {
                    null
                }

                if (page != null) {
                    autoRunItemId = if (extras.containsKey("autoRunItemId")) ("" + extras.getString("autoRunItemId")) else ""

                    if (page.activity.isNotEmpty()) {
                        return
                    }



                    if (page.title.isNotEmpty()) {
                        title = page.title
                    }
                    currentPageConfig = page
                } else {
                    Toast.makeText(this, R.string.page_info_invalid, Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }

        if (currentPageConfig.pageConfigPath.isEmpty() && currentPageConfig.pageConfigSh.isEmpty()) {
            setResult(2)
            finish()
        }
    }

    private var actionShortClickHandler = object : KrScriptActionHandler {
        override fun onActionCompleted(runnableNode: RunnableNode) {
            if (runnableNode.autoFinish) {
                finishAndRemoveTask()
            } else if (runnableNode.reloadPage) {
                loadPageConfig()
            }
        }

        override fun addToFavorites(clickableNode: ClickableNode, addToFavoritesHandler: KrScriptActionHandler.AddToFavoritesHandler) {
            val page = when (clickableNode)  {
                is PageNode -> clickableNode
                is RunnableNode -> currentPageConfig
                else -> return
            }

            val intent = Intent()

            intent.component = ComponentName(this@ActionPage.applicationContext, ActionPage::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
            if (clickableNode is RunnableNode) {
                intent.putExtra("autoRunItemId", clickableNode.key)
            }

            intent.putExtra("page", page)

            addToFavoritesHandler.onAddToFavorites(clickableNode, intent)
        }

        override fun onSubPageClick(pageNode: PageNode) {
            openPageKre(pageNode)
        }

        override fun openFileChooser(fileSelectedInterface: ParamsFileChooserRender.FileSelectedInterface): Boolean {
            return chooseFilePath(fileSelectedInterface)
        }
    }

    private var fileSelectedInterface: ParamsFileChooserRender.FileSelectedInterface? = null
    private val ACTION_FILE_PATH_CHOOSER = 65400
    private val ACTION_FILE_PATH_CHOOSER_INNER = 65300

    private fun chooseFilePath(extension: String) {
        try {
            val intent = Intent(this, ActivityFileSelector::class.java)
            intent.putExtra("extension", extension)
            intent.putExtra("mode", 0)
            startActivityForResult(intent, ACTION_FILE_PATH_CHOOSER_INNER)
        } catch (ex: Exception) {
            Toast.makeText(this, R.string.file_selector_open_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun chooseFolderPath() {
        try {
            val intent = Intent(this, ActivityFileSelector::class.java)
            intent.putExtra("mode", 1)
            startActivityForResult(intent, ACTION_FILE_PATH_CHOOSER_INNER)
        } catch (ex: Exception) {
            Toast.makeText(this, R.string.file_selector_open_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private var menuOptions:ArrayList<PageMenuOption>? = null

    // 右上角菜单的创建
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        if (menuOptions == null) {
            menuOptions = PageMenuLoader(this@ActionPage, currentPageConfig).load()
        }

        if (menuOptions != null && menu != null) {
            for (i in 0 until menuOptions!!.size) {
                val menuOption = menuOptions!![i]
                if (menuOption.isFab) {
                    addFab(menuOption)
                } else {
                    menu.add(-1, i, i, menuOption.title)
                }
            }
        }

        return true // super.onCreateOptionsMenu(menu)
    }

    private fun addFab(menuOption: PageMenuOption) {
        binding.actionPageFab.run {
            visibility = View.VISIBLE
            setOnClickListener {
                onMenuItemClick(menuOption)
            }

            if (menuOption.type == "file" && menuOption.iconPath.isEmpty()) {
                setImageDrawable(ContextCompat.getDrawable(context, R.drawable.kr_folder))
            } else if (menuOption.iconPath.isNotEmpty()) {
                val icon = IconPathAnalysis().loadLogo(context, menuOption, false)
                if (icon != null) {
                    setImageDrawable(icon)
                } else {
                    setImageDrawable(ContextCompat.getDrawable(context, R.drawable.kr_fab))
                }
            } else {
                setImageDrawable(ContextCompat.getDrawable(context, R.drawable.kr_fab))
            }
        }
    }

    // 右上角菜单的点击操作
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (menuOptions == null) {
            return false
        }

        onMenuItemClick(menuOptions!![item.itemId])

        return true
    }

    private fun onMenuItemClick(menuOption: PageMenuOption) {
        when(menuOption.type) {
            "refresh", "reload" -> {
                recreate()
            }
            "exit", "finish", "close" -> {
                finish()
            }
            "file" -> {
                menuItemChooseFile(menuOption)
            }
            else -> {
                menuItemExecute(menuOption, HashMap<String, String>().apply{
                    put("state", menuOption.key)
                    put("menu_id", menuOption.key)
                })
            }
        }
    }

    private fun menuItemExecute(menuOption: PageMenuOption, params: HashMap<String, String>) {
        val onDismiss = Runnable {
            if (menuOption.autoFinish) {
                finish()
            } else if (menuOption.reloadPage) {
                recreate()
            }
        }
// Iknow else if (menuOption.updateBlocks != null) {
//                // TODO rootGroup.triggerUpdateByKey(item.updateBlocks!!)
//            } is very important ,but it useless now
        val darkMode = ThemeModeState.getThemeMode().isDarkMode
        val dialog = DialogLogFragment.create(
                menuOption,
                Runnable {  },
                onDismiss,
                currentPageConfig.pageHandlerSh,
                params,
                darkMode)
        dialog.show(supportFragmentManager, "")
        dialog.isCancelable = false
    }

    private fun menuItemChooseFile(menuOption: PageMenuOption) {
        chooseFilePath(object: ParamsFileChooserRender.FileSelectedInterface{
            override fun onFileSelected(path: String?) {
                if (path != null) {
                    handler.post {
                        menuItemExecute(menuOption, HashMap<String, String>().apply{
                            put("state", menuOption.key)
                            put("menu_id", menuOption.key)
                            put("file", path)
                            put("folder", path)
                        })
                    }
                }
            }

            // TODO:文件类型过滤
            override fun mimeType(): String? {
                return menuOption.mime.ifEmpty { null }

            }

            override fun suffix(): String? {
                return menuOption.suffix.ifEmpty { null }
            }

            override fun type(): Int {
                return when(menuOption.type) {
                    "folder" -> ParamsFileChooserRender.FileSelectedInterface.TYPE_FOLDER
                    "file" -> ParamsFileChooserRender.FileSelectedInterface.TYPE_FILE
                    else -> ParamsFileChooserRender.FileSelectedInterface.TYPE_FILE
                }
            }
        })
    }

    private fun chooseFilePath(fileSelectedInterface: ParamsFileChooserRender.FileSelectedInterface): Boolean {
        return try {
            if (fileSelectedInterface.type() == ParamsFileChooserRender.FileSelectedInterface.TYPE_FOLDER) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(READ_EXTERNAL_STORAGE), 2)
                    Toast.makeText(this, getString(R.string.kr_write_external_storage), Toast.LENGTH_LONG).show()
                    return false
                }
                this.fileSelectedInterface = fileSelectedInterface
                chooseFolderPath()
            } else {
                this.fileSelectedInterface = fileSelectedInterface
                openSystemFileChooser(fileSelectedInterface)
            }
            true
        } catch (ex: java.lang.Exception) {
            Toast.makeText(this, R.string.file_selector_open_failed, Toast.LENGTH_SHORT).show()
            false
        }
    }

    private fun openSystemFileChooser(fileSelectedInterface: ParamsFileChooserRender.FileSelectedInterface) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        intent.type = fileSelectedInterface.mimeType() ?: "*/*"
        intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true)
        startActivityForResult(intent, ACTION_FILE_PATH_CHOOSER)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == ACTION_FILE_PATH_CHOOSER) {
            val result = if (data == null || resultCode != Activity.RESULT_OK) null else data.data
            if (fileSelectedInterface != null) {
                if (result != null) {
                    resolveSelectedUri(result, data?.flags ?: 0)
                } else {
                    fileSelectedInterface?.onFileSelected(null)
                    this.fileSelectedInterface = null
                }
            }
        } else if (requestCode == ACTION_FILE_PATH_CHOOSER_INNER) {
            val absPath = if (data == null || resultCode != Activity.RESULT_OK) null else data.getStringExtra("file")
            fileSelectedInterface?.onFileSelected(absPath)
            this.fileSelectedInterface = null
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun resolveSelectedUri(uri: Uri, intentFlags: Int) {
        showDialog(getString(R.string.file_workspace_prepare))
        Thread {
            storageGateway.persistReadPermission(uri, intentFlags)
            val result = storageGateway.resolveUriForShell(
                uri,
                StorageResolveOptions(
                    preferLegacyDirectPath = false,
                    copyContentUriToWorkspace = true,
                    computeSha256 = true
                )
            )

            handler.post {
                hideDialog()
                when (result) {
                    is StorageResolveResult.Resolved -> {
                        // RU: Stage 20 — регистрируем выбранную прошивку в
                        //     AppRuntimeStore. Анализ выполняется в background-
                        //     потоке, чтобы не блокировать UI; на failure просто
                        //     логируем (legacy-путь уже получил shellPath).
                        // EN: Stage 20 — register the picked firmware in
                        //     AppRuntimeStore. Analysis runs on a background
                        //     thread so it does not block the UI; on failure we
                        //     just log (the legacy path already has shellPath).
                        val shellPath = result.shellPath
                        Thread {
                            try {
                                com.mio.kitchen.ui.modern.AppRuntimeStore
                                    .setFirmware(shellPath)
                            } catch (_: Throwable) {
                                // Best-effort — analysis failures here must not
                                // block the legacy path.
                            }
                        }.start()
                        fileSelectedInterface?.onFileSelected(shellPath)
                    }
                    is StorageResolveResult.Failed -> {
                        Toast.makeText(
                            this,
                            getString(R.string.file_workspace_resolve_failed, result.message),
                            Toast.LENGTH_LONG
                        ).show()
                        fileSelectedInterface?.onFileSelected(null)
                    }
                }
                this.fileSelectedInterface = null
            }
        }.start()
    }

    private fun showDialog(msg: String) {
        handler.post {
            progressBarDialog.showDialog(msg)
        }
    }

    private fun hideDialog() {
        handler.post {
            progressBarDialog.hideDialog()
        }
    }

    override fun onResume() {
        super.onResume()

        if (!actionsLoaded) {
            loadPageConfig()
        }
    }

    private fun loadPageConfig() {
        val activity = this

        Thread(Runnable {
            currentPageConfig.run {
                if (beforeRead.isNotEmpty()) {
                    showDialog(getString(R.string.kr_page_before_load))
                    ScriptEnvironmen.executeResultRoot(activity, beforeRead, this)
                }

                showDialog(getString(R.string.kr_page_loading))
                var items: ArrayList<NodeInfoBase>? = null

                if (pageConfigSh.isNotEmpty()) {
                    items = PageConfigSh(this@ActionPage, pageConfigSh, this).execute()
                }

                if (items == null && pageConfigPath.isNotEmpty()) {
                    // RU: Stage 22 — заменяем legacy PageConfigReader на новый
                    //     PageConfigLoader (PageConfigRepository + RuntimeBinder).
                    // EN: Stage 22 — replace legacy PageConfigReader with the new
                    //     PageConfigLoader (PageConfigRepository + RuntimeBinder).
                    items = PageConfigLoader.load(this@ActionPage, pageConfigPath, pageConfigDir)
                }

                if (afterRead.isNotEmpty()) {
                    showDialog(getString(R.string.kr_page_after_load))
                    ScriptEnvironmen.executeResultRoot(activity, afterRead, this)
                }

                if (items != null && items.size != 0) {
                    if (loadSuccess.isNotEmpty()) {
                        showDialog(getString(R.string.kr_page_load_success))
                        ScriptEnvironmen.executeResultRoot(activity, loadSuccess, this)
                    }

                    handler.post {
                        val autoRunTask = if (actionsLoaded) null else object : AutoRunTask {
                            override val key = autoRunItemId
                            override fun onCompleted(result: Boolean?) {
                                if (result != true) {
                                    Toast.makeText(this@ActionPage, getString(R.string.kr_auto_run_item_losted), Toast.LENGTH_SHORT).show()
                                }
                            }
                        }

                        val fragment = ActionListFragment.create(items, actionShortClickHandler, autoRunTask,
                            ThemeModeState.getThemeMode()
                        )
                        supportFragmentManager.beginTransaction().replace(R.id.main_list, fragment).commitAllowingStateLoss()
                        hideDialog()
                        actionsLoaded = true
                    }
                } else {
                    if (loadFail.isNotEmpty()) {
                        showDialog(getString(R.string.kr_page_load_fail))
                        ScriptEnvironmen.executeResultRoot(activity, loadFail, this)
                        hideDialog()
                    }

                    handler.post {
                        Toast.makeText(this@ActionPage, getString(R.string.kr_page_load_fail), Toast.LENGTH_SHORT).show()
                    }
                    hideDialog()
                    finish()
                }
            }
        }).start()
    }

    fun openPageKre(pageNode: PageNode) {
        OpenPageHelper(this).openPage(pageNode)
    }

    override fun onDestroy() {
        this.setExcludeFromRecents()
        super.onDestroy()
    }

    private fun setExcludeFromRecents() {
        if (isTaskRoot) {
            try {
                val service = this.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                for (task in service.appTasks) {
                    task.setExcludeFromRecents(task.taskInfo.id == this.taskId)
                }
            } catch (_: Exception) {
            }
        }
    }
}
