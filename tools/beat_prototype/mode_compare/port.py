import numpy as np, math
from math import ceil, floor, exp, log, sqrt

NEVER=-1e12
class Base:
    def __init__(s, frame_ms):
        s.fm=frame_ms
        s.hn=int(min(256,max(32,round(1000/frame_ms))))
        s.fh=np.zeros(s.hn); s.hs=0; s.hw=0
        s.on=int(round(6000/frame_ms)); s.odf=np.zeros(s.on); s.oc=0; s.ow=0
        s.minlag=ceil(300/frame_ms); s.maxlag=floor(1000/frame_ms)
        s.acf=np.zeros(s.maxlag*2+2)
        s.frames=0; s.period=0.0; s.bpm=0.0; s.tconf=0.0; s.lockq=0.0; s.was=False
        s.pend=0.0; s.pendn=0
        s.last_onset=NEVER; s.last_beat=NEVER; s.next=float('nan'); s.missed=0
    def push_flux(s,f):
        s.fh[s.hw]=f; s.hw=(s.hw+1)%s.hn; s.hs=min(s.hn,s.hs+1)
    def push_odf(s,f):
        s.odf[s.ow]=f; s.ow=(s.ow+1)%s.on; s.oc=min(s.on,s.oc+1)
    def comb(s,lag,mc):
        h=0.5*s.acf[lag*2] if lag*2<=mc else 0.0
        return s.acf[lag]+h
    def prior(s,lag,width):
        t=60000/(lag*s.fm); o=log(t/120)/log(2); return exp(-0.5*(o/width)**2)
    def update_tempo(s, v6):
        n=min(s.oc,s.on)
        if n<int(3000/s.fm): return
        st=(s.ow-n+s.on)%s.on
        w=np.array([s.odf[(st+i)%s.on] for i in range(n)])
        m=w.mean(); w=np.maximum(0,w-m); r0=(w*w).sum()
        if r0<1e-12: return
        mc=min(s.maxlag*2,n//2); sm=min(s.maxlag,mc)
        if sm<=s.minlag+2: return
        norm=n/r0
        for lag in range(s.minlag,mc+1):
            s.acf[lag]=float(np.dot(w[lag:],w[:n-lag]))/(n-lag)*norm
        width=1.35 if v6 else 0.9
        best=-1;bs=-1.0;ma=0.0
        for lag in range(s.minlag,sm+1):
            ma+=s.acf[lag]; sc=s.comb(lag,mc)*s.prior(lag,width)
            if sc>bs: bs=sc;best=lag
        if best<0: return
        ma/=(sm-s.minlag+1)
        fl=float(best)
        if s.minlag<best<sm:
            a=s.comb(best-1,mc);b=s.comb(best,mc);c=s.comb(best+1,mc);d=a-2*b+c
            if abs(d)>1e-9: fl=min(best+1.0,max(best-1.0,best+0.5*(a-c)/d))
        contrast=min(1.0,max(0.0,(s.acf[best]-ma)*2.5))
        decay=0.55 if v6 else 0.5
        s.tconf=s.tconf*decay+contrast*(1-decay)
        cand=fl*s.fm
        gain=0.25 if v6 else 0.3
        if s.period<=0: s.period=cand
        elif abs(cand/s.period-1)<0.06:
            s.period+=(cand-s.period)*gain; s.pendn=0
        else:
            if s.pend>0 and abs(cand/s.pend-1)<0.06: s.pendn+=1
            else: s.pend=cand; s.pendn=1
            if s.pendn>=3:
                s.period=cand; s.pendn=0; s.next=float('nan'); s.missed=0
                if v6: s.lockq=0.0
        s.bpm=60000/s.period

class Aggressive(Base):
    def update(s, now, rms_db, flux, sens):
        s.frames+=1
        sv=min(10,max(1,sens))
        mean=s.fh[:s.hs].mean() if s.hs else 0.0
        if s.hs<12: thr=max(0.05,mean*1.6)
        else:
            std=max(1e-6,sqrt(((s.fh[:s.hs]-mean)**2).mean()))
            thr=max(0.05,mean+std*(1.65-(sv-1)*0.075))
        nf=flux/thr if thr>1e-9 else 0.0
        rr=3.0-(sv-1)*0.12
        gate=rms_db>=-52; ref=now-s.last_onset>=85
        onset=gate and ref and flux>thr and nf>=1.08 and flux>=mean*rr
        s.push_flux(flux); s.push_odf(flux)
        if s.frames%12==0: s.update_tempo(False)
        conf=s.tconf*(0.5+0.5*s.lockq); period=s.period
        confident=period>0 and conf>=0.42
        beat=False; pred=False
        if onset:
            if period>0:
                if math.isnan(s.next): s.next=now+period
                else:
                    e=now-s.next; wr=e-round(e/period)*period
                    if abs(wr)<=max(40,period*0.15):
                        s.next+=wr*0.4; s.lockq+=(1-s.lockq)*0.15; s.missed=0
                    else: s.lockq-=s.lockq*0.15
            if now-s.last_beat>=90: beat=True; s.last_beat=now
            s.last_onset=now
        elif confident and gate and not math.isnan(s.next) and now>=s.next+25 and s.missed<3 and now-s.last_beat>=90:
            beat=True; pred=True; s.last_beat=now; s.missed+=1
            while s.next<=now: s.next+=period
        if period>0 and not math.isnan(s.next):
            while s.next<=now-period: s.next+=period
        return beat,onset,pred,s.bpm,conf

PRE=dict(lowSigma=0.95,sigmaRange=1.05,relB=1.15,relR=0.99,gap=140,xgap=70,frac=0.28,open=4,plB=1.02,plR=0.18,
         sB=1.65,sR=0.15,rB=1.8,rR=0.45,sGap=0.0,lc=0.65,lq=0.62,hy=0.0,pen=0.10,pw=8.0,pml=35.0)
HYB=dict(lowSigma=0.95,sigmaRange=1.05,relB=1.15,relR=0.99,gap=120,xgap=55,frac=0.26,open=5,plB=1.02,plR=0.16,
         sB=1.50,sR=0.15,rB=1.65,rR=0.40,sGap=95.0,lc=0.55,lq=0.52,hy=0.25,pen=0.03,pw=10.0,pml=40.0)
class Tempo(Base):
    def __init__(s,fm,p): super().__init__(fm); s.p=p; s.prev=0.0
    def update(s, now, rms_db, flux, sens):
        p=s.p; s.frames+=1
        sv=min(10,max(1,sens)); s01=(sv-1)/9
        mean=s.fh[:s.hs].mean() if s.hs else 0.0
        std=sqrt(((s.fh[:s.hs]-mean)**2).mean()) if s.hs>1 else 0.0
        std=max(std,1e-6) if s.hs>1 else 0.0
        if s.hs<12: thr=max(0.05,mean*1.6)
        else: thr=max(0.05,mean+std*(p['lowSigma']+s01*p['sigmaRange']))
        nf=flux/thr if thr>1e-9 else 0.0
        rr=p['relB']+s01*p['relR']; pr=1.0+s01*0.12
        gate=rms_db>=-52; ref=now-s.last_onset>=85
        rising=flux>=max(0.05,s.prev*pr)
        onset=gate and ref and flux>thr and nf>=1.02 and flux>=mean*rr and rising
        nov=max(0.0,flux-mean); s.push_flux(flux); s.push_odf(nov); s.prev=flux
        if s.frames%12==0: s.update_tempo(True)
        conf=s.tconf*(0.5+0.5*s.lockq); period=s.period
        rel=(1-p['hy']) if s.was else 1.0
        locked=period>0 and conf>=p['lc']*rel and s.lockq>=p['lq']*rel
        s.was=locked
        beat=False; pred=False
        if onset:
            aligned=False
            if period>0 and not math.isnan(s.next):
                e=now-s.next; wr=e-round(e/period)*period
                aligned=abs(wr)<=max(45,period*0.18)
            strong=nf>=(p['sB']+p['sR']*s01) and flux>=mean*(p['rB']+p['rR']*s01)
            prelock = True if sv<=p['open'] else nf>=(p['plB']+p['plR']*s01)
            gap=max(p['gap'],period*p['frac']) if locked else p['gap']+p['xgap']*s01
            if strong and p['sGap']>0: gap=min(gap,p['sGap'])
            acc=((aligned or strong) and now-s.last_beat>=gap) if locked else (prelock and now-s.last_beat>=gap)
            if period>0:
                if math.isnan(s.next): s.next=now+period
                else:
                    e=now-s.next; wr=e-round(e/period)*period
                    if abs(wr)<=max(45,period*0.18):
                        s.next+=wr*0.35; s.lockq+=(1-s.lockq)*0.18; s.missed=0
                    elif locked: s.lockq*=(1-p['pen'])
            if acc: beat=True; s.last_beat=now
            s.last_onset=now
        elif locked and gate and not math.isnan(s.next) and now>=s.next+p['pw'] and now<=s.next+p['pml'] and s.missed<3 and now-s.last_beat>=max(p['gap'],period*p['frac']):
            beat=True; pred=True; s.last_beat=now; s.missed+=1
            while s.next<=now: s.next+=period
        elif locked and not math.isnan(s.next) and now>s.next+period*0.35:
            while s.next<=now: s.next+=period
            s.missed=min(3,s.missed+1)
        if period>0 and not math.isnan(s.next):
            while s.next<=now-period: s.next+=period
        return beat,onset,pred,s.bpm,conf
