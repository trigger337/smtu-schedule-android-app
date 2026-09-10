# Расписание СПбГМТУ — minimal native Android app

## Скачать · Download

APK публикуется только в [релизах GitLab](https://gitlab.com/trigger337/smtu-schedule-android-app/-/releases) —
на домашней странице проекта. Зеркало на GitHub содержит только исходный
код, без загрузок.

**[Скачать последнюю версию →](https://gitlab.com/trigger337/smtu-schedule-android-app/-/releases/permalink/latest)** ·
[все релизы](https://gitlab.com/trigger337/smtu-schedule-android-app/-/releases)

---

APKs are published on the
[GitLab releases](https://gitlab.com/trigger337/smtu-schedule-android-app/-/releases)
page only — the project home. The GitHub mirror carries the source code
only, no downloads.

**[Download the latest release →](https://gitlab.com/trigger337/smtu-schedule-android-app/-/releases/permalink/latest)** ·
[all releases](https://gitlab.com/trigger337/smtu-schedule-android-app/-/releases)

---

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

### Виджеты

Все виджеты сами следят за временем через AlarmManager (обновление каждую
минуту только во время пары или короткой перемены, иначе — на границе
состояния или в полночь), переживают перезагрузку и смену часового пояса
и обновляют устаревший кэш (старше 12 ч) в фоне. **Одиночный тап обновляет
виджет, двойной — открывает приложение** (RemoteViews не видит жестов,
поэтому двойной тап распознаётся по двум нажатиям за 350 м). Опубликованные
виджеты изменяют размер (resizeMode) и подстраивают ориентацию под форму:
шире, чем выше — горизонтальная компоновка, иначе — вертикальная. На экран
блокировки виджеты в Android поставить нельзя (удалены ещё в Android 5).

Общий элемент — **полоса ближайшего часа**: 60 минут от текущего момента,
клетки по 10 минут чередуются двумя оттенками (пара — синий/голубой,
перемена — оранжевый/жёлтый), слева стрелка «сейчас». Пара «рождается»
на правом краю за час до начала и доехав до стрелки — начинается.
Рисуется на Canvas в bitmap (RemoteViews не умеет динамические сегменты),
поэтому смена темы подхватывается битмапами со следующим тиком.

- **Таймер пар (1×1)** — радиальное кольцо: синее убывает до конца пары,
  жёлтое наполняется до начала следующей (окно 90 минут, дальше — пусто);
  после учебного дня — время следующей пары («завтра» / день недели)
- **Текущая пара (3×1)** — метка «СЕЙЧАС» / «СЛЕДУЮЩАЯ ПАРА», предмет,
  преподаватель, время и обратный отсчёт («до конца 65 мин», «через 5 мин»)
  или аудитория + полоса ближайшего часа; при сужении до **1×3** — карточка с
  **вертикальным текстом**: буквы стоят прямо и читаются сверху вниз,
  колонки идут справа налево (первая — предмет), полоса снизу
- **Расписание на день (4×2)** — на доработке и временно скрыт из выборки
  виджетов. Запланированный вид: половина карточки — текстовая панель
  («Далее», при нехватке места ближние пары в приоритете), вторая —
  **плитка-полоса**: полоса ближайшего часа на всю площадь, внутри только
  отсчёт; в вертикальной форме (2×4) половины меняются местами

### Карты корпусов

Для корпусов А, Б и У в диалоге занятия есть строка «🗺 Корпус У, 5 этаж —
на карте»: она открывает официальный поэтажный план (smtu.ru/ru/page/201,
хранится на isu.smtu.ru) в браузере — просмотр и масштабирование остаются
на стороне сайта. Нужный этаж — первая цифра номера аудитории. Для
корпусов Г и М планов нет — показывается адрес.

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
        WidgetArt.java          canvas-рисование: полоса часа, кольцо, панель расписания
        WidgetUpdater.java      рендер всех виджетов + самопланируемые будильники
        WidgetClickReceiver.java тап = обновление, двойной тап = открыть приложение
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

### Widgets

All widgets track time themselves via AlarmManager (per-minute updates only
during a lesson or a short break, otherwise at the next state boundary or
midnight), survive reboot and timezone changes, and refresh a stale (>12 h)
cache in the background. **A single tap refreshes the widget, a double tap
opens the app** (RemoteViews cannot see gestures, so the double tap is two
clicks within 350 ms). The published widgets are resizable and adapt their
orientation to their shape: wider than tall — horizontal layout, otherwise
vertical. Lock-screen widgets are not a thing in modern Android (removed
in 5.0).

The shared piece is the **next-hour timeline bar**: 60 minutes from now,
10-minute cells alternating two shades (lessons blue/light blue, breaks
orange/yellow), with a "now" arrow at the left edge. A lesson spawns at
the right edge an hour before it starts; reaching the arrow means it
begins. It is drawn on a Canvas into a bitmap (RemoteViews cannot draw
dynamic segments), so a theme flip is picked up by bitmaps on the next
tick.

- **Таймер пар (1×1)** — radial ring: blue drains to the end of the lesson,
  yellow fills toward the next one (a 90-minute window, empty beyond it);
  after the study day — the next lesson time («завтра» / weekday)
- **Текущая пара (3×1)** — «СЕЙЧАС» / «СЛЕДУЮЩАЯ ПАРА» label, subject,
  teacher, time range and a countdown («до конца 65 мин», «через 5 мин») or
  the room, plus the next-hour bar; when narrowed down to **1×3** the card
  switches to **vertical text**: upright letters reading top-to-bottom,
  columns running right-to-left (the subject first), bar at the bottom
- **Расписание на день (4×2)** — being reworked and temporarily hidden from
  the widget picker. The planned look: half the card is a text panel
  («Далее» rows; when space runs out the nearest lessons win), the other
  half is the **bar tile** — the next-hour bar filling the whole area with
  just the countdown inside; in a vertical shape (2×4) the halves stack

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
        WidgetArt.java          canvas drawing: hour bar, ring, agenda panel
        WidgetUpdater.java      renders all widgets + self-rescheduling alarms
        WidgetClickReceiver.java tap = refresh, double tap = open the app
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
