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

@customElement('gr-diff-row')
export class GrDiffRow extends LitElement {
  contentLeftRef: Ref<HTMLDivElement> = createRef();

  contentRightRef: Ref<HTMLDivElement> = createRef();

  lineNumberLeftRef: Ref<HTMLTableCellElement> = createRef();

  lineNumberRightRef: Ref<HTMLTableCellElement> = createRef();

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

  // TODO
  static override styles = css`
    :host {
    }
  `;

  override render() {
    if (!this.left || !this.right) return;
    return html`
      <tr
        ${diffClasses('diff-row', 'side-by-side')}
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
        ${diffClasses()}
        class="style-scope gr-diff blame"
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
        ${diffClasses('side')}
      ></td>`;
    }

    return html`<td
      ${ref(this.lineNumberRef(side))}
      ${diffClasses('side', 'lineNum')}
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
    const hideButton =
      this.hideFileCommentButton &&
      (lineNumber === 'FILE' || lineNumber === 'LOST');
    if (hideButton) return;
    return html`
      <button
        ${diffClasses('lineNumButton', side)}
        tabindex="-1"
        data-value=${lineNumber}
        aria-label=${ifDefined(
          this.computeLineNumberAriaLabel(line, lineNumber)
        )}
        @mouseenter=${() =>
          fire(this, 'line-mouse-enter', {lineNum: lineNumber, side})}
        @mouseleave=${() =>
          fire(this, 'line-mouse-leave', {lineNum: lineNumber, side})}
      >
        ${lineNumber === 'FILE' ? 'File' : lineNumber.toString()}
      </button>
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

    return html`
      <td
        ${diffClasses(...extras)}
        @mouseenter=${() => {
          if (lineNumber)
            fire(this, 'line-mouse-enter', {lineNum: lineNumber, side});
        }}
        @mouseleave=${() => {
          if (lineNumber)
            fire(this, 'line-mouse-leave', {lineNum: lineNumber, side});
        }}
      >
        ${this.renderText(side)}
      </td>
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
    const line = this.line(side);
    return side === Side.LEFT ? line?.beforeNumber : line?.afterNumber;
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
    return html`
      <div
        ${ref(this.contentRef(side))}
        ${diffClasses('contentText')}
        .ariaLabel=${text}
        data-side=${ifDefined(side)}
      >
        ${pieces}
      </div>
    `;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-diff-row': GrDiffRow;
  }
}
