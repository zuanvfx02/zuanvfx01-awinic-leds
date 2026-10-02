from dsp import *
import numpy as np
for bpm in (80,100,120,128,150,170):
    y,truth=synth(bpm,dur=40,seed=bpm)
    out=run(y)
    late=[o for o in out if o['t']>12000]
    bpms=[o['bpm'] for o in late]
    confs=[o['conf'] for o in late]
    beats=[o['t'] for o in late if o['beat']]
    pred=sum(1 for o in late if o['pred'])
    truth_ms=np.array(truth)*1000
    allt=np.sort(np.concatenate([truth_ms, truth_ms+30000.0/bpm]))
    errs=[]
    for b in beats:
        d=b-allt[allt<=b+5]
        if len(d): errs.append(d.min())
    # fraction of true beats (after 12s) that had a beat within 60ms after
    tt=[t for t in truth_ms if t>12000 and t<39000]
    hit=sum(1 for t in tt if any(0<=b-t<=60 for b in beats))
    print(f"bpm={bpm} est med={np.median(bpms):.1f} conf med={np.median(confs):.2f} beats={len(beats)} of {len(tt)} true, pred={pred}, hit60={hit}/{len(tt)}, lat med={np.median(errs):.0f}ms p90={np.percentile(errs,90):.0f}ms")
# negative control: noise only
rng=np.random.default_rng(1)
y=0.02*rng.standard_normal(48000*30)
out=run(y); late=[o for o in out if o['t']>12000]
print("noise only: beats",sum(o['beat'] for o in late),"conf med",np.median([o['conf'] for o in late]))
