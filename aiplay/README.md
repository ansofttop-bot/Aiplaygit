# AI Play — DeepSeek-автопилот для Minecraft 1.21.4 (Fabric, client-side)

Клиентский мод: DeepSeek принимает стратегические решения, локальный контроллер исполняет их каждый тик
(прицеливание, движение, удары, лечение, предметы). AI управляет **вашим** игроком.
Используйте только на своём тестовом сервере / там, где автоматизация разрешена.

## Сборка
Нужны: JDK 21.
```
gradle wrapper --gradle-version 8.12   # один раз, если нет gradlew
./gradlew build
```
Результат: `build/libs/aiplay-1.0.0.jar` (не `-sources`).
Без установки Gradle: загрузите папку на GitHub — вкладка Actions → build → Artifacts → `aiplay-jar`.
Или откройте проект в IntelliJ IDEA (импорт Gradle) → задача `build`.

## Установка
1. Fabric Loader 0.16.10+ для Minecraft 1.21.4, 2. Fabric API 0.119.4+1.21.4 в `mods/`, 3. `aiplay-1.0.0.jar` в `mods/`.

## Ключ DeepSeek
RShift → Settings → DeepSeek API Key (поле маскируется). Ключ хранится в
`.minecraft/config/aiplay/config.json`, не в jar. Можно также задать переменную окружения `DEEPSEEK_API_KEY`.
Модель: `deepseek-chat` (не используйте `deepseek-reasoner` — он не поддерживает JSON-режим).

## Управление
- **RShift** — ClickGUI (закрыть тоже RShift)
- **RCTRL** — аварийное выключение AI (работает всегда, не ждёт API); также кнопка STOP AI
- AI → FULL AI PLAY — включить автопилот
- Ручной перехват: WASD/Space/Shift временно отключают AI (Movement → Manual Override)
- Закройте GUI во время игры: пока GUI открыт, ваниль не обрабатывает мышь (мод использует предметы напрямую, но удержание кнопки недоступно)

## Как обучать AI
AI-вкладка → чат: «Если HP ниже 40% — лечись. Если враг убегает — Shadow Dash. Golden Apple не трать зря.»
Правила сохраняются в `config/aiplay/memory.json`. Предметы: «Shadow Dash телепортирует на 5 блоков, кулдаун 8 сек, ПКМ».
Items → Unknown Items → Explain to AI. Очистка: Settings → Clear AI Memory.

## Приоритеты
Emergency Stop → Manual Override → Critical survival (HP ≤ Critical) → User rules (Heal below %, чат-правила) → стратегия боя → общее решение AI.

## Ограничения (честно)
- Реализовано на Yarn 1.21.4+build.8; проект не компилировался в среде автора — при ошибке сборки пришлите лог.
- Manual Override реагирует на физические WASD/Space/Shift (не на мышь).
- При ошибке API AI выключается сразу; при 3 подряд неверных JSON-ответах — тоже.
- Серверные античиты могут банить за автоматизацию боя.
