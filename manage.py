#!/usr/bin/env python3
"""Build, install, verify and maintain IDE Project Profiles without a retained checkout."""
from pathlib import Path
import argparse,datetime,hashlib,json,os,shutil,subprocess,sys,tempfile,time,zipfile

ROOT=Path(__file__).resolve().parent
HOME=Path.home()
SUPPORT=HOME/'Library/Application Support/IDE Project Profiles'
IDE=Path(os.environ.get('IDE_PROFILES_IDEA_APP','/Applications/IntelliJ IDEA.app'))/'Contents'
CURSOR=Path(os.environ.get('IDE_PROFILES_CURSOR_APP','/Applications/Cursor.app'))/'Contents/Resources/app/bin/cursor'
AGENTS=HOME/'Library/LaunchAgents/intellij-project-profiles'
REPO='https://github.com/andreizb/ide-project-profiles'

def digest(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def run(args,**kw):return subprocess.run([str(a) for a in args],check=True,**kw)
def atomic(p,data):
 p.parent.mkdir(parents=True,exist_ok=True)
 with tempfile.NamedTemporaryFile(dir=p.parent,delete=False) as f:
  temp=Path(f.name)
  try:
   f.write(data);f.close()
   if p.exists():shutil.copymode(p,temp)
   else:temp.chmod(0o600)
   os.replace(temp,p)
  finally:temp.unlink(missing_ok=True)
def write_json(p,d):atomic(p,(json.dumps(d,indent=2)+'\n').encode())
def ide_info():return json.loads((IDE/'Resources/product-info.json').read_text())
def config_dir():return HOME/'Library/Application Support/JetBrains'/ide_info()['dataDirectoryName']
def vm_file():
 override=os.environ.get('IDEA_VM_OPTIONS')
 if not override:
  override=subprocess.run(['/bin/launchctl','getenv','IDEA_VM_OPTIONS'],capture_output=True,text=True).stdout.strip()
 p=Path(override).expanduser() if override else config_dir()/'idea.vmoptions'
 if override and not p.is_file():raise RuntimeError('IDEA_VM_OPTIONS selects a missing file; repair that override before installation.')
 if p.is_symlink():raise RuntimeError('Custom VM options must be a regular independent file, not a symlink.')
 return p

def core_probe():
 """Use installed artifacts and the real IDE class, independent of any checkout."""
 with tempfile.TemporaryDirectory(prefix='ide-profiles-verify-') as folder:
  p=Path(folder)/'InstalledAgentProbe.java'
  p.write_text('''import java.nio.file.*;import java.lang.reflect.*;import ro.andrei.ideprofiles.bridge.ProfileBridge;
class InstalledAgentProbe {public static void main(String[] a)throws Exception {
 Class<?> t=Class.forName("com.intellij.openapi.project.impl.ProjectManagerImpl");t.getDeclaredMethods();
 if(!ProfileBridge.agentStatus.equals("active"))throw new AssertionError(ProfileBridge.agentStatus+": "+ProfileBridge.detail);
 Class<?> u=Class.forName("sun.misc.Unsafe");Field f=u.getDeclaredField("theUnsafe");f.setAccessible(true);
 Object o=u.getMethod("allocateInstance",Class.class).invoke(f.get(null),t);int[] calls={0};ProfileBridge.handler=(path,options)->{calls[0]++;return true;};
 Object result=t.getMethod("openProjectAsync",Path.class,Class.forName("com.intellij.ide.impl.OpenProjectTask"),Class.forName("kotlin.coroutines.Continuation")).invoke(o,Path.of("/tmp/installed-profile-check"),null,null);
 if(result!=null||calls[0]!=1)throw new AssertionError("Opening guard did not intercept");
 System.out.println("PASS: installed agent intercepts the real IDE opening method with JVM verification");}}
''')
  cp=':'.join([str(AGENTS/'profile-bridge.jar')]+[str(f) for f in (IDE/'lib').glob('*.jar')])
  run([IDE/'jbr/Contents/Home/bin/java','-ea','-Xverify:all','-javaagent:'+str(AGENTS/'profile-agent.jar'),'-cp',cp,p],cwd=IDE)

def verify():
 receipt=json.loads((SUPPORT/'installation.json').read_text())
 if 'intellij' in receipt:
  d=receipt['intellij'];info=ide_info();assert info['buildNumber']==d['build'],'IDE updated: rerun the GitHub installer'
  for name,sha in d['sha256'].items():assert digest(Path(name))==sha,'Installed artifact differs: '+name
  flag='-javaagent:'+str(AGENTS/'profile-agent.jar');vm=Path(d['vmOptions'])
  assert vm.read_text().splitlines().count(flag)==1,'Agent startup option is missing or duplicated'
  with zipfile.ZipFile(AGENTS/'profile-agent.jar') as z:target=dict(l.split('=',1) for l in z.read('target.properties').decode().splitlines())
  with zipfile.ZipFile(IDE/'lib/intellij.platform.ide.impl.jar') as z:assert hashlib.sha256(z.read('com/intellij/openapi/project/impl/ProjectManagerImpl.class')).hexdigest()==target['sha256']
  core_probe()
 if 'cursor' in receipt:
  d=receipt['cursor'];registry=json.loads((HOME/'.cursor/extensions/extensions.json').read_text());entry=next(e for e in registry if e['identifier']['id'].lower()==d['id'])
  path=Path(entry['location']['path']);assert json.loads((path/'package.json').read_text())['version']==d['version'];assert digest(path/'dist/extension.js')==d['bundleSha256']
  print('PASS: Cursor native extension registry and installed bundle verified')
 print('PASS: installed components work without the source checkout')
 return receipt

def install():
 if sys.platform!='darwin':raise RuntimeError('This installer targets macOS.')
 info=ide_info() if (IDE/'Resources/product-info.json').exists() else None
 has_cursor=CURSOR.is_file()
 if not info and not has_cursor:raise RuntimeError('Install IntelliJ IDEA and/or Cursor first.')
 # Native plugin replacement requires IntelliJ to be closed; never force-close user work.
 processes=subprocess.check_output(['ps','-axo','comm=,args='],text=True)
 if info and any(l.startswith(str(IDE/'MacOS/idea')) and 'stdioMcpServer' not in l for l in processes.splitlines()):raise RuntimeError('Quit IntelliJ completely, then rerun this command. Your open work was not changed.')
 env=os.environ.copy();env['npm_config_cache']=str(ROOT/'.npm-cache');env['TMPDIR']=str(ROOT/'.tmp');Path(env['TMPDIR']).mkdir(exist_ok=True)
 if info:
  run([sys.executable,ROOT/'build.py'],env=env);run([sys.executable,ROOT/'run_tests.py'],env=env)
 if has_cursor:
  if not shutil.which('npm') or not shutil.which('node'):raise RuntimeError('Node.js 22+ and npm are required for the Cursor source build.')
  run(['npm','ci','--ignore-scripts','--no-audit','--no-fund'],cwd=ROOT/'cursor',env=env)
  for task in ['typecheck','build','test']:run(['npm','run',task],cwd=ROOT/'cursor',env=env)
  run([sys.executable,ROOT/'cursor/pack.py'],env=env)
 SUPPORT.mkdir(parents=True,exist_ok=True,mode=0o700)
 stamp=datetime.datetime.now().strftime('%Y%m%d-%H%M%S-%f');backup=SUPPORT/'backups'/stamp;backup.mkdir(mode=0o700,parents=True)
 previous=SUPPORT/'installation.json'
 if previous.exists():shutil.copy2(previous,backup/'installation.json')
 result={'version':'0.2.0','repository':REPO,'sourceCommit':os.environ.get('IDE_PROFILES_SOURCE_COMMIT','local-development'),'installedAt':stamp,'backup':str(backup)}
 if info:
  AGENTS.mkdir(parents=True,exist_ok=True,mode=0o700);config=config_dir();vm=vm_file();old=vm.read_bytes() if vm.exists() else b''
  (backup/'idea.vmoptions').write_bytes(old)
  target=config/'plugins/automatic-project-plugins'
  if target.exists():shutil.copytree(target,backup/'automatic-project-plugins')
  for name in ['profile-agent.jar','profile-bridge.jar']:
   if (AGENTS/name).exists():shutil.copy2(AGENTS/name,backup/name)
   atomic(AGENTS/name,(ROOT/'dist'/name).read_bytes())
  stage=target.with_name('.automatic-project-plugins-installing')
  if stage.exists():raise RuntimeError('A prior plugin installation staging directory exists; inspect it first.')
  shutil.copytree(ROOT/'dist/automatic-project-plugins',stage)
  if target.exists():shutil.rmtree(target)
  stage.rename(target)
  lines=[l for l in old.decode().splitlines() if not(l.startswith('-javaagent:') and '/intellij-project-profiles/profile-agent.jar' in l)];lines.append('-javaagent:'+str(AGENTS/'profile-agent.jar'));atomic(vm,('\n'.join(lines)+'\n').encode())
  marker=config/'macos-priority.enabled'
  if not marker.exists():atomic(marker,b'IDE shortcut policy enabled; macOS preferences remain untouched.\n')
  result['intellij']={'build':info['buildNumber'],'vmOptions':str(vm),'sha256':{str(p):digest(p) for p in [AGENTS/'profile-agent.jar',AGENTS/'profile-bridge.jar',target/'lib/automatic-project-plugins.jar']}}
  # Retain compatibility with the earlier local uninstall helper, without obsolete source paths.
  write_json(AGENTS/'installation.json',{'installedAt':stamp,'ideBuild':info['buildNumber'],'agent':str(AGENTS/'profile-agent.jar'),'pluginDirectory':str(target),'vmOptions':str(vm),'vmOptionsBackup':str(backup/'idea.vmoptions'),'originalVmOptionsSha256':hashlib.sha256(old).hexdigest(),'installedVmOptionsSha256':digest(vm),'artifacts':{n:digest(AGENTS/n) for n in ['profile-agent.jar','profile-bridge.jar']}})
 if has_cursor:
  registry=HOME/'.cursor/extensions/extensions.json';before=json.loads(registry.read_text()) if registry.exists() else [];write_json(backup/'cursor-extensions.json',before)
  run([CURSOR,'--install-extension',ROOT/'cursor/dist/automatic-project-extensions.vsix','--force'],cwd=CURSOR.parent)
  after=json.loads(registry.read_text());owned='andrei-local.automatic-project-extensions';old_other={e['identifier']['id'].lower():e['version'] for e in before if e['identifier']['id'].lower()!=owned};new_other={e['identifier']['id'].lower():e['version'] for e in after if e['identifier']['id'].lower()!=owned}
  if old_other!=new_other:raise RuntimeError('An unrelated Cursor extension changed during install; the registry baseline is backed up for inspection.')
  entry=next(e for e in after if e['identifier']['id'].lower()==owned);location=Path(entry['location']['path']);result['cursor']={'id':owned,'version':entry['version'],'location':str(location),'bundleSha256':digest(location/'dist/extension.js')}
 write_json(SUPPORT/'installation.json',result)
 # Installed maintenance code contains no build tree or dependency cache.
 atomic(SUPPORT/'manage.py',Path(__file__).read_bytes())
 verify()
 if has_cursor:run([sys.executable,ROOT/'cursor/verify_host.py',result['cursor']['location']])
 write_json(SUPPORT/'verification.json',{'verifiedAt':datetime.datetime.now().isoformat(),'sourceCommit':result['sourceCommit'],'installedAgentProbe':bool(info),'cursorHostProbe':has_cursor,'passed':True})
 print('Installation verified. Temporary source, dependencies and build output will now be removed by the bootstrap.')

def uninstall():
 receipt=json.loads((SUPPORT/'installation.json').read_text())
 if 'cursor' in receipt:
  # Workspace choices are user-owned data; never silently clear every workspace database.
  raise RuntimeError('Use Enable All Extensions Here in each managed Cursor workspace first, then uninstall the Cursor extension from its native UI. IntelliJ files were not changed.')
 if 'intellij' in receipt:
  d=receipt['intellij'];p=Path(d['vmOptions']);flag='-javaagent:'+str(AGENTS/'profile-agent.jar');atomic(p,('\n'.join(l for l in p.read_text().splitlines() if l!=flag)+'\n').encode());target=config_dir()/'plugins/automatic-project-plugins';backup=SUPPORT/'backups'/('uninstall-'+datetime.datetime.now().strftime('%Y%m%d-%H%M%S'));backup.mkdir(parents=True);shutil.move(str(target),backup/'automatic-project-plugins')
 print('IntelliJ integration removed. Project data and inactive agent JARs remain for safe rollback.')

if __name__=='__main__':
 try:
  action=sys.argv[1] if len(sys.argv)>1 else 'install'
  {'install':install,'verify':verify,'uninstall':uninstall}[action]()
 except (Exception,KeyboardInterrupt) as e:
  print('IDE Project Profiles:',str(e),file=sys.stderr);sys.exit(1)
