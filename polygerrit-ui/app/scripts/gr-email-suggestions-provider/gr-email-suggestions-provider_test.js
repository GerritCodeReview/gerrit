/**
 * @license
 * Copyright (C) 2019 The Android Open Source Project
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

const basicFixture = fixtureFromTemplate(html`
<gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
`);

import '../../test/common-test-setup-karma.js';
import '../../elements/shared/gr-rest-api-interface/gr-rest-api-interface.js';
import {GrEmailSuggestionsProvider} from './gr-email-suggestions-provider.js';
import {html} from '@polymer/polymer/lib/utils/html-tag.js';

suite('GrEmailSuggestionsProvider tests', () => {
  let sandbox;
  let restAPI;
  let provider;
  const account1 = {
    name: 'Some name',
    email: 'some@example.com',
  };
  const account2 = {
    email: 'other@example.com',
    _account_id: 3,
  };

  setup(() => {
    sandbox = sinon.sandbox.create();

    stub('gr-rest-api-interface', {
      getConfig() { return Promise.resolve({}); },
    });
    restAPI = basicFixture.instantiate();
    provider = new GrEmailSuggestionsProvider(restAPI);
  });

  teardown(() => {
    sandbox.restore();
  });

  test('getSuggestions', done => {
    const getSuggestedAccountsStub =
        sandbox.stub(restAPI, 'getSuggestedAccounts')
            .returns(Promise.resolve([account1, account2]));

    provider.getSuggestions('Some input').then(res => {
      assert.deepEqual(res, [account1, account2]);
      assert.isTrue(getSuggestedAccountsStub.calledOnce);
      assert.equal(getSuggestedAccountsStub.lastCall.args[0], 'Some input');
      done();
    });
  });

  test('makeSuggestionItem', () => {
    assert.deepEqual(provider.makeSuggestionItem(account1), {
      name: 'Some name <some@example.com>',
      value: {
        account: account1,
        count: 1,
      },
    });

    assert.deepEqual(provider.makeSuggestionItem(account2), {
      name: 'other@example.com <other@example.com>',
      value: {
        account: account2,
        count: 1,
      },
    });
  });
});

