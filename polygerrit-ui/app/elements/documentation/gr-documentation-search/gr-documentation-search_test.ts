/**
 * @license
 * Copyright (C) 2018 The Android Open Source Project
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
import './gr-documentation-search';
import {GrDocumentationSearch} from './gr-documentation-search';
import {page} from '../../../utils/page-wrapper-utils';
import {queryAndAssert, stubRestApi} from '../../../test/test-utils';
import {DocResult} from '../../../types/common';

const basicFixture = fixtureFromElement('gr-documentation-search');

function documentationGenerator(counter: number) {
  return {
    title: `Gerrit Code Review - REST API Developers Notes${counter}`,
    url: 'Documentation/dev-rest-api.html',
  };
}

function createDocumentationList(n: number) {
  const list = [];
  for (let i = 0; i < n; ++i) {
    list.push(documentationGenerator(i));
  }
  return list;
}

suite('gr-documentation-search tests', () => {
  let element: GrDocumentationSearch;
  let documentationSearches: DocResult[];

  setup(async () => {
    sinon.stub(page, 'show');
    element = basicFixture.instantiate();
    await element.updateComplete;
  });

  suite('list with searches for documentation', () => {
    setup(async () => {
      documentationSearches = createDocumentationList(26);
      stubRestApi('getDocumentationSearches').returns(
        Promise.resolve(documentationSearches)
      );
      await element.paramsChanged();
      await element.updateComplete;
    });

    test('test for test repo in the list', async () => {
      assert.equal(
        element.documentationSearches![1].title,
        'Gerrit Code Review - REST API Developers Notes1'
      );
      assert.equal(
        element.documentationSearches![1].url,
        'Documentation/dev-rest-api.html'
      );
    });
  });

  suite('filter', () => {
    setup(() => {
      documentationSearches = createDocumentationList(25);
    });

    test('paramsChanged', async () => {
      const stub = stubRestApi('getDocumentationSearches').returns(
        Promise.resolve(documentationSearches)
      );
      element.params = {filter: 'test'};
      await element.paramsChanged();
      assert.isTrue(stub.lastCall.calledWithExactly('test'));
    });
  });

  suite('loading', () => {
    test('correct contents are displayed', async () => {
      assert.isTrue(element.loading);
      assert.equal(
        getComputedStyle(queryAndAssert(element, '#loading')).display,
        'block'
      );

      element.loading = false;

      await element.updateComplete;
      assert.equal(
        getComputedStyle(queryAndAssert(element, '#loading')).display,
        'none'
      );
    });
  });
});
