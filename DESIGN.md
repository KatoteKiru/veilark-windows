---
name: Veilark Windows
description: "Строгая компактная Material 3-система для спокойного управления VPN в Windows"
colors:
  primary: "light-dark(#285F9A, #A9C7FF)"
  on-primary: "light-dark(#FFFFFF, #00315F)"
  primary-container: "light-dark(#D3E4FF, #1A4977)"
  on-primary-container: "light-dark(#001C38, #D3E4FF)"
  secondary-container: "light-dark(#D7E3F8, #3B4758)"
  on-secondary-container: "light-dark(#101C2B, #D7E3F8)"
  degraded: "light-dark(#6B5778, #D5BEE5)"
  degraded-container: "light-dark(#FFDF9B, #5C4300)"
  on-degraded-container: "light-dark(#3E2E00, #FFDF9B)"
  background: "light-dark(#F9F9FF, #111318)"
  surface-lowest: "light-dark(#FFFFFF, #0C0E13)"
  surface-low: "light-dark(#F3F3FA, #191C20)"
  surface: "light-dark(#EDEDF4, #1D2024)"
  surface-high: "light-dark(#E7E8EE, #272A2F)"
  text: "light-dark(#1A1C20, #E2E2E9)"
  muted-text: "light-dark(#43474E, #C3C6CF)"
  outline: "light-dark(#74777F, #8D9199)"
  error: "light-dark(#BA1A1A, #FFB4AB)"
  error-container: "light-dark(#FFDAD6, #93000A)"
  on-error-container: "light-dark(#410002, #FFDAD6)"
typography:
  headline:
    fontFamily: "system-ui, \"Segoe UI\", sans-serif"
    fontSize: "28sp"
    fontWeight: 600
    lineHeight: "36sp"
  headline-compact:
    fontFamily: "system-ui, \"Segoe UI\", sans-serif"
    fontSize: "21sp"
    fontWeight: 600
    lineHeight: "32sp"
  title:
    fontFamily: "system-ui, \"Segoe UI\", sans-serif"
    fontSize: "18sp"
    fontWeight: 500
    lineHeight: "28sp"
  title-compact:
    fontFamily: "system-ui, \"Segoe UI\", sans-serif"
    fontSize: "16sp"
    fontWeight: 500
    lineHeight: "24sp"
  body:
    fontFamily: "system-ui, \"Segoe UI\", sans-serif"
    fontSize: "16sp"
    fontWeight: 400
    lineHeight: "24sp"
  body-compact:
    fontFamily: "system-ui, \"Segoe UI\", sans-serif"
    fontSize: "14sp"
    fontWeight: 400
    lineHeight: "20sp"
  caption:
    fontFamily: "system-ui, \"Segoe UI\", sans-serif"
    fontSize: "12sp"
    fontWeight: 400
    lineHeight: "16sp"
  label:
    fontFamily: "system-ui, \"Segoe UI\", sans-serif"
    fontSize: "14sp"
    fontWeight: 500
    lineHeight: "20sp"
rounded:
  badge: "8dp"
  compact: "12dp"
  control: "14dp"
  container: "16dp"
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
    rounded: "{rounded.compact}"
    padding: "0 24dp"
    height: "44dp"
  button-tonal:
    backgroundColor: "{colors.secondary-container}"
    textColor: "{colors.on-secondary-container}"
    typography: "{typography.label}"
    rounded: "{rounded.container}"
    padding: "0 24dp"
    height: "50dp"
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
    padding: "0 8dp"
    height: "38dp"
  card-section:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.text}"
    rounded: "{rounded.compact}"
    padding: "12dp 14dp"
  field-outlined:
    backgroundColor: "transparent"
    textColor: "{colors.text}"
    typography: "{typography.body-compact}"
    rounded: "{rounded.container}"
    padding: "13dp 16dp"
  engine-choice-selected:
    backgroundColor: "{colors.primary-container}"
    textColor: "{colors.on-primary-container}"
    typography: "{typography.label}"
    rounded: "{rounded.container}"
    padding: "0 14dp"
    height: "48dp"
  status-mark:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on-primary}"
    rounded: "{rounded.full}"
    size: "44dp"
---

# Design System: Veilark Windows

## Overview

**Creative North Star: "Спокойная консоль соединения"**

«Спокойная консоль соединения» — это строгий, компактный визуальный мир для ежедневной Windows-утилиты. Он использует Material 3 как нативную систему компонентов, но держит плотность, типографику и ритм ближе к настольному рабочему инструменту: ясно ранжированные действия, короткие русские подписи и минимум декоративного шума.

Нейтральные тональные плоскости несут структуру, а синий цвет сообщает действие, выбор и подтверждённое положительное состояние. Предупреждение и ошибка остаются локальными сигналами. Анимация служит только смене состояния — она подтверждает переход, не превращая VPN-клиент в демонстрационный экран.

**Key Characteristics:**

- Компактная рабочая плотность вместо растянутого мобильного набора карточек.
- Нативная Windows-типографика и стандартные Material 3-контролы.
- Нейтральная тональная иерархия с синими семантическими акцентами.
- Цвет состояния локализован в метках, знаках, тексте и основном действии.
- Короткое движение по состоянию и адаптивное поведение wide/tight.

## Colors

Палитра — холодная нейтральная Material-схема с парными светлыми и тёмными значениями; каждый токен в frontmatter переключается вместе с системной темой Windows.

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

**Display Font:** системный UI-шрифт Windows через Compose `FontFamily.Default`.

**Body Font:** системный UI-шрифт Windows через Compose `FontFamily.Default`.

**Label/Mono Font:** системный UI-шрифт для меток; `FontFamily.Monospace` только для технического журнала.

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

**The Native Voice Rule.** Интерфейс использует системную гарнитуру Windows; характер создают иерархия, ясный русский текст и умеренная насыщенность, а не отдельный брендовый шрифт.

## Layout

Система строится на компактном ритме 4/8/10/12/14/16 dp, с 16 dp внешним отступом рабочих страниц и максимальной шириной основной рабочей области 780 dp. Повторяющиеся контейнеры используют 12×14 dp внутреннего отступа; промежутки между связанными контролами обычно составляют 8–10 dp.

Адаптивность меняет структуру, а не сокращает содержание: при доступной ширине 720 dp и выше плотные рабочие области могут идти параллельно, ниже этого порога они складываются в последовательную колонку. Окно рассчитано на настольную сцену с исходным размером 940 × 640 dp и минимальным размером 820 × 560 px; текст и контролы должны сохранять назначение в обоих wide/tight состояниях.

**The Compact Rhythm Rule.** Сначала используйте устойчивую шкалу интервалов, а локальные поправки применяйте только для выравнивания нативного Material-контрола.

**The Adaptive Continuity Rule.** Tight-layout перестраивает порядок и ширину, но не скрывает доказательства состояния и основные действия.

## Elevation & Depth

Система плоская по умолчанию и не задаёт пользовательских теней. Глубина строится сменой тональных уровней `surface-lowest` → `surface-low` → `surface` → `surface-high`; стандартные Material 3-меню и диалоги могут использовать собственную платформенную elevation-модель.

### Named Rules

**The Tonal Depth Rule.** Постоянная структура отделяется тональным уровнем поверхности, а тень остаётся свойством нативного временного слоя, а не декором карточки.

## Shapes

Формы мягкие, но собранные: 12 dp для компактных контролов и навигации, 14 dp для обычных интерактивных контейнеров, 16 dp для секций, полей и главных действий. Радиус 8 dp разрешён только малой текстовой метке, а круг — компактному статусному знаку или индикатору.

**The Bounded Curve Rule.** Основная геометрия держится в диапазоне 12–16 dp; круг и малая метка являются семантическими исключениями, а не альтернативным стилем поверхности.

## Components

### Buttons

- **Shape:** основное действие использует крупную форму контейнера; компактные вторичные действия используют compact/control-радиусы.
- **Primary:** сплошной `primary` с `on-primary`, label-типографикой и высотой 50 dp для доминирующего действия.
- **Tonal:** `secondary-container` для остановки, отключения и вторичного акцента без тревожной окраски.
- **Outlined:** прозрачная поверхность и стандартная Material-обводка для обновления, проверки, отмены и других вспомогательных действий.
- **Hover / Focus / Disabled:** состояния оставляются стандартным Material 3 state layers и нативному focus-поведению; занятые состояния получают progress-индикатор и явный текст.

### Chips

- **Style:** малая текстовая метка использует `secondary-container`, label-типографику и badge-радиус.
- **State:** метка сообщает происхождение или другой устойчивый атрибут; она не заменяет интерактивный выбор.

### Cards / Containers

- **Corner Style:** container-радиус.
- **Background:** `surface` для обычной секции, `surface-high` для усиленной рабочей плоскости, `surface-lowest` для локального вложенного статуса.
- **Shadow Strategy:** без пользовательской тени; см. Tonal Depth Rule.
- **Internal Padding:** обычно `spacing.md`, с `spacing.xl` у крупного системного сообщения.

### Inputs / Fields

- **Style:** стандартный Material 3 `OutlinedTextField` с container-радиусом, читаемой подписью и поддерживающим текстом.
- **Focus:** нативное Material 3 focus-состояние и акцентная обводка.
- **Error / Disabled:** блокировка сохраняет читаемый текст и объяснение; ошибка не кодируется одной обводкой без сообщения.

### Navigation

- **Style:** горизонтально компактный пункт высотой 42 dp с иконкой, label-текстом и compact-радиусом; выбранный пункт использует `secondary-container`, остальные остаются прозрачными.
- **Semantics:** каждый пункт — `Role.Tab`; текущий пункт сообщает выбранное состояние.

### Engine Choice

- **Style:** парный выбор высотой 48 dp; выбранный вариант получает `primary-container`, невыбранный — `surface-high`.
- **Semantics:** группа использует selectable-group, каждый вариант — `Role.RadioButton`; отсутствие профиля объясняется текстом.

### Status Mark

- **Style:** круглый локальный знак меняет семантический цвет, пиктограмму и progress-состояние без окрашивания окружающей плоскости.
- **Motion:** цвет меняется за 220 ms; содержимое появляется за 160 ms с мягким масштабом от 92% и исчезает за 120 ms. Переходы страниц и условных секций используют fade/crossfade 120–180 ms, а стрелка раскрытия поворачивается за 160 ms.
- **Evidence:** любой цвет сопровождается русским текстовым состоянием, а при подключении — длительностью и трафиком.

### Setting Row

- **Style:** вся строка является целью переключения; заголовок и пояснение остаются рядом со стандартным Material 3 switch.
- **Semantics:** `Role.Switch`, объединённые дочерние элементы и явное `stateDescription` «Включено»/«Выключено».

### Named Rules

**The Semantic Control Rule.** Сохраняйте Material-компонент и соответствующий accessibility role; иконка внутри подписанного контроля не заменяет его текстовую метку.

**The State Motion Rule.** Движение коротко подтверждает смену состояния, раскрытие или навигацию и никогда не становится постоянным декоративным эффектом.

## Do's and Don'ts

### Do:

- **Do** используйте основной синий для действия, выбора и положительного доказательства состояния.
- **Do** оставляйте большие контейнеры нейтральными и стройте иерархию тональными уровнями поверхности.
- **Do** используйте системную Windows-гарнитуру и зафиксированную Material-иерархию текста.
- **Do** держите интерактивную геометрию в диапазоне 12–16 dp, сохраняя 8 dp только для малой метки и круг только для компактного статуса.
- **Do** перестраивайте параллельные области в колонку на tight-ширине, не скрывая содержание.
- **Do** сопровождайте цвет состояния ясным русским текстом и доступной семантикой роли или состояния.
- **Do** используйте короткое движение только для обратной связи о состоянии.

### Don't:

- **Don't** заливайте статусным цветом большие карточки или добавляйте неоновое свечение.
- **Don't** добавляйте декоративную display-гарнитуру, капс или letter-spacing ради «технологичности».
- **Don't** заменяйте стандартные кнопки, поля, switch, меню и диалоги веб-подобными самодельными контролами.
- **Don't** используйте цвет как единственное доказательство подключения, ошибки или доступности.
- **Don't** добавляйте постоянные пользовательские тени там, где иерархию уже выражает тональная поверхность.
