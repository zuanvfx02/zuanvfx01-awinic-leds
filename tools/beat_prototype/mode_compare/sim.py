import numpy as np, math, sys
sys.path.insert(0, __import__('os').path.dirname(__import__('os').path.abspath(__file__)))
from port import *
SR=48000; HOP=256; FRAME=1024; fm=HOP*1000/SR
GAMMA=300.0
def analyzer():
    win=0.5-0.5*np.cos(2*np.pi*np.arange(FRAME)/(FRAME-1)); prev=np.zeros(FRAME//2)
    def f(x):
        nonlocal prev
        s=x/32768.0; rms=math.sqrt(np.mean(s*s)); rdb=20*math.log10(rms) if rms>1e-7 else -100
        X=np.fft.rfft(s*win)[:FRAME//2]; mag=np.abs(X)/(FRAME/4); lm=np.log1p(GAMMA*mag)
        hz=np.arange(FRAME//2)*SR/FRAME; d=np.maximum(lm-prev,0)
        lo=(hz>=40)&(hz<250); mi=(hz>=250)&(hz<2000); hi=(hz>=2000)&(hz<8000)
        flux=d[lo].mean()+d[mi].mean()+0.6*d[hi].mean(); prev=lm; return rdb,flux
    return f
def kick(n,amp,f0=60):
    t=np.arange(n)/SR; return amp*np.sin(2*np.pi*(f0+80*np.exp(-t*40))*t)*np.exp(-t*18)
def hat(n,amp):
    rng=np.random.default_rng(1); t=np.arange(n)/SR
    return amp*rng.standard_normal(n)*np.exp(-t*60)
def synth(bpm=126,dur=30,hits=True,seed=0):
    rng=np.random.default_rng(seed); n=int(dur*SR); y=0.01*rng.standard_normal(n)*3000
    per=60/bpm; truth=[]; t=1.0
    while t<dur-0.5:
        i=int(t*SR); k=kick(int(0.25*SR),9000); y[i:i+len(k)]+=k; truth.append(t*1000); t+=per
    off=[]
    if hits:
        t=1.0+per*0.5
        while t<dur-0.5:   # weak off-grid hi-hats every beat (8th note)
            i=int(t*SR); h=hat(int(0.06*SR),1800); y[i:i+len(h)]+=h; t+=per
        # a few strong off-grid snares (syncopation)
        for tt in [10.35,14.62,19.2,24.9]:
            i=int(tt*SR); sn=hat(int(0.12*SR),9000)+kick(int(0.12*SR),5000,180); y[i:i+len(sn)]+=sn; off.append(tt*1000)
    return np.clip(y,-32000,32000).astype(np.int16),truth,off
def run(y,tracker,sens):
    an=analyzer(); frame=np.zeros(FRAME,dtype=np.float64); beats=[]; 
    for k in range(0,len(y)-HOP,HOP):
        frame=np.concatenate([frame[HOP:],y[k:k+HOP].astype(np.float64)])
        now=k/SR*1000
        rdb,flux=an(frame)
        b,o,p,bpm,conf=tracker.update(now,rdb,flux,sens)
        if b: beats.append((now,p))
    return beats,tracker
def score(beats,truth,off,warm=8000):
    bt=[b for b,p in beats if b>warm]
    tr=[t for t in truth if t>warm]
    hit=0
    for t in tr:
        if any(abs(b-t)<=90 for b in bt): hit+=1
    near=lambda b: any(abs(b-t)<=90 for t in tr)
    extra=[b for b in bt if not near(b)]
    offhit=sum(1 for o in off if any(abs(b-o)<=100 for b in bt))
    return hit/len(tr), len(bt), len(extra), offhit
for bpm in (100,126,140):
    y,truth,off=synth(bpm)
    print(f"--- {bpm} BPM, weak off-grid hats + 4 strong off-grid snares ---")
    for sens in (3,6,10):
        for name,mk in (("AGGR",lambda:Aggressive(fm)),("PREC",lambda:Tempo(fm,PRE)),("HYBR",lambda:Tempo(fm,HYB))):
            beats,tr=run(y,mk(),sens)
            r,total,extra,offhit=score(beats,truth,off)
            print(f"s={sens:2d} {name}: onbeat={r*100:5.1f}% beats={total:3d} extra(not on kick)={extra:3d} strong-offgrid-hit={offhit}/4 bpm={tr.bpm:.1f}")
