import subprocess, os, sys
os.chdir('/tmp/claude-0/ed')
FONT='/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'
RES='/home/user/the_dantes_infinity/src/main/resources/assets/infinity_gauntlet/sounds'
SEG=['intro','gauntlet','power','beam','reality','soul','time','planet','snap','arrive1','arrive2','fight','death','crash']
def run(cmd): subprocess.run(cmd,check=True)
def dur(f): return float(subprocess.check_output(['ffprobe','-v','error','-show_entries','format=duration','-of','csv=p=0',f]).decode())
step=sys.argv[1] if len(sys.argv)>1 else 'all'
total=sum(dur(f'seg/{s}.mp4') for s in SEG); print('segments total',total)
LOGO=90.0-total
if step in('all','logo'):
    fc=("[0:v]format=rgba[bg];"
        "[1:v]scale=1500:-1,format=rgba,split[l1][l2];[l2]gblur=sigma=30,colorchannelmixer=aa=0.9[glow];"
        f"[glow]fade=in:st=0.5:d=1.6:alpha=1[glow2];[l1]fade=in:st=0.4:d=1.4:alpha=1[logo1];"
        "[bg][glow2]overlay=(W-w)/2:(H-h)/2-60:format=auto[b1];[b1][logo1]overlay=(W-w)/2:(H-h)/2-60:format=auto,"
        f"drawtext=fontfile={FONT}:text='A  MINECRAFT  FABRIC  MOD':fontsize=40:fontcolor=white@0.0:x=(w-text_w)/2:y=820:"
        "alpha='if(lt(t,2.4),0,min(1,(t-2.4)/0.9))':fontcolor=0xe8e2c8,"
        f"drawtext=fontfile={FONT}:text='FOR  MINECRAFT  26.1.2':fontsize=30:x=(w-text_w)/2:y=880:fontcolor=0xa8a28a:"
        "alpha='if(lt(t,3.0),0,min(1,(t-3.0)/0.9))',"
        "vignette=angle=PI/4,noise=alls=5:allf=t,fade=t=in:st=0:d=0.16:color=white,"
        f"fade=t=out:st={LOGO-1.2:.2f}:d=1.2,format=yuv420p[v]")
    run(['ffmpeg','-y','-loglevel','error','-f','lavfi','-i',f'color=c=0x060608:s=1920x1080:r=30:d={LOGO:.2f}','-loop','1','-framerate','30','-t',f'{LOGO:.2f}','-i','logo.png',
         '-filter_complex',fc,'-map','[v]','-r','30','-c:v','libx264','-preset','fast','-crf','16','seg/logo.mp4'])
if step in('all','join'):
    with open('list.txt','w') as f:
        for s in SEG+['logo']: f.write(f"file 'seg/{s}.mp4'\n")
    run(['ffmpeg','-y','-loglevel','error','-f','concat','-safe','0','-i','list.txt','-c','copy','joined.mp4'])
if step in('all','titles'):
    T=[(0.8,4.6,'SIX STONES',150,'c'),(5.0,9.2,'ONE GAUNTLET',150,'c'),(10.4,14.6,'UNLIMITED POWER',120,'c'),
       (15.4,18.8,'POWER',54,'ll'),(19.4,24.8,'INFINITY BEAM',54,'ll'),(25.3,28.3,'REALITY',54,'ll'),(28.8,31.3,'SOUL',54,'ll'),(31.8,34.3,'TIME',54,'ll'),
       (35.2,40.8,'SHATTER WORLDS',120,'c'),(47.2,50.4,'ONE SNAP',150,'c'),
       (52.2,56.6,'BUT SOMEONE IS COMING',90,'c'),(59.0,63.0,'THANOS',170,'c'),
       (64.2,69.6,'FACE THE TITAN',110,'c'),(71.2,75.8,'AND MAKE HIM KNEEL',100,'c'),(77.0,81.0,'THE UNIVERSE IS YOURS',100,'c')]
    ch=[]
    for a,b,txt,size,pos in T:
        al=f"if(lt(t,{a}),0,if(lt(t,{a+0.35}),(t-{a})/0.35,if(lt(t,{b-0.4}),1,if(lt(t,{b}),({b}-t)/0.4,0))))"
        x='(w-text_w)/2' if pos=='c' else '120'
        y='(h-text_h)/2' if pos=='c' else '870'
        # a faint shadow then the text; slow drift up
        ch.append(f"drawtext=fontfile={FONT}:text='{txt}':fontsize={size}:fontcolor=black@0.55:x={x}+4:y={y}+4:alpha='{al}'")
        ch.append(f"drawtext=fontfile={FONT}:text='{txt}':fontsize={size}:fontcolor=0xfff2c0:x={x}:y={y}-(t-{a})*4:alpha='{al}'")
    run(['ffmpeg','-y','-loglevel','error','-i','joined.mp4','-vf',','.join(ch),'-c:v','libx264','-preset','fast','-crf','17','-pix_fmt','yuv420p','titled.mp4'])
if step in('all','audio'):
    MUS='/root/.claude/uploads/7ac50626-97a5-5ee7-bc11-69b9e85ff03e/172fa903-Resolute_Triumph.mp3'
    ins=[('-ss','4','-t','72','-i',MUS),('-ss','167','-t','23','-i',MUS)]
    sfx=[ # (file, time, gain)
     ('sfx/heart.wav',0.3,0.8),('sfx/riser4.wav',5.8,0.7),('sfx/boom.wav',10.0,1.0),
     (f'{RES}/stones/stone_insert_space.ogg',11.3,0.9),(f'{RES}/stones/stone_insert_reality.ogg',12.2,0.9),(f'{RES}/stones/stone_insert_time.ogg',13.1,0.9),(f'{RES}/stones/gauntlet_complete.ogg',14.0,0.9),
     ('sfx/whoosh.wav',14.6,0.7),('sfx/hit.wav',15.1,1.0),('sfx/whoosh.wav',18.6,.7),('sfx/hit.wav',19.1,1.0),('sfx/whoosh.wav',24.6,.7),('sfx/hit.wav',25.0,1.0),
     ('sfx/hit.wav',28.5,1.0),('sfx/hit.wav',31.5,1.0),('sfx/riser2.wav',32.5,0.8),('sfx/boom.wav',34.5,1.0),
     ('sfx/boom_big.wav',39.0,1.0),(f'{RES}/stones/celestial_shatter.ogg',39.0,0.9),('sfx/riser4.wav',42.7,0.8),
     (f'{RES}/infinity_snap.ogg',46.9,1.2),('sfx/boom_big.wav',46.6,0.8),
     ('sfx/riser2.wav',49.7,0.8),('sfx/boom.wav',51.6,1.0),(f'{RES}/stones/stone_ominous_1.ogg',52.0,0.8),
     ('sfx/boom_big.wav',59.0,1.0),('sfx/hit.wav',64.0,1.0),('sfx/hit.wav',66.5,.9),('sfx/hit.wav',69.0,.9),
     ('sfx/boom.wav',76.6,1.0),('sfx/boom_big.wav',77.6,1.0),('sfx/riser4.wav',77.0,.7),('sfx/boom_big.wav',81.5,1.1)]
    vox=[(f'{RES}/thanos/arrival.ogg',59.8,1.6),(f'{RES}/thanos/give_stones.ogg',64.3,1.6),(f'{RES}/thanos/universe.ogg',67.4,1.6),(f'{RES}/thanos/defeat_1.ogg',72.2,1.6)]
    cmd=['ffmpeg','-y','-loglevel','error']
    for i in ins: cmd+=list(i)
    for f,t,g in sfx+vox: cmd+=['-i',f]
    n=len(ins); parts=[]; mx=[]
    parts.append("[0:a][1:a]acrossfade=d=1.5:c1=tri:c2=tri,volume=0.85,afade=t=out:st=87.5:d=2.5,apad=whole_dur=90,atrim=0:90[mus]")
    k=n
    for j,(f,t,g) in enumerate(sfx):
        parts.append(f"[{k}:a]aformat=sample_rates=48000:channel_layouts=stereo,volume={g},adelay={int(t*1000)}|{int(t*1000)}[s{j}]"); mx.append(f"[s{j}]"); k+=1
    vx=[]
    for j,(f,t,g) in enumerate(vox):
        parts.append(f"[{k}:a]aformat=sample_rates=48000:channel_layouts=stereo,volume={g},adelay={int(t*1000)}|{int(t*1000)}[v{j}]"); vx.append(f"[v{j}]"); k+=1
    parts.append("".join(mx)+f"amix=inputs={len(mx)}:normalize=0:duration=longest,apad=whole_dur=90,atrim=0:90[sfxmix]")
    parts.append("".join(vx)+f"amix=inputs={len(vx)}:normalize=0:duration=longest,apad=whole_dur=90,atrim=0:90,asplit=2[vox][voxsc]")
    parts.append("[mus][voxsc]sidechaincompress=threshold=0.04:ratio=6:attack=20:release=400[musd]")
    parts.append("[musd][sfxmix][vox]amix=inputs=3:normalize=0:duration=longest,alimiter=limit=0.95,atrim=0:90,afade=t=in:st=0:d=0.5[out]")
    cmd+=['-filter_complex',';'.join(parts),'-map','[out]','-ar','48000','-ac','2','audio.wav']
    run(cmd)
if step in('all','mux'):
    run(['ffmpeg','-y','-loglevel','error','-i','titled.mp4','-i','audio.wav','-c:v','copy','-c:a','aac','-b:a','256k','-shortest','-movflags','+faststart','trailer.mp4'])
    print('done', dur('trailer.mp4'))
