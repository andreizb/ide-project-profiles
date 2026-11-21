"""Run the installed bundle in isolated native Cursor extension hosts; never inspect real workspaces."""
from pathlib import Path
import json,os,shutil,signal,subprocess,sys,tempfile,time,socket,urllib.request

root=Path(__file__).resolve().parent
installed=Path(sys.argv[1]).resolve()
cli=Path(os.environ.get('IDE_PROFILES_CURSOR_APP','/Applications/Cursor.app'))/'Contents/Resources/app/bin/cursor'
with tempfile.TemporaryDirectory(prefix='ide-profile-host-') as folder:
 base=Path(folder).resolve();extensions=base/'extensions';extensions.mkdir();results=base/'results';results.mkdir()
 package=json.loads((installed/'package.json').read_text());shutil.copytree(installed,extensions/('andrei-local.automatic-project-extensions-'+package['version']))
 probe=extensions/'local-check.profile-verification-1.0.0'
 shutil.copytree(root/'probe',probe);(probe/'context.json').write_text(json.dumps({'base':str(base)}))
 for name,language,suffix in [('java','java','.java'),('latex','latex','.tex'),('sql','sql','.sql')]:
  p=extensions/('verification.'+name+'-1.0.0');p.mkdir();(p/'index.js').write_text('exports.activate=()=>{};')
  (p/'package.json').write_text(json.dumps({'name':name,'publisher':'verification','version':'1.0.0','engines':{'vscode':'^1.90.0'},'main':'index.js','activationEvents':['onLanguage:'+language],'contributes':{'languages':[{'id':language,'extensions':[suffix]}]}}))
 for name,suffix in [('java','.java'),('latex','.tex'),('python','.py')]:
  p=base/'fixtures'/name;p.mkdir(parents=True);(p/('example'+suffix)).write_text('# local verification fixture\n')
 env=os.environ.copy();env['IDE_PROFILES_VERIFY_DIR']=str(base)
 # App integrations remain enabled, but use private HOME/state and a separate
 # loopback listener so verification cannot own the user's normal bridge.
 private_home=base/'home';private_home.mkdir();env['CURSOR_BYOK_PRIVATE_HOME']=str(private_home)
 byok=(cli.parents[1]/'cursor-byok/bootstrap.mjs').exists()
 port=0
 if byok:
  with socket.socket() as listener:
   listener.bind(('127.0.0.1',0));port=listener.getsockname()[1]
  env['CURSOR_LISTEN_ADDR']='127.0.0.1:'+str(port)
  env['CURSOR_CONTROL_URL']='http://127.0.0.1:'+str(port)
  database=private_home/'Library/Application Support/Cursor/User/cursor-byok/cursor-byok.db';database.parent.mkdir(parents=True);database.touch(mode=0o600)
  env['CURSOR_DATABASE_URL']='sqlite://'+str(database)

 try:
  reports={}
  for name,expected in [('java',{'verification.java'}),('latex',{'verification.latex'}),('python',set())]:
   subprocess.run([str(cli),'--classic','--new-window','--user-data-dir',str(base/'data'),'--extensions-dir',str(extensions),'--disable-workspace-trust','--skip-welcome','--skip-release-notes',str(base/'fixtures'/name)],env=env,cwd=cli.parent,check=True,stdout=subprocess.DEVNULL)
   deadline=time.monotonic()+90
   while time.monotonic()<deadline:
    try:
     d=json.loads((results/(name+'.json')).read_text());enabled=set(d['enabled']);local={e['id'] for e in d.get('local') or []};all_ids={'verification.java','verification.latex','verification.sql'}
     assert d['active'] and d['version']==package['version'];assert not d['global'];assert enabled&all_ids==expected;assert all_ids-expected<=local
     state=Path(d['workspaceStorage']).parent/'andrei-local.automatic-project-extensions/state.json';history=json.loads(state.read_text());assert len(history['workspaces'])==1
     os.kill(d['pid'],0);reports[name]=d;break
    except (OSError,ValueError,KeyError,AssertionError):time.sleep(1)
   else:
    problems=(base/'probe-error.txt').read_text() if (base/'probe-error.txt').exists() else ', '.join(p.name for p in results.iterdir())
    for log in base.glob('data/logs/*/window*/exthost/exthost.log'):
     lines=[line[:350] for line in log.read_text(errors='replace').splitlines() if any(word in line for word in ['profile-verification','automatic-project-extensions','Error:','error]'])]
     print('Isolated host diagnostic:',log.parent.parent.name,lines[-8:])
    raise RuntimeError('Cursor host did not reach independent workspace state for '+name+': '+problems)
  for report in reports.values():os.kill(report['pid'],0)
  assert len({r['pid'] for r in reports.values()})==3
  if byok:
   opener=urllib.request.build_opener(urllib.request.ProxyHandler({}))
   with opener.open('http://127.0.0.1:'+str(port)+'/__byok-api__/api/harness/cursor/status',timeout=10) as response:
    assert response.status==200;json.loads(response.read())
   print('PASS: BYOK bridge remained enabled and healthy alongside the profile extension in isolated native hosts')
  print('PASS: installed Cursor bundle active in Java/LaTeX/Python hosts concurrently; workspace disable lists independent; global disable list unchanged')
 finally:
  rows=subprocess.check_output(['ps','-axo','pid=,args='],text=True)
  owned=[]
  for row in rows.splitlines():
   fields=row.strip().split(None,1)
   if len(fields)==2 and str(base/'data') in fields[1] and '/Applications/Cursor.app/Contents/MacOS/Cursor' in fields[1]:owned.append(int(fields[0]))
  for pid in owned:
   try:os.kill(pid,signal.SIGTERM)
   except ProcessLookupError:pass
  # Only this test's private Cursor processes are signalled.
  end=time.monotonic()+10
  for pid in owned:
   while time.monotonic()<end:
    try:os.kill(pid,0)
    except ProcessLookupError:break
    time.sleep(.2)

  # Some Electron builds retain the empty main process after SIGTERM. Recheck
  # ownership before force-stopping only that synthetic test instance.
  for pid in owned:
   command=subprocess.run(['ps','-p',str(pid),'-o','args='],capture_output=True,text=True).stdout
   if str(base/'data') in command and '/Applications/Cursor.app/Contents/MacOS/Cursor' in command:
    try:os.kill(pid,signal.SIGKILL)
    except ProcessLookupError:pass
