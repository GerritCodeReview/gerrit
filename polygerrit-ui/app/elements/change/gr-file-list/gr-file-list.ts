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
import '../../../styles/gr-a11y-styles';
import '../../../styles/shared-styles';
import '../../../embed/diff/gr-diff-cursor/gr-diff-cursor';
import '../../diff/gr-diff-host/gr-diff-host';
import '../../diff/gr-diff-preferences-dialog/gr-diff-preferences-dialog';
import '../../edit/gr-edit-file-controls/gr-edit-file-controls';
import '../../shared/gr-button/gr-button';
import '../../shared/gr-cursor-manager/gr-cursor-manager';
import '../../shared/gr-icons/gr-icons';
import '../../shared/gr-linked-text/gr-linked-text';
import '../../shared/gr-select/gr-select';
import '../../shared/gr-tooltip-content/gr-tooltip-content';
import '../../shared/gr-copy-clipboard/gr-copy-clipboard';
import '../../shared/gr-file-status-chip/gr-file-status-chip';
import {asyncForeach, debounce, DelayedTask} from '../../../utils/async-util';
import {FilesExpandedState} from '../gr-file-list-constants';
import {pluralize} from '../../../utils/string-util';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {getPluginEndpoints} from '../../shared/gr-js-api-interface/gr-plugin-endpoints';
import {getPluginLoader} from '../../shared/gr-js-api-interface/gr-plugin-loader';
import {getAppContext} from '../../../services/app-context';
import {
  DiffViewMode,
  ScrollMode,
  SpecialFilePath,
} from '../../../constants/constants';
import {
  addGlobalShortcut,
  addShortcut,
  descendedFromClass,
  Key,
  toggleClass,
} from '../../../utils/dom-util';
import {
  addUnmodifiedFiles,
  computeDisplayPath,
  computeTruncatedPath,
  isMagicPath,
  specialFilePathCompare,
} from '../../../utils/path-list-util';
import {customElement, property, query, state} from 'lit/decorators';
import {
  BasePatchSetNum,
  EditPatchSetNum,
  FileInfo,
  FileNameToFileInfoMap,
  NumericChangeId,
  PatchRange,
} from '../../../types/common';
import {DiffPreferencesInfo} from '../../../types/diff';
import {GrDiffHost} from '../../diff/gr-diff-host/gr-diff-host';
import {GrDiffPreferencesDialog} from '../../diff/gr-diff-preferences-dialog/gr-diff-preferences-dialog';
import {GrDiffCursor} from '../../../embed/diff/gr-diff-cursor/gr-diff-cursor';
import {GrCursorManager} from '../../shared/gr-cursor-manager/gr-cursor-manager';
import {ChangeComments} from '../../diff/gr-comment-api/gr-comment-api';
import {ParsedChangeInfo, PatchSetFile} from '../../../types/types';
import {Timing} from '../../../constants/reporting';
import {RevisionInfo} from '../../shared/revision-info/revision-info';
import {select} from '../../../utils/observable-util';
import {resolve} from '../../../models/dependency';
import {browserModelToken} from '../../../models/browser/browser-model';
import {commentsModelToken} from '../../../models/comments/comments-model';
import {changeModelToken} from '../../../models/change/change-model';
import {ShortcutController} from '../../lit/shortcut-controller';
import {css, html, LitElement, nothing, PropertyValues} from 'lit';
import {Shortcut} from '../../../services/shortcuts/shortcuts-config';
import {fire} from '../../../utils/event-util';
import {a11yStyles} from '../../../styles/gr-a11y-styles';
import {sharedStyles} from '../../../styles/shared-styles';
import {ValueChangedEvent} from '../../../types/events';
import {subscribe} from '../../lit/subscription-controller';
import {when} from 'lit/directives/when';
import {incrementalRepeat} from '../../lit/incremental-repeat';
import {ifDefined} from 'lit/directives/if-defined';

export const DEFAULT_NUM_FILES_SHOWN = 200;

const WARN_SHOW_ALL_THRESHOLD = 1000;
const LOADING_DEBOUNCE_INTERVAL = 100;

const SIZE_BAR_MAX_WIDTH = 61;
const SIZE_BAR_GAP_WIDTH = 1;
const SIZE_BAR_MIN_WIDTH = 1.5;

const FILE_ROW_CLASS = 'file-row';

interface ReviewedFileInfo extends FileInfo {
  isReviewed?: boolean;
}

export interface NormalizedFileInfo extends ReviewedFileInfo {
  __path: string;
}

interface PatchChange {
  inserted: number;
  deleted: number;
  size_delta_inserted: number;
  size_delta_deleted: number;
  total_size: number;
}

function createDefaultPatchChange(): PatchChange {
  // Use function instead of const to prevent unexpected changes in the default
  // values.
  return {
    inserted: 0,
    deleted: 0,
    size_delta_inserted: 0,
    size_delta_deleted: 0,
    total_size: 0,
  };
}

interface SizeBarLayout {
  maxInserted: number;
  maxDeleted: number;
  maxAdditionWidth: number;
  maxDeletionWidth: number;
  deletionOffset: number;
}

function createDefaultSizeBarLayout(): SizeBarLayout {
  // Use function instead of const to prevent unexpected changes in the default
  // values.
  return {
    maxInserted: 0,
    maxDeleted: 0,
    maxAdditionWidth: 0,
    maxDeletionWidth: 0,
    deletionOffset: 0,
  };
}

interface FileRow {
  file: PatchSetFile;
  element: HTMLElement;
}

export type FileNameToReviewedFileInfoMap = {[name: string]: ReviewedFileInfo};

/**
 * Type for FileInfo
 *
 * This should match with the type returned from `files` API plus
 * additional info like `__path`.
 *
 * @typedef {Object} FileInfo
 * @property {string} __path
 * @property {?string} old_path
 * @property {number} size
 * @property {number} size_delta - fallback to 0 if not present in api
 * @property {number} lines_deleted - fallback to 0 if not present in api
 * @property {number} lines_inserted - fallback to 0 if not present in api
 */

@customElement('gr-file-list')
export class GrFileList extends LitElement {
  /**
   * @event selected-index-changed
   * @event files-expanded-changed
   * @event num-files-shown-changed
   * @event diff-prefs-changed
   */
  @query('#diffPreferencesDialog')
  diffPreferencesDialog?: GrDiffPreferencesDialog;

  @property({type: Object})
  patchRange?: PatchRange;

  @property({type: String})
  patchNum?: string;

  @property({type: Number})
  changeNum?: NumericChangeId;

  @property({type: Object})
  changeComments?: ChangeComments;

  @property({type: Number, attribute: 'selected-index'})
  selectedIndex = -1;

  @property({type: Object})
  change?: ParsedChangeInfo;

  @property({type: String})
  diffViewMode?: DiffViewMode;

  @property({type: Boolean})
  editMode?: boolean;

  @property({type: String, attribute: 'files-expanded'})
  filesExpanded = FilesExpandedState.NONE;

  // Private but used in tests.
  @state()
  filesByPath?: FileNameToFileInfoMap;

  // Private but used in tests.
  @state()
  files: NormalizedFileInfo[] = [];

  // Private but used in tests.
  @state()
  loggedIn = false;

  @property({type: Array})
  reviewed?: string[] = [];

  @property({type: Object, attribute: 'diff-prefs'})
  diffPrefs?: DiffPreferencesInfo;

  @property({type: Number, attribute: 'num-files-shown'})
  numFilesShown: number = DEFAULT_NUM_FILES_SHOWN;

  @property({type: Number, attribute: 'file-list-increment'})
  fileListIncrement: number = DEFAULT_NUM_FILES_SHOWN;

  // Private but used in tests.
  shownFiles: NormalizedFileInfo[] = [];

  @state()
  private reportinShownFilesIncrement = 0;

  // Private but used in tests.
  @state()
  expandedFiles: PatchSetFile[] = [];

  // Private but used in tests.
  @state()
  displayLine?: boolean;

  @state()
  loading?: boolean;

  // Private but used in tests.
  @state()
  showSizeBars = true;

  // For merge commits vs Auto Merge, an extra file row is shown detailing the
  // files that were merged without conflict. These files are also passed to any
  // plugins.
  @state()
  private cleanlyMergedPaths: string[] = [];

  // Private but used in tests.
  @state()
  cleanlyMergedOldPaths: string[] = [];

  private cancelForEachDiff?: () => void;

  loadingTask?: DelayedTask;

  @state()
  private dynamicHeaderEndpoints?: string[];

  @state()
  private dynamicContentEndpoints?: string[];

  @state()
  private dynamicSummaryEndpoints?: string[];

  @state()
  private dynamicPrependedHeaderEndpoints?: string[];

  @state()
  private dynamicPrependedContentEndpoints?: string[];

  private readonly reporting = getAppContext().reportingService;

  private readonly restApiService = getAppContext().restApiService;

  private readonly userModel = getAppContext().userModel;

  private readonly getChangeModel = resolve(this, changeModelToken);

  private readonly getCommentsModel = resolve(this, commentsModelToken);

  private readonly getBrowserModel = resolve(this, browserModelToken);

  /** Called in disconnectedCallback. */
  private cleanups: (() => void)[] = [];

  shortcutsController = new ShortcutController(this);

  // private but used in test
  fileCursor = new GrCursorManager();

  // private but used in test
  diffCursor = new GrDiffCursor();

  static override get styles() {
    return [
      a11yStyles,
      sharedStyles,
      css`
        :host {
          display: block;
        }
        .row {
          align-items: center;
          border-top: 1px solid var(--border-color);
          display: flex;
          min-height: calc(var(--line-height-normal) + 2 * var(--spacing-s));
          padding: var(--spacing-xs) var(--spacing-l);
        }
        /* The class defines a content visible only to screen readers */
        .noCommentsScreenReaderText {
          opacity: 0;
          max-width: 1px;
          overflow: hidden;
          display: none;
          vertical-align: top;
        }
        div[role='gridcell']
          > div.comments
          > span:empty
          + span:empty
          + span.noCommentsScreenReaderText {
          /* inline-block instead of block, such that it can control width */
          display: inline-block;
        }
        :host(.loading) .row {
          opacity: 0.5;
        }
        :host(.editMode) .hideOnEdit {
          display: none;
        }
        .showOnEdit {
          display: none;
        }
        :host(.editMode) .showOnEdit {
          display: initial;
        }
        .invisible {
          visibility: hidden;
        }
        .header-row {
          background-color: var(--background-color-secondary);
        }
        .controlRow {
          align-items: center;
          display: flex;
          height: 2.25em;
          justify-content: center;
        }
        .controlRow.invisible,
        .show-hide.invisible {
          display: none;
        }
        .reviewed,
        .status {
          align-items: center;
          display: inline-flex;
        }
        .reviewed {
          display: inline-block;
          text-align: left;
          width: 1.5em;
        }
        .file-row {
          cursor: pointer;
        }
        .file-row.expanded {
          border-bottom: 1px solid var(--border-color);
          position: -webkit-sticky;
          position: sticky;
          top: 0;
          /* Has to visible above the diff view, and by default has a lower
            z-index. setting to 1 places it directly above. */
          z-index: 1;
        }
        .file-row:hover {
          background-color: var(--hover-background-color);
        }
        .file-row.selected {
          background-color: var(--selection-background-color);
        }
        .file-row.expanded,
        .file-row.expanded:hover {
          background-color: var(--expanded-background-color);
        }
        .path {
          cursor: pointer;
          flex: 1;
          /* Wrap it into multiple lines if too long. */
          white-space: normal;
          word-break: break-word;
        }
        .oldPath {
          color: var(--deemphasized-text-color);
        }
        .header-stats {
          text-align: center;
          min-width: 7.5em;
        }
        .stats {
          text-align: right;
          min-width: 7.5em;
        }
        .comments {
          padding-left: var(--spacing-l);
          min-width: 7.5em;
          white-space: nowrap;
        }
        .row:not(.header-row) .stats,
        .total-stats {
          font-family: var(--monospace-font-family);
          font-size: var(--font-size-mono);
          line-height: var(--line-height-mono);
          display: flex;
        }
        .sizeBars {
          margin-left: var(--spacing-m);
          min-width: 7em;
          text-align: center;
        }
        .sizeBars.hide {
          display: none;
        }
        .added,
        .removed {
          display: inline-block;
          min-width: 3.5em;
        }
        .added {
          color: var(--positive-green-text-color);
        }
        .removed {
          color: var(--negative-red-text-color);
          text-align: left;
          min-width: 4em;
          padding-left: var(--spacing-s);
        }
        .drafts {
          color: var(--error-foreground);
          font-weight: var(--font-weight-bold);
        }
        .show-hide-icon:focus {
          outline: none;
        }
        .show-hide {
          margin-left: var(--spacing-s);
          width: 1.9em;
        }
        .fileListButton {
          margin: var(--spacing-m);
        }
        .totalChanges {
          justify-content: flex-end;
          text-align: right;
        }
        .warning {
          color: var(--deemphasized-text-color);
        }
        input.show-hide {
          display: none;
        }
        label.show-hide {
          cursor: pointer;
          display: block;
          min-width: 2em;
        }
        gr-diff {
          display: block;
          overflow-x: auto;
        }
        .truncatedFileName {
          display: none;
        }
        .mobile {
          display: none;
        }
        .reviewed {
          margin-left: var(--spacing-xxl);
          width: 15em;
        }
        .reviewedSwitch {
          color: var(--link-color);
          opacity: 0;
          justify-content: flex-end;
          width: 100%;
        }
        .reviewedSwitch:hover {
          cursor: pointer;
          opacity: 100;
        }
        .showParentButton {
          line-height: var(--line-height-normal);
          margin-bottom: calc(var(--spacing-s) * -1);
          margin-left: var(--spacing-m);
          margin-top: calc(var(--spacing-s) * -1);
        }
        .row:focus {
          outline: none;
        }
        .row:hover .reviewedSwitch,
        .row:focus-within .reviewedSwitch,
        .row.expanded .reviewedSwitch {
          opacity: 100;
        }
        .reviewedLabel {
          color: var(--deemphasized-text-color);
          margin-right: var(--spacing-l);
          opacity: 0;
        }
        .reviewedLabel.isReviewed {
          display: initial;
          opacity: 100;
        }
        .editFileControls {
          width: 7em;
        }
        .markReviewed:focus {
          outline: none;
        }
        .markReviewed,
        .pathLink {
          display: inline-block;
          margin: -2px 0;
          padding: var(--spacing-s) 0;
          text-decoration: none;
        }
        .pathLink:hover span.fullFileName,
        .pathLink:hover span.truncatedFileName {
          text-decoration: underline;
        }

        /** copy on file path **/
        .pathLink gr-copy-clipboard,
        .oldPath gr-copy-clipboard {
          display: inline-block;
          visibility: hidden;
          vertical-align: bottom;
          --gr-button-padding: 0px;
        }
        .row:focus-within gr-copy-clipboard,
        .row:hover gr-copy-clipboard {
          visibility: visible;
        }

        @media screen and (max-width: 1200px) {
          gr-endpoint-decorator.extra-col {
            display: none;
          }
        }

        @media screen and (max-width: 1000px) {
          .reviewed {
            display: none;
          }
        }

        @media screen and (max-width: 800px) {
          .desktop {
            display: none;
          }
          .mobile {
            display: block;
          }
          .row.selected {
            background-color: var(--view-background-color);
          }
          .stats {
            display: none;
          }
          .reviewed,
          .status {
            justify-content: flex-start;
          }
          .comments {
            min-width: initial;
          }
          .expanded .fullFileName,
          .truncatedFileName {
            display: inline;
          }
          .expanded .truncatedFileName,
          .fullFileName {
            display: none;
          }
        }
        :host(.hideComments) {
          --gr-comment-thread-display: none;
        }
      `,
    ];
  }

  constructor() {
    super();
    this.fileCursor.scrollMode = ScrollMode.KEEP_VISIBLE;
    this.fileCursor.cursorTargetClass = 'selected';
    this.fileCursor.focusOnMove = true;
    this.shortcutsController.addAbstract(Shortcut.LEFT_PANE, _ =>
      this.handleLeftPane()
    );
    this.shortcutsController.addAbstract(Shortcut.RIGHT_PANE, _ =>
      this.handleRightPane()
    );
    this.shortcutsController.addAbstract(Shortcut.TOGGLE_INLINE_DIFF, _ =>
      this.handleToggleInlineDiff()
    );
    this.shortcutsController.addAbstract(Shortcut.TOGGLE_ALL_INLINE_DIFFS, _ =>
      this.toggleInlineDiffs()
    );
    this.shortcutsController.addAbstract(
      Shortcut.TOGGLE_HIDE_ALL_COMMENT_THREADS,
      _ => toggleClass(this, 'hideComments')
    );
    this.shortcutsController.addAbstract(Shortcut.CURSOR_NEXT_FILE, e =>
      this.handleCursorNext(e)
    );
    this.shortcutsController.addAbstract(Shortcut.CURSOR_PREV_FILE, e =>
      this.handleCursorPrev(e)
    );
    // This is already been taken care of by CURSOR_NEXT_FILE above. The two
    // shortcuts share the same bindings. It depends on whether all files
    // are expanded whether the cursor moves to the next file or line.
    this.shortcutsController.addAbstract(Shortcut.NEXT_LINE, _ => {}); // docOnly
    // This is already been taken care of by CURSOR_PREV_FILE above. The two
    // shortcuts share the same bindings. It depends on whether all files
    // are expanded whether the cursor moves to the previous file or line.
    this.shortcutsController.addAbstract(Shortcut.PREV_LINE, _ => {}); // docOnly
    this.shortcutsController.addAbstract(Shortcut.NEW_COMMENT, _ =>
      this.handleNewComment()
    );
    this.shortcutsController.addAbstract(Shortcut.OPEN_LAST_FILE, _ =>
      this.openSelectedFile(this.files.length - 1)
    );
    this.shortcutsController.addAbstract(Shortcut.OPEN_FIRST_FILE, _ =>
      this.openSelectedFile(0)
    );
    this.shortcutsController.addAbstract(Shortcut.OPEN_FILE, _ =>
      this.handleOpenFile()
    );
    this.shortcutsController.addAbstract(Shortcut.NEXT_CHUNK, _ =>
      this.handleNextChunk()
    );
    this.shortcutsController.addAbstract(Shortcut.PREV_CHUNK, _ =>
      this.handlePrevChunk()
    );
    this.shortcutsController.addAbstract(Shortcut.NEXT_COMMENT_THREAD, _ =>
      this.handleNextComment()
    );
    this.shortcutsController.addAbstract(Shortcut.PREV_COMMENT_THREAD, _ =>
      this.handlePrevComment()
    );
    this.shortcutsController.addAbstract(Shortcut.TOGGLE_FILE_REVIEWED, _ =>
      this.handleToggleFileReviewed()
    );
    this.shortcutsController.addAbstract(Shortcut.TOGGLE_LEFT_PANE, _ =>
      this.handleToggleLeftPane()
    );
    this.shortcutsController.addAbstract(
      Shortcut.EXPAND_ALL_COMMENT_THREADS,
      _ => {}
    ); // docOnly
    this.shortcutsController.addAbstract(
      Shortcut.COLLAPSE_ALL_COMMENT_THREADS,
      _ => {}
    ); // docOnly
    subscribe(
      this,
      () => this.getCommentsModel().changeComments$,
      changeComments => {
        this.changeComments = changeComments;
      }
    );
    subscribe(
      this,
      () => this.getBrowserModel().diffViewMode$,
      diffView => {
        this.diffViewMode = diffView;
      }
    );
    subscribe(
      this,
      () => this.userModel.diffPreferences$,
      diffPreferences => {
        this.diffPrefs = diffPreferences;
        fire(this, 'diff-prefs-changed', {value: this.diffPrefs});
      }
    );
    subscribe(
      this,
      () =>
        select(
          this.userModel.preferences$,
          prefs => !!prefs?.size_bar_in_change_table
        ),
      sizeBarInChangeTable => {
        this.showSizeBars = sizeBarInChangeTable;
      }
    );
    subscribe(
      this,
      () => this.getChangeModel().reviewedFiles$,
      reviewedFiles => {
        this.reviewed = reviewedFiles ?? [];
      }
    );
  }

  override willUpdate(changedProperties: PropertyValues): void {
    if (changedProperties.has('filesByPath')) {
      this.updateCleanlyMergedPaths();
    }
    if (changedProperties.has('editMode')) {
      this.editModeChanged();
    }
    if (
      changedProperties.has('diffPrefs') ||
      changedProperties.has('diffViewMode')
    ) {
      this.updateDiffPreferences();
    }
    if (
      changedProperties.has('filesByPath') ||
      changedProperties.has('changeComments') ||
      changedProperties.has('patchRange') ||
      changedProperties.has('reviewed') ||
      changedProperties.has('loading')
    ) {
      changedProperties.set('files', this.files);
      this.computeFiles();
    }
    if (changedProperties.has('loading')) {
      // Should run after files has been updated.
      this.loadingChanged();
    }
    if (changedProperties.has('files')) {
      this.filesChanged();
    }
    if (
      changedProperties.has('files') ||
      changedProperties.has('numFilesShown')
    ) {
      this.shownFiles = this.computeFilesShown();
    }
    if (changedProperties.has('expandedFiles')) {
      changedProperties.set('filesExpanded', this.filesExpanded);
      this.expandedFilesChanged(changedProperties.get('expandedFiles'));
    }
    if (changedProperties.has('filesExpanded')) {
      fire(this, 'files-expanded-changed', {value: this.filesExpanded});
    }
  }

  override connectedCallback() {
    super.connectedCallback();

    getPluginLoader()
      .awaitPluginsLoaded()
      .then(() => {
        this.dynamicHeaderEndpoints = getPluginEndpoints().getDynamicEndpoints(
          'change-view-file-list-header'
        );
        this.dynamicContentEndpoints = getPluginEndpoints().getDynamicEndpoints(
          'change-view-file-list-content'
        );
        this.dynamicPrependedHeaderEndpoints =
          getPluginEndpoints().getDynamicEndpoints(
            'change-view-file-list-header-prepend'
          );
        this.dynamicPrependedContentEndpoints =
          getPluginEndpoints().getDynamicEndpoints(
            'change-view-file-list-content-prepend'
          );
        this.dynamicSummaryEndpoints = getPluginEndpoints().getDynamicEndpoints(
          'change-view-file-list-summary'
        );

        if (
          this.dynamicHeaderEndpoints.length !==
          this.dynamicContentEndpoints.length
        ) {
          this.reporting.error(new Error('dynamic header/content mismatch'));
        }
        if (
          this.dynamicPrependedHeaderEndpoints.length !==
          this.dynamicPrependedContentEndpoints.length
        ) {
          this.reporting.error(new Error('dynamic header/content mismatch'));
        }
        if (
          this.dynamicHeaderEndpoints.length !==
          this.dynamicSummaryEndpoints.length
        ) {
          this.reporting.error(new Error('dynamic header/content mismatch'));
        }
      });
    this.cleanups.push(
      addGlobalShortcut({key: Key.ESC}, _ => this.handleEscKey()),
      addShortcut(this, {key: Key.ENTER}, _ => this.handleOpenFile(), {
        shouldSuppress: true,
      })
    );
  }

  override disconnectedCallback() {
    this.diffCursor.dispose();
    this.fileCursor.unsetCursor();
    this.cancelDiffs();
    this.loadingTask?.cancel();
    for (const cleanup of this.cleanups) cleanup();
    this.cleanups = [];
    super.disconnectedCallback();
  }

  override render() {
    const patchChange = this.calculatePatchChange();
    const showDynamicColumns = this.computeShowDynamicColumns();
    return html`
      <h3 class="assistive-tech-only">File list</h3>
      ${this.renderContainer(showDynamicColumns)}
      ${this.renderChangeTotals(patchChange, showDynamicColumns)}
      ${this.renderBinaryTotals(patchChange)} ${this.renderControlRow()}
      <gr-diff-preferences-dialog
        id="diffPreferencesDialog"
        @reload-diff-preference=${this.handleReloadingDiffPreference}
      >
      </gr-diff-preferences-dialog>
    `;
  }

  private renderContainer(showDynamicColumns: boolean) {
    const showPrependedDynamicColumns =
      this.computeShowPrependedDynamicColumns();
    return html`
      <div
        id="container"
        @click=${(e: MouseEvent) => this.handleFileListClick(e)}
        role="grid"
        aria-label="Files list"
      >
        ${this.renderHeaderRow(showDynamicColumns, showPrependedDynamicColumns)}
        ${this.renderShownFiles(
          showDynamicColumns,
          showPrependedDynamicColumns
        )}
        ${when(this.computeShowNumCleanlyMerged(), () =>
          this.renderCleanlyMerged(showPrependedDynamicColumns)
        )}
      </div>
    `;
  }

  private renderHeaderRow(
    showDynamicColumns: boolean,
    showPrependedDynamicColumns: boolean
  ) {
    return html` <div class="header-row row" role="row">
      <!-- endpoint: change-view-file-list-header-prepend -->
      ${when(showPrependedDynamicColumns, () =>
        this.renderPrependedHeaderEndpoints()
      )}

      <div class="path" role="columnheader">File</div>
      <div class="comments desktop" role="columnheader">Comments</div>
      <div class="comments mobile" role="columnheader" title="Comments">C</div>
      <div class="sizeBars desktop" role="columnheader">Size</div>
      <div class="header-stats" role="columnheader">Delta</div>
      <!-- endpoint: change-view-file-list-header -->
      ${when(showDynamicColumns, () => this.renderDynamicHeaderEndpoints())}
      <!-- Empty div here exists to keep spacing in sync with file rows. -->
      <div
        class="reviewed hideOnEdit"
        ?hidden=${!this.loggedIn}
        aria-hidden="true"
      ></div>
      <div class="editFileControls showOnEdit" aria-hidden="true"></div>
      <div class="show-hide" aria-hidden="true"></div>
    </div>`;
  }

  private renderPrependedHeaderEndpoints() {
    return this.dynamicPrependedHeaderEndpoints?.map(
      headerEndpoint => html`
        <gr-endpoint-decorator
          class="prepended-col"
          .name=${headerEndpoint}
          role="columnheader"
        >
          <gr-endpoint-param name="change" .value=${this.change}>
          </gr-endpoint-param>
          <gr-endpoint-param name="patchRange" .value=${this.patchRange}>
          </gr-endpoint-param>
          <gr-endpoint-param name="files" .value=${this.files}>
          </gr-endpoint-param>
        </gr-endpoint-decorator>
      `
    );
  }

  private renderDynamicHeaderEndpoints() {
    return this.dynamicHeaderEndpoints?.map(
      headerEndpoint => html`
        <gr-endpoint-decorator
          class="extra-col"
          .name=${headerEndpoint}
          role="columnheader"
        ></gr-endpoint-decorator>
      `
    );
  }

  private renderShownFiles(
    showDynamicColumns: boolean,
    showPrependedDynamicColumns: boolean
  ) {
    const sizeBarLayout = this.computeSizeBarLayout();

    return incrementalRepeat({
      values: this.shownFiles,
      mapFn: (f, i) =>
        this.renderFileRow(
          f as NormalizedFileInfo,
          i,
          sizeBarLayout,
          showDynamicColumns,
          showPrependedDynamicColumns
        ),
      initialCount: this.fileListIncrement,
      targetFrameRate: 30,
    });
  }

  private renderFileRow(
    file: NormalizedFileInfo,
    index: number,
    sizeBarLayout: SizeBarLayout,
    showDynamicColumns: boolean,
    showPrependedDynamicColumns: boolean
  ) {
    this.reportRenderedRow(index);
    const patchSetFile = this.computePatchSetFile(file);
    return html` <div class="stickyArea">
      <div
        class=${`file-row row ${this.computePathClass(file.__path)}`}
        data-file=${JSON.stringify(patchSetFile)}
        tabindex="-1"
        role="row"
      >
        <!-- endpoint: change-view-file-list-content-prepend -->
        ${when(showPrependedDynamicColumns, () =>
          this.renderPrependedContentEndpointsForFile(file)
        )}
        ${this.renderFilePath(file)} ${this.renderFileComments(file)}
        ${this.renderSizeBar(file, sizeBarLayout)} ${this.renderFileStats(file)}
        ${when(showDynamicColumns, () =>
          this.renderDynamicContentEndpointsForFile(file)
        )}
        <!-- endpoint: change-view-file-list-content -->
        ${this.renderReviewed(file)} ${this.renderFileControls(file)}
        ${this.renderShowHide(file)}
      </div>
      ${when(
        this.isFileExpanded(file.__path),
        () => html`
          <gr-diff-host
            ?noAutoRender=${true}
            ?showLoadFailure=${true}
            .displayLine=${this.displayLine}
            .changeNum=${this.changeNum}
            .change=${this.change}
            .patchRange=${this.patchRange}
            .file=${patchSetFile}
            .path=${file.__path}
            .prefs=${this.diffPrefs}
            .projectName=${this.change?.project}
            ?noRenderOnPrefsChange=${true}
          ></gr-diff-host>
        `
      )}
    </div>`;
  }

  private renderPrependedContentEndpointsForFile(file: NormalizedFileInfo) {
    return this.dynamicPrependedContentEndpoints?.map(
      contentEndpoint => html`
        <gr-endpoint-decorator
          class="prepended-col"
          .name=${contentEndpoint}
          role="gridcell"
        >
          <gr-endpoint-param name="change" .value=${this.change}>
          </gr-endpoint-param>
          <gr-endpoint-param name="changeNum" .value=${this.changeNum}>
          </gr-endpoint-param>
          <gr-endpoint-param name="patchRange" .value=${this.patchRange}>
          </gr-endpoint-param>
          <gr-endpoint-param name="path" .value=${file.__path}>
          </gr-endpoint-param>
          <gr-endpoint-param name="oldPath" .value=${this.getOldPath(file)}>
          </gr-endpoint-param>
        </gr-endpoint-decorator>
      `
    );
  }

  private renderFilePath(file: NormalizedFileInfo) {
    return html` <span class="path" role="gridcell">
      <a class="pathLink" href=${ifDefined(this.computeDiffURL(file.__path))}>
        <span title=${computeDisplayPath(file.__path)} class="fullFileName">
          ${computeDisplayPath(file.__path)}
        </span>
        <span
          title=${computeDisplayPath(file.__path)}
          class="truncatedFileName"
        >
          ${computeTruncatedPath(file.__path)}
        </span>
        <gr-file-status-chip .file=${file}></gr-file-status-chip>
        <gr-copy-clipboard
          ?hideInput=${true}
          .text=${file.__path}
        ></gr-copy-clipboard>
      </a>
      ${when(
        file.old_path,
        () => html`
          <div class="oldPath" title=${file.old_path}>
            [[file.old_path]]
            <gr-copy-clipboard
              ?hideInput=${true}
              .text=${file.old_path}
            ></gr-copy-clipboard>
          </div>
        `
      )}
    </span>`;
  }

  private renderFileComments(file: NormalizedFileInfo) {
    return html` <div role="gridcell">
      <div class="comments desktop">
        <span class="drafts">${this.computeDraftsString(file)}</span>
        <span>${this.computeCommentsString(file)}</span>
        <span class="noCommentsScreenReaderText">
          <!-- Screen readers read the following content only if 2 other
          spans in the parent div is empty. The content is not visible on
          the page.
          Without this span, screen readers don't navigate correctly inside
          table, because empty div doesn't rendered. For example, VoiceOver
          jumps back to the whole table.
          We can use &nbsp instead, but it sounds worse.
          -->
          No comments
        </span>
      </div>
      <div class="comments mobile">
        <span class="drafts">${this.computeDraftsStringMobile(file)}</span>
        <span>${this.computeCommentsStringMobile(file)}</span>
        <span class="noCommentsScreenReaderText">
          <!-- The same as for desktop comments -->
          No comments
        </span>
      </div>
    </div>`;
  }

  private renderSizeBar(
    file: NormalizedFileInfo,
    sizeBarLayout: SizeBarLayout
  ) {
    return html` <div class="desktop" role="gridcell">
      <!-- The content must be in a separate div. It guarantees, that
          gridcell always visible for screen readers.
          For example, without a nested div screen readers pronounce the
          "Commit message" row content with incorrect column headers.
        -->
      <div
        class=${this.computeSizeBarsClass(file.__path)}
        aria-label="A bar that represents the addition and deletion ratio for the current file"
      >
        <svg width="61" height="8">
          <rect
            x=${this.computeBarAdditionX(file, sizeBarLayout)}
            y="0"
            height="8"
            fill="var(--positive-green-text-color)"
            width=${this.computeBarAdditionWidth(file, sizeBarLayout)}
          ></rect>
          <rect
            x=${this.computeBarDeletionX(sizeBarLayout)}
            y="0"
            height="8"
            fill="var(--negative-red-text-color)"
            width=${this.computeBarDeletionWidth(file, sizeBarLayout)}
          ></rect>
        </svg>
      </div>
    </div>`;
  }

  private renderFileStats(file: NormalizedFileInfo) {
    return html` <div class="stats" role="gridcell">
      <!-- The content must be in a separate div. It guarantees, that
        gridcell always visible for screen readers.
        For example, without a nested div screen readers pronounce the
        "Commit message" row content with incorrect column headers.
        -->
      <div class=${this.computeClass('', file.__path)}>
        <span
          class="added"
          tabindex="0"
          aria-label=${`${file.lines_inserted} lines added`}
          ?hidden=${file.binary}
        >
          +${file.lines_inserted}
        </span>
        <span
          class="removed"
          tabindex="0"
          aria-label=${`${file.lines_deleted} lines removed`}
          ?hidden=${file.binary}
        >
          -${file.lines_deleted}
        </span>
        <span
          class=${ifDefined(this.computeBinaryClass(file.size_delta))}
          ?hidden=${!file.binary}
        >
          ${this.formatBytes(file.size_delta)}
          ${this.formatPercentage(file.size, file.size_delta)}
        </span>
      </div>
    </div>`;
  }

  private renderDynamicContentEndpointsForFile(file: NormalizedFileInfo) {
    this.dynamicContentEndpoints?.map(
      contentEndpoint => html` <div
        class=${this.computeClass('', file.__path)}
        role="gridcell"
      >
        <gr-endpoint-decorator class="extra-col" .name=${contentEndpoint}>
          <gr-endpoint-param name="change" .value=${this.change}>
          </gr-endpoint-param>
          <gr-endpoint-param name="changeNum" .value=${this.changeNum}>
          </gr-endpoint-param>
          <gr-endpoint-param name="patchRange" .value=${this.patchRange}>
          </gr-endpoint-param>
          <gr-endpoint-param name="path" .value=${file.__path}>
          </gr-endpoint-param>
        </gr-endpoint-decorator>
      </div>`
    );
  }

  private renderReviewed(file: NormalizedFileInfo) {
    if (!this.loggedIn) return nothing;
    return html` <div class="reviewed hideOnEdit" role="gridcell">
      <span
        class=${`reviewedLabel ${this.computeReviewedClass(file.isReviewed)}`}
        aria-hidden=${this.booleanToString(!file.isReviewed)}
        >Reviewed</span
      >
      <!-- Do not use input type="checkbox" with hidden input and
              visible label here. Screen readers don't read/interract
              correctly with such input.
          -->
      <span
        class="reviewedSwitch"
        role="switch"
        tabindex="0"
        @click=${(e: MouseEvent) => this.reviewedClick(e)}
        @keydown=${(e: KeyboardEvent) => this.reviewedClick(e)}
        aria-label="Reviewed"
        aria-checked=${this.booleanToString(file.isReviewed)}
      >
        <!-- Trick with tabindex to avoid outline on mouse focus, but
            preserve focus outline for keyboard navigation -->
        <span
          tabindex="-1"
          class="markReviewed"
          title=${this.reviewedTitle(file.isReviewed)}
          >${this.computeReviewedText(file.isReviewed)}</span
        >
      </span>
    </div>`;
  }

  private renderFileControls(file: NormalizedFileInfo) {
    return html` <div
      class="editFileControls showOnEdit"
      role="gridcell"
      aria-hidden=${this.booleanToString(!this.editMode)}
    >
      ${when(
        this.editMode,
        () => html`
          <gr-edit-file-controls
            class=${this.computeClass('', file.__path)}
            .filePath=${file.__path}
          ></gr-edit-file-controls>
        `
      )}
    </div>`;
  }

  private renderShowHide(file: NormalizedFileInfo) {
    return html` <div class="show-hide" role="gridcell">
      <!-- Do not use input type="checkbox" with hidden input and
            visible label here. Screen readers don't read/interract
            correctly with such input.
        -->
      <span
        class="show-hide"
        data-path=${file.__path}
        data-expand="true"
        role="switch"
        tabindex="0"
        aria-checked=${this.isFileExpandedStr(file.__path)}
        aria-label="Expand file"
        @click=${this.expandedClick}
        @keydown=${this.expandedClick}
      >
        <!-- Trick with tabindex to avoid outline on mouse focus, but
          preserve focus outline for keyboard navigation -->
        <iron-icon
          class="show-hide-icon"
          tabindex="-1"
          id="icon"
          icon=${this.computeShowHideIcon(file.__path)}
        >
        </iron-icon>
      </span>
    </div>`;
  }

  private renderCleanlyMerged(showPrependedDynamicColumns: boolean) {
    return html` <div class="row">
      <!-- endpoint: change-view-file-list-content-prepend -->
      ${when(showPrependedDynamicColumns, () =>
        this.renderPrependedContentEndpoints()
      )}
      <div role="gridcell">
        <div>
          <span class="cleanlyMergedText">
            ${this.computeCleanlyMergedText()}
          </span>
          <gr-button
            link
            class="showParentButton"
            @click=${this.handleShowParent1}
          >
            Show Parent 1
          </gr-button>
        </div>
      </div>
    </div>`;
  }

  private renderPrependedContentEndpoints() {
    return this.dynamicPrependedContentEndpoints?.map(
      contentEndpoint => html`
        <gr-endpoint-decorator
          class="prepended-col"
          .name=${contentEndpoint}
          role="gridcell"
        >
          <gr-endpoint-param name="change" .value=${this.change}>
          </gr-endpoint-param>
          <gr-endpoint-param name="changeNum" .value=${this.changeNum}>
          </gr-endpoint-param>
          <gr-endpoint-param name="patchRange" .value=${this.patchRange}>
          </gr-endpoint-param>
          <gr-endpoint-param
            name="cleanlyMergedPaths"
            .value=${this.cleanlyMergedPaths}
          >
          </gr-endpoint-param>
          <gr-endpoint-param
            name="cleanlyMergedOldPaths"
            .value=${this.cleanlyMergedOldPaths}
          >
          </gr-endpoint-param>
        </gr-endpoint-decorator>
      `
    );
  }

  private renderChangeTotals(
    patchChange: PatchChange,
    showDynamicColumns: boolean
  ) {
    if (this.shouldHideChangeTotals(patchChange)) return nothing;
    return html`
      <div class="row totalChanges">
        <div class="total-stats">
          <div>
            <span
              class="added"
              tabindex="0"
              aria-label="Total ${patchChange.inserted} lines added"
            >
              +${patchChange.inserted}
            </span>
            <span
              class="removed"
              tabindex="0"
              aria-label="Total ${patchChange.deleted} lines removed"
            >
              -${patchChange.deleted}
            </span>
          </div>
        </div>
        ${when(showDynamicColumns, () =>
          this.dynamicSummaryEndpoints?.map(
            summaryEndpoint => html`
              <gr-endpoint-decorator class="extra-col" name=${summaryEndpoint}>
                <gr-endpoint-param name="change" .value=${this.change}>
                </gr-endpoint-param>
                <gr-endpoint-param name="patchRange" .value=${this.patchRange}>
                </gr-endpoint-param>
              </gr-endpoint-decorator>
            `
          )
        )}

        <!-- Empty div here exists to keep spacing in sync with file rows. -->
        <div class="reviewed hideOnEdit" ?hidden=${!this.loggedIn}></div>
        <div class="editFileControls showOnEdit"></div>
        <div class="show-hide"></div>
      </div>
    `;
  }

  private renderBinaryTotals(patchChange: PatchChange) {
    if (this.shouldHideBinaryChangeTotals(patchChange)) return nothing;
    const deltaInserted = this.formatBytes(patchChange.size_delta_inserted);
    const deltaDeleted = this.formatBytes(patchChange.size_delta_deleted);
    return html`
      <div class="row totalChanges">
        <div class="total-stats">
          <span
            class="added"
            aria-label="Total bytes inserted: ${deltaInserted}"
          >
            ${deltaInserted}
            ${this.formatPercentage(
              patchChange.total_size,
              patchChange.size_delta_inserted
            )}
          </span>
          <span
            class="removed"
            aria-label="Total bytes removed: ${deltaDeleted}"
          >
            ${deltaDeleted}
            ${this.formatPercentage(
              patchChange.total_size,
              patchChange.size_delta_deleted
            )}
          </span>
        </div>
      </div>
    `;
  }

  private renderControlRow() {
    return html`<div
      class=${`row controlRow ${this.computeFileListControlClass()}`}
    >
      <gr-button
        class="fileListButton"
        id="incrementButton"
        link=""
        @click=${this.incrementNumFilesShown}
      >
        ${this.computeIncrementText()}
      </gr-button>
      <gr-tooltip-content
        ?has-tooltip=${this.computeWarnShowAll()}
        ?show-icon=${this.computeWarnShowAll()}
        .title=${this.computeShowAllWarning()}
      >
        <gr-button
          class="fileListButton"
          id="showAllButton"
          link=""
          @click=${this.showAllFiles}
        >
          ${this.computeShowAllText()}
        </gr-button>
      </gr-tooltip-content>
    </div>`;
  }

  reload() {
    if (!this.changeNum || !this.patchRange?.patchNum) {
      return Promise.resolve();
    }
    const changeNum = this.changeNum;
    const patchRange = this.patchRange;

    this.loading = true;

    this.collapseAllDiffs();
    const promises: Promise<boolean | void>[] = [];

    promises.push(
      this.restApiService
        .getChangeOrEditFiles(changeNum, patchRange)
        .then(filesByPath => {
          this.filesByPath = filesByPath;
        })
    );

    promises.push(
      this.getLoggedIn().then(loggedIn => (this.loggedIn = loggedIn))
    );

    return Promise.all(promises).then(() => {
      this.loading = false;
      this.detectChromiteButler();
      this.reporting.fileListDisplayed();
    });
  }

  private async updateCleanlyMergedPaths() {
    // When viewing Auto Merge base vs a patchset, add an additional row that
    // knows how many files were cleanly merged. This requires an additional RPC
    // for the diffs between target parent and the patch set. The cleanly merged
    // files are all the files in the target RPC that weren't in the Auto Merge
    // RPC.
    if (
      this.change &&
      this.changeNum &&
      this.patchRange?.patchNum &&
      new RevisionInfo(this.change).isMergeCommit(this.patchRange.patchNum) &&
      this.patchRange.basePatchNum === 'PARENT' &&
      this.patchRange.patchNum !== EditPatchSetNum
    ) {
      const allFilesByPath = await this.restApiService.getChangeOrEditFiles(
        this.changeNum,
        {
          basePatchNum: -1 as BasePatchSetNum, // -1 is first (target) parent
          patchNum: this.patchRange.patchNum,
        }
      );
      if (!allFilesByPath || !this.filesByPath) return;
      const conflictingPaths = Object.keys(this.filesByPath);
      this.cleanlyMergedPaths = Object.keys(allFilesByPath).filter(
        path => !conflictingPaths.includes(path)
      );
      this.cleanlyMergedOldPaths = this.cleanlyMergedPaths
        .map(path => allFilesByPath[path].old_path)
        .filter((oldPath): oldPath is string => !!oldPath);
    } else {
      this.cleanlyMergedPaths = [];
      this.cleanlyMergedOldPaths = [];
    }
  }

  private detectChromiteButler() {
    const hasButler = !!document.getElementById('butler-suggested-owners');
    if (hasButler) {
      this.reporting.reportExtension('butler');
    }
  }

  get diffs(): GrDiffHost[] {
    const diffs = this.shadowRoot!.querySelectorAll('gr-diff-host');
    // It is possible that a bogus diff element is hanging around invisibly
    // from earlier with a different patch set choice and associated with a
    // different entry in the files array. So filter on visible items only.
    return Array.from(diffs).filter(
      el => !!el && !!el.style && el.style.display !== 'none'
    );
  }

  openDiffPrefs() {
    this.diffPreferencesDialog?.open();
  }

  // Private but used in tests.
  calculatePatchChange(): PatchChange {
    const magicFilesExcluded = this.files.filter(
      file => !isMagicPath(file.__path)
    );

    return magicFilesExcluded.reduce((acc, obj) => {
      const inserted = obj.lines_inserted ? obj.lines_inserted : 0;
      const deleted = obj.lines_deleted ? obj.lines_deleted : 0;
      const total_size = obj.size && obj.binary ? obj.size : 0;
      const size_delta_inserted =
        obj.binary && obj.size_delta > 0 ? obj.size_delta : 0;
      const size_delta_deleted =
        obj.binary && obj.size_delta < 0 ? obj.size_delta : 0;

      return {
        inserted: acc.inserted + inserted,
        deleted: acc.deleted + deleted,
        size_delta_inserted: acc.size_delta_inserted + size_delta_inserted,
        size_delta_deleted: acc.size_delta_deleted + size_delta_deleted,
        total_size: acc.total_size + total_size,
      };
    }, createDefaultPatchChange());
  }

  // private but used in test
  toggleFileExpanded(file: PatchSetFile) {
    // Is the path in the list of expanded diffs? If so, remove it, otherwise
    // add it to the list.
    const indexInExpanded = this.expandedFiles.findIndex(
      f => f.path === file.path
    );
    if (indexInExpanded === -1) {
      this.expandedFiles = this.expandedFiles.concat([file]);
    } else {
      this.expandedFiles = this.expandedFiles.filter(
        (_val, idx) => idx !== indexInExpanded
      );
    }
    const indexInAll = this.files.findIndex(f => f.__path === file.path);
    this.shadowRoot!.querySelectorAll(`.${FILE_ROW_CLASS}`)[
      indexInAll
    ].scrollIntoView({block: 'nearest'});
  }

  private toggleFileExpandedByIndex(index: number) {
    this.toggleFileExpanded(this.computePatchSetFile(this.files[index]));
  }

  // Private but used in tests.
  updateDiffPreferences() {
    if (!this.diffs.length) {
      return;
    }
    // Re-render all expanded diffs sequentially.
    this.renderInOrder(
      this.expandedFiles,
      this.diffs
    );
  }

  private forEachDiff(fn: (host: GrDiffHost) => void) {
    const diffs = this.diffs;
    for (let i = 0; i < diffs.length; i++) {
      fn(diffs[i]);
    }
  }

  expandAllDiffs() {
    // Find the list of paths that are in the file list, but not in the
    // expanded list.
    const newFiles: PatchSetFile[] = [];
    let path: string;
    for (let i = 0; i < this.shownFiles.length; i++) {
      path = this.shownFiles[i].__path;
      if (!this.expandedFiles.some(f => f.path === path)) {
        newFiles.push(this.computePatchSetFile(this.shownFiles[i]));
      }
    }

    this.expandedFiles = newFiles.concat(this.expandedFiles);
  }

  collapseAllDiffs() {
    this.expandedFiles = [];
  }

  /**
   * Computes a string with the number of comments and unresolved comments.
   */
  computeCommentsString(file?: NormalizedFileInfo) {
    if (
      this.changeComments === undefined ||
      this.patchRange === undefined ||
      file?.__path === undefined
    ) {
      return '';
    }
    return this.changeComments.computeCommentsString(
      this.patchRange,
      file.__path,
      file
    );
  }

  /**
   * Computes a string with the number of drafts.
   */
  computeDraftsString(file?: NormalizedFileInfo) {
    if (this.changeComments === undefined) return '';
    const draftCount = this.changeComments.computeDraftCountForFile(
      this.patchRange,
      file
    );
    if (draftCount === 0) return '';
    return pluralize(Number(draftCount), 'draft');
  }

  /**
   * Computes a shortened string with the number of drafts.
   * Private but used in tests.
   */
  computeDraftsStringMobile(file?: NormalizedFileInfo) {
    if (this.changeComments === undefined) return '';
    const draftCount = this.changeComments.computeDraftCountForFile(
      this.patchRange,
      file
    );
    return draftCount === 0 ? '' : `${draftCount}d`;
  }

  /**
   * Computes a shortened string with the number of comments.
   */
  computeCommentsStringMobile(file?: NormalizedFileInfo) {
    if (
      this.changeComments === undefined ||
      this.patchRange === undefined ||
      file === undefined
    ) {
      return '';
    }
    const commentThreadCount =
      this.changeComments.computeCommentThreadCount({
        patchNum: this.patchRange.basePatchNum,
        path: file.__path,
      }) +
      this.changeComments.computeCommentThreadCount({
        patchNum: this.patchRange.patchNum,
        path: file.__path,
      });
    return commentThreadCount === 0 ? '' : `${commentThreadCount}c`;
  }

  // Private but used in tests.
  reviewFile(path: string, reviewed?: boolean) {
    if (this.editMode) {
      return Promise.resolve();
    }
    const index = this.files.findIndex(file => file.__path === path);
    reviewed = reviewed || !this.files[index].isReviewed;
    this.files[index].isReviewed = reviewed;
    if (index < this.shownFiles.length) {
      this.requestUpdate('shownFiles');
    }
    this.requestUpdate('files');
    return this._saveReviewedState(path, reviewed);
  }

  _saveReviewedState(path: string, reviewed: boolean) {
    if (!this.changeNum || !this.patchRange) {
      throw new Error('changeNum and patchRange must be set');
    }

    return this.getChangeModel().setReviewedFilesStatus(
      this.changeNum,
      this.patchRange.patchNum,
      path,
      reviewed
    );
  }

  private getLoggedIn() {
    return this.restApiService.getLoggedIn();
  }

  private normalizeChangeFilesResponse(
    response: FileNameToReviewedFileInfoMap
  ): NormalizedFileInfo[] {
    const paths = Object.keys(response).sort(specialFilePathCompare);
    const files: NormalizedFileInfo[] = [];
    for (let i = 0; i < paths.length; i++) {
      const info = {...response[paths[i]]} as NormalizedFileInfo;
      info.__path = paths[i];
      info.lines_inserted = info.lines_inserted || 0;
      info.lines_deleted = info.lines_deleted || 0;
      info.size_delta = info.size_delta || 0;
      files.push(info);
    }
    return files;
  }

  /**
   * Returns true if the event e is a click on an element.
   *
   * The click is: mouse click or pressing Enter or Space key
   * P.S> Screen readers sends click event as well
   */
  private isClickEvent(e: MouseEvent | KeyboardEvent) {
    if (e.type === 'click') {
      return true;
    }
    const ke = e as KeyboardEvent;
    const isSpaceOrEnter = ke.key === 'Enter' || ke.key === ' ';
    return ke.type === 'keydown' && isSpaceOrEnter;
  }

  private fileActionClick(
    e: MouseEvent | KeyboardEvent,
    fileAction: (file: PatchSetFile) => void
  ) {
    if (this.isClickEvent(e)) {
      const fileRow = this.getFileRowFromEvent(e);
      if (!fileRow) {
        return;
      }
      // Prevent default actions (e.g. scrolling for space key)
      e.preventDefault();
      // Prevent handleFileListClick handler call
      e.stopPropagation();
      this.fileCursor.setCursor(fileRow.element);
      fileAction(fileRow.file);
    }
  }

  // Private but used in tests.
  reviewedClick(e: MouseEvent | KeyboardEvent) {
    this.fileActionClick(e, file => this.reviewFile(file.path));
  }

  private expandedClick(e: MouseEvent | KeyboardEvent) {
    this.fileActionClick(e, file => this.toggleFileExpanded(file));
  }

  /**
   * Handle all events from the file list dom-repeat so event handlers don't
   * have to get registered for potentially very long lists.
   * Private but used in tests.
   */
  handleFileListClick(e: MouseEvent) {
    if (!e.target) {
      return;
    }
    const fileRow = this.getFileRowFromEvent(e);
    if (!fileRow) {
      return;
    }
    const file = fileRow.file;
    const path = file.path;
    // If a path cannot be interpreted from the click target (meaning it's not
    // somewhere in the row, e.g. diff content) or if the user clicked the
    // link, defer to the native behavior.
    if (!path || descendedFromClass(e.target as Element, 'pathLink')) {
      return;
    }

    // Disregard the event if the click target is in the edit controls.
    if (descendedFromClass(e.target as Element, 'editFileControls')) {
      return;
    }

    e.preventDefault();
    this.fileCursor.setCursor(fileRow.element);
    this.toggleFileExpanded(file);
  }

  private getFileRowFromEvent(e: Event): FileRow | null {
    // Traverse upwards to find the row element if the target is not the row.
    let row = e.target as HTMLElement;
    while (!row.classList.contains(FILE_ROW_CLASS) && row.parentElement) {
      row = row.parentElement;
    }

    // No action needed for item without a valid file
    if (!row.dataset['file']) {
      return null;
    }

    return {
      file: JSON.parse(row.dataset['file']) as PatchSetFile,
      element: row,
    };
  }

  /**
   * Generates file range from file info object.
   */
  private computePatchSetFile(file: NormalizedFileInfo): PatchSetFile {
    const fileData: PatchSetFile = {
      path: file.__path,
    };
    if (file.old_path) {
      fileData.basePath = file.old_path;
    }
    return fileData;
  }

  private handleLeftPane() {
    if (this.noDiffsExpanded()) return;
    this.diffCursor.moveLeft();
  }

  private handleRightPane() {
    if (this.noDiffsExpanded()) return;
    this.diffCursor.moveRight();
  }

  private handleToggleInlineDiff() {
    if (this.fileCursor.index === -1) return;
    this.toggleFileExpandedByIndex(this.fileCursor.index);
  }

  // Private but used in tests.
  handleCursorNext(e: KeyboardEvent) {
    if (this.filesExpanded === FilesExpandedState.ALL) {
      this.diffCursor.moveDown();
      this.displayLine = true;
    } else {
      if (e.key === Key.DOWN) return;
      this.fileCursor.next({circular: true});
      this.selectedIndex = this.fileCursor.index;
      fire(this, 'selected-index-changed', {value: this.fileCursor.index});
    }
  }

  // Private but used in tests.
  handleCursorPrev(e: KeyboardEvent) {
    if (this.filesExpanded === FilesExpandedState.ALL) {
      this.diffCursor.moveUp();
      this.displayLine = true;
    } else {
      if (e.key === Key.UP) return;
      this.fileCursor.previous({circular: true});
      this.selectedIndex = this.fileCursor.index;
      fire(this, 'selected-index-changed', {value: this.fileCursor.index});
    }
  }

  private handleNewComment() {
    this.classList.remove('hideComments');
    this.diffCursor.createCommentInPlace();
  }

  // Private but used in tests.
  handleOpenFile() {
    if (this.filesExpanded === FilesExpandedState.ALL) {
      this.openCursorFile();
      return;
    }
    this.openSelectedFile();
  }

  private handleNextChunk() {
    if (this.noDiffsExpanded()) return;
    this.diffCursor.moveToNextChunk();
  }

  private handleNextComment() {
    if (this.noDiffsExpanded()) return;
    this.diffCursor.moveToNextCommentThread();
  }

  private handlePrevChunk() {
    if (this.noDiffsExpanded()) return;
    this.diffCursor.moveToPreviousChunk();
  }

  private handlePrevComment() {
    if (this.noDiffsExpanded()) return;
    this.diffCursor.moveToPreviousCommentThread();
  }

  private handleToggleFileReviewed() {
    if (!this.files[this.fileCursor.index]) {
      return;
    }
    this.reviewFile(this.files[this.fileCursor.index].__path);
  }

  private handleToggleLeftPane() {
    this.forEachDiff(diff => {
      diff.toggleLeftDiff();
    });
  }

  private toggleInlineDiffs() {
    if (this.filesExpanded === FilesExpandedState.ALL) {
      this.collapseAllDiffs();
    } else {
      this.expandAllDiffs();
    }
  }

  // Private but used in tests.
  openCursorFile() {
    const diff = this.diffCursor.getTargetDiffElement();
    if (!this.change || !diff || !this.patchRange || !diff.path) {
      throw new Error('change, diff and patchRange must be all set and valid');
    }
    GerritNav.navigateToDiff(
      this.change,
      diff.path,
      this.patchRange.patchNum,
      this.patchRange.basePatchNum
    );
  }

  // Private but used in tests.
  openSelectedFile(index?: number) {
    if (index !== undefined) {
      this.fileCursor.setCursorAtIndex(index);
    }
    if (!this.files[this.fileCursor.index]) {
      return;
    }
    if (!this.change || !this.patchRange) {
      throw new Error('change and patchRange must be set');
    }
    GerritNav.navigateToDiff(
      this.change,
      this.files[this.fileCursor.index].__path,
      this.patchRange.patchNum,
      this.patchRange.basePatchNum
    );
  }

  // Private but used in tests.
  shouldHideChangeTotals(patchChange: PatchChange): boolean {
    return patchChange.inserted === 0 && patchChange.deleted === 0;
  }

  // Private but used in tests.
  shouldHideBinaryChangeTotals(patchChange: PatchChange) {
    return (
      patchChange.size_delta_inserted === 0 &&
      patchChange.size_delta_deleted === 0
    );
  }

  // Private but used in tests
  computeDiffURL(path?: string) {
    if (
      this.change === undefined ||
      this.patchRange?.patchNum === undefined ||
      path === undefined ||
      this.editMode === undefined
    ) {
      return;
    }
    if (this.editMode && path !== SpecialFilePath.MERGE_LIST) {
      return GerritNav.getEditUrlForDiff(
        this.change,
        path,
        this.patchRange.patchNum
      );
    }
    return GerritNav.getUrlForDiff(
      this.change,
      path,
      this.patchRange.patchNum,
      this.patchRange.basePatchNum
    );
  }

  // Private but used in tests.
  formatBytes(bytes?: number) {
    if (!bytes) return '+/-0 B';
    const bits = 1024;
    const decimals = 1;
    const sizes = ['B', 'KiB', 'MiB', 'GiB', 'TiB', 'PiB', 'EiB', 'ZiB', 'YiB'];
    const exponent = Math.floor(Math.log(Math.abs(bytes)) / Math.log(bits));
    const prepend = bytes > 0 ? '+' : '';
    const value = parseFloat(
      (bytes / Math.pow(bits, exponent)).toFixed(decimals)
    );
    return `${prepend}${value} ${sizes[exponent]}`;
  }

  // Private but used in tests.
  formatPercentage(size?: number, delta?: number) {
    if (size === undefined || delta === undefined) {
      return '';
    }
    const oldSize = size - delta;

    if (oldSize === 0) {
      return '';
    }

    const percentage = Math.round(Math.abs((delta * 100) / oldSize));
    return `(${delta > 0 ? '+' : '-'}${percentage}%)`;
  }

  private computeBinaryClass(delta?: number) {
    if (!delta) {
      return;
    }
    return delta > 0 ? 'added' : 'removed';
  }

  private computeClass(baseClass?: string, path?: string) {
    const classes = [];
    if (baseClass) {
      classes.push(baseClass);
    }
    if (
      path === SpecialFilePath.COMMIT_MESSAGE ||
      path === SpecialFilePath.MERGE_LIST
    ) {
      classes.push('invisible');
    }
    return classes.join(' ');
  }

  private computePathClass(path: string | undefined) {
    return this.isFileExpanded(path) ? 'expanded' : '';
  }

  private computeShowHideIcon(path: string | undefined) {
    return this.isFileExpanded(path)
      ? 'gr-icons:expand-less'
      : 'gr-icons:expand-more';
  }

  private computeShowNumCleanlyMerged(): boolean {
    return this.cleanlyMergedPaths.length > 0;
  }

  private computeCleanlyMergedText(): string {
    const fileCount = pluralize(this.cleanlyMergedPaths.length, 'file');
    return `${fileCount} merged cleanly in Parent 1`;
  }

  private handleShowParent1(): void {
    if (!this.change || !this.patchRange) return;
    GerritNav.navigateToChange(this.change, {
      patchNum: this.patchRange.patchNum,
      basePatchNum: -1 as BasePatchSetNum, // Parent 1
    });
  }

  private computeFiles() {
    if (
      this.filesByPath === undefined ||
      this.changeComments === undefined ||
      this.patchRange === undefined ||
      this.reviewed === undefined ||
      this.loading === undefined
    ) {
      return;
    }
    // Await all promises resolving from reload. @See Issue 9057
    if (this.loading || !this.changeComments) {
      return;
    }
    const commentedPaths = this.changeComments.getPaths(this.patchRange);
    const files: FileNameToReviewedFileInfoMap = {...this.filesByPath};
    addUnmodifiedFiles(files, commentedPaths);
    const reviewedSet = new Set(this.reviewed || []);
    for (const [filePath, reviewedFileInfo] of Object.entries(files)) {
      reviewedFileInfo.isReviewed = reviewedSet.has(filePath);
    }
    this.files = this.normalizeChangeFilesResponse(files);
  }

  private computeFilesShown(): NormalizedFileInfo[] {
    const previousNumFilesShown = this.shownFiles ? this.shownFiles.length : 0;

    const filesShown = this.files.slice(0, this.numFilesShown);
    this.dispatchEvent(
      new CustomEvent('files-shown-changed', {
        detail: {length: filesShown.length},
        composed: true,
        bubbles: true,
      })
    );

    // Start the timer for the rendering work hwere because this is where the
    // shownFiles property is being set, and shownFiles is used in the
    // dom-repeat binding.
    this.reporting.time(Timing.FILE_RENDER);

    // How many more files are being shown (if it's an increase).
    this.reportinShownFilesIncrement = Math.max(
      0,
      filesShown.length - previousNumFilesShown
    );

    return filesShown;
  }

  // Private but used in tests.
  updateDiffCursor() {
    // Overwrite the cursor's list of diffs:
    this.diffCursor.replaceDiffs(this.diffs);
  }

  async filesChanged() {
    if (!this.files || this.files.length === 0) return;
    await this.updateComplete;
    this.fileCursor.stops = Array.from(
      this.shadowRoot?.querySelectorAll(`.${FILE_ROW_CLASS}`) ?? []
    );
    this.fileCursor.setCursorAtIndex(this.selectedIndex, true);
  }

  private incrementNumFilesShown() {
    this.numFilesShown += this.fileListIncrement;
    fire(this, 'num-files-shown-changed', {value: this.numFilesShown});
  }

  private computeFileListControlClass() {
    return this.numFilesShown >= this.files.length ? 'invisible' : '';
  }

  private computeIncrementText() {
    const text = Math.min(
      this.fileListIncrement,
      this.files.length - this.numFilesShown
    );
    return `Show ${text} more`;
  }

  private computeShowAllText() {
    return `Show all ${this.files.length} files`;
  }

  private computeWarnShowAll() {
    return this.files.length > WARN_SHOW_ALL_THRESHOLD;
  }

  private computeShowAllWarning() {
    if (!this.computeWarnShowAll()) {
      return '';
    }
    return `Warning: showing all ${this.files.length} files may take several seconds.`;
  }

  private showAllFiles() {
    this.numFilesShown = this.files.length;
    fire(this, 'num-files-shown-changed', {value: this.numFilesShown});
  }

  /**
   * Converts any boolean-like variable to the string 'true' or 'false'
   *
   * This method is useful when you bind aria-checked attribute to a boolean
   * value. The aria-checked attribute is string attribute. Binding directly
   * to boolean variable causes problem on gerrit-CI.
   *
   * @return 'true' if val is true-like, otherwise false
   */
  private booleanToString(val?: unknown) {
    return val ? 'true' : 'false';
  }

  private isFileExpanded(path: string | undefined) {
    return this.expandedFiles.some(f => f.path === path);
  }

  private isFileExpandedStr(path: string | undefined) {
    return this.booleanToString(this.isFileExpanded(path));
  }

  private computeExpandedFiles(): FilesExpandedState {
    if (this.expandedFiles.length === 0) {
      return FilesExpandedState.NONE;
    } else if (this.expandedFiles.length === this.files.length) {
      return FilesExpandedState.ALL;
    }
    return FilesExpandedState.SOME;
  }

  /**
   * Handle splices to the list of expanded file paths. If there are any new
   * entries in the expanded list, then render each diff corresponding in
   * order by waiting for the previous diff to finish before starting the next
   * one.
   *
   * @param newFiles The new files that have been added.
   * Private but used in tests.
   */
  async expandedFilesChanged(oldFiles: Array<PatchSetFile>) {
    // Clear content for any diffs that are not open so if they get re-opened
    // the stale content does not flash before it is cleared and reloaded.
    const collapsedDiffs = this.diffs.filter(
      diff => this.expandedFiles.findIndex(f => f.path === diff.path) === -1
    );
    this.clearCollapsedDiffs(collapsedDiffs);

    this.filesExpanded = this.computeExpandedFiles();

    const newFiles = this.expandedFiles.filter(
      file => (oldFiles ?? []).findIndex(f => f.path === file.path) === -1
    );

    // Required so that the newly created diff view is included in this.diffs.
    await this.updateComplete;

    if (newFiles.length) {
      await this.renderInOrder(newFiles, this.diffs);
    }
    this.updateDiffCursor();
    this.diffCursor.reInitAndUpdateStops();
  }

  // private but used in test
  clearCollapsedDiffs(collapsedDiffs: GrDiffHost[]) {
    for (const diff of collapsedDiffs) {
      diff.cancel();
      diff.clearDiffContent();
    }
  }

  /**
   * Given an array of paths and a NodeList of diff elements, render the diff
   * for each path in order, awaiting the previous render to complete before
   * continuing.
   *
   * private but used in test
   *
   * @param initialCount The total number of paths in the pass.
   */
  async renderInOrder(
    files: PatchSetFile[],
    diffElements: GrDiffHost[]) {
    this.reporting.time(Timing.FILE_EXPAND_ALL);

    for (const file of files) {
      const path = file.path;
      const diffElem = this.findDiffByPath(path, diffElements);
      if (!diffElem) {
        this.reporting.error(
          new Error(`Did not find <gr-diff-host> element for ${path}`)
        );
        return;
      }
      diffElem.prefetchDiff();
    }

    await asyncForeach(files, async (file, cancel) => {
      const path = file.path;
      this.cancelForEachDiff = cancel;

      const diffElem = this.findDiffByPath(path, diffElements);
      if (!diffElem) {
        this.reporting.error(
          new Error(`Did not find <gr-diff-host> element for ${path}`)
        );
        return;
      }
      if (!this.diffPrefs) {
        throw new Error('diffPrefs must be set');
      }

      // When one file is expanded individually then automatically mark as
      // reviewed, if the user's diff prefs request it. Doing this for
      // "Expand All" would not be what the user wants, because there is no
      // control over which diffs were actually seen. And for lots of diffs
      // that would even be a problem for write QPS quota.
      if (
        this.loggedIn &&
        !this.diffPrefs.manual_review &&
        files.length === 1
      ) {
        this.reviewFile(path, true);
      }
      await diffElem.reload();
    });

    this.cancelForEachDiff = undefined;
    this.reporting.timeEnd(Timing.FILE_EXPAND_ALL, {
      count: files.length,
      height: this.clientHeight,
    });
    /* Block diff cursor from auto scrolling after files are done rendering.
    * This prevents the bug where the screen jumps to the first diff chunk
    * after files are done being rendered after the user has already begun
    * scrolling.
    * This also however results in the fact that the cursor does not auto
    * focus on the first diff chunk on a small screen. This is however, a use
    * case we are willing to not support for now.

    * Using handleDiffUpdate resulted in diffCursor.row being set which
    * prevented the issue of scrolling to top when we expand the second
    * file individually.
    */
    this.diffCursor.reInitAndUpdateStops();
  }

  /** Cancel the rendering work of every diff in the list */
  private cancelDiffs() {
    if (this.cancelForEachDiff) {
      this.cancelForEachDiff();
    }
    this.forEachDiff(d => d.cancel());
  }

  /**
   * In the given NodeList of diff elements, find the diff for the given path.
   */
  private findDiffByPath(path: string, diffElements: GrDiffHost[]) {
    for (let i = 0; i < diffElements.length; i++) {
      if (diffElements[i].path === path) {
        return diffElements[i];
      }
    }
    return undefined;
  }

  // Private but used in tests.
  handleEscKey() {
    this.displayLine = false;
  }

  /**
   * Update the loading class for the file list rows. The update is inside a
   * debouncer so that the file list doesn't flash gray when the API requests
   * are reasonably fast.
   */
  private loadingChanged() {
    const loading = this.loading;
    this.loadingTask = debounce(
      this.loadingTask,
      () => {
        // Only show set the loading if there have been files loaded to show. In
        // this way, the gray loading style is not shown on initial loads.
        this.classList.toggle('loading', loading && !!this.files.length);
      },
      LOADING_DEBOUNCE_INTERVAL
    );
  }

  private editModeChanged() {
    this.classList.toggle('editMode', this.editMode);
  }

  private computeReviewedClass(isReviewed?: boolean) {
    return isReviewed ? 'isReviewed' : '';
  }

  private computeReviewedText(isReviewed?: boolean) {
    return isReviewed ? 'MARK UNREVIEWED' : 'MARK REVIEWED';
  }

  /**
   * Given a file path, return whether that path should have visible size bars
   * and be included in the size bars calculation.
   */
  private showBarsForPath(path?: string) {
    return (
      path !== SpecialFilePath.COMMIT_MESSAGE &&
      path !== SpecialFilePath.MERGE_LIST
    );
  }

  /**
   * Compute size bar layout values from the file list.
   * Private but used in tests.
   */
  computeSizeBarLayout() {
    const stats: SizeBarLayout = createDefaultSizeBarLayout();
    this.shownFiles
      .filter(f => this.showBarsForPath(f.__path))
      .forEach(f => {
        if (f.lines_inserted) {
          stats.maxInserted = Math.max(stats.maxInserted, f.lines_inserted);
        }
        if (f.lines_deleted) {
          stats.maxDeleted = Math.max(stats.maxDeleted, f.lines_deleted);
        }
      });
    const ratio = stats.maxInserted / (stats.maxInserted + stats.maxDeleted);
    if (!isNaN(ratio)) {
      stats.maxAdditionWidth =
        (SIZE_BAR_MAX_WIDTH - SIZE_BAR_GAP_WIDTH) * ratio;
      stats.maxDeletionWidth =
        SIZE_BAR_MAX_WIDTH - SIZE_BAR_GAP_WIDTH - stats.maxAdditionWidth;
      stats.deletionOffset = stats.maxAdditionWidth + SIZE_BAR_GAP_WIDTH;
    }
    return stats;
  }

  /**
   * Get the width of the addition bar for a file.
   * Private but used in tests.
   */
  computeBarAdditionWidth(file?: NormalizedFileInfo, stats?: SizeBarLayout) {
    if (
      !file ||
      !stats ||
      stats.maxInserted === 0 ||
      !file.lines_inserted ||
      !this.showBarsForPath(file.__path)
    ) {
      return 0;
    }
    const width =
      (stats.maxAdditionWidth * file.lines_inserted) / stats.maxInserted;
    return width === 0 ? 0 : Math.max(SIZE_BAR_MIN_WIDTH, width);
  }

  /**
   * Get the x-offset of the addition bar for a file.
   * Private but used in tests.
   */
  computeBarAdditionX(file?: NormalizedFileInfo, stats?: SizeBarLayout) {
    if (!file || !stats) return;
    return stats.maxAdditionWidth - this.computeBarAdditionWidth(file, stats);
  }

  /**
   * Get the width of the deletion bar for a file.
   * Private but used in tests.
   */
  computeBarDeletionWidth(file?: NormalizedFileInfo, stats?: SizeBarLayout) {
    if (
      !file ||
      !stats ||
      stats.maxDeleted === 0 ||
      !file.lines_deleted ||
      !this.showBarsForPath(file.__path)
    ) {
      return 0;
    }
    const width =
      (stats.maxDeletionWidth * file.lines_deleted) / stats.maxDeleted;
    return width === 0 ? 0 : Math.max(SIZE_BAR_MIN_WIDTH, width);
  }

  /**
   * Get the x-offset of the deletion bar for a file.
   */
  private computeBarDeletionX(stats: SizeBarLayout) {
    return stats.deletionOffset;
  }

  // Private but used in tests.
  computeSizeBarsClass(path?: string) {
    let hideClass = '';
    if (!this.showSizeBars) {
      hideClass = 'hide';
    } else if (!this.showBarsForPath(path)) {
      hideClass = 'invisible';
    }
    return `sizeBars ${hideClass}`;
  }

  /**
   * Shows registered dynamic columns iff the 'header', 'content' and
   * 'summary' endpoints are registered the exact same number of times.
   * Ideally, there should be a better way to enforce the expectation of the
   * dependencies between dynamic endpoints.
   */
  private computeShowDynamicColumns() {
    return !!(
      this.dynamicHeaderEndpoints &&
      this.dynamicContentEndpoints &&
      this.dynamicSummaryEndpoints &&
      this.dynamicHeaderEndpoints.length &&
      this.dynamicHeaderEndpoints.length ===
        this.dynamicContentEndpoints.length &&
      this.dynamicHeaderEndpoints.length === this.dynamicSummaryEndpoints.length
    );
  }

  /**
   * Shows registered dynamic prepended columns iff the 'header', 'content'
   * endpoints are registered the exact same number of times.
   */
  private computeShowPrependedDynamicColumns() {
    return !!(
      this.dynamicPrependedHeaderEndpoints &&
      this.dynamicPrependedContentEndpoints &&
      this.dynamicPrependedHeaderEndpoints.length &&
      this.dynamicPrependedHeaderEndpoints.length ===
        this.dynamicPrependedContentEndpoints.length
    );
  }

  /**
   * Returns true if none of the inline diffs have been expanded.
   * Private but used in tests.
   */
  noDiffsExpanded() {
    return this.filesExpanded === FilesExpandedState.NONE;
  }

  /**
   * Method to call via binding when each file list row is rendered. This
   * allows approximate detection of when the dom-repeat has completed
   * rendering.
   *
   * @param index The index of the row being rendered.
   * Private but used in tests.
   */
  reportRenderedRow(index: number) {
    if (index === this.shownFiles.length - 1) {
      setTimeout(() => {
        this.reporting.timeEnd(Timing.FILE_RENDER, {
          count: this.reportinShownFilesIncrement,
        });
      }, 1);
    }
  }

  // Private but used in tests.
  reviewedTitle(reviewed?: boolean) {
    if (reviewed) {
      return 'Mark as not reviewed (shortcut: r)';
    }

    return 'Mark as reviewed (shortcut: r)';
  }

  private handleReloadingDiffPreference() {
    this.userModel.getDiffPreferences();
  }

  private getOldPath(file: NormalizedFileInfo) {
    // The gr-endpoint-decorator is waiting until all gr-endpoint-param
    // values are updated.
    // The old_path property is undefined for added files, and the
    // gr-endpoint-param value bound to file.old_path is never updates.
    // As a results, the gr-endpoint-decorator doesn't work for added files.
    // As a workaround, this method returns null instead of undefined.
    return file.old_path ?? null;
  }
}

declare global {
  interface HTMLElementEventMap {
    'num-files-shown-changed': ValueChangedEvent<number>;
    'files-expanded-changed': ValueChangedEvent<FilesExpandedState>;
    'diff-prefs-changed': ValueChangedEvent<DiffPreferencesInfo>;
  }
  interface HTMLElementTagNameMap {
    'gr-file-list': GrFileList;
  }
}
