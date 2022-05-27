/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
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
import {queryAndAssert} from '../../../test/test-utils';
import {GrButton} from '../../shared/gr-button/gr-button';

const basicFixture = fixtureFromElement('gr-http-password');

suite('gr-http-password tests', () => {
  let element: GrHttpPassword;
  let account: AccountDetailInfo;
  let config: ServerInfo;

  setup(async () => {
    account = {...createAccountDetailWithId(), username: 'user name'};
    config = createServerInfo();

    stubRestApi('getAccount').returns(Promise.resolve(account));
    stubRestApi('getConfig').returns(Promise.resolve(config));

    element = basicFixture.instantiate();
    await element.loadData();
    await flush();
  });

  test('generate password', () => {
    const button = queryAndAssert<GrButton>(element, '#generateButton');
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

  test('with http_password_url', async () => {
    config.auth.http_password_url = 'http://example.com/';
    await element.loadData();
    assert.isNotNull(element._passwordUrl);
    assert.equal(element._passwordUrl, config.auth.http_password_url);
  });
});
