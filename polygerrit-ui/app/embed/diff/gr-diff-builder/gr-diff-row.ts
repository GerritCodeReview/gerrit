/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {css, html, LitElement, TemplateResult} from 'lit';
import {customElement, property} from 'lit/decorators';
import {ifDefined} from 'lit/directives/if-defined';
import {createRef, Ref, ref} from 'lit/directives/ref';
import {
  DiffResponsiveMode,
  Side,
  LineNumber,
  DiffLayer,
} from '../../../api/diff';
import {assertIsDefined} from '../../../utils/common-util';
import {fire} from '../../../utils/event-util';
import {GrDiffLine, GrDiffLineType} from '../gr-diff/gr-diff-line';
import {
  diffClasses,
  isResponsive,
  lineBreak,
  REGEX_TAB_OR_SURROGATE_PAIR,
  tabWrapper,
} from '../gr-diff/gr-diff-utils';
import {grRangedCommentTheme} from '../gr-ranged-comment-themes/gr-ranged-comment-theme';
import {grSyntaxTheme} from '../gr-syntax-themes/gr-syntax-theme';

@customElement('gr-diff-row')
export class GrDiffRow extends LitElement {
  contentLeftRef: Ref<HTMLDivElement> = createRef();

  contentRightRef: Ref<HTMLDivElement> = createRef();

  lineNumberLeftRef: Ref<HTMLTableCellElement> = createRef();

  lineNumberRightRef: Ref<HTMLTableCellElement> = createRef();

  blameCellRef: Ref<HTMLTableCellElement> = createRef();

  @property({type: Object})
  left?: GrDiffLine;

  @property({type: Object})
  right?: GrDiffLine;

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

  static override styles = [
    grRangedCommentTheme,
    grSyntaxTheme,
    css`
      :host {
        display: contents;
      }
      td {
        padding: 0px;
      }
      td.lineNum {
        /* Enforces background whenever lines wrap */
        background-color: var(--diff-blank-background-color);
      }

      /* Provides the option to add side borders (left and right) to the line number column. */
      td.left,
      td.right,
      td.moveControlsLineNumCol,
      td.contextLineNum {
        box-shadow: var(--line-number-box-shadow, unset);
      }
      .lineNumButton {
        display: block;
        width: 100%;
        height: 100%;
        background-color: var(--diff-blank-background-color);
        box-shadow: var(--line-number-box-shadow, unset);
        font: inherit;
      }
      td.lineNum {
        vertical-align: top;
      }
      /*
      The only way to focus this (clicking) will apply our own focus styling,
      so this default styling is not needed and distracting.
      */
      .lineNumButton:focus {
        outline: none;
      }
      .diff-row {
        outline: none;
        user-select: none;
      }
      .diff-row.target-row.target-side-left .lineNumButton.left,
      .diff-row.target-row.target-side-right .lineNumButton.right,
      .diff-row.target-row.unified .lineNumButton {
        background-color: var(--diff-selection-background-color);
        color: var(--primary-text-color);
      }
      .displayLine .diff-row.target-row td {
        box-shadow: inset 0 -1px var(--border-color);
      }
      .target-row td.blame {
        background: var(--diff-selection-background-color);
      }
      .content {
        background-color: var(--diff-blank-background-color);
      }
      /*
      The file line, which has no contentText, add some margin before the first
      comment. We cannot add padding the container because we only want it if
      there is at least one comment thread, and the slotting makes :empty not
      work as expected.
     */
      .content.file slot:first-child::slotted(.comment-thread) {
        display: block;
        margin-top: var(--spacing-xs);
      }
      .contentText {
        background-color: var(--view-background-color);
      }
      .blank {
        background-color: var(--diff-blank-background-color);
      }
      .image-diff .content {
        background-color: var(--diff-blank-background-color);
      }
      .responsive {
        width: 100%;
      }
      .responsive .contentText {
        white-space: break-spaces;
        word-break: break-all;
      }
      .lineNumButton,
      .content {
        vertical-align: top;
        white-space: pre;
      }
      .contextLineNum,
      .lineNumButton {
        -webkit-user-select: none;
        -moz-user-select: none;
        -ms-user-select: none;
        user-select: none;

        color: var(--deemphasized-text-color);
        padding: 0 var(--spacing-m);
        border: 0;
        text-align: right;
      }
      .canComment .lineNumButton {
        cursor: pointer;
      }
      .content {
        /* Set min width since setting width on table cells still
           allows them to shrink. Do not set max width because
           CJK (Chinese-Japanese-Korean) glyphs have variable width */
        min-width: var(--content-width, 80ch);
        width: var(--content-width, 80ch);
      }
      .content.add .contentText .intraline,
      /* If there are no intraline info, consider everything changed */
      .content.add.no-intraline-info .contentText,
      .delta.total .content.add .contentText {
        background-color: var(--dark-add-highlight-color);
      }
      .content.add .contentText {
        background-color: var(--light-add-highlight-color);
      }
      .content.remove .contentText .intraline,
      /* If there are no intraline info, consider everything changed */
      .content.remove.no-intraline-info .contentText,
      .delta.total .content.remove .contentText {
        background-color: var(--dark-remove-highlight-color);
      }
      .content.remove .contentText {
        background-color: var(--light-remove-highlight-color);
      }

      /* dueToRebase */
      .dueToRebase .content.add .contentText .intraline,
      .delta.total.dueToRebase .content.add .contentText {
        background-color: var(--dark-rebased-add-highlight-color);
      }
      .dueToRebase .content.add .contentText {
        background-color: var(--light-rebased-add-highlight-color);
      }
      .dueToRebase .content.remove .contentText .intraline,
      .delta.total.dueToRebase .content.remove .contentText {
        background-color: var(--dark-rebased-remove-highlight-color);
      }
      .dueToRebase .content.remove .contentText {
        background-color: var(--light-remove-add-highlight-color);
      }

      /* dueToMove */
      .dueToMove .content.add .contentText,
      .dueToMove .moveControls.movedIn .moveHeader,
      .delta.total.dueToMove .content.add .contentText {
        background-color: var(--diff-moved-in-background);
      }

      .dueToMove .content.remove .contentText,
      .dueToMove .moveControls.movedOut .moveHeader,
      .delta.total.dueToMove .content.remove .contentText {
        background-color: var(--diff-moved-out-background);
      }

      .delta.dueToMove .movedIn .moveHeader {
        --gr-range-header-color: var(--diff-moved-in-label-color);
      }
      .delta.dueToMove .movedOut .moveHeader {
        --gr-range-header-color: var(--diff-moved-out-label-color);
      }

      .moveHeader a {
        color: inherit;
      }

      /* ignoredWhitespaceOnly */
      .ignoredWhitespaceOnly .content.add .contentText .intraline,
      .delta.total.ignoredWhitespaceOnly .content.add .contentText,
      .ignoredWhitespaceOnly .content.add .contentText,
      .ignoredWhitespaceOnly .content.remove .contentText .intraline,
      .delta.total.ignoredWhitespaceOnly .content.remove .contentText,
      .ignoredWhitespaceOnly .content.remove .contentText {
        background-color: var(--view-background-color);
      }

      .content .contentText:empty:after {
        /* Newline, to ensure empty lines are one line-height tall. */
        content: '\\A';
      }

      .br:after {
        /* Line feed */
        content: '\\A';
      }
      .tab {
        display: inline-block;
      }
      .tab-indicator:before {
        color: var(--diff-tab-indicator-color);
        /* >> character */
        content: '\\00BB';
        position: absolute;
      }
      .special-char-indicator {
        /* spacing so elements don't collide */
        padding-right: var(--spacing-m);
      }
      .special-char-indicator:before {
        color: var(--diff-tab-indicator-color);
        content: '•';
        position: absolute;
      }
      .special-char-warning {
        /* spacing so elements don't collide */
        padding-right: var(--spacing-m);
      }
      .special-char-warning:before {
        color: var(--warning-foreground);
        content: '!';
        position: absolute;
      }
      /* Is defined after other background-colors, such that this
         rule wins in case of same specificity. */
      .trailing-whitespace,
      .content .trailing-whitespace,
      .trailing-whitespace .intraline,
      .content .trailing-whitespace .intraline {
        border-radius: var(--border-radius, 4px);
        background-color: var(--diff-trailing-whitespace-indicator);
      }

      td.lost div {
        background-color: var(--info-background);
        padding: var(--spacing-s) 0 0 0;
      }
      td.lost div:first-of-type {
        font-family: var(--font-family, 'Roboto');
        font-size: var(--font-size-normal, 14px);
        line-height: var(--line-height-normal);
      }
      td.lost iron-icon {
        padding: 0 var(--spacing-s) 0 var(--spacing-m);
        color: var(--blue-700);
      }
      col.blame {
        display: none;
      }
      td.blame {
        display: none;
        padding: 0 var(--spacing-m);
        white-space: pre;
      }
      :host(.showBlame) col.blame {
        display: table-column;
      }
      :host(.showBlame) td.blame {
        display: table-cell;
      }
      td.blame > span {
        opacity: 0.6;
      }
      td.blame > span.startOfRange {
        opacity: 1;
      }
      td.blame .blameDate {
        font-family: var(--monospace-font-family);
        color: var(--link-color);
        text-decoration: none;
      }
      .responsive td.blame {
        overflow: hidden;
        width: 200px;
      }
      /** Support the line length indicator **/
      .responsive td.content .contentText {
        /*
      Same strategy as in https://stackoverflow.com/questions/1179928/how-can-i-put-a-vertical-line-down-the-center-of-a-div
      */
        background-image: linear-gradient(
          var(--line-length-indicator-color),
          var(--line-length-indicator-color)
        );
        background-size: 1px 100%;
        background-position: var(--line-limit-marker) 0;
        background-repeat: no-repeat;
      }

      .lineNum.COVERED .lineNumButton {
        background-color: var(--coverage-covered, #e0f2f1);
      }
      .lineNum.NOT_COVERED .lineNumButton {
        background-color: var(--coverage-not-covered, #ffd1a4);
      }
      .lineNum.PARTIALLY_COVERED .lineNumButton {
        background: linear-gradient(
          to right bottom,
          var(--coverage-not-covered, #ffd1a4) 0%,
          var(--coverage-not-covered, #ffd1a4) 50%,
          var(--coverage-covered, #e0f2f1) 50%,
          var(--coverage-covered, #e0f2f1) 100%
        );
      }

      .content,
      .contextControl,
      .blame {
        -webkit-user-select: none;
        -moz-user-select: none;
        -ms-user-select: none;
        user-select: none;
      }

      .selected-left:not(.selected-comment)
        .side-by-side
        .left
        + .content
        .contentText,
      .selected-right:not(.selected-comment)
        .side-by-side
        .right
        + .content
        .contentText,
      .selected-left:not(.selected-comment)
        .unified
        .left.lineNum
        ~ .content:not(.both)
        .contentText,
      .selected-right:not(.selected-comment)
        .unified
        .right.lineNum
        ~ .content
        .contentText,
      .selected-left.selected-comment .side-by-side .left + .content .message,
      .selected-right.selected-comment
        .side-by-side
        .right
        + .content
        .message
        :not(.collapsedContent),
      .selected-comment .unified .message :not(.collapsedContent),
      .selected-blame .blame {
        -webkit-user-select: text;
        -moz-user-select: text;
        -ms-user-select: text;
        user-select: text;
      }

      /** Make comments selectable when selected */
      .selected-left.selected-comment
        ::slotted(gr-comment-thread[diff-side='left']),
      .selected-right.selected-comment
        ::slotted(gr-comment-thread[diff-side='right']) {
        -webkit-user-select: text;
        -moz-user-select: text;
        -ms-user-select: text;
        user-select: text;
      }

      .token-highlight {
        background-color: var(--token-highlighting-color, #fffd54);
      }
    `,
  ];

  override render() {
    if (!this.left || !this.right) return;
    console.log(
      `diff-row render ${this.left.beforeNumber} ${this.right.afterNumber}`
    );
    return html`
      <tr
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
  }

  /**
   * Create a blame cell for the given base line. Blame information will be
   * included in the cell if available.
   */
  private renderBlameCell() {
    // TODO: Add actual blame info.
    return html`
      <td
        ${ref(this.blameCellRef)}
        class="${diffClasses('blame')}"
        data-line-number="${this.left?.beforeNumber ?? 0}"
      ></td>
    `;
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
    if (lineNumber > 0) {
      if (line.type === GrDiffLineType.REMOVE) {
        return `${lineNumber} removed`;
      } else if (line.type === GrDiffLineType.ADD) {
        return `${lineNumber} added`;
      }
    }
    return undefined;
  }

  private renderContentCell(side: Side): TemplateResult {
    const line = this.line(side);
    const lineNumber = this.lineNumber(side);
    assertIsDefined(line, 'line');
    const extras: string[] = [];
    extras.push(line.type);
    if (line.type !== GrDiffLineType.BLANK) extras.push('content');
    if (!line.hasIntralineInfo) extras.push('no-intraline-info');
    if (line.beforeNumber === 'FILE') extras.push('file');
    if (line.beforeNumber === 'LOST') extras.push('lost');

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
      >${this.renderText(side)}</td>
    `;
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

  override updated() {
    this.updateLayers(Side.LEFT);
    this.updateLayers(Side.RIGHT);
  }

  private updateLayers(side: Side) {
    const line = this.line(side);
    const contentEl = this.contentRef(side).value;
    const lineNumberEl = this.lineNumberRef(side).value;
    console.log(
      `diff-row update layers ${this.left?.beforeNumber} ${
        this.right?.afterNumber
      } ${this.layers.length} ${!line || !contentEl || !lineNumberEl}`
    );
    if (!line || !contentEl || !lineNumberEl) return;
    for (const layer of this.layers) {
      if (typeof layer.annotate === 'function') {
        layer.annotate(contentEl, lineNumberEl, line, side);
      }
    }
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
    const tabSize = this.tabSize;
    const lineLimit = this.lineLength;
    const responsive = isResponsive(this.responsiveMode);
    let columnPos = 0;
    let textOffset = 0;
    const pieces = [];
    for (const segment of text.split(REGEX_TAB_OR_SURROGATE_PAIR)) {
      if (segment) {
        // |segment| contains only normal characters. If |segment| doesn't fit
        // entirely on the current line, append chunks of |segment| followed by
        // line breaks.
        let rowStart = 0;
        let rowEnd = lineLimit - columnPos;
        while (rowEnd < segment.length) {
          pieces.push(segment.substring(rowStart, rowEnd));
          pieces.push(lineBreak(responsive));
          columnPos = 0;
          rowStart = rowEnd;
          rowEnd += lineLimit;
        }
        // Append the last part of |segment|, which fits on the current line.
        pieces.push(segment.substring(rowStart));
        columnPos += segment.length - rowStart;
        textOffset += segment.length;
      }
      if (textOffset < text.length) {
        // Handle the special character at |textOffset|.
        if (text.startsWith('\t', textOffset)) {
          // Append a single '\t' character.
          let effectiveTabSize = tabSize - (columnPos % tabSize);
          if (columnPos + effectiveTabSize > lineLimit) {
            pieces.push(lineBreak(responsive));
            columnPos = 0;
            effectiveTabSize = tabSize;
          }
          pieces.push(tabWrapper(effectiveTabSize));
          columnPos += effectiveTabSize;
          textOffset++;
        } else {
          // Append a single surrogate pair.
          if (columnPos >= lineLimit) {
            pieces.push(lineBreak(responsive));
            columnPos = 0;
          }
          pieces.push(text.substring(textOffset, textOffset + 2));
          textOffset += 2;
          columnPos += 1;
        }
      }
    }
    // prettier-ignore
    return html`<div ${ref(this.contentRef(side))}
                     class="${diffClasses('contentText')}"
                     .ariaLabel=${text}
                     data-side=${ifDefined(side)}
    >${pieces}</div>`;
  }

  findContentCell(side: Side) {
    const div = this.contentRef(side)?.value;
    if (!div) return undefined;
    return div.parentElement as HTMLTableCellElement;
  }

  findLineNumberCell(side: Side) {
    return this.lineNumberRef(side)?.value;
  }

  findBlameCell() {
    return this.blameCellRef.value;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-diff-row': GrDiffRow;
  }
}
