import subprocess, sys, os, json
C1='/tmp/claude-0/clips'; C3='/tmp/claude-0/clips3'; C4='/tmp/claude-0/clips4'
OFF=1.25; SPEED=4.0
# name, src, game_start, game_dur, flash(in) , grade
SEGS=[
 ('intro',    f'{C3}/intro.mkv',          3.0, 10.0, 'black', 'night'),
 ('gauntlet', f'{C1}/gauntlet.mkv',       1.0, 5.0, 'white', 'night'),
 ('power',    f'{C3}/power.mkv',          3.6, 4.0, 'white', 'night'),
 ('beam',     f'{C1}/beam.mkv',           2.2, 6.0, 'white', 'night'),
 ('reality',  f'{C3}/reality.mkv',        3.2, 3.5, 'white', 'night'),
 ('soul',     f'{C3}/soul.mkv',           2.0, 3.0, 'white', 'night'),
 ('time',     f'{C3}/time.mkv',           4.3, 3.0, 'white', 'night'),
 ('planet',   f'{C1}/planet.mkv',         0.5, 12.0,'black', 'space'),
 ('snap',     f'{C4}/snap.mkv',           0.3, 2.6, 'black', 'night'),
 ('arrive1',  f'{C3}/thanos_arrive.mkv',  3.0, 5.5, 'white', 'day'),
 ('arrive2',  f'{C3}/thanos_arrive.mkv',  19.0,6.5, 'white', 'day'),
 ('fight',    f'{C4}/thanos_fight.mkv',   7.2, 7.0, 'white', 'day'),
 ('death',    f'{C4}/thanos_death.mkv',   0.0, 6.0, 'white', 'day'),
 ('crash',    f'{C3}/thanos_crash.mkv',   0.5, 5.0, 'white', 'day'),
]
GRADE={
 'night':"eq=brightness=0.07:contrast=1.18:saturation=1.35:gamma=1.25,colorbalance=rs=.04:bs=.1:rh=.08:bh=-.04",
 'space':"eq=brightness=0.03:contrast=1.15:saturation=1.3:gamma=1.1,colorbalance=bs=.06:rh=.06",
 'day':"eq=brightness=-0.02:contrast=1.2:saturation=1.3:gamma=0.95,colorbalance=rs=.05:bs=.06:rh=.07:bh=-.08",
}
def fx(grade):
    return (f"scale=1920:1080:flags=lanczos,{GRADE[grade]},"
            "split[a][b];[b]lutyuv=y='if(gt(val,150),(val-150)*2.2,0)',gblur=sigma=22[g];"
            "[a][g]blend=all_mode=screen:all_opacity=0.7,"
            "rgbashift=rh=-2:bh=2,vignette=angle=PI/4.2,noise=alls=6:allf=t,"
            "crop=1920:804:0:138,pad=1920:1080:0:138:black,format=yuv420p")
only=set(sys.argv[1:])
for name,src,gs,gd,flash,grade in SEGS:
    if only and name not in only: continue
    out=f'seg/{name}.mp4'
    ss=OFF+gs*SPEED; t=gd*SPEED
    div=SPEED; extra=''
    if name=='snap': div=2.08; extra=',fade=t=out:st=4.55:d=0.45:color=white'
    vf=f"setpts=(PTS-STARTPTS)/{div},fps=30,{fx(grade)},fade=t=in:st=0:d=0.14:color={flash}{extra}"
    cmd=['ffmpeg','-y','-loglevel','error','-ss',f'{ss:.3f}','-t',f'{t:.3f}','-i',src,'-vf',vf,'-an','-r','30',
         '-c:v','libx264','-preset','fast','-crf','16',out]
    print(name, flush=True); subprocess.run(cmd,check=True)
