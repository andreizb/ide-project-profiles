"""Run the installed bundle in isolated native Cursor extension hosts; never inspect real workspaces."""
from pathlib import Path
import json,os,shutil,signal,subprocess,sys,tempfile,time

root=Path(__file__).resolve().parent
installed=Path(sys.argv[1]).resolve()
cli=Path(os.environ.get('IDE_PROFILES_CURSOR_APP','/Applications/Cursor.app'))/'Contents/Resources/app/bin/cursor'
with tempfile.TemporaryDirectory(prefix='ide-profile-host-') as folder:
 base=Path(folder).resolve();extensions=base/'extensions';extensions.mkdir();results=base/'results';results.mkdir()
 package=json.loads((installed/'package.json').read_text());shutil.copytree(installed,extensions/('andrei-local.automatic-project-extensions-'+package['version']))
 shutil.copytree(root/'probe',extensions/'local-check.profile-verification-1.0.0')
 for name,language,suffix in [('java','java','.java'),('latex','latex','.tex'),('sql','sql','.sql')]:
  p=extensions/('verification.'+name+'-1.0.0');p.mkdir();(p/'index.js').write_text('exports.activate=()=>{};')
  (p/'package.json').write_text(json.dumps({'name':name,'publisher':'verification','version':'1.0.0','engines':{'vscode':'^1.90.0'},'main':'index.js','activationEvents':['onLanguage:'+language],'contributes':{'languages':[{'id':language,'extensions':[suffix]}]}}))
 for name,suffix in [('java','.java'),('latex','.tex'),('python','.py')]:
  p=base/'fixtures'/name;p.mkdir(parents=True);(p/('example'+suffix)).write_text('# local verification fixture\n')
 env=os.environ.copy();env['IDE_PROFILES_VERIFY_DIR']=str(base)
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
    raise RuntimeError('Cursor host did not reach independent workspace state for '+name+': '+problems)
  for report in reports.values():os.kill(report['pid'],0)
  assert len({r['pid'] for r in reports.values()})==3
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
