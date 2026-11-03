"""Call the user's existing localhost Blockbench bridge, without altering Codex config."""
import argparse, base64, json, urllib.request
from pathlib import Path

def call(action, params=None):
    payload=json.dumps({'id':'questplus-assets','action':action,'params':params or {}}).encode()
    req=urllib.request.Request('http://127.0.0.1:8787/command',data=payload,headers={'Content-Type':'application/json'})
    with urllib.request.urlopen(req,timeout=60) as response: result=json.load(response)
    if not result.get('ok'):raise RuntimeError(result)
    return result.get('result')

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('action');p.add_argument('params',nargs='?',default='{}');p.add_argument('--output');args=p.parse_args()
    result=call(args.action,json.loads(args.params))
    if args.output:
        target=Path(args.output)
        if isinstance(result,dict) and 'base64' in result:target.write_bytes(base64.b64decode(result['base64']))
        else:target.write_text(json.dumps(result,indent=2),encoding='utf-8')
    else:print(json.dumps(result,indent=2))
