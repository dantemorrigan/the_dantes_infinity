#!/usr/bin/env python3
"""Writes guide.infinity_gauntlet.* (the in-game guide book) into both lang files and checks page overflow.

Usage: python3 scripts/gen_guide.py
Pages are Minecraft written-book pages: 14 lines, ~114 px wide. The check uses approximate default-font widths.
"""
import json, collections, pathlib, re

ROOT = pathlib.Path(__file__).resolve().parent.parent / 'src/main/resources/assets/infinity_gauntlet/lang'
H = '§5§l'   # heading
B = '§l'     # bold
R = '§r§0'   # back to body
G = '§8'

def p(en, ru): return (en, ru)

PAGES = collections.OrderedDict()
def add(key, en, ru): PAGES[key] = (en, ru)

add('cover',
 f"\n{H}   INFINITY\n   GAUNTLET{R}\n\n{G}Field guide{R}\n\nSix stones, one gauntlet, and the power to unmake worlds.\n\n{G}Turn the page ▶",
 f"\n{H}   ПЕРЧАТКА\n   БЕСКОНЕЧНОСТИ{R}\n\n{G}Путеводитель{R}\n\nШесть камней, одна перчатка и сила, способная стирать миры.\n\n{G}Листай дальше ▶")
add('contents', None, None)  # built below
add('start1',
 f"{H}First steps{R}\nThe gauntlet works only in your {B}offhand{R}: press {B}F{R} with it in hand.\n\nRight-click it in your main hand to open the stone slots.",
 f"{H}Первые шаги{R}\nПерчатка работает только в {B}левой руке{R}: возьми её и нажми {B}F{R}.\n\nПКМ с перчаткой в правой руке открывает слоты камней.")
add('start2',
 f"{H}The six stones{R}\n{B}Power, Space, Reality, Mind, Time, Soul.{R}\n\nStones can't be crafted yet: take them from the Infinity Gauntlet creative tab or use {B}/gauntlet give_stone{R}.",
 f"{H}Шесть камней{R}\n{B}Сила, Пространство, Реальность, Разум, Время, Душа.{R}\n\nКамни пока не крафтятся: бери их в креативе (вкладка мода) или командой {B}/gauntlet give_stone{R}.")
add('wheel1',
 f"{H}Ability wheel{R}\nHold {B}V{R}: the wheel opens. Point at a stone, then at an ability, and release V to choose.\n\nPress {B}R{R} to use it.",
 f"{H}Колесо способностей{R}\nУдерживай {B}V{R}: откроется колесо. Наведи на камень, затем на способность и отпусти V.\n\n{B}R{R} — применить.")
add('wheel2',
 f"{H}Wheel tips{R}\nHold R for charged powers: strike, beam, Reality.\n{B}Esc{R} cancels. {B}PgUp/PgDn{R} switch stones.\nA dimmed ability is unavailable: hover it to see why.",
 f"{H}Советы по колесу{R}\nУдерживай R для заряжаемых сил: удар, луч, Реальность.\n{B}Esc{R} — отмена. {B}PgUp/PgDn{R} — смена камня.\nПотускневшая способность недоступна: наведи, чтобы узнать причину.")
add('power1',
 f"{H}Power Stone{R}\n{B}Power strike{R}: hold R to charge (purple aura), release to smash for damage and knockback.\n\n{B}Planet Destruction{R}: full charge, then strike the ground.",
 f"{H}Камень Силы{R}\n{B}Силовой удар{R}: держи R — заряд (фиолетовая аура), отпусти — удар с уроном и отбрасыванием.\n\n{B}Уничтожение планеты{R}: полный заряд и удар по земле.")
add('power2',
 f"{H}Planet Destruction{R}\nThe planet shatters into fragments. You and nearby players are thrown into Dead Space.\n\n{B}Time Stone + R{R} there puts everything back together.",
 f"{H}Уничтожение планеты{R}\nПланета раскалывается на куски. Ты и игроки рядом попадаете в Мёртвый космос.\n\n{B}Камень Времени + R{R} там собирает всё обратно.")
add('power3',
 f"{H}Sun and Moon{R}\n{B}Celestial Destruction{R}: aim at the sun or moon and hold R for ~3 s. It cracks and falls, the sky turns red, and a 5-minute meteor shower begins.\n\nNo sun means eternal night.",
 f"{H}Солнце и Луна{R}\n{B}Разрушение светил{R}: наведи на солнце или луну и держи R ~3 с. Оно трескается и падает, небо краснеет, начинается 5-минутный метеоритный дождь.\n\nНет солнца — вечная ночь.")
add('power4',
 f"{H}Meteor Stream{R}\nLock on the sun or moon, then aim at a patch of ground and release.\n\nFor 60 s meteors rain on that circle. You are safe from it.",
 f"{H}Поток метеоритов{R}\nЗахвати солнце или луну, затем наведи на участок земли и отпусти.\n\n60 секунд метеориты бьют по этому кругу. Тебе они не вредят.")
add('space1',
 f"{H}Space Stone{R}\n{B}Portal{R}: opens a portal ahead for 30 s. Pick Nether, End or Overworld in the submenu.\n\n{B}Blink{R}: teleport where you look, up to 96 blocks.",
 f"{H}Камень Пространства{R}\n{B}Портал{R}: открывает портал впереди на 30 с. Измерение (Незер, Энд, обычный мир) выбирается в подменю.\n\n{B}Рывок{R}: телепорт туда, куда смотришь, до 96 блоков.")
add('space2',
 f"{H}Flight{R}\nFly freely: WASD, Space up, Shift down, {B}Ctrl{R} boosts.\n\nAbove Y=256 the sky darkens; around Y=640 you leave the atmosphere.",
 f"{H}Полёт{R}\nЛети свободно: WASD, Space вверх, Shift вниз, {B}Ctrl{R} — ускорение.\n\nВыше Y=256 небо темнеет, около Y=640 ты покидаешь атмосферу.")
add('space3',
 f"{H}The Cosmos{R}\nA real Solar System: the Sun, eight planets and the Moon. Aim at a body to see its name and distance.\n\n{B}Warp{R}: pick it in the wheel, press R. 2.5 s charge, then you land on the lit side.",
 f"{H}Космос{R}\nНастоящая Солнечная система: Солнце, восемь планет и Луна. Наведи на тело — увидишь имя и расстояние.\n\n{B}Варп{R}: выбери в колесе и нажми R. Заряд 2,5 с, и ты на освещённой стороне цели.")
add('space4',
 f"{H}Back home{R}\nNear Earth choose {B}Return to atmosphere{R}, or just fly down to its surface.\n\nOther worlds are solid: you can't land on them.",
 f"{H}Домой{R}\nУ Земли выбери {B}Вернуться в атмосферу{R} или просто опустись к её поверхности.\n\nОстальные тела твёрдые: сесть на них нельзя.")
add('reality1',
 f"{H}Reality Stone{R}\n{B}Reality strike{R}: tap R. A red wave turns nearby mobs into butterflies (no drops) and the ground into redstone and fire.\n\nThis change is real.",
 f"{H}Камень Реальности{R}\n{B}Удар Реальности{R}: коротко нажми R. Красная волна превращает мобов в бабочек (без добычи), а землю — в красный камень и огонь.\n\nЭто изменение настоящее.")
add('reality2',
 f"{H}Reality Inversion{R}\nHold R: the world cracks, folds and turns inside out around your gaze. Turn your head to steer the rift.\n\nIt only changes your view. Release and everything returns. You can't walk while holding.",
 f"{H}Изнанка реальности{R}\nДержи R: мир трескается, складывается и выворачивается вслед за взглядом. Поворачивай голову — разлом идёт за ним.\n\nМеняется лишь картинка. Отпусти — всё вернётся.")
add('time1',
 f"{H}Time Stone{R}\n{B}Rewind{R}: you are played back along your last 5 seconds, health and hunger included.\n\n{B}Day / Night{R}: spins the sky to the other half of the day (Overworld only, 30 s cooldown).",
 f"{H}Камень Времени{R}\n{B}Перемотка{R}: тебя «проигрывает» назад на 5 секунд, здоровье и голод тоже.\n\n{B}День / Ночь{R}: прокручивает небо на другую половину суток (только в обычном мире, перезарядка 30 с).")
add('mind1',
 f"{H}Mind Stone{R}\n{B}Grab{R}: on terrain it rips out a chunk of real blocks; on a mob it lifts the mob (not bosses). Move your gaze to steer.\n\nPress R again to {B}throw{R}.",
 f"{H}Камень Разума{R}\n{B}Захват{R}: по земле вырывает кусок настоящих блоков, по мобу — поднимает его (кроме боссов). Взгляд управляет целью.\n\nR ещё раз — {B}бросок{R}.")
add('mind2',
 f"{H}Mind Stone{R}\nA thrown cluster hits everything around and crumbles into falling blocks.\n\n{B}Tear apart{R}: a held mob shakes, turns red and bursts. Loot and XP drop.\n\n{B}Release{R} sets it down safely.",
 f"{H}Камень Разума{R}\nБрошенный кусок бьёт всё вокруг и рассыпается блоками.\n\n{B}Разорвать{R}: удерживаемый моб дрожит, краснеет и лопается. Добыча выпадает.\n\n{B}Отпустить{R} — безопасно поставить.")
add('soul1',
 f"{H}Soul Stone{R}\n{B}Extract soul{R}: aim at a mob. Its soul is pulled out and absorbed.\n\nEach soul gives {B}+1 ❤{R} max health and {B}+0.5{R} damage for 60 s, up to 20 stacks.",
 f"{H}Камень Души{R}\n{B}Извлечь душу{R}: наведи на моба. Его душа вытягивается и поглощается.\n\nКаждая душа даёт {B}+1 ❤{R} макс. здоровья и {B}+0.5{R} урона на 60 с, до 20 душ.")
add('soul2',
 f"{H}Mass extraction{R}\nA wave pulls the souls of every enemy within 24 blocks. Cooldown 45 s.\n\nBosses, peaceful mobs and named mobs are safe. On players it drains up to 2 hearts.",
 f"{H}Массовое извлечение{R}\nВолна вытягивает души всех врагов в радиусе 24 блоков. Перезарядка 45 с.\n\nБоссы, мирные и именованные мобы в безопасности. У игроков забирает до 2 сердец.")
add('beam',
 f"{H}Infinity Beam{R}\nNeeds {B}all six stones{R}. Hold R: 2 s charge, then a 6 s beam of 72 blocks.\n\nIt drills a 9-block tunnel (bedrock stays) and takes 70% of the Ender Dragon's health. Cooldown 45 s.",
 f"{H}Луч Бесконечности{R}\nНужны {B}все шесть камней{R}. Держи R: заряд 2 с, затем луч 6 с на 72 блока.\n\nПробивает тоннель в 9 блоков (бедрок цел), снимает 70% здоровья Дракона Края. Перезарядка 45 с.")
add('snap',
 f"{H}Infinity Snap{R}\nAlso needs all six stones. The universe is erased: everyone lands in the white void and the gauntlet burns out.\n\nTo undo it, right-click a loose {B}Time Stone{R} there.",
 f"{H}Щелчок Бесконечности{R}\nТоже нужны все шесть камней. Вселенная стирается: все оказываются в белой пустоте, а перчатка сгорает.\n\nЧтобы вернуть всё, ПКМ по {B}Камню Времени{R} вне перчатки.")
add('ore1',
 f"{H}Uru ore{R}\nUru is a rare metal in the Overworld, from the bedrock up to Y=0, richest near {B}Y=-32{R}. Rarer than diamonds; the ore glows faintly.",
 f"{H}Руда Уру{R}\nУру — редкий металл Верхнего мира: от дна до Y=0, богаче всего около {B}Y=-32{R}. Реже алмазов; руда слабо светится.")
add('ore2',
 f"{H}Mining and smelting{R}\nMine it with a {B}diamond or netherite pickaxe{R}. Fortune works; Silk Touch keeps the ore.\n\nSmelt the ore or {B}raw uru{R} in a furnace or blast furnace into {B}Uru ingots{R}.",
 f"{H}Добыча и плавка{R}\nЛомай {B}алмазной или незеритовой киркой{R}. Удача работает, Шёлковое касание сохраняет руду.\n\nПереплавь руду или {B}сырое уру{R} в печи или плавильне в {B}слитки Уру{R}.")
add('armor1',
 f"{H}Armor of Uru{R}\nCraft it like iron armor, from Uru ingots: helmet, chestplate, leggings, boots.\n\nFireproof, tougher than netherite (24 armor points). Repair with ingots on an anvil.",
 f"{H}Броня из Уру{R}\nКрафтится как железная, из слитков Уру: шлем, нагрудник, поножи, ботинки.\n\nОгнеупорна, прочнее незерита (24 очка брони). Чинится слитками на наковальне.")
add('armor2',
 f"{H}Against the gauntlet{R}\nA full six-stone gauntlet ignores normal armor and shields. A complete Uru set lets through only 25% of such a hit; instant kills become survivable.\n\nWorld events are not blocked.",
 f"{H}Против перчатки{R}\nПолная перчатка игнорирует обычную броню и щит. Целый комплект Уру пропускает лишь 25% удара, мгновенные убийства становятся переживаемыми.\n\nСобытия мира не блокируются.")
add('extractor1',
 f"{H}Infinity Extractor{R}\nTakes a stone from someone's gauntlet. Hold right-click on a target within 16 blocks and in sight.\n\nThree 3-second cycles pull the stone out. Progress is kept for 20 s.",
 f"{H}Извлекатель Бесконечности{R}\nВытягивает камень из чужой перчатки. Держи ПКМ на цели в пределах 16 блоков и в прямой видимости.\n\nТри цикла по 3 с вытаскивают камень. Прогресс помнится 20 с.")
add('extractor2',
 f"{H}Extractor tips{R}\nShift + right-click on nothing picks which stone to pull. A stone in use can't be taken.\n\nShift + right-click with a stone inside takes it out; right-click on nothing returns it to your gauntlet.",
 f"{H}Советы по извлекателю{R}\nShift + ПКМ в пустоту выбирает, какой камень тянуть. Камень в деле не взять.\n\nShift + ПКМ с камнем внутри достаёт его; ПКМ в пустоту возвращает его в перчатку.")
add('settings',
 f"{H}Settings{R}\n{B}Language{R}: Options or Pause menu, button {B}Infinity Gauntlet{R} (bottom left). Auto, English or Русский.\n\nAll keys can be rebound in Controls.",
 f"{H}Настройки{R}\n{B}Язык{R}: меню Настроек или Паузы, кнопка {B}Infinity Gauntlet{R} (слева внизу). Авто, English или Русский.\n\nВсе клавиши меняются в настройках управления.")
add('end',
 f"{H}Good to know{R}\nLost this book? Type {B}/gauntlet_guide{R}.\n\nOperators have {B}/gauntlet{R} for testing: stones, effects, dummies.\n\n{G}Use the power wisely, wielder.",
 f"{H}Полезно знать{R}\nПотерял книгу? Введи {B}/gauntlet_guide{R}.\n\nУ операторов есть {B}/gauntlet{R} для проверок: камни, эффекты, куклы.\n\n{G}Распоряжайся силой мудро.")

# --- contents: built from the page order ---
order = list(PAGES)
def pn(k): return str(order.index(k) + 1)
toc_en = [("First steps",'start1'),("Ability wheel",'wheel1'),("Power",'power1'),("Space",'space1'),("Reality",'reality1'),("Time",'time1'),("Mind",'mind1'),("Soul",'soul1'),("Beam, Snap",'beam'),("Uru",'ore1'),("Extractor",'extractor1'),("Settings",'settings')]
toc_ru = [("Первые шаги",'start1'),("Колесо",'wheel1'),("Сила",'power1'),("Пространство",'space1'),("Реальность",'reality1'),("Время",'time1'),("Разум",'mind1'),("Душа",'soul1'),("Луч, щелчок",'beam'),("Уру",'ore1'),("Извлекатель",'extractor1'),("Настройки",'settings')]
PAGES['contents'] = (
 f"{H}Contents{R}\n" + "\n".join(f"{n} {G}{pn(k)}{R}" for n,k in toc_en),
 f"{H}Содержание{R}\n" + "\n".join(f"{n} {G}{pn(k)}{R}" for n,k in toc_ru))

# --- overflow check (approximate default font) ---
NARROW = {'i':2,'!':2,'.':2,',':2,':':2,';':2,'|':2,"'":2,'l':3,'`':3,'I':4,'t':4,' ':4,'[':4,']':4,'f':5,'k':5,'"':5,'(':5,')':5,'*':5,'<':5,'>':5,'{':5,'}':5,'@':7,'~':7,'▶':7,'❤':7}
def cw(c, bold): return NARROW.get(c, 6) + (1 if bold else 0)
def lines_needed(text):
    """Greedy word-wrap, tracking bold through section codes."""
    bold = False; total = 0
    for para in text.split('\n'):
        words = []; cur = ''; 
        # tokenize while keeping bold state per char
        chars = []; i = 0
        while i < len(para):
            if para[i] == '§' and i + 1 < len(para):
                c = para[i+1].lower()
                if c == 'l': bold = True
                elif c in '0123456789abcdefr': bold = False
                i += 2; continue
            chars.append((para[i], bold)); i += 1
        if not chars: total += 1; continue
        x = 0; n = 1; word = []
        def flush():
            nonlocal x, n, word
            w = sum(cw(c,b) for c,b in word)
            if x + w > 114 and x > 0: n += 1; x = 0
            x += w; word = []
        for c,b in chars:
            if c == ' ':
                flush(); x += cw(' ', b)
            else: word.append((c,b))
        flush(); total += n
    return total

problems = 0
for k,(en,ru) in PAGES.items():
    for lang,t in (('en',en),('ru',ru)):
        n = lines_needed(t)
        if n > 13: print(f'OVERFLOW {k} [{lang}] ~{n} lines'); problems += 1
print('GuideBook.PAGE_COUNT must be', len(PAGES)); print('pages:', len(PAGES), 'overflow:', problems)

# --- write to lang files ---
for lang, idx in (('en_us',0),('ru_ru',1)):
    path = ROOT / f'{lang}.json'
    d = json.load(open(path), object_pairs_hook=collections.OrderedDict)
    for k in [k for k in d if k.startswith('guide.infinity_gauntlet.')]: del d[k]
    d['guide.infinity_gauntlet.title'] = ('Infinity Gauntlet: Field Guide','Перчатка Бесконечности: путеводитель')[idx]
    for i,(k,v) in enumerate(PAGES.items()):
        d[f'guide.infinity_gauntlet.page{i+1}'] = v[idx]
    with open(path,'w') as f: json.dump(d,f,ensure_ascii=False,indent=2); f.write('\n')
