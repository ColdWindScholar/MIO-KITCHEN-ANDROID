# Этап 15 — Миграция UI на AppRuntimeProfile

> Дата: 2026-06-18
> Статус: завершено
> Связанные этапы: после Stage 14 (export policy), перед Stage 16 (static analysis)

---

Этап 8 ввёл `FirmwareAnalysisViewModel` и `UiState` как рекомендуемый путь для
нового UI-кода, но ни одна активность их не потребляла. Этап 15 закрывает этот
разрыв, добавляя `FirmwareAnalysisActivity` — reference-реализацию, связывающую
все компоненты из этапов 4-14.

## Что делает этот этап

Добавляет `FirmwareAnalysisActivity`, которая на одном экране:

1. Запрашивает runtime-разрешения через `RuntimePermissionHelper` (Stage 13).
2. Запускает SAF-picker через `OpenDocumentHelper` (Stage 8).
3. Разрешает выбранный `content://` URI в shell-path через
   `AndroidStorageGateway` (Stage 4) на `Dispatchers.IO`.
4. Передаёт shell-path в `FirmwareAnalysisViewModel.analyzeFile()` (Stage 8),
   который использует `FirmwareAnalyzerRegistry` (Stage 7).
5. Наблюдает `UiState<FirmwareProfile>` через `repeatOnLifecycle` (Stage 8).
6. Рендерит результат через `FirmwareProfileFormatter.detailed()` (Stage 8).
7. Запускает `FirmwareOperationService` (Stage 13) для foreground-state во
   время анализа.

Существующие активности (`MainActivity`, `ActionPage`, `ActivityFileSelector`,
`SplashActivity`) НЕ затронуты. Они продолжают использовать legacy-путь
`PageConfigReader` / `ScriptEnvironmen`. Эта активность — рекомендуемая точка
входа для нового UI-кода и шаблон для миграции существующих активностей.

## Новые компоненты

```text
pio/src/main/java/com/mio/kitchen/ui/modern/
  FirmwareAnalysisActivity.kt    # reference-активность, связывающая все этапы
```

## Регистрация в манифесте

```xml
<activity
    android:name=".ui.modern.FirmwareAnalysisActivity"
    android:label="@string/app_name"
    android:configChanges="keyboardHidden|orientation|uiMode|..."
    android:screenOrientation="portrait"
    android:exported="false" />
```

## CI-проверка

```bash
python3 tools/check-ui-migration.py
```

Ожидаемый вывод:

```text
PASS: FirmwareAnalysisActivity wires all Stage 4-13 components together
PASS: Activity uses ViewModel + StateFlow via repeatOnLifecycle
PASS: Activity uses RuntimePermissionHelper before launching picker
PASS: Activity uses OpenDocumentHelper for SAF picker
PASS: Activity uses AndroidStorageGateway for content:// resolution
PASS: Activity starts FirmwareOperationService for foreground state
PASS: Activity is registered in AndroidManifest
```

## Что намеренно НЕ сделано здесь

- Активность использует минимальный программно-построенный layout (без XML).
  Правильный layout-XML с Material Design-компонентами будет добавлен в
  следующем этапе.
- Активность пока НЕ запускает `OperationExecutor` — только анализирует
  прошивку. Следующий экран "operations" выберет операцию, построит
  `OperationPlan` и вызовет `OperationExecutor.executeForResult`.
- Существующая `MainActivity` НЕ заменена. Иконка запуска по-прежнему ведёт
  на `SplashActivity` → `MainActivity`. Будущий этап может добавить
  настройку-переключатель между legacy и modern UI.
- В активности нет собственных локализованных строк (используется
  `R.string.app_name` и захардкоженный английский). Локализация будет
  добавлена, когда layout будет переведён на XML.
