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
  pressKey,
  query,
  queryAll,
  queryAndAssert,
  stubReporting,
  stubRestApi,
  stubUsers,
  waitEventLoop,
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
  EDIT,
  FileInfo,
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
import {LoadingStatus} from '../../../models/change/change-model';
import {CommentMap} from '../../../utils/comment-util';
import {ParsedChangeInfo} from '../../../types/types';
import {assertIsDefined} from '../../../utils/common-util';
import {GrDiffModeSelector} from '../../../embed/diff/gr-diff-mode-selector/gr-diff-mode-selector';
import {fixture, html, assert} from '@open-wc/testing';
import {EventType} from '../../../types/events';
import {Key} from '../../../utils/dom-util';
import {GrButton} from '../../shared/gr-button/gr-button';

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
      assertIsDefined(element.diffHost);
      sinon.stub(element.diffHost, 'reload').returns(Promise.resolve());
      sinon.stub(element, 'initPatchRange');
      sinon.stub(element, 'fetchFiles');
      const paramsChangedSpy = sinon.spy(element, 'paramsChanged');
      element.params = {
        view: GerritView.DIFF,
        changeNum: 42 as NumericChangeId,
        patchNum: 2 as RevisionPatchSetNum,
        basePatchNum: 1 as BasePatchSetNum,
        path: '/COMMIT_MSG',
      };
      element.path = '/COMMIT_MSG';
      element.patchRange = createPatchRange();
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
          'initLineOfInterestAndCursor'
        );
        getUrlStub = sinon.stub(GerritNav, 'getUrlForDiffById');
        replaceStateStub = sinon.stub(history, 'replaceState');
        sinon.stub(element, 'fetchFiles');
        stubReporting('diffViewDisplayed');
        assertIsDefined(element.diffHost);
        sinon.stub(element.diffHost, 'reload').returns(Promise.resolve());
        paramsChangedSpy = sinon.spy(element, 'paramsChanged');
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
          view: GerritView.DIFF,
          changeNum: 42 as NumericChangeId,
          commentLink: true,
          commentId: 'c1' as UrlEncodedCommentId,
          path: 'abcd',
          patchNum: 1 as RevisionPatchSetNum,
        };
        element.change = {
          ...createParsedChange(),
          revisions: createRevisions(11),
        };
        return paramsChangedSpy.returnValues[0].then(() => {
          assert.isTrue(
            initLineOfInterestAndCursorStub.calledWithExactly(true)
          );
          assert.equal(element.focusLineNum, 10);
          assert.equal(element.patchRange?.patchNum, 11 as RevisionPatchSetNum);
          assert.equal(element.patchRange?.basePatchNum, 2 as BasePatchSetNum);
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
      element.isBlameLoaded = true;
      stubReporting('diffViewDisplayed');
      const loadBlameStub = sinon.stub(element, 'loadBlame');
      assertIsDefined(element.diffHost);
      sinon.stub(element.diffHost, 'reload').returns(Promise.resolve());
      const paramsChangedSpy = sinon.spy(element, 'paramsChanged');
      sinon.stub(element, 'initPatchRange');
      sinon.stub(element, 'fetchFiles');
      element.params = {
        view: GerritView.DIFF,
        changeNum: 42 as NumericChangeId,
        patchNum: 2 as RevisionPatchSetNum,
        basePatchNum: 1 as BasePatchSetNum,
        path: '/COMMIT_MSG',
      };
      element.path = '/COMMIT_MSG';
      element.patchRange = createPatchRange();
      return paramsChangedSpy.returnValues[0]!.then(() => {
        assert.isTrue(element.isBlameLoaded);
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
      sinon.stub(element, 'loadBlame');
      assertIsDefined(element.diffHost);
      sinon.stub(element.diffHost, 'reload').returns(Promise.resolve());
      sinon.stub(element, 'isFileUnchanged').returns(true);
      const paramsChangedSpy = sinon.spy(element, 'paramsChanged');
      element.getChangeModel().setState({
        change: {
          ...createParsedChange(),
          revisions: createRevisions(11),
        },
        loadingStatus: LoadingStatus.LOADED,
      });
      element.params = {
        view: GerritView.DIFF,
        changeNum: 42 as NumericChangeId,
        path: '/COMMIT_MSG',
        commentLink: true,
        commentId: 'c1' as UrlEncodedCommentId,
      };
      element.change = {
        ...createParsedChange(),
        revisions: createRevisions(11),
      };
      return paramsChangedSpy.returnValues[0]?.then(() => {
        assert.isTrue(
          diffNavStub.lastCall.calledWithExactly(
            element.change!,
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
      sinon.stub(element, 'loadBlame');
      assertIsDefined(element.diffHost);
      sinon.stub(element.diffHost, 'reload').returns(Promise.resolve());
      sinon.stub(element, 'isFileUnchanged').returns(true);
      const paramsChangedSpy = sinon.spy(element, 'paramsChanged');
      element.getChangeModel().setState({
        change: {
          ...createParsedChange(),
          revisions: createRevisions(11),
        },
        loadingStatus: LoadingStatus.LOADED,
      });
      element.params = {
        view: GerritView.DIFF,
        changeNum: 42 as NumericChangeId,
        path: '/COMMIT_MSG',
        commentLink: true,
        commentId: 'c3' as UrlEncodedCommentId,
      };
      element.change = {
        ...createParsedChange(),
        revisions: createRevisions(11),
      };
      return paramsChangedSpy.returnValues[0]!.then(() => {
        assert.isFalse(diffNavStub.called);
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
      sinon.stub(element, 'loadBlame');
      assertIsDefined(element.diffHost);
      sinon.stub(element.diffHost, 'reload').returns(Promise.resolve());
      const paramsChangedSpy = sinon.spy(element, 'paramsChanged');
      element.change = undefined;
      element.getChangeModel().setState({
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
      element.params = {
        view: GerritView.DIFF,
        changeNum: 42 as NumericChangeId,
        project: 'p' as RepoName,
        commentId: 'c1' as UrlEncodedCommentId,
        commentLink: true,
      };
      await paramsChangedSpy.returnValues[0];
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
      element.getBrowserModel().setScreenWidth(0);
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
                <a href=""> 42 </a>
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
                  href=""
                  title="Go to previous file (shortcut: [)"
                >
                  Prev
                </a>
                <span class="separator"> </span>
                <a class="navLink" href="" title="Up to change (shortcut: u)">
                  Up
                </a>
                <span class="separator"> </span>
                <a
                  class="navLink"
                  href=""
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
              <a class="mobileNavLink" href=""> < </a>
              <div class="fullFileName mobile">glados.txt</div>
              <a class="mobileNavLink" href=""> > </a>
            </div>
          </div>
          <div class="loading">Loading...</div>
          <h2 class="assistive-tech-only">Diff view</h2>
          <gr-diff-host hidden="" id="diffHost"> </gr-diff-host>
          <gr-apply-fix-dialog id="applyFixDialog"> </gr-apply-fix-dialog>
          <gr-diff-preferences-dialog id="diffPreferencesDialog">
          </gr-diff-preferences-dialog>
          <gr-overlay
            aria-hidden="true"
            id="downloadOverlay"
            style="outline: none; display: none;"
          >
            <gr-download-dialog id="downloadDialog" role="dialog">
            </gr-download-dialog>
          </gr-overlay>
        `
      );
    });

    test('keyboard shortcuts', async () => {
      clock = sinon.useFakeTimers();
      element.changeNum = 42 as NumericChangeId;
      element.getBrowserModel().setScreenWidth(0);
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

      const diffNavStub = sinon.stub(GerritNav, 'navigateToDiff');
      const changeNavStub = sinon.stub(GerritNav, 'navigateToChange');

      pressKey(element, 'u');
      assert(
        changeNavStub.lastCall.calledWith(element.change),
        'Should navigate to /c/42/'
      );
      await element.updateComplete;

      pressKey(element, ']');
      assert(
        diffNavStub.lastCall.calledWith(
          element.change,
          'wheatley.md',
          10 as RevisionPatchSetNum,
          PARENT
        ),
        'Should navigate to /c/42/10/wheatley.md'
      );
      element.path = 'wheatley.md';
      await element.updateComplete;

      assert.isTrue(element.loading);

      pressKey(element, '[');
      assert(
        diffNavStub.lastCall.calledWith(
          element.change,
          'glados.txt',
          10 as RevisionPatchSetNum,
          PARENT
        ),
        'Should navigate to /c/42/10/glados.txt'
      );
      element.path = 'glados.txt';
      await element.updateComplete;

      assert.isTrue(element.loading);

      pressKey(element, '[');
      assert(
        diffNavStub.lastCall.calledWith(
          element.change,
          'chell.go',
          10 as RevisionPatchSetNum,
          PARENT
        ),
        'Should navigate to /c/42/10/chell.go'
      );
      element.path = 'chell.go';
      await element.updateComplete;

      assert.isTrue(element.loading);

      pressKey(element, '[');
      assert(
        changeNavStub.lastCall.calledWith(element.change),
        'Should navigate to /c/42/'
      );
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

      // Note that stubbing setReviewed means that the value of the
      // `element.reviewed` checkbox is not flipped.
      const setReviewedStub = sinon.stub(element, 'setReviewed');
      const handleToggleSpy = sinon.spy(element, 'handleToggleFileReviewed');
      assertIsDefined(element.reviewed);
      element.reviewed.checked = false;
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
      const diffNavStub = sinon.stub(GerritNav, 'navigateToDiff');
      const diffChangeStub = sinon.stub(element, 'navigateToChange');
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

      pressKey(element, 'N');
      await element.updateComplete;
      assert.isTrue(
        diffNavStub.calledWithExactly(
          element.change,
          'wheatley.md',
          10 as RevisionPatchSetNum,
          PARENT,
          21
        )
      );

      element.path = 'wheatley.md'; // navigated to next file

      pressKey(element, 'N');
      await element.updateComplete;

      assert.isTrue(diffChangeStub.called);
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
      const diffNavStub = sinon.stub(GerritNav, 'navigateToDiff');
      element.handleDiffAgainstBase();
      const args = diffNavStub.getCall(0).args;
      assert.equal(args[2], 10 as RevisionPatchSetNum);
      assert.isNotOk(args[3]);
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
      const diffNavStub = sinon.stub(GerritNav, 'navigateToDiff');
      element.handleDiffAgainstLatest();
      const args = diffNavStub.getCall(0).args;
      assert.equal(args[2], 12 as RevisionPatchSetNum);
      assert.equal(args[3], 5 as BasePatchSetNum);
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
      element.params = {
        view: GerritView.DASHBOARD,
        dashboard: 'id' as DashboardId,
      };
      await element.updateComplete;
      const diffNavStub = sinon.stub(GerritNav, 'navigateToDiff');
      element.handleDiffBaseAgainstLeft();
      assert(diffNavStub.called);
      const args = diffNavStub.getCall(0).args;
      assert.equal(args[2], 1 as RevisionPatchSetNum);
      assert.equal(args[3], PARENT);
      assert.isNotOk(args[4]);
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
      sinon.stub(element, 'paramsChanged');
      element.params = {
        commentLink: true,
        view: GerritView.DIFF,
        changeNum: 42 as NumericChangeId,
      };
      element.focusLineNum = 10;
      const diffNavStub = sinon.stub(GerritNav, 'navigateToDiff');
      element.handleDiffBaseAgainstLeft();
      assert(diffNavStub.called);
      const args = diffNavStub.getCall(0).args;
      assert.equal(args[2], 1 as RevisionPatchSetNum);
      assert.equal(args[3], PARENT);
      assert.equal(args[4], 10);
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
      const diffNavStub = sinon.stub(GerritNav, 'navigateToDiff');
      element.handleDiffRightAgainstLatest();
      assert(diffNavStub.called);
      const args = diffNavStub.getCall(0).args;
      assert.equal(args[2], 10 as RevisionPatchSetNum);
      assert.equal(args[3], 3 as BasePatchSetNum);
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
      const diffNavStub = sinon.stub(GerritNav, 'navigateToDiff');
      element.handleDiffBaseAgainstLatest();
      assert(diffNavStub.called);
      const args = diffNavStub.getCall(0).args;
      assert.equal(args[2], 10 as RevisionPatchSetNum);
      assert.isNotOk(args[3]);
    });

    test('A fires an error event when not logged in', async () => {
      const changeNavStub = sinon.stub(GerritNav, 'navigateToChange');
      element.loggedIn = false;
      const loggedInErrorSpy = sinon.spy();
      element.addEventListener('show-auth-required', loggedInErrorSpy);
      pressKey(element, 'a');
      await element.updateComplete;
      assert.isTrue(
        changeNavStub.notCalled,
        'The `a` keyboard shortcut ' +
          'should only work when the user is logged in.'
      );
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
      const changeNavStub = sinon.stub(GerritNav, 'navigateToChange');
      element.loggedIn = true;
      const loggedInErrorSpy = sinon.spy();
      element.addEventListener('show-auth-required', loggedInErrorSpy);
      pressKey(element, 'a');
      await element.updateComplete;
      assert(
        changeNavStub.lastCall.calledWithExactly(element.change, {
          patchNum: 10 as RevisionPatchSetNum,
          basePatchNum: 5 as BasePatchSetNum,
          openReplyDialog: true,
        }),
        'Should navigate to /c/42/5..10'
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
      const changeNavStub = sinon.stub(GerritNav, 'navigateToChange');
      element.loggedIn = true;
      const loggedInErrorSpy = sinon.spy();
      element.addEventListener('show-auth-required', loggedInErrorSpy);
      pressKey(element, 'a');
      await element.updateComplete;
      assert(
        changeNavStub.lastCall.calledWithExactly(element.change, {
          patchNum: 1 as RevisionPatchSetNum,
          basePatchNum: PARENT,
          openReplyDialog: true,
        }),
        'Should navigate to /c/42/1'
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

      const diffNavStub = sinon.stub(GerritNav, 'navigateToDiff');
      const changeNavStub = sinon.stub(GerritNav, 'navigateToChange');

      pressKey(element, 'u');
      assert(
        changeNavStub.lastCall.calledWithExactly(element.change, {
          patchNum: 10 as RevisionPatchSetNum,
          basePatchNum: 5 as BasePatchSetNum,
          openReplyDialog: false,
        }),
        'Should navigate to /c/42/5..10'
      );

      pressKey(element, ']');
      assert.isTrue(element.loading);
      assert(
        diffNavStub.lastCall.calledWithExactly(
          element.change,
          'wheatley.md',
          10 as RevisionPatchSetNum,
          5 as BasePatchSetNum,
          undefined
        ),
        'Should navigate to /c/42/5..10/wheatley.md'
      );
      element.path = 'wheatley.md';

      pressKey(element, '[');
      assert.isTrue(element.loading);
      assert(
        diffNavStub.lastCall.calledWithExactly(
          element.change,
          'glados.txt',
          10 as RevisionPatchSetNum,
          5 as BasePatchSetNum,
          undefined
        ),
        'Should navigate to /c/42/5..10/glados.txt'
      );
      element.path = 'glados.txt';

      pressKey(element, '[');
      assert.isTrue(element.loading);
      assert(
        diffNavStub.lastCall.calledWithExactly(
          element.change,
          'chell.go',
          10 as RevisionPatchSetNum,
          5 as BasePatchSetNum,
          undefined
        ),
        'Should navigate to /c/42/5..10/chell.go'
      );
      element.path = 'chell.go';

      pressKey(element, '[');
      assert.isTrue(element.loading);
      assert(
        changeNavStub.lastCall.calledWithExactly(element.change, {
          patchNum: 10 as RevisionPatchSetNum,
          basePatchNum: 5 as BasePatchSetNum,
          openReplyDialog: false,
        }),
        'Should navigate to /c/42/5..10'
      );

      assertIsDefined(element.downloadOverlay);
      const downloadOverlayStub = sinon
        .stub(element.downloadOverlay, 'open')
        .returns(Promise.resolve());
      pressKey(element, 'd');
      assert.isTrue(downloadOverlayStub.called);
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

      const diffNavStub = sinon.stub(GerritNav, 'navigateToDiff');
      const changeNavStub = sinon.stub(GerritNav, 'navigateToChange');

      pressKey(element, 'u');
      assert(
        changeNavStub.lastCall.calledWithExactly(element.change, {
          patchNum: 1 as RevisionPatchSetNum,
          basePatchNum: PARENT,
          openReplyDialog: false,
        }),
        'Should navigate to /c/42/1'
      );

      pressKey(element, ']');
      assert(
        diffNavStub.lastCall.calledWithExactly(
          element.change,
          'wheatley.md',
          1 as RevisionPatchSetNum,
          PARENT,
          undefined
        ),
        'Should navigate to /c/42/1/wheatley.md'
      );
      element.path = 'wheatley.md';

      pressKey(element, '[');
      assert(
        diffNavStub.lastCall.calledWithExactly(
          element.change,
          'glados.txt',
          1 as RevisionPatchSetNum,
          PARENT,
          undefined
        ),
        'Should navigate to /c/42/1/glados.txt'
      );
      element.path = 'glados.txt';

      pressKey(element, '[');
      assert(
        diffNavStub.lastCall.calledWithExactly(
          element.change,
          'chell.go',
          1 as RevisionPatchSetNum,
          PARENT,
          undefined
        ),
        'Should navigate to /c/42/1/chell.go'
      );
      element.path = 'chell.go';

      changeNavStub.reset();
      pressKey(element, '[');
      assert(
        changeNavStub.lastCall.calledWithExactly(element.change, {
          patchNum: 1 as RevisionPatchSetNum,
          basePatchNum: PARENT,
          openReplyDialog: false,
        }),
        'Should navigate to /c/42/1'
      );
      assert.isTrue(changeNavStub.calledOnce);
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
      const redirectStub = sinon.stub(GerritNav, 'navigateToRelativeUrl');
      await element.updateComplete;
      const editBtn = queryAndAssert<GrButton>(
        element,
        '.editButton gr-button'
      );
      assert.isTrue(!!editBtn);
      editBtn.click();
      assert.isTrue(redirectStub.called);
      assert.isTrue(
        redirectStub.lastCall.calledWithExactly(
          GerritNav.getEditUrlForDiff(
            element.change,
            element.path,
            element.patchRange.patchNum
          )
        )
      );
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
      const redirectStub = sinon.stub(GerritNav, 'navigateToRelativeUrl');
      await element.updateComplete;
      const editBtn = queryAndAssert<GrButton>(
        element,
        '.editButton gr-button'
      );
      assert.isTrue(!!editBtn);
      editBtn.click();
      assert.isTrue(redirectStub.called);
      assert.isTrue(
        redirectStub.lastCall.calledWithExactly(
          GerritNav.getEditUrlForDiff(
            element.change,
            element.path,
            element.patchRange.patchNum,
            lineNumber
          )
        )
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

    suite('url params', () => {
      setup(() => {
        sinon.stub(element, 'fetchFiles');
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
        assert.equal(linkEls[0].getAttribute('href'), '42-chell.go-10-PARENT');
        assert.equal(linkEls[1].getAttribute('href'), '42-undefined-undefined');
        assert.equal(
          linkEls[2].getAttribute('href'),
          '42-wheatley.md-10-PARENT'
        );
        element.path = 'wheatley.md';
        await element.updateComplete;
        assert.equal(
          linkEls[0].getAttribute('href'),
          '42-glados.txt-10-PARENT'
        );
        assert.equal(linkEls[1].getAttribute('href'), '42-undefined-undefined');
        assert.equal(linkEls[2].getAttribute('href'), '42-undefined-undefined');
        element.path = 'chell.go';
        await element.updateComplete;
        assert.equal(linkEls[0].getAttribute('href'), '42-undefined-undefined');
        assert.equal(linkEls[1].getAttribute('href'), '42-undefined-undefined');
        assert.equal(
          linkEls[2].getAttribute('href'),
          '42-glados.txt-10-PARENT'
        );
        element.path = 'not_a_real_file';
        await element.updateComplete;
        assert.equal(
          linkEls[0].getAttribute('href'),
          '42-wheatley.md-10-PARENT'
        );
        assert.equal(linkEls[1].getAttribute('href'), '42-undefined-undefined');
        assert.equal(linkEls[2].getAttribute('href'), '42-chell.go-10-PARENT');
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
        assert.equal(linkEls[0].getAttribute('href'), '42-chell.go-10-5');
        assert.equal(linkEls[1].getAttribute('href'), '42-10-5');
        assert.equal(linkEls[2].getAttribute('href'), '42-wheatley.md-10-5');
        element.path = 'wheatley.md';
        await element.updateComplete;
        assert.equal(linkEls[0].getAttribute('href'), '42-glados.txt-10-5');
        assert.equal(linkEls[1].getAttribute('href'), '42-10-5');
        assert.equal(linkEls[2].getAttribute('href'), '42-10-5');
        element.path = 'chell.go';
        await element.updateComplete;
        assert.equal(linkEls[0].getAttribute('href'), '42-10-5');
        assert.equal(linkEls[1].getAttribute('href'), '42-10-5');
        assert.equal(linkEls[2].getAttribute('href'), '42-glados.txt-10-5');
      });
    });

    test('handlePatchChange calls navigateToDiff correctly', async () => {
      const navigateStub = sinon.stub(GerritNav, 'navigateToDiff');
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

      assert(
        navigateStub.lastCall.calledWithExactly(
          element.change,
          element.path,
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

        assertIsDefined(element.diffHost);
        sinon.stub(element.diffHost, 'reload');
        element.loggedIn = true;
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
        element.patchRange = {
          patchNum: 2 as RevisionPatchSetNum,
          basePatchNum: 1 as BasePatchSetNum,
        };

        await waitUntil(() => setReviewedStatusStub.called);

        assert.isFalse(setReviewedFileStatusStub.called);

        // if prefs are updated then the reviewed status should not be set again
        element.userModel.setDiffPreferences(createDefaultDiffPrefs());

        await element.updateComplete;
        assert.isFalse(setReviewedFileStatusStub.called);
      }
    );

    test('_prefs.manual_review false means set reviewed is called', async () => {
      const setReviewedFileStatusStub = sinon
        .stub(element.getChangeModel(), 'setReviewedFilesStatus')
        .callsFake(() => Promise.resolve());

      assertIsDefined(element.diffHost);
      sinon.stub(element.diffHost, 'reload');
      element.loggedIn = true;
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
      element.patchRange = {
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
      element.loggedIn = true;
      const saveReviewedStub = sinon
        .stub(element.getChangeModel(), 'setReviewedFilesStatus')
        .callsFake(() => Promise.resolve());
      assertIsDefined(element.diffHost);
      sinon.stub(element.diffHost, 'reload');

      element.userModel.setDiffPreferences(createDefaultDiffPrefs());

      element.routerModel.updateState({
        changeNum: TEST_NUMERIC_CHANGE_ID,
        view: GerritView.DIFF,
        patchNum: 2 as RevisionPatchSetNum,
      });

      element.patchRange = {
        patchNum: 2 as RevisionPatchSetNum,
        basePatchNum: 1 as BasePatchSetNum,
      };

      await waitUntil(() => saveReviewedStub.called);

      element.getChangeModel().updateStateFileReviewed('/COMMIT_MSG', true);
      await element.updateComplete;

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

      reviewedStatusCheckBox.click();
      assert.isFalse(reviewedStatusCheckBox.checked);
      assert.deepEqual(saveReviewedStub.lastCall.args, [
        42,
        2,
        '/COMMIT_MSG',
        false,
      ]);

      element.getChangeModel().updateStateFileReviewed('/COMMIT_MSG', false);
      await element.updateComplete;

      reviewedStatusCheckBox.click();
      assert.isTrue(reviewedStatusCheckBox.checked);
      assert.deepEqual(saveReviewedStub.lastCall.args, [
        42,
        2,
        '/COMMIT_MSG',
        true,
      ]);

      const callCount = saveReviewedStub.callCount;

      element.params = {
        view: GerritView.CHANGE,
        changeNum: 42 as NumericChangeId,
        project: 'test' as RepoName,
      };
      await element.updateComplete;

      // saveReviewedState observer observes params, but should not fire when
      // view !== GerritView.DIFF.
      assert.equal(saveReviewedStub.callCount, callCount);
    });

    test('file review status with edit loaded', async () => {
      const saveReviewedStub = sinon.stub(
        element.getChangeModel(),
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

    test('hash is determined from params', async () => {
      assertIsDefined(element.diffHost);
      sinon.stub(element.diffHost, 'reload');
      const initLineStub = sinon.stub(element, 'initLineOfInterestAndCursor');

      element.loggedIn = true;
      element.params = {
        view: GerritView.DIFF,
        changeNum: 42 as NumericChangeId,
        patchNum: 2 as RevisionPatchSetNum,
        basePatchNum: 1 as BasePatchSetNum,
        path: '/COMMIT_MSG',
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
      element.getBrowserModel().setScreenWidth(0);

      const userStub = stubUsers('updatePreferences');

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
        element.params = {
          view: GerritView.DIFF,
          changeNum: 42 as NumericChangeId,
          patchNum: 4 as RevisionPatchSetNum,
          basePatchNum: 2 as BasePatchSetNum,
          path: '/COMMIT_MSG',
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
        element.params = {
          view: GerritView.DIFF,
          changeNum: 42 as NumericChangeId,
          patchNum: 5 as RevisionPatchSetNum,
          path: '/COMMIT_MSG',
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

      // Does nothing when params specify no cursor address:
      element.initCursor(false);
      assert.isNotOk(element.cursor.initialLineNumber);

      // Does nothing when params specify side but no number:
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
      const getUrlStub = sinon.stub(GerritNav, 'getUrlForDiffById');
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
      assert.isTrue(getUrlStub.called);
      assert.isFalse(getUrlStub.lastCall.args[6]);
    });

    test('line selected on left side', () => {
      const getUrlStub = sinon.stub(GerritNav, 'getUrlForDiffById');
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
      assert.isTrue(getUrlStub.called);
      assert.isTrue(getUrlStub.lastCall.args[6]);
    });

    test('handleToggleDiffMode', () => {
      const userStub = stubUsers('updatePreferences');
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
        element.params = {
          view: GerritView.DIFF,
          changeNum: 42 as NumericChangeId,
          patchNum: 3 as RevisionPatchSetNum,
          path: 'abcd',
        };
        await element.updateComplete;
      });
      test('empty', () => {
        sinon.stub(element, 'getPaths').returns({});
        element.initPatchRange();
        assert.equal(Object.keys(element.commentMap ?? {}).length, 0);
      });

      test('has paths', () => {
        sinon.stub(element, 'fetchFiles');
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
        let navToDiffStub: SinonStub;

        setup(() => {
          navToChangeStub = sinon.stub(element, 'navToChangeView');
          navToDiffStub = sinon.stub(GerritNav, 'navigateToDiff');
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
            assert.isFalse(navToDiffStub.called);
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
            assert.isFalse(navToDiffStub.called);
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
            assert.isTrue(navToDiffStub.calledOnce);
          });
        });

        suite('moveToNextFileWithComment', () => {
          test('no skips', () => {
            element.moveToNextFileWithComment();
            assert.isFalse(navToChangeStub.called);
            assert.isFalse(navToDiffStub.called);
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
            assert.isFalse(navToDiffStub.called);
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
            assert.isTrue(navToDiffStub.calledOnce);
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
        assertIsDefined(element.reviewed);
        // Reviewed checkbox should be shown.
        assert.isTrue(isVisible(element.reviewed));
        element.patchRange = {...element.patchRange, patchNum: EDIT};
        await element.updateComplete;

        assert.isFalse(isVisible(element.reviewed));
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

    test('File change should trigger navigateToDiff once', async () => {
      element.files = getFilesFromFileList(['file1', 'file2', 'file3']);
      sinon.stub(element, 'initLineOfInterestAndCursor');
      const navigateToDiffStub = sinon.stub(GerritNav, 'navigateToDiff');

      // Load file1
      element.params = {
        view: GerritView.DIFF,
        patchNum: 1 as RevisionPatchSetNum,
        changeNum: 101 as NumericChangeId,
        project: 'test-project' as RepoName,
        path: 'file1',
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
      assert.isTrue(navigateToDiffStub.notCalled);

      // Switch to file2
      element.handleFileChange(
        new CustomEvent('value-change', {detail: {value: 'file2'}})
      );
      assert.isTrue(navigateToDiffStub.calledOnce);

      // This is to mock the param change triggered by above navigate
      element.params = {
        view: GerritView.DIFF,
        patchNum: 1 as RevisionPatchSetNum,
        changeNum: 101 as NumericChangeId,
        project: 'test-project' as RepoName,
        path: 'file2',
      };
      element.patchRange = {
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

  suite('unmodified files with comments', () => {
    let element: GrDiffView;

    setup(async () => {
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
      element = await fixture(html`<gr-diff-view></gr-diff-view>`);
      element.changeNum = 42 as NumericChangeId;
    });

    test('fetchFiles add files with comments without changes', () => {
      element.patchRange = {
        basePatchNum: 5 as BasePatchSetNum,
        patchNum: 10 as RevisionPatchSetNum,
      };
      element.changeComments = {
        getPaths: sinon.stub().returns({
          'file2.txt': {},
          'file1.txt': {},
        }),
      } as unknown as ChangeComments;
      element.changeNum = 23 as NumericChangeId;
      return element.fetchFiles().then(() => {
        assert.deepEqual(element.files, {
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
