/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
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
import './gr-repo-list.js';
import {page} from '../../../utils/page-wrapper-utils.js';
import 'lodash/lodash.js';
import {stubRestApi} from '../../../test/test-utils.js';

const basicFixture = fixtureFromElement('gr-repo-list');

let counter;
const repoGenerator = () => {
  return {
    id: `test${++counter}`,
    state: 'ACTIVE',
    web_links: [
      {
        name: 'diffusion',
        url: `https://phabricator.example.org/r/project/test${counter}`,
      },
    ],
  };
};

suite('gr-repo-list tests', () => {
  let element;
  let repos;

  let value;

  setup(() => {
    sinon.stub(page, 'show');
    element = basicFixture.instantiate();
    counter = 0;
  });

  suite('list with repos', () => {
    setup(done => {
      repos = _.times(26, repoGenerator);
      stubRestApi('getRepos').returns(Promise.resolve(repos));
      element._paramsChanged(value).then(() => { flush(done); });
    });

    test('test for test repo in the list', done => {
      flush(() => {
        assert.equal(element._repos[1].id, 'test2');
        done();
      });
    });

    test('_shownRepos', () => {
      assert.equal(element._shownRepos.length, 25);
    });

    test('_maybeOpenCreateOverlay', () => {
      const overlayOpen = sinon.stub(element.$.createOverlay, 'open');
      element._maybeOpenCreateOverlay();
      assert.isFalse(overlayOpen.called);
      const params = {};
      element._maybeOpenCreateOverlay(params);
      assert.isFalse(overlayOpen.called);
      params.openCreateModal = true;
      element._maybeOpenCreateOverlay(params);
      assert.isTrue(overlayOpen.called);
    });
  });

  suite('list with less then 25 repos', () => {
    setup(done => {
      repos = _.times(25, repoGenerator);
      stubRestApi('getRepos').returns(Promise.resolve(repos));
      element._paramsChanged(value).then(() => { flush(done); });
    });

    test('_shownRepos', () => {
      assert.equal(element._shownRepos.length, 25);
    });
  });

  suite('filter', () => {
    let reposFiltered;
    setup(() => {
      repos = _.times(25, repoGenerator);
      reposFiltered = _.times(1, repoGenerator);
    });

    test('_paramsChanged', async () => {
      const repoStub = stubRestApi('getRepos');
      repoStub.returns(Promise.resolve(repos));
      const value = {
        filter: 'test',
        offset: 25,
      };
      await element._paramsChanged(value);
      assert.isTrue(repoStub.lastCall.calledWithExactly('test', 25, 25));
    });

    test('latest repos requested are always set', done => {
      const repoStub = stubRestApi('getRepos');
      repoStub.withArgs('test').returns(Promise.resolve(repos));
      repoStub.withArgs('filter').returns(Promise.resolve(reposFiltered));
      element._filter = 'test';

      // Repos are not set because the element._filter differs.
      element._getRepos('filter', 25, 0).then(() => {
        assert.deepEqual(element._repos, []);
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
      element._repos = _.times(25, repoGenerator);

      flush();
      assert.equal(element.computeLoadingClass(element._loading), '');
      assert.equal(getComputedStyle(element.$.loading).display, 'none');
    });
  });

  suite('create new', () => {
    test('_handleCreateClicked called when create-click fired', () => {
      sinon.stub(element, '_handleCreateClicked');
      element.shadowRoot
          .querySelector('gr-list-view').dispatchEvent(
              new CustomEvent('create-clicked', {
                composed: true, bubbles: true,
              }));
      assert.isTrue(element._handleCreateClicked.called);
    });

    test('_handleCreateClicked opens modal', () => {
      const openStub = sinon.stub(element.$.createOverlay, 'open').returns(
          Promise.resolve());
      element._handleCreateClicked();
      assert.isTrue(openStub.called);
    });

    test('_handleCreateRepo called when confirm fired', () => {
      sinon.stub(element, '_handleCreateRepo');
      element.$.createDialog.dispatchEvent(
          new CustomEvent('confirm', {
            composed: true, bubbles: true,
          }));
      assert.isTrue(element._handleCreateRepo.called);
    });

    test('_handleCloseCreate called when cancel fired', () => {
      sinon.stub(element, '_handleCloseCreate');
      element.$.createDialog.dispatchEvent(
          new CustomEvent('cancel', {
            composed: true, bubbles: true,
          }));
      assert.isTrue(element._handleCloseCreate.called);
    });
  });
});

