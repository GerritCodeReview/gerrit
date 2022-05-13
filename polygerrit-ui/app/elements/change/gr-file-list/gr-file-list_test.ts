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
import '../../shared/gr-date-formatter/gr-date-formatter';
import './gr-file-list';
import {FilesExpandedState} from '../gr-file-list-constants';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {runA11yAudit} from '../../../test/a11y-test-utils';
import {
  listenOnce,
  mockPromise,
  query,
  spyRestApi,
  stubRestApi,
} from '../../../test/test-utils';
import {
  BasePatchSetNum,
  CommitId,
  EditPatchSetNum,
  NumericChangeId,
  PatchRange,
  PatchSetNum,
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
import {createDefaultDiffPrefs} from '../../../constants/constants';
import {queryAll, queryAndAssert} from '../../../utils/common-util';
import {GrFileList, NormalizedFileInfo} from './gr-file-list';
import {DiffPreferencesInfo} from '../../../types/diff';
import {GrButton} from '../../shared/gr-button/gr-button';
import * as MockInteractions from '@polymer/iron-test-helpers/mock-interactions';
import {ParsedChangeInfo} from '../../../types/types';
import {GrDiffHost} from '../../diff/gr-diff-host/gr-diff-host';
import {IronIconElement} from '@polymer/iron-icon';
import {PolymerDeepPropertyChange} from '@polymer/polymer/interfaces';
import {GrEditFileControls} from '../../edit/gr-edit-file-controls/gr-edit-file-controls';

const basicFixture = fixtureFromElement('gr-file-list');

suite('gr-diff a11y test', () => {
  test('audit', async () => {
    await runA11yAudit(basicFixture);
  });
});

function createFilesByPath(count: number) {
  return Array(count)
    .fill(0)
    .reduce((_filesByPath, _, idx) => {
      _filesByPath[`'/file${idx}`] = {lines_inserted: 9};
      return _filesByPath;
    }, {});
}

suite('gr-file-list tests', () => {
  let element: GrFileList;

  let saveStub: sinon.SinonStub;

  suite('basic tests', () => {
    setup(async () => {
      stubRestApi('getDiffComments').returns(Promise.resolve({}));
      stubRestApi('getDiffRobotComments').returns(Promise.resolve({}));
      stubRestApi('getDiffDrafts').returns(Promise.resolve({}));
      stubRestApi('getAccountCapabilities').returns(Promise.resolve({}));
      stub('gr-date-formatter', '_loadTimeFormat').callsFake(() =>
        Promise.resolve()
      );
      stub('gr-diff-host', 'reload').callsFake(() => Promise.resolve());
      stub('gr-diff-host', 'prefetchDiff').callsFake(() => {});

      // Element must be wrapped in an element with direct access to the
      // comment API.
      element = basicFixture.instantiate();

      element._loading = false;
      element.diffPrefs = {} as DiffPreferencesInfo;
      element.numFilesShown = 200;
      element.patchRange = {
        basePatchNum: 'PARENT' as BasePatchSetNum,
        patchNum: 2 as RevisionPatchSetNum,
      };
      saveStub = sinon
        .stub(element, '_saveReviewedState')
        .callsFake(() => Promise.resolve());
    });

    test('renders', () => {
      expect(element).shadowDom.to.equal(/* HTML */ `<h3
          class="assistive-tech-only"
        >
          File list
        </h3>
        <div aria-label="Files list" id="container" role="grid">
          <div class="header-row row" role="row">
            <dom-if style="display: none;">
              <template is="dom-if"> </template>
            </dom-if>
            <div class="path" role="columnheader">File</div>
            <div class="comments desktop" role="columnheader">Comments</div>
            <div class="comments mobile" role="columnheader" title="Comments">
              C
            </div>
            <div class="desktop sizeBars" role="columnheader">Size</div>
            <div class="header-stats" role="columnheader">Delta</div>
            <dom-if style="display: none;">
              <template is="dom-if"> </template>
            </dom-if>
            <div
              aria-hidden="true"
              class="hideOnEdit reviewed"
              hidden="true"
            ></div>
            <div aria-hidden="true" class="editFileControls showOnEdit"></div>
            <div aria-hidden="true" class="show-hide"></div>
          </div>
          <dom-repeat
            as="file"
            id="files"
            style="display: none;"
            target-framerate="1"
          >
            <template is="dom-repeat"> </template>
          </dom-repeat>
          <dom-if style="display: none;">
            <template is="dom-if"> </template>
          </dom-if>
        </div>
        <div class="row totalChanges" hidden="true">
          <div class="total-stats">
            <div>
              <span aria-label="Total 0 lines added" class="added" tabindex="0">
                +0
              </span>
              <span
                aria-label="Total 0 lines removed"
                class="removed"
                tabindex="0"
              >
                -0
              </span>
            </div>
          </div>
          <dom-if style="display: none;">
            <template is="dom-if"> </template>
          </dom-if>
          <div class="hideOnEdit reviewed" hidden="true"></div>
          <div class="editFileControls showOnEdit"></div>
          <div class="show-hide"></div>
        </div>
        <div class="row totalChanges" hidden="true">
          <div class="total-stats">
            <span aria-label="Total bytes inserted: +/-0 B " class="added">
              +/-0 B
            </span>
            <span aria-label="Total bytes removed: +/-0 B" class="removed">
              +/-0 B
            </span>
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
        ></gr-diff-preferences-dialog>`);
    });

    test('renders file row', () => {
      element._filesByPath = createFilesByPath(1);
      flush();
      const fileRows = queryAll<HTMLDivElement>(element, '.file-row');
      expect(fileRows?.[0]).dom.equal(/* HTML */ `<div
        class="file-row row"
        data-file='{"path":"&apos;/file0"}'
        role="row"
        tabindex="-1"
      >
        <dom-if style="display: none;">
          <template is="dom-if"> </template>
        </dom-if>
        <span class="path" role="gridcell">
          <a class="pathLink">
            <span class="fullFileName" title="'/file0"> '/file0 </span>
            <span class="truncatedFileName" title="'/file0"> …/file0 </span>
            <gr-file-status-chip> </gr-file-status-chip>
            <gr-copy-clipboard hideinput=""> </gr-copy-clipboard>
          </a>
          <dom-if style="display: none;">
            <template is="dom-if"> </template>
          </dom-if>
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
            class="sizeBars"
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
            <span hidden="true"> +/-0 B </span>
          </div>
        </div>
        <dom-if style="display: none;">
          <template is="dom-if"> </template>
        </dom-if>
        <div class="hideOnEdit reviewed" hidden="true" role="gridcell">
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
        <div class="editFileControls showOnEdit" role="gridcell">
          <dom-if style="display: none;">
            <template is="dom-if"> </template>
          </dom-if>
        </div>
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
            <iron-icon class="show-hide-icon" id="icon" tabindex="-1">
            </iron-icon>
          </span>
        </div>
      </div>`);
    });

    test('correct number of files are shown', () => {
      element.fileListIncrement = 300;
      element._filesByPath = createFilesByPath(500);

      flush();
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
        'Show 300 more'
      );
      assert.equal(
        queryAndAssert<GrButton>(element, '#showAllButton').textContent!.trim(),
        'Show all 500 files'
      );

      MockInteractions.tap(queryAndAssert<GrButton>(element, '#showAllButton'));
      flush();

      assert.equal(element.numFilesShown, 500);
      assert.equal(element._shownFiles.length, 500);
      assert.isTrue(controlRow.classList.contains('invisible'));
    });

    test('rendering each row calls the _reportRenderedRow method', () => {
      const renderedStub = sinon.stub(element, '_reportRenderedRow');
      element._filesByPath = createFilesByPath(10);
      assert.equal(queryAll<HTMLDivElement>(element, '.file-row').length, 10);
      assert.equal(renderedStub.callCount, 10);
    });

    test('calculate totals for patch number', () => {
      element._filesByPath = {
        '/COMMIT_MSG': {
          lines_inserted: 9,
          size: 0,
          size_delta: 0,
        },
        '/MERGE_LIST': {
          lines_inserted: 9,
          size: 0,
          size_delta: 0,
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
          size: 0,
          size_delta: 0,
        },
        '/COMMIT_MSG': {
          lines_inserted: 9,
          size: 0,
          size_delta: 0,
        },
        '/MERGE_LIST': {
          lines_inserted: 9,
          size: 0,
          size_delta: 0,
        },
        'myfile.txt': {
          lines_inserted: 1,
          lines_deleted: 1,
          size: 0,
          size_delta: 0,
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
          size: 0,
          size_delta: 0,
        },
        'myfile.txt': {
          lines_inserted: 1,
          lines_deleted: 1,
          size: 0,
          size_delta: 0,
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
        'file_added_in_rev2.txt': {
          lines_inserted: 1,
          size: 0,
          size_delta: 0,
        },
        'myfile.txt': {
          lines_deleted: 1,
          size: 0,
          size_delta: 0,
        },
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
        '/COMMIT_MSG': {
          lines_inserted: 9,
          size: 0,
          size_delta: 0,
        },
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
        '/COMMIT_MSG': {
          lines_inserted: 9,
          size: 0,
          size_delta: 0,
        },
        file_binary_1: {binary: true, size_delta: 10, size: 100},
        file_binary_2: {binary: true, size_delta: -5, size: 120},
        'myfile.txt': {lines_deleted: 5, size_delta: -10, size: 100},
        'myfile2.txt': {
          lines_inserted: 10,
          size: 0,
          size_delta: 0,
        },
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
          {__path: '/COMMIT_MSG', size: 0, size_delta: 0}
        ),
        '2c'
      );
      assert.equal(
        element._computeCommentsStringMobile(element.changeComments, _1To2, {
          __path: '/COMMIT_MSG',
          size: 0,
          size_delta: 0,
        }),
        '3c'
      );
      assert.equal(
        element._computeDraftsString(element.changeComments, parentTo1, {
          __path: 'unresolved.file',
          size: 0,
          size_delta: 0,
        }),
        '1 draft'
      );
      assert.equal(
        element._computeDraftsString(element.changeComments, _1To2, {
          __path: 'unresolved.file',
          size: 0,
          size_delta: 0,
        }),
        '1 draft'
      );
      assert.equal(
        element._computeDraftsStringMobile(element.changeComments, parentTo1, {
          __path: 'unresolved.file',
          size: 0,
          size_delta: 0,
        }),
        '1d'
      );
      assert.equal(
        element._computeDraftsStringMobile(element.changeComments, _1To2, {
          __path: 'unresolved.file',
          size: 0,
          size_delta: 0,
        }),
        '1d'
      );
      assert.equal(
        element._computeCommentsStringMobile(
          element.changeComments,
          parentTo1,
          {__path: 'myfile.txt', size: 0, size_delta: 0}
        ),
        '1c'
      );
      assert.equal(
        element._computeCommentsStringMobile(element.changeComments, _1To2, {
          __path: 'myfile.txt',
          size: 0,
          size_delta: 0,
        }),
        '3c'
      );
      assert.equal(
        element._computeDraftsString(element.changeComments, parentTo1, {
          __path: 'myfile.txt',
          size: 0,
          size_delta: 0,
        }),
        ''
      );
      assert.equal(
        element._computeDraftsString(element.changeComments, _1To2, {
          __path: 'myfile.txt',
          size: 0,
          size_delta: 0,
        }),
        ''
      );
      assert.equal(
        element._computeDraftsStringMobile(element.changeComments, parentTo1, {
          __path: 'myfile.txt',
          size: 0,
          size_delta: 0,
        }),
        ''
      );
      assert.equal(
        element._computeDraftsStringMobile(element.changeComments, _1To2, {
          __path: 'myfile.txt',
          size: 0,
          size_delta: 0,
        }),
        ''
      );
      assert.equal(
        element._computeCommentsStringMobile(
          element.changeComments,
          parentTo1,
          {__path: 'file_added_in_rev2.txt', size: 0, size_delta: 0}
        ),
        ''
      );
      assert.equal(
        element._computeCommentsStringMobile(element.changeComments, _1To2, {
          __path: 'file_added_in_rev2.txt',
          size: 0,
          size_delta: 0,
        }),
        ''
      );
      assert.equal(
        element._computeDraftsString(element.changeComments, parentTo1, {
          __path: 'file_added_in_rev2.txt',
          size: 0,
          size_delta: 0,
        }),
        ''
      );
      assert.equal(
        element._computeDraftsString(element.changeComments, _1To2, {
          __path: 'file_added_in_rev2.txt',
          size: 0,
          size_delta: 0,
        }),
        ''
      );
      assert.equal(
        element._computeDraftsStringMobile(element.changeComments, parentTo1, {
          __path: 'file_added_in_rev2.txt',
          size: 0,
          size_delta: 0,
        }),
        ''
      );
      assert.equal(
        element._computeDraftsStringMobile(element.changeComments, _1To2, {
          __path: 'file_added_in_rev2.txt',
          size: 0,
          size_delta: 0,
        }),
        ''
      );
      assert.equal(
        element._computeCommentsStringMobile(
          element.changeComments,
          parentTo2,
          {__path: '/COMMIT_MSG', size: 0, size_delta: 0}
        ),
        '1c'
      );
      assert.equal(
        element._computeCommentsStringMobile(element.changeComments, _1To2, {
          __path: '/COMMIT_MSG',
          size: 0,
          size_delta: 0,
        }),
        '3c'
      );
      assert.equal(
        element._computeDraftsString(element.changeComments, parentTo1, {
          __path: '/COMMIT_MSG',
          size: 0,
          size_delta: 0,
        }),
        '2 drafts'
      );
      assert.equal(
        element._computeDraftsString(element.changeComments, _1To2, {
          __path: '/COMMIT_MSG',
          size: 0,
          size_delta: 0,
        }),
        '2 drafts'
      );
      assert.equal(
        element._computeDraftsStringMobile(element.changeComments, parentTo1, {
          __path: '/COMMIT_MSG',
          size: 0,
          size_delta: 0,
        }),
        '2d'
      );
      assert.equal(
        element._computeDraftsStringMobile(element.changeComments, _1To2, {
          __path: '/COMMIT_MSG',
          size: 0,
          size_delta: 0,
        }),
        '2d'
      );
      assert.equal(
        element._computeCommentsStringMobile(
          element.changeComments,
          parentTo2,
          {__path: 'myfile.txt', size: 0, size_delta: 0}
        ),
        '2c'
      );
      assert.equal(
        element._computeCommentsStringMobile(element.changeComments, _1To2, {
          __path: 'myfile.txt',
          size: 0,
          size_delta: 0,
        }),
        '3c'
      );
      assert.equal(
        element._computeDraftsStringMobile(element.changeComments, parentTo2, {
          __path: 'myfile.txt',
          size: 0,
          size_delta: 0,
        }),
        ''
      );
      assert.equal(
        element._computeDraftsStringMobile(element.changeComments, _1To2, {
          __path: 'myfile.txt',
          size: 0,
          size_delta: 0,
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
          '/COMMIT_MSG': {size: 0, size_delta: 0},
          'file_added_in_rev2.txt': {size: 0, size_delta: 0},
          'myfile.txt': {size: 0, size_delta: 0},
        };
        element.changeNum = 42 as NumericChangeId;
        element.patchRange = {
          basePatchNum: 'PARENT' as BasePatchSetNum,
          patchNum: 2 as RevisionPatchSetNum,
        };
        element.change = {_number: 42 as NumericChangeId} as ParsedChangeInfo;
        element.fileCursor.setCursorAtIndex(0);
      });

      test('toggle left diff via shortcut', () => {
        const toggleLeftDiffStub = sinon.stub();
        // Property getter cannot be stubbed w/ sandbox due to a bug in Sinon.
        // https://github.com/sinonjs/sinon/issues/781
        const diffsStub = sinon
          .stub(element, 'diffs')
          .get(() => [{toggleLeftDiff: toggleLeftDiffStub}]);
        MockInteractions.pressAndReleaseKeyOn(element, 65, null, 'A');
        assert.isTrue(toggleLeftDiffStub.calledOnce);
        diffsStub.restore();
      });

      test('keyboard shortcuts', () => {
        flush();

        const items = [...queryAll<HTMLDivElement>(element, '.file-row')];
        element.fileCursor.stops = items;
        element.fileCursor.setCursorAtIndex(0);
        assert.equal(items.length, 3);
        assert.isTrue(items[0].classList.contains('selected'));
        assert.isFalse(items[1].classList.contains('selected'));
        assert.isFalse(items[2].classList.contains('selected'));
        // j with a modifier should not move the cursor.
        MockInteractions.pressAndReleaseKeyOn(element, 74, null, 'J');
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
        MockInteractions.pressAndReleaseKeyOn(element, 75, null, 'K');
        assert.equal(element.fileCursor.index, 2);

        // up should not move the cursor.
        MockInteractions.pressAndReleaseKeyOn(element, 38, null, 'up');
        assert.equal(element.fileCursor.index, 2);

        MockInteractions.pressAndReleaseKeyOn(element, 75, null, 'k');
        assert.equal(element.fileCursor.index, 1);
        assert.equal(element.selectedIndex, 1);
        MockInteractions.pressAndReleaseKeyOn(element, 79, null, 'o');

        assert(
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
        assert.equal(element.fileCursor.index, 1);
        assert.equal(element.selectedIndex, 1);

        const createCommentInPlaceStub = sinon.stub(
          element.diffCursor,
          'createCommentInPlace'
        );
        MockInteractions.pressAndReleaseKeyOn(element, 67, null, 'c');
        assert.isTrue(createCommentInPlaceStub.called);
      });

      test('i key shows/hides selected inline diff', () => {
        const paths = Object.keys(element._filesByPath!);
        sinon.stub(element, '_expandedFilesChanged');
        flush();
        const files = [...queryAll<HTMLDivElement>(element, '.file-row')];
        element.fileCursor.stops = files;
        element.fileCursor.setCursorAtIndex(0);
        assert.equal(element.diffs.length, 0);
        assert.equal(element._expandedFiles.length, 0);

        MockInteractions.pressAndReleaseKeyOn(element, 73, null, 'i');
        flush();
        assert.equal(element.diffs.length, 1);
        assert.equal(element.diffs[0].path, paths[0]);
        assert.equal(element._expandedFiles.length, 1);
        assert.equal(element._expandedFiles[0].path, paths[0]);

        MockInteractions.pressAndReleaseKeyOn(element, 73, null, 'i');
        flush();
        assert.equal(element.diffs.length, 0);
        assert.equal(element._expandedFiles.length, 0);

        element.fileCursor.setCursorAtIndex(1);
        MockInteractions.pressAndReleaseKeyOn(element, 73, null, 'i');
        flush();
        assert.equal(element.diffs.length, 1);
        assert.equal(element.diffs[0].path, paths[1]);
        assert.equal(element._expandedFiles.length, 1);
        assert.equal(element._expandedFiles[0].path, paths[1]);

        MockInteractions.pressAndReleaseKeyOn(element, 73, null, 'I');
        flush();
        assert.equal(element.diffs.length, paths.length);
        assert.equal(element._expandedFiles.length, paths.length);
        for (const diff of element.diffs) {
          assert.isTrue(element._expandedFiles.some(f => f.path === diff.path));
        }
        // since _expandedFilesChanged is stubbed
        element.filesExpanded = FilesExpandedState.ALL;
        MockInteractions.pressAndReleaseKeyOn(element, 73, null, 'I');
        flush();
        assert.equal(element.diffs.length, 0);
        assert.equal(element._expandedFiles.length, 0);
      });

      test('r key toggles reviewed flag', () => {
        const reducer = (accum: number, file: NormalizedFileInfo) =>
          file.isReviewed ? ++accum : accum;
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

      suite('handleOpenFile', () => {
        let interact: Function;

        setup(() => {
          const openCursorStub = sinon.stub(element, '_openCursorFile');
          const openSelectedStub = sinon.stub(element, '_openSelectedFile');
          const expandStub = sinon.stub(element, '_toggleFileExpanded');

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
        const moveLeftStub = sinon.stub(element.diffCursor, 'moveLeft');
        const moveRightStub = sinon.stub(element.diffCursor, 'moveRight');

        let noDiffsExpanded = true;
        sinon
          .stub(element, '_noDiffsExpanded')
          .callsFake(() => noDiffsExpanded);

        MockInteractions.pressAndReleaseKeyOn(
          element,
          73,
          'shift',
          'ArrowLeft'
        );
        assert.isFalse(moveLeftStub.called);
        MockInteractions.pressAndReleaseKeyOn(
          element,
          73,
          'shift',
          'ArrowRight'
        );
        assert.isFalse(moveRightStub.called);

        noDiffsExpanded = false;

        MockInteractions.pressAndReleaseKeyOn(
          element,
          73,
          'shift',
          'ArrowLeft'
        );
        assert.isTrue(moveLeftStub.called);
        MockInteractions.pressAndReleaseKeyOn(
          element,
          73,
          'shift',
          'ArrowRight'
        );
        assert.isTrue(moveRightStub.called);
      });
    });

    test('file review status', () => {
      element.reviewed = ['/COMMIT_MSG', 'myfile.txt'];
      element._filesByPath = {
        '/COMMIT_MSG': {size: 0, size_delta: 0},
        'file_added_in_rev2.txt': {size: 0, size_delta: 0},
        'myfile.txt': {size: 0, size_delta: 0},
      };
      element._loggedIn = true;
      element.changeNum = 42 as NumericChangeId;
      element.patchRange = {
        basePatchNum: 'PARENT' as BasePatchSetNum,
        patchNum: 2 as RevisionPatchSetNum,
      };
      element.fileCursor.setCursorAtIndex(0);
      const reviewSpy = sinon.spy(element, '_reviewFile');
      const toggleExpandSpy = sinon.spy(element, '_toggleFileExpanded');

      flush();
      queryAll(element, '.row:not(.header-row)');
      const fileRows = queryAll(element, '.row:not(.header-row)');
      const checkSelector = 'span.reviewedSwitch[role="switch"]';
      const commitMsg = fileRows[0].querySelector(checkSelector);
      const fileAdded = fileRows[1].querySelector(checkSelector);
      const myFile = fileRows[2].querySelector(checkSelector);

      assert.equal(commitMsg!.getAttribute('aria-checked'), 'true');
      assert.equal(fileAdded!.getAttribute('aria-checked'), 'false');
      assert.equal(myFile!.getAttribute('aria-checked'), 'true');

      const commitReviewLabel = fileRows[0].querySelector('.reviewedLabel');
      const markReviewLabel = fileRows[0].querySelector('.markReviewed');
      assert.isTrue(commitReviewLabel!.classList.contains('isReviewed'));
      assert.equal(markReviewLabel!.textContent, 'MARK UNREVIEWED');

      const clickSpy = sinon.spy(element, '_reviewedClick');
      MockInteractions.tap(markReviewLabel!);
      // assert.isTrue(saveStub.lastCall.calledWithExactly('/COMMIT_MSG', false));
      // assert.isFalse(commitReviewLabel.classList.contains('isReviewed'));
      assert.equal(markReviewLabel!.textContent, 'MARK REVIEWED');
      assert.isTrue(clickSpy.lastCall.args[0].defaultPrevented);
      assert.isTrue(reviewSpy.calledOnce);

      MockInteractions.tap(markReviewLabel!);
      assert.isTrue(saveStub.lastCall.calledWithExactly('/COMMIT_MSG', true));
      assert.isTrue(commitReviewLabel!.classList.contains('isReviewed'));
      assert.equal(markReviewLabel!.textContent, 'MARK UNREVIEWED');
      assert.isTrue(clickSpy.lastCall.args[0].defaultPrevented);
      assert.isTrue(reviewSpy.calledTwice);

      assert.isFalse(toggleExpandSpy.called);
    });

    test('_handleFileListClick', () => {
      element._filesByPath = {
        '/COMMIT_MSG': {size: 0, size_delta: 0},
        'f1.txt': {size: 0, size_delta: 0},
        'f2.txt': {size: 0, size_delta: 0},
      };
      element.changeNum = 42 as NumericChangeId;
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

    test('_handleFileListClick editMode', () => {
      element._filesByPath = {
        '/COMMIT_MSG': {size: 0, size_delta: 0},
        'f1.txt': {size: 0, size_delta: 0},
        'f2.txt': {size: 0, size_delta: 0},
      };
      element.changeNum = 42 as NumericChangeId;
      element.patchRange = {
        basePatchNum: 'PARENT' as BasePatchSetNum,
        patchNum: 2 as RevisionPatchSetNum,
      };
      element.editMode = true;
      flush();
      const clickSpy = sinon.spy(element, '_handleFileListClick');
      const toggleExpandSpy = sinon.spy(element, '_toggleFileExpanded');

      // Tap the edit controls. Should be ignored by _handleFileListClick.
      MockInteractions.tap(queryAndAssert(element, '.editFileControls'));
      assert.isTrue(clickSpy.calledOnce);
      assert.isFalse(toggleExpandSpy.called);
    });

    test('checkbox shows/hides diff inline', () => {
      element._filesByPath = {
        'myfile.txt': {size: 0, size_delta: 0},
      };
      element.changeNum = 42 as NumericChangeId;
      element.patchRange = {
        basePatchNum: 'PARENT' as BasePatchSetNum,
        patchNum: 2 as RevisionPatchSetNum,
      };
      element.fileCursor.setCursorAtIndex(0);
      sinon.stub(element, '_expandedFilesChanged');
      flush();
      const fileRows = queryAll(element, '.row:not(.header-row)');
      // Because the label surrounds the input, the tap event is triggered
      // there first.
      const showHideCheck = fileRows[0].querySelector(
        'span.show-hide[role="switch"]'
      );
      const showHideLabel = showHideCheck!.querySelector('.show-hide-icon');
      assert.equal(showHideCheck!.getAttribute('aria-checked'), 'false');
      MockInteractions.tap(showHideLabel!);
      assert.equal(showHideCheck!.getAttribute('aria-checked'), 'true');
      assert.notEqual(
        element._expandedFiles.findIndex(f => f.path === 'myfile.txt'),
        -1
      );
    });

    test('diff mode correctly toggles the diffs', () => {
      element._filesByPath = {
        'myfile.txt': {size: 0, size_delta: 0},
      };
      element.changeNum = 42 as NumericChangeId;
      element.patchRange = {
        basePatchNum: 'PARENT' as BasePatchSetNum,
        patchNum: 2 as RevisionPatchSetNum,
      };
      const updateDiffPrefSpy = sinon.spy(element, '_updateDiffPreferences');
      element.fileCursor.setCursorAtIndex(0);
      flush();

      // Tap on a file to generate the diff.
      const row = queryAll(element, '.row:not(.header-row) span.show-hide')[0];

      MockInteractions.tap(row);
      flush();
      element.set('diffViewMode', 'UNIFIED_DIFF');
      assert.isTrue(updateDiffPrefSpy.called);
    });

    test('expanded attribute not set on path when not expanded', () => {
      element._filesByPath = {
        '/COMMIT_MSG': {size: 0, size_delta: 0},
      };
      assert.isNotOk(query(element, 'expanded'));
    });

    test('tapping row ignores links', () => {
      element._filesByPath = {
        '/COMMIT_MSG': {size: 0, size_delta: 0},
      };
      element.changeNum = 42 as NumericChangeId;
      element.patchRange = {
        basePatchNum: 'PARENT' as BasePatchSetNum,
        patchNum: 2 as RevisionPatchSetNum,
      };
      sinon.stub(element, '_expandedFilesChanged');
      flush();
      const commitMsgFile = queryAll(
        element,
        '.row:not(.header-row) a.pathLink'
      )[0];

      // Remove href attribute so the app doesn't route to a diff view
      commitMsgFile.removeAttribute('href');
      const togglePathSpy = sinon.spy(element, '_toggleFileExpanded');

      MockInteractions.tap(commitMsgFile);
      flush();
      assert(togglePathSpy.notCalled, 'file is opened as diff view');
      assert.isNotOk(query(element, '.expanded'));
      assert.notEqual(
        getComputedStyle(queryAndAssert(element, '.show-hide')).display,
        'none'
      );
    });

    test('_toggleFileExpanded', () => {
      const path = 'path/to/my/file.txt';
      element._filesByPath = {[path]: {size: 0, size_delta: 0}};
      const renderSpy = sinon.spy(element, '_renderInOrder');
      const collapseStub = sinon.stub(element, '_clearCollapsedDiffs');

      assert.equal(
        queryAndAssert<IronIconElement>(element, 'iron-icon').icon,
        'gr-icons:expand-more'
      );
      assert.equal(element._expandedFiles.length, 0);
      element._toggleFileExpanded({path});
      flush();
      assert.equal(collapseStub.lastCall.args[0].length, 0);
      assert.equal(
        queryAndAssert<IronIconElement>(element, 'iron-icon').icon,
        'gr-icons:expand-less'
      );

      assert.equal(renderSpy.callCount, 1);
      assert.isTrue(element._expandedFiles.some(f => f.path === path));
      element._toggleFileExpanded({path});
      flush();

      assert.equal(
        queryAndAssert<IronIconElement>(element, 'iron-icon').icon,
        'gr-icons:expand-more'
      );
      assert.equal(renderSpy.callCount, 1);
      assert.isFalse(element._expandedFiles.some(f => f.path === path));
      assert.equal(collapseStub.lastCall.args[0].length, 1);
    });

    test('expandAllDiffs and collapseAllDiffs', () => {
      const collapseStub = sinon.stub(element, '_clearCollapsedDiffs');
      const cursorUpdateStub = sinon.stub(
        element.diffCursor,
        'handleDiffUpdate'
      );
      const reInitStub = sinon.stub(element.diffCursor, 'reInitAndUpdateStops');

      const path = 'path/to/my/file.txt';
      element._filesByPath = {[path]: {size: 0, size_delta: 0}};
      element.expandAllDiffs();
      flush();
      assert.equal(element.filesExpanded, FilesExpandedState.ALL);
      assert.isTrue(reInitStub.calledOnce);
      assert.equal(collapseStub.lastCall.args[0].length, 0);

      element.collapseAllDiffs();
      flush();
      assert.equal(element._expandedFiles.length, 0);
      assert.equal(element.filesExpanded, FilesExpandedState.NONE);
      assert.isTrue(cursorUpdateStub.calledOnce);
      assert.equal(collapseStub.lastCall.args[0].length, 1);
    });

    test('_expandedFilesChanged', async () => {
      sinon.stub(element, '_reviewFile');
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
      element.push('_expandedFiles', {path});
      await promise;
    });

    test('_clearCollapsedDiffs', () => {
      // Have to type as any because the type is 'GrDiffHost'
      // which would require stubbing so many different
      // methods / properties that it isn't worth it.
      const diff = {
        cancel: sinon.stub(),
        clearDiffContent: sinon.stub(),
      } as any;
      element._clearCollapsedDiffs([diff]);
      assert.isTrue(diff.cancel.calledOnce);
      assert.isTrue(diff.clearDiffContent.calledOnce);
    });

    test('filesExpanded value updates to correct enum', () => {
      element._filesByPath = {
        'foo.bar': {size: 0, size_delta: 0},
        'baz.bar': {size: 0, size_delta: 0},
      };
      flush();
      assert.equal(element.filesExpanded, FilesExpandedState.NONE);
      element.push('_expandedFiles', {path: 'baz.bar'});
      flush();
      assert.equal(element.filesExpanded, FilesExpandedState.SOME);
      element.push('_expandedFiles', {path: 'foo.bar'});
      flush();
      assert.equal(element.filesExpanded, FilesExpandedState.ALL);
      element.collapseAllDiffs();
      flush();
      assert.equal(element.filesExpanded, FilesExpandedState.NONE);
      element.expandAllDiffs();
      flush();
      assert.equal(element.filesExpanded, FilesExpandedState.ALL);
    });

    test('_renderInOrder', async () => {
      const reviewStub = sinon.stub(element, '_reviewFile');
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
      element._renderInOrder(
        [{path: 'p2'}, {path: 'p1'}, {path: 'p0'}],
        diffs,
        3
      );
      await flush();
      assert.isFalse(reviewStub.called);
    });

    test('_renderInOrder logged in', async () => {
      element._loggedIn = true;
      const reviewStub = sinon.stub(element, '_reviewFile');
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
      element._renderInOrder([{path: 'p2'}], diffs, 1);
      await flush();
      assert.equal(reviewStub.callCount, 1);
    });

    test('_renderInOrder respects diffPrefs.manual_review', async () => {
      element._loggedIn = true;
      element.diffPrefs = {manual_review: true} as DiffPreferencesInfo;
      const reviewStub = sinon.stub(element, '_reviewFile');
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

      element._renderInOrder([{path: 'p'}], diffs, 1);
      await flush();
      assert.isFalse(reviewStub.called);
      delete element.diffPrefs.manual_review;
      element._renderInOrder([{path: 'p'}], diffs, 1);
      await flush();
      assert.isTrue(reviewStub.called);
      assert.isTrue(reviewStub.calledWithExactly('p', true));
    });

    test('_loadingChanged fired from reload in debouncer', async () => {
      const reloadBlocker = mockPromise();
      stubRestApi('getChangeOrEditFiles').resolves({
        'foo.bar': {size: 0, size_delta: 0},
      });
      stubRestApi('getReviewedFiles').resolves(undefined);
      stubRestApi('getDiffPreferences').resolves(createDefaultDiffPrefs());
      stubRestApi('getLoggedIn').returns(reloadBlocker.then(() => false));

      element.changeNum = 123 as NumericChangeId;
      element.patchRange = {patchNum: 12 as RevisionPatchSetNum} as PatchRange;
      element._filesByPath = {'foo.bar': {size: 0, size_delta: 0}};
      element.change = {
        ...createParsedChange(),
        _number: 123 as NumericChangeId,
      };

      const reloaded = element.reload();
      assert.isTrue(element._loading);
      assert.isFalse(element.classList.contains('loading'));
      element.loadingTask!.flush();
      assert.isTrue(element.classList.contains('loading'));

      reloadBlocker.resolve();
      await reloaded;

      assert.isFalse(element._loading);
      element.loadingTask!.flush();
      assert.isFalse(element.classList.contains('loading'));
    });

    test('_loadingChanged does not set class when there are no files', () => {
      const reloadBlocker = mockPromise();
      stubRestApi('getLoggedIn').returns(reloadBlocker.then(() => false));
      sinon.stub(element, '_getReviewedFiles').resolves([]);
      element.changeNum = 123 as NumericChangeId;
      element.patchRange = {patchNum: 12 as RevisionPatchSetNum} as PatchRange;
      element.change = {
        ...createParsedChange(),
        _number: 123 as NumericChangeId,
      };
      element.reload();

      assert.isTrue(element._loading);

      element.loadingTask!.flush();

      assert.isFalse(element.classList.contains('loading'));
    });

    suite('for merge commits', () => {
      let filesStub: sinon.SinonStub;

      setup(async () => {
        filesStub = stubRestApi('getChangeOrEditFiles')
          .onFirstCall()
          .resolves({'conflictingFile.js': {size: 0, size_delta: 0}})
          .onSecondCall()
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
        element.patchRange = {
          basePatchNum: 'PARENT' as BasePatchSetNum,
          patchNum: 1 as RevisionPatchSetNum,
        };
        await flush();
      });

      test('displays cleanly merged file count', async () => {
        await element.reload();
        await flush();

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
          .resolves({'conflictingFile.js': {size: 0, size_delta: 0}})
          .onSecondCall()
          .resolves({
            'conflictingFile.js': {size: 0, size_delta: 0},
            'cleanlyMergedFile.js': {size: 0, size_delta: 0},
            'anotherCleanlyMergedFile.js': {size: 0, size_delta: 0},
          });
        await element.reload();
        await flush();

        const message = queryAndAssert(
          element,
          '.cleanlyMergedText'
        ).textContent!.trim();
        assert.equal(message, '2 files merged cleanly in Parent 1');
      });

      test('displays button for navigating to parent 1 base', async () => {
        await element.reload();
        await flush();

        queryAndAssert(element, '.showParentButton');
      });

      test('computes old paths for cleanly merged files', async () => {
        filesStub.restore();
        stubRestApi('getChangeOrEditFiles')
          .onFirstCall()
          .resolves({'conflictingFile.js': {size: 0, size_delta: 0}})
          .onSecondCall()
          .resolves({
            'conflictingFile.js': {size: 0, size_delta: 0},
            'cleanlyMergedFile.js': {
              old_path: 'cleanlyMergedFileOldName.js',
              size: 0,
              size_delta: 0,
            },
          });
        await element.reload();
        await flush();

        assert.deepEqual(element._cleanlyMergedOldPaths, [
          'cleanlyMergedFileOldName.js',
        ]);
      });

      test('not shown for non-Auto Merge base parents', async () => {
        element.patchRange = {
          basePatchNum: 1 as BasePatchSetNum,
          patchNum: 2 as RevisionPatchSetNum,
        };
        await element.reload();
        await flush();

        assert.notOk(query(element, '.cleanlyMergedText'));
        assert.notOk(query(element, '.showParentButton'));
      });

      test('not shown in edit mode', async () => {
        element.patchRange = {
          basePatchNum: 1 as BasePatchSetNum,
          patchNum: EditPatchSetNum,
        };
        await element.reload();
        await flush();

        assert.notOk(query(element, '.cleanlyMergedText'));
        assert.notOk(query(element, '.showParentButton'));
      });
    });
  });

  suite('diff url file list', () => {
    test('diff url', () => {
      const diffStub = sinon
        .stub(GerritNav, 'getUrlForDiff')
        .returns('/c/gerrit/+/1/1/index.php');
      const change = {
        ...createParsedChange(),
        _number: 1 as NumericChangeId,
        project: 'gerrit' as RepoName,
      };
      const path = 'index.php';
      assert.equal(
        element._computeDiffURL(
          change,
          undefined,
          1 as RevisionPatchSetNum,
          path,
          false
        ),
        '/c/gerrit/+/1/1/index.php'
      );
      diffStub.restore();
    });

    test('diff url commit msg', () => {
      const diffStub = sinon
        .stub(GerritNav, 'getUrlForDiff')
        .returns('/c/gerrit/+/1/1//COMMIT_MSG');
      const change = {
        ...createParsedChange(),
        _number: 1 as NumericChangeId,
        project: 'gerrit' as RepoName,
      };
      const path = '/COMMIT_MSG';
      assert.equal(
        element._computeDiffURL(
          change,
          undefined,
          1 as RevisionPatchSetNum,
          path,
          false
        ),
        '/c/gerrit/+/1/1//COMMIT_MSG'
      );
      diffStub.restore();
    });

    test('edit url', () => {
      const editStub = sinon
        .stub(GerritNav, 'getEditUrlForDiff')
        .returns('/c/gerrit/+/1/edit/index.php,edit');
      const change = {
        ...createParsedChange(),
        _number: 1 as NumericChangeId,
        project: 'gerrit' as RepoName,
      };
      const path = 'index.php';
      assert.equal(
        element._computeDiffURL(
          change,
          undefined,
          1 as RevisionPatchSetNum,
          path,
          true
        ),
        '/c/gerrit/+/1/edit/index.php,edit'
      );
      editStub.restore();
    });

    test('edit url commit msg', () => {
      const editStub = sinon
        .stub(GerritNav, 'getEditUrlForDiff')
        .returns('/c/gerrit/+/1/edit//COMMIT_MSG,edit');
      const change = {
        ...createParsedChange(),
        _number: 1 as NumericChangeId,
        project: 'gerrit' as RepoName,
      };
      const path = '/COMMIT_MSG';
      assert.equal(
        element._computeDiffURL(
          change,
          undefined,
          1 as RevisionPatchSetNum,
          path,
          true
        ),
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

      assert.deepEqual(
        element._computeSizeBarLayout(undefined),
        defaultSizeBarLayout
      );
      assert.deepEqual(
        element._computeSizeBarLayout(
          {} as PolymerDeepPropertyChange<
            NormalizedFileInfo[],
            NormalizedFileInfo[]
          >
        ),
        defaultSizeBarLayout
      );
      assert.deepEqual(
        element._computeSizeBarLayout({base: []} as any),
        defaultSizeBarLayout
      );

      const files = [
        {__path: '/COMMIT_MSG', lines_inserted: 10000},
        {__path: 'foo', lines_inserted: 4, lines_deleted: 10},
        {__path: 'bar', lines_inserted: 5, lines_deleted: 8},
      ];
      const layout = element._computeSizeBarLayout({
        base: files,
      } as PolymerDeepPropertyChange<NormalizedFileInfo[], NormalizedFileInfo[]>);
      assert.equal(layout.maxInserted, 5);
      assert.equal(layout.maxDeleted, 10);
    });

    test('_computeBarAdditionWidth', () => {
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
      assert.equal(element._computeBarAdditionWidth(file, stats), 30);

      // If there are no insertions, there is no width.
      stats.maxInserted = 0;
      assert.equal(element._computeBarAdditionWidth(file, stats), 0);

      // If the insertions is not present on the file, there is no width.
      stats.maxInserted = 10;
      file.lines_inserted = 0;
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
      assert.equal(element._computeBarAdditionX(file, stats), 30);
    });

    test('_computeBarDeletionWidth', () => {
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
      assert.equal(element._computeBarDeletionWidth(file, stats), 15);

      // If there are no deletions, there is no width.
      stats.maxDeleted = 0;
      assert.equal(element._computeBarDeletionWidth(file, stats), 0);

      // If the deletions is not present on the file, there is no width.
      stats.maxDeleted = 10;
      file.lines_deleted = 0;
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
        'sizeBars hide'
      );
      assert.equal(
        element._computeSizeBarsClass(true, '/COMMIT_MSG'),
        'sizeBars invisible'
      );
      assert.equal(
        element._computeSizeBarsClass(true, 'foo/bar.baz'),
        'sizeBars '
      );
    });
  });

  suite('gr-file-list inline diff tests', () => {
    let element: GrFileList;
    let reviewFileStub: sinon.SinonStub;

    const commitMsgComments = [
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
      diff.diff = createDiff();
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
      stubRestApi('getPreferences').returns(Promise.resolve(undefined));
      stubRestApi('getDiffComments').returns(Promise.resolve({}));
      stubRestApi('getDiffRobotComments').returns(Promise.resolve({}));
      stubRestApi('getDiffDrafts').returns(Promise.resolve({}));
      stub('gr-date-formatter', '_loadTimeFormat').callsFake(() =>
        Promise.resolve()
      );
      stub('gr-diff-host', 'reload').callsFake(() => Promise.resolve());
      stub('gr-diff-host', 'prefetchDiff').callsFake(() => {});

      // Element must be wrapped in an element with direct access to the
      // comment API.
      element = basicFixture.instantiate();
      element.diffPrefs = {} as DiffPreferencesInfo;
      element.change = {
        ...createParsedChange(),
        _number: 42 as NumericChangeId,
        project: 'testRepo' as RepoName,
      };
      reviewFileStub = sinon.stub(element, '_reviewFile');

      element._loading = false;
      element.numFilesShown = 75;
      element.selectedIndex = 0;
      element._filesByPath = {
        '/COMMIT_MSG': {lines_inserted: 9, size: 0, size_delta: 0},
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
      element.reviewed = ['/COMMIT_MSG', 'myfile.txt'];
      element._loggedIn = true;
      element.changeNum = 42 as NumericChangeId;
      element.patchRange = {
        basePatchNum: 'PARENT' as BasePatchSetNum,
        patchNum: 2 as RevisionPatchSetNum,
      };
      sinon
        .stub(window, 'fetch')
        .callsFake(() => Promise.resolve(new Response()));
      await flush();
    });

    test('cursor with individually opened files', async () => {
      MockInteractions.pressAndReleaseKeyOn(element, 73, null, 'i');
      await flush();
      let diffs = await renderAndGetNewDiffs(0);
      const diffStops = diffs[0].getCursorStops();

      // 1 diff should be rendered.
      assert.equal(diffs.length, 1);

      // No line number is selected.
      assert.isFalse(
        (diffStops[10] as HTMLElement).classList.contains('target-row')
      );

      // Tapping content on a line selects the line number.
      MockInteractions.tap(
        queryAll(diffStops[10] as HTMLElement, '.contentText')[0]
      );
      await flush();
      assert.isTrue(
        (diffStops[10] as HTMLElement).classList.contains('target-row')
      );

      // Keyboard shortcuts are still moving the file cursor, not the diff
      // cursor.
      MockInteractions.pressAndReleaseKeyOn(element, 74, null, 'j');
      await flush();
      assert.isTrue(
        (diffStops[10] as HTMLElement).classList.contains('target-row')
      );
      assert.isFalse(
        (diffStops[11] as HTMLElement).classList.contains('target-row')
      );

      // The file cursor is now at 1.
      assert.equal(element.fileCursor.index, 1);

      MockInteractions.pressAndReleaseKeyOn(element, 73, null, 'i');
      await flush();
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
      MockInteractions.pressAndReleaseKeyOn(element, 73, null, 'I');
      await flush();

      const diffs = await renderAndGetNewDiffs(0);
      const diffStops = diffs[0].getCursorStops();

      // 1 diff should be rendered.
      assert.equal(diffs.length, 3);

      // No line number is selected.
      assert.isFalse(
        (diffStops[10] as HTMLElement).classList.contains('target-row')
      );

      // Tapping content on a line selects the line number.
      MockInteractions.tap(
        queryAll(diffStops[10] as HTMLElement, '.contentText')[0]
      );
      await flush();
      assert.isTrue(
        (diffStops[10] as HTMLElement).classList.contains('target-row')
      );

      // Keyboard shortcuts are still moving the file cursor, not the diff
      // cursor.
      MockInteractions.pressAndReleaseKeyOn(element, 74, null, 'j');
      await flush();
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
        sinon.stub(element, '_renderInOrder').returns(Promise.resolve());
        nextCommentStub = sinon.stub(
          element.diffCursor,
          'moveToNextCommentThread'
        );
        nextChunkStub = sinon.stub(element.diffCursor, 'moveToNextChunk');
        fileRows = queryAll<HTMLDivElement>(element, '.row:not(.header-row)');
      });

      test('n key with some files expanded', async () => {
        MockInteractions.pressAndReleaseKeyOn(fileRows[0], 73, null, 'i');
        await flush();
        assert.equal(element.filesExpanded, FilesExpandedState.SOME);

        MockInteractions.pressAndReleaseKeyOn(element, 78, null, 'n');
        assert.isTrue(nextChunkStub.calledOnce);
      });

      test('N key with some files expanded', async () => {
        MockInteractions.pressAndReleaseKeyOn(fileRows[0], 73, null, 'i');
        await flush();
        assert.equal(element.filesExpanded, FilesExpandedState.SOME);

        MockInteractions.pressAndReleaseKeyOn(element, 78, null, 'N');
        assert.isTrue(nextCommentStub.calledOnce);
      });

      test('n key with all files expanded', async () => {
        MockInteractions.pressAndReleaseKeyOn(fileRows[0], 73, null, 'I');
        await flush();
        assert.equal(element.filesExpanded, FilesExpandedState.ALL);

        MockInteractions.pressAndReleaseKeyOn(element, 78, null, 'n');
        assert.isTrue(nextChunkStub.calledOnce);
      });

      test('N key with all files expanded', async () => {
        MockInteractions.pressAndReleaseKeyOn(fileRows[0], 73, null, 'I');
        await flush();
        assert.equal(element.filesExpanded, FilesExpandedState.ALL);

        MockInteractions.pressAndReleaseKeyOn(element, 78, null, 'N');
        assert.isTrue(nextCommentStub.called);
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
      element.filesExpanded = FilesExpandedState.ALL;

      element._displayLine = false;
      element._handleCursorNext(new KeyboardEvent('keydown'));
      assert.isTrue(element._displayLine);

      element._displayLine = false;
      element._handleCursorPrev(new KeyboardEvent('keydown'));
      assert.isTrue(element._displayLine);

      element._displayLine = true;
      element._handleEscKey();
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

      test('_getReviewedFiles does not call API', () => {
        const apiSpy = spyRestApi('getReviewedFiles');
        element.editMode = true;
        return element
          ._getReviewedFiles(0 as NumericChangeId, {patchNum: 0} as PatchRange)
          .then(files => {
            assert.equal(files!.length, 0);
            assert.isFalse(apiSpy.called);
          });
      });
    });

    test('editing actions', async () => {
      // Edit controls are guarded behind a dom-if initially and not rendered.
      assert.isNotOk(
        query<GrEditFileControls>(element, 'gr-edit-file-controls')
      );

      element.editMode = true;
      await flush();

      // Commit message should not have edit controls.
      const editControls = Array.from(
        queryAll(element, '.row:not(.header-row)')
      ).map(row => row.querySelector('gr-edit-file-controls'));
      assert.isTrue(editControls[0]!.classList.contains('invisible'));
    });
  });
});
