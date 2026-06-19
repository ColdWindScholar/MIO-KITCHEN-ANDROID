package com.mio.kitchen;

import android.content.Context;

import com.omarea.krscript.executor.ScriptEnvironmen;
import com.omarea.krscript.model.PageNode;

import java.util.HashMap;

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
            ScriptEnvironmen.init(context, getExecutorCore(), getToolkitDir());
        }
        return this;
    }

    public HashMap<String, String> getVariables() {
        return configInfo;
    }

    private String getExecutorCore() {
        if (configInfo != null && configInfo.containsKey("executor_core")) {
            return configInfo.get("executor_core");
        }
        return "file:///android_asset/script2/executor.sh";
    }

    private String getToolkitDir() {
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
