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

## Извлекатель Бесконечности и кукла Стива (как устроено)
- Предмет `infinity_extractor` (`extractor/InfinityExtractorItem`), правила — `extractor/StoneExtraction` (сервер).
  ПКМ (держать) на том, у кого в левой руке рабочая перчатка, до `RANGE`=16 блоков, цель в прицеле и в прямой видимости:
  один цикл = `CYCLE_TICKS`=60 тиков. Нужно `CYCLES`=3 цикла; после каждого игрок нажимает заново, прогресс помнится
  `KEEP_TICKS`=20 с (на пару «владелец извлекателя + цель + камень»). Цикл срывается, если цель потеряна/вышла из конуса
  прицела/камень начали использовать (`GauntletState.inUse`) — прогресс при этом не растёт. Если камень уже
  используется, цикл не начинается (HUD: `blocked`).
- Выбор камня: Shift+ПКМ с пустой камерой переключает «авто → Сила → … → Время». Авто берёт первый вставленный камень,
  который сейчас не используется.
- После 3-го цикла камень вынимается из перчатки цели (`GauntletState.takeStone`) и кладётся в камеру — компонент
  `container` предмета (поэтому при смерти владельца извлекатель выпадает вместе с камнем). Shift+ПКМ с камнем в камере —
  достать в инвентарь. ПКМ без цели с камнем в камере — вернуть камень в свою перчатку (`GauntletState.putStone`).
- Состояние модели — `custom_model_data` float 0: 0 пусто, 1 идёт 1-й цикл, 2/3 один/два цикла готовы, 4 камень в камере
  (`items/infinity_extractor.json` из пака). Эффекты — `ExtractorFx` → `client/fx/ExtractorClient` (нить `extractor_tether`,
  `gather`/`success`/`mote`/`seal`, кольцо на прицеле из `textures/gui/extractor`). В руке модель повёрнута по `display` в
  `held_empty.json` (остальные состояния наследуют его).
- Кукла: сущность `gauntlet_doll` (`entity/GauntletDoll`, рендер `client/render/GauntletDollRenderer` на модели игрока со
  скином Стива). Призыв: яйцо в креативе или `/gauntlet doll`. Полная перчатка в левой руке, ходит, слегка бьёт (0.5 сердца)
  ближайших игроков, раз в ~14 с «использует» случайный камень на 5 с (камень светится, извлекатель его не берёт).
  Тест: `./gradlew runClientGameTest -PonlyTest=ExtractorTest`.

## Поток метеоритов (Камень Силы, как устроено)
- Способность `Ability.METEOR_STREAM` в колесе Камня Силы (удерживаемая). Сервер: `meteor/MeteorStream`.
  Фаза 1 — навести перчатку на Солнце/Луну (`CelestialApocalypse.aimedBody`, `streamLockTicks`=30 тиков) → захват.
  Фаза 2 — на земле виден круг-прицел (`MeteorStreamFx` kind 0 → частица `meteor_reticle` + пламя по кромке), его центр —
  точка земли под прицелом (`streamMinRange`..`streamMaxRange`, 12–160 блоков). Отпустить кнопку — огонь.
- Поток длится `streamSeconds`=60 с игрового времени: `streamMeteorsPerSecond`=5 метеоритов в круг радиуса `streamRadius`=24.
  Летят от Солнца/Луны по линии их текущего направления (стартуют в ~260–320 блоках), готовые пакеты `MeteorSpawn`/`MeteorImpact`
  и визуалы из `CelestialClient`. Само светило не разрушается. Небо краснеет мягче, чем в апокалипсисе (`MeteorStreamClient.redFactor`,
  до 55%, плавно нарастает/затухает, только вблизи круга).
- Мультиплеер: всё решает сервер. Своё состояние у каждого игрока (`AIMS`), кулдаун `streamCooldownSeconds`, лимиты
  `streamMaxConcurrent` потоков и `streamMaxActiveMeteors` метеоритов на весь сервер. Круг всем в радиусе ~600 блоков виден как зона
  опасности (+ повтор раз в 5 с и при входе на сервер). Урон игрокам и разрушения подчиняются `CoopRules`
  (`abilityPvp`, `abilityBlockChanges`, защита спавна); сам заклинатель к потоку неуязвим; выключается `allowMeteorStream`.
  Поток обрывается, если заклинатель вышел с сервера. Пока он идёт, Камень Силы в перчатке светится и его нельзя вытянуть извлекателем.
- Сообщения HUD (`ModStatus`) теперь идут как `Component` — переводы подставляет клиент, на выделенном сервере больше не видны ключи.
- Команда проверки: `/gauntlet meteor` (поток в 40 блоках перед игроком, без прицеливания). Тест: `-PonlyTest=MeteorStreamTest`.

## Кооператив

Параметры сервера в `config/infinity_gauntlet.json` по умолчанию включены.

- `abilityPvp` разрешает атаки способностями на игроков, если это разрешено правилами Minecraft и команды. `mindPlayerGrab`, `soulPlayerDrain` и `extractorPlayerTheft` настраиваются отдельно.
- Разум в перчатке удерживает игрока до 2 секунд. Приседание, укрытие и смена измерения освобождают цель. Разрыв применяется к мобам.
- Точечная Душа в перчатке забирает до 2 сердец, оставляя цели хотя бы половину сердца, и лечит владельца на фактически снятое здоровье. Приседание, укрытие или выход из дальности прерывают вытягивание.
- `abilityBlockChanges` отключает изменение блоков способностями, включая кратеры и огонь от метеоров. `beamBreakBlocks` отдельно управляет разрушением от луча. Метеоры могут наносить урон как эффект окружения.
- `allowPlanetDestruction`, `allowCelestialDestruction`, `allowInfinitySnap` и `allowTimeShift` отключают запуск отдельных глобальных событий. Уже запущенное событие можно завершить и отмотать.

Для проверки вдвоём: щелчок из космоса при втором игроке на Земле, выход одного участника до отката, взгляд на искажение Реальности с разных сторон, вход в измерение с открытым порталом, два извлекателя на одном камне, захват и освобождение игрока с разрешённым и запрещённым PvP.

## Броня из Уру (как устроено)
- Предметы `uru_helmet/chestplate/leggings/boots` на материале `uru/UruArmor.MATERIAL`: броня 4/9/7/4 (24, у незерита 20),
  твёрдость 4, отбрасывание 0.1 на часть, прочность 660/960/900/780, огнестойкие. Ремонт — тег `infinity_gauntlet:repairs_uru_armor`
  (пока пустой: добыча Уру и кузница не придуманы). Выдать: креатив или `/gauntlet uru`; кукла в Уру: `/gauntlet doll uru`.
- Весь урон перчатки по живым существам идёт через `uru/GauntletDamage.hurt` (силовой удар, Луч, Душа, Разум, Реальность,
  сверхзвуковой таран). Если у атакующего **все 6 камней** и цель — игрок (или кто угодно в полном Уру), урон идёт типом
  `infinity_gauntlet:infinity_gauntlet` (теги `bypasses_armor/enchantments/shield`) и умножается на `fullGauntletDamageMultiplier`=1.5:
  обычная броня и щит от полной перчатки не спасают. Полный целый комплект Уру пропускает `uruDamageFactor`=25% (минимум 1 HP),
  мгновенные убийства превращает в удар на `uruLethalIncoming`=40 (→10 HP), и каждая часть теряет clamp(ceil(поглощено/4),1,12) прочности.
  Неполная перчатка и мобы без Уру — старый путь. PvP по-прежнему решает `CoopRules` до вызова; износ и вспышка только при принятом ударе.
- Не защищает от событий мира: щелчок, разрушение планеты, небесный апокалипсис, поток метеоритов (взрывы — обычный урон, броня Уру работает
  как обычная броня), кража камней, порталы.
- Вид: текстуры нарисованы генератором с нуля в 4x (256x128, `art/source/uru/make_textures.py`: кованые пластины с фаской, ламели,
  гравированные светящиеся руны, забрало, гнездо камня; иконки пака перетонированы под тёмный металл) + объёмные детали
  `client/render/UruDetailLayer` (шлем с веером перьев и рунным гребнем, щёчные пластины, горжет, камень в оправе, пояс с пряжкой,
  острые табарды, трёхслойные наплечники, наручи, наколенники, окованные сапоги; руны и камень светятся). Детали — item-модели из
  `art/source/uru/make_models.py` в координатах частей модели, поэтому работают на любом гуманоиде. В 1-м лице на своей руке видна
  броня (`UruClient.firstPersonArm`), вокруг полного комплекта мерцают бирюзовые искры рун. Вспышка рун — частица
  `uru_guard` по пакету `UruGuardFx` (не чаще раза в 8 тиков на носителя). Тест: `-PonlyTest=UruArmorTest`.
