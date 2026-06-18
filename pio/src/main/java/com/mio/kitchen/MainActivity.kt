package com.mio.kitchen

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CompoundButton
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import com.mio.kitchen.databinding.ActivityMainBinding
import com.mio.kitchen.ui.TabIconHelper
import com.mio.kitchen.ui.modern.AppRuntimeStore
import com.mio.kitchen.ui.modern.RuntimePermissionHelper
import com.omarea.common.storage.AndroidStorageGateway
import com.omarea.common.storage.StorageResolveOptions
import com.omarea.common.storage.StorageResolveResult
import com.omarea.common.ui.DialogHelper
import com.omarea.common.ui.ProgressBarDialog
import com.omarea.krscript.config.PageConfigLoader
import com.omarea.krscript.config.PageConfigSh
import com.omarea.krscript.executor.ScriptEnvironmen
import com.omarea.krscript.runtime.LegacyShellBridge
import com.omarea.krscript.model.ClickableNode
import com.omarea.krscript.model.KrScriptActionHandler
import com.omarea.krscript.model.NodeInfoBase
import com.omarea.krscript.model.PageNode
import com.omarea.krscript.model.RunnableNode
import com.omarea.krscript.ui.ActionListFragment
import com.omarea.krscript.ui.ParamsFileChooserRender


class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val progressBarDialog = ProgressBarDialog(this)
    private var handler = Handler()
    private var krScriptConfig = KrScriptConfig()
    private val storageGateway: AndroidStorageGateway by lazy { AndroidStorageGateway(applicationContext) }

    private fun checkPermission(permission: String): Boolean = PermissionChecker.checkSelfPermission(this, permission) == PermissionChecker.PERMISSION_GRANTED

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageConfig.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // RU: Stage 22 — инициализируем LegacyShellBridge (замена старому
        //     ScriptEnvironmen.init, который ранее вызывался из KrScriptConfig).
        //     Bridge сам определяет root-статус и выбирает ShellRuntime.
        // EN: Stage 22 — initialise LegacyShellBridge (replacement for the
        //     old ScriptEnvironmen.init previously called from KrScriptConfig).
        //     The bridge determines root status and picks a ShellRuntime.
        LegacyShellBridge.init(this)
        ScriptEnvironmen.refreshTranslations(this)
        ThemeModeState.switchTheme(this)
        // RU: Stage 20 — обновляем AppRuntimeStore. Если SplashActivity уже
        //     инициализировал его, это просто обновит device-часть.
        // EN: Stage 20 — refresh AppRuntimeStore. If SplashActivity already
        //     initialised it, this just refreshes the device portion.
        AppRuntimeStore.init()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        //supportActionBar!!.elevation = 0f
        val toolbar = findViewById<View>(R.id.toolbar) as Toolbar
        setSupportActionBar(toolbar)
        setTitle(R.string.app_name)

        krScriptConfig = KrScriptConfig()


        binding.mainTabhost.setup()
        val tabIconHelper = TabIconHelper(binding.mainTabhost, this)

        binding.mainTabhost.setOnTabChangedListener {
            tabIconHelper.updateHighlight()
        }

        progressBarDialog.showDialog(getString(R.string.please_wait))
        Thread(Runnable {
            val page2Config = krScriptConfig.pageListConfig
            val favoritesConfig = krScriptConfig.favoriteConfig

            val pages = getItems(page2Config)
            val favorites = getItems(favoritesConfig)
            handler.post {
                progressBarDialog.hideDialog()

                if (favorites != null && favorites.size > 0) {
                    updateFavoritesTab(favorites, favoritesConfig)
                    tabIconHelper.newTabSpec(getString(R.string.tab_favorites), ContextCompat.getDrawable(this,
                        R.drawable.tab_favorites
                    )!!, R.id.main_tabhost_2
                    )
                } else {
                    binding.mainTabhost2.visibility = View.GONE
                }

                if (pages != null && pages.size > 0) {
                    updateMoreTab(pages, page2Config)
                    tabIconHelper.newTabSpec(getString(R.string.tab_pages), ContextCompat.getDrawable(this,
                        R.drawable.tab_pages
                    )!!, R.id.main_tabhost_3
                    )
                } else {
                    binding.mainTabhost3.visibility = View.GONE
                }
            }
        }).start()



        // RU: Stage 20 — заменяем устаревший прямой запрос storage-разрешений
        //     на RuntimePermissionHelper, который учитывает targetSdk 35
        //     (POST_NOTIFICATIONS на Android 13+, capped READ_EXTERNAL_STORAGE
        //     на Android ≤ 12).
        // EN: Stage 20 — replace the legacy direct storage-permission request
        //     with RuntimePermissionHelper, which respects targetSdk 35
        //     (POST_NOTIFICATIONS on Android 13+, capped READ_EXTERNAL_STORAGE
        //     on Android ≤ 12).
        if (!RuntimePermissionHelper.areAllGranted(this)) {
            RuntimePermissionHelper.requestMissing(this, 111)
        }
    }

    private fun getItems(pageNode: PageNode): ArrayList<NodeInfoBase>? {
        var items: ArrayList<NodeInfoBase>? = null

        if (pageNode.pageConfigSh.isNotEmpty()) {
            items = PageConfigSh(this, pageNode.pageConfigSh, null).execute()
        }
        if (items == null && pageNode.pageConfigPath.isNotEmpty()) {
            // RU: Stage 22 — заменяем legacy PageConfigReader на новый
            //     PageConfigLoader (PageConfigRepository + RuntimeBinder).
            // EN: Stage 22 — replace legacy PageConfigReader with the new
            //     PageConfigLoader (PageConfigRepository + RuntimeBinder).
            items = PageConfigLoader.load(this@MainActivity, pageNode.pageConfigPath, null)
        }

        return items
    }

    private fun updateFavoritesTab(items: ArrayList<NodeInfoBase>, pageNode: PageNode) {
        val favoritesFragment = ActionListFragment.create(items, getKrScriptActionHandler(pageNode, true), null,
            ThemeModeState.getThemeMode()
        )
        supportFragmentManager.beginTransaction().replace(R.id.list_favorites, favoritesFragment).commitAllowingStateLoss()
    }

    private fun updateMoreTab(items: ArrayList<NodeInfoBase>, pageNode: PageNode) {
        val allItemFragment = ActionListFragment.create(items, getKrScriptActionHandler(pageNode, false), null,
            ThemeModeState.getThemeMode()
        )
        supportFragmentManager.beginTransaction().replace(R.id.list_pages, allItemFragment).commitAllowingStateLoss()
    }

    private fun reloadFavoritesTab() {
        Thread(Runnable {
            val favoritesConfig = krScriptConfig.favoriteConfig
            getItems(favoritesConfig)?.run {
                handler.post {
                    updateFavoritesTab(this, favoritesConfig)
                }
            }
        }).start()
    }

    private fun reloadMoreTab() {
        Thread(Runnable {
            val page2Config = krScriptConfig.pageListConfig

            getItems(page2Config)?.run {
                handler.post {
                    updateMoreTab(this, page2Config)
                }
            }
        }).start()
    }

    private fun getKrScriptActionHandler(pageNode: PageNode, isFavoritesTab: Boolean): KrScriptActionHandler {
        return object : KrScriptActionHandler {
            override fun onActionCompleted(runnableNode: RunnableNode) {
                if (runnableNode.autoFinish ) {
                    finishAndRemoveTask()
                } else if (runnableNode.reloadPage) {
                    // TODO:多线程优化
                    if (isFavoritesTab) reloadFavoritesTab() else reloadMoreTab()
                }
            }

            override fun addToFavorites(clickableNode: ClickableNode, addToFavoritesHandler: KrScriptActionHandler.AddToFavoritesHandler) {
                val page = when(clickableNode){
                    is PageNode -> clickableNode
                    is RunnableNode -> pageNode
                    else -> return
                }

                val intent = Intent()

                intent.component = ComponentName(this@MainActivity.applicationContext, ActionPage::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)

                if (clickableNode is RunnableNode) {
                    intent.putExtra("autoRunItemId", clickableNode.key)
                }
                intent.putExtra("page", page)

                addToFavoritesHandler.onAddToFavorites(clickableNode, intent)
            }

            override fun onSubPageClick(pageNode: PageNode) {
                openPage(pageNode)
            }

            override fun openFileChooser(fileSelectedInterface: ParamsFileChooserRender.FileSelectedInterface): Boolean {
                return chooseFilePath(fileSelectedInterface)
            }
        }
    }

    private var fileSelectedInterface: ParamsFileChooserRender.FileSelectedInterface? = null
    private val ACTION_FILE_PATH_CHOOSER = 65400
    private val ACTION_FILE_PATH_CHOOSER_INNER = 65300

    private fun chooseFilePath(extension: String) {
        try {
            val intent = Intent(this, ActivityFileSelector::class.java)
            intent.putExtra("extension", extension)
            startActivityForResult(intent, ACTION_FILE_PATH_CHOOSER_INNER)
        } catch (ex: java.lang.Exception) {
            Toast.makeText(this, R.string.file_selector_open_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun chooseFilePath(fileSelectedInterface: ParamsFileChooserRender.FileSelectedInterface): Boolean {
        return try {
            this.fileSelectedInterface = fileSelectedInterface
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            intent.type = fileSelectedInterface.mimeType() ?: "*/*"
            intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true)
            startActivityForResult(intent, ACTION_FILE_PATH_CHOOSER)
            true
        } catch (ex: java.lang.Exception) {
            Toast.makeText(this, R.string.file_selector_open_failed, Toast.LENGTH_SHORT).show()
            false
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == ACTION_FILE_PATH_CHOOSER) {
            val result = if (data == null || resultCode != Activity.RESULT_OK) null else data.data
            if (fileSelectedInterface != null && result != null) {
                resolveSelectedUri(result, data?.flags ?: 0)
            } else {
                fileSelectedInterface?.onFileSelected(null)
                this.fileSelectedInterface = null
            }
        } else if (requestCode == ACTION_FILE_PATH_CHOOSER_INNER) {
            val absPath = if (data == null || resultCode != Activity.RESULT_OK) null else data.getStringExtra("file")
            fileSelectedInterface?.onFileSelected(absPath)
            this.fileSelectedInterface = null
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun resolveSelectedUri(uri: Uri, intentFlags: Int) {
        progressBarDialog.showDialog(getString(R.string.file_workspace_prepare))
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
                progressBarDialog.hideDialog()
                when (result) {
                    is StorageResolveResult.Resolved -> fileSelectedInterface?.onFileSelected(result.shellPath)
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

    fun openPage(pageNode: PageNode) {
        OpenPageHelper(this).openPage(pageNode)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }
    private fun joinQQGroup(): Boolean {
        val intent = Intent()
        intent.setData(Uri.parse("mqqopensdkapi://bizAgent/qm/qr?url=http%3A%2F%2Fqm.qq.com%2Fcgi-bin%2Fqm%2Fqr%3Ffrom%3Dapp%26p%3Dandroid%26jump_from%3Dwebapi%26k%3D21AK43VAjnenVLiSaFLdTLQS6-Uv_ITm"))
        // 此Flag可根据具体产品需要自定义，如设置，则在加群界面按返回，返回手Q主界面，不设置，按返回会返回到呼起产品界面    //intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            startActivity(intent)
            true
        } catch (e: java.lang.Exception) {
            // 未安装手Q或安装的版本不支持
            false
        }
    }
    private fun juanzen(): Boolean {
        val intent = Intent()
        intent.setData(Uri.parse("wxp://f2f0aLQl5w5FtoV2_xBiJKG8oZsXqsCFIn1HUaYTW2PNpPDZBD13d_09gfCY1HtsAVah"))
        return try {
            startActivity(intent)
            true
        } catch (e: java.lang.Exception) {
            // 未安装手Q或安装的版本不支持
            false
        }
    }
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.option_menu_info -> {
                val layoutInflater = LayoutInflater.from(this)
                val layout = layoutInflater.inflate(R.layout.dialog_about, null)
                val transparentUi = layout.findViewById<CompoundButton>(R.id.transparent_ui)
                val languageSpinner = layout.findViewById<Spinner>(R.id.language_spinner)
                val themeConfig = ThemeConfig(this)
                val add = layout.findViewById<Button>(R.id.button_add_qq)
                val wxpay = layout.findViewById<Button>(R.id.button_juanzeng)
                val languageLabels = LanguageConfig.supportedLanguages.map { getString(it.labelRes) }
                val languageAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, languageLabels)
                languageAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                languageSpinner.adapter = languageAdapter
                languageSpinner.setSelection(LanguageConfig.getSelectedIndex(this), false)
                wxpay.setOnClickListener{
                    Toast.makeText(this@MainActivity, if (juanzen()) {
                        getString(R.string.thanks_for_your_support)} else {
                        getString(R.string.wechat_is_not_installed)}, Toast.LENGTH_LONG).show()
                }
                add.setOnClickListener{
                    Toast.makeText(this@MainActivity, if (joinQQGroup()) {
                        getString(R.string.redirected_to_qq)} else {
                        getString(R.string.qq_is_not_installed)}, Toast.LENGTH_LONG).show()
                }
                transparentUi.setOnClickListener {
                    val isChecked = (it as CompoundButton).isChecked
                    if (isChecked && !checkPermission(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                        it.isChecked = false
                        Toast.makeText(this@MainActivity, R.string.kr_write_external_storage, Toast.LENGTH_SHORT).show()
                    } else {
                        themeConfig.setAllowTransparentUI(isChecked)
                    }
                }
                transparentUi.isChecked = themeConfig.getAllowTransparentUI()

                val dialog = DialogHelper.customDialog(this, layout)
                languageSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                        val selectedLanguage = LanguageConfig.supportedLanguages[position].code
                        if (LanguageConfig.setLanguage(this@MainActivity, selectedLanguage)) {
                            ScriptEnvironmen.refreshTranslations(LanguageConfig.wrap(applicationContext))
                            dialog.dismiss()
                            recreate()
                        }
                    }

                    override fun onNothingSelected(parent: AdapterView<*>?) {
                    }
                }
            }
            R.id.option_menu_reboot -> {
                DialogPower(this).showPowerMenu()
            }

        }
        return super.onOptionsItemSelected(item)
    }
}
