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
import {GerritNav} from '../../core/gr-navigation/gr-navigation.js';
import {ChangeStatus, DiffViewMode, createDefaultDiffPrefs} from '../../../constants/constants.js';
import {stubRestApi, stubUsers, waitUntil} from '../../../test/test-utils.js';
import {ChangeComments} from '../gr-comment-api/gr-comment-api.js';
import {GerritView} from '../../../services/router/router-model.js';
import {
  createChange,
  createRevisions,
  createComment,
  TEST_NUMERIC_CHANGE_ID,
} from '../../../test/test-data-generators.js';
import {EditPatchSetNum} from '../../../types/common.js';
import {CursorMoveResult} from '../../../api/core.js';
import {Side} from '../../../api/diff.js';

const basicFixture = fixtureFromElement('gr-diff-view');

suite('gr-diff-view tests', () => {
  suite('basic tests', () => {
    let element;
    let clock;
    let diffCommentsStub;

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

    setup(async () => {
      stubRestApi('getConfig').returns(Promise.resolve({change: {}}));
      stubRestApi('getLoggedIn').returns(Promise.resolve(false));
      stubRestApi('getProjectConfig').returns(Promise.resolve({}));
      stubRestApi('getChangeFiles').returns(Promise.resolve({}));
      stubRestApi('saveFileReviewed').returns(Promise.resolve());
      diffCommentsStub = stubRestApi('getDiffComments');
      diffCommentsStub.returns(Promise.resolve({}));
      stubRestApi('getDiffRobotComments').returns(Promise.resolve({}));
      stubRestApi('getDiffDrafts').returns(Promise.resolve({}));
      stubRestApi('getPortedComments').returns(Promise.resolve({}));

      element = basicFixture.instantiate();
      element._changeNum = '42';
      element._path = 'some/path.txt';
      element._change = {};
      element._diff = {content: []};
      element._patchRange = {
        patchNum: 77,
        basePatchNum: 'PARENT',
      };
      element._changeComments = new ChangeComments({'/COMMIT_MSG': [
        {
          ...createComment(),
          id: 'c1',
          line: 10,
          patch_set: 2,
          path: '/COMMIT_MSG',
        }, {
          ...createComment(),
          id: 'c3',
          line: 10,
          patch_set: 'PARENT',
          path: '/COMMIT_MSG',
        },
      ]});
      await flush();

      element.getCommentsModel().setState({
        comments: {},
        robotComments: {},
        drafts: {},
        portedComments: {},
        portedDrafts: {},
        discardedDrafts: [],
      });
    });

    teardown(() => {
      clock && clock.restore();
      sinon.restore();
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
        patchNum: 2,
        basePatchNum: 1,
        path: '/COMMIT_MSG',
      };
      element._path = '/COMMIT_MSG';
      element._patchRange = {};
      return element._paramsChanged.returnValues[0].then(() => {
        assert.isTrue(element.reporting.diffViewDisplayed.calledOnce);
      });
    });

    suite('comment route', () => {
      let initLineOfInterestAndCursorStub; let getUrlStub; let replaceStateStub;
      setup(() => {
        initLineOfInterestAndCursorStub =
        sinon.stub(element, '_initLineOfInterestAndCursor');
        getUrlStub = sinon.stub(GerritNav, 'getUrlForDiffById');
        replaceStateStub = sinon.stub(history, 'replaceState');
        sinon.stub(element, '_getFiles');
        sinon.stub(element.reporting, 'diffViewDisplayed');
        sinon.stub(element.$.diffHost, 'reload').returns(Promise.resolve());
        sinon.spy(element, '_paramsChanged');
        element.getChangeModel().setState({
          change: {
            ...createChange(),
            revisions: createRevisions(11),
          }});
      });

      test('comment url resolves to comment.patch_set vs latest', () => {
        element.getCommentsModel().setState({
          comments: {
            '/COMMIT_MSG': [
              {
                ...createComment(),
                id: 'c1',
                line: 10,
                patch_set: 2,
                path: '/COMMIT_MSG',
              }, {
                ...createComment(),
                id: 'c3',
                line: 10,
                patch_set: 'PARENT',
                path: '/COMMIT_MSG',
              },
            ]},
          robotComments: {},
          drafts: {},
          portedComments: {},
          portedDrafts: {},
          discardedDrafts: [],
        });
        element.params = {
          view: GerritNav.View.DIFF,
          changeNum: '42',
          commentLink: true,
          commentId: 'c1',
          path: 'abcd',
        };
        element._change = {
          ...createChange(),
          revisions: createRevisions(11),
        };
        return element._paramsChanged.returnValues[0].then(() => {
          assert.isTrue(initLineOfInterestAndCursorStub.
              calledWithExactly(true));
          assert.equal(element._focusLineNum, 10);
          assert.equal(element._patchRange.patchNum, 11);
          assert.equal(element._patchRange.basePatchNum, 2);
          assert.isTrue(replaceStateStub.called);
          assert.isTrue(getUrlStub.calledWithExactly('42', 'test-project',
              '/COMMIT_MSG', 11, 2, 10, true));
        });
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
        patchNum: 2,
        basePatchNum: 1,
        path: '/COMMIT_MSG',
      };
      element._path = '/COMMIT_MSG';
      element._patchRange = {};
      return element._paramsChanged.returnValues[0].then(() => {
        assert.isTrue(element._isBlameLoaded);
        assert.isTrue(element._loadBlame.calledOnce);
      });
    });

    test('unchanged diff X vs latest from comment links navigates to base vs X'
        , () => {
          const diffNavStub = sinon.stub(GerritNav, 'navigateToDiff');
          element.getCommentsModel().setState({
            comments: {
              '/COMMIT_MSG': [
                {
                  ...createComment(),
                  id: 'c1',
                  line: 10,
                  patch_set: 2,
                  path: '/COMMIT_MSG',
                }, {
                  ...createComment(),
                  id: 'c3',
                  line: 10,
                  patch_set: 'PARENT',
                  path: '/COMMIT_MSG',
                },
              ]},
            robotComments: {},
            drafts: {},
            portedComments: {},
            portedDrafts: {},
            discardedDrafts: [],
          });
          sinon.stub(element.reporting, 'diffViewDisplayed');
          sinon.stub(element, '_loadBlame');
          sinon.stub(element.$.diffHost, 'reload').returns(Promise.resolve());
          sinon.stub(element, '_isFileUnchanged').returns(true);
          sinon.spy(element, '_paramsChanged');
          element.getChangeModel().setState({
            change: {
              ...createChange(),
              revisions: createRevisions(11),
            }});
          element.params = {
            view: GerritNav.View.DIFF,
            changeNum: '42',
            path: '/COMMIT_MSG',
            commentLink: true,
            commentId: 'c1',
          };
          element._change = {
            ...createChange(),
            revisions: createRevisions(11),
          };
          return element._paramsChanged.returnValues[0].then(() => {
            assert.isTrue(diffNavStub.lastCall.calledWithExactly(
                element._change, '/COMMIT_MSG', 2, 'PARENT', 10));
          });
        });

    test('unchanged diff Base vs latest from comment does not navigate'
        , () => {
          const diffNavStub = sinon.stub(GerritNav, 'navigateToDiff');
          element.getCommentsModel().setState({
            comments: {
              '/COMMIT_MSG': [
                {
                  ...createComment(),
                  id: 'c1',
                  line: 10,
                  patch_set: 2,
                  path: '/COMMIT_MSG',
                }, {
                  ...createComment(),
                  id: 'c3',
                  line: 10,
                  patch_set: 'PARENT',
                  path: '/COMMIT_MSG',
                },
              ]},
            robotComments: {},
            drafts: {},
            portedComments: {},
            portedDrafts: {},
            discardedDrafts: [],
          });
          sinon.stub(element.reporting, 'diffViewDisplayed');
          sinon.stub(element, '_loadBlame');
          sinon.stub(element.$.diffHost, 'reload').returns(Promise.resolve());
          sinon.stub(element, '_isFileUnchanged').returns(true);
          sinon.spy(element, '_paramsChanged');
          element.getChangeModel().setState({
            change: {
              ...createChange(),
              revisions: createRevisions(11),
            }});
          element.params = {
            view: GerritNav.View.DIFF,
            changeNum: '42',
            path: '/COMMIT_MSG',
            commentLink: true,
            commentId: 'c3',
          };
          element._change = {
            ...createChange(),
            revisions: createRevisions(11),
          };
          return element._paramsChanged.returnValues[0].then(() => {
            assert.isFalse(diffNavStub.called);
          });
        });

    test('_isFileUnchanged', () => {
      let diff = {
        content: [
          {a: 'abcd', ab: 'ef'},
          {b: 'ancd', a: 'xx'},
        ],
      };
      assert.equal(element._isFileUnchanged(diff), false);
      diff = {
        content: [
          {ab: 'abcd'},
          {ab: 'ancd'},
        ],
      };
      assert.equal(element._isFileUnchanged(diff), true);
      diff = {
        content: [
          {a: 'abcd', ab: 'ef', common: true},
          {b: 'ancd', ab: 'xx'},
        ],
      };
      assert.equal(element._isFileUnchanged(diff), false);
      diff = {
        content: [
          {a: 'abcd', ab: 'ef', common: true},
          {b: 'ancd', ab: 'xx', common: true},
        ],
      };
      assert.equal(element._isFileUnchanged(diff), true);
    });

    test('diff toast to go to latest is shown and not base', async () => {
      element.getCommentsModel().setState({
        comments: {
          '/COMMIT_MSG': [
            {
              ...createComment(),
              id: 'c1',
              line: 10,
              patch_set: 2,
              path: '/COMMIT_MSG',
            }, {
              ...createComment(),
              id: 'c3',
              line: 10,
              patch_set: 'PARENT',
              path: '/COMMIT_MSG',
            },
          ]},
        robotComments: {},
        drafts: {},
        portedComments: {},
        portedDrafts: {},
        discardedDrafts: [],
      });

      sinon.stub(element.reporting, 'diffViewDisplayed');
      sinon.stub(element, '_loadBlame');
      sinon.stub(element.$.diffHost, 'reload').returns(Promise.resolve());
      sinon.spy(element, '_paramsChanged');
      element._change = undefined;
      element.getChangeModel().setState({
        change: {
          ...createChange(),
          revisions: createRevisions(11),
        }});
      element._patchRange = {
        patchNum: 2,
        basePatchNum: 1,
      };
      sinon.stub(element, '_isFileUnchanged').returns(false);
      const toastStub =
          sinon.stub(element, '_displayDiffBaseAgainstLeftToast');
      element.params = {
        view: GerritNav.View.DIFF,
        changeNum: '42',
        project: 'p',
        commentId: 'c1',
        commentLink: true,
      };
      await element._paramsChanged.returnValues[0];
      assert.isTrue(toastStub.called);
    });

    test('toggle left diff with a hotkey', () => {
      const toggleLeftDiffStub = sinon.stub(
          element.$.diffHost, 'toggleLeftDiff');
      MockInteractions.pressAndReleaseKeyOn(element, 65, null, 'A');
      assert.isTrue(toggleLeftDiffStub.calledOnce);
    });

    test('keyboard shortcuts', () => {
      clock = sinon.useFakeTimers();
      element._changeNum = '42';
      element.getBrowserModel().setScreenWidth(0);
      element._patchRange = {
        basePatchNum: PARENT,
        patchNum: 10,
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
          10, PARENT), 'Should navigate to /c/42/10/wheatley.md');
      element._path = 'wheatley.md';
      assert.equal(element.changeViewState.selectedFileIndex, 2);
      assert.isTrue(element._loading);

      MockInteractions.pressAndReleaseKeyOn(element, 219, null, '[');
      assert(diffNavStub.lastCall.calledWith(element._change, 'glados.txt',
          10, PARENT), 'Should navigate to /c/42/10/glados.txt');
      element._path = 'glados.txt';
      assert.equal(element.changeViewState.selectedFileIndex, 1);
      assert.isTrue(element._loading);

      MockInteractions.pressAndReleaseKeyOn(element, 219, null, '[');
      assert(diffNavStub.lastCall.calledWith(element._change, 'chell.go', 10,
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

      let scrollStub = sinon.stub(element.cursor, 'moveToNextChunk');
      MockInteractions.pressAndReleaseKeyOn(element, 78, null, 'n');
      assert(scrollStub.calledOnce);

      scrollStub = sinon.stub(element.cursor, 'moveToPreviousChunk');
      MockInteractions.pressAndReleaseKeyOn(element, 80, null, 'p');
      assert(scrollStub.calledOnce);

      scrollStub = sinon.stub(element.cursor, 'moveToNextCommentThread');
      MockInteractions.pressAndReleaseKeyOn(element, 78, null, 'N');
      assert(scrollStub.calledOnce);

      scrollStub = sinon.stub(element.cursor,
          'moveToPreviousCommentThread');
      MockInteractions.pressAndReleaseKeyOn(element, 80, null, 'P');
      assert(scrollStub.calledOnce);

      const computeContainerClassStub = sinon.stub(element.$.diffHost.$.diff,
          '_computeContainerClass');
      MockInteractions.pressAndReleaseKeyOn(element, 74, null, 'j');
      assert(computeContainerClassStub.lastCall.calledWithExactly(
          false, DiffViewMode.SIDE_BY_SIDE, true));

      MockInteractions.pressAndReleaseKeyOn(element, 27, null, 'Escape');
      assert(computeContainerClassStub.lastCall.calledWithExactly(
          false, DiffViewMode.SIDE_BY_SIDE, false));

      // Note that stubbing _setReviewed means that the value of the
      // `element.$.reviewed` checkbox is not flipped.
      sinon.stub(element, '_setReviewed');
      sinon.spy(element, '_handleToggleFileReviewed');
      element.$.reviewed.checked = false;
      assert.isFalse(element._handleToggleFileReviewed.called);
      assert.isFalse(element._setReviewed.called);

      MockInteractions.pressAndReleaseKeyOn(element, 82, null, 'r');
      assert.isTrue(element._handleToggleFileReviewed.calledOnce);
      assert.isTrue(element._setReviewed.calledOnce);
      assert.equal(element._setReviewed.lastCall.args[0], true);

      // Handler is throttled, so another key press within 500 ms is ignored.
      clock.tick(100);
      MockInteractions.pressAndReleaseKeyOn(element, 82, null, 'r');
      assert.isTrue(element._handleToggleFileReviewed.calledOnce);
      assert.isTrue(element._setReviewed.calledOnce);

      clock.tick(1000);
      MockInteractions.pressAndReleaseKeyOn(element, 82, null, 'r');
      assert.isTrue(element._handleToggleFileReviewed.calledTwice);
      assert.isTrue(element._setReviewed.calledTwice);
      clock.restore();
    });

    test('moveToNextCommentThread navigates to next file', () => {
      const diffNavStub = sinon.stub(GerritNav, 'navigateToDiff');
      const diffChangeStub = sinon.stub(element, '_navigateToChange');
      sinon.stub(element.cursor, 'isAtEnd').returns(true);
      element._changeNum = '42';
      const comment = {
        'wheatley.md': [{
          ...createComment(),
          patch_set: 10,
          line: 21,
        }],
      };
      element._changeComments = new ChangeComments(comment);
      element._patchRange = {
        basePatchNum: PARENT,
        patchNum: 10,
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

      MockInteractions.pressAndReleaseKeyOn(element, 78, null, 'N');
      flush();
      assert.isTrue(diffNavStub.calledWithExactly(
          element._change, 'wheatley.md', 10, PARENT, 21));

      element._path = 'wheatley.md'; // navigated to next file

      MockInteractions.pressAndReleaseKeyOn(element, 78, null, 'N');
      flush();

      assert.isTrue(diffChangeStub.called);
    });

    test('shift+x shortcut toggles all diff context', () => {
      const toggleStub = sinon.stub(element.$.diffHost, 'toggleAllContext');
      MockInteractions.pressAndReleaseKeyOn(element, 88, null, 'X');
      flush();
      assert.isTrue(toggleStub.called);
    });

    test('diff against base', () => {
      element._patchRange = {
        basePatchNum: 5,
        patchNum: 10,
      };
      const diffNavStub = sinon.stub(GerritNav, 'navigateToDiff');
      element._handleDiffAgainstBase(new CustomEvent(''));
      const args = diffNavStub.getCall(0).args;
      assert.equal(args[2], 10);
      assert.isNotOk(args[3]);
    });

    test('diff against latest', () => {
      element._change = {
        ...createChange(),
        revisions: createRevisions(12),
      };
      element._patchRange = {
        basePatchNum: 5,
        patchNum: 10,
      };
      const diffNavStub = sinon.stub(GerritNav, 'navigateToDiff');
      element._handleDiffAgainstLatest(new CustomEvent(''));
      const args = diffNavStub.getCall(0).args;
      assert.equal(args[2], 12);
      assert.equal(args[3], 5);
    });

    test('_handleDiffBaseAgainstLeft', () => {
      element._change = {
        ...createChange(),
        revisions: createRevisions(10),
      };
      element._patchRange = {
        patchNum: 3,
        basePatchNum: 1,
      };
      element.params = {};
      const diffNavStub = sinon.stub(GerritNav, 'navigateToDiff');
      element._handleDiffBaseAgainstLeft(new CustomEvent(''));
      assert(diffNavStub.called);
      const args = diffNavStub.getCall(0).args;
      assert.equal(args[2], 1);
      assert.equal(args[3], 'PARENT');
      assert.isNotOk(args[4]);
    });

    test('_handleDiffBaseAgainstLeft when initially navigating to a comment',
        () => {
          element._change = {
            ...createChange(),
            revisions: createRevisions(10),
          };
          element._patchRange = {
            patchNum: 3,
            basePatchNum: 1,
          };
          sinon.stub(element, '_paramsChanged');
          element.params = {commentLink: true, view: GerritView.DIFF};
          element._focusLineNum = 10;
          const diffNavStub = sinon.stub(GerritNav, 'navigateToDiff');
          element._handleDiffBaseAgainstLeft(new CustomEvent(''));
          assert(diffNavStub.called);
          const args = diffNavStub.getCall(0).args;
          assert.equal(args[2], 1);
          assert.equal(args[3], 'PARENT');
          assert.equal(args[4], 10);
        });

    test('_handleDiffRightAgainstLatest', () => {
      element._change = {
        ...createChange(),
        revisions: createRevisions(10),
      };
      element._patchRange = {
        basePatchNum: 1,
        patchNum: 3,
      };
      const diffNavStub = sinon.stub(GerritNav, 'navigateToDiff');
      element._handleDiffRightAgainstLatest(new CustomEvent(''));
      assert(diffNavStub.called);
      const args = diffNavStub.getCall(0).args;
      assert.equal(args[2], 10);
      assert.equal(args[3], 3);
    });

    test('_handleDiffBaseAgainstLatest', () => {
      element._change = {
        ...createChange(),
        revisions: createRevisions(10),
      };
      element._patchRange = {
        basePatchNum: 1,
        patchNum: 3,
      };
      const diffNavStub = sinon.stub(GerritNav, 'navigateToDiff');
      element._handleDiffBaseAgainstLatest(new CustomEvent(''));
      assert(diffNavStub.called);
      const args = diffNavStub.getCall(0).args;
      assert.equal(args[2], 10);
      assert.isNotOk(args[3]);
    });

    test('A fires an error event when not logged in', async () => {
      const changeNavStub = sinon.stub(GerritNav, 'navigateToChange');
      sinon.stub(element, '_getLoggedIn').returns(Promise.resolve(false));
      const loggedInErrorSpy = sinon.spy();
      element.addEventListener('show-auth-required', loggedInErrorSpy);
      MockInteractions.pressAndReleaseKeyOn(element, 65, null, 'a');
      await flush();
      assert.isTrue(changeNavStub.notCalled, 'The `a` keyboard shortcut ' +
        'should only work when the user is logged in.');
      assert.isNull(window.sessionStorage.getItem(
          'changeView.showReplyDialog'));
      assert.isTrue(loggedInErrorSpy.called);
    });

    test('A navigates to change with logged in', async () => {
      element._changeNum = '42';
      element._patchRange = {
        basePatchNum: 5,
        patchNum: 10,
      };
      element._change = {
        _number: 42,
        revisions: {
          a: {_number: 10, commit: {parents: []}},
          b: {_number: 5, commit: {parents: []}},
        },
      };
      const changeNavStub = sinon.stub(GerritNav, 'navigateToChange');
      sinon.stub(element, '_getLoggedIn').returns(Promise.resolve(true));
      const loggedInErrorSpy = sinon.spy();
      element.addEventListener('show-auth-required', loggedInErrorSpy);
      MockInteractions.pressAndReleaseKeyOn(element, 65, null, 'a');
      await flush();
      assert.isTrue(element.changeViewState.showReplyDialog);
      assert(changeNavStub.lastCall.calledWithExactly(element._change, {
        patchNum: 10, basePatchNum: 5}), 'Should navigate to /c/42/5..10');
      assert.isFalse(loggedInErrorSpy.called);
    });

    test('A navigates to change with old patch number with logged in',
        async () => {
          element._changeNum = '42';
          element._patchRange = {
            basePatchNum: PARENT,
            patchNum: 1,
          };
          element._change = {
            _number: 42,
            revisions: {
              a: {_number: 1, commit: {parents: []}},
              b: {_number: 2, commit: {parents: []}},
            },
          };
          const changeNavStub = sinon.stub(GerritNav, 'navigateToChange');
          sinon.stub(element, '_getLoggedIn').returns(Promise.resolve(true));
          const loggedInErrorSpy = sinon.spy();
          element.addEventListener('show-auth-required', loggedInErrorSpy);
          MockInteractions.pressAndReleaseKeyOn(element, 65, null, 'a');
          await flush();
          assert.isTrue(element.changeViewState.showReplyDialog);
          assert(changeNavStub.lastCall.calledWithExactly(element._change, {
            patchNum: 1, basePatchNum: PARENT}), 'Should navigate to /c/42/1');
          assert.isFalse(loggedInErrorSpy.called);
        });

    test('keyboard shortcuts with patch range', () => {
      element._changeNum = '42';
      element._patchRange = {
        basePatchNum: 5,
        patchNum: 10,
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

      MockInteractions.pressAndReleaseKeyOn(element, 85, null, 'u');
      assert(changeNavStub.lastCall.calledWithExactly(element._change,
          {patchNum: 10, basePatchNum: 5}), 'Should navigate to /c/42/5..10');

      MockInteractions.pressAndReleaseKeyOn(element, 221, null, ']');
      assert.isTrue(element._loading);
      assert(diffNavStub.lastCall.calledWithExactly(element._change,
          'wheatley.md', 10, 5, undefined),
      'Should navigate to /c/42/5..10/wheatley.md');
      element._path = 'wheatley.md';

      MockInteractions.pressAndReleaseKeyOn(element, 219, null, '[');
      assert.isTrue(element._loading);
      assert(diffNavStub.lastCall.calledWithExactly(element._change,
          'glados.txt', 10, 5, undefined),
      'Should navigate to /c/42/5..10/glados.txt');
      element._path = 'glados.txt';

      MockInteractions.pressAndReleaseKeyOn(element, 219, null, '[');
      assert.isTrue(element._loading);
      assert(diffNavStub.lastCall.calledWithExactly(
          element._change,
          'chell.go',
          10,
          5,
          undefined),
      'Should navigate to /c/42/5..10/chell.go');
      element._path = 'chell.go';

      MockInteractions.pressAndReleaseKeyOn(element, 219, null, '[');
      assert.isTrue(element._loading);
      assert(changeNavStub.lastCall.calledWithExactly(element._change,
          {patchNum: 10, basePatchNum: 5}),
      'Should navigate to /c/42/5..10');

      const downloadOverlayStub = sinon.stub(element.$.downloadOverlay, 'open')
          .returns(Promise.resolve());
      MockInteractions.pressAndReleaseKeyOn(element, 68, null, 'd');
      assert.isTrue(downloadOverlayStub.called);
    });

    test('keyboard shortcuts with old patch number', () => {
      element._changeNum = '42';
      element._patchRange = {
        basePatchNum: PARENT,
        patchNum: 1,
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

      MockInteractions.pressAndReleaseKeyOn(element, 85, null, 'u');
      assert(changeNavStub.lastCall.calledWithExactly(element._change,
          {patchNum: 1, basePatchNum: PARENT}), 'Should navigate to /c/42/1');

      MockInteractions.pressAndReleaseKeyOn(element, 221, null, ']');
      assert(diffNavStub.lastCall.calledWithExactly(element._change,
          'wheatley.md', 1, PARENT, undefined),
      'Should navigate to /c/42/1/wheatley.md');
      element._path = 'wheatley.md';

      MockInteractions.pressAndReleaseKeyOn(element, 219, null, '[');
      assert(diffNavStub.lastCall.calledWithExactly(element._change,
          'glados.txt', 1, PARENT, undefined),
      'Should navigate to /c/42/1/glados.txt');
      element._path = 'glados.txt';

      MockInteractions.pressAndReleaseKeyOn(element, 219, null, '[');
      assert(diffNavStub.lastCall.calledWithExactly(
          element._change,
          'chell.go',
          1,
          PARENT,
          undefined), 'Should navigate to /c/42/1/chell.go');
      element._path = 'chell.go';

      changeNavStub.reset();
      MockInteractions.pressAndReleaseKeyOn(element, 219, null, '[');
      assert(changeNavStub.lastCall.calledWithExactly(element._change,
          {patchNum: 1, basePatchNum: PARENT}), 'Should navigate to /c/42/1');
      assert.isTrue(changeNavStub.calledOnce);
    });

    test('edit should redirect to edit page', async () => {
      element._loggedIn = true;
      element._path = 't.txt';
      element._patchRange = {
        basePatchNum: PARENT,
        patchNum: 1,
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
      await flush();
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
    });

    test('edit should redirect to edit page with line number', async () => {
      const lineNumber = 42;
      element._loggedIn = true;
      element._path = 't.txt';
      element._patchRange = {
        basePatchNum: PARENT,
        patchNum: 1,
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
      sinon.stub(element.cursor, 'getAddress')
          .returns({number: lineNumber, isLeftSide: false});
      const redirectStub = sinon.stub(GerritNav, 'navigateToRelativeUrl');
      await flush();
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
    });

    function isEditVisibile({loggedIn, changeStatus}) {
      return new Promise(resolve => {
        element._loggedIn = loggedIn;
        element._path = 't.txt';
        element._patchRange = {
          basePatchNum: PARENT,
          patchNum: 1,
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
      for (const changeStatus of Object.keys(ChangeStatus)) {
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
        element._prefs = undefined;
        element.disableDiffPrefs = false;
        element._loggedIn = false;
        flush();
        assert.isTrue(element.$.diffPrefsContainer.hidden);

        element._loggedIn = true;
        flush();
        assert.isTrue(element.$.diffPrefsContainer.hidden);

        element._loggedIn = false;
        element._prefs = {font_size: '12'};
        flush();
        assert.isTrue(element.$.diffPrefsContainer.hidden);

        element._loggedIn = true;
        element._prefs = {font_size: '12'};
        flush();
        assert.isFalse(element.$.diffPrefsContainer.hidden);
      });
    });

    test('prefsButton opens gr-diff-preferences', () => {
      const handlePrefsTapSpy = sinon.spy(element, '_handlePrefsTap');
      const overlayOpenStub = sinon.stub(element.$.diffPreferencesDialog,
          'open');
      const prefsButton =
          element.root.querySelector('.prefsButton');

      MockInteractions.tap(prefsButton);

      assert.isTrue(handlePrefsTapSpy.called);
      assert.isTrue(overlayOpenStub.called);
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
            .callsFake((c, ops) =>
              `${c._number}-${ops.patchNum}-${ops.basePatchNum}`);
      });

      test('_formattedFiles', () => {
        element._changeNum = '42';
        element._patchRange = {
          basePatchNum: PARENT,
          patchNum: 10,
        };
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
            file: {
              __path: 'chell.go',
            },
          }, {
            text: 'glados.txt',
            mobileText: 'glados.txt',
            value: 'glados.txt',
            bottomText: '',
            file: {
              __path: 'glados.txt',
            },
          }, {
            text: 'wheatley.md',
            mobileText: 'wheatley.md',
            value: 'wheatley.md',
            bottomText: '',
            file: {
              __path: 'wheatley.md',
            },
          },
          {
            text: 'Commit message',
            mobileText: 'Commit message',
            value: '/COMMIT_MSG',
            bottomText: '',
            file: {
              __path: '/COMMIT_MSG',
            },
          },
          {
            text: 'Merge list',
            mobileText: 'Merge list',
            value: '/MERGE_LIST',
            bottomText: '',
            file: {
              __path: '/MERGE_LIST',
            },
          },
        ];

        assert.deepEqual(element._formattedFiles, expectedFormattedFiles);
        assert.equal(element._formattedFiles[1].value, element._path);
      });

      test('prev/up/next links', () => {
        element._changeNum = '42';
        element._patchRange = {
          basePatchNum: PARENT,
          patchNum: 10,
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
        flush();
        const linkEls = element.root.querySelectorAll('.navLink');
        assert.equal(linkEls.length, 3);
        assert.equal(linkEls[0].getAttribute('href'), '42-chell.go-10-PARENT');
        assert.equal(linkEls[1].getAttribute('href'), '42-undefined-undefined');
        assert.equal(linkEls[2].getAttribute('href'),
            '42-wheatley.md-10-PARENT');
        element._path = 'wheatley.md';
        flush();
        assert.equal(linkEls[0].getAttribute('href'),
            '42-glados.txt-10-PARENT');
        assert.equal(linkEls[1].getAttribute('href'), '42-undefined-undefined');
        assert.equal(linkEls[2].getAttribute('href'), '42-undefined-undefined');
        element._path = 'chell.go';
        flush();
        assert.equal(linkEls[0].getAttribute('href'), '42-undefined-undefined');
        assert.equal(linkEls[1].getAttribute('href'), '42-undefined-undefined');
        assert.equal(linkEls[2].getAttribute('href'),
            '42-glados.txt-10-PARENT');
        element._path = 'not_a_real_file';
        flush();
        assert.equal(linkEls[0].getAttribute('href'),
            '42-wheatley.md-10-PARENT');
        assert.equal(linkEls[1].getAttribute('href'), '42-undefined-undefined');
        assert.equal(linkEls[2].getAttribute('href'), '42-chell.go-10-PARENT');
      });

      test('prev/up/next links with patch range', () => {
        element._changeNum = '42';
        element._patchRange = {
          basePatchNum: 5,
          patchNum: 10,
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
        flush();
        const linkEls = element.root.querySelectorAll('.navLink');
        assert.equal(linkEls.length, 3);
        assert.equal(linkEls[0].getAttribute('href'), '42-chell.go-10-5');
        assert.equal(linkEls[1].getAttribute('href'), '42-10-5');
        assert.equal(linkEls[2].getAttribute('href'), '42-wheatley.md-10-5');
        element._path = 'wheatley.md';
        flush();
        assert.equal(linkEls[0].getAttribute('href'), '42-glados.txt-10-5');
        assert.equal(linkEls[1].getAttribute('href'), '42-10-5');
        assert.equal(linkEls[2].getAttribute('href'), '42-10-5');
        element._path = 'chell.go';
        flush();
        assert.equal(linkEls[0].getAttribute('href'),
            '42-10-5');
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
        patchNum: 3,
      };

      const detail = {
        basePatchNum: 'PARENT',
        patchNum: 1,
      };

      element.$.rangeSelect.dispatchEvent(
          new CustomEvent('patch-range-change', {detail, bubbles: false}));

      assert(navigateStub.lastCall.calledWithExactly(element._change,
          element._path, 1, 'PARENT'));
    });

    test('_prefs.manual_review true means set reviewed is not ' +
      'automatically called', async () => {
      const setReviewedFileStatusStub =
        sinon.stub(element.getChangeModel(), 'setReviewedFilesStatus')
            .callsFake(() => Promise.resolve());

      const setReviewedStatusStub = sinon.spy(element, 'setReviewedStatus');

      sinon.stub(element.$.diffHost, 'reload');
      sinon.stub(element, '_getLoggedIn').returns(Promise.resolve(true));
      const diffPreferences = {
        ...createDefaultDiffPrefs(),
        manual_review: true,
      };
      element.userModel.setDiffPreferences(diffPreferences);
      element.getChangeModel().setState({
        change: createChange(),
        diffPath: '/COMMIT_MSG',
        reviewedFiles: [],
      });

      element.routerModel.setState({
        changeNum: TEST_NUMERIC_CHANGE_ID, view: GerritView.DIFF, patchNum: 2}
      );
      element._patchRange = {
        patchNum: 2,
        basePatchNum: 1,
      };

      await waitUntil(() => setReviewedStatusStub.called);

      assert.isFalse(setReviewedFileStatusStub.called);

      // if prefs are updated then the reviewed status should not be set again
      element.userModel.setDiffPreferences(createDefaultDiffPrefs());

      await flush();
      assert.isFalse(setReviewedFileStatusStub.called);
    });

    test('_prefs.manual_review false means set reviewed is called',
        async () => {
          const setReviewedFileStatusStub =
              sinon.stub(element.getChangeModel(), 'setReviewedFilesStatus')
                  .callsFake(() => Promise.resolve());

          sinon.stub(element.$.diffHost, 'reload');
          sinon.stub(element, '_getLoggedIn').returns(Promise.resolve(true));
          const diffPreferences = {
            ...createDefaultDiffPrefs(),
            manual_review: false,
          };
          element.userModel.setDiffPreferences(diffPreferences);
          element.getChangeModel().setState({
            change: createChange(),
            diffPath: '/COMMIT_MSG',
            reviewedFiles: [],
          });

          element.routerModel.setState({
            changeNum: TEST_NUMERIC_CHANGE_ID, view: GerritView.DIFF,
            patchNum: 22}
          );
          element._patchRange = {
            patchNum: 2,
            basePatchNum: 1,
          };

          await waitUntil(() => setReviewedFileStatusStub.called);

          assert.isTrue(setReviewedFileStatusStub.called);
        });

    test('file review status', async () => {
      element.getChangeModel().setState({
        change: createChange(),
        diffPath: '/COMMIT_MSG',
        reviewedFiles: [],
      });
      sinon.stub(element, '_getLoggedIn').returns(Promise.resolve(true));
      const saveReviewedStub =
          sinon.stub(element.getChangeModel(), 'setReviewedFilesStatus')
              .callsFake(() => Promise.resolve());
      sinon.stub(element.$.diffHost, 'reload');

      element.userModel.setDiffPreferences(createDefaultDiffPrefs());

      element.routerModel.setState({
        changeNum: TEST_NUMERIC_CHANGE_ID, view: GerritView.DIFF, patchNum: 2}
      );

      element._patchRange = {
        patchNum: 2,
        basePatchNum: 1,
      };

      await waitUntil(() => saveReviewedStub.called);

      element.getChangeModel().updateStateFileReviewed('/COMMIT_MSG', true);
      await flush();

      const reviewedStatusCheckBox = element.root.querySelector(
          'input[type="checkbox"]');

      assert.isTrue(reviewedStatusCheckBox.checked);
      assert.deepEqual(saveReviewedStub.lastCall.args,
          ['42', 2, '/COMMIT_MSG', true]);

      MockInteractions.tap(reviewedStatusCheckBox);
      assert.isFalse(reviewedStatusCheckBox.checked);
      assert.deepEqual(saveReviewedStub.lastCall.args,
          ['42', 2, '/COMMIT_MSG', false]);

      element.getChangeModel().updateStateFileReviewed('/COMMIT_MSG', false);
      await flush();

      MockInteractions.tap(reviewedStatusCheckBox);
      assert.isTrue(reviewedStatusCheckBox.checked);
      assert.deepEqual(saveReviewedStub.lastCall.args,
          ['42', 2, '/COMMIT_MSG', true]);

      const callCount = saveReviewedStub.callCount;

      element.set('params.view', GerritNav.View.CHANGE);
      await flush();

      // saveReviewedState observer observes params, but should not fire when
      // view !== GerritNav.View.DIFF.
      assert.equal(saveReviewedStub.callCount, callCount);
    });

    test('file review status with edit loaded', () => {
      const saveReviewedStub =
          sinon.stub(element.getChangeModel(), 'setReviewedFilesStatus');

      element._patchRange = {patchNum: EditPatchSetNum};
      flush();

      assert.isTrue(element._editMode);
      element._setReviewed();
      assert.isFalse(saveReviewedStub.called);
    });

    test('hash is determined from params', async () => {
      sinon.stub(element.$.diffHost, 'reload');
      sinon.stub(element, '_initLineOfInterestAndCursor');

      element._loggedIn = true;
      element.params = {
        view: GerritNav.View.DIFF,
        changeNum: '42',
        patchNum: 2,
        basePatchNum: 1,
        path: '/COMMIT_MSG',
        hash: 10,
      };

      await flush();
      assert.isTrue(element._initLineOfInterestAndCursor.calledOnce);
    });

    test('diff mode selector correctly toggles the diff', () => {
      const select = element.$.modeSelect;
      const diffDisplay = element.$.diffHost;
      element._userPrefs = {diff_view: DiffViewMode.SIDE_BY_SIDE};
      element.getBrowserModel().setScreenWidth(0);

      const userStub = stubUsers('updatePreferences');

      flush();
      // The mode selected in the view state reflects the selected option.
      // assert.equal(element._userPrefs.diff_view, select.mode);

      // The mode selected in the view state reflects the view rednered in the
      // diff.
      assert.equal(select.mode, diffDisplay.viewMode);

      // We will simulate a user change of the selected mode.
      element._handleToggleDiffMode();
      assert.isTrue(userStub.calledWithExactly({
        diff_view: DiffViewMode.UNIFIED}));
    });

    test('diff mode selector should be hidden for binary', async () => {
      element._diff = {binary: true, content: []};

      await flush();
      const diffModeSelector = element.shadowRoot
          .querySelector('.diffModeSelector');
      assert.isTrue(diffModeSelector.classList.contains('hide'));
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
      });

      test('uses the patchNum and basePatchNum ', async () => {
        element.params = {
          view: GerritNav.View.DIFF,
          changeNum: '42',
          patchNum: 4,
          basePatchNum: 2,
          path: '/COMMIT_MSG',
        };
        element._change = change;
        await flush();
        assert.deepEqual(element._commitRange, {
          baseCommit: 'commit-sha-2',
          commit: 'commit-sha-4',
        });
      });

      test('uses the parent when there is no base patch num ', async () => {
        element.params = {
          view: GerritNav.View.DIFF,
          changeNum: '42',
          patchNum: 5,
          path: '/COMMIT_MSG',
        };
        element._change = change;
        await flush();
        assert.deepEqual(element._commitRange, {
          commit: 'commit-sha-5',
          baseCommit: 'sha-5-parent',
        });
      });
    });

    test('_initCursor', () => {
      assert.isNotOk(element.cursor.initialLineNumber);

      // Does nothing when params specify no cursor address:
      element._initCursor(false);
      assert.isNotOk(element.cursor.initialLineNumber);

      // Does nothing when params specify side but no number:
      element._initCursor(true);
      assert.isNotOk(element.cursor.initialLineNumber);

      // Revision hash: specifies lineNum but not side.

      element._focusLineNum = 234;
      element._initCursor(false);
      assert.equal(element.cursor.initialLineNumber, 234);
      assert.equal(element.cursor.side, 'right');

      // Base hash: specifies lineNum and side.
      element._focusLineNum = 345;
      element._initCursor(true);
      assert.equal(element.cursor.initialLineNumber, 345);
      assert.equal(element.cursor.side, 'left');

      // Specifies right side:
      element._focusLineNum = 123;
      element._initCursor(false);
      assert.equal(element.cursor.initialLineNumber, 123);
      assert.equal(element.cursor.side, 'right');
    });

    test('_getLineOfInterest', () => {
      assert.isUndefined(element._getLineOfInterest(false));

      element._focusLineNum = 12;
      let result = element._getLineOfInterest(false);
      assert.equal(result.lineNum, 12);
      assert.equal(result.side, Side.RIGHT);

      result = element._getLineOfInterest(true);
      assert.equal(result.lineNum, 12);
      assert.equal(result.side, Side.LEFT);
    });

    test('_onLineSelected', () => {
      const getUrlStub = sinon.stub(GerritNav, 'getUrlForDiffById');
      const replaceStateStub = sinon.stub(history, 'replaceState');
      sinon.stub(element.cursor, 'getAddress')
          .returns({number: 123, isLeftSide: false});

      element._changeNum = 321;
      element._change = {_number: 321, project: 'foo/bar'};
      element._patchRange = {
        basePatchNum: 3,
        patchNum: 5,
      };
      const e = {};
      const detail = {number: 123, side: 'right'};

      element._onLineSelected(e, detail);

      assert.isTrue(replaceStateStub.called);
      assert.isTrue(getUrlStub.called);
      assert.isFalse(getUrlStub.lastCall.args[6]);
    });

    test('line selected on left side', () => {
      const getUrlStub = sinon.stub(GerritNav, 'getUrlForDiffById');
      const replaceStateStub = sinon.stub(history, 'replaceState');
      sinon.stub(element.cursor, 'getAddress')
          .returns({number: 123, isLeftSide: true});

      element._changeNum = 321;
      element._change = {_number: 321, project: 'foo/bar'};
      element._patchRange = {
        basePatchNum: 3,
        patchNum: 5,
      };
      const e = {};
      const detail = {number: 123, side: 'left'};

      element._onLineSelected(e, detail);

      assert.isTrue(replaceStateStub.called);
      assert.isTrue(getUrlStub.called);
      assert.isTrue(getUrlStub.lastCall.args[6]);
    });

    test('_handleToggleDiffMode', () => {
      const userStub = stubUsers('updatePreferences');
      const e = new CustomEvent('keydown', {
        detail: {keyboardEvent: new KeyboardEvent('keydown'), key: 'x'},
      });
      element._userPrefs = {diff_view: DiffViewMode.SIDE_BY_SIDE};

      element._handleToggleDiffMode(e);
      assert.deepEqual(userStub.lastCall.args[0], {
        diff_view: DiffViewMode.UNIFIED});

      element._userPrefs = {diff_view: DiffViewMode.UNIFIED};

      element._handleToggleDiffMode(e);
      assert.deepEqual(userStub.lastCall.args[0], {
        diff_view: DiffViewMode.SIDE_BY_SIDE});
    });

    suite('_initPatchRange', () => {
      setup(async () => {
        stubRestApi('getDiff').returns(Promise.resolve({}));
        element.params = {
          view: GerritView.DIFF,
          changeNum: '42',
          patchNum: 3,
          path: 'abcd',
        };
        await flush();
      });
      test('empty', () => {
        sinon.stub(element, '_getPaths').returns(new Map());
        element._initPatchRange();
        assert.equal(Object.keys(element._commentMap).length, 0);
      });

      test('has paths', () => {
        sinon.stub(element, '_getFiles');
        sinon.stub(element, '_getPaths').returns({
          'path/to/file/one.cpp': [{patch_set: 3, message: 'lorem'}],
          'path-to/file/two.py': [{patch_set: 5, message: 'ipsum'}],
        });
        element._changeNum = '42';
        element._patchRange = {
          basePatchNum: 3,
          patchNum: 5,
        };
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
          element._patchRange = {patchNum: 2, basePatchNum: 1};
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

    test('f open file dropdown', () => {
      assert.isFalse(element.$.dropdown.$.dropdown.opened);
      MockInteractions.pressAndReleaseKeyOn(element, 70, null, 'f');
      flush();
      assert.isTrue(element.$.dropdown.$.dropdown.opened);
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
        element._patchRange = {patchNum: 1};
        // Reviewed checkbox should be shown.
        assert.isTrue(isVisible(element.$.reviewed));
        element.set('_patchRange.patchNum', EditPatchSetNum);
        flush();

        assert.isFalse(isVisible(element.$.reviewed));
      });
    });

    suite('switching files', () => {
      let dispatchEventStub;
      let navToFileStub;
      let moveToPreviousChunkStub;
      let moveToNextChunkStub;
      let isAtStartStub;
      let isAtEndStub;
      let nowStub;

      setup(() => {
        dispatchEventStub = sinon.stub(
            element, 'dispatchEvent').callThrough();
        navToFileStub = sinon.stub(element, '_navToFile');
        moveToPreviousChunkStub =
            sinon.stub(element.cursor, 'moveToPreviousChunk');
        moveToNextChunkStub =
            sinon.stub(element.cursor, 'moveToNextChunk');
        isAtStartStub = sinon.stub(element.cursor, 'isAtStart');
        isAtEndStub = sinon.stub(element.cursor, 'isAtEnd');
        nowStub = sinon.stub(Date, 'now');
      });

      test('shows toast when at the end of file', () => {
        moveToNextChunkStub.returns(CursorMoveResult.CLIPPED);
        isAtEndStub.returns(true);

        MockInteractions.pressAndReleaseKeyOn(element, 78, null, 'n');

        assert.isTrue(moveToNextChunkStub.called);
        assert.equal(dispatchEventStub.lastCall.args[0].type, 'show-alert');
        assert.isFalse(navToFileStub.called);
      });

      test('navigates to next file when n is tapped again', () => {
        moveToNextChunkStub.returns(CursorMoveResult.CLIPPED);
        isAtEndStub.returns(true);

        element._files = getFilesFromFileList(['file1', 'file2', 'file3']);
        element.reviewedFiles = new Set(['file2']);
        element._path = 'file1';

        nowStub.returns(5);
        MockInteractions.pressAndReleaseKeyOn(element, 78, null, 'n');
        nowStub.returns(10);
        MockInteractions.pressAndReleaseKeyOn(element, 78, null, 'n');

        assert.isTrue(navToFileStub.called);
        assert.deepEqual(navToFileStub.lastCall.args, [
          'file1',
          ['file1', 'file3'],
          1,
        ]);
      });

      test('does not navigate if n is tapped twice too slow', () => {
        moveToNextChunkStub.returns(CursorMoveResult.CLIPPED);
        isAtEndStub.returns(true);

        nowStub.returns(5);
        MockInteractions.pressAndReleaseKeyOn(element, 78, null, 'n');
        nowStub.returns(6000);
        MockInteractions.pressAndReleaseKeyOn(element, 78, null, 'n');

        assert.isFalse(navToFileStub.called);
      });

      test('shows toast when at the start of file', () => {
        moveToPreviousChunkStub.returns(CursorMoveResult.CLIPPED);
        isAtStartStub.returns(true);

        MockInteractions.pressAndReleaseKeyOn(element, 80, null, 'p');

        assert.isTrue(moveToPreviousChunkStub.called);
        assert.equal(dispatchEventStub.lastCall.args[0].type, 'show-alert');
        assert.isFalse(navToFileStub.called);
      });

      test('navigates to prev file when p is tapped again', () => {
        moveToPreviousChunkStub.returns(CursorMoveResult.CLIPPED);
        isAtStartStub.returns(true);

        element._files = getFilesFromFileList(['file1', 'file2', 'file3']);
        element.reviewedFiles = new Set(['file2']);
        element._path = 'file3';

        nowStub.returns(5);
        MockInteractions.pressAndReleaseKeyOn(element, 80, null, 'p');
        nowStub.returns(10);
        MockInteractions.pressAndReleaseKeyOn(element, 80, null, 'p');

        assert.isTrue(navToFileStub.called);
        assert.deepEqual(navToFileStub.lastCall.args, [
          'file3',
          ['file1', 'file3'],
          -1,
        ]);
      });

      test('does not navigate if p is tapped twice too slow', () => {
        moveToPreviousChunkStub.returns(CursorMoveResult.CLIPPED);
        isAtStartStub.returns(true);

        nowStub.returns(5);
        MockInteractions.pressAndReleaseKeyOn(element, 80, null, 'p');
        nowStub.returns(6000);
        MockInteractions.pressAndReleaseKeyOn(element, 80, null, 'p');

        assert.isFalse(navToFileStub.called);
      });

      test('does not navigate when tapping n then p', () => {
        moveToNextChunkStub.returns(CursorMoveResult.CLIPPED);
        isAtEndStub.returns(true);

        nowStub.returns(5);
        MockInteractions.pressAndReleaseKeyOn(element, 78, null, 'n');

        moveToPreviousChunkStub.returns(CursorMoveResult.CLIPPED);
        isAtStartStub.returns(true);

        nowStub.returns(10);
        MockInteractions.pressAndReleaseKeyOn(element, 80, null, 'p');

        assert.isFalse(navToFileStub.called);
      });
    });

    test('shift+m navigates to next unreviewed file', () => {
      element._files = getFilesFromFileList(['file1', 'file2', 'file3']);
      element.reviewedFiles = new Set(['file1', 'file2']);
      element._path = 'file1';
      const reviewedStub = sinon.stub(element, '_setReviewed');
      const navStub = sinon.stub(element, '_navToFile');
      MockInteractions.pressAndReleaseKeyOn(element, 77, null, 'M');
      flush();

      assert.isTrue(reviewedStub.lastCall.args[0]);
      assert.deepEqual(navStub.lastCall.args, [
        'file1',
        ['file1', 'file3'],
        1,
      ]);
    });

    test('File change should trigger navigateToDiff once', async () => {
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
      element._change = {
        ...createChange(),
        revisions: createRevisions(1),
      };
      await flush();
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

  suite('unmodified files with comments', () => {
    let element;
    setup(() => {
      const changedFiles = {
        'file1.txt': {},
        'a/b/test.c': {},
      };
      stubRestApi('getConfig').returns(Promise.resolve({change: {}}));

      stubRestApi('getProjectConfig').returns(Promise.resolve({}));
      stubRestApi('getChangeFiles').returns(Promise.resolve(changedFiles));
      stubRestApi('saveFileReviewed').returns(Promise.resolve());
      stubRestApi('getDiffComments').returns(Promise.resolve({}));
      stubRestApi('getDiffRobotComments').returns(Promise.resolve({}));
      stubRestApi('getDiffDrafts').returns(Promise.resolve({}));
      stubRestApi('getReviewedFiles').returns(
          Promise.resolve([]));
      element = basicFixture.instantiate();
      element._changeNum = '42';
    });

    test('_getFiles add files with comments without changes', () => {
      const patchChangeRecord = {
        base: {
          basePatchNum: 5,
          patchNum: 10,
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

