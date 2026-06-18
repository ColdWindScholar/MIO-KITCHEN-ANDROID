package com.mio.kitchen.ui.modern

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omarea.common.firmware.FirmwareAnalyzer
import com.omarea.common.firmware.FirmwareAnalyzerRegistry
import com.omarea.common.firmware.FirmwareAnalysisException
import com.omarea.common.firmware.FirmwareProfile
import com.omarea.common.firmware.FirmwareSource
import com.omarea.common.ui.UiState
import com.omarea.common.ui.UiStateHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * RU: ViewModel для экрана анализа прошивки.
 *
 * Это шаблон нового UI-слоя: он НЕ хранит `Context`, НЕ использует `Handler` и
 * НЕ запускает shell напрямую. Вместо этого он:
 *   - принимает [FirmwareAnalyzer] через конструктор (тестируемость);
 *   - выставляет [state] как [StateFlow] (реактивный UI);
 *   - запускает анализ в `viewModelScope` на `Dispatchers.IO`.
 *
 * EN: ViewModel for the firmware analysis screen.
 *
 * This is the template for the new UI layer: it does NOT hold a `Context`,
 * does NOT use `Handler`, does NOT run shell directly. Instead it:
 *   - takes [FirmwareAnalyzer] via constructor (testability);
 *   - exposes [state] as a [StateFlow] (reactive UI);
 *   - runs analysis in `viewModelScope` on `Dispatchers.IO`.
 */
class FirmwareAnalysisViewModel(
    private val analyzer: FirmwareAnalyzer = FirmwareAnalyzerRegistry.createDefault()
) : ViewModel() {

    private val _state = UiStateHolder<FirmwareProfile>()
    val state: kotlinx.coroutines.flow.StateFlow<UiState<FirmwareProfile>> = _state.flow

    /**
     * RU: Запускает анализ прошивки по пути файла.
     *
     * @param shellPath путь к файлу, готовый для shell (после `StorageGateway`).
     *
     * EN: Starts firmware analysis by file path.
     *
     * @param shellPath file path ready for shell (after `StorageGateway`).
     */
    fun analyzeFile(shellPath: String) {
        analyze(FirmwareSource.DirectPath(shellPath))
    }

    /**
     * RU: Запускает анализ прошивки по [FirmwareSource].
     * EN: Starts firmware analysis by [FirmwareSource].
     */
    fun analyze(source: FirmwareSource) {
        _state.setLoading(message = "Analyzing firmware…")
        viewModelScope.launch {
            try {
                val profile = withContext(Dispatchers.IO) {
                    analyzer.analyze(source)
                }
                _state.setSuccess(profile)
            } catch (e: FirmwareAnalysisException) {
                _state.setError(e.message ?: "Firmware analysis failed", e)
            } catch (e: Throwable) {
                _state.setError(e.message ?: e.javaClass.simpleName, e)
            }
        }
    }

    /**
     * RU: Сбрасывает состояние в Idle.
     * EN: Resets the state to Idle.
     */
    fun reset() {
        _state.setIdle()
    }
}
