import { cp, mkdir, readFile, rm, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { build } from 'esbuild';
import { execFileSync } from 'node:child_process';

const projectDir = resolve(fileURLToPath(new URL('..', import.meta.url)));
const repoDir = resolve(projectDir, '..');
const docsDir = resolve(repoDir, 'docs');
const webDir = resolve(projectDir, 'www');

execFileSync('npm', ['--prefix', docsDir, 'run', 'build'], { stdio: 'inherit' });
await rm(webDir, { recursive: true, force: true });
await mkdir(webDir, { recursive: true });
await cp(docsDir, webDir, {
  recursive: true,
  filter: (source) => !['node_modules', 'src', 'tests'].includes(source.split('/').at(-1))
    && !['package.json', 'package-lock.json', 'tsconfig.json', 'playwright.config.js'].includes(source.split('/').at(-1)),
});

await build({
  entryPoints: [resolve(docsDir, 'build/src/app.js')],
  bundle: true,
  format: 'esm',
  outfile: resolve(webDir, 'app.js'),
  nodePaths: [resolve(projectDir, 'node_modules')],
  target: ['es2022'],
});

const indexPath = resolve(webDir, 'index.html');
const html = (await readFile(indexPath, 'utf8')).replace('./build/src/app.js', './app.js');
await writeFile(indexPath, html);
