# MIO-KITCHEN localization

## Goal

Localization must work consistently across three project layers:

1. Android UI: activities, fragments, layouts, dialogs, and Toast messages.
2. KrScript XML DSL: `home.xml`, `mio.xml`, `more.xml`, and dynamic page/action/param nodes.
3. Shell/runtime output: `tool.sh`, `executor.sh`, `start.sh`, and commands that emit rows such as `@string/name`.

The user selects the language once, and the app applies that choice to UI, XML configs, and shell output.

## Where translations live

Translations are not centralized in code. Every localization has its own Android resource file:

```text
values/strings.xml        # English default language
values-en/strings.xml
values-ru/strings.xml
values-zh/strings.xml
values-ja/strings.xml
```

Each Android module has that resource structure:

```text
common/src/main/res/values*/strings.xml
krscript/src/main/res/values*/strings.xml
pio/src/main/res/values*/strings.xml
```

English is the default language. The user can explicitly choose another language in the UI.

## Responsibility split

```text
common
  └─ AppLanguage
       ├─ SharedPreferences name
       ├─ language key
       ├─ default language code en
       └─ shell env names APP_LANGUAGE / LC_CTYPE

pio
  └─ LanguageConfig
       ├─ language list shown to the user
       ├─ code -> Locale mapping
       ├─ code -> label resource mapping
       ├─ supported-language validation
       └─ localized Context creation

krscript
  └─ ScriptEnvironmen
       ├─ reads the stored language through AppLanguage
       └─ passes APP_LANGUAGE to shell

common
  └─ ShellTranslation
       ├─ resolves @string/name and @string:name
       ├─ supports arguments via |
       ├─ caches resource ids
       └─ does not contain runtime translation fallbacks
```

`AppLanguage` is not a translation store and not the UI language list. It is only the shared technical contract that lets `pio` and `krscript` read the same user selection.

## Current UI languages

The list lives in:

```text
pio/src/main/java/com/mio/kitchen/LanguageConfig.kt
```

| Code | Language |
| --- | --- |
| `en` | English |
| `ru` | Русский |
| `zh` | 中文 |
| `ja` | 日本語 |

## Android UI

Every Activity that needs localized UI should use:

```kotlin
override fun attachBaseContext(newBase: Context) {
    super.attachBaseContext(LanguageConfig.wrap(newBase))
}
```

The main entry points already do this:

```text
PIO.kt
SplashActivity.kt
MainActivity.kt
ActionPage.kt
ActivityFileSelector.kt
```

## Shell and XML DSL

XML and shell can use references:

```text
@string/script_action_extract_rom
@string:shell_script_lost|/path/to/script.sh
```

`ShellTranslation` supports both formats:

```text
@string/name
@string:name
```

A missing resource is a resource-quality error. Runtime does not inject fallback text. The original `@string/...` reference remains visible, and `tools/validate-localization.py` must catch the issue before release.

## APP_LANGUAGE environment variable

The shell runtime receives:

```text
APP_LANGUAGE=en
APP_LANGUAGE=ru
APP_LANGUAGE=zh
APP_LANGUAGE=ja
```

Scripts can use it when they need language-specific output or behavior.

## Adding a new language

1. Add a new `LanguageOption` to `LanguageConfig.supportedLanguages`.
2. Add `language_<name>` to every `pio/src/main/res/values*/strings.xml`.
3. Create or update resource folders in every module:
   - `common/src/main/res/values-<code>/strings.xml`
   - `krscript/src/main/res/values-<code>/strings.xml`
   - `pio/src/main/res/values-<code>/strings.xml`
4. Make sure the new language does not require fallback text in shell/XML.
5. Run:

```bash
python3 tools/validate-localization.py
```

## Quality rules

- Keys in `values/strings.xml` and `values-*/strings.xml` must match within each module.
- Format placeholders (`%s`, `%1$s`, `%d`) must match across languages.
- Every `@string/...` and `@string:...` reference in XML, shell, and layout files must exist.
- Runtime translation fallbacks are not used.
- New string keys must not be added to only one language.
- Shell string resources must not contain real user data. Paths, file names, and numbers must be passed through `|` arguments.

## Validation

```bash
python3 tools/validate-localization.py
```

The check covers:

- every required `strings.xml`;
- key parity across locales;
- placeholder parity;
- existing `@string/...` references;
- correct responsibility split between `AppLanguage` and `LanguageConfig`;
- disabled runtime translation fallbacks;
- RU/EN documentation presence.
