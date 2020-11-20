/**
 * @license
 * Copyright (C) 2015 The Android Open Source Project
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
import {listenOnce} from '../../../test/test-utils.js';
import '../../diff/gr-comment-api/gr-comment-api.js';
import {getMockDiffResponse} from '../../../test/mocks/diff-response.js';
import './gr-file-list.js';
import {createCommentApiMockWithTemplateElement} from '../../../test/mocks/comment-api.js';
import {dom} from '@polymer/polymer/lib/legacy/polymer.dom.js';
import {FilesExpandedState} from '../gr-file-list-constants.js';
import {GerritNav} from '../../core/gr-navigation/gr-navigation.js';
import {runA11yAudit} from '../../../test/a11y-test-utils.js';
import {html} from '@polymer/polymer/lib/utils/html-tag.js';
import {TestKeyboardShortcutBinder} from '../../../test/test-utils.js';
import {Shortcut} from '../../../mixins/keyboard-shortcut-mixin/keyboard-shortcut-mixin.js';
import {ChangeComments} from '../../diff/gr-comment-api/gr-comment-api.js';

const commentApiMock = createCommentApiMockWithTemplateElement(
    'gr-file-list-comment-api-mock', html`
    <gr-file-list id="fileList"
        change-comments="[[_changeComments]]"
        on-reload-drafts="_reloadDraftsWithCallback"></gr-file-list>
    <gr-comment-api id="commentAPI"></gr-comment-api>
`);

const basicFixture = fixtureFromElement(commentApiMock.is);

suite('gr-diff a11y test', () => {
  test('audit', async () => {
    await runA11yAudit(basicFixture);
  });
});

suite('gr-file-list tests', () => {
  let element;
  let commentApiWrapper;

  let saveStub;
  let loadCommentSpy;

  suiteSetup(() => {
    const kb = TestKeyboardShortcutBinder.push();
    kb.bindShortcut(Shortcut.LEFT_PANE, 'shift+left');
    kb.bindShortcut(Shortcut.RIGHT_PANE, 'shift+right');
    kb.bindShortcut(Shortcut.TOGGLE_INLINE_DIFF, 'i:keyup');
    kb.bindShortcut(Shortcut.TOGGLE_ALL_INLINE_DIFFS, 'shift+i:keyup');
    kb.bindShortcut(Shortcut.CURSOR_NEXT_FILE, 'j', 'down');
    kb.bindShortcut(Shortcut.CURSOR_PREV_FILE, 'k', 'up');
    kb.bindShortcut(Shortcut.NEXT_LINE, 'j', 'down');
    kb.bindShortcut(Shortcut.PREV_LINE, 'k', 'up');
    kb.bindShortcut(Shortcut.NEW_COMMENT, 'c');
    kb.bindShortcut(Shortcut.OPEN_LAST_FILE, '[');
    kb.bindShortcut(Shortcut.OPEN_FIRST_FILE, ']');
    kb.bindShortcut(Shortcut.OPEN_FILE, 'o');
    kb.bindShortcut(Shortcut.NEXT_CHUNK, 'n');
    kb.bindShortcut(Shortcut.PREV_CHUNK, 'p');
    kb.bindShortcut(Shortcut.TOGGLE_FILE_REVIEWED, 'r');
    kb.bindShortcut(Shortcut.TOGGLE_LEFT_PANE, 'shift+a');
  });

  suiteTeardown(() => {
    TestKeyboardShortcutBinder.pop();
  });

  suite('basic tests', () => {
    setup(done => {
      stub('gr-rest-api-interface', {
        getLoggedIn() { return Promise.resolve(true); },
        getPreferences() { return Promise.resolve({}); },
        getDiffPreferences() { return Promise.resolve({}); },
        getDiffComments() { return Promise.resolve({}); },
        getDiffRobotComments() { return Promise.resolve({}); },
        getDiffDrafts() { return Promise.resolve({}); },
        getAccountCapabilities() { return Promise.resolve({}); },
      });
      stub('gr-date-formatter', {
        _loadTimeFormat() { return Promise.resolve(''); },
      });
      stub('gr-diff-host', {
        reload() { return Promise.resolve(); },
        prefetchDiff() {},
      });

      // Element must be wrapped in an element with direct access to the
      // comment API.
      commentApiWrapper = basicFixture.instantiate();
      element = commentApiWrapper.$.fileList;
      loadCommentSpy = sinon.spy(commentApiWrapper.$.commentAPI, 'loadAll');

      // Stub methods on the changeComments object after changeComments has
      // been initialized.
      commentApiWrapper.loadComments().then(() => {
        sinon.stub(element.changeComments, 'getPaths').returns({});
        sinon.stub(element.changeComments, 'getCommentsBySideForPath')
            .returns({meta: {}, left: [], right: []});
        done();
      });
      element._loading = false;
      element.diffPrefs = {};
      element.numFilesShown = 200;
      element.patchRange = {
        basePatchNum: 'PARENT',
        patchNum: 2,
      };
      saveStub = sinon.stub(element, '_saveReviewedState').callsFake(
          () => Promise.resolve());
    });

    test('correct number of files are shown', () => {
      element.fileListIncrement = 300;
      element._filesByPath = Array(500).fill(0)
          .reduce((_filesByPath, _, idx) => {
            _filesByPath['/file' + idx] = {lines_inserted: 9};
            return _filesByPath;
          }, {});

      flush();
      assert.equal(
          element.root.querySelectorAll('.file-row').length,
          element.numFilesShown);
      const controlRow = element.shadowRoot
          .querySelector('.controlRow');
      assert.isFalse(controlRow.classList.contains('invisible'));
      assert.equal(element.$.incrementButton.textContent.trim(),
          'Show 300 more');
      assert.equal(element.$.showAllButton.textContent.trim(),
          'Show all 500 files');

      MockInteractions.tap(element.$.showAllButton);
      flush();

      assert.equal(element.numFilesShown, 500);
      assert.equal(element._shownFiles.length, 500);
      assert.isTrue(controlRow.classList.contains('invisible'));
    });

    test('rendering each row calls the _reportRenderedRow method', () => {
      const renderedStub = sinon.stub(element, '_reportRenderedRow');
      element._filesByPath = Array(10).fill(0)
          .reduce((_filesByPath, _, idx) => {
            _filesByPath['/file' + idx] = {lines_inserted: 9};
            return _filesByPath;
          }, {});
      flush();
      assert.equal(
          element.root.querySelectorAll('.file-row').length, 10);
      assert.equal(renderedStub.callCount, 10);
    });

    test('calculate totals for patch number', () => {
      element._filesByPath = {
        '/COMMIT_MSG': {
          lines_inserted: 9,
        },
        '/MERGE_LIST': {
          lines_inserted: 9,
        },
        'file_added_in_rev2.txt': {
          lines_inserted: 1,
          lines_deleted: 1,
          size_delta: 10,
          size: 100,
        },
        'myfile.txt': {
          lines_inserted: 1,
          lines_deleted: 1,
          size_delta: 10,
          size: 100,
        },
      };

      assert.deepEqual(element._patchChange, {
        inserted: 2,
        deleted: 2,
        size_delta_inserted: 0,
        size_delta_deleted: 0,
        total_size: 0,
      });
      assert.isTrue(element._hideBinaryChangeTotals);
      assert.isFalse(element._hideChangeTotals);

      // Test with a commit message that isn't the first file.
      element._filesByPath = {
        'file_added_in_rev2.txt': {
          lines_inserted: 1,
          lines_deleted: 1,
        },
        '/COMMIT_MSG': {
          lines_inserted: 9,
        },
        '/MERGE_LIST': {
          lines_inserted: 9,
        },
        'myfile.txt': {
          lines_inserted: 1,
          lines_deleted: 1,
        },
      };

      assert.deepEqual(element._patchChange, {
        inserted: 2,
        deleted: 2,
        size_delta_inserted: 0,
        size_delta_deleted: 0,
        total_size: 0,
      });
      assert.isTrue(element._hideBinaryChangeTotals);
      assert.isFalse(element._hideChangeTotals);

      // Test with no commit message.
      element._filesByPath = {
        'file_added_in_rev2.txt': {
          lines_inserted: 1,
          lines_deleted: 1,
        },
        'myfile.txt': {
          lines_inserted: 1,
          lines_deleted: 1,
        },
      };

      assert.deepEqual(element._patchChange, {
        inserted: 2,
        deleted: 2,
        size_delta_inserted: 0,
        size_delta_deleted: 0,
        total_size: 0,
      });
      assert.isTrue(element._hideBinaryChangeTotals);
      assert.isFalse(element._hideChangeTotals);

      // Test with files missing either lines_inserted or lines_deleted.
      element._filesByPath = {
        'file_added_in_rev2.txt': {lines_inserted: 1},
        'myfile.txt': {lines_deleted: 1},
      };
      assert.deepEqual(element._patchChange, {
        inserted: 1,
        deleted: 1,
        size_delta_inserted: 0,
        size_delta_deleted: 0,
        total_size: 0,
      });
      assert.isTrue(element._hideBinaryChangeTotals);
      assert.isFalse(element._hideChangeTotals);
    });

    test('binary only files', () => {
      element._filesByPath = {
        '/COMMIT_MSG': {lines_inserted: 9},
        'file_binary_1': {binary: true, size_delta: 10, size: 100},
        'file_binary_2': {binary: true, size_delta: -5, size: 120},
      };
      assert.deepEqual(element._patchChange, {
        inserted: 0,
        deleted: 0,
        size_delta_inserted: 10,
        size_delta_deleted: -5,
        total_size: 220,
      });
      assert.isFalse(element._hideBinaryChangeTotals);
      assert.isTrue(element._hideChangeTotals);
    });

    test('binary and regular files', () => {
      element._filesByPath = {
        '/COMMIT_MSG': {lines_inserted: 9},
        'file_binary_1': {binary: true, size_delta: 10, size: 100},
        'file_binary_2': {binary: true, size_delta: -5, size: 120},
        'myfile.txt': {lines_deleted: 5, size_delta: -10, size: 100},
        'myfile2.txt': {lines_inserted: 10},
      };
      assert.deepEqual(element._patchChange, {
        inserted: 10,
        deleted: 5,
        size_delta_inserted: 10,
        size_delta_deleted: -5,
        total_size: 220,
      });
      assert.isFalse(element._hideBinaryChangeTotals);
      assert.isFalse(element._hideChangeTotals);
    });

    test('_formatBytes function', () => {
      const table = {
        '64': '+64 B',
        '1023': '+1023 B',
        '1024': '+1 KiB',
        '4096': '+4 KiB',
        '1073741824': '+1 GiB',
        '-64': '-64 B',
        '-1023': '-1023 B',
        '-1024': '-1 KiB',
        '-4096': '-4 KiB',
        '-1073741824': '-1 GiB',
        '0': '+/-0 B',
      };

      for (const bytes in table) {
        if (table.hasOwnProperty(bytes)) {
          assert.equal(element._formatBytes(Number(bytes)), table[bytes]);
        }
      }
    });

    test('_formatPercentage function', () => {
      const table = [
        {size: 100,
          delta: 100,
          display: '',
        },
        {size: 195060,
          delta: 64,
          display: '(+0%)',
        },
        {size: 195060,
          delta: -64,
          display: '(-0%)',
        },
        {size: 394892,
          delta: -7128,
          display: '(-2%)',
        },
        {size: 90,
          delta: -10,
          display: '(-10%)',
        },
        {size: 110,
          delta: 10,
          display: '(+10%)',
        },
      ];

      for (const item of table) {
        assert.equal(element._formatPercentage(
            item.size, item.delta), item.display);
      }
    });

    test('comment filtering', () => {
      const comments = {
        '/COMMIT_MSG': [
          {
            patch_set: 1,
            message: 'Done',
            updated: '2017-02-08 16:40:49',
            id: '1',
          },
          {
            patch_set: 1,
            message: 'oh hay',
            updated: '2017-02-09 16:40:49',
            id: '2',
          },
          {
            patch_set: 2,
            message: 'hello',
            updated: '2017-02-10 16:40:49',
            id: '3',
          },
        ],
        'myfile.txt': [
          {
            patch_set: 1,
            message: 'good news!',
            updated: '2017-02-08 16:40:49',
            id: '4',
          },
          {
            patch_set: 2,
            message: 'wat!?',
            updated: '2017-02-09 16:40:49',
            id: '5',
          },
          {
            patch_set: 2,
            message: 'hi',
            updated: '2017-02-10 16:40:49',
            id: '6',
          },
        ],
        'unresolved.file': [
          {
            patch_set: 2,
            message: 'wat!?',
            updated: '2017-02-09 16:40:49',
            id: '7',
            unresolved: true,
          },
          {
            patch_set: 2,
            message: 'hi',
            updated: '2017-02-10 16:40:49',
            id: '8',
            in_reply_to: '7',
            unresolved: false,
          },
          {
            patch_set: 2,
            message: 'good news!',
            updated: '2017-02-08 16:40:49',
            id: '9',
            unresolved: true,
          },
        ],
      };
      const drafts = {
        '/COMMIT_MSG': [
          {
            patch_set: 1,
            message: 'hi',
            updated: '2017-02-15 16:40:49',
            id: '10',
            unresolved: true,
          },
          {
            patch_set: 1,
            message: 'fyi',
            updated: '2017-02-15 16:40:49',
            id: '11',
            unresolved: false,
          },
        ],
        'unresolved.file': [
          {
            patch_set: 1,
            message: 'hi',
            updated: '2017-02-11 16:40:49',
            id: '12',
            unresolved: false,
          },
        ],
      };
      element.changeComments = new ChangeComments(comments, {}, drafts, 123);

      const parentTo1 = {
        basePatchNum: 'PARENT',
        patchNum: 1,
      };

      const parentTo2 = {
        basePatchNum: 'PARENT',
        patchNum: 2,
      };

      const _1To2 = {
        basePatchNum: 1,
        patchNum: 2,
      };

      assert.equal(
          element._computeCommentsString(element.changeComments, parentTo1,
              '/COMMIT_MSG', 'comment'), '2 comments (1 unresolved)');
      assert.equal(
          element._computeCommentsString(element.changeComments, _1To2,
              '/COMMIT_MSG', 'comment'), '3 comments (1 unresolved)');
      assert.equal(
          element._computeCommentsStringMobile(element.changeComments, parentTo1
              , '/COMMIT_MSG'), '2c');
      assert.equal(
          element._computeCommentsStringMobile(element.changeComments, _1To2
              , '/COMMIT_MSG'), '3c');
      assert.equal(
          element._computeDraftsString(element.changeComments, parentTo1,
              'unresolved.file'), '1 draft');
      assert.equal(
          element._computeDraftsString(element.changeComments, _1To2,
              'unresolved.file'), '1 draft');
      assert.equal(
          element._computeDraftsStringMobile(element.changeComments, parentTo1,
              'unresolved.file'), '1d');
      assert.equal(
          element._computeDraftsStringMobile(element.changeComments, _1To2,
              'unresolved.file'), '1d');
      assert.equal(
          element._computeCommentsString(element.changeComments, parentTo1,
              'myfile.txt', 'comment'), '1 comment');
      assert.equal(
          element._computeCommentsString(element.changeComments, _1To2,
              'myfile.txt', 'comment'), '3 comments');
      assert.equal(
          element._computeCommentsStringMobile(
              element.changeComments,
              parentTo1,
              'myfile.txt'
          ), '1c');
      assert.equal(
          element._computeCommentsStringMobile(element.changeComments, _1To2,
              'myfile.txt'), '3c');
      assert.equal(
          element._computeDraftsString(element.changeComments, parentTo1,
              'myfile.txt'), '');
      assert.equal(
          element._computeDraftsString(element.changeComments, _1To2,
              'myfile.txt'), '');
      assert.equal(
          element._computeDraftsStringMobile(element.changeComments, parentTo1,
              'myfile.txt'), '');
      assert.equal(
          element._computeDraftsStringMobile(element.changeComments, _1To2,
              'myfile.txt'), '');
      assert.equal(
          element._computeCommentsString(element.changeComments, parentTo1,
              'file_added_in_rev2.txt', 'comment'), '');
      assert.equal(
          element._computeCommentsString(element.changeComments, _1To2,
              'file_added_in_rev2.txt', 'comment'), '');
      assert.equal(
          element._computeCommentsStringMobile(
              element.changeComments,
              parentTo1,
              'file_added_in_rev2.txt'
          ), '');
      assert.equal(
          element._computeCommentsStringMobile(element.changeComments, _1To2,
              'file_added_in_rev2.txt'), '');
      assert.equal(
          element._computeDraftsString(element.changeComments, parentTo1,
              'file_added_in_rev2.txt'), '');
      assert.equal(
          element._computeDraftsString(element.changeComments, _1To2,
              'file_added_in_rev2.txt'), '');
      assert.equal(
          element._computeDraftsStringMobile(element.changeComments, parentTo1,
              'file_added_in_rev2.txt'), '');
      assert.equal(
          element._computeDraftsStringMobile(element.changeComments, _1To2,
              'file_added_in_rev2.txt'), '');
      assert.equal(
          element._computeCommentsString(element.changeComments, parentTo2,
              '/COMMIT_MSG', 'comment'), '1 comment');
      assert.equal(
          element._computeCommentsString(element.changeComments, _1To2,
              '/COMMIT_MSG', 'comment'), '3 comments (1 unresolved)');
      assert.equal(
          element._computeCommentsStringMobile(
              element.changeComments,
              parentTo2,
              '/COMMIT_MSG'
          ), '1c');
      assert.equal(
          element._computeCommentsStringMobile(element.changeComments, _1To2,
              '/COMMIT_MSG'), '3c');
      assert.equal(
          element._computeDraftsString(element.changeComments, parentTo1,
              '/COMMIT_MSG'), '2 drafts');
      assert.equal(
          element._computeDraftsString(element.changeComments, _1To2,
              '/COMMIT_MSG'), '2 drafts');
      assert.equal(
          element._computeDraftsStringMobile(
              element.changeComments,
              parentTo1,
              '/COMMIT_MSG'
          ), '2d');
      assert.equal(
          element._computeDraftsStringMobile(element.changeComments, _1To2,
              '/COMMIT_MSG'), '2d');
      assert.equal(
          element._computeCommentsString(element.changeComments, parentTo2,
              'myfile.txt', 'comment'), '2 comments');
      assert.equal(
          element._computeCommentsString(element.changeComments, _1To2,
              'myfile.txt', 'comment'), '3 comments');
      assert.equal(
          element._computeCommentsStringMobile(
              element.changeComments,
              parentTo2,
              'myfile.txt'
          ), '2c');
      assert.equal(
          element._computeCommentsStringMobile(element.changeComments, _1To2,
              'myfile.txt'), '3c');
      assert.equal(
          element._computeDraftsStringMobile(element.changeComments, parentTo2,
              'myfile.txt'), '');
      assert.equal(
          element._computeDraftsStringMobile(element.changeComments, _1To2,
              'myfile.txt'), '');
      assert.equal(
          element._computeCommentsString(element.changeComments, parentTo2,
              'file_added_in_rev2.txt', 'comment'), '');
      assert.equal(
          element._computeCommentsString(element.changeComments, _1To2,
              'file_added_in_rev2.txt', 'comment'), '');
      assert.equal(
          element._computeCommentsString(element.changeComments, parentTo2,
              'unresolved.file', 'comment'), '2 comments (1 unresolved)');
      assert.equal(
          element._computeCommentsString(element.changeComments, _1To2,
              'unresolved.file', 'comment'), '2 comments (1 unresolved)');
    });

    test('_reviewedTitle', () => {
      assert.equal(
          element._reviewedTitle(true), 'Mark as not reviewed (shortcut: r)');

      assert.equal(
          element._reviewedTitle(false), 'Mark as reviewed (shortcut: r)');
    });

    suite('keyboard shortcuts', () => {
      setup(() => {
        element._filesByPath = {
          '/COMMIT_MSG': {},
          'file_added_in_rev2.txt': {},
          'myfile.txt': {},
        };
        element.changeNum = '42';
        element.patchRange = {
          basePatchNum: 'PARENT',
          patchNum: 2,
        };
        element.change = {_number: 42};
        element.$.fileCursor.setCursorAtIndex(0);
      });

      test('toggle left diff via shortcut', () => {
        const toggleLeftDiffStub = sinon.stub();
        // Property getter cannot be stubbed w/ sandbox due to a bug in Sinon.
        // https://github.com/sinonjs/sinon/issues/781
        const diffsStub = sinon.stub(element, 'diffs')
            .get(() => [{toggleLeftDiff: toggleLeftDiffStub}]);
        MockInteractions.pressAndReleaseKeyOn(element, 65, 'shift', 'a');
        assert.isTrue(toggleLeftDiffStub.calledOnce);
        diffsStub.restore();
      });

      test('keyboard shortcuts', () => {
        flush();

        const items = [...element.root.querySelectorAll('.file-row')];
        element.$.fileCursor.stops = items;
        element.$.fileCursor.setCursorAtIndex(0);
        assert.equal(items.length, 3);
        assert.isTrue(items[0].classList.contains('selected'));
        assert.isFalse(items[1].classList.contains('selected'));
        assert.isFalse(items[2].classList.contains('selected'));
        // j with a modifier should not move the cursor.
        MockInteractions.pressAndReleaseKeyOn(element, 74, 'shift', 'j');
        assert.equal(element.$.fileCursor.index, 0);
        // down should not move the cursor.
        MockInteractions.pressAndReleaseKeyOn(element, 40, null, 'down');
        assert.equal(element.$.fileCursor.index, 0);

        MockInteractions.pressAndReleaseKeyOn(element, 74, null, 'j');
        assert.equal(element.$.fileCursor.index, 1);
        assert.equal(element.selectedIndex, 1);
        MockInteractions.pressAndReleaseKeyOn(element, 74, null, 'j');

        const navStub = sinon.stub(GerritNav, 'navigateToDiff');
        assert.equal(element.$.fileCursor.index, 2);
        assert.equal(element.selectedIndex, 2);

        // k with a modifier should not move the cursor.
        MockInteractions.pressAndReleaseKeyOn(element, 75, 'shift', 'k');
        assert.equal(element.$.fileCursor.index, 2);

        // up should not move the cursor.
        MockInteractions.pressAndReleaseKeyOn(element, 38, null, 'down');
        assert.equal(element.$.fileCursor.index, 2);

        MockInteractions.pressAndReleaseKeyOn(element, 75, null, 'k');
        assert.equal(element.$.fileCursor.index, 1);
        assert.equal(element.selectedIndex, 1);
        MockInteractions.pressAndReleaseKeyOn(element, 79, null, 'o');

        assert(navStub.lastCall.calledWith(element.change,
            'file_added_in_rev2.txt', 2),
        'Should navigate to /c/42/2/file_added_in_rev2.txt');

        MockInteractions.pressAndReleaseKeyOn(element, 75, null, 'k');
        MockInteractions.pressAndReleaseKeyOn(element, 75, null, 'k');
        MockInteractions.pressAndReleaseKeyOn(element, 75, null, 'k');
        assert.equal(element.$.fileCursor.index, 0);
        assert.equal(element.selectedIndex, 0);

        const createCommentInPlaceStub = sinon.stub(element.$.diffCursor,
            'createCommentInPlace');
        MockInteractions.pressAndReleaseKeyOn(element, 67, null, 'c');
        assert.isTrue(createCommentInPlaceStub.called);
      });

      test('i key shows/hides selected inline diff', () => {
        const paths = Object.keys(element._filesByPath);
        sinon.stub(element, '_expandedFilesChanged');
        flush();
        const files = [...element.root.querySelectorAll('.file-row')];
        element.$.fileCursor.stops = files;
        element.$.fileCursor.setCursorAtIndex(0);
        assert.equal(element.diffs.length, 0);
        assert.equal(element._expandedFiles.length, 0);

        MockInteractions.keyUpOn(element, 73, null, 'i');
        flush();
        assert.equal(element.diffs.length, 1);
        assert.equal(element.diffs[0].path, paths[0]);
        assert.equal(element._expandedFiles.length, 1);
        assert.equal(element._expandedFiles[0].path, paths[0]);

        MockInteractions.keyUpOn(element, 73, null, 'i');
        flush();
        assert.equal(element.diffs.length, 0);
        assert.equal(element._expandedFiles.length, 0);

        element.$.fileCursor.setCursorAtIndex(1);
        MockInteractions.keyUpOn(element, 73, null, 'i');
        flush();
        assert.equal(element.diffs.length, 1);
        assert.equal(element.diffs[0].path, paths[1]);
        assert.equal(element._expandedFiles.length, 1);
        assert.equal(element._expandedFiles[0].path, paths[1]);

        MockInteractions.keyUpOn(element, 73, 'shift', 'i');
        flush();
        assert.equal(element.diffs.length, paths.length);
        assert.equal(element._expandedFiles.length, paths.length);
        for (const index in element.diffs) {
          if (!element.diffs.hasOwnProperty(index)) { continue; }
          assert.isTrue(
              element._expandedFiles
                  .some(f => f.path === element.diffs[index].path)
          );
        }

        MockInteractions.keyUpOn(element, 73, 'shift', 'i');
        flush();
        assert.equal(element.diffs.length, 0);
        assert.equal(element._expandedFiles.length, 0);
      });

      test('r key toggles reviewed flag', () => {
        const reducer = (accum, file) => (file.isReviewed ? ++accum : accum);
        const getNumReviewed = () => element._files.reduce(reducer, 0);
        flush();

        // Default state should be unreviewed.
        assert.equal(getNumReviewed(), 0);

        // Press the review key to toggle it (set the flag).
        MockInteractions.pressAndReleaseKeyOn(element, 82, null, 'r');
        flush();
        assert.equal(getNumReviewed(), 1);

        // Press the review key to toggle it (clear the flag).
        MockInteractions.pressAndReleaseKeyOn(element, 82, null, 'r');
        assert.equal(getNumReviewed(), 0);
      });

      suite('_handleOpenFile', () => {
        let interact;

        setup(() => {
          sinon.stub(element, 'shouldSuppressKeyboardShortcut')
              .returns(false);
          sinon.stub(element, 'modifierPressed').returns(false);
          const openCursorStub = sinon.stub(element, '_openCursorFile');
          const openSelectedStub = sinon.stub(element, '_openSelectedFile');
          const expandStub = sinon.stub(element, '_toggleFileExpanded');

          interact = function(opt_payload) {
            openCursorStub.reset();
            openSelectedStub.reset();
            expandStub.reset();

            const e = new CustomEvent('fake-keyboard-event', opt_payload);
            sinon.stub(e, 'preventDefault');
            element._handleOpenFile(e);
            assert.isTrue(e.preventDefault.called);
            const result = {};
            if (openCursorStub.called) {
              result.opened_cursor = true;
            }
            if (openSelectedStub.called) {
              result.opened_selected = true;
            }
            if (expandStub.called) {
              result.expanded = true;
            }
            return result;
          };
        });

        test('open from selected file', () => {
          element._showInlineDiffs = false;
          assert.deepEqual(interact(), {opened_selected: true});
        });

        test('open from diff cursor', () => {
          element._showInlineDiffs = true;
          assert.deepEqual(interact(), {opened_cursor: true});
        });

        test('expand when user prefers', () => {
          element._showInlineDiffs = false;
          assert.deepEqual(interact(), {opened_selected: true});
          element._userPrefs = {};
          assert.deepEqual(interact(), {opened_selected: true});
        });
      });

      test('shift+left/shift+right', () => {
        const moveLeftStub = sinon.stub(element.$.diffCursor, 'moveLeft');
        const moveRightStub = sinon.stub(element.$.diffCursor, 'moveRight');

        let noDiffsExpanded = true;
        sinon.stub(element, '_noDiffsExpanded')
            .callsFake(() => noDiffsExpanded);

        MockInteractions.pressAndReleaseKeyOn(element, 73, 'shift', 'left');
        assert.isFalse(moveLeftStub.called);
        MockInteractions.pressAndReleaseKeyOn(element, 73, 'shift', 'right');
        assert.isFalse(moveRightStub.called);

        noDiffsExpanded = false;

        MockInteractions.pressAndReleaseKeyOn(element, 73, 'shift', 'left');
        assert.isTrue(moveLeftStub.called);
        MockInteractions.pressAndReleaseKeyOn(element, 73, 'shift', 'right');
        assert.isTrue(moveRightStub.called);
      });
    });

    test('computed properties', () => {
      assert.equal(element._computeFileStatus('A'), 'A');
      assert.equal(element._computeFileStatus(undefined), 'M');
      assert.equal(element._computeFileStatus(null), 'M');

      assert.equal(element._computeClass('clazz', '/foo/bar/baz'), 'clazz');
      assert.equal(element._computeClass('clazz', '/COMMIT_MSG'),
          'clazz invisible');
    });

    test('file review status', () => {
      element._reviewed = ['/COMMIT_MSG', 'myfile.txt'];
      element._filesByPath = {
        '/COMMIT_MSG': {},
        'file_added_in_rev2.txt': {},
        'myfile.txt': {},
      };
      element._loggedIn = true;
      element.changeNum = '42';
      element.patchRange = {
        basePatchNum: 'PARENT',
        patchNum: 2,
      };
      element.$.fileCursor.setCursorAtIndex(0);
      const reviewSpy = sinon.spy(element, '_reviewFile');
      const toggleExpandSpy = sinon.spy(element, '_toggleFileExpanded');

      flush();
      const fileRows =
          element.root.querySelectorAll('.row:not(.header-row)');
      const checkSelector = 'span.reviewedSwitch[role="switch"]';
      const commitMsg = fileRows[0].querySelector(checkSelector);
      const fileAdded = fileRows[1].querySelector(checkSelector);
      const myFile = fileRows[2].querySelector(checkSelector);

      assert.equal(commitMsg.getAttribute('aria-checked'), 'true');
      assert.equal(fileAdded.getAttribute('aria-checked'), 'false');
      assert.equal(myFile.getAttribute('aria-checked'), 'true');

      const commitReviewLabel = fileRows[0].querySelector('.reviewedLabel');
      const markReviewLabel = fileRows[0].querySelector('.markReviewed');
      assert.isTrue(commitReviewLabel.classList.contains('isReviewed'));
      assert.equal(markReviewLabel.textContent, 'MARK UNREVIEWED');

      const clickSpy = sinon.spy(element, '_reviewedClick');
      MockInteractions.tap(markReviewLabel);
      // assert.isTrue(saveStub.lastCall.calledWithExactly('/COMMIT_MSG', false));
      // assert.isFalse(commitReviewLabel.classList.contains('isReviewed'));
      assert.equal(markReviewLabel.textContent, 'MARK REVIEWED');
      assert.isTrue(clickSpy.lastCall.args[0].defaultPrevented);
      assert.isTrue(reviewSpy.calledOnce);

      MockInteractions.tap(markReviewLabel);
      assert.isTrue(saveStub.lastCall.calledWithExactly('/COMMIT_MSG', true));
      assert.isTrue(commitReviewLabel.classList.contains('isReviewed'));
      assert.equal(markReviewLabel.textContent, 'MARK UNREVIEWED');
      assert.isTrue(clickSpy.lastCall.args[0].defaultPrevented);
      assert.isTrue(reviewSpy.calledTwice);

      assert.isFalse(toggleExpandSpy.called);
    });

    test('_computeFileStatusLabel', () => {
      assert.equal(element._computeFileStatusLabel('A'), 'Added');
      assert.equal(element._computeFileStatusLabel('M'), 'Modified');
    });

    test('_handleFileListClick', () => {
      element._filesByPath = {
        '/COMMIT_MSG': {},
        'f1.txt': {},
        'f2.txt': {},
      };
      element.changeNum = '42';
      element.patchRange = {
        basePatchNum: 'PARENT',
        patchNum: 2,
      };

      const clickSpy = sinon.spy(element, '_handleFileListClick');
      const reviewStub = sinon.stub(element, '_reviewFile');
      const toggleExpandSpy = sinon.spy(element, '_toggleFileExpanded');

      const row = dom(element.root)
          .querySelector(`.row[data-file='{"path":"f1.txt"}']`);

      // Click on the expand button, resulting in _toggleFileExpanded being
      // called and not resulting in a call to _reviewFile.
      row.querySelector('div.show-hide').click();
      assert.isTrue(clickSpy.calledOnce);
      assert.isTrue(toggleExpandSpy.calledOnce);
      assert.isFalse(reviewStub.called);

      // Click inside the diff. This should result in no additional calls to
      // _toggleFileExpanded or _reviewFile.
      element.root.querySelector('gr-diff-host')
          .click();
      assert.isTrue(clickSpy.calledTwice);
      assert.isTrue(toggleExpandSpy.calledOnce);
      assert.isFalse(reviewStub.called);
    });

    test('_handleFileListClick editMode', () => {
      element._filesByPath = {
        '/COMMIT_MSG': {},
        'f1.txt': {},
        'f2.txt': {},
      };
      element.changeNum = '42';
      element.patchRange = {
        basePatchNum: 'PARENT',
        patchNum: 2,
      };
      element.editMode = true;
      flush();
      const clickSpy = sinon.spy(element, '_handleFileListClick');
      const toggleExpandSpy = sinon.spy(element, '_toggleFileExpanded');

      // Tap the edit controls. Should be ignored by _handleFileListClick.
      MockInteractions.tap(element.shadowRoot
          .querySelector('.editFileControls'));
      assert.isTrue(clickSpy.calledOnce);
      assert.isFalse(toggleExpandSpy.called);
    });

    test('checkbox shows/hides diff inline', () => {
      element._filesByPath = {
        'myfile.txt': {},
      };
      element.changeNum = '42';
      element.patchRange = {
        basePatchNum: 'PARENT',
        patchNum: 2,
      };
      element.$.fileCursor.setCursorAtIndex(0);
      sinon.stub(element, '_expandedFilesChanged');
      flush();
      const fileRows =
          element.root.querySelectorAll('.row:not(.header-row)');
      // Because the label surrounds the input, the tap event is triggered
      // there first.
      const showHideCheck = fileRows[0].querySelector(
          'span.show-hide[role="switch"]');
      const showHideLabel = showHideCheck.querySelector('.show-hide-icon');
      assert.equal(showHideCheck.getAttribute('aria-checked'), 'false');
      MockInteractions.tap(showHideLabel);
      assert.equal(showHideCheck.getAttribute('aria-checked'), 'true');
      assert.notEqual(
          element._expandedFiles.findIndex(f => f.path === 'myfile.txt'),
          -1);
    });

    test('diff mode correctly toggles the diffs', () => {
      element._filesByPath = {
        'myfile.txt': {},
      };
      element.changeNum = '42';
      element.patchRange = {
        basePatchNum: 'PARENT',
        patchNum: 2,
      };
      sinon.spy(element, '_updateDiffPreferences');
      element.$.fileCursor.setCursorAtIndex(0);
      flush();

      // Tap on a file to generate the diff.
      const row = dom(element.root)
          .querySelectorAll('.row:not(.header-row) span.show-hide')[0];

      MockInteractions.tap(row);
      flush();
      const diffDisplay = element.diffs[0];
      element._userPrefs = {default_diff_view: 'SIDE_BY_SIDE'};
      element.set('diffViewMode', 'UNIFIED_DIFF');
      assert.equal(diffDisplay.viewMode, 'UNIFIED_DIFF');
      assert.isTrue(element._updateDiffPreferences.called);
    });

    test('expanded attribute not set on path when not expanded', () => {
      element._filesByPath = {
        '/COMMIT_MSG': {},
      };
      assert.isNotOk(element.shadowRoot
          .querySelector('.expanded'));
    });

    test('tapping row ignores links', () => {
      element._filesByPath = {
        '/COMMIT_MSG': {},
      };
      element.changeNum = '42';
      element.patchRange = {
        basePatchNum: 'PARENT',
        patchNum: 2,
      };
      sinon.stub(element, '_expandedFilesChanged');
      flush();
      const commitMsgFile = dom(element.root)
          .querySelectorAll('.row:not(.header-row) a.pathLink')[0];

      // Remove href attribute so the app doesn't route to a diff view
      commitMsgFile.removeAttribute('href');
      const togglePathSpy = sinon.spy(element, '_toggleFileExpanded');

      MockInteractions.tap(commitMsgFile);
      flush();
      assert(togglePathSpy.notCalled, 'file is opened as diff view');
      assert.isNotOk(element.shadowRoot
          .querySelector('.expanded'));
      assert.notEqual(getComputedStyle(element.shadowRoot
          .querySelector('.show-hide')).display,
      'none');
    });

    test('_toggleFileExpanded', () => {
      const path = 'path/to/my/file.txt';
      element._filesByPath = {[path]: {}};
      const renderSpy = sinon.spy(element, '_renderInOrder');
      const collapseStub = sinon.stub(element, '_clearCollapsedDiffs');

      assert.equal(element.shadowRoot
          .querySelector('iron-icon').icon, 'gr-icons:expand-more');
      assert.equal(element._expandedFiles.length, 0);
      element._toggleFileExpanded({path});
      flush();
      assert.equal(collapseStub.lastCall.args[0].length, 0);
      assert.equal(element.shadowRoot
          .querySelector('iron-icon').icon, 'gr-icons:expand-less');

      assert.equal(renderSpy.callCount, 1);
      assert.isTrue(element._expandedFiles.some(f => f.path === path));
      element._toggleFileExpanded({path});
      flush();

      assert.equal(element.shadowRoot
          .querySelector('iron-icon').icon, 'gr-icons:expand-more');
      assert.equal(renderSpy.callCount, 1);
      assert.isFalse(element._expandedFiles.some(f => f.path === path));
      assert.equal(collapseStub.lastCall.args[0].length, 1);
    });

    test('expandAllDiffs and collapseAllDiffs', () => {
      const collapseStub = sinon.stub(element, '_clearCollapsedDiffs');
      const cursorUpdateStub = sinon.stub(element.$.diffCursor,
          'handleDiffUpdate');
      const reInitStub = sinon.stub(element.$.diffCursor,
          'reInitAndUpdateStops');

      const path = 'path/to/my/file.txt';
      element._filesByPath = {[path]: {}};
      element.expandAllDiffs();
      flush();
      assert.isTrue(element._showInlineDiffs);
      assert.isTrue(reInitStub.calledOnce);
      assert.equal(collapseStub.lastCall.args[0].length, 0);

      element.collapseAllDiffs();
      flush();
      assert.equal(element._expandedFiles.length, 0);
      assert.isFalse(element._showInlineDiffs);
      assert.isTrue(cursorUpdateStub.calledOnce);
      assert.equal(collapseStub.lastCall.args[0].length, 1);
    });

    test('_expandedFilesChanged', done => {
      sinon.stub(element, '_reviewFile');
      const path = 'path/to/my/file.txt';
      const diffs = [{
        path,
        style: {},
        reload() {
          done();
        },
        prefetchDiff() {},
        cancel() {},
        getCursorStops() { return []; },
        addEventListener(eventName, callback) {
          if (['render-start', 'render-content', 'scroll']
              .indexOf(eventName) >= 0) {
            callback(new Event(eventName));
          }
        },
      }];
      sinon.stub(element, 'diffs').get(() => diffs);
      element.push('_expandedFiles', {path});
    });

    test('_clearCollapsedDiffs', () => {
      const diff = {
        cancel: sinon.stub(),
        clearDiffContent: sinon.stub(),
      };
      element._clearCollapsedDiffs([diff]);
      assert.isTrue(diff.cancel.calledOnce);
      assert.isTrue(diff.clearDiffContent.calledOnce);
    });

    test('filesExpanded value updates to correct enum', () => {
      element._filesByPath = {
        'foo.bar': {},
        'baz.bar': {},
      };
      flush();
      assert.equal(element.filesExpanded,
          FilesExpandedState.NONE);
      element.push('_expandedFiles', {path: 'baz.bar'});
      flush();
      assert.equal(element.filesExpanded,
          FilesExpandedState.SOME);
      element.push('_expandedFiles', {path: 'foo.bar'});
      flush();
      assert.equal(element.filesExpanded,
          FilesExpandedState.ALL);
      element.collapseAllDiffs();
      flush();
      assert.equal(element.filesExpanded,
          FilesExpandedState.NONE);
      element.expandAllDiffs();
      flush();
      assert.equal(element.filesExpanded,
          FilesExpandedState.ALL);
    });

    test('_renderInOrder', done => {
      const reviewStub = sinon.stub(element, '_reviewFile');
      let callCount = 0;
      const diffs = [{
        path: 'p0',
        style: {},
        prefetchDiff() {},
        reload() {
          assert.equal(callCount++, 2);
          return Promise.resolve();
        },
      }, {
        path: 'p1',
        style: {},
        prefetchDiff() {},
        reload() {
          assert.equal(callCount++, 1);
          return Promise.resolve();
        },
      }, {
        path: 'p2',
        style: {},
        prefetchDiff() {},
        reload() {
          assert.equal(callCount++, 0);
          return Promise.resolve();
        },
      }];
      element._renderInOrder([
        {path: 'p2'}, {path: 'p1'}, {path: 'p0'},
      ], diffs, 3)
          .then(() => {
            assert.isFalse(reviewStub.called);
            assert.isTrue(loadCommentSpy.called);
            done();
          });
    });

    test('_renderInOrder logged in', done => {
      element._loggedIn = true;
      const reviewStub = sinon.stub(element, '_reviewFile');
      let callCount = 0;
      const diffs = [{
        path: 'p0',
        style: {},
        prefetchDiff() {},
        reload() {
          assert.equal(reviewStub.callCount, 2);
          assert.equal(callCount++, 2);
          return Promise.resolve();
        },
      }, {
        path: 'p1',
        style: {},
        prefetchDiff() {},
        reload() {
          assert.equal(reviewStub.callCount, 1);
          assert.equal(callCount++, 1);
          return Promise.resolve();
        },
      }, {
        path: 'p2',
        style: {},
        prefetchDiff() {},
        reload() {
          assert.equal(reviewStub.callCount, 0);
          assert.equal(callCount++, 0);
          return Promise.resolve();
        },
      }];
      element._renderInOrder([
        {path: 'p2'}, {path: 'p1'}, {path: 'p0'},
      ], diffs, 3)
          .then(() => {
            assert.equal(reviewStub.callCount, 3);
            done();
          });
    });

    test('_renderInOrder respects diffPrefs.manual_review', () => {
      element._loggedIn = true;
      element.diffPrefs = {manual_review: true};
      const reviewStub = sinon.stub(element, '_reviewFile');
      const diffs = [{
        path: 'p',
        style: {},
        prefetchDiff() {},
        reload() { return Promise.resolve(); },
      }];

      return element._renderInOrder([{path: 'p'}], diffs, 1).then(() => {
        assert.isFalse(reviewStub.called);
        delete element.diffPrefs.manual_review;
        return element._renderInOrder([{path: 'p'}], diffs, 1).then(() => {
          assert.isTrue(reviewStub.called);
          assert.isTrue(reviewStub.calledWithExactly('p', true));
        });
      });
    });

    test('_loadingChanged fired from reload in debouncer', done => {
      sinon.stub(element, '_getReviewedFiles').returns(Promise.resolve([]));
      element.changeNum = 123;
      element.patchRange = {patchNum: 12};
      element._filesByPath = {'foo.bar': {}};

      element.reload().then(() => {
        assert.isFalse(element._loading);
        element.flushDebouncer('loading-change');
        assert.isFalse(element.classList.contains('loading'));
        done();
      });
      assert.isTrue(element._loading);
      assert.isFalse(element.classList.contains('loading'));
      element.flushDebouncer('loading-change');
      assert.isTrue(element.classList.contains('loading'));
    });

    test('_loadingChanged does not set class when there are no files', () => {
      sinon.stub(element, '_getReviewedFiles').returns(Promise.resolve([]));
      element.changeNum = 123;
      element.patchRange = {patchNum: 12};
      element.reload();
      assert.isTrue(element._loading);
      element.flushDebouncer('loading-change');
      assert.isFalse(element.classList.contains('loading'));
    });
  });

  suite('diff url file list', () => {
    test('diff url', () => {
      const diffStub = sinon.stub(GerritNav, 'getUrlForDiff')
          .returns('/c/gerrit/+/1/1/index.php');
      const change = {
        _number: 1,
        project: 'gerrit',
      };
      const path = 'index.php';
      const patchRange = {
        patchNum: 1,
      };
      assert.equal(
          element._computeDiffURL(change, patchRange, path, false),
          '/c/gerrit/+/1/1/index.php');
      diffStub.restore();
    });

    test('diff url commit msg', () => {
      const diffStub = sinon.stub(GerritNav, 'getUrlForDiff')
          .returns('/c/gerrit/+/1/1//COMMIT_MSG');
      const change = {
        _number: 1,
        project: 'gerrit',
      };
      const path = '/COMMIT_MSG';
      const patchRange = {
        patchNum: 1,
      };
      assert.equal(
          element._computeDiffURL(change, patchRange, path, false),
          '/c/gerrit/+/1/1//COMMIT_MSG');
      diffStub.restore();
    });

    test('edit url', () => {
      const editStub = sinon.stub(GerritNav, 'getEditUrlForDiff')
          .returns('/c/gerrit/+/1/edit/index.php,edit');
      const change = {
        _number: 1,
        project: 'gerrit',
      };
      const path = 'index.php';
      const patchRange = {
        patchNum: 1,
      };
      assert.equal(
          element._computeDiffURL(change, patchRange, path, true),
          '/c/gerrit/+/1/edit/index.php,edit');
      editStub.restore();
    });

    test('edit url commit msg', () => {
      const editStub = sinon.stub(GerritNav, 'getEditUrlForDiff')
          .returns('/c/gerrit/+/1/edit//COMMIT_MSG,edit');
      const change = {
        _number: 1,
        project: 'gerrit',
      };
      const path = '/COMMIT_MSG';
      const patchRange = {
        patchNum: 1,
      };
      assert.equal(
          element._computeDiffURL(change, patchRange, path, true),
          '/c/gerrit/+/1/edit//COMMIT_MSG,edit');
      editStub.restore();
    });
  });

  suite('size bars', () => {
    test('_computeSizeBarLayout', () => {
      const defaultSizeBarLayout = {
        maxInserted: 0,
        maxDeleted: 0,
        maxAdditionWidth: 0,
        maxDeletionWidth: 0,
        deletionOffset: 0,
      };

      assert.deepEqual(
          element._computeSizeBarLayout(null),
          defaultSizeBarLayout);
      assert.deepEqual(
          element._computeSizeBarLayout({}),
          defaultSizeBarLayout);
      assert.deepEqual(
          element._computeSizeBarLayout({base: []}),
          defaultSizeBarLayout);

      const files = [
        {__path: '/COMMIT_MSG', lines_inserted: 10000},
        {__path: 'foo', lines_inserted: 4, lines_deleted: 10},
        {__path: 'bar', lines_inserted: 5, lines_deleted: 8},
      ];
      const layout = element._computeSizeBarLayout({base: files});
      assert.equal(layout.maxInserted, 5);
      assert.equal(layout.maxDeleted, 10);
    });

    test('_computeBarAdditionWidth', () => {
      const file = {
        __path: 'foo/bar.baz',
        lines_inserted: 5,
        lines_deleted: 0,
      };
      const stats = {
        maxInserted: 10,
        maxDeleted: 0,
        maxAdditionWidth: 60,
        maxDeletionWidth: 0,
        deletionOffset: 60,
      };

      // Uses half the space when file is half the largest addition and there
      // are no deletions.
      assert.equal(element._computeBarAdditionWidth(file, stats), 30);

      // If there are no insetions, there is no width.
      stats.maxInserted = 0;
      assert.equal(element._computeBarAdditionWidth(file, stats), 0);

      // If the insertions is not present on the file, there is no width.
      stats.maxInserted = 10;
      file.lines_inserted = undefined;
      assert.equal(element._computeBarAdditionWidth(file, stats), 0);

      // If the file is a commit message, returns zero.
      file.lines_inserted = 5;
      file.__path = '/COMMIT_MSG';
      assert.equal(element._computeBarAdditionWidth(file, stats), 0);

      // Width bottoms-out at the minimum width.
      file.__path = 'stuff.txt';
      file.lines_inserted = 1;
      stats.maxInserted = 1000000;
      assert.equal(element._computeBarAdditionWidth(file, stats), 1.5);
    });

    test('_computeBarAdditionX', () => {
      const file = {
        __path: 'foo/bar.baz',
        lines_inserted: 5,
        lines_deleted: 0,
      };
      const stats = {
        maxInserted: 10,
        maxDeleted: 0,
        maxAdditionWidth: 60,
        maxDeletionWidth: 0,
        deletionOffset: 60,
      };
      assert.equal(element._computeBarAdditionX(file, stats), 30);
    });

    test('_computeBarDeletionWidth', () => {
      const file = {
        __path: 'foo/bar.baz',
        lines_inserted: 0,
        lines_deleted: 5,
      };
      const stats = {
        maxInserted: 10,
        maxDeleted: 10,
        maxAdditionWidth: 30,
        maxDeletionWidth: 30,
        deletionOffset: 31,
      };

      // Uses a quarter the space when file is half the largest deletions and
      // there are equal additions.
      assert.equal(element._computeBarDeletionWidth(file, stats), 15);

      // If there are no deletions, there is no width.
      stats.maxDeleted = 0;
      assert.equal(element._computeBarDeletionWidth(file, stats), 0);

      // If the deletions is not present on the file, there is no width.
      stats.maxDeleted = 10;
      file.lines_deleted = undefined;
      assert.equal(element._computeBarDeletionWidth(file, stats), 0);

      // If the file is a commit message, returns zero.
      file.lines_deleted = 5;
      file.__path = '/COMMIT_MSG';
      assert.equal(element._computeBarDeletionWidth(file, stats), 0);

      // Width bottoms-out at the minimum width.
      file.__path = 'stuff.txt';
      file.lines_deleted = 1;
      stats.maxDeleted = 1000000;
      assert.equal(element._computeBarDeletionWidth(file, stats), 1.5);
    });

    test('_computeSizeBarsClass', () => {
      assert.equal(element._computeSizeBarsClass(false, 'foo/bar.baz'),
          'sizeBars desktop hide');
      assert.equal(element._computeSizeBarsClass(true, '/COMMIT_MSG'),
          'sizeBars desktop invisible');
      assert.equal(element._computeSizeBarsClass(true, 'foo/bar.baz'),
          'sizeBars desktop ');
    });
  });

  suite('gr-file-list inline diff tests', () => {
    let element;

    const commitMsgComments = [
      {
        patch_set: 2,
        path: '/p',
        id: 'ecf0b9fa_fe1a5f62',
        line: 20,
        updated: '2018-02-08 18:49:18.000000000',
        message: 'another comment',
        unresolved: true,
      },
      {
        patch_set: 2,
        path: '/p',
        id: '503008e2_0ab203ee',
        line: 10,
        updated: '2018-02-14 22:07:43.000000000',
        message: 'a comment',
        unresolved: true,
      },
      {
        patch_set: 2,
        path: '/p',
        id: 'cc788d2c_cb1d728c',
        line: 20,
        in_reply_to: 'ecf0b9fa_fe1a5f62',
        updated: '2018-02-13 22:07:43.000000000',
        message: 'response',
        unresolved: true,
      },
    ];

    async function setupDiff(diff) {
      diff.comments = {
        left: diff.path === '/COMMIT_MSG' ? commitMsgComments : [],
        right: [],
        meta: {
          changeNum: 1,
          patchRange: {
            basePatchNum: 'PARENT',
            patchNum: 2,
          },
        },
      };
      diff.prefs = {
        context: 10,
        tab_size: 8,
        font_size: 12,
        line_length: 100,
        cursor_blink_rate: 0,
        line_wrapping: false,
        intraline_difference: true,
        show_line_endings: true,
        show_tabs: true,
        show_whitespace_errors: true,
        syntax_highlighting: true,
        auto_hide_diff_table_header: true,
        theme: 'DEFAULT',
        ignore_whitespace: 'IGNORE_NONE',
      };
      diff.diff = getMockDiffResponse();
      commentApiWrapper.loadComments().then(() => {
        sinon.stub(element.changeComments, 'getCommentsBySideForPath')
            .withArgs('/COMMIT_MSG', {
              basePatchNum: 'PARENT',
              patchNum: 2,
            })
            .returns(diff.comments);
      });
      await listenOnce(diff, 'render');
    }

    async function renderAndGetNewDiffs(index) {
      const diffs =
          element.root.querySelectorAll('gr-diff-host');

      for (let i = index; i < diffs.length; i++) {
        await setupDiff(diffs[i]);
      }

      element._updateDiffCursor();
      element.$.diffCursor.handleDiffUpdate();
      return diffs;
    }

    setup(done => {
      stub('gr-rest-api-interface', {
        getLoggedIn() { return Promise.resolve(true); },
        getPreferences() { return Promise.resolve({}); },
        getDiffComments() { return Promise.resolve({}); },
        getDiffRobotComments() { return Promise.resolve({}); },
        getDiffDrafts() { return Promise.resolve({}); },
      });
      stub('gr-date-formatter', {
        _loadTimeFormat() { return Promise.resolve(''); },
      });
      stub('gr-diff-host', {
        reload() { return Promise.resolve(); },
        prefetchDiff() {},
      });

      // Element must be wrapped in an element with direct access to the
      // comment API.
      commentApiWrapper = basicFixture.instantiate();
      element = commentApiWrapper.$.fileList;
      loadCommentSpy = sinon.spy(commentApiWrapper.$.commentAPI, 'loadAll');
      element.diffPrefs = {};
      element.change = {_number: 42, project: 'testRepo'};
      sinon.stub(element, '_reviewFile');

      // Stub methods on the changeComments object after changeComments has
      // been initialized.
      commentApiWrapper.loadComments().then(() => {
        sinon.stub(element.changeComments, 'getPaths').returns({});
        sinon.stub(element.changeComments, 'getCommentsBySideForPath')
            .returns({meta: {}, left: [], right: []});
        done();
      });
      element._loading = false;
      element.numFilesShown = 75;
      element.selectedIndex = 0;
      element._filesByPath = {
        '/COMMIT_MSG': {lines_inserted: 9},
        'file_added_in_rev2.txt': {
          lines_inserted: 1,
          lines_deleted: 1,
          size_delta: 10,
          size: 100,
        },
        'myfile.txt': {
          lines_inserted: 1,
          lines_deleted: 1,
          size_delta: 10,
          size: 100,
        },
      };
      element._reviewed = ['/COMMIT_MSG', 'myfile.txt'];
      element._loggedIn = true;
      element.changeNum = '42';
      element.patchRange = {
        basePatchNum: 'PARENT',
        patchNum: 2,
      };
      sinon.stub(window, 'fetch').callsFake(() => Promise.resolve());
      flush();
    });

    test('cursor with individually opened files', async () => {
      MockInteractions.keyUpOn(element, 73, null, 'i');
      flush();
      let diffs = await renderAndGetNewDiffs(0);
      const diffStops = diffs[0].getCursorStops();

      // 1 diff should be rendered.
      assert.equal(diffs.length, 1);

      // No line number is selected.
      assert.isFalse(diffStops[10].classList.contains('target-row'));

      // Tapping content on a line selects the line number.
      MockInteractions.tap(dom(
          diffStops[10]).querySelectorAll('.contentText')[0]);
      flush();
      assert.isTrue(diffStops[10].classList.contains('target-row'));

      // Keyboard shortcuts are still moving the file cursor, not the diff
      // cursor.
      MockInteractions.pressAndReleaseKeyOn(element, 74, null, 'j');
      flush();
      assert.isTrue(diffStops[10].classList.contains('target-row'));
      assert.isFalse(diffStops[11].classList.contains('target-row'));

      // The file cursor is now at 1.
      assert.equal(element.$.fileCursor.index, 1);
      MockInteractions.keyUpOn(element, 73, null, 'i');
      flush();

      diffs = await renderAndGetNewDiffs(1);
      // Two diffs should be rendered.
      assert.equal(diffs.length, 2);
      const diffStopsFirst = diffs[0].getCursorStops();
      const diffStopsSecond = diffs[1].getCursorStops();

      // The line on the first diff is still selected
      assert.isTrue(diffStopsFirst[10].classList.contains('target-row'));
      assert.isFalse(diffStopsSecond[10].classList.contains('target-row'));
    });

    test('cursor with toggle all files', async () => {
      MockInteractions.keyUpOn(element, 73, 'shift', 'i');
      flush();

      const diffs = await renderAndGetNewDiffs(0);
      const diffStops = diffs[0].getCursorStops();

      // 1 diff should be rendered.
      assert.equal(diffs.length, 3);

      // No line number is selected.
      assert.isFalse(diffStops[10].classList.contains('target-row'));

      // Tapping content on a line selects the line number.
      MockInteractions.tap(dom(
          diffStops[10]).querySelectorAll('.contentText')[0]);
      flush();
      assert.isTrue(diffStops[10].classList.contains('target-row'));

      // Keyboard shortcuts are still moving the file cursor, not the diff
      // cursor.
      MockInteractions.pressAndReleaseKeyOn(element, 74, null, 'j');
      flush();
      assert.isFalse(diffStops[10].classList.contains('target-row'));
      assert.isTrue(diffStops[11].classList.contains('target-row'));

      // The file cursor is still at 0.
      assert.equal(element.$.fileCursor.index, 0);
    });

    suite('n key presses', () => {
      let nKeySpy;
      let nextCommentStub;
      let nextChunkStub;
      let fileRows;

      setup(() => {
        sinon.stub(element, '_renderInOrder').returns(Promise.resolve());
        nKeySpy = sinon.spy(element, '_handleNextChunk');
        nextCommentStub = sinon.stub(element.$.diffCursor,
            'moveToNextCommentThread');
        nextChunkStub = sinon.stub(element.$.diffCursor,
            'moveToNextChunk');
        fileRows =
            element.root.querySelectorAll('.row:not(.header-row)');
      });

      test('n key with some files expanded and no shift key', () => {
        MockInteractions.keyUpOn(fileRows[0], 73, null, 'i');
        flush();

        // Handle N key should return before calling diff cursor functions.
        MockInteractions.pressAndReleaseKeyOn(element, 78, null, 'n');
        assert.isTrue(nKeySpy.called);
        assert.isFalse(nextCommentStub.called);

        // This is also called in diffCursor.moveToFirstChunk.
        assert.equal(nextChunkStub.callCount, 1);
        assert.equal(element.filesExpanded, 'some');
      });

      test('n key with some files expanded and shift key', () => {
        MockInteractions.keyUpOn(fileRows[0], 73, null, 'i');
        flush();
        assert.equal(nextChunkStub.callCount, 0);

        MockInteractions.pressAndReleaseKeyOn(element, 78, 'shift', 'n');
        assert.isTrue(nKeySpy.called);
        assert.isTrue(nextCommentStub.called);

        // This is also called in diffCursor.moveToFirstChunk.
        assert.equal(nextChunkStub.callCount, 0);
        assert.equal(element.filesExpanded, 'some');
      });

      test('n key without all files expanded and shift key', () => {
        MockInteractions.keyUpOn(fileRows[0], 73, 'shift', 'i');
        flush();

        MockInteractions.pressAndReleaseKeyOn(element, 78, null, 'n');
        assert.isTrue(nKeySpy.called);
        assert.isFalse(nextCommentStub.called);

        // This is also called in diffCursor.moveToFirstChunk.
        assert.equal(nextChunkStub.callCount, 1);
        assert.isTrue(element._showInlineDiffs);
      });

      test('n key without all files expanded and no shift key', () => {
        MockInteractions.keyUpOn(fileRows[0], 73, 'shift', 'i');
        flush();

        MockInteractions.pressAndReleaseKeyOn(element, 78, 'shift', 'n');
        assert.isTrue(nKeySpy.called);
        assert.isTrue(nextCommentStub.called);

        // This is also called in diffCursor.moveToFirstChunk.
        assert.equal(nextChunkStub.callCount, 0);
        assert.isTrue(element._showInlineDiffs);
      });
    });

    test('_openSelectedFile behavior', () => {
      const _filesByPath = element._filesByPath;
      element.set('_filesByPath', {});
      const navStub = sinon.stub(GerritNav, 'navigateToDiff');
      // Noop when there are no files.
      element._openSelectedFile();
      assert.isFalse(navStub.called);

      element.set('_filesByPath', _filesByPath);
      flush();
      // Navigates when a file is selected.
      element._openSelectedFile();
      assert.isTrue(navStub.called);
    });

    test('_displayLine', () => {
      sinon.stub(element, 'shouldSuppressKeyboardShortcut')
          .callsFake(() => false);
      sinon.stub(element, 'modifierPressed')
          .callsFake(() => false);
      element._showInlineDiffs = true;
      const mockEvent = {preventDefault() {}};

      element._displayLine = false;
      element._handleCursorNext(mockEvent);
      assert.isTrue(element._displayLine);

      element._displayLine = false;
      element._handleCursorPrev(mockEvent);
      assert.isTrue(element._displayLine);

      element._displayLine = true;
      element._handleEscKey(mockEvent);
      assert.isFalse(element._displayLine);
    });

    suite('editMode behavior', () => {
      test('reviewed checkbox', () => {
        element._reviewFile.restore();
        const saveReviewStub = sinon.stub(element, '_saveReviewedState');

        element.editMode = false;
        MockInteractions.pressAndReleaseKeyOn(element, 82, null, 'r');
        assert.isTrue(saveReviewStub.calledOnce);

        element.editMode = true;
        flush();

        MockInteractions.pressAndReleaseKeyOn(element, 82, null, 'r');
        assert.isTrue(saveReviewStub.calledOnce);
      });

      test('_getReviewedFiles does not call API', () => {
        const apiSpy = sinon.spy(element.$.restAPI, 'getReviewedFiles');
        element.editMode = true;
        return element._getReviewedFiles().then(files => {
          assert.equal(files.length, 0);
          assert.isFalse(apiSpy.called);
        });
      });
    });

    test('editing actions', () => {
      // Edit controls are guarded behind a dom-if initially and not rendered.
      assert.isNotOk(dom(element.root)
          .querySelector('gr-edit-file-controls'));

      element.editMode = true;
      flush();

      // Commit message should not have edit controls.
      const editControls =
          Array.from(
              dom(element.root)
                  .querySelectorAll('.row:not(.header-row)'))
              .map(row => row.querySelector('gr-edit-file-controls'));
      assert.isTrue(editControls[0].classList.contains('invisible'));
    });

    test('reloadCommentsForThreadWithRootId', async () => {
      // Expand the commit message diff
      MockInteractions.keyUpOn(element, 73, 'shift', 'i');
      const diffs = await renderAndGetNewDiffs(0);
      flush();

      // Two comment threads should be generated by renderAndGetNewDiffs
      const threadEls = diffs[0].getThreadEls();
      assert.equal(threadEls.length, 2);
      const threadElsByRootId = new Map(
          threadEls.map(threadEl => [threadEl.rootId, threadEl]));

      const thread1 = threadElsByRootId.get('503008e2_0ab203ee');
      assert.equal(thread1.comments.length, 1);
      assert.equal(thread1.comments[0].message, 'a comment');
      assert.equal(thread1.comments[0].line, 10);

      const thread2 = threadElsByRootId.get('ecf0b9fa_fe1a5f62');
      assert.equal(thread2.comments.length, 2);
      assert.isTrue(thread2.comments[0].unresolved);
      assert.equal(thread2.comments[0].message, 'another comment');
      assert.equal(thread2.comments[0].line, 20);

      const commentStub =
          sinon.stub(element.changeComments, 'getCommentsForThread');
      const commentStubRes1 = [
        {
          patch_set: 2,
          path: '/p',
          id: '503008e2_0ab203ee',
          line: 20,
          updated: '2018-02-08 18:49:18.000000000',
          message: 'edited text',
          unresolved: false,
        },
      ];
      const commentStubRes2 = [
        {
          patch_set: 2,
          path: '/p',
          id: 'ecf0b9fa_fe1a5f62',
          line: 20,
          updated: '2018-02-08 18:49:18.000000000',
          message: 'another comment',
          unresolved: true,
        },
        {
          patch_set: 2,
          path: '/p',
          id: '503008e2_0ab203ee',
          line: 10,
          in_reply_to: 'ecf0b9fa_fe1a5f62',
          updated: '2018-02-14 22:07:43.000000000',
          message: 'response',
          unresolved: true,
        },
        {
          patch_set: 2,
          path: '/p',
          id: '503008e2_0ab203ef',
          line: 20,
          in_reply_to: '503008e2_0ab203ee',
          updated: '2018-02-15 22:07:43.000000000',
          message: 'a third comment in the thread',
          unresolved: true,
        },
      ];
      commentStub.withArgs('503008e2_0ab203ee').returns(
          commentStubRes1);
      commentStub.withArgs('ecf0b9fa_fe1a5f62').returns(
          commentStubRes2);

      // Reload comments from the first comment thread, which should have a
      // an updated message and a toggled resolve state.
      element.reloadCommentsForThreadWithRootId('503008e2_0ab203ee',
          '/COMMIT_MSG');
      assert.equal(thread1.comments.length, 1);
      assert.isFalse(thread1.comments[0].unresolved);
      assert.equal(thread1.comments[0].message, 'edited text');

      // Reload comments from the second comment thread, which should have a new
      // reply.
      element.reloadCommentsForThreadWithRootId('ecf0b9fa_fe1a5f62',
          '/COMMIT_MSG');
      assert.equal(thread2.comments.length, 3);

      const commentStubCount = commentStub.callCount;
      const getThreadsSpy = sinon.spy(diffs[0], 'getThreadEls');

      // Should not be getting threads when the file is not expanded.
      element.reloadCommentsForThreadWithRootId('ecf0b9fa_fe1a5f62',
          'other/file');
      assert.isFalse(getThreadsSpy.called);
      assert.equal(commentStubCount, commentStub.callCount);

      // Should be query selecting diffs when the file is expanded.
      // Should not be fetching change comments when the rootId is not found
      // to match.
      element.reloadCommentsForThreadWithRootId('acf0b9fa_fe1a5f62',
          '/COMMIT_MSG');
      assert.isTrue(getThreadsSpy.called);
      assert.equal(commentStubCount, commentStub.callCount);
    });
  });
});

