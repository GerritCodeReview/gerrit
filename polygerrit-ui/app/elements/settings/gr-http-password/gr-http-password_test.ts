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

import '../../../test/common-test-setup-karma';
import './gr-http-password';
import {GrHttpPassword} from './gr-http-password';
import {stubRestApi} from '../../../test/test-utils';
import * as MockInteractions from '@polymer/iron-test-helpers/mock-interactions';
import {
  createAccountDetailWithId,
  createServerInfo,
} from '../../../test/test-data-generators';
import {AccountDetailInfo, ServerInfo} from '../../../types/common';

const basicFixture = fixtureFromElement('gr-http-password');

suite('gr-http-password tests', () => {
  let element: GrHttpPassword;
  let account: AccountDetailInfo;
  let config: ServerInfo;

  setup(done => {
    account = {...createAccountDetailWithId(), username: 'user name'};
    config = createServerInfo();

    stubRestApi('getAccount').returns(Promise.resolve(account));
    stubRestApi('getConfig').returns(Promise.resolve(config));

    element = basicFixture.instantiate();
    element.loadData().then(() => {
      flush(done);
    });
  });

  test('generate password', () => {
    const button = element.$.generateButton;
    const nextPassword = 'the new password';
    let generateResolve: (value: string | PromiseLike<string>) => void;
    const generateStub = stubRestApi('generateAccountHttpPassword').callsFake(
      () =>
        new Promise(resolve => {
          generateResolve = resolve;
        })
    );

    assert.isNotOk(element._generatedPassword);

    MockInteractions.tap(button);

    assert.isTrue(generateStub.called);
    assert.equal(element._generatedPassword, 'Generating...');

    generateStub.lastCall.returnValue.then(() => {
      generateResolve(nextPassword);
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
