
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


<meta charset="utf-8">







<test-fixture id="basic">
  <template>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
  </template>
</test-fixture>


import '../../test/common-test-setup.js';
import '../../elements/shared/gr-rest-api-interface/gr-rest-api-interface.js';
import {GrGroupSuggestionsProvider} from './gr-group-suggestions-provider.js';

suite('GrGroupSuggestionsProvider tests', () => {
  let sandbox;
  let restAPI;
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
    sandbox = sinon.sandbox.create();

    stub('gr-rest-api-interface', {
      getConfig() { return Promise.resolve({}); },
    });
    restAPI = fixture('basic');
    provider = new GrGroupSuggestionsProvider(restAPI);
  });

  teardown(() => {
    sandbox.restore();
  });

  test('getSuggestions', done => {
    const getSuggestedAccountsStub =
        sandbox.stub(restAPI, 'getSuggestedGroups')
            .returns(Promise.resolve({
              'Some name': {id: 1},
              'Other name': {id: 3, url: 'abcd'},
            }));

    provider.getSuggestions('Some input').then(res => {
      assert.deepEqual(res, [group1, group2]);
      assert.isTrue(getSuggestedAccountsStub.calledOnce);
      assert.equal(getSuggestedAccountsStub.lastCall.args[0], 'Some input');
      done();
    });
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

