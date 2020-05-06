/**
 * @fileoverview Minimal viable frontend functional test.
 */
'use strict';

const {until} = require('selenium-webdriver');
const {setup, cleanup} = require('test-infra');

jasmine.DEFAULT_TIMEOUT_INTERVAL = 20000;

describe('example ', () => {
  let driver;

  beforeAll(() => setup().then(d => driver = d));

  afterAll(() => cleanup());

  it('should update title', () => driver.wait(
      until.titleIs('status:open · Gerrit Code Review'), 5000
  ));
});
