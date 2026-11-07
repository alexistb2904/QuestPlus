import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { importDocuments, validate } from '../../web/editor/src/core/project.js';
import { inspectFtbPack } from '../../web/editor/src/core/ftb-import.js';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const content = path.join(root, 'web/docs-site/content');
const files = fs.readdirSync(content, { recursive: true }).filter(name => name.endsWith('.md'));
const config = fs.readFileSync(path.join(root, 'web/docs-site/.vitepress/config.mts'), 'utf8');
const media = path.join(content, 'public/media');
const manifest = JSON.parse(fs.readFileSync(path.join(media, 'manifest.json'), 'utf8'));
const oldHashes = new Set(fs.readdirSync(path.join(root, 'docs/showcase'))
  .filter(name => /\.(webp|gif|mp4)$/.test(name))
  .map(name => createHash('sha256').update(fs.readFileSync(path.join(root, 'docs/showcase', name))).digest('hex')));

for (const [name, record] of Object.entries(manifest.files)) {
  const bytes = fs.readFileSync(path.join(media, name));
  const hash = createHash('sha256').update(bytes).digest('hex');
  assert.equal(bytes.length, record.bytes, name + ' size');
  assert.equal(hash, record.sha256, name + ' hash');
  assert.ok(!oldHashes.has(hash), name + ' reuses an earlier showcase file');
}
assert.ok(Object.keys(manifest.files).length >= 18, 'Expected the complete fresh media set');
for (const [name, source] of Object.entries(manifest.sources)) {
  const sidecar = JSON.parse(fs.readFileSync(path.join(media, name + '.webp.json'), 'utf8'));
  assert.ok(sidecar.prompt.includes(source), name + ' must retain its capture origin');
}

for (const name of files) {
  const body = fs.readFileSync(path.join(content, name), 'utf8');
  const prose = body.replace(/~~~[^]*?~~~/g, '').replace(/```[^]*?```/g, '');
  assert.ok(!/[—–;]/.test(prose), name + ' uses forbidden prose punctuation');
  if (name !== 'index.md') {
    const route = '/' + name.replaceAll('\\', '/').replace(/\.md$/, '');
    assert.ok(config.includes("link: '" + route + "'"), name + ' is missing from navigation');
  }
  const targets = [...body.matchAll(/!?\[[^\]]*\]\(([^)]+)\)/g)].map(match => match[1]);
  targets.push(...[...body.matchAll(/withBase\('([^']+)'\)/g)].map(match => match[1]));
  for (const target of targets) {
    if (/^(https?:|mailto:|#)/.test(target)) continue;
    const clean = decodeURIComponent(target.split('#')[0]);
    const isAsset = /\.(webp|png|gif|mp4|json|svg)$/.test(clean);
    const candidate = clean.startsWith('/')
      ? path.join(content, isAsset ? 'public' : '', clean)
      : path.resolve(path.dirname(path.join(content, name)), clean);
    const resolved = isAsset || candidate.endsWith('.md') ? candidate : candidate.replace(/\.html$/, '') + '.md';
    assert.ok(fs.existsSync(resolved), name + ' has a broken link: ' + target);
    if (isAsset && clean !== '/media/manifest.json') assert.ok(clean.startsWith('/media/'), name + ' uses an older capture');
  }
  for (const image of body.matchAll(/!\[([^\]]*)\]\(/g)) assert.ok(image[1].trim(), name + ' has an image without alt text');
}

const format = fs.readFileSync(path.join(content, 'reference/pack-format.md'), 'utf8');
const examples = [...format.matchAll(/~~~json5\s*\n([^]*?)~~~/g)].map(match => match[1]);
const project = importDocuments([
  { name: 'categories/basics.json5', content: examples[0] },
  { name: 'quests/basics/stone.json5', content: examples[1] }
]);
assert.deepEqual(validate(project), [], 'The documented category and quest must form a valid pack');
const sample = fs.readFileSync(path.join(root, 'scripts/docs/ftb-example/chapters/first_steps.snbt'), 'utf8');
const report = inspectFtbPack([{ name: 'chapters/first_steps.snbt', content: sample }]);
assert.equal(report.project.quests.length, 2, 'The FTB example should import two supported quests');
assert.ok(report.warnings.some(warning => warning.includes('example:machine_task')), 'The FTB example should explain its exclusion');

console.log(`PASS: ${files.length} pages, navigation, links, punctuation, fresh media hashes and pack examples`);
