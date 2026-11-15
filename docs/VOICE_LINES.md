# Voice lines: Doctor Doom and the Doombots

Every spoken line in the add-on, as it is shown in game (colour codes and the speaker prefix stripped). For each line: the key it has in the language files, when it plays, the text in English and Russian, and the file name the integration expects.

**Format for the files.** Mono `.ogg` (Vorbis), 44.1 kHz, one file per line, no leading silence. Doom: deep, slow, regal. Doombots: flat, mechanical, with a metallic filter (they should all sound alike). Record Russian and English separately if you want both; the game picks by the language setting.

**Folders.** Put files in `assets/infinity_gauntlet/sounds/doctor_doom/voice/ru/` and `.../voice/en/` (Doom) and `assets/infinity_gauntlet/sounds/doombot/voice/ru/` and `.../en/` (Doombots). Tell me when they are there and I will register them in `sounds.json` and play each line with its message.

Lines marked with ⏱ play one after another in a scene; leave about 0.5 s of silence at the end so they do not overlap.

## Doctor Doom (34 lines)

| Key | When | English | Русский | File |
|---|---|---|---|---|
| `doom.intro_1` | Throne-room fight starts (rising from the throne), first line | You stand in my hall uninvited. | Ты вошёл в мой зал без приглашения. | `doom_intro_1.ogg` |
| `doom.intro_2` | Throne-room fight starts, second line, ~2.5 s after the first | Kneel, or be taught how. | Преклони колено. Или я научу тебя. | `doom_intro_2.ogg` |
| `doom.phase_2` | Fight, health drops to 2/3: steel gives way to sorcery | Steel was a courtesy. Now, sorcery. | Сталь была любезностью. Теперь — магия. | `doom_phase_2.ogg` |
| `doom.phase_3` | Fight, health drops to 1/3: before the Latverian Edict | Enough. Latveria itself will judge you. | Довольно. Тебя осудит сама Латверия. | `doom_phase_3.ogg` |
| `doom.bastion` | Doom raises the Iron Bastion shield | Strike. It changes nothing. | Бей. Это ничего не изменит. | `doom_bastion.ogg` |
| `doom.doombots` | Doom calls Doombots down from the ceiling (phase 1) | Doombots. Remove them. | Думботы. Уберите их. | `doom_doombots.ogg` |
| `doom.seven_suns` | Doom casts the Seven Suns | Seven suns for one fool. | Семь солнц на одного глупца. | `doom_seven_suns.ogg` |
| `doom.edict` | Doom casts the Latverian Edict | Hear the Edict of Latveria! | Внемли эдикту Латверии! | `doom_edict.ogg` |
| `doom.interrupted` | The Edict cast is broken by the player | You dare interrupt Doom?! | Ты смеешь прерывать Дума?! | `doom_interrupted.ogg` |
| `doom.time` | Doom is hit by Time-Stone magic | Time… betrays me? | Время… предаёт меня? | `doom_time.ogg` |
| `doom.siphon` | Doom starts draining a player's Infinity Gauntlet | That gauntlet was always meant for a worthier hand. Give it to Doom! | Эта перчатка всегда предназначалась более достойной руке. Отдай её Думу! | `doom_siphon.ogg` |
| `doom.siphon_broken` | The drain is broken by blows | Insolent wretch! You will pay for that! | Наглец! Ты заплатишь за это! | `doom_siphon_broken.ogg` |
| `doom.siphon_done` | The drain completes: the gauntlet is silenced | Yes... the power of Infinity answers Doom now! | Да... сила Бесконечности теперь отвечает Думу! | `doom_siphon_done.ogg` |
| `doom.decoys` | Decoy gambit: three Dooms appear | Which of us is Doom? Choose... if you dare. | Кто из нас Дум? Выбирай... если осмелишься. | `doom_decoys.ogg` |
| `doom.decoy_revealed` | The player strikes a decoy | A Doombot! You struck at a shadow, fool. | Думбот! Ты ударил тень, глупец. | `doom_decoy_revealed.ogg` |
| `doom.ascend_1` | Six-stone ascension, line 1 | Six stones. Every power in creation. And all of it... Doom's. | Шесть камней. Вся сила мироздания. И вся она... принадлежит Думу. | `doom_ascend_1.ogg` |
| `doom.ascend_2` | Six-stone ascension, line 2 | Behold your god. I unmake every world there ever was. | Узрите своего бога. Я стираю каждый мир, что когда-либо существовал. | `doom_ascend_2.ogg` |
| `doom.ascend_3` | Six-stone ascension, line 3 | From the ashes of the multiverse, Battleworld. KNEEL. | Из пепла мультивселенной - Мир Битв. НА КОЛЕНИ. | `doom_ascend_3.ogg` |
| `doom.defeat_1` | Doom is defeated (normal fight), line 1 | Impossible. Doom does not fall. | Невозможно. Дум не падает. | `doom_defeat_1.ogg` |
| `doom.defeat_2` | Doom is defeated (normal fight), line 2, ~2.5 s later | Keep the castle. Doom will return for it. | Владей замком. Дум вернётся за ним. | `doom_defeat_2.ogg` |
| `doom.steal_1` | Doom steals the incomplete Gauntlet, cutscene line 1 | Pathetic fool... The Infinity Gauntlet belongs to Doom alone! | Бессильный глупец... Перчатка Бесконечности принадлежит лишь Думу! | `doom_steal_1.ogg` |
| `doom.steal_2` | Doom steals the incomplete Gauntlet, cutscene line 2 | One stone short... Doom himself shall finish what you lacked the will to complete! | Не хватает последнего камня... Я сам завершу то, на что у тебя не хватило воли! | `doom_steal_2.ogg` |
| `doom.arena_push` | Overworld battlefield: Doom pushes the player aside as Thanos arrives | Out of my way! You are not Doom's quarry this hour—HE has arrived! | Прочь с дороги! Не ты моя цель в этот час — ОН уже здесь! | `doom_arena_push.ogg` |
| `doom.kill_thanos_1` | Cutscene: Doom channels the stones into Thanos | A 'Mad Titan'... Nothing more than a primitive warlord before the might of Doom! | «Безумный Титан»... Всего лишь примитивный завоеватель перед величием Дума! | `doom_kill_thanos_1.ogg` |
| `doom.kill_thanos_2` | Cutscene: Doom fires at Thanos' warship | And your wretched warship shall turn to dust alongside you! | И твой жалкий корабль обратится в прах вместе с тобой! | `doom_kill_thanos_2.ogg` |
| `doom.final_challenge` | Cutscene end: Doom challenges the player in the Gauntlet fight | And now—you. Come for your Gauntlet with Stormbreaker in hand? Try and take it from Doom! | А теперь — ты. Пришёл за своей Перчаткой с Гром-Секирой в руках? Попробуй забрать её у Дума! | `doom_final_challenge.ogg` |
| `doom.gauntlet_sun` | Gauntlet fight: meteors from the sun (Power Stone), odd casts | The sun itself is mine now. Let it fall! | Теперь и солнце — моё. Пусть падает! | `doom_gauntlet_sun.ogg` |
| `doom.gauntlet_moon` | Gauntlet fight: meteors from the moon (Power Stone), even casts | The moon bows to the Gauntlet. Let it fall on you! | Луна склоняется перед Перчаткой. Пусть упадёт на тебя! | `doom_gauntlet_moon.ogg` |
| `doom.gauntlet_time` | Gauntlet fight: Time Stone, spins the sky | Time is a river, and I turn it. Slow, little king. | Время — река, и я поворачиваю её. Медленнее, маленький король. | `doom_gauntlet_time.ogg` |
| `doom.gauntlet_reality` | Gauntlet fight: Reality Stone, the ground folds | Reality is clay. Watch the ground forget itself! | Реальность — глина. Смотри, как земля забывает себя! | `doom_gauntlet_reality.ogg` |
| `doom.gauntlet_mind` | Gauntlet fight: Mind Stone, lifts the player | Your will is not your own. Rise. | Твоя воля тебе не принадлежит. Поднимись. | `doom_gauntlet_mind.ogg` |
| `doom.gauntlet_soul` | Gauntlet fight: Soul Stone, drains life | Give me your life. I have more use for it. | Отдай мне свою жизнь. Мне она нужнее. | `doom_gauntlet_soul.ogg` |
| `doom.pull` | Any fight: the gravity pull on a player who keeps out of reach | There is no sky above Doom's hall. Come down. | Над залом Дума нет неба. Спускайтесь. | `doom_pull.ogg` |
| `doom.final_defeat_1` | Gauntlet fight: Doom is defeated, line 1 | Impossible... Even wielding the Infinity Stones... Doom... cannot fall... | Невозможно... Даже с мощью Камней Бесконечности... Дум... не может пасть... | `doom_final_defeat_1.ogg` |

## Doombots (17 lines)

| Key | When | English | Русский | File |
|---|---|---|---|---|
| `bot.ask.1` | Hunt: a Doombot stops a townsman and asks (variant 1 of 3) | Citizen. An intruder. Where did they go? | Гражданин. Нарушитель. Куда он ушёл? | `bot_ask_1.ogg` |
| `bot.ask.2` | Hunt: asks a townsman (variant 2) | Halt. You will tell Doom where the stranger is hiding. | Стоять. Ты скажешь Думу, где прячется чужак. | `bot_ask_2.ogg` |
| `bot.ask.3` | Hunt: asks a townsman (variant 3) | Speak. Have you seen the outsider? | Говори. Ты видел чужака? | `bot_ask_3.ogg` |
| `bot.tip` | Hunt: the townsman pointed the way | Information confirmed. Converging. | Сведения подтверждены. Сходимся. | `bot_tip.ogg` |
| `bot.tip.house` | Hunt: a witness says the intruder went into a house | Witness confirms: the intruder entered that house. | Свидетель подтверждает: нарушитель вошёл в тот дом. | `bot_tip_house.ogg` |
| `bot.tip.seen` | Hunt: a witness saw the intruder nearby | Witness saw the intruder nearby. Sweeping the area. | Свидетель видел нарушителя поблизости. Прочёсываем район. | `bot_tip_seen.ogg` |
| `bot.nothing` | Hunt: the townsman knows nothing | Useless. Continue the sweep. | Бесполезно. Продолжаем прочёсывание. | `bot_nothing.ogg` |
| `bot.knock.1` | Hunt: knocks on a house door (variant 1) | Open. By order of Doctor Doom. | Открывайте. По приказу Доктора Дума. | `bot_knock_1.ogg` |
| `bot.knock.2` | Hunt: knocks on a house door (variant 2) | Routine inspection. Do not resist. | Плановая проверка. Не сопротивляйтесь. | `bot_knock_2.ogg` |
| `bot.xray` | Hunt: scans a house through its walls | Thermal scan of the dwelling. | Тепловое сканирование жилища. | `bot_xray.ogg` |
| `bot.siege.1` | Hunt: a house is surrounded | Intruder! The house is surrounded. Come out and surrender to Doom! | Нарушитель! Дом окружён. Выходи и сдавайся Думу! | `bot_siege_1.ogg` |
| `bot.siege.2` | Hunt: siege, halfway through the countdown | Fifteen seconds. Doom's patience has limits. | Пятнадцать секунд. Терпение Дума не безгранично. | `bot_siege_2.ogg` |
| `bot.siege.3` | Hunt: siege, last warning | Last warning. We are coming in. | Последнее предупреждение. Мы входим. | `bot_siege_3.ogg` |
| `bot.siege.storm` | Hunt: the door is broken in / walls breached | Breach! | Штурм! | `bot_siege_storm.ogg` |
| `bot.siege.empty` | Hunt: the besieged house was empty | The house is empty. The intruder slipped away. Widen the search. | Дом пуст. Нарушитель ускользнул. Расширить поиск. | `bot_siege_empty.ogg` |
| `bot.siege.out` | Hunt: the intruder came out of the house | Target is out. Engage! | Цель вышла. Огонь! | `bot_siege_out.ogg` |
| `bot.hot` | Hunt: the district becomes a hot zone | Sector declared a hot zone. Patrols in position. | Сектор объявлен горячей зоной. Патрули на позициях. | `bot_hot.ogg` |

## Not spoken (no recording needed)

- On-screen notices, not speech: `doom.stolen_fled_hint`, `doom.stolen_alert`, `doom.final_defeat_2`, `doom.final_victory_alert` are on-screen notices, not speech.
- `doom.thanos_reply` is Thanos, not Doom.
- `bot.guard`, `bot.follow`, `bot.denied`, `bot.none`, `bot.all_*`, `bot.built`, `bot.forged` are status messages for the player.
