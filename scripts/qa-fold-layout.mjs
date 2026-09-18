import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import puppeteer from 'puppeteer';
import { expandApplicationHtml } from '../build/application-html.js';

const html = expandApplicationHtml(await fs.readFile('index.html', 'utf8'));
const css = (await Promise.all(
  [...(await fs.readFile('style.css', 'utf8')).matchAll(/@import '([^']+)';/g)]
    .map((match) => fs.readFile(match[1], 'utf8')),
)).join('\n') + '\n' + await fs.readFile('src/ui/styles/fold.css', 'utf8');
const fixture = html.replace(/<script[^>]*>[\s\S]*?<\/script>/g, '')
  .replace(/<link[^>]*>/g, '')
  .replace('<html lang="en">', '<html lang="en" data-fold-app="true">')
  .replace('</head>', '<style>' + css + '</style></head>')
  .replace('</body>', '<div id="gev-voice-control"></div></body>');
const browser = await puppeteer.launch({
  headless: true,
  args: ['--no-sandbox', '--disable-setuid-sandbox'],
});
await fs.mkdir('qa-fold', { recursive: true });
try {
  const page = await browser.newPage();
  await page.setRequestInterception(true);
  page.on('request', (request) => request.abort());
  // Representative CSS viewports, not claims about a particular Fold model.
  for (const [width, height] of [[344, 800], [412, 850], [690, 800], [800, 690], [900, 420]]) {
    await page.setViewport({ width, height, isMobile: true, hasTouch: true });
    await page.setContent(fixture);
    await page.evaluate(() => {
      document.querySelector('#loading-screen')?.remove();
      document.querySelector('#welcome-screen')?.remove();
    });
    for (const panel of ['location-bar', 'control-panel']) {
      await page.evaluate((id) => {
        for (const name of ['location-bar', 'control-panel']) {
          document.getElementById(name).classList.toggle('collapsed', name !== id);
        }
      }, panel);
      // Finish the CSS transition before measuring.
      await page.evaluate(() => Promise.all(
        document.getAnimations().map((animation) => {
          animation.finish();
          return animation.finished.catch(() => {});
        }),
      ));
      const geometry = await page.evaluate((id) => {
        const tray = document.querySelector('#' + id + ' .dock-popover-content');
        const rect = tray.getBoundingClientRect();
        const button = document.getElementById(id + '-toggle').getBoundingClientRect();
        return { left: rect.left, right: rect.right, top: rect.top, bottom: rect.bottom,
          buttonHeight: button.height, visible: getComputedStyle(tray).visibility };
      }, panel);
      assert.equal(geometry.visible, 'visible');
      assert.ok(geometry.left >= -1 && geometry.right <= width + 1,
        JSON.stringify({ width, panel, geometry }));
      assert.ok(geometry.top >= -1 && geometry.bottom <= height + 1,
        JSON.stringify({ height, panel, geometry }));
      assert.ok(geometry.buttonHeight >= 44);
      await page.screenshot({ path: 'qa-fold/' + width + 'x' + height + '-' + panel + '.png' });
    }
  }
  console.log('Fold trays fit the cover, inner, and landscape viewports.');
} finally {
  await browser.close();
}
