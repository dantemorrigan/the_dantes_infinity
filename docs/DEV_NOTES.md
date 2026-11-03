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

## Камень Силы / Уничтожение планеты (как устроено)
- Частицы: `ModParticles` (8 типов) + клиентские провайдеры `client/fx` — кадр по возрасту, петля только у ауры,
  плоские квады для волны/трещин (нормаль в параметрах), слой TRANSLUCENT, full-bright. Тайминги/размеры читаются
  из `assets/.../planet_destruction/*_vfx_presets.json`.
- Пользовательские форматы пака грузит `client/space/PackData`: blueprints, asset_catalog (центры), planet_stages,
  cinematic_timeline, sky_orientation.
- Сервер: `power/PowerAbility` (удар/ультимейт), `space/PlanetDestruction` (событие, участники, перенос),
  `space/FragmentCapture` (снимок блоков без изменения мира), `space/SpaceManager` (измерение, вложение `space_record`).
- Клиент: `client/cutscene/Cutscene` (таймлайн, дрожь, вспышка, блок ввода, отрыв блоков),
  `client/space/SpaceScene` (кубмап, планета, энергия, обломки, полёт), `render/BlockMeshes` (кэш моделей блоков с биомным цветом).
- Рендер в мире: `LevelRenderEvents.COLLECT_SUBMITS` → `submitBlockModel` / `ItemStackRenderState.submit` / `submitCustomGeometry`;
  `order(-1000)` для неба. Туман в Мёртвом космосе отключён (`FogRendererMixin`), иначе всё дальше render distance чёрное.
- Перчатка на руке: 3-е лицо — display `thirdperson_lefthand` модели `gauntlet_in_hand` (рассчитан под цепочку
  ItemInHandLayer); 1-е лицо — `ItemInHandRendererMixin` рисует руку игрока, `AvatarRendererMixin` надевает на неё модель `gauntlet_worn`.

## Щелчок Бесконечности (как устроено)
- Способность `Ability.SNAP` (колесо, кнопка справа внизу, нужны все 6 камней). Сервер: `snap/InfinitySnap`.
  Таймлайн от каста: жест руки (`GestureFx` kind 1) → `SNAP_TICK` звук `infinity_gauntlet:infinity_snap`
  (`sounds/infinity_snap.ogg`, щелчок в самом начале файла) и перчатка с камнями «сгорают» (компонент `snapped`) →
  экран белеет (`SnapFx` 0) → `ERASE_TICK` все игроки (кроме Мёртвого космоса) уходят в измерение `white_void`.
- Белая комната: `data/.../dimension/white_void.json` — плоский мир из одного слоя `barrier` (пол невидим),
  белые небо/туман, `skybox: none`. Там можно только ходить: колбэки атаки/использования блоков, сущностей и
  предметов возвращают FAIL (кроме перчатки и Камня Времени), урона нет.
- Сломанная перчатка: `GauntletState.activeGauntlet` возвращает EMPTY → ни одна способность не работает;
  `wornGauntlet` отдаёт её как есть. Клавиша колеса открывает её инвентарь. Текстуры `*_damaged.png`
  генерирует `art/source/snap/make_damaged.py`; модели переключаются условием `minecraft:has_component` в `items/*.json`.
  Дым — клиентские частицы в `client/fx/SnapClient`.
- Возврат: Камень Времени (`TimeStoneItem`) вне перчатки, ПКМ в белой комнате → всем игрокам того же щелчка
  перемотка `REWIND_TICKS`, затем телепорт в сохранённую точку (`snap_record`), щелкнувшему — целая перчатка
  из записи, все сгоревшие предметы удаляются. Тест: `./gradlew runClientGameTest -PonlyTest=SnapTest`.
