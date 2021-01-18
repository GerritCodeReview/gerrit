/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import '../../../test/common-test-setup-karma.js';
import './gr-http-password.js';
import {stubRestApi} from '../../../test/test-utils.js';

const basicFixture = fixtureFromElement('gr-http-password');

suite('gr-http-password tests', () => {
  let element;
  let account;
  let config;

  setup(done => {
    account = {username: 'user name'};
    config = {auth: {}};

    stubRestApi('getAccount').returns(Promise.resolve(account));
    stubRestApi('getConfig').returns(Promise.resolve(config));

    element = basicFixture.instantiate();
    element.loadData().then(() => { flush(done); });
  });

  test('generate password', () => {
    const button = element.$.generateButton;
    const nextPassword = 'the new password';
    let generateResolve;
    const generateStub = stubRestApi(
        'generateAccountHttpPassword')
        .callsFake(() => new Promise(resolve => {
          generateResolve = resolve;
        }));

    assert.isNotOk(element._generatedPassword);

    MockInteractions.tap(button);

    assert.isTrue(generateStub.called);
    assert.equal(element._generatedPassword, 'Generating...');

    generateResolve(nextPassword);

    generateStub.lastCall.returnValue.then(() => {
      assert.equal(element._generatedPassword, nextPassword);
    });
  });

  test('without http_password_url', () => {
    assert.isNull(element._passwordUrl);
  });

  test('with http_password_url', done => {
    config.auth.http_password_url = 'http://example.com/';
    element.loadData().then(() => {
      assert.isNotNull(element._passwordUrl);
      assert.equal(element._passwordUrl, config.auth.http_password_url);
      done();
    });
  });
});

