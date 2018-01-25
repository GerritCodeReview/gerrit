const {Builder, By, until} = require('selenium-webdriver');

describe('smoke test', () => {
  let driver;

  beforeEach(() => {
    driver = new Builder()
      .forBrowser('chrome')
      .usingServer('http://localhost:4444/wd/hub')
      .build();
  });

  afterEach(done => {
    driver.quit().then(done);
  });

  it('should update title', async done => {
    await driver.get('http://localhost:8080');
    await driver.wait(until.titleIs('status:open · Gerrit Code Review'), 5000);
    done();
  });
});
