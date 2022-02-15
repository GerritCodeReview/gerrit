/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {html, LitElement, TemplateResult} from 'lit';
import {customElement, property, state} from 'lit/decorators';
import {ifDefined} from 'lit/directives/if-defined';
import {createRef, Ref, ref} from 'lit/directives/ref';
import {
  DiffResponsiveMode,
  Side,
  LineNumber,
  DiffLayer,
} from '../../../api/diff';
import {BlameInfo} from '../../../types/common';
import {assertIsDefined} from '../../../utils/common-util';
import {fire} from '../../../utils/event-util';
import {getBaseUrl} from '../../../utils/url-util';
import {GrDiffLine, GrDiffLineType} from '../gr-diff/gr-diff-line';
import {diffClasses, isResponsive, renderText} from '../gr-diff/gr-diff-utils';

@customElement('gr-diff-row')
export class GrDiffRow extends LitElement {
  contentLeftRef: Ref<HTMLDivElement> = createRef();

  contentRightRef: Ref<HTMLDivElement> = createRef();

  lineNumberLeftRef: Ref<HTMLTableCellElement> = createRef();

  lineNumberRightRef: Ref<HTMLTableCellElement> = createRef();

  blameCellRef: Ref<HTMLTableCellElement> = createRef();

  tableRowRef: Ref<HTMLTableRowElement> = createRef();

  @property({type: Object})
  left?: GrDiffLine;

  @property({type: Object})
  right?: GrDiffLine;

  @property({type: Object})
  blameInfo?: BlameInfo;

  @property({type: Object})
  responsiveMode?: DiffResponsiveMode;

  @property({type: Number})
  tabSize = 2;

  @property({type: Number})
  lineLength = 80;

  @property({type: Boolean})
  hideFileCommentButton = false;

  @property({type: Object})
  layers: DiffLayer[] = [];

  /**
   * While not visible we are trying to optimize rendering performance by
   * rendering a simpler version of the diff. Once this has become true it
   * cannot be set back to false.
   */
  @state()
  isVisible = false;

  /**
   * Semantic DOM diff testing does not work with just table fragments, so when
   * running such tests the render() method has to wrap the DOM in a proper
   * <table> element.
   */
  @state()
  addTableWrapperForTesting = false;

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
    this.updateLayers(Side.LEFT);
    this.updateLayers(Side.RIGHT);
  }

  /**
   * TODO: This needs some refinement, because layers do not detect whether they
   * have already applied their information, so at the moment all layers would
   * constantly re-apply their information to the diff in each lit rendering
   * pass.
   */
  private updateLayers(side: Side) {
    if (!this.isVisible) return;
    const line = this.line(side);
    const contentEl = this.contentRef(side).value;
    const lineNumberEl = this.lineNumberRef(side).value;
    if (!line || !contentEl || !lineNumberEl) return;
    for (const layer of this.layers) {
      if (typeof layer.annotate === 'function') {
        layer.annotate(contentEl, lineNumberEl, line, side);
      }
    }
  }

  private renderInvisible() {
    return html`
      <tr>
        <td class="style-scope gr-diff blame"></td>
        <td class="style-scope gr-diff left"></td>
        <td class="style-scope gr-diff left content">
          <div>${this.left?.text ?? ''}</div>
        </td>
        <td class="style-scope gr-diff right"></td>
        <td class="style-scope gr-diff right content">
          <div>${this.right?.text ?? ''}</div>
        </td>
      </tr>
    `;
  }

  override render() {
    if (!this.left || !this.right) return;
    if (!this.isVisible) return this.renderInvisible();
    const row = html`
      <tr
        ${ref(this.tableRowRef)}
        class="${diffClasses('diff-row', 'side-by-side')}"
        left-type="${this.left.type}"
        right-type="${this.right.type}"
        tabindex="-1"
      >
        ${this.renderBlameCell()} ${this.renderLineNumberCell(Side.LEFT)}
        ${this.renderContentCell(Side.LEFT)}
        ${this.renderLineNumberCell(Side.RIGHT)}
        ${this.renderContentCell(Side.RIGHT)}
      </tr>
    `;
    if (this.addTableWrapperForTesting) {
      return html`<table>
        ${row}
      </table>`;
    }
    return row;
  }

  getTableRow(): HTMLTableRowElement | undefined {
    return this.tableRowRef.value;
  }

  getLineNumberCell(side: Side): HTMLTableCellElement | undefined {
    return this.lineNumberRef(side).value;
  }

  getContentCell(side: Side) {
    const div = this.contentRef(side)?.value;
    if (!div) return undefined;
    return div.parentElement as HTMLTableCellElement;
  }

  getBlameCell() {
    return this.blameCellRef.value;
  }

  private renderBlameCell() {
    // td.blame has `white-space: pre`, so prettier must not add spaces.
    // prettier-ignore
    return html`
      <td
        ${ref(this.blameCellRef)}
        class="${diffClasses('blame')}"
        data-line-number="${this.left?.beforeNumber ?? 0}"
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
    return html`<span class="${diffClasses(...extras)}"
        ><a href="${url}" class="${diffClasses('blameDate')}">${date}</a
        ><span class="${diffClasses('blameAuthor')}"> ${shortName}</span
        ><gr-hovercard class="${diffClasses()}">
          <span class="${diffClasses('blameHoverCard')}">
            Commit ${commit.id}<br />
            Author: ${commit.author}<br />
            Date: ${date}<br />
            <br />
            ${commit.commit_msg}
          </span>
        </gr-hovercard
      ></span>`;
  }

  private renderLineNumberCell(side: Side): TemplateResult {
    const line = this.line(side);
    const lineNumber = this.lineNumber(side);
    if (!line || !lineNumber || line.type === GrDiffLineType.BLANK) {
      return html`<td
        ${ref(this.lineNumberRef(side))}
        class="${diffClasses(side)}"
      ></td>`;
    }

    return html`<td
      ${ref(this.lineNumberRef(side))}
      class="${diffClasses(side, 'lineNum')}"
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
    if (this.hideFileCommentButton && lineNumber === 'FILE') return;
    if (lineNumber === 'LOST') return;
    // .lineNumButton has `white-space: pre`, so prettier must not add spaces.
    // prettier-ignore
    return html`
      <button
        class="${diffClasses('lineNumButton', side)}"
        tabindex="-1"
        data-value=${lineNumber}
        aria-label=${ifDefined(
          this.computeLineNumberAriaLabel(line, lineNumber)
        )}
        @mouseenter=${() =>
          fire(this, 'line-mouse-enter', {lineNum: lineNumber, side})}
        @mouseleave=${() =>
          fire(this, 'line-mouse-leave', {lineNum: lineNumber, side})}
      >${lineNumber === 'FILE' ? 'File' : lineNumber.toString()}</button>
    `;
  }

  private computeLineNumberAriaLabel(line: GrDiffLine, lineNumber: LineNumber) {
    if (lineNumber === 'FILE') return 'Add file comment';

    // Add aria-labels for valid line numbers.
    // For unified diff, this method will be called with number set to 0 for
    // the empty line number column for added/removed lines. This should not
    // be announced to the screenreader.
    if (lineNumber <= 0) return undefined;

    switch (line.type) {
      case GrDiffLineType.REMOVE:
        return `${lineNumber} removed`;
      case GrDiffLineType.ADD:
        return `${lineNumber} added`;
      case GrDiffLineType.BOTH:
      case GrDiffLineType.BLANK:
        return undefined;
    }
  }

  private renderContentCell(side: Side): TemplateResult {
    const line = this.line(side);
    const lineNumber = this.lineNumber(side);
    assertIsDefined(line, 'line');
    const extras: string[] = [line.type, side];
    if (line.type !== GrDiffLineType.BLANK) extras.push('content');
    if (!line.hasIntralineInfo) extras.push('no-intraline-info');
    if (line.beforeNumber === 'FILE') extras.push('file');
    if (line.beforeNumber === 'LOST') extras.push('lost');

    // .content has `white-space: pre`, so prettier must not add spaces.
    // prettier-ignore
    return html`
      <td
        class="${diffClasses(...extras)}"
        @mouseenter=${() => {
          if (lineNumber)
            fire(this, 'line-mouse-enter', {lineNum: lineNumber, side});
        }}
        @mouseleave=${() => {
          if (lineNumber)
            fire(this, 'line-mouse-leave', {lineNum: lineNumber, side});
        }}
      >${this.renderText(side)}${this.renderThreadGroup(side, lineNumber)}</td>
    `;
  }

  private renderThreadGroup(side: Side, lineNumber?: LineNumber) {
    if (!lineNumber) return;
    // TODO: For the LOST line number the convention is that a <tr> will always
    // be rendered, but it will not be visible, because of all cells being
    // empty. For this to work with lit-based rendering we may only render a
    // thread-group and a <slot> when there is a thread using that slot. The
    // cleanest solution for that is probably introducing a gr-diff-model, where
    // each diff row can look up or observe comment threads.
    // .content has `white-space: pre`, so prettier must not add spaces.
    // prettier-ignore
    return html`<div class="thread-group" data-side=${side}><slot name="${side}-${lineNumber}"></slot></div>`;
  }

  private contentRef(side: Side) {
    return side === Side.LEFT ? this.contentLeftRef : this.contentRightRef;
  }

  private lineNumberRef(side: Side) {
    return side === Side.LEFT
      ? this.lineNumberLeftRef
      : this.lineNumberRightRef;
  }

  private lineNumber(side: Side) {
    return this.line(side)?.lineNumber(side);
  }

  private line(side: Side) {
    return side === Side.LEFT ? this.left : this.right;
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
    if (lineNumber === 'FILE' || lineNumber === 'LOST') return;
    const text = line?.text ?? '';
    const pieces = renderText(
      text,
      isResponsive(this.responsiveMode),
      this.tabSize,
      this.lineLength
    );
    // .content has `white-space: pre`, so prettier must not add spaces.
    // prettier-ignore
    return html`<div ${ref(this.contentRef(side))}
                     class="${diffClasses('contentText', side)}"
                     .ariaLabel=${text}
                     data-side=${ifDefined(side)}
    >${pieces}</div>`;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-diff-row': GrDiffRow;
  }
}
