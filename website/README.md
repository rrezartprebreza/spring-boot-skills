# Skills website

Static Astro catalog for both Spring Boot version trees. Requires Node 24 LTS
(or Node >=22.23.4). No backend, database, API keys, or runtime GitHub API calls.

```sh
cd website
npm ci
npm run dev
```

Open the printed URL under `/spring-boot-skills/`.

## Content

- Skill names, triggers, documents, and examples come from `../skills/`.
- Categories and tags come from the existing README Markdown tables.
- Verification details come from `../verification/MATRIX.md`.
- Task guides are curated in `src/pages/guides.astro`.
- Installation commands use the plugin names validated against both manifests.

The build fails if a skill lacks metadata, a catalog entry, or a coverage entry.
Do not add a second manually maintained skill catalog to the website.
Relative skill links become site links; other relative links open their repository source.
Raw HTML in skill Markdown is not rendered; code examples are escaped.

## Verification

```sh
npm test
npm run build
npx playwright install chromium
npm run test:e2e
```

Browser tests cover both mobile and desktop: search, categories, version switching,
installation command selection/copying, example expansion, theme persistence,
all skill routes, and horizontal overflow. Screenshots are saved in `test-results/`.

## GitHub Pages

In repository Settings > Pages, select GitHub Actions as the build source. The
`Skills website` workflow tests pull requests, then publishes only from `main`
after the website checks pass. The expected URL is:

https://rrezartprebreza.github.io/spring-boot-skills/

The website is not published by local builds. Merge the reviewed website PR to
deploy after Pages is enabled. To use a custom domain, update `site` and `base` in
`astro.config.mjs` and the shared `BASE` in `src/lib/content.mjs`, then rerun tests.

Google Fonts is an external resource; system fonts are the fallback. The maintainer
photo in `public/maintainer.jpg` comes from the owner's public GitHub profile.
No application analytics or tracking is included.
