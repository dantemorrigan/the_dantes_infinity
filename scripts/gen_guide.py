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
 f"{H}Where to look{R}\n{B}Power{R}: follow the fallen stars.\n{B}Space{R}: beat the dragon. Wear iron.\n{B}Reality{R}: shoot the Nether mist wisp.\n{B}Time{R}: 3 gears in the Time Temple.\n{B}Mind{R}: map to the Ruined City.\n{B}Soul{R}: temple map. Offer a sacrifice.",
 f"{H}Где искать{R}\n{B}Сила{R}: упавшие звёзды.\n{B}Пространство{R}: дракон и броня.\n{B}Реальность{R}: туман в Незере.\n{B}Время{R}: 3 шестерни в Храме.\n{B}Разум{R}: Разрушенный город.\n{B}Душа{R}: карта храма и жертва.")
add('wheel',
 f"{H}Ability wheel{R}\nHold {B}V{R}, point at a stone, then an ability, release. {B}R{R} uses it; hold R for charged powers.\n\n{B}Esc{R} cancels. Pin with 1–6. Hold B for favorites.",
 f"{H}Колесо способностей{R}\nДержи {B}V{R}, наведи на камень, потом на способность, отпусти. {B}R{R} — применить, держи R для заряжаемых.\n\n{B}Esc{R} — отмена. 1–6 закрепляют способность. Держи B для избранного.")
add('power',
 f"{H}Power Stone{R}\n{B}Power strike{R}: hold R, release.\n{B}Planet Destruction{R}: full charge, hit the ground.\n{B}Celestial Destruction{R}: aim at the sun or moon.\n{B}Meteor Stream{R}: lock on, aim a circle.\n\nHeld bare, it kills.",
 f"{H}Камень Силы{R}\n{B}Силовой удар{R}: держи R, отпусти.\n{B}Уничтожение планеты{R}: заряд и удар в землю.\n{B}Разрушение светил{R}: наведи на солнце или луну.\n{B}Поток метеоритов{R}: захват, круг.\n\nГолым он убивает.")
add('space',
 f"{H}Space Stone{R}\n{B}Portal{R}: a door to the Nether, End or Overworld.\n{B}Blink{R}: teleport where you look.\n{B}Flight{R}: Ctrl boosts; beyond the sky lies the Solar System.\n{B}Warp{R}: jump to a planet.",
 f"{H}Камень Пространства{R}\n{B}Портал{R}: дверь в другое измерение.\n{B}Рывок{R}: телепорт туда, куда смотришь.\n{B}Полёт{R}: Ctrl ускоряет; за небом — Солнечная система.\n{B}Варп{R}: прыжок к планете.")
add('reality',
 f"{H}Reality Stone{R}\n{B}Reality strike{R}: select the strike and press R. Mobs become butterflies, the ground turns to redstone and fire.\n\n{B}Inversion{R}: hold R. The world folds around your gaze. Blocks stay intact.",
 f"{H}Камень Реальности{R}\n{B}Удар Реальности{R}: выбери удар и нажми R. Мобы — бабочки, земля — красный камень и огонь.\n\n{B}Изнанка{R}: держи R. Мир складывается вслед за взглядом. Блоки не ломаются.")
add('time',
 f"{H}Time Stone{R}\n{B}Rewind{R}: you are played back through your last 5 seconds.\n{B}Day / Night{R}: spins the sky to the other half of the day.\n\nIn Dead Space, rewind reassembles a destroyed planet.",
 f"{H}Камень Времени{R}\n{B}Перемотка{R}: тебя «проигрывает» назад на 5 секунд.\n{B}День / Ночь{R}: прокручивает небо на другую половину суток.\n\nВ Мёртвом космосе перемотка собирает разрушенную планету.")
add('mind',
 f"{H}Mind Stone{R}\n{B}Grab{R}: lift blocks or a mob with your gaze, R again to {B}throw{R}.\n{B}Tear apart{R}: a held mob bursts.\n{B}Release{R}: put it down safely.",
 f"{H}Камень Разума{R}\n{B}Захват{R}: поднимай блоки или моба взглядом, R ещё раз — {B}бросок{R}.\n{B}Разорвать{R}: удерживаемый моб лопается.\n{B}Отпустить{R}: безопасно поставить.")
add('soul',
 f"{H}Soul Stone{R}\n{B}Extract soul{R}: pull a mob's soul. Each gives +1 ❤ and +0.5 damage for a minute.\n{B}Mass extraction{R}: a wave takes every enemy's soul around you.",
 f"{H}Камень Души{R}\n{B}Извлечь душу{R}: вытяни душу моба. Каждая даёт +1 ❤ и +0.5 урона на минуту.\n{B}Массовое извлечение{R}: волна забирает души всех врагов рядом.")
add('beam',
 f"{H}All six stones{R}\n{B}Infinity Beam{R}: hold R. Drills tunnels, wounds the Ender Dragon.\n\n{B}Infinity Snap{R}: erases the universe and burns the gauntlet out. A loose Time Stone can undo it.",
 f"{H}Все шесть камней{R}\n{B}Луч Бесконечности{R}: держи R. Пробивает тоннели, ранит Дракона.\n\n{B}Щелчок{R}: стирает вселенную и сжигает перчатку. Вернуть может Камень Времени вне перчатки.")
add('uru',
 f"{H}Uru{R}\nFind ore near Y=−32. Mine with diamond or netherite and smelt it.\n\n{B}Uru armor{R} uses iron recipes. A full set reduces full-gauntlet damage. Ingots also go into the gauntlet recipe.",
 f"{H}Уру{R}\nИщи около Y=−32. Добудь алмазной или незеритовой киркой и переплавь.\n\n{B}Броня из Уру{R} крафтится как железная. Полный комплект гасит урон полной перчатки. Слитки нужны и для перчатки.")
add('extractor',
 f"{H}Infinity Extractor{R}\nPulls a stone out of someone's gauntlet: hold right-click on them, three cycles. Shift + right-click picks which stone.\n\nYour stones stay with you when you die, unless another player kills you.",
 f"{H}Извлекатель{R}\nВытягивает камень из чужой перчатки: держи ПКМ на цели, три цикла. Shift + ПКМ выбирает камень.\n\nПосле смерти камни остаются у тебя, если не убил другой игрок.")
add('settings',
 f"{H}Settings{R}\n{B}Language{R}: Options or Pause menu, button {B}Infinity Gauntlet{R}.\n\nKeys can be rebound in Controls. Lost the book? {B}/gauntlet_guide{R}.\n\n{G}Use the power wisely.",
 f"{H}Настройки{R}\n{B}Язык{R}: меню Настроек или Паузы, кнопка {B}Infinity Gauntlet{R}.\n\nКлавиши меняются в настройках управления. Потерял книгу? {B}/gauntlet_guide{R}.\n\n{G}Распоряжайся силой мудро.")
# --- contents: built from the page order ---
order = list(PAGES)
def pn(k): return str(order.index(k) + 1)
toc_en = [("Hints",'hints'),("Wheel",'wheel'),("Power",'power'),("Space",'space'),("Reality",'reality'),("Time",'time'),("Mind",'mind'),("Soul",'soul'),("Beam",'beam'),("Uru",'uru'),("Extractor",'extractor'),("Settings",'settings')]
toc_ru = [("Наводки",'hints'),("Колесо",'wheel'),("Сила",'power'),("Пространство",'space'),("Реальность",'reality'),("Время",'time'),("Разум",'mind'),("Душа",'soul'),("Луч",'beam'),("Уру",'uru'),("Извлекатель",'extractor'),("Опции",'settings')]
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
