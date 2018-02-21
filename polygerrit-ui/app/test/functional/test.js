/**
 * @fileoverview Minimal viable frontend functional test.
 */
'use strict';

const {until, By, Key} = require('selenium-webdriver');
const {setup, cleanup} = require('test-infra');

jasmine.DEFAULT_TIMEOUT_INTERVAL = 20000;

describe('example ', () => {
  let driver;

  beforeAll(() => {
    return setup().then(d => driver = d);
  });

  afterAll(() => {
    return cleanup();
  });

  it('should update title', () => {
    return driver.wait(until.titleIs('status:open · Gerrit Code Review'), 5000);
  });

  it('finds no merged changes', () => {
    return driver.findElement(By.id('searchInput')).sendKeys(
        Key.chord(Key.CONTROL, 'a'), 'status:merged', Key.RETURN).then(() => {
          return driver.wait(
              until.titleIs('status:merged · Gerrit Code Review'), 5000);
        }).then(() => {
          return driver.findElement(
              By.css('#changeList .noChanges .cell')).getText().then(text => {
                expect(text).toBe('No changes');
              });
        });
  });

  it('finds open changes', () => {
    return driver.findElement(By.id('searchInput')).sendKeys(
        Key.chord(Key.CONTROL, 'a'), 'status:open', Key.RETURN).then(() => {
          return driver.wait(
              until.titleIs('status:open · Gerrit Code Review'), 5000);
        }).then(() => {
          return driver.findElement(
              By.css('gr-change-list-item .subject a')).getText().then(text => {
                expect(text).toBe('Test change, please ignore');
              });
        });
  });
});
