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

import '../../test/common-test-setup-karma.js';
import {GrEmailSuggestionsProvider} from './gr-email-suggestions-provider.js';
import {appContext} from '../../services/app-context.js';
import {stubRestApi} from '../../test/test-utils.js';
import {createServerInfo} from '../../test/test-data-generators.js';

suite('GrEmailSuggestionsProvider tests', () => {
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
    stubRestApi('getConfig').returns(Promise.resolve(createServerInfo()));
    provider = new GrEmailSuggestionsProvider(appContext.restApiService);
  });

  test('getSuggestions', done => {
    const getSuggestedAccountsStub =
        stubRestApi('getSuggestedAccounts').returns(
            Promise.resolve([account1, account2]));

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

