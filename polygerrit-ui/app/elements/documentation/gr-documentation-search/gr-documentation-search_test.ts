/**
 * @license
 * Copyright 2018 Google LLC
 * SPDX-License-Identifier: Apache-2.0
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

    test('render', () => {
      expect(element).shadowDom.to.equal(/* HTML */ `
        <gr-list-view>
          <table class="genericList" id="list">
            <tbody>
              <tr class="headerRow">
                <th class="name topHeader">Name</th>
                <th class="name topHeader"></th>
                <th class="name topHeader"></th>
              </tr>
              <tr class="loadingMsg" id="loading">
                <td>Loading...</td>
              </tr>
            </tbody>
            <tbody>
              <tr class="table">
                <td class="name">
                  <a href="/Documentation/dev-rest-api.html">
                    Gerrit Code Review - REST API Developers Notes0
                  </a>
                </td>
                <td></td>
                <td></td>
              </tr>
              <tr class="table">
                <td class="name">
                  <a href="/Documentation/dev-rest-api.html">
                    Gerrit Code Review - REST API Developers Notes1
                  </a>
                </td>
                <td></td>
                <td></td>
              </tr>
              <tr class="table">
                <td class="name">
                  <a href="/Documentation/dev-rest-api.html">
                    Gerrit Code Review - REST API Developers Notes2
                  </a>
                </td>
                <td></td>
                <td></td>
              </tr>
              <tr class="table">
                <td class="name">
                  <a href="/Documentation/dev-rest-api.html">
                    Gerrit Code Review - REST API Developers Notes3
                  </a>
                </td>
                <td></td>
                <td></td>
              </tr>
              <tr class="table">
                <td class="name">
                  <a href="/Documentation/dev-rest-api.html">
                    Gerrit Code Review - REST API Developers Notes4
                  </a>
                </td>
                <td></td>
                <td></td>
              </tr>
              <tr class="table">
                <td class="name">
                  <a href="/Documentation/dev-rest-api.html">
                    Gerrit Code Review - REST API Developers Notes5
                  </a>
                </td>
                <td></td>
                <td></td>
              </tr>
              <tr class="table">
                <td class="name">
                  <a href="/Documentation/dev-rest-api.html">
                    Gerrit Code Review - REST API Developers Notes6
                  </a>
                </td>
                <td></td>
                <td></td>
              </tr>
              <tr class="table">
                <td class="name">
                  <a href="/Documentation/dev-rest-api.html">
                    Gerrit Code Review - REST API Developers Notes7
                  </a>
                </td>
                <td></td>
                <td></td>
              </tr>
              <tr class="table">
                <td class="name">
                  <a href="/Documentation/dev-rest-api.html">
                    Gerrit Code Review - REST API Developers Notes8
                  </a>
                </td>
                <td></td>
                <td></td>
              </tr>
              <tr class="table">
                <td class="name">
                  <a href="/Documentation/dev-rest-api.html">
                    Gerrit Code Review - REST API Developers Notes9
                  </a>
                </td>
                <td></td>
                <td></td>
              </tr>
              <tr class="table">
                <td class="name">
                  <a href="/Documentation/dev-rest-api.html">
                    Gerrit Code Review - REST API Developers Notes10
                  </a>
                </td>
                <td></td>
                <td></td>
              </tr>
              <tr class="table">
                <td class="name">
                  <a href="/Documentation/dev-rest-api.html">
                    Gerrit Code Review - REST API Developers Notes11
                  </a>
                </td>
                <td></td>
                <td></td>
              </tr>
              <tr class="table">
                <td class="name">
                  <a href="/Documentation/dev-rest-api.html">
                    Gerrit Code Review - REST API Developers Notes12
                  </a>
                </td>
                <td></td>
                <td></td>
              </tr>
              <tr class="table">
                <td class="name">
                  <a href="/Documentation/dev-rest-api.html">
                    Gerrit Code Review - REST API Developers Notes13
                  </a>
                </td>
                <td></td>
                <td></td>
              </tr>
              <tr class="table">
                <td class="name">
                  <a href="/Documentation/dev-rest-api.html">
                    Gerrit Code Review - REST API Developers Notes14
                  </a>
                </td>
                <td></td>
                <td></td>
              </tr>
              <tr class="table">
                <td class="name">
                  <a href="/Documentation/dev-rest-api.html">
                    Gerrit Code Review - REST API Developers Notes15
                  </a>
                </td>
                <td></td>
                <td></td>
              </tr>
              <tr class="table">
                <td class="name">
                  <a href="/Documentation/dev-rest-api.html">
                    Gerrit Code Review - REST API Developers Notes16
                  </a>
                </td>
                <td></td>
                <td></td>
              </tr>
              <tr class="table">
                <td class="name">
                  <a href="/Documentation/dev-rest-api.html">
                    Gerrit Code Review - REST API Developers Notes17
                  </a>
                </td>
                <td></td>
                <td></td>
              </tr>
              <tr class="table">
                <td class="name">
                  <a href="/Documentation/dev-rest-api.html">
                    Gerrit Code Review - REST API Developers Notes18
                  </a>
                </td>
                <td></td>
                <td></td>
              </tr>
              <tr class="table">
                <td class="name">
                  <a href="/Documentation/dev-rest-api.html">
                    Gerrit Code Review - REST API Developers Notes19
                  </a>
                </td>
                <td></td>
                <td></td>
              </tr>
              <tr class="table">
                <td class="name">
                  <a href="/Documentation/dev-rest-api.html">
                    Gerrit Code Review - REST API Developers Notes20
                  </a>
                </td>
                <td></td>
                <td></td>
              </tr>
              <tr class="table">
                <td class="name">
                  <a href="/Documentation/dev-rest-api.html">
                    Gerrit Code Review - REST API Developers Notes21
                  </a>
                </td>
                <td></td>
                <td></td>
              </tr>
              <tr class="table">
                <td class="name">
                  <a href="/Documentation/dev-rest-api.html">
                    Gerrit Code Review - REST API Developers Notes22
                  </a>
                </td>
                <td></td>
                <td></td>
              </tr>
              <tr class="table">
                <td class="name">
                  <a href="/Documentation/dev-rest-api.html">
                    Gerrit Code Review - REST API Developers Notes23
                  </a>
                </td>
                <td></td>
                <td></td>
              </tr>
              <tr class="table">
                <td class="name">
                  <a href="/Documentation/dev-rest-api.html">
                    Gerrit Code Review - REST API Developers Notes24
                  </a>
                </td>
                <td></td>
                <td></td>
              </tr>
              <tr class="table">
                <td class="name">
                  <a href="/Documentation/dev-rest-api.html">
                    Gerrit Code Review - REST API Developers Notes25
                  </a>
                </td>
                <td></td>
                <td></td>
              </tr>
            </tbody>
          </table>
        </gr-list-view>
      `);
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
