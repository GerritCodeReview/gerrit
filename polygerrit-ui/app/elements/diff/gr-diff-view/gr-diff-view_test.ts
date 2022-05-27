/**
 * @license
 * Copyright 2015 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup-karma';
import './gr-diff-view';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {
  ChangeStatus,
  DiffViewMode,
  createDefaultDiffPrefs,
  createDefaultPreferences,
} from '../../../constants/constants';
import {
  isVisible,
  query,
  queryAll,
  queryAndAssert,
  stubReporting,
  stubRestApi,
  stubUsers,
  waitUntil,
} from '../../../test/test-utils';
import {ChangeComments} from '../gr-comment-api/gr-comment-api';
import {GerritView} from '../../../services/router/router-model';
import {
  createRevisions,
  createComment as createCommentGeneric,
  TEST_NUMERIC_CHANGE_ID,
  createDiff,
  createPatchRange,
  createServerInfo,
  createConfig,
  createParsedChange,
  createRevision,
  createCommit,
  createFileInfo,
} from '../../../test/test-data-generators';
import {
  BasePatchSetNum,
  CommentInfo,
  CommitId,
  DashboardId,
  EditPatchSetNum,
  FileInfo,
  NumericChangeId,
  PARENT,
  PatchRange,
  PatchSetNum,
  PathToCommentsInfoMap,
  RepoName,
  RevisionPatchSetNum,
  UrlEncodedCommentId,
} from '../../../types/common';
import {CursorMoveResult} from '../../../api/core';
import {DiffInfo, Side} from '../../../api/diff';
import {Files, GrDiffView} from './gr-diff-view';
import {DropdownItem} from '../../shared/gr-dropdown-list/gr-dropdown-list';
import {SinonFakeTimers, SinonStub, SinonSpy} from 'sinon';
import {LoadingStatus} from '../../../models/change/change-model';
import * as MockInteractions from '@polymer/iron-test-helpers/mock-interactions';
import {CommentMap} from '../../../utils/comment-util';
import {ParsedChangeInfo} from '../../../types/types';

const basicFixture = fixtureFromElement('gr-diff-view');

function createComment(
  id: string,
  line: number,
  ps: number | PatchSetNum,
  path: string
): CommentInfo {
  return {
    ...createCommentGeneric(),
    id: id as UrlEncodedCommentId,
    line,
    patch_set: ps as RevisionPatchSetNum,
    path,
  };
}

suite('gr-diff-view tests', () => {
  suite('basic tests', () => {
    let element: GrDiffView;
    let clock: SinonFakeTimers;
    let diffCommentsStub;

    function getFilesFromFileList(fileList: string[]): Files {
      const changeFilesByPath = fileList.reduce((files, path) => {
        files[path] = createFileInfo();
        return files;
      }, {} as {[path: string]: FileInfo});
      return {
        sortedFileList: fileList,
        changeFilesByPath,
      };
    }

    setup(async () => {
      stubRestApi('getConfig').returns(Promise.resolve(createServerInfo()));
      stubRestApi('getLoggedIn').returns(Promise.resolve(false));
      stubRestApi('getProjectConfig').returns(Promise.resolve(createConfig()));
      stubRestApi('getChangeFiles').returns(Promise.resolve({}));
      stubRestApi('saveFileReviewed').returns(Promise.resolve(new Response()));
      diffCommentsStub = stubRestApi('getDiffComments');
      diffCommentsStub.returns(Promise.resolve({}));
      stubRestApi('getDiffRobotComments').returns(Promise.resolve({}));
      stubRestApi('getDiffDrafts').returns(Promise.resolve({}));
      stubRestApi('getPortedComments').returns(Promise.resolve({}));

      element = basicFixture.instantiate();
      element._changeNum = 42 as NumericChangeId;
      element._path = 'some/path.txt';
      element._change = createParsedChange();
      element._diff = {...createDiff(), content: []};
      element._patchRange = createPatchRange();
      element._changeComments = new ChangeComments({
        '/COMMIT_MSG': [
          createComment('c1', 10, 2, '/COMMIT_MSG'),
          createComment('c3', 10, PARENT, '/COMMIT_MSG'),
        ],
      });
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
      const diffViewDisplayedStub = stubReporting('diffViewDisplayed');
      sinon.stub(element.$.diffHost, 'reload').returns(Promise.resolve());
      sinon.stub(element, '_initPatchRange');
      sinon.stub(element, '_getFiles');
      const paramsChangedSpy = sinon.spy(element, '_paramsChanged');
      element.params = {
        view: GerritNav.View.DIFF,
        changeNum: 42 as NumericChangeId,
        patchNum: 2 as RevisionPatchSetNum,
        basePatchNum: 1 as BasePatchSetNum,
        path: '/COMMIT_MSG',
      };
      element._path = '/COMMIT_MSG';
      element._patchRange = createPatchRange();
      return paramsChangedSpy.returnValues[0]?.then(() => {
        assert.isTrue(diffViewDisplayedStub.calledOnce);
      });
    });

    suite('comment route', () => {
      let initLineOfInterestAndCursorStub: SinonStub;
      let getUrlStub: SinonStub;
      let replaceStateStub: SinonStub;
      let paramsChangedSpy: SinonSpy;
      setup(() => {
        initLineOfInterestAndCursorStub = sinon.stub(
          element,
          '_initLineOfInterestAndCursor'
        );
        getUrlStub = sinon.stub(GerritNav, 'getUrlForDiffById');
        replaceStateStub = sinon.stub(history, 'replaceState');
        sinon.stub(element, '_getFiles');
        stubReporting('diffViewDisplayed');
        sinon.stub(element.$.diffHost, 'reload').returns(Promise.resolve());
        paramsChangedSpy = sinon.spy(element, '_paramsChanged');
        element.getChangeModel().setState({
          change: {
            ...createParsedChange(),
            revisions: createRevisions(11),
          },
          loadingStatus: LoadingStatus.LOADED,
        });
      });

      test('comment url resolves to comment.patch_set vs latest', () => {
        element.getCommentsModel().setState({
          comments: {
            '/COMMIT_MSG': [
              createComment('c1', 10, 2, '/COMMIT_MSG'),
              createComment('c3', 10, PARENT, '/COMMIT_MSG'),
            ],
          },
          robotComments: {},
          drafts: {},
          portedComments: {},
          portedDrafts: {},
          discardedDrafts: [],
        });
        element.params = {
          view: GerritNav.View.DIFF,
          changeNum: 42 as NumericChangeId,
          commentLink: true,
          commentId: 'c1' as UrlEncodedCommentId,
          path: 'abcd',
          patchNum: 1 as RevisionPatchSetNum,
        };
        element._change = {
          ...createParsedChange(),
          revisions: createRevisions(11),
        };
        return paramsChangedSpy.returnValues[0].then(() => {
          assert.isTrue(
            initLineOfInterestAndCursorStub.calledWithExactly(true)
          );
          assert.equal(element._focusLineNum, 10);
          assert.equal(
            element._patchRange?.patchNum,
            11 as RevisionPatchSetNum
          );
          assert.equal(element._patchRange?.basePatchNum, 2 as BasePatchSetNum);
          assert.isTrue(replaceStateStub.called);
          assert.isTrue(
            getUrlStub.calledWithExactly(
              42,
              'test-project',
              '/COMMIT_MSG',
              11,
              2,
              10,
              true
            )
          );
        });
      });
    });

    test('params change causes blame to load if it was set to true', () => {
      // Blame loads for subsequent files if it was loaded for one file
      element._isBlameLoaded = true;
      stubReporting('diffViewDisplayed');
      const loadBlameStub = sinon.stub(element, '_loadBlame');
      sinon.stub(element.$.diffHost, 'reload').returns(Promise.resolve());
      const paramsChangedSpy = sinon.spy(element, '_paramsChanged');
      sinon.stub(element, '_initPatchRange');
      sinon.stub(element, '_getFiles');
      element.params = {
        view: GerritNav.View.DIFF,
        changeNum: 42 as NumericChangeId,
        patchNum: 2 as RevisionPatchSetNum,
        basePatchNum: 1 as BasePatchSetNum,
        path: '/COMMIT_MSG',
      };
      element._path = '/COMMIT_MSG';
      element._patchRange = createPatchRange();
      return paramsChangedSpy.returnValues[0]!.then(() => {
        assert.isTrue(element._isBlameLoaded);
        assert.isTrue(loadBlameStub.calledOnce);
      });
    });

    test('unchanged diff X vs latest from comment links navigates to base vs X', () => {
      const diffNavStub = sinon.stub(GerritNav, 'navigateToDiff');
      element.getCommentsModel().setState({
        comments: {
          '/COMMIT_MSG': [
            createComment('c1', 10, 2, '/COMMIT_MSG'),
            createComment('c3', 10, PARENT, '/COMMIT_MSG'),
          ],
        },
        robotComments: {},
        drafts: {},
        portedComments: {},
        portedDrafts: {},
        discardedDrafts: [],
      });
      stubReporting('diffViewDisplayed');
      sinon.stub(element, '_loadBlame');
      sinon.stub(element.$.diffHost, 'reload').returns(Promise.resolve());
      sinon.stub(element, '_isFileUnchanged').returns(true);
      const paramsChangedSpy = sinon.spy(element, '_paramsChanged');
      element.getChangeModel().setState({
        change: {
          ...createParsedChange(),
          revisions: createRevisions(11),
        },
        loadingStatus: LoadingStatus.LOADED,
      });
      element.params = {
        view: GerritNav.View.DIFF,
        changeNum: 42 as NumericChangeId,
        path: '/COMMIT_MSG',
        commentLink: true,
        commentId: 'c1' as UrlEncodedCommentId,
      };
      element._change = {
        ...createParsedChange(),
        revisions: createRevisions(11),
      };
      return paramsChangedSpy.returnValues[0]?.then(() => {
        assert.isTrue(
          diffNavStub.lastCall.calledWithExactly(
            element._change!,
            '/COMMIT_MSG',
            2 as RevisionPatchSetNum,
            PARENT,
            10
          )
        );
      });
    });

    test('unchanged diff Base vs latest from comment does not navigate', () => {
      const diffNavStub = sinon.stub(GerritNav, 'navigateToDiff');
      element.getCommentsModel().setState({
        comments: {
          '/COMMIT_MSG': [
            createComment('c1', 10, 2, '/COMMIT_MSG'),
            createComment('c3', 10, PARENT, '/COMMIT_MSG'),
          ],
        },
        robotComments: {},
        drafts: {},
        portedComments: {},
        portedDrafts: {},
        discardedDrafts: [],
      });
      stubReporting('diffViewDisplayed');
      sinon.stub(element, '_loadBlame');
      sinon.stub(element.$.diffHost, 'reload').returns(Promise.resolve());
      sinon.stub(element, '_isFileUnchanged').returns(true);
      const paramsChangedSpy = sinon.spy(element, '_paramsChanged');
      element.getChangeModel().setState({
        change: {
          ...createParsedChange(),
          revisions: createRevisions(11),
        },
        loadingStatus: LoadingStatus.LOADED,
      });
      element.params = {
        view: GerritNav.View.DIFF,
        changeNum: 42 as NumericChangeId,
        path: '/COMMIT_MSG',
        commentLink: true,
        commentId: 'c3' as UrlEncodedCommentId,
      };
      element._change = {
        ...createParsedChange(),
        revisions: createRevisions(11),
      };
      return paramsChangedSpy.returnValues[0]!.then(() => {
        assert.isFalse(diffNavStub.called);
      });
    });

    test('_isFileUnchanged', () => {
      let diff: DiffInfo = {
        ...createDiff(),
        content: [
          {a: ['abcd'], ab: ['ef']},
          {b: ['ancd'], a: ['xx']},
        ],
      };
      assert.equal(element._isFileUnchanged(diff), false);
      diff = {
        ...createDiff(),
        content: [{ab: ['abcd']}, {ab: ['ancd']}],
      };
      assert.equal(element._isFileUnchanged(diff), true);
      diff = {
        ...createDiff(),
        content: [
          {a: ['abcd'], ab: ['ef'], common: true},
          {b: ['ancd'], ab: ['xx']},
        ],
      };
      assert.equal(element._isFileUnchanged(diff), false);
      diff = {
        ...createDiff(),
        content: [
          {a: ['abcd'], ab: ['ef'], common: true},
          {b: ['ancd'], ab: ['xx'], common: true},
        ],
      };
      assert.equal(element._isFileUnchanged(diff), true);
    });

    test('diff toast to go to latest is shown and not base', async () => {
      element.getCommentsModel().setState({
        comments: {
          '/COMMIT_MSG': [
            createComment('c1', 10, 2, '/COMMIT_MSG'),
            createComment('c3', 10, PARENT, '/COMMIT_MSG'),
          ],
        },
        robotComments: {},
        drafts: {},
        portedComments: {},
        portedDrafts: {},
        discardedDrafts: [],
      });

      stubReporting('diffViewDisplayed');
      sinon.stub(element, '_loadBlame');
      sinon.stub(element.$.diffHost, 'reload').returns(Promise.resolve());
      const paramsChangedSpy = sinon.spy(element, '_paramsChanged');
      element._change = undefined;
      element.getChangeModel().setState({
        change: {
          ...createParsedChange(),
          revisions: createRevisions(11),
        },
        loadingStatus: LoadingStatus.LOADED,
      });
      element._patchRange = {
        patchNum: 2 as RevisionPatchSetNum,
        basePatchNum: 1 as BasePatchSetNum,
      };
      sinon.stub(element, '_isFileUnchanged').returns(false);
      const toastStub = sinon.stub(element, '_displayDiffBaseAgainstLeftToast');
      element.params = {
        view: GerritNav.View.DIFF,
        changeNum: 42 as NumericChangeId,
        project: 'p' as RepoName,
        commentId: 'c1' as UrlEncodedCommentId,
        commentLink: true,
      };
      await paramsChangedSpy.returnValues[0];
      assert.isTrue(toastStub.called);
    });

    test('toggle left diff with a hotkey', () => {
      const toggleLeftDiffStub = sinon.stub(
        element.$.diffHost,
        'toggleLeftDiff'
      );
      MockInteractions.pressAndReleaseKeyOn(element, 65, null, 'A');
      assert.isTrue(toggleLeftDiffStub.calledOnce);
    });

    test('keyboard shortcuts', () => {
      clock = sinon.useFakeTimers();
      element._changeNum = 42 as NumericChangeId;
      element.getBrowserModel().setScreenWidth(0);
      element._patchRange = {
        basePatchNum: PARENT,
        patchNum: 10 as RevisionPatchSetNum,
      };
      element._change = {
        ...createParsedChange(),
        _number: 42 as NumericChangeId,
        revisions: {
          a: createRevision(10),
        },
      };
      element._files = getFilesFromFileList([
        'chell.go',
        'glados.txt',
        'wheatley.md',
      ]);
      element._path = 'glados.txt';
      element.changeViewState.selectedFileIndex = 1;
      element._loggedIn = true;

      const diffNavStub = sinon.stub(GerritNav, 'navigateToDiff');
      const changeNavStub = sinon.stub(GerritNav, 'navigateToChange');

      MockInteractions.pressAndReleaseKeyOn(element, 85, null, 'u');
      assert(
        changeNavStub.lastCall.calledWith(element._change),
        'Should navigate to /c/42/'
      );

      MockInteractions.pressAndReleaseKeyOn(element, 221, null, ']');
      assert(
        diffNavStub.lastCall.calledWith(
          element._change,
          'wheatley.md',
          10 as RevisionPatchSetNum,
          PARENT
        ),
        'Should navigate to /c/42/10/wheatley.md'
      );
      element._path = 'wheatley.md';
      assert.equal(element.changeViewState.selectedFileIndex, 2);
      assert.isTrue(element._loading);

      MockInteractions.pressAndReleaseKeyOn(element, 219, null, '[');
      assert(
        diffNavStub.lastCall.calledWith(
          element._change,
          'glados.txt',
          10 as RevisionPatchSetNum,
          PARENT
        ),
        'Should navigate to /c/42/10/glados.txt'
      );
      element._path = 'glados.txt';
      assert.equal(element.changeViewState.selectedFileIndex, 1);
      assert.isTrue(element._loading);

      MockInteractions.pressAndReleaseKeyOn(element, 219, null, '[');
      assert(
        diffNavStub.lastCall.calledWith(
          element._change,
          'chell.go',
          10 as RevisionPatchSetNum,
          PARENT
        ),
        'Should navigate to /c/42/10/chell.go'
      );
      element._path = 'chell.go';
      assert.equal(element.changeViewState.selectedFileIndex, 0);
      assert.isTrue(element._loading);

      MockInteractions.pressAndReleaseKeyOn(element, 219, null, '[');
      assert(
        changeNavStub.lastCall.calledWith(element._change),
        'Should navigate to /c/42/'
      );
      assert.equal(element.changeViewState.selectedFileIndex, 0);
      assert.isTrue(element._loading);

      const showPrefsStub = sinon
        .stub(element.$.diffPreferencesDialog, 'open')
        .callsFake(() => Promise.resolve());

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

      scrollStub = sinon.stub(element.cursor, 'moveToPreviousCommentThread');
      MockInteractions.pressAndReleaseKeyOn(element, 80, null, 'P');
      assert(scrollStub.calledOnce);

      const computeContainerClassStub = sinon.stub(
        element.$.diffHost.$.diff,
        '_computeContainerClass'
      );
      MockInteractions.pressAndReleaseKeyOn(element, 74, null, 'j');
      assert(
        computeContainerClassStub.lastCall.calledWithExactly(
          false,
          DiffViewMode.SIDE_BY_SIDE,
          true
        )
      );

      MockInteractions.pressAndReleaseKeyOn(element, 27, null, 'Escape');
      assert(
        computeContainerClassStub.lastCall.calledWithExactly(
          false,
          DiffViewMode.SIDE_BY_SIDE,
          false
        )
      );

      // Note that stubbing _setReviewed means that the value of the
      // `element.$.reviewed` checkbox is not flipped.
      const setReviewedStub = sinon.stub(element, '_setReviewed');
      const handleToggleSpy = sinon.spy(element, '_handleToggleFileReviewed');
      element.$.reviewed.checked = false;
      assert.isFalse(handleToggleSpy.called);
      assert.isFalse(setReviewedStub.called);

      MockInteractions.pressAndReleaseKeyOn(element, 82, null, 'r');
      assert.isTrue(handleToggleSpy.calledOnce);
      assert.isTrue(setReviewedStub.calledOnce);
      assert.equal(setReviewedStub.lastCall.args[0], true);

      // Handler is throttled, so another key press within 500 ms is ignored.
      clock.tick(100);
      MockInteractions.pressAndReleaseKeyOn(element, 82, null, 'r');
      assert.isTrue(handleToggleSpy.calledOnce);
      assert.isTrue(setReviewedStub.calledOnce);

      clock.tick(1000);
      MockInteractions.pressAndReleaseKeyOn(element, 82, null, 'r');
      assert.isTrue(handleToggleSpy.calledTwice);
      assert.isTrue(setReviewedStub.calledTwice);
      clock.restore();
    });

    test('moveToNextCommentThread navigates to next file', () => {
      const diffNavStub = sinon.stub(GerritNav, 'navigateToDiff');
      const diffChangeStub = sinon.stub(element, '_navigateToChange');
      sinon.stub(element.cursor, 'isAtEnd').returns(true);
      element._changeNum = 42 as NumericChangeId;
      const comment: PathToCommentsInfoMap = {
        'wheatley.md': [createComment('c2', 21, 10, 'wheatley.md')],
      };
      element._changeComments = new ChangeComments(comment);
      element._patchRange = {
        basePatchNum: PARENT,
        patchNum: 10 as RevisionPatchSetNum,
      };
      element._change = {
        ...createParsedChange(),
        _number: 42 as NumericChangeId,
        revisions: {
          a: createRevision(10),
        },
      };
      element._files = getFilesFromFileList([
        'chell.go',
        'glados.txt',
        'wheatley.md',
      ]);
      element._path = 'glados.txt';
      element.changeViewState.selectedFileIndex = 1;
      element._loggedIn = true;

      MockInteractions.pressAndReleaseKeyOn(element, 78, null, 'N');
      flush();
      assert.isTrue(
        diffNavStub.calledWithExactly(
          element._change,
          'wheatley.md',
          10 as RevisionPatchSetNum,
          PARENT,
          21
        )
      );

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
        basePatchNum: 5 as BasePatchSetNum,
        patchNum: 10 as RevisionPatchSetNum,
      };
      const diffNavStub = sinon.stub(GerritNav, 'navigateToDiff');
      element._handleDiffAgainstBase();
      const args = diffNavStub.getCall(0).args;
      assert.equal(args[2], 10 as RevisionPatchSetNum);
      assert.isNotOk(args[3]);
    });

    test('diff against latest', () => {
      element._change = {
        ...createParsedChange(),
        revisions: createRevisions(12),
      };
      element._patchRange = {
        basePatchNum: 5 as BasePatchSetNum,
        patchNum: 10 as RevisionPatchSetNum,
      };
      const diffNavStub = sinon.stub(GerritNav, 'navigateToDiff');
      element._handleDiffAgainstLatest();
      const args = diffNavStub.getCall(0).args;
      assert.equal(args[2], 12 as RevisionPatchSetNum);
      assert.equal(args[3], 5 as BasePatchSetNum);
    });

    test('_handleDiffBaseAgainstLeft', () => {
      element._change = {
        ...createParsedChange(),
        revisions: createRevisions(10),
      };
      element._patchRange = {
        patchNum: 3 as RevisionPatchSetNum,
        basePatchNum: 1 as BasePatchSetNum,
      };
      element.params = {
        view: GerritView.DASHBOARD,
        dashboard: 'id' as DashboardId,
      };
      const diffNavStub = sinon.stub(GerritNav, 'navigateToDiff');
      element._handleDiffBaseAgainstLeft();
      assert(diffNavStub.called);
      const args = diffNavStub.getCall(0).args;
      assert.equal(args[2], 1 as RevisionPatchSetNum);
      assert.equal(args[3], PARENT);
      assert.isNotOk(args[4]);
    });

    test('_handleDiffBaseAgainstLeft when initially navigating to a comment', () => {
      element._change = {
        ...createParsedChange(),
        revisions: createRevisions(10),
      };
      element._patchRange = {
        patchNum: 3 as RevisionPatchSetNum,
        basePatchNum: 1 as BasePatchSetNum,
      };
      sinon.stub(element, '_paramsChanged');
      element.params = {
        commentLink: true,
        view: GerritView.DIFF,
        changeNum: 42 as NumericChangeId,
      };
      element._focusLineNum = 10;
      const diffNavStub = sinon.stub(GerritNav, 'navigateToDiff');
      element._handleDiffBaseAgainstLeft();
      assert(diffNavStub.called);
      const args = diffNavStub.getCall(0).args;
      assert.equal(args[2], 1 as RevisionPatchSetNum);
      assert.equal(args[3], PARENT);
      assert.equal(args[4], 10);
    });

    test('_handleDiffRightAgainstLatest', () => {
      element._change = {
        ...createParsedChange(),
        revisions: createRevisions(10),
      };
      element._patchRange = {
        basePatchNum: 1 as BasePatchSetNum,
        patchNum: 3 as RevisionPatchSetNum,
      };
      const diffNavStub = sinon.stub(GerritNav, 'navigateToDiff');
      element._handleDiffRightAgainstLatest();
      assert(diffNavStub.called);
      const args = diffNavStub.getCall(0).args;
      assert.equal(args[2], 10 as RevisionPatchSetNum);
      assert.equal(args[3], 3 as BasePatchSetNum);
    });

    test('_handleDiffBaseAgainstLatest', () => {
      element._change = {
        ...createParsedChange(),
        revisions: createRevisions(10),
      };
      element._patchRange = {
        basePatchNum: 1 as BasePatchSetNum,
        patchNum: 3 as RevisionPatchSetNum,
      };
      const diffNavStub = sinon.stub(GerritNav, 'navigateToDiff');
      element._handleDiffBaseAgainstLatest();
      assert(diffNavStub.called);
      const args = diffNavStub.getCall(0).args;
      assert.equal(args[2], 10 as RevisionPatchSetNum);
      assert.isNotOk(args[3]);
    });

    test('A fires an error event when not logged in', async () => {
      const changeNavStub = sinon.stub(GerritNav, 'navigateToChange');
      sinon.stub(element, '_getLoggedIn').returns(Promise.resolve(false));
      const loggedInErrorSpy = sinon.spy();
      element.addEventListener('show-auth-required', loggedInErrorSpy);
      MockInteractions.pressAndReleaseKeyOn(element, 65, null, 'a');
      await flush();
      assert.isTrue(
        changeNavStub.notCalled,
        'The `a` keyboard shortcut ' +
          'should only work when the user is logged in.'
      );
      assert.isNull(
        window.sessionStorage.getItem('changeView.showReplyDialog')
      );
      assert.isTrue(loggedInErrorSpy.called);
    });

    test('A navigates to change with logged in', async () => {
      element._changeNum = 42 as NumericChangeId;
      element._patchRange = {
        basePatchNum: 5 as BasePatchSetNum,
        patchNum: 10 as RevisionPatchSetNum,
      };
      element._change = {
        ...createParsedChange(),
        _number: 42 as NumericChangeId,
        revisions: {
          a: createRevision(10),
          b: createRevision(5),
        },
      };
      const changeNavStub = sinon.stub(GerritNav, 'navigateToChange');
      sinon.stub(element, '_getLoggedIn').returns(Promise.resolve(true));
      const loggedInErrorSpy = sinon.spy();
      element.addEventListener('show-auth-required', loggedInErrorSpy);
      MockInteractions.pressAndReleaseKeyOn(element, 65, null, 'a');
      await flush();
      assert.isTrue(element.changeViewState.showReplyDialog);
      assert(
        changeNavStub.lastCall.calledWithExactly(element._change, {
          patchNum: 10 as RevisionPatchSetNum,
          basePatchNum: 5 as BasePatchSetNum,
        }),
        'Should navigate to /c/42/5..10'
      );
      assert.isFalse(loggedInErrorSpy.called);
    });

    test('A navigates to change with old patch number with logged in', async () => {
      element._changeNum = 42 as NumericChangeId;
      element._patchRange = {
        basePatchNum: PARENT,
        patchNum: 1 as RevisionPatchSetNum,
      };
      element._change = {
        ...createParsedChange(),
        _number: 42 as NumericChangeId,
        revisions: {
          a: createRevision(1),
          b: createRevision(2),
        },
      };
      const changeNavStub = sinon.stub(GerritNav, 'navigateToChange');
      sinon.stub(element, '_getLoggedIn').returns(Promise.resolve(true));
      const loggedInErrorSpy = sinon.spy();
      element.addEventListener('show-auth-required', loggedInErrorSpy);
      MockInteractions.pressAndReleaseKeyOn(element, 65, null, 'a');
      await flush();
      assert.isTrue(element.changeViewState.showReplyDialog);
      assert(
        changeNavStub.lastCall.calledWithExactly(element._change, {
          patchNum: 1 as RevisionPatchSetNum,
          basePatchNum: PARENT,
        }),
        'Should navigate to /c/42/1'
      );
      assert.isFalse(loggedInErrorSpy.called);
    });

    test('keyboard shortcuts with patch range', () => {
      element._changeNum = 42 as NumericChangeId;
      element._patchRange = {
        basePatchNum: 5 as BasePatchSetNum,
        patchNum: 10 as RevisionPatchSetNum,
      };
      element._change = {
        ...createParsedChange(),
        _number: 42 as NumericChangeId,
        revisions: {
          a: createRevision(10),
          b: createRevision(5),
        },
      };
      element._files = getFilesFromFileList([
        'chell.go',
        'glados.txt',
        'wheatley.md',
      ]);
      element._path = 'glados.txt';

      const diffNavStub = sinon.stub(GerritNav, 'navigateToDiff');
      const changeNavStub = sinon.stub(GerritNav, 'navigateToChange');

      MockInteractions.pressAndReleaseKeyOn(element, 85, null, 'u');
      assert(
        changeNavStub.lastCall.calledWithExactly(element._change, {
          patchNum: 10 as RevisionPatchSetNum,
          basePatchNum: 5 as BasePatchSetNum,
        }),
        'Should navigate to /c/42/5..10'
      );

      MockInteractions.pressAndReleaseKeyOn(element, 221, null, ']');
      assert.isTrue(element._loading);
      assert(
        diffNavStub.lastCall.calledWithExactly(
          element._change,
          'wheatley.md',
          10 as RevisionPatchSetNum,
          5 as BasePatchSetNum,
          undefined
        ),
        'Should navigate to /c/42/5..10/wheatley.md'
      );
      element._path = 'wheatley.md';

      MockInteractions.pressAndReleaseKeyOn(element, 219, null, '[');
      assert.isTrue(element._loading);
      assert(
        diffNavStub.lastCall.calledWithExactly(
          element._change,
          'glados.txt',
          10 as RevisionPatchSetNum,
          5 as BasePatchSetNum,
          undefined
        ),
        'Should navigate to /c/42/5..10/glados.txt'
      );
      element._path = 'glados.txt';

      MockInteractions.pressAndReleaseKeyOn(element, 219, null, '[');
      assert.isTrue(element._loading);
      assert(
        diffNavStub.lastCall.calledWithExactly(
          element._change,
          'chell.go',
          10 as RevisionPatchSetNum,
          5 as BasePatchSetNum,
          undefined
        ),
        'Should navigate to /c/42/5..10/chell.go'
      );
      element._path = 'chell.go';

      MockInteractions.pressAndReleaseKeyOn(element, 219, null, '[');
      assert.isTrue(element._loading);
      assert(
        changeNavStub.lastCall.calledWithExactly(element._change, {
          patchNum: 10 as RevisionPatchSetNum,
          basePatchNum: 5 as BasePatchSetNum,
        }),
        'Should navigate to /c/42/5..10'
      );

      const downloadOverlayStub = sinon
        .stub(element.$.downloadOverlay, 'open')
        .returns(Promise.resolve());
      MockInteractions.pressAndReleaseKeyOn(element, 68, null, 'd');
      assert.isTrue(downloadOverlayStub.called);
    });

    test('keyboard shortcuts with old patch number', () => {
      element._changeNum = 42 as NumericChangeId;
      element._patchRange = {
        basePatchNum: PARENT,
        patchNum: 1 as RevisionPatchSetNum,
      };
      element._change = {
        ...createParsedChange(),
        _number: 42 as NumericChangeId,
        revisions: {
          a: createRevision(1),
          b: createRevision(2),
        },
      };
      element._files = getFilesFromFileList([
        'chell.go',
        'glados.txt',
        'wheatley.md',
      ]);
      element._path = 'glados.txt';

      const diffNavStub = sinon.stub(GerritNav, 'navigateToDiff');
      const changeNavStub = sinon.stub(GerritNav, 'navigateToChange');

      MockInteractions.pressAndReleaseKeyOn(element, 85, null, 'u');
      assert(
        changeNavStub.lastCall.calledWithExactly(element._change, {
          patchNum: 1 as RevisionPatchSetNum,
          basePatchNum: PARENT,
        }),
        'Should navigate to /c/42/1'
      );

      MockInteractions.pressAndReleaseKeyOn(element, 221, null, ']');
      assert(
        diffNavStub.lastCall.calledWithExactly(
          element._change,
          'wheatley.md',
          1 as RevisionPatchSetNum,
          PARENT,
          undefined
        ),
        'Should navigate to /c/42/1/wheatley.md'
      );
      element._path = 'wheatley.md';

      MockInteractions.pressAndReleaseKeyOn(element, 219, null, '[');
      assert(
        diffNavStub.lastCall.calledWithExactly(
          element._change,
          'glados.txt',
          1 as RevisionPatchSetNum,
          PARENT,
          undefined
        ),
        'Should navigate to /c/42/1/glados.txt'
      );
      element._path = 'glados.txt';

      MockInteractions.pressAndReleaseKeyOn(element, 219, null, '[');
      assert(
        diffNavStub.lastCall.calledWithExactly(
          element._change,
          'chell.go',
          1 as RevisionPatchSetNum,
          PARENT,
          undefined
        ),
        'Should navigate to /c/42/1/chell.go'
      );
      element._path = 'chell.go';

      changeNavStub.reset();
      MockInteractions.pressAndReleaseKeyOn(element, 219, null, '[');
      assert(
        changeNavStub.lastCall.calledWithExactly(element._change, {
          patchNum: 1 as RevisionPatchSetNum,
          basePatchNum: PARENT,
        }),
        'Should navigate to /c/42/1'
      );
      assert.isTrue(changeNavStub.calledOnce);
    });

    test('edit should redirect to edit page', async () => {
      element._loggedIn = true;
      element._path = 't.txt';
      element._patchRange = {
        basePatchNum: PARENT,
        patchNum: 1 as RevisionPatchSetNum,
      };
      element._change = {
        ...createParsedChange(),
        _number: 42 as NumericChangeId,
        project: 'gerrit' as RepoName,
        status: ChangeStatus.NEW,
        revisions: {
          a: createRevision(1),
          b: createRevision(2),
        },
      };
      const redirectStub = sinon.stub(GerritNav, 'navigateToRelativeUrl');
      await flush();
      const editBtn = queryAndAssert(element, '.editButton gr-button');
      assert.isTrue(!!editBtn);
      MockInteractions.tap(editBtn);
      assert.isTrue(redirectStub.called);
      assert.isTrue(
        redirectStub.lastCall.calledWithExactly(
          GerritNav.getEditUrlForDiff(
            element._change,
            element._path,
            element._patchRange.patchNum
          )
        )
      );
    });

    test('edit should redirect to edit page with line number', async () => {
      const lineNumber = 42;
      element._loggedIn = true;
      element._path = 't.txt';
      element._patchRange = {
        basePatchNum: PARENT,
        patchNum: 1 as RevisionPatchSetNum,
      };
      element._change = {
        ...createParsedChange(),
        _number: 42 as NumericChangeId,
        project: 'gerrit' as RepoName,
        status: ChangeStatus.NEW,
        revisions: {
          a: createRevision(1),
          b: createRevision(2),
        },
      };
      sinon
        .stub(element.cursor, 'getAddress')
        .returns({number: lineNumber, leftSide: false});
      const redirectStub = sinon.stub(GerritNav, 'navigateToRelativeUrl');
      await flush();
      const editBtn = queryAndAssert(element, '.editButton gr-button');
      assert.isTrue(!!editBtn);
      MockInteractions.tap(editBtn);
      assert.isTrue(redirectStub.called);
      assert.isTrue(
        redirectStub.lastCall.calledWithExactly(
          GerritNav.getEditUrlForDiff(
            element._change,
            element._path,
            element._patchRange.patchNum,
            lineNumber
          )
        )
      );
    });

    function isEditVisibile({
      loggedIn,
      changeStatus,
    }: {
      loggedIn: boolean;
      changeStatus: ChangeStatus;
    }) {
      return new Promise(resolve => {
        element._loggedIn = loggedIn;
        element._path = 't.txt';
        element._patchRange = {
          basePatchNum: PARENT,
          patchNum: 1 as RevisionPatchSetNum,
        };
        element._change = {
          ...createParsedChange(),
          _number: 42 as NumericChangeId,
          status: changeStatus,
          revisions: {
            a: createRevision(1),
            b: createRevision(2),
          },
        };
        flush(() => {
          const editBtn = query(element, '.editButton gr-button');
          resolve(!!editBtn);
        });
      });
    }

    test('edit visible only when logged and status NEW', async () => {
      for (const changeStatus of Object.keys(ChangeStatus) as ChangeStatus[]) {
        assert.isFalse(
          await isEditVisibile({loggedIn: false, changeStatus}),
          `loggedIn: false, changeStatus: ${changeStatus}`
        );

        if (changeStatus !== ChangeStatus.NEW) {
          assert.isFalse(
            await isEditVisibile({loggedIn: true, changeStatus}),
            `loggedIn: true, changeStatus: ${changeStatus}`
          );
        } else {
          assert.isTrue(
            await isEditVisibile({loggedIn: true, changeStatus}),
            `loggedIn: true, changeStatus: ${changeStatus}`
          );
        }
      }
    });

    test('edit visible when logged and status NEW', async () => {
      assert.isTrue(
        await isEditVisibile({loggedIn: true, changeStatus: ChangeStatus.NEW})
      );
    });

    test('edit hidden when logged and status ABANDONED', async () => {
      assert.isFalse(
        await isEditVisibile({
          loggedIn: true,
          changeStatus: ChangeStatus.ABANDONED,
        })
      );
    });

    test('edit hidden when logged and status MERGED', async () => {
      assert.isFalse(
        await isEditVisibile({
          loggedIn: true,
          changeStatus: ChangeStatus.MERGED,
        })
      );
    });

    suite('diff prefs hidden', () => {
      test('when no prefs or logged out', () => {
        element._prefs = undefined;
        element._loggedIn = false;
        flush();
        assert.isTrue(element.$.diffPrefsContainer.hidden);

        element._loggedIn = true;
        flush();
        assert.isTrue(element.$.diffPrefsContainer.hidden);

        element._loggedIn = false;
        element._prefs = {...createDefaultDiffPrefs(), font_size: 12};
        flush();
        assert.isTrue(element.$.diffPrefsContainer.hidden);

        element._loggedIn = true;
        element._prefs = {...createDefaultDiffPrefs(), font_size: 12};
        flush();
        assert.isFalse(element.$.diffPrefsContainer.hidden);
      });
    });

    test('prefsButton opens gr-diff-preferences', () => {
      const handlePrefsTapSpy = sinon.spy(element, '_handlePrefsTap');
      const overlayOpenStub = sinon.stub(
        element.$.diffPreferencesDialog,
        'open'
      );
      const prefsButton = queryAndAssert(element, '.prefsButton');
      MockInteractions.tap(prefsButton);

      assert.isTrue(handlePrefsTapSpy.called);
      assert.isTrue(overlayOpenStub.called);
    });

    suite('url params', () => {
      setup(() => {
        sinon.stub(element, '_getFiles');
        sinon
          .stub(GerritNav, 'getUrlForDiff')
          .callsFake((c, p, pn, bpn) => `${c._number}-${p}-${pn}-${bpn}`);
        sinon
          .stub(GerritNav, 'getUrlForChange')
          .callsFake(
            (c, ops) => `${c._number}-${ops?.patchNum}-${ops?.basePatchNum}`
          );
      });

      test('_formattedFiles', () => {
        element._changeNum = 42 as NumericChangeId;
        element._patchRange = {
          basePatchNum: PARENT,
          patchNum: 10 as RevisionPatchSetNum,
        };
        element._change = {
          ...createParsedChange(),
          _number: 42 as NumericChangeId,
        };
        element._files = getFilesFromFileList([
          'chell.go',
          'glados.txt',
          'wheatley.md',
          '/COMMIT_MSG',
          '/MERGE_LIST',
        ]);
        element._path = 'glados.txt';
        const expectedFormattedFiles: DropdownItem[] = [
          {
            text: 'chell.go',
            mobileText: 'chell.go',
            value: 'chell.go',
            bottomText: '',
            file: {
              ...createFileInfo(),
              __path: 'chell.go',
            },
          },
          {
            text: 'glados.txt',
            mobileText: 'glados.txt',
            value: 'glados.txt',
            bottomText: '',
            file: {
              ...createFileInfo(),
              __path: 'glados.txt',
            },
          },
          {
            text: 'wheatley.md',
            mobileText: 'wheatley.md',
            value: 'wheatley.md',
            bottomText: '',
            file: {
              ...createFileInfo(),
              __path: 'wheatley.md',
            },
          },
          {
            text: 'Commit message',
            mobileText: 'Commit message',
            value: '/COMMIT_MSG',
            bottomText: '',
            file: {
              ...createFileInfo(),
              __path: '/COMMIT_MSG',
            },
          },
          {
            text: 'Merge list',
            mobileText: 'Merge list',
            value: '/MERGE_LIST',
            bottomText: '',
            file: {
              ...createFileInfo(),
              __path: '/MERGE_LIST',
            },
          },
        ];

        assert.deepEqual(element._formattedFiles, expectedFormattedFiles);
        assert.equal(element._formattedFiles?.[1].value, element._path);
      });

      test('prev/up/next links', () => {
        element._changeNum = 42 as NumericChangeId;
        element._patchRange = {
          basePatchNum: PARENT,
          patchNum: 10 as RevisionPatchSetNum,
        };
        element._change = {
          ...createParsedChange(),
          _number: 42 as NumericChangeId,
          revisions: {
            a: createRevision(10),
          },
        };
        element._files = getFilesFromFileList([
          'chell.go',
          'glados.txt',
          'wheatley.md',
        ]);
        element._path = 'glados.txt';
        flush();
        const linkEls = queryAll(element, '.navLink');
        assert.equal(linkEls.length, 3);
        assert.equal(linkEls[0].getAttribute('href'), '42-chell.go-10-PARENT');
        assert.equal(linkEls[1].getAttribute('href'), '42-undefined-undefined');
        assert.equal(
          linkEls[2].getAttribute('href'),
          '42-wheatley.md-10-PARENT'
        );
        element._path = 'wheatley.md';
        flush();
        assert.equal(
          linkEls[0].getAttribute('href'),
          '42-glados.txt-10-PARENT'
        );
        assert.equal(linkEls[1].getAttribute('href'), '42-undefined-undefined');
        assert.equal(linkEls[2].getAttribute('href'), '42-undefined-undefined');
        element._path = 'chell.go';
        flush();
        assert.equal(linkEls[0].getAttribute('href'), '42-undefined-undefined');
        assert.equal(linkEls[1].getAttribute('href'), '42-undefined-undefined');
        assert.equal(
          linkEls[2].getAttribute('href'),
          '42-glados.txt-10-PARENT'
        );
        element._path = 'not_a_real_file';
        flush();
        assert.equal(
          linkEls[0].getAttribute('href'),
          '42-wheatley.md-10-PARENT'
        );
        assert.equal(linkEls[1].getAttribute('href'), '42-undefined-undefined');
        assert.equal(linkEls[2].getAttribute('href'), '42-chell.go-10-PARENT');
      });

      test('prev/up/next links with patch range', () => {
        element._changeNum = 42 as NumericChangeId;
        element._patchRange = {
          basePatchNum: 5 as BasePatchSetNum,
          patchNum: 10 as RevisionPatchSetNum,
        };
        element._change = {
          ...createParsedChange(),
          _number: 42 as NumericChangeId,
          revisions: {
            a: createRevision(5),
            b: createRevision(10),
          },
        };
        element._files = getFilesFromFileList([
          'chell.go',
          'glados.txt',
          'wheatley.md',
        ]);
        element._path = 'glados.txt';
        flush();
        const linkEls = queryAll(element, '.navLink');
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
        assert.equal(linkEls[0].getAttribute('href'), '42-10-5');
        assert.equal(linkEls[1].getAttribute('href'), '42-10-5');
        assert.equal(linkEls[2].getAttribute('href'), '42-glados.txt-10-5');
      });
    });

    test('_handlePatchChange calls navigateToDiff correctly', () => {
      const navigateStub = sinon.stub(GerritNav, 'navigateToDiff');
      element._change = {
        ...createParsedChange(),
        _number: 321 as NumericChangeId,
        project: 'foo/bar' as RepoName,
      };
      element._path = 'path/to/file.txt';

      element._patchRange = {
        basePatchNum: PARENT,
        patchNum: 3 as RevisionPatchSetNum,
      };

      const detail = {
        basePatchNum: PARENT,
        patchNum: 1 as RevisionPatchSetNum,
      };

      element.$.rangeSelect.dispatchEvent(
        new CustomEvent('patch-range-change', {detail, bubbles: false})
      );

      assert(
        navigateStub.lastCall.calledWithExactly(
          element._change,
          element._path,
          1 as RevisionPatchSetNum,
          PARENT
        )
      );
    });

    test(
      '_prefs.manual_review true means set reviewed is not ' +
        'automatically called',
      async () => {
        const setReviewedFileStatusStub = sinon
          .stub(element.getChangeModel(), 'setReviewedFilesStatus')
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
          change: createParsedChange(),
          diffPath: '/COMMIT_MSG',
          reviewedFiles: [],
          loadingStatus: LoadingStatus.LOADED,
        });

        element.routerModel.updateState({
          changeNum: TEST_NUMERIC_CHANGE_ID,
          view: GerritView.DIFF,
          patchNum: 2 as RevisionPatchSetNum,
        });
        element._patchRange = {
          patchNum: 2 as RevisionPatchSetNum,
          basePatchNum: 1 as BasePatchSetNum,
        };

        await waitUntil(() => setReviewedStatusStub.called);

        assert.isFalse(setReviewedFileStatusStub.called);

        // if prefs are updated then the reviewed status should not be set again
        element.userModel.setDiffPreferences(createDefaultDiffPrefs());

        await flush();
        assert.isFalse(setReviewedFileStatusStub.called);
      }
    );

    test('_prefs.manual_review false means set reviewed is called', async () => {
      const setReviewedFileStatusStub = sinon
        .stub(element.getChangeModel(), 'setReviewedFilesStatus')
        .callsFake(() => Promise.resolve());

      sinon.stub(element.$.diffHost, 'reload');
      sinon.stub(element, '_getLoggedIn').returns(Promise.resolve(true));
      const diffPreferences = {
        ...createDefaultDiffPrefs(),
        manual_review: false,
      };
      element.userModel.setDiffPreferences(diffPreferences);
      element.getChangeModel().setState({
        change: createParsedChange(),
        diffPath: '/COMMIT_MSG',
        reviewedFiles: [],
        loadingStatus: LoadingStatus.LOADED,
      });

      element.routerModel.updateState({
        changeNum: TEST_NUMERIC_CHANGE_ID,
        view: GerritView.DIFF,
        patchNum: 22 as RevisionPatchSetNum,
      });
      element._patchRange = {
        patchNum: 2 as RevisionPatchSetNum,
        basePatchNum: 1 as BasePatchSetNum,
      };

      await waitUntil(() => setReviewedFileStatusStub.called);

      assert.isTrue(setReviewedFileStatusStub.called);
    });

    test('file review status', async () => {
      element.getChangeModel().setState({
        change: createParsedChange(),
        diffPath: '/COMMIT_MSG',
        reviewedFiles: [],
        loadingStatus: LoadingStatus.LOADED,
      });
      sinon.stub(element, '_getLoggedIn').returns(Promise.resolve(true));
      const saveReviewedStub = sinon
        .stub(element.getChangeModel(), 'setReviewedFilesStatus')
        .callsFake(() => Promise.resolve());
      sinon.stub(element.$.diffHost, 'reload');

      element.userModel.setDiffPreferences(createDefaultDiffPrefs());

      element.routerModel.updateState({
        changeNum: TEST_NUMERIC_CHANGE_ID,
        view: GerritView.DIFF,
        patchNum: 2 as RevisionPatchSetNum,
      });

      element._patchRange = {
        patchNum: 2 as RevisionPatchSetNum,
        basePatchNum: 1 as BasePatchSetNum,
      };

      await waitUntil(() => saveReviewedStub.called);

      element.getChangeModel().updateStateFileReviewed('/COMMIT_MSG', true);
      await flush();

      const reviewedStatusCheckBox = queryAndAssert<HTMLInputElement>(
        element,
        'input[type="checkbox"]'
      );

      assert.isTrue(reviewedStatusCheckBox.checked);
      assert.deepEqual(saveReviewedStub.lastCall.args, [
        42,
        2,
        '/COMMIT_MSG',
        true,
      ]);

      MockInteractions.tap(reviewedStatusCheckBox);
      assert.isFalse(reviewedStatusCheckBox.checked);
      assert.deepEqual(saveReviewedStub.lastCall.args, [
        42,
        2,
        '/COMMIT_MSG',
        false,
      ]);

      element.getChangeModel().updateStateFileReviewed('/COMMIT_MSG', false);
      await flush();

      MockInteractions.tap(reviewedStatusCheckBox);
      assert.isTrue(reviewedStatusCheckBox.checked);
      assert.deepEqual(saveReviewedStub.lastCall.args, [
        42,
        2,
        '/COMMIT_MSG',
        true,
      ]);

      const callCount = saveReviewedStub.callCount;

      element.set('params.view', GerritNav.View.CHANGE);
      await flush();

      // saveReviewedState observer observes params, but should not fire when
      // view !== GerritNav.View.DIFF.
      assert.equal(saveReviewedStub.callCount, callCount);
    });

    test('file review status with edit loaded', () => {
      const saveReviewedStub = sinon.stub(
        element.getChangeModel(),
        'setReviewedFilesStatus'
      );

      element._patchRange = {
        basePatchNum: 1 as BasePatchSetNum,
        patchNum: EditPatchSetNum,
      };
      flush();

      assert.isTrue(element._editMode);
      element._setReviewed(true);
      assert.isFalse(saveReviewedStub.called);
    });

    test('hash is determined from params', async () => {
      sinon.stub(element.$.diffHost, 'reload');
      const initLineStub = sinon.stub(element, '_initLineOfInterestAndCursor');

      element._loggedIn = true;
      element.params = {
        view: GerritNav.View.DIFF,
        changeNum: 42 as NumericChangeId,
        patchNum: 2 as RevisionPatchSetNum,
        basePatchNum: 1 as BasePatchSetNum,
        path: '/COMMIT_MSG',
      };

      await flush();
      assert.isTrue(initLineStub.calledOnce);
    });

    test('diff mode selector correctly toggles the diff', () => {
      const select = element.$.modeSelect;
      const diffDisplay = element.$.diffHost;
      element._userPrefs = {
        ...createDefaultPreferences(),
        diff_view: DiffViewMode.SIDE_BY_SIDE,
      };
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
      assert.isTrue(
        userStub.calledWithExactly({
          diff_view: DiffViewMode.UNIFIED,
        })
      );
    });

    test('diff mode selector should be hidden for binary', async () => {
      element._diff = {
        ...createDiff(),
        binary: true,
        content: [],
      };

      await flush();
      const diffModeSelector = queryAndAssert(element, '.diffModeSelector');
      assert.isTrue(diffModeSelector.classList.contains('hide'));
    });

    suite('_commitRange', () => {
      const change: ParsedChangeInfo = {
        ...createParsedChange(),
        _number: 42 as NumericChangeId,
        revisions: {
          'commit-sha-1': {
            ...createRevision(1),
            commit: {
              ...createCommit(),
              parents: [{subject: 's1', commit: 'sha-1-parent' as CommitId}],
            },
          },
          'commit-sha-2': createRevision(2),
          'commit-sha-3': createRevision(3),
          'commit-sha-4': createRevision(4),
          'commit-sha-5': {
            ...createRevision(5),
            commit: {
              ...createCommit(),
              parents: [{subject: 's5', commit: 'sha-5-parent' as CommitId}],
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
          changeNum: 42 as NumericChangeId,
          patchNum: 4 as RevisionPatchSetNum,
          basePatchNum: 2 as BasePatchSetNum,
          path: '/COMMIT_MSG',
        };
        element._change = change;
        await flush();
        assert.deepEqual(element._commitRange, {
          baseCommit: 'commit-sha-2' as CommitId,
          commit: 'commit-sha-4' as CommitId,
        });
      });

      test('uses the parent when there is no base patch num ', async () => {
        element.params = {
          view: GerritNav.View.DIFF,
          changeNum: 42 as NumericChangeId,
          patchNum: 5 as RevisionPatchSetNum,
          path: '/COMMIT_MSG',
        };
        element._change = change;
        await flush();
        assert.deepEqual(element._commitRange, {
          commit: 'commit-sha-5' as CommitId,
          baseCommit: 'sha-5-parent' as CommitId,
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
      assert.equal(element.cursor.side, Side.RIGHT);

      // Base hash: specifies lineNum and side.
      element._focusLineNum = 345;
      element._initCursor(true);
      assert.equal(element.cursor.initialLineNumber, 345);
      assert.equal(element.cursor.side, Side.LEFT);

      // Specifies right side:
      element._focusLineNum = 123;
      element._initCursor(false);
      assert.equal(element.cursor.initialLineNumber, 123);
      assert.equal(element.cursor.side, Side.RIGHT);
    });

    test('_getLineOfInterest', () => {
      assert.isUndefined(element._getLineOfInterest(false));

      element._focusLineNum = 12;
      let result = element._getLineOfInterest(false);
      assert.isOk(result);
      assert.equal(result!.lineNum, 12);
      assert.equal(result!.side, Side.RIGHT);

      result = element._getLineOfInterest(true);
      assert.isOk(result);
      assert.equal(result!.lineNum, 12);
      assert.equal(result!.side, Side.LEFT);
    });

    test('_onLineSelected', () => {
      const getUrlStub = sinon.stub(GerritNav, 'getUrlForDiffById');
      const replaceStateStub = sinon.stub(history, 'replaceState');
      sinon
        .stub(element.cursor, 'getAddress')
        .returns({number: 123, leftSide: false});

      element._changeNum = 321 as NumericChangeId;
      element._change = {
        ...createParsedChange(),
        _number: 321 as NumericChangeId,
        project: 'foo/bar' as RepoName,
      };
      element._patchRange = {
        basePatchNum: 3 as BasePatchSetNum,
        patchNum: 5 as RevisionPatchSetNum,
      };
      const e = {} as CustomEvent;
      const detail = {number: 123, side: Side.RIGHT};

      element._onLineSelected(e, detail);

      assert.isTrue(replaceStateStub.called);
      assert.isTrue(getUrlStub.called);
      assert.isFalse(getUrlStub.lastCall.args[6]);
    });

    test('line selected on left side', () => {
      const getUrlStub = sinon.stub(GerritNav, 'getUrlForDiffById');
      const replaceStateStub = sinon.stub(history, 'replaceState');
      sinon
        .stub(element.cursor, 'getAddress')
        .returns({number: 123, leftSide: true});

      element._changeNum = 321 as NumericChangeId;
      element._change = {
        ...createParsedChange(),
        _number: 321 as NumericChangeId,
        project: 'foo/bar' as RepoName,
      };
      element._patchRange = {
        basePatchNum: 3 as BasePatchSetNum,
        patchNum: 5 as RevisionPatchSetNum,
      };
      const e = {} as CustomEvent;
      const detail = {number: 123, side: Side.LEFT};

      element._onLineSelected(e, detail);

      assert.isTrue(replaceStateStub.called);
      assert.isTrue(getUrlStub.called);
      assert.isTrue(getUrlStub.lastCall.args[6]);
    });

    test('_handleToggleDiffMode', () => {
      const userStub = stubUsers('updatePreferences');
      element._userPrefs = {
        ...createDefaultPreferences(),
        diff_view: DiffViewMode.SIDE_BY_SIDE,
      };

      element._handleToggleDiffMode();
      assert.deepEqual(userStub.lastCall.args[0], {
        diff_view: DiffViewMode.UNIFIED,
      });

      element._userPrefs = {
        ...createDefaultPreferences(),
        diff_view: DiffViewMode.UNIFIED,
      };

      element._handleToggleDiffMode();
      assert.deepEqual(userStub.lastCall.args[0], {
        diff_view: DiffViewMode.SIDE_BY_SIDE,
      });
    });

    suite('_initPatchRange', () => {
      setup(async () => {
        stubRestApi('getDiff').returns(Promise.resolve(createDiff()));
        element.params = {
          view: GerritView.DIFF,
          changeNum: 42 as NumericChangeId,
          patchNum: 3 as RevisionPatchSetNum,
          path: 'abcd',
        };
        await flush();
      });
      test('empty', () => {
        sinon.stub(element, '_getPaths').returns({});
        element._initPatchRange();
        assert.equal(Object.keys(element._commentMap ?? {}).length, 0);
      });

      test('has paths', () => {
        sinon.stub(element, '_getFiles');
        sinon.stub(element, '_getPaths').returns({
          'path/to/file/one.cpp': true,
          'path-to/file/two.py': true,
        });
        element._changeNum = 42 as NumericChangeId;
        element._patchRange = {
          basePatchNum: 3 as BasePatchSetNum,
          patchNum: 5 as RevisionPatchSetNum,
        };
        element._initPatchRange();
        assert.deepEqual(Object.keys(element._commentMap ?? {}), [
          'path/to/file/one.cpp',
          'path-to/file/two.py',
        ]);
      });
    });

    suite('_computeCommentSkips', () => {
      test('empty file list', () => {
        const commentMap = {
          'path/one.jpg': true,
          'path/three.wav': true,
        };
        const path = 'path/two.m4v';
        const result = element._computeCommentSkips(commentMap, [], path);
        assert.isOk(result);
        assert.isNotOk(result!.previous);
        assert.isNotOk(result!.next);
      });

      test('finds skips', () => {
        const fileList = ['path/one.jpg', 'path/two.m4v', 'path/three.wav'];
        let path = fileList[1];
        const commentMap: CommentMap = {};
        commentMap[fileList[0]] = true;
        commentMap[fileList[1]] = false;
        commentMap[fileList[2]] = true;

        let result = element._computeCommentSkips(commentMap, fileList, path);
        assert.isOk(result);
        assert.equal(result!.previous, fileList[0]);
        assert.equal(result!.next, fileList[2]);

        commentMap[fileList[1]] = true;

        result = element._computeCommentSkips(commentMap, fileList, path);
        assert.isOk(result);
        assert.equal(result!.previous, fileList[0]);
        assert.equal(result!.next, fileList[2]);

        path = fileList[0];

        result = element._computeCommentSkips(commentMap, fileList, path);
        assert.isOk(result);
        assert.isNull(result!.previous);
        assert.equal(result!.next, fileList[1]);

        path = fileList[2];

        result = element._computeCommentSkips(commentMap, fileList, path);
        assert.isOk(result);
        assert.equal(result!.previous, fileList[1]);
        assert.isNull(result!.next);
      });

      suite('skip next/previous', () => {
        let navToChangeStub: SinonStub;
        let navToDiffStub: SinonStub;

        setup(() => {
          navToChangeStub = sinon.stub(element, '_navToChangeView');
          navToDiffStub = sinon.stub(GerritNav, 'navigateToDiff');
          element._files = getFilesFromFileList([
            'path/one.jpg',
            'path/two.m4v',
            'path/three.wav',
          ]);
          element._patchRange = {
            patchNum: 2 as RevisionPatchSetNum,
            basePatchNum: 1 as BasePatchSetNum,
          };
        });

        suite('_moveToPreviousFileWithComment', () => {
          test('no skips', () => {
            element._moveToPreviousFileWithComment();
            assert.isFalse(navToChangeStub.called);
            assert.isFalse(navToDiffStub.called);
          });

          test('no previous', () => {
            const commentMap: CommentMap = {};
            commentMap[element._fileList![0]!] = false;
            commentMap[element._fileList![1]!] = false;
            commentMap[element._fileList![2]!] = true;
            element._commentMap = commentMap;
            element._path = element._fileList![1]!;

            element._moveToPreviousFileWithComment();
            assert.isTrue(navToChangeStub.calledOnce);
            assert.isFalse(navToDiffStub.called);
          });

          test('w/ previous', () => {
            const commentMap: CommentMap = {};
            commentMap[element._fileList![0]!] = true;
            commentMap[element._fileList![1]!] = false;
            commentMap[element._fileList![2]!] = true;
            element._commentMap = commentMap;
            element._path = element._fileList![1]!;

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
            const commentMap: CommentMap = {};
            commentMap[element._fileList![0]!] = true;
            commentMap[element._fileList![1]!] = false;
            commentMap[element._fileList![2]!] = false;
            element._commentMap = commentMap;
            element._path = element._fileList![1];

            element._moveToNextFileWithComment();
            assert.isTrue(navToChangeStub.calledOnce);
            assert.isFalse(navToDiffStub.called);
          });

          test('w/ previous', () => {
            const commentMap: CommentMap = {};
            commentMap[element._fileList![0]!] = true;
            commentMap[element._fileList![1]!] = false;
            commentMap[element._fileList![2]!] = true;
            element._commentMap = commentMap;
            element._path = element._fileList![1];

            element._moveToNextFileWithComment();
            assert.isFalse(navToChangeStub.called);
            assert.isTrue(navToDiffStub.calledOnce);
          });
        });
      });
    });

    test('_computeEditMode', () => {
      const callCompute = (range: PatchRange) =>
        element._computeEditMode({base: range, path: '', value: range});
      assert.isFalse(
        callCompute({
          basePatchNum: PARENT,
          patchNum: 1 as RevisionPatchSetNum,
        })
      );
      assert.isTrue(
        callCompute({
          basePatchNum: 1 as BasePatchSetNum,
          patchNum: EditPatchSetNum,
        })
      );
    });

    test('_computeFileNum', () => {
      assert.equal(
        element._computeFileNum('/foo', [
          {text: '/foo', value: '/foo'},
          {text: '/bar', value: '/bar'},
        ]),
        1
      );
      assert.equal(
        element._computeFileNum('/bar', [
          {text: '/foo', value: '/foo'},
          {text: '/bar', value: '/bar'},
        ]),
        2
      );
    });

    test('_computeFileNumClass', () => {
      assert.equal(element._computeFileNumClass(0, []), '');
      assert.equal(
        element._computeFileNumClass(1, [
          {text: '/foo', value: '/foo'},
          {text: '/bar', value: '/bar'},
        ]),
        'show'
      );
    });

    test('f open file dropdown', () => {
      assert.isFalse(element.$.dropdown.$.dropdown.opened);
      MockInteractions.pressAndReleaseKeyOn(element, 70, null, 'f');
      flush();
      assert.isTrue(element.$.dropdown.$.dropdown.opened);
    });

    suite('blame', () => {
      test('toggle blame with button', () => {
        const toggleBlame = sinon
          .stub(element.$.diffHost, 'loadBlame')
          .callsFake(() => Promise.resolve([]));
        MockInteractions.tap(element.$.toggleBlame);
        assert.isTrue(toggleBlame.calledOnce);
      });
      test('toggle blame with shortcut', () => {
        const toggleBlame = sinon
          .stub(element.$.diffHost, 'loadBlame')
          .callsFake(() => Promise.resolve([]));
        MockInteractions.pressAndReleaseKeyOn(element, 66, null, 'b');
        assert.isTrue(toggleBlame.calledOnce);
      });
    });

    suite('editMode behavior', () => {
      setup(() => {
        element._loggedIn = true;
      });

      test('reviewed checkbox', () => {
        sinon.stub(element, '_handlePatchChange');
        element._patchRange = createPatchRange();
        // Reviewed checkbox should be shown.
        assert.isTrue(isVisible(element.$.reviewed));
        element.set('_patchRange.patchNum', EditPatchSetNum);
        flush();

        assert.isFalse(isVisible(element.$.reviewed));
      });
    });

    suite('switching files', () => {
      let dispatchEventStub: SinonStub;
      let navToFileStub: SinonStub;
      let moveToPreviousChunkStub: SinonStub;
      let moveToNextChunkStub: SinonStub;
      let isAtStartStub: SinonStub;
      let isAtEndStub: SinonStub;
      let nowStub: SinonStub;

      setup(() => {
        dispatchEventStub = sinon.stub(element, 'dispatchEvent').callThrough();
        navToFileStub = sinon.stub(element, '_navToFile');
        moveToPreviousChunkStub = sinon.stub(
          element.cursor,
          'moveToPreviousChunk'
        );
        moveToNextChunkStub = sinon.stub(element.cursor, 'moveToNextChunk');
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
      assert.deepEqual(navStub.lastCall.args, ['file1', ['file1', 'file3'], 1]);
    });

    test('File change should trigger navigateToDiff once', async () => {
      element._files = getFilesFromFileList(['file1', 'file2', 'file3']);
      sinon.stub(element, '_initLineOfInterestAndCursor');
      const navigateToDiffStub = sinon.stub(GerritNav, 'navigateToDiff');

      // Load file1
      element.params = {
        view: GerritNav.View.DIFF,
        patchNum: 1 as RevisionPatchSetNum,
        changeNum: 101 as NumericChangeId,
        project: 'test-project' as RepoName,
        path: 'file1',
      };
      element._patchRange = {
        patchNum: 1 as RevisionPatchSetNum,
        basePatchNum: PARENT,
      };
      element._change = {
        ...createParsedChange(),
        revisions: createRevisions(1),
      };
      await flush();
      assert.isTrue(navigateToDiffStub.notCalled);

      // Switch to file2
      element._handleFileChange(
        new CustomEvent('value-change', {detail: {value: 'file2'}})
      );
      assert.isTrue(navigateToDiffStub.calledOnce);

      // This is to mock the param change triggered by above navigate
      element.params = {
        view: GerritNav.View.DIFF,
        patchNum: 1 as RevisionPatchSetNum,
        changeNum: 101 as NumericChangeId,
        project: 'test-project' as RepoName,
        path: 'file2',
      };
      element._patchRange = {
        patchNum: 1 as RevisionPatchSetNum,
        basePatchNum: PARENT,
      };

      // No extra call
      assert.isTrue(navigateToDiffStub.calledOnce);
    });

    test('_computeDownloadDropdownLinks', () => {
      const downloadLinks = [
        {
          url: '/changes/test~12/revisions/1/patch?zip&path=index.php',
          name: 'Patch',
        },
        {
          url: '/changes/test~12/revisions/1/files/index.php/download?parent=1',
          name: 'Left Content',
        },
        {
          url: '/changes/test~12/revisions/1/files/index.php/download',
          name: 'Right Content',
        },
      ];

      const base = {
        patchNum: 1 as RevisionPatchSetNum,
        basePatchNum: PARENT,
      };

      assert.deepEqual(
        element._computeDownloadDropdownLinks(
          'test' as RepoName,
          12 as NumericChangeId,
          base,
          'index.php',
          createDiff()
        ),
        downloadLinks
      );
    });

    test('_computeDownloadDropdownLinks diff returns renamed', () => {
      const downloadLinks = [
        {
          url: '/changes/test~12/revisions/3/patch?zip&path=index.php',
          name: 'Patch',
        },
        {
          url: '/changes/test~12/revisions/2/files/index2.php/download',
          name: 'Left Content',
        },
        {
          url: '/changes/test~12/revisions/3/files/index.php/download',
          name: 'Right Content',
        },
      ];

      const diff = createDiff();
      diff.change_type = 'RENAMED';
      diff.meta_a!.name = 'index2.php';

      const base = {
        patchNum: 3 as RevisionPatchSetNum,
        basePatchNum: 2 as BasePatchSetNum,
      };

      assert.deepEqual(
        element._computeDownloadDropdownLinks(
          'test' as RepoName,
          12 as NumericChangeId,
          base,
          'index.php',
          diff
        ),
        downloadLinks
      );
    });

    test('_computeDownloadFileLink', () => {
      const base = {
        patchNum: 1 as RevisionPatchSetNum,
        basePatchNum: PARENT,
      };

      assert.equal(
        element._computeDownloadFileLink(
          'test' as RepoName,
          12 as NumericChangeId,
          base,
          'index.php',
          true
        ),
        '/changes/test~12/revisions/1/files/index.php/download?parent=1'
      );

      assert.equal(
        element._computeDownloadFileLink(
          'test' as RepoName,
          12 as NumericChangeId,
          base,
          'index.php',
          false
        ),
        '/changes/test~12/revisions/1/files/index.php/download'
      );
    });

    test('_computeDownloadPatchLink', () => {
      assert.equal(
        element._computeDownloadPatchLink(
          'test' as RepoName,
          12 as NumericChangeId,
          {basePatchNum: PARENT, patchNum: 1 as RevisionPatchSetNum},
          'index.php'
        ),
        '/changes/test~12/revisions/1/patch?zip&path=index.php'
      );
    });
  });

  suite('unmodified files with comments', () => {
    let element: GrDiffView;
    setup(() => {
      const changedFiles = {
        'file1.txt': createFileInfo(),
        'a/b/test.c': createFileInfo(),
      };
      stubRestApi('getConfig').returns(Promise.resolve(createServerInfo()));
      stubRestApi('getProjectConfig').returns(Promise.resolve(createConfig()));
      stubRestApi('getChangeFiles').returns(Promise.resolve(changedFiles));
      stubRestApi('saveFileReviewed').returns(Promise.resolve(new Response()));
      stubRestApi('getDiffComments').returns(Promise.resolve({}));
      stubRestApi('getDiffRobotComments').returns(Promise.resolve({}));
      stubRestApi('getDiffDrafts').returns(Promise.resolve({}));
      stubRestApi('getReviewedFiles').returns(Promise.resolve([]));
      element = basicFixture.instantiate();
      element._changeNum = 42 as NumericChangeId;
    });

    test('_getFiles add files with comments without changes', () => {
      const patchChangeRecord = {
        base: {
          basePatchNum: 5 as BasePatchSetNum,
          patchNum: 10 as RevisionPatchSetNum,
        },
        value: {
          basePatchNum: 5 as BasePatchSetNum,
          patchNum: 10 as RevisionPatchSetNum,
        },
        path: '',
      };
      const changeComments = {
        getPaths: sinon.stub().returns({
          'file2.txt': {},
          'file1.txt': {},
        }),
      } as unknown as ChangeComments;
      return element
        ._getFiles(23 as NumericChangeId, patchChangeRecord, changeComments)
        .then(() => {
          assert.deepEqual(element._files, {
            sortedFileList: ['a/b/test.c', 'file1.txt', 'file2.txt'],
            changeFilesByPath: {
              'file1.txt': createFileInfo(),
              'file2.txt': {status: 'U'} as FileInfo,
              'a/b/test.c': createFileInfo(),
            },
          });
        });
    });
  });
});
