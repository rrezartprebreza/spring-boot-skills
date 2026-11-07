import { readFile, readdir } from 'node:fs/promises';
import { resolve, relative, dirname, sep } from 'node:path';
import { unified } from 'unified';
import remarkParse from 'remark-parse';
import remarkGfm from 'remark-gfm';
import remarkFrontmatter from 'remark-frontmatter';
import remarkRehype from 'remark-rehype';
import rehypeSanitize from 'rehype-sanitize';
import rehypeStringify from 'rehype-stringify';
import { visit } from 'unist-util-visit';
import { toString } from 'mdast-util-to-string';
import { parseDocument } from 'yaml';
import GithubSlugger from 'github-slugger';

// npm scripts run from website/, including Astro's bundled prerender process.
export const ROOT = resolve(process.cwd(), '..');
export const REPO = 'https://github.com/rrezartprebreza/spring-boot-skills';
export const BASE = '/spring-boot-skills/';
export const href = (path = '') => `${BASE}${path}`;
const parser = unified().use(remarkParse).use(remarkGfm).use(remarkFrontmatter);
const read = (path) => readFile(resolve(ROOT, path), 'utf8');

function yaml(text, file) {
  const doc = parseDocument(text);
  if (doc.errors.length) throw new Error(`Invalid YAML in ${file}: ${doc.errors[0].message}`);
  return doc.toJS();
}

/** Read catalog categories and coverage from their existing Markdown tables. */
export function readTables(text) {
  let category = '';
  const rows = [];
  for (const node of parser.parse(text).children) {
    if (node.type === 'heading' && node.depth === 3) {
      category = toString(node).replace(/^[^\p{L}\p{N}]+/u, '').trim();
    }
    if (node.type === 'table') {
      for (const row of node.children.slice(1)) {
        const cells = row.children.map(toString);
        rows.push({ category, cells });
      }
    }
  }
  return rows;
}

/** Resolve relative repository links without allowing protocols outside HTTP(S). */
export function resolveLink(url, source) {
  if (url.startsWith('#')) return url;
  if (/^https?:\/\//i.test(url)) return url;
  if (/^[a-z][a-z\d+.-]*:/i.test(url) || url.startsWith('//')) return '#';
  const [path, fragment] = url.split('#');
  const absolute = resolve(ROOT, dirname(source), decodeURIComponent(path));
  const local = relative(ROOT, absolute).split(sep).join('/');
  if (local.startsWith('../')) return '#';
  const skill = local.match(/^skills\/spring-boot-([34])\/([^/]+)(?:\/SKILL\.md)?\/?$/);
  if (skill) return href(`boot-${skill[1]}/${skill[2]}/`) + (fragment ? `#${fragment}` : '');
  return `${REPO}/blob/main/${local.split('/').map(encodeURIComponent).join('/')}${fragment ? `#${fragment}` : ''}`;
}

/** Render repository Markdown with sanitized HTML and matching heading anchors. */
export async function renderMarkdown(text, source, removeTitle = false) {
  const tree = parser.parse(text);
  const slugger = new GithubSlugger();
  const headings = [];
  tree.children = tree.children.filter((node) => node.type !== 'yaml' && !(removeTitle && node.type === 'heading' && node.depth === 1));
  visit(tree, 'heading', (node) => {
    const title = toString(node);
    const id = slugger.slug(title);
    node.data = { hProperties: { id } };
    if (node.depth === 2) headings.push({ title, id });
  });
  visit(tree, (node) => {
    if (node.type === 'link' || node.type === 'definition' || node.type === 'image') {
      node.url = resolveLink(node.url, source);
    }
  });
  const renderer = unified().use(remarkRehype).use(rehypeSanitize, { clobberPrefix: '' }).use(rehypeStringify);
  return { html: renderer.stringify(await renderer.run(tree)), headings };
}

let cached;
export function getCatalog() {
  return cached ??= loadCatalog();
}

async function loadCatalog() {
  const catalog = new Map(readTables(await read('README.md')).map(row => [row.cells[0], row]));
  const coverage = new Map(readTables(await read('verification/MATRIX.md')).map(row => [row.cells[0], row.cells]));
  const skills = [];
  for (const version of ['3', '4']) {
    const root = `skills/spring-boot-${version}`;
    const dirs = await readdir(resolve(ROOT, root), { withFileTypes: true });
    for (const dir of dirs.filter(dir => dir.isDirectory())) {
      const slug = dir.name;
      const source = `${root}/${slug}/SKILL.md`;
      const text = await read(source);
      const frontmatter = parser.parse(text).children.find(node => node.type === 'yaml');
      const data = yaml(frontmatter?.value ?? '', source);
      const meta = yaml(await read(`${root}/${slug}/agents/openai.yaml`), source).interface;
      if (data.name !== slug || !data.description || !meta.display_name) throw new Error(`Invalid metadata: ${source}`);
      const row = catalog.get(slug);
      const proof = coverage.get(slug);
      if (!row || !proof) throw new Error(`Missing catalog or coverage entry: ${slug}`);
      const examples = [];
      for (const folder of ['examples', 'templates']) {
        let files;
        try { files = await readdir(resolve(ROOT, root, slug, folder), { withFileTypes: true }); }
        catch (error) { if (error.code === 'ENOENT') continue; throw error; }
        for (const file of files.filter(file => file.isFile()).sort((a, b) => a.name.localeCompare(b.name))) {
          examples.push({ name: file.name, folder, source: `${root}/${slug}/${folder}/${file.name}` });
        }
      }
      skills.push({
        slug, version, source, text, title: meta.display_name,
        summary: meta.short_description, trigger: data.description.trim(),
        category: row.category, tags: row.cells[2].split(/\s+/).filter(Boolean),
        compiled: proof[1], runtime: proof[2], examples,
        url: href(`boot-${version}/${slug}/`),
      });
    }
  }
  return skills.sort((a, b) => a.title.localeCompare(b.title));
}

export async function exampleContent(source) { return read(source); }

/** Generate commands from the actual versioned plugin names in this repository. */
export function installCommand(agent, version) {
  if (!['claude', 'codex'].includes(agent) || !['3', '4'].includes(version)) throw new Error('Invalid installation selection');
  return `${agent} plugin marketplace add rrezartprebreza/spring-boot-skills\n${agent} plugin ${agent === 'claude' ? 'install' : 'add'} spring-boot-${version}-skills@spring-boot-skills`;
}
