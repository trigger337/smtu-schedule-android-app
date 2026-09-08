# Расписание СПбГМТУ — minimal native Android app

## Русский

Неофициальное приложение для просмотра расписания
[СПбГМТУ](https://www.smtu.ru/). Чистая Java, ноль внешних зависимостей
(без AndroidX и библиотек — только фреймворк `android.*`), один Activity с
интерфейсом, построенным кодом. Release-APK — около 46 КБ.

### Источник данных: только www.smtu.ru

У сайта нет API, поэтому приложение разбирает его серверные страницы —
единственный авторитетный источник, который университет правит сам:

    GET /ru/listschedule/                   -> все 449 id и названия групп
    GET /ru/viewschedule_new/<gid>/         -> расписание группы на весь семестр
    GET /ru/viewschedule_new/teacher/<pid>/ -> расписание преподавателя на семестр
                                               (pid = id из ссылки /ru/viewperson/<pid>/,
                                                которая стоит у каждого преподавателя)

Каждая страница расписания содержит весь семестр одним ответом. Парсер
ориентирован на карточное представление, общее для всех этих страниц:
дневные блоки → карточки по времени → контейнеры верхней/нижней недели.
Каждое занятие несёт:

- предмет, вид (Лекция/Практическое занятие/…), аудиторию
- класс `js-week-1`/`js-week-2` = верхняя/нижняя неделя
- атрибут `title` со **всеми точными датами проведения** — именно это делает
  просмотр по дням и неделям точным
- имя преподавателя и его viewperson-id (открывает его собственное расписание)

Чётность недели для плашки в шапке считается от якоря, который пересчитывается
при каждой загрузке по строке сайта «Сегодня: … верхняя/нижняя неделя», поэтому
остаётся верным и в следующие семестры. Запасной вариант: неделя 2026-08-31 —
верхняя.

Каждая загрузка сливается в кэш-файл конкретного расписания в приватном
хранилище приложения: офлайн-работа, мгновенный запуск, история переживает
правки на сайте.

### Возможности

- Группа выбирается один раз (поиск по всем 449 группам) и сохраняется;
  смена — тапом по названию группы в шапке
- Просмотр по неделе (Пн–Вс) и по дню с быстрым переключением дней, навигация
  ‹ › на весь семестр (одна загрузка — и всё расписание уже на устройстве)
- Компактные колонки времени (начало–конец в три строки) и аудитории
  (номер / корпус) — больше места предмету
- Светлая и тёмная темы по системной настройке (включая виджеты)
- Кнопка ⟳ в шапке обновляет расписание с smtu.ru
- Тап по занятию: день недели, время, вид, аудитория, чётность, диапазон дат,
  все точные даты и ссылка «🗺 на карту» (см. ниже)
- «О предмете»: все вхождения предмета
- «Преподаватель»: полное расписание преподавателя по всем группам,
  загружается с smtu.ru по его viewperson-id
- Для занятий без списка дат — запасное сопоставление по чётности и дню недели

### Виджеты (3 штуки)

Все виджеты сами следят за временем через AlarmManager (обновление каждую
минуту только во время пары или короткой перемены, иначе — на границе
состояния или в полночь), переживают перезагрузку и смену часового пояса,
обновляют устаревший кэш (старше 12 ч) в фоне и открывают приложение по тапу:

- **Таймер пар (1×1)** — радиальное кольцо: синее убывает до конца пары,
  жёлтое наполняется до начала следующей; после учебного дня — время
  следующей пары («завтра» / день недели)
- **Текущая пара (1×2)** — цветная полоса, метка «СЕЙЧАС» / «СЛЕДУЮЩАЯ
  ПАРА», предмет, время и обратный отсчёт («до конца 65 мин», «через
  5 мин») или аудитория
- **Расписание на день (2×3)** — блок «Сейчас» с тонкой полосой-таймером
  и список «Далее» (до 3 строк); в конце дня переключается на следующий
  учебный день с датой в заголовке

### Карты корпусов

Для корпусов А, Б и У в диалоге занятия есть строка «🗺 Корпус У, 5 этаж —
на карте»: официальный поэтажный план (smtu.ru/ru/page/201) скачивается
один раз (~2–4 МБ на корпус) в приватное хранилище и дальше работает
офлайн; нужный этаж — это первая цифра номера аудитории (страница N =
этаж N, проверено по штампам «N ЭТАЖ» в сканах). Просмотр — встроенный
`PdfRenderer` с масштабированием щипком. Для корпусов Г и М планов нет —
показывается адрес.

### Обновления

При запуске приложение тихо проверяет [релизы GitLab](https://gitlab.com/trigger337/smtu-schedule-android-app/-/releases);
если есть новая версия — тост в течение следующих 3 запусков, кнопка
«Скачать» в диалоге ⓘ. Никаких автоустановок.

### Структура

    app/src/main/
      AndroidManifest.xml
      java/com/korabel/schedule/
        Smtu.java               клиент smtu.ru: загрузка, парсер карточек, кэш, чётность
        MainActivity.java       интерфейс приложения (кнопки/списки кодом)
        Now.java                машина состояний «сейчас/перемена/далее» + общий путь данных виджетов
        WidgetUpdater.java      рендер всех виджетов + самопланируемые будильники
        TimerWidgetProvider.java / NameWidgetProvider.java / AgendaWidgetProvider.java
        SystemReceiver.java     BOOT/TIME_SET/TIMEZONE/… — перезапуск будильников
        UpdateChecker.java      проверка релизов GitLab
        Maps.java               планы корпусов: таблица, скачивание, PdfRenderer, зум
        Dates.java              помощники для дат

### Сборка

Требуются JDK 17, Android SDK (cmdline-tools + `platforms;android-34` +
`build-tools;34.0.0`). Gradle wrapper включён в репозиторий:

    ./gradlew assembleDebug
    # -> app/build/outputs/apk/debug/app-debug.apk
    adb install app/build/outputs/apk/debug/app-debug.apk

Продакшен-сборка:

    ./gradlew assembleRelease
    # -> app/build/outputs/apk/release/app-release.apk

Release собирается с R8-минификацией и сжатием ресурсов (~46 КБ). Для подписи:
создайте keystore и файл `keystore.properties` в корне проекта (ожидаемые
ключи — в `app/build.gradle`) — он подхватится автоматически. Без него
release-APK подписывается отладочным ключом: годится для личной установки,
но не для публикации.

CI (`.gitlab-ci.yml`) собирает debug на каждый push в main, а на теги
`X.Y.Z` — release+debug, публикует оба APK в пакеты `app_release`/`app_debug`
и создаёт релиз GitLab.

### Замечания

- Приложение обращается к `www.smtu.ru` напрямую с телефона. Сервер
  университета, похоже, отклоняет часть не-российских IP, поэтому при VPN с
  зарубежным выходом первая загрузка может не пройти — приложение продолжит
  работать из кэша. Сеть используется только при запуске и по кнопке ⟳
  (виджеты — раз в 12 ч при устаревшем кэше).
- Планы корпусов качаются с isu.smtu.ru (первый показ карты), дальше — офлайн.
- Min SDK 23 (Android 6.0), target SDK 34.

### Лицензия

MIT — см. [LICENSE](LICENSE). Приложение не связано с СПбГМТУ; все данные
расписания принадлежат университету.

---

## English

Unofficial schedule viewer for [SPbSMTU](https://www.smtu.ru/) (Saint Petersburg
State Marine Technical University). Pure Java, zero external dependencies
(no AndroidX, no libraries — framework `android.*` only), one Activity with
programmatic views. Release APK is ~46 KB.

### Data source: www.smtu.ru only

The site has no API, so the app parses its server-rendered pages — the
authoritative source, corrected and updated by the university directly:

    GET /ru/listschedule/                   -> all 449 group ids and names
    GET /ru/viewschedule_new/<gid>/         -> full-semester group schedule
    GET /ru/viewschedule_new/teacher/<pid>/ -> full-semester teacher schedule
                                               (pid = the /ru/viewperson/<pid>/ id
                                                linked from every lesson's teacher)

Each schedule page contains the whole semester in one response. The parser
targets the card view all these pages share: day blocks → time cards →
upper/lower week containers, where each lesson carries:

- subject, type (Лекция/Практическое занятие/…), room
- `js-week-1`/`js-week-2` class = верхняя/нижняя неделя
- a `title` attribute listing **every exact occurrence date** — this is what
  makes day/week views exact
- teacher name + viewperson id (enables the teacher's own full schedule)

Week parity for the navigation badge is computed from an anchor that is
recalibrated on every fetch from the site's own "Сегодня: … верхняя/нижняя
неделя" header, so it stays correct across semesters. Fallback constant: the
week of 2026-08-31 is upper.

Every fetch merges into a per-schedule cache file in the app's private storage:
offline support, instant startup, and past lessons survive page edits.

### Features

- Pick your group once (searchable list of all 449 groups) — saved on device;
  tap the group name in the header to change it later
- Week view (Mon–Sun) and day view with weekday quick-switch, ‹ › navigation
  across the whole semester (one fetch has it all)
- Compact time column (start–end on three lines) and room column
  (number / building) — more room for the subject
- System light/dark theme (widgets included)
- ⟳ button in the header re-fetches the schedule from smtu.ru
- Tap a lesson: weekday, time, type, room, parity, date range, every exact
  date, and a «🗺 map» link (see below)
- "О предмете": all occurrences of the subject
- "Преподаватель": the teacher's full schedule across all groups, fetched
  live from smtu.ru via their viewperson id
- Lessons whose rows carry no exact dates fall back to parity + weekday matching

### Widgets (3)

All widgets track time themselves via AlarmManager (per-minute updates only
during a lesson or a short break, otherwise at the next state boundary or
midnight), survive reboot and timezone changes, refresh a stale (>12 h)
cache in the background, and open the app on tap:

- **Таймер пар (1×1)** — radial ring: blue drains to the end of the lesson,
  yellow fills toward the next one; after the study day — the next lesson
  time («завтра» / weekday)
- **Текущая пара (1×2)** — colored stripe, «СЕЙЧАС» / «СЛЕДУЮЩАЯ ПАРА»
  label, subject, time range and a countdown («до конца 65 мин», «через
  5 мин») or the room
- **Расписание на день (2×3)** — a «Сейчас» block with a thin countdown bar
  plus a «Далее» list (up to 3 rows); falls over to the next study day with
  the date in the header

### Building maps

For buildings А, Б and У the lesson dialog has a «🗺 Корпус У, 5 этаж — на
карте» line: the official floor plan (smtu.ru/ru/page/201) downloads once
(~2–4 MB per building) into private storage and then works offline; the
floor is the leading digit of the room number (page N = floor N, verified
against the «N ЭТАЖ» stamps in the scans). Rendered with the framework
`PdfRenderer` and pinch-zoom. Buildings Г and М have no published plans —
an address dialog is shown instead.

### Updates

On launch the app silently checks the
[GitLab releases](https://gitlab.com/trigger337/smtu-schedule-android-app/-/releases);
on a newer version — a toast on the next 3 launches and a «Скачать» button
in the ⓘ dialog. No auto-installs.

### Layout

    app/src/main/
      AndroidManifest.xml
      java/com/korabel/schedule/
        Smtu.java               smtu.ru client: fetch, card-view parser, cache, parity
        MainActivity.java       the app UI (programmatic views)
        Now.java                the "now/break/next" state machine + shared widget data path
        WidgetUpdater.java      renders all widgets + self-rescheduling alarms
        TimerWidgetProvider.java / NameWidgetProvider.java / AgendaWidgetProvider.java
        SystemReceiver.java     BOOT/TIME_SET/TIMEZONE/… — restart the alarms
        UpdateChecker.java      GitLab release check
        Maps.java               building plans: table, download, PdfRenderer, zoom
        Dates.java              date helpers

### Building

Requirements: JDK 17, Android SDK (cmdline-tools + `platforms;android-34` +
`build-tools;34.0.0`). The Gradle wrapper is committed:

    ./gradlew assembleDebug
    # -> app/build/outputs/apk/debug/app-debug.apk
    adb install app/build/outputs/apk/debug/app-debug.apk

Production build:

    ./gradlew assembleRelease
    # -> app/build/outputs/apk/release/app-release.apk

The release build is R8-minified and resource-shrunk (~46 KB). For signing:
create a keystore and a `keystore.properties` in the project root (see
`app/build.gradle` for the expected keys) — it is picked up automatically.
Without it the release APK is signed with the debug key, which is fine for
personal sideloading but not for publishing.

CI (`.gitlab-ci.yml`) builds debug on every push to main; on `X.Y.Z` tags it
builds release+debug, uploads both APKs to the `app_release`/`app_debug`
packages and creates the GitLab release.

### Notes

- The app talks to `www.smtu.ru` directly from the phone. The university's
  server appears to refuse some non-Russian exit IPs, so on a VPN with a
  foreign endpoint the initial fetch may time out — the app then keeps
  working from cache. Fetches happen only at startup and on ⟳ (widgets:
  once per 12 h when the cache is stale).
- Building plans are fetched from isu.smtu.ru on first view, then offline.
- Min SDK 23 (Android 6.0), target SDK 34.

### License

MIT — see [LICENSE](LICENSE). Not affiliated with SPbSMTU; all schedule data
belongs to the university.
