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
import {NO_THREADS_MSG} from '../../../constants/messages.js';
import {SpecialFilePath} from '../../../constants/constants.js';

const basicFixture = fixtureFromElement('gr-thread-list');

suite('gr-thread-list tests', () => {
  let element;

  let threadElements;

  function getVisibleThreads() {
    return [...dom(element.root)
        .querySelectorAll('gr-comment-thread')]
        .filter(e => e.style.display !== 'none');
  }

  setup(done => {
    element = basicFixture.instantiate();
    element.change = {};
    element.threads = [
      {
        comments: [
          {
            __path: '/COMMIT_MSG',
            author: {
              _account_id: 1000000,
              name: 'user',
              username: 'user',
            },
            patch_set: 4,
            id: 'ecf0b9fa_fe1a5f62',
            line: 5,
            updated: '2018-02-08 18:49:18.000000000',
            message: 'test',
            unresolved: true,
          },
          {
            id: '503008e2_0ab203ee',
            path: '/COMMIT_MSG',
            line: 5,
            in_reply_to: 'ecf0b9fa_fe1a5f62',
            updated: '2018-02-13 22:48:48.018000000',
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
        start_datetime: '2018-02-08 18:49:18.000000000',
      },
      {
        comments: [
          {
            __path: 'test.txt',
            author: {
              _account_id: 1000000,
              name: 'user',
              username: 'user',
            },
            patch_set: 3,
            id: '09a9fb0a_1484e6cf',
            side: 'PARENT',
            updated: '2018-02-13 22:47:19.000000000',
            message: 'Some comment on another patchset.',
            unresolved: false,
          },
        ],
        patchNum: 3,
        path: 'test.txt',
        rootId: '09a9fb0a_1484e6cf',
        start_datetime: '2018-02-13 22:47:19.000000000',
        commentSide: 'PARENT',
      },
      {
        comments: [
          {
            __path: '/COMMIT_MSG',
            author: {
              _account_id: 1000000,
              name: 'user',
              username: 'user',
            },
            patch_set: 2,
            id: '8caddf38_44770ec1',
            updated: '2018-02-13 22:48:40.000000000',
            message: 'Another unresolved comment',
            unresolved: false,
          },
        ],
        patchNum: 2,
        path: '/COMMIT_MSG',
        rootId: '8caddf38_44770ec1',
        start_datetime: '2018-02-13 22:48:40.000000000',
      },
      {
        comments: [
          {
            __path: '/COMMIT_MSG',
            author: {
              _account_id: 1000000,
              name: 'user',
              username: 'user',
            },
            patch_set: 2,
            id: 'scaddf38_44770ec1',
            line: 4,
            updated: '2018-02-14 22:48:40.000000000',
            message: 'Yet another unresolved comment',
            unresolved: true,
          },
        ],
        patchNum: 2,
        path: '/COMMIT_MSG',
        line: 4,
        rootId: 'scaddf38_44770ec1',
        start_datetime: '2018-02-14 22:48:40.000000000',
      },
      {
        comments: [
          {
            id: 'zcf0b9fa_fe1a5f62',
            path: '/COMMIT_MSG',
            line: 6,
            updated: '2018-02-15 22:48:48.018000000',
            message: 'resolved draft',
            unresolved: false,
            __draft: true,
            __draftID: '0.m683trwff68',
            __editing: false,
            patch_set: '2',
          },
        ],
        patchNum: 4,
        path: '/COMMIT_MSG',
        line: 6,
        rootId: 'zcf0b9fa_fe1a5f62',
        start_datetime: '2018-02-09 18:49:18.000000000',
      },
      {
        comments: [
          {
            id: 'patchset_level_1',
            path: SpecialFilePath.PATCHSET_LEVEL_COMMENTS,
            updated: '2018-02-15 22:48:48.018000000',
            message: 'patchset comment 1',
            unresolved: false,
            __editing: false,
            patch_set: '2',
          },
        ],
        patchNum: 2,
        path: SpecialFilePath.PATCHSET_LEVEL_COMMENTS,
        rootId: 'patchset_level_1',
        start_datetime: '2018-02-09 18:49:18.000000000',
      },
      {
        comments: [
          {
            id: 'patchset_level_2',
            path: SpecialFilePath.PATCHSET_LEVEL_COMMENTS,
            updated: '2018-02-15 22:48:48.018000000',
            message: 'patchset comment 2',
            unresolved: false,
            __editing: false,
            patch_set: '3',
          },
        ],
        patchNum: 3,
        path: SpecialFilePath.PATCHSET_LEVEL_COMMENTS,
        rootId: 'patchset_level_2',
        start_datetime: '2018-02-09 18:49:18.000000000',
      },
      {
        comments: [
          {
            __path: '/COMMIT_MSG',
            author: {
              _account_id: 1000000,
              name: 'user',
              username: 'user',
            },
            patch_set: 4,
            id: 'rc1',
            line: 5,
            updated: '2019-02-08 18:49:18.000000000',
            message: 'test',
            unresolved: true,
            robot_id: 'rc1',
          },
        ],
        patchNum: 4,
        path: '/COMMIT_MSG',
        line: 5,
        rootId: 'rc1',
        start_datetime: '2019-02-08 18:49:18.000000000',
      },
      {
        comments: [
          {
            __path: '/COMMIT_MSG',
            author: {
              _account_id: 1000000,
              name: 'user',
              username: 'user',
            },
            patch_set: 4,
            id: 'rc2',
            line: 7,
            updated: '2019-03-08 18:49:18.000000000',
            message: 'test',
            unresolved: true,
            robot_id: 'rc2',
          },
          {
            __path: '/COMMIT_MSG',
            author: {
              _account_id: 1000000,
              name: 'user',
              username: 'user',
            },
            patch_set: 4,
            id: 'c2_1',
            line: 5,
            updated: '2019-03-08 18:49:18.000000000',
            message: 'test',
            unresolved: true,
          },
        ],
        patchNum: 4,
        path: '/COMMIT_MSG',
        line: 7,
        rootId: 'rc2',
        start_datetime: '2019-03-08 18:49:18.000000000',
      },
    ];

    // use flush to render all (bypass initial-count set on dom-repeat)
    flush(() => {
      threadElements = dom(element.root)
          .querySelectorAll('gr-comment-thread');
      done();
    });
  });

  test('draft toggle only appears when logged in', () => {
    assert.equal(getComputedStyle(element.shadowRoot
        .querySelector('.draftToggle')).display,
    'none');
    element.loggedIn = true;
    assert.notEqual(getComputedStyle(element.shadowRoot
        .querySelector('.draftToggle')).display,
    'none');
  });

  test('show all threads by default', () => {
    assert.equal(dom(element.root)
        .querySelectorAll('gr-comment-thread').length, element.threads.length);
    assert.equal(getVisibleThreads().length, element.threads.length);
  });

  test('showing file name takes visible threads into account', () => {
    assert.equal(element._isFirstThreadWithFileName(element._sortedThreads,
        element._sortedThreads[2], element._unresolvedOnly, element._draftsOnly,
        element.onlyShowRobotCommentsWithHumanReply), true);
    element._unresolvedOnly = true;
    assert.equal(element._isFirstThreadWithFileName(element._sortedThreads,
        element._sortedThreads[2], element._unresolvedOnly, element._draftsOnly,
        element.onlyShowRobotCommentsWithHumanReply), false);
  });

  test('onlyShowRobotCommentsWithHumanReply ', () => {
    element.onlyShowRobotCommentsWithHumanReply = true;
    flushAsynchronousOperations();
    assert.equal(
        getVisibleThreads().length,
        element.threads.length - 1);
    assert.isNotOk(getVisibleThreads().find(th => th.rootId === 'rc1'));
  });

  suite('_compareThreads', () => {
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

  test('thread removal and sort again', () => {
    threadElements[1].dispatchEvent(
        new CustomEvent('thread-discard', {
          detail: {rootId: 'rc2'},
          composed: true, bubbles: true,
        }));
    flushAsynchronousOperations();
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

  test('toggle unresolved only shows unresolved comments', () => {
    MockInteractions.tap(element.shadowRoot.querySelector(
        '#unresolvedToggle'));
    flushAsynchronousOperations();
    assert.equal(getVisibleThreads().length, 4);
  });

  test('toggle drafts only shows threads with draft comments', () => {
    MockInteractions.tap(element.shadowRoot.querySelector('#draftToggle'));
    flushAsynchronousOperations();
    assert.equal(getVisibleThreads().length, 2);
  });

  test('toggle drafts and unresolved should ignore comments in editing', () => {
    const modifiedThreads = [...element.threads];
    modifiedThreads[5] = {...modifiedThreads[5]};
    modifiedThreads[5].comments = [...modifiedThreads[5].comments];
    modifiedThreads[5].comments.push({
      ...modifiedThreads[5].comments[0],
      __editing: true,
    });
    element.threads = modifiedThreads;
    MockInteractions.tap(element.shadowRoot.querySelector('#draftToggle'));
    MockInteractions.tap(element.shadowRoot.querySelector(
        '#unresolvedToggle'));
    flushAsynchronousOperations();
    assert.equal(getVisibleThreads().length, 2);
  });

  test('toggle drafts and unresolved only shows threads with drafts and ' +
      'publicly unresolved ', () => {
    MockInteractions.tap(element.shadowRoot.querySelector('#draftToggle'));
    MockInteractions.tap(element.shadowRoot.querySelector(
        '#unresolvedToggle'));
    flushAsynchronousOperations();
    assert.equal(getVisibleThreads().length, 1);
  });

  test('modification events are consumed and displatched', () => {
    sinon.spy(element, '_handleCommentsChanged');
    const dispatchSpy = sinon.stub();
    element.addEventListener('thread-list-modified', dispatchSpy);
    threadElements[0].dispatchEvent(
        new CustomEvent('thread-changed', {
          detail: {
            rootId: 'ecf0b9fa_fe1a5f62', path: '/COMMIT_MSG'},
          composed: true, bubbles: true,
        }));
    assert.isTrue(element._handleCommentsChanged.called);
    assert.isTrue(dispatchSpy.called);
    assert.equal(dispatchSpy.lastCall.args[0].detail.rootId,
        'ecf0b9fa_fe1a5f62');
    assert.equal(dispatchSpy.lastCall.args[0].detail.path, '/COMMIT_MSG');
  });

  suite('hideToggleButtons', () => {
    setup(done => {
      element.hideToggleButtons = true;
      flush(() => {
        done();
      });
    });

    test('toggle buttons are hidden', () => {
      assert.equal(element.shadowRoot.querySelector('.header').style.display,
          'none');
    });
  });

  suite('empty thread', () => {
    setup(done => {
      element.threads = [];
      flush(() => {
        done();
      });
    });

    test('default empty message should show', () => {
      assert.equal(
          element.shadowRoot.querySelector('#threads').textContent.trim(),
          NO_THREADS_MSG
      );
    });

    test('can override empty message', () => {
      element.emptyThreadMsg = 'test';
      assert.equal(
          element.shadowRoot.querySelector('#threads').textContent.trim(),
          'test'
      );
    });
  });
});

