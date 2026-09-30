---
name: Veilark Windows
description: "Fluent-inspired настольная оболочка Compose для спокойного управления VPN в Windows"
colors:
  primary: "light-dark(#275D8C, #A1CAFC)"
  on-primary: "light-dark(#FFFFFF, #003257)"
  primary-container: "light-dark(#D2E4FF, #064A75)"
  on-primary-container: "light-dark(#0B446F, #D2E4FF)"
  secondary-container: "light-dark(#DCE3EA, #3C4852)"
  on-secondary-container: "light-dark(#27323B, #DCE3EA)"
  degraded: "light-dark(#7A590C, #E2C38C)"
  degraded-container: "light-dark(#FFDF9B, #5C4300)"
  on-degraded-container: "light-dark(#3E2E00, #FFDF9B)"
  background: "light-dark(#F9F9FC, #111317)"
  surface-lowest: "light-dark(#FFFFFF, #0C0E12)"
  surface-low: "light-dark(#F3F3F7, #191C20)"
  surface: "light-dark(#EDEDF2, #1D2024)"
  surface-high: "light-dark(#E7E8ED, #272A2E)"
  text: "light-dark(#191C20, #E2E2E7)"
  muted-text: "light-dark(#43474E, #C3C7CF)"
  outline: "light-dark(#73777F, #8D9199)"
  error: "light-dark(#BA1A1A, #FFB4AB)"
  error-container: "light-dark(#FFDAD6, #93000A)"
  on-error-container: "light-dark(#410002, #FFDAD6)"
typography:
  headline:
    fontFamily: "local Segoe UI / bundled Noto Sans fallback"
    fontSize: "28sp"
    fontWeight: 600
    lineHeight: "36sp"
  headline-compact:
    fontFamily: "local Segoe UI / bundled Noto Sans fallback"
    fontSize: "22sp"
    fontWeight: 600
    lineHeight: "28sp"
  title:
    fontFamily: "local Segoe UI / bundled Noto Sans fallback"
    fontSize: "20sp"
    fontWeight: 500
    lineHeight: "26sp"
  title-compact:
    fontFamily: "local Segoe UI / bundled Noto Sans fallback"
    fontSize: "16sp"
    fontWeight: 500
    lineHeight: "24sp"
  body:
    fontFamily: "local Segoe UI / bundled Noto Sans fallback"
    fontSize: "16sp"
    fontWeight: 400
    lineHeight: "24sp"
  body-compact:
    fontFamily: "local Segoe UI / bundled Noto Sans fallback"
    fontSize: "14sp"
    fontWeight: 400
    lineHeight: "20sp"
  caption:
    fontFamily: "local Segoe UI / bundled Noto Sans fallback"
    fontSize: "12sp"
    fontWeight: 400
    lineHeight: "16sp"
  label:
    fontFamily: "local Segoe UI / bundled Noto Sans fallback"
    fontSize: "14sp"
    fontWeight: 500
    lineHeight: "20sp"
rounded:
  badge: "4dp"
  compact: "4dp"
  control: "8dp"
  container: "8dp"
  large: "12dp"
  full: "999dp"
spacing:
  xxs: "4dp"
  xs: "8dp"
  sm: "12dp"
  md: "16dp"
  lg: "20dp"
  xl: "24dp"
components:
  button-primary:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on-primary}"
    typography: "{typography.label}"
    rounded: "{rounded.container}"
    padding: "0 24dp"
    height: "52dp"
  button-tonal:
    backgroundColor: "{colors.secondary-container}"
    textColor: "{colors.on-secondary-container}"
    typography: "{typography.label}"
    rounded: "{rounded.container}"
    padding: "0 24dp"
    height: "52dp"
  button-outlined:
    backgroundColor: "transparent"
    textColor: "{colors.primary}"
    typography: "{typography.label}"
    rounded: "{rounded.compact}"
    padding: "0 14dp"
  navigation-item-selected:
    backgroundColor: "{colors.secondary-container}"
    textColor: "{colors.on-secondary-container}"
    typography: "{typography.label}"
    rounded: "{rounded.compact}"
    padding: "0 10dp"
    height: "40dp"
  card-section:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.text}"
    rounded: "{rounded.container}"
    padding: "caller-defined"
  field-outlined:
    backgroundColor: "transparent"
    textColor: "{colors.text}"
    typography: "{typography.body-compact}"
    rounded: "component default or local override (search: 12dp)"
    padding: "component/caller-defined"
  engine-choice-selected:
    backgroundColor: "{colors.primary-container}"
    textColor: "{colors.on-primary-container}"
    typography: "{typography.label}"
    rounded: "18dp (local explicit override)"
    padding: "0 14dp"
    height: "48dp"
  status-mark:
    backgroundColor: "neutral, state-dependent"
    textColor: "neutral, state-dependent"
    rounded: "{rounded.full}"
    size: "72dp outer indicator"
---

# Design System: Veilark Windows

## Overview

**Creative North Star: "Veilark Control Desk — спокойная консоль соединения"**

«Спокойная консоль соединения» — это строгий визуальный мир для ежедневной Windows-утилиты. Настольная оболочка заимствует направление Fluent: постоянная боковая навигация, локальная Windows-гарнитура и более собранная геометрия. Рендерер и компоненты остаются Compose Desktop / Material 3; это не нативные WinUI-контролы и не миграция на Windows App SDK. Системная оконная рамка AWT сохраняется. Mica, Acrylic и прозрачный системный backdrop не реализованы.

Нейтральные тональные плоскости несут структуру, а синий цвет сообщает действие, выбор и подтверждённое положительное состояние. Предупреждение и ошибка остаются локальными сигналами. Анимация служит только смене состояния — она подтверждает переход, не превращая VPN-клиент в демонстрационный экран.

**Key Characteristics:**

- Компактная рабочая плотность вместо растянутого мобильного набора карточек.
- Локальная Segoe UI с bundled Noto Sans fallback и стандартные Compose Material 3-контролы.
- Нейтральная тональная иерархия с синими семантическими акцентами.
- Цвет состояния локализован в метках, знаках, тексте и основном действии.
- Короткое движение по состоянию и адаптивное поведение wide/tight.

## Colors

Палитра сохраняет холодную нейтральную Material-схему с парными светлыми и тёмными значениями. `VeilarkTheme` выбирает схему через Compose `isSystemInDarkTheme()`. Frontmatter описывает значения исходника, а не является исполняемым конфигом. `surface` здесь означает `surfaceContainer`; базовый Material `surface` совпадает с `background`.

### Primary

- **Veilark Blue** (`primary`): основное действие, выбранное состояние, активные ссылки и положительное доказательство соединения.
- **Veilark Blue Container** (`primary-container`): спокойное выделение выбранного режима или активной сущности без заливки насыщенным акцентом.

### Secondary

- **Quiet Slate Selection** (`secondary-container`): выбранная навигация, тональные вторичные действия и постоянные информационные полосы.

### Tertiary

- **Degraded Amber** (`degraded`, `degraded-container`): только нестабильное или требующее внимания состояние, которое ещё не является ошибкой.
- **Guarded Red** (`error`, `error-container`): только сбой, недоступность или разрушительное подтверждение.

### Neutral

- **Cool Canvas** (`background`): общий фон окна.
- **Tonal Planes** (`surface-lowest`, `surface-low`, `surface`, `surface-high`): последовательные уровни структуры от вложенного индикатора до выделенного рабочего контейнера.
- **Windows Ink** (`text`): основной текст и заголовки.
- **Muted Slate Ink** (`muted-text`): пояснения, метаданные и вторичная информация.
- **Quiet Outline** (`outline`): границы стандартных outlined-контролов и нейтральные индикаторы.

### Named Rules

**The Local Signal Rule.** Цвет состояния остаётся внутри компактной метки, иконки, текста или действия; большие рабочие контейнеры сохраняют нейтральный тон.

**The Semantic Blue Rule.** Синий означает действие, выбор или проверенное положительное состояние, а не украшение.

## Typography

**Display Font:** локально установленная Segoe UI через Compose platform `Font(File, weight)`.

**Body Font:** та же Segoe UI. `Design.kt` ищет `segoeui.ttf` и `seguisb.ttf` в `%WINDIR%/Fonts` (резервный путь `C:/Windows/Fonts`); Medium и SemiBold используют установленный semibold-файл. При отсутствии файлов или ошибке создания семейства используется bundled Noto Sans Regular/Medium/SemiBold. Системные файлы шрифтов не распространяются с приложением; Segoe UI Variable в этой итерации не загружается.

**Label/Mono Font:** то же семейство с fallback для меток; `FontFamily.Monospace` для технического журнала.

**Character:** спокойная нативная типографика без отдельного рекламного display-шрифта. Иерархия строится размером и насыщенностью Material-ролей, поэтому интерфейс остаётся знакомым при системном масштабировании Windows.

### Hierarchy

- **Headline** (`typography.headline`): заголовок рабочей страницы.
- **Compact Headline** (`typography.headline-compact`): главное состояние внутри крупного функционального контейнера.
- **Title** (`typography.title`): заголовок раздела или критического сообщения.
- **Compact Title** (`typography.title-compact`): заголовки карточек, строк настроек и числовые значения.
- **Body** (`typography.body`): основной ввод и развёрнутый текст.
- **Compact Body** (`typography.body-compact`): пояснения и служебные сообщения.
- **Caption** (`typography.caption`): версии, протоколы и компактные метаданные.
- **Label** (`typography.label`): кнопки, навигация и статусы.

### Named Rules

**The Native Voice Rule.** На Windows предпочтительна установленная Segoe UI, но отсутствие гарнитуры не должно мешать запуску. Характер создают иерархия, ясный текст и умеренная насыщенность, а не отдельный рекламный шрифт.

## Layout

Система строится на компактном ритме 4/8/10/12/14/16 dp. Общая страница имеет 12 dp горизонтального и 10 dp вертикального отступа; Home — 16/8 dp. Ширина назначается по задаче: Home — 440 dp, Diagnostics — 520 dp, Updates — 580 dp, Profiles и Routing — 600 dp. Прокручиваемая страница занимает доступную высоту; локальные контейнеры могут иметь собственные отступы.

Начальный размер окна — 900 × 680 dp, минимальный — 540 × 480 dp. Минимум AWT пересчитывается в физические пиксели через текущую Compose density; системная рамка и insets уменьшают полезную область. При ширине контента от 760 dp боковая навигация раскрыта до 184 dp; ниже — rail 56 dp. Все шесть разделов остаются доступны в обоих режимах, в компактном режиме названия доступны через tooltip и accessibility description. Нижней панели и hamburger-меню нет. Главный сценарий сохраняет одну центральную ось: знак состояния → состояние и трафик → отдельная кнопка действия → выбор ядра → сервер → вторичные действия. Прокрутка сохраняет доступ к подробностям при малой высоте и длинных сообщениях; видимость всех контролов без прокрутки не гарантируется.

**The Compact Rhythm Rule.** Сначала используйте устойчивую шкалу интервалов, а локальные поправки применяйте только для выравнивания Compose Material-контрола.

**The Adaptive Continuity Rule.** Tight-layout перестраивает порядок и ширину, но не скрывает доказательства состояния и основные действия.

**The Centered Action Spine Rule.** Домашний экран не меняет визуальную ось на промежуточной ширине: главное действие остаётся в центре, а все зависимые элементы читаются строго сверху вниз.

## Elevation & Depth

Постоянные секции плоские по умолчанию. Глубина строится сменой тональных уровней `surface-lowest` → `surface-low` → `surface` → `surface-high`; Material 3-меню и диалоги используют Compose elevation-модель. Tooltip боковой навигации имеет локальную тень 2 dp.

### Named Rules

**The Tonal Depth Rule.** Постоянная структура отделяется тональным уровнем поверхности, а тень остаётся свойством временного Compose-слоя, а не декором карточки.

## Shapes

Общая тема задаёт радиусы 4 dp для extraSmall/small, 8 dp для medium и 12 dp для large/extraLarge. Боковая навигация использует 4 dp. Это Fluent-inspired уточнение общей геометрии, не полная замена всех компонентов: локальные явные радиусы сохраняются, например 16 dp у EndpointPicker, 20 dp у EngineSelector и 18 dp у EngineChoice. Круг остаётся статусным знаком и progress-индикатором, а pill — статусной меткой.

**The Bounded Curve Rule.** Новая общая геометрия строится на 4/8/12 dp; локальные overrides следует учитывать отдельно, не объявляя весь интерфейс приведённым к этой шкале.

## Components

### Buttons

- **Shape:** основное действие использует крупную форму контейнера; компактные вторичные действия используют compact/control-радиусы.
- **Primary:** сплошной `primary` с `on-primary`, label-типографикой и высотой 52 dp у доминирующей кнопки подключения.
- **Tonal:** `secondary-container` для остановки, отключения и вторичного акцента без тревожной окраски.
- **Outlined:** прозрачная поверхность и стандартная Material-обводка для обновления, проверки, отмены и других вспомогательных действий.
- **Hover / Focus / Disabled:** стандартные Compose Material 3 state layers и focus-поведение; занятые состояния получают progress-индикатор и явный текст. Это не WinUI state layers.

### Chips

- **Style:** малая текстовая метка использует `secondary-container`, label-типографику и badge-радиус.
- **State:** метка сообщает происхождение или другой устойчивый атрибут; она не заменяет интерактивный выбор.

### Cards / Containers

- **Corner Style:** container-радиус.
- **Background:** `surface` для обычной секции, `surface-high` для усиленной рабочей плоскости, `surface-lowest` для локального вложенного статуса.
- **Shadow Strategy:** без пользовательской тени; см. Tonal Depth Rule.
- **Internal Padding:** обычно `spacing.md`, с `spacing.xl` у крупного системного сообщения.

### Inputs / Fields

- **Style:** стандартный Material 3 `OutlinedTextField` с читаемой подписью и поддерживающим текстом; геометрия зависит от компонента и локального override (поиск использует 12 dp).
- **Focus:** Compose Material 3 focus-состояние и акцентная обводка.
- **Error / Disabled:** блокировка сохраняет читаемый текст и объяснение; ошибка не кодируется одной обводкой без сообщения.

### Navigation

- **Style:** постоянная левая навигация 184/56 dp; шесть пунктов высотой 40 dp: подключение, подписки, маршруты, диагностика, обновления, журнал. При недостатке высоты навигация прокручивается. Верхняя Compose `TopAppBar` содержит фирменный знак, название, статус и переключатель языка; её высота определяется компонентом, не фиксирована в 48 dp.
- **Semantics:** выбранный пункт имеет tonal-подложку и `selected = true`; каждый пункт получает локализованный `contentDescription` и tooltip. В раскрытом режиме название также видно рядом с иконкой. Эти строки построены в Compose, не являются WinUI NavigationView.

### Engine Choice

- **Style:** парный выбор в центральной области подключения; выбранный вариант получает `primary-container`, невыбранный — нейтральную поверхность. Локальная геометрия группы и элементов сохраняется из существующего Compose UI.
- **Semantics:** группа использует selectable-group, каждый вариант — `Role.RadioButton`; отсутствие профиля объясняется текстом.

### Status Mark

- **Style:** внешняя область знака состояния — 72 dp; это не кнопка. Внутри отображается фирменная сова на нейтральном диске, а занятое состояние добавляет progress-ring. Подключение, отключение, отмена попытки и повторная остановка выполняются отдельной подписанной кнопкой. `Connected` в верхней метке называется «Подключено», не самостоятельным утверждением о безопасности.
- **Motion:** цвет знака меняется за 180 ms; занятое состояние использует non-bouncy spring с масштабом 98%. Переход страниц использует fade-in 160 ms / fade-out 100 ms; проблемная полоса — 160/120 ms. Постоянной декоративной анимации нет; progress-индикаторы служат занятости.
- **Evidence:** любой цвет сопровождается русским текстовым состоянием, а при подключении — длительностью и трафиком.

### Setting Row

- **Style:** вся строка является целью переключения; заголовок и пояснение остаются рядом со стандартным Material 3 switch.
- **Semantics:** `Role.Switch`, объединённые дочерние элементы и явное `stateDescription` «Включено»/«Выключено».

### Named Rules

**The Semantic Control Rule.** Сохраняйте Material-компонент и соответствующий accessibility role; иконка внутри подписанного контроля не заменяет его текстовую метку.

**The State Motion Rule.** Движение коротко подтверждает смену состояния, раскрытие или навигацию и никогда не становится постоянным декоративным эффектом.

## Localization

Русский и английский переключаются кнопкой EN/RU в верхней панели. При первом запуске используется системная локаль Windows, затем выбор сохраняется атомарно в пользовательском каталоге Veilark. Пользовательские подписи используют `UiLanguage.text(ru, en)` и `LocalUiLanguage`; технические значения, названия ядер и протоколов не переводятся.

Композиция не полагается на одинаковую длину переводов: заголовки и подписи имеют предсказуемые `maxLines`/ellipsis, действия обновления вынесены из trailing-области строки, а команды журнала используют доступные icon-button с локализованным `contentDescription`. Ручные правила маршрутизации складываются в колонку при ширине менее 520 dp.

**Localization acceptance:** RU и EN переключаются без перезапуска; app bar, заголовки, обновления и routing не создают горизонтальный overflow при минимальном размере; строки ошибок сначала сокращаются до пользовательского сообщения, а полная техническая запись остаётся в журнале.

**Known risk:** inline-пары строк просты для двух языков, но не дают контроля полноты каталога и plural rules. При добавлении третьего языка пары следует механически перенести в типизированный `UiTextKey`/resource-каталог; до этого CI должен проверять отсутствие прямых пользовательских строк вне локализационных вызовов.

## Do's and Don'ts

### Do:

- **Do** используйте основной синий для действия, выбора и положительного доказательства состояния.
- **Do** оставляйте большие контейнеры нейтральными и стройте иерархию тональными уровнями поверхности.
- **Do** используйте установленную Segoe UI с bundled fallback и зафиксированную Compose-иерархию текста.
- **Do** используйте общую шкалу радиусов 4/8/12 dp и явно учитывайте существующие локальные overrides.
- **Do** перестраивайте параллельные области в колонку на tight-ширине, не скрывая содержание.
- **Do** сопровождайте цвет состояния ясным русским текстом и доступной семантикой роли или состояния.
- **Do** используйте короткое движение только для обратной связи о состоянии.

### Don't:

- **Don't** заливайте статусным цветом большие карточки или добавляйте неоновое свечение.
- **Don't** добавляйте декоративную display-гарнитуру, капс или letter-spacing ради «технологичности».
- **Don't** заменяйте стандартные кнопки, поля, switch, меню и диалоги веб-подобными самодельными контролами.
- **Don't** используйте цвет как единственное доказательство подключения, ошибки или доступности.
- **Don't** добавляйте постоянные пользовательские тени там, где иерархию уже выражает тональная поверхность.

## Verification and release boundary

Этот документ описывает локальную кандидатную итерацию исходников, а не опубликованный OTA-релиз. Проверки Compose UI render подтверждают только создание изображений тестовой сцены и проверяемые тестом свойства. Они не доказывают поведение настоящего окна AWT, keyboard/screen-reader acceptance, UAC, подключение VPN, прохождение трафика или готовность установщика. Приёмка реального Windows-приложения и решение о публикации остаются отдельными этапами; наличие документа и render-тестов не означает их завершения.

Направление основано на [Fluent typography](https://fluent2.microsoft.design/typography) и [Fluent shapes](https://fluent2.microsoft.design/shapes), но не обещает точного воспроизведения WinUI. [Mica](https://learn.microsoft.com/en-us/windows/apps/design/style/mica) является системным backdrop окна и не реализована в этой итерации.
