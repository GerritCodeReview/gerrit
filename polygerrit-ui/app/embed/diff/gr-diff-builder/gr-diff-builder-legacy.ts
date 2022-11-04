/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {
  MovedLinkClickedEventDetail,
  RenderPreferences,
} from '../../../api/diff';
import {fire} from '../../../utils/event-util';
import {GrDiffLine, GrDiffLineType, LineNumber} from '../gr-diff/gr-diff-line';
import {GrDiffGroup, GrDiffGroupType} from '../gr-diff/gr-diff-group';
import '../gr-context-controls/gr-context-controls';
import {
  GrContextControls,
  GrContextControlsShowConfig,
} from '../gr-context-controls/gr-context-controls';
import {DiffInfo, DiffPreferencesInfo} from '../../../types/diff';
import {DiffViewMode, Side} from '../../../constants/constants';
import {DiffLayer} from '../../../types/types';
import {
  createBlameElement,
  createElementDiff,
  createElementDiffWithText,
  formatText,
  getResponsiveMode,
} from '../gr-diff/gr-diff-utils';
import {GrDiffBuilder} from './gr-diff-builder';
import {BlameInfo} from '../../../types/common';

function lineTdSelector(lineNumber: LineNumber, side?: Side): string {
  const sideSelector = side ? `.${side}` : '';
  return `td.lineNum[data-value="${lineNumber}"]${sideSelector}`;
}
/**
 * Base class for builders that are creating the DOM elements programmatically
 * by calling `document.createElement()` and such. We are calling such builders
 * "legacy", because we want to create (Lit) component based diff elements.
 *
 * TODO: Do not subclass `GrDiffBuilder`. Use composition and interfaces.
 */
export abstract class GrDiffBuilderLegacy extends GrDiffBuilder {
  constructor(
    diff: DiffInfo,
    prefs: DiffPreferencesInfo,
    outputEl: HTMLElement,
    layers: DiffLayer[] = [],
    renderPrefs?: RenderPreferences
  ) {
    super(diff, prefs, outputEl, layers, renderPrefs);
  }

  override getContentTdByLine(
    lineNumber: LineNumber,
    side?: Side,
    root: Element = this.outputEl
  ): HTMLTableCellElement | null {
    return root.querySelector<HTMLTableCellElement>(
      `${lineTdSelector(lineNumber, side)} ~ td.content`
    );
  }

  override getLineElByNumber(
    lineNumber: LineNumber,
    side?: Side
  ): HTMLTableCellElement | null {
    return this.outputEl.querySelector<HTMLTableCellElement>(
      lineTdSelector(lineNumber, side)
    );
  }

  override getLineNumberRows() {
    return Array.from(
      this.outputEl.querySelectorAll<HTMLTableRowElement>(
        ':not(.contextControl) > .diff-row'
      ) ?? []
    ).filter(tr => tr.querySelector('button'));
  }

  override getLineNumEls(side: Side): HTMLTableCellElement[] {
    return Array.from(
      this.outputEl.querySelectorAll<HTMLTableCellElement>(
        `td.lineNum.${side}`
      ) ?? []
    );
  }

  override getBlameTdByLine(lineNum: number): Element | undefined {
    return (
      this.outputEl.querySelector(`td.blame[data-line-number="${lineNum}"]`) ??
      undefined
    );
  }

  override getContentByLine(
    lineNumber: LineNumber,
    side?: Side,
    root?: HTMLElement
  ): HTMLElement | null {
    const td = this.getContentTdByLine(lineNumber, side, root);
    return td ? td.querySelector('.contentText') : null;
  }

  override renderContentByRange(
    start: LineNumber,
    end: LineNumber,
    side: Side
  ) {
    const lines: GrDiffLine[] = [];
    const elements: HTMLElement[] = [];
    let line;
    let el;
    this.findLinesByRange(start, end, side, lines, elements);
    for (let i = 0; i < lines.length; i++) {
      line = lines[i];
      el = elements[i];
      if (!el || !el.parentElement) {
        // Cannot re-render an element if it does not exist. This can happen
        // if lines are collapsed and not visible on the page yet.
        continue;
      }
      const lineNumberEl = this.getLineNumberEl(el, side);
      const newContent = this.createTextEl(lineNumberEl, line, side)
        .firstChild as HTMLElement;
      // Note that ${el.id} ${newContent.id} might actually mismatch: In unified
      // diff we are rendering the same content twice for all the diff chunk
      // that are unchanged from left to right. TODO: Be smarter about this.
      el.parentElement.replaceChild(newContent, el);
    }
  }

  override renderBlameByRange(blame: BlameInfo, start: number, end: number) {
    for (let i = start; i <= end; i++) {
      // TODO(wyatta): this query is expensive, but, when traversing a
      // range, the lines are consecutive, and given the previous blame
      // cell, the next one can be reached cheaply.
      const blameCell = this.getBlameTdByLine(i);
      if (!blameCell) continue;

      // Remove the element's children (if any).
      while (blameCell.hasChildNodes()) {
        blameCell.removeChild(blameCell.lastChild!);
      }
      const blameEl = createBlameElement(i, blame);
      if (blameEl) blameCell.appendChild(blameEl);
    }
  }

  /**
   * Finds the line number element given the content element by walking up the
   * DOM tree to the diff row and then querying for a .lineNum element on the
   * requested side.
   *
   * TODO(brohlfs): Consolidate this with getLineEl... methods in html file.
   */
  // visible for testing
  getLineNumberEl(content: HTMLElement, side: Side): HTMLElement | null {
    let row: HTMLElement | null = content;
    while (row && !row.classList.contains('diff-row')) row = row.parentElement;
    return row ? (row.querySelector('.lineNum.' + side) as HTMLElement) : null;
  }

  /**
   * Adds <tr> table rows to a <tbody> section for allowing the user to expand
   * collapsed of lines. Called by subclasses.
   */
  protected createContextControls(
    section: HTMLElement,
    group: GrDiffGroup,
    viewMode: DiffViewMode
  ) {
    const leftStart = group.lineRange.left.start_line;
    const leftEnd = group.lineRange.left.end_line;
    const firstGroupIsSkipped = !!group.contextGroups[0].skip;
    const lastGroupIsSkipped =
      !!group.contextGroups[group.contextGroups.length - 1].skip;

    const containsWholeFile = this.numLinesLeft === leftEnd - leftStart + 1;
    const showAbove =
      (leftStart > 1 && !firstGroupIsSkipped) || containsWholeFile;
    const showBelow = leftEnd < this.numLinesLeft && !lastGroupIsSkipped;

    if (showAbove) {
      const paddingRow = this.createContextControlPaddingRow(viewMode);
      paddingRow.classList.add('above');
      section.appendChild(paddingRow);
    }
    section.appendChild(
      this.createContextControlRow(group, showAbove, showBelow, viewMode)
    );
    if (showBelow) {
      const paddingRow = this.createContextControlPaddingRow(viewMode);
      paddingRow.classList.add('below');
      section.appendChild(paddingRow);
    }
  }

  /**
   * Creates a context control <tr> table row for with buttons the allow the
   * user to expand collapsed lines. Buttons extend from the gap created by this
   * method up or down into the area of code that they affect.
   */
  private createContextControlRow(
    group: GrDiffGroup,
    showAbove: boolean,
    showBelow: boolean,
    viewMode: DiffViewMode
  ): HTMLElement {
    const row = createElementDiff('tr', 'dividerRow');
    let showConfig: GrContextControlsShowConfig;
    if (showAbove && !showBelow) {
      showConfig = 'above';
    } else if (!showAbove && showBelow) {
      showConfig = 'below';
    } else {
      // Note that !showAbove && !showBelow also intentionally creates
      // "show-both". This means the file is completely collapsed, which is
      // unusual, but at least happens in one test.
      showConfig = 'both';
    }
    row.classList.add(`show-${showConfig}`);

    row.appendChild(this.createBlameCell(0));
    if (viewMode === DiffViewMode.SIDE_BY_SIDE) {
      row.appendChild(createElementDiff('td'));
    }

    const cell = createElementDiff('td', 'dividerCell');
    const colspan = viewMode === DiffViewMode.SIDE_BY_SIDE ? '5' : '3';
    cell.setAttribute('colspan', colspan);
    row.appendChild(cell);

    const contextControls = createElementDiff(
      'gr-context-controls'
    ) as GrContextControls;
    contextControls.diff = this._diff;
    contextControls.renderPreferences = this.renderPrefs;
    contextControls.group = group;
    contextControls.showConfig = showConfig;
    cell.appendChild(contextControls);
    return row;
  }

  /**
   * Creates a table row to serve as padding between code and context controls.
   * Blame column, line gutters, and content area will continue visually, but
   * context controls can render over this background to map more clearly to
   * the area of code they expand.
   */
  private createContextControlPaddingRow(viewMode: DiffViewMode) {
    const row = createElementDiff('tr', 'contextBackground');

    if (viewMode === DiffViewMode.SIDE_BY_SIDE) {
      row.classList.add('side-by-side');
      row.setAttribute('left-type', GrDiffGroupType.CONTEXT_CONTROL);
      row.setAttribute('right-type', GrDiffGroupType.CONTEXT_CONTROL);
    } else {
      row.classList.add('unified');
    }

    row.appendChild(this.createBlameCell(0));
    row.appendChild(createElementDiff('td', 'contextLineNum'));
    if (viewMode === DiffViewMode.SIDE_BY_SIDE) {
      row.appendChild(createElementDiff('td', 'sign'));
      row.appendChild(createElementDiff('td'));
    }
    row.appendChild(createElementDiff('td', 'contextLineNum'));
    if (viewMode === DiffViewMode.SIDE_BY_SIDE) {
      row.appendChild(createElementDiff('td', 'sign'));
    }
    row.appendChild(createElementDiff('td'));

    return row;
  }

  protected createLineEl(
    line: GrDiffLine,
    number: LineNumber,
    type: GrDiffLineType,
    side: Side
  ) {
    const td = createElementDiff('td');
    td.classList.add(side);
    if (line.type === GrDiffLineType.BLANK) {
      td.classList.add('blankLineNum');
      return td;
    }
    if (line.type === GrDiffLineType.BOTH || line.type === type) {
      td.classList.add('lineNum');
      td.dataset['value'] = number.toString();

      if (
        ((this._prefs.show_file_comment_button === false ||
          this.renderPrefs?.show_file_comment_button === false) &&
          number === 'FILE') ||
        number === 'LOST'
      ) {
        return td;
      }

      const button = createElementDiff('button');
      td.appendChild(button);
      button.tabIndex = -1;
      button.classList.add('lineNumButton');
      button.classList.add(side);
      button.dataset['value'] = number.toString();
      button.id =
        side === Side.LEFT ? `left-button-${number}` : `right-button-${number}`;
      button.textContent = number === 'FILE' ? 'File' : number.toString();
      if (number === 'FILE') {
        button.setAttribute('aria-label', 'Add file comment');
      }

      // Add aria-labels for valid line numbers.
      // For unified diff, this method will be called with number set to 0 for
      // the empty line number column for added/removed lines. This should not
      // be announced to the screenreader.
      if (number > 0) {
        if (line.type === GrDiffLineType.REMOVE) {
          button.setAttribute('aria-label', `${number} removed`);
        } else if (line.type === GrDiffLineType.ADD) {
          button.setAttribute('aria-label', `${number} added`);
        } else {
          button.setAttribute('aria-label', `${number} unmodified`);
        }
      }
      this.addLineNumberMouseEvents(td, number, side);
    }
    return td;
  }

  private addLineNumberMouseEvents(
    el: HTMLElement,
    number: LineNumber,
    side: Side
  ) {
    el.addEventListener('mouseenter', () => {
      fire(el, 'line-mouse-enter', {lineNum: number, side});
    });
    el.addEventListener('mouseleave', () => {
      fire(el, 'line-mouse-leave', {lineNum: number, side});
    });
  }

  // visible for testing
  createTextEl(
    lineNumberEl: HTMLElement | null,
    line: GrDiffLine,
    side?: Side
  ) {
    const td = createElementDiff('td');
    if (line.type !== GrDiffLineType.BLANK) {
      td.classList.add('content');
    }
    if (side) {
      td.classList.add(side);
    }

    // If intraline info is not available, the entire line will be
    // considered as changed and marked as dark red / green color
    if (!line.hasIntralineInfo) {
      td.classList.add('no-intraline-info');
    }
    td.classList.add(line.type);

    const {beforeNumber, afterNumber} = line;
    if (beforeNumber !== 'FILE' && beforeNumber !== 'LOST') {
      const responsiveMode = getResponsiveMode(this._prefs, this.renderPrefs);
      let contentId = '';
      if (side === Side.LEFT && beforeNumber > 0) {
        contentId = `left-content-${beforeNumber}`;
      }
      if (side === Side.RIGHT && afterNumber > 0) {
        contentId = `right-content-${afterNumber}`;
      }
      const contentText = formatText(
        line.text,
        responsiveMode,
        this._prefs.tab_size,
        this._prefs.line_length,
        contentId
      );

      if (side) {
        contentText.setAttribute('data-side', side);
        const number = line.lineNumber(side);
        this.addLineNumberMouseEvents(td, number, side);
      }

      if (lineNumberEl && side) {
        for (const layer of this.layers) {
          if (typeof layer.annotate === 'function') {
            layer.annotate(contentText, lineNumberEl, line, side);
          }
        }
      } else {
        console.error('lineNumberEl or side not set, skipping layer.annotate');
      }

      td.appendChild(contentText);
    } else if (line.beforeNumber === 'FILE') {
      td.classList.add('file');
    } else if (line.beforeNumber === 'LOST') {
      td.classList.add('lost');
    }

    if (side && line.lineNumber(side)) {
      const lineNumber = line.lineNumber(side);
      const threadGroupEl = document.createElement('div');
      threadGroupEl.className = 'thread-group';
      threadGroupEl.setAttribute('data-side', side);
      const slot = document.createElement('slot');
      slot.name = `${side}-${lineNumber}`;
      threadGroupEl.appendChild(slot);
      td.appendChild(threadGroupEl);
    }

    return td;
  }

  private createMovedLineAnchor(line: number, side: Side) {
    const anchor = createElementDiffWithText('a', `${line}`);

    // href is not actually used but important for Screen Readers
    anchor.setAttribute('href', `#${line}`);
    anchor.addEventListener('click', e => {
      e.preventDefault();
      anchor.dispatchEvent(
        new CustomEvent<MovedLinkClickedEventDetail>('moved-link-clicked', {
          detail: {
            lineNum: line,
            side,
          },
          composed: true,
          bubbles: true,
        })
      );
    });
    return anchor;
  }

  private createMoveDescriptionDiv(movedIn: boolean, group: GrDiffGroup) {
    const div = createElementDiff('div');
    if (group.moveDetails?.range) {
      const {changed, range} = group.moveDetails;
      const otherSide = movedIn ? Side.LEFT : Side.RIGHT;
      const andChangedLabel = changed ? 'and changed ' : '';
      const direction = movedIn ? 'from' : 'to';
      const textLabel = `Moved ${andChangedLabel}${direction} lines `;
      div.appendChild(createElementDiffWithText('span', textLabel));
      div.appendChild(this.createMovedLineAnchor(range.start, otherSide));
      div.appendChild(createElementDiffWithText('span', ' - '));
      div.appendChild(this.createMovedLineAnchor(range.end, otherSide));
    } else {
      div.appendChild(
        createElementDiffWithText('span', movedIn ? 'Moved in' : 'Moved out')
      );
    }
    return div;
  }

  protected buildMoveControls(group: GrDiffGroup) {
    const movedIn = group.adds.length > 0;
    const {
      numberOfCells,
      movedOutIndex,
      movedInIndex,
      lineNumberCols,
      signCols,
    } = this.getMoveControlsConfig();

    let controlsClass;
    let descriptionIndex;
    const descriptionTextDiv = this.createMoveDescriptionDiv(movedIn, group);
    if (movedIn) {
      controlsClass = 'movedIn';
      descriptionIndex = movedInIndex;
    } else {
      controlsClass = 'movedOut';
      descriptionIndex = movedOutIndex;
    }

    const controls = createElementDiff('tr', `moveControls ${controlsClass}`);
    const cells = [...Array(numberOfCells).keys()].map(() =>
      createElementDiff('td')
    );
    lineNumberCols.forEach(index => {
      cells[index].classList.add('moveControlsLineNumCol');
    });

    if (signCols) {
      cells[signCols.left].classList.add('sign', 'left');
      cells[signCols.right].classList.add('sign', 'right');
    }
    const moveRangeHeader = createElementDiff('gr-range-header');
    moveRangeHeader.setAttribute('icon', 'move_item');
    moveRangeHeader.appendChild(descriptionTextDiv);
    cells[descriptionIndex].classList.add('moveHeader');
    cells[descriptionIndex].appendChild(moveRangeHeader);
    cells.forEach(c => {
      controls.appendChild(c);
    });
    return controls;
  }

  /**
   * Create a blame cell for the given base line. Blame information will be
   * included in the cell if available.
   */
  // visible for testing
  createBlameCell(lineNumber: LineNumber): HTMLTableCellElement {
    const blameTd = createElementDiff('td', 'blame') as HTMLTableCellElement;
    blameTd.setAttribute('data-line-number', lineNumber.toString());
    if (!lineNumber) return blameTd;

    const blameInfo = this.getBlameCommitForBaseLine(lineNumber);
    if (!blameInfo) return blameTd;

    blameTd.appendChild(createBlameElement(lineNumber, blameInfo));
    return blameTd;
  }
}
