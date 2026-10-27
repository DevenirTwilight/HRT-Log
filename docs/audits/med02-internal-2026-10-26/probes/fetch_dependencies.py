#!/usr/bin/env python3
"""Fetch pinned public test dependencies only; never downloads application/user data."""
import hashlib
import json
import pathlib
import sys
import urllib.request

root = pathlib.Path(__file__).resolve().parent
target = pathlib.Path(sys.argv[1]).resolve()
target.mkdir(parents=True, exist_ok=True)
for item in json.loads((root / 'dependencies.json').read_text()):
    path = target / item['name']
    if not path.exists():
        urllib.request.urlretrieve(item['url'], path)
    assert path.stat().st_size == item['bytes'], path
    assert hashlib.sha256(path.read_bytes()).hexdigest() == item['sha256'], path
    print('VERIFIED', item['name'])
