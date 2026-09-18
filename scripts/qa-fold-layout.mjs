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
const menuScript = await fs.readFile('android/menu-controls.js', 'utf8');
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
          buttonHeight: button.height, visible: getComputedStyle(tray).visibility,
          dock: document.getElementById('command-dock').getBoundingClientRect().toJSON() };
      }, panel);
      assert.equal(geometry.visible, 'visible');
      assert.ok(geometry.left >= -1 && geometry.right <= width + 1,
        JSON.stringify({ width, panel, geometry }));
      assert.ok(geometry.top >= -1 && geometry.bottom <= height + 1,
        JSON.stringify({ height, panel, geometry }));
      assert.ok(geometry.buttonHeight >= 44);
      await page.screenshot({ path: 'qa-fold/' + width + 'x' + height + '-' + panel + '.png' });
    }
    await page.evaluate(() => { delete window.foldOpenPanel; delete window.foldClosePanel; });
    await page.evaluate(menuScript);
    const panels = ['data-panel', 'location-bar', 'control-panel', 'pp-toggles',
      'scene-panel', 'global-context-panel', 'cctv-panel', 'top-center-actions'];
    for (const panel of panels) {
      assert.equal(await page.evaluate(id => window.foldOpenPanel(id), panel), true);
      await page.evaluate(() => Promise.all(document.getAnimations().map(animation => {
        animation.finish(); return animation.finished.catch(() => {});
      })));
      const geometry = await page.evaluate(id => {
        const element = document.getElementById(id);
        const rect = element.getBoundingClientRect();
        return {left: rect.left, right: rect.right, top: rect.top, bottom: rect.bottom,
          display: getComputedStyle(element).display,
          closeHeight: document.getElementById('fold-panel-close').getBoundingClientRect().height,
          openCount: document.querySelectorAll('.fold-menu-panel').length};
      }, panel);
      assert.notEqual(geometry.display, 'none');
      assert.equal(geometry.openCount, 1);
      assert.ok(geometry.left >= 0 && geometry.right <= width + 1 &&
        geometry.top >= 0 && geometry.bottom <= height + 1,
        JSON.stringify({width, height, panel, geometry}));
      assert.ok(geometry.closeHeight >= 44);
      await page.screenshot({path: 'qa-fold/' + width + 'x' + height + '-menu-' + panel + '.png'});
    }
    await page.click('#fold-panel-close');
    assert.equal(await page.evaluate(() => document.querySelectorAll('.fold-menu-panel').length), 0);
    for (const panel of panels)
      assert.equal(await page.evaluate(id => getComputedStyle(document.getElementById(id)).display, panel), 'none');
  }
  console.log('Fold trays fit the cover, inner, and landscape viewports.');
} finally {
  await browser.close();
}
