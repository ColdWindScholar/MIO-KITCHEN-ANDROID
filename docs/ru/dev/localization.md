# Локализация MIO-KITCHEN

## Цель

Система локализации должна работать одинаково для трёх слоёв проекта:

1. Android UI: Activity, Fragment, layout, dialog, Toast.
2. KrScript XML DSL: `home.xml`, `mio.xml`, `more.xml`, динамические page/action/param-узлы.
3. Shell/runtime output: `tool.sh`, `executor.sh`, `start.sh` и команды, которые возвращают строки вида `@string/name`.

Пользователь выбирает язык один раз, а приложение применяет этот выбор в UI, XML-конфигах и shell-выводе.

## Где хранятся переводы

Переводы не централизуются в коде. Для каждой локализации есть отдельный Android resource-файл:

```text
values/strings.xml        # английский язык по умолчанию
values-en/strings.xml
values-ru/strings.xml
values-zh/strings.xml
values-ja/strings.xml
```

Такой набор есть в каждом Android-модуле:

```text
common/src/main/res/values*/strings.xml
krscript/src/main/res/values*/strings.xml
pio/src/main/res/values*/strings.xml
```

Английский — дефолтный язык. Если пользователь хочет другой язык, он выбирает его в UI.

## Разделение ответственности

```text
common
  └─ AppLanguage
       ├─ SharedPreferences name
       ├─ language key
       ├─ default language code en
       └─ shell env names APP_LANGUAGE / LC_CTYPE

pio
  └─ LanguageConfig
       ├─ список языков, показанных пользователю
       ├─ mapping code -> Locale
       ├─ mapping code -> label resource
       ├─ проверка поддерживаемого языка
       └─ создание локализованного Context

krscript
  └─ ScriptEnvironmen
       ├─ берёт сохранённый язык через AppLanguage
       └─ передаёт APP_LANGUAGE в shell

common
  └─ ShellTranslation
       ├─ резолвит @string/name и @string:name
       ├─ поддерживает аргументы через |
       ├─ кеширует resource id
       └─ не содержит runtime fallback-переводов
```

`AppLanguage` — это не хранилище переводов и не список языков UI. Это только общий технический контракт, который нужен `pio` и `krscript`, чтобы читать один и тот же пользовательский выбор.

## Текущие языки UI

Список находится в:

```text
pio/src/main/java/com/mio/kitchen/LanguageConfig.kt
```

| Код | Язык |
| --- | --- |
| `en` | English |
| `ru` | Русский |
| `zh` | 中文 |
| `ja` | 日本語 |

## Android UI

Все Activity, где нужен локализованный UI, должны использовать:

```kotlin
override fun attachBaseContext(newBase: Context) {
    super.attachBaseContext(LanguageConfig.wrap(newBase))
}
```

Основные входные точки уже используют эту схему:

```text
PIO.kt
SplashActivity.kt
MainActivity.kt
ActionPage.kt
ActivityFileSelector.kt
```

## Shell и XML DSL

В XML и shell можно использовать ссылки:

```text
@string/script_action_extract_rom
@string:shell_script_lost|/path/to/script.sh
```

`ShellTranslation` поддерживает два формата:

```text
@string/name
@string:name
```

Если ресурс не найден, это ошибка качества ресурсов. Runtime не подставляет fallback-текст. Строка остаётся видимой как исходная `@string/...` ссылка, а `tools/validate-localization.py` должен поймать проблему до релиза.

## Переменная APP_LANGUAGE

Shell runtime получает переменную окружения:

```text
APP_LANGUAGE=en
APP_LANGUAGE=ru
APP_LANGUAGE=zh
APP_LANGUAGE=ja
```

Она нужна для скриптов, которым важно самостоятельно выбирать локализованный вывод или поведение.

## Как добавить новый язык

1. Добавить новый `LanguageOption` в `LanguageConfig.supportedLanguages`.
2. Добавить строку `language_<name>` во все `pio/src/main/res/values*/strings.xml`.
3. Создать или обновить каталоги во всех модулях:
   - `common/src/main/res/values-<code>/strings.xml`
   - `krscript/src/main/res/values-<code>/strings.xml`
   - `pio/src/main/res/values-<code>/strings.xml`
4. Проверить, что новый язык не требует fallback-текстов в shell/XML.
5. Запустить:

```bash
python3 tools/validate-localization.py
```

## Правила качества

- Ключи в `values/strings.xml` и `values-*/strings.xml` внутри одного модуля должны совпадать.
- Форматные placeholders (`%s`, `%1$s`, `%d`) должны совпадать между языками.
- Все `@string/...` и `@string:...` ссылки в XML, shell и layout-файлах должны существовать.
- Runtime fallback-переводы не используются.
- Новые ключи нельзя добавлять только в один язык.
- Shell-строки не должны хранить реальные пользовательские данные внутри ресурсов. Пути, имена файлов и числа передаются через аргументы `|`.

## Проверки

```bash
python3 tools/validate-localization.py
```

Проверка покрывает:

- наличие всех `strings.xml`;
- совпадение ключей по локалям;
- совпадение placeholders;
- существование `@string/...` ссылок;
- корректное разделение ответственности между `AppLanguage` и `LanguageConfig`;
- отсутствие runtime fallback-переводов;
- наличие RU/EN документации.
