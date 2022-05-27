/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
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
        assert.equal(element.items![3].ref, 'refs/heads/test2');
      });

      test('test for web links in the branches list', () => {
        assert.equal(
          element.items![3].web_links![0].url,
          'https://git.example.org/branch/test;refs/heads/test2'
        );
      });

      test('test for refs/heads/ being striped from ref', () => {
        assert.equal(
          element.stripRefs(element.items![3].ref, element.detailType),
          'test2'
        );
      });

      test('items', () => {
        assert.equal(queryAll<HTMLTableElement>(element, '.table').length, 25);
      });

      test('Edit HEAD button not admin', async () => {
        sinon.stub(element, 'getLoggedIn').returns(Promise.resolve(true));
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
        await element.determineIfOwner('test' as RepoName);
        assert.equal(element.isOwner, false);
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

        sinon.stub(element, 'getLoggedIn').returns(Promise.resolve(true));
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
          'handleSaveRevision'
        );
        await element.determineIfOwner('test' as RepoName);
        assert.equal(element.isOwner, true);
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
        assert.equal(element.revisedRef, 'master' as GitRef);

        assert.isFalse(saveBtn.disabled);

        // Delete the ref.
        element.revisedRef = '' as GitRef;
        await element.updateComplete;
        assert.isTrue(saveBtn.disabled);

        // Change the ref to something else
        element.revisedRef = 'newRef' as GitRef;
        element.repo = 'test' as RepoName;
        await element.updateComplete;
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

      test('handleSaveRevision with invalid rev', async () => {
        element.isEditing = true;
        stubRestApi('setRepoHead').returns(
          Promise.resolve({
            status: 400,
          } as Response)
        );

        await element.setRepoHead('test' as RepoName, 'newRef' as GitRef, 1);
        assert.isTrue(element.isEditing);
      });

      test('handleSaveRevision with valid rev', async () => {
        element.isEditing = true;
        stubRestApi('setRepoHead').returns(
          Promise.resolve({
            status: 200,
          } as Response)
        );

        await element.setRepoHead('test' as RepoName, 'newRef' as GitRef, 1);
        assert.isFalse(element.isEditing);
      });

      test('test computeItemName', () => {
        assert.deepEqual(
          element.computeItemName(RepoDetailView.BRANCHES),
          'Branch'
        );
        assert.deepEqual(element.computeItemName(RepoDetailView.TAGS), 'Tag');
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

      test('items', () => {
        assert.equal(queryAll<HTMLTableElement>(element, '.table').length, 25);
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
        assert.equal(element.items![2].ref, 'refs/tags/test2');
      });

      test('test for tag message in the list', async () => {
        assert.equal((element.items as TagInfo[])![2].message, 'Annotated tag');
      });

      test('test for tagger in the tag list', async () => {
        const tagger = {
          name: 'Test User',
          email: 'test.user@gmail.com' as EmailAddress,
          date: '2017-09-19 14:54:00.000000000' as Timestamp,
          tz: 540 as TimezoneOffset,
        };

        assert.deepEqual((element.items as TagInfo[])![2].tagger, tagger);
      });

      test('test for web links in the tags list', async () => {
        assert.equal(
          element.items![2].web_links![0].url,
          'https://git.example.org/tag/test;refs/tags/test2'
        );
      });

      test('test for refs/tags/ being striped from ref', async () => {
        assert.equal(
          element.stripRefs(element.items![2].ref, element.detailType),
          'test2'
        );
      });

      test('items', () => {
        assert.equal(element.items!.slice(0, SHOWN_ITEMS_COUNT)!.length, 25);
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

      test('items', () => {
        assert.equal(element.items!.slice(0, SHOWN_ITEMS_COUNT)!.length, 25);
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
      test('handleCreateClicked called when create-click fired', () => {
        const handleCreateClickedStub = sinon.stub(
          element,
          'handleCreateClicked'
        );
        queryAndAssert<GrListView>(element, 'gr-list-view').dispatchEvent(
          new CustomEvent('create-clicked', {
            composed: true,
            bubbles: true,
          })
        );
        assert.isTrue(handleCreateClickedStub.called);
      });

      test('handleCreateClicked opens modal', () => {
        queryAndAssert<GrOverlay>(element, '#createOverlay');
        const openStub = sinon.stub(
          queryAndAssert<GrOverlay>(element, '#createOverlay'),
          'open'
        );
        element.handleCreateClicked();
        assert.isTrue(openStub.called);
      });

      test('handleCreateItem called when confirm fired', () => {
        const handleCreateItemStub = sinon.stub(element, 'handleCreateItem');
        queryAndAssert<GrDialog>(element, '#createDialog').dispatchEvent(
          new CustomEvent('confirm', {
            composed: true,
            bubbles: true,
          })
        );
        assert.isTrue(handleCreateItemStub.called);
      });

      test('handleCloseCreate called when cancel fired', () => {
        const handleCloseCreateStub = sinon.stub(element, 'handleCloseCreate');
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

    test('computeItemName', () => {
      assert.equal(element.computeItemName(RepoDetailView.BRANCHES), 'Branch');
      assert.equal(element.computeItemName(RepoDetailView.TAGS), 'Tag');
    });
  });
});
