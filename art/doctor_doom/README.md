# Доктор Дум

Модель и текстуру генерирует первый скрипт, анимации — второй. Запускать в этом порядке:

```bash
python3 art/source/doctor_doom/make_model.py art/doctor_doom/preview/doctor_doom.png
```

```bash
python3 art/source/doctor_doom/make_animations.py art/doctor_doom/preview/animations
```

| Ресурс | Идентификатор |
| --- | --- |
| Геометрия для `GeoModel.load(Identifier)` | `infinity_gauntlet:doctor_doom/doctor_doom.geo.json` |
| Ключевые кадры Bedrock 1.8, 33 клипа | `infinity_gauntlet:doctor_doom/doctor_doom.animation.json` |
| Текстура RGBA 256×256 | `infinity_gauntlet:textures/entity/doctor_doom/doctor_doom.png` |
| Частицы пака «The Fury of Doom», 22 типа | `infinity_gauntlet:doctor_doom/<id>` |
| HUD и иконки способностей | `infinity_gauntlet:textures/gui/doctor_doom/` |
| Проект Blockbench | `blockbench/doctor_doom.bbmodel` |
| Превью модели и клипов | `preview/doctor_doom.png`, `preview/animations/*.gif` |
| Описание пака эффектов и моменты запуска | `vfx/README.md`, `vfx/doom_vfx.json` |

## Модель

Модель высотой 39 единиц, 16 единиц на блок, 144 куба. Начало координат у ног, фронт по −Z. У каждой грани свой участок текстуры с плотностью 2 пикселя на единицу. Узоры рисуются по координатам модели, поэтому совпадают на соседних кубах.

Анимируемые кости: `root`, `body`, `head`, `arm_r`, `arm_l`, `forearm_r`, `forearm_l`, `leg_r`, `leg_l`, `shin_r`, `shin_l`, `cape`, `cape_mid`, `cape_low`, `cape_wing_r`, `cape_wing_l`. Наклонные элементы (спад капюшона, пластины наплечников, внешние полотна плаща) вынесены в дочерние кости `bind_*` с фиксированным поворотом, чтобы их поддерживал `GeoModel`. Прорези глаз в маске настоящие, глаза нарисованы на тёмной нише за ними на высоте 34,5.

## Анимации

Клипы описаны позами в `art/source/doctor_doom/make_animations.py`. `GeoAnimation` сглаживает переход между соседними ключами, поэтому ключи стоят только в крайних положениях.

| Группа | Клипы |
| --- | --- |
| Покой и движение | `idle`, `idle_arms_crossed`, `idle_menace`, `idle_survey`, `hover`, `walk` |
| Базовые атаки | `gauntlet_punch`, `backhand`, `boot_kick`, `armored_combo` |
| Размашистые атаки | `cleave`, `overhead_smash`, `cape_sweep`, `jet_slam` |
| Магия и техника | `gauntlet_bolt`, `sorcery_bolt`, `hex_barrage`, `arcane_nova`, `lightning_call`, `seven_suns`, `rift_step`, `ovoid_echo`, `time_platform`, `latverian_edict`, `iron_bastion`, `doombot_drop`, `phase_shift` |
| Реакции и сцены | `block`, `stagger`, `stagger_heavy`, `taunt`, `intro`, `defeat` |

Плащ строится из дорожки `cape` каждого клипа. `cape_mid` и `cape_low` повторяют её с запозданием и меньшим размахом, боковые полотна расходятся при взлёте плаща, и у каждого сегмента своя фаза колыхания на ветру.

Клипы, для которых в паке есть эффекты, попадают в его отметки: удары `armored_combo` приходятся на тики 12, 17 и 20, выстрел `gauntlet_bolt` на 18, волна `jet_slam` на 28, колонна `latverian_edict` на 50. Полная таблица лежит в `vfx/README.md`. Длительности семнадцати прежних клипов не менялись. Сцена «Латверия» использует `intro`, `idle` и `gauntlet_bolt`.

## Эффекты

Частицы зарегистрированы в `ModParticles` (`DOOM_*`), параметры кадров заданы в `VfxPresets`. Наземные знаки (`drop_marker`, `slam_wave`, `time_platform`, `edict_warning`) лежат на поверхности, остальные повёрнуты к камере. Запуск эффектов в бою и отрисовка HUD появятся вместе с сущностью Дума.

Скрипты заменяют в проекте Blockbench кубы, текстуру и анимации. Правки вносятся в скрипты, а не в `.bbmodel`.

## Аддон The Fury of Doom

Сущность босса — `doom/DoctorDoom`, Думботы — `doom/Doombot`. Модели Думботов, их текстуры и общий файл анимаций `doombot.animation.json` генерирует `art/source/doctor_doom/make_doombots.py` на основе того же генератора. Иконки предметов, броня на игроке и текстура логотипа собираются скриптом `make_items.py` из пака `doombots/` и картинки `logo/`.

Латверию строит `doom/LatveriaGenerator` по плану `doom/LatveriaPlan`: рельеф и все постройки описаны функциями координат. Чанк записывается один раз при генерации, дальше это обычный сохранённый чанк. Штурм, отряды и владельцев замка хранит `doom/Latveria`. Сценарий проверки — `LatveriaTest`.
