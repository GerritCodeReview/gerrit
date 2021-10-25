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
import 'lodash/lodash';
import {queryAndAssert, stubRestApi} from '../../../test/test-utils';
import {DocResult} from '../../../types/common';
import {ListViewParams} from '../../gr-app-types';

const basicFixture = fixtureFromElement('gr-documentation-search');

let counter: number;
const documentationGenerator = () => {
  return {
    title: `Gerrit Code Review - REST API Developers Notes${++counter}`,
    url: 'Documentation/dev-rest-api.html',
  };
};

suite('gr-documentation-search tests', () => {
  let element: GrDocumentationSearch;
  let documentationSearches: DocResult[];

  let value: ListViewParams;

  setup(async () => {
    sinon.stub(page, 'show');
    element = basicFixture.instantiate();
    counter = 0;
    await flush();
  });

  suite('list with searches for documentation', () => {
    setup(async () => {
      documentationSearches = _.times(26, documentationGenerator);
      stubRestApi('getDocumentationSearches').returns(
        Promise.resolve(documentationSearches)
      );
      await element._paramsChanged(value);
      await flush();
    });

    test('test for test repo in the list', async () => {
      assert.equal(
        element._documentationSearches![0].title,
        'Gerrit Code Review - REST API Developers Notes1'
      );
      assert.equal(
        element._documentationSearches![0].url,
        'Documentation/dev-rest-api.html'
      );
    });
  });

  suite('filter', () => {
    setup(() => {
      documentationSearches = _.times(25, documentationGenerator);
    });

    test('_paramsChanged', async () => {
      const stub = stubRestApi('getDocumentationSearches').returns(
        Promise.resolve(documentationSearches)
      );
      const value = {filter: 'test'};
      await element._paramsChanged(value);
      assert.isTrue(stub.lastCall.calledWithExactly('test'));
    });
  });

  suite('loading', () => {
    test('correct contents are displayed', async () => {
      assert.isTrue(element._loading);
      assert.equal(element.computeLoadingClass(element._loading), 'loading');
      assert.equal(
        getComputedStyle(queryAndAssert(element, '#loading')).display,
        'block'
      );

      element._loading = false;

      await flush();
      assert.equal(element.computeLoadingClass(element._loading), '');
      assert.equal(
        getComputedStyle(queryAndAssert(element, '#loading')).display,
        'none'
      );
    });
  });
});
