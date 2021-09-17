/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the 'License');
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import {
  ContentLoadNeededEventDetail,
  DiffContextExpandedExternalDetail,
  MovedLinkClickedEventDetail,
  RenderPreferences,
} from '../../../api/diff';
import {getBaseUrl} from '../../../utils/url-util';
import {fire} from '../../../utils/event-util';
import {GrDiffLine, GrDiffLineType, LineNumber} from '../gr-diff/gr-diff-line';
import {GrDiffGroup, GrDiffGroupType} from '../gr-diff/gr-diff-group';

import '../gr-context-controls/gr-context-controls';
import {
  GrContextControls,
  GrContextControlsShowConfig,
} from '../gr-context-controls/gr-context-controls';
import {BlameInfo} from '../../../types/common';
import {
  DiffInfo,
  DiffPreferencesInfo,
  DiffResponsiveMode,
} from '../../../types/diff';
import {DiffViewMode, Side} from '../../../constants/constants';
import {DiffLayer} from '../../../types/types';

/**
 * In JS, unicode code points above 0xFFFF occupy two elements of a string.
 * For example '𐀏'.length is 2. An occurrence of such a code point is called a
 * surrogate pair.
 *
 * This regex segments a string along tabs ('\t') and surrogate pairs, since
 * these are two cases where '1 char' does not automatically imply '1 column'.
 *
 * TODO: For human languages whose orthographies use combining marks, this
 * approach won't correctly identify the grapheme boundaries. In those cases,
 * a grapheme consists of multiple code points that should count as only one
 * character against the column limit. Getting that correct (if it's desired)
 * is probably beyond the limits of a regex, but there are nonstandard APIs to
 * do this, and proposed (but, as of Nov 2017, unimplemented) standard APIs.
 *
 * Further reading:
 *   On Unicode in JS: https://mathiasbynens.be/notes/javascript-unicode
 *   Graphemes: http://unicode.org/reports/tr29/#Grapheme_Cluster_Boundaries
 *   A proposed JS API: https://github.com/tc39/proposal-intl-segmenter
 */
const REGEX_TAB_OR_SURROGATE_PAIR = /\t|[\uD800-\uDBFF][\uDC00-\uDFFF]/;

export interface DiffContextExpandedEventDetail
  extends DiffContextExpandedExternalDetail {
  groups: GrDiffGroup[];
  section: HTMLElement;
  numLines: number;
}

declare global {
  interface HTMLElementEventMap {
    'diff-context-expanded': CustomEvent<DiffContextExpandedEventDetail>;
    'content-load-needed': CustomEvent<ContentLoadNeededEventDetail>;
  }
}

export function getResponsiveMode(
  prefs: DiffPreferencesInfo,
  renderPrefs?: RenderPreferences
): DiffResponsiveMode {
  if (renderPrefs?.responsive_mode) {
    return renderPrefs.responsive_mode;
  }
  // Backwards compatibility to the line_wrapping param.
  if (prefs.line_wrapping) {
    return 'FULL_RESPONSIVE';
  }
  return 'NONE';
}

export function isResponsive(responsiveMode: DiffResponsiveMode) {
  return (
    responsiveMode === 'FULL_RESPONSIVE' || responsiveMode === 'SHRINK_ONLY'
  );
}

export abstract class GrDiffBuilder {
  private readonly _diff: DiffInfo;

  private readonly _numLinesLeft: number;

  private readonly _prefs: DiffPreferencesInfo;

  protected readonly _renderPrefs?: RenderPreferences;

  protected readonly _outputEl: HTMLElement;

  readonly groups: GrDiffGroup[];

  private blameInfo: BlameInfo[] | null;

  private readonly _layerUpdateListener: (
    start: LineNumber,
    end: LineNumber,
    side: Side
  ) => void;

  constructor(
    diff: DiffInfo,
    prefs: DiffPreferencesInfo,
    outputEl: HTMLElement,
    readonly layers: DiffLayer[] = [],
    renderPrefs?: RenderPreferences
  ) {
    this._diff = diff;
    this._numLinesLeft = this._diff.content
      ? this._diff.content.reduce((sum, chunk) => {
          const left = chunk.a || chunk.ab;
          return sum + (left?.length || chunk.skip || 0);
        }, 0)
      : 0;
    this._prefs = prefs;
    this._renderPrefs = renderPrefs;
    this._outputEl = outputEl;
    this.groups = [];
    this.blameInfo = null;

    if (isNaN(prefs.tab_size) || prefs.tab_size <= 0) {
      throw Error('Invalid tab size from preferences.');
    }

    if (isNaN(prefs.line_length) || prefs.line_length <= 0) {
      throw Error('Invalid line length from preferences.');
    }

    this._layerUpdateListener = (
      start: LineNumber,
      end: LineNumber,
      side: Side
    ) => this._handleLayerUpdate(start, end, side);
    for (const layer of this.layers) {
      if (layer.addListener) {
        layer.addListener(this._layerUpdateListener);
      }
    }
  }

  clear() {
    for (const layer of this.layers) {
      if (layer.removeListener) {
        layer.removeListener(this._layerUpdateListener);
      }
    }
  }

  // TODO(TS): Convert to enum.
  static readonly GroupType = {
    ADDED: 'b',
    BOTH: 'ab',
    REMOVED: 'a',
  };

  // TODO(TS): Convert to enum.
  static readonly Highlights = {
    ADDED: 'edit_b',
    REMOVED: 'edit_a',
  };

  abstract addColumns(outputEl: HTMLElement, fontSize: number): void;

  abstract buildSectionElement(group: GrDiffGroup): HTMLElement;

  emitGroup(group: GrDiffGroup, beforeSection: HTMLElement | null) {
    const element = this.buildSectionElement(group);
    this._outputEl.insertBefore(element, beforeSection);
    group.element = element;
  }

  getGroupsByLineRange(
    startLine: LineNumber,
    endLine: LineNumber,
    side?: Side
  ) {
    const groups = [];
    for (let i = 0; i < this.groups.length; i++) {
      const group = this.groups[i];
      if (group.lines.length === 0) {
        continue;
      }
      let groupStartLine = 0;
      let groupEndLine = 0;
      if (side) {
        const range =
          side === Side.LEFT ? group.lineRange.left : group.lineRange.right;
        groupStartLine = range.start_line;
        groupEndLine = range.end_line;
      }

      if (groupStartLine === 0) {
        // Line was removed or added.
        groupStartLine = groupEndLine;
      }
      if (groupEndLine === 0) {
        // Line was removed or added.
        groupEndLine = groupStartLine;
      }
      if (startLine <= groupEndLine && endLine >= groupStartLine) {
        groups.push(group);
      }
    }
    return groups;
  }

  getContentTdByLine(
    lineNumber: LineNumber,
    side?: Side,
    root: Element = this._outputEl
  ): Element | null {
    const sideSelector: string = side ? `.${side}` : '';
    return root.querySelector(
      `td.lineNum[data-value="${lineNumber}"]${sideSelector} ~ td.content`
    );
  }

  getContentByLine(
    lineNumber: LineNumber,
    side?: Side,
    root?: HTMLElement
  ): HTMLElement | null {
    const td = this.getContentTdByLine(lineNumber, side, root);
    return td ? td.querySelector('.contentText') : null;
  }

  /**
   * Find line elements or line objects by a range of line numbers and a side.
   *
   * @param start The first line number
   * @param end The last line number
   * @param side The side of the range. Either 'left' or 'right'.
   * @param out_lines The output list of line objects. Use null if not desired.
   * @param out_elements The output list of line elements. Use null if not
   *        desired.
   */
  findLinesByRange(
    start: LineNumber,
    end: LineNumber,
    side: Side,
    out_lines: GrDiffLine[] | null,
    out_elements: HTMLElement[] | null
  ) {
    const groups = this.getGroupsByLineRange(start, end, side);
    for (const group of groups) {
      let content: HTMLElement | null = null;
      for (const line of group.lines) {
        if (
          (side === 'left' && line.type === GrDiffLineType.ADD) ||
          (side === 'right' && line.type === GrDiffLineType.REMOVE)
        ) {
          continue;
        }
        const lineNumber =
          side === 'left' ? line.beforeNumber : line.afterNumber;
        if (lineNumber < start || lineNumber > end) {
          continue;
        }

        if (out_lines) {
          out_lines.push(line);
        }
        if (out_elements) {
          if (content) {
            content = this._getNextContentOnSide(content, side);
          } else {
            content = this.getContentByLine(lineNumber, side, group.element);
          }
          if (content) {
            out_elements.push(content);
          }
        }
      }
    }
  }

  /**
   * Re-renders the DIV.contentText elements for the given side and range of
   * diff content.
   */
  _renderContentByRange(start: LineNumber, end: LineNumber, side: Side) {
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
      const lineNumberEl = this._getLineNumberEl(el, side);
      el.parentElement.replaceChild(
        this._createTextEl(lineNumberEl, line, side).firstChild!,
        el
      );
    }
  }

  getSectionsByLineRange(
    startLine: LineNumber,
    endLine: LineNumber,
    side: Side
  ) {
    return this.getGroupsByLineRange(startLine, endLine, side).map(
      group => group.element
    );
  }

  _createContextControls(
    section: HTMLElement,
    contextGroups: GrDiffGroup[],
    viewMode: DiffViewMode
  ) {
    const leftStart = contextGroups[0].lineRange.left.start_line;
    const leftEnd =
      contextGroups[contextGroups.length - 1].lineRange.left.end_line;
    const firstGroupIsSkipped = !!contextGroups[0].skip;
    const lastGroupIsSkipped = !!contextGroups[contextGroups.length - 1].skip;

    const containsWholeFile = this._numLinesLeft === leftEnd - leftStart + 1;
    const showAbove =
      (leftStart > 1 && !firstGroupIsSkipped) || containsWholeFile;
    const showBelow = leftEnd < this._numLinesLeft && !lastGroupIsSkipped;

    if (showAbove) {
      const paddingRow = this._createContextControlPaddingRow(viewMode);
      paddingRow.classList.add('above');
      section.appendChild(paddingRow);
    }
    section.appendChild(
      this._createContextControlRow(
        section,
        contextGroups,
        showAbove,
        showBelow,
        viewMode
      )
    );
    if (showBelow) {
      const paddingRow = this._createContextControlPaddingRow(viewMode);
      paddingRow.classList.add('below');
      section.appendChild(paddingRow);
    }
  }

  /**
   * Creates context controls. Buttons extend from the gap created by this
   * method up or down into the area of code that they affect.
   */
  _createContextControlRow(
    section: HTMLElement,
    contextGroups: GrDiffGroup[],
    showAbove: boolean,
    showBelow: boolean,
    viewMode: DiffViewMode
  ): HTMLElement {
    const row = this._createElement('tr', 'dividerRow');
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

    row.appendChild(this._createBlameCell(0));
    if (viewMode === DiffViewMode.SIDE_BY_SIDE) {
      row.appendChild(this._createElement('td'));
    }

    const cell = this._createElement('td', 'dividerCell');
    cell.setAttribute('colspan', '3');
    row.appendChild(cell);

    const contextControls = this._createElement(
      'gr-context-controls'
    ) as GrContextControls;
    contextControls.diff = this._diff;
    contextControls.renderPreferences = this._renderPrefs;
    contextControls.section = section;
    contextControls.contextGroups = contextGroups;
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
  _createContextControlPaddingRow(viewMode: DiffViewMode) {
    const row = this._createElement('tr', 'contextBackground');

    if (viewMode === DiffViewMode.SIDE_BY_SIDE) {
      row.classList.add('side-by-side');
      row.setAttribute('left-type', GrDiffGroupType.CONTEXT_CONTROL);
      row.setAttribute('right-type', GrDiffGroupType.CONTEXT_CONTROL);
    } else {
      row.classList.add('unified');
    }

    row.appendChild(this._createBlameCell(0));
    row.appendChild(this._createElement('td', 'contextLineNum'));
    if (viewMode === DiffViewMode.SIDE_BY_SIDE) {
      row.appendChild(this._createElement('td'));
    }
    row.appendChild(this._createElement('td', 'contextLineNum'));
    row.appendChild(this._createElement('td'));

    return row;
  }

  _createLineEl(
    line: GrDiffLine,
    number: LineNumber,
    type: GrDiffLineType,
    side: Side
  ) {
    const td = this._createElement('td');
    td.classList.add(side);
    if (line.type === GrDiffLineType.BLANK) {
      return td;
    }
    if (line.type === GrDiffLineType.BOTH || line.type === type) {
      td.classList.add('lineNum');
      td.dataset['value'] = number.toString();

      if (
        ((this._prefs.show_file_comment_button === false ||
          this._renderPrefs?.show_file_comment_button === false) &&
          number === 'FILE') ||
        number === 'LOST'
      ) {
        return td;
      }

      const button = this._createElement('button');
      td.appendChild(button);
      button.tabIndex = -1;
      button.classList.add('lineNumButton');
      button.classList.add(side);
      button.dataset['value'] = number.toString();
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
        }
      }
      this._addLineNumberMouseEvents(td, number, side);
    }
    return td;
  }

  _addLineNumberMouseEvents(el: HTMLElement, number: LineNumber, side: Side) {
    el.addEventListener('mouseenter', () => {
      fire(el, 'line-mouse-enter', {lineNum: number, side});
    });
    el.addEventListener('mouseleave', () => {
      fire(el, 'line-mouse-leave', {lineNum: number, side});
    });
  }

  _createTextEl(
    lineNumberEl: HTMLElement | null,
    line: GrDiffLine,
    side?: Side
  ) {
    const td = this._createElement('td');
    if (line.type !== GrDiffLineType.BLANK) {
      td.classList.add('content');
    }

    // If intraline info is not available, the entire line will be
    // considered as changed and marked as dark red / green color
    if (!line.hasIntralineInfo) {
      td.classList.add('no-intraline-info');
    }
    td.classList.add(line.type);

    const {beforeNumber, afterNumber} = line;
    if (beforeNumber !== 'FILE' && beforeNumber !== 'LOST') {
      const responsiveMode = getResponsiveMode(this._prefs, this._renderPrefs);
      const contentText = this._formatText(
        line.text,
        responsiveMode,
        this._prefs.tab_size,
        this._prefs.line_length
      );

      if (side) {
        contentText.setAttribute('data-side', side);
        const number = side === Side.LEFT ? beforeNumber : afterNumber;
        this._addLineNumberMouseEvents(td, number, side);
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
    } else if (line.beforeNumber === 'FILE') td.classList.add('file');
    else if (line.beforeNumber === 'LOST') td.classList.add('lost');

    return td;
  }

  private createLineBreak(responsive: boolean) {
    return responsive
      ? this._createElement('wbr')
      : this._createElement('span', 'br');
  }

  /**
   * Returns a 'div' element containing the supplied |text| as its innerText,
   * with '\t' characters expanded to a width determined by |tabSize|, and the
   * text wrapped at column |lineLimit|, which may be Infinity if no wrapping is
   * desired.
   *
   * @param text The text to be formatted.
   * @param tabSize The width of each tab stop.
   * @param lineLimit The column after which to wrap lines.
   */
  _formatText(
    text: string,
    responsiveMode: DiffResponsiveMode,
    tabSize: number,
    lineLimit: number
  ): HTMLElement {
    const contentText = this._createElement('div', 'contentText');
    contentText.ariaLabel = text;
    const responsive = isResponsive(responsiveMode);
    let columnPos = 0;
    let textOffset = 0;
    for (const segment of text.split(REGEX_TAB_OR_SURROGATE_PAIR)) {
      if (segment) {
        // |segment| contains only normal characters. If |segment| doesn't fit
        // entirely on the current line, append chunks of |segment| followed by
        // line breaks.
        let rowStart = 0;
        let rowEnd = lineLimit - columnPos;
        while (rowEnd < segment.length) {
          contentText.appendChild(
            document.createTextNode(segment.substring(rowStart, rowEnd))
          );
          contentText.appendChild(this.createLineBreak(responsive));
          columnPos = 0;
          rowStart = rowEnd;
          rowEnd += lineLimit;
        }
        // Append the last part of |segment|, which fits on the current line.
        contentText.appendChild(
          document.createTextNode(segment.substring(rowStart))
        );
        columnPos += segment.length - rowStart;
        textOffset += segment.length;
      }
      if (textOffset < text.length) {
        // Handle the special character at |textOffset|.
        if (text.startsWith('\t', textOffset)) {
          // Append a single '\t' character.
          let effectiveTabSize = tabSize - (columnPos % tabSize);
          if (columnPos + effectiveTabSize > lineLimit) {
            contentText.appendChild(this.createLineBreak(responsive));
            columnPos = 0;
            effectiveTabSize = tabSize;
          }
          contentText.appendChild(this._getTabWrapper(effectiveTabSize));
          columnPos += effectiveTabSize;
          textOffset++;
        } else {
          // Append a single surrogate pair.
          if (columnPos >= lineLimit) {
            contentText.appendChild(this.createLineBreak(responsive));
            columnPos = 0;
          }
          contentText.appendChild(
            document.createTextNode(text.substring(textOffset, textOffset + 2))
          );
          textOffset += 2;
          columnPos += 1;
        }
      }
    }
    return contentText;
  }

  /**
   * Returns a <span> element holding a '\t' character, that will visually
   * occupy |tabSize| many columns.
   *
   * @param tabSize The effective size of this tab stop.
   */
  _getTabWrapper(tabSize: number): HTMLElement {
    // Force this to be a number to prevent arbitrary injection.
    const result = this._createElement('span', 'tab');
    result.setAttribute(
      'style',
      `tab-size: ${tabSize}; -moz-tab-size: ${tabSize};`
    );
    result.innerText = '\t';
    return result;
  }

  _createElement(tagName: string, classStr?: string): HTMLElement {
    const el = document.createElement(tagName);
    // When Shady DOM is being used, these classes are added to account for
    // Polymer's polyfill behavior. In order to guarantee sufficient
    // specificity within the CSS rules, these are added to every element.
    // Since the Polymer DOM utility functions (which would do this
    // automatically) are not being used for performance reasons, this is
    // done manually.
    el.classList.add('style-scope', 'gr-diff');
    if (classStr) {
      for (const className of classStr.split(' ')) {
        el.classList.add(className);
      }
    }
    return el;
  }

  _handleLayerUpdate(start: LineNumber, end: LineNumber, side: Side) {
    this._renderContentByRange(start, end, side);
  }

  /**
   * Finds the next DIV.contentText element following the given element, and on
   * the same side. Will only search within a group.
   */
  abstract _getNextContentOnSide(
    content: HTMLElement,
    side: Side
  ): HTMLElement | null;

  /**
   * Gets configuration for creating move controls for chunks marked with
   * dueToMove
   */
  abstract _getMoveControlsConfig(): {
    numberOfCells: number;
    movedOutIndex: number;
    movedInIndex: number;
    lineNumberCols: number[];
  };

  /**
   * Determines whether the given group is either totally an addition or totally
   * a removal.
   */
  _isTotal(group: GrDiffGroup): boolean {
    return (
      group.type === GrDiffGroupType.DELTA &&
      (!group.adds.length || !group.removes.length) &&
      !(!group.adds.length && !group.removes.length)
    );
  }

  /**
   * Set the blame information for the diff. For any already-rendered line,
   * re-render its blame cell content.
   */
  setBlame(blame: BlameInfo[] | null) {
    this.blameInfo = blame;
    if (!blame) return;

    // TODO(wyatta): make this loop asynchronous.
    for (const commit of blame) {
      for (const range of commit.ranges) {
        for (let i = range.start; i <= range.end; i++) {
          // TODO(wyatta): this query is expensive, but, when traversing a
          // range, the lines are consecutive, and given the previous blame
          // cell, the next one can be reached cheaply.
          const el = this._getBlameByLineNum(i);
          if (!el) {
            continue;
          }
          // Remove the element's children (if any).
          while (el.hasChildNodes()) {
            el.removeChild(el.lastChild!);
          }
          const blame = this._getBlameForBaseLine(i, commit);
          if (blame) el.appendChild(blame);
        }
      }
    }
  }

  _createMovedLineAnchor(line: number, side: Side) {
    const anchor = this._createElementWithText('a', `${line}`);

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

  _createElementWithText(tagName: string, textContent: string) {
    const element = this._createElement(tagName);
    element.textContent = textContent;
    return element;
  }

  _createMoveDescriptionDiv(movedIn: boolean, group: GrDiffGroup) {
    const div = this._createElement('div');
    if (group.moveDetails?.range) {
      const {changed, range} = group.moveDetails;
      const otherSide = movedIn ? Side.LEFT : Side.RIGHT;
      const andChangedLabel = changed ? 'and changed ' : '';
      const direction = movedIn ? 'from' : 'to';
      const textLabel = `Moved ${andChangedLabel}${direction} lines `;
      div.appendChild(this._createElementWithText('span', textLabel));
      div.appendChild(this._createMovedLineAnchor(range.start, otherSide));
      div.appendChild(this._createElementWithText('span', ' - '));
      div.appendChild(this._createMovedLineAnchor(range.end, otherSide));
    } else {
      div.appendChild(
        this._createElementWithText('span', movedIn ? 'Moved in' : 'Moved out')
      );
    }
    return div;
  }

  _buildMoveControls(group: GrDiffGroup) {
    const movedIn = group.adds.length > 0;
    const {numberOfCells, movedOutIndex, movedInIndex, lineNumberCols} =
      this._getMoveControlsConfig();

    let controlsClass;
    let descriptionIndex;
    const descriptionTextDiv = this._createMoveDescriptionDiv(movedIn, group);
    if (movedIn) {
      controlsClass = 'movedIn';
      descriptionIndex = movedInIndex;
    } else {
      controlsClass = 'movedOut';
      descriptionIndex = movedOutIndex;
    }

    const controls = this._createElement('tr', `moveControls ${controlsClass}`);
    const cells = [...Array(numberOfCells).keys()].map(() =>
      this._createElement('td')
    );
    lineNumberCols.forEach(index => {
      cells[index].classList.add('moveControlsLineNumCol');
    });

    const moveRangeHeader = this._createElement('gr-range-header');
    moveRangeHeader.setAttribute('icon', 'gr-icons:move-item');
    moveRangeHeader.appendChild(descriptionTextDiv);
    cells[descriptionIndex].classList.add('moveHeader');
    cells[descriptionIndex].appendChild(moveRangeHeader);
    cells.forEach(c => {
      controls.appendChild(c);
    });
    return controls;
  }

  /**
   * Find the blame cell for a given line number.
   */
  _getBlameByLineNum(lineNum: number): Element | null {
    return this._outputEl.querySelector(
      `td.blame[data-line-number="${lineNum}"]`
    );
  }

  /**
   * Given a base line number, return the commit containing that line in the
   * current set of blame information. If no blame information has been
   * provided, null is returned.
   *
   * @return The commit information.
   */
  _getBlameCommitForBaseLine(lineNum: LineNumber) {
    if (!this.blameInfo) {
      return null;
    }

    for (const blameCommit of this.blameInfo) {
      for (const range of blameCommit.ranges) {
        if (range.start <= lineNum && range.end >= lineNum) {
          return blameCommit;
        }
      }
    }
    return null;
  }

  /**
   * Given the number of a base line, get the content for the blame cell of that
   * line. If there is no blame information for that line, returns null.
   *
   * @param commit Optionally provide the commit object, so that
   *     it does not need to be searched.
   */
  _getBlameForBaseLine(
    lineNum: LineNumber,
    commit: BlameInfo | null = this._getBlameCommitForBaseLine(lineNum)
  ): HTMLElement | null {
    if (!commit) {
      return null;
    }

    const isStartOfRange = commit.ranges.some(r => r.start === lineNum);

    const date = new Date(commit.time * 1000).toLocaleDateString();
    const blameNode = this._createElement(
      'span',
      isStartOfRange ? 'startOfRange' : ''
    );

    const shaNode = this._createElement('a', 'blameDate');
    shaNode.innerText = `${date}`;
    shaNode.setAttribute('href', `${getBaseUrl()}/q/${commit.id}`);
    blameNode.appendChild(shaNode);

    const shortName = commit.author.split(' ')[0];
    const authorNode = this._createElement('span', 'blameAuthor');
    authorNode.innerText = ` ${shortName}`;
    blameNode.appendChild(authorNode);

    const hoverCardFragment = this._createElement('span', 'blameHoverCard');
    hoverCardFragment.innerText = `Commit ${commit.id}
Author: ${commit.author}
Date: ${date}

${commit.commit_msg}`;
    const hovercard = this._createElement('gr-hovercard');
    hovercard.appendChild(hoverCardFragment);
    blameNode.appendChild(hovercard);

    return blameNode;
  }

  /**
   * Create a blame cell for the given base line. Blame information will be
   * included in the cell if available.
   */
  _createBlameCell(lineNumber: LineNumber): HTMLTableDataCellElement {
    const blameTd = this._createElement(
      'td',
      'blame'
    ) as HTMLTableDataCellElement;
    blameTd.setAttribute('data-line-number', lineNumber.toString());
    if (lineNumber) {
      const content = this._getBlameForBaseLine(lineNumber);
      if (content) {
        blameTd.appendChild(content);
      }
    }
    return blameTd;
  }

  /**
   * Finds the line number element given the content element by walking up the
   * DOM tree to the diff row and then querying for a .lineNum element on the
   * requested side.
   *
   * TODO(brohlfs): Consolidate this with getLineEl... methods in html file.
   */
  _getLineNumberEl(content: HTMLElement, side: Side): HTMLElement | null {
    let row: HTMLElement | null = content;
    while (row && !row.classList.contains('diff-row')) row = row.parentElement;
    return row ? (row.querySelector('.lineNum.' + side) as HTMLElement) : null;
  }

  updateRenderPrefs(_renderPrefs: RenderPreferences) {}
}
