# Этап 5 — Разделение парсера и runtime KrScript

> Дата: 2026-06-17
> Статус: завершено
> Связанные этапы: после Stage 4 (storage/workspace), перед Stage 6 (shell runtime)

---


Старый `PageConfigReader` смешивал в одном классе три задачи:

1. **Разбор XML** — обход XML-дерева и построение узлов `NodeInfoBase`.
2. **Выполнение shell** — вызовы `ScriptEnvironmen.executeResultRoot` для
   атрибутов `desc-sh`, `summary-sh`, `support`/`visible` и элементов `<getstate>`.
3. **Побочные эффекты** — извлечение `<resource>` через `ExtractAssets`,
   показ `Toast` при ошибках, постинг в главный `Handler`.

Из-за этого класс нельзя было тестировать без Android-устройства, его было
сложно читать, и он блокировал остальные этапы дорожной карты (firmware
analyzer, shell runtime, UI modernization).

Этап 5 вводит чистый парсер, который:

- НЕ вызывает shell,
- НЕ показывает Toast и НЕ пишет в лог,
- НЕ извлекает ресурсы,
- НЕ зависит от `Context`, `Handler`, `Looper` или `Toast`,
- делегирует всё разрешение динамических значений в интерфейс `DynamicValueResolver`.

### Новые компоненты

```text
krscript/parser/
  PageConfigSource.kt           # абстракция над источником XML
  StreamPageConfigSource.kt     # тривиальный источник на основе потока (тесты/кэш)
  DynamicValueResolver.kt       # интерфейс + NoopDynamicValueResolver по умолчанию
  PageConfigParser.kt           # чистый XML -> List<NodeInfoBase>
  PageConfigRepository.kt       # источник -> парсер -> валидатор -> ParsedPageConfig

krscript/validator/
  PageConfigValidator.kt        # ValidationReport (ошибки + предупреждения)

krscript/runtime/
  RuntimeBinder.kt              # реализация DynamicValueResolver, вызывающая ScriptEnvironmen
                                # + точка входа extractResources

krscript/config/
  AndroidPageConfigSource.kt    # адаптер: PathAnalysis -> PageConfigSource

krscript/src/test/java/.../parser/
  PageConfigParserTest.kt       # JVM-тесты (без Robolectric)
```

### Ключевые контракты

```text
парсер      -> БЕЗ shell, БЕЗ UI, БЕЗ Context
валидатор   -> БЕЗ shell, БЕЗ UI, БЕЗ Context
RuntimeBinder -> единственное место, где вызывается ScriptEnvironmen
PageConfigSource -> открывает InputStream + сообщает absolutePath/parentDir
DynamicValueResolver -> resolveShellValue / isSupported / resolveText
```

### Обратная совместимость

Старый `PageConfigReader` сохранён без изменений, чтобы существующий UI-код
(`PageLayoutRender`, `ActionListFragment`, `PageMenuLoader`, …) продолжал
работать. Новая архитектура — рекомендуемый путь для нового кода; после этапа
UI modernization (Stage 8) старый ридер будет удалён.

### CI-проверка

```bash
python3 tools/check-parser-split.py
```

Ожидаемый вывод:

```text
PASS: KrScript parser/runtime split is in place
PASS: parser does not depend on Context/Handler/Toast
PASS: parser does not invoke ScriptEnvironmen or ExtractAssets
PASS: validator does not execute shell
PASS: RuntimeBinder is the single shell-bound DynamicValueResolver
PASS: parser unit tests and JVM test dependencies are present
PASS: legacy PageConfigReader is preserved for backward compatibility
```

### Почему это важно

Этап 6 (Shell runtime) требует парсер, который не вызывает shell напрямую,
чтобы shell runtime можно было заменить типизированным интерфейсом
`ShellRuntime`. Этап 7 (Firmware analyzer) требует того же парсера для
тестирования в JVM без Robolectric. Этап 8 (UI modernization) требует
парсер, переиспользуемый из ViewModel без участия `Activity`/`Fragment`.

### Что намеренно НЕ сделано здесь

- Старый `PageConfigReader` не удалён (оставлен для обратной совместимости).
- Извлечение `<resource>` всё ещё идёт через `ExtractAssets` из `RuntimeBinder`;
  отдельный этап заменит это типизированным `ResourceExtractor`.
- Рендерер страниц (`PageLayoutRender`) всё ещё использует старый ридер.
- Динамический загрузчик конфигурации `PageConfigSh` пока не мигрирован.
