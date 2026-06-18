# Этап 8 — Модернизация UI

> Дата: 2026-06-17
> Статус: завершено
> Связанные этапы: после Stage 7 (firmware analyzer), перед Stage 9 (tests/CI)

---


Этап 3 уже включил ViewBinding и мигрировал существующие активности на него.
Этап 8 продолжает на этом фундаменте и добавляет оставшиеся части
UI-модернизации, заявленные в дорожной карте:

- Типизированный `UiState` / `UiStateHolder` — реактивный контейнер состояния
  (sealed class + `StateFlow`), чтобы новый код не раскидывал `Boolean isBusy`
  + `String? error` по полям ViewModel.
- Помощники нового Activity Result API (`OpenDocumentHelper`,
  `CreateDocumentHelper`, `UriPermissionPersistor`), чтобы новый код не
  использовал устаревшую пару `startActivityForResult` + `onActivityResult`.
- `FirmwareAnalysisViewModel` — первый ViewModel, связывающий новые слои:
  `FirmwareAnalyzer` (Stage 7) + `UiStateHolder` + `viewModelScope` +
  `Dispatchers.IO`.
- `FirmwareProfileFormatter` — чистый форматтер, превращающий `FirmwareProfile`
  в человекочитаемый текст. Чистый = тестируемый в JVM без Robolectric.

### Новые компоненты

```text
common/ui/
  UiState.kt                    # sealed class UiState<T> + UiStateHolder<T>

pio/ui/modern/
  ActivityResultHelpers.kt      # OpenDocumentHelper, CreateDocumentHelper,
                                # UriPermissionPersistor
  FirmwareAnalysisViewModel.kt  # ViewModel экрана анализа прошивки
  FirmwareProfileFormatter.kt   # чистый profile -> человекочитаемый текст

pio/src/test/java/.../ui/modern/
  FirmwareProfileFormatterTest.kt  # JVM-тесты
```

### Архитектурные правила

```text
UiState/UiStateHolder
  -> sealed class, без Context, без View
  -> на StateFlow, реактивный

FirmwareAnalysisViewModel
  -> extends ViewModel
  -> использует viewModelScope + Dispatchers.IO
  -> НЕ импортирует android.app.Activity, android.os.Handler
  -> НЕ использует activity!! / context!!

ActivityResultHelpers
  -> используют ActivityResultCaller (новый API)
  -> НЕ используют startActivityForResult / onActivityResult

FirmwareProfileFormatter
  -> чистый, без импортов View/widget
```

### CI-проверка

```bash
python3 tools/check-ui-modernization.py
```

Ожидаемый вывод:

```text
PASS: UiState/UiStateHolder typed reactive state is in place
PASS: OpenDocumentHelper/CreateDocumentHelper use modern Activity Result API
PASS: FirmwareAnalysisViewModel extends ViewModel and uses viewModelScope
PASS: FirmwareAnalysisViewModel has no Context/Handler/activity!! dependencies
PASS: FirmwareProfileFormatter is pure (no View/widget imports)
PASS: pio/build.gradle declares lifecycle + activity-ktx dependencies
PASS: UI modernization JVM unit tests are present
```

### Что намеренно НЕ сделано здесь

- Существующие активности (`MainActivity`, `ActionPage`, `ActivityFileSelector`,
  `SplashActivity`) НЕ мигрированы на новый ViewModel-паттерн — они всё ещё
  используют `Handler`, `Runnable` и старый `PageConfigReader`. Миграция
  отложена на отдельный этап UI-переписывания.
- Новый `FirmwareAnalysisViewModel` пока не подключён к реальной Activity —
  это рекомендуемая точка входа для будущего "unified firmware analysis screen"
  из дорожной карты.
- Compose пока не вводится.
