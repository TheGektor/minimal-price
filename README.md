# MinimalPrice

**MinimalPrice** — это передовое и надежное решение для управления экономикой на современных серверах Minecraft. В условиях постоянно развивающихся игровых экономик, администраторам критически важно иметь инструменты для предотвращения демпинга и обесценивания ресурсов. Наш плагин предоставляет эти административные возможности в удобном формате.

Вы сможете легко категоризировать товары, устанавливать жесткие минимальные пороги цен и управлять всем этим через удобный графический интерфейс прямо в игровом чате. Больше никаких сложных команд для рядовых игроков — только интуитивно понятные кликабельные меню.

Однако настоящей "киллер-фичей" является наша **глубокая интеграция с Discord**. В отличие от простых ботов, MinimalPrice использует возможности Discord Forum Channels на полную мощность. Плагин автоматически создает отдельные ветки обсуждений для каждой категории товаров, публикует красиво оформленные списки цен и, что самое главное, синхронизирует любые изменения в игре в реальном времени. Если вы меняете цену на алмаз в игре, она мгновенно обновляется в Discord, гарантируя, что ваши игроки всегда имеют доступ к актуальной информации, даже находясь оффлайн. Мы позаботились и о технических деталях: защита от спама, обход ограничений (Rate Limits) Discord API и автоматическая очистка устаревших данных делают работу плагина абсолютно прозрачной и стабильной.

## ✨ Возможности

*   **Управление категориями и товарами**: Удобная организация предметов по группам.
*   **Контроль минимальных цен**: Установка и отслеживание фиксированных цен на товары.
*   **Интерактивный GUI в чате**: Кликабельные списки категорий и товаров для удобной навигации без использования инвентаря.
*   **Discord Интеграция**: Автоматическое создание и обновление тредов в Форум-канале Discord.
    *   Мгновенная синхронизация при изменении цен.
    *   Отдельный тред для каждой категории.
    *   Красивое оформление Embed-сообщений.
    *   Устойчивость к Rate Limit'ам Discord API.
*   **Локализация**: Полная поддержка русского и английского языков (`ru`, `en`).
*   **Настраиваемость**: Возможность изменить символ валюты, формат сообщений и дизайн.
*   **SQLite База данных**: Надежное локальное хранение данных.

## 🚀 Установка

1.  Скачайте последний релиз `.jar` файла.
2.  Поместите его в папку `plugins` вашего сервера.
3.  **(Рекомендуется)** Установите [DiscordSRV](https://github.com/DiscordSRV/DiscordSRV) для работы интеграции с Discord.
4.  Перезагрузите сервер.

## ⚙️ Конфигурация

Основной конфиг находится в `plugins/MinimalPrice/config.yml`.

```yaml
# Язык сообщений: ru (Russian) или en (English)
language: ru

# Символ валюты, отображаемый в чате и Discord
currency: '$'

# ID Форум-канала в Discord для синхронизации цен
# Включите режим разработчика в Discord, нажмите ПКМ на канал -> Копировать ID
discord_forum_channel_id: "123456789012345678"
```

### Настройка Discord Интеграции

1.  Убедитесь, что плагин **DiscordSRV** установлен и подключен к вашему боту.
2.  Создайте **Forum Channel (Канал-форум)** в вашем Discord сервере.
3.  Скопируйте **ID канала** (ПКМ -> Copy ID).
4.  Вставьте ID в поле `discord_forum_channel_id` в `config.yml`.
5.  Перезагрузите плагин (`/minimal reload`).
6.  Бот автоматически удалит старые (созданные им) посты и создаст новые актуальные списки.

> **Важно**: Боту требуются права `Manage Threads` (Управление ветками) и `Send Messages` (Отправка сообщений) в этом канале.

## 📜 Команды и Права

| Команда | Описание | Право |
| :--- | :--- | :--- |
| `/minimal view` | Открыть интерактивный список категорий. | `minimalprice.view` |
| `/minimal create category <name>` | Создать новую категорию. | `minimalprice.admin` |
| `/minimal add price <cat> <item> <price>` | Добавить товар с ценой в категорию. | `minimalprice.admin` |
| `/minimal set category <old> <new>` | Переименовать категорию. | `minimalprice.admin` |
| `/minimal set goods <old> <new>` | Переименовать товар (во всех категориях). | `minimalprice.admin` |
| `/minimal set price <cat> <item> <price>` | Изменить цену товара. | `minimalprice.admin` |
| `/minimal reload` | Перезагрузить конфиг и языки. | `minimalprice.admin` |

*Алиасы: `/price`, `/mp`*

---

## 👨‍💻 Руководство Разработчика (Developer Guide)

Мы приветствуем разработчиков, желающих внести свой вклад в проект!

### Требования (Prerequisites)

*   **Java 21** (Необходима для сборки и запуска).
*   **Gradle 8.5+** (Используется Gradle Wrapper в проекте).

### Структура Проекта (Architecture)

Проект следует архитектурному паттерну **Package by Feature** (Пакет по Фиче), чтобы код оставался модульным и понятным.

*   `ru.minimalprice.minimalprice`
    *   `configuration`: Логика работы с `config.yml` и загрузка локализаций (`messages_*.yml`).
    *   `database`: Управление подключением SQLite и пулом соединений (`HikariCP`).
    *   `features`
        *   `price`: Основная логика цен.
            *   `models`: POJO классы `Category`, `Product`.
            *   `storage`: `PriceRepository` (SQL запросы).
            *   `events`: Кастомные Bukkit Events (`CategoryCreateEvent`, `ProductUpdateEvent` и др.), на которые могут подписываться другие модули.
        *   `discord`: Модуль синхронизации с Discord.
            *   `storage`: `DiscordRepository` (Отслеживает ID созданных тредов в БД).
            *   `DiscordManager`: Слушает ивенты из пакета `price` и управляет очередью задач.
            *   `DiscordRestUtil`: Класс-утилита, использующий `java.net.http.HttpClient` для прямых запросов к Discord API (v10). Реализует логику **Retry-After** для обхода Rate Limit'ов при работе с Форумами.

### Сборка Проекта

1.  Клонируйте репозиторий.
2.  Откройте проект в IDE (рекомендуется IntelliJ IDEA).
3.  Выполните команду сборки:

```bash
./gradlew build
```

Скомпилированный `.jar` файл появится в директории `build/libs/`.

### Как внести вклад (Contribution)

1.  Сделайте **Fork** репозитория.
2.  Создайте ветку для вашей фичи (`git checkout -b feature/cool-new-feature`).
3.  Внесите изменения и закоммитьте их.
4.  Запушьте ветку в ваш форк (`git push origin feature/cool-new-feature`).
5.  Откройте **Pull Request** в основной репозиторий.

### Ключевые технологии

*   **Paper API 1.21.10**: Основа плагина.
*   **DiscordSRV**: Используется для авторизации бота, чтобы не дублировать токен в конфигах.
*   **Java HTTP Client**: Используется для работы с функционалом Discord Forums (так как встроенный JDA в DiscordSRV может быть устаревшей версии).
*   **HikariCP**: Пул соединений для базы данных для высокой производительности.
*   **Gson**: Парсинг JSON ответов от Discord API.

---
---

# MinimalPrice (English Version)

**MinimalPrice** is an advanced and reliable solution for economy management on modern Minecraft servers. In the context of constantly evolving in-game economies, it is critical for administrators to have tools to prevent dumping and resource devaluation. Our plugin provides these administrative capabilities in a convenient format.

You can easily categorize items, set strict minimum price thresholds, and manage everything through a user-friendly GUI directly in the game chat. No more complex commands for regular players — just intuitive clickable menus.

However, the real "killer feature" is our **deep integration with Discord**. Unlike simple bots, MinimalPrice leverages the full power of Discord Forum Channels. The plugin automatically creates separate discussion threads for each item category, publishes beautifully formatted price lists, and, most importantly, synchronizes any in-game changes in real-time. If you change the price of a diamond in-game, it instantly updates in Discord, ensuring your players always have access to up-to-date information, even when offline. We've also taken care of the technical details: spam protection, Discord API Rate Limit handling, and automatic cleanup of outdated data make the plugin's operation completely transparent and stable.

## ✨ Features

*   **Category and Item Management**: Organize items into groups efficiently.
*   **Minimum Price Control**: Set and track fixed prices for items.
*   **Interactive Chat GUI**: Clickable lists of categories and items for easy navigation without using inventory GUIs.
*   **Discord Integration**: Automatically creates and updates threads in a Discord Forum Channel.
    *   Instant synchronization on price changes.
    *   Separate thread for each category.
    *   Beautifully formatted Embed messages.
    *   Resilient to Discord API Rate Limits.
*   **Localization**: Full support for Russian and English languages (`ru`, `en`).
*   **Customization**: Change currency symbols, message formats, and design.
*   **SQLite Database**: Reliable local data storage.

## 🚀 Installation

1.  Download the latest release `.jar` file.
2.  Place it in your server's `plugins` folder.
3.  **(Recommended)** Install [DiscordSRV](https://github.com/DiscordSRV/DiscordSRV) for Discord integration.
4.  Restart the server.

## ⚙️ Configuration

The main config is located at `plugins/MinimalPrice/config.yml`.

```yaml
# Message language: ru (Russian) or en (English)
language: ru

# Currency symbol displayed in chat and Discord
currency: '$'

# Discord Forum Channel ID for price synchronization
# Enable Developer Mode in Discord, right-click the channel -> Copy ID
discord_forum_channel_id: "123456789012345678"
```

### Discord Integration Setup

1.  Ensure **DiscordSRV** is installed and connected to your bot.
2.  Create a **Forum Channel** in your Discord server.
3.  Copy the **Channel ID** (Right-click -> Copy ID).
4.  Paste the ID into `discord_forum_channel_id` in `config.yml`.
5.  Reload the plugin (`/minimal reload`).
6.  The bot will automatically delete old posts (created by itself) and create new, up-to-date lists.

> **Important**: The bot requires `Manage Threads` and `Send Messages` permissions in this channel.

## 📜 Commands and Permissions

| Command | Description | Permission |
| :--- | :--- | :--- |
| `/minimal view` | Open the interactive category list. | `minimalprice.view` |
| `/minimal create category <name>` | Create a new category. | `minimalprice.admin` |
| `/minimal add price <cat> <item> <price>` | Add an item with a price to a category. | `minimalprice.admin` |
| `/minimal set category <old> <new>` | Rename a category. | `minimalprice.admin` |
| `/minimal set goods <old> <new>` | Rename an item (across all categories). | `minimalprice.admin` |
| `/minimal set price <cat> <item> <price>` | Change item price. | `minimalprice.admin` |
| `/minimal reload` | Reload config and languages. | `minimalprice.admin` |

*Aliases: `/price`, `/mp`*

---

## 👨‍💻 Developer Guide

We welcome developers who want to contribute to the project!

### Prerequisites

*   **Java 21** (Required for building and running).
*   **Gradle 8.5+** (The project uses Gradle Wrapper).

### Architecture

The project follows the **Package by Feature** architecture to keep code modular and understandable.

*   `ru.minimalprice.minimalprice`
    *   `configuration`: Logic for `config.yml` and loading localizations (`messages_*.yml`).
    *   `database`: managing SQLite connection and connection pool (`HikariCP`).
    *   `features`
        *   `price`: Core price logic.
            *   `models`: POJO classes `Category`, `Product`.
            *   `storage`: `PriceRepository` (SQL queries).
            *   `events`: Custom Bukkit Events (`CategoryCreateEvent`, `ProductUpdateEvent`, etc.) that other modules can listen to.
        *   `discord`: Discord synchronization module.
            *   `storage`: `DiscordRepository` (Tracks threads created in DB).
            *   `DiscordManager`: Listens to events from the `price` package and manages the task queue.
            *   `DiscordRestUtil`: Utility class using `java.net.http.HttpClient` for direct requests to Discord API (v10). Implements **Retry-After** logic to handle Rate Limits for Forums.

### Building the Project

1.  Clone the repository.
2.  Open the project in an IDE (IntelliJ IDEA recommended).
3.  Run the build command:

```bash
./gradlew build
```

The compiled `.jar` file will appear in the `build/libs/` directory.

### How to Contribute

1.  **Fork** the repository.
2.  Create a branch for your feature (`git checkout -b feature/cool-new-feature`).
3.  Commit your changes.
4.  Push the branch to your fork (`git push origin feature/cool-new-feature`).
5.  Open a **Pull Request** to the main repository.

### Key Technologies

*   **Paper API 1.21.10**: Plugin foundation.
*   **DiscordSRV**: Used for bot authorization to avoid duplicating tokens in configs.
*   **Java HTTP Client**: Used for Discord Forums functionality (as the bundled JDA in DiscordSRV might be outdated).
*   **HikariCP**: Database connection pool for high performance.
*   **Gson**: Parsing JSON responses from the Discord API.
