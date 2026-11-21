const assert=require('node:assert/strict'),fs=require('node:fs/promises'),os=require('node:os'),path=require('node:path');
const t=require('./dist/test-api.cjs'),rules=t.normalizeUserRules(require('./rules.json'));
const ext=(id,manifest)=>({id,manifest,isBuiltin:false,isApplicationScoped:false});
const installed=[ext('redhat.java',{contributes:{languages:[{id:'java',extensions:['.java']} ]}}),ext('vscjava.vscode-java-debug',{activationEvents:['onLanguage:java']}),ext('vscjava.vscode-java-test',{activationEvents:['onLanguage:java'],extensionDependencies:['redhat.java','vscjava.vscode-java-debug']}),ext('james-yu.latex-workshop',{contributes:{languages:[{id:'latex',extensions:['.tex']}]}}),ext('oracle.sql-developer',{contributes:{languages:[{id:'sql',extensions:['.sql']}]}}),ext('anysphere.remote-ssh',{}),ext('anysphere.remote-containers',{})];
(async()=>{
 const index=t.buildLanguageIndex([{contributes:{languages:[{id:'python',extensions:['.py']}]}},...installed.map(x=>x.manifest)]),ctx={index,dependedOn:t.dependedOn(installed),hints:t.PROJECT_HINTS,userRules:rules};
 for(const [name,files,on]of [['java',['Hello.java','pom.xml'],['redhat.java','vscjava.vscode-java-debug','vscjava.vscode-java-test']],['python',['main.py'],[]],['latex',['main.tex'],['james-yu.latex-workshop']],['sql',['schema.sql'],['oracle.sql-developer']]]){
  const d=t.detect([{name,files}],[],index,[]),p=t.plan(installed,d,ctx,{}),enabled=new Set(p.extensions.map(e=>e.id));
  for(const id of installed.map(x=>x.id))assert.equal(enabled.has(id),on.includes(id)||id.startsWith('anysphere.'),name+': '+id);
 }
 const local=[],global=[{id:'manual.global'}],writes=[];
 const channel={initializeExtensionStorage:async shared=>shared?global:local,setValue:async(shared,key,value)=>{writes.push(shared);assert.equal(shared,false);local.splice(0,local.length,...(value||[]));}};
 assert.equal(t.WorkspaceEnablement.from({_shared:true,_storage:channel}),undefined);
 await t.WorkspaceEnablement.from({_shared:false,_storage:channel}).setWorkspaceDisabled([{id:'auto.off'}]);assert.deepEqual(global,[{id:'manual.global'}]);assert.deepEqual(writes,[false]);
 let reads=0;const restored=[];const incompatible={initializeExtensionStorage:async()=>++reads===1?[]:[{id:'wrong'}],setValue:async(s,k,v)=>restored.push(v)};
 await assert.rejects(()=>t.WorkspaceEnablement.from({_shared:false,_storage:incompatible}).setWorkspaceDisabled([{id:'auto.off'}]),/restored/);assert.equal(restored.at(-1),undefined);
 assert.deepEqual(t.nextDisabledList([{id:'manual.off',uuid:'keep'},{id:'auto.off'}],['auto.off'],['next.off']).list,[{id:'manual.off',uuid:'keep'},{id:'next.off'}]);
 const dir=await fs.mkdtemp(path.join(os.tmpdir(),'profile-state-'));try{
  const a=new t.StateStore(path.join(dir,'a/state.json')),b=new t.StateStore(path.join(dir,'b/state.json'));
  await Promise.all(Array.from({length:30},(_,i)=>a.updateWorkspace('a',w=>{w['a'+i]=true})).concat(Array.from({length:30},(_,i)=>b.updateWorkspace('b',w=>{w['b'+i]=true}))));
  assert.equal(Object.keys(await a.workspace('a')).length,30);assert.equal(Object.keys(await b.workspace('b')).length,30);assert.deepEqual(Object.keys((await a.read()).workspaces),['a']);
 }finally{await fs.rm(dir,{recursive:true,force:true});}
 console.log('PASS: Java/dependency/LaTeX/SQL/Python selection, remote preservation, scope guard, read-back rollback, manual choices and independent serialized state');
})().catch(e=>{console.error(e);process.exit(1)});
