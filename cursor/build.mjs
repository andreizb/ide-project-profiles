import { build } from 'esbuild';
await build({entryPoints:['src/extension.ts'],outfile:'dist/extension.js',bundle:true,platform:'node',format:'cjs',target:'es2022',external:['vscode']});
await build({entryPoints:['test-api.ts'],outfile:'dist/test-api.cjs',bundle:true,platform:'node',format:'cjs',target:'es2022'});
