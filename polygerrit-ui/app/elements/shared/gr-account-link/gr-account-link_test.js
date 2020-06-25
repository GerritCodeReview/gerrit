/**
 * @license
 * Copyright (C) 2015 The Android Open Source Project
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
import './gr-account-link.js';
import {GerritNav} from '../../core/gr-navigation/gr-navigation.js';

const basicFixture = fixtureFromElement('gr-account-link');

suite('gr-account-link tests', () => {
  let element;
  let sandbox;

  setup(() => {
    stub('gr-rest-api-interface', {
      getConfig() { return Promise.resolve({}); },
    });
    element = basicFixture.instantiate();
    sandbox = sinon.sandbox.create();
  });

  teardown(() => {
    sandbox.restore();
  });

  test('computed fields', () => {
    const url = 'test/url';
    const urlStub = sandbox.stub(GerritNav, 'getUrlForOwner').returns(url);
    const account = {
      email: 'email',
      username: 'username',
      name: 'name',
      _account_id: '_account_id',
    };
    assert.isNotOk(element._computeOwnerLink());
    assert.equal(element._computeOwnerLink(account), url);
    assert.isTrue(urlStub.lastCall.calledWithExactly('email'));

    delete account.email;
    assert.equal(element._computeOwnerLink(account), url);
    assert.isTrue(urlStub.lastCall.calledWithExactly('username'));

    delete account.username;
    assert.equal(element._computeOwnerLink(account), url);
    assert.isTrue(urlStub.lastCall.calledWithExactly('name'));

    delete account.name;
    assert.equal(element._computeOwnerLink(account), url);
    assert.isTrue(urlStub.lastCall.calledWithExactly('_account_id'));
  });
});

