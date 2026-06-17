package com.mio.kitchen;

import android.content.Context;

import com.omarea.krscript.model.PageNode;

import java.util.HashMap;

/**
 * RU: Конфигурация KrScript для MIO-KITCHEN.
 *
 * Stage 22: ранее этот класс также инициализировал `ScriptEnvironmen`
 * напрямую. Теперь `ScriptEnvironmen` инициализируется через
 * `LegacyShellBridge.init(context)`, который вызывается из `SplashActivity`
 * и `MainActivity`. Этот класс остаётся только как источник конфигурации
 * (пути к assets, page-config-sh, before-start-sh).
 *
 * EN: KrScript configuration for MIO-KITCHEN.
 *
 * Stage 22: previously this class also initialised `ScriptEnvironmen`
 * directly. Now `ScriptEnvironmen` is initialised via
 * `LegacyShellBridge.init(context)`, which is called from `SplashActivity`
 * and `MainActivity`. This class remains only as a configuration source
 * (asset paths, page-config-sh, before-start-sh).
 */
public class KrScriptConfig {

    private final static String TOOLKIT_DIR = "toolkit_dir";

    private final static String PAGE_LIST_CONFIG_SH = "page_list_config_sh";

    private final static String FAVORITE_CONFIG_SH = "favorite_config_sh";

    private static HashMap<String, String> configInfo;

    public KrScriptConfig init(Context context) {
        if (configInfo == null) {
            configInfo = new HashMap<>();
            configInfo.put("before_start_sh", "file:///android_asset/script/start.sh");
            configInfo.put("executor_core", "file:///android_asset/script2/executor.sh");
            configInfo.put("page_list_config", "file:///android_asset/script2/more.xml");
            configInfo.put("favorite_config", "file:///android_asset/script2/home.xml");
            configInfo.put("toolkit_dir", "file:///android_asset/bin");
            // RU: Stage 22 — ScriptEnvironmen.init теперь вызывается через
            //     LegacyShellBridge из SplashActivity/MainActivity. Здесь мы
            //     только заполняем конфигурацию.
            // EN: Stage 22 — ScriptEnvironmen.init is now called via
            //     LegacyShellBridge from SplashActivity/MainActivity. Here we
            //     only populate the configuration.
        }
        return this;
    }

    public HashMap<String, String> getVariables() {
        return configInfo;
    }

    public String getExecutorCore() {
        if (configInfo != null && configInfo.containsKey("executor_core")) {
            return configInfo.get("executor_core");
        }
        return "file:///android_asset/script2/executor.sh";
    }

    public String getToolkitDir() {
        if (configInfo != null && configInfo.containsKey(TOOLKIT_DIR)) {
            return configInfo.get(TOOLKIT_DIR);
        }
        return "file:///android_asset/bin";
    }

    public PageNode getPageListConfig() {
        if (configInfo != null) {
            PageNode pageInfo = new PageNode("");
            if (configInfo.containsKey(PAGE_LIST_CONFIG_SH)) {
                pageInfo.setPageConfigSh(configInfo.get(PAGE_LIST_CONFIG_SH));
            }
            if (configInfo.containsKey("page_list_config")) {
                pageInfo.setPageConfigPath(configInfo.get("page_list_config"));
            }
            return pageInfo;
        }
        return null;
    }

    public PageNode getFavoriteConfig() {
        if (configInfo != null) {
            PageNode pageInfo = new PageNode("");
            if (configInfo.containsKey(FAVORITE_CONFIG_SH)) {
                pageInfo.setPageConfigSh(configInfo.get(FAVORITE_CONFIG_SH));
            }
            if (configInfo.containsKey("favorite_config")) {
                pageInfo.setPageConfigPath(configInfo.get("favorite_config"));
            }
            return pageInfo;
        }
        return null;
    }

    public String getBeforeStartSh() {
        if (configInfo != null && configInfo.containsKey("before_start_sh")) {
            return configInfo.get("before_start_sh");
        }
        return "file:///android_asset/script/start.sh";
    }
}
