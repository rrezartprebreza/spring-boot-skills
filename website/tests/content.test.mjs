import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { ROOT, BASE, REPO, getCatalog, installCommand, readTables, renderMarkdown, resolveLink } from '../src/lib/content.mjs';

test('catalog contains both version trees with source metadata and coverage', async () => {
  const skills = await getCatalog();
  const boot3 = skills.filter(skill => skill.version === '3');
  const boot4 = skills.filter(skill => skill.version === '4');
  assert.ok(boot3.length >= 34);
  assert.ok(boot4.length >= 34);
  assert.equal(new Set(skills.map(skill => skill.url)).size, skills.length);
  for (const skill of skills) {
    assert.ok(skill.title && skill.category && skill.compiled && skill.runtime);
    assert.ok(skill.examples.length > 0);
    assert.match(skill.url, new RegExp(`${BASE}boot-${skill.version}/${skill.slug}/$`));
    assert.ok(skill.trigger.includes('Use when'));
  }
});

test('Markdown table parser preserves categories, code tags and linked names', () => {
  assert.deepEqual(readTables('### Security\n\n| Skill | Description | Tags |\n|---|---|---|\n| [**jwt**](somewhere) | Tokens | `security` `jwt` |'), [
    { category: 'Security', cells: ['jwt', 'Tokens', 'security jwt'] },
  ]);
});

test('installation selections match both checked-in marketplaces', async () => {
  for (const agent of ['claude', 'codex']) {
    const path = agent === 'claude' ? '.claude-plugin/marketplace.json' : '.agents/plugins/marketplace.json';
    const manifest = JSON.parse(await readFile(resolve(ROOT, path), 'utf8'));
    for (const version of ['3', '4']) {
      const name = `spring-boot-${version}-skills`;
      assert.ok(manifest.plugins.some(plugin => plugin.name === name));
      assert.ok(installCommand(agent, version).includes(`${name}@spring-boot-skills`));
      assert.ok(installCommand(agent, version).includes(`${agent} plugin ${agent === 'claude' ? 'install' : 'add'}`));
    }
  }
  assert.throws(() => installCommand('unknown', '4'));
  assert.throws(() => installCommand('codex', '5'));
});

test('relative links resolve to skill pages or the correct repository source', () => {
  const source = 'skills/spring-boot-4/rest-api-conventions/SKILL.md';
  assert.equal(resolveLink('../spring-data-jpa/SKILL.md#gotchas', source), `${BASE}boot-4/spring-data-jpa/#gotchas`);
  assert.equal(resolveLink('examples/good.java', source), `${REPO}/blob/main/skills/spring-boot-4/rest-api-conventions/examples/good.java`);
  assert.equal(resolveLink('#gotchas', source), '#gotchas');
  assert.equal(resolveLink('javascript:alert(1)', source), '#');
  assert.equal(resolveLink('//evil.example', source), '#');
  assert.equal(resolveLink('../../../../outside', source), '#');
});

test('Markdown produces stable unique anchors and excludes unsafe raw HTML', async () => {
  const result = await renderMarkdown('---\nname: example\n---\n# Title\n## Gotchas\n## Gotchas\n<script>alert(1)</script>\n\n[unsafe](javascript:alert)\n\n```java\nclass Example {}\n```', 'skills/example/SKILL.md', true);
  assert.deepEqual(result.headings, [{ title: 'Gotchas', id: 'gotchas' }, { title: 'Gotchas', id: 'gotchas-1' }]);
  assert.ok(result.html.includes('id="gotchas"'));
  assert.ok(result.html.includes('language-java'));
  assert.ok(!result.html.includes('<script'));
  assert.ok(!result.html.includes('<h1'));
  assert.ok(!result.html.includes('javascript:'));
});

test('every skill renders with a usable table of contents', async () => {
  for (const skill of await getCatalog()) {
    const rendered = await renderMarkdown(skill.text, skill.source, true);
    assert.ok(rendered.headings.length > 0, skill.slug);
    assert.ok(rendered.html.includes('<h2'), skill.slug);
    for (const heading of rendered.headings) assert.ok(rendered.html.includes(`id="${heading.id}"`), `${skill.slug}: ${heading.id}`);
  }
});
