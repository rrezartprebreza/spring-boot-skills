import { test, expect } from '@playwright/test';
import { getCatalog } from '../../src/lib/content.mjs';
const base = '/spring-boot-skills/';
const catalog = await getCatalog();

test('catalog filters, empty state, reset and URL persistence', async ({ page }) => {
  await page.goto(base);
  await expect(page.locator('.skill-card:visible')).toHaveCount(catalog.filter(skill => skill.version === '4').length);
  await page.getByRole('searchbox').fill('oauth2-resource-server');
  await expect(page.locator('.skill-card:visible')).toHaveCount(2);
  await expect(page.locator('.skill-card:visible').filter({ hasText: 'OAuth2 Resource Server' })).toHaveCount(1);
  await page.reload();
  await expect(page.getByRole('searchbox')).toHaveValue('oauth2-resource-server');
  await page.getByRole('searchbox').fill('nothing-matches-123');
  await expect(page.getByText('No matching skills')).toBeVisible();
  await page.getByRole('button', { name: 'Clear filters' }).click();
  if (await page.locator('#category-select').isVisible()) await page.locator('#category-select').selectOption('Security');
  else await page.locator('[data-category-link="Security"]').click();
  await expect(page.locator('.skill-card:visible')).toHaveCount(catalog.filter(skill => skill.version === '4' && skill.category === 'Security').length);
  await page.getByRole('navigation', { name: 'Spring Boot version' }).getByRole('link', { name: 'Boot 3' }).click();
  await expect(page.locator('.skill-card:visible')).toHaveCount(catalog.filter(skill => skill.version === '3' && skill.category === 'Security').length);
  await expect(page).toHaveURL(/boot-3\/\?category=Security/);
});

test('agent selection generates correct versioned commands and clipboard', async ({ page, context }) => {
  await context.grantPermissions(['clipboard-read', 'clipboard-write']);
  await page.goto(`${base}boot-3/`);
  if (!(await page.locator('.install').evaluate(panel => panel.open))) await page.locator('.install summary').click();
  await page.getByRole('button', { name: 'Codex', exact: true }).click();
  await expect(page.locator('[data-install-agent="codex"]')).toContainText('codex plugin add spring-boot-3-skills@spring-boot-skills');
  await page.getByRole('button', { name: 'Copy installation commands' }).click();
  await expect(page.locator('.copy-status')).toHaveText('Copied');
  expect(await page.evaluate(() => navigator.clipboard.readText())).toContain('codex plugin add spring-boot-3-skills');
});

test('skill page, version counterpart, source examples, anchors and themes', async ({ page }) => {
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  await page.goto(`${base}boot-4/oauth2-resource-server/`);
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('OAuth2 Resource Server');
  await expect(page.locator('#gotchas')).toBeAttached();
  await page.getByRole('navigation', { name: 'Spring Boot version' }).getByRole('link', { name: 'Boot 3' }).click();
  await expect(page).toHaveURL(/boot-3\/oauth2-resource-server\//);
  await page.locator('.example').filter({ hasText: 'ExternalIdentity.java' }).locator('summary').click();
  await expect(page.locator('.example[open] pre')).toContainText('record ExternalIdentity');
  const before = await page.locator('html').getAttribute('data-theme');
  await page.getByRole('button', { name: 'Switch color theme' }).click();
  await expect(page.locator('html')).not.toHaveAttribute('data-theme', before);
  await page.reload();
  await expect(page.locator('html')).not.toHaveAttribute('data-theme', before);
  expect(errors).toEqual([]);
});

test('responsive pages fit the viewport and render media', async ({ page }, testInfo) => {
  for (const path of ['', 'boot-4/spring-data-jpa/', 'boot-4/java-supply-chain-security/', 'boot-3/spring-ai-security/', 'guides/', 'verification/', 'about/']) {
    await page.goto(`${base}${path}`);
    await expect(page.locator('h1')).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBe(true);
  }
  await expect(page.locator('.maintainer img')).toBeVisible();
  await expect.poll(() => page.locator('.maintainer img').evaluate(img => img.naturalWidth)).toBeGreaterThan(0);
  await page.goto(base);
  await page.screenshot({ path: testInfo.outputPath('catalog.png'), fullPage: false, animations: 'disabled', scale: 'css' });
  await page.locator('html').evaluate(el => el.dataset.theme = 'dark');
  await page.screenshot({ path: testInfo.outputPath('catalog-dark.png'), fullPage: false, animations: 'disabled', scale: 'css' });
});

test('all built skill URLs resolve and render unique titles', async ({ page, request }) => {
  for (const version of ['3', '4']) {
    await page.goto(`${base}boot-${version}/`);
    const links = await page.locator('.skill-card').evaluateAll(cards => cards.map(card => card.getAttribute('href')));
    for (const link of links) {
      const response = await request.get(link);
      expect(response.status(), link).toBe(200);
      expect(await response.text()).toContain('View SKILL.md');
    }
  }
});
