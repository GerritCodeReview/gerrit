import puppeteer, {Browser, JSHandle, Page} from 'puppeteer';
// import {expect} from 'chai';
import {before, after, describe, it} from 'mocha';
import {expect} from 'chai';

const GERRIT_HOSTNAME='localhost:8080'

let browser: Browser;

before(async () => {
  browser = await puppeteer.launch({
    defaultViewport: { width: 1920, height: 1200 }
  });
  console.log(await browser.version());
});

after(async() => {
  browser.close();
});

export async function debugPage(page: Page, name: string = 'screenshot') {
  await page.screenshot({
    path: `./screenshots/${name}.png`
  });
}

export async function querySelector(page: Page, selector: string, ...selectors: string[]) {
  let handle = await (await page.evaluateHandle((s) => {console.log(s); return document.querySelector(s)}, selector)).asElement();
  if (!handle) {
    console.error('Failed to find: ', selector);
    return null;
  }
  for (let selector of selectors) {
    const jsh: JSHandle<Element> =
      await handle!.evaluateHandle((node, s) => node.shadowRoot.querySelector(s), selector) ?? null;
    const h = await jsh?.asElement();
    if (!h) {
      console.error('Failed to find: ', selector);
      console.log(handle);
      return null;
    }
    handle = h;
  }
  return handle;
}

export async function login(page: Page, username: string) {
  await page.goto(`http://${GERRIT_HOSTNAME}/`);
  const loginButton = await querySelector(page,
    '#app',
    '#app-element',
    '#mainHeader',
    '#accountContainer > a.loginButton');
  await loginButton?.click();
  await page.waitForNavigation();
  const accountId = await querySelector(page, 'input[type="text"][name="user_name"]');
  const becomeBtn = await querySelector(page, "#gerrit_body > table > tbody > tr:nth-child(1) > td > form > input[type=submit]:nth-child(2)");
  await accountId!.type(username);
  await becomeBtn!.click();
  await page.waitForNavigation();
}

describe('Login flows', async () => {
  let page: Page;
  before(async() => {
    page = await browser.newPage();
    page.on('console', (msg) => console.log(msg.text()));
    page.on('request', interceptedRequest => {
      if (interceptedRequest.url().endsWith('.png') ||
          interceptedRequest.url().endsWith('.jpg')) {
        interceptedRequest.abort();
      } else {
        interceptedRequest.continue();
      }
    });
    await page.setViewport({
      width: 1920,
      height: 1200,
    });
  });

  after(async() => {
    await page.close();
  });

  it('should login', async () => {
    await login(page, 'simply');
    const avatar = await querySelector(page,
      '#app',
      '#app-element',
      '#mainHeader',
      '#accountContainer > gr-account-dropdown',
      'gr-dropdown > span');
    expect(avatar).to.not.be.null;
    const username = await avatar?.evaluate(
      (node) => (node as HTMLElement).innerText);
    expect(username).to.equal('SIMPLY');
  });

  it('remain logged in', async () => {
    const avatar = await querySelector(page,
      '#app',
      '#app-element',
      '#mainHeader',
      '#accountContainer > gr-account-dropdown',
      'gr-dropdown > span');
    expect(avatar).to.not.be.null;
    const username = await avatar?.evaluate(
      (node) => (node as HTMLElement).innerText);
    expect(username).to.equal('SIMPLY');
  });
});
