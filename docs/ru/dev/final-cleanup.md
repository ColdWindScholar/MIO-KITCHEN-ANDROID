# Этап 23 — Финальная очистка legacy

> Дата: 2026-06-18
> Статус: завершено
> Связанные этапы: после Stage 22 (legacy removal)

---

Этап 22 удалил три крупных legacy-класса, но оставил три проблемы:

1. **ShellExecutor.execute() был сломан** — он вызывал
   `ScriptEnvironmen.getRuntime()`, который после Stage 22 возвращал `null`.
   Это ломало core-путь выполнения KrScript actions (DialogLogFragment +
   ActionListFragment.runHiddenAction).
2. **ExtractAssets всё ещё использовался** в `LegacyShellBridge.init` для
   извлечения toolkit-директории. Манифест не покрывал `.so` библиотеки.
3. **SplashActivity.BeforeStartThread** зависел от `common/shell/
   ShellExecutor` (другой класс) для `getSuperUserRuntime()`/`getRuntime()`.

Этап 23 исправляет все три.

## Что делает этот этап

### ShellExecutor.java → ShellExecutor.kt

Переписывает `krscript/executor/ShellExecutor` с Java на Kotlin. Новая
реализация:

- Создаёт собственный `Process` через `Runtime.exec("su")` /
  `Runtime.exec("sh")` (на основе `LegacyShellBridge.isRooted`).
- Строит full streaming command (env exports + executor path + script path)
  через `LegacyShellBridge.buildStreamingCommand()`.
- Пишет команду в stdin процесса.
- Сохраняет `SimpleShellWatcher` для streaming stdout/stderr в
  `ShellHandlerBase`.
- Возвращает `Process` для force-stop (используется кнопкой stop в
  DialogLogFragment).

Контракт с callers (`DialogLogFragment`,
`ActionListFragment.runHiddenAction`) сохранён 1:1.

### ScriptEnvironmen.getRuntime() возвращает реальный Process

Ранее `getRuntime()` возвращал `null` (placeholder Stage 22). Теперь
возвращает `Process` через `Runtime.exec("su")` / `Runtime.exec("sh")` —
используется `SplashActivity.BeforeStartThread`.

### LegacyShellBridge.buildStreamingCommand()

Новый метод, строит full shell command для streaming-выполнения:

```text
export TOOLKIT='<path>'
export START_DIR='<path>'
export PAGE_CONFIG_DIR='<path>'
...
<executorPath> "<scriptPath>" "<tag>"
exit
exit
```

Заменяет старую логику `ScriptEnvironmen.executeShell()`, которая писала
env + executor + script в `DataOutputStream`.

### LegacyShellBridge.installToolkit()

Новый private-метод, заменяет `ExtractAssets.extractResources()` для
извлечения toolkit:

1. Загружает `assets/toolchain/manifest.json` через `ToolManifestLoader`.
2. Конструирует `ToolchainInstaller` с `assetProvider`, открывающим потоки
   `assets/bin/<name>`.
3. Запускает `installer.install(<filesDir>/bin/, verifyChecksums=false)`.
4. Возвращает путь к директории с инструментами.

Использует тот же `ToolchainInstaller` (Stage 11), что и
`FirmwareOperationService.onCreate` (Stage 21) — унифицированный путь
установки инструментов.

### Манифест расширен

Добавлено 8 записей в `assets/toolchain/manifest.json`:
- `utils` (shell-утилита)
- 7 shared libraries: `libandroid-posix-semaphore.so`,
  `libandroid-support.so`, `libbz2.so.1.0`, `libffi.so`, `liblz4.so`,
  `liblzma.so.5`, `libz.so.1`

Манифест теперь объявляет все 21 файл из `assets/bin/` — 13 исполняемых
файлов + 8 shared libs/utils. Это позволяет `ToolchainInstaller` извлечь
всё.

### SplashActivity.BeforeStartThread обновлён

- Больше не импортирует `com.omarea.common.shell.ShellExecutor`.
- Использует `ScriptEnvironmen.getRuntime()` (теперь возвращает реальный
  Process) вместо `ShellExecutor.getSuperUserRuntime()` /
  `ShellExecutor.getRuntime()`.
- Использует `LegacyShellBridge.buildStreamingCommand()` вместо
  `ScriptEnvironmen.executeShell()`.

## CI-проверка

```bash
python3 tools/check-final-cleanup.py
```

Ожидаемый вывод:

```text
PASS: ShellExecutor.java replaced by ShellExecutor.kt (uses Runtime.exec directly)
PASS: ShellExecutor.execute() uses LegacyShellBridge.buildStreamingCommand (not ScriptEnvironmen.getRuntime)
PASS: ScriptEnvironmen.getRuntime() returns a real Process (not null)
PASS: LegacyShellBridge.buildStreamingCommand + installToolkit are declared
PASS: SplashActivity.BeforeStartThread uses new API (no common.shell.ShellExecutor dependency)
PASS: Manifest covers all 21 assets/bin/ files (13 executables + 8 shared libs/utils)
PASS: LegacyShellBridge.init uses ToolchainInstaller (not ExtractAssets) for toolkit
```

## Что намеренно НЕ сделано здесь

- `ExtractAssets.java` НЕ удалён — он всё ещё используется `RuntimeBinder`
  для извлечения `<resource file="...">` (KrScript XML resource tags) и
  `LegacyShellBridge` для построения env-переменных `PAGE_WORK_DIR`/
  `PAGE_WORK_FILE`. Это другой use case (извлечение отдельных файлов, не
  установка toolkit). Будущий этап может заменить его на типизированный
  `ResourceExtractor`.
- `common/shell/ShellExecutor.java` УДАЛЁН — это был utility-класс с
  `getSuperUserRuntime()`/`getRuntime()`, создающий Process-объекты. После
  Stage 23 ни один caller его не импортирует (SplashActivity использует
  `ScriptEnvironmen.getRuntime()`). Мёртвый код, удалён.
- `ShellTranslation.kt` НЕ удалён — это таблица переводов для `$({KEY})`
  плейсхолдеров, используется `LegacyShellBridge`, `KeepShellRuntime`,
  `RuntimeBinder`, `SimpleShellWatcher` и `SplashActivity`. Не legacy.
- Метод `ScriptEnvironmen.executeShell()` сохранён как no-op фасад — пишет
  `cmds` в `DataOutputStream` для обратной совместимости, но основной путь
  выполнения теперь идёт через `ShellExecutor.execute()` →
  `LegacyShellBridge.buildStreamingCommand()`.
