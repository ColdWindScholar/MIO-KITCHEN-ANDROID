package com.omarea.common.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * RU: Минимальный typed UI-state для "запущена ли сейчас операция".
 *
 * Это упрощённая замена `Boolean isBusy` + `String? errorMessage`, оформленная
 * как sealed class. Новый код должен использовать [UiState] вместо россыпи
 * полей во ViewModel.
 *
 * EN: Minimal typed UI state for "is an operation in progress".
 *
 * A simpler replacement for `Boolean isBusy` + `String? errorMessage` modelled
 * as a sealed class. New code should use [UiState] instead of a scatter of
 * fields in a ViewModel.
 */
sealed class UiState<out T> {
    /**
     * RU: Операция не запущена, данных нет.
     * EN: No operation in progress, no data yet.
     */
    data object Idle : UiState<Nothing>()

    /**
     * RU: Операция выполняется.
     * @param message опциональное сообщение для прогресс-бара.
     *
     * EN: Operation in progress.
     * @param message optional message for the progress bar.
     */
    data class Loading(val message: String? = null) : UiState<Nothing>()

    /**
     * RU: Операция завершилась успешно.
     * EN: Operation finished successfully.
     */
    data class Success<T>(val data: T) : UiState<T>()

    /**
     * RU: Операция завершилась с ошибкой.
     * EN: Operation finished with an error.
     */
    data class Error(val message: String, val cause: Throwable? = null) : UiState<Nothing>()
}

/**
 * RU: Хранилище [UiState] на основе [MutableStateFlow].
 *
 * Используется во ViewModel: `private val state = UiStateHolder<FirmwareProfile>()`.
 * UI подписывается на `state.flow`, а ViewModel вызывает `state.setLoading()`,
 * `state.setSuccess(profile)`, `state.setError(...)` и т.д.
 *
 * EN: A [UiState] container backed by [MutableStateFlow].
 *
 * Used in a ViewModel: `private val state = UiStateHolder<FirmwareProfile>()`.
 * The UI subscribes to `state.flow`; the ViewModel calls `state.setLoading()`,
 * `state.setSuccess(profile)`, `state.setError(...)`, etc.
 */
class UiStateHolder<T> {
    private val _state = MutableStateFlow<UiState<T>>(UiState.Idle)
    val flow: StateFlow<UiState<T>> = _state.asStateFlow()

    val value: UiState<T> get() = _state.value

    fun setIdle() {
        _state.value = UiState.Idle
    }

    fun setLoading(message: String? = null) {
        _state.value = UiState.Loading(message)
    }

    fun setSuccess(data: T) {
        _state.value = UiState.Success(data)
    }

    fun setError(message: String, cause: Throwable? = null) {
        _state.value = UiState.Error(message, cause)
    }
}
