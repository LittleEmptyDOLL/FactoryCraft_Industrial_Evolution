#!/usr/bin/env python3
"""Download pinned official runtime dependencies for local compatibility QA; no jars are redistributed."""
import argparse,hashlib,json,urllib.request
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('output',type=Path);args=p.parse_args();args.output.mkdir(parents=True,exist_ok=True)
for mod in json.loads(Path(__file__).with_name('optional-mods.json').read_text()):
 target=args.output/mod['name']
 data=target.read_bytes() if target.exists() else urllib.request.urlopen(mod['url'],timeout=60).read()
 if hashlib.sha256(data).hexdigest()!=mod['sha256']:raise SystemExit('Digest mismatch: '+mod['name'])
 target.write_bytes(data);print(mod['name'])
