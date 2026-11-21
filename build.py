#!/usr/bin/env python3
"""Build against the installed IntelliJ SDK without downloading a second IDE."""
from pathlib import Path
import hashlib, json, shutil, subprocess, zipfile, os, xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent
IDE = Path(os.environ.get('IDE_PROFILES_IDEA_APP','/Applications/IntelliJ IDEA.app'))/'Contents'
JAVA_BIN = IDE / 'jbr/Contents/Home/bin'
BUILD = ROOT / 'build'
DIST = ROOT / 'dist'

def compile_java(source, dest, cp=''):
    dest.mkdir(parents=True, exist_ok=True)
    cmd=[str(JAVA_BIN/'javac'), '--release', '21', '-encoding', 'UTF-8', '-d', str(dest)]
    if cp: cmd += ['-cp', cp]
    cmd += [str(p) for p in sorted(source.rglob('*.java'))]
    result=subprocess.run(cmd)
    if result.returncode: raise SystemExit(result.returncode)

def jar(path, directory, extra=None):
    path.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(path,'w',zipfile.ZIP_DEFLATED) as z:
        for p in sorted(directory.rglob('*')):
            if p.is_file(): z.write(p,p.relative_to(directory).as_posix())
        for name,data in (extra or {}).items(): z.writestr(name,data)

def main():
    if BUILD.exists(): shutil.rmtree(BUILD)
    BUILD.mkdir(); DIST.mkdir(exist_ok=True)
    compile_java(ROOT/'src/bridge',BUILD/'bridge')
    jar(DIST/'profile-bridge.jar',BUILD/'bridge')
    asm=IDE/'lib/intellij.libraries.asm.jar'
    compile_java(ROOT/'src/agent',BUILD/'agent',f'{BUILD/"bridge"}:{asm}')
    with zipfile.ZipFile(asm) as z:
        for name in z.namelist():
            if name.endswith('.class'):
                target=BUILD/'agent'/name; target.parent.mkdir(parents=True,exist_ok=True); target.write_bytes(z.read(name))
    with zipfile.ZipFile(IDE/'lib/intellij.platform.ide.impl.jar') as z:
        digest=hashlib.sha256(z.read('com/intellij/openapi/project/impl/ProjectManagerImpl.class')).hexdigest()
    info=json.loads((IDE/'Resources/product-info.json').read_text())
    jar(DIST/'profile-agent.jar',BUILD/'agent',{
        'META-INF/MANIFEST.MF':'Manifest-Version: 1.0\nPremain-Class: ro.andrei.ideprofiles.agent.ProfileAgent\n\n',
        'target.properties':f'build={info["buildNumber"]}\nsha256={digest}\n',
        'META-INF/LICENSE.asm.txt':(ROOT/'licenses/ASM.txt').read_bytes()})
    cp=':'.join([str(BUILD/'bridge')]+[str(p) for p in (IDE/'lib').glob('*.jar')])
    compile_java(ROOT/'src/plugin',BUILD/'plugin',cp)
    shutil.copytree(ROOT/'src/plugin/META-INF',BUILD/'plugin/META-INF')
    # Advertise the SDK actually compiled and verified, rather than a stale manual range.
    descriptor=BUILD/'plugin/META-INF/plugin.xml'
    tree=ET.parse(descriptor)
    bounds=tree.getroot().find('idea-version')
    bounds.set('since-build',info['buildNumber'])
    bounds.set('until-build',info['buildNumber'])
    tree.write(descriptor,encoding='utf-8',xml_declaration=True)
    jar(DIST/'automatic-project-plugins/lib/automatic-project-plugins.jar',BUILD/'plugin')
    with zipfile.ZipFile(DIST/'automatic-project-plugins.zip','w',zipfile.ZIP_DEFLATED) as z:
        for p in (DIST/'automatic-project-plugins').rglob('*'):
            if p.is_file(): z.write(p,p.relative_to(DIST))
    print('Built agent, bridge, and plugin for IntelliJ '+info['version']+' ('+info['buildNumber']+')')

if __name__=='__main__': main()
