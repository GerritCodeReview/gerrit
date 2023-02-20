/**
 * @license
 * Copyright 2015 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import '../../shared/gr-date-formatter/gr-date-formatter';
import './gr-file-list';
import {FilesExpandedState} from '../gr-file-list-constants';
import {navigationToken} from '../../core/gr-navigation/gr-navigation';
import {
  mockPromise,
  query,
  stubRestApi,
  waitUntil,
  pressKey,
  stubElement,
  waitEventLoop,
} from '../../../test/test-utils';
import {
  BasePatchSetNum,
  CommitId,
  EDIT,
  NumericChangeId,
  PARENT,
  RepoName,
  RevisionPatchSetNum,
  Timestamp,
  UrlEncodedCommentId,
} from '../../../types/common';
import {createCommentThreads} from '../../../utils/comment-util';
import {
  createChangeComments,
  createCommit,
  createDiff,
  createParsedChange,
  createRevision,
} from '../../../test/test-data-generators';
import {
  createDefaultDiffPrefs,
  DiffViewMode,
} from '../../../constants/constants';
import {
  assertIsDefined,
  queryAll,
  queryAndAssert,
} from '../../../utils/common-util';
import {GrFileList, NormalizedFileInfo} from './gr-file-list';
import {FileInfo, PatchSetNumber} from '../../../api/rest-api';
import {GrButton} from '../../shared/gr-button/gr-button';
import {ParsedChangeInfo} from '../../../types/types';
import {normalize} from '../../../models/change/files-model';
import {GrDiffHost} from '../../diff/gr-diff-host/gr-diff-host';
import {GrEditFileControls} from '../../edit/gr-edit-file-controls/gr-edit-file-controls';
import {GrIcon} from '../../shared/gr-icon/gr-icon';
import {fixture, html, assert} from '@open-wc/testing';
import {Modifier} from '../../../utils/dom-util';
import {testResolver} from '../../../test/common-test-setup';

suite('gr-diff a11y test', () => {
  test('audit', async () => {
    assert.isAccessible(await fixture(html`<gr-file-list></gr-file-list>`));
  });
});

function createFiles(
  count: number,
  fileInfo: FileInfo = {}
): NormalizedFileInfo[] {
  const files = Array(count).fill({});
  return files.map((_, idx) => normalize(fileInfo, `'/file${idx}`));
}

suite('gr-file-list tests', () => {
  let element: GrFileList;

  let saveStub: sinon.SinonStub;

  suite('basic tests', async () => {
    setup(async () => {
      stubRestApi('getDiffComments').returns(Promise.resolve({}));
      stubRestApi('getDiffRobotComments').returns(Promise.resolve({}));
      stubRestApi('getDiffDrafts').returns(Promise.resolve({}));
      stubRestApi('getAccountCapabilities').returns(Promise.resolve({}));
      stubElement('gr-date-formatter', 'loadTimeFormat').callsFake(() =>
        Promise.resolve()
      );
      stubElement('gr-diff-host', 'reload').callsFake(() => Promise.resolve());
      stubElement('gr-diff-host', 'prefetchDiff').callsFake(() => {});

      element = await fixture(html`<gr-file-list></gr-file-list>`);

      element.diffPrefs = {
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
      element.numFilesShown = 200;
      element.basePatchNum = PARENT;
      element.patchNum = 2 as RevisionPatchSetNum;
      saveStub = sinon
        .stub(element, '_saveReviewedState')
        .callsFake(() => Promise.resolve());
      await element.updateComplete;
      // Wait for expandedFilesChanged to complete.
      await waitEventLoop();
    });

    test('renders', () => {
      assert.shadowDom.equal(
        element,
        /* HTML */ `<h3 class="assistive-tech-only">File list</h3>
          <div aria-label="Files list" id="container" role="grid">
            <div class="header-row row" role="row">
              <div class="status" role="gridcell"></div>
              <div class="path" role="columnheader">File</div>
              <div class="comments desktop" role="columnheader">Comments</div>
              <div class="comments mobile" role="columnheader" title="Comments">
                C
              </div>
              <div class="desktop sizeBars" role="columnheader">Size</div>
              <div class="header-stats" role="columnheader">Delta</div>
              <div aria-hidden="true" class="hideOnEdit reviewed"></div>
              <div aria-hidden="true" class="editFileControls showOnEdit"></div>
              <div aria-hidden="true" class="show-hide"></div>
            </div>
          </div>
          <div class="controlRow invisible row">
            <gr-button
              aria-disabled="false"
              class="fileListButton"
              id="incrementButton"
              link=""
              role="button"
              tabindex="0"
            >
              Show -200 more
            </gr-button>
            <gr-tooltip-content title="">
              <gr-button
                aria-disabled="false"
                class="fileListButton"
                id="showAllButton"
                link=""
                role="button"
                tabindex="0"
              >
                Show all 0 files
              </gr-button>
            </gr-tooltip-content>
          </div>
          <gr-diff-preferences-dialog
            id="diffPreferencesDialog"
          ></gr-diff-preferences-dialog>`
      );
    });

    test('renders file row', async () => {
      element.files = createFiles(1, {lines_inserted: 9});
      await element.updateComplete;
      const fileRows = queryAll<HTMLDivElement>(element, '.file-row');
      assert.dom.equal(
        fileRows?.[0],
        /* HTML */ `<div
          class="file-row row"
          data-file='{"path":"&apos;/file0"}'
          role="row"
          tabindex="-1"
        >
          <div class="status" role="gridcell">
            <gr-file-status></gr-file-status>
          </div>
          <span class="path" role="gridcell">
            <a class="pathLink">
              <span class="fullFileName" title="'/file0">
                <span class="newFilePath"> '/ </span>
                <span class="fileName"> file0 </span>
              </span>
              <span class="truncatedFileName" title="'/file0"> …/file0 </span>
              <gr-copy-clipboard hideinput=""> </gr-copy-clipboard>
            </a>
          </span>
          <div role="gridcell">
            <div class="comments desktop">
              <span class="drafts"> </span> <span> </span>
              <span class="noCommentsScreenReaderText"> No comments </span>
            </div>
            <div class="comments mobile">
              <span class="drafts"> </span> <span> </span>
              <span class="noCommentsScreenReaderText"> No comments </span>
            </div>
          </div>
          <div class="desktop" role="gridcell">
            <div
              aria-label="A bar that represents the addition and deletion ratio for the current file"
              class="hide sizeBars"
            ></div>
          </div>
          <div class="stats" role="gridcell">
            <div>
              <span aria-label="9 lines added" class="added" tabindex="0">
                +9
              </span>
              <span aria-label="0 lines removed" class="removed" tabindex="0">
                -0
              </span>
              <span hidden=""> +/-0 B </span>
            </div>
          </div>
          <div class="hideOnEdit reviewed" role="gridcell">
            <span aria-hidden="true" class="reviewedLabel"> Reviewed </span>
            <span
              aria-checked="false"
              aria-label="Reviewed"
              class="reviewedSwitch"
              role="switch"
              tabindex="0"
            >
              <span
                class="markReviewed"
                tabindex="-1"
                title="Mark as reviewed (shortcut: r)"
              >
                MARK REVIEWED
              </span>
            </span>
          </div>
          <div
            aria-hidden="true"
            class="editFileControls showOnEdit"
            role="gridcell"
          ></div>
          <div class="show-hide" role="gridcell">
            <span
              aria-checked="false"
              aria-label="Expand file"
              class="show-hide"
              data-expand="true"
              data-path="'/file0"
              role="switch"
              tabindex="0"
            >
              <gr-icon
                icon="expand_more"
                class="show-hide-icon"
                id="icon"
                tabindex="-1"
              ></gr-icon>
            </span>
          </div>
        </div>`
      );
    });

    test('renders file paths', async () => {
      element.files = createFiles(2, {lines_inserted: 9});
      await element.updateComplete;
      const fileRows = queryAll<HTMLDivElement>(element, '.file-row');

      assert.dom.equal(
        fileRows[0].querySelector('.path'),
        /* HTML */ `
          <span class="path" role="gridcell">
            <a class="pathLink">
              <span class="fullFileName" title="'/file0">
                <span class="newFilePath"> '/ </span>
                <span class="fileName"> file0 </span>
              </span>
              <span class="truncatedFileName" title="'/file0"> …/file0 </span>
              <gr-copy-clipboard hideinput=""> </gr-copy-clipboard>
            </a>
          </span>
        `
      );
      // The second row will have a matchingFilePath instead of newFilePath.
      assert.dom.equal(
        fileRows[1].querySelector('.path'),
        /* HTML */ `
          <span class="path" role="gridcell">
            <a class="pathLink">
              <span class="fullFileName" title="'/file1">
                <span class="matchingFilePath"> '/ </span>
                <span class="fileName"> file1 </span>
              </span>
              <span class="truncatedFileName" title="'/file1"> …/file1 </span>
              <gr-copy-clipboard hideinput=""> </gr-copy-clipboard>
            </a>
          </span>
        `
      );
    });

    test('renders file status column', async () => {
      element.files = createFiles(1, {lines_inserted: 9});
      element.filesLeftBase = createFiles(1, {lines_inserted: 9});
      await element.updateComplete;
      const fileRows = queryAll<HTMLDivElement>(element, '.file-row');
      const statusCol = queryAndAssert(fileRows?.[0], '.status');
      assert.dom.equal(
        statusCol,
        /* HTML */ `
          <div class="extended status" role="gridcell">
            <gr-file-status></gr-file-status>
            <gr-icon class="file-status-arrow" icon="arrow_right_alt"></gr-icon>
            <gr-file-status></gr-file-status>
          </div>
        `
      );
    });

    test('renders file status column header', async () => {
      element.files = createFiles(1, {lines_inserted: 9});
      element.filesLeftBase = createFiles(1, {lines_inserted: 9});
      element.basePatchNum = 1 as PatchSetNumber;
      await element.updateComplete;
      const fileRows = queryAll<HTMLDivElement>(element, '.header-row');
      const statusCol = queryAndAssert(fileRows?.[0], '.status');
      assert.dom.equal(
        statusCol,
        /* HTML */ `
          <div class="extended status" role="gridcell">
            <gr-tooltip-content has-tooltip="" title="Patchset 1">
              <div class="content">1</div>
            </gr-tooltip-content>
            <gr-icon class="file-status-arrow" icon="arrow_right_alt"></gr-icon>
            <gr-tooltip-content has-tooltip="" title="Patchset 2">
              <div class="content">2</div>
            </gr-tooltip-content>
          </div>
        `
      );
    });

    test('correct number of files are shown', async () => {
      element.fileListIncrement = 100;
      element.files = createFiles(250);
      await element.updateComplete;
      await waitEventLoop();

      assert.equal(
        queryAll<HTMLDivElement>(element, '.file-row').length,
        element.numFilesShown
      );
      const controlRow = queryAndAssert<HTMLDivElement>(element, '.controlRow');
      assert.isFalse(controlRow.classList.contains('invisible'));
      assert.equal(
        queryAndAssert<GrButton>(
          element,
          '#incrementButton'
        ).textContent!.trim(),
        'Show 50 more'
      );
      assert.equal(
        queryAndAssert<GrButton>(element, '#showAllButton').textContent!.trim(),
        'Show all 250 files'
      );

      queryAndAssert<GrButton>(element, '#showAllButton').click();
      await element.updateComplete;
      await waitEventLoop();

      assert.equal(element.numFilesShown, 250);
      assert.equal(element.shownFiles.length, 250);
      assert.isTrue(controlRow.classList.contains('invisible'));
    });

    test('rendering each row calls the reportRenderedRow method', async () => {
      const renderedStub = sinon.stub(element, 'reportRenderedRow');
      element.files = createFiles(10);
      await element.updateComplete;

      assert.equal(queryAll<HTMLDivElement>(element, '.file-row').length, 10);
      assert.equal(renderedStub.callCount, 10);
    });

    test('calculate totals for patch number', async () => {
      element.files = [
        {
          __path: '/COMMIT_MSG',
          lines_inserted: 9,
          size: 0,
          size_delta: 0,
        },
        {
          __path: '/MERGE_LIST',
          lines_inserted: 9,
          size: 0,
          size_delta: 0,
        },
        {
          __path: 'file_added_in_rev2.txt',
          lines_inserted: 1,
          lines_deleted: 1,
          size_delta: 10,
          size: 100,
        },
        {
          __path: 'myfile.txt',
          lines_inserted: 1,
          lines_deleted: 1,
          size_delta: 10,
          size: 100,
        },
      ];
      await element.updateComplete;

      let patchChange = element.calculatePatchChange();
      assert.deepEqual(patchChange, {
        inserted: 2,
        deleted: 2,
        size_delta_inserted: 0,
        size_delta_deleted: 0,
        total_size: 0,
      });
      assert.isTrue(element.shouldHideBinaryChangeTotals(patchChange));
      assert.isFalse(element.shouldHideChangeTotals(patchChange));

      // Test with a commit message that isn't the first file.
      element.files = [
        {
          __path: 'file_added_in_rev2.txt',
          lines_inserted: 1,
          lines_deleted: 1,
          size: 0,
          size_delta: 0,
        },
        {
          __path: '/COMMIT_MSG',
          lines_inserted: 9,
          size: 0,
          size_delta: 0,
        },
        {
          __path: '/MERGE_LIST',
          lines_inserted: 9,
          size: 0,
          size_delta: 0,
        },
        {
          __path: 'myfile.txt',
          lines_inserted: 1,
          lines_deleted: 1,
          size: 0,
          size_delta: 0,
        },
      ];
      await element.updateComplete;

      patchChange = element.calculatePatchChange();
      assert.deepEqual(patchChange, {
        inserted: 2,
        deleted: 2,
        size_delta_inserted: 0,
        size_delta_deleted: 0,
        total_size: 0,
      });
      assert.isTrue(element.shouldHideBinaryChangeTotals(patchChange));
      assert.isFalse(element.shouldHideChangeTotals(patchChange));

      // Test with no commit message.
      element.files = [
        {
          __path: 'file_added_in_rev2.txt',
          lines_inserted: 1,
          lines_deleted: 1,
          size: 0,
          size_delta: 0,
        },
        {
          __path: 'myfile.txt',
          lines_inserted: 1,
          lines_deleted: 1,
          size: 0,
          size_delta: 0,
        },
      ];
      await element.updateComplete;

      patchChange = element.calculatePatchChange();
      assert.deepEqual(patchChange, {
        inserted: 2,
        deleted: 2,
        size_delta_inserted: 0,
        size_delta_deleted: 0,
        total_size: 0,
      });
      assert.isTrue(element.shouldHideBinaryChangeTotals(patchChange));
      assert.isFalse(element.shouldHideChangeTotals(patchChange));

      // Test with files missing either lines_inserted or lines_deleted.
      element.files = [
        {
          __path: 'file_added_in_rev2.txt',
          lines_inserted: 1,
          size: 0,
          size_delta: 0,
        },
        {
          __path: 'myfile.txt',
          lines_deleted: 1,
          size: 0,
          size_delta: 0,
        },
      ];
      await element.updateComplete;

      patchChange = element.calculatePatchChange();
      assert.deepEqual(patchChange, {
        inserted: 1,
        deleted: 1,
        size_delta_inserted: 0,
        size_delta_deleted: 0,
        total_size: 0,
      });
      assert.isTrue(element.shouldHideBinaryChangeTotals(patchChange));
      assert.isFalse(element.shouldHideChangeTotals(patchChange));
    });

    test('binary only files', async () => {
      element.files = [
        {
          __path: '/COMMIT_MSG',
          lines_inserted: 9,
          size: 0,
          size_delta: 0,
        },
        {
          __path: 'file_binary_1',
          binary: true,
          size_delta: 10,
          size: 100,
        },
        {
          __path: 'file_binary_2',
          binary: true,
          size_delta: -5,
          size: 120,
        },
      ];
      await element.updateComplete;

      const patchChange = element.calculatePatchChange();
      assert.deepEqual(patchChange, {
        inserted: 0,
        deleted: 0,
        size_delta_inserted: 10,
        size_delta_deleted: -5,
        total_size: 220,
      });
      assert.isFalse(element.shouldHideBinaryChangeTotals(patchChange));
      assert.isTrue(element.shouldHideChangeTotals(patchChange));
    });

    test('binary and regular files', async () => {
      element.files = [
        {
          __path: '/COMMIT_MSG',
          lines_inserted: 9,
          size: 0,
          size_delta: 0,
        },
        {
          __path: 'file_binary_1',
          binary: true,
          size_delta: 10,
          size: 100,
        },
        {
          __path: 'file_binary_2',
          binary: true,
          size_delta: -5,
          size: 120,
        },
        {
          __path: 'myfile.txt',
          lines_deleted: 5,
          size_delta: -10,
          size: 100,
        },
        {
          __path: 'myfile2.txt',
          lines_inserted: 10,
          size: 0,
          size_delta: 0,
        },
      ];
      await element.updateComplete;

      const patchChange = element.calculatePatchChange();
      assert.deepEqual(patchChange, {
        inserted: 10,
        deleted: 5,
        size_delta_inserted: 10,
        size_delta_deleted: -5,
        total_size: 220,
      });
      assert.isFalse(element.shouldHideBinaryChangeTotals(patchChange));
      assert.isFalse(element.shouldHideChangeTotals(patchChange));
    });

    test('formatBytes function', () => {
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
        assert.equal(element.formatBytes(Number(bytes)), expected);
      }
    });

    test('formatPercentage function', () => {
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
          element.formatPercentage(item.size, item.delta),
          item.display
        );
      }
    });

    test('comment filtering', () => {
      element.changeComments = createChangeComments();

      element.basePatchNum = PARENT;
      element.patchNum = 1 as RevisionPatchSetNum;
      assert.equal(
        element.computeCommentsStringMobile({
          __path: '/COMMIT_MSG',
          size: 0,
          size_delta: 0,
        }),
        '2c'
      );

      element.basePatchNum = 1 as BasePatchSetNum;
      element.patchNum = 2 as RevisionPatchSetNum;
      assert.equal(
        element.computeCommentsStringMobile({
          __path: '/COMMIT_MSG',
          size: 0,
          size_delta: 0,
        }),
        '3c'
      );

      element.basePatchNum = PARENT;
      element.patchNum = 1 as RevisionPatchSetNum;
      assert.equal(
        element.computeDraftsString({
          __path: 'unresolved.file',
          size: 0,
          size_delta: 0,
        }),
        '1 draft'
      );

      element.basePatchNum = 1 as BasePatchSetNum;
      element.patchNum = 2 as RevisionPatchSetNum;
      assert.equal(
        element.computeDraftsString({
          __path: 'unresolved.file',
          size: 0,
          size_delta: 0,
        }),
        '1 draft'
      );

      element.basePatchNum = PARENT;
      element.patchNum = 1 as RevisionPatchSetNum;
      assert.equal(
        element.computeDraftsStringMobile({
          __path: 'unresolved.file',
          size: 0,
          size_delta: 0,
        }),
        '1d'
      );

      element.basePatchNum = 1 as BasePatchSetNum;
      element.patchNum = 2 as RevisionPatchSetNum;
      assert.equal(
        element.computeDraftsStringMobile({
          __path: 'unresolved.file',
          size: 0,
          size_delta: 0,
        }),
        '1d'
      );

      element.basePatchNum = PARENT;
      element.patchNum = 1 as RevisionPatchSetNum;
      assert.equal(
        element.computeCommentsStringMobile({
          __path: 'myfile.txt',
          size: 0,
          size_delta: 0,
        }),
        '1c'
      );

      element.basePatchNum = 1 as BasePatchSetNum;
      element.patchNum = 2 as RevisionPatchSetNum;
      assert.equal(
        element.computeCommentsStringMobile({
          __path: 'myfile.txt',
          size: 0,
          size_delta: 0,
        }),
        '3c'
      );

      element.basePatchNum = PARENT;
      element.patchNum = 1 as RevisionPatchSetNum;
      assert.equal(
        element.computeDraftsString({
          __path: 'myfile.txt',
          size: 0,
          size_delta: 0,
        }),
        ''
      );

      element.basePatchNum = 1 as BasePatchSetNum;
      element.patchNum = 2 as RevisionPatchSetNum;
      assert.equal(
        element.computeDraftsString({
          __path: 'myfile.txt',
          size: 0,
          size_delta: 0,
        }),
        ''
      );

      element.basePatchNum = PARENT;
      element.patchNum = 1 as RevisionPatchSetNum;
      assert.equal(
        element.computeDraftsStringMobile({
          __path: 'myfile.txt',
          size: 0,
          size_delta: 0,
        }),
        ''
      );

      element.basePatchNum = 1 as BasePatchSetNum;
      element.patchNum = 2 as RevisionPatchSetNum;
      assert.equal(
        element.computeDraftsStringMobile({
          __path: 'myfile.txt',
          size: 0,
          size_delta: 0,
        }),
        ''
      );

      element.basePatchNum = PARENT;
      element.patchNum = 1 as RevisionPatchSetNum;
      assert.equal(
        element.computeCommentsStringMobile({
          __path: 'file_added_in_rev2.txt',
          size: 0,
          size_delta: 0,
        }),
        ''
      );

      element.basePatchNum = 1 as BasePatchSetNum;
      element.patchNum = 2 as RevisionPatchSetNum;
      assert.equal(
        element.computeCommentsStringMobile({
          __path: 'file_added_in_rev2.txt',
          size: 0,
          size_delta: 0,
        }),
        ''
      );

      element.basePatchNum = PARENT;
      element.patchNum = 1 as RevisionPatchSetNum;
      assert.equal(
        element.computeDraftsString({
          __path: 'file_added_in_rev2.txt',
          size: 0,
          size_delta: 0,
        }),
        ''
      );

      element.basePatchNum = 1 as BasePatchSetNum;
      element.patchNum = 2 as RevisionPatchSetNum;
      assert.equal(
        element.computeDraftsString({
          __path: 'file_added_in_rev2.txt',
          size: 0,
          size_delta: 0,
        }),
        ''
      );

      element.basePatchNum = PARENT;
      element.patchNum = 1 as RevisionPatchSetNum;
      assert.equal(
        element.computeDraftsStringMobile({
          __path: 'file_added_in_rev2.txt',
          size: 0,
          size_delta: 0,
        }),
        ''
      );

      element.basePatchNum = 1 as BasePatchSetNum;
      element.patchNum = 2 as RevisionPatchSetNum;
      assert.equal(
        element.computeDraftsStringMobile({
          __path: 'file_added_in_rev2.txt',
          size: 0,
          size_delta: 0,
        }),
        ''
      );

      element.basePatchNum = PARENT;
      element.patchNum = 2 as RevisionPatchSetNum;
      assert.equal(
        element.computeCommentsStringMobile({
          __path: '/COMMIT_MSG',
          size: 0,
          size_delta: 0,
        }),
        '1c'
      );

      element.basePatchNum = 1 as BasePatchSetNum;
      element.patchNum = 2 as RevisionPatchSetNum;
      assert.equal(
        element.computeCommentsStringMobile({
          __path: '/COMMIT_MSG',
          size: 0,
          size_delta: 0,
        }),
        '3c'
      );

      element.basePatchNum = PARENT;
      element.patchNum = 1 as RevisionPatchSetNum;
      assert.equal(
        element.computeDraftsString({
          __path: '/COMMIT_MSG',
          size: 0,
          size_delta: 0,
        }),
        '2 drafts'
      );

      element.basePatchNum = 1 as BasePatchSetNum;
      element.patchNum = 2 as RevisionPatchSetNum;
      assert.equal(
        element.computeDraftsString({
          __path: '/COMMIT_MSG',
          size: 0,
          size_delta: 0,
        }),
        '2 drafts'
      );

      element.basePatchNum = PARENT;
      element.patchNum = 1 as RevisionPatchSetNum;
      assert.equal(
        element.computeDraftsStringMobile({
          __path: '/COMMIT_MSG',
          size: 0,
          size_delta: 0,
        }),
        '2d'
      );

      element.basePatchNum = 1 as BasePatchSetNum;
      element.patchNum = 2 as RevisionPatchSetNum;
      assert.equal(
        element.computeDraftsStringMobile({
          __path: '/COMMIT_MSG',
          size: 0,
          size_delta: 0,
        }),
        '2d'
      );

      element.basePatchNum = PARENT;
      element.patchNum = 2 as RevisionPatchSetNum;
      assert.equal(
        element.computeCommentsStringMobile({
          __path: 'myfile.txt',
          size: 0,
          size_delta: 0,
        }),
        '2c'
      );

      element.basePatchNum = 1 as BasePatchSetNum;
      element.patchNum = 2 as RevisionPatchSetNum;
      assert.equal(
        element.computeCommentsStringMobile({
          __path: 'myfile.txt',
          size: 0,
          size_delta: 0,
        }),
        '3c'
      );

      element.basePatchNum = PARENT;
      element.patchNum = 2 as RevisionPatchSetNum;
      assert.equal(
        element.computeDraftsStringMobile({
          __path: 'myfile.txt',
          size: 0,
          size_delta: 0,
        }),
        ''
      );

      element.basePatchNum = 1 as BasePatchSetNum;
      element.patchNum = 2 as RevisionPatchSetNum;
      assert.equal(
        element.computeDraftsStringMobile({
          __path: 'myfile.txt',
          size: 0,
          size_delta: 0,
        }),
        ''
      );
    });

    suite('keyboard shortcuts', () => {
      setup(async () => {
        element.files = [
          normalize({}, '/COMMIT_MSG'),
          normalize({}, 'file_added_in_rev2.txt'),
          normalize({}, 'myfile.txt'),
        ];
        element.changeNum = 42 as NumericChangeId;
        element.basePatchNum = PARENT;
        element.patchNum = 2 as RevisionPatchSetNum;
        element.change = {
          _number: 42 as NumericChangeId,
          project: 'test-project',
        } as ParsedChangeInfo;
        element.fileCursor.setCursorAtIndex(0);
        await element.updateComplete;
        await waitEventLoop();
      });

      test('toggle left diff via shortcut', () => {
        const toggleLeftDiffStub = sinon.stub();
        // Property getter cannot be stubbed w/ sandbox due to a bug in Sinon.
        // https://github.com/sinonjs/sinon/issues/781
        const diffsStub = sinon
          .stub(element, 'diffs')
          .get(() => [{toggleLeftDiff: toggleLeftDiffStub}]);
        pressKey(element, 'A');
        assert.isTrue(toggleLeftDiffStub.calledOnce);
        diffsStub.restore();
      });

      test('keyboard shortcuts', async () => {
        const items = [...queryAll<HTMLDivElement>(element, '.file-row')];
        element.fileCursor.stops = items;
        element.fileCursor.setCursorAtIndex(0);
        assert.equal(items.length, 3);
        assert.isTrue(items[0].classList.contains('selected'));
        assert.isFalse(items[1].classList.contains('selected'));
        assert.isFalse(items[2].classList.contains('selected'));
        // j with a modifier should not move the cursor.
        pressKey(element, 'J');
        assert.equal(element.fileCursor.index, 0);
        // down should not move the cursor.
        pressKey(element, 'ArrowDown');
        assert.equal(element.fileCursor.index, 0);

        pressKey(element, 'j');
        assert.equal(element.fileCursor.index, 1);
        assert.equal(element.selectedIndex, 1);
        pressKey(element, 'j');

        const setUrlStub = sinon.stub(testResolver(navigationToken), 'setUrl');
        assert.equal(element.fileCursor.index, 2);
        assert.equal(element.selectedIndex, 2);

        // k with a modifier should not move the cursor.
        pressKey(element, 'K');
        assert.equal(element.fileCursor.index, 2);

        // up should not move the cursor.
        pressKey(element, 'ArrowUp');
        assert.equal(element.fileCursor.index, 2);

        pressKey(element, 'k');
        assert.equal(element.fileCursor.index, 1);
        assert.equal(element.selectedIndex, 1);
        pressKey(element, 'o');

        assert.equal(setUrlStub.callCount, 1);
        assert.equal(
          setUrlStub.lastCall.firstArg,
          '/c/test-project/+/42/2/file_added_in_rev2.txt'
        );

        pressKey(element, 'k');
        pressKey(element, 'k');
        pressKey(element, 'k');
        assert.equal(element.fileCursor.index, 1);
        assert.equal(element.selectedIndex, 1);
        assertIsDefined(element.diffCursor);

        const createCommentInPlaceStub = sinon.stub(
          element.diffCursor,
          'createCommentInPlace'
        );
        pressKey(element, 'c');
        assert.isTrue(createCommentInPlaceStub.called);
      });

      test('i key shows/hides selected inline diff', async () => {
        const paths = element.files.map(f => f.__path);
        sinon.stub(element, 'expandedFilesChanged');
        const files = [...queryAll<HTMLDivElement>(element, '.file-row')];
        element.fileCursor.stops = files;
        element.fileCursor.setCursorAtIndex(0);
        await element.updateComplete;
        assert.equal(element.diffs.length, 0);
        assert.equal(element.expandedFiles.length, 0);

        pressKey(element, 'i');
        await element.updateComplete;
        assert.equal(element.diffs.length, 1);
        assert.equal(element.diffs[0].path, paths[0]);
        assert.equal(element.expandedFiles.length, 1);
        assert.equal(element.expandedFiles[0].path, paths[0]);

        pressKey(element, 'i');
        await element.updateComplete;
        assert.equal(element.diffs.length, 0);
        assert.equal(element.expandedFiles.length, 0);

        element.fileCursor.setCursorAtIndex(1);
        pressKey(element, 'i');
        await element.updateComplete;
        assert.equal(element.diffs.length, 1);
        assert.equal(element.diffs[0].path, paths[1]);
        assert.equal(element.expandedFiles.length, 1);
        assert.equal(element.expandedFiles[0].path, paths[1]);

        pressKey(element, 'I');
        await element.updateComplete;
        assert.equal(element.diffs.length, paths.length);
        assert.equal(element.expandedFiles.length, paths.length);
        for (const diff of element.diffs) {
          assert.isTrue(element.expandedFiles.some(f => f.path === diff.path));
        }
        // since _expandedFilesChanged is stubbed
        element.filesExpanded = FilesExpandedState.ALL;
        pressKey(element, 'I');
        await element.updateComplete;
        assert.equal(element.diffs.length, 0);
        assert.equal(element.expandedFiles.length, 0);
      });

      test('r key sets reviewed flag', async () => {
        await element.updateComplete;

        pressKey(element, 'r');
        await element.updateComplete;

        assert.isTrue(saveStub.called);
        assert.isTrue(saveStub.lastCall.calledWithExactly('/COMMIT_MSG', true));
      });

      test('r key clears reviewed flag', async () => {
        element.reviewed = ['/COMMIT_MSG'];
        await element.updateComplete;

        pressKey(element, 'r');
        await element.updateComplete;

        assert.isTrue(saveStub.called);
        assert.isTrue(
          saveStub.lastCall.calledWithExactly('/COMMIT_MSG', false)
        );
      });

      suite('handleOpenFile', () => {
        let interact: Function;

        setup(() => {
          const openCursorStub = sinon.stub(element, 'openCursorFile');
          const openSelectedStub = sinon.stub(element, 'openSelectedFile');
          const expandStub = sinon.stub(element, 'toggleFileExpanded');

          interact = function () {
            openCursorStub.reset();
            openSelectedStub.reset();
            expandStub.reset();
            element.handleOpenFile();
            const result = {} as any;
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
          element.filesExpanded = FilesExpandedState.NONE;
          assert.deepEqual(interact(), {opened_selected: true});
        });

        test('open from diff cursor', () => {
          element.filesExpanded = FilesExpandedState.ALL;
          assert.deepEqual(interact(), {opened_cursor: true});
        });

        test('expand when user prefers', () => {
          element.filesExpanded = FilesExpandedState.NONE;
          assert.deepEqual(interact(), {opened_selected: true});
        });
      });

      test('shift+left/shift+right', () => {
        assertIsDefined(element.diffCursor);
        const moveLeftStub = sinon.stub(element.diffCursor, 'moveLeft');
        const moveRightStub = sinon.stub(element.diffCursor, 'moveRight');

        let noDiffsExpanded = true;
        sinon.stub(element, 'noDiffsExpanded').callsFake(() => noDiffsExpanded);

        pressKey(element, 'ArrowLeft', Modifier.SHIFT_KEY);
        assert.isFalse(moveLeftStub.called);
        pressKey(element, 'ArrowRight', Modifier.SHIFT_KEY);
        assert.isFalse(moveRightStub.called);

        noDiffsExpanded = false;

        pressKey(element, 'ArrowLeft', Modifier.SHIFT_KEY);
        assert.isTrue(moveLeftStub.called);
        pressKey(element, 'ArrowRight', Modifier.SHIFT_KEY);
        assert.isTrue(moveRightStub.called);
      });
    });

    test('file review status', async () => {
      element.reviewed = ['/COMMIT_MSG', 'myfile.txt'];
      element.files = [
        normalize({}, '/COMMIT_MSG'),
        normalize({}, 'file_added_in_rev2.txt'),
        normalize({}, 'myfile.txt'),
      ];
      element.changeNum = 42 as NumericChangeId;
      element.basePatchNum = PARENT;
      element.patchNum = 2 as RevisionPatchSetNum;
      element.fileCursor.setCursorAtIndex(0);

      const reviewSpy = sinon.spy(element, 'reviewFile');
      const toggleExpandSpy = sinon.spy(element, 'toggleFileExpanded');

      await element.updateComplete;

      const fileRows = queryAll(element, '.row:not(.header-row)');
      const checkSelector = 'span.reviewedSwitch[role="switch"]';
      const commitMsg = fileRows[0].querySelector(checkSelector);
      const fileAdded = fileRows[1].querySelector(checkSelector);
      const myFile = fileRows[2].querySelector(checkSelector);

      assert.equal(commitMsg!.getAttribute('aria-checked'), 'true');
      assert.equal(fileAdded!.getAttribute('aria-checked'), 'false');
      assert.equal(myFile!.getAttribute('aria-checked'), 'true');

      const commitReviewLabel = fileRows[0].querySelector('.reviewedLabel');
      assert.isOk(commitReviewLabel);
      const markReviewLabel =
        fileRows[0].querySelector<HTMLSpanElement>('.markReviewed');
      assert.isOk(markReviewLabel);
      assert.isTrue(commitReviewLabel!.classList.contains('isReviewed'));
      assert.equal(markReviewLabel!.textContent, 'MARK UNREVIEWED');

      const clickSpy = sinon.spy(element, 'reviewedClick');
      markReviewLabel!.click();
      await element.updateComplete;

      assert.isTrue(clickSpy.calledOnce);
      assert.isTrue(clickSpy.lastCall.args[0].defaultPrevented);
      assert.isTrue(reviewSpy.calledOnce);
      assert.isTrue(saveStub.lastCall.calledWithExactly('/COMMIT_MSG', false));

      element.reviewed = ['myfile.txt'];
      await element.updateComplete;

      assert.isFalse(commitReviewLabel!.classList.contains('isReviewed'));
      assert.equal(markReviewLabel!.textContent, 'MARK REVIEWED');

      markReviewLabel!.click();
      await element.updateComplete;

      assert.isTrue(saveStub.lastCall.calledWithExactly('/COMMIT_MSG', true));
      assert.isTrue(clickSpy.lastCall.args[0].defaultPrevented);
      assert.isTrue(reviewSpy.calledTwice);
      assert.isFalse(toggleExpandSpy.called);

      element.reviewed = ['/COMMIT_MSG', 'myfile.txt'];
      await element.updateComplete;

      assert.isTrue(commitReviewLabel!.classList.contains('isReviewed'));
      assert.equal(markReviewLabel!.textContent, 'MARK UNREVIEWED');
    });

    test('handleFileListClick', async () => {
      element.files = [
        normalize({}, '/COMMIT_MSG'),
        normalize({}, 'f1.txt'),
        normalize({}, 'f2.txt'),
      ];
      element.changeNum = 42 as NumericChangeId;
      element.basePatchNum = PARENT;
      element.patchNum = 2 as RevisionPatchSetNum;
      await element.updateComplete;

      const clickSpy = sinon.spy(element, 'handleFileListClick');
      const reviewStub = sinon.stub(element, 'reviewFile');
      const toggleExpandSpy = sinon.spy(element, 'toggleFileExpanded');

      const row = queryAndAssert(
        element,
        '.row[data-file=\'{"path":"f1.txt"}\']'
      );

      // Click on the expand button, resulting in toggleFileExpanded being
      // called and resulting in a call to reviewFile().
      queryAndAssert<HTMLDivElement>(row, 'div.show-hide').click();
      await element.updateComplete;

      assert.isTrue(clickSpy.calledOnce);
      assert.isTrue(toggleExpandSpy.calledOnce);
      await waitUntil(() => reviewStub.calledOnce);

      // Click inside the diff. This should result in no additional calls to
      // toggleFileExpanded or reviewFile.
      queryAndAssert<GrDiffHost>(element, 'gr-diff-host').click();
      await element.updateComplete;
      assert.isTrue(clickSpy.calledTwice);
      assert.isTrue(toggleExpandSpy.calledOnce);
      assert.isTrue(reviewStub.calledOnce);
    });

    test('handleFileListClick editMode', async () => {
      element.files = [
        normalize({}, '/COMMIT_MSG'),
        normalize({}, 'f1.txt'),
        normalize({}, 'f2.txt'),
      ];
      element.changeNum = 42 as NumericChangeId;
      element.basePatchNum = PARENT;
      element.patchNum = 2 as RevisionPatchSetNum;
      element.editMode = true;
      await element.updateComplete;

      const clickSpy = sinon.spy(element, 'handleFileListClick');
      const toggleExpandSpy = sinon.spy(element, 'toggleFileExpanded');

      // Tap the edit controls. Should be ignored by handleFileListClick.
      queryAndAssert<HTMLDivElement>(element, '.editFileControls').click();
      await element.updateComplete;

      assert.isTrue(clickSpy.calledOnce);
      assert.isFalse(toggleExpandSpy.called);
    });

    test('checkbox shows/hides diff inline', async () => {
      element.files = [normalize({}, 'myfile.txt')];
      element.changeNum = 42 as NumericChangeId;
      element.basePatchNum = PARENT;
      element.patchNum = 2 as RevisionPatchSetNum;
      element.fileCursor.setCursorAtIndex(0);
      sinon.stub(element, 'expandedFilesChanged');
      await element.updateComplete;
      const fileRows = queryAll(element, '.row:not(.header-row)');
      // Because the label surrounds the input, the tap event is triggered
      // there first.
      const showHideCheck = fileRows[0].querySelector(
        'span.show-hide[role="switch"]'
      );
      const showHideLabel =
        showHideCheck!.querySelector<GrIcon>('.show-hide-icon');
      assert.equal(showHideCheck!.getAttribute('aria-checked'), 'false');
      showHideLabel!.click();
      await element.updateComplete;

      assert.equal(showHideCheck!.getAttribute('aria-checked'), 'true');
      assert.notEqual(
        element.expandedFiles.findIndex(f => f.path === 'myfile.txt'),
        -1
      );
    });

    test('diff mode correctly toggles the diffs', async () => {
      element.files = [normalize({}, 'myfile.txt')];
      element.changeNum = 42 as NumericChangeId;
      element.basePatchNum = PARENT;
      element.patchNum = 2 as RevisionPatchSetNum;
      const updateDiffPrefSpy = sinon.spy(element, 'updateDiffPreferences');
      element.fileCursor.setCursorAtIndex(0);
      await element.updateComplete;

      // Tap on a file to generate the diff.
      const row = queryAll<HTMLSpanElement>(
        element,
        '.row:not(.header-row) span.show-hide'
      )[0];

      row.click();

      element.diffViewMode = DiffViewMode.UNIFIED;
      await element.updateComplete;

      assert.isTrue(updateDiffPrefSpy.called);
    });

    test('expanded attribute not set on path when not expanded', () => {
      element.files = [normalize({}, '/COMMIT_MSG')];
      assert.isNotOk(query(element, 'expanded'));
    });

    test('tapping row ignores links', async () => {
      element.files = [normalize({}, '/COMMIT_MSG')];
      element.changeNum = 42 as NumericChangeId;
      element.basePatchNum = PARENT;
      element.patchNum = 2 as RevisionPatchSetNum;
      sinon.stub(element, 'expandedFilesChanged');
      await element.updateComplete;
      const commitMsgFile = queryAll<HTMLAnchorElement>(
        element,
        '.row:not(.header-row) a.pathLink'
      )[0];

      // Remove href attribute so the app doesn't route to a diff view
      commitMsgFile.removeAttribute('href');
      const togglePathSpy = sinon.spy(element, 'toggleFileExpanded');

      commitMsgFile.click();
      await element.updateComplete;
      assert(togglePathSpy.notCalled, 'file is opened as diff view');
      assert.isNotOk(query(element, '.expanded'));
      assert.notEqual(
        getComputedStyle(queryAndAssert(element, '.show-hide')).display,
        'none'
      );
    });

    test('toggleFileExpanded', async () => {
      const path = 'path/to/my/file.txt';
      element.files = [normalize({}, path)];
      await element.updateComplete;
      // Wait for expandedFilesChanged to finish.
      await waitEventLoop();

      const renderSpy = sinon.spy(element, 'renderInOrder');
      const collapseStub = sinon.stub(element, 'clearCollapsedDiffs');

      assert.equal(
        queryAndAssert<GrIcon>(element, 'gr-icon').icon,
        'expand_more'
      );
      assert.equal(element.expandedFiles.length, 0);
      element.toggleFileExpanded({path});
      await element.updateComplete;
      // Wait for expandedFilesChanged to finish.
      await waitEventLoop();

      assert.equal(collapseStub.lastCall.args[0].length, 0);
      assert.equal(
        queryAndAssert<GrIcon>(element, 'gr-icon').icon,
        'expand_less'
      );

      assert.equal(renderSpy.callCount, 1);
      assert.isTrue(element.expandedFiles.some(f => f.path === path));
      element.toggleFileExpanded({path});
      await element.updateComplete;
      // Wait for expandedFilesChanged to finish.
      await waitEventLoop();

      assert.equal(
        queryAndAssert<GrIcon>(element, 'gr-icon').icon,
        'expand_more'
      );
      assert.equal(renderSpy.callCount, 1);
      assert.isFalse(element.expandedFiles.some(f => f.path === path));
      assert.equal(collapseStub.lastCall.args[0].length, 1);
    });

    test('expandAllDiffs and collapseAllDiffs', async () => {
      const collapseStub = sinon.stub(element, 'clearCollapsedDiffs');
      assertIsDefined(element.diffCursor);
      const reInitStub = sinon.stub(element.diffCursor, 'reInitAndUpdateStops');

      const path = 'path/to/my/file.txt';
      element.files = [normalize({}, path)];
      // Wait for diffs to be computed.
      await element.updateComplete;
      await waitEventLoop();
      element.expandAllDiffs();
      await element.updateComplete;
      // Wait for expandedFilesChanged to finish.
      await waitEventLoop();
      assert.equal(element.filesExpanded, FilesExpandedState.ALL);
      assert.isTrue(reInitStub.calledTwice);
      assert.equal(collapseStub.lastCall.args[0].length, 0);

      element.collapseAllDiffs();
      await element.updateComplete;
      // Wait for expandedFilesChanged to finish.
      await waitEventLoop();
      assert.equal(element.expandedFiles.length, 0);
      assert.equal(element.filesExpanded, FilesExpandedState.NONE);
      assert.equal(collapseStub.lastCall.args[0].length, 1);
    });

    test('expandedFilesChanged', async () => {
      sinon.stub(element, 'reviewFile');
      const path = 'path/to/my/file.txt';
      const promise = mockPromise();
      const diffs = [
        {
          path,
          style: {},
          reload() {
            promise.resolve();
          },
          prefetchDiff() {},
          cancel() {},
          getCursorStops() {
            return [];
          },
          addEventListener(eventName: string, callback: Function) {
            if (
              ['render-start', 'render-content', 'scroll'].indexOf(eventName) >=
              0
            ) {
              callback(new Event(eventName));
            }
          },
        },
      ];
      sinon.stub(element, 'diffs').get(() => diffs);
      element.expandedFiles = element.expandedFiles.concat([{path}]);
      await element.updateComplete;
      await waitEventLoop();
      await promise;
    });

    test('clearCollapsedDiffs', () => {
      // Have to type as any because the type is 'GrDiffHost'
      // which would require stubbing so many different
      // methods / properties that it isn't worth it.
      const diff = {
        cancel: sinon.stub(),
        clearDiffContent: sinon.stub(),
      } as any;
      element.clearCollapsedDiffs([diff]);
      assert.isTrue(diff.cancel.calledOnce);
      assert.isTrue(diff.clearDiffContent.calledOnce);
    });

    test('filesExpanded value updates to correct enum', async () => {
      element.files = [normalize({}, 'foo.bar'), normalize({}, 'baz.bar')];
      await element.updateComplete;
      assert.equal(element.filesExpanded, FilesExpandedState.NONE);
      element.expandedFiles.push({path: 'baz.bar'});
      element.expandedFilesChanged([{path: 'baz.bar'}]);
      await element.updateComplete;
      assert.equal(element.filesExpanded, FilesExpandedState.SOME);
      element.expandedFiles.push({path: 'foo.bar'});
      element.expandedFilesChanged([{path: 'foo.bar'}]);
      await element.updateComplete;
      assert.equal(element.filesExpanded, FilesExpandedState.ALL);
      element.collapseAllDiffs();
      await element.updateComplete;
      assert.equal(element.filesExpanded, FilesExpandedState.NONE);
      element.expandAllDiffs();
      await element.updateComplete;
      assert.equal(element.filesExpanded, FilesExpandedState.ALL);
    });

    test('renderInOrder', async () => {
      const reviewStub = sinon.stub(element, 'reviewFile');
      let callCount = 0;
      // Have to type as any because the type is 'GrDiffHost'
      // which would require stubbing so many different
      // methods / properties that it isn't worth it.
      const diffs = [
        {
          path: 'p0',
          style: {},
          prefetchDiff() {},
          reload() {
            assert.equal(callCount++, 2);
            return Promise.resolve();
          },
        },
        {
          path: 'p1',
          style: {},
          prefetchDiff() {},
          reload() {
            assert.equal(callCount++, 1);
            return Promise.resolve();
          },
        },
        {
          path: 'p2',
          style: {},
          prefetchDiff() {},
          reload() {
            assert.equal(callCount++, 0);
            return Promise.resolve();
          },
        },
      ] as any;
      element.renderInOrder([{path: 'p2'}, {path: 'p1'}, {path: 'p0'}], diffs);
      await element.updateComplete;
      assert.isFalse(reviewStub.called);
    });

    test('renderInOrder logged in', async () => {
      const reviewStub = sinon.stub(element, 'reviewFile');
      let callCount = 0;
      // Have to type as any because the type is 'GrDiffHost'
      // which would require stubbing so many different
      // methods / properties that it isn't worth it.
      const diffs = [
        {
          path: 'p2',
          style: {},
          prefetchDiff() {},
          reload() {
            assert.equal(reviewStub.callCount, 0);
            assert.equal(callCount++, 0);
            return Promise.resolve();
          },
        },
      ] as any;
      element.renderInOrder([{path: 'p2'}], diffs);
      await element.updateComplete;
      assert.equal(reviewStub.callCount, 1);
    });

    test('renderInOrder respects diffPrefs.manual_review', async () => {
      element.diffPrefs = {
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
        manual_review: true,
      };
      const reviewStub = sinon.stub(element, 'reviewFile');
      // Have to type as any because the type is 'GrDiffHost'
      // which would require stubbing so many different
      // methods / properties that it isn't worth it.
      const diffs = [
        {
          path: 'p',
          style: {},
          prefetchDiff() {},
          reload() {
            return Promise.resolve();
          },
        },
      ] as any;

      element.renderInOrder([{path: 'p'}], diffs);
      await element.updateComplete;
      assert.isFalse(reviewStub.called);
      delete element.diffPrefs.manual_review;
      element.renderInOrder([{path: 'p'}], diffs);
      await element.updateComplete;
      // Wait for renderInOrder to finish
      await waitEventLoop();
      assert.isTrue(reviewStub.called);
      assert.isTrue(reviewStub.calledWithExactly('p', true));
    });

    suite('for merge commits', () => {
      let filesStub: sinon.SinonStub;

      setup(async () => {
        element.files = [
          normalize({size: 0, size_delta: 0}, 'conflictingFile.js'),
        ];
        filesStub = stubRestApi('getChangeOrEditFiles')
          .onFirstCall()
          .resolves({
            'conflictingFile.js': {size: 0, size_delta: 0},
            'cleanlyMergedFile.js': {size: 0, size_delta: 0},
          });
        stubRestApi('getReviewedFiles').resolves([]);
        stubRestApi('getDiffPreferences').resolves(createDefaultDiffPrefs());
        const changeWithMultipleParents = {
          ...createParsedChange(),
          revisions: {
            r1: {
              ...createRevision(),
              commit: {
                ...createCommit(),
                parents: [
                  {commit: 'p1' as CommitId, subject: 'subject1'},
                  {commit: 'p2' as CommitId, subject: 'subject2'},
                ],
              },
            },
          },
        };
        element.changeNum = changeWithMultipleParents._number;
        element.change = changeWithMultipleParents;
        element.basePatchNum = PARENT;
        element.patchNum = 1 as RevisionPatchSetNum;
        await element.updateComplete;
        await waitEventLoop();
      });

      test('displays cleanly merged file count', async () => {
        await waitUntil(() => !!query(element, '.cleanlyMergedText'));

        const message = queryAndAssert<HTMLSpanElement>(
          element,
          '.cleanlyMergedText'
        ).textContent!.trim();
        assert.equal(message, '1 file merged cleanly in Parent 1');
      });

      test('displays plural cleanly merged file count', async () => {
        filesStub.restore();
        stubRestApi('getChangeOrEditFiles')
          .onFirstCall()
          .resolves({
            'conflictingFile.js': {size: 0, size_delta: 0},
            'cleanlyMergedFile.js': {size: 0, size_delta: 0},
            'anotherCleanlyMergedFile.js': {size: 0, size_delta: 0},
          });
        await element.updateCleanlyMergedPaths();
        await element.updateComplete;
        await waitUntil(() => !!query(element, '.cleanlyMergedText'));

        const message = queryAndAssert(
          element,
          '.cleanlyMergedText'
        ).textContent!.trim();
        assert.equal(message, '2 files merged cleanly in Parent 1');
      });

      test('displays button for navigating to parent 1 base', async () => {
        await waitUntil(() => !!query(element, '.showParentButton'));

        queryAndAssert(element, '.showParentButton');
      });

      test('computes old paths for cleanly merged files', async () => {
        filesStub.restore();
        stubRestApi('getChangeOrEditFiles')
          .onFirstCall()
          .resolves({
            'conflictingFile.js': {size: 0, size_delta: 0},
            'cleanlyMergedFile.js': {
              old_path: 'cleanlyMergedFileOldName.js',
              size: 0,
              size_delta: 0,
            },
          });
        await element.updateCleanlyMergedPaths();

        assert.deepEqual(element.cleanlyMergedOldPaths, [
          'cleanlyMergedFileOldName.js',
        ]);
      });

      test('not shown for non-Auto Merge base parents', async () => {
        element.basePatchNum = 1 as BasePatchSetNum;
        element.patchNum = 2 as RevisionPatchSetNum;
        await element.updateCleanlyMergedPaths();
        await element.updateComplete;

        assert.notOk(query(element, '.cleanlyMergedText'));
        assert.notOk(query(element, '.showParentButton'));
      });

      test('not shown in edit mode', async () => {
        element.basePatchNum = 1 as BasePatchSetNum;
        element.patchNum = EDIT;
        await element.updateCleanlyMergedPaths();
        await element.updateComplete;

        assert.notOk(query(element, '.cleanlyMergedText'));
        assert.notOk(query(element, '.showParentButton'));
      });
    });
  });

  suite('diff url file list', () => {
    test('diff url', () => {
      element.change = {
        ...createParsedChange(),
        _number: 1 as NumericChangeId,
        project: 'gerrit' as RepoName,
      };
      element.basePatchNum = PARENT;
      element.patchNum = 1 as RevisionPatchSetNum;
      const path = 'index.php';
      element.editMode = false;
      assert.equal(element.computeDiffURL(path), '/c/gerrit/+/1/1/index.php');
    });

    test('diff url commit msg', () => {
      element.change = {
        ...createParsedChange(),
        _number: 1 as NumericChangeId,
        project: 'gerrit' as RepoName,
      };
      element.basePatchNum = PARENT;
      element.patchNum = 1 as RevisionPatchSetNum;
      element.editMode = false;
      const path = '/COMMIT_MSG';
      assert.equal(element.computeDiffURL(path), '/c/gerrit/+/1/1//COMMIT_MSG');
    });

    test('edit url', () => {
      element.change = {
        ...createParsedChange(),
        _number: 1 as NumericChangeId,
        project: 'gerrit' as RepoName,
      };
      element.basePatchNum = PARENT;
      element.patchNum = 1 as RevisionPatchSetNum;
      element.editMode = true;
      const path = 'index.php';
      assert.equal(
        element.computeDiffURL(path),
        '/c/gerrit/+/1/1/index.php,edit'
      );
    });

    test('edit url commit msg', () => {
      element.change = {
        ...createParsedChange(),
        _number: 1 as NumericChangeId,
        project: 'gerrit' as RepoName,
      };
      element.basePatchNum = PARENT;
      element.patchNum = 1 as RevisionPatchSetNum;
      element.editMode = true;
      const path = '/COMMIT_MSG';
      assert.equal(
        element.computeDiffURL(path),
        '/c/gerrit/+/1/1//COMMIT_MSG,edit'
      );
    });
  });

  suite('size bars', () => {
    test('computeSizeBarLayout', async () => {
      const defaultSizeBarLayout = {
        maxInserted: 0,
        maxDeleted: 0,
        maxAdditionWidth: 0,
        maxDeletionWidth: 0,
        deletionOffset: 0,
      };

      element.files = [];
      await element.updateComplete;
      assert.deepEqual(element.computeSizeBarLayout(), defaultSizeBarLayout);

      element.files = [
        {
          __path: '/COMMIT_MSG',
          lines_inserted: 10000,
          size_delta: 10000,
          size: 10000,
        },
        {
          __path: 'foo',
          lines_inserted: 4,
          lines_deleted: 10,
          size_delta: 14,
          size: 20,
        },
        {
          __path: 'bar',
          lines_inserted: 5,
          lines_deleted: 8,
          size_delta: 13,
          size: 21,
        },
      ];
      await element.updateComplete;
      const layout = element.computeSizeBarLayout();
      assert.equal(layout.maxInserted, 5);
      assert.equal(layout.maxDeleted, 10);
    });

    test('computeBarAdditionWidth', () => {
      const file = {
        __path: 'foo/bar.baz',
        lines_inserted: 5,
        lines_deleted: 0,
        size: 0,
        size_delta: 0,
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
      assert.equal(element.computeBarAdditionWidth(file, stats), 30);

      // If there are no insertions, there is no width.
      stats.maxInserted = 0;
      assert.equal(element.computeBarAdditionWidth(file, stats), 0);

      // If the insertions is not present on the file, there is no width.
      stats.maxInserted = 10;
      file.lines_inserted = 0;
      assert.equal(element.computeBarAdditionWidth(file, stats), 0);

      // If the file is a commit message, returns zero.
      file.lines_inserted = 5;
      file.__path = '/COMMIT_MSG';
      assert.equal(element.computeBarAdditionWidth(file, stats), 0);

      // Width bottoms-out at the minimum width.
      file.__path = 'stuff.txt';
      file.lines_inserted = 1;
      stats.maxInserted = 1000000;
      assert.equal(element.computeBarAdditionWidth(file, stats), 1.5);
    });

    test('_computeBarAdditionX', () => {
      const file = {
        __path: 'foo/bar.baz',
        lines_inserted: 5,
        lines_deleted: 0,
        size: 0,
        size_delta: 0,
      };
      const stats = {
        maxInserted: 10,
        maxDeleted: 0,
        maxAdditionWidth: 60,
        maxDeletionWidth: 0,
        deletionOffset: 60,
      };
      assert.equal(element.computeBarAdditionX(file, stats), 30);
    });

    test('computeBarDeletionWidth', () => {
      const file = {
        __path: 'foo/bar.baz',
        lines_inserted: 0,
        lines_deleted: 5,
        size: 0,
        size_delta: 0,
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
      assert.equal(element.computeBarDeletionWidth(file, stats), 15);

      // If there are no deletions, there is no width.
      stats.maxDeleted = 0;
      assert.equal(element.computeBarDeletionWidth(file, stats), 0);

      // If the deletions is not present on the file, there is no width.
      stats.maxDeleted = 10;
      file.lines_deleted = 0;
      assert.equal(element.computeBarDeletionWidth(file, stats), 0);

      // If the file is a commit message, returns zero.
      file.lines_deleted = 5;
      file.__path = '/COMMIT_MSG';
      assert.equal(element.computeBarDeletionWidth(file, stats), 0);

      // Width bottoms-out at the minimum width.
      file.__path = 'stuff.txt';
      file.lines_deleted = 1;
      stats.maxDeleted = 1000000;
      assert.equal(element.computeBarDeletionWidth(file, stats), 1.5);
    });

    test('_computeSizeBarsClass', () => {
      element.showSizeBars = false;
      assert.equal(
        element.computeSizeBarsClass('foo/bar.baz'),
        'sizeBars hide'
      );
      element.showSizeBars = true;
      assert.equal(
        element.computeSizeBarsClass('/COMMIT_MSG'),
        'sizeBars invisible'
      );
      assert.equal(element.computeSizeBarsClass('foo/bar.baz'), 'sizeBars ');
    });
  });

  suite('gr-file-list inline diff tests', () => {
    let element: GrFileList;
    let reviewFileStub: sinon.SinonStub;

    const commitMsgComments = [
      {
        patch_set: 2 as RevisionPatchSetNum,
        path: '/p',
        id: 'ecf0b9fa_fe1a5f62' as UrlEncodedCommentId,
        line: 20,
        updated: '2018-02-08 18:49:18.000000000' as Timestamp,
        message: 'another comment',
        unresolved: true,
      },
      {
        patch_set: 2 as RevisionPatchSetNum,
        path: '/p',
        id: '503008e2_0ab203ee' as UrlEncodedCommentId,
        line: 10,
        updated: '2018-02-14 22:07:43.000000000' as Timestamp,
        message: 'a comment',
        unresolved: true,
      },
      {
        patch_set: 2 as RevisionPatchSetNum,
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
      await diff.waitForReloadToRender();
    }

    async function renderAndGetNewDiffs(index: number) {
      const diffs = queryAll<GrDiffHost>(element, 'gr-diff-host');

      for (let i = index; i < diffs.length; i++) {
        await setupDiff(diffs[i]);
      }

      assertIsDefined(element.diffCursor);
      element.updateDiffCursor();
      element.diffCursor.reInitCursor();
      return diffs;
    }

    setup(async () => {
      stubRestApi('getPreferences').returns(Promise.resolve(undefined));
      stubRestApi('getDiffComments').returns(Promise.resolve({}));
      stubRestApi('getDiffRobotComments').returns(Promise.resolve({}));
      stubRestApi('getDiffDrafts').returns(Promise.resolve({}));
      stubElement('gr-date-formatter', 'loadTimeFormat').callsFake(() =>
        Promise.resolve()
      );
      stubRestApi('getDiff').callsFake(() => Promise.resolve(createDiff()));
      stubElement('gr-diff-host', 'prefetchDiff').callsFake(() => {});

      element = await fixture(html`<gr-file-list></gr-file-list>`);
      element.diffPrefs = {
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
      element.change = {
        ...createParsedChange(),
        _number: 42 as NumericChangeId,
        project: 'testRepo' as RepoName,
      };
      reviewFileStub = sinon.stub(element, 'reviewFile');

      element.numFilesShown = 75;
      element.selectedIndex = 0;
      element.files = [
        {__path: '/COMMIT_MSG', lines_inserted: 9, size: 0, size_delta: 0},
        {
          __path: 'file_added_in_rev2.txt',
          lines_inserted: 1,
          lines_deleted: 1,
          size_delta: 10,
          size: 100,
        },
        {
          __path: 'myfile.txt',
          lines_inserted: 1,
          lines_deleted: 1,
          size_delta: 10,
          size: 100,
        },
      ];
      element.reviewed = ['/COMMIT_MSG', 'myfile.txt'];
      element.changeNum = 42 as NumericChangeId;
      element.basePatchNum = PARENT;
      element.patchNum = 2 as RevisionPatchSetNum;
      sinon
        .stub(window, 'fetch')
        .callsFake(() => Promise.resolve(new Response()));
      await element.updateComplete;
    });

    test('cursor with individually opened files', async () => {
      await element.updateComplete;
      pressKey(element, 'i');

      await waitUntil(async () => {
        const diffs = await renderAndGetNewDiffs(0);
        return diffs.length > 0;
      });
      let diffs = await renderAndGetNewDiffs(0);
      const diffStops = diffs[0].getCursorStops();

      // 1 diff should be rendered.
      assert.equal(diffs.length, 1);
      assert.isTrue(diffStops.length > 12);

      // No line number is selected.
      assert.isFalse(
        (diffStops[10] as HTMLElement).classList.contains('target-row')
      );

      // Tapping content on a line selects the line number.
      queryAll<HTMLDivElement>(
        diffStops[10] as HTMLElement,
        '.contentText'
      )[0].click();
      await element.updateComplete;
      assert.isTrue(
        (diffStops[10] as HTMLElement).classList.contains('target-row')
      );

      // Keyboard shortcuts are still moving the file cursor, not the diff
      // cursor.
      pressKey(element, 'j');
      await element.updateComplete;
      assert.isTrue(
        (diffStops[10] as HTMLElement).classList.contains('target-row')
      );
      assert.isFalse(
        (diffStops[11] as HTMLElement).classList.contains('target-row')
      );

      // The file cursor is now at 1.
      assert.equal(element.fileCursor.index, 1);

      pressKey(element, 'i');
      await element.updateComplete;
      diffs = await renderAndGetNewDiffs(1);

      // Two diffs should be rendered.
      assert.equal(diffs.length, 2);
      const diffStopsFirst = diffs[0].getCursorStops();
      const diffStopsSecond = diffs[1].getCursorStops();

      // The line on the first diff is still selected
      assert.isTrue(
        (diffStopsFirst[10] as HTMLElement).classList.contains('target-row')
      );
      assert.isFalse(
        (diffStopsSecond[10] as HTMLElement).classList.contains('target-row')
      );
    });

    test('cursor with toggle all files', async () => {
      pressKey(element, 'I');
      await element.updateComplete;

      const diffs = await renderAndGetNewDiffs(0);
      const diffStops = diffs[0].getCursorStops();

      // 1 diff should be rendered.
      assert.equal(diffs.length, 3);
      assert.isTrue(diffStops.length > 12);

      // No line number is selected.
      assert.isFalse(
        (diffStops[10] as HTMLElement).classList.contains('target-row')
      );

      // Tapping content on a line selects the line number.
      queryAll<HTMLDivElement>(
        diffStops[10] as HTMLElement,
        '.contentText'
      )[0].click();
      await element.updateComplete;
      assert.isTrue(
        (diffStops[10] as HTMLElement).classList.contains('target-row')
      );

      // Keyboard shortcuts are still moving the file cursor, not the diff
      // cursor.
      pressKey(element, 'j');
      await element.updateComplete;
      assert.isFalse(
        (diffStops[10] as HTMLElement).classList.contains('target-row')
      );
      assert.isTrue(
        (diffStops[11] as HTMLElement).classList.contains('target-row')
      );

      // The file cursor is still at 0.
      assert.equal(element.fileCursor.index, 0);
    });

    suite('n key presses', () => {
      let nextCommentStub: sinon.SinonStub;
      let nextChunkStub: sinon.SinonStub;
      let fileRows: NodeListOf<HTMLDivElement>;

      setup(() => {
        sinon.stub(element, 'renderInOrder').returns(Promise.resolve());
        assertIsDefined(element.diffCursor);
        nextCommentStub = sinon.stub(
          element.diffCursor,
          'moveToNextCommentThread'
        );
        nextChunkStub = sinon.stub(element.diffCursor, 'moveToNextChunk');
        fileRows = queryAll<HTMLDivElement>(element, '.row:not(.header-row)');
      });

      test('correct number of files expanded', async () => {
        pressKey(fileRows[0], 'i');
        await element.updateComplete;
        assert.equal(element.filesExpanded, FilesExpandedState.SOME);

        pressKey(element, 'n');
        await element.updateComplete;
        assert.isTrue(nextChunkStub.calledOnce);
      });

      test('N key with some files expanded', async () => {
        pressKey(fileRows[0], 'i');
        await element.updateComplete;
        assert.equal(element.filesExpanded, FilesExpandedState.SOME);

        pressKey(element, 'N');
        await element.updateComplete;
        assert.isTrue(nextCommentStub.calledOnce);
      });

      test('n key with all files expanded', async () => {
        pressKey(fileRows[0], 'I');
        await element.updateComplete;
        assert.equal(element.filesExpanded, FilesExpandedState.ALL);

        pressKey(element, 'n');
        await element.updateComplete;
        assert.isTrue(nextChunkStub.calledOnce);
      });

      test('N key with all files expanded', async () => {
        pressKey(fileRows[0], 'I');
        await element.updateComplete;
        assert.equal(element.filesExpanded, FilesExpandedState.ALL);

        pressKey(element, 'N');
        await element.updateComplete;
        assert.isTrue(nextCommentStub.called);
      });
    });

    test('openSelectedFile behavior', async () => {
      const files = element.files;
      element.files = [];
      await element.updateComplete;
      const setUrlStub = sinon.stub(testResolver(navigationToken), 'setUrl');
      // Noop when there are no files.
      element.openSelectedFile();
      assert.isFalse(setUrlStub.calledOnce);

      element.files = files;
      await element.updateComplete;
      // Navigates when a file is selected.
      element.openSelectedFile();
      assert.isTrue(setUrlStub.calledOnce);
    });

    test('displayLine', () => {
      element.filesExpanded = FilesExpandedState.ALL;

      element.displayLine = false;
      element.handleCursorNext(new KeyboardEvent('keydown'));
      assert.isTrue(element.displayLine);

      element.displayLine = false;
      element.handleCursorPrev(new KeyboardEvent('keydown'));
      assert.isTrue(element.displayLine);

      element.displayLine = true;
      element.handleEscKey();
      assert.isFalse(element.displayLine);
    });

    suite('editMode behavior', () => {
      test('reviewed checkbox', async () => {
        reviewFileStub.restore();
        const saveReviewStub = sinon.stub(element, '_saveReviewedState');

        element.editMode = false;
        pressKey(element, 'r');
        assert.isTrue(saveReviewStub.calledOnce);

        element.editMode = true;
        await element.updateComplete;

        pressKey(element, 'r');
        assert.isTrue(saveReviewStub.calledOnce);
      });
    });

    test('editing actions', async () => {
      // Edit controls are guarded behind a dom-if initially and not rendered.
      assert.isNotOk(
        query<GrEditFileControls>(element, 'gr-edit-file-controls')
      );

      element.editMode = true;
      await element.updateComplete;

      // Commit message should not have edit controls.
      const editControls = Array.from(
        queryAll(element, '.row:not(.header-row)')
      ).map(row => row.querySelector('gr-edit-file-controls'));
      assert.isTrue(editControls[0]!.classList.contains('invisible'));
    });
  });
});
