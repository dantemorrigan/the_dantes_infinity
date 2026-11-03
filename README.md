# Infinity Gauntlet (Fabric, Minecraft 26.1.2)

Требования: **Java 25**. Сборка: `./gradlew build` → `build/libs/infinity_gauntlet-0.1.0.jar` (+ Fabric API 0.155.3+26.1.2).

```
./gradlew runClient          # тестовый клиент
./gradlew runClientGameTest  # визуальный прогон со скриншотами (build/run/clientGameTest/screenshots)
```

## Камень Силы
- Перчатка работает только **в левой руке** (F). ПКМ с перчаткой в правой руке — меню камней.
- **Держать ПКМ** — заряд (фиолетовая аура у кулака), **отпустить** — силовой удар (урон, отбрасывание, перезарядка — на сервере).
- **V** — переключить режим: силовой удар / **Уничтожение планеты**.
- Ультимейт: режим «Уничтожение планеты», полный заряд (2 с), удар по земле под собой.

## Камень Времени
- **R** — отмотать время: игрок «проигрывается» назад по своему пути за последние 5 с, здоровье и голод возвращаются к тому моменту.
- В Мёртвом космосе **R** отменяет разрушение: планета и обломки собираются обратно, зелёная вспышка, все участники того же события возвращаются домой.

## Отладочные команды (уровень оператора)
| Команда | Что делает |
|---|---|
| `/gauntlet give_power` | перчатка с Камнем Силы в левую руку |
| `/gauntlet give_time` | вставить Камень Времени в надетую перчатку |
| `/gauntlet fx <эффект>` | показать один эффект: power_strike, power_impact, power_shockwave, power_sparks_smoke, ground_fracture, planet_rift, detonation_flash, void_dissolve, camera_shake, white_flash, lift_debris |
| `/gauntlet cutscene` / `cutscene stop` | запустить/прервать катсцену разрушения без проверок |
| `/gauntlet space enter` | сразу в Мёртвый космос |
| `/gauntlet space return [игрок]` | вернуть игрока и сбросить состояние |
| `/gauntlet state` | текущее состояние: IDLE/CHARGING/STRIKE/DESTRUCTION/TRANSFER/SPACE |
| `/gauntlet reload_config` | перечитать `config/infinity_gauntlet.json` |

## Настройки
`config/infinity_gauntlet.json`: урон/радиус/отбрасывание/перезарядка удара, заряд и перезарядка ультимейта,
радиус участников (`participantRadius`), перенос всего измерения (`transferWholeDimension`, выкл.), тик переноса,
сила дрожи камеры и вспышки, параметры полёта в космосе, лимит частиц.

Идеи: [IDEAS.md](IDEAS.md) · Заметки по API: [docs/DEV_NOTES.md](docs/DEV_NOTES.md) · Ресурсы паков: `art/vfx/`
