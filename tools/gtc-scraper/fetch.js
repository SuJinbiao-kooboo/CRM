const { chromium } = require('playwright-core');
const fs = require('fs');
(async () => {
  const browser = await chromium.launch({ executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe', headless: true });
  const page = await browser.newPage();
  page.on('response', async (resp) => {
    const u = resp.url();
    if (u.includes('/api/exhibitors')) {
      try { const t = await resp.text(); fs.writeFileSync('C:/Project/RuoYi-Vue/tools/gtc-scraper/exhibitors.json', t); console.log('saved', t.length); } catch(e){ console.log('err', e.message); }
    }
  });
  await page.goto('https://www.nvidia.com/gtc/sponsors/', {waitUntil:'domcontentloaded', timeout:60000});
  await page.waitForSelector('.exhibitor-tile', {timeout:60000});
  await page.waitForTimeout(4000);
  await browser.close();
})();
