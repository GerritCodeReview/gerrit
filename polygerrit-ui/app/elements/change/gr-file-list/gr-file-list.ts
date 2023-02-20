/**
 * @license
 * Copyright 2015 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../styles/gr-a11y-styles';
import '../../../styles/shared-styles';
import '../../../embed/diff/gr-diff-cursor/gr-diff-cursor';
import '../../diff/gr-diff-host/gr-diff-host';
import '../../diff/gr-diff-preferences-dialog/gr-diff-preferences-dialog';
import '../../edit/gr-edit-file-controls/gr-edit-file-controls';
import '../../shared/gr-button/gr-button';
import '../../shared/gr-cursor-manager/gr-cursor-manager';
import '../../shared/gr-icon/gr-icon';
import '../../shared/gr-select/gr-select';
import '../../shared/gr-tooltip-content/gr-tooltip-content';
import '../../shared/gr-copy-clipboard/gr-copy-clipboard';
import '../../shared/gr-file-status/gr-file-status';
import {assertIsDefined} from '../../../utils/common-util';
import {asyncForeach} from '../../../utils/async-util';
import {FilesExpandedState} from '../gr-file-list-constants';
import {diffFilePaths, pluralize} from '../../../utils/string-util';
import {navigationToken} from '../../core/gr-navigation/gr-navigation';
import {getPluginEndpoints} from '../../shared/gr-js-api-interface/gr-plugin-endpoints';
import {getPluginLoader} from '../../shared/gr-js-api-interface/gr-plugin-loader';
import {getAppContext} from '../../../services/app-context';
import {
  DiffViewMode,
  FileInfoStatus,
  ScrollMode,
  SpecialFilePath,
} from '../../../constants/constants';
import {descendedFromClass, Key, toggleClass} from '../../../utils/dom-util';
import {
  computeDisplayPath,
  computeTruncatedPath,
  isMagicPath,
} from '../../../utils/path-list-util';
import {customElement, property, query, state} from 'lit/decorators.js';
import {
  BasePatchSetNum,
  EDIT,
  FileInfo,
  NumericChangeId,
  PARENT,
  PatchRange,
  RevisionPatchSetNum,
} from '../../../types/common';
import {DiffPreferencesInfo} from '../../../types/diff';
import {GrDiffHost} from '../../diff/gr-diff-host/gr-diff-host';
import {GrDiffPreferencesDialog} from '../../diff/gr-diff-preferences-dialog/gr-diff-preferences-dialog';
import {GrDiffCursor} from '../../../embed/diff/gr-diff-cursor/gr-diff-cursor';
import {GrCursorManager} from '../../shared/gr-cursor-manager/gr-cursor-manager';
import {ChangeComments} from '../../diff/gr-comment-api/gr-comment-api';
import {ParsedChangeInfo, PatchSetFile} from '../../../types/types';
import {Interaction, Timing} from '../../../constants/reporting';
import {RevisionInfo} from '../../shared/revision-info/revision-info';
import {select} from '../../../utils/observable-util';
import {resolve} from '../../../models/dependency';
import {browserModelToken} from '../../../models/browser/browser-model';
import {commentsModelToken} from '../../../models/comments/comments-model';
import {changeModelToken} from '../../../models/change/change-model';
import {filesModelToken} from '../../../models/change/files-model';
import {ShortcutController} from '../../lit/shortcut-controller';
import {css, html, LitElement, nothing, PropertyValues} from 'lit';
import {Shortcut} from '../../../services/shortcuts/shortcuts-config';
import {fire} from '../../../utils/event-util';
import {a11yStyles} from '../../../styles/gr-a11y-styles';
import {sharedStyles} from '../../../styles/shared-styles';
import {ValueChangedEvent} from '../../../types/events';
import {subscribe} from '../../lit/subscription-controller';
import {when} from 'lit/directives/when.js';
import {classMap} from 'lit/directives/class-map.js';
import {incrementalRepeat} from '../../lit/incremental-repeat';
import {ifDefined} from 'lit/directives/if-defined.js';
import {HtmlPatched} from '../../../utils/lit-util';
import {createDiffUrl} from '../../../models/views/diff';
import {createEditUrl} from '../../../models/views/edit';
import {createChangeUrl} from '../../../models/views/change';

export const DEFAULT_NUM_FILES_SHOWN = 200;

const WARN_SHOW_ALL_THRESHOLD = 1000;

const SIZE_BAR_MAX_WIDTH = 61;
const SIZE_BAR_GAP_WIDTH = 1;
const SIZE_BAR_MIN_WIDTH = 1.5;

const FILE_ROW_CLASS = 'file-row';

export interface NormalizedFileInfo extends FileInfo {
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

declare global {
  interface HTMLElementEventMap {
    'files-shown-changed': CustomEvent<{length: number}>;
    'files-expanded-changed': ValueChangedEvent<FilesExpandedState>;
    'diff-prefs-changed': ValueChangedEvent<DiffPreferencesInfo>;
  }
  interface HTMLElementTagNameMap {
    'gr-file-list': GrFileList;
  }
}
@customElement('gr-file-list')
export class GrFileList extends LitElement {
  /**
   * @event files-expanded-changed
   * @event files-shown-changed
   * @event diff-prefs-changed
   */
  @query('#diffPreferencesDialog')
  diffPreferencesDialog?: GrDiffPreferencesDialog;

  get patchRange(): PatchRange | undefined {
    if (!this.patchNum) return undefined;
    return {
      patchNum: this.patchNum,
      basePatchNum: this.basePatchNum,
    };
  }

  // Private but used in tests.
  @state()
  patchNum?: RevisionPatchSetNum;

  // Private but used in tests.
  @state()
  basePatchNum: BasePatchSetNum = PARENT;

  @property({type: Number})
  changeNum?: NumericChangeId;

  @property({type: Object})
  changeComments?: ChangeComments;

  @state() selectedIndex = 0;

  @property({type: Object})
  change?: ParsedChangeInfo;

  @state()
  diffViewMode?: DiffViewMode;

  @property({type: Boolean})
  editMode?: boolean;

  private _filesExpanded = FilesExpandedState.NONE;

  get filesExpanded() {
    return this._filesExpanded;
  }

  set filesExpanded(filesExpanded: FilesExpandedState) {
    if (this._filesExpanded === filesExpanded) return;
    const oldFilesExpanded = this._filesExpanded;
    this._filesExpanded = filesExpanded;
    fire(this, 'files-expanded-changed', {value: this._filesExpanded});
    this.requestUpdate('filesExpanded', oldFilesExpanded);
  }

  // Private but used in tests.
  @state()
  files: NormalizedFileInfo[] = [];

  // Private but used in tests.
  @state() filesLeftBase: NormalizedFileInfo[] = [];

  @state() private filesRightBase: NormalizedFileInfo[] = [];

  // Private but used in tests.
  @state()
  loggedIn = false;

  /**
   * List of paths of files that are marked as reviewed. Direct model
   * subscription.
   */
  @state()
  reviewed: string[] = [];

  @state()
  diffPrefs?: DiffPreferencesInfo;

  @state() numFilesShown = DEFAULT_NUM_FILES_SHOWN;

  @state()
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

  private readonly getFilesModel = resolve(this, filesModelToken);

  private readonly getCommentsModel = resolve(this, commentsModelToken);

  private readonly getBrowserModel = resolve(this, browserModelToken);

  private readonly patched = new HtmlPatched(key => {
    this.reporting.reportInteraction(Interaction.AUTOCLOSE_HTML_PATCHED, {
      component: this.tagName,
      key: key.substring(0, 300),
    });
  });

  shortcutsController = new ShortcutController(this);

  private readonly getNavigation = resolve(this, navigationToken);

  // private but used in test
  fileCursor = new GrCursorManager();

  // private but used in test
  diffCursor?: GrDiffCursor;

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
        .reviewed {
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
        .status {
          margin-right: var(--spacing-m);
          display: flex;
          width: 20px;
          justify-content: flex-end;
        }
        .status.extended {
          width: 56px;
        }
        .status > * {
          display: block;
        }
        .header-row .status .content {
          width: 20px;
          text-align: center;
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
        .matchingFilePath {
          color: var(--deemphasized-text-color);
        }
        .newFilePath {
          color: var(--primary-text-color);
        }
        .fileName {
          color: var(--link-color);
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

        .file-status-arrow {
          font-size: 16px;
          position: relative;
          top: 2px;
          display: block;
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
    this.shortcutsController.addAbstract(
      Shortcut.CURSOR_NEXT_FILE,
      e => this.handleCursorNext(e),
      {preventDefault: false}
    );
    this.shortcutsController.addAbstract(
      Shortcut.CURSOR_PREV_FILE,
      e => this.handleCursorPrev(e),
      {preventDefault: false}
    );
    // This is already been taken care of by CURSOR_NEXT_FILE above. The two
    // shortcuts share the same bindings. It depends on whether all files
    // are expanded whether the cursor moves to the next file or line.
    this.shortcutsController.addAbstract(Shortcut.NEXT_LINE, _ => {}, {
      preventDefault: false,
    }); // docOnly
    // This is already been taken care of by CURSOR_PREV_FILE above. The two
    // shortcuts share the same bindings. It depends on whether all files
    // are expanded whether the cursor moves to the previous file or line.
    this.shortcutsController.addAbstract(Shortcut.PREV_LINE, _ => {}, {
      preventDefault: false,
    }); // docOnly
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
    this.shortcutsController.addGlobal({key: Key.ESC}, _ =>
      this.handleEscKey()
    );
    this.shortcutsController.addAbstract(
      Shortcut.EXPAND_ALL_COMMENT_THREADS,
      _ => {}
    ); // docOnly
    this.shortcutsController.addAbstract(
      Shortcut.COLLAPSE_ALL_COMMENT_THREADS,
      _ => {}
    ); // docOnly
    this.shortcutsController.addLocal(
      {key: Key.ENTER},
      _ => this.handleOpenFile(),
      {
        shouldSuppress: true,
      }
    );
    subscribe(
      this,
      () => this.getCommentsModel().changeComments$,
      changeComments => {
        this.changeComments = changeComments;
      }
    );
    subscribe(
      this,
      () => this.getFilesModel().filesWithUnmodified$,
      files => {
        this.files = [...files];
      }
    );
    subscribe(
      this,
      () => this.getFilesModel().filesLeftBase$,
      files => {
        this.filesLeftBase = [...files];
      }
    );
    subscribe(
      this,
      () => this.getFilesModel().filesRightBase$,
      files => {
        this.filesRightBase = [...files];
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
      () => this.userModel.loggedIn$,
      loggedIn => {
        this.loggedIn = loggedIn;
      }
    );
    subscribe(
      this,
      () => this.getChangeModel().reviewedFiles$,
      reviewedFiles => {
        this.reviewed = reviewedFiles ?? [];
      }
    );
    subscribe(
      this,
      () => this.getChangeModel().patchNum$,
      x => (this.patchNum = x)
    );
    subscribe(
      this,
      () => this.getChangeModel().basePatchNum$,
      x => (this.basePatchNum = x)
    );
  }

  override willUpdate(changedProperties: PropertyValues): void {
    if (
      changedProperties.has('diffPrefs') ||
      changedProperties.has('diffViewMode')
    ) {
      this.updateDiffPreferences();
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
      this.expandedFilesChanged(changedProperties.get('expandedFiles'));
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
          this.reporting.error(
            'Plugin change-view-file-list',
            new Error('dynamic header/content mismatch')
          );
        }
        if (
          this.dynamicPrependedHeaderEndpoints.length !==
          this.dynamicPrependedContentEndpoints.length
        ) {
          this.reporting.error(
            'Plugin change-view-file-list',
            new Error('dynamic prepend header/content mismatch')
          );
        }
        if (
          this.dynamicHeaderEndpoints.length !==
          this.dynamicSummaryEndpoints.length
        ) {
          this.reporting.error(
            'Plugin change-view-file-list',
            new Error('dynamic header/summary mismatch')
          );
        }
      });
    this.diffCursor = new GrDiffCursor();
    this.diffCursor.replaceDiffs(this.diffs);
  }

  override disconnectedCallback() {
    this.diffCursor?.dispose();
    this.fileCursor.unsetCursor();
    this.cancelDiffs();
    super.disconnectedCallback();
  }

  protected override async getUpdateComplete(): Promise<boolean> {
    const result = await super.getUpdateComplete();
    await Promise.all(this.diffs.map(d => d.updateComplete));
    return result;
  }

  override render() {
    this.classList.toggle('editMode', this.editMode);
    const patchChange = this.calculatePatchChange();
    return html`
      <h3 class="assistive-tech-only">File list</h3>
      ${this.renderContainer()} ${this.renderChangeTotals(patchChange)}
      ${this.renderBinaryTotals(patchChange)} ${this.renderControlRow()}
      <gr-diff-preferences-dialog
        id="diffPreferencesDialog"
        @reload-diff-preference=${this.handleReloadingDiffPreference}
      >
      </gr-diff-preferences-dialog>
    `;
  }

  private renderContainer() {
    return html`
      <div
        id="container"
        @click=${(e: MouseEvent) => this.handleFileListClick(e)}
        role="grid"
        aria-label="Files list"
      >
        ${this.renderHeaderRow()} ${this.renderShownFiles()}
        ${when(this.computeShowNumCleanlyMerged(), () =>
          this.renderCleanlyMerged()
        )}
      </div>
    `;
  }

  private renderHeaderRow() {
    const showPrependedDynamicColumns =
      this.computeShowPrependedDynamicColumns();
    const showDynamicColumns = this.computeShowDynamicColumns();
    return html` <div class="header-row row" role="row">
      <!-- endpoint: change-view-file-list-header-prepend -->
      ${when(showPrependedDynamicColumns, () =>
        this.renderPrependedHeaderEndpoints()
      )}
      ${this.renderFileStatus()}
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

  // for DIFF_AUTOCLOSE logging purposes only
  private shownFilesOld: NormalizedFileInfo[] = this.shownFiles;

  private renderShownFiles() {
    const showDynamicColumns = this.computeShowDynamicColumns();
    const showPrependedDynamicColumns =
      this.computeShowPrependedDynamicColumns();
    const sizeBarLayout = this.computeSizeBarLayout();

    // for DIFF_AUTOCLOSE logging purposes only
    if (
      this.shownFilesOld.length > 0 &&
      this.shownFiles !== this.shownFilesOld
    ) {
      this.reporting.reportInteraction(
        Interaction.DIFF_AUTOCLOSE_SHOWN_FILES_CHANGED
      );
    }
    this.shownFilesOld = this.shownFiles;
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
      targetFrameRate: 1,
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
    const previousFileName = this.shownFiles[index - 1]?.__path;
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
        ${this.renderFileStatus(file)}
        ${this.renderFilePath(file, previousFileName)}
        ${this.renderFileComments(file)}
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
        () => this.patched.html`
          <gr-diff-host
            ?noAutoRender=${true}
            ?showLoadFailure=${true}
            .displayLine=${this.displayLine}
            .changeNum=${this.changeNum}
            .change=${this.change}
            .patchRange=${this.patchRange}
            .file=${patchSetFile}
            .path=${file.__path}
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

  private renderFileStatus(file?: NormalizedFileInfo) {
    const hasExtendedStatus = this.filesLeftBase.length > 0;
    const leftStatus = this.renderFileStatusLeft(file?.__path);
    const rightStatus = this.renderFileStatusRight(file);
    return html`<div
      class=${classMap({status: true, extended: hasExtendedStatus})}
      role="gridcell"
    >
      ${leftStatus}${rightStatus}
    </div>`;
  }

  private renderDivWithTooltip(content: string, tooltip: string) {
    return html`
      <gr-tooltip-content title=${tooltip} has-tooltip>
        <div class="content">${content}</div>
      </gr-tooltip-content>
    `;
  }

  private renderFileStatusRight(file?: NormalizedFileInfo) {
    const hasExtendedStatus = this.filesLeftBase.length > 0;
    // no file means "header row"
    if (!file) {
      const psNum = this.patchNum;
      return hasExtendedStatus
        ? this.renderDivWithTooltip(`${psNum}`, `Patchset ${psNum}`)
        : nothing;
    }
    if (isMagicPath(file.__path)) return nothing;

    const fileWasAlreadyChanged = this.filesLeftBase.some(
      info => info.__path === file?.__path
    );
    const fileIsReverted =
      fileWasAlreadyChanged &&
      !this.filesRightBase.some(info => info.__path === file?.__path);
    const newlyChanged = hasExtendedStatus && !fileWasAlreadyChanged;

    const status = fileIsReverted
      ? FileInfoStatus.REVERTED
      : file?.status ?? FileInfoStatus.MODIFIED;
    const left = `patchset ${this.basePatchNum}`;
    const right = `patchset ${this.patchNum}`;
    const postfix = ` between ${left} and ${right}`;

    return html`<gr-file-status
      .status=${status}
      .labelPostfix=${postfix}
      ?newlyChanged=${newlyChanged}
    ></gr-file-status>`;
  }

  private renderFileStatusLeft(path?: string) {
    if (this.filesLeftBase.length === 0) return nothing;
    // no path means "header row"
    const psNum = this.basePatchNum;
    if (!path) {
      return html`
        ${this.renderDivWithTooltip(`${psNum}`, `Patchset ${psNum}`)}
        <gr-icon icon="arrow_right_alt" class="file-status-arrow"></gr-icon>
      `;
    }
    if (isMagicPath(path)) return nothing;
    const file = this.filesLeftBase.find(info => info.__path === path);
    if (!file) return nothing;

    const status = file.status ?? FileInfoStatus.MODIFIED;
    const left = 'base';
    const right = `patchset ${this.basePatchNum}`;
    const postfix = ` between ${left} and ${right}`;

    return html`
      <gr-file-status
        .status=${status}
        .labelPostfix=${postfix}
      ></gr-file-status>
      <gr-icon icon="arrow_right_alt" class="file-status-arrow"></gr-icon>
    `;
  }

  private renderFilePath(file: NormalizedFileInfo, previousFilePath?: string) {
    return html`
      <span class="path" role="gridcell">
        <a class="pathLink" href=${ifDefined(this.computeDiffURL(file.__path))}>
          <span title=${computeDisplayPath(file.__path)} class="fullFileName">
            ${this.renderStyledPath(file.__path, previousFilePath)}
          </span>
          <span
            title=${computeDisplayPath(file.__path)}
            class="truncatedFileName"
          >
            ${computeTruncatedPath(file.__path)}
          </span>
          <gr-copy-clipboard
            ?hideInput=${true}
            .text=${file.__path}
          ></gr-copy-clipboard>
        </a>
        ${when(
          file.old_path,
          () => html`
            <div class="oldPath" title=${ifDefined(file.old_path)}>
              ${file.old_path}
              <gr-copy-clipboard
                ?hideInput=${true}
                .text=${file.old_path}
              ></gr-copy-clipboard>
            </div>
          `
        )}
      </span>
    `;
  }

  private renderStyledPath(filePath: string, previousFilePath?: string) {
    const {matchingFolders, newFolders, fileName} = diffFilePaths(
      filePath,
      previousFilePath
    );
    return [
      matchingFolders.length > 0
        ? html`<span class="matchingFilePath">${matchingFolders}</span>`
        : nothing,
      newFolders.length > 0
        ? html`<span class="newFilePath">${newFolders}</span>`
        : nothing,
      html`<span class="fileName">${fileName}</span>`,
    ];
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
    return this.dynamicContentEndpoints?.map(
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
    const isReviewed = this.reviewed.includes(file.__path);
    const reviewedTitle = `Mark as ${
      isReviewed ? 'not ' : ''
    }reviewed (shortcut: r)`;
    const reviewedText = isReviewed ? 'MARK UNREVIEWED' : 'MARK REVIEWED';
    return html` <div class="reviewed hideOnEdit" role="gridcell">
      <span
        class=${`reviewedLabel ${isReviewed ? 'isReviewed' : ''}`}
        aria-hidden=${this.booleanToString(!isReviewed)}
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
        aria-checked=${this.booleanToString(isReviewed)}
      >
        <!-- Trick with tabindex to avoid outline on mouse focus, but
            preserve focus outline for keyboard navigation -->
        <span tabindex="-1" class="markReviewed" title=${reviewedTitle}
          >${reviewedText}</span
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
        <gr-icon
          class="show-hide-icon"
          tabindex="-1"
          id="icon"
          icon=${this.computeShowHideIcon(file.__path)}
        ></gr-icon>
      </span>
    </div>`;
  }

  private renderCleanlyMerged() {
    const showPrependedDynamicColumns =
      this.computeShowPrependedDynamicColumns();
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

  private renderChangeTotals(patchChange: PatchChange) {
    const showDynamicColumns = this.computeShowDynamicColumns();
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

  protected override firstUpdated(): void {
    this.detectChromiteButler();
    this.reporting.fileListDisplayed();
  }

  protected override updated(): void {
    // for DIFF_AUTOCLOSE logging purposes only
    const ids = this.diffs.map(d => d.uid);
    if (ids.length > 0) {
      this.reporting.reportInteraction(
        Interaction.DIFF_AUTOCLOSE_FILE_LIST_UPDATED,
        {l: ids.length, ids: ids.slice(0, 10)}
      );
    }
  }

  // TODO: Move into files-model.
  // visible for testing
  async updateCleanlyMergedPaths() {
    // When viewing Auto Merge base vs a patchset, add an additional row that
    // knows how many files were cleanly merged. This requires an additional RPC
    // for the diffs between target parent and the patch set. The cleanly merged
    // files are all the files in the target RPC that weren't in the Auto Merge
    // RPC.
    if (
      this.change &&
      this.changeNum &&
      this.patchNum &&
      new RevisionInfo(this.change).isMergeCommit(this.patchNum) &&
      this.basePatchNum === PARENT &&
      this.patchNum !== EDIT
    ) {
      const allFilesByPath = await this.restApiService.getChangeOrEditFiles(
        this.changeNum,
        {
          basePatchNum: -1 as BasePatchSetNum, // -1 is first (target) parent
          patchNum: this.patchNum,
        }
      );
      if (!allFilesByPath) return;
      const conflictingPaths = this.files.map(f => f.__path);
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

  resetFileState() {
    this.numFilesShown = DEFAULT_NUM_FILES_SHOWN;
    this.selectedIndex = 0;
    this.fileCursor.setCursorAtIndex(this.selectedIndex, true);
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
        obj.binary && obj.size_delta && obj.size_delta > 0 ? obj.size_delta : 0;
      const size_delta_deleted =
        obj.binary && obj.size_delta && obj.size_delta < 0 ? obj.size_delta : 0;

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
    this.reporting.reportInteraction(
      Interaction.DIFF_AUTOCLOSE_RELOAD_FILELIST_PREFS
    );

    // Re-render all expanded diffs sequentially.
    this.renderInOrder(this.expandedFiles, this.diffs);
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
    if (this.editMode) return Promise.resolve();
    reviewed = reviewed ?? !this.reviewed.includes(path);
    return this._saveReviewedState(path, reviewed);
  }

  _saveReviewedState(path: string, reviewed: boolean) {
    assertIsDefined(this.changeNum, 'changeNum');
    assertIsDefined(this.patchRange, 'patchRange');

    return this.getChangeModel().setReviewedFilesStatus(
      this.changeNum,
      this.patchRange.patchNum,
      path,
      reviewed
    );
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
    this.diffCursor?.moveLeft();
  }

  private handleRightPane() {
    if (this.noDiffsExpanded()) return;
    this.diffCursor?.moveRight();
  }

  private handleToggleInlineDiff() {
    if (this.fileCursor.index === -1) return;
    this.toggleFileExpandedByIndex(this.fileCursor.index);
  }

  // Private but used in tests.
  handleCursorNext(e: KeyboardEvent) {
    // We want to allow users to use arrow keys for standard browser scrolling
    // when files are not expanded. That is also why we use the `preventDefault`
    // option when registering the shortcut.
    if (this.filesExpanded !== FilesExpandedState.ALL && e.key === Key.DOWN) {
      return;
    }

    e.preventDefault();
    e.stopPropagation();
    if (this.filesExpanded === FilesExpandedState.ALL) {
      this.diffCursor?.moveDown();
      this.displayLine = true;
    } else {
      this.fileCursor.next({circular: true});
      this.selectedIndex = this.fileCursor.index;
    }
  }

  // Private but used in tests.
  handleCursorPrev(e: KeyboardEvent) {
    // We want to allow users to use arrow keys for standard browser scrolling
    // when files are not expanded. That is also why we use the `preventDefault`
    // option when registering the shortcut.
    if (this.filesExpanded !== FilesExpandedState.ALL && e.key === Key.UP) {
      return;
    }

    e.preventDefault();
    e.stopPropagation();
    if (this.filesExpanded === FilesExpandedState.ALL) {
      this.diffCursor?.moveUp();
      this.displayLine = true;
    } else {
      this.fileCursor.previous({circular: true});
      this.selectedIndex = this.fileCursor.index;
    }
  }

  private handleNewComment() {
    this.classList.remove('hideComments');
    this.diffCursor?.createCommentInPlace();
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
    this.diffCursor?.moveToNextChunk();
  }

  private handleNextComment() {
    if (this.noDiffsExpanded()) return;
    this.diffCursor?.moveToNextCommentThread();
  }

  private handlePrevChunk() {
    if (this.noDiffsExpanded()) return;
    this.diffCursor?.moveToPreviousChunk();
  }

  private handlePrevComment() {
    if (this.noDiffsExpanded()) return;
    this.diffCursor?.moveToPreviousCommentThread();
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
    const diff = this.diffCursor?.getTargetDiffElement();
    if (!this.change || !diff || !this.patchRange || !diff.path) {
      throw new Error('change, diff and patchRange must be all set and valid');
    }
    this.getNavigation().setUrl(
      createDiffUrl({
        change: this.change,
        path: diff.path,
        patchNum: this.patchRange.patchNum,
        basePatchNum: this.patchRange.basePatchNum,
      })
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
    this.getNavigation().setUrl(
      createDiffUrl({
        change: this.change,
        path: this.files[this.fileCursor.index].__path,
        patchNum: this.patchRange.patchNum,
        basePatchNum: this.patchRange.basePatchNum,
      })
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
      return createEditUrl({
        changeNum: this.change._number,
        project: this.change.project,
        path,
        patchNum: this.patchRange.patchNum,
      });
    }
    return createDiffUrl({
      changeNum: this.change._number,
      project: this.change.project,
      path,
      patchNum: this.patchRange.patchNum,
      basePatchNum: this.patchRange.basePatchNum,
    });
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
    if (baseClass) classes.push(baseClass);
    if (isMagicPath(path)) classes.push('invisible');
    return classes.join(' ');
  }

  private computePathClass(path: string | undefined) {
    return this.isFileExpanded(path) ? 'expanded' : '';
  }

  private computeShowHideIcon(path: string | undefined) {
    return this.isFileExpanded(path) ? 'expand_less' : 'expand_more';
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
    this.getNavigation().setUrl(
      createChangeUrl({
        change: this.change,
        patchNum: this.patchRange.patchNum,
        basePatchNum: -1 as BasePatchSetNum, // Parent 1
      })
    );
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

    // Start the timer for the rendering work here because this is where the
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
    this.diffCursor?.replaceDiffs(this.diffs);
  }

  async filesChanged() {
    if (this.expandedFiles.length > 0) this.expandedFiles = [];
    await this.updateCleanlyMergedPaths();
    if (!this.files || this.files.length === 0) return;
    await this.updateComplete;
    this.fileCursor.stops = Array.from(
      this.shadowRoot?.querySelectorAll(`.${FILE_ROW_CLASS}`) ?? []
    );
    this.fileCursor.setCursorAtIndex(this.selectedIndex, true);
  }

  private incrementNumFilesShown() {
    this.numFilesShown += this.fileListIncrement;
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
    this.diffCursor?.reInitAndUpdateStops();
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
  async renderInOrder(files: PatchSetFile[], diffElements: GrDiffHost[]) {
    this.reporting.time(Timing.FILE_EXPAND_ALL);

    for (const file of files) {
      const path = file.path;
      const diffElem = this.findDiffByPath(path, diffElements);
      if (!diffElem) {
        this.reporting.error(
          'GrFileList',
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
          'GrFileList',
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
        await this.reviewFile(path, true);
      }
      await diffElem.reload();
    });

    this.cancelForEachDiff = undefined;
    this.reporting.timeEnd(Timing.FILE_EXPAND_ALL, {
      count: files.length,
      height: this.clientHeight,
    });
    /*
    * Block diff cursor from auto scrolling after files are done rendering.
    * This prevents the bug where the screen jumps to the first diff chunk
    * after files are done being rendered after the user has already begun
    * scrolling.
    * This also however results in the fact that the cursor does not auto
    * focus on the first diff chunk on a small screen. This is however, a use
    * case we are willing to not support for now.

    * Using reInit resulted in diffCursor.row being set which
    * prevented the issue of scrolling to top when we expand the second
    * file individually.
    */
    this.diffCursor?.reInitAndUpdateStops();
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
   * Compute size bar layout values from the file list.
   * Private but used in tests.
   */
  computeSizeBarLayout() {
    const stats: SizeBarLayout = createDefaultSizeBarLayout();
    this.shownFiles
      .filter(f => !isMagicPath(f.__path))
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
      !!isMagicPath(file.__path)
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
      !!isMagicPath(file.__path)
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
    } else if (isMagicPath(path)) {
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
