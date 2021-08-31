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
import './gr-repo-detail-list.js';
import 'lodash/lodash.js';
import {page} from '../../../utils/page-wrapper-utils.js';
import {dom} from '@polymer/polymer/lib/legacy/polymer.dom.js';
import {
  addListenerForTest,
  mockPromise,
  stubRestApi,
} from '../../../test/test-utils.js';
import {RepoDetailView} from '../../core/gr-navigation/gr-navigation.js';

const basicFixture = fixtureFromElement('gr-repo-detail-list');

let counter;
const branchGenerator = () => {
  return {
    ref: `refs/heads/test${++counter}`,
    revision: '9c9d08a438e55e52f33b608415e6dddd9b18550d',
    web_links: [
      {
        name: 'diffusion',
        url: `https://git.example.org/branch/test;refs/heads/test${counter}`,
      },
    ],
  };
};
const tagGenerator = () => {
  return {
    ref: `refs/tags/test${++counter}`,
    revision: '9c9d08a438e55e52f33b608415e6dddd9b18550d',
    web_links: [
      {
        name: 'diffusion',
        url: `https://git.example.org/tag/test;refs/tags/test${counter}`,
      },
    ],
    message: 'Annotated tag',
    tagger: {
      name: 'Test User',
      email: 'test.user@gmail.com',
      date: '2017-09-19 14:54:00.000000000',
      tz: 540,
    },
  };
};

suite('gr-repo-detail-list', () => {
  suite('Branches', () => {
    let element;
    let branches;

    setup(() => {
      element = basicFixture.instantiate();
      element.detailType = 'branches';
      counter = 0;
      sinon.stub(page, 'show');
    });

    suite('list of repo branches', () => {
      setup(async () => {
        branches = [{
          ref: 'HEAD',
          revision: 'master',
        }].concat(_.times(25, branchGenerator));
        stubRestApi('getRepoBranches').returns(Promise.resolve(branches));

        const params = {
          repo: 'test',
          detail: 'branches',
        };
        await element._paramsChanged(params);
        await flush();
      });

      test('test for branch in the list', () => {
        assert.equal(element._items[2].ref, 'refs/heads/test2');
      });

      test('test for web links in the branches list', () => {
        assert.equal(element._items[2].web_links[0].url,
            'https://git.example.org/branch/test;refs/heads/test2');
      });

      test('test for refs/heads/ being striped from ref', () => {
        assert.equal(element._stripRefs(element._items[2].ref,
            element.detailType), 'test2');
      });

      test('_shownItems', () => {
        assert.equal(element._shownItems.length, 25);
      });

      test('Edit HEAD button not admin', async () => {
        sinon.stub(element, '_getLoggedIn').returns(Promise.resolve(true));
        stubRestApi('getRepoAccess').returns(
            Promise.resolve({
              test: {is_owner: false},
            }));
        await element._determineIfOwner('test');
        assert.equal(element._isOwner, false);
        assert.equal(getComputedStyle(dom(element.root)
            .querySelector('.revisionNoEditing')).display, 'inline');
        assert.equal(getComputedStyle(dom(element.root)
            .querySelector('.revisionEdit')).display, 'none');
      });

      test('Edit HEAD button admin', async () => {
        const saveBtn = element.root.querySelector('.saveBtn');
        const cancelBtn = element.root.querySelector('.cancelBtn');
        const editBtn = element.root.querySelector('.editBtn');
        const revisionNoEditing = dom(element.root)
            .querySelector('.revisionNoEditing');
        const revisionWithEditing = dom(element.root)
            .querySelector('.revisionWithEditing');

        sinon.stub(element, '_getLoggedIn').returns(Promise.resolve(true));
        stubRestApi('getRepoAccess').returns(
            Promise.resolve({
              test: {is_owner: true},
            }));
        sinon.stub(element, '_handleSaveRevision');
        await element._determineIfOwner('test');
        assert.equal(element._isOwner, true);
        // The revision container for non-editing enabled row is not visible.
        assert.equal(getComputedStyle(revisionNoEditing).display, 'none');

        // The revision container for editing enabled row is visible.
        assert.notEqual(getComputedStyle(dom(element.root)
            .querySelector('.revisionEdit')).display, 'none');

        // The revision and edit button are visible.
        assert.notEqual(getComputedStyle(revisionWithEditing).display,
            'none');
        assert.notEqual(getComputedStyle(editBtn).display, 'none');

        // The input, cancel, and save buttons are not visible.
        const hiddenElements = dom(element.root)
            .querySelectorAll('.canEdit .editItem');

        for (const item of hiddenElements) {
          assert.equal(getComputedStyle(item).display, 'none');
        }

        MockInteractions.tap(editBtn);
        await flush();
        // The revision and edit button are not visible.
        assert.equal(getComputedStyle(revisionWithEditing).display, 'none');
        assert.equal(getComputedStyle(editBtn).display, 'none');

        // The input, cancel, and save buttons are not visible.
        for (const item of hiddenElements) {
          assert.notEqual(getComputedStyle(item).display, 'none');
        }

        // The revised ref was set correctly
        assert.equal(element._revisedRef, 'master');

        assert.isFalse(saveBtn.disabled);

        // Delete the ref.
        element._revisedRef = '';
        assert.isTrue(saveBtn.disabled);

        // Change the ref to something else
        element._revisedRef = 'newRef';
        element._repo = 'test';
        assert.isFalse(saveBtn.disabled);

        // Save button calls handleSave. since this is stubbed, the edit
        // section remains open.
        MockInteractions.tap(saveBtn);
        assert.isTrue(element._handleSaveRevision.called);

        // When cancel is tapped, the edit secion closes.
        MockInteractions.tap(cancelBtn);
        await flush();

        // The revision and edit button are visible.
        assert.notEqual(getComputedStyle(revisionWithEditing).display,
            'none');
        assert.notEqual(getComputedStyle(editBtn).display, 'none');

        // The input, cancel, and save buttons are not visible.
        for (const item of hiddenElements) {
          assert.equal(getComputedStyle(item).display, 'none');
        }
      });

      test('_handleSaveRevision with invalid rev', async () => {
        const event = {model: {set: sinon.stub()}};
        element._isEditing = true;
        stubRestApi('setRepoHead').returns(
            Promise.resolve({
              status: 400,
            })
        );

        await element._setRepoHead('test', 'newRef', event);
        assert.isTrue(element._isEditing);
        assert.isFalse(event.model.set.called);
      });

      test('_handleSaveRevision with valid rev', async () => {
        const event = {model: {set: sinon.stub()}};
        element._isEditing = true;
        stubRestApi('setRepoHead').returns(
            Promise.resolve({
              status: 200,
            })
        );

        await element._setRepoHead('test', 'newRef', event);
        assert.isFalse(element._isEditing);
        assert.isTrue(event.model.set.called);
      });

      test('test _computeItemName', () => {
        assert.deepEqual(element._computeItemName('branches'), 'Branch');
        assert.deepEqual(element._computeItemName('tags'), 'Tag');
      });
    });

    suite('list with less then 25 branches', () => {
      setup(async () => {
        branches = _.times(25, branchGenerator);
        stubRestApi('getRepoBranches').returns(Promise.resolve(branches));

        const params = {
          repo: 'test',
          detail: 'branches',
        };

        await element._paramsChanged(params);
        await flush();
      });

      test('_shownItems', () => {
        assert.equal(element._shownItems.length, 25);
      });
    });

    suite('filter', () => {
      test('_paramsChanged', async () => {
        const stub = stubRestApi('getRepoBranches').returns(
            Promise.resolve(branches));
        const params = {
          detail: 'branches',
          repo: 'test',
          filter: 'test',
          offset: 25,
        };
        await element._paramsChanged(params);
        assert.equal(stub.lastCall.args[0], 'test');
        assert.equal(stub.lastCall.args[1], 'test');
        assert.equal(stub.lastCall.args[2], 25);
        assert.equal(stub.lastCall.args[3], 25);
      });
    });

    suite('404', () => {
      test('fires page-error', async () => {
        const response = {status: 404};
        stubRestApi('getRepoBranches').callsFake(
            (filter, repo, reposBranchesPerPage, opt_offset, errFn) => {
              errFn(response);
              return Promise.resolve();
            });

        const promise = mockPromise();
        addListenerForTest(document, 'page-error', e => {
          assert.deepEqual(e.detail.response, response);
          promise.resolve();
        });

        const params = {
          detail: 'branches',
          repo: 'test',
          filter: 'test',
          offset: 25,
        };
        element._paramsChanged(params);
        await promise;
      });
    });
  });

  suite('Tags', () => {
    let element;
    let tags;

    setup(() => {
      element = basicFixture.instantiate();
      element.detailType = 'tags';
      counter = 0;
      sinon.stub(page, 'show');
    });

    test('_computeMessage', () => {
      let message = 'v2.15-rc1↵-----BEGIN PGP SIGNATURE-----↵Version: GnuPG v' +
      '1↵↵iQIcBAABAgAGBQJZ27O7AAoJEF/XxZqaEoiMy6kQAMoQCpGr3J6JITI4BVWsr7QM↵xy' +
      'EcWH5YPUko5EPTbkABHmaVyFmKGkuIQdn6c+NIbqJOk+5XT4oUyRSo1T569HPJ↵3kyxEJi' +
      'T1ryvp5BIHwdvHx58fjw1+YkiWLZuZq1FFkUYqnWTYCrkv7Fok98pdOmV↵CL1Hgugi5uK8' +
      '/kxf1M7+Nv6piaZ140pwSb1h6QdAjaZVfaBCnoxlG4LRUqHvEYay↵f4QYgFT67auHIGkZ4' +
      'moUcsp2Du/1jSsCWL/CPwjPFGbbckVAjLCMT9yD3NKwpEZF↵pfsiZyHI9dL0M+QjVrM+RD' +
      'HwIIJwra8R0IMkDlQ6MDrFlKNqNBbo588S6UPrm71L↵YuiwWlcrK9ZIybxT6LzbR65Rvez' +
      'DSitQ+xeIfpZE19/X6BCnvlARLE8k/tC2JksI↵lEZi7Lf3FQdIcwwyt98tJkS9HX9v9jbC' +
      '5QXifnoj3Li8tHSLuQ1dJCxHQiis6ojI↵OWUFkm0IHBXVNHA2dqYBdM+pL12mlI3wp6Ica' +
      '4cdEVDwzu+j1xnVSFUa+d+Y2xJF↵7mytuyhHiKG4hm+zbhMv6WD8Q3FoDsJZeLY99l0hYQ' +
      'SnnkMduFVroIs45pAs8gUA↵RvYla8mm9w/543IJAPzzFarPVLSsSyQ7tJl3UBzjKRNH/rX' +
      'W+F22qyWD1zyHPUIR↵C00ItmwlAvveImYKpQAH↵=L+K9↵-----END PGP SIGNATURE---' +
      '--';
      assert.equal(element._computeMessage(message), 'v2.15-rc1↵');
      message = 'v2.15-rc1';
      assert.equal(element._computeMessage(message), 'v2.15-rc1');
    });

    suite('list of repo tags', () => {
      setup(async () => {
        tags = _.times(26, tagGenerator);
        stubRestApi('getRepoTags').returns(Promise.resolve(tags));

        const params = {
          repo: 'test',
          detail: 'tags',
        };

        await element._paramsChanged(params);
        await flush();
      });

      test('test for tag in the list', async () => {
        assert.equal(element._items[1].ref, 'refs/tags/test2');
      });

      test('test for tag message in the list', async () => {
        assert.equal(element._items[1].message, 'Annotated tag');
      });

      test('test for tagger in the tag list', async () => {
        const tagger = {
          name: 'Test User',
          email: 'test.user@gmail.com',
          date: '2017-09-19 14:54:00.000000000',
          tz: 540,
        };

        assert.deepEqual(element._items[1].tagger, tagger);
      });

      test('test for web links in the tags list', async () => {
        assert.equal(element._items[1].web_links[0].url,
            'https://git.example.org/tag/test;refs/tags/test2');
      });

      test('test for refs/tags/ being striped from ref', async () => {
        assert.equal(element._stripRefs(element._items[1].ref,
            element.detailType), 'test2');
      });

      test('_shownItems', () => {
        assert.equal(element._shownItems.length, 25);
      });

      test('_computeHideTagger', () => {
        const testObject1 = {
          tagger: 'test',
        };
        assert.equal(element._computeHideTagger(testObject1), '');

        assert.equal(element._computeHideTagger(undefined), 'hide');
      });
    });

    suite('list with less then 25 tags', () => {
      setup(async () => {
        tags = _.times(25, tagGenerator);
        stubRestApi('getRepoTags').returns(Promise.resolve(tags));

        const params = {
          repo: 'test',
          detail: 'tags',
        };

        await element._paramsChanged(params);
        await flush();
      });

      test('_shownItems', () => {
        assert.equal(element._shownItems.length, 25);
      });
    });

    suite('filter', () => {
      test('_paramsChanged', async () => {
        const stub = stubRestApi('getRepoTags').returns(Promise.resolve(tags));
        const params = {
          repo: 'test',
          detail: 'tags',
          filter: 'test',
          offset: 25,
        };
        await element._paramsChanged(params);
        assert.equal(stub.lastCall.args[0], 'test');
        assert.equal(stub.lastCall.args[1], 'test');
        assert.equal(stub.lastCall.args[2], 25);
        assert.equal(stub.lastCall.args[3], 25);
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
        const openStub = sinon.stub(element.$.createOverlay, 'open');
        element._handleCreateClicked();
        assert.isTrue(openStub.called);
      });

      test('_handleCreateItem called when confirm fired', () => {
        sinon.stub(element, '_handleCreateItem');
        element.$.createDialog.dispatchEvent(
            new CustomEvent('confirm', {
              composed: true, bubbles: true,
            }));
        assert.isTrue(element._handleCreateItem.called);
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

    suite('404', () => {
      test('fires page-error', async () => {
        const response = {status: 404};
        stubRestApi('getRepoTags').callsFake(
            (filter, repo, reposTagsPerPage, opt_offset, errFn) => {
              errFn(response);
              return Promise.resolve();
            });

        const promise = mockPromise();
        addListenerForTest(document, 'page-error', e => {
          assert.deepEqual(e.detail.response, response);
          promise.resolve();
        });

        const params = {
          repo: 'test',
          detail: 'tags',
          filter: 'test',
          offset: 25,
        };
        element._paramsChanged(params);
        await promise;
      });
    });

    test('test _computeHideDeleteClass', () => {
      assert.deepEqual(element._computeHideDeleteClass(true, false), 'show');
      assert.deepEqual(element._computeHideDeleteClass(false, true), 'show');
      assert.deepEqual(element._computeHideDeleteClass(false, false), '');
    });

    test('_computeItemName', () => {
      assert.equal(element._computeItemName(RepoDetailView.BRANCHES), 'Branch');
      assert.equal(element._computeItemName(RepoDetailView.TAGS),
          'Tag');
    });
  });
});

