const {Builder, By, until} = require('selenium-webdriver');

describe('smoke test', function() {
  let driver;

  beforeEach(function() {
    driver = new Builder()
      .forBrowser('chrome')
      .usingServer('http://localhost:4444/wd/hub')
      .build();
  });

  afterEach(function(done) {
    driver.quit().then(done);
  });

  it('should update title', async function(done) {
    await driver.get('https://gerrit-review.googlesource.com/?polygerrit=1');
    await driver.wait(until.titleIs('status:open · Gerrit Code Review'), 5000);
    done();
  });
});
