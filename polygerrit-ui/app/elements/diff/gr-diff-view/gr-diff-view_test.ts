/**
 * @license
 * Copyright 2015 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-diff-view';
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
  stubRestApi,
  waitEventLoop,
  waitUntil,
} from '../../../test/test-utils';
import {ChangeComments} from '../gr-comment-api/gr-comment-api';
import {
  createRevisions,
  createComment as createCommentGeneric,
  createDiff,
  createServerInfo,
  createConfig,
  createParsedChange,
  createRevision,
  createFileInfo,
  createDiffViewState,
  TEST_NUMERIC_CHANGE_ID,
} from '../../../test/test-data-generators';
import {
  BasePatchSetNum,
  CommentInfo,
  EDIT,
  NumericChangeId,
  PARENT,
  PatchSetNum,
  PatchSetNumber,
  PathToCommentsInfoMap,
  RepoName,
  RevisionPatchSetNum,
  UrlEncodedCommentId,
} from '../../../types/common';
import {CursorMoveResult} from '../../../api/core';
import {Side} from '../../../api/diff';
import {Files, GrDiffView} from './gr-diff-view';
import {DropdownItem} from '../../shared/gr-dropdown-list/gr-dropdown-list';
import {SinonFakeTimers, SinonStub} from 'sinon';
import {
  changeModelToken,
  ChangeModel,
  LoadingStatus,
} from '../../../models/change/change-model';
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
import {
  ChangeViewModel,
  changeViewModelToken,
} from '../../../models/views/change';
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
    let navToChangeStub: SinonStub;
    let navToDiffStub: SinonStub;
    let navToEditStub: SinonStub;
    let changeModel: ChangeModel;
    let viewModel: ChangeViewModel;
    let commentsModel: CommentsModel;
    let browserModel: BrowserModel;
    let userModel: UserModel;

    function getFilesFromFileList(fileList: string[]): Files {
      const changeFilesByPath = fileList.reduce((files, path) => {
        files[path] = createFileInfo(path);
        return files;
      }, {} as FileNameToNormalizedFileInfoMap);
      return {
        sortedPaths: fileList,
        changeFilesByPath,
      };
    }

    setup(async () => {
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
      viewModel = testResolver(changeViewModelToken);
      viewModel.setState(createDiffViewState());
      await waitUntil(() => element.changeNum === TEST_NUMERIC_CHANGE_ID);
      element.path = 'some/path.txt';
      element.change = createParsedChange();
      element.diff = {...createDiff(), content: []};
      getDiffRestApiStub = stubRestApi('getDiff');
      // Delayed in case a test updates element.diff.
      getDiffRestApiStub.callsFake(() => Promise.resolve(element.diff));
      element.patchNum = 1 as RevisionPatchSetNum;
      element.basePatchNum = PARENT;
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
      navToChangeStub = sinon.stub(changeModel, 'navigateToChange');
      navToDiffStub = sinon.stub(changeModel, 'navigateToDiff');
      navToEditStub = sinon.stub(changeModel, 'navigateToEdit');

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

    test('toggle left diff with a hotkey', () => {
      assertIsDefined(element.diffHost);
      const toggleLeftDiffStub = sinon.stub(element.diffHost, 'toggleLeftDiff');
      pressKey(element, 'A');
      assert.isTrue(toggleLeftDiffStub.calledOnce);
    });

    test('renders', async () => {
      browserModel.setScreenWidth(0);
      element.patchNum = 10 as RevisionPatchSetNum;
      element.basePatchNum = PARENT;
      const change = {
        ...createParsedChange(),
        _number: 42 as NumericChangeId,
        revisions: {
          a: createRevision(10),
        },
      };
      changeModel.updateStateChange(change);
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
          <h2 class="assistive-tech-only">Diff view</h2>
          <gr-diff-host id="diffHost"> </gr-diff-host>
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
      element.patchNum = 10 as RevisionPatchSetNum;
      element.basePatchNum = PARENT;
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
      navToChangeStub.reset();

      pressKey(element, 'u');
      assert.isTrue(navToChangeStub.calledOnce);
      await element.updateComplete;

      pressKey(element, ']');
      assert.equal(navToDiffStub.callCount, 1);
      assert.deepEqual(navToDiffStub.lastCall.args, [
        {path: 'wheatley.md', lineNum: undefined},
      ]);

      element.path = 'wheatley.md';
      await element.updateComplete;

      pressKey(element, '[');
      assert.equal(navToDiffStub.callCount, 2);
      assert.deepEqual(navToDiffStub.lastCall.args, [
        {path: 'glados.txt', lineNum: undefined},
      ]);

      element.path = 'glados.txt';
      await element.updateComplete;

      pressKey(element, '[');
      assert.equal(navToDiffStub.callCount, 3);
      assert.deepEqual(navToDiffStub.lastCall.args, [
        {path: 'chell.go', lineNum: undefined},
      ]);

      element.path = 'chell.go';
      await element.updateComplete;

      pressKey(element, '[');
      assert.equal(navToChangeStub.callCount, 2);
      await element.updateComplete;

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
      element.patchNum = 10 as RevisionPatchSetNum;
      element.basePatchNum = PARENT;
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
      navToDiffStub.reset();

      pressKey(element, 'N');
      await element.updateComplete;
      assert.equal(navToDiffStub.callCount, 1);
      assert.deepEqual(navToDiffStub.lastCall.args, [
        {path: 'wheatley.md', lineNum: 21},
      ]);

      element.path = 'wheatley.md'; // navigated to next file

      pressKey(element, 'N');
      await element.updateComplete;

      assert.equal(navToChangeStub.callCount, 1);
    });

    test('shift+x shortcut toggles all diff context', async () => {
      assertIsDefined(element.diffHost);
      const toggleStub = sinon.stub(element.diffHost, 'toggleAllContext');
      pressKey(element, 'X');
      await element.updateComplete;
      assert.isTrue(toggleStub.called);
    });

    test('diff against base', async () => {
      element.patchNum = 10 as RevisionPatchSetNum;
      element.basePatchNum = 5 as BasePatchSetNum;
      await element.updateComplete;
      element.handleDiffAgainstBase();
      const expected = [{path: 'some/path.txt'}, 10, PARENT];
      assert.deepEqual(navToDiffStub.lastCall.args, expected);
    });

    test('diff against latest', async () => {
      element.path = 'foo';
      element.latestPatchNum = 12 as PatchSetNumber;
      element.patchNum = 10 as RevisionPatchSetNum;
      element.basePatchNum = 5 as BasePatchSetNum;
      await element.updateComplete;
      element.handleDiffAgainstLatest();
      const expected = [{path: 'foo'}, 12, 5];
      assert.deepEqual(navToDiffStub.lastCall.args, expected);
    });

    test('handleDiffBaseAgainstLeft', async () => {
      element.path = 'foo';
      element.latestPatchNum = 10 as PatchSetNumber;
      element.patchNum = 3 as RevisionPatchSetNum;
      element.basePatchNum = 1 as BasePatchSetNum;
      viewModel.setState({
        ...createDiffViewState(),
        patchNum: 3 as RevisionPatchSetNum,
        basePatchNum: 1 as BasePatchSetNum,
        diffView: {path: 'foo'},
      });
      await element.updateComplete;
      element.handleDiffBaseAgainstLeft();
      const expected = [{path: 'foo'}, 1, PARENT];
      assert.deepEqual(navToDiffStub.lastCall.args, expected);
    });

    test('handleDiffRightAgainstLatest', async () => {
      element.path = 'foo';
      element.latestPatchNum = 10 as PatchSetNumber;
      element.patchNum = 3 as RevisionPatchSetNum;
      element.basePatchNum = 1 as BasePatchSetNum;
      await element.updateComplete;
      element.handleDiffRightAgainstLatest();
      const expected = [{path: 'foo'}, 10, 3];
      assert.deepEqual(navToDiffStub.lastCall.args, expected);
    });

    test('handleDiffBaseAgainstLatest', async () => {
      element.latestPatchNum = 10 as PatchSetNumber;
      element.patchNum = 3 as RevisionPatchSetNum;
      element.basePatchNum = 1 as BasePatchSetNum;
      await element.updateComplete;
      element.handleDiffBaseAgainstLatest();
      const expected = [{path: 'some/path.txt'}, 10, PARENT];
      assert.deepEqual(navToDiffStub.lastCall.args, expected);
    });

    test('A fires an error event when not logged in', async () => {
      element.loggedIn = false;
      const loggedInErrorSpy = sinon.spy();
      element.addEventListener('show-auth-required', loggedInErrorSpy);
      pressKey(element, 'a');
      await element.updateComplete;
      assert.isFalse(navToDiffStub.calledOnce);
      assert.isTrue(loggedInErrorSpy.called);
    });

    test('A navigates to change with logged in', async () => {
      element.changeNum = 42 as NumericChangeId;
      element.patchNum = 10 as RevisionPatchSetNum;
      element.basePatchNum = 5 as BasePatchSetNum;
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
      navToDiffStub.reset();

      pressKey(element, 'a');

      await element.updateComplete;
      assert.isTrue(navToChangeStub.calledOnce);
      assert.deepEqual(navToChangeStub.lastCall.args, [true]);
      assert.isFalse(loggedInErrorSpy.called);
    });

    test('A navigates to change with old patch number with logged in', async () => {
      element.changeNum = 42 as NumericChangeId;
      element.patchNum = 1 as RevisionPatchSetNum;
      element.basePatchNum = PARENT;
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
      assert.isTrue(navToChangeStub.calledOnce);
      assert.deepEqual(navToChangeStub.lastCall.args, [true]);
      assert.isFalse(loggedInErrorSpy.called);
    });

    test('keyboard shortcuts with patch range', () => {
      element.changeNum = 42 as NumericChangeId;
      element.patchNum = 10 as RevisionPatchSetNum;
      element.basePatchNum = 5 as BasePatchSetNum;
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
      assert.equal(navToChangeStub.callCount, 1);

      pressKey(element, ']');
      assert.equal(navToDiffStub.callCount, 1);
      assert.deepEqual(navToDiffStub.lastCall.args, [
        {path: 'wheatley.md', lineNum: undefined},
      ]);
      element.path = 'wheatley.md';

      pressKey(element, '[');
      assert.equal(navToDiffStub.callCount, 2);
      assert.deepEqual(navToDiffStub.lastCall.args, [
        {path: 'glados.txt', lineNum: undefined},
      ]);
      element.path = 'glados.txt';

      pressKey(element, '[');
      assert.equal(navToDiffStub.callCount, 3);
      assert.deepEqual(navToDiffStub.lastCall.args, [
        {path: 'chell.go', lineNum: undefined},
      ]);
      element.path = 'chell.go';

      pressKey(element, '[');
      assert.equal(navToChangeStub.callCount, 2);

      assertIsDefined(element.downloadModal);
      const downloadModalStub = sinon.stub(element.downloadModal, 'showModal');
      pressKey(element, 'd');
      assert.isTrue(downloadModalStub.called);
    });

    test('keyboard shortcuts with old patch number', async () => {
      element.changeNum = 42 as NumericChangeId;
      element.patchNum = 1 as RevisionPatchSetNum;
      element.basePatchNum = PARENT;
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
      assert.isTrue(navToChangeStub.calledOnce);

      pressKey(element, ']');
      assert.deepEqual(navToDiffStub.lastCall.args, [
        {path: 'wheatley.md', lineNum: undefined},
      ]);
      element.path = 'wheatley.md';

      pressKey(element, '[');
      assert.deepEqual(navToDiffStub.lastCall.args, [
        {path: 'glados.txt', lineNum: undefined},
      ]);
      element.path = 'glados.txt';

      pressKey(element, '[');
      assert.deepEqual(navToDiffStub.lastCall.args, [
        {path: 'chell.go', lineNum: undefined},
      ]);

      element.path = 'chell.go';
      await element.updateComplete;
      navToDiffStub.reset();
      pressKey(element, '[');
      assert.equal(navToChangeStub.callCount, 2);
    });

    test('reloadDiff is called when patchNum changes', async () => {
      const reloadStub = sinon.stub(element, 'reloadDiff');
      element.patchNum = 5 as RevisionPatchSetNum;
      await element.updateComplete;
      assert.isTrue(reloadStub.called);
    });

    test('initializePositions is called when view becomes active', async () => {
      const reloadStub = sinon.stub(element, 'reloadDiff');
      const initializeStub = sinon.stub(element, 'initializePositions');

      element.isActiveChildView = false;
      await element.updateComplete;
      element.isActiveChildView = true;
      await element.updateComplete;

      assert.isTrue(initializeStub.calledOnce);
      assert.isFalse(reloadStub.called);
    });

    test('edit should redirect to edit page', async () => {
      element.loggedIn = true;
      element.path = 't.txt';
      element.patchNum = 1 as RevisionPatchSetNum;
      element.basePatchNum = PARENT;
      await element.updateComplete;
      const editBtn = queryAndAssert<GrButton>(
        element,
        '.editButton gr-button'
      );
      assert.isTrue(!!editBtn);
      editBtn.click();
      assert.equal(navToEditStub.callCount, 1);
      assert.deepEqual(navToEditStub.lastCall.args, [
        {path: 't.txt', lineNum: undefined},
      ]);
    });

    test('edit should redirect to edit page with line number', async () => {
      const lineNumber = 42;
      element.loggedIn = true;
      element.path = 't.txt';
      element.patchNum = 1 as RevisionPatchSetNum;
      element.basePatchNum = PARENT;
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
      assert.equal(navToEditStub.callCount, 1);
      assert.deepEqual(navToEditStub.lastCall.args, [
        {path: 't.txt', lineNum: 42},
      ]);
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
      element.patchNum = 1 as RevisionPatchSetNum;
      element.basePatchNum = PARENT;
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
        element.patchNum = 10 as RevisionPatchSetNum;
        element.basePatchNum = PARENT;
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
        viewModel.setState({
          ...createDiffViewState(),
        });
        const change = {
          ...createParsedChange(),
          _number: 42 as NumericChangeId,
          revisions: {
            a: createRevision(10),
          },
        };
        changeModel.updateStateChange(change);
        await element.updateComplete;

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
        viewModel.setState({
          ...createDiffViewState(),
          basePatchNum: 5 as BasePatchSetNum,
          patchNum: 10 as RevisionPatchSetNum,
          diffView: {path: 'glados.txt'},
        });
        const change = {
          ...createParsedChange(),
          _number: 42 as NumericChangeId,
          revisions: {
            a: createRevision(5),
            b: createRevision(10),
            c: createRevision(12),
          },
        };
        changeModel.updateStateChange(change);
        element.files = getFilesFromFileList([
          'chell.go',
          'glados.txt',
          'wheatley.md',
        ]);
        await waitUntil(() => element.path === 'glados.txt');
        await waitUntil(() => element.patchRange?.patchNum === 10);

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

        viewModel.updateState({diffView: {path: 'wheatley.md'}});
        await waitUntil(() => element.path === 'wheatley.md');

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

        viewModel.updateState({diffView: {path: 'chell.go'}});
        await waitUntil(() => element.path === 'chell.go');

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
      element.path = 'path/to/file.txt';
      element.patchNum = 3 as RevisionPatchSetNum;
      element.basePatchNum = PARENT;
      await element.updateComplete;

      const detail = {
        basePatchNum: PARENT,
        patchNum: 1 as RevisionPatchSetNum,
      };
      queryAndAssert(element, '#rangeSelect').dispatchEvent(
        new CustomEvent('patch-range-change', {detail, bubbles: false})
      );

      assert.deepEqual(navToDiffStub.lastCall.args, [
        {path: element.path},
        detail.patchNum,
        detail.basePatchNum,
      ]);
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
        viewModel.updateState({diffView: {path: 'wheatley.md'}});
        changeModel.setState({
          change: createParsedChange(),
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
      viewModel.updateState({diffView: {path: 'wheatley.md'}});
      changeModel.setState({
        change: createParsedChange(),
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
      userModel.setDiffPreferences(createDefaultDiffPrefs());
      viewModel.updateState({
        patchNum: 1 as RevisionPatchSetNum,
        basePatchNum: PARENT,
        diffView: {path: '/COMMIT_MSG'},
      });
      changeModel.setState({
        change: createParsedChange(),
        reviewedFiles: [],
        loadingStatus: LoadingStatus.LOADED,
      });
      element.loggedIn = true;
      await waitUntil(() => element.patchRange?.patchNum === 1);
      await element.updateComplete;
      assertIsDefined(element.diffHost);
      sinon.stub(element.diffHost, 'reload');

      await waitUntil(() => saveReviewedStub.called);

      changeModel.updateStateFileReviewed('/COMMIT_MSG', true);
      await element.updateComplete;

      const reviewedStatusCheckBox = queryAndAssert<HTMLInputElement>(
        element,
        'input#reviewed'
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

      viewModel.setState({
        ...createDiffViewState(),
        repo: 'test' as RepoName,
      });
      await element.updateComplete;

      // saveReviewedState observer observes viewState, but should not fire when
      // view !== GerritView.DIFF.
      assert.equal(saveReviewedStub.callCount, callCount);
    });

    test('do not set file review status for EDIT patchset', async () => {
      const saveReviewedStub = sinon.stub(
        changeModel,
        'setReviewedFilesStatus'
      );

      element.patchNum = EDIT;
      element.basePatchNum = 1 as BasePatchSetNum;
      await waitEventLoop();

      element.setReviewed(true);

      assert.isFalse(saveReviewedStub.called);
    });

    test('hash is determined from viewState', async () => {
      assertIsDefined(element.diffHost);
      sinon.stub(element.diffHost, 'reload');
      const initLineStub = sinon.stub(element, 'initCursor');

      element.focusLineNum = 123;

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

    test('initCursor', () => {
      assertIsDefined(element.cursor);
      assert.isNotOk(element.cursor.initialLineNumber);

      // Does nothing when viewState specify no cursor address:
      element.leftSide = false;
      element.initCursor();
      assert.isNotOk(element.cursor.initialLineNumber);

      // Does nothing when viewState specify side but no number:
      element.leftSide = true;
      element.initCursor();
      assert.isNotOk(element.cursor.initialLineNumber);

      // Revision hash: specifies lineNum but not side.

      element.focusLineNum = 234;
      element.leftSide = false;
      element.initCursor();
      assert.equal(element.cursor.initialLineNumber, 234);
      assert.equal(element.cursor.side, Side.RIGHT);

      // Base hash: specifies lineNum and side.
      element.focusLineNum = 345;
      element.leftSide = true;
      element.initCursor();
      assert.equal(element.cursor.initialLineNumber, 345);
      assert.equal(element.cursor.side, Side.LEFT);

      // Specifies right side:
      element.focusLineNum = 123;
      element.leftSide = false;
      element.initCursor();
      assert.equal(element.cursor.initialLineNumber, 123);
      assert.equal(element.cursor.side, Side.RIGHT);
    });

    test('getLineOfInterest', () => {
      element.leftSide = false;
      assert.isUndefined(element.getLineOfInterest());

      element.focusLineNum = 12;
      element.leftSide = false;
      let result = element.getLineOfInterest();
      assert.isOk(result);
      assert.equal(result!.lineNum, 12);
      assert.equal(result!.side, Side.RIGHT);

      element.leftSide = true;
      result = element.getLineOfInterest();
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
      element.patchNum = 5 as RevisionPatchSetNum;
      element.basePatchNum = 3 as BasePatchSetNum;
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
      element.patchNum = 5 as RevisionPatchSetNum;
      element.basePatchNum = 3 as BasePatchSetNum;
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

    suite('findFileWithComment', () => {
      test('empty file list', () => {
        element.changeComments = new ChangeComments({
          'path/one.jpg': [createComment('c1', 1, 1, 'path/one.jpg')],
          'path/three.wav': [createComment('c1', 1, 1, 'path/three.wav')],
        });
        element.path = 'path/two.m4v';
        assert.isUndefined(element.findFileWithComment(-1));
        assert.isUndefined(element.findFileWithComment(1));
      });

      test('finds skips', () => {
        const fileList = ['path/one.jpg', 'path/two.m4v', 'path/three.wav'];
        element.files = {sortedPaths: fileList, changeFilesByPath: {}};
        element.path = fileList[1];
        element.changeComments = new ChangeComments({
          'path/one.jpg': [createComment('c1', 1, 1, 'path/one.jpg')],
          'path/three.wav': [createComment('c1', 1, 1, 'path/three.wav')],
        });

        assert.equal(element.findFileWithComment(-1), fileList[0]);
        assert.equal(element.findFileWithComment(1), fileList[2]);

        element.changeComments = new ChangeComments({
          'path/one.jpg': [createComment('c1', 1, 1, 'path/one.jpg')],
          'path/two.m4v': [createComment('c1', 1, 1, 'path/two.m4v')],
          'path/three.wav': [createComment('c1', 1, 1, 'path/three.wav')],
        });

        assert.equal(element.findFileWithComment(-1), fileList[0]);
        assert.equal(element.findFileWithComment(1), fileList[2]);

        element.path = fileList[0];

        assert.isUndefined(element.findFileWithComment(-1));
        assert.equal(element.findFileWithComment(1), fileList[1]);

        element.path = fileList[2];

        assert.equal(element.findFileWithComment(-1), fileList[1]);
        assert.isUndefined(element.findFileWithComment(1));
      });

      suite('skip next/previous', () => {
        setup(() => {
          element.files = getFilesFromFileList([
            'path/one.jpg',
            'path/two.m4v',
            'path/three.wav',
          ]);
          element.patchNum = 2 as RevisionPatchSetNum;
          element.basePatchNum = 1 as BasePatchSetNum;
        });

        suite('moveToFileWithComment previous', () => {
          test('no previous', async () => {
            element.changeComments = new ChangeComments({
              'path/three.wav': [createComment('c1', 1, 1, 'path/three.wav')],
            });
            element.path = element.files.sortedPaths[1];
            await element.updateComplete;

            element.moveToFileWithComment(-1);
            assert.isTrue(navToChangeStub.calledOnce);
            assert.isFalse(navToDiffStub.called);
          });

          test('w/ previous', async () => {
            element.changeComments = new ChangeComments({
              'path/one.jpg': [createComment('c1', 1, 1, 'path/one.jpg')],
              'path/three.wav': [createComment('c1', 1, 1, 'path/three.wav')],
            });
            element.path = element.files.sortedPaths[1];
            await element.updateComplete;

            element.moveToFileWithComment(-1);
            assert.isFalse(navToChangeStub.called);
            assert.isTrue(navToDiffStub.calledOnce);
          });
        });

        suite('moveToFileWithComment next', () => {
          test('no previous', async () => {
            element.changeComments = new ChangeComments({
              'path/one.jpg': [createComment('c1', 1, 1, 'path/one.jpg')],
            });
            element.path = element.files.sortedPaths[1];
            await element.updateComplete;

            element.moveToFileWithComment(1);
            assert.isTrue(navToChangeStub.calledOnce);
            assert.isFalse(navToDiffStub.called);
          });

          test('w/ previous', async () => {
            element.changeComments = new ChangeComments({
              'path/one.jpg': [createComment('c1', 1, 1, 'path/one.jpg')],
              'path/three.wav': [createComment('c1', 1, 1, 'path/three.wav')],
            });
            element.path = element.files.sortedPaths[1];
            await element.updateComplete;

            element.moveToFileWithComment(1);
            assert.isFalse(navToChangeStub.called);
            assert.isTrue(navToDiffStub.calledOnce);
          });
        });
      });
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
        element.patchNum = 1 as RevisionPatchSetNum;
        element.basePatchNum = PARENT;
        await element.updateComplete;

        let checkbox = queryAndAssert(element, '#reviewed');
        assert.isTrue(isVisible(checkbox));

        element.patchNum = EDIT;
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
      sinon.stub(element, 'initCursor');

      // Load file1
      viewModel.setState({
        ...createDiffViewState(),
        patchNum: 1 as RevisionPatchSetNum,
        repo: 'test-project' as RepoName,
        diffView: {path: 'file1'},
      });
      element.patchNum = 1 as RevisionPatchSetNum;
      element.basePatchNum = PARENT;
      element.change = {
        ...createParsedChange(),
        revisions: createRevisions(1),
      };
      await element.updateComplete;
      assert.isFalse(navToDiffStub.called);

      // Switch to file2
      element.handleFileChange(
        new CustomEvent('value-change', {detail: {value: 'file2'}})
      );
      assert.isTrue(navToDiffStub.calledOnce);
      assert.deepEqual(navToDiffStub.lastCall.firstArg, {path: 'file2'});

      // This is to mock the param change triggered by above navigate
      viewModel.setState({
        ...createDiffViewState(),
        patchNum: 1 as RevisionPatchSetNum,
        repo: 'test-project' as RepoName,
        diffView: {path: 'file2'},
      });
      element.patchNum = 1 as RevisionPatchSetNum;
      element.basePatchNum = PARENT;

      // No extra call
      assert.isTrue(navToDiffStub.calledOnce);
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
      element.patchNum = 1 as RevisionPatchSetNum;
      element.basePatchNum = PARENT;
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
      element.patchNum = 3 as RevisionPatchSetNum;
      element.basePatchNum = 2 as BasePatchSetNum;
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
