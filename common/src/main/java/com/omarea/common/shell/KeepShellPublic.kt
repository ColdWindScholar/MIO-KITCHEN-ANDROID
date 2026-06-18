package com.omarea.common.shell

/**
 * Created by Hello on 2018/01/23.
 */
object KeepShellPublic {

    private val defaultKeepShell = KeepShell()
    private val secondaryKeepShell = KeepShell()

    fun getDefaultInstance(): KeepShell {
        return if (defaultKeepShell.isIdle || !secondaryKeepShell.isIdle) {
            defaultKeepShell
        } else {
            secondaryKeepShell
        }
    }

    //执行脚本
    fun doCmdSync(cmd: String): String {
        return getDefaultInstance().doCmdSync(cmd)
    }

    //执行脚本
    fun checkRoot(): Boolean {
        return defaultKeepShell.checkRoot()
    }

    fun tryExit() {
        defaultKeepShell.tryExit()
        secondaryKeepShell.tryExit()
    }
}
