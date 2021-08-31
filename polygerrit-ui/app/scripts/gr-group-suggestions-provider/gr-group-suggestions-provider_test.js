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
import {GrGroupSuggestionsProvider} from './gr-group-suggestions-provider.js';
import {appContext} from '../../services/app-context.js';
import {stubRestApi} from '../../test/test-utils.js';

suite('GrGroupSuggestionsProvider tests', () => {
  let provider;
  const group1 = {
    name: 'Some name',
    id: 1,
  };
  const group2 = {
    name: 'Other name',
    id: 3,
    url: 'abcd',
  };

  setup(() => {
    provider = new GrGroupSuggestionsProvider(appContext.restApiService);
  });

  test('getSuggestions', async () => {
    const getSuggestedAccountsStub =
        stubRestApi('getSuggestedGroups')
            .returns(Promise.resolve({
              'Some name': {id: 1},
              'Other name': {id: 3, url: 'abcd'},
            }));

    const res = await provider.getSuggestions('Some input');
    assert.deepEqual(res, [group1, group2]);
    assert.isTrue(getSuggestedAccountsStub.calledOnce);
    assert.equal(getSuggestedAccountsStub.lastCall.args[0], 'Some input');
  });

  test('makeSuggestionItem', () => {
    assert.deepEqual(provider.makeSuggestionItem(group1), {
      name: 'Some name',
      value: {
        group: {
          name: 'Some name',
          id: 1,
        },
      },
    });

    assert.deepEqual(provider.makeSuggestionItem(group2), {
      name: 'Other name',
      value: {
        group: {
          name: 'Other name',
          id: 3,
        },
      },
    });
  });
});

