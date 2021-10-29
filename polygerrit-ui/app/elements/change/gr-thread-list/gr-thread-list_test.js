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
import './gr-thread-list.js';
import {dom} from '@polymer/polymer/lib/legacy/polymer.dom.js';
import {SpecialFilePath} from '../../../constants/constants.js';
import {CommentTabState} from '../../../types/events.js';
import {__testOnly_SortDropdownState} from './gr-thread-list.js';
import {queryAll} from '../../../test/test-utils.js';
import {accountOrGroupKey} from '../../../utils/account-util.js';
import {tap} from '@polymer/iron-test-helpers/mock-interactions';
import {createAccountDetailWithId} from '../../../test/test-data-generators.js';

const basicFixture = fixtureFromElement('gr-thread-list');

suite('gr-thread-list tests', () => {
  let element;

  function getVisibleThreads() {
    return [...dom(element.root)
        .querySelectorAll('gr-comment-thread')]
        .filter(e => e.style.display !== 'none');
  }

  setup(async () => {
    element = basicFixture.instantiate();
    element.changeNum = 123;
    element.change = {
      project: 'testRepo',
    };
    element.threads = [
      {
        comments: [
          {
            path: '/COMMIT_MSG',
            author: {
              _account_id: 1000001,
              name: 'user',
              username: 'user',
            },
            patch_set: 4,
            id: 'ecf0b9fa_fe1a5f62',
            line: 5,
            updated: '1',
            message: 'test',
            unresolved: true,
          },
          {
            id: '503008e2_0ab203ee',
            path: '/COMMIT_MSG',
            line: 5,
            in_reply_to: 'ecf0b9fa_fe1a5f62',
            updated: '1',
            message: 'draft',
            unresolved: true,
            __draft: true,
            __draftID: '0.m683trwff68',
            __editing: false,
            patch_set: '2',
          },
        ],
        patchNum: 4,
        path: '/COMMIT_MSG',
        line: 5,
        rootId: 'ecf0b9fa_fe1a5f62',
        updated: '1',
      },
      {
        comments: [
          {
            path: 'test.txt',
            author: {
              _account_id: 1000002,
              name: 'user',
              username: 'user',
            },
            patch_set: 3,
            id: '09a9fb0a_1484e6cf',
            side: 'PARENT',
            updated: '2',
            message: 'Some comment on another patchset.',
            unresolved: false,
          },
        ],
        patchNum: 3,
        path: 'test.txt',
        rootId: '09a9fb0a_1484e6cf',
        updated: '2',
        commentSide: 'PARENT',
      },
      {
        comments: [
          {
            path: '/COMMIT_MSG',
            author: {
              _account_id: 1000002,
              name: 'user',
              username: 'user',
            },
            patch_set: 2,
            id: '8caddf38_44770ec1',
            updated: '3',
            message: 'Another unresolved comment',
            unresolved: false,
          },
        ],
        patchNum: 2,
        path: '/COMMIT_MSG',
        rootId: '8caddf38_44770ec1',
        updated: '3',
      },
      {
        comments: [
          {
            path: '/COMMIT_MSG',
            author: {
              _account_id: 1000003,
              name: 'user',
              username: 'user',
            },
            patch_set: 2,
            id: 'scaddf38_44770ec1',
            line: 4,
            updated: '4',
            message: 'Yet another unresolved comment',
            unresolved: true,
          },
        ],
        patchNum: 2,
        path: '/COMMIT_MSG',
        line: 4,
        rootId: 'scaddf38_44770ec1',
        updated: '4',
      },
      {
        comments: [
          {
            id: 'zcf0b9fa_fe1a5f62',
            path: '/COMMIT_MSG',
            line: 6,
            updated: '5',
            message: 'resolved draft',
            unresolved: false,
            __draft: true,
            __draftID: '0.m683trwff69',
            __editing: false,
            patch_set: '2',
          },
        ],
        patchNum: 4,
        path: '/COMMIT_MSG',
        line: 6,
        rootId: 'zcf0b9fa_fe1a5f62',
        updated: '5',
      },
      {
        comments: [
          {
            id: 'patchset_level_1',
            path: SpecialFilePath.PATCHSET_LEVEL_COMMENTS,
            updated: '6',
            message: 'patchset comment 1',
            unresolved: false,
            __editing: false,
            patch_set: '2',
          },
        ],
        patchNum: 2,
        path: SpecialFilePath.PATCHSET_LEVEL_COMMENTS,
        rootId: 'patchset_level_1',
        updated: '6',
      },
      {
        comments: [
          {
            id: 'patchset_level_2',
            path: SpecialFilePath.PATCHSET_LEVEL_COMMENTS,
            updated: '7',
            message: 'patchset comment 2',
            unresolved: false,
            __editing: false,
            patch_set: '3',
          },
        ],
        patchNum: 3,
        path: SpecialFilePath.PATCHSET_LEVEL_COMMENTS,
        rootId: 'patchset_level_2',
        updated: '7',
      },
      {
        comments: [
          {
            path: '/COMMIT_MSG',
            author: {
              _account_id: 1000000,
              name: 'user',
              username: 'user',
            },
            patch_set: 4,
            id: 'rc1',
            line: 5,
            updated: '8',
            message: 'test',
            unresolved: true,
            robot_id: 'rc1',
          },
        ],
        patchNum: 4,
        path: '/COMMIT_MSG',
        line: 5,
        rootId: 'rc1',
        updated: '8',
      },
      {
        comments: [
          {
            path: '/COMMIT_MSG',
            author: {
              _account_id: 1000000,
              name: 'user',
              username: 'user',
            },
            patch_set: 4,
            id: 'rc2',
            line: 7,
            updated: '9',
            message: 'test',
            unresolved: true,
            robot_id: 'rc2',
          },
          {
            path: '/COMMIT_MSG',
            author: {
              _account_id: 1000000,
              name: 'user',
              username: 'user',
            },
            patch_set: 4,
            id: 'c2_1',
            line: 5,
            updated: '10',
            message: 'test',
            unresolved: true,
          },
        ],
        patchNum: 4,
        path: '/COMMIT_MSG',
        line: 7,
        rootId: 'rc2',
        updated: '10',
      },
    ];

    // use flush to render all (bypass initial-count set on dom-repeat)
    await flush();
  });

  test('draft dropdown item only appears when logged in', () => {
    element.loggedIn = false;
    flush();
    assert.equal(element.getCommentsDropdownEntires(element.threads,
        element.loggedIn).length, 2);
    element.loggedIn = true;
    flush();
    assert.equal(element.getCommentsDropdownEntires(element.threads,
        element.loggedIn).length, 3);
  });

  test('show all threads by default', () => {
    assert.equal(dom(element.root)
        .querySelectorAll('gr-comment-thread').length, element.threads.length);
    assert.equal(getVisibleThreads().length, element.threads.length);
  });

  test('show unresolved threads if unresolvedOnly is set', async () => {
    element.unresolvedOnly = true;
    await flush();
    const unresolvedThreads = element.threads.filter(t => t.comments.some(
        c => c.unresolved
    ));
    assert.equal(getVisibleThreads().length, unresolvedThreads.length);
  });

  test('showing file name takes visible threads into account', () => {
    element.sortDropdownValue = __testOnly_SortDropdownState.FILES;
    assert.equal(element._isFirstThreadWithFileName(element._sortedThreads,
        element._sortedThreads[2], element.unresolvedOnly, element.draftsOnly,
        element.onlyShowRobotCommentsWithHumanReply, element.selectedAuthors),
    true);
    element.unresolvedOnly = true;
    assert.equal(element._isFirstThreadWithFileName(element._sortedThreads,
        element._sortedThreads[2], element.unresolvedOnly, element.draftsOnly,
        element.onlyShowRobotCommentsWithHumanReply, element.selectedAuthors),
    false);
  });

  test('onlyShowRobotCommentsWithHumanReply ', () => {
    element.onlyShowRobotCommentsWithHumanReply = true;
    flush();
    assert.equal(
        getVisibleThreads().length,
        element.threads.length - 1);
    assert.isNotOk(getVisibleThreads().find(th => th.rootId === 'rc1'));
  });

  suite('_compareThreads', () => {
    setup(() => {
      element.sortDropdownValue = __testOnly_SortDropdownState.FILES;
    });

    test('patchset comes before any other file', () => {
      const t1 = {thread: {path: SpecialFilePath.PATCHSET_LEVEL_COMMENTS}};
      const t2 = {thread: {path: SpecialFilePath.COMMIT_MESSAGE}};

      t1.patchNum = t2.patchNum = 1;
      t1.unresolved = t2.unresolved = t1.hasDraft = t2.hasDraft = false;
      assert.equal(element._compareThreads(t1, t2), -1);
      assert.equal(element._compareThreads(t2, t1), 1);

      // assigning values to properties such that t2 should come first
      t1.patchNum = 1;
      t2.patchNum = 2;
      t1.unresolved = t1.hasDraft = false;
      t2.unresolved = t2.unresolved = true;
      assert.equal(element._compareThreads(t1, t2), -1);
      assert.equal(element._compareThreads(t2, t1), 1);
    });

    test('file path is compared lexicographically', () => {
      const t1 = {thread: {path: 'a.txt'}};
      const t2 = {thread: {path: 'b.txt'}};
      t1.patchNum = t2.patchNum = 1;
      t1.unresolved = t2.unresolved = t1.hasDraft = t2.hasDraft = false;
      assert.equal(element._compareThreads(t1, t2), -1);
      assert.equal(element._compareThreads(t2, t1), 1);

      t1.patchNum = 1;
      t2.patchNum = 2;
      t1.unresolved = t1.hasDraft = false;
      t2.unresolved = t2.unresolved = true;
      assert.equal(element._compareThreads(t1, t2), -1);
      assert.equal(element._compareThreads(t2, t1), 1);
    });

    test('patchset comments sorted by reverse patchset', () => {
      const t1 = {thread: {path: SpecialFilePath.PATCHSET_LEVEL_COMMENTS,
        patchNum: 1}};
      const t2 = {thread: {path: SpecialFilePath.PATCHSET_LEVEL_COMMENTS,
        patchNum: 2}};
      t1.unresolved = t2.unresolved = t1.hasDraft = t2.hasDraft = false;
      assert.equal(element._compareThreads(t1, t2), 1);
      assert.equal(element._compareThreads(t2, t1), -1);

      t1.unresolved = t1.hasDraft = false;
      t2.unresolved = t2.unresolved = true;
      assert.equal(element._compareThreads(t1, t2), 1);
      assert.equal(element._compareThreads(t2, t1), -1);
    });

    test('patchset comments with same patchset picks unresolved first', () => {
      const t1 = {thread: {path: SpecialFilePath.PATCHSET_LEVEL_COMMENTS,
        patchNum: 1}, unresolved: true};
      const t2 = {thread: {path: SpecialFilePath.PATCHSET_LEVEL_COMMENTS,
        patchNum: 1}, unresolved: false};
      t1.hasDraft = t2.hasDraft = false;
      assert.equal(element._compareThreads(t1, t2), -1);
      assert.equal(element._compareThreads(t2, t1), 1);
    });

    test('file level comment before line', () => {
      const t1 = {thread: {path: 'a.txt', line: 2}};
      const t2 = {thread: {path: 'a.txt'}};
      t1.patchNum = t2.patchNum = 1;
      t1.unresolved = t2.unresolved = t1.hasDraft = t2.hasDraft = false;
      assert.equal(element._compareThreads(t1, t2), 1);
      assert.equal(element._compareThreads(t2, t1), -1);

      // give preference to t1 in unresolved/draft properties
      t1.unresolved = t1.hasDraft = true;
      t2.unresolved = t2.unresolved = false;
      assert.equal(element._compareThreads(t1, t2), 1);
      assert.equal(element._compareThreads(t2, t1), -1);
    });

    test('comments sorted by line', () => {
      const t1 = {thread: {path: 'a.txt', line: 2}};
      const t2 = {thread: {path: 'a.txt', line: 3}};
      t1.patchNum = t2.patchNum = 1;
      t1.unresolved = t2.unresolved = t1.hasDraft = t2.hasDraft = false;
      assert.equal(element._compareThreads(t1, t2), -1);
      assert.equal(element._compareThreads(t2, t1), 1);

      t1.unresolved = t1.hasDraft = false;
      t2.unresolved = t2.unresolved = true;
      assert.equal(element._compareThreads(t1, t2), -1);
      assert.equal(element._compareThreads(t2, t1), 1);
    });

    test('comments on same line sorted by reverse patchset', () => {
      const t1 = {thread: {path: 'a.txt', line: 2, patchNum: 1}};
      const t2 = {thread: {path: 'a.txt', line: 2, patchNum: 2}};
      t1.unresolved = t2.unresolved = t1.hasDraft = t2.hasDraft = false;
      assert.equal(element._compareThreads(t1, t2), 1);
      assert.equal(element._compareThreads(t2, t1), -1);

      // give preference to t1 in unresolved/draft properties
      t1.unresolved = t1.hasDraft = true;
      t2.unresolved = t2.unresolved = false;
      assert.equal(element._compareThreads(t1, t2), 1);
      assert.equal(element._compareThreads(t2, t1), -1);
    });

    test('comments on same line & patchset sorted by unresolved first',
        () => {
          const t1 = {thread: {path: 'a.txt', line: 2, patchNum: 1},
            unresolved: true};
          const t2 = {thread: {path: 'a.txt', line: 2, patchNum: 1},
            unresolved: false};
          t1.patchNum = t2.patchNum = 1;
          assert.equal(element._compareThreads(t1, t2), -1);
          assert.equal(element._compareThreads(t2, t1), 1);

          t2.hasDraft = true;
          t1.hasDraft = false;
          assert.equal(element._compareThreads(t1, t2), -1);
          assert.equal(element._compareThreads(t2, t1), 1);
        });

    test('comments on same line & patchset & unresolved sorted by draft',
        () => {
          const t1 = {thread: {path: 'a.txt', line: 2, patchNum: 1},
            unresolved: true, hasDraft: false};
          const t2 = {thread: {path: 'a.txt', line: 2, patchNum: 1},
            unresolved: true, hasDraft: true};
          t1.patchNum = t2.patchNum = 1;
          assert.equal(element._compareThreads(t1, t2), 1);
          assert.equal(element._compareThreads(t2, t1), -1);
        });
  });

  test('_computeSortedThreads', () => {
    element.sortDropdownValue = __testOnly_SortDropdownState.FILES;
    assert.equal(element._sortedThreads.length, 9);
    const expectedSortedRootIds = [
      'patchset_level_2', // Posted on Patchset 3
      'patchset_level_1', // Posted on Patchset 2
      '8caddf38_44770ec1', // File level on COMMIT_MSG
      'scaddf38_44770ec1', // Line 4 on COMMIT_MSG
      'ecf0b9fa_fe1a5f62', // Line 5 on COMMIT_MESSAGE but with drafts
      'rc1', // Line 5 on COMMIT_MESSAGE without drafts
      'zcf0b9fa_fe1a5f62', // Line 6 on COMMIT_MSG
      'rc2', // Line 7 on COMMIT_MSG
      '09a9fb0a_1484e6cf', // File level on test.txt
    ];
    element._sortedThreads.forEach((thread, index) => {
      assert.equal(thread.rootId, expectedSortedRootIds[index]);
    });
  });

  test('_computeSortedThreads with timestamp', () => {
    element.sortDropdownValue = __testOnly_SortDropdownState.TIMESTAMP;
    element.resortThreads(element.threads);
    assert.equal(element._sortedThreads.length, 9);
    const expectedSortedRootIds = [
      'rc2',
      'rc1',
      'patchset_level_2',
      'patchset_level_1',
      'zcf0b9fa_fe1a5f62',
      'scaddf38_44770ec1',
      '8caddf38_44770ec1',
      '09a9fb0a_1484e6cf',
      'ecf0b9fa_fe1a5f62',
    ];
    element._sortedThreads.forEach((thread, index) => {
      assert.equal(thread.rootId, expectedSortedRootIds[index]);
    });
  });

  test('tapping single author chips', () => {
    element.account = createAccountDetailWithId(1);
    flush();
    const chips = Array.from(queryAll(element, 'gr-account-label'));
    const authors = chips.map(
        chip => accountOrGroupKey(chip.account))
        .sort();
    assert.deepEqual(authors, [1, 1000000, 1000001, 1000002, 1000003]);
    assert.equal(element.threads.length, 9);
    assert.equal(element._displayedThreads.length, 9);

    // accountId 1000001
    const chip = chips.find(chip => chip.account._account_id === 1000001);

    tap(chip);
    flush();

    assert.equal(element.threads.length, 9);
    assert.equal(element._displayedThreads.length, 1);
    assert.equal(element._displayedThreads[0].comments[0].author._account_id,
        1000001);

    tap(chip); // tapping again resets
    flush();
    assert.equal(element.threads.length, 9);
    assert.equal(element._displayedThreads.length, 9);
  });

  test('tapping multiple author chips', () => {
    element.account = createAccountDetailWithId(1);
    flush();
    const chips = Array.from(queryAll(element, 'gr-account-label'));

    tap(chips.find(chip => chip.account._account_id === 1000001));
    tap(chips.find(chip => chip.account._account_id === 1000002));
    flush();

    assert.equal(element.threads.length, 9);
    assert.equal(element._displayedThreads.length, 3);
    assert.equal(element._displayedThreads[0].comments[0].author._account_id,
        1000002);
    assert.equal(element._displayedThreads[1].comments[0].author._account_id,
        1000002);
    assert.equal(element._displayedThreads[2].comments[0].author._account_id,
        1000001);
  });

  test('thread removal and sort again', () => {
    element.sortDropdownValue = __testOnly_SortDropdownState.FILES;
    const index = element.threads.findIndex(t => t.rootId === 'rc2');
    element.threads.splice(index, 1);
    element.threads = [...element.threads]; // trigger observers
    flush();
    assert.equal(element._sortedThreads.length, 8);
    const expectedSortedRootIds = [
      'patchset_level_2',
      'patchset_level_1',
      '8caddf38_44770ec1', // File level on COMMIT_MSG
      'scaddf38_44770ec1', // Line 4 on COMMIT_MSG
      'ecf0b9fa_fe1a5f62', // Line 5 on COMMIT_MESSAGE but with drafts
      'rc1', // Line 5 on COMMIT_MESSAGE without drafts
      'zcf0b9fa_fe1a5f62', // Line 6 on COMMIT_MSG
      '09a9fb0a_1484e6cf', // File level on test.txt
    ];
    element._sortedThreads.forEach((thread, index) => {
      assert.equal(thread.rootId, expectedSortedRootIds[index]);
    });
  });

  test('modification on thread shold not trigger sort again', () => {
    element.sortDropdownValue = __testOnly_SortDropdownState.FILES;
    const currentSortedThreads = [...element._sortedThreads];
    for (const thread of currentSortedThreads) {
      thread.comments = [...thread.comments];
    }
    const modifiedThreads = [...element.threads];
    modifiedThreads[5] = {...modifiedThreads[5]};
    modifiedThreads[5].comments = [...modifiedThreads[5].comments, {
      ...modifiedThreads[5].comments[0],
      unresolved: false,
    }];
    element.threads = modifiedThreads;
    assert.notDeepEqual(currentSortedThreads, element._sortedThreads);

    // exact same order as in _computeSortedThreads
    const expectedSortedRootIds = [
      'patchset_level_2',
      'patchset_level_1',
      '8caddf38_44770ec1', // File level on COMMIT_MSG
      'scaddf38_44770ec1', // Line 4 on COMMIT_MSG
      'ecf0b9fa_fe1a5f62', // Line 5 on COMMIT_MESSAGE but with drafts
      'rc1', // Line 5 on COMMIT_MESSAGE without drafts
      'zcf0b9fa_fe1a5f62', // Line 6 on COMMIT_MSG
      'rc2', // Line 7 on COMMIT_MSG
      '09a9fb0a_1484e6cf', // File level on test.txt
    ];
    element._sortedThreads.forEach((thread, index) => {
      assert.equal(thread.rootId, expectedSortedRootIds[index]);
    });
  });

  test('reset sortedThreads when threads set to undefiend', () => {
    element.threads = undefined;
    assert.deepEqual(element._sortedThreads, []);
  });

  test('non-equal length of sortThreads and threads' +
    ' should trigger sort again', () => {
    element.sortDropdownValue = __testOnly_SortDropdownState.FILES;
    const modifiedThreads = [...element.threads];
    const currentSortedThreads = [...element._sortedThreads];
    element._sortedThreads = [];
    element.threads = modifiedThreads;
    assert.deepEqual(currentSortedThreads, element._sortedThreads);

    // exact same order as in _computeSortedThreads
    const expectedSortedRootIds = [
      'patchset_level_2',
      'patchset_level_1',
      '8caddf38_44770ec1', // File level on COMMIT_MSG
      'scaddf38_44770ec1', // Line 4 on COMMIT_MSG
      'ecf0b9fa_fe1a5f62', // Line 5 on COMMIT_MESSAGE but with drafts
      'rc1', // Line 5 on COMMIT_MESSAGE without drafts
      'zcf0b9fa_fe1a5f62', // Line 6 on COMMIT_MSG
      'rc2', // Line 7 on COMMIT_MSG
      '09a9fb0a_1484e6cf', // File level on test.txt
    ];
    element._sortedThreads.forEach((thread, index) => {
      assert.equal(thread.rootId, expectedSortedRootIds[index]);
    });
  });

  test('show all comments', () => {
    element.handleCommentsDropdownValueChange({detail: {
      value: CommentTabState.SHOW_ALL}});
    flush();
    assert.equal(getVisibleThreads().length, 9);
  });

  test('unresolved shows all unresolved comments', () => {
    element.handleCommentsDropdownValueChange({detail: {
      value: CommentTabState.UNRESOLVED}});
    flush();
    assert.equal(getVisibleThreads().length, 4);
  });

  test('toggle drafts only shows threads with draft comments', () => {
    element.handleCommentsDropdownValueChange({detail: {
      value: CommentTabState.DRAFTS}});
    flush();
    assert.equal(getVisibleThreads().length, 2);
  });

  suite('hideDropdown', () => {
    setup(async () => {
      element.hideDropdown = true;
      await flush();
    });

    test('toggle buttons are hidden', () => {
      assert.equal(element.shadowRoot.querySelector('.header').style.display,
          'none');
    });
  });

  suite('empty thread', () => {
    setup(async () => {
      element.threads = [];
      await flush();
    });

    test('default empty message should show', () => {
      assert.isTrue(
          element.shadowRoot.querySelector('#threads').textContent.trim()
              .includes('No comments'));
    });
  });
});

