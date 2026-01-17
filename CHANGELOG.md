# Changelog

All notable changes to the "MinimalPrice" project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2026-01-17

### Added
- **Core Price Management:**
  - Implemented category and product models.
  - Added SQLite database support (`database_v2.db`) for persistent storage.
  - CRUD operations for categories and products via `PriceRepository`.
- **User Interface:**
  - Added `/minimal` (alias `/price`, `/mp`) command suite.
  - Interactive chat interface (`/minimal view`) with clickable categories and items.
  - Secure input validation and tab completion support.
- **Discord Integration:**
  - **DiscordSRV Support:** Automatic integration if the plugin is present.
  - **Forum Channels:** Synchronizes categories and prices to a Discord Forum Channel specified in the config.
  - **Real-time Sync:** Updates Discord threads instantly when prices or categories change in-game.
  - **Embed Redesign:** Clean, modern embed design using Markdown headers and bold text for readability.
  - **Direct API Handling:** Uses `DiscordRestUtil` to bypass JDA version conflicts and handle Discord API v10 direct requests.
  - **Resilience:** Implemented robust rate limit handling (429 Retry-After) and startup cleanup logic.
- **Localization & Configuration:**
  - Full support for English (`en`) and Russian (`ru`) languages.
  - Configurable currency symbol (default `$`).
  - Reload command (`/minimal reload`) to update config and messages without restarting.

### Fixed
- **Discord Empty Embeds:** Fixed an issue where Discord posts were created before data was fully loaded, resulting in empty messages. Implemented `initFuture` to ensure data readiness.
- **Race Conditions:** Resolved initialization race conditions between `PriceManager` and `DiscordManager`.
- **JDA Compatibility:** Fixed compatibility issues with DiscordSRV's bundled JDA by using a custom HTTP client for Forum interactions.
- **Bot Token Handling:** Improved token retrieval to handle various formats (e.g., "Bot TOKEN").

### Technical
- Implemented "Package by Feature" architecture.
- Used `HikariCP` for efficient database connection pooling.
- Added extensive debug logging for Discord synchronization.

---
---

# Список изменений (Russian Version)

Все заметные изменения проекта "MinimalPrice" будут задокументированы в этом файле.

## [1.0.0] - 2026-01-17

### Добавлено
- **Управление ценами (Ядро):**
  - Реализованы модели категорий и товаров.
  - Добавлена поддержка базы данных SQLite (`database_v2.db`) для постоянного хранения.
  - CRUD операции для категорий и товаров через `PriceRepository`.
- **Пользовательский интерфейс:**
  - Добавлен набор команд `/minimal` (аллиасы `/price`, `/mp`).
  - Интерактивный чат-интерфейс (`/minimal view`) с кликабельными категориями и товарами.
  - Безопасная валидация ввода и поддержка автодополнения (Tab Completion).
- **Интеграция с Discord:**
  - **Поддержка DiscordSRV:** Автоматическая интеграция при наличии плагина.
  - **Форум-каналы:** Синхронизация категорий и цен в указанный в конфиге Discord Forum Channel.
  - **Синхронизация в реальном времени:** Мгновенное обновление тредов в Discord при изменении цен или категорий в игре.
  - **Редизайн Embed-сообщений:** Чистый, современный дизайн с использованием Markdown заголовков и жирного текста.
  - **Прямая работа с API:** Использование `DiscordRestUtil` для обхода конфликтов версий JDA и прямых запросов к Discord API v10.
  - **Устойчивость:** Реализована надежная обработка лимитов запросов (429 Retry-After) и логика очистки при запуске.
- **Локализация и Конфигурация:**
  - Полная поддержка английского (`en`) и русского (`ru`) языков.
  - Настраиваемый символ валюты (по умолчанию `$`).
  - Команда перезагрузки (`/minimal reload`) для обновления конфига и сообщений без перезапуска сервера.

### Исправлено
- **Пустые сообщения Discord:** Исправлена проблема, когда посты в Discord создавались до полной загрузки данных, что приводило к пустым сообщениям. Внедрен `initFuture` для гарантии готовности данных.
- **Состояния гонки (Race Conditions):** Устранены конфликты инициализации между `PriceManager` и `DiscordManager`.
- **Совместимость с JDA:** Исправлены проблемы совместимости с JDA, встроенным в DiscordSRV, путем использования кастомного HTTP клиента для работы с форумами.
- **Обработка токена бота:** Улучшено извлечение токена для поддержки различных форматов (например, "Bot TOKEN").

### Техническое
- Внедрена архитектура "Package by Feature".
- Использование `HikariCP` для эффективного пулинга соединений с базой данных.
- Добавлено подробное логирование отладки для синхронизации с Discord.
