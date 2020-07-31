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
import './gr-diff-view.js';
import {dom} from '@polymer/polymer/lib/legacy/polymer.dom.js';
import {GerritNav} from '../../core/gr-navigation/gr-navigation.js';
import {ChangeStatus} from '../../../constants/constants.js';
import {generateChange, TestKeyboardShortcutBinder} from '../../../test/test-utils';
import {SPECIAL_PATCH_SET_NUM} from '../../../utils/patch-set-util.js';
import {Shortcut} from '../../../mixins/keyboard-shortcut-mixin/keyboard-shortcut-mixin.js';
import {_testOnly_findCommentById} from '../gr-comment-api/gr-comment-api.js';

const basicFixture = fixtureFromElement('gr-diff-view');

const blankFixture = fixtureFromElement('div');

suite('gr-diff-view tests', () => {
  suite('basic tests', () => {
    let element;

    suiteSetup(() => {
      const kb = TestKeyboardShortcutBinder.push();
      kb.bindShortcut(Shortcut.LEFT_PANE, 'shift+left');
      kb.bindShortcut(Shortcut.RIGHT_PANE, 'shift+right');
      kb.bindShortcut(Shortcut.NEXT_LINE, 'j', 'down');
      kb.bindShortcut(Shortcut.PREV_LINE, 'k', 'up');
      kb.bindShortcut(Shortcut.NEXT_FILE_WITH_COMMENTS, 'shift+j');
      kb.bindShortcut(Shortcut.PREV_FILE_WITH_COMMENTS, 'shift+k');
      kb.bindShortcut(Shortcut.NEW_COMMENT, 'c');
      kb.bindShortcut(Shortcut.SAVE_COMMENT, 'ctrl+s');
      kb.bindShortcut(Shortcut.NEXT_FILE, ']');
      kb.bindShortcut(Shortcut.PREV_FILE, '[');
      kb.bindShortcut(Shortcut.NEXT_CHUNK, 'n');
      kb.bindShortcut(Shortcut.NEXT_COMMENT_THREAD, 'shift+n');
      kb.bindShortcut(Shortcut.PREV_CHUNK, 'p');
      kb.bindShortcut(Shortcut.PREV_COMMENT_THREAD, 'shift+p');
      kb.bindShortcut(Shortcut.OPEN_REPLY_DIALOG, 'a');
      kb.bindShortcut(Shortcut.TOGGLE_LEFT_PANE, 'shift+a');
      kb.bindShortcut(Shortcut.UP_TO_CHANGE, 'u');
      kb.bindShortcut(Shortcut.OPEN_DIFF_PREFS, ',');
      kb.bindShortcut(Shortcut.TOGGLE_DIFF_MODE, 'm');
      kb.bindShortcut(Shortcut.TOGGLE_FILE_REVIEWED, 'r');
      kb.bindShortcut(Shortcut.EXPAND_ALL_DIFF_CONTEXT, 'shift+x');
      kb.bindShortcut(Shortcut.EXPAND_ALL_COMMENT_THREADS, 'e');
      kb.bindShortcut(Shortcut.TOGGLE_HIDE_ALL_COMMENT_THREADS, 'h');
      kb.bindShortcut(Shortcut.COLLAPSE_ALL_COMMENT_THREADS, 'shift+e');
      kb.bindShortcut(Shortcut.NEXT_UNREVIEWED_FILE, 'shift+m');
      kb.bindShortcut(Shortcut.TOGGLE_BLAME, 'b');
    });

    suiteTeardown(() => {
      TestKeyboardShortcutBinder.pop();
    });

    const PARENT = 'PARENT';

    function getFilesFromFileList(fileList) {
      const changeFilesByPath = fileList.reduce((files, path) => {
        files[path] = {};
        return files;
      }, {});
      return {
        sortedFileList: fileList,
        changeFilesByPath,
      };
    }

    setup(() => {
      stub('gr-rest-api-interface', {
        getConfig() {
          return Promise.resolve({change: {}});
        },
        getLoggedIn() {
          return Promise.resolve(false);
        },
        getProjectConfig() {
          return Promise.resolve({});
        },
        getDiffChangeDetail() {
          return Promise.resolve({});
        },
        getChangeFiles() {
          return Promise.resolve({});
        },
        saveFileReviewed() {
          return Promise.resolve();
        },
        getDiffComments() {
          return Promise.resolve({});
        },
        getDiffRobotComments() {
          return Promise.resolve({});
        },
        getDiffDrafts() {
          return Promise.resolve({});
        },
        getReviewedFiles() {
          return Promise.resolve([]);
        },
      });
      element = basicFixture.instantiate();
      sinon.stub(element.$.commentAPI, 'loadAll').returns(Promise.resolve({
        _comments: {'/COMMIT_MSG': [{id: 'c1', line: 10, patch_set: 2,
          __commentSide: 'left'}]},
        computeCommentCount: () => {},
        computeUnresolvedNum: () => {},
        getPaths: () => {},
        getCommentsBySideForPath: () => {},
        findCommentById: _testOnly_findCommentById,
      }));
      return element._loadComments();
    });

    test('params change triggers diffViewDisplayed()', () => {
      sinon.stub(element.reporting, 'diffViewDisplayed');
      sinon.stub(element.$.diffHost, 'reload').returns(Promise.resolve());
      sinon.stub(element, '_initPatchRange');
      sinon.stub(element, '_getFiles');
      sinon.spy(element, '_paramsChanged');
      element.params = {
        view: GerritNav.View.DIFF,
        changeNum: '42',
        patchNum: '2',
        basePatchNum: '1',
        path: '/COMMIT_MSG',
      };
      return element._paramsChanged.returnValues[0].then(() => {
        assert.isTrue(element.reporting.diffViewDisplayed.calledOnce);
      });
    });

    test('comment route', () => {
      const initLineOfInterestAndCursorStub =
        sinon.stub(element, '_initLineOfInterestAndCursor');
      sinon.stub(element, '_getFiles');
      sinon.stub(element.reporting, 'diffViewDisplayed');
      sinon.stub(element.$.diffHost, 'reload').returns(Promise.resolve());
      sinon.spy(element, '_paramsChanged');
      sinon.stub(element, '_getChangeDetail').returns(Promise.resolve(
          generateChange({revisionsCount: 11})));
      element.params = {
        view: GerritNav.View.DIFF,
        changeNum: '42',
        commentLink: true,
        commentId: 'c1',
      };
      sinon.stub(element.$.diffHost, '_commentsChanged');
      sinon.stub(element, '_getCommentsForPath').returns({
        left: [{id: 'c1', __commentSide: 'left', line: 10}],
        right: [{id: 'c2', __commentSide: 'right', line: 11}],
      });
      element._change = generateChange({revisionsCount: 11});
      return element._paramsChanged.returnValues[0].then(() => {
        assert.isTrue(initLineOfInterestAndCursorStub.
            calledWithExactly(10, true));
        assert.equal(element._patchRange.patchNum, 11);
        assert.equal(element._patchRange.basePatchNum, 2);
      });
    });

    test('params change causes blame to load if it was set to true', () => {
      // Blame loads for subsequent files if it was loaded for one file
      element._isBlameLoaded = true;
      sinon.stub(element.reporting, 'diffViewDisplayed');
      sinon.stub(element, '_loadBlame');
      sinon.stub(element.$.diffHost, 'reload').returns(Promise.resolve());
      sinon.spy(element, '_paramsChanged');
      sinon.stub(element, '_initPatchRange');
      sinon.stub(element, '_getFiles');
      element.params = {
        view: GerritNav.View.DIFF,
        changeNum: '42',
        patchNum: '2',
        basePatchNum: '1',
        path: '/COMMIT_MSG',
      };

      return element._paramsChanged.returnValues[0].then(() => {
        assert.isTrue(element._isBlameLoaded);
        assert.isTrue(element._loadBlame.calledOnce);
      });
    });

    test('toggle left diff with a hotkey', () => {
      const toggleLeftDiffStub = sinon.stub(
          element.$.diffHost, 'toggleLeftDiff');
      MockInteractions.pressAndReleaseKeyOn(element, 65, 'shift', 'a');
      assert.isTrue(toggleLeftDiffStub.calledOnce);
    });

    test('keyboard shortcuts', () => {
      element._changeNum = '42';
      element._patchRange = {
        basePatchNum: PARENT,
        patchNum: '10',
      };
      element._change = {
        _number: 42,
        revisions: {
          a: {_number: 10, commit: {parents: []}},
        },
      };
      element._files = getFilesFromFileList(
          ['chell.go', 'glados.txt', 'wheatley.md']);
      element._path = 'glados.txt';
      element.changeViewState.selectedFileIndex = 1;
      element._loggedIn = true;

      const diffNavStub = sinon.stub(GerritNav, 'navigateToDiff');
      const changeNavStub = sinon.stub(GerritNav, 'navigateToChange');

      MockInteractions.pressAndReleaseKeyOn(element, 85, null, 'u');
      assert(changeNavStub.lastCall.calledWith(element._change),
          'Should navigate to /c/42/');

      MockInteractions.pressAndReleaseKeyOn(element, 221, null, ']');
      assert(diffNavStub.lastCall.calledWith(element._change, 'wheatley.md',
          '10', PARENT), 'Should navigate to /c/42/10/wheatley.md');
      element._path = 'wheatley.md';
      assert.equal(element.changeViewState.selectedFileIndex, 2);
      assert.isTrue(element._loading);

      MockInteractions.pressAndReleaseKeyOn(element, 219, null, '[');
      assert(diffNavStub.lastCall.calledWith(element._change, 'glados.txt',
          '10', PARENT), 'Should navigate to /c/42/10/glados.txt');
      element._path = 'glados.txt';
      assert.equal(element.changeViewState.selectedFileIndex, 1);
      assert.isTrue(element._loading);

      MockInteractions.pressAndReleaseKeyOn(element, 219, null, '[');
      assert(diffNavStub.lastCall.calledWith(element._change, 'chell.go', '10',
          PARENT), 'Should navigate to /c/42/10/chell.go');
      element._path = 'chell.go';
      assert.equal(element.changeViewState.selectedFileIndex, 0);
      assert.isTrue(element._loading);

      MockInteractions.pressAndReleaseKeyOn(element, 219, null, '[');
      assert(changeNavStub.lastCall.calledWith(element._change),
          'Should navigate to /c/42/');
      assert.equal(element.changeViewState.selectedFileIndex, 0);
      assert.isTrue(element._loading);

      const showPrefsStub =
          sinon.stub(element.$.diffPreferencesDialog, 'open').callsFake(
              () => Promise.resolve());

      MockInteractions.pressAndReleaseKeyOn(element, 188, null, ',');
      assert(showPrefsStub.calledOnce);

      element.disableDiffPrefs = true;
      MockInteractions.pressAndReleaseKeyOn(element, 188, null, ',');
      assert(showPrefsStub.calledOnce);

      let scrollStub = sinon.stub(element.$.cursor, 'moveToNextChunk');
      MockInteractions.pressAndReleaseKeyOn(element, 78, null, 'n');
      assert(scrollStub.calledOnce);

      scrollStub = sinon.stub(element.$.cursor, 'moveToPreviousChunk');
      MockInteractions.pressAndReleaseKeyOn(element, 80, null, 'p');
      assert(scrollStub.calledOnce);

      scrollStub = sinon.stub(element.$.cursor, 'moveToNextCommentThread');
      MockInteractions.pressAndReleaseKeyOn(element, 78, 'shift', 'n');
      assert(scrollStub.calledOnce);

      scrollStub = sinon.stub(element.$.cursor,
          'moveToPreviousCommentThread');
      MockInteractions.pressAndReleaseKeyOn(element, 80, 'shift', 'p');
      assert(scrollStub.calledOnce);

      const computeContainerClassStub = sinon.stub(element.$.diffHost.$.diff,
          '_computeContainerClass');
      MockInteractions.pressAndReleaseKeyOn(element, 74, null, 'j');
      assert(computeContainerClassStub.lastCall.calledWithExactly(
          false, 'SIDE_BY_SIDE', true));

      MockInteractions.pressAndReleaseKeyOn(element, 27, null, 'esc');
      assert(computeContainerClassStub.lastCall.calledWithExactly(
          false, 'SIDE_BY_SIDE', false));

      sinon.stub(element, '_setReviewed');
      sinon.spy(element, '_handleToggleFileReviewed');
      element.$.reviewed.checked = false;
      MockInteractions.pressAndReleaseKeyOn(element, 82, 'shift', 'r');
      assert.isFalse(element._setReviewed.called);
      assert.isTrue(element._handleToggleFileReviewed.calledOnce);

      MockInteractions.pressAndReleaseKeyOn(element, 82, null, 'r');
      assert.isTrue(element._handleToggleFileReviewed.calledTwice);
      assert.isTrue(element._setReviewed.called);
      assert.equal(element._setReviewed.lastCall.args[0], true);
    });

    test('shift+x shortcut expands all diff context', () => {
      const expandStub = sinon.stub(element.$.diffHost, 'expandAllContext');
      MockInteractions.pressAndReleaseKeyOn(element, 88, 'shift', 'x');
      flushAsynchronousOperations();
      assert.isTrue(expandStub.called);
    });

    test('diff against base', () => {
      element._patchRange = {
        basePatchNum: '5',
        patchNum: '10',
      };
      sinon.stub(element, 'shouldSuppressKeyboardShortcut').returns(false);
      const diffNavStub = sinon.stub(GerritNav, 'navigateToDiff');
      element._handleDiffAgainstBase(new CustomEvent(''));
      const args = diffNavStub.getCall(0).args;
      assert.equal(args[2], 10);
      assert.isNotOk(args[3]);
    });

    test('diff against latest', () => {
      element._change = generateChange({revisionsCount: 12});
      element._patchRange = {
        basePatchNum: '5',
        patchNum: '10',
      };
      sinon.stub(element, 'shouldSuppressKeyboardShortcut').returns(false);
      const diffNavStub = sinon.stub(GerritNav, 'navigateToDiff');
      element._handleDiffAgainstLatest(new CustomEvent(''));
      const args = diffNavStub.getCall(0).args;
      assert.equal(args[2], 12);
      assert.equal(args[3], 5);
    });

    test('_handleDiffBaseAgainstLeft', () => {
      element._change = generateChange({revisionsCount: 10});
      element._patchRange = {
        patchNum: 3,
        basePatchNum: 1,
      };
      sinon.stub(element, 'shouldSuppressKeyboardShortcut').returns(false);
      const diffNavStub = sinon.stub(GerritNav, 'navigateToDiff');
      element._handleDiffBaseAgainstLeft(new CustomEvent(''));
      assert(diffNavStub.called);
      const args = diffNavStub.getCall(0).args;
      assert.equal(args[2], 1);
      assert.isNotOk(args[3]);
    });

    test('_handleDiffRightAgainstLatest', () => {
      element._change = generateChange({revisionsCount: 10});
      element._patchRange = {
        basePatchNum: 1,
        patchNum: 3,
      };
      sinon.stub(element, 'shouldSuppressKeyboardShortcut').returns(false);
      const diffNavStub = sinon.stub(GerritNav, 'navigateToDiff');
      element._handleDiffRightAgainstLatest(new CustomEvent(''));
      assert(diffNavStub.called);
      const args = diffNavStub.getCall(0).args;
      assert.equal(args[2], 10);
      assert.equal(args[3], 3);
    });

    test('_handleDiffBaseAgainstLatest', () => {
      element._change = generateChange({revisionsCount: 10});
      element._patchRange = {
        basePatchNum: 1,
        patchNum: 3,
      };
      sinon.stub(element, 'shouldSuppressKeyboardShortcut').returns(false);
      const diffNavStub = sinon.stub(GerritNav, 'navigateToDiff');
      element._handleDiffBaseAgainstLatest(new CustomEvent(''));
      assert(diffNavStub.called);
      const args = diffNavStub.getCall(0).args;
      assert.equal(args[2], 10);
      assert.isNotOk(args[3]);
    });

    test('keyboard shortcuts with patch range', () => {
      element._changeNum = '42';
      element._patchRange = {
        basePatchNum: '5',
        patchNum: '10',
      };
      element._change = {
        _number: 42,
        revisions: {
          a: {_number: 10, commit: {parents: []}},
          b: {_number: 5, commit: {parents: []}},
        },
      };
      element._files = getFilesFromFileList(
          ['chell.go', 'glados.txt', 'wheatley.md']);
      element._path = 'glados.txt';

      const diffNavStub = sinon.stub(GerritNav, 'navigateToDiff');
      const changeNavStub = sinon.stub(GerritNav, 'navigateToChange');

      MockInteractions.pressAndReleaseKeyOn(element, 65, null, 'a');
      assert.isTrue(changeNavStub.notCalled, 'The `a` keyboard shortcut ' +
          'should only work when the user is logged in.');
      assert.isNull(window.sessionStorage.getItem(
          'changeView.showReplyDialog'));

      element._loggedIn = true;
      MockInteractions.pressAndReleaseKeyOn(element, 65, null, 'a');
      assert.isTrue(element.changeViewState.showReplyDialog);

      assert(changeNavStub.lastCall.calledWithExactly(element._change, '10',
          '5'), 'Should navigate to /c/42/5..10');

      MockInteractions.pressAndReleaseKeyOn(element, 85, null, 'u');
      assert(changeNavStub.lastCall.calledWithExactly(element._change, '10',
          '5'), 'Should navigate to /c/42/5..10');

      MockInteractions.pressAndReleaseKeyOn(element, 221, null, ']');
      assert.isTrue(element._loading);
      assert(diffNavStub.lastCall.calledWithExactly(element._change,
          'wheatley.md', '10', '5'),
      'Should navigate to /c/42/5..10/wheatley.md');
      element._path = 'wheatley.md';

      MockInteractions.pressAndReleaseKeyOn(element, 219, null, '[');
      assert.isTrue(element._loading);
      assert(diffNavStub.lastCall.calledWithExactly(element._change,
          'glados.txt', '10', '5'),
      'Should navigate to /c/42/5..10/glados.txt');
      element._path = 'glados.txt';

      MockInteractions.pressAndReleaseKeyOn(element, 219, null, '[');
      assert.isTrue(element._loading);
      assert(diffNavStub.lastCall.calledWithExactly(
          element._change,
          'chell.go',
          '10',
          '5'),
      'Should navigate to /c/42/5..10/chell.go');
      element._path = 'chell.go';

      MockInteractions.pressAndReleaseKeyOn(element, 219, null, '[');
      assert.isTrue(element._loading);
      assert(changeNavStub.lastCall.calledWithExactly(element._change, '10',
          '5'),
      'Should navigate to /c/42/5..10');
    });

    test('keyboard shortcuts with old patch number', () => {
      element._changeNum = '42';
      element._patchRange = {
        basePatchNum: PARENT,
        patchNum: '1',
      };
      element._change = {
        _number: 42,
        revisions: {
          a: {_number: 1, commit: {parents: []}},
          b: {_number: 2, commit: {parents: []}},
        },
      };
      element._files = getFilesFromFileList(
          ['chell.go', 'glados.txt', 'wheatley.md']);
      element._path = 'glados.txt';

      const diffNavStub = sinon.stub(GerritNav, 'navigateToDiff');
      const changeNavStub = sinon.stub(GerritNav, 'navigateToChange');

      MockInteractions.pressAndReleaseKeyOn(element, 65, null, 'a');
      assert.isTrue(changeNavStub.notCalled, 'The `a` keyboard shortcut ' +
          'should only work when the user is logged in.');
      assert.isNull(window.sessionStorage.getItem(
          'changeView.showReplyDialog'));

      element._loggedIn = true;
      MockInteractions.pressAndReleaseKeyOn(element, 65, null, 'a');
      assert.isTrue(element.changeViewState.showReplyDialog);

      assert(changeNavStub.lastCall.calledWithExactly(element._change, '1',
          PARENT), 'Should navigate to /c/42/1');

      MockInteractions.pressAndReleaseKeyOn(element, 85, null, 'u');
      assert(changeNavStub.lastCall.calledWithExactly(element._change, '1',
          PARENT), 'Should navigate to /c/42/1');

      MockInteractions.pressAndReleaseKeyOn(element, 221, null, ']');
      assert(diffNavStub.lastCall.calledWithExactly(element._change,
          'wheatley.md', '1', PARENT),
      'Should navigate to /c/42/1/wheatley.md');
      element._path = 'wheatley.md';

      MockInteractions.pressAndReleaseKeyOn(element, 219, null, '[');
      assert(diffNavStub.lastCall.calledWithExactly(element._change,
          'glados.txt', '1', PARENT),
      'Should navigate to /c/42/1/glados.txt');
      element._path = 'glados.txt';

      MockInteractions.pressAndReleaseKeyOn(element, 219, null, '[');
      assert(diffNavStub.lastCall.calledWithExactly(
          element._change,
          'chell.go',
          '1',
          PARENT), 'Should navigate to /c/42/1/chell.go');
      element._path = 'chell.go';

      MockInteractions.pressAndReleaseKeyOn(element, 219, null, '[');
      assert(changeNavStub.lastCall.calledWithExactly(element._change, '1',
          PARENT), 'Should navigate to /c/42/1');
    });

    test('edit should redirect to edit page', done => {
      element._loggedIn = true;
      element._path = 't.txt';
      element._patchRange = {
        basePatchNum: PARENT,
        patchNum: '1',
      };
      element._change = {
        _number: 42,
        project: 'gerrit',
        status: ChangeStatus.NEW,
        revisions: {
          a: {_number: 1, commit: {parents: []}},
          b: {_number: 2, commit: {parents: []}},
        },
      };
      const redirectStub = sinon.stub(GerritNav, 'navigateToRelativeUrl');
      flush(() => {
        const editBtn = element.shadowRoot
            .querySelector('.editButton gr-button');
        assert.isTrue(!!editBtn);
        MockInteractions.tap(editBtn);
        assert.isTrue(redirectStub.called);
        assert.isTrue(redirectStub.lastCall.calledWithExactly(
            GerritNav.getEditUrlForDiff(
                element._change,
                element._path,
                element._patchRange.patchNum
            )));
        done();
      });
    });

    test('edit should redirect to edit page with line number', done => {
      const lineNumber = 42;
      element._loggedIn = true;
      element._path = 't.txt';
      element._patchRange = {
        basePatchNum: PARENT,
        patchNum: '1',
      };
      element._change = {
        _number: 42,
        project: 'gerrit',
        status: ChangeStatus.NEW,
        revisions: {
          a: {_number: 1, commit: {parents: []}},
          b: {_number: 2, commit: {parents: []}},
        },
      };
      sinon.stub(element.$.cursor, 'getAddress')
          .returns({number: lineNumber, isLeftSide: false});
      const redirectStub = sinon.stub(GerritNav, 'navigateToRelativeUrl');
      flush(() => {
        const editBtn = element.shadowRoot
            .querySelector('.editButton gr-button');
        assert.isTrue(!!editBtn);
        MockInteractions.tap(editBtn);
        assert.isTrue(redirectStub.called);
        assert.isTrue(redirectStub.lastCall.calledWithExactly(
            GerritNav.getEditUrlForDiff(
                element._change,
                element._path,
                element._patchRange.patchNum,
                lineNumber
            )));
        done();
      });
    });

    function isEditVisibile({loggedIn, changeStatus}) {
      return new Promise(resolve => {
        element._loggedIn = loggedIn;
        element._path = 't.txt';
        element._patchRange = {
          basePatchNum: PARENT,
          patchNum: '1',
        };
        element._change = {
          _number: 42,
          status: changeStatus,
          revisions: {
            a: {_number: 1, commit: {parents: []}},
            b: {_number: 2, commit: {parents: []}},
          },
        };
        flush(() => {
          const editBtn = element.shadowRoot
              .querySelector('.editButton gr-button');
          resolve(!!editBtn);
        });
      });
    }

    test('edit visible only when logged and status NEW', async () => {
      for (const changeStatus in ChangeStatus) {
        if (!ChangeStatus.hasOwnProperty(changeStatus)) {
          continue;
        }
        assert.isFalse(await isEditVisibile({loggedIn: false, changeStatus}),
            `loggedIn: false, changeStatus: ${changeStatus}`);

        if (changeStatus !== ChangeStatus.NEW) {
          assert.isFalse(await isEditVisibile({loggedIn: true, changeStatus}),
              `loggedIn: true, changeStatus: ${changeStatus}`);
        } else {
          assert.isTrue(await isEditVisibile({loggedIn: true, changeStatus}),
              `loggedIn: true, changeStatus: ${changeStatus}`);
        }
      }
    });

    test('edit visible when logged and status NEW', async () => {
      assert.isTrue(await isEditVisibile(
          {loggedIn: true, changeStatus: ChangeStatus.NEW}));
    });

    test('edit hidden when logged and status ABANDONED', async () => {
      assert.isFalse(await isEditVisibile(
          {loggedIn: true, changeStatus: ChangeStatus.ABANDONED}));
    });

    test('edit hidden when logged and status MERGED', async () => {
      assert.isFalse(await isEditVisibile(
          {loggedIn: true, changeStatus: ChangeStatus.MERGED}));
    });

    suite('diff prefs hidden', () => {
      test('when no prefs or logged out', () => {
        element.disableDiffPrefs = false;
        element._loggedIn = false;
        flushAsynchronousOperations();
        assert.isTrue(element.$.diffPrefsContainer.hidden);

        element._loggedIn = true;
        flushAsynchronousOperations();
        assert.isTrue(element.$.diffPrefsContainer.hidden);

        element._loggedIn = false;
        element._prefs = {font_size: '12'};
        flushAsynchronousOperations();
        assert.isTrue(element.$.diffPrefsContainer.hidden);

        element._loggedIn = true;
        flushAsynchronousOperations();
        assert.isFalse(element.$.diffPrefsContainer.hidden);
      });

      test('when disableDiffPrefs is set', () => {
        element._loggedIn = true;
        element._prefs = {font_size: '12'};
        element.disableDiffPrefs = false;
        flushAsynchronousOperations();

        assert.isFalse(element.$.diffPrefsContainer.hidden);
        element.disableDiffPrefs = true;
        flushAsynchronousOperations();

        assert.isTrue(element.$.diffPrefsContainer.hidden);
      });
    });

    test('prefsButton opens gr-diff-preferences', () => {
      const handlePrefsTapSpy = sinon.spy(element, '_handlePrefsTap');
      const overlayOpenStub = sinon.stub(element.$.diffPreferencesDialog,
          'open');
      const prefsButton =
          dom(element.root).querySelector('.prefsButton');

      MockInteractions.tap(prefsButton);

      assert.isTrue(handlePrefsTapSpy.called);
      assert.isTrue(overlayOpenStub.called);
    });

    test('_computeCommentString', done => {
      const path = '/test';
      element.$.commentAPI.loadAll().then(comments => {
        const commentCountStub =
            sinon.stub(comments, 'computeCommentCount');
        const unresolvedCountStub =
            sinon.stub(comments, 'computeUnresolvedNum');
        commentCountStub.withArgs({patchNum: 1, path}).returns(0);
        commentCountStub.withArgs({patchNum: 2, path}).returns(1);
        commentCountStub.withArgs({patchNum: 3, path}).returns(2);
        commentCountStub.withArgs({patchNum: 4, path}).returns(0);
        unresolvedCountStub.withArgs({patchNum: 1, path}).returns(1);
        unresolvedCountStub.withArgs({patchNum: 2, path}).returns(0);
        unresolvedCountStub.withArgs({patchNum: 3, path}).returns(2);
        unresolvedCountStub.withArgs({patchNum: 4, path}).returns(0);

        assert.equal(element._computeCommentString(comments, 1, path, {}),
            '1 unresolved');
        assert.equal(
            element._computeCommentString(comments, 2, path, {status: 'M'}),
            '1 comment');
        assert.equal(
            element._computeCommentString(comments, 2, path, {status: 'U'}),
            'no changes, 1 comment');
        assert.equal(
            element._computeCommentString(comments, 3, path, {status: 'A'}),
            '2 comments, 2 unresolved');
        assert.equal(
            element._computeCommentString(
                comments, 4, path, {status: 'M'}
            ), '');
        assert.equal(
            element._computeCommentString(comments, 4, path, {status: 'U'}),
            'no changes');
        done();
      });
    });

    suite('url params', () => {
      setup(() => {
        sinon.stub(element, '_getFiles');
        sinon.stub(
            GerritNav,
            'getUrlForDiff')
            .callsFake((c, p, pn, bpn) => `${c._number}-${p}-${pn}-${bpn}`);
        sinon.stub(
            GerritNav
            , 'getUrlForChange')
            .callsFake((c, pn, bpn) => `${c._number}-${pn}-${bpn}`);
      });

      test('_formattedFiles', () => {
        element._changeNum = '42';
        element._patchRange = {
          basePatchNum: PARENT,
          patchNum: '10',
        };
        // computeCommentCount is an empty function hence stubbing function
        // that depends on it's return value
        sinon.stub(element, '_computeCommentString').returns('');
        element._change = {_number: 42};
        element._files = getFilesFromFileList(
            ['chell.go', 'glados.txt', 'wheatley.md',
              '/COMMIT_MSG', '/MERGE_LIST']);
        element._path = 'glados.txt';
        const expectedFormattedFiles = [
          {
            text: 'chell.go',
            mobileText: 'chell.go',
            value: 'chell.go',
            bottomText: '',
          }, {
            text: 'glados.txt',
            mobileText: 'glados.txt',
            value: 'glados.txt',
            bottomText: '',
          }, {
            text: 'wheatley.md',
            mobileText: 'wheatley.md',
            value: 'wheatley.md',
            bottomText: '',
          },
          {
            text: 'Commit message',
            mobileText: 'Commit message',
            value: '/COMMIT_MSG',
            bottomText: '',
          },
          {
            text: 'Merge list',
            mobileText: 'Merge list',
            value: '/MERGE_LIST',
            bottomText: '',
          },
        ];

        assert.deepEqual(element._formattedFiles, expectedFormattedFiles);
        assert.equal(element._formattedFiles[1].value, element._path);
      });

      test('prev/up/next links', () => {
        element._changeNum = '42';
        element._patchRange = {
          basePatchNum: PARENT,
          patchNum: '10',
        };
        element._change = {
          _number: 42,
          revisions: {
            a: {_number: 10, commit: {parents: []}},
          },
        };
        element._files = getFilesFromFileList(
            ['chell.go', 'glados.txt', 'wheatley.md']);
        element._path = 'glados.txt';
        flushAsynchronousOperations();
        const linkEls = dom(element.root).querySelectorAll('.navLink');
        assert.equal(linkEls.length, 3);
        assert.equal(linkEls[0].getAttribute('href'), '42-chell.go-10-PARENT');
        assert.equal(linkEls[1].getAttribute('href'), '42-undefined-undefined');
        assert.equal(linkEls[2].getAttribute('href'),
            '42-wheatley.md-10-PARENT');
        element._path = 'wheatley.md';
        flushAsynchronousOperations();
        assert.equal(linkEls[0].getAttribute('href'),
            '42-glados.txt-10-PARENT');
        assert.equal(linkEls[1].getAttribute('href'), '42-undefined-undefined');
        assert.isFalse(linkEls[2].hasAttribute('href'));
        element._path = 'chell.go';
        flushAsynchronousOperations();
        assert.isFalse(linkEls[0].hasAttribute('href'));
        assert.equal(linkEls[1].getAttribute('href'), '42-undefined-undefined');
        assert.equal(linkEls[2].getAttribute('href'),
            '42-glados.txt-10-PARENT');
        element._path = 'not_a_real_file';
        flushAsynchronousOperations();
        assert.equal(linkEls[0].getAttribute('href'),
            '42-wheatley.md-10-PARENT');
        assert.equal(linkEls[1].getAttribute('href'), '42-undefined-undefined');
        assert.equal(linkEls[2].getAttribute('href'), '42-chell.go-10-PARENT');
      });

      test('prev/up/next links with patch range', () => {
        element._changeNum = '42';
        element._patchRange = {
          basePatchNum: '5',
          patchNum: '10',
        };
        element._change = {
          _number: 42,
          revisions: {
            a: {_number: 5, commit: {parents: []}},
            b: {_number: 10, commit: {parents: []}},
          },
        };
        element._files = getFilesFromFileList(
            ['chell.go', 'glados.txt', 'wheatley.md']);
        element._path = 'glados.txt';
        flushAsynchronousOperations();
        const linkEls = dom(element.root).querySelectorAll('.navLink');
        assert.equal(linkEls.length, 3);
        assert.equal(linkEls[0].getAttribute('href'), '42-chell.go-10-5');
        assert.equal(linkEls[1].getAttribute('href'), '42-10-5');
        assert.equal(linkEls[2].getAttribute('href'), '42-wheatley.md-10-5');
        element._path = 'wheatley.md';
        flushAsynchronousOperations();
        assert.equal(linkEls[0].getAttribute('href'), '42-glados.txt-10-5');
        assert.equal(linkEls[1].getAttribute('href'), '42-10-5');
        assert.isFalse(linkEls[2].hasAttribute('href'));
        element._path = 'chell.go';
        flushAsynchronousOperations();
        assert.isFalse(linkEls[0].hasAttribute('href'));
        assert.equal(linkEls[1].getAttribute('href'), '42-10-5');
        assert.equal(linkEls[2].getAttribute('href'), '42-glados.txt-10-5');
      });
    });

    test('_handlePatchChange calls navigateToDiff correctly', () => {
      const navigateStub = sinon.stub(GerritNav, 'navigateToDiff');
      element._change = {_number: 321, project: 'foo/bar'};
      element._path = 'path/to/file.txt';

      element._patchRange = {
        basePatchNum: 'PARENT',
        patchNum: '3',
      };

      const detail = {
        basePatchNum: 'PARENT',
        patchNum: '1',
      };

      element.$.rangeSelect.dispatchEvent(
          new CustomEvent('patch-range-change', {detail, bubbles: false}));

      assert(navigateStub.lastCall.calledWithExactly(element._change,
          element._path, '1', 'PARENT'));
    });

    test('_prefs.manual_review is respected', () => {
      const saveReviewedStub = sinon.stub(element, '_saveReviewedState')
          .callsFake(() => Promise.resolve());
      const getReviewedStub = sinon.stub(element, '_getReviewedStatus')
          .callsFake(() => Promise.resolve());

      sinon.stub(element.$.diffHost, 'reload');
      element._loggedIn = true;
      element.params = {
        view: GerritNav.View.DIFF,
        changeNum: '42',
        patchNum: '2',
        basePatchNum: '1',
        path: '/COMMIT_MSG',
      };
      element._patchRange = {
        patchNum: '2',
        basePatchNum: '1',
      };
      element._prefs = {manual_review: true};
      flushAsynchronousOperations();

      assert.isFalse(saveReviewedStub.called);
      assert.isTrue(getReviewedStub.called);

      element._prefs = {};
      flushAsynchronousOperations();

      assert.isTrue(saveReviewedStub.called);
      assert.isTrue(getReviewedStub.calledOnce);
    });

    test('file review status', () => {
      const saveReviewedStub = sinon.stub(element, '_saveReviewedState')
          .callsFake(() => Promise.resolve());
      sinon.stub(element.$.diffHost, 'reload');

      element._loggedIn = true;
      element.params = {
        view: GerritNav.View.DIFF,
        changeNum: '42',
        patchNum: '2',
        basePatchNum: '1',
        path: '/COMMIT_MSG',
      };
      element._patchRange = {
        patchNum: '2',
        basePatchNum: '1',
      };
      element._prefs = {};
      flushAsynchronousOperations();

      const commitMsg = dom(element.root).querySelector(
          'input[type="checkbox"]');

      assert.isTrue(commitMsg.checked);
      MockInteractions.tap(commitMsg);
      assert.isFalse(commitMsg.checked);
      assert.isTrue(saveReviewedStub.lastCall.calledWithExactly(false));

      MockInteractions.tap(commitMsg);
      assert.isTrue(commitMsg.checked);
      assert.isTrue(saveReviewedStub.lastCall.calledWithExactly(true));
      const callCount = saveReviewedStub.callCount;

      element.set('params.view', GerritNav.View.CHANGE);
      flushAsynchronousOperations();

      // saveReviewedState observer observes params, but should not fire when
      // view !== GerritNav.View.DIFF.
      assert.equal(saveReviewedStub.callCount, callCount);
    });

    test('file review status with edit loaded', () => {
      const saveReviewedStub = sinon.stub(element, '_saveReviewedState');

      element._patchRange = {patchNum: SPECIAL_PATCH_SET_NUM.EDIT};
      flushAsynchronousOperations();

      assert.isTrue(element._editMode);
      element._setReviewed();
      assert.isFalse(saveReviewedStub.called);
    });

    test('hash is determined from params', done => {
      sinon.stub(element.$.diffHost, 'reload');
      sinon.stub(element, '_initLineOfInterestAndCursor');

      element._loggedIn = true;
      element.params = {
        view: GerritNav.View.DIFF,
        changeNum: '42',
        patchNum: '2',
        basePatchNum: '1',
        path: '/COMMIT_MSG',
        hash: 10,
      };

      flush(() => {
        assert.isTrue(element._initLineOfInterestAndCursor.calledOnce);
        done();
      });
    });

    test('diff mode selector correctly toggles the diff', () => {
      const select = element.$.modeSelect;
      const diffDisplay = element.$.diffHost;
      element._userPrefs = {default_diff_view: 'SIDE_BY_SIDE'};

      // The mode selected in the view state reflects the selected option.
      assert.equal(element._getDiffViewMode(), select.mode);

      // The mode selected in the view state reflects the view rednered in the
      // diff.
      assert.equal(select.mode, diffDisplay.viewMode);

      // We will simulate a user change of the selected mode.
      const newMode = 'UNIFIED_DIFF';

      // Set the mode, and simulate the change event.
      element.set('changeViewState.diffMode', newMode);

      // Make sure the handler was called and the state is still coherent.
      assert.equal(element._getDiffViewMode(), newMode);
      assert.equal(element._getDiffViewMode(), select.mode);
      assert.equal(element._getDiffViewMode(), diffDisplay.viewMode);
    });

    test('diff mode selector initializes from preferences', () => {
      let resolvePrefs;
      const prefsPromise = new Promise(resolve => {
        resolvePrefs = resolve;
      });
      sinon.stub(element.$.restAPI, 'getPreferences')
          .callsFake(() => prefsPromise);

      // Attach a new gr-diff-view so we can intercept the preferences fetch.
      const view = document.createElement('gr-diff-view');
      blankFixture.instantiate().appendChild(view);
      flushAsynchronousOperations();

      // At this point the diff mode doesn't yet have the user's preference.
      assert.equal(view._getDiffViewMode(), 'SIDE_BY_SIDE');

      // Receive the overriding preference.
      resolvePrefs({default_diff_view: 'UNIFIED'});
      flushAsynchronousOperations();
      assert.equal(element._getDiffViewMode(), 'SIDE_BY_SIDE');
    });

    test('diff mode selector should be hidden for binary', done => {
      element._diff = {binary: true, content: []};

      flush(() => {
        const diffModeSelector = element.shadowRoot
            .querySelector('.diffModeSelector');
        assert.isTrue(diffModeSelector.classList.contains('hide'));
        done();
      });
    });

    suite('_commitRange', () => {
      const change = {
        _number: 42,
        revisions: {
          'commit-sha-1': {
            _number: 1,
            commit: {
              parents: [{commit: 'sha-1-parent'}],
            },
          },
          'commit-sha-2': {_number: 2, commit: {parents: []}},
          'commit-sha-3': {_number: 3, commit: {parents: []}},
          'commit-sha-4': {_number: 4, commit: {parents: []}},
          'commit-sha-5': {
            _number: 5,
            commit: {
              parents: [{commit: 'sha-5-parent'}],
            },
          },
        },
      };
      setup(() => {
        sinon.stub(element.$.diffHost, 'reload');
        sinon.stub(element, '_initCursor');
        element._change = change;
        sinon.stub(element, '_getChangeDetail').returns(Promise.resolve(
            change));
      });

      test('uses the patchNum and basePatchNum ', done => {
        element.params = {
          view: GerritNav.View.DIFF,
          changeNum: '42',
          patchNum: '4',
          basePatchNum: '2',
          path: '/COMMIT_MSG',
        };
        element._change = change;
        flush(() => {
          assert.deepEqual(element._commitRange, {
            baseCommit: 'commit-sha-2',
            commit: 'commit-sha-4',
          });
          done();
        });
      });

      test('uses the parent when there is no base patch num ', done => {
        element.params = {
          view: GerritNav.View.DIFF,
          changeNum: '42',
          patchNum: '5',
          path: '/COMMIT_MSG',
        };
        element._change = change;
        flush(() => {
          assert.deepEqual(element._commitRange, {
            commit: 'commit-sha-5',
            baseCommit: 'sha-5-parent',
          });
          done();
        });
      });
    });

    test('_initCursor', () => {
      assert.isNotOk(element.$.cursor.initialLineNumber);

      // Does nothing when params specify no cursor address:
      element._initCursor({});
      assert.isNotOk(element.$.cursor.initialLineNumber);

      // Does nothing when params specify side but no number:
      element._initCursor({leftSide: true});
      assert.isNotOk(element.$.cursor.initialLineNumber);

      // Revision hash: specifies lineNum but not side.
      element._initCursor({lineNum: 234});
      assert.equal(element.$.cursor.initialLineNumber, 234);
      assert.equal(element.$.cursor.side, 'right');

      // Base hash: specifies lineNum and side.
      element._initCursor({leftSide: true, lineNum: 345});
      assert.equal(element.$.cursor.initialLineNumber, 345);
      assert.equal(element.$.cursor.side, 'left');

      // Specifies right side:
      element._initCursor({leftSide: false, lineNum: 123});
      assert.equal(element.$.cursor.initialLineNumber, 123);
      assert.equal(element.$.cursor.side, 'right');
    });

    test('_getLineOfInterest', () => {
      assert.isNull(element._getLineOfInterest({}));

      let result = element._getLineOfInterest({lineNum: 12});
      assert.equal(result.number, 12);
      assert.isNotOk(result.leftSide);

      result = element._getLineOfInterest({lineNum: 12, leftSide: true});
      assert.equal(result.number, 12);
      assert.isOk(result.leftSide);
    });

    test('_onLineSelected', () => {
      const getUrlStub = sinon.stub(GerritNav, 'getUrlForDiffById');
      const replaceStateStub = sinon.stub(history, 'replaceState');
      sinon.stub(element.$.cursor, 'getAddress')
          .returns({number: 123, isLeftSide: false});

      element._changeNum = 321;
      element._change = {_number: 321, project: 'foo/bar'};
      element._patchRange = {
        basePatchNum: '3',
        patchNum: '5',
      };
      const e = {};
      const detail = {number: 123, side: 'right'};

      element._onLineSelected(e, detail);

      assert.isTrue(replaceStateStub.called);
      assert.isTrue(getUrlStub.called);
    });

    test('_onLineSelected w/o line address', () => {
      const getUrlStub = sinon.stub(GerritNav, 'getUrlForDiffById');
      sinon.stub(history, 'replaceState');
      sinon.stub(element.$.cursor, 'moveToLineNumber');
      sinon.stub(element.$.cursor, 'getAddress').returns(null);
      element._changeNum = 321;
      element._change = {_number: 321, project: 'foo/bar'};
      element._patchRange = {basePatchNum: '3', patchNum: '5'};
      element._onLineSelected({}, {number: 123, side: 'right'});
      assert.isTrue(getUrlStub.calledOnce);
      assert.isUndefined(getUrlStub.lastCall.args[5]);
      assert.isUndefined(getUrlStub.lastCall.args[6]);
    });

    test('_getDiffViewMode', () => {
      // No user prefs or change view state set.
      assert.equal(element._getDiffViewMode(), 'SIDE_BY_SIDE');

      // User prefs but no change view state set.
      element._userPrefs = {default_diff_view: 'UNIFIED_DIFF'};
      assert.equal(element._getDiffViewMode(), 'UNIFIED_DIFF');

      // User prefs and change view state set.
      element.changeViewState = {diffMode: 'SIDE_BY_SIDE'};
      assert.equal(element._getDiffViewMode(), 'SIDE_BY_SIDE');
    });

    test('_handleToggleDiffMode', () => {
      sinon.stub(element, 'shouldSuppressKeyboardShortcut').returns(false);
      const e = {preventDefault: () => {}};
      // Initial state.
      assert.equal(element._getDiffViewMode(), 'SIDE_BY_SIDE');

      element._handleToggleDiffMode(e);
      assert.equal(element._getDiffViewMode(), 'UNIFIED_DIFF');

      element._handleToggleDiffMode(e);
      assert.equal(element._getDiffViewMode(), 'SIDE_BY_SIDE');
    });

    suite('_initPatchRange', () => {
      test('empty', () => {
        sinon.stub(element, '_getCommentsForPath');
        sinon.stub(element, '_getPaths').returns(new Map());
        element.params = {};
        element._initPatchRange();
        assert.equal(Object.keys(element._commentMap).length, 0);
      });

      test('has paths', () => {
        sinon.stub(element, '_getFiles');
        sinon.stub(element, '_getPaths').returns({
          'path/to/file/one.cpp': [{patch_set: 3, message: 'lorem'}],
          'path-to/file/two.py': [{patch_set: 5, message: 'ipsum'}],
        });
        sinon.stub(element, '_getCommentsForPath').returns({meta: {}});
        element._changeNum = '42';
        element._patchRange = {
          basePatchNum: '3',
          patchNum: '5',
        };
        element.params = {};
        element._initPatchRange();
        assert.deepEqual(Object.keys(element._commentMap),
            ['path/to/file/one.cpp', 'path-to/file/two.py']);
      });
    });

    suite('_computeCommentSkips', () => {
      test('empty file list', () => {
        const commentMap = {
          'path/one.jpg': true,
          'path/three.wav': true,
        };
        const path = 'path/two.m4v';
        const fileList = [];
        const result = element._computeCommentSkips(commentMap, fileList, path);
        assert.isNull(result.previous);
        assert.isNull(result.next);
      });

      test('finds skips', () => {
        const fileList = ['path/one.jpg', 'path/two.m4v', 'path/three.wav'];
        let path = fileList[1];
        const commentMap = {};
        commentMap[fileList[0]] = true;
        commentMap[fileList[1]] = false;
        commentMap[fileList[2]] = true;

        let result = element._computeCommentSkips(commentMap, fileList, path);
        assert.equal(result.previous, fileList[0]);
        assert.equal(result.next, fileList[2]);

        commentMap[fileList[1]] = true;

        result = element._computeCommentSkips(commentMap, fileList, path);
        assert.equal(result.previous, fileList[0]);
        assert.equal(result.next, fileList[2]);

        path = fileList[0];

        result = element._computeCommentSkips(commentMap, fileList, path);
        assert.isNull(result.previous);
        assert.equal(result.next, fileList[1]);

        path = fileList[2];

        result = element._computeCommentSkips(commentMap, fileList, path);
        assert.equal(result.previous, fileList[1]);
        assert.isNull(result.next);
      });

      suite('skip next/previous', () => {
        let navToChangeStub;
        let navToDiffStub;

        setup(() => {
          navToChangeStub = sinon.stub(element, '_navToChangeView');
          navToDiffStub = sinon.stub(GerritNav, 'navigateToDiff');
          element._files = getFilesFromFileList([
            'path/one.jpg', 'path/two.m4v', 'path/three.wav',
          ]);
          element._patchRange = {patchNum: '2', basePatchNum: '1'};
        });

        suite('_moveToPreviousFileWithComment', () => {
          test('no skips', () => {
            element._moveToPreviousFileWithComment();
            assert.isFalse(navToChangeStub.called);
            assert.isFalse(navToDiffStub.called);
          });

          test('no previous', () => {
            const commentMap = {};
            commentMap[element._fileList[0]] = false;
            commentMap[element._fileList[1]] = false;
            commentMap[element._fileList[2]] = true;
            element._commentMap = commentMap;
            element._path = element._fileList[1];

            element._moveToPreviousFileWithComment();
            assert.isTrue(navToChangeStub.calledOnce);
            assert.isFalse(navToDiffStub.called);
          });

          test('w/ previous', () => {
            const commentMap = {};
            commentMap[element._fileList[0]] = true;
            commentMap[element._fileList[1]] = false;
            commentMap[element._fileList[2]] = true;
            element._commentMap = commentMap;
            element._path = element._fileList[1];

            element._moveToPreviousFileWithComment();
            assert.isFalse(navToChangeStub.called);
            assert.isTrue(navToDiffStub.calledOnce);
          });
        });

        suite('_moveToNextFileWithComment', () => {
          test('no skips', () => {
            element._moveToNextFileWithComment();
            assert.isFalse(navToChangeStub.called);
            assert.isFalse(navToDiffStub.called);
          });

          test('no previous', () => {
            const commentMap = {};
            commentMap[element._fileList[0]] = true;
            commentMap[element._fileList[1]] = false;
            commentMap[element._fileList[2]] = false;
            element._commentMap = commentMap;
            element._path = element._fileList[1];

            element._moveToNextFileWithComment();
            assert.isTrue(navToChangeStub.calledOnce);
            assert.isFalse(navToDiffStub.called);
          });

          test('w/ previous', () => {
            const commentMap = {};
            commentMap[element._fileList[0]] = true;
            commentMap[element._fileList[1]] = false;
            commentMap[element._fileList[2]] = true;
            element._commentMap = commentMap;
            element._path = element._fileList[1];

            element._moveToNextFileWithComment();
            assert.isFalse(navToChangeStub.called);
            assert.isTrue(navToDiffStub.calledOnce);
          });
        });
      });
    });

    test('_computeEditMode', () => {
      const callCompute = range => element._computeEditMode({base: range});
      assert.isFalse(callCompute({}));
      assert.isFalse(callCompute({basePatchNum: 'PARENT', patchNum: 1}));
      assert.isFalse(callCompute({basePatchNum: 'edit', patchNum: 1}));
      assert.isTrue(callCompute({basePatchNum: 1, patchNum: 'edit'}));
    });

    test('_computeFileNum', () => {
      assert.equal(element._computeFileNum('/foo',
          [{value: '/foo'}, {value: '/bar'}]), 1);
      assert.equal(element._computeFileNum('/bar',
          [{value: '/foo'}, {value: '/bar'}]), 2);
    });

    test('_computeFileNumClass', () => {
      assert.equal(element._computeFileNumClass(0, []), '');
      assert.equal(element._computeFileNumClass(1,
          [{value: '/foo'}, {value: '/bar'}]), 'show');
    });

    test('_getReviewedStatus', () => {
      const promises = [];
      element.$.restAPI.getReviewedFiles.restore();

      sinon.stub(element.$.restAPI, 'getReviewedFiles')
          .returns(Promise.resolve(['path']));

      promises.push(element._getReviewedStatus(true, null, null, 'path')
          .then(reviewed => assert.isFalse(reviewed)));

      promises.push(element._getReviewedStatus(false, null, null, 'otherPath')
          .then(reviewed => assert.isFalse(reviewed)));

      promises.push(element._getReviewedStatus(false, null, null, 'path')
          .then(reviewed => assert.isTrue(reviewed)));

      return Promise.all(promises);
    });

    suite('blame', () => {
      test('toggle blame with button', () => {
        const toggleBlame = sinon.stub(
            element.$.diffHost, 'loadBlame')
            .callsFake(() => Promise.resolve());
        MockInteractions.tap(element.$.toggleBlame);
        assert.isTrue(toggleBlame.calledOnce);
      });
      test('toggle blame with shortcut', () => {
        const toggleBlame = sinon.stub(
            element.$.diffHost, 'loadBlame').callsFake(() => Promise.resolve());
        MockInteractions.pressAndReleaseKeyOn(element, 66, null, 'b');
        assert.isTrue(toggleBlame.calledOnce);
      });
    });

    suite('editMode behavior', () => {
      setup(() => {
        element._loggedIn = true;
      });

      const isVisible = el => {
        assert.ok(el);
        return getComputedStyle(el).getPropertyValue('display') !== 'none';
      };

      test('reviewed checkbox', () => {
        sinon.stub(element, '_handlePatchChange');
        element._patchRange = {patchNum: '1'};
        // Reviewed checkbox should be shown.
        assert.isTrue(isVisible(element.$.reviewed));
        element.set('_patchRange.patchNum', SPECIAL_PATCH_SET_NUM.EDIT);
        flushAsynchronousOperations();

        assert.isFalse(isVisible(element.$.reviewed));
      });
    });

    test('_paramsChanged sets in projectLookup', () => {
      sinon.stub(element, '_initLineOfInterestAndCursor');
      const setStub = sinon.stub(element.$.restAPI, 'setInProjectLookup');
      element._paramsChanged({
        view: GerritNav.View.DIFF,
        changeNum: 101,
        project: 'test-project',
        path: '',
      });
      assert.isTrue(setStub.calledOnce);
      assert.isTrue(setStub.calledWith(101, 'test-project'));
    });

    test('shift+m navigates to next unreviewed file', () => {
      element._files = getFilesFromFileList(['file1', 'file2', 'file3']);
      element._reviewedFiles = new Set(['file1', 'file2']);
      element._path = 'file1';
      const reviewedStub = sinon.stub(element, '_setReviewed');
      const navStub = sinon.stub(element, '_navToFile');
      MockInteractions.pressAndReleaseKeyOn(element, 77, 'shift', 'm');
      flushAsynchronousOperations();

      assert.isTrue(reviewedStub.lastCall.args[0]);
      assert.deepEqual(navStub.lastCall.args, [
        'file1',
        ['file1', 'file3'],
        1,
      ]);
    });

    test('File change should trigger navigateToDiff once', done => {
      element._files = getFilesFromFileList(['file1', 'file2', 'file3']);
      sinon.stub(element, '_initLineOfInterestAndCursor');
      sinon.stub(GerritNav, 'navigateToDiff');

      // Load file1
      element.params = {
        view: GerritNav.View.DIFF,
        patchNum: 1,
        changeNum: 101,
        project: 'test-project',
        path: 'file1',
      };
      element._patchRange = {
        patchNum: 1,
        basePatchNum: 'PARENT',
      };
      flushAsynchronousOperations();
      assert.isTrue(GerritNav.navigateToDiff.notCalled);

      // Switch to file2
      element._handleFileChange({detail: {value: 'file2'}});
      assert.isTrue(GerritNav.navigateToDiff.calledOnce);

      // This is to mock the param change triggered by above navigate
      element.params = {
        view: GerritNav.View.DIFF,
        patchNum: 1,
        changeNum: 101,
        project: 'test-project',
        path: 'file2',
      };
      element._patchRange = {
        patchNum: 1,
        basePatchNum: 'PARENT',
      };

      // No extra call
      assert.isTrue(GerritNav.navigateToDiff.calledOnce);
      done();
    });

    test('_computeDownloadDropdownLinks', () => {
      const downloadLinks = [
        {
          url: '/changes/test~12/revisions/1/patch?zip&path=index.php',
          name: 'Patch',
        },
        {
          url: '/changes/test~12/revisions/1' +
              '/files/index.php/download?parent=1',
          name: 'Left Content',
        },
        {
          url: '/changes/test~12/revisions/1' +
              '/files/index.php/download',
          name: 'Right Content',
        },
      ];

      const side = {
        meta_a: true,
        meta_b: true,
      };

      const base = {
        patchNum: 1,
        basePatchNum: 'PARENT',
      };

      assert.deepEqual(
          element._computeDownloadDropdownLinks(
              'test', 12, base, 'index.php', side),
          downloadLinks);
    });

    test('_computeDownloadDropdownLinks diff returns renamed', () => {
      const downloadLinks = [
        {
          url: '/changes/test~12/revisions/3/patch?zip&path=index.php',
          name: 'Patch',
        },
        {
          url: '/changes/test~12/revisions/2' +
              '/files/index2.php/download',
          name: 'Left Content',
        },
        {
          url: '/changes/test~12/revisions/3' +
              '/files/index.php/download',
          name: 'Right Content',
        },
      ];

      const side = {
        change_type: 'RENAMED',
        meta_a: {
          name: 'index2.php',
        },
        meta_b: true,
      };

      const base = {
        patchNum: 3,
        basePatchNum: 2,
      };

      assert.deepEqual(
          element._computeDownloadDropdownLinks(
              'test', 12, base, 'index.php', side),
          downloadLinks);
    });

    test('_computeDownloadFileLink', () => {
      const base = {
        patchNum: 1,
        basePatchNum: 'PARENT',
      };

      assert.equal(
          element._computeDownloadFileLink(
              'test', 12, base, 'index.php', true),
          '/changes/test~12/revisions/1/files/index.php/download?parent=1');

      assert.equal(
          element._computeDownloadFileLink(
              'test', 12, base, 'index.php', false),
          '/changes/test~12/revisions/1/files/index.php/download');
    });

    test('_computeDownloadPatchLink', () => {
      assert.equal(
          element._computeDownloadPatchLink(
              'test', 12, {patchNum: 1}, 'index.php'),
          '/changes/test~12/revisions/1/patch?zip&path=index.php');
    });
  });

  suite('gr-diff-view tests unmodified files with comments', () => {
    let element;
    setup(() => {
      const changedFiles = {
        'file1.txt': {},
        'a/b/test.c': {},
      };
      stub('gr-rest-api-interface', {
        getConfig() { return Promise.resolve({change: {}}); },
        getLoggedIn() { return Promise.resolve(false); },
        getProjectConfig() { return Promise.resolve({}); },
        getDiffChangeDetail() { return Promise.resolve({}); },
        getChangeFiles() { return Promise.resolve(changedFiles); },
        saveFileReviewed() { return Promise.resolve(); },
        getDiffComments() { return Promise.resolve({}); },
        getDiffRobotComments() { return Promise.resolve({}); },
        getDiffDrafts() { return Promise.resolve({}); },
        getReviewedFiles() { return Promise.resolve([]); },
      });
      element = basicFixture.instantiate();
      return element._loadComments();
    });

    test('_getFiles add files with comments without changes', () => {
      const patchChangeRecord = {
        base: {
          basePatchNum: '5',
          patchNum: '10',
        },
      };
      const changeComments = {
        getPaths: sinon.stub().returns({
          'file2.txt': {},
          'file1.txt': {},
        }),
      };
      return element._getFiles(23, patchChangeRecord, changeComments)
          .then(() => {
            assert.deepEqual(element._files, {
              sortedFileList: ['a/b/test.c', 'file1.txt', 'file2.txt'],
              changeFilesByPath: {
                'file1.txt': {},
                'file2.txt': {status: 'U'},
                'a/b/test.c': {},
              },
            });
          });
    });
  });
});

