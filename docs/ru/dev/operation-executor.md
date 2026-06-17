# Этап 12 — OperationExecutor

> Дата: 2026-06-18
> Статус: завершено
> Связанные этапы: после Stage 11 (ToolchainInstaller), перед Stage 13 (targetSdk 35 runtime migration)

---

Stage 10 строит `OperationPlan` (типизированное описание того, что мы хотим
сделать). Stage 6 даёт `ShellRuntime` (типизированный способ запуска shell).
Stage 12 — мост между ними: `OperationExecutor`, который превращает план в
`ShellCommand` и запускает его через `ShellRuntime`.

## Что делает этот этап

До этого этапа не было типизированного способа:

- превратить `OperationPlan` в реальную shell-команду с правильными env-переменными
  (`OPERATION`, `FIRMWARE_PATH`, `WORK_DIR`, `TOOLS_DIR`, capability-флагами);
- соблюдать safety-профиль (отказаться запускать деструктивную операцию без root
  или в dry-run режиме);
- собрать `ShellEvent`-ы из операции в типизированный `ShellResult`;
- предпросмотреть команду без её реального запуска.

Этап 12 вводит:

- `PreparedExecution` — data-класс, оборачивающий `OperationPlan` + `ShellCommand`
  + флаг `dryRun`, с `ready` и `blockers()`.
- `OperationExecutor` — конвертирует `OperationPlan` в `PreparedExecution`,
  затем запускает через `ShellRuntime.execute()` (Flow) или
  `ShellRuntime.executeForResult()` (suspend).
- `OperationExecutionException` — типизированная ошибка для "не зарегистрирован
  скрипт" / "план не готов".

## Новый API

```text
common/operations/
  OperationExecutor.kt       # PreparedExecution + OperationExecutor +
                             # OperationExecutionException

common/src/test/java/.../operations/
  OperationExecutorTest.kt   # 11 JVM-тестов с DryRun + Fake shell runtime
```

## Контракт

```text
OperationExecutor
  -> prepare(plan, dryRun): PreparedExecution
  -> execute(prepared): Flow<ShellEvent>
  -> executeForResult(prepared): ShellResult (suspend)
  -> НЕ показывает UI
  -> НЕ хранит глобальное состояние
  -> делегирует выполнение shell в ShellRuntime
  -> отказывается запускать при plan.canExecute == false (кроме dryRun=true)
  -> отключает requiresRoot в dry-run режиме

PreparedExecution
  -> operation: FirmwareOperation
  -> plan: OperationPlan
  -> command: ShellCommand
  -> dryRun: Boolean
  -> ready: Boolean (plan.canExecute ИЛИ dryRun)
  -> blockers(): List<String> (пусто в dry-run)
```

## Переменные окружения, выставляемые executor'ом

```text
OPERATION             -> имя операции (например, UNPACK_BOOT_IMAGE)
FIRMWARE_TYPE         -> тип упаковки прошивки
FIRMWARE_ANDROID      -> подсказка версии Android (если известна)
FIRMWARE_PATH         -> путь к прошивке (для DirectPath/WorkspaceFile)
WORK_DIR              -> путь к workspace (из workspacePathProvider)
TOOLS_DIR             -> директория toolchain (из toolsDirProvider)
HAS_PAYLOAD_BIN       -> 1/0 capability-флаг
HAS_SUPER_IMAGE       -> 1/0 capability-флаг
HAS_DYNAMIC_PARTITIONS-> 1/0 capability-флаг
HAS_EROFS             -> 1/0 capability-флаг
HAS_EXT4              -> 1/0 capability-флаг
HAS_BOOT_IMAGE        -> 1/0 capability-флаг
HAS_VBMETA            -> 1/0 capability-флаг
USES_AVB              -> 1/0 capability-флаг
USES_AB               -> 1/0 capability-флаг
REQUIRES_16KB_CHECK   -> 1/0 capability-флаг
REQUIRED_TOOLS        -> список имён обязательных инструментов через пробел
```

## CI-проверка

```bash
python3 tools/check-operation-executor.py
```

Ожидаемый вывод:

```text
PASS: OperationExecutor with prepare/execute/executeForResult is in place
PASS: PreparedExecution wraps ShellCommand with operation env
PASS: defaultScriptLocator maps all FirmwareOperation values to script2/ paths
PASS: OperationExecutor tests cover prepare/execute/dry-run/blocked
PASS: OperationExecutor is pure (no android.app/android.widget imports)
```

## Что намеренно НЕ сделано здесь

- Executor пока НЕ вызывает `ToolchainInstaller` из Stage 11, чтобы
  убедиться в наличии инструментов. Ожидается, что вызывающая сторона
  сначала запустит installer. Высокоуровневый фасад, связывающий installer
  + executor, будет добавлен в будущем этапе.
- Executor полностью делегирует выполнение shell в `ShellRuntime`. Когда
  модуль `pio` подключит это к реальной Activity, он передаст
  `KeepShellRuntime` (переходная реализация Stage 6).
- Executor НЕ парсит вывод shell для обновления прогресса; это задача
  будущего `ShellEventParser`, который маппит stdout-строки в типизированные
  progress-события.
