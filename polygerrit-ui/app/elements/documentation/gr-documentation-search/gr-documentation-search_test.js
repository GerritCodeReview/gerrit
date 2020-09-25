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

import '../../../test/common-test-setup-karma.js';
import './gr-documentation-search.js';
import {page} from '../../../utils/page-wrapper-utils.js';

const basicFixture = fixtureFromElement('gr-documentation-search');

let counter;
const documentationGenerator = () => {
  return {
    title: `Gerrit Code Review - REST API Developers Notes${++counter}`,
    url: 'Documentation/dev-rest-api.html',
  };
};

suite('gr-documentation-search tests', () => {
  let element;
  let documentationSearches;

  let value;

  setup(() => {
    sinon.stub(page, 'show');
    element = basicFixture.instantiate();
    counter = 0;
  });

  suite('list with searches for documentation', () => {
    setup(done => {
      documentationSearches = _.times(26, documentationGenerator);
      stub('gr-rest-api-interface', {
        getDocumentationSearches() {
          return Promise.resolve(documentationSearches);
        },
      });
      element._paramsChanged(value).then(() => { flush(done); });
    });

    test('test for test repo in the list', done => {
      flush(() => {
        assert.equal(element._documentationSearches[0].title,
            'Gerrit Code Review - REST API Developers Notes1');
        assert.equal(element._documentationSearches[0].url,
            'Documentation/dev-rest-api.html');
        done();
      });
    });
  });

  suite('filter', () => {
    setup(() => {
      documentationSearches = _.times(25, documentationGenerator);
      _.times(1, documentationSearches);
    });

    test('_paramsChanged', done => {
      sinon.stub(
          element.$.restAPI,
          'getDocumentationSearches')
          .callsFake(() => Promise.resolve(documentationSearches));
      const value = {
        filter: 'test',
      };
      element._paramsChanged(value).then(() => {
        assert.isTrue(element.$.restAPI.getDocumentationSearches.lastCall
            .calledWithExactly('test'));
        done();
      });
    });
  });

  suite('loading', () => {
    test('correct contents are displayed', () => {
      assert.isTrue(element._loading);
      assert.equal(element.computeLoadingClass(element._loading), 'loading');
      assert.equal(getComputedStyle(element.$.loading).display, 'block');

      element._loading = false;
      element._repos = _.times(25, documentationGenerator);

      flush();
      assert.equal(element.computeLoadingClass(element._loading), '');
      assert.equal(getComputedStyle(element.$.loading).display, 'none');
    });
  });
});

