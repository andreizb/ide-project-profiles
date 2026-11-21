from pathlib import Path
import json,zipfile,xml.etree.ElementTree as ET
r=Path(__file__).resolve().parent;p=json.loads((r/'package.json').read_text());ns='http://schemas.microsoft.com/developer/vsx-schema/2011'
root=ET.Element('PackageManifest',{'Version':'2.0.0','xmlns':ns});meta=ET.SubElement(root,'Metadata')
ET.SubElement(meta,'Identity',{'Language':'en-US','Id':p['name'],'Version':p['version'],'Publisher':p['publisher']})
ET.SubElement(meta,'DisplayName').text=p['displayName'];ET.SubElement(meta,'Description',{'xml:space':'preserve'}).text=p['description']
ET.SubElement(meta,'Categories').text='Other';props=ET.SubElement(meta,'Properties');ET.SubElement(props,'Property',{'Id':'Microsoft.VisualStudio.Code.Engine','Value':p['engines']['vscode']})
ET.SubElement(props,'Property',{'Id':'Microsoft.VisualStudio.Code.ExtensionDependencies','Value':''})
ET.SubElement(ET.SubElement(root,'Installation'),'InstallationTarget',{'Id':'Microsoft.VisualStudio.Code'})
ET.SubElement(root,'Dependencies');assets=ET.SubElement(root,'Assets');ET.SubElement(assets,'Asset',{'Type':'Microsoft.VisualStudio.Code.Manifest','Path':'extension/package.json','Addressable':'true'})
content=b'<?xml version="1.0"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="json" ContentType="application/json"/><Default Extension="js" ContentType="application/javascript"/><Default Extension="md" ContentType="text/markdown"/><Default Extension="txt" ContentType="text/plain"/><Default Extension="png" ContentType="image/png"/><Default Extension="svg" ContentType="image/svg+xml"/></Types>'
notices=[]
for n in ['picomatch','smol-toml']:
 d=r/'node_modules'/n;f=next(x for x in d.iterdir() if x.name.upper().startswith('LICENSE'));notices.append(n+'\n'+f.read_text())
with zipfile.ZipFile(r/'dist/automatic-project-extensions.vsix','w',zipfile.ZIP_DEFLATED) as z:
 z.writestr('[Content_Types].xml',content);z.writestr('extension.vsixmanifest',ET.tostring(root,encoding='utf-8',xml_declaration=True))
 for n in ['package.json','README.md','LICENSE.upstream','dist/extension.js','images/icon.png','images/icon.svg']:z.write(r/n,'extension/'+n)
 z.writestr('extension/THIRD_PARTY_NOTICES.txt','\n\n'.join(notices))
print('Built Cursor VSIX from TypeScript source')
