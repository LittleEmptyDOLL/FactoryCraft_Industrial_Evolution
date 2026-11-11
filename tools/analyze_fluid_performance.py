#!/usr/bin/env python3
"""Render like-for-like kernel/server summaries; never pool precomputed quantiles or select best runs."""
import json, statistics
from pathlib import Path
root=Path(__file__).resolve().parents[1]/'validation/fluid-v0241'
def rows(name):return [json.loads(s) for s in (root/name).read_text().splitlines() if s.startswith('{')]
def median(xs):return statistics.median(xs)
def delta(a,b):return '—' if not a else f'{(b/a-1)*100:+.1f}%'
lines=['# Fluid 0.24.0 → 0.24.1 comparison','', 'Kernel: identical 256-MiB initial / 512-MiB max heaps, three 1000-node warmups, three measured rounds per size. Times include complete settlement with a 256-unit advance budget. Values below are medians of per-round means/quantiles, not pooled percentiles. Allocation is whole-operation thread allocation, not retained heap.','', '| Members | Operation | 0.24.0 mean ms | 0.24.1 mean ms | Change | Old/new allocation KiB |','|---:|---|---:|---:|---:|---:|']
b=rows('kernel-before-final.jsonl');a=rows('kernel-after-ordered.jsonl')
for n in (100,1000,5000,10000):
 for op in ('create','10000_fill_drain','split','merge','leaf_add','leaf_remove','tank_add','tank_remove','availability_16'):
  x=[v for v in b if v['nodes']==n and v['op']==op];y=[v for v in a if v['nodes']==n and v['op']==op]
  old=median(v['mean_ns'] for v in x)/1e6;new=median(v['mean_ns'] for v in y)/1e6
  lines.append(f"| {n} | {op} | {old:.6f} | {new:.6f} | {delta(old,new)} | {median(v['allocation_mean_bytes'] for v in x)/1024:.2f} / {median(v['allocation_mean_bytes'] for v in y)/1024:.2f} |")
lines+=['','## Kernel tails, 10000 members','', '| Operation | Old/new median ms | Old/new p95 ms | Old/new p99 ms | Observed old/new maximum ms |','|---|---:|---:|---:|---:|']
for op in ('create','split','merge','leaf_add','leaf_remove','availability_16'):
 x=[v for v in b if v['nodes']==10000 and v['op']==op];y=[v for v in a if v['nodes']==10000 and v['op']==op]
 values=[f"{median(v[k] for v in x)/1e6:.4f} / {median(v[k] for v in y)/1e6:.4f}" for k in ('median_ns','p95_ns','p99_ns')]
 values.append(f"{max(v['max_ns'] for v in x)/1e6:.4f} / {max(v['max_ns'] for v in y)/1e6:.4f}")
 lines.append('| '+op+' | '+' | '.join(values)+' |')
if (root/'creation-before.jsonl').exists() and (root/'creation-after.jsonl').exists():
 lines+=['','## Creation follow-up: eight size-matched warmups, thirty new networks per size','','The three single-trial creation results above were unstable, so this separate longer run is retained alongside them. Do not substitute its best trial for the original data.','','| Members | Old/new mean ms | Old/new median ms | Old/new p95 ms | Old/new p99 / maximum ms |','|---:|---:|---:|---:|---:|']
 x=rows('creation-before.jsonl');y=rows('creation-after.jsonl')
 for n in (100,1000,5000,10000):
  a0=next(v for v in x if v['nodes']==n);b0=next(v for v in y if v['nodes']==n)
  values=[f"{a0[k]/1e6:.4f} / {b0[k]/1e6:.4f}" for k in ('mean_ns','median_ns','p95_ns','p99_ns')]
  lines.append('| '+str(n)+' | '+' | '.join(values)+' |')
server_names=['server-before.json','server-before-repeat.json','server-after-final.json','server-after-final-repeat.json']
if all((root/n).exists() for n in server_names):
 old=[json.loads((root/n).read_text()) for n in server_names[:2]];new=[json.loads((root/n).read_text()) for n in server_names[2:]]
 lines+=['','## Real dedicated server: two independent JVM/world runs per version','', 'Each run contains three scenario cycles. Entries are median per-run summaries with the observed run range. They are not merged p95/p99 distributions. Startup/build, warmup, checkpoint saving and steady ticks are separated. Zero connected clients in every run.','', '| Scenario / metric | 0.24.0 ms (range) | 0.24.1 ms (range) | Median change |','|---|---:|---:|---:|']
 for stage in ('build','stable','construction','split-merge','unload','reload'):
  for metric in ('meanMs','p95Ms','p99Ms','maxMs'):
   x=[v['stages'][stage][metric] for v in old];y=[v['stages'][stage][metric] for v in new]
   lines.append(f"| {stage} / {metric} | {median(x):.3f} ({min(x):.3f}–{max(x):.3f}) | {median(y):.3f} ({min(y):.3f}–{max(y):.3f}) | {delta(median(x),median(y))} |")
 lines+=['','| Whole-run count / memory | 0.24.0 range | 0.24.1 range |','|---|---:|---:|']
 for key in ('visits','rebuilds','chunkMarks','rawChanges'):
  x=[v['metrics'][key] for v in old];y=[v['metrics'][key] for v in new];lines.append(f'| {key} | {min(x)}–{max(x)} | {min(y)}–{max(y)} |')
 for key in ('syncRequests','heapAfterWarmGc'):
  x=[v[key] for v in old];y=[v[key] for v in new];lines.append(f'| {key} | {min(x)}–{max(x)} | {min(y)}–{max(y)} |')
 for stage in ('stable','construction','split-merge','reload'):
  x=[v['stages'][stage]['allocatedBytes']/v['stages'][stage]['count']/1024 for v in old];y=[v['stages'][stage]['allocatedBytes']/v['stages'][stage]['count']/1024 for v in new];lines.append(f'| {stage} thread allocation KiB/tick | {min(x):.1f}–{max(x):.1f} | {min(y):.1f}–{max(y):.1f} |')
 lines+=['','`chunkMarks` counts segment-adapter setUnsaved(true) requests, not disk writes or all world chunk mutations. `syncRequests` counts BE update requests; JFR records **zero actual sent packets with zero clients**. New `finalizeWork`, `localEdits`, `frozenMembers` and `aborts` have no matching old counters and must be reported separately. The heap sample is the entire warmed server after explicit GC, not retained graph size. No universal TPS or peak-latency claim follows from these samples.']
(root/'comparison.md').write_text('\n'.join(lines)+'\n')
