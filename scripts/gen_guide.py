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
add('start',
 f"{H}First steps{R}\nThe gauntlet works in your {B}offhand{R}: press {B}F{R}. Right-click it in your main hand to open the stone slots.\n\nAll six stones have {B}survival sources{R}.",
 f"{H}Первые шаги{R}\nПерчатка работает в {B}левой руке{R}: нажми {B}F{R}. ПКМ с ней в правой руке открывает слоты камней.\n\nВсе шесть камней доступны в {B}выживании{R}.")
add('hints',
 f"{H}Where to look{R}\n{B}Power{R}: follow the fallen stars.\n{B}Space{R}: beat the dragon. Wear iron.\n{B}Reality{R}: shoot the Nether mist wisp.\n{B}Time{R}: 3 gears in the Time Temple.\n{B}Mind{R}: Ruined City. Beat Loki.\n{B}Soul{R}: temple map. Offer a sacrifice.",
 f"{H}Где искать{R}\n{B}Сила{R}: упавшие звёзды.\n{B}Пространство{R}: дракон и броня.\n{B}Реальность{R}: туман в Незере.\n{B}Время{R}: 3 шестерни в Храме.\n{B}Разум{R}: Локи в Разрушенном городе.\n{B}Душа{R}: карта храма и жертва.")
add('wheel',
 f"{H}Ability wheel{R}\nHold {B}V{R}, point at a stone, then an ability, release. {B}R{R} uses it; hold R for charged powers.\n\n{B}Esc{R} cancels. Pin with 1–6. Hold B for favorites.",
 f"{H}Колесо способностей{R}\nДержи {B}V{R}, наведи на камень, потом на способность, отпусти. {B}R{R} — применить, держи R для заряжаемых.\n\n{B}Esc{R} — отмена. 1–6 закрепляют способность. Держи B для избранного.")
add('power',
 f"{H}Power Stone{R}\n{B}Power strike{R}: hold R, release.\n{B}Planet Destruction{R}: needs Time. Charge, hit ground.\n{B}Celestial Destruction{R}: aim at a luminary.\n{B}Meteor Stream{R}: lock on, aim low, release R.\n\nHeld bare, it kills.",
 f"{H}Камень Силы{R}\n{B}Силовой удар{R}: держи R, отпусти.\n{B}Уничтожение планеты{R}: Камень Времени, удар вниз.\n{B}Разрушение светил{R}: цель — светило.\n{B}Поток метеоритов{R}: захват, вниз, отпусти R.\nГолым убивает.")
add('space',
 f"{H}Space Stone{R}\n{B}Portal{R}: a door to the Nether, End or Overworld.\n{B}Blink{R}: teleport where you look.\n{B}Flight{R}: Ctrl boosts; beyond the sky lies the Solar System.\n{B}Warp{R}: jump to a planet.",
 f"{H}Камень Пространства{R}\n{B}Портал{R}: дверь в другое измерение.\n{B}Рывок{R}: телепорт туда, куда смотришь.\n{B}Полёт{R}: Ctrl ускоряет; за небом — Солнечная система.\n{B}Варп{R}: прыжок к планете.")
add('reality',
 f"{H}Reality Stone{R}\n{B}Reality strike{R}: press R. Mobs turn to butterflies, ground to redstone and fire.\n\n{B}Inversion{R}: hold R while moving. The vortex moves with you. Mobs dissolve, and terrain stays twisted.",
 f"{H}Камень Реальности{R}\n{B}Удар Реальности{R}: нажми R. Мобы — бабочки, земля — редстоун и огонь.\n\n{B}Изнанка{R}: держи R на ходу. Вихрь движется за тобой. Мобы исчезают, а рельеф остаётся закрученным.")
add('time',
 f"{H}Time Stone{R}\n{B}Rewind{R}: replay your last 5 seconds backwards.\n{B}Time Flow{R}: hold R and aim.\n{B}Day / Night{R}: switch the sky.\n\nRewind restores a broken planet in Dead Space.",
 f"{H}Камень Времени{R}\n{B}Перемотка{R}: назад на 5 секунд.\n{B}Поток времени{R}: держи R и целься.\n{B}День / Ночь{R}: сменить небо.\n\nВ Мёртвом космосе перемотка восстанавливает планету.")
add('mind',
 f"{H}Mind Stone{R}\n{B}Grab{R}: lift blocks or a mob with your gaze, R again to {B}throw{R}.\n{B}Tear apart{R}: a held mob bursts.\n{B}Release{R}: put it down safely.\n\n10 city zombies summon Loki.",
 f"{H}Камень Разума{R}\n{B}Захват{R}: поднимай блоки или моба взглядом, R ещё раз — {B}бросок{R}.\n{B}Разорвать{R}: удерживаемый моб лопается.\n{B}Отпустить{R}: безопасно поставить.\n\n10 зомби города вызывают Локи.")
add('soul',
 f"{H}Soul Stone{R}\n{B}Extract soul{R}: pull a mob's soul. Each gives +1 ❤ and +0.5 damage for a minute.\n{B}Mass extraction{R}: a wave takes every enemy's soul around you.",
 f"{H}Камень Души{R}\n{B}Извлечь душу{R}: вытяни душу моба. Каждая даёт +1 ❤ и +0.5 урона на минуту.\n{B}Массовое извлечение{R}: волна забирает души всех врагов рядом.")
add('beam',
 f"{H}All six stones{R}\n{B}Infinity Beam{R}: hold R. Burns through terrain and kills most mobs on contact.\n\n{B}Infinity Snap{R}: erases the universe and burns the gauntlet out. A loose Time Stone can undo it.",
 f"{H}Все шесть камней{R}\n{B}Луч Бесконечности{R}: держи R. Бурит землю и сжигает мобов.\n\n{B}Щелчок{R}: стирает вселенную и сжигает перчатку. Вернуть может Камень Времени вне перчатки.")
add('loki',
 f"{H}Loki{R}\nDefeat 10 zombies in the Ruined City. Loki creates illusions and strikes from the ruins. At 45% health he becomes Fenrir. Dodge the marks. After victory the Mind Stone flies to a participant.",
 f"{H}Локи{R}\nОдолей 10 зомби в Разрушенном городе. Локи создаёт копии и бьёт с руин. На 45% здоровья он станет Фенриром. Избегай меток. После победы Камень Разума прилетит к участнику.")
add('cosmos',
 f"{H}Solar System{R}\nFly beyond the sky with Space.\n{B}Ctrl{R}: boost.\n{B}V{R}: choose a route.\n{B}R{R}: warp to it.\n\nTen bodies have distinct icons. Approach Earth to return. Planets rotate slowly.",
 f"{H}Солнечная система{R}\nС Пространством лети выше неба.\n{B}Ctrl{R}: ускорение.\n{B}V{R}: выбрать маршрут.\n{B}R{R}: выполнить варп.\n\nУ 10 светил свои иконки. Подлети к Земле для возврата. Планеты вращаются.")
add('cosmic_combat',
 f"{H}Powers in space{R}\n{B}Power / Reality{R}: aim in any direction.\n{B}Inversion{R}: bends space and damages once per cast.\n{B}Beam / Time Flow{R}: aim through a target.\n\nBlink and meteors also work in flight.",
 f"{H}Силы в космосе{R}\n{B}Сила / Реальность{R}: целься в любую сторону.\n{B}Изнанка{R}: гнёт пространство, бьёт раз за применение.\n{B}Луч / Поток времени{R}: наведи на цель.\n\nРывок и метеориты работают в полёте.")
add('thanos',
 f"{H}Thanos{R}\nGather {B}five stones{R}: after half a day of calm the Mad Titan comes for them.\n{B}Uru armor{R} is a must. The worn gauntlet hurts him most, then bare stones and explosions. Swords barely scratch him.\nBeaten once, he never returns.",
 f"{H}Танос{R}\nСобери {B}пять камней{R}: через полсуток придёт Безумный Титан.\n{B}Броня Уру{R} обязательна. Сильнее всего бьёт перчатка, затем камни в руке и взрывы. Мечи почти не ранят.\nПобеждённый, он не вернётся.")
add('uru',
 f"{H}Uru{R}\nFind ore near Y=−32. Mine with diamond or netherite and smelt it.\n\n{B}Uru armor{R} uses iron recipes. A full set reduces full-gauntlet damage. Find a {B}Gauntlet Schematic{R} in chests or shops. Use it to learn recipes.",
 f"{H}Уру{R}\nРуда около Y=−32. Добудь алмазной или незеритовой киркой, переплавь.\n\n{B}Броня из Уру{R} снижает урон Перчатки. {B}Схема перчатки{R} открывает рецепты по ПКМ. Ищи в сундуках и у торговцев.")
add('extractor',
 f"{H}Infinity Extractor{R}\nPulls a stone out of someone's gauntlet: hold right-click on them, three cycles. Shift + right-click picks which stone.\n\nYour stones stay with you when you die, unless another player kills you.",
 f"{H}Извлекатель{R}\nВытягивает камень из чужой перчатки: держи ПКМ на цели, три цикла. Shift + ПКМ выбирает камень.\n\nПосле смерти камни остаются у тебя, если не убил другой игрок.")
add('settings',
 f"{H}Settings{R}\n{B}Menu{R}: Options or Pause, button {B}Infinity Gauntlet{R} (language, wheel mode, HUD, shake, flash).\n\nLost the book? {B}/gauntlet_guide{R}.\n\n{G}Use the power wisely.",
 f"{H}Настройки{R}\n{B}Меню{R}: Настройки или Пауза, кнопка {B}Infinity Gauntlet{R} (язык, колесо, HUD, тряска, вспышки).\n\nПотерял книгу? {B}/gauntlet_guide{R}.\n\n{G}Распоряжайся силой мудро.")
add('storm_find',
 f"{H}Stormbreaker{R}\nOne axe per world. Forge it with {B}5 Uru ingots{R}, a Nether Star and a jungle log. Or find it after a thunderstorm skyfall. A compass appears within 110 blocks. Right-click to claim it.",
 f"{H}Гром-Секира{R}\nОдна на сервер. Для ковки нужны {B}5 слитков Уру{R}, звезда Незера и тропическое бревно. В грозу ищи её по компасу в радиусе 110 блоков. Подними секиру ПКМ.")
add('storm_combat',
 f"{H}Stormbreaker combat{R}\n{B}Left click{R}: three-hit combo. Hold at the ground and release for a lightning wall.\n{B}Right click{R}: lightning, radius 7.\n{B}H{R}: storm, radius 20.\n{B}X{R}: throw. {B}J{R}: flight.",
 f"{H}Бой с Гром-Секирой{R}\n{B}ЛКМ{R}: тройное комбо. Зажми на земле и отпусти для стены молний.\n{B}ПКМ{R}: молнии, радиус 7.\n{B}H{R}: гроза, радиус 20.\n{B}X{R}: бросок. {B}J{R}: полёт.")
add('storm_owner',
 f"{H}The chosen wielder{R}\n{B}Q{R} or death plants the axe. Only the owner can recover it. {B}K{R} recalls it from any dimension or the void.\nIn hand, it gives strength and softens gauntlet hits.",
 f"{H}Владелец секиры{R}\n{B}Q{R} или смерть оставляют секиру в земле. Забрать её может владелец. {B}K{R} призывает её из других измерений и бездны.\nВ руке даёт силу и снижает урон Перчатки.")
# --- The Fury of Doom (appended, so the earlier page numbers stay put) ---
add('doom',
 f"{H}The Fury of Doom{R}\nBeat {B}Loki{R}. Carry the {B}Mind Stone{R}: it reads Doom's seal on the Space Stone portal. Pick {B}Latveria{R} on the portal wheel.\nFriends within 4 blocks come along.",
 f"{H}Ярость Дума{R}\nПобеди {B}Локи{R}. Носи {B}Камень Разума{R}: он прочтёт печать Дума на портале Пространства. Выбери {B}Латверию{R} в колесе порталов.\nДрузья в 4 блоках идут с тобой.")
add('latveria',
 f"{H}Latveria{R}\nA stormy Carpathian night: a city, a road, {B}Castle Doom{R}. Two themes play as you walk.\nThe {B}gate{R} at the city's south end takes you home: stand in it for 2 seconds.\nIts stone does not break: no beam, blast or pick cuts a way in.",
 f"{H}Латверия{R}\nБурная карпатская ночь: город, дорога, {B}замок Дума{R}. Пока идёшь, играют две темы.\n{B}Врата{R} на юге города вернут домой: постой в них 2 секунды.\nКамень города не ломается: ни луч, ни взрыв, ни кирка не пробьют путь.")
add('assault',
 f"{H}The assault{R}\nSquads hold the {B}gate{R} and the {B}yard{R}: beat them and the doors open.\nOr go east: the {B}service door{R} leads to the lab. Right-click the {B}generator{R}: Doom and the shields weaken, a side door opens, the {B}searchlights{R} die.",
 f"{H}Штурм{R}\nОтряды держат {B}ворота{R} и {B}двор{R}: разбей их, и двери откроются.\nИли иди на восток: {B}служебная дверь{R} ведёт в лабораторию. ПКМ по {B}генератору{R}: Дум и щиты слабеют, открывается боковая дверь, гаснут {B}прожекторы{R}.")
add('doombots',
 f"{H}Doombots{R}\nThey take turns to strike.\n{B}Soldier{R} shoots. {B}Bastion{R} shields its front. {B}Interceptor{R} dives, then is dazed. {B}Arcanist{R} wards. {B}Engineer{R} repairs. {B}Guard{R} swings a halberd.\nAfter the fight they obey you.",
 f"{H}Думботы{R}\nБьют по очереди.\n{B}Солдат{R} стреляет. {B}Бастион{R} закрыт спереди. {B}Перехватчик{R} пикирует и оглушён. {B}Арканист{R} ставит защиту. {B}Инженер{R} чинит. {B}Страж{R} с алебардой.\nПосле боя слушаются тебя.")
add('doomfight',
 f"{H}Doctor Doom{R}\nThree stages: steel, sorcery, the {B}Edict{R}.\nIn the Edict break a glowing {B}seal node{R} to save its side of the hall, or hit him hard to stop the cast.\nAfter big attacks he is open.",
 f"{H}Доктор Дум{R}\nТри этапа: сталь, магия, {B}Эдикт{R}.\nВ Эдикт разбей светящийся {B}узел печати{R}, чтобы спасти свою часть зала, или сильно ударь Дума, чтобы сорвать каст.\nПосле мощных атак он открыт.")
add('doomtricks',
 f"{H}Doom's tricks{R}\n{B}Searchlights{R} sweep the slope: caught, the alarm calls Interceptors.\nStage 2: three Dooms. The fakes are {B}Doombots{R}: the real one has the boss bar.\nStage 3: he {B}siphons{R} your Infinity Gauntlet. Break sight, blink or hit hard.",
 f"{H}Уловки Дума{R}\n{B}Прожекторы{R} обшаривают склон: поймают - тревога.\nЭтап 2: три Дума. Ложные - {B}думботы{R}, у настоящего полоса босса.\nЭтап 3: он {B}тянет силу{R} Перчатки. Скройся, блинк или бей сильно.")
add('doomspoils',
 f"{H}The spoils{R}\nVictory gives you Doom's {B}armor{R}, gauntlet, grimoire, the {B}Latveria Key{R}, a Doombot controller and Castle Seals for friends.\nThe castle is yours; the {B}Key{R} opens it from any dimension.",
 f"{H}Трофеи{R}\nПобеда даёт {B}броню{R} Дума, перчатку, гримуар, {B}Ключ Латверии{R}, пульт Думботов и Печати замка для друзей.\nЗамок твой; {B}Ключ{R} открывает его из любого измерения.")
add('doomcourt',
 f"{H}The court{R}\nAs lord, right-click the {B}throne{R}: an honour guard forms.\n{B}Mechanical Heart{R}: a Doombot. Other hand picks it: reactor Bastion, feather Interceptor, jade Arcanist, repair tool Engineer, axe Guard. An {B}anvil{R} makes it sturdier.",
 f"{H}Двор{R}\nКак хозяин, ПКМ по {B}трону{R}: встанет караул.\n{B}Мех. сердце{R}: думбот. Вторая рука: реактор Бастион, перо Перехватчик, нефрит Арканист, ремонт Инженер, топор Страж. {B}Наковальня{R} - крепче.")
add('doomarmor',
 f"{H}Doom's armor{R}\nFull set: hold {B}M{R} to pick a power, {B}N{R} casts it.\nBolt, Bastion, Jets (Space climbs), Rift Step, Suns, Edict. Rift and Edict show where they land; the suns wait, one per press.",
 f"{H}Броня Дума{R}\nПолный набор: держи {B}M{R}, выбери силу, {B}N{R} применяет.\nРазряд, Бастион, Полёт (Пробел вверх), Шаг, Солнца, Эдикт. Шаг и Эдикт показывают цель; солнца ждут, по одному за нажатие.")
# --- contents: built from the page order ---
order = list(PAGES)
def pn(k): return str(order.index(k) + 1)
toc_en = [("Wheel",'wheel'),("Power",'power'),("Space",'space'),("Reality",'reality'),("Time",'time'),("Mind",'mind'),("Soul",'soul'),("Loki",'loki'),("Thanos",'thanos'),("Uru",'uru','Tools','extractor'),("Stormbreaker",'storm_find'),("Doom",'doom')]
toc_ru = [("Колесо",'wheel'),("Сила",'power'),("Пространство",'space'),("Реальность",'reality'),("Время",'time'),("Разум",'mind'),("Душа",'soul'),("Локи",'loki'),("Танос",'thanos'),("Уру",'uru','Инстр.','extractor'),("Гром-Секира",'storm_find'),("Дум",'doom')]
def toc_line(t):
    if len(t) == 2: return f"{t[0]} {G}{pn(t[1])}{R}"
    return f"{t[0]} {G}{pn(t[1])}{R} · {t[2]} {G}{pn(t[3])}{R}"
PAGES['contents'] = (
 f"{H}Contents{R}\n" + "\n".join(toc_line(t) for t in toc_en),
 f"{H}Содержание{R}\n" + "\n".join(toc_line(t) for t in toc_ru))

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
print('pages:', len(PAGES), 'overflow:', problems)

# --- split the marked-up text into styled segments (legacy § codes render badly inside books) ---
STYLE_OF = [('§5§l', 'H'), ('§l', 'B'), ('§8', 'G'), ('§r§0', 'T')]
def segments(text):
    out = []; style = 'T'; buf = ''; i = 0
    while i < len(text):
        for code, st in STYLE_OF:
            if text.startswith(code, i):
                if buf: out.append((style, buf)); buf = ''
                style = st; i += len(code); break
        else:
            buf += text[i]; i += 1
    if buf: out.append((style, buf))
    return out

SEGS = {}
for k,(en,ru) in PAGES.items():
    se, sr = segments(en), segments(ru)
    if [a for a,_ in se] != [a for a,_ in sr]:
        print(f'STYLE MISMATCH {k}: {[a for a,_ in se]} vs {[a for a,_ in sr]}'); problems += 1
    SEGS[k] = (se, sr)
if problems: raise SystemExit('fix the problems above first')

# --- write lang files + GuidePages.java ---
for lang, idx in (('en_us',0),('ru_ru',1)):
    path = ROOT / f'{lang}.json'
    d = json.load(open(path), object_pairs_hook=collections.OrderedDict)
    for k in [k for k in d if k.startswith('guide.infinity_gauntlet.')]: del d[k]
    d['guide.infinity_gauntlet.title'] = ('Infinity Gauntlet: Field Guide','Перчатка Бесконечности: путеводитель')[idx]
    for i,k in enumerate(PAGES):
        for j,(st,txt) in enumerate(SEGS[k][idx]):
            d[f'guide.infinity_gauntlet.p{i+1}.{j+1}'] = txt
    with open(path,'w') as f: json.dump(d,f,ensure_ascii=False,indent=2); f.write('\n')

rows = ',\n'.join('\t\t"' + ''.join(st for st,_ in SEGS[k][0]) + '"' for k in PAGES)
JAVA = ROOT.parents[3] / 'java/com/dantemorrigan/gauntlet/guide/GuidePages.java'
JAVA.write_text(f"""package com.dantemorrigan.gauntlet.guide;

/** GENERATED by scripts/gen_guide.py - do not edit. One string per page, one letter per styled text segment. */
final class GuidePages {{
	/** H heading, T text, B bold, G gray. */
	static final String[] STYLES = {{
{rows}
	}};

	private GuidePages() {{}}
}}
""")
