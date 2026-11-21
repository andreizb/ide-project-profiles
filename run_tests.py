#!/usr/bin/env python3
from pathlib import Path
import subprocess,json,tempfile,zipfile,shutil,argparse,os

root=Path(__file__).resolve().parent
parser=argparse.ArgumentParser()
parser.add_argument('--catalog',type=Path,default=root/'tests/fixtures/catalog.json')
args=parser.parse_args()
ide=Path(os.environ.get('IDE_PROFILES_IDEA_APP','/Applications/IntelliJ IDEA.app'))/'Contents'
lib=ide/'lib'
java_bin=ide/'jbr/Contents/Home/bin'
cp=':'.join([str(root/'build/plugin'),str(root/'build/bridge')]+[str(p) for p in lib.glob('*.jar')])
classes=['PolicyTests','StorageTests','AgentVerification','CatalogTests','AgentInactiveVerification','AgentFailureVerification','DockHandoffTests','WelcomeProjectTests']
compile=subprocess.run([str(java_bin/'javac'),'--release','21','-cp',cp,'-d',str(root/'build/tests')]+[str(root/'tests'/(c+'.java')) for c in classes])
if compile.returncode:raise SystemExit(compile.returncode)
results=[]
for name in classes:
    command=[str(java_bin/'java'),'-ea','-Djava.io.tmpdir='+tempfile.gettempdir()]
    if name=='AgentVerification': command+=['-Xverify:all','-javaagent:'+str(root/'dist/profile-agent.jar')]
    if name in ['AgentInactiveVerification','AgentFailureVerification']:
        folder=Path(tempfile.mkdtemp(prefix='profile-agent-test-'))
        agent=folder/'profile-agent.jar'
        with zipfile.ZipFile(root/'dist/profile-agent.jar') as src,zipfile.ZipFile(agent,'w') as dest:
            for item in src.infolist():
                data=src.read(item.filename)
                if name=='AgentInactiveVerification' and item.filename=='target.properties':data=b'build=unknown-test\nsha256=0000000000000000\n'
                dest.writestr(item,data)
        if name=='AgentInactiveVerification':shutil.copy2(root/'dist/profile-bridge.jar',folder/'profile-bridge.jar')
        command+=['-javaagent:'+str(agent)]
    command+=['-cp',str(root/'build/tests')+':'+cp,name]
    if name=='CatalogTests': command+=[str(args.catalog)]
    r=subprocess.run(command,capture_output=True,text=True)
    print(r.stdout.strip())
    if r.returncode: print(r.stderr);raise SystemExit(r.returncode)
    results.append({'test':name,'passed':True,'output':r.stdout.strip()})
(root/'test-results.json').write_text(json.dumps(results,indent=2)+'\n')
