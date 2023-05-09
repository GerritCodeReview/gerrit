/**
 * @license
 * Copyright 2015 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../styles/shared-styles';
import '../../../elements/shared/gr-button/gr-button';
import '../../../elements/shared/gr-icon/gr-icon';
import '../gr-diff-highlight/gr-diff-highlight';
import '../gr-diff-selection/gr-diff-selection';
import '../gr-syntax-themes/gr-syntax-theme';
import '../gr-ranged-comment-themes/gr-ranged-comment-theme';
import '../gr-ranged-comment-hint/gr-ranged-comment-hint';
import {GrDiffLine, LineNumber} from './gr-diff-line';
import {
  getLine,
  getLineElByChild,
  getLineNumber,
  getRange,
  getSide,
  GrDiffThreadElement,
  isLongCommentRange,
  isThreadEl,
  rangesEqual,
  getResponsiveMode,
  isResponsive,
  getDiffLength,
  getSideByLineEl,
  toCommentThreadModel,
  compareComments,
  FullContext,
  FULL_CONTEXT,
  diffClasses,
  DiffContextExpandedEventDetail,
} from './gr-diff-utils';
import {BlameInfo, CommentRange, ImageInfo} from '../../../types/common';
import {DiffInfo, DiffPreferencesInfo} from '../../../types/diff';
import {
  CreateRangeCommentEventDetail,
  GrDiffHighlight,
} from '../gr-diff-highlight/gr-diff-highlight';
import {CoverageRange, DiffLayer, isDefined} from '../../../types/types';
import {
  CommentRangeLayer,
  GrRangedCommentLayer,
} from '../gr-ranged-comment-layer/gr-ranged-comment-layer';
import {
  DiffViewMode,
  Side,
  createDefaultDiffPrefs,
} from '../../../constants/constants';
import {fire, fireAlert} from '../../../utils/event-util';
import {MovedLinkClickedEvent, ValueChangedEvent} from '../../../types/events';
import {getContentEditableRange} from '../../../utils/safari-selection-util';
import {AbortStop} from '../../../api/core';
import {
  CreateCommentEventDetail as CreateCommentEventDetailApi,
  RenderPreferences,
  GrDiff as GrDiffApi,
  DisplayLine,
  ContentLoadNeededEventDetail,
} from '../../../api/diff';
import {isHtmlElement, isSafari, toggleClass} from '../../../utils/dom-util';
import {assertIsDefined} from '../../../utils/common-util';
import {GrDiffSelection} from '../gr-diff-selection/gr-diff-selection';
import {
  customElement,
  property,
  query,
  queryAll,
  state,
} from 'lit/decorators.js';
import {sharedStyles} from '../../../styles/shared-styles';
import {html, LitElement, nothing, PropertyValues} from 'lit';
import {when} from 'lit/directives/when.js';
import {grSyntaxTheme} from '../gr-syntax-themes/gr-syntax-theme';
import {grRangedCommentTheme} from '../gr-ranged-comment-themes/gr-ranged-comment-theme';
import {classMap} from 'lit/directives/class-map.js';
import {iconStyles} from '../../../styles/gr-icon-styles';
import {expandFileMode} from '../../../utils/file-util';
import {DiffModel, diffModelToken} from '../gr-diff-model/gr-diff-model';
import {provide} from '../../../models/dependency';
import {grDiffStyles} from './gr-diff-styles';
import {GrAnnotation} from '../gr-diff-highlight/gr-annotation';
import {GrCoverageLayer} from '../gr-coverage-layer/gr-coverage-layer';
import {
  GrDiffGroup,
  GrDiffGroupType,
  hideInContextControl,
} from './gr-diff-group';
import {subscribe} from '../../../elements/lit/subscription-controller';
import '../gr-diff-builder/gr-diff-builder-image';
import {GrDiffRow} from '../gr-diff-builder/gr-diff-row';
import '../gr-diff-builder/gr-diff-section';
import {GrDiffSection} from '../gr-diff-builder/gr-diff-section';

const NO_NEWLINE_LEFT = 'No newline at end of left file.';
const NO_NEWLINE_RIGHT = 'No newline at end of right file.';

const LARGE_DIFF_THRESHOLD_LINES = 10000;

const COMMIT_MSG_PATH = '/COMMIT_MSG';
/**
 * 72 is the unofficial length standard for git commit messages.
 * Derived from the fact that git log/show appends 4 ws in the beginning of
 * each line when displaying commit messages. To center the commit message
 * in an 80 char terminal a 4 ws border is added to the rightmost side:
 * 4 + 72 + 4
 */
const COMMIT_MSG_LINE_LENGTH = 72;

const TRAILING_WHITESPACE_PATTERN = /\s+$/;

export interface CreateCommentEventDetail extends CreateCommentEventDetailApi {
  path: string;
}

@customElement('gr-diff')
export class GrDiff extends LitElement implements GrDiffApi {
  /**
   * Fired when the user selects a line.
   *
   * @event line-selected
   */

  /**
   * Fired if being logged in is required.
   *
   * @event show-auth-required
   */

  /**
   * Fired when a comment is created
   *
   * @event create-comment
   */

  /**
   * Fired when rendering, including syntax highlighting, is done. Also fired
   * when no rendering can be done because required preferences are not set.
   *
   * @event render
   */

  /**
   * Fired for interaction reporting when a diff context is expanded.
   * Contains an event.detail with numLines about the number of lines that
   * were expanded.
   *
   * @event diff-context-expanded
   */

  @query('#diffTable')
  diffTable?: HTMLTableElement;

  @queryAll('gr-diff-section')
  diffSections?: NodeListOf<GrDiffSection>;

  @property({type: Boolean})
  noAutoRender = false;

  @property({type: String})
  path?: string;

  @property({type: Object})
  prefs?: DiffPreferencesInfo;

  @property({type: Object})
  renderPrefs: RenderPreferences = {};

  @property({type: Boolean})
  isImageDiff?: boolean;

  @property({type: Boolean, reflect: true})
  override hidden = false;

  @property({type: Boolean})
  noRenderOnPrefsChange?: boolean;

  // Private but used in tests.
  @state()
  commentRanges: CommentRangeLayer[] = [];

  // explicitly highlight a range if it is not associated with any comment
  @property({type: Object})
  highlightRange?: CommentRange;

  @property({type: Array})
  coverageRanges: CoverageRange[] = [];

  @property({type: Boolean})
  lineWrapping = false;

  @property({type: String})
  viewMode = DiffViewMode.SIDE_BY_SIDE;

  @property({type: Object})
  lineOfInterest?: DisplayLine;

  /**
   * True when diff is changed, until the content is done rendering.
   * Use getter/setter loading instead of this.
   */
  private _loading = true;

  get loading() {
    return this._loading;
  }

  set loading(loading: boolean) {
    if (this._loading === loading) return;
    const oldLoading = this._loading;
    this._loading = loading;
    fire(this, 'loading-changed', {value: this._loading});
    this.requestUpdate('loading', oldLoading);
  }

  @property({type: Boolean})
  loggedIn = false;

  @property({type: Object})
  diff?: DiffInfo;

  @state()
  private diffTableClass = '';

  @property({type: Object})
  baseImage?: ImageInfo;

  @property({type: Object})
  revisionImage?: ImageInfo;

  /**
   * In order to allow multi-select in Safari browsers, a workaround is required
   * to trigger 'beforeinput' events to get a list of static ranges. This is
   * obtained by making the content of the diff table "contentEditable".
   */
  @property({type: Boolean})
  override isContentEditable = isSafari();

  @property({type: String})
  errorMessage: string | null = null;

  @property({type: Array})
  blame: BlameInfo[] | null = null;

  @property({type: Boolean})
  showNewlineWarningLeft = false;

  @property({type: Boolean})
  showNewlineWarningRight = false;

  @property({type: Boolean})
  useNewImageDiffUi = false;

  // Private but used in tests.
  @state()
  diffLength?: number;

  /** Observes comment nodes added or removed at any point. */
  private nodeObserver?: MutationObserver;

  // Private but used in tests.
  diffSelection = new GrDiffSelection();

  // Private but used in tests.
  highlights = new GrDiffHighlight();

  private diffModel = new DiffModel();

  /**
   * Just the layers that are passed in from the outside. See `layersAll`
   * for an array of all layers.
   */
  @property({type: Array})
  layers: DiffLayer[] = [];

  /**
   * Just the internal default layers. See `layers` for the property that can
   * be set from the outside.
   */
  @state() layersInternal: DiffLayer[] = [];

  /**
   * All layers, just combines `layers` and `layersInternal`.
   */
  @state() layersAll: DiffLayer[] = [];

  private coverageLayerLeft = new GrCoverageLayer(Side.LEFT);

  private coverageLayerRight = new GrCoverageLayer(Side.RIGHT);

  private rangeLayer = new GrRangedCommentLayer();

  @state() groups: GrDiffGroup[] = [];

  @state() private context = 3;

  private readonly layerUpdateListener: (
    start: LineNumber,
    end: LineNumber,
    side: Side
  ) => void;

  static override get styles() {
    return [
      iconStyles,
      sharedStyles,
      grSyntaxTheme,
      grRangedCommentTheme,
      grDiffStyles,
    ];
  }

  constructor() {
    super();
    console.log(`${Date.now() % 100000} asdf gr-diff constructor`);
    provide(this, diffModelToken, () => this.diffModel);
    subscribe(
      this,
      () => this.diffModel.context$,
      context => (this.context = context)
    );
    subscribe(
      this,
      () => this.diffModel.groups$,
      groups => (this.groups = groups)
    );
    this.addEventListener(
      'create-range-comment',
      (e: CustomEvent<CreateRangeCommentEventDetail>) =>
        this.handleCreateRangeComment(e)
    );
    this.addEventListener('moved-link-clicked', (e: MovedLinkClickedEvent) => {
      this.dispatchSelectedLine(e.detail.lineNum, e.detail.side);
    });
    this.addEventListener('diff-context-expanded', this.onDiffContextExpanded);
    this.layerUpdateListener = (
      start: LineNumber,
      end: LineNumber,
      side: Side
    ) => this.renderContentByRange(start, end, side);
    this.layersInternalInit();
  }

  override connectedCallback() {
    super.connectedCallback();
    if (this.loggedIn) {
      this.addSelectionListeners();
    }
    if (this.diff && this.diffTable) {
      this.diffSelection.init(this.diff, this.diffTable);
    }
    if (this.diffTable) {
      this.highlights.init(this.diffTable, this);
    }
  }

  override disconnectedCallback() {
    this.removeSelectionListeners();
    this.diffSelection.cleanup();
    this.highlights.cleanup();
    super.disconnectedCallback();
  }

  protected override willUpdate(changedProperties: PropertyValues<this>): void {
    console.log(
      `${Date.now() % 100000} asdf gr-diff willUpdate ${[
        ...changedProperties.keys(),
      ]}`
    );

    if (
      changedProperties.has('diff') ||
      changedProperties.has('path') ||
      changedProperties.has('renderPrefs') ||
      changedProperties.has('viewMode') ||
      changedProperties.has('prefs') ||
      changedProperties.has('lineOfInterest')
    ) {
      if (this.diff && this.prefs) {
        const renderPrefs = {...(this.renderPrefs ?? {})};
        if (renderPrefs.view_mode === undefined) {
          renderPrefs.view_mode = this.viewMode;
        }
        this.diffModel.updateState({
          diff: this.diff,
          path: this.path,
          renderPrefs,
          diffPrefs: this.prefs,
          lineOfInterest: this.lineOfInterest,
          isImageDiff: this.isImageDiff,
        });
      }
    }
    if (
      changedProperties.has('path') ||
      changedProperties.has('lineWrapping') ||
      changedProperties.has('viewMode') ||
      changedProperties.has('useNewImageDiffUi') ||
      changedProperties.has('prefs')
    ) {
      this.prefsChanged();
    }
    if (changedProperties.has('layers')) {
      this.layersChanged();
    }
    if (changedProperties.has('blame')) {
      this.blameChanged();
    }
    if (changedProperties.has('renderPrefs')) {
      this.renderPrefsChanged();
    }
    if (changedProperties.has('loggedIn')) {
      if (this.loggedIn && this.isConnected) {
        this.addSelectionListeners();
      } else {
        this.removeSelectionListeners();
      }
    }
    if (changedProperties.has('coverageRanges')) {
      this.updateCoverageRanges(this.coverageRanges);
    }
    if (changedProperties.has('lineOfInterest')) {
      this.lineOfInterestChanged();
    }
  }

  private async fireRenderContent() {
    await this.updateComplete;
    console.log(
      `${Date.now() % 100000} asdf fireRenderContent ${this.groups.length}`
    );
    this.loading = false;
    this.observeNodes();
    // TODO: Retire one of these two events.
    fire(this, 'render-content', {});
    fire(this, 'render', {});
  }

  protected override async getUpdateComplete(): Promise<boolean> {
    const result = await super.getUpdateComplete();
    const sections = [...(this.diffSections ?? [])];
    await Promise.all(sections.map(section => section.updateComplete));
    console.log(`${Date.now() % 100000} asdf gr-diff updateComplete`);
    return result;
  }

  protected override updated(changedProperties: PropertyValues<this>) {
    console.log(
      `${Date.now() % 100000} asdf gr-diff updated ${[
        ...changedProperties.keys(),
      ]}`
    );
    if (changedProperties.has('diff')) {
      // diffChanged relies on diffTable having been rendered.
      this.diffChanged();
    }
    if (changedProperties.has('groups')) {
      if (this.groups?.length > 0) this.fireRenderContent();
    }
  }

  override render() {
    console.log(`${Date.now() % 100000} asdf gr-diff render`);
    fire(this.diffTable, 'render-start', {});
    return html`
      ${this.renderHeader()} ${this.renderContainer()}
      ${this.renderNewlineWarning()} ${this.renderLoadingError()}
    `;
  }

  private renderHeader() {
    const diffheaderItems = this.computeDiffHeaderItems();
    if (diffheaderItems.length === 0) return nothing;
    return html`
      <div id="diffHeader">
        ${diffheaderItems.map(item => html`<div>${item}</div>`)}
      </div>
    `;
  }

  private renderContainer() {
    const cssClasses = {
      diffContainer: true,
      unified: this.viewMode === DiffViewMode.UNIFIED,
      sideBySide: this.viewMode === DiffViewMode.SIDE_BY_SIDE,
      canComment: this.loggedIn,
    };
    return html`
      <div class=${classMap(cssClasses)} @click=${this.handleTap}>
        <table
          id="diffTable"
          class=${this.diffTableClass}
          ?contenteditable=${this.isContentEditable}
        >
          ${this.renderColumns()}
          ${when(!this.showWarning(), () =>
            this.groups.map(g => this.renderSectionElement(g))
          )}
          ${this.renderImageDiff()} ${this.renderBinaryDiff()}
        </table>
        ${when(
          this.showNoChangeMessage(),
          () => html`
            <div class="whitespace-change-only-message">
              This file only contains whitespace changes. Modify the whitespace
              setting to see the changes.
            </div>
          `
        )}
        ${when(this.showWarning(), () => this.renderSizeWarning())}
      </div>
    `;
  }

  private renderNewlineWarning() {
    const newlineWarning = this.computeNewlineWarning();
    if (!newlineWarning) return nothing;
    return html`<div class="newlineWarning">${newlineWarning}</div>`;
  }

  private renderLoadingError() {
    if (!this.errorMessage) return nothing;
    return html`<div id="loadingError">${this.errorMessage}</div>`;
  }

  private renderSizeWarning() {
    if (!this.showWarning()) return nothing;
    // TODO: Update comment about 'Whole file' as it's not in settings.
    return html`
      <div id="sizeWarning">
        <p>
          Prevented render because "Whole file" is enabled and this diff is very
          large (about ${this.diffLength} lines).
        </p>
        <gr-button @click=${this.collapseContext}>
          Render with limited context
        </gr-button>
        <gr-button @click=${this.handleFullBypass}>
          Render anyway (may be slow)
        </gr-button>
      </div>
    `;
  }

  private addSelectionListeners() {
    document.addEventListener('selectionchange', this.handleSelectionChange);
    document.addEventListener('mouseup', this.handleMouseUp);
  }

  private removeSelectionListeners() {
    document.removeEventListener('selectionchange', this.handleSelectionChange);
    document.removeEventListener('mouseup', this.handleMouseUp);
  }

  // Private but used in tests.
  showNoChangeMessage() {
    return (
      !this.loading &&
      this.diff &&
      !this.diff.binary &&
      this.prefs &&
      this.prefs.ignore_whitespace !== 'IGNORE_NONE' &&
      this.diffLength === 0
    );
  }

  private readonly handleSelectionChange = () => {
    // Because of shadow DOM selections, we handle the selectionchange here,
    // and pass the shadow DOM selection into gr-diff-highlight, where the
    // corresponding range is determined and normalized.
    const selection = this.getShadowOrDocumentSelection();
    this.highlights.handleSelectionChange(selection, false);
  };

  private readonly handleMouseUp = () => {
    // To handle double-click outside of text creating comments, we check on
    // mouse-up if there's a selection that just covers a line change. We
    // can't do that on selection change since the user may still be dragging.
    const selection = this.getShadowOrDocumentSelection();
    this.highlights.handleSelectionChange(selection, true);
  };

  /** Gets the current selection, preferring the shadow DOM selection. */
  private getShadowOrDocumentSelection() {
    // When using native shadow DOM, the selection returned by
    // document.getSelection() cannot reference the actual DOM elements making
    // up the diff in Safari because they are in the shadow DOM of the gr-diff
    // element. This takes the shadow DOM selection if one exists.
    return this.shadowRoot?.getSelection
      ? this.shadowRoot.getSelection()
      : isSafari()
      ? getContentEditableRange()
      : document.getSelection();
  }

  private updateRanges(
    addedThreadEls: GrDiffThreadElement[],
    removedThreadEls: GrDiffThreadElement[]
  ) {
    function commentRangeFromThreadEl(
      threadEl: GrDiffThreadElement
    ): CommentRangeLayer | undefined {
      const side = getSide(threadEl);
      if (!side) return undefined;
      const range = getRange(threadEl);
      if (!range) return undefined;

      return {side, range, rootId: threadEl.rootId};
    }

    // TODO(brohlfs): Rewrite `.map().filter() as ...` with `.reduce()` instead.
    const addedCommentRanges = addedThreadEls
      .map(commentRangeFromThreadEl)
      .filter(range => !!range) as CommentRangeLayer[];
    const removedCommentRanges = removedThreadEls
      .map(commentRangeFromThreadEl)
      .filter(range => !!range) as CommentRangeLayer[];
    for (const removedCommentRange of removedCommentRanges) {
      const i = this.commentRanges.findIndex(
        cr =>
          cr.side === removedCommentRange.side &&
          rangesEqual(cr.range, removedCommentRange.range)
      );
      this.commentRanges.splice(i, 1);
    }

    if (addedCommentRanges?.length) {
      this.commentRanges.push(...addedCommentRanges);
    }
    if (this.highlightRange) {
      this.commentRanges.push({
        side: Side.RIGHT,
        range: this.highlightRange,
        rootId: '',
      });
    }

    this.rangeLayer?.updateRanges(this.commentRanges);
  }

  // Dispatch events that are handled by the gr-diff-highlight.
  private redispatchHoverEvents(
    hoverEl: HTMLElement,
    threadEl: GrDiffThreadElement
  ) {
    hoverEl.addEventListener('mouseenter', () => {
      fire(threadEl, 'comment-thread-mouseenter', {});
    });
    hoverEl.addEventListener('mouseleave', () => {
      fire(threadEl, 'comment-thread-mouseleave', {});
    });
  }

  getCursorStops(): Array<HTMLElement | AbortStop> {
    if (this.hidden && this.noAutoRender) return [];

    // Get rendered stops.
    const stops: Array<HTMLElement | AbortStop> = this.getLineNumberRows();

    // If we are still loading this diff, abort after the rendered stops to
    // avoid skipping over to e.g. the next file.
    if (this.loading) {
      stops.push(new AbortStop());
    }
    return stops;
  }

  isRangeSelected() {
    return !!this.highlights.selectedRange;
  }

  toggleLeftDiff() {
    toggleClass(this, 'no-left');
  }

  private blameChanged() {
    this.setBlame(this.blame ?? []);
    if (this.blame) {
      this.classList.add('showBlame');
    } else {
      this.classList.remove('showBlame');
    }
  }

  // Private but used in tests.
  handleTap(e: Event) {
    const el = e.target as Element;

    if (
      el.getAttribute('data-value') !== 'LOST' &&
      (el.classList.contains('lineNum') ||
        el.classList.contains('lineNumButton'))
    ) {
      this.addDraftAtLine(el);
    } else if (
      el.tagName === 'HL' ||
      el.classList.contains('content') ||
      el.classList.contains('contentText')
    ) {
      const target = getLineElByChild(el);
      if (target) {
        this.selectLine(target);
      }
    }
  }

  // Private but used in tests.
  selectLine(el: Element) {
    const lineNumber = Number(el.getAttribute('data-value'));
    const side = el.classList.contains('left') ? Side.LEFT : Side.RIGHT;
    this.dispatchSelectedLine(lineNumber, side);
  }

  private dispatchSelectedLine(number: LineNumber, side: Side) {
    fire(this, 'line-selected', {
      number,
      side,
      path: this.path,
    });
  }

  addDraftAtLine(el: Element) {
    this.selectLine(el);

    const lineNum = getLineNumber(el);
    if (lineNum === null) {
      fireAlert(this, 'Invalid line number');
      return;
    }

    this.createComment(el, lineNum);
  }

  createRangeComment() {
    if (!this.isRangeSelected()) {
      throw Error('Selection is needed for new range comment');
    }
    const selectedRange = this.highlights.selectedRange;
    if (!selectedRange) throw Error('selected range not set');
    const {side, range} = selectedRange;
    this.createCommentForSelection(side, range);
  }

  createCommentForSelection(side: Side, range: CommentRange) {
    const lineNum = range.end_line;
    const lineEl = this.getLineElByNumber(lineNum, side);
    if (lineEl) {
      this.createComment(lineEl, lineNum, side, range);
    }
  }

  private handleCreateRangeComment(
    e: CustomEvent<CreateRangeCommentEventDetail>
  ) {
    const range = e.detail.range;
    const side = e.detail.side;
    this.createCommentForSelection(side, range);
  }

  // Private but used in tests.
  createComment(
    lineEl: Element,
    lineNum: LineNumber,
    side?: Side,
    range?: CommentRange
  ) {
    const contentEl = this.getContentTdByLineEl(lineEl);
    if (!contentEl) throw new Error('content el not found for line el');
    side = side ?? this.getCommentSideByLineAndContent(lineEl, contentEl);
    assertIsDefined(this.path, 'path');
    fire(this, 'create-comment', {
      path: this.path,
      side,
      lineNum,
      range,
    });
  }

  private getCommentSideByLineAndContent(
    lineEl: Element,
    contentEl: Element
  ): Side {
    return lineEl.classList.contains(Side.LEFT) ||
      contentEl.classList.contains('remove')
      ? Side.LEFT
      : Side.RIGHT;
  }

  private lineOfInterestChanged() {
    if (this.loading) return;
    if (!this.lineOfInterest) return;
    const lineNum = this.lineOfInterest.lineNum;
    if (typeof lineNum !== 'number') return;
    this.unhideLine(lineNum, this.lineOfInterest.side);
  }

  private prefsChanged() {
    if (!this.prefs) return;

    this.blame = null;
    this.updatePreferenceStyles();

    if (!Number.isInteger(this.prefs.tab_size) || this.prefs.tab_size <= 0) {
      this.handlePreferenceError('tab size');
    }
    if (
      !Number.isInteger(this.prefs.line_length) ||
      this.prefs.line_length <= 0
    ) {
      this.handlePreferenceError('diff width');
    }
  }

  private updatePreferenceStyles() {
    assertIsDefined(this.prefs, 'prefs');
    const lineLength =
      this.path === COMMIT_MSG_PATH
        ? COMMIT_MSG_LINE_LENGTH
        : this.prefs.line_length;
    const sideBySide = this.viewMode === 'SIDE_BY_SIDE';

    const responsiveMode = getResponsiveMode(this.prefs, this.renderPrefs);
    const responsive = isResponsive(responsiveMode);
    this.diffTableClass = responsive ? 'responsive' : '';
    const lineLimit = `${lineLength}ch`;
    this.style.setProperty(
      '--line-limit-marker',
      responsiveMode === 'FULL_RESPONSIVE' ? lineLimit : '-1px'
    );
    this.style.setProperty('--content-width', responsive ? 'none' : lineLimit);
    if (responsiveMode === 'SHRINK_ONLY') {
      // Calculating ideal (initial) width for the whole table including
      // width of each table column (content and line number columns) and
      // border. We also add a 1px correction as some values are calculated
      // in 'ch'.

      // We might have 1 to 2 columns for content depending if side-by-side
      // or unified mode
      const contentWidth = `${sideBySide ? 2 : 1} * ${lineLimit}`;

      // We always have 2 columns for line number
      const lineNumberWidth = `2 * ${getLineNumberCellWidth(this.prefs)}px`;

      // border-right in ".section" css definition (in gr-diff_html.ts)
      const sectionRightBorder = '1px';

      // each sign col has 1ch width.
      const signColsWidth =
        sideBySide && this.renderPrefs?.show_sign_col ? '2ch' : '0ch';

      // As some of these calculations are done using 'ch' we end up having <1px
      // difference between ideal and calculated size for each side leading to
      // lines using the max columns (e.g. 80) to wrap (decided exclusively by
      // the browser).This happens even in monospace fonts. Empirically adding
      // 2px as correction to be sure wrapping won't happen in these cases so it
      // doesn't block further experimentation with the SHRINK_MODE. This was
      // previously set to 1px but due to to a more aggressive text wrapping
      // (via word-break: break-all; - check .contextText) we need to be even
      // more lenient in some cases. If we find another way to avoid this
      // correction we will change it.
      const dontWrapCorrection = '2px';
      this.style.setProperty(
        '--diff-max-width',
        `calc(${contentWidth} + ${lineNumberWidth} + ${signColsWidth} + ${sectionRightBorder} + ${dontWrapCorrection})`
      );
    } else {
      this.style.setProperty('--diff-max-width', 'none');
    }
    if (this.prefs.font_size) {
      this.style.setProperty('--font-size', `${this.prefs.font_size}px`);
    }
  }

  private renderPrefsChanged() {
    if (this.renderPrefs.hide_left_side) {
      this.classList.add('no-left');
    }
    if (this.renderPrefs.disable_context_control_buttons) {
      this.classList.add('disable-context-control-buttons');
    }
    if (this.renderPrefs.hide_line_length_indicator) {
      this.classList.add('hide-line-length-indicator');
    }
    if (this.renderPrefs.show_sign_col) {
      this.classList.add('with-sign-col');
    }
    if (this.prefs) {
      this.updatePreferenceStyles();
    }
  }

  private diffChanged() {
    this.loading = true;
    if (this.diff) {
      this.diffLength = this.getDiffLength(this.diff);
      assertIsDefined(this.diffTable, 'diffTable');
      this.diffSelection.init(this.diff, this.diffTable);
      this.highlights.init(this.diffTable, this);
    }
  }

  // Implemented so the test can stub it.
  getDiffLength(diff?: DiffInfo) {
    return getDiffLength(diff);
  }

  private showWarning() {
    return (
      this.prefs?.context === FULL_CONTEXT &&
      this.diffModel.getState().showFullContext === FullContext.UNDECIDED &&
      this.diffLength &&
      this.diffLength >= LARGE_DIFF_THRESHOLD_LINES
    );
  }

  /**
   * This must be called once, but only after diff lines are rendered. Otherwise
   * `processNodes()` will fail to lookup the HTML elements that it wants to
   * manipulate.
   */
  private observeNodes() {
    if (this.nodeObserver) return;
    console.log(`${Date.now() % 100000} asdf observeNodes`);
    // Then introduce a Mutation observer that watches for children being added
    // to gr-diff. If those children are `isThreadEl`, namely then they are
    // processed.
    this.nodeObserver = new MutationObserver(mutations => {
      const addedThreadEls = extractAddedNodes(mutations).filter(isThreadEl);
      const removedThreadEls =
        extractRemovedNodes(mutations).filter(isThreadEl);
      this.processNodes(addedThreadEls, removedThreadEls);
    });
    this.nodeObserver.observe(this, {childList: true});
    // Make sure to process existing gr-comment-threads that already exist.
    this.processNodes([...this.childNodes].filter(isThreadEl), []);
  }

  private processNodes(
    addedThreadEls: GrDiffThreadElement[],
    removedThreadEls: GrDiffThreadElement[]
  ) {
    console.log(
      `${Date.now() % 100000} asdf processNodes ${addedThreadEls.length}`
    );
    this.diffModel.updateState({
      comments: [...this.childNodes]
        .filter(isHtmlElement)
        .map(toCommentThreadModel)
        .filter(isDefined)
        .sort(compareComments),
    });
    this.updateRanges(addedThreadEls, removedThreadEls);
    addedThreadEls.forEach(threadEl =>
      this.redispatchHoverEvents(threadEl, threadEl)
    );
    // Removed nodes do not need to be handled because all this code does is
    // adding a slot for the added thread elements, and the extra slots do
    // not hurt. It's probably a bigger performance cost to remove them than
    // to keep them around. Medium term we can even consider to add one slot
    // for each line from the start.
    for (const threadEl of addedThreadEls) {
      const lineNum = getLine(threadEl);
      const commentSide = getSide(threadEl);
      const range = getRange(threadEl);
      if (!commentSide) continue;
      const lineEl = this.getLineElByNumber(lineNum, commentSide);
      // When the line the comment refers to does not exist, log an error
      // but don't crash. This can happen e.g. if the API does not fully
      // validate e.g. (robot) comments
      if (!lineEl) {
        console.error(
          'thread attached to line ',
          commentSide,
          lineNum,
          ' which does not exist.'
        );
        continue;
      }
      const contentEl = this.getContentTdByLineEl(lineEl);
      if (!contentEl) continue;
      if (lineNum === 'LOST') {
        this.insertPortedCommentsWithoutRangeMessage(contentEl);
      }

      const slotAtt = threadEl.getAttribute('slot');
      if (range && isLongCommentRange(range) && slotAtt) {
        const longRangeCommentHint = document.createElement(
          'gr-ranged-comment-hint'
        );
        longRangeCommentHint.range = range;
        longRangeCommentHint.setAttribute('threadElRootId', threadEl.rootId);
        longRangeCommentHint.setAttribute('slot', slotAtt);
        this.insertBefore(longRangeCommentHint, threadEl);
        this.redispatchHoverEvents(longRangeCommentHint, threadEl);
      }
    }

    for (const threadEl of removedThreadEls) {
      this.querySelector(
        `gr-ranged-comment-hint[threadElRootId="${threadEl.rootId}"]`
      )?.remove();
    }
  }

  private insertPortedCommentsWithoutRangeMessage(lostCell: Element) {
    const existingMessage = lostCell.querySelector('div.lost-message');
    if (existingMessage) return;

    const div = document.createElement('div');
    div.className = 'lost-message';
    const icon = document.createElement('gr-icon');
    icon.setAttribute('icon', 'info');
    div.appendChild(icon);
    const span = document.createElement('span');
    span.innerText = 'Original comment position not found in this patchset';
    div.appendChild(span);
    lostCell.insertBefore(div, lostCell.firstChild);
  }

  // Private but used in tests.
  computeDiffHeaderItems() {
    return (this.diff?.diff_header ?? [])
      .filter(
        item =>
          !(
            item.startsWith('diff --git ') ||
            item.startsWith('index ') ||
            item.startsWith('+++ ') ||
            item.startsWith('--- ') ||
            item === 'Binary files differ'
          )
      )
      .map(expandFileMode);
  }

  private handleFullBypass() {
    this.diffModel.updateState({showFullContext: FullContext.YES});
  }

  private collapseContext() {
    this.diffModel.updateState({showFullContext: FullContext.NO});
  }

  // TODO: Migrate callers to just update prefs.context.
  toggleAllContext() {
    const current = this.diffModel.getState().showFullContext;
    this.diffModel.updateState({
      showFullContext:
        current === FullContext.YES ? FullContext.NO : FullContext.YES,
    });
  }

  private computeNewlineWarning(): string | undefined {
    const messages = [];
    if (this.showNewlineWarningLeft) {
      messages.push(NO_NEWLINE_LEFT);
    }
    if (this.showNewlineWarningRight) {
      messages.push(NO_NEWLINE_RIGHT);
    }
    if (!messages.length) {
      return undefined;
    }
    return messages.join(' \u2014 '); // \u2014 - '—'
  }

  updateCoverageRanges(rs: CoverageRange[]) {
    this.coverageLayerLeft.setRanges(rs.filter(r => r?.side === Side.LEFT));
    this.coverageLayerRight.setRanges(rs.filter(r => r?.side === Side.RIGHT));
  }

  public renderImageDiff() {
    if (!this.diff?.binary) return nothing;
    if (!this.isImageDiff) return nothing;
    return when(
      this.useNewImageDiffUi,
      () => this.renderImageDiffNew(),
      () => this.renderImageDiffOld()
    );
  }

  private renderImageDiffNew() {
    const autoBlink = !!this.renderPrefs?.image_diff_prefs?.automatic_blink;
    return html`
      <gr-diff-image-new
        .automaticBlink=${autoBlink}
        .baseImage=${this.baseImage ?? undefined}
        .revisionImage=${this.revisionImage ?? undefined}
      ></gr-diff-image-new>
    `;
  }

  private renderImageDiffOld() {
    return html`
      <gr-diff-image-old
        .baseImage=${this.baseImage ?? undefined}
        .revisionImage=${this.revisionImage ?? undefined}
      ></gr-diff-image-old>
    `;
  }

  public renderBinaryDiff() {
    if (!this.diff?.binary) return nothing;
    // `this.diff.binary` is also true for image diffs, which has its own
    // render function.
    if (this.isImageDiff) return nothing;
    return html`
      <tbody class="gr-diff binary-diff">
        <tr class="gr-diff">
          <td colspan="5" class="gr-diff">
            <span>Difference in binary files</span>
          </td>
        </tr>
      </tbody>
    `;
  }

  // visible for testing
  async untilGroupsRendered(groups: readonly GrDiffGroup[] = this.groups) {
    return Promise.all(groups.map(g => g.waitUntilRendered()));
  }

  private onDiffContextExpanded = (
    e: CustomEvent<DiffContextExpandedEventDetail>
  ) => {
    // Don't stop propagation. The host may listen for reporting or
    // resizing.
    this.diffModel.replaceGroup(e.detail.contextGroup, e.detail.groups);
  };

  private layersChanged() {
    this.layersAll = [...this.layersInternal, ...this.layers];
    for (const layer of this.layersAll) {
      layer.removeListener?.(this.layerUpdateListener);
      layer.addListener?.(this.layerUpdateListener);
    }
  }

  private layersInternalInit() {
    this.layersInternal = [
      this.createTrailingWhitespaceLayer(),
      this.createIntralineLayer(),
      this.createTabIndicatorLayer(),
      this.createSpecialCharacterIndicatorLayer(),
      this.rangeLayer,
      this.coverageLayerLeft,
      this.coverageLayerRight,
    ];
    this.layersChanged();
  }

  getContentTdByLineEl(lineEl?: Element): Element | undefined {
    if (!lineEl) return undefined;
    const line = getLineNumber(lineEl);
    if (!line) return undefined;
    const side = getSideByLineEl(lineEl);
    return this.getContentTdByLine(line, side);
  }

  /**
   * When the line is hidden behind a context expander, expand it.
   *
   * @param lineNum A line number to expand. Using number here because other
   *   special case line numbers are never hidden, so it does not make sense
   *   to expand them.
   * @param side The side the line number refer to.
   */
  unhideLine(lineNum: number, side: Side) {
    assertIsDefined(this.prefs, 'prefs');

    const group = this.findGroup(side, lineNum);
    // Cannot unhide a line that is not part of the diff.
    if (!group) return;
    // If it's already visible, great!
    if (group.type !== GrDiffGroupType.CONTEXT_CONTROL) return;
    const lineRange = group.lineRange[side];
    const lineOffset = lineNum - lineRange.start_line;
    const newGroups = [];
    const groups = hideInContextControl(
      group.contextGroups,
      0,
      lineOffset - 1 - this.context
    );
    // If there is a context group, it will be the first group because we
    // start hiding from 0 offset
    if (groups[0].type === GrDiffGroupType.CONTEXT_CONTROL) {
      newGroups.push(groups.shift()!);
    }
    newGroups.push(
      ...hideInContextControl(
        groups,
        lineOffset + 1 + this.context,
        // Both ends inclusive, so difference is the offset of the last line.
        // But we need to pass the first line not to hide, which is the element
        // after.
        lineRange.end_line - lineRange.start_line + 1
      )
    );
    this.diffModel.replaceGroup(group, newGroups);
  }

  // visible for testing
  handlePreferenceError(pref: string): never {
    const message =
      `The value of the '${pref}' user preference is ` +
      'invalid. Fix in diff preferences';
    fireAlert(this, message);
    throw Error(`Invalid preference value: ${pref}`);
  }

  // visible for testing
  createIntralineLayer(): DiffLayer {
    return {
      // Take a DIV.contentText element and a line object with intraline
      // differences to highlight and apply them to the element as
      // annotations.
      annotate(contentEl: HTMLElement, _: HTMLElement, line: GrDiffLine) {
        const HL_CLASS = 'gr-diff intraline';
        for (const highlight of line.highlights) {
          // The start and end indices could be the same if a highlight is
          // meant to start at the end of a line and continue onto the
          // next one. Ignore it.
          if (highlight.startIndex === highlight.endIndex) {
            continue;
          }

          // If endIndex isn't present, continue to the end of the line.
          const endIndex =
            highlight.endIndex === undefined
              ? GrAnnotation.getStringLength(line.text)
              : highlight.endIndex;

          GrAnnotation.annotateElement(
            contentEl,
            highlight.startIndex,
            endIndex - highlight.startIndex,
            HL_CLASS
          );
        }
      },
    };
  }

  // visible for testing
  createTabIndicatorLayer(): DiffLayer {
    const show = () => this.prefs?.show_tabs;
    return {
      annotate(contentEl: HTMLElement, _: HTMLElement, line: GrDiffLine) {
        if (!show()) return;
        annotateSymbols(contentEl, line, '\t', 'tab-indicator');
      },
    };
  }

  private createSpecialCharacterIndicatorLayer(): DiffLayer {
    return {
      annotate(contentEl: HTMLElement, _: HTMLElement, line: GrDiffLine) {
        // Find and annotate the locations of soft hyphen (\u00AD)
        annotateSymbols(contentEl, line, '\u00AD', 'special-char-indicator');
        // Find and annotate Stateful Unicode directional controls
        annotateSymbols(
          contentEl,
          line,
          /[\u202A-\u202E\u2066-\u2069]/,
          'special-char-warning'
        );
      },
    };
  }

  // visible for testing
  createTrailingWhitespaceLayer(): DiffLayer {
    const show = () => this.prefs?.show_whitespace_errors;
    return {
      annotate(contentEl: HTMLElement, _: HTMLElement, line: GrDiffLine) {
        if (!show()) return;
        const match = line.text.match(TRAILING_WHITESPACE_PATTERN);
        if (match) {
          // Normalize string positions in case there is unicode before or
          // within the match.
          const index = GrAnnotation.getStringLength(
            line.text.substr(0, match.index)
          );
          const length = GrAnnotation.getStringLength(match[0]);
          GrAnnotation.annotateElement(
            contentEl,
            index,
            length,
            'gr-diff trailing-whitespace'
          );
        }
      },
    };
  }

  getContentTdByLine(
    lineNumber: LineNumber,
    side?: Side
  ): HTMLTableCellElement | undefined {
    if (!side) return undefined;
    const row = this.findRow(lineNumber, side);
    return row?.getContentCell(side);
  }

  getLineElByNumber(
    lineNumber: LineNumber,
    side?: Side
  ): HTMLTableCellElement | undefined {
    if (!side) return undefined;
    const row = this.findRow(lineNumber, side);
    return row?.getLineNumberCell(side);
  }

  private findRow(lineNumber?: LineNumber, side?: Side): GrDiffRow | undefined {
    if (!side || !lineNumber) return undefined;
    const group = this.findGroup(side, lineNumber);
    if (!group) return undefined;
    const section = this.findSection(group);
    if (!section) return undefined;
    return section.findRow(side, lineNumber);
  }

  private getDiffRows() {
    assertIsDefined(this.diffTable, 'diffTable');
    const sections = [
      ...this.diffTable.querySelectorAll<GrDiffSection>('gr-diff-section'),
    ];
    return sections.map(s => s.getDiffRows()).flat();
  }

  getLineNumberRows(): HTMLTableRowElement[] {
    const rows = this.getDiffRows();
    return rows.map(r => r.getTableRow()).filter(isDefined);
  }

  getLineNumEls(side: Side): HTMLTableCellElement[] {
    const rows = this.getDiffRows();
    return rows.map(r => r.getLineNumberCell(side)).filter(isDefined);
  }

  /** This is used when layers initiate an update. */
  renderContentByRange(start: LineNumber, end: LineNumber, side: Side) {
    const groups = this.getGroupsByLineRange(start, end, side);
    for (const group of groups) {
      const section = this.findSection(group);
      for (const row of section?.getDiffRows() ?? []) {
        row.requestUpdate();
      }
    }
  }

  private findSection(group: GrDiffGroup): GrDiffSection | undefined {
    assertIsDefined(this.diffTable, 'diffTable');
    const leftClass = `left-${group.startLine(Side.LEFT)}`;
    const rightClass = `right-${group.startLine(Side.RIGHT)}`;
    return (
      this.diffTable.querySelector<GrDiffSection>(
        `gr-diff-section.${leftClass}.${rightClass}`
      ) ?? undefined
    );
  }

  renderSectionElement(group: GrDiffGroup) {
    const leftCl = `left-${group.startLine(Side.LEFT)}`;
    const rightCl = `right-${group.startLine(Side.RIGHT)}`;
    return html`
      <gr-diff-section
        class="${leftCl} ${rightCl}"
        .group=${group}
        .diff=${this.diff}
        .layers=${this.layersAll}
        .diffPrefs=${this.prefs}
        .renderPrefs=${this.renderPrefs}
      ></gr-diff-section>
    `;
  }

  renderColumns() {
    const lineNumberWidth = getLineNumberCellWidth(
      this.prefs ?? createDefaultDiffPrefs()
    );
    return html`
      <colgroup>
        <col class=${diffClasses('blame')}></col>
        ${when(
          (this.renderPrefs?.view_mode ?? this.viewMode) ===
            DiffViewMode.UNIFIED,
          () => html` ${this.renderUnifiedColumns(lineNumberWidth)} `,
          () => html`
            ${this.renderSideBySideColumns(Side.LEFT, lineNumberWidth)}
            ${this.renderSideBySideColumns(Side.RIGHT, lineNumberWidth)}
          `
        )}
      </colgroup>
    `;
  }

  private renderUnifiedColumns(lineNumberWidth: number) {
    return html`
      <col class=${diffClasses()} width=${lineNumberWidth}></col>
      <col class=${diffClasses()} width=${lineNumberWidth}></col>
      <col class=${diffClasses()}></col>
    `;
  }

  private renderSideBySideColumns(side: Side, lineNumberWidth: number) {
    return html`
      <col class=${diffClasses(side)} width=${lineNumberWidth}></col>
      <col class=${diffClasses(side, 'sign')}></col>
      <col class=${diffClasses(side)}></col>
    `;
  }

  findGroup(side: Side, line: LineNumber) {
    return this.groups.find(group => group.containsLine(side, line));
  }

  // visible for testing
  getGroupsByLineRange(
    startLine: LineNumber,
    endLine: LineNumber,
    side: Side
  ): GrDiffGroup[] {
    const startIndex = this.groups.findIndex(group =>
      group.containsLine(side, startLine)
    );
    if (startIndex === -1) return [];
    let endIndex = this.groups.findIndex(group =>
      group.containsLine(side, endLine)
    );
    // Not all groups may have been processed yet (i.e. this.groups is still
    // incomplete). In that case let's just return *all* groups until the end
    // of the array.
    if (endIndex === -1) endIndex = this.groups.length - 1;
    // The filter preserves the legacy behavior to only return non-context
    // groups
    return this.groups
      .slice(startIndex, endIndex + 1)
      .filter(group => group.lines.length > 0);
  }

  /**
   * Set the blame information for the diff. For any already-rendered line,
   * re-render its blame cell content.
   */
  setBlame(blame: BlameInfo[]) {
    for (const blameInfo of blame) {
      for (const range of blameInfo.ranges) {
        for (let line = range.start; line <= range.end; line++) {
          const row = this.findRow(line, Side.LEFT);
          if (row) row.blameInfo = blameInfo;
        }
      }
    }
  }
}

export function getLineNumberCellWidth(prefs: DiffPreferencesInfo) {
  return prefs.font_size * 4;
}

function annotateSymbols(
  contentEl: HTMLElement,
  line: GrDiffLine,
  separator: string | RegExp,
  className: string
) {
  const split = line.text.split(separator);
  if (!split || split.length < 2) {
    return;
  }
  for (let i = 0, pos = 0; i < split.length - 1; i++) {
    // Skip forward by the length of the content
    pos += split[i].length;

    GrAnnotation.annotateElement(contentEl, pos, 1, `gr-diff ${className}`);

    pos++;
  }
}

function extractAddedNodes(mutations: MutationRecord[]) {
  return mutations.flatMap(mutation => [...mutation.addedNodes]);
}

function extractRemovedNodes(mutations: MutationRecord[]) {
  return mutations.flatMap(mutation => [...mutation.removedNodes]);
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-diff': GrDiff;
  }
  interface HTMLElementEventMap {
    'comment-thread-mouseenter': CustomEvent<{}>;
    'comment-thread-mouseleave': CustomEvent<{}>;
    'loading-changed': ValueChangedEvent<boolean>;
    'render-required': CustomEvent<{}>;
    /**
     * Fired when the diff begins rendering - both for full renders and for
     * partial rerenders.
     */
    'render-start': CustomEvent<{}>;
    /**
     * Fired when the diff finishes rendering text content - both for full
     * renders and for partial rerenders.
     */
    'render-content': CustomEvent<{}>;
    'diff-context-expanded': CustomEvent<DiffContextExpandedEventDetail>;
    'content-load-needed': CustomEvent<ContentLoadNeededEventDetail>;
  }
}
