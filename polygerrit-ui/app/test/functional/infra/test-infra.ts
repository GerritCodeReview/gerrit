'use strict';

const {Builder} = require('selenium-webdriver');

let driver;

function setup() {
  return new Builder()
      .forBrowser('chrome')
      .usingServer('http://localhost:4444/wd/hub')
      .build()
      .then(d => {
        driver = d;
        return driver.get('http://localhost:8080');
      })
      .then(() => driver);
}

function cleanup() {
  return driver.quit();
}

exports.setup = setup;
exports.cleanup = cleanup;
