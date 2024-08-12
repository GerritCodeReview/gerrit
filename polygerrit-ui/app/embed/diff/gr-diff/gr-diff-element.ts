/**
 * @license
 * Copyright 2023 Google LLC
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
import '../gr-diff-builder/gr-diff-row';
import {isResponsive, FullContext, FULL_CONTEXT} from './gr-diff-utils';
import {ImageInfo} from '../../../types/common';
import {DiffInfo, DiffPreferencesInfo} from '../../../types/diff';
import {
  DiffViewMode,
  Side,
  createDefaultDiffPrefs,
} from '../../../constants/constants';
import {fire} from '../../../utils/event-util';
import {RenderPreferences, LOST, DiffResponsiveMode} from '../../../api/diff';
import {query, queryAll, state} from 'lit/decorators.js';
import {html, LitElement, nothing} from 'lit';
import {when} from 'lit/directives/when.js';
import {classMap} from 'lit/directives/class-map.js';
import {expandFileMode} from '../../../utils/file-util';
import {
  ColumnsToShow,
  NO_COLUMNS,
  diffModelToken,
} from '../gr-diff-model/gr-diff-model';
import {resolve} from '../../../models/dependency';
import {getDiffLength, isImageDiff} from '../../../utils/diff-util';
import {GrDiffGroup} from './gr-diff-group';
import {subscribe} from '../../../elements/lit/subscription-controller';
import {GrDiffSection} from '../gr-diff-builder/gr-diff-section';
import {repeat} from 'lit/directives/repeat.js';
import {
  Shortcut,
  shortcutsServiceToken,
} from '../../../services/shortcuts/shortcuts-service';

const LARGE_DIFF_THRESHOLD_LINES = 10000;

export class GrDiffElement extends LitElement {
  @query('#diffTable')
  diffTable?: HTMLTableElement;

  @queryAll('gr-diff-section')
  diffSections?: NodeListOf<GrDiffSection>;

  @state() diff?: DiffInfo;

  @state() baseImage?: ImageInfo;

  @state() revisionImage?: ImageInfo;

  @state() diffPrefs: DiffPreferencesInfo = createDefaultDiffPrefs();

  @state() renderPrefs: RenderPreferences = {};

  @state() viewMode = DiffViewMode.SIDE_BY_SIDE;

  @state() groups: GrDiffGroup[] = [];

  @state() showFullContext: FullContext = FullContext.UNDECIDED;

  @state() errorMessage?: string;

  @state() responsiveMode: DiffResponsiveMode = 'NONE';

  @state() loading = true;

  @state() columns: ColumnsToShow = NO_COLUMNS;

  @state() columnCount = 0;

  private getDiffModel = resolve(this, diffModelToken);

  private readonly getShortcutsService = resolve(this, shortcutsServiceToken);

  /**
   * The browser API for handling selection does not (yet) work for selection
   * across multiple shadow DOM elements. So we are rendering gr-diff components
   * into the light DOM instead of the shadow DOM by overriding this method,
   * which was the recommended workaround by the lit team.
   * See also https://github.com/WICG/webcomponents/issues/79.
   */
  override createRenderRoot() {
    return this;
  }

  constructor() {
    super();
    subscribe(
      this,
      () => this.getDiffModel().diff$,
      diff => (this.diff = diff)
    );
    subscribe(
      this,
      () => this.getDiffModel().baseImage$,
      baseImage => (this.baseImage = baseImage)
    );
    subscribe(
      this,
      () => this.getDiffModel().revisionImage$,
      revisionImage => (this.revisionImage = revisionImage)
    );
    subscribe(
      this,
      () => this.getDiffModel().diffPrefs$,
      diffPrefs => (this.diffPrefs = diffPrefs)
    );
    subscribe(
      this,
      () => this.getDiffModel().renderPrefs$,
      renderPrefs => (this.renderPrefs = renderPrefs)
    );
    subscribe(
      this,
      () => this.getDiffModel().columnsToShow$,
      columnsToShow => (this.columns = columnsToShow)
    );
    subscribe(
      this,
      () => this.getDiffModel().columnCount$,
      columnCount => (this.columnCount = columnCount)
    );
    subscribe(
      this,
      () => this.getDiffModel().viewMode$,
      viewMode => (this.viewMode = viewMode)
    );
    subscribe(
      this,
      () => this.getDiffModel().showFullContext$,
      showFullContext => (this.showFullContext = showFullContext)
    );
    subscribe(
      this,
      () => this.getDiffModel().errorMessage$,
      errorMessage => (this.errorMessage = errorMessage)
    );
    subscribe(
      this,
      () => this.getDiffModel().groups$,
      groups => (this.groups = groups)
    );
    subscribe(
      this,
      () => this.getDiffModel().loading$,
      loading => (this.loading = loading)
    );
    subscribe(
      this,
      () => this.getDiffModel().responsiveMode$,
      responsiveMode => (this.responsiveMode = responsiveMode)
    );
  }

  protected override async getUpdateComplete(): Promise<boolean> {
    const result = await super.getUpdateComplete();
    const sections = [...(this.diffSections ?? [])];
    await Promise.all(sections.map(section => section.updateComplete));
    return result;
  }

  protected override updated() {
    if (this.diffSections?.length) {
      this.fireRenderContent();
    }
  }

  private async fireRenderContent() {
    await this.updateComplete;
    // TODO: Retire one of these two events.
    fire(this, 'render-content', {});
    fire(this, 'render', {});
  }

  override render() {
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
      canComment: !!this.renderPrefs.can_comment,
    };
    const tableClasses = {
      responsive: isResponsive(this.responsiveMode),
    };
    const isBinary = !!this.diff?.binary;
    const isImage = isImageDiff(this.diff);
    return html`
      <div class=${classMap(cssClasses)}>
        <table
          id="diffTable"
          class=${classMap(tableClasses)}
          ?contenteditable=${this.isContentEditable}
        >
          ${this.renderColumns()}
          ${when(!this.showWarning(), () =>
            repeat(
              this.groups,
              group => group.id(),
              group => this.renderSectionElement(group)
            )
          )}
          ${when(isImage, () => this.renderImageDiff())}
          ${when(!isImage && isBinary, () => this.renderBinaryDiff())}
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
          large (about ${getDiffLength(this.diff)} lines).
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

  // Private but used in tests.
  showNoChangeMessage() {
    return (
      !this.loading &&
      this.diff &&
      !this.diff.binary &&
      this.diffPrefs.ignore_whitespace !== 'IGNORE_NONE' &&
      getDiffLength(this.diff) === 0
    );
  }

  private showWarning() {
    return (
      this.diffPrefs?.context === FULL_CONTEXT &&
      this.showFullContext === FullContext.UNDECIDED &&
      getDiffLength(this.diff) >= LARGE_DIFF_THRESHOLD_LINES
    );
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
    this.getDiffModel().updateState({showFullContext: FullContext.YES});
  }

  private collapseContext() {
    this.getDiffModel().updateState({showFullContext: FullContext.NO});
  }

  private computeNewlineWarning(): string | undefined {
    const messages = [];
    if (this.renderPrefs.show_newline_warning_left) {
      messages.push('No newline at end of left file.');
    }
    if (this.renderPrefs.show_newline_warning_right) {
      messages.push('No newline at end of right file.');
    }
    if (!messages.length) {
      return undefined;
    }
    return messages.join(' \u2014 '); // \u2014 - '—'
  }

  private renderImageDiff() {
    return when(
      this.renderPrefs.use_new_image_diff_ui,
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
        .columnCount=${this.columnCount}
      ></gr-diff-image-new>
    `;
  }

  private renderImageDiffOld() {
    return html`
      <gr-diff-image-old
        .baseImage=${this.baseImage ?? undefined}
        .revisionImage=${this.revisionImage ?? undefined}
        .columnCount=${this.columnCount}
      ></gr-diff-image-old>
    `;
  }

  public renderBinaryDiff() {
    return html`
      <tbody class="binary-diff">
        <tr>
          <td colspan=${this.columnCount}>
            <span
              >Difference in binary files. Download commit to view (shortcut:
              ${this.getShortcutsService().getShortcut(
                Shortcut.OPEN_DOWNLOAD_DIALOG
              )})</span
            >
          </td>
        </tr>
      </tbody>
    `;
  }

  renderSectionElement(group: GrDiffGroup) {
    const leftClass = `left-${group.startLine(Side.LEFT)}`;
    const rightClass = `right-${group.startLine(Side.RIGHT)}`;
    if (this.diff?.binary && group.startLine(Side.LEFT) === LOST) {
      return nothing;
    }
    return html`
      <gr-diff-section
        class="${leftClass} ${rightClass}"
        .group=${group}
      ></gr-diff-section>
    `;
  }

  renderColumns() {
    const lineNumberWidth = getLineNumberCellWidth(
      this.diffPrefs ?? createDefaultDiffPrefs()
    );
    return html`
      <colgroup>
        ${when(this.columns.blame, () => html`<col class="blame" />`)}
        ${when(
          this.columns.leftNumber,
          () => html`<col class="left" width=${lineNumberWidth} />`
        )}
        ${when(this.columns.leftSign, () => html`<col class="left sign" />`)}
        ${when(this.columns.leftContent, () => html`<col class="left" />`)}
        ${when(
          this.columns.rightNumber,
          () => html`<col class="right" width=${lineNumberWidth} />`
        )}
        ${when(this.columns.rightSign, () => html`<col class="right sign" />`)}
        ${when(this.columns.rightContent, () => html`<col class="right" />`)}
      </colgroup>
    `;
  }
}

function getLineNumberCellWidth(prefs: DiffPreferencesInfo) {
  return prefs.font_size * 4;
}

customElements.define('gr-diff-element', GrDiffElement);

declare global {
  interface HTMLElementTagNameMap {
    'gr-diff-element': GrDiffElement;
  }
}
