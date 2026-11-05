import numpy as np, wave
from scipy.signal import lfilter
SR=48000
rng=np.random.default_rng(7)
def save(name,x):
    x=np.clip(x,-1,1)
    st=np.stack([x,x],1)
    with wave.open(f'sfx/{name}.wav','wb') as w:
        w.setnchannels(2);w.setsampwidth(2);w.setframerate(SR)
        w.writeframes((st*32767).astype('<i2').tobytes())
def lp(x,fc):
    a=np.exp(-2*np.pi*fc/SR);return lfilter([1-a],[1,-a],x)
def boom(dur=3.5,f0=70,f1=28,noise=0.5):
    t=np.arange(int(SR*dur))/SR
    ph=2*np.pi*np.cumsum(f1+(f0-f1)*np.exp(-t*6))/SR
    x=np.sin(ph)*np.exp(-t*1.3)
    x=x*1.1+lp(rng.standard_normal(len(t)),1800)*np.exp(-t*7)*noise*3+lp(rng.standard_normal(len(t)),300)*np.exp(-t*2.0)*noise*4
    return x/np.max(np.abs(x))*0.95
def riser(dur=4.0):
    n=int(SR*dur);t=np.arange(n)/SR;x=rng.standard_normal(n);out=np.zeros(n);blk=1024
    fc=200+6000*(t/dur)**2
    for i in range(0,n,blk): out[i:i+blk]=lp(x[i:i+blk],fc[i])
    sw=np.sin(2*np.pi*np.cumsum(80+700*(t/dur)**2)/SR)*0.25
    y=(out*3+sw)*(t/dur)**1.8
    return y/np.max(np.abs(y))*0.8
def whoosh(dur=1.2):
    n=int(SR*dur);t=np.arange(n)/SR;x=rng.standard_normal(n);out=np.zeros(n);blk=512
    fc=300+3500*np.sin(np.pi*t/dur)
    for i in range(0,n,blk): out[i:i+blk]=lp(x[i:i+blk],fc[i])
    y=out*np.sin(np.pi*t/dur)**2
    return y/np.max(np.abs(y))*0.7
def heart(dur=6.0,bpm=62):
    n=int(SR*dur);y=np.zeros(n);step=60/bpm
    for k in range(int(dur/step)):
        for off,amp in ((0,1),(0.28,.7)):
            s=int((k*step+off)*SR);m=int(SR*.35);t=np.arange(m)/SR
            if s+m<n:y[s:s+m]+=np.sin(2*np.pi*(52*np.exp(-t*8)+34)*t)*np.exp(-t*14)*amp
    return y*0.9
def hit(dur=0.6):
    t=np.arange(int(SR*dur))/SR
    x=np.sin(2*np.pi*(120*np.exp(-t*20)+45)*t)*np.exp(-t*9)+lp(rng.standard_normal(len(t)),4000)*np.exp(-t*40)*.8
    return x/np.max(np.abs(x))*0.9
save('boom',boom());save('boom_big',boom(5.0,60,24,0.7));save('riser4',riser(4.0));save('riser2',riser(2.0))
save('whoosh',whoosh());save('heart',heart());save('hit',hit())
print('ok')
