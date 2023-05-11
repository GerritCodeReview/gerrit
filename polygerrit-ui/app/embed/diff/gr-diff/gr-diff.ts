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
import '../gr-diff-builder/gr-diff-builder-image';
import '../gr-diff-builder/gr-diff-section';
import './gr-diff-element';
import '../gr-diff-builder/gr-diff-row';
import {
  getLineNumber,
  isThreadEl,
  getResponsiveMode,
  isResponsive,
  isNewDiff,
  getSideByLineEl,
  compareComments,
  getDataFromCommentThreadEl,
  FullContext,
  DiffContextExpandedEventDetail,
  GrDiffCommentThread,
} from '../gr-diff/gr-diff-utils';
import {BlameInfo, CommentRange, ImageInfo} from '../../../types/common';
import {DiffInfo, DiffPreferencesInfo} from '../../../types/diff';
import {GrDiffHighlight} from '../gr-diff-highlight/gr-diff-highlight';
import {CoverageRange, DiffLayer, isDefined} from '../../../types/types';
import {GrRangedCommentLayer} from '../gr-ranged-comment-layer/gr-ranged-comment-layer';
import {DiffViewMode, Side} from '../../../constants/constants';
import {fire, fireAlert} from '../../../utils/event-util';
import {MovedLinkClickedEvent, ValueChangedEvent} from '../../../types/events';
import {getContentEditableRange} from '../../../utils/safari-selection-util';
import {AbortStop} from '../../../api/core';
import {
  RenderPreferences,
  GrDiff as GrDiffApi,
  DisplayLine,
  LineNumber,
  ContentLoadNeededEventDetail,
} from '../../../api/diff';
import {isSafari, toggleClass} from '../../../utils/dom-util';
import {assertIsDefined} from '../../../utils/common-util';
import {GrDiffSelection} from '../gr-diff-selection/gr-diff-selection';
import {property, query, state} from 'lit/decorators.js';
import {sharedStyles} from '../../../styles/shared-styles';
import {html, LitElement, PropertyValues} from 'lit';
import {grSyntaxTheme} from '../gr-syntax-themes/gr-syntax-theme';
import {grRangedCommentTheme} from '../gr-ranged-comment-themes/gr-ranged-comment-theme';
import {iconStyles} from '../../../styles/gr-icon-styles';
import {DiffModel, diffModelToken} from '../gr-diff-model/gr-diff-model';
import {provide} from '../../../models/dependency';
import {grDiffStyles} from './gr-diff-styles';
import {GrCoverageLayer} from '../gr-coverage-layer/gr-coverage-layer';
import {
  GrAnnotationImpl,
  getStringLength,
} from '../gr-diff-highlight/gr-annotation';
import {
  GrDiffGroup,
  GrDiffGroupType,
  hideInContextControl,
} from './gr-diff-group';
import {GrDiffLine} from './gr-diff-line';
import {subscribe} from '../../../elements/lit/subscription-controller';
import {GrDiffSection} from '../gr-diff-builder/gr-diff-section';
import {GrDiffRow} from '../gr-diff-builder/gr-diff-row';
import {GrDiffElement} from './gr-diff-element';

const TRAILING_WHITESPACE_PATTERN = /\s+$/;

const COMMIT_MSG_PATH = '/COMMIT_MSG';
/**
 * 72 is the unofficial length standard for git commit messages.
 * Derived from the fact that git log/show appends 4 ws in the beginning of
 * each line when displaying commit messages. To center the commit message
 * in an 80 char terminal a 4 ws border is added to the rightmost side:
 * 4 + 72 + 4
 */
const COMMIT_MSG_LINE_LENGTH = 72;

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

  /**
   * Deprecated. Use `diffElement` instead.
   *
   * TODO: Migrate to new diff. Remove dependency on this property from external
   * gr-diff users that instantiate TokenHighlightLayer.
   */
  @query('gr-diff-element')
  diffTable?: HTMLElement;

  @query('gr-diff-element')
  diffElement?: GrDiffElement;

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

  // Private but used in tests.
  diffModel = new DiffModel(this);

  /**
   * Just the layers that are passed in from the outside. Will be joined with
   * `layersInternal` and sent to the diff model.
   */
  @property({type: Array})
  layers: DiffLayer[] = [];

  /**
   * Just the internal default layers. See `layers` for the property that can
   * be set from the outside.
   */
  private layersInternal: DiffLayer[] = [];

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
    this.addEventListener('moved-link-clicked', (e: MovedLinkClickedEvent) => {
      this.diffModel.selectLine(e.detail.lineNum, e.detail.side);
    });
    this.addEventListener(
      'diff-context-expanded-internal-new',
      this.onDiffContextExpanded
    );
    this.layerUpdateListener = (
      start: LineNumber,
      end: LineNumber,
      side: Side
    ) => this.requestRowUpdates(start, end, side);
    this.layersInternalInit();
  }

  override connectedCallback() {
    super.connectedCallback();
    if (this.loggedIn) {
      this.addSelectionListeners();
    }
    if (this.diff && this.diffElement) {
      this.diffSelection.init(this.diff, this.diffElement);
    }
    if (this.diffElement) {
      this.highlights.init(this.diffElement, this);
    }
  }

  override disconnectedCallback() {
    this.removeSelectionListeners();
    this.diffSelection.cleanup();
    this.highlights.cleanup();
    super.disconnectedCallback();
  }

  protected override willUpdate(changedProperties: PropertyValues<this>): void {
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
    if (changedProperties.has('baseImage')) {
      this.diffModel.updateState({baseImage: this.baseImage});
    }
    if (changedProperties.has('revisionImage')) {
      this.diffModel.updateState({revisionImage: this.revisionImage});
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

  protected override async getUpdateComplete(): Promise<boolean> {
    const result = await super.getUpdateComplete();
    await this.diffElement?.updateComplete;
    return result;
  }

  protected override updated(changedProperties: PropertyValues<this>) {
    if (changedProperties.has('diff')) {
      // diffChanged relies on diffElement having been rendered.
      this.diffChanged();
    }
    if (changedProperties.has('groups')) {
      if (this.groups?.length > 0) {
        this.loading = false;
        this.observeNodes();
      }
    }
  }

  override render() {
    fire(this, 'render-start', {});
    return html`<gr-diff-element></gr-diff-element>`;
  }

  private addSelectionListeners() {
    document.addEventListener('selectionchange', this.handleSelectionChange);
    document.addEventListener('mouseup', this.handleMouseUp);
  }

  private removeSelectionListeners() {
    document.removeEventListener('selectionchange', this.handleSelectionChange);
    document.removeEventListener('mouseup', this.handleMouseUp);
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

  private commentThreadRedispatcher = (
    target: EventTarget | null,
    eventName: 'comment-thread-mouseenter' | 'comment-thread-mouseleave'
  ) => {
    if (!isThreadEl(target)) return;
    const data = getDataFromCommentThreadEl(target);
    if (data) fire(target, eventName, data);
  };

  private commentThreadEnterRedispatcher = (e: Event) => {
    this.commentThreadRedispatcher(e.target, 'comment-thread-mouseenter');
  };

  private commentThreadLeaveRedispatcher = (e: Event) => {
    this.commentThreadRedispatcher(e.target, 'comment-thread-mouseleave');
  };

  /** TODO: Can be removed when diff-old is gone. */
  cancel() {}

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
  selectLine(el: Element) {
    const lineNumber = Number(el.getAttribute('data-value'));
    const side = el.classList.contains('left') ? Side.LEFT : Side.RIGHT;
    this.diffModel.selectLine(lineNumber, side);
  }

  addDraftAtLine(lineNum: LineNumber, side: Side) {
    this.diffModel.createCommentOnLine(lineNum, side);
  }

  createRangeComment() {
    const selectedRange = this.highlights.selectedRange;
    assertIsDefined(selectedRange, 'no range selected');
    const {side, range} = selectedRange;
    this.diffModel.createCommentOnRange(range, side);
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
      assertIsDefined(this.diffElement, 'diffElement');
      this.diffSelection.init(this.diff, this.diffElement);
      this.highlights.init(this.diffElement, this);
    }
  }

  /**
   * This must be called once, but only after diff lines are rendered. Otherwise
   * `processNodes()` will fail to lookup the HTML elements that it wants to
   * manipulate.
   *
   * TODO: Validate whether the above comment is still true. We don't look up
   * elements anymore, and processing the nodes earlier might be beneficial
   * performance wise.
   */
  private observeNodes() {
    if (this.nodeObserver) return;
    // Watches children being added to gr-diff. We are expecting only comment
    // widgets to be direct children.
    this.nodeObserver = new MutationObserver(() => this.processNodes());
    this.nodeObserver.observe(this, {childList: true});
    // Process existing comment widgets before the first observed change.
    this.processNodes();
  }

  private processNodes() {
    const threadEls = [...this.childNodes].filter(isThreadEl);
    const comments = threadEls
      .map(getDataFromCommentThreadEl)
      .filter(isDefined)
      .sort(compareComments);
    this.diffModel.updateState({comments});
    this.rangeLayer.updateRanges(comments);
    for (const el of threadEls) {
      el.addEventListener('mouseenter', this.commentThreadEnterRedispatcher);
      el.addEventListener('mouseleave', this.commentThreadLeaveRedispatcher);
    }
  }

  /** TODO: Can be removed when diff-old is gone. */
  clearDiffContent() {}

  // TODO: Migrate callers to just update prefs.context.
  toggleAllContext() {
    const current = this.diffModel.getState().showFullContext;
    this.diffModel.updateState({
      showFullContext:
        current === FullContext.YES ? FullContext.NO : FullContext.YES,
    });
  }

  private updateCoverageRanges(rs: CoverageRange[]) {
    this.coverageLayerLeft.setRanges(rs.filter(r => r?.side === Side.LEFT));
    this.coverageLayerRight.setRanges(rs.filter(r => r?.side === Side.RIGHT));
  }

  private onDiffContextExpanded = (
    e: CustomEvent<DiffContextExpandedEventDetail>
  ) => {
    // Don't stop propagation. The host may listen for reporting or
    // resizing.
    this.diffModel.replaceGroup(e.detail.contextGroup, e.detail.groups);
  };

  private layersChanged() {
    const layers = [...this.layersInternal, ...this.layers];
    for (const layer of layers) {
      layer.removeListener?.(this.layerUpdateListener);
      layer.addListener?.(this.layerUpdateListener);
    }
    this.diffModel.updateState({layers});
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
              ? getStringLength(line.text)
              : highlight.endIndex;

          GrAnnotationImpl.annotateElement(
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
          const index = getStringLength(line.text.substr(0, match.index));
          const length = getStringLength(match[0]);
          GrAnnotationImpl.annotateElement(
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
    const row = this.findRow(side, lineNumber);
    return row?.getContentCell(side);
  }

  getLineElByNumber(
    lineNumber: LineNumber,
    side?: Side
  ): HTMLTableCellElement | undefined {
    if (!side) return undefined;
    const row = this.findRow(side, lineNumber);
    return row?.getLineNumberCell(side);
  }

  private findRow(side: Side, lineNumber: LineNumber): GrDiffRow | undefined {
    const group = this.findGroup(side, lineNumber);
    if (!group) return undefined;
    const section = this.findSection(group);
    if (!section) return undefined;
    return section.findRow(side, lineNumber);
  }

  private getDiffRows() {
    assertIsDefined(this.diffElement, 'diffElement');
    const sections = [...(this.diffElement.diffSections ?? [])];
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
  private requestRowUpdates(start: LineNumber, end: LineNumber, side: Side) {
    const groups = this.getGroupsByLineRange(start, end, side);
    for (const group of groups) {
      const section = this.findSection(group);
      for (const row of section?.getDiffRows() ?? []) {
        row.requestUpdate();
      }
    }
  }

  private findSection(group: GrDiffGroup): GrDiffSection | undefined {
    assertIsDefined(this.diffElement, 'diffElement');
    const leftClass = `left-${group.startLine(Side.LEFT)}`;
    const rightClass = `right-${group.startLine(Side.RIGHT)}`;
    return (
      this.diffElement.querySelector<GrDiffSection>(
        `gr-diff-section.${leftClass}.${rightClass}`
      ) ?? undefined
    );
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
          const row = this.findRow(Side.LEFT, line);
          if (row) row.blameInfo = blameInfo;
        }
      }
    }
  }
}

function getLineNumberCellWidth(prefs: DiffPreferencesInfo) {
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

    GrAnnotationImpl.annotateElement(contentEl, pos, 1, `gr-diff ${className}`);

    pos++;
  }
}

// TODO(newdiff-cleanup): Remove once newdiff migration is completed.
if (isNewDiff()) {
  customElements.define('gr-diff', GrDiff);
}

declare global {
  interface HTMLElementTagNameMap {
    // TODO(newdiff-cleanup): Replace once newdiff migration is completed.
    'gr-diff': LitElement;
  }
  interface HTMLElementEventMap {
    'comment-thread-mouseenter': CustomEvent<GrDiffCommentThread>;
    'comment-thread-mouseleave': CustomEvent<GrDiffCommentThread>;
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
    'diff-context-expanded-internal-new': CustomEvent<DiffContextExpandedEventDetail>;
    'content-load-needed': CustomEvent<ContentLoadNeededEventDetail>;
  }
}
