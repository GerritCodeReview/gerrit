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

import '../../test/common-test-setup-karma';
import {GrGroupSuggestionsProvider} from './gr-group-suggestions-provider';
import {getAppContext} from '../../services/app-context';
import {stubRestApi} from '../../test/test-utils';
import {GroupId, GroupName} from '../../types/common';

suite('GrGroupSuggestionsProvider tests', () => {
  let provider: GrGroupSuggestionsProvider;
  const group1 = {
    name: 'Some name' as GroupName,
    id: '1' as GroupId,
  };
  const group2 = {
    name: 'Other name' as GroupName,
    id: '3' as GroupId,
    url: 'abcd',
  };

  setup(() => {
    provider = new GrGroupSuggestionsProvider(getAppContext().restApiService);
  });

  test('getSuggestions', async () => {
    const getSuggestedAccountsStub = stubRestApi('getSuggestedGroups').returns(
      Promise.resolve({
        'Some name': {id: '1' as GroupId},
        'Other name': {id: '3' as GroupId, url: 'abcd'},
      })
    );

    const res = await provider.getSuggestions('Some input');
    assert.deepEqual(res, [group1, group2]);
    assert.isTrue(getSuggestedAccountsStub.calledOnce);
    assert.equal(getSuggestedAccountsStub.lastCall.args[0], 'Some input');
  });

  test('makeSuggestionItem', () => {
    assert.deepEqual(provider.makeSuggestionItem(group1), {
      name: 'Some name' as GroupName,
      value: {
        group: {
          name: 'Some name' as GroupName,
          id: '1' as GroupId,
        },
      },
    });

    assert.deepEqual(provider.makeSuggestionItem(group2), {
      name: 'Other name' as GroupName,
      value: {
        group: {
          name: 'Other name' as GroupName,
          id: '3' as GroupId,
        },
      },
    });
  });
});
