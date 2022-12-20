/**
 * @license
 * Copyright 2015 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-diff-view';
import {navigationToken} from '../../core/gr-navigation/gr-navigation';
import {
  ChangeStatus,
  DiffViewMode,
  createDefaultDiffPrefs,
  createDefaultPreferences,
} from '../../../constants/constants';
import {
  isVisible,
  pressKey,
  query,
  queryAll,
  queryAndAssert,
  stubReporting,
  stubRestApi,
  waitEventLoop,
  waitUntil,
} from '../../../test/test-utils';
import {ChangeComments} from '../gr-comment-api/gr-comment-api';
import {
  createRevisions,
  createComment as createCommentGeneric,
  createDiff,
  createPatchRange,
  createServerInfo,
  createConfig,
  createParsedChange,
  createRevision,
  createCommit,
  createFileInfo,
  createDiffViewState,
} from '../../../test/test-data-generators';
import {
  BasePatchSetNum,
  CommentInfo,
  CommitId,
  EDIT,
  NumericChangeId,
  PARENT,
  PatchRange,
  PatchSetNum,
  PatchSetNumber,
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
import {
  changeModelToken,
  ChangeModel,
  LoadingStatus,
} from '../../../models/change/change-model';
import {CommentMap} from '../../../utils/comment-util';
import {ParsedChangeInfo} from '../../../types/types';
import {assertIsDefined} from '../../../utils/common-util';
import {GrDiffModeSelector} from '../../../embed/diff/gr-diff-mode-selector/gr-diff-mode-selector';
import {fixture, html, assert} from '@open-wc/testing';
import {EventType} from '../../../types/events';
import {Key} from '../../../utils/dom-util';
import {GrButton} from '../../shared/gr-button/gr-button';
import {testResolver} from '../../../test/common-test-setup';
import {UserModel, userModelToken} from '../../../models/user/user-model';
import {
  commentsModelToken,
  CommentsModel,
} from '../../../models/comments/comments-model';
import {
  BrowserModel,
  browserModelToken,
} from '../../../models/browser/browser-model';
import {changeViewModelToken} from '../../../models/views/change';
import {FileNameToNormalizedFileInfoMap} from '../../../models/change/files-model';

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
    let getDiffRestApiStub: SinonStub;
    let setUrlStub: SinonStub;
    let changeModel: ChangeModel;
    let commentsModel: CommentsModel;
    let browserModel: BrowserModel;
    let userModel: UserModel;

    function getFilesFromFileList(fileList: string[]): Files {
      const changeFilesByPath = fileList.reduce((files, path) => {
        files[path] = createFileInfo(path);
        return files;
      }, {} as FileNameToNormalizedFileInfoMap);
      return {
        sortedFileList: fileList,
        changeFilesByPath,
      };
    }

    setup(async () => {
      setUrlStub = sinon.stub(testResolver(navigationToken), 'setUrl');
      stubRestApi('getConfig').returns(Promise.resolve(createServerInfo()));
      stubRestApi('getLoggedIn').returns(Promise.resolve(false));
      stubRestApi('getProjectConfig').returns(Promise.resolve(createConfig()));
      stubRestApi('getChangeFiles').returns(
        Promise.resolve({
          'chell.go': createFileInfo(),
          'glados.txt': createFileInfo(),
          'wheatley.md': createFileInfo(),
        })
      );
      stubRestApi('saveFileReviewed').returns(Promise.resolve(new Response()));
      diffCommentsStub = stubRestApi('getDiffComments');
      diffCommentsStub.returns(Promise.resolve({}));
      stubRestApi('getDiffRobotComments').returns(Promise.resolve({}));
      stubRestApi('getDiffDrafts').returns(Promise.resolve({}));
      stubRestApi('getPortedComments').returns(Promise.resolve({}));

      element = await fixture(html`<gr-diff-view></gr-diff-view>`);
      const viewModel = testResolver(changeViewModelToken);
      viewModel.setState(createDiffViewState());
      element.changeNum = 42 as NumericChangeId;
      element.path = 'some/path.txt';
      element.change = createParsedChange();
      element.diff = {...createDiff(), content: []};
      getDiffRestApiStub = stubRestApi('getDiff');
      // Delayed in case a test updates element.diff.
      getDiffRestApiStub.callsFake(() => Promise.resolve(element.diff));
      element.patchRange = createPatchRange();
      element.changeComments = new ChangeComments({
        '/COMMIT_MSG': [
          createComment('c1', 10, 2, '/COMMIT_MSG'),
          createComment('c3', 10, PARENT, '/COMMIT_MSG'),
        ],
      });
      await element.updateComplete;
      commentsModel = testResolver(commentsModelToken);
      changeModel = testResolver(changeModelToken);
      browserModel = testResolver(browserModelToken);
      userModel = testResolver(userModelToken);

      commentsModel.setState({
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

    test('viewState change triggers diffViewDisplayed()', () => {
      const diffViewDisplayedStub = stubReporting('diffViewDisplayed');
      assertIsDefined(element.diffHost);
      sinon.stub(element.diffHost, 'reload').returns(Promise.resolve());
      sinon.stub(element, 'initPatchRange');
      const viewStateChangedSpy = sinon.spy(element, 'viewStateChanged');
      element.viewState = {
        ...createDiffViewState(),
        patchNum: 2 as RevisionPatchSetNum,
        basePatchNum: 1 as BasePatchSetNum,
        diffView: {path: '/COMMIT_MSG'},
      };
      element.path = '/COMMIT_MSG';
      element.patchRange = createPatchRange();
      return viewStateChangedSpy.returnValues[0]?.then(() => {
        assert.isTrue(diffViewDisplayedStub.calledOnce);
      });
    });

    test('viewState change causes blame to load if it was set to true', () => {
      // Blame loads for subsequent files if it was loaded for one file
      element.isBlameLoaded = true;
      stubReporting('diffViewDisplayed');
      const loadBlameStub = sinon.stub(element, 'loadBlame');
      assertIsDefined(element.diffHost);
      sinon.stub(element.diffHost, 'reload').returns(Promise.resolve());
      const viewStateChangedSpy = sinon.spy(element, 'viewStateChanged');
      sinon.stub(element, 'initPatchRange');
      element.viewState = {
        ...createDiffViewState(),
        patchNum: 2 as RevisionPatchSetNum,
        basePatchNum: 1 as BasePatchSetNum,
        diffView: {path: '/COMMIT_MSG'},
      };
      element.path = '/COMMIT_MSG';
      element.patchRange = createPatchRange();
      return viewStateChangedSpy.returnValues[0]!.then(() => {
        assert.isTrue(element.isBlameLoaded);
        assert.isTrue(loadBlameStub.calledOnce);
      });
    });

    test('isFileUnchanged', () => {
      let diff: DiffInfo = {
        ...createDiff(),
        content: [
          {a: ['abcd'], ab: ['ef']},
          {b: ['ancd'], a: ['xx']},
        ],
      };
      assert.equal(element.isFileUnchanged(diff), false);
      diff = {
        ...createDiff(),
        content: [{ab: ['abcd']}, {ab: ['ancd']}],
      };
      assert.equal(element.isFileUnchanged(diff), true);
      diff = {
        ...createDiff(),
        content: [
          {a: ['abcd'], ab: ['ef'], common: true},
          {b: ['ancd'], ab: ['xx']},
        ],
      };
      assert.equal(element.isFileUnchanged(diff), false);
      diff = {
        ...createDiff(),
        content: [
          {a: ['abcd'], ab: ['ef'], common: true},
          {b: ['ancd'], ab: ['xx'], common: true},
        ],
      };
      assert.equal(element.isFileUnchanged(diff), true);
    });

    test('diff toast to go to latest is shown and not base', async () => {
      commentsModel.setState({
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
      sinon.stub(element, 'loadBlame');
      assertIsDefined(element.diffHost);
      sinon.stub(element.diffHost, 'reload').returns(Promise.resolve());
      const viewStateChangedSpy = sinon.spy(element, 'viewStateChanged');
      element.change = undefined;
      changeModel.setState({
        change: {
          ...createParsedChange(),
          revisions: createRevisions(11),
        },
        loadingStatus: LoadingStatus.LOADED,
      });
      element.patchRange = {
        patchNum: 2 as RevisionPatchSetNum,
        basePatchNum: 1 as BasePatchSetNum,
      };
      sinon.stub(element, 'isFileUnchanged').returns(false);
      const toastStub = sinon.stub(element, 'displayDiffBaseAgainstLeftToast');
      element.viewState = {
        ...createDiffViewState(),
        repo: 'p' as RepoName,
        commentId: 'c1' as UrlEncodedCommentId,
        diffView: {commentLink: true},
      };
      await viewStateChangedSpy.returnValues[0];
      assert.isTrue(toastStub.called);
    });

    test('toggle left diff with a hotkey', () => {
      assertIsDefined(element.diffHost);
      const toggleLeftDiffStub = sinon.stub(element.diffHost, 'toggleLeftDiff');
      pressKey(element, 'A');
      assert.isTrue(toggleLeftDiffStub.calledOnce);
    });

    test('renders', async () => {
      clock = sinon.useFakeTimers();
      element.changeNum = 42 as NumericChangeId;
      browserModel.setScreenWidth(0);
      element.patchRange = {
        basePatchNum: PARENT,
        patchNum: 10 as RevisionPatchSetNum,
      };
      element.change = {
        ...createParsedChange(),
        _number: 42 as NumericChangeId,
        revisions: {
          a: createRevision(10),
        },
      };
      element.files = getFilesFromFileList([
        'chell.go',
        'glados.txt',
        'wheatley.md',
      ]);
      element.path = 'glados.txt';
      element.loggedIn = true;
      await element.updateComplete;
      assert.shadowDom.equal(
        element,
        /* HTML */ `
          <div class="stickyHeader">
            <h1 class="assistive-tech-only">Diff of glados.txt</h1>
            <header>
              <div>
                <a href="/c/test-project/+/42"> 42 </a>
                <span class="changeNumberColon"> : </span>
                <span class="headerSubject"> Test subject </span>
                <input
                  aria-label="file reviewed"
                  class="hideOnEdit reviewed"
                  id="reviewed"
                  title="Toggle reviewed status of file"
                  type="checkbox"
                />
                <div class="jumpToFileContainer">
                  <gr-dropdown-list id="dropdown" show-copy-for-trigger-text="">
                  </gr-dropdown-list>
                </div>
              </div>
              <div class="desktop navLinks">
                <span class="fileNum show">
                  File 2 of 3
                  <span class="separator"> </span>
                </span>
                <a
                  class="navLink"
                  href="/c/test-project/+/42/10/chell.go"
                  title="Go to previous file (shortcut: [)"
                >
                  Prev
                </a>
                <span class="separator"> </span>
                <a
                  class="navLink"
                  href="/c/test-project/+/42"
                  title="Up to change (shortcut: u)"
                >
                  Up
                </a>
                <span class="separator"> </span>
                <a
                  class="navLink"
                  href="/c/test-project/+/42/10/wheatley.md"
                  title="Go to next file (shortcut: ])"
                >
                  Next
                </a>
              </div>
            </header>
            <div class="subHeader">
              <div class="patchRangeLeft">
                <gr-patch-range-select id="rangeSelect">
                </gr-patch-range-select>
                <span class="desktop download">
                  <span class="separator"> </span>
                  <gr-dropdown down-arrow="" horizontal-align="left" link="">
                    <span class="downloadTitle"> Download </span>
                  </gr-dropdown>
                </span>
              </div>
              <div class="rightControls">
                <span class="blameLoader show">
                  <gr-button
                    aria-disabled="false"
                    id="toggleBlame"
                    link=""
                    role="button"
                    tabindex="0"
                    title="Toggle blame (shortcut: b)"
                  >
                    Show blame
                  </gr-button>
                </span>
                <span class="separator"> </span>
                <span class="editButton">
                  <gr-button
                    aria-disabled="false"
                    link=""
                    role="button"
                    tabindex="0"
                    title="Edit current file"
                  >
                    edit
                  </gr-button>
                </span>
                <span class="separator"> </span>
                <div class="diffModeSelector">
                  <span> Diff view: </span>
                  <gr-diff-mode-selector id="modeSelect" show-tooltip-below="">
                  </gr-diff-mode-selector>
                </div>
                <span id="diffPrefsContainer">
                  <span class="desktop preferences">
                    <gr-tooltip-content
                      has-tooltip=""
                      position-below=""
                      title="Diff preferences"
                    >
                      <gr-button
                        aria-disabled="false"
                        class="prefsButton"
                        link=""
                        role="button"
                        tabindex="0"
                      >
                        <gr-icon icon="settings" filled></gr-icon>
                      </gr-button>
                    </gr-tooltip-content>
                  </span>
                </span>
                <gr-endpoint-decorator name="annotation-toggler">
                  <span hidden="" id="annotation-span">
                    <label for="annotation-checkbox" id="annotation-label">
                    </label>
                    <iron-input>
                      <input
                        disabled=""
                        id="annotation-checkbox"
                        is="iron-input"
                        type="checkbox"
                        value=""
                      />
                    </iron-input>
                  </span>
                </gr-endpoint-decorator>
              </div>
            </div>
            <div class="fileNav mobile">
              <a class="mobileNavLink" href="/c/test-project/+/42/10/chell.go">
                <
              </a>
              <div class="fullFileName mobile">glados.txt</div>
              <a
                class="mobileNavLink"
                href="/c/test-project/+/42/10/wheatley.md"
              >
                >
              </a>
            </div>
          </div>
          <div class="loading">Loading...</div>
          <h2 class="assistive-tech-only">Diff view</h2>
          <gr-diff-host hidden="" id="diffHost"> </gr-diff-host>
          <gr-apply-fix-dialog id="applyFixDialog"> </gr-apply-fix-dialog>
          <gr-diff-preferences-dialog id="diffPreferencesDialog">
          </gr-diff-preferences-dialog>
          <dialog id="downloadModal" tabindex="-1">
            <gr-download-dialog id="downloadDialog" role="dialog">
            </gr-download-dialog>
          </dialog>
        `
      );
    });

    test('keyboard shortcuts', async () => {
      clock = sinon.useFakeTimers();
      element.changeNum = 42 as NumericChangeId;
      browserModel.setScreenWidth(0);
      element.patchRange = {
        basePatchNum: PARENT,
        patchNum: 10 as RevisionPatchSetNum,
      };
      element.change = {
        ...createParsedChange(),
        _number: 42 as NumericChangeId,
        revisions: {
          a: createRevision(10),
        },
      };
      element.files = getFilesFromFileList([
        'chell.go',
        'glados.txt',
        'wheatley.md',
      ]);
      element.path = 'glados.txt';
      element.loggedIn = true;
      await element.updateComplete;
      setUrlStub.reset();

      pressKey(element, 'u');
      assert.equal(setUrlStub.callCount, 1);
      assert.equal(setUrlStub.lastCall.firstArg, '/c/test-project/+/42');
      await element.updateComplete;

      pressKey(element, ']');
      assert.equal(setUrlStub.callCount, 2);
      assert.equal(
        setUrlStub.lastCall.firstArg,
        '/c/test-project/+/42/10/wheatley.md'
      );
      element.path = 'wheatley.md';
      await element.updateComplete;

      assert.isTrue(element.loading);

      pressKey(element, '[');
      assert.equal(setUrlStub.callCount, 3);
      assert.equal(
        setUrlStub.lastCall.firstArg,
        '/c/test-project/+/42/10/glados.txt'
      );
      element.path = 'glados.txt';
      await element.updateComplete;

      assert.isTrue(element.loading);

      pressKey(element, '[');
      assert.equal(setUrlStub.callCount, 4);
      assert.equal(
        setUrlStub.lastCall.firstArg,
        '/c/test-project/+/42/10/chell.go'
      );
      element.path = 'chell.go';
      await element.updateComplete;

      assert.isTrue(element.loading);

      pressKey(element, '[');
      assert.equal(setUrlStub.callCount, 5);
      assert.equal(setUrlStub.lastCall.firstArg, '/c/test-project/+/42');
      await element.updateComplete;
      assert.isTrue(element.loading);

      assertIsDefined(element.diffPreferencesDialog);
      const showPrefsStub = sinon
        .stub(element.diffPreferencesDialog, 'open')
        .callsFake(() => Promise.resolve());

      pressKey(element, ',');
      await element.updateComplete;
      assert(showPrefsStub.calledOnce);

      assertIsDefined(element.cursor);
      let scrollStub = sinon.stub(element.cursor, 'moveToNextChunk');
      pressKey(element, 'n');
      await element.updateComplete;
      assert(scrollStub.calledOnce);

      scrollStub = sinon.stub(element.cursor, 'moveToPreviousChunk');
      pressKey(element, 'p');
      await element.updateComplete;
      assert(scrollStub.calledOnce);

      scrollStub = sinon.stub(element.cursor, 'moveToNextCommentThread');
      pressKey(element, 'N');
      await element.updateComplete;
      assert(scrollStub.calledOnce);

      scrollStub = sinon.stub(element.cursor, 'moveToPreviousCommentThread');
      pressKey(element, 'P');
      await element.updateComplete;
      assert(scrollStub.calledOnce);

      assertIsDefined(element.diffHost);
      assertIsDefined(element.diffHost.diffElement);
      pressKey(element, 'j');
      await element.updateComplete;
      assert.equal(
        element.diffHost.diffElement.viewMode,
        DiffViewMode.SIDE_BY_SIDE
      );
      assert.isTrue(element.diffHost.diffElement.displayLine);

      pressKey(element, Key.ESC);
      await element.updateComplete;
      assert.equal(
        element.diffHost.diffElement.viewMode,
        DiffViewMode.SIDE_BY_SIDE
      );
      assert.isFalse(element.diffHost.diffElement.displayLine);

      const setReviewedStub = sinon.stub(element, 'setReviewed');
      const handleToggleSpy = sinon.spy(element, 'handleToggleFileReviewed');
      assert.isFalse(handleToggleSpy.called);
      assert.isFalse(setReviewedStub.called);

      pressKey(element, 'r');
      assert.isTrue(handleToggleSpy.calledOnce);
      assert.isTrue(setReviewedStub.calledOnce);
      assert.equal(setReviewedStub.lastCall.args[0], true);

      // Handler is throttled, so another key press within 500 ms is ignored.
      clock.tick(100);
      pressKey(element, 'r');
      assert.isTrue(handleToggleSpy.calledOnce);
      assert.isTrue(setReviewedStub.calledOnce);

      clock.tick(1000);
      pressKey(element, 'r');
      assert.isTrue(handleToggleSpy.calledTwice);
      assert.isTrue(setReviewedStub.calledTwice);
      clock.restore();
    });

    test('moveToNextCommentThread navigates to next file', async () => {
      assertIsDefined(element.cursor);
      sinon.stub(element.cursor, 'isAtEnd').returns(true);
      element.changeNum = 42 as NumericChangeId;
      const comment: PathToCommentsInfoMap = {
        'wheatley.md': [createComment('c2', 21, 10, 'wheatley.md')],
      };
      element.changeComments = new ChangeComments(comment);
      element.patchRange = {
        basePatchNum: PARENT,
        patchNum: 10 as RevisionPatchSetNum,
      };
      element.change = {
        ...createParsedChange(),
        _number: 42 as NumericChangeId,
        revisions: {
          a: createRevision(10),
        },
      };
      element.files = getFilesFromFileList([
        'chell.go',
        'glados.txt',
        'wheatley.md',
      ]);
      element.path = 'glados.txt';
      element.loggedIn = true;
      await element.updateComplete;
      setUrlStub.reset();

      pressKey(element, 'N');
      await element.updateComplete;
      assert.equal(setUrlStub.callCount, 1);
      assert.equal(
        setUrlStub.lastCall.firstArg,
        '/c/test-project/+/42/10/wheatley.md#21'
      );

      element.path = 'wheatley.md'; // navigated to next file

      pressKey(element, 'N');
      await element.updateComplete;

      assert.equal(setUrlStub.callCount, 2);
      assert.equal(setUrlStub.lastCall.firstArg, '/c/test-project/+/42');
    });

    test('shift+x shortcut toggles all diff context', async () => {
      assertIsDefined(element.diffHost);
      const toggleStub = sinon.stub(element.diffHost, 'toggleAllContext');
      pressKey(element, 'X');
      await element.updateComplete;
      assert.isTrue(toggleStub.called);
    });

    test('diff against base', async () => {
      element.patchRange = {
        basePatchNum: 5 as BasePatchSetNum,
        patchNum: 10 as RevisionPatchSetNum,
      };
      await element.updateComplete;
      element.handleDiffAgainstBase();
      assert.equal(
        setUrlStub.lastCall.firstArg,
        '/c/test-project/+/42/10/some/path.txt'
      );
    });

    test('diff against latest', async () => {
      element.path = 'foo';
      element.change = {
        ...createParsedChange(),
        revisions: createRevisions(12),
      };
      element.patchRange = {
        basePatchNum: 5 as BasePatchSetNum,
        patchNum: 10 as RevisionPatchSetNum,
      };
      await element.updateComplete;
      element.handleDiffAgainstLatest();
      assert.equal(
        setUrlStub.lastCall.firstArg,
        '/c/test-project/+/42/5..12/foo'
      );
    });

    test('handleDiffBaseAgainstLeft', async () => {
      element.path = 'foo';
      element.change = {
        ...createParsedChange(),
        revisions: createRevisions(10),
      };
      element.patchRange = {
        patchNum: 3 as RevisionPatchSetNum,
        basePatchNum: 1 as BasePatchSetNum,
      };
      element.viewState = {
        ...createDiffViewState(),
        patchNum: 3 as RevisionPatchSetNum,
        basePatchNum: 1 as BasePatchSetNum,
        diffView: {path: 'foo'},
      };
      await element.updateComplete;
      element.handleDiffBaseAgainstLeft();
      assert.equal(setUrlStub.lastCall.firstArg, '/c/test-project/+/42/1/foo');
    });

    test('handleDiffBaseAgainstLeft when initially navigating to a comment', () => {
      element.change = {
        ...createParsedChange(),
        revisions: createRevisions(10),
      };
      element.patchRange = {
        patchNum: 3 as RevisionPatchSetNum,
        basePatchNum: 1 as BasePatchSetNum,
      };
      sinon.stub(element, 'viewStateChanged');
      element.viewState = {
        ...createDiffViewState(),
        diffView: {commentLink: true},
      };
      element.focusLineNum = 10;
      element.handleDiffBaseAgainstLeft();
      assert.equal(
        setUrlStub.lastCall.firstArg,
        '/c/test-project/+/42/1/some/path.txt#10'
      );
    });

    test('handleDiffRightAgainstLatest', async () => {
      element.path = 'foo';
      element.change = {
        ...createParsedChange(),
        revisions: createRevisions(10),
      };
      element.patchRange = {
        basePatchNum: 1 as BasePatchSetNum,
        patchNum: 3 as RevisionPatchSetNum,
      };
      await element.updateComplete;
      element.handleDiffRightAgainstLatest();
      assert.equal(
        setUrlStub.lastCall.firstArg,
        '/c/test-project/+/42/3..10/foo'
      );
    });

    test('handleDiffBaseAgainstLatest', async () => {
      element.change = {
        ...createParsedChange(),
        revisions: createRevisions(10),
      };
      element.patchRange = {
        basePatchNum: 1 as BasePatchSetNum,
        patchNum: 3 as RevisionPatchSetNum,
      };
      await element.updateComplete;
      element.handleDiffBaseAgainstLatest();
      assert.equal(
        setUrlStub.lastCall.firstArg,
        '/c/test-project/+/42/10/some/path.txt'
      );
    });

    test('A fires an error event when not logged in', async () => {
      element.loggedIn = false;
      const loggedInErrorSpy = sinon.spy();
      element.addEventListener('show-auth-required', loggedInErrorSpy);
      pressKey(element, 'a');
      await element.updateComplete;
      assert.isFalse(setUrlStub.calledOnce);
      assert.isTrue(loggedInErrorSpy.called);
    });

    test('A navigates to change with logged in', async () => {
      element.changeNum = 42 as NumericChangeId;
      element.patchRange = {
        basePatchNum: 5 as BasePatchSetNum,
        patchNum: 10 as RevisionPatchSetNum,
      };
      element.change = {
        ...createParsedChange(),
        _number: 42 as NumericChangeId,
        revisions: {
          a: createRevision(10),
          b: createRevision(5),
        },
      };
      element.loggedIn = true;
      await element.updateComplete;
      const loggedInErrorSpy = sinon.spy();
      element.addEventListener('show-auth-required', loggedInErrorSpy);
      setUrlStub.reset();

      pressKey(element, 'a');

      await element.updateComplete;
      assert.equal(setUrlStub.callCount, 1);
      assert.equal(
        setUrlStub.lastCall.firstArg,
        '/c/test-project/+/42/5..10?openReplyDialog=true'
      );
      assert.isFalse(loggedInErrorSpy.called);
    });

    test('A navigates to change with old patch number with logged in', async () => {
      element.changeNum = 42 as NumericChangeId;
      element.patchRange = {
        basePatchNum: PARENT,
        patchNum: 1 as RevisionPatchSetNum,
      };
      element.change = {
        ...createParsedChange(),
        _number: 42 as NumericChangeId,
        revisions: {
          a: createRevision(1),
          b: createRevision(2),
        },
      };
      element.loggedIn = true;
      const loggedInErrorSpy = sinon.spy();
      element.addEventListener('show-auth-required', loggedInErrorSpy);
      pressKey(element, 'a');
      await element.updateComplete;
      assert.isTrue(setUrlStub.calledOnce);
      assert.equal(
        setUrlStub.lastCall.firstArg,
        '/c/test-project/+/42/1?openReplyDialog=true'
      );
      assert.isFalse(loggedInErrorSpy.called);
    });

    test('keyboard shortcuts with patch range', () => {
      element.changeNum = 42 as NumericChangeId;
      element.patchRange = {
        basePatchNum: 5 as BasePatchSetNum,
        patchNum: 10 as RevisionPatchSetNum,
      };
      element.change = {
        ...createParsedChange(),
        _number: 42 as NumericChangeId,
        revisions: {
          a: createRevision(10),
          b: createRevision(5),
        },
      };
      element.files = getFilesFromFileList([
        'chell.go',
        'glados.txt',
        'wheatley.md',
      ]);
      element.path = 'glados.txt';

      pressKey(element, 'u');
      assert.equal(setUrlStub.callCount, 1);
      assert.equal(setUrlStub.lastCall.firstArg, '/c/test-project/+/42/5..10');

      pressKey(element, ']');
      assert.isTrue(element.loading);
      assert.equal(setUrlStub.callCount, 2);
      assert.equal(
        setUrlStub.lastCall.firstArg,
        '/c/test-project/+/42/5..10/wheatley.md'
      );
      element.path = 'wheatley.md';

      pressKey(element, '[');
      assert.isTrue(element.loading);
      assert.equal(setUrlStub.callCount, 3);
      assert.equal(
        setUrlStub.lastCall.firstArg,
        '/c/test-project/+/42/5..10/glados.txt'
      );
      element.path = 'glados.txt';

      pressKey(element, '[');
      assert.isTrue(element.loading);
      assert.equal(setUrlStub.callCount, 4);
      assert.equal(
        setUrlStub.lastCall.firstArg,
        '/c/test-project/+/42/5..10/chell.go'
      );
      element.path = 'chell.go';

      pressKey(element, '[');
      assert.isTrue(element.loading);
      assert.equal(setUrlStub.callCount, 5);
      assert.equal(setUrlStub.lastCall.firstArg, '/c/test-project/+/42/5..10');

      assertIsDefined(element.downloadModal);
      const downloadModalStub = sinon.stub(element.downloadModal, 'showModal');
      pressKey(element, 'd');
      assert.isTrue(downloadModalStub.called);
    });

    test('keyboard shortcuts with old patch number', () => {
      element.changeNum = 42 as NumericChangeId;
      element.patchRange = {
        basePatchNum: PARENT,
        patchNum: 1 as RevisionPatchSetNum,
      };
      element.change = {
        ...createParsedChange(),
        _number: 42 as NumericChangeId,
        revisions: {
          a: createRevision(1),
          b: createRevision(2),
        },
      };
      element.files = getFilesFromFileList([
        'chell.go',
        'glados.txt',
        'wheatley.md',
      ]);
      element.path = 'glados.txt';

      pressKey(element, 'u');
      assert.isTrue(setUrlStub.calledOnce);
      assert.equal(setUrlStub.lastCall.firstArg, '/c/test-project/+/42/1');

      pressKey(element, ']');
      assert.equal(
        setUrlStub.lastCall.firstArg,
        '/c/test-project/+/42/1/wheatley.md'
      );
      element.path = 'wheatley.md';

      pressKey(element, '[');
      assert.equal(
        setUrlStub.lastCall.firstArg,
        '/c/test-project/+/42/1/glados.txt'
      );
      element.path = 'glados.txt';

      pressKey(element, '[');
      assert.equal(
        setUrlStub.lastCall.firstArg,
        '/c/test-project/+/42/1/chell.go'
      );
      element.path = 'chell.go';

      setUrlStub.reset();
      pressKey(element, '[');
      assert.isTrue(setUrlStub.calledOnce);
      assert.equal(setUrlStub.lastCall.firstArg, '/c/test-project/+/42/1');
    });

    test('edit should redirect to edit page', async () => {
      element.loggedIn = true;
      element.path = 't.txt';
      element.patchRange = {
        basePatchNum: PARENT,
        patchNum: 1 as RevisionPatchSetNum,
      };
      element.change = {
        ...createParsedChange(),
        _number: 42 as NumericChangeId,
        project: 'gerrit' as RepoName,
        status: ChangeStatus.NEW,
        revisions: {
          a: createRevision(1),
          b: createRevision(2),
        },
      };
      await element.updateComplete;
      const editBtn = queryAndAssert<GrButton>(
        element,
        '.editButton gr-button'
      );
      assert.isTrue(!!editBtn);
      editBtn.click();
      assert.equal(setUrlStub.callCount, 1);
      assert.equal(setUrlStub.lastCall.firstArg, '/c/gerrit/+/42/1/t.txt,edit');
    });

    test('edit should redirect to edit page with line number', async () => {
      const lineNumber = 42;
      element.loggedIn = true;
      element.path = 't.txt';
      element.patchRange = {
        basePatchNum: PARENT,
        patchNum: 1 as RevisionPatchSetNum,
      };
      element.change = {
        ...createParsedChange(),
        _number: 42 as NumericChangeId,
        project: 'gerrit' as RepoName,
        status: ChangeStatus.NEW,
        revisions: {
          a: createRevision(1),
          b: createRevision(2),
        },
      };
      assertIsDefined(element.cursor);
      sinon
        .stub(element.cursor, 'getAddress')
        .returns({number: lineNumber, leftSide: false});
      await element.updateComplete;
      const editBtn = queryAndAssert<GrButton>(
        element,
        '.editButton gr-button'
      );
      assert.isTrue(!!editBtn);
      editBtn.click();
      assert.equal(setUrlStub.callCount, 1);
      assert.equal(
        setUrlStub.lastCall.firstArg,
        '/c/gerrit/+/42/1/t.txt,edit#42'
      );
    });

    async function isEditVisibile({
      loggedIn,
      changeStatus,
    }: {
      loggedIn: boolean;
      changeStatus: ChangeStatus;
    }): Promise<boolean> {
      element.loggedIn = loggedIn;
      element.path = 't.txt';
      element.patchRange = {
        basePatchNum: PARENT,
        patchNum: 1 as RevisionPatchSetNum,
      };
      element.change = {
        ...createParsedChange(),
        _number: 42 as NumericChangeId,
        status: changeStatus,
        revisions: {
          a: createRevision(1),
          b: createRevision(2),
        },
      };
      await element.updateComplete;
      const editBtn = query(element, '.editButton gr-button');
      return !!editBtn;
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
      test('when no prefs or logged out', async () => {
        const getDiffPrefsContainer = () =>
          query<HTMLSpanElement>(element, '#diffPrefsContainer');
        element.prefs = undefined;
        element.loggedIn = false;
        await element.updateComplete;
        assert.isNotOk(getDiffPrefsContainer());

        element.loggedIn = true;
        await element.updateComplete;
        assert.isNotOk(getDiffPrefsContainer());

        element.loggedIn = false;
        element.prefs = {...createDefaultDiffPrefs(), font_size: 12};
        await element.updateComplete;
        assert.isNotOk(getDiffPrefsContainer());

        element.loggedIn = true;
        element.prefs = {...createDefaultDiffPrefs(), font_size: 12};
        await element.updateComplete;
        assert.isOk(getDiffPrefsContainer());
      });
    });

    test('prefsButton opens gr-diff-preferences', () => {
      const handlePrefsTapSpy = sinon.spy(element, 'handlePrefsTap');
      assertIsDefined(element.diffPreferencesDialog);
      const overlayOpenStub = sinon.stub(element.diffPreferencesDialog, 'open');
      const prefsButton = queryAndAssert<GrButton>(element, '.prefsButton');
      prefsButton.click();

      assert.isTrue(handlePrefsTapSpy.called);
      assert.isTrue(overlayOpenStub.called);
    });

    suite('url parameters', () => {
      test('_formattedFiles', () => {
        element.changeNum = 42 as NumericChangeId;
        element.patchRange = {
          basePatchNum: PARENT,
          patchNum: 10 as RevisionPatchSetNum,
        };
        element.change = {
          ...createParsedChange(),
          _number: 42 as NumericChangeId,
        };
        element.files = getFilesFromFileList([
          'chell.go',
          'glados.txt',
          'wheatley.md',
          '/COMMIT_MSG',
          '/MERGE_LIST',
        ]);
        element.path = 'glados.txt';
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

        const result = element.formatFilesForDropdown();

        assert.deepEqual(result, expectedFormattedFiles);
        assert.equal(result[1].value, element.path);
      });

      test('prev/up/next links', async () => {
        element.changeNum = 42 as NumericChangeId;
        element.patchRange = {
          basePatchNum: PARENT,
          patchNum: 10 as RevisionPatchSetNum,
        };
        element.change = {
          ...createParsedChange(),
          _number: 42 as NumericChangeId,
          revisions: {
            a: createRevision(10),
          },
        };
        element.files = getFilesFromFileList([
          'chell.go',
          'glados.txt',
          'wheatley.md',
        ]);
        element.path = 'glados.txt';
        await element.updateComplete;

        const linkEls = queryAll(element, '.navLink');
        assert.equal(linkEls.length, 3);
        assert.equal(
          linkEls[0].getAttribute('href'),
          '/c/test-project/+/42/10/chell.go'
        );
        assert.equal(linkEls[1].getAttribute('href'), '/c/test-project/+/42');
        assert.equal(
          linkEls[2].getAttribute('href'),
          '/c/test-project/+/42/10/wheatley.md'
        );
        element.path = 'wheatley.md';
        await element.updateComplete;
        assert.equal(
          linkEls[0].getAttribute('href'),
          '/c/test-project/+/42/10/glados.txt'
        );
        assert.equal(linkEls[1].getAttribute('href'), '/c/test-project/+/42');
        assert.equal(linkEls[2].getAttribute('href'), '/c/test-project/+/42');
        element.path = 'chell.go';
        await element.updateComplete;
        assert.equal(linkEls[0].getAttribute('href'), '/c/test-project/+/42');
        assert.equal(linkEls[1].getAttribute('href'), '/c/test-project/+/42');
        assert.equal(
          linkEls[2].getAttribute('href'),
          '/c/test-project/+/42/10/glados.txt'
        );
        element.path = 'not_a_real_file';
        await element.updateComplete;
        assert.equal(
          linkEls[0].getAttribute('href'),
          '/c/test-project/+/42/10/wheatley.md'
        );
        assert.equal(linkEls[1].getAttribute('href'), '/c/test-project/+/42');
        assert.equal(
          linkEls[2].getAttribute('href'),
          '/c/test-project/+/42/10/chell.go'
        );
      });

      test('prev/up/next links with patch range', async () => {
        element.changeNum = 42 as NumericChangeId;
        element.patchRange = {
          basePatchNum: 5 as BasePatchSetNum,
          patchNum: 10 as RevisionPatchSetNum,
        };
        element.change = {
          ...createParsedChange(),
          _number: 42 as NumericChangeId,
          revisions: {
            a: createRevision(5),
            b: createRevision(10),
          },
        };
        element.files = getFilesFromFileList([
          'chell.go',
          'glados.txt',
          'wheatley.md',
        ]);
        element.path = 'glados.txt';
        await element.updateComplete;
        const linkEls = queryAll(element, '.navLink');
        assert.equal(linkEls.length, 3);
        assert.equal(
          linkEls[0].getAttribute('href'),
          '/c/test-project/+/42/5..10/chell.go'
        );
        assert.equal(
          linkEls[1].getAttribute('href'),
          '/c/test-project/+/42/5..10'
        );
        assert.equal(
          linkEls[2].getAttribute('href'),
          '/c/test-project/+/42/5..10/wheatley.md'
        );
        element.path = 'wheatley.md';
        await element.updateComplete;
        assert.equal(
          linkEls[0].getAttribute('href'),
          '/c/test-project/+/42/5..10/glados.txt'
        );
        assert.equal(
          linkEls[1].getAttribute('href'),
          '/c/test-project/+/42/5..10'
        );
        assert.equal(
          linkEls[2].getAttribute('href'),
          '/c/test-project/+/42/5..10'
        );
        element.path = 'chell.go';
        await element.updateComplete;
        assert.equal(
          linkEls[0].getAttribute('href'),
          '/c/test-project/+/42/5..10'
        );
        assert.equal(
          linkEls[1].getAttribute('href'),
          '/c/test-project/+/42/5..10'
        );
        assert.equal(
          linkEls[2].getAttribute('href'),
          '/c/test-project/+/42/5..10/glados.txt'
        );
      });
    });

    test('handlePatchChange calls setUrl correctly', async () => {
      element.change = {
        ...createParsedChange(),
        _number: 321 as NumericChangeId,
        project: 'foo/bar' as RepoName,
      };
      element.path = 'path/to/file.txt';

      element.patchRange = {
        basePatchNum: PARENT,
        patchNum: 3 as RevisionPatchSetNum,
      };
      await element.updateComplete;

      const detail = {
        basePatchNum: PARENT,
        patchNum: 1 as RevisionPatchSetNum,
      };

      queryAndAssert(element, '#rangeSelect').dispatchEvent(
        new CustomEvent('patch-range-change', {detail, bubbles: false})
      );

      assert.equal(
        setUrlStub.lastCall.firstArg,
        '/c/foo/bar/+/321/1/path/to/file.txt'
      );
    });

    test(
      '_prefs.manual_review true means set reviewed is not ' +
        'automatically called',
      async () => {
        const setReviewedFileStatusStub = sinon
          .stub(changeModel, 'setReviewedFilesStatus')
          .callsFake(() => Promise.resolve());

        const setReviewedStatusStub = sinon.spy(element, 'setReviewedStatus');

        assertIsDefined(element.diffHost);
        sinon.stub(element.diffHost, 'reload');
        element.loggedIn = true;
        const diffPreferences = {
          ...createDefaultDiffPrefs(),
          manual_review: true,
        };
        userModel.setDiffPreferences(diffPreferences);
        changeModel.setState({
          change: createParsedChange(),
          diffPath: '/COMMIT_MSG',
          reviewedFiles: [],
          loadingStatus: LoadingStatus.LOADED,
        });

        await waitUntil(() => setReviewedStatusStub.called);

        assert.isFalse(setReviewedFileStatusStub.called);

        // if prefs are updated then the reviewed status should not be set again
        userModel.setDiffPreferences(createDefaultDiffPrefs());

        await element.updateComplete;
        assert.isFalse(setReviewedFileStatusStub.called);
      }
    );

    test('_prefs.manual_review false means set reviewed is called', async () => {
      const setReviewedFileStatusStub = sinon
        .stub(changeModel, 'setReviewedFilesStatus')
        .callsFake(() => Promise.resolve());

      assertIsDefined(element.diffHost);
      sinon.stub(element.diffHost, 'reload');
      element.loggedIn = true;
      const diffPreferences = {
        ...createDefaultDiffPrefs(),
        manual_review: false,
      };
      userModel.setDiffPreferences(diffPreferences);
      changeModel.setState({
        change: createParsedChange(),
        diffPath: '/COMMIT_MSG',
        reviewedFiles: [],
        loadingStatus: LoadingStatus.LOADED,
      });

      await waitUntil(() => setReviewedFileStatusStub.called);

      assert.isTrue(setReviewedFileStatusStub.called);
    });

    test('file review status', async () => {
      const saveReviewedStub = sinon
        .stub(changeModel, 'setReviewedFilesStatus')
        .callsFake(() => Promise.resolve());
      changeModel.setState({
        change: createParsedChange(),
        diffPath: '/COMMIT_MSG',
        reviewedFiles: [],
        loadingStatus: LoadingStatus.LOADED,
      });
      element.loggedIn = true;
      assertIsDefined(element.diffHost);
      sinon.stub(element.diffHost, 'reload');

      userModel.setDiffPreferences(createDefaultDiffPrefs());

      await waitUntil(() => saveReviewedStub.called);

      changeModel.updateStateFileReviewed('/COMMIT_MSG', true);
      await element.updateComplete;

      const reviewedStatusCheckBox = queryAndAssert<HTMLInputElement>(
        element,
        'input[type="checkbox"]'
      );

      assert.isTrue(reviewedStatusCheckBox.checked);
      assert.deepEqual(saveReviewedStub.lastCall.args, [
        42,
        1,
        '/COMMIT_MSG',
        true,
      ]);

      reviewedStatusCheckBox.click();
      assert.isFalse(reviewedStatusCheckBox.checked);
      assert.deepEqual(saveReviewedStub.lastCall.args, [
        42,
        1,
        '/COMMIT_MSG',
        false,
      ]);

      changeModel.updateStateFileReviewed('/COMMIT_MSG', false);
      await element.updateComplete;

      reviewedStatusCheckBox.click();
      assert.isTrue(reviewedStatusCheckBox.checked);
      assert.deepEqual(saveReviewedStub.lastCall.args, [
        42,
        1,
        '/COMMIT_MSG',
        true,
      ]);

      const callCount = saveReviewedStub.callCount;

      element.viewState = {
        ...createDiffViewState(),
        repo: 'test' as RepoName,
      };
      await element.updateComplete;

      // saveReviewedState observer observes viewState, but should not fire when
      // view !== GerritView.DIFF.
      assert.equal(saveReviewedStub.callCount, callCount);
    });

    test('file review status with edit loaded', async () => {
      const saveReviewedStub = sinon.stub(
        changeModel,
        'setReviewedFilesStatus'
      );

      element.patchRange = {
        basePatchNum: 1 as BasePatchSetNum,
        patchNum: EDIT,
      };
      await waitEventLoop();

      assert.isTrue(element.computeEditMode());
      element.setReviewed(true);
      assert.isFalse(saveReviewedStub.called);
    });

    test('hash is determined from viewState', async () => {
      assertIsDefined(element.diffHost);
      sinon.stub(element.diffHost, 'reload');
      const initLineStub = sinon.stub(element, 'initLineOfInterestAndCursor');

      element.loggedIn = true;
      element.viewState = {
        ...createDiffViewState(),
        patchNum: 2 as RevisionPatchSetNum,
        basePatchNum: 1 as BasePatchSetNum,
        diffView: {path: '/COMMIT_MSG'},
      };

      await element.updateComplete;
      await waitEventLoop();
      assert.isTrue(initLineStub.calledOnce);
    });

    test('diff mode selector correctly toggles the diff', async () => {
      const select = queryAndAssert<GrDiffModeSelector>(element, '#modeSelect');
      const diffDisplay = element.diffHost;
      assertIsDefined(diffDisplay);
      element.userPrefs = {
        ...createDefaultPreferences(),
        diff_view: DiffViewMode.SIDE_BY_SIDE,
      };
      browserModel.setScreenWidth(0);

      const userStub = sinon.stub(userModel, 'updatePreferences');

      await element.updateComplete;
      // The mode selected in the view state reflects the selected option.
      // assert.equal(element.userPrefs.diff_view, select.mode);

      // The mode selected in the view state reflects the view rednered in the
      // diff.
      assert.equal(select.mode, diffDisplay.viewMode);

      // We will simulate a user change of the selected mode.
      element.handleToggleDiffMode();
      assert.isTrue(
        userStub.calledWithExactly({
          diff_view: DiffViewMode.UNIFIED,
        })
      );
    });

    test('diff mode selector should be hidden for binary', async () => {
      element.diff = {
        ...createDiff(),
        binary: true,
        content: [],
      };

      await element.updateComplete;
      const diffModeSelector = queryAndAssert(element, '.diffModeSelector');
      assert.isTrue(diffModeSelector.classList.contains('hide'));
    });

    suite('commitRange', () => {
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
      setup(async () => {
        assertIsDefined(element.diffHost);
        sinon.stub(element.diffHost, 'reload');
        sinon.stub(element, 'initCursor');
        element.change = change;
        await element.updateComplete;
        await element.diffHost.updateComplete;
      });

      test('uses the patchNum and basePatchNum ', async () => {
        element.viewState = {
          ...createDiffViewState(),
          patchNum: 4 as RevisionPatchSetNum,
          basePatchNum: 2 as BasePatchSetNum,
          diffView: {path: '/COMMIT_MSG'},
        };
        element.change = change;
        await element.updateComplete;
        await waitEventLoop();
        assert.deepEqual(element.commitRange, {
          baseCommit: 'commit-sha-2' as CommitId,
          commit: 'commit-sha-4' as CommitId,
        });
      });

      test('uses the parent when there is no base patch num ', async () => {
        element.viewState = {
          ...createDiffViewState(),
          patchNum: 5 as RevisionPatchSetNum,
          diffView: {path: '/COMMIT_MSG'},
        };
        element.change = change;
        await element.updateComplete;
        await waitEventLoop();
        assert.deepEqual(element.commitRange, {
          commit: 'commit-sha-5' as CommitId,
          baseCommit: 'sha-5-parent' as CommitId,
        });
      });
    });

    test('initCursor', () => {
      assertIsDefined(element.cursor);
      assert.isNotOk(element.cursor.initialLineNumber);

      // Does nothing when viewState specify no cursor address:
      element.initCursor(false);
      assert.isNotOk(element.cursor.initialLineNumber);

      // Does nothing when viewState specify side but no number:
      element.initCursor(true);
      assert.isNotOk(element.cursor.initialLineNumber);

      // Revision hash: specifies lineNum but not side.

      element.focusLineNum = 234;
      element.initCursor(false);
      assert.equal(element.cursor.initialLineNumber, 234);
      assert.equal(element.cursor.side, Side.RIGHT);

      // Base hash: specifies lineNum and side.
      element.focusLineNum = 345;
      element.initCursor(true);
      assert.equal(element.cursor.initialLineNumber, 345);
      assert.equal(element.cursor.side, Side.LEFT);

      // Specifies right side:
      element.focusLineNum = 123;
      element.initCursor(false);
      assert.equal(element.cursor.initialLineNumber, 123);
      assert.equal(element.cursor.side, Side.RIGHT);
    });

    test('getLineOfInterest', () => {
      assert.isUndefined(element.getLineOfInterest(false));

      element.focusLineNum = 12;
      let result = element.getLineOfInterest(false);
      assert.isOk(result);
      assert.equal(result!.lineNum, 12);
      assert.equal(result!.side, Side.RIGHT);

      result = element.getLineOfInterest(true);
      assert.isOk(result);
      assert.equal(result!.lineNum, 12);
      assert.equal(result!.side, Side.LEFT);
    });

    test('onLineSelected', () => {
      const replaceStateStub = sinon.stub(history, 'replaceState');
      assertIsDefined(element.cursor);
      sinon
        .stub(element.cursor, 'getAddress')
        .returns({number: 123, leftSide: false});

      element.changeNum = 321 as NumericChangeId;
      element.change = {
        ...createParsedChange(),
        _number: 321 as NumericChangeId,
        project: 'foo/bar' as RepoName,
      };
      element.patchRange = {
        basePatchNum: 3 as BasePatchSetNum,
        patchNum: 5 as RevisionPatchSetNum,
      };
      const e = {detail: {number: 123, side: Side.RIGHT}} as CustomEvent;

      element.onLineSelected(e);

      assert.isTrue(replaceStateStub.called);
    });

    test('line selected on left side', () => {
      const replaceStateStub = sinon.stub(history, 'replaceState');
      assertIsDefined(element.cursor);
      sinon
        .stub(element.cursor, 'getAddress')
        .returns({number: 123, leftSide: true});

      element.changeNum = 321 as NumericChangeId;
      element.change = {
        ...createParsedChange(),
        _number: 321 as NumericChangeId,
        project: 'foo/bar' as RepoName,
      };
      element.patchRange = {
        basePatchNum: 3 as BasePatchSetNum,
        patchNum: 5 as RevisionPatchSetNum,
      };
      const e = {detail: {number: 123, side: Side.LEFT}} as CustomEvent;

      element.onLineSelected(e);

      assert.isTrue(replaceStateStub.called);
    });

    test('handleToggleDiffMode', () => {
      const userStub = sinon.stub(userModel, 'updatePreferences');
      element.userPrefs = {
        ...createDefaultPreferences(),
        diff_view: DiffViewMode.SIDE_BY_SIDE,
      };

      element.handleToggleDiffMode();
      assert.deepEqual(userStub.lastCall.args[0], {
        diff_view: DiffViewMode.UNIFIED,
      });

      element.userPrefs = {
        ...createDefaultPreferences(),
        diff_view: DiffViewMode.UNIFIED,
      };

      element.handleToggleDiffMode();
      assert.deepEqual(userStub.lastCall.args[0], {
        diff_view: DiffViewMode.SIDE_BY_SIDE,
      });
    });

    suite('initPatchRange', () => {
      setup(async () => {
        getDiffRestApiStub.returns(Promise.resolve(createDiff()));
        element.viewState = {
          ...createDiffViewState(),
          patchNum: 3 as RevisionPatchSetNum,
          diffView: {path: 'abcd'},
        };
        await element.updateComplete;
      });
      test('empty', () => {
        sinon.stub(element, 'getPaths').returns({});
        element.initPatchRange();
        assert.equal(Object.keys(element.commentMap ?? {}).length, 0);
      });

      test('has paths', () => {
        sinon.stub(element, 'getPaths').returns({
          'path/to/file/one.cpp': true,
          'path-to/file/two.py': true,
        });
        element.changeNum = 42 as NumericChangeId;
        element.patchRange = {
          basePatchNum: 3 as BasePatchSetNum,
          patchNum: 5 as RevisionPatchSetNum,
        };
        element.initPatchRange();
        assert.deepEqual(Object.keys(element.commentMap ?? {}), [
          'path/to/file/one.cpp',
          'path-to/file/two.py',
        ]);
      });
    });

    suite('computeCommentSkips', () => {
      test('empty file list', () => {
        const commentMap = {
          'path/one.jpg': true,
          'path/three.wav': true,
        };
        const path = 'path/two.m4v';
        const result = element.computeCommentSkips(commentMap, [], path);
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

        let result = element.computeCommentSkips(commentMap, fileList, path);
        assert.isOk(result);
        assert.equal(result!.previous, fileList[0]);
        assert.equal(result!.next, fileList[2]);

        commentMap[fileList[1]] = true;

        result = element.computeCommentSkips(commentMap, fileList, path);
        assert.isOk(result);
        assert.equal(result!.previous, fileList[0]);
        assert.equal(result!.next, fileList[2]);

        path = fileList[0];

        result = element.computeCommentSkips(commentMap, fileList, path);
        assert.isOk(result);
        assert.isNull(result!.previous);
        assert.equal(result!.next, fileList[1]);

        path = fileList[2];

        result = element.computeCommentSkips(commentMap, fileList, path);
        assert.isOk(result);
        assert.equal(result!.previous, fileList[1]);
        assert.isNull(result!.next);
      });

      suite('skip next/previous', () => {
        let navToChangeStub: SinonStub;

        setup(() => {
          navToChangeStub = sinon.stub(element, 'navToChangeView');
          element.files = getFilesFromFileList([
            'path/one.jpg',
            'path/two.m4v',
            'path/three.wav',
          ]);
          element.patchRange = {
            patchNum: 2 as RevisionPatchSetNum,
            basePatchNum: 1 as BasePatchSetNum,
          };
        });

        suite('moveToPreviousFileWithComment', () => {
          test('no skips', () => {
            element.moveToPreviousFileWithComment();
            assert.isFalse(navToChangeStub.called);
            assert.isFalse(setUrlStub.called);
          });

          test('no previous', async () => {
            const commentMap: CommentMap = {};
            commentMap[element.files.sortedFileList[0]!] = false;
            commentMap[element.files.sortedFileList[1]!] = false;
            commentMap[element.files.sortedFileList[2]!] = true;
            element.commentMap = commentMap;
            element.path = element.files.sortedFileList[1];
            await element.updateComplete;

            element.moveToPreviousFileWithComment();
            assert.isTrue(navToChangeStub.calledOnce);
            assert.isFalse(setUrlStub.called);
          });

          test('w/ previous', async () => {
            const commentMap: CommentMap = {};
            commentMap[element.files.sortedFileList[0]!] = true;
            commentMap[element.files.sortedFileList[1]!] = false;
            commentMap[element.files.sortedFileList[2]!] = true;
            element.commentMap = commentMap;
            element.path = element.files.sortedFileList[1];
            await element.updateComplete;

            element.moveToPreviousFileWithComment();
            assert.isFalse(navToChangeStub.called);
            assert.isTrue(setUrlStub.calledOnce);
          });
        });

        suite('moveToNextFileWithComment', () => {
          test('no skips', () => {
            element.moveToNextFileWithComment();
            assert.isFalse(navToChangeStub.called);
            assert.isFalse(setUrlStub.called);
          });

          test('no previous', async () => {
            const commentMap: CommentMap = {};
            commentMap[element.files.sortedFileList[0]!] = true;
            commentMap[element.files.sortedFileList[1]!] = false;
            commentMap[element.files.sortedFileList[2]!] = false;
            element.commentMap = commentMap;
            element.path = element.files.sortedFileList[1];
            await element.updateComplete;

            element.moveToNextFileWithComment();
            assert.isTrue(navToChangeStub.calledOnce);
            assert.isFalse(setUrlStub.called);
          });

          test('w/ previous', async () => {
            const commentMap: CommentMap = {};
            commentMap[element.files.sortedFileList[0]!] = true;
            commentMap[element.files.sortedFileList[1]!] = false;
            commentMap[element.files.sortedFileList[2]!] = true;
            element.commentMap = commentMap;
            element.path = element.files.sortedFileList[1];
            await element.updateComplete;

            element.moveToNextFileWithComment();
            assert.isFalse(navToChangeStub.called);
            assert.isTrue(setUrlStub.calledOnce);
          });
        });
      });
    });

    test('_computeEditMode', () => {
      const callCompute = (range: PatchRange) => {
        element.patchRange = range;
        return element.computeEditMode();
      };
      assert.isFalse(
        callCompute({
          basePatchNum: PARENT,
          patchNum: 1 as RevisionPatchSetNum,
        })
      );
      assert.isTrue(
        callCompute({
          basePatchNum: 1 as BasePatchSetNum,
          patchNum: EDIT,
        })
      );
    });

    test('computeFileNum', () => {
      element.path = '/foo';
      assert.equal(
        element.computeFileNum([
          {text: '/foo', value: '/foo'},
          {text: '/bar', value: '/bar'},
        ]),
        1
      );
      element.path = '/bar';
      assert.equal(
        element.computeFileNum([
          {text: '/foo', value: '/foo'},
          {text: '/bar', value: '/bar'},
        ]),
        2
      );
    });

    test('computeFileNumClass', () => {
      assert.equal(element.computeFileNumClass(0, []), '');
      assert.equal(
        element.computeFileNumClass(1, [
          {text: '/foo', value: '/foo'},
          {text: '/bar', value: '/bar'},
        ]),
        'show'
      );
    });

    test('f open file dropdown', async () => {
      assertIsDefined(element.dropdown);
      assertIsDefined(element.dropdown.dropdown);
      assert.isFalse(element.dropdown.dropdown.opened);
      pressKey(element, 'f');
      await element.updateComplete;
      assert.isTrue(element.dropdown.dropdown.opened);
    });

    suite('blame', () => {
      test('toggle blame with button', () => {
        assertIsDefined(element.diffHost);
        const toggleBlame = sinon
          .stub(element.diffHost, 'loadBlame')
          .callsFake(() => Promise.resolve([]));
        queryAndAssert<GrButton>(element, '#toggleBlame').click();
        assert.isTrue(toggleBlame.calledOnce);
      });
      test('toggle blame with shortcut', () => {
        assertIsDefined(element.diffHost);
        const toggleBlame = sinon
          .stub(element.diffHost, 'loadBlame')
          .callsFake(() => Promise.resolve([]));
        pressKey(element, 'b');
        assert.isTrue(toggleBlame.calledOnce);
      });
    });

    suite('editMode behavior', () => {
      setup(async () => {
        element.loggedIn = true;
        await element.updateComplete;
      });

      test('reviewed checkbox', async () => {
        sinon.stub(element, 'handlePatchChange');
        element.patchRange = createPatchRange();
        await element.updateComplete;

        let checkbox = queryAndAssert(element, '#reviewed');
        assert.isTrue(isVisible(checkbox));

        element.patchRange = {...element.patchRange, patchNum: EDIT};
        await element.updateComplete;

        checkbox = queryAndAssert(element, '#reviewed');
        assert.isFalse(isVisible(checkbox));
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
        navToFileStub = sinon.stub(element, 'navToFile');
        assertIsDefined(element.cursor);
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

        pressKey(element, 'n');

        assert.isTrue(moveToNextChunkStub.called);
        assert.equal(
          dispatchEventStub.lastCall.args[0].type,
          EventType.SHOW_ALERT
        );
        assert.isFalse(navToFileStub.called);
      });

      test('navigates to next file when n is tapped again', () => {
        moveToNextChunkStub.returns(CursorMoveResult.CLIPPED);
        isAtEndStub.returns(true);

        element.files = getFilesFromFileList(['file1', 'file2', 'file3']);
        element.reviewedFiles = new Set(['file2']);
        element.path = 'file1';

        nowStub.returns(5);
        pressKey(element, 'n');
        nowStub.returns(10);
        pressKey(element, 'n');

        assert.isTrue(navToFileStub.called);
        assert.deepEqual(navToFileStub.lastCall.args, [['file1', 'file3'], 1]);
      });

      test('does not navigate if n is tapped twice too slow', () => {
        moveToNextChunkStub.returns(CursorMoveResult.CLIPPED);
        isAtEndStub.returns(true);

        nowStub.returns(5);
        pressKey(element, 'n');
        nowStub.returns(6000);
        pressKey(element, 'n');

        assert.isFalse(navToFileStub.called);
      });

      test('shows toast when at the start of file', () => {
        moveToPreviousChunkStub.returns(CursorMoveResult.CLIPPED);
        isAtStartStub.returns(true);

        pressKey(element, 'p');

        assert.isTrue(moveToPreviousChunkStub.called);
        assert.equal(
          dispatchEventStub.lastCall.args[0].type,
          EventType.SHOW_ALERT
        );
        assert.isFalse(navToFileStub.called);
      });

      test('navigates to prev file when p is tapped again', () => {
        moveToPreviousChunkStub.returns(CursorMoveResult.CLIPPED);
        isAtStartStub.returns(true);

        element.files = getFilesFromFileList(['file1', 'file2', 'file3']);
        element.reviewedFiles = new Set(['file2']);
        element.path = 'file3';

        nowStub.returns(5);
        pressKey(element, 'p');
        nowStub.returns(10);
        pressKey(element, 'p');

        assert.isTrue(navToFileStub.called);
        assert.deepEqual(navToFileStub.lastCall.args, [['file1', 'file3'], -1]);
      });

      test('does not navigate if p is tapped twice too slow', () => {
        moveToPreviousChunkStub.returns(CursorMoveResult.CLIPPED);
        isAtStartStub.returns(true);

        nowStub.returns(5);
        pressKey(element, 'p');
        nowStub.returns(6000);
        pressKey(element, 'p');

        assert.isFalse(navToFileStub.called);
      });

      test('does not navigate when tapping n then p', () => {
        moveToNextChunkStub.returns(CursorMoveResult.CLIPPED);
        isAtEndStub.returns(true);

        nowStub.returns(5);
        pressKey(element, 'n');

        moveToPreviousChunkStub.returns(CursorMoveResult.CLIPPED);
        isAtStartStub.returns(true);

        nowStub.returns(10);
        pressKey(element, 'p');

        assert.isFalse(navToFileStub.called);
      });
    });

    test('shift+m navigates to next unreviewed file', async () => {
      element.files = getFilesFromFileList(['file1', 'file2', 'file3']);
      element.reviewedFiles = new Set(['file1', 'file2']);
      element.path = 'file1';
      const reviewedStub = sinon.stub(element, 'setReviewed');
      const navStub = sinon.stub(element, 'navToFile');
      pressKey(element, 'M');
      await waitEventLoop();

      assert.isTrue(reviewedStub.lastCall.args[0]);
      assert.deepEqual(navStub.lastCall.args, [['file1', 'file3'], 1]);
    });

    test('File change should trigger setUrl once', async () => {
      element.files = getFilesFromFileList(['file1', 'file2', 'file3']);
      sinon.stub(element, 'initLineOfInterestAndCursor');

      // Load file1
      element.viewState = {
        ...createDiffViewState(),
        patchNum: 1 as RevisionPatchSetNum,
        repo: 'test-project' as RepoName,
        diffView: {path: 'file1'},
      };
      element.patchRange = {
        patchNum: 1 as RevisionPatchSetNum,
        basePatchNum: PARENT,
      };
      element.change = {
        ...createParsedChange(),
        revisions: createRevisions(1),
      };
      await element.updateComplete;
      assert.isFalse(setUrlStub.called);

      // Switch to file2
      element.handleFileChange(
        new CustomEvent('value-change', {detail: {value: 'file2'}})
      );
      assert.isTrue(setUrlStub.calledOnce);

      // This is to mock the param change triggered by above navigate
      element.viewState = {
        ...createDiffViewState(),
        patchNum: 1 as RevisionPatchSetNum,
        repo: 'test-project' as RepoName,
        diffView: {path: 'file2'},
      };
      element.patchRange = {
        patchNum: 1 as RevisionPatchSetNum,
        basePatchNum: PARENT,
      };

      // No extra call
      assert.isTrue(setUrlStub.calledOnce);
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

      element.change = createParsedChange();
      element.change.project = 'test' as RepoName;
      element.changeNum = 12 as NumericChangeId;
      element.patchRange = {
        patchNum: 1 as RevisionPatchSetNum,
        basePatchNum: PARENT,
      };
      element.path = 'index.php';
      element.diff = createDiff();
      assert.deepEqual(element.computeDownloadDropdownLinks(), downloadLinks);
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

      element.change = createParsedChange();
      element.change.project = 'test' as RepoName;
      element.changeNum = 12 as NumericChangeId;
      element.patchRange = {
        patchNum: 3 as RevisionPatchSetNum,
        basePatchNum: 2 as BasePatchSetNum,
      };
      element.path = 'index.php';
      element.diff = diff;
      assert.deepEqual(element.computeDownloadDropdownLinks(), downloadLinks);
    });

    test('computeDownloadFileLink', () => {
      assert.equal(
        element.computeDownloadFileLink(
          'test' as RepoName,
          12 as NumericChangeId,
          {patchNum: 1 as PatchSetNumber, basePatchNum: PARENT},
          'index.php',
          true
        ),
        '/changes/test~12/revisions/1/files/index.php/download?parent=1'
      );

      assert.equal(
        element.computeDownloadFileLink(
          'test' as RepoName,
          12 as NumericChangeId,
          {patchNum: 1 as PatchSetNumber, basePatchNum: -2 as PatchSetNumber},
          'index.php',
          true
        ),
        '/changes/test~12/revisions/1/files/index.php/download?parent=2'
      );

      assert.equal(
        element.computeDownloadFileLink(
          'test' as RepoName,
          12 as NumericChangeId,
          {patchNum: 3 as PatchSetNumber, basePatchNum: 2 as PatchSetNumber},
          'index.php',
          true
        ),
        '/changes/test~12/revisions/2/files/index.php/download'
      );

      assert.equal(
        element.computeDownloadFileLink(
          'test' as RepoName,
          12 as NumericChangeId,
          {patchNum: 3 as PatchSetNumber, basePatchNum: 2 as PatchSetNumber},
          'index.php',
          false
        ),
        '/changes/test~12/revisions/3/files/index.php/download'
      );
    });

    test('computeDownloadPatchLink', () => {
      assert.equal(
        element.computeDownloadPatchLink(
          'test' as RepoName,
          12 as NumericChangeId,
          {basePatchNum: PARENT, patchNum: 1 as RevisionPatchSetNum},
          'index.php'
        ),
        '/changes/test~12/revisions/1/patch?zip&path=index.php'
      );
    });
  });
});
