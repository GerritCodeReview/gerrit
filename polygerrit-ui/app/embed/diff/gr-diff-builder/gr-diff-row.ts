/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {html, LitElement, nothing, PropertyValues} from 'lit';
import {property, state} from 'lit/decorators.js';
import {ifDefined} from 'lit/directives/if-defined.js';
import {createRef, Ref, ref} from 'lit/directives/ref.js';
import {
  DiffLayer,
  DiffResponsiveMode,
  FILE,
  GrDiffLineType,
  LineNumber,
  LOST,
  Side,
} from '../../../api/diff';
import {BlameInfo} from '../../../types/common';
import {assertIsDefined} from '../../../utils/common-util';
import {fire} from '../../../utils/event-util';
import {getBaseUrl} from '../../../utils/url-util';
import {otherSide} from '../../../utils/diff-util';
import './gr-diff-text';
import {
  findBlame,
  GrDiffCommentThread,
  isLongCommentRange,
  isResponsive,
} from '../gr-diff/gr-diff-utils';
import {resolve} from '../../../models/dependency';
import {
  ColumnsToShow,
  diffModelToken,
  NO_COLUMNS,
} from '../gr-diff-model/gr-diff-model';
import {when} from 'lit/directives/when.js';
import {isDefined} from '../../../types/types';
import {BehaviorSubject, combineLatest} from 'rxjs';
import '../../../elements/shared/gr-hovercard/gr-hovercard';
import {GrDiffLine} from '../gr-diff/gr-diff-line';
import {distinctUntilChanged, map} from 'rxjs/operators';
import {deepEqual} from '../../../utils/deep-util';
import {subscribe} from '../../../elements/lit/subscription-controller';

export class GrDiffRow extends LitElement {
  contentLeftRef: Ref<LitElement> = createRef();

  contentRightRef: Ref<LitElement> = createRef();

  contentCellLeftRef: Ref<HTMLTableCellElement> = createRef();

  contentCellRightRef: Ref<HTMLTableCellElement> = createRef();

  lineNumberLeftRef: Ref<HTMLTableCellElement> = createRef();

  lineNumberRightRef: Ref<HTMLTableCellElement> = createRef();

  blameCellRef: Ref<HTMLTableCellElement> = createRef();

  tableRowRef: Ref<HTMLTableRowElement> = createRef();

  @property({type: Object})
  left?: GrDiffLine;

  private left$ = new BehaviorSubject<GrDiffLine | undefined>(undefined);

  @property({type: Object})
  right?: GrDiffLine;

  private right$ = new BehaviorSubject<GrDiffLine | undefined>(undefined);

  @property({type: Object})
  responsiveMode?: DiffResponsiveMode;

  @property({type: Boolean})
  unifiedDiff = false;

  @property({type: Number})
  tabSize = 2;

  @property({type: Number})
  lineLength = 80;

  @property({type: Boolean})
  hideFileCommentButton = false;

  @property({type: Object})
  layers: DiffLayer[] = [];

  /**
   * Semantic DOM diff testing does not work with just table fragments, so when
   * running such tests the render() method has to wrap the DOM in a proper
   * <table> element.
   */
  @state() addTableWrapperForTesting = false;

  @state() leftComments: GrDiffCommentThread[] = [];

  @state() rightComments: GrDiffCommentThread[] = [];

  @state() columns: ColumnsToShow = NO_COLUMNS;

  @state() blameInfo?: BlameInfo;

  /**
   * Keeps track of whether diff layers have already been applied to the diff
   * row. That happens after the DOM has been created in the `updated()`
   * lifecycle callback.
   *
   * Once layers are applied, the diff row requires two rendering passes for an
   * update: 1. Remove all <gr-diff-text> elements and their layer manipulated
   * DOMs. 2. Add fresh <gr-diff-text> elements and let layers re-apply in
   * `updated()`.
   */
  private layersApplied = false;

  private readonly getDiffModel = resolve(this, diffModelToken);

  constructor() {
    super();
    subscribe(
      this,
      () =>
        combineLatest([this.left$, this.getDiffModel().comments$]).pipe(
          map(([left, comments]) =>
            comments.filter(
              c =>
                c.line === left?.lineNumber(Side.LEFT) && c.side === Side.LEFT
            )
          ),
          distinctUntilChanged(deepEqual)
        ),
      leftComments => (this.leftComments = leftComments)
    );
    subscribe(
      this,
      () =>
        combineLatest([this.right$, this.getDiffModel().comments$]).pipe(
          map(([right, comments]) =>
            comments.filter(
              c =>
                c.line === right?.lineNumber(Side.RIGHT) &&
                c.side === Side.RIGHT
            )
          ),
          distinctUntilChanged(deepEqual)
        ),
      rightComments => (this.rightComments = rightComments)
    );
    subscribe(
      this,
      () => this.getDiffModel().columnsToShow$,
      columnsToShow => (this.columns = columnsToShow)
    );
    subscribe(
      this,
      () => this.getDiffModel().blameInfo$,
      blameInfos => {
        const line = this.left?.lineNumber(Side.LEFT);
        this.blameInfo = findBlame(blameInfos, line);
      }
    );
  }

  override willUpdate(changedProperties: PropertyValues) {
    if (changedProperties.has('left')) this.left$.next(this.left);
    if (changedProperties.has('right')) this.right$.next(this.right);
  }

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

  override updated() {
    if (this.layersApplied) {
      // <gr-diff-text> elements have been removed during rendering. Let's start
      // another rendering cycle with freshly created <gr-diff-text> elements.
      this.updateComplete.then(() => {
        this.layersApplied = false;
        this.requestUpdate();
      });
    } else {
      this.updateLayers(Side.LEFT);
      this.updateLayers(Side.RIGHT);
    }
  }

  /**
   * The diff layers API is designed to let layers manipulate the DOM. So we
   * have to apply them after the rendering cycle is done (`updated()`). But
   * when re-rendering a row that already has layers applied, then we have to
   * first wipe away <gr-diff-text>. This is achieved by
   * `this.layersApplied = true`.
   */
  private async updateLayers(side: Side) {
    const line = this.line(side);
    const contentEl = this.contentRef(side).value;
    const lineNumberEl = this.lineNumberRef(side).value;
    if (!line || !contentEl || !lineNumberEl) return;

    // We have to wait for the <gr-diff-text> child component to finish
    // rendering before we can apply layers, which will re-write the HTML.
    await contentEl?.updateComplete;
    for (const layer of this.layers) {
      if (typeof layer.annotate === 'function') {
        layer.annotate(contentEl, lineNumberEl, line, side);
      }
    }
    // At this point we consider layers applied. So as soon as <gr-diff-row>
    // enters a new rendering cycle <gr-diff-text> elements will be removed.
    this.layersApplied = true;
  }

  override render() {
    if (!this.left || !this.right) return;
    const classes = this.unifiedDiff ? ['unified'] : ['side-by-side'];
    const unifiedType = this.unifiedType();
    if (this.unifiedDiff && unifiedType) classes.push(unifiedType);
    const row = html`
      <tr
        ${ref(this.tableRowRef)}
        class=${['diff-row', ...classes].join(' ')}
        left-type=${ifDefined(this.getType(Side.LEFT))}
        right-type=${ifDefined(this.getType(Side.RIGHT))}
        tabindex="-1"
        aria-labelledby=${this.ariaLabelIds()}
      >
        ${this.renderBlameCell()} ${this.renderLineNumberCell(Side.LEFT)}
        ${this.renderSignCell(Side.LEFT)} ${this.renderContentCell(Side.LEFT)}
        ${this.renderLineNumberCell(Side.RIGHT)}
        ${this.renderSignCell(Side.RIGHT)} ${this.renderContentCell(Side.RIGHT)}
      </tr>
      ${this.renderPostLineSlot(Side.LEFT)}
      ${this.renderPostLineSlot(Side.RIGHT)}
    `;
    if (this.addTableWrapperForTesting) {
      return html`<table>
        ${row}
      </table>`;
    }
    return row;
  }

  private ariaLabelIds() {
    const ids: string[] = [];
    ids.push(this.lineNumberId(Side.LEFT));
    if (!this.unifiedDiff) ids.push(this.contentId(Side.LEFT));
    ids.push(this.lineNumberId(Side.RIGHT));
    if (!this.unifiedDiff) ids.push(this.contentId(Side.RIGHT));
    if (this.unifiedDiff) ids.push(this.contentId(this.unifiedSide()));
    return ids.filter(id => !!id).join(' ');
  }

  private lineNumberId(side: Side): string {
    const lineNumber = this.lineNumber(side);
    if (!lineNumber) return '';
    return `${side}-button-${lineNumber}`;
  }

  private unifiedSide() {
    const isLeft = this.line(Side.RIGHT)?.type === GrDiffLineType.BLANK;
    return isLeft ? Side.LEFT : Side.RIGHT;
  }

  private contentId(side: Side): string {
    const lineNumber = this.lineNumber(side);
    if (!lineNumber) return '';
    return `${side}-content-${lineNumber}`;
  }

  getTableRow(): HTMLTableRowElement | undefined {
    return this.tableRowRef.value;
  }

  getLineNumberCell(side: Side): HTMLTableCellElement | undefined {
    return this.lineNumberRef(side).value;
  }

  getContentCell(side: Side) {
    return this.contentCellRef(side)?.value;
  }

  getBlameCell() {
    return this.blameCellRef.value;
  }

  private renderBlameCell() {
    if (!this.columns.blame) return nothing;
    // td.blame has `white-space: pre`, so prettier must not add spaces.
    // prettier-ignore
    return html`
      <td
        ${ref(this.blameCellRef)}
        class="blame"
        data-line-number=${this.left?.beforeNumber ?? 0}
      >${this.renderBlameElement()}</td>
    `;
  }

  private renderBlameElement() {
    const lineNum = this.left?.beforeNumber;
    const commit = this.blameInfo;
    if (!lineNum || !commit) return;

    const isStartOfRange = commit.ranges.some(r => r.start === lineNum);
    const extras: string[] = [];
    if (isStartOfRange) extras.push('startOfRange');
    const date = new Date(commit.time * 1000).toLocaleDateString();
    const shortName = commit.author.split(' ')[0];
    const url = `${getBaseUrl()}/q/${commit.id}`;

    // td.blame has `white-space: pre`, so prettier must not add spaces.
    // prettier-ignore
    return html`<span class=${extras.join(' ')}
        ><a href=${url} class="blameDate">${date}</a
        ><span class="blameAuthor"> ${shortName}</span
        ><gr-hovercard>
          <span class="blameHoverCard">
            Commit ${commit.id}<br />
            Author: ${commit.author}<br />
            Date: ${date}<br />
            <br />
            ${commit.commit_msg}
          </span>
        </gr-hovercard
      ></span>`;
  }

  private renderLineNumberCell(side: Side) {
    if (!this.columns.leftNumber && side === Side.LEFT) return nothing;
    if (!this.columns.rightNumber && side === Side.RIGHT) return nothing;
    const line = this.line(side);
    const lineNumber = this.lineNumber(side);
    const isBlank = line?.type === GrDiffLineType.BLANK;
    if (!line || !lineNumber || isBlank || this.layersApplied) {
      const blankClass = isBlank ? 'blankLineNum' : '';
      return html`<td
        ${ref(this.lineNumberRef(side))}
        class=${[side, blankClass].join(' ')}
      ></td>`;
    }

    return html`<td
      ${ref(this.lineNumberRef(side))}
      class=${[side, 'lineNum'].join(' ')}
      data-value=${lineNumber}
    >
      ${this.renderLineNumberButton(line, lineNumber, side)}
    </td>`;
  }

  private renderLineNumberButton(
    line: GrDiffLine,
    lineNumber: LineNumber,
    side: Side
  ) {
    if (this.hideFileCommentButton && lineNumber === FILE) return;
    if (lineNumber === LOST) return;
    // .lineNumButton has `white-space: pre`, so prettier must not add spaces.
    // prettier-ignore
    return html`
      <button
        id=${this.lineNumberId(side)}
        class=${['lineNumButton', side].join(' ')}
        tabindex="-1"
        data-value=${lineNumber}
        aria-label=${ifDefined(
          this.computeLineNumberAriaLabel(line, lineNumber)
    )}
        @click=${() => this.getDiffModel().createCommentOnLine(lineNumber, side)}
        @mouseenter=${() =>
          fire(this, 'line-mouse-enter', {lineNum: lineNumber, side})}
        @mouseleave=${() =>
          fire(this, 'line-mouse-leave', {lineNum: lineNumber, side})}
      >${lineNumber === FILE ? 'FILE' : lineNumber.toString()}</button>
    `;
  }

  private computeLineNumberAriaLabel(line: GrDiffLine, lineNumber: LineNumber) {
    if (lineNumber === FILE) return 'Add file comment';

    // Add aria-labels for valid line numbers.
    // For unified diff, this method will be called with number set to 0 for
    // the empty line number column for added/removed lines. This should not
    // be announced to the screenreader.
    if (
      lineNumber === LOST ||
      (typeof lineNumber === 'number' && lineNumber <= 0)
    )
      return undefined;

    switch (line.type) {
      case GrDiffLineType.REMOVE:
        return `${lineNumber} removed`;
      case GrDiffLineType.ADD:
        return `${lineNumber} added`;
      case GrDiffLineType.BOTH:
      case GrDiffLineType.BLANK:
        return `${lineNumber} unmodified`;
    }
  }

  private renderContentCell(side: Side) {
    if (!this.columns.leftContent && side === Side.LEFT) return nothing;
    if (!this.columns.rightContent && side === Side.RIGHT) return nothing;

    let line = this.line(side);
    if (this.unifiedDiff) {
      if (line?.type === GrDiffLineType.BLANK) {
        side = Side.LEFT;
        line = this.line(Side.LEFT);
      }
    }
    const lineNumber = this.lineNumber(side);
    assertIsDefined(line, 'line');
    const extras: string[] = [line.type, side];
    if (line.type !== GrDiffLineType.BLANK) extras.push('content');
    if (!line.hasIntralineInfo) extras.push('no-intraline-info');
    if (line.beforeNumber === FILE) extras.push('file');
    if (line.beforeNumber === LOST) extras.push('lost');

    // .content has `white-space: pre`, so prettier must not add spaces.
    // prettier-ignore
    return html`
      <td
        ${ref(this.contentCellRef(side))}
        class=${extras.join(' ')}
        @click=${() => {
          if (lineNumber) {
            this.getDiffModel().selectLine(lineNumber, side);
          }
        }}
        @mouseenter=${() => {
          if (lineNumber)
            fire(this, 'line-mouse-enter', {lineNum: lineNumber, side});
        }}
        @mouseleave=${() => {
          if (lineNumber)
            fire(this, 'line-mouse-leave', {lineNum: lineNumber, side});
        }}
      >${this.renderText(side)}${this.renderLostMessage(side)}${this.renderThreadGroup(side)}</td>
    `;
  }

  private renderSignCell(side: Side) {
    if (!this.columns.leftSign && side === Side.LEFT) return nothing;
    if (!this.columns.rightSign && side === Side.RIGHT) return nothing;

    const line = this.line(side);
    assertIsDefined(line, 'line');
    const isBlank = line.type === GrDiffLineType.BLANK;
    const isAdd = line.type === GrDiffLineType.ADD && side === Side.RIGHT;
    const isRemove = line.type === GrDiffLineType.REMOVE && side === Side.LEFT;
    const extras: string[] = ['sign', side];
    if (isBlank) extras.push('blank');
    if (isAdd) extras.push('add');
    if (isRemove) extras.push('remove');
    if (!line.hasIntralineInfo) extras.push('no-intraline-info');

    const sign = isAdd ? '+' : isRemove ? '-' : '';
    return html`<td class=${extras.join(' ')}>${sign}</td>`;
  }

  private renderLostMessage(side: Side) {
    if (this.lineNumber(side) !== LOST) return nothing;
    if (this.getComments(side).length === 0) return nothing;
    // .content has `white-space: pre`, so prettier must not add spaces.
    // prettier-ignore
    return html`<div class="lost-message"
      ><gr-icon icon="info"></gr-icon
      ><span>Original comment position not found in this patchset</span
    ></div>`;
  }

  private renderThreadGroup(side: Side) {
    if (!this.lineNumber(side)) return nothing;

    if (
      this.getComments(side).length === 0 &&
      (!this.unifiedDiff || this.getComments(otherSide(side)).length === 0)
    ) {
      return nothing;
    }
    return html`<div class="thread-group" data-side=${side}>
      ${this.renderSlot(side)}
      ${when(this.unifiedDiff, () => this.renderSlot(otherSide(side)))}
    </div>`;
  }

  private renderSlot(side: Side) {
    const line = this.lineNumber(side);
    if (!line) return nothing;
    if (this.getComments(side).length === 0) return nothing;
    return html`
      ${this.renderRangedCommentHints(side)}
      <slot name="${side}-${line}"></slot>
    `;
  }

  private renderRangedCommentHints(side: Side) {
    const ranges = this.getComments(side)
      .map(c => c.range)
      .filter(isDefined)
      .filter(isLongCommentRange);
    return ranges.map(
      range =>
        html`
          <gr-ranged-comment-hint .range=${range}></gr-ranged-comment-hint>
        `
    );
  }

  private contentRef(side: Side) {
    return side === Side.LEFT ? this.contentLeftRef : this.contentRightRef;
  }

  private contentCellRef(side: Side) {
    return side === Side.LEFT
      ? this.contentCellLeftRef
      : this.contentCellRightRef;
  }

  private lineNumberRef(side: Side) {
    return side === Side.LEFT
      ? this.lineNumberLeftRef
      : this.lineNumberRightRef;
  }

  lineNumber(side: Side) {
    return this.line(side)?.lineNumber(side);
  }

  line(side: Side) {
    return side === Side.LEFT ? this.left : this.right;
  }

  private getComments(side: Side) {
    return side === Side.LEFT ? this.leftComments : this.rightComments;
  }

  private getType(side?: Side): string | undefined {
    if (this.unifiedDiff) return undefined;
    if (side === Side.LEFT) return this.left?.type;
    if (side === Side.RIGHT) return this.right?.type;
    return undefined;
  }

  private unifiedType() {
    return this.left?.type === GrDiffLineType.BLANK
      ? this.right?.type
      : this.left?.type;
  }

  /**
   * Returns a 'div' element containing the supplied |text| as its innerText,
   * with '\t' characters expanded to a width determined by |tabSize|, and the
   * text wrapped at column |lineLimit|, which may be Infinity if no wrapping is
   * desired.
   */
  private renderText(side: Side) {
    const line = this.line(side);
    const lineNumber = this.lineNumber(side);
    if (typeof lineNumber !== 'number') return;

    // Note that `this.layersApplied` will wipe away the <gr-diff-text>, and
    // another rendering cycle will be initiated in `updated()`.
    // prettier-ignore
    const textElement = !this.layersApplied
      ? html`<gr-diff-text
          ${ref(this.contentRef(side))}
          data-side=${ifDefined(side)}
          .text=${line?.text ?? ''}
          .tabSize=${this.tabSize}
          .lineLimit=${this.lineLength}
          .isResponsive=${isResponsive(this.responsiveMode)}
        ></gr-diff-text>` : '';
    // .content has `white-space: pre`, so prettier must not add spaces.
    // prettier-ignore
    return html`<div
        class="contentText"
        data-side=${ifDefined(side)}
        id=${this.contentId(side)}
      >${textElement}</div>`;
  }

  private renderPostLineSlot(side: Side) {
    const lineNumber = this.lineNumber(side);
    return lineNumber && Number.isInteger(lineNumber)
      ? html`<slot name="post-${side}-line-${lineNumber}"></slot>`
      : nothing;
  }
}

customElements.define('gr-diff-row', GrDiffRow);

declare global {
  interface HTMLElementTagNameMap {
    'gr-diff-row': GrDiffRow;
  }
}
