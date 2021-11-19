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

import '../../../test/common-test-setup-karma';
import './gr-repo-detail-list.js';
import {GrRepoDetailList} from './gr-repo-detail-list';
import {page} from '../../../utils/page-wrapper-utils';
import {
  addListenerForTest,
  mockPromise,
  queryAll,
  queryAndAssert,
  stubRestApi,
} from '../../../test/test-utils';
import {RepoDetailView} from '../../core/gr-navigation/gr-navigation';
import {
  BranchInfo,
  EmailAddress,
  GitRef,
  GroupId,
  GroupName,
  ProjectAccessGroups,
  ProjectAccessInfoMap,
  RepoName,
  TagInfo,
  Timestamp,
  TimezoneOffset,
} from '../../../types/common';
import {GerritView} from '../../../services/router/router-model';
import {GrButton} from '../../shared/gr-button/gr-button';
import * as MockInteractions from '@polymer/iron-test-helpers/mock-interactions';
import {PageErrorEvent} from '../../../types/events';
import {GrOverlay} from '../../shared/gr-overlay/gr-overlay';
import {GrDialog} from '../../shared/gr-dialog/gr-dialog';
import {GrListView} from '../../shared/gr-list-view/gr-list-view';
import {SHOWN_ITEMS_COUNT} from '../../../constants/constants';

const basicFixture = fixtureFromElement('gr-repo-detail-list');

function branchGenerator(counter: number) {
  return {
    ref: `refs/heads/test${counter}` as GitRef,
    revision: '9c9d08a438e55e52f33b608415e6dddd9b18550d',
    web_links: [
      {
        name: 'diffusion',
        url: `https://git.example.org/branch/test;refs/heads/test${counter}`,
      },
    ],
  };
}

function createBranchesList(n: number) {
  const branches = [];
  for (let i = 0; i < n; ++i) {
    branches.push(branchGenerator(i));
  }
  return branches;
}

function tagGenerator(counter: number) {
  return {
    ref: `refs/tags/test${counter}` as GitRef,
    revision: '9c9d08a438e55e52f33b608415e6dddd9b18550d',
    can_delete: false,
    web_links: [
      {
        name: 'diffusion',
        url: `https://git.example.org/tag/test;refs/tags/test${counter}`,
      },
    ],
    message: 'Annotated tag',
    tagger: {
      name: 'Test User',
      email: 'test.user@gmail.com' as EmailAddress,
      date: '2017-09-19 14:54:00.000000000' as Timestamp,
      tz: 540 as TimezoneOffset,
    },
  };
}

function createTagsList(n: number) {
  const tags = [];
  for (let i = 0; i < n; ++i) {
    tags.push(tagGenerator(i));
  }
  return tags;
}

suite('gr-repo-detail-list', () => {
  suite('Branches', () => {
    let element: GrRepoDetailList;
    let branches: BranchInfo[];

    setup(async () => {
      element = basicFixture.instantiate();
      await element.updateComplete;
      element.detailType = RepoDetailView.BRANCHES;
      sinon.stub(page, 'show');
    });

    suite('list of repo branches', () => {
      setup(async () => {
        branches = [
          {
            ref: 'HEAD' as GitRef,
            revision: 'master',
          },
        ].concat(createBranchesList(25));
        stubRestApi('getRepoBranches').returns(Promise.resolve(branches));

        element.params = {
          view: GerritView.REPO,
          repo: 'test' as RepoName,
          detail: RepoDetailView.BRANCHES,
        };
        await element.paramsChanged();
        await element.updateComplete;
      });

      test('test for branch in the list', () => {
        assert.equal(element._items![3].ref, 'refs/heads/test2');
      });

      test('test for web links in the branches list', () => {
        assert.equal(
          element._items![3].web_links![0].url,
          'https://git.example.org/branch/test;refs/heads/test2'
        );
      });

      test('test for refs/heads/ being striped from ref', () => {
        assert.equal(
          element._stripRefs(element._items![3].ref, element.detailType),
          'test2'
        );
      });

      test('_items', () => {
        assert.equal(element._items!.slice(0, SHOWN_ITEMS_COUNT)!.length, 25);
      });

      test('Edit HEAD button not admin', async () => {
        sinon.stub(element, '_getLoggedIn').returns(Promise.resolve(true));
        stubRestApi('getRepoAccess').returns(
          Promise.resolve({
            test: {
              revision: 'xxxx',
              local: {
                'refs/*': {
                  permissions: {
                    owner: {rules: {xxx: {action: 'ALLOW', force: false}}},
                  },
                },
              },
              owner_of: ['refs/*'] as GitRef[],
              groups: {
                xxxx: {
                  id: 'xxxx' as GroupId,
                  url: 'test',
                  name: 'test' as GroupName,
                },
              } as ProjectAccessGroups,
              config_web_links: [{name: 'gitiles', url: 'test'}],
            },
          } as ProjectAccessInfoMap)
        );
        await element._determineIfOwner('test' as RepoName);
        assert.equal(element._isOwner, false);
        assert.equal(
          getComputedStyle(
            queryAndAssert<HTMLSpanElement>(element, '.revisionNoEditing')
          ).display,
          'inline'
        );
        assert.equal(
          getComputedStyle(
            queryAndAssert<HTMLSpanElement>(element, '.revisionEdit')
          ).display,
          'none'
        );
      });

      test('Edit HEAD button admin', async () => {
        const saveBtn = queryAndAssert<GrButton>(element, '.saveBtn');
        const cancelBtn = queryAndAssert<GrButton>(element, '.cancelBtn');
        const editBtn = queryAndAssert<GrButton>(element, '.editBtn');
        const revisionNoEditing = queryAndAssert<HTMLSpanElement>(
          element,
          '.revisionNoEditing'
        );
        const revisionWithEditing = queryAndAssert<HTMLSpanElement>(
          element,
          '.revisionWithEditing'
        );

        sinon.stub(element, '_getLoggedIn').returns(Promise.resolve(true));
        stubRestApi('getRepoAccess').returns(
          Promise.resolve({
            test: {
              revision: 'xxxx',
              local: {
                'refs/*': {
                  permissions: {
                    owner: {rules: {xxx: {action: 'ALLOW', force: false}}},
                  },
                },
              },
              is_owner: true,
              owner_of: ['refs/*'] as GitRef[],
              groups: {
                xxxx: {
                  id: 'xxxx' as GroupId,
                  url: 'test',
                  name: 'test' as GroupName,
                },
              } as ProjectAccessGroups,
              config_web_links: [{name: 'gitiles', url: 'test'}],
            },
          } as ProjectAccessInfoMap)
        );
        const handleSaveRevisionStub = sinon.stub(
          element,
          '_handleSaveRevision'
        );
        await element._determineIfOwner('test' as RepoName);
        assert.equal(element._isOwner, true);
        // The revision container for non-editing enabled row is not visible.
        assert.equal(getComputedStyle(revisionNoEditing).display, 'none');

        // The revision container for editing enabled row is visible.
        assert.notEqual(
          getComputedStyle(
            queryAndAssert<HTMLSpanElement>(element, '.revisionEdit')
          ).display,
          'none'
        );

        // The revision and edit button are visible.
        assert.notEqual(getComputedStyle(revisionWithEditing).display, 'none');
        assert.notEqual(getComputedStyle(editBtn).display, 'none');

        // The input, cancel, and save buttons are not visible.
        const hiddenElements = queryAll<HTMLTableElement>(
          element,
          '.canEdit .editItem'
        );

        for (const item of hiddenElements) {
          assert.equal(getComputedStyle(item).display, 'none');
        }

        MockInteractions.tap(editBtn);
        await element.updateComplete;
        // The revision and edit button are not visible.
        assert.equal(getComputedStyle(revisionWithEditing).display, 'none');
        assert.equal(getComputedStyle(editBtn).display, 'none');

        // The input, cancel, and save buttons are not visible.
        for (const item of hiddenElements) {
          assert.notEqual(getComputedStyle(item).display, 'none');
        }

        // The revised ref was set correctly
        assert.equal(element._revisedRef, 'master' as GitRef);

        assert.isFalse(saveBtn.disabled);

        // Delete the ref.
        element._revisedRef = '' as GitRef;
        assert.isTrue(saveBtn.disabled);

        // Change the ref to something else
        element._revisedRef = 'newRef' as GitRef;
        element._repo = 'test' as RepoName;
        assert.isFalse(saveBtn.disabled);

        // Save button calls handleSave. since this is stubbed, the edit
        // section remains open.
        MockInteractions.tap(saveBtn);
        assert.isTrue(handleSaveRevisionStub.called);

        // When cancel is tapped, the edit secion closes.
        MockInteractions.tap(cancelBtn);
        await element.updateComplete;

        // The revision and edit button are visible.
        assert.notEqual(getComputedStyle(revisionWithEditing).display, 'none');
        assert.notEqual(getComputedStyle(editBtn).display, 'none');

        // The input, cancel, and save buttons are not visible.
        for (const item of hiddenElements) {
          assert.equal(getComputedStyle(item).display, 'none');
        }
      });

      test('_handleSaveRevision with invalid rev', async () => {
        // We need to replicate an event to get all the properties that
        // go with it.
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        const event: any = new Event('polymer-dom-repeat');
        event.model = {set: sinon.stub()};
        element._isEditing = true;
        stubRestApi('setRepoHead').returns(
          Promise.resolve({
            status: 400,
          } as Response)
        );

        await element._setRepoHead(
          'test' as RepoName,
          'newRef' as GitRef,
          event
        );
        assert.isTrue(element._isEditing);
        assert.isFalse(event.model.set.called);
      });

      test('_handleSaveRevision with valid rev', async () => {
        // We need to replicate an event to get all the properties that
        // go with it.
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        const event: any = new Event('polymer-dom-repeat');
        event.model = {set: sinon.stub()};
        element._isEditing = true;
        stubRestApi('setRepoHead').returns(
          Promise.resolve({
            status: 200,
          } as Response)
        );

        await element._setRepoHead(
          'test' as RepoName,
          'newRef' as GitRef,
          event
        );
        assert.isFalse(element._isEditing);
        assert.isTrue(event.model.set.called);
      });

      test('test _computeItemName', () => {
        assert.deepEqual(
          element._computeItemName(RepoDetailView.BRANCHES),
          'Branch'
        );
        assert.deepEqual(element._computeItemName(RepoDetailView.TAGS), 'Tag');
      });
    });

    suite('list with less then 25 branches', () => {
      setup(async () => {
        branches = createBranchesList(25);
        stubRestApi('getRepoBranches').returns(Promise.resolve(branches));

        element.params = {
          view: GerritView.REPO,
          repo: 'test' as RepoName,
          detail: RepoDetailView.BRANCHES,
        };

        await element.paramsChanged();
        await element.updateComplete;
      });

      test('_items', () => {
        assert.equal(element._items!.slice(0, SHOWN_ITEMS_COUNT)!.length, 25);
      });
    });

    suite('filter', () => {
      test('paramsChanged', async () => {
        const stub = stubRestApi('getRepoBranches').returns(
          Promise.resolve(branches)
        );
        element.params = {
          view: GerritView.REPO,
          repo: 'test' as RepoName,
          detail: RepoDetailView.BRANCHES,
          filter: 'test',
          offset: 25,
        };
        await element.paramsChanged();
        assert.equal(stub.lastCall.args[0], 'test');
        assert.equal(stub.lastCall.args[1], 'test');
        assert.equal(stub.lastCall.args[2], 25);
        assert.equal(stub.lastCall.args[3], 25);
      });
    });

    suite('404', () => {
      test('fires page-error', async () => {
        const response = {status: 404} as Response;
        stubRestApi('getRepoBranches').callsFake(
          (_filter, _repo, _reposBranchesPerPage, _opt_offset, errFn) => {
            if (errFn !== undefined) {
              errFn(response);
            }
            return Promise.resolve([]);
          }
        );

        const promise = mockPromise();
        addListenerForTest(document, 'page-error', e => {
          assert.deepEqual((e as PageErrorEvent).detail.response, response);
          promise.resolve();
        });

        element.params = {
          view: GerritView.REPO,
          repo: 'test' as RepoName,
          detail: RepoDetailView.BRANCHES,
          filter: 'test',
          offset: 25,
        };
        element.paramsChanged();
        await promise;
      });
    });
  });

  suite('Tags', () => {
    let element: GrRepoDetailList;
    let tags: TagInfo[];

    setup(async () => {
      element = basicFixture.instantiate();
      await element.updateComplete;
      element.detailType = RepoDetailView.TAGS;
      sinon.stub(page, 'show');
    });

    test('_computeMessage', () => {
      let message =
        'v2.15-rc1↵-----BEGIN PGP SIGNATURE-----↵Version: GnuPG v' +
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
        tags = createTagsList(26);
        stubRestApi('getRepoTags').returns(Promise.resolve(tags));

        element.params = {
          view: GerritView.REPO,
          repo: 'test' as RepoName,
          detail: RepoDetailView.TAGS,
        };

        await element.paramsChanged();
        await element.updateComplete;
      });

      test('test for tag in the list', async () => {
        assert.equal(element._items![2].ref, 'refs/tags/test2');
      });

      test('test for tag message in the list', async () => {
        assert.equal(
          (element._items as TagInfo[])![2].message,
          'Annotated tag'
        );
      });

      test('test for tagger in the tag list', async () => {
        const tagger = {
          name: 'Test User',
          email: 'test.user@gmail.com' as EmailAddress,
          date: '2017-09-19 14:54:00.000000000' as Timestamp,
          tz: 540 as TimezoneOffset,
        };

        assert.deepEqual((element._items as TagInfo[])![2].tagger, tagger);
      });

      test('test for web links in the tags list', async () => {
        assert.equal(
          element._items![2].web_links![0].url,
          'https://git.example.org/tag/test;refs/tags/test2'
        );
      });

      test('test for refs/tags/ being striped from ref', async () => {
        assert.equal(
          element._stripRefs(element._items![2].ref, element.detailType),
          'test2'
        );
      });

      test('_items', () => {
        assert.equal(element._items!.slice(0, SHOWN_ITEMS_COUNT)!.length, 25);
      });

      test('_computeHideTagger', () => {
        const testObject1 = {
          name: 'Test User',
          email: 'test.user@gmail.com' as EmailAddress,
          date: '2017-09-19 14:54:00.000000000' as Timestamp,
          tz: 540 as TimezoneOffset,
        };
        assert.equal(element._computeHideTagger(testObject1), '');

        assert.equal(element._computeHideTagger(undefined), 'hide');
      });
    });

    suite('list with less then 25 tags', () => {
      setup(async () => {
        tags = createTagsList(25);
        stubRestApi('getRepoTags').returns(Promise.resolve(tags));

        element.params = {
          view: GerritView.REPO,
          repo: 'test' as RepoName,
          detail: RepoDetailView.TAGS,
        };

        await element.paramsChanged();
        await element.updateComplete;
      });

      test('_items', () => {
        assert.equal(element._items!.slice(0, SHOWN_ITEMS_COUNT)!.length, 25);
      });
    });

    suite('filter', () => {
      test('paramsChanged', async () => {
        const stub = stubRestApi('getRepoTags').returns(Promise.resolve(tags));
        element.params = {
          view: GerritView.REPO,
          repo: 'test' as RepoName,
          detail: RepoDetailView.TAGS,
          filter: 'test',
          offset: 25,
        };
        await element.paramsChanged();
        assert.equal(stub.lastCall.args[0], 'test');
        assert.equal(stub.lastCall.args[1], 'test');
        assert.equal(stub.lastCall.args[2], 25);
        assert.equal(stub.lastCall.args[3], 25);
      });
    });

    suite('create new', () => {
      test('_handleCreateClicked called when create-click fired', () => {
        const handleCreateClickedStub = sinon.stub(
          element,
          '_handleCreateClicked'
        );
        queryAndAssert<GrListView>(element, 'gr-list-view').dispatchEvent(
          new CustomEvent('create-clicked', {
            composed: true,
            bubbles: true,
          })
        );
        assert.isTrue(handleCreateClickedStub.called);
      });

      test('_handleCreateClicked opens modal', () => {
        queryAndAssert<GrOverlay>(element, '#createOverlay');
        const openStub = sinon.stub(
          queryAndAssert<GrOverlay>(element, '#createOverlay'),
          'open'
        );
        element._handleCreateClicked();
        assert.isTrue(openStub.called);
      });

      test('_handleCreateItem called when confirm fired', () => {
        const handleCreateItemStub = sinon.stub(element, '_handleCreateItem');
        queryAndAssert<GrDialog>(element, '#createDialog').dispatchEvent(
          new CustomEvent('confirm', {
            composed: true,
            bubbles: true,
          })
        );
        assert.isTrue(handleCreateItemStub.called);
      });

      test('_handleCloseCreate called when cancel fired', () => {
        const handleCloseCreateStub = sinon.stub(element, '_handleCloseCreate');
        queryAndAssert<GrDialog>(element, '#createDialog').dispatchEvent(
          new CustomEvent('cancel', {
            composed: true,
            bubbles: true,
          })
        );
        assert.isTrue(handleCloseCreateStub.called);
      });
    });

    suite('404', () => {
      test('fires page-error', async () => {
        const response = {status: 404} as Response;
        stubRestApi('getRepoTags').callsFake(
          (_filter, _repo, _reposTagsPerPage, _opt_offset, errFn) => {
            if (errFn !== undefined) {
              errFn(response);
            }
            return Promise.resolve([]);
          }
        );

        const promise = mockPromise();
        addListenerForTest(document, 'page-error', e => {
          assert.deepEqual((e as PageErrorEvent).detail.response, response);
          promise.resolve();
        });

        element.params = {
          view: GerritView.REPO,
          repo: 'test' as RepoName,
          detail: RepoDetailView.TAGS,
          filter: 'test',
          offset: 25,
        };
        element.paramsChanged();
        await promise;
      });
    });

    test('_computeItemName', () => {
      assert.equal(element._computeItemName(RepoDetailView.BRANCHES), 'Branch');
      assert.equal(element._computeItemName(RepoDetailView.TAGS), 'Tag');
    });
  });
});
