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

import '../../../test/common-test-setup-karma';
import '../../diff/gr-comment-api/gr-comment-api';
import {getMockDiffResponse} from '../../../test/mocks/diff-response';
import {
  CommentApiMock,
  createCommentApiMockWithTemplateElement,
} from '../../../test/mocks/comment-api';
import {FilesExpandedState} from '../gr-file-list-constants';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {runA11yAudit} from '../../../test/a11y-test-utils';
import {html} from '@polymer/polymer/lib/utils/html-tag';
import {
  TestKeyboardShortcutBinder,
  stubRestApi,
  spyRestApi,
  listenOnce,
  queryAll,
  queryAndAssert,
  query,
  mockPromise,
} from '../../../test/test-utils';
import {Shortcut} from '../../../mixins/keyboard-shortcut-mixin/keyboard-shortcut-mixin';
import {createCommentThreads, UIComment} from '../../../utils/comment-util';
import {
  createChangeComments,
  createParsedChange,
  createPreferences,
} from '../../../test/test-data-generators';
import {GrFileList, NormalizedFileInfo} from './gr-file-list';
import './gr-file-list';
import {
  BasePatchSetNum,
  NumericChangeId,
  PatchSetNum,
  RepoName,
  RevisionPatchSetNum,
  Timestamp,
  UrlEncodedCommentId,
} from '../../../types/common';
import * as MockInteractions from '@polymer/iron-test-helpers/mock-interactions';
import {GrDiffHost} from '../../diff/gr-diff-host/gr-diff-host';
import {GrCommentApi} from '../../diff/gr-comment-api/gr-comment-api';
import {IronIconElement} from '@polymer/iron-icon';
import {CustomKeyboardEvent} from '../../../types/events';
import {
  createDefaultDiffPrefs,
  DiffViewMode,
} from '../../../constants/constants';
import sinon from 'sinon/pkg/sinon-esm';
import {DiffInfo} from '../../../types/diff';

const commentApiMockClass = createCommentApiMockWithTemplateElement(
  'gr-file-list-comment-api',
  html`
    <gr-file-list
      change-comments="[[_changeComments]]"
      on-reload-drafts="_reloadDraftsWithCallback"
    ></gr-file-list>
    <gr-comment-api id="commentAPI"></gr-comment-api>
  `
);

const basicFixture = fixtureFromElement(commentApiMockClass.is);

const changeNum = 42 as NumericChangeId;

suite('gr-diff a11y test', () => {
  test('audit', async () => {
    await runA11yAudit(basicFixture);
  });
});

suite('gr-file-list tests', () => {
  let element: GrFileList;
  let commentApiWrapper: CommentApiMock & Element;
  let saveStub: sinon.SinonStub;
  let loadCommentSpy: sinon.SinonSpy;

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
    let reloadStub: sinon.SinonStub;
    let prefetchDiffStub: sinon.SinonStub;

    setup(async () => {
      stubRestApi('getPreferences').resolves(createPreferences());
      stubRestApi('getDiffComments').resolves({});
      stubRestApi('getDiffRobotComments').resolves({});
      stubRestApi('getDiffDrafts').resolves({});
      stubRestApi('getAccountCapabilities').resolves({});
      stub('gr-date-formatter', '_loadTimeFormat').resolves('');
      reloadStub = stub('gr-diff-host', 'reload').resolves();
      prefetchDiffStub = stub('gr-diff-host', 'prefetchDiff').returns({});

      // Element must be wrapped in an element with direct access to the
      // comment API.
      commentApiWrapper = basicFixture.instantiate() as CommentApiMock &
        Element;
      element = queryAndAssert(commentApiWrapper, 'gr-file-list');
      loadCommentSpy = sinon.spy(
        queryAndAssert<GrCommentApi>(commentApiWrapper, 'gr-comment-api'),
        'loadAll'
      );

      // Stub methods on the changeComments object after changeComments has
      // been initialized.
      await commentApiWrapper.loadComments();
      sinon.stub(element.changeComments, 'getPaths').returns({});
      element._loading = false;
      element.diffPrefs = createDefaultDiffPrefs();
      element.numFilesShown = 200;
      element.patchRange = {
        basePatchNum: 'PARENT' as BasePatchSetNum,
        patchNum: 2 as RevisionPatchSetNum,
      };
      saveStub = sinon.stub(element, '_saveReviewedState').resolves();
    });

    test('correct number of files are shown', async () => {
      element.fileListIncrement = 300;
      element._filesByPath = Array(500)
        .fill(0)
        .reduce((_filesByPath, _, idx) => {
          _filesByPath[`/file${idx}`] = {lines_inserted: 9};
          return _filesByPath;
        }, {});

      await flush();
      assert.lengthOf(queryAll(element, '.file-row'), element.numFilesShown);
      const controlRow = queryAndAssert(element, '.controlRow');
      assert.isFalse(controlRow.classList.contains('invisible'));
      assert.equal(
        queryAndAssert(element, '#incrementButton').textContent?.trim(),
        'Show 300 more'
      );
      const showAllButton = queryAndAssert(element, '#showAllButton');
      assert.equal(showAllButton.textContent?.trim(), 'Show all 500 files');

      MockInteractions.tap(showAllButton);
      await flush();

      assert.equal(element.numFilesShown, 500);
      assert.lengthOf(element._shownFiles, 500);
      assert.isTrue(controlRow.classList.contains('invisible'));
    });

    test('rendering each row calls the _reportRenderedRow method', async () => {
      const renderedStub = sinon.stub(element, '_reportRenderedRow');
      element._filesByPath = Array(10)
        .fill(0)
        .reduce((_filesByPath, _, idx) => {
          _filesByPath[`/file${idx}`] = {lines_inserted: 9};
          return _filesByPath;
        }, {});
      await flush();
      assert.lengthOf(queryAll(element, '.file-row'), 10);
      assert.equal(renderedStub.callCount, 10);
    });

    test('calculate totals for patch number', () => {
      element._filesByPath = {
        '/COMMIT_MSG': {
          lines_inserted: 9,
          size_delta: 0,
          size: 0,
        },
        '/MERGE_LIST': {
          lines_inserted: 9,
          size_delta: 0,
          size: 0,
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
          size_delta: 2,
          size: 8,
        },
        '/COMMIT_MSG': {
          lines_inserted: 9,
          size_delta: 0,
          size: 0,
        },
        '/MERGE_LIST': {
          lines_inserted: 9,
          size_delta: 0,
          size: 0,
        },
        'myfile.txt': {
          lines_inserted: 1,
          lines_deleted: 1,
          size_delta: 2,
          size: 8,
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
          size_delta: 0,
          size: 0,
        },
        'myfile.txt': {
          lines_inserted: 1,
          lines_deleted: 1,
          size_delta: 0,
          size: 0,
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
        'file_added_in_rev2.txt': {lines_inserted: 1, size_delta: 0, size: 0},
        'myfile.txt': {lines_deleted: 1, size_delta: 0, size: 0},
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
        '/COMMIT_MSG': {lines_inserted: 9, size_delta: 0, size: 0},
        file_binary_1: {binary: true, size_delta: 10, size: 100},
        file_binary_2: {binary: true, size_delta: -5, size: 120},
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
        '/COMMIT_MSG': {lines_inserted: 9, size_delta: 0, size: 0},
        file_binary_1: {binary: true, size_delta: 10, size: 100},
        file_binary_2: {binary: true, size_delta: -5, size: 120},
        'myfile.txt': {lines_deleted: 5, size_delta: -10, size: 100},
        'myfile2.txt': {lines_inserted: 10, size_delta: 0, size: 0},
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
      for (const [bytes, expected] of Object.entries(table)) {
        assert.equal(element._formatBytes(Number(bytes)), expected);
      }
    });

    test('_formatPercentage function', () => {
      const table = [
        {size: 100, delta: 100, display: ''},
        {size: 195060, delta: 64, display: '(+0%)'},
        {size: 195060, delta: -64, display: '(-0%)'},
        {size: 394892, delta: -7128, display: '(-2%)'},
        {size: 90, delta: -10, display: '(-10%)'},
        {size: 110, delta: 10, display: '(+10%)'},
      ];

      for (const item of table) {
        assert.equal(
          element._formatPercentage(item.size, item.delta),
          item.display
        );
      }
    });

    test('comment filtering', () => {
      element.changeComments = createChangeComments();
      const parentTo1 = {
        basePatchNum: 'PARENT' as BasePatchSetNum,
        patchNum: 1 as RevisionPatchSetNum,
      };

      const parentTo2 = {
        basePatchNum: 'PARENT' as BasePatchSetNum,
        patchNum: 2 as RevisionPatchSetNum,
      };

      const _1To2 = {
        basePatchNum: 1 as BasePatchSetNum,
        patchNum: 2 as RevisionPatchSetNum,
      };

      assert.equal(
        element._computeCommentsStringMobile(
          element.changeComments,
          parentTo1,
          {__path: '/COMMIT_MSG', size_delta: 0, size: 0}
        ),
        '2c'
      );
      assert.equal(
        element._computeCommentsStringMobile(element.changeComments, _1To2, {
          __path: '/COMMIT_MSG',
          size_delta: 0,
          size: 0,
        }),
        '3c'
      );
      assert.equal(
        element._computeDraftsString(element.changeComments, parentTo1, {
          __path: 'unresolved.file',
          size_delta: 0,
          size: 0,
        }),
        '1 draft'
      );
      assert.equal(
        element._computeDraftsString(element.changeComments, _1To2, {
          __path: 'unresolved.file',
          size_delta: 0,
          size: 0,
        }),
        '1 draft'
      );
      assert.equal(
        element._computeDraftsStringMobile(element.changeComments, parentTo1, {
          __path: 'unresolved.file',
          size_delta: 0,
          size: 0,
        }),
        '1d'
      );
      assert.equal(
        element._computeDraftsStringMobile(element.changeComments, _1To2, {
          __path: 'unresolved.file',
          size_delta: 0,
          size: 0,
        }),
        '1d'
      );
      assert.equal(
        element._computeCommentsStringMobile(
          element.changeComments,
          parentTo1,
          {__path: 'myfile.txt', size_delta: 0, size: 0}
        ),
        '1c'
      );
      assert.equal(
        element._computeCommentsStringMobile(element.changeComments, _1To2, {
          __path: 'myfile.txt',
          size_delta: 0,
          size: 0,
        }),
        '3c'
      );
      assert.equal(
        element._computeDraftsString(element.changeComments, parentTo1, {
          __path: 'myfile.txt',
          size_delta: 0,
          size: 0,
        }),
        ''
      );
      assert.equal(
        element._computeDraftsString(element.changeComments, _1To2, {
          __path: 'myfile.txt',
          size_delta: 0,
          size: 0,
        }),
        ''
      );
      assert.equal(
        element._computeDraftsStringMobile(element.changeComments, parentTo1, {
          __path: 'myfile.txt',
          size_delta: 0,
          size: 0,
        }),
        ''
      );
      assert.equal(
        element._computeDraftsStringMobile(element.changeComments, _1To2, {
          __path: 'myfile.txt',
          size_delta: 0,
          size: 0,
        }),
        ''
      );
      assert.equal(
        element._computeCommentsStringMobile(
          element.changeComments,
          parentTo1,
          {__path: 'file_added_in_rev2.txt', size_delta: 0, size: 0}
        ),
        ''
      );
      assert.equal(
        element._computeCommentsStringMobile(element.changeComments, _1To2, {
          __path: 'file_added_in_rev2.txt',
          size_delta: 0,
          size: 0,
        }),
        ''
      );
      assert.equal(
        element._computeDraftsString(element.changeComments, parentTo1, {
          __path: 'file_added_in_rev2.txt',
          size_delta: 0,
          size: 0,
        }),
        ''
      );
      assert.equal(
        element._computeDraftsString(element.changeComments, _1To2, {
          __path: 'file_added_in_rev2.txt',
          size_delta: 0,
          size: 0,
        }),
        ''
      );
      assert.equal(
        element._computeDraftsStringMobile(element.changeComments, parentTo1, {
          __path: 'file_added_in_rev2.txt',
          size_delta: 0,
          size: 0,
        }),
        ''
      );
      assert.equal(
        element._computeDraftsStringMobile(element.changeComments, _1To2, {
          __path: 'file_added_in_rev2.txt',
          size_delta: 0,
          size: 0,
        }),
        ''
      );
      assert.equal(
        element._computeCommentsStringMobile(
          element.changeComments,
          parentTo2,
          {__path: '/COMMIT_MSG', size_delta: 0, size: 0}
        ),
        '1c'
      );
      assert.equal(
        element._computeCommentsStringMobile(element.changeComments, _1To2, {
          __path: '/COMMIT_MSG',
          size_delta: 0,
          size: 0,
        }),
        '3c'
      );
      assert.equal(
        element._computeDraftsString(element.changeComments, parentTo1, {
          __path: '/COMMIT_MSG',
          size_delta: 0,
          size: 0,
        }),
        '2 drafts'
      );
      assert.equal(
        element._computeDraftsString(element.changeComments, _1To2, {
          __path: '/COMMIT_MSG',
          size_delta: 0,
          size: 0,
        }),
        '2 drafts'
      );
      assert.equal(
        element._computeDraftsStringMobile(element.changeComments, parentTo1, {
          __path: '/COMMIT_MSG',
          size_delta: 0,
          size: 0,
        }),
        '2d'
      );
      assert.equal(
        element._computeDraftsStringMobile(element.changeComments, _1To2, {
          __path: '/COMMIT_MSG',
          size_delta: 0,
          size: 0,
        }),
        '2d'
      );
      assert.equal(
        element._computeCommentsStringMobile(
          element.changeComments,
          parentTo2,
          {__path: 'myfile.txt', size_delta: 0, size: 0}
        ),
        '2c'
      );
      assert.equal(
        element._computeCommentsStringMobile(element.changeComments, _1To2, {
          __path: 'myfile.txt',
          size_delta: 0,
          size: 0,
        }),
        '3c'
      );
      assert.equal(
        element._computeDraftsStringMobile(element.changeComments, parentTo2, {
          __path: 'myfile.txt',
          size_delta: 0,
          size: 0,
        }),
        ''
      );
      assert.equal(
        element._computeDraftsStringMobile(element.changeComments, _1To2, {
          __path: 'myfile.txt',
          size_delta: 0,
          size: 0,
        }),
        ''
      );
    });

    test('_reviewedTitle', () => {
      assert.equal(
        element._reviewedTitle(true),
        'Mark as not reviewed (shortcut: r)'
      );

      assert.equal(
        element._reviewedTitle(false),
        'Mark as reviewed (shortcut: r)'
      );
    });

    suite('keyboard shortcuts', () => {
      setup(() => {
        element._filesByPath = {
          '/COMMIT_MSG': {size_delta: 0, size: 0},
          'file_added_in_rev2.txt': {size_delta: 0, size: 0},
          'myfile.txt': {size_delta: 0, size: 0},
        };
        element.changeNum = changeNum;
        element.patchRange = {
          basePatchNum: 'PARENT' as BasePatchSetNum,
          patchNum: 2 as RevisionPatchSetNum,
        };
        element.change = {...createParsedChange(), _number: changeNum};
        element.fileCursor.setCursorAtIndex(0);
      });

      test('toggle left diff via shortcut', () => {
        const toggleLeftDiffStub = sinon.stub();
        // Property getter cannot be stubbed w/ sandbox due to a bug in Sinon.
        // https://github.com/sinonjs/sinon/issues/781
        const diffsStub = sinon
          .stub(element, 'diffs')
          .get(() => [{toggleLeftDiff: toggleLeftDiffStub}]);
        MockInteractions.pressAndReleaseKeyOn(element, 65, 'shift', 'a');
        assert.isTrue(toggleLeftDiffStub.calledOnce);
        diffsStub.restore();
      });

      test('keyboard shortcuts', async () => {
        await flush();

        const items = [...queryAll(element, '.file-row')];
        element.fileCursor.stops = items;
        element.fileCursor.setCursorAtIndex(0);
        assert.lengthOf(items, 3);
        assert.isTrue(items[0].classList.contains('selected'));
        assert.isFalse(items[1].classList.contains('selected'));
        assert.isFalse(items[2].classList.contains('selected'));
        // j with a modifier should not move the cursor.
        MockInteractions.pressAndReleaseKeyOn(element, 74, 'shift', 'j');
        assert.equal(element.fileCursor.index, 0);
        // down should not move the cursor.
        MockInteractions.pressAndReleaseKeyOn(element, 40, null, 'down');
        assert.equal(element.fileCursor.index, 0);

        MockInteractions.pressAndReleaseKeyOn(element, 74, null, 'j');
        assert.equal(element.fileCursor.index, 1);
        assert.equal(element.selectedIndex, 1);
        MockInteractions.pressAndReleaseKeyOn(element, 74, null, 'j');

        const navStub = sinon.stub(GerritNav, 'navigateToDiff');
        assert.equal(element.fileCursor.index, 2);
        assert.equal(element.selectedIndex, 2);

        // k with a modifier should not move the cursor.
        MockInteractions.pressAndReleaseKeyOn(element, 75, 'shift', 'k');
        assert.equal(element.fileCursor.index, 2);

        // up should not move the cursor.
        MockInteractions.pressAndReleaseKeyOn(element, 38, null, 'down');
        assert.equal(element.fileCursor.index, 2);

        MockInteractions.pressAndReleaseKeyOn(element, 75, null, 'k');
        assert.equal(element.fileCursor.index, 1);
        assert.equal(element.selectedIndex, 1);
        MockInteractions.pressAndReleaseKeyOn(element, 79, null, 'o');

        assert.isTrue(
          navStub.lastCall.calledWith(
            element.change,
            'file_added_in_rev2.txt',
            2 as PatchSetNum
          ),
          'Should navigate to /c/42/2/file_added_in_rev2.txt'
        );

        MockInteractions.pressAndReleaseKeyOn(element, 75, null, 'k');
        MockInteractions.pressAndReleaseKeyOn(element, 75, null, 'k');
        MockInteractions.pressAndReleaseKeyOn(element, 75, null, 'k');
        assert.equal(element.fileCursor.index, 0);
        assert.equal(element.selectedIndex, 0);

        const createCommentInPlaceStub = sinon.stub(
          element.diffCursor,
          'createCommentInPlace'
        );
        MockInteractions.pressAndReleaseKeyOn(element, 67, null, 'c');
        assert.isTrue(createCommentInPlaceStub.called);
      });

      test('i key shows/hides selected inline diff', async () => {
        const paths = Object.keys(element._filesByPath!);
        sinon.stub(element, '_expandedFilesChanged');
        await flush();
        const files = [...queryAll(element, '.file-row')];
        element.fileCursor.stops = files;
        element.fileCursor.setCursorAtIndex(0);
        assert.isEmpty(element.diffs);
        assert.isEmpty(element._expandedFiles);

        MockInteractions.keyUpOn(element, 73, null, 'i');
        await flush();
        assert.lengthOf(element.diffs, 1);
        assert.equal(element.diffs[0].path, paths[0]);
        assert.lengthOf(element._expandedFiles, 1);
        assert.equal(element._expandedFiles[0].path, paths[0]);

        MockInteractions.keyUpOn(element, 73, null, 'i');
        await flush();
        assert.isEmpty(element.diffs);
        assert.isEmpty(element._expandedFiles);

        element.fileCursor.setCursorAtIndex(1);
        MockInteractions.keyUpOn(element, 73, null, 'i');
        await flush();
        assert.lengthOf(element.diffs, 1);
        assert.equal(element.diffs[0].path, paths[1]);
        assert.lengthOf(element._expandedFiles, 1);
        assert.equal(element._expandedFiles[0].path, paths[1]);

        MockInteractions.keyUpOn(element, 73, 'shift', 'i');
        await flush();
        assert.equal(element.diffs.length, paths.length);
        assert.equal(element._expandedFiles.length, paths.length);
        for (const diff of element.diffs) {
          assert.isTrue(element._expandedFiles.some(f => f.path === diff.path));
        }

        MockInteractions.keyUpOn(element, 73, 'shift', 'i');
        await flush();
        assert.isEmpty(element.diffs);
        assert.isEmpty(element._expandedFiles);
      });

      test('r key toggles reviewed flag', async () => {
        const getNumReviewed = () =>
          element._files.filter(file => file.isReviewed).length;
        await flush();

        // Default state should be unreviewed.
        assert.equal(getNumReviewed(), 0);

        // Press the review key to toggle it (set the flag).
        MockInteractions.pressAndReleaseKeyOn(element, 82, null, 'r');
        await flush();
        assert.equal(getNumReviewed(), 1);

        // Press the review key to toggle it (clear the flag).
        MockInteractions.pressAndReleaseKeyOn(element, 82, null, 'r');
        assert.equal(getNumReviewed(), 0);
      });

      suite('_handleOpenFile', () => {
        let openCursorStub: sinon.SinonStub;
        let openSelectedStub: sinon.SinonStub;
        let expandStub: sinon.SinonStub;

        function interact() {
          const e = new CustomEvent(
            'fake-keyboard-event'
          ) as CustomKeyboardEvent;
          const preventDefaultStub = sinon.stub(e, 'preventDefault');
          element._handleOpenFile(e);
          assert.isTrue(preventDefaultStub.called);
        }

        setup(() => {
          sinon.stub(element, 'shouldSuppressKeyboardShortcut').returns(false);
          sinon.stub(element, 'modifierPressed').returns(false);
          openCursorStub = sinon.stub(element, '_openCursorFile');
          openSelectedStub = sinon.stub(element, '_openSelectedFile');
          expandStub = sinon.stub(element, '_toggleFileExpanded');
        });

        test('open from selected file', () => {
          element._showInlineDiffs = false;
          interact();
          assert.isFalse(openCursorStub.called);
          assert.isTrue(openSelectedStub.called);
          assert.isFalse(expandStub.called);
        });

        test('open from diff cursor', () => {
          element._showInlineDiffs = true;
          interact();
          assert.isTrue(openCursorStub.called);
          assert.isFalse(openSelectedStub.called);
          assert.isFalse(expandStub.called);
        });

        test('expand when user prefers', () => {
          element._showInlineDiffs = false;
          interact();
          assert.isFalse(openCursorStub.called);
          assert.isTrue(openSelectedStub.called);
          assert.isFalse(expandStub.called);

          element._userPrefs = createPreferences();
          interact();
          assert.isFalse(openCursorStub.called);
          assert.isTrue(openSelectedStub.called);
          assert.isFalse(expandStub.called);
        });
      });

      test('shift+left/shift+right', () => {
        const moveLeftStub = sinon.stub(element.diffCursor, 'moveLeft');
        const moveRightStub = sinon.stub(element.diffCursor, 'moveRight');

        let noDiffsExpanded = true;
        sinon
          .stub(element, '_noDiffsExpanded')
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

    test('file review status', async () => {
      element._reviewed = ['/COMMIT_MSG', 'myfile.txt'];
      element._filesByPath = {
        '/COMMIT_MSG': {size_delta: 0, size: 0},
        'file_added_in_rev2.txt': {size_delta: 0, size: 0},
        'myfile.txt': {size_delta: 0, size: 0},
      };
      element._loggedIn = true;
      element.changeNum = changeNum;
      element.patchRange = {
        basePatchNum: 'PARENT' as BasePatchSetNum,
        patchNum: 2 as RevisionPatchSetNum,
      };
      element.fileCursor.setCursorAtIndex(0);
      const reviewSpy = sinon.spy(element, '_reviewFile');
      const toggleExpandSpy = sinon.spy(element, '_toggleFileExpanded');

      await flush();
      const fileRows = queryAll(element, '.row:not(.header-row)');
      const checkSelector = 'span.reviewedSwitch[role="switch"]';
      const commitMsg = queryAndAssert(fileRows[0], checkSelector);
      const fileAdded = queryAndAssert(fileRows[1], checkSelector);
      const myFile = queryAndAssert(fileRows[2], checkSelector);

      assert.equal(commitMsg.getAttribute('aria-checked'), 'true');
      assert.equal(fileAdded.getAttribute('aria-checked'), 'false');
      assert.equal(myFile.getAttribute('aria-checked'), 'true');

      const commitReviewLabel = queryAndAssert(fileRows[0], '.reviewedLabel');
      const markReviewLabel = queryAndAssert(fileRows[0], '.markReviewed');
      assert.isTrue(commitReviewLabel.classList.contains('isReviewed'));
      assert.equal(markReviewLabel.textContent, 'MARK UNREVIEWED');

      const clickSpy = sinon.spy(element, '_reviewedClick');
      MockInteractions.tap(markReviewLabel);
      assert.isTrue(saveStub.lastCall.calledWithExactly('/COMMIT_MSG', false));
      assert.isFalse(commitReviewLabel.classList.contains('isReviewed'));
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

    test('_handleFileListClick', () => {
      element._filesByPath = {
        '/COMMIT_MSG': {size_delta: 0, size: 0},
        'f1.txt': {size_delta: 0, size: 0},
        'f2.txt': {size_delta: 0, size: 0},
      };
      element.changeNum = changeNum;
      element.patchRange = {
        basePatchNum: 'PARENT' as BasePatchSetNum,
        patchNum: 2 as RevisionPatchSetNum,
      };

      const clickSpy = sinon.spy(element, '_handleFileListClick');
      const reviewStub = sinon.stub(element, '_reviewFile');
      const toggleExpandSpy = sinon.spy(element, '_toggleFileExpanded');

      const row = queryAndAssert(
        element,
        '.row[data-file=\'{"path":"f1.txt"}\']'
      );

      // Click on the expand button, resulting in _toggleFileExpanded being
      // called and not resulting in a call to _reviewFile.
      queryAndAssert<HTMLDivElement>(row, 'div.show-hide').click();
      assert.isTrue(clickSpy.calledOnce);
      assert.isTrue(toggleExpandSpy.calledOnce);
      assert.isFalse(reviewStub.called);

      // Click inside the diff. This should result in no additional calls to
      // _toggleFileExpanded or _reviewFile.
      queryAndAssert<GrDiffHost>(element, 'gr-diff-host').click();
      assert.isTrue(clickSpy.calledTwice);
      assert.isTrue(toggleExpandSpy.calledOnce);
      assert.isFalse(reviewStub.called);
    });

    test('_handleFileListClick editMode', async () => {
      element._filesByPath = {
        '/COMMIT_MSG': {size_delta: 0, size: 0},
        'f1.txt': {size_delta: 0, size: 0},
        'f2.txt': {size_delta: 0, size: 0},
      };
      element.changeNum = changeNum;
      element.patchRange = {
        basePatchNum: 'PARENT' as BasePatchSetNum,
        patchNum: 2 as RevisionPatchSetNum,
      };
      element.editMode = true;
      await flush();
      const clickSpy = sinon.spy(element, '_handleFileListClick');
      const toggleExpandSpy = sinon.spy(element, '_toggleFileExpanded');

      // Tap the edit controls. Should be ignored by _handleFileListClick.
      MockInteractions.tap(queryAndAssert(element, '.editFileControls'));
      assert.isTrue(clickSpy.calledOnce);
      assert.isFalse(toggleExpandSpy.called);
    });

    test('checkbox shows/hides diff inline', async () => {
      element._filesByPath = {
        'myfile.txt': {size_delta: 0, size: 0},
      };
      element.changeNum = changeNum;
      element.patchRange = {
        basePatchNum: 'PARENT' as BasePatchSetNum,
        patchNum: 2 as RevisionPatchSetNum,
      };
      element.fileCursor.setCursorAtIndex(0);
      sinon.stub(element, '_expandedFilesChanged');
      await flush();
      const fileRows = queryAll(element, '.row:not(.header-row)');
      // Because the label surrounds the input, the tap event is triggered
      // there first.
      const showHideCheck = queryAndAssert(
        fileRows[0],
        'span.show-hide[role="switch"]'
      );
      const showHideLabel = queryAndAssert(showHideCheck, '.show-hide-icon');
      assert.equal(showHideCheck.getAttribute('aria-checked'), 'false');
      MockInteractions.tap(showHideLabel);
      assert.equal(showHideCheck.getAttribute('aria-checked'), 'true');
      assert.include(
        element._expandedFiles.map(f => f.path),
        'myfile.txt'
      );
    });

    test('diff mode correctly toggles the diffs', async () => {
      element._filesByPath = {
        'myfile.txt': {size_delta: 0, size: 0},
      };
      element.changeNum = changeNum;
      element.patchRange = {
        basePatchNum: 'PARENT' as BasePatchSetNum,
        patchNum: 2 as RevisionPatchSetNum,
      };
      const updateDiffPreferencesSpy = sinon.spy(
        element,
        '_updateDiffPreferences'
      );
      element.fileCursor.setCursorAtIndex(0);
      await flush();

      // Tap on a file to generate the diff.
      const row = queryAndAssert(
        element,
        '.row:not(.header-row) span.show-hide'
      );

      MockInteractions.tap(row);
      await flush();
      const diffDisplay = element.diffs[0];
      element._userPrefs = {
        ...createPreferences(),
        default_diff_view: DiffViewMode.SIDE_BY_SIDE,
      };
      element.set('diffViewMode', 'UNIFIED_DIFF');
      assert.equal(diffDisplay.viewMode, 'UNIFIED_DIFF');
      assert.isTrue(updateDiffPreferencesSpy.called);
    });

    test('expanded attribute not set on path when not expanded', () => {
      element._filesByPath = {
        '/COMMIT_MSG': {size_delta: 0, size: 0},
      };
      assert.isNotOk(query(element, '.expanded'));
    });

    test('tapping row ignores links', async () => {
      element._filesByPath = {
        '/COMMIT_MSG': {size_delta: 0, size: 0},
      };
      element.changeNum = changeNum;
      element.patchRange = {
        basePatchNum: 'PARENT' as BasePatchSetNum,
        patchNum: 2 as RevisionPatchSetNum,
      };
      sinon.stub(element, '_expandedFilesChanged');
      await flush();
      const commitMsgFile = queryAndAssert(
        element,
        '.row:not(.header-row) a.pathLink'
      );

      // Remove href attribute so the app doesn't route to a diff view
      commitMsgFile.removeAttribute('href');
      const togglePathSpy = sinon.spy(element, '_toggleFileExpanded');

      MockInteractions.tap(commitMsgFile);
      await flush();
      assert.isTrue(togglePathSpy.notCalled, 'file is opened as diff view');
      assert.isNotOk(query(element, '.expanded'));
      assert.notEqual(
        getComputedStyle(queryAndAssert(element, '.show-hide')).display,
        'none'
      );
    });

    test('_toggleFileExpanded', async () => {
      const path = 'path/to/my/file.txt';
      element._filesByPath = {[path]: {size_delta: 0, size: 0}};
      const renderSpy = sinon.spy(element, '_renderInOrder');
      const collapseStub = sinon.stub(element, '_clearCollapsedDiffs');

      assert.equal(
        queryAndAssert<IronIconElement>(element, 'iron-icon').icon,
        'gr-icons:expand-more'
      );
      assert.isEmpty(element._expandedFiles);
      element._toggleFileExpanded({path});
      await flush();
      assert.isEmpty(collapseStub.lastCall.args[0]);
      assert.equal(
        queryAndAssert<IronIconElement>(element, 'iron-icon').icon,
        'gr-icons:expand-less'
      );

      assert.equal(renderSpy.callCount, 1);
      assert.include(
        element._expandedFiles.map(f => f.path),
        path
      );
      element._toggleFileExpanded({path});
      await flush();

      assert.equal(
        queryAndAssert<IronIconElement>(element, 'iron-icon').icon,
        'gr-icons:expand-more'
      );
      assert.equal(renderSpy.callCount, 1);
      assert.notInclude(
        element._expandedFiles.map(f => f.path),
        path
      );
      assert.lengthOf(collapseStub.lastCall.args[0], 1);
    });

    test('expandAllDiffs and collapseAllDiffs', async () => {
      const collapseStub = sinon.stub(element, '_clearCollapsedDiffs');
      const cursorUpdateStub = sinon.stub(
        element.diffCursor,
        'handleDiffUpdate'
      );
      const reInitStub: sinon.SinonStub = sinon.stub(
        element.diffCursor,
        'reInitAndUpdateStops'
      );

      const path = 'path/to/my/file.txt';
      element._filesByPath = {[path]: {size_delta: 0, size: 0}};
      await flush();
      element.expandAllDiffs();
      await flush();
      assert.isTrue(element._showInlineDiffs);
      assert.isTrue(reInitStub.called);
      assert.isEmpty(collapseStub.lastCall.args[0]);

      element.collapseAllDiffs();
      await flush();
      assert.isEmpty(element._expandedFiles);
      assert.isFalse(element._showInlineDiffs);
      assert.isTrue(cursorUpdateStub.calledOnce);
      assert.lengthOf(collapseStub.lastCall.args[0], 1);
    });

    test('_expandedFilesChanged calls reload', async () => {
      const reloadCalled = mockPromise();
      sinon.stub(element, '_reviewFile');
      const path = 'path/to/my/file.txt';
      const diff = new GrDiffHost();
      diff.path = path;
      reloadStub.callsFake(() => reloadCalled.resolve());
      sinon
        .stub(diff, 'addEventListener')
        .callsFake((eventName: string, callback: (e: Event) => any) => {
          const eventsToTrigger = ['render-start', 'render-content', 'scroll'];
          if (eventsToTrigger.indexOf(eventName) >= 0) {
            callback(new Event(eventName));
          }
        });
      sinon.stub(diff, 'cancel').returns();
      sinon.stub(diff, 'getCursorStops').returns([]);
      sinon.stub(element, 'diffs').get(() => [diff]);

      element.push('_expandedFiles', {path});

      await reloadCalled;
    });

    test('_clearCollapsedDiffs', () => {
      reloadStub.restore();
      prefetchDiffStub.restore();
      const stubbedDiff = sinon.createStubInstance(GrDiffHost);
      element._clearCollapsedDiffs([stubbedDiff]);
      assert.isTrue(stubbedDiff.cancel.calledOnce);
      assert.isTrue(stubbedDiff.clearDiffContent.calledOnce);
    });

    test('filesExpanded value updates to correct enum', async () => {
      element._filesByPath = {
        'foo.bar': {size_delta: 0, size: 0},
        'baz.bar': {size_delta: 0, size: 0},
      };
      await flush();
      assert.equal(element.filesExpanded, FilesExpandedState.NONE);
      element.push('_expandedFiles', {path: 'baz.bar'});
      await flush();
      assert.equal(element.filesExpanded, FilesExpandedState.SOME);
      element.push('_expandedFiles', {path: 'foo.bar'});
      await flush();
      assert.equal(element.filesExpanded, FilesExpandedState.ALL);
      element.collapseAllDiffs();
      await flush();
      assert.equal(element.filesExpanded, FilesExpandedState.NONE);
      element.expandAllDiffs();
      await flush();
      assert.equal(element.filesExpanded, FilesExpandedState.ALL);
    });

    test('_renderInOrder', async () => {
      const reviewStub = sinon.stub(element, '_reviewFile');
      const diffs: GrDiffHost[] = [
        new GrDiffHost(),
        new GrDiffHost(),
        new GrDiffHost(),
      ];
      diffs[0].path = 'p0';
      diffs[1].path = 'p1';
      diffs[2].path = 'p2';
      reloadStub.restore();
      const p0reloaded = sinon.stub(diffs[0], 'reload').resolves();
      const p1reloaded = sinon.stub(diffs[1], 'reload').resolves();
      const p2reloaded = sinon.stub(diffs[2], 'reload').resolves();

      await element._renderInOrder(
        [{path: 'p2'}, {path: 'p1'}, {path: 'p0'}],
        diffs,
        3
      );
      assert.isTrue(p0reloaded.called);
      assert.isTrue(p1reloaded.called);
      assert.isTrue(p2reloaded.called);
      assert.isFalse(reviewStub.called);
      assert.isTrue(loadCommentSpy.called);
    });

    test('_renderInOrder logged in', async () => {
      element._loggedIn = true;
      const reviewStub = sinon.stub(element, '_reviewFile');
      const diffs: GrDiffHost[] = [
        new GrDiffHost(),
        new GrDiffHost(),
        new GrDiffHost(),
      ];
      diffs[0].path = 'p0';
      diffs[1].path = 'p1';
      diffs[2].path = 'p2';
      reloadStub.restore();
      const p0reloaded = sinon.stub(diffs[0], 'reload').resolves();
      const p1reloaded = sinon.stub(diffs[1], 'reload').resolves();
      const p2reloaded = sinon.stub(diffs[2], 'reload').resolves();

      await element._renderInOrder(
        [{path: 'p2'}, {path: 'p1'}, {path: 'p0'}],
        diffs,
        3
      );
      assert.equal(reviewStub.callCount, 3);
      assert.isTrue(p0reloaded.called);
      assert.isTrue(p1reloaded.called);
      assert.isTrue(p2reloaded.called);
    });

    test('_renderInOrder respects diffPrefs.manual_review', async () => {
      element._loggedIn = true;
      element.diffPrefs = {...createDefaultDiffPrefs(), manual_review: true};
      const reviewStub = sinon.stub(element, '_reviewFile');
      const diffHost = new GrDiffHost();
      diffHost.path = 'p';
      const diffs = [diffHost];
      await element._renderInOrder([{path: 'p'}], diffs, 1);
      assert.isFalse(reviewStub.called);
      delete element.diffPrefs.manual_review;
      await element._renderInOrder([{path: 'p'}], diffs, 1);
      assert.isTrue(reviewStub.called);
      assert.isTrue(reviewStub.calledWithExactly('p', true));
    });

    test('_loadingChanged fired from reload in debouncer', async () => {
      sinon.stub(element, '_getReviewedFiles').resolves([]);
      element.changeNum = 123 as NumericChangeId;
      element.patchRange = {
        basePatchNum: 'PARENT' as BasePatchSetNum,
        patchNum: 12 as RevisionPatchSetNum,
      };
      element._filesByPath = {'foo.bar': {size_delta: 0, size: 0}};

      const blockReload = mockPromise();
      stubRestApi('getLoggedIn').callsFake(async () => {
        await blockReload;
        return false;
      });
      const reloaded = element.reload();

      assert.isTrue(element._loading);
      assert.isFalse(element.classList.contains('loading'));
      element.loadingTask!.flush();
      assert.isTrue(element.classList.contains('loading'));

      blockReload.resolve();
      await reloaded;

      assert.isFalse(element._loading);
      element.loadingTask!.flush();
      assert.isFalse(element.classList.contains('loading'));
    });

    test('_loadingChanged does not set class when there are no files', () => {
      sinon.stub(element, '_getReviewedFiles').resolves([]);
      element.changeNum = 123 as NumericChangeId;
      element.patchRange = {
        basePatchNum: 'PARENT' as BasePatchSetNum,
        patchNum: 12 as RevisionPatchSetNum,
      };
      element.reload();
      assert.isTrue(element._loading);
      element.loadingTask!.flush();
      assert.isFalse(element.classList.contains('loading'));
    });
  });

  suite('diff url file list', () => {
    const change = {
      ...createParsedChange(),
      _number: 1 as NumericChangeId,
      project: 'gerrit' as RepoName,
    };
    const patchRange = {
      basePatchNum: 'PARENT' as BasePatchSetNum,
      patchNum: 1 as RevisionPatchSetNum,
    };

    test('diff url', () => {
      const diffStub = sinon
        .stub(GerritNav, 'getUrlForDiff')
        .returns('/c/gerrit/+/1/1/index.php');
      const path = 'index.php';
      assert.equal(
        element._computeDiffURL(change, patchRange, path, false),
        '/c/gerrit/+/1/1/index.php'
      );
      diffStub.restore();
    });

    test('diff url commit msg', () => {
      const diffStub = sinon
        .stub(GerritNav, 'getUrlForDiff')
        .returns('/c/gerrit/+/1/1//COMMIT_MSG');
      const path = '/COMMIT_MSG';
      assert.equal(
        element._computeDiffURL(change, patchRange, path, false),
        '/c/gerrit/+/1/1//COMMIT_MSG'
      );
      diffStub.restore();
    });

    test('edit url', () => {
      const editStub = sinon
        .stub(GerritNav, 'getEditUrlForDiff')
        .returns('/c/gerrit/+/1/edit/index.php,edit');
      const path = 'index.php';
      assert.equal(
        element._computeDiffURL(change, patchRange, path, true),
        '/c/gerrit/+/1/edit/index.php,edit'
      );
      editStub.restore();
    });

    test('edit url commit msg', () => {
      const editStub = sinon
        .stub(GerritNav, 'getEditUrlForDiff')
        .returns('/c/gerrit/+/1/edit//COMMIT_MSG,edit');
      const path = '/COMMIT_MSG';
      assert.equal(
        element._computeDiffURL(change, patchRange, path, true),
        '/c/gerrit/+/1/edit//COMMIT_MSG,edit'
      );
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

      assert.deepEqual(element._computeSizeBarLayout(), defaultSizeBarLayout);
      assert.deepEqual(
        element._computeSizeBarLayout({path: '', base: [], value: []}),
        defaultSizeBarLayout
      );

      const files: NormalizedFileInfo[] = [
        {
          __path: '/COMMIT_MSG',
          lines_inserted: 10000,
          size_delta: 100,
          size: 100,
        },
        {
          __path: 'foo',
          lines_inserted: 4,
          lines_deleted: 10,
          size_delta: -6,
          size: 4,
        },
        {
          __path: 'bar',
          lines_inserted: 5,
          lines_deleted: 8,
          size_delta: -3,
          size: 5,
        },
      ];
      const layout = element._computeSizeBarLayout({
        path: '',
        base: files,
        value: files,
      });
      assert.equal(layout.maxInserted, 5);
      assert.equal(layout.maxDeleted, 10);
    });

    test('_computeBarAdditionWidth', () => {
      const file: NormalizedFileInfo = {
        __path: 'foo/bar.baz',
        lines_inserted: 5,
        lines_deleted: 0,
        size_delta: 5,
        size: 10,
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

      // If there are no insertions, there is no width.
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
      const file: NormalizedFileInfo = {
        __path: 'foo/bar.baz',
        lines_inserted: 5,
        lines_deleted: 0,
        size_delta: 5,
        size: 10,
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
      const file: NormalizedFileInfo = {
        __path: 'foo/bar.baz',
        lines_inserted: 0,
        lines_deleted: 5,
        size_delta: -5,
        size: 10,
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
      assert.equal(
        element._computeSizeBarsClass(false, 'foo/bar.baz'),
        'sizeBars desktop hide'
      );
      assert.equal(
        element._computeSizeBarsClass(true, '/COMMIT_MSG'),
        'sizeBars desktop invisible'
      );
      assert.equal(
        element._computeSizeBarsClass(true, 'foo/bar.baz'),
        'sizeBars desktop '
      );
    });
  });

  suite('gr-file-list inline diff tests', () => {
    let element: GrFileList;
    let reviewFileStub: sinon.SinonStub;

    const commitMsgComments: UIComment[] = [
      {
        patch_set: 2 as PatchSetNum,
        path: '/p',
        id: 'ecf0b9fa_fe1a5f62' as UrlEncodedCommentId,
        line: 20,
        updated: '2018-02-08 18:49:18.000000000' as Timestamp,
        message: 'another comment',
        unresolved: true,
      },
      {
        patch_set: 2 as PatchSetNum,
        path: '/p',
        id: '503008e2_0ab203ee' as UrlEncodedCommentId,
        line: 10,
        updated: '2018-02-14 22:07:43.000000000' as Timestamp,
        message: 'a comment',
        unresolved: true,
      },
      {
        patch_set: 2 as PatchSetNum,
        path: '/p',
        id: 'cc788d2c_cb1d728c' as UrlEncodedCommentId,
        line: 20,
        in_reply_to: 'ecf0b9fa_fe1a5f62' as UrlEncodedCommentId,
        updated: '2018-02-13 22:07:43.000000000' as Timestamp,
        message: 'response',
        unresolved: true,
      },
    ];

    async function setupDiff(diff: GrDiffHost) {
      diff.threads =
        diff.path === '/COMMIT_MSG'
          ? createCommentThreads(commitMsgComments)
          : [];
      diff.prefs = {
        context: 10,
        tab_size: 8,
        font_size: 12,
        line_length: 100,
        cursor_blink_rate: 0,
        line_wrapping: false,
        show_line_endings: true,
        show_tabs: true,
        show_whitespace_errors: true,
        syntax_highlighting: true,
        ignore_whitespace: 'IGNORE_NONE',
      };
      diff.diff = getMockDiffResponse() as DiffInfo;
      await commentApiWrapper.loadComments();
      sinon
        .stub(element.changeComments, 'getCommentsForPath')
        .withArgs('/COMMIT_MSG', {
          basePatchNum: 'PARENT',
          patchNum: 2,
        })
        .returns(diff.threads);
      await listenOnce(diff, 'render');
    }

    async function renderAndGetNewDiffs(index: number) {
      const diffs = queryAll<GrDiffHost>(element, 'gr-diff-host');

      for (let i = index; i < diffs.length; i++) {
        await setupDiff(diffs[i]);
      }

      element._updateDiffCursor();
      element.diffCursor.handleDiffUpdate();
      return diffs;
    }

    setup(async () => {
      stubRestApi('getPreferences').resolves(createPreferences());
      stubRestApi('getDiffComments').resolves({});
      stubRestApi('getDiffRobotComments').resolves({});
      stubRestApi('getDiffDrafts').resolves({});
      stub('gr-date-formatter', '_loadTimeFormat').resolves('');
      stub('gr-diff-host', 'reload').resolves();
      stub('gr-diff-host', 'prefetchDiff').returns({});

      // Element must be wrapped in an element with direct access to the
      // comment API.
      commentApiWrapper = basicFixture.instantiate() as CommentApiMock &
        Element;
      element = queryAndAssert(commentApiWrapper, 'gr-file-list');
      loadCommentSpy = sinon.spy(
        queryAndAssert<GrCommentApi>(commentApiWrapper, 'gr-comment-api'),
        'loadAll'
      );
      element.diffPrefs = createDefaultDiffPrefs();
      element.change = {
        ...createParsedChange(),
        _number: changeNum,
      };
      reviewFileStub = sinon.stub(element, '_reviewFile');

      // Stub methods on the changeComments object after changeComments has
      // been initialized.
      await commentApiWrapper.loadComments();
      sinon.stub(element.changeComments, 'getPaths').returns({});
      element._loading = false;
      element.numFilesShown = 75;
      element.selectedIndex = 0;
      element._filesByPath = {
        '/COMMIT_MSG': {lines_inserted: 9, size_delta: 0, size: 0},
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
      element.changeNum = changeNum;
      element.patchRange = {
        basePatchNum: 'PARENT' as BasePatchSetNum,
        patchNum: 2 as RevisionPatchSetNum,
      };
      sinon.stub(window, 'fetch').resolves();
      await flush();
    });

    test('cursor with individually opened files', async () => {
      MockInteractions.keyUpOn(element, 73, null, 'i');
      await flush();
      let diffs = await renderAndGetNewDiffs(0);
      const diffStops = diffs[0].getCursorStops();
      const line10 = diffStops[10] as HTMLElement;
      const line11 = diffStops[11] as HTMLElement;

      // 1 diff should be rendered.
      assert.lengthOf(diffs, 1);

      // No line number is selected.
      assert.isFalse(line10.classList.contains('target-row'));

      // Tapping content on a line selects the line number.
      MockInteractions.tap(queryAll(line10, '.contentText')[0]);
      await flush();
      assert.isTrue(line10.classList.contains('target-row'));

      // Keyboard shortcuts are still moving the file cursor, not the diff
      // cursor.
      MockInteractions.pressAndReleaseKeyOn(element, 74, null, 'j');
      await flush();
      assert.isTrue(line10.classList.contains('target-row'));
      assert.isFalse(line11.classList.contains('target-row'));

      // The file cursor is now at 1.
      assert.equal(element.fileCursor.index, 1);

      MockInteractions.keyUpOn(element, 73, null, 'i');
      await flush();
      diffs = await renderAndGetNewDiffs(1);

      // Two diffs should be rendered.
      assert.lengthOf(diffs, 2);
      const diffStopsFirst = diffs[0].getCursorStops();
      const diffStopsSecond = diffs[1].getCursorStops();

      // The line on the first diff is still selected
      const firstDiffLine10 = diffStopsFirst[10] as HTMLElement;
      assert.isTrue(firstDiffLine10.classList.contains('target-row'));
      const secondDiffLine10 = diffStopsSecond[10] as HTMLElement;
      assert.isFalse(secondDiffLine10.classList.contains('target-row'));
    });

    test('cursor with toggle all files', async () => {
      MockInteractions.keyUpOn(element, 73, 'shift', 'i');
      await flush();

      const diffs = await renderAndGetNewDiffs(0);
      const diffStops = diffs[0].getCursorStops();

      // 1 diff should be rendered.
      assert.lengthOf(diffs, 3);

      // No line number is selected.
      const line10 = diffStops[10] as HTMLElement;
      assert.isFalse(line10.classList.contains('target-row'));

      // Tapping content on a line selects the line number.
      MockInteractions.tap(queryAll(line10, '.contentText')[0]);
      await flush();
      assert.isTrue(line10.classList.contains('target-row'));

      // Keyboard shortcuts are still moving the file cursor, not the diff
      // cursor.
      MockInteractions.pressAndReleaseKeyOn(element, 74, null, 'j');
      await flush();
      const line11 = diffStops[11] as HTMLElement;
      assert.isFalse(line10.classList.contains('target-row'));
      assert.isTrue(line11.classList.contains('target-row'));

      // The file cursor is still at 0.
      assert.equal(element.fileCursor.index, 0);
    });

    suite('n key presses', () => {
      let nKeySpy: sinon.SinonSpy;
      let nextCommentStub: sinon.SinonStub;
      let nextChunkStub: sinon.SinonStub;
      let fileRows: NodeListOf<HTMLDivElement>;

      setup(() => {
        sinon.stub(element, '_renderInOrder').resolves();
        nKeySpy = sinon.spy(element, '_handleNextChunk');
        nextCommentStub = sinon.stub(
          element.diffCursor,
          'moveToNextCommentThread'
        );
        nextChunkStub = sinon.stub(element.diffCursor, 'moveToNextChunk');
        fileRows = queryAll(element, '.row:not(.header-row)');
      });

      test('n key with some files expanded and no shift key', async () => {
        MockInteractions.keyUpOn(fileRows[0], 73, null, 'i');
        await flush();

        // Handle N key should return before calling diff cursor functions.
        MockInteractions.pressAndReleaseKeyOn(element, 78, null, 'n');
        assert.isTrue(nKeySpy.called);
        assert.isFalse(nextCommentStub.called);

        // This is also called in diffCursor.moveToFirstChunk.
        assert.equal(nextChunkStub.callCount, 1);
        assert.equal(element.filesExpanded, 'some');
      });

      test('n key with some files expanded and shift key', async () => {
        MockInteractions.keyUpOn(fileRows[0], 73, null, 'i');
        await flush();
        assert.equal(nextChunkStub.callCount, 0);

        MockInteractions.pressAndReleaseKeyOn(element, 78, 'shift', 'n');
        assert.isTrue(nKeySpy.called);
        assert.isTrue(nextCommentStub.called);

        // This is also called in diffCursor.moveToFirstChunk.
        assert.equal(nextChunkStub.callCount, 0);
        assert.equal(element.filesExpanded, 'some');
      });

      test('n key without all files expanded and shift key', async () => {
        MockInteractions.keyUpOn(fileRows[0], 73, 'shift', 'i');
        await flush();

        MockInteractions.pressAndReleaseKeyOn(element, 78, null, 'n');
        assert.isTrue(nKeySpy.called);
        assert.isFalse(nextCommentStub.called);

        // This is also called in diffCursor.moveToFirstChunk.
        assert.equal(nextChunkStub.callCount, 1);
        assert.isTrue(element._showInlineDiffs);
      });

      test('n key without all files expanded and no shift key', async () => {
        MockInteractions.keyUpOn(fileRows[0], 73, 'shift', 'i');
        await flush();

        MockInteractions.pressAndReleaseKeyOn(element, 78, 'shift', 'n');
        assert.isTrue(nKeySpy.called);
        assert.isTrue(nextCommentStub.called);

        // This is also called in diffCursor.moveToFirstChunk.
        assert.equal(nextChunkStub.callCount, 0);
        assert.isTrue(element._showInlineDiffs);
      });
    });

    test('_openSelectedFile behavior', async () => {
      const _filesByPath = element._filesByPath;
      element.set('_filesByPath', {});
      const navStub = sinon.stub(GerritNav, 'navigateToDiff');
      // Noop when there are no files.
      element._openSelectedFile();
      assert.isFalse(navStub.called);

      element.set('_filesByPath', _filesByPath);
      await flush();
      // Navigates when a file is selected.
      element._openSelectedFile();
      assert.isTrue(navStub.called);
    });

    test('_displayLine', () => {
      sinon.stub(element, 'shouldSuppressKeyboardShortcut').returns(false);
      sinon.stub(element, 'modifierPressed').returns(false);
      element._showInlineDiffs = true;
      const mockEvent = new CustomEvent('mock') as CustomKeyboardEvent;

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
      test('reviewed checkbox', async () => {
        reviewFileStub.restore();
        const saveReviewStub = sinon.stub(element, '_saveReviewedState');

        element.editMode = false;
        MockInteractions.pressAndReleaseKeyOn(element, 82, null, 'r');
        assert.isTrue(saveReviewStub.calledOnce);

        element.editMode = true;
        await flush();

        MockInteractions.pressAndReleaseKeyOn(element, 82, null, 'r');
        assert.isTrue(saveReviewStub.calledOnce);
      });

      test('_getReviewedFiles does not call API', async () => {
        const apiSpy = spyRestApi('getReviewedFiles');
        element.editMode = true;
        const files = await element._getReviewedFiles(123 as NumericChangeId, {
          basePatchNum: 'PARENT' as BasePatchSetNum,
          patchNum: 1 as RevisionPatchSetNum,
        });
        assert.isEmpty(files);
        assert.isFalse(apiSpy.called);
      });
    });

    test('editing actions', async () => {
      // Edit controls are guarded behind a dom-if initially and not rendered.
      assert.isNotOk(query(element, 'gr-edit-file-controls'));

      element.editMode = true;
      await flush();

      // Commit message (header row) should not have edit controls.
      const editControls = queryAndAssert(
        element,
        '.row:not(.header-row) gr-edit-file-controls'
      );
      assert.isTrue(editControls.classList.contains('invisible'));
    });

    test('reloadCommentsForThreadWithRootId', async () => {
      // Expand the commit message diff
      MockInteractions.keyUpOn(element, 73, 'shift', 'i');
      const diffs = await renderAndGetNewDiffs(0);
      await flush();

      // Two comment threads should be generated by renderAndGetNewDiffs
      const threadEls = diffs[0].getThreadEls();
      assert.lengthOf(threadEls, 2);
      const threadElsByRootId = new Map(
        threadEls.map(threadEl => [threadEl.rootId, threadEl])
      );

      const thread1 = threadElsByRootId.get(commitMsgComments[1].id)!;
      assert.lengthOf(thread1.comments, 1);
      assert.equal(thread1.comments[0].message, 'a comment');
      assert.equal(thread1.comments[0].line, 10);

      const thread2 = threadElsByRootId.get(commitMsgComments[0].id)!;
      assert.lengthOf(thread2.comments, 2);
      assert.isTrue(thread2.comments[0].unresolved);
      assert.equal(thread2.comments[0].message, 'another comment');
      assert.equal(thread2.comments[0].line, 20);

      const commentStub = sinon.stub(
        element.changeComments,
        'getCommentsForThread'
      );
      const commentStubRes1: UIComment[] = [
        {
          patch_set: 2 as PatchSetNum,
          path: '/p',
          id: commitMsgComments[1].id,
          line: 20,
          updated: '2018-02-08 18:49:18.000000000' as Timestamp,
          message: 'edited text',
          unresolved: false,
        },
      ];
      const commentStubRes2: UIComment[] = [
        {
          patch_set: 2 as PatchSetNum,
          path: '/p',
          id: commitMsgComments[0].id,
          line: 20,
          updated: '2018-02-08 18:49:18.000000000' as Timestamp,
          message: 'another comment',
          unresolved: true,
        },
        {
          patch_set: 2 as PatchSetNum,
          path: '/p',
          id: commitMsgComments[1].id,
          line: 10,
          in_reply_to: commitMsgComments[0].id,
          updated: '2018-02-14 22:07:43.000000000' as Timestamp,
          message: 'response',
          unresolved: true,
        },
        {
          patch_set: 2 as PatchSetNum,
          path: '/p',
          id: '503008e2_0ab203ef' as UrlEncodedCommentId,
          line: 20,
          in_reply_to: commitMsgComments[1].id,
          updated: '2018-02-15 22:07:43.000000000' as Timestamp,
          message: 'a third comment in the thread',
          unresolved: true,
        },
      ];
      commentStub.withArgs(commitMsgComments[1].id).returns(commentStubRes1);
      commentStub.withArgs(commitMsgComments[0].id).returns(commentStubRes2);

      // Reload comments from the first comment thread, which should have a
      // an updated message and a toggled resolve state.
      element.reloadCommentsForThreadWithRootId(
        commitMsgComments[1].id!,
        '/COMMIT_MSG'
      );
      assert.lengthOf(thread1.comments, 1);
      assert.isFalse(thread1.comments[0].unresolved);
      assert.equal(thread1.comments[0].message, 'edited text');

      // Reload comments from the second comment thread, which should have a new
      // reply.
      element.reloadCommentsForThreadWithRootId(
        commitMsgComments[0].id!,
        '/COMMIT_MSG'
      );
      assert.lengthOf(thread2.comments, 3);

      const commentStubCount = commentStub.callCount;
      const getThreadsSpy = sinon.spy(diffs[0], 'getThreadEls');

      // Should not be getting threads when the file is not expanded.
      element.reloadCommentsForThreadWithRootId(
        commitMsgComments[0].id!,
        'other/file'
      );
      assert.isFalse(getThreadsSpy.called);
      assert.equal(commentStubCount, commentStub.callCount);

      // Should be query selecting diffs when the file is expanded.
      // Should not be fetching change comments when the rootId is not found
      // to match.
      element.reloadCommentsForThreadWithRootId(
        'acf0b9fa_fe1a5f62' as UrlEncodedCommentId,
        '/COMMIT_MSG'
      );
      assert.isTrue(getThreadsSpy.called);
      assert.equal(commentStubCount, commentStub.callCount);
    });
  });
});
