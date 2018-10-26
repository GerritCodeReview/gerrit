'use strict';

const {Builder} = require('selenium-webdriver');

let driver;

async function setup() {
  const driver = await new Builder()
      .forBrowser('chrome')
      .usingServer('http://localhost:4444/wd/hub')
      .build();
  await driver.get('http://localhost:8080');
  return driver;
}

function cleanup() {
  return driver.quit();
}

exports.setup = setup;
exports.cleanup = cleanup;
