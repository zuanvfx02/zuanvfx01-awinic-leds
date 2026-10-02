import numpy as np, math

GAMMA = 300.0
REL_RISE=2.52
PRED_WAIT=25
LW=1.0;MW=1.0;HW=0.6
class Analyzer:
    def __init__(self, n=1024):
        self.n=n
        self.win = 0.5-0.5*np.cos(2*np.pi*np.arange(n)/(n-1))
        self.prev = np.zeros(n//2)
    def analyze(self, x, sr):
        n=self.n
        s = x/32768.0
        rms = math.sqrt(np.mean(s*s)) if len(s) else 0
        rms_db = 20*math.log10(rms) if rms>1e-7 else -100.0
        X = np.fft.fft(s*self.win)
        bins=n//2
        mag = np.abs(X[:bins])/(n/4.0)
        lm = np.log1p(GAMMA*mag)
        hz = np.arange(bins)*sr/n
        d = np.maximum(lm-self.prev,0)
        lo=(hz>=40)&(hz<250); mi=(hz>=250)&(hz<2000); hi=(hz>=2000)&(hz<8000)
        flux = d[lo].mean()*LW + d[mi].mean()*MW + d[hi].mean()*HW
        self.prev=lm
        return rms_db, flux

class Tracker:
    def __init__(self, frame_ms):
        self.fm=frame_ms
        self.hn=int(min(256,max(32,round(1000/frame_ms))))
        self.fh=np.zeros(self.hn); self.hsize=0; self.hw=0
        self.on=int(round(6000/frame_ms))
        self.odf=np.zeros(self.on); self.ocount=0; self.ow=0
        self.minlag=int(math.ceil(300/frame_ms)); self.maxlag=int(math.floor(1000/frame_ms))
        self.period=0.0; self.conf_t=0.0; self.lock=0.0
        self.pend=0.0; self.pend_n=0
        self.last_onset=-10**9; self.last_beat=-10**9; self.next_pred=None
        self.missed=0; self.frames=0; self.bpm=0.0
    FLOOR=0.05
    def thr(self,sens):
        if self.hsize<12:
            return max(self.FLOOR, self.fh[:self.hsize].mean()*1.6 if self.hsize else 0)
        v=self.fh[:self.hsize]; m=v.mean(); sd=max(v.std(),1e-6)
        s=min(10,max(1,sens)); mult=1.65-(s-1)*0.075
        return max(self.FLOOR, m+sd*mult)
    def tempo(self):
        N=min(self.ocount,self.on)
        if N < int(3000/self.fm): return
        idx=(self.ow-N+np.arange(N))%self.on
        v=self.odf[idx]
        x=np.maximum(v-v.mean(),0)
        r0=float(np.dot(x,x))
        if r0<1e-12: return
        maxl=min(2*self.maxlag, N//2)
        rn=np.zeros(maxl+2)
        for L in range(self.minlag, maxl+1):
            rn[L]=np.dot(x[L:],x[:N-L])/(N-L)/(r0/N)
        best=-1;bs=-1;
        sv={}
        for L in range(self.minlag,self.maxlag+1):
            s=rn[L]+(0.5*rn[2*L] if 2*L<=maxl else 0.0)
            bpm=60000.0/(L*self.fm)
            w=math.exp(-0.5*(math.log2(bpm/120.0)/0.9)**2)
            sv[L]=s
            if s*w>bs: bs=s*w;best=L
        L=best
        frac=float(L)
        if L>self.minlag and L<self.maxlag:
            a,b,c=sv[L-1],sv[L],sv[L+1]
            den=a-2*b+c
            if abs(den)>1e-9: frac=L+0.5*(a-c)/den
        mean_rn=np.mean([rn[l] for l in range(self.minlag,self.maxlag+1)])
        peak=rn[L]
        conf=min(1.0,max(0.0,(peak-mean_rn)*2.5))
        self.conf_t=self.conf_t*0.5+conf*0.5
        cand=frac*self.fm
        if self.period<=0: self.period=cand
        else:
            if abs(cand/self.period-1)<0.06: self.period+= (cand-self.period)*0.3; self.pend_n=0
            else:
                if self.pend>0 and abs(cand/self.pend-1)<0.06: self.pend_n+=1
                else: self.pend=cand; self.pend_n=1
                if self.pend_n>=3: self.period=cand; self.pend_n=0; self.next_pred=None
        self.bpm=60000.0/self.period
    def update(self, now, rms_db, flux, sens=5):
        self.frames+=1
        th=self.thr(sens)
        nf= flux/th if th>1e-9 else 0
        gate=rms_db>=-52
        refr=now-self.last_onset>=85
        mean_f = self.fh[:self.hsize].mean() if self.hsize else 0.0
        onset= gate and refr and flux>th and nf>=1.08 and flux>=mean_f*REL_RISE
        self.fh[self.hw]=flux; self.hw=(self.hw+1)%self.hn; self.hsize=min(self.hn,self.hsize+1)
        self.odf[self.ow]=flux; self.ow=(self.ow+1)%self.on; self.ocount+=1
        if self.frames%12==0: self.tempo()
        conf=self.conf_t*(0.5+0.5*self.lock)
        beat=False; pred=False
        strength=min(2,max(0,nf-1))/2
        P=self.period
        confident = P>0 and conf>=0.42
        if onset:
            if P>0:
                if self.next_pred is None: self.next_pred=now+P
                else:
                    e=now-self.next_pred; wrapped=e-round(e/P)*P
                    win=max(40,P*0.15)
                    if abs(wrapped)<=win:
                        self.next_pred+=wrapped*0.4; self.lock+= (1-self.lock)*0.15; self.missed=0
                    else:
                        self.lock+= (0-self.lock)*0.15
            if now-self.last_beat>=90 or self.last_beat<0:
                beat=True; self.last_beat=now
            self.last_onset=now
        elif confident and self.next_pred is not None and now>=self.next_pred+PRED_WAIT and self.missed<3 and gate:
            if now-self.last_beat>=90:
                beat=True; pred=True; self.last_beat=now; strength=max(strength,conf*0.55); self.missed+=1
            while self.next_pred<=now: self.next_pred+=P
        # keep grid moving past now
        if self.next_pred is not None and P>0:
            while self.next_pred<=now-P: self.next_pred+=P
        phase=0.0
        if self.next_pred is not None and P>0:
            phase=min(1,max(0,1-(self.next_pred-now)/P))
        return dict(beat=beat,onset=onset,pred=pred,bpm=self.bpm,conf=conf,phase=phase,strength=strength)

def synth(bpm, dur=30, sr=48000, seed=0, kind='music', noise_db=-45):
    rng=np.random.default_rng(seed)
    n=int(dur*sr); y=np.zeros(n)
    beat=60.0/bpm; truth=[]
    t=0.5
    k=0
    while t<dur-0.5:
        i=int(t*sr)
        L=int(0.15*sr); tt=np.arange(L)/sr
        kick=0.6*np.sin(2*np.pi*55*tt*(1+2*np.exp(-tt*40)))*np.exp(-tt*25)
        end=min(n,i+L); y[i:end]+=kick[:end-i]
        truth.append(t)
        if k%2==1:
            L2=int(0.12*sr); tt2=np.arange(L2)/sr
            sn=0.35*rng.standard_normal(L2)*np.exp(-tt2*35)
            end=min(n,i+L2); y[i:end]+=sn[:end-i]
        # hats on offbeat
        j=int((t+beat/2)*sr); L3=int(0.04*sr); tt3=np.arange(L3)/sr
        hat=0.12*rng.standard_normal(L3)*np.exp(-tt3*120)
        end=min(n,j+L3)
        if j<n: y[j:end]+=hat[:end-j]
        t+=beat; k+=1
    # pad / bg noise
    y+= 10**(noise_db/20)*rng.standard_normal(n)
    y+=0.05*np.sin(2*np.pi*220*np.arange(n)/sr)
    return np.clip(y,-1,1), truth

def run(y, sr=48000, hop=512, frame=1024):
    ana=Analyzer(frame); tr=Tracker(hop*1000.0/sr)
    buf=np.zeros(frame)
    out=[]
    ys=(y*32767).astype(np.int16).astype(float)
    nh=len(ys)//hop
    for h in range(nh):
        buf=np.concatenate([buf[hop:], ys[h*hop:(h+1)*hop]])
        now=(h+1)*hop*1000.0/sr  # end of hop time ms
        rms,flux=ana.analyze(buf,sr)
        r=tr.update(now,rms,flux)
        r['t']=now; out.append(r)
    return out
