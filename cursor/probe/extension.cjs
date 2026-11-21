const vscode=require('vscode'),fs=require('node:fs/promises'),path=require('node:path');
exports.activate=context=>{
 const base=process.env.IDE_PROFILES_VERIFY_DIR,folder=vscode.workspace.workspaceFolders?.[0];
 if(!base||!folder||!folder.uri.fsPath.startsWith(path.join(base,'fixtures')+path.sep))return;
 const timer=setInterval(async()=>{try{
  const channel=context.workspaceState._storage;if(context.workspaceState._shared!==false)throw Error('Wrong workspace scope');
  const ext=vscode.extensions.getExtension('andrei-local.automatic-project-extensions');if(ext&&!ext.isActive)await ext.activate();
  await fs.mkdir(path.join(base,'results'),{recursive:true});
  await fs.writeFile(path.join(base,'results',folder.name+'.json'),JSON.stringify({pid:process.pid,active:ext?.isActive,version:ext?.packageJSON.version,enabled:vscode.extensions.all.map(e=>e.id.toLowerCase()),local:await channel.initializeExtensionStorage(false,'extensionsIdentifiers/disabled',null),global:await channel.initializeExtensionStorage(true,'extensionsIdentifiers/disabled',null),workspaceStorage:context.storageUri?.fsPath},null,2));
 }catch(e){await fs.writeFile(path.join(base,'probe-error.txt'),String(e));}},1500);
 context.subscriptions.push({dispose:()=>clearInterval(timer)});
};
