package com.mio.kitchen.permissions

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.omarea.common.ui.DialogHelper
import com.mio.kitchen.R
import kotlin.system.exitProcess

/**
 * RU: Проверка root-доступа.
 *
 * Stage 22: ранее использовал `KeepShellPublic.checkRoot()`, который владел
 * глобальным `KeepShell`-инстансом. Теперь `KeepShell` удалён, и
 * `CheckRootStatus` пробует root напрямую через `Runtime.exec("su")`.
 *
 * Результат кешируется в `lastCheckResult` — это сохраняет контракт с
 * `SplashActivity`, который проверяет `lastCheckResult` после callback.
 *
 * EN: Root access check.
 *
 * Stage 22: previously used `KeepShellPublic.checkRoot()`, which owned a
 * global `KeepShell` instance. Now `KeepShell` is removed, and
 * `CheckRootStatus` probes root directly via `Runtime.exec("su")`.
 *
 * The result is cached in `lastCheckResult` — this preserves the contract
 * with `SplashActivity`, which checks `lastCheckResult` after the callback.
 */
class CheckRootStatus(var context: Context, private var next: Runnable? = null) {
    private var myHandler: Handler = Handler(Looper.getMainLooper())

    private var therad: Thread? = null
    fun forceGetRoot() {
        if (lastCheckResult) {
            val n = next
            if (n != null) {
                myHandler.post(n)
            }
        } else {
            var completed = false
            therad = Thread {
                rootStatus = probeRoot()
                if (completed) {
                    return@Thread
                }

                completed = true

                if (lastCheckResult) {
                    val n2 = next
                    if (n2 != null) {
                        myHandler.post(n2)
                    }
                } else {
                    myHandler.post {
                        DialogHelper.confirm(context,  context.getString(R.string.warn_), context.getString(R.string.error_root),null,
                            DialogHelper.DialogButton(context.getString(R.string.btn_retry), {
                                if (therad != null && therad!!.isAlive && !therad!!.isInterrupted) {
                                    therad!!.interrupt()
                                    therad = null
                                }
                                forceGetRoot()
                            }), DialogHelper.DialogButton(context.getString(R.string.btn_exit), {
                                exitProcess(0)
                            }),
                            if (!context.resources.getBoolean(R.bool.force_root)) {
                                DialogHelper.DialogButton(context.getString(R.string.btn_skip), {
                                    val n3 = next
                                    if (n3 != null) {
                                        myHandler.post(n3)
                                    }
                                })
                            } else {null}
                        )
                    }
                }
            }
            therad!!.start()
            Thread(Runnable {
                Thread.sleep(1000 * 15)

                if (!completed) {
                    myHandler.post {
                        DialogHelper.confirm(context,
                        context.getString(R.string.error_root),
                        context.getString(R.string.error_su_timeout),
                        null,
                        DialogHelper.DialogButton(context.getString(R.string.btn_retry), {
                            if (therad != null && therad!!.isAlive && !therad!!.isInterrupted) {
                                therad!!.interrupt()
                                therad = null
                            }
                            forceGetRoot()
                        }),
                        DialogHelper.DialogButton(context.getString(R.string.btn_exit), {
                            exitProcess(0)
                        }))
                    }
                }
            }).start()
        }
    }

    /**
     * RU: Пробует запустить `su -c id` и проверяет, что вывод содержит
     *     `uid=0`. Возвращает `false` при любой ошибке.
     *
     * EN: Tries to run `su -c id` and verifies the output contains `uid=0`.
     *     Returns `false` on any error.
     */
    private fun probeRoot(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val stdout = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            exitCode == 0 && stdout.contains("uid=0")
        } catch (_: Throwable) {
            false
        }
    }

    companion object {
        private var rootStatus = false

        // 最后的ROOT检测结果
        val lastCheckResult: Boolean
            get() {
                return rootStatus
            }
    }
}
