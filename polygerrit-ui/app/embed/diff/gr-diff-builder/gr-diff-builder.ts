/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {
  ContentLoadNeededEventDetail,
  DiffContextExpandedExternalDetail,
  RenderPreferences,
} from '../../../api/diff';
import {GrDiffLine, GrDiffLineType, LineNumber} from '../gr-diff/gr-diff-line';
import {GrDiffGroup} from '../gr-diff/gr-diff-group';
import {assert} from '../../../utils/common-util';
import '../gr-context-controls/gr-context-controls';
import {BlameInfo} from '../../../types/common';
import {DiffInfo, DiffPreferencesInfo} from '../../../types/diff';
import {Side} from '../../../constants/constants';
import {DiffLayer} from '../../../types/types';

export interface DiffContextExpandedEventDetail
  extends DiffContextExpandedExternalDetail {
  /** The context control group that should be replaced by `groups`. */
  contextGroup: GrDiffGroup;
  groups: GrDiffGroup[];
  numLines: number;
}

declare global {
  interface HTMLElementEventMap {
    'diff-context-expanded': CustomEvent<DiffContextExpandedEventDetail>;
    'content-load-needed': CustomEvent<ContentLoadNeededEventDetail>;
  }
}

/**
 * Given that GrDiffBuilder has ~1,000 lines of code, this interface is just
 * making refactorings easier by emphasizing what the public facing "contract"
 * of this class is. There are no plans for adding separate implementations.
 */
export interface DiffBuilder {
  init(): void;
  cleanup(): void;
  addGroups(groups: readonly GrDiffGroup[]): void;
  clearGroups(): void;
  replaceGroup(
    contextControl: GrDiffGroup,
    groups: readonly GrDiffGroup[]
  ): void;
  findGroup(side: Side, line: LineNumber): GrDiffGroup | undefined;
  addColumns(outputEl: HTMLElement, fontSize: number): void;
  // TODO: Change `null` to `undefined`.
  getContentTdByLine(
    lineNumber: LineNumber,
    side?: Side,
    root?: Element
  ): HTMLTableCellElement | null;
  getLineElByNumber(
    lineNumber: LineNumber,
    side?: Side
  ): HTMLTableCellElement | null;
  getLineNumberRows(): HTMLTableRowElement[];
  getLineNumEls(side: Side): HTMLTableCellElement[];
  setBlame(blame: BlameInfo[]): void;
  updateRenderPrefs(renderPrefs: RenderPreferences): void;
}

export interface DiffBuilderImage {
  renderImageDiff(): void;
}

/**
 * Base class for different diff builders, like side-by-side, unified etc.
 *
 * The builder takes GrDiffGroups, and builds the corresponding DOM elements,
 * called sections. Only the builder should add or remove sections from the
 * DOM. Callers can use the ...group() methods to modify groups and thus cause
 * rendering changes.
 *
 * TODO: Do not subclass `GrDiffBuilder`. Use composition and interfaces.
 */
export abstract class GrDiffBuilder implements DiffBuilder {
  protected readonly _diff: DiffInfo;

  protected readonly numLinesLeft: number;

  // visible for testing
  readonly _prefs: DiffPreferencesInfo;

  protected renderPrefs?: RenderPreferences;

  protected readonly outputEl: HTMLElement;

  protected groups: GrDiffGroup[];

  private blameInfo: BlameInfo[] = [];

  private readonly layerUpdateListener: (
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
    this.numLinesLeft = this._diff.content
      ? this._diff.content.reduce((sum, chunk) => {
          const left = chunk.a || chunk.ab;
          return sum + (left?.length || chunk.skip || 0);
        }, 0)
      : 0;
    this._prefs = prefs;
    this.renderPrefs = renderPrefs;
    this.outputEl = outputEl;
    this.groups = [];

    if (isNaN(prefs.tab_size) || prefs.tab_size <= 0) {
      throw Error('Invalid tab size from preferences.');
    }

    if (isNaN(prefs.line_length) || prefs.line_length <= 0) {
      throw Error('Invalid line length from preferences.');
    }

    this.layerUpdateListener = (
      start: LineNumber,
      end: LineNumber,
      side: Side
    ) => this.renderContentByRange(start, end, side);
    this.init();
  }

  /**
   * This is meant to be called when the gr-diff component re-connects, or when
   * the diff is (re-)rendered.
   *
   * Make sure that this method is symmetric with cleanup(), which is called
   * when gr-diff disconnects.
   */
  init() {
    this.cleanup();
    for (const layer of this.layers) {
      if (layer.addListener) {
        layer.addListener(this.layerUpdateListener);
      }
    }
  }

  /**
   * This is meant to be called when the gr-diff component disconnects, or when
   * the diff is (re-)rendered.
   *
   * Make sure that this method is symmetric with init(), which is called when
   * gr-diff re-connects.
   */
  cleanup() {
    for (const layer of this.layers) {
      if (layer.removeListener) {
        layer.removeListener(this.layerUpdateListener);
      }
    }
  }

  abstract addColumns(outputEl: HTMLElement, fontSize: number): void;

  protected abstract buildSectionElement(group: GrDiffGroup): HTMLElement;

  addGroups(groups: readonly GrDiffGroup[]) {
    for (const group of groups) {
      this.groups.push(group);
      this.emitGroup(group);
    }
  }

  clearGroups() {
    for (const deletedGroup of this.groups) {
      deletedGroup.element?.remove();
    }
    this.groups = [];
  }

  replaceGroup(contextControl: GrDiffGroup, groups: readonly GrDiffGroup[]) {
    const i = this.groups.indexOf(contextControl);
    if (i === -1) throw new Error('cannot find context control group');

    const contextControlSection = this.groups[i].element;
    if (!contextControlSection) throw new Error('diff group element not set');

    this.groups.splice(i, 1, ...groups);
    for (const group of groups) {
      this.emitGroup(group, contextControlSection);
    }
    if (contextControlSection) contextControlSection.remove();
  }

  findGroup(side: Side, line: LineNumber) {
    return this.groups.find(group => group.containsLine(side, line));
  }

  private emitGroup(group: GrDiffGroup, beforeSection?: HTMLElement) {
    const element = this.buildSectionElement(group);
    this.outputEl.insertBefore(element, beforeSection ?? null);
    group.element = element;
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

  // TODO: Change `null` to `undefined`.
  abstract getContentTdByLine(
    lineNumber: LineNumber,
    side?: Side,
    root?: Element
  ): HTMLTableCellElement | null;

  // TODO: Change `null` to `undefined`.
  abstract getLineElByNumber(
    lineNumber: LineNumber,
    side?: Side
  ): HTMLTableCellElement | null;

  abstract getLineNumberRows(): HTMLTableRowElement[];

  abstract getLineNumEls(side: Side): HTMLTableCellElement[];

  protected abstract getBlameTdByLine(lineNum: number): Element | undefined;

  // TODO: Change `null` to `undefined`.
  protected abstract getContentByLine(
    lineNumber: LineNumber,
    side?: Side,
    root?: HTMLElement
  ): HTMLElement | null;

  /**
   * Find line elements or line objects by a range of line numbers and a side.
   *
   * @param start The first line number
   * @param end The last line number
   * @param side The side of the range. Either 'left' or 'right'.
   * @param out_lines The output list of line objects.
   *        TODO: Change to camelCase.
   * @param out_elements The output list of line elements.
   *        TODO: Change to camelCase.
   */
  // visible for testing
  findLinesByRange(
    start: LineNumber,
    end: LineNumber,
    side: Side,
    out_lines: GrDiffLine[],
    out_elements: HTMLElement[]
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

        if (content) {
          content = this.getNextContentOnSide(content, side);
        } else {
          content = this.getContentByLine(lineNumber, side, group.element);
        }
        if (content) {
          // out_lines and out_elements must match. So if we don't have an
          // element to push, then also don't push a line.
          out_lines.push(line);
          out_elements.push(content);
        }
      }
    }
    assert(
      out_lines.length === out_elements.length,
      'findLinesByRange: lines and elements arrays must have same length'
    );
  }

  protected abstract renderContentByRange(
    start: LineNumber,
    end: LineNumber,
    side: Side
  ): void;

  protected abstract renderBlameByRange(
    blame: BlameInfo,
    start: number,
    end: number
  ): void;

  /**
   * Finds the next DIV.contentText element following the given element, and on
   * the same side. Will only search within a group.
   *
   * TODO: Change `null` to `undefined`.
   */
  protected abstract getNextContentOnSide(
    content: HTMLElement,
    side: Side
  ): HTMLElement | null;

  /**
   * Gets configuration for creating move controls for chunks marked with
   * dueToMove
   */
  protected abstract getMoveControlsConfig(): {
    numberOfCells: number;
    movedOutIndex: number;
    movedInIndex: number;
    lineNumberCols: number[];
    signCols?: {left: number; right: number};
  };

  /**
   * Set the blame information for the diff. For any already-rendered line,
   * re-render its blame cell content.
   */
  setBlame(blame: BlameInfo[]) {
    this.blameInfo = blame;
    for (const commit of blame) {
      for (const range of commit.ranges) {
        this.renderBlameByRange(commit, range.start, range.end);
      }
    }
  }

  /**
   * Given a base line number, return the commit containing that line in the
   * current set of blame information. If no blame information has been
   * provided, null is returned.
   *
   * @return The commit information.
   */
  // visible for testing
  getBlameCommitForBaseLine(lineNum: LineNumber): BlameInfo | undefined {
    for (const blameCommit of this.blameInfo) {
      for (const range of blameCommit.ranges) {
        if (range.start <= lineNum && range.end >= lineNum) {
          return blameCommit;
        }
      }
    }
    return undefined;
  }

  /**
   * Only special builders need to implement this. The default is to
   * just ignore it.
   */
  updateRenderPrefs(_: RenderPreferences) {}
}
