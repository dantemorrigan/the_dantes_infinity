# Dev notes: Minecraft 26.1.2 + Fabric (заметки по API)

Версии: MC 26.1.2, Fabric Loader 0.20.1, Fabric API 0.155.3+26.1.2, Loom 1.19.x, Gradle 9.8.2, **Java 25**.

## Главное отличие от старых гайдов
- Майнкрафт **без обфускации**: имена классов Mojang, маппинги не нужны. Плагин `net.fabricmc.fabric-loom` (не `fabric-loom` старый).
- `ResourceLocation` теперь **`Identifier`** (`Identifier.fromNamespaceAndPath`).
- Гайды 1.20/1.21 часто устарели: сверяться с исходниками (`./gradlew genSources`, jar в `.gradle/loom-cache/minecraftMaven/net/minecraft/`).

## Предметы
- Регистрация: `Registry.register(BuiltInRegistries.ITEM, key, item)`, где `key = ResourceKey.create(Registries.ITEM, id)`.
- **Обязательно** `new Item.Properties().setId(key)` до создания Item (иначе краш).
- Properties: `stacksTo`, `durability`, `rarity`, `fireResistant`, `useCooldown(seconds)`, `enchantable`, `.component(...)`.
- Хуки Item: `use`, `useOn`, `hurtEnemy`, `inventoryTick`, `onUseTick`, `releaseUsing`, `getUseAnimation`.
- Модель предмета: **`assets/<modid>/items/<id>.json`** (item model definition, тип `minecraft:model`) указывает на `models/item/<id>.json`; текстуры в `textures/item/`. Есть select/conditional/range_select/composite модели (переключение вида по компоненту, например активная способность).
- Название: `item.<modid>.<id>` в `lang/en_us.json` (и `ru_ru.json`).

## Данные и рецепты
- Состояние перчатки (какие камни вставлены, назначенные способности) хранить в **Data Components** на ItemStack (свой компонент с Codec + StreamCodec).
- Состояние игрока/мира: Fabric Data Attachment API (`fabric-data-attachment-api-v1`).
- Рецепты/лут/теги: JSON в data pack (`data/<modid>/recipe/...`), можно генерировать через fabric-data-generation.

## Клиент
- Клавиши: `KeyMapping(name, keysym, KeyMapping.Category)` + `KeyMappingHelper` (Fabric key-mapping API).
- Экран: `Screen` (клиент) + `AbstractContainerMenu` (если нужны слоты с сервером). Меню способностей можно сделать обычным `Screen` + пакеты на сервер.
- Рендер: `fabric-rendering-v1` (EntityRendererRegistry, ModelLayerRegistry, HUD, tooltip и т.д.).

## Сеть (клиент <-> сервер)
- Fabric Networking v1: `PayloadTypeRegistry`, `CustomPacketPayload` + `StreamCodec`, `ServerPlayNetworking` / `ClientPlayNetworking`.
- Все способности **выполняются на сервере**; клиент только шлёт запрос (выбор способности, активация) и рисует эффекты.

## Небо / «красное небо» (важно!)
- В 26.x есть **система Environment Attributes** (`net.minecraft.world.attribute`): `SKY_COLOR`, `FOG_COLOR`, `CLOUD_COLOR`, `SUN_ANGLE`, `MOON_ANGLE`, `STAR_BRIGHTNESS`, `SKY_LIGHT_*` и др. Часть можно менять данными/слоями, **возможно без миксинов**. Проверить `EnvironmentAttributeLayer` / `EnvironmentAttributeSystem` на этапе «Падение Солнца».
- Рендер неба: `client/renderer/SkyRenderer` (`renderSunMoonAndStars`, `renderSunriseAndSunset`, `renderEndSky`): для кастомного неба мультиверса/падающего солнца точка входа для миксина.
- Кастомное измерение: `fabric-dimensions-v1` + JSON dimension / dimension_type.

## Сущности
- Метеориты: своя сущность (по образцу `FireworkRocketEntity` / `Projectile` / `LargeFireball`), либо `FallingBlockEntity`. Для массовых эффектов предпочитать частицы + ограниченное число сущностей.
- Массовое разрушение блоков: разносить по тикам (очередь на сервере), не ломать всё за один тик.

## Модули Fabric API, которые пригодятся
networking, key-mapping, rendering, particles, dimensions, data-attachment, item-api, object-builder, creative-tab, command-api, data-generation, resource-loader, screen-api, sound-api, entity-events, lifecycle-events.

## Команды
```
./gradlew runClient     # тест-клиент
./gradlew runServer     # тест-сервер
./gradlew build         # jar -> build/libs
./gradlew genSources    # исходники Minecraft для чтения
```
