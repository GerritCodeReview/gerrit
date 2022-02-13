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
  RenderPreferences,
} from '../../../api/diff';
import {GrDiffLine, GrDiffLineType, LineNumber} from '../gr-diff/gr-diff-line';
import {GrDiffGroup, GrDiffGroupType} from '../gr-diff/gr-diff-group';

import '../gr-context-controls/gr-context-controls';
import {BlameInfo} from '../../../types/common';
import {DiffInfo, DiffPreferencesInfo} from '../../../types/diff';
import {Side} from '../../../constants/constants';
import {DiffLayer} from '../../../types/types';

export interface DiffContextExpandedEventDetail
  extends DiffContextExpandedExternalDetail {
  groups: GrDiffGroup[];
  section?: HTMLElement;
  numLines: number;
}

declare global {
  interface HTMLElementEventMap {
    'diff-context-expanded': CustomEvent<DiffContextExpandedEventDetail>;
    'content-load-needed': CustomEvent<ContentLoadNeededEventDetail>;
  }
}

export interface DiffBuilder {
  clear(): void;
  spliceGroups(
    start: number,
    deleteCount: number,
    ...addedGroups: GrDiffGroup[]
  ): GrDiffGroup[];
  getIndexOfSection(sectionEl?: HTMLElement, group?: GrDiffGroup): number;
  addColumns(outputEl: HTMLElement, fontSize: number): void;
  getContentTdByLine(
    lineNumber: LineNumber,
    side?: Side,
    root?: Element
  ): Element | null;
  getLineElByNumber(lineNumber: LineNumber, side: Side): HTMLElement | null;
  setBlame(blame: BlameInfo[] | null): void;
  updateRenderPrefs(renderPrefs: RenderPreferences): void;
}

/**
 * Base class for different diff builders, like side-by-side, unified etc.
 *
 * The builder takes GrDiffGroups, and builds the corresponding DOM elements,
 * called sections. Only the builder should add or remove sections from the
 * DOM. Callers can use the spliceGroups method to add groups that
 * will then be rendered - or remove groups whose sections will then be
 * removed from the DOM.
 */
export abstract class GrDiffBuilder implements DiffBuilder {
  protected readonly _diff: DiffInfo;

  protected readonly numLinesLeft: number;

  protected readonly _prefs: DiffPreferencesInfo;

  protected readonly renderPrefs?: RenderPreferences;

  protected readonly outputEl: HTMLElement;

  protected readonly groups: GrDiffGroup[];

  private blameInfo: BlameInfo[] | null;

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
    this.blameInfo = null;

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
    for (const layer of this.layers) {
      if (layer.addListener) {
        layer.addListener(this.layerUpdateListener);
      }
    }
  }

  clear() {
    for (const layer of this.layers) {
      if (layer.removeListener) {
        layer.removeListener(this.layerUpdateListener);
      }
    }
  }

  abstract addColumns(outputEl: HTMLElement, fontSize: number): void;

  protected abstract buildSectionElement(group: GrDiffGroup): HTMLElement;

  getIndexOfSection(sectionEl?: HTMLElement, group?: GrDiffGroup) {
    if (sectionEl) {
      return this.groups.findIndex(g => g.element === sectionEl);
    }
    if (group) {
      return this.groups.findIndex(
        g =>
          g.type === GrDiffGroupType.CONTEXT_CONTROL &&
          g.contextGroups[0] === group
      );
    }
    throw new Error('section or group must be provided');
  }

  spliceGroups(
    start: number,
    deleteCount: number,
    ...addedGroups: GrDiffGroup[]
  ) {
    const sectionBeforeWhichToInsert =
      start < this.groups.length ? this.groups[start].element ?? null : null;
    // Update the groups array
    const deletedGroups = this.groups.splice(
      start,
      deleteCount,
      ...addedGroups
    );

    // Add new sections for the new groups
    for (const addedGroup of addedGroups) {
      this.emitGroup(addedGroup, sectionBeforeWhichToInsert);
    }
    // Remove sections corresponding to deleted groups from the DOM
    for (const deletedGroup of deletedGroups) {
      const section = deletedGroup.element;
      section?.parentNode?.removeChild(section);
    }
    return deletedGroups;
  }

  private emitGroup(group: GrDiffGroup, beforeSection: HTMLElement | null) {
    const element = this.buildSectionElement(group);
    this.outputEl.insertBefore(element, beforeSection);
    group.element = element;
  }

  protected getGroupsByLineRange(
    startLine: LineNumber,
    endLine: LineNumber,
    side: Side
  ) {
    const startIndex = this.groups.findIndex(group =>
      group.containsLine(side, startLine)
    );
    const endIndex = this.groups.findIndex(group =>
      group.containsLine(side, endLine)
    );
    // The filter preserves the legacy behavior to only return non-context
    // groups
    return this.groups
      .slice(startIndex, endIndex + 1)
      .filter(group => group.lines.length > 0);
  }

  abstract getContentTdByLine(
    lineNumber: LineNumber,
    side?: Side,
    root?: Element
  ): Element | null;

  abstract getLineElByNumber(
    lineNumber: LineNumber,
    side: Side
  ): HTMLElement | null;

  protected abstract getBlameTdByLine(lineNum: number): Element | undefined;

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
   * @param out_lines The output list of line objects. Use null if not desired.
   * @param out_elements The output list of line elements. Use null if not
   *        desired.
   */
  protected findLinesByRange(
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
          (side === Side.LEFT && line.type === GrDiffLineType.ADD) ||
          (side === Side.RIGHT && line.type === GrDiffLineType.REMOVE)
        ) {
          continue;
        }
        const lineNumber = line.lineNumber(side);
        if (lineNumber < start || lineNumber > end) {
          continue;
        }

        if (out_lines) {
          out_lines.push(line);
        }
        if (out_elements) {
          if (content) {
            content = this.getNextContentOnSide(content, side);
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
  protected abstract renderContentByRange(
    start: LineNumber,
    end: LineNumber,
    side: Side
  ): void;

  /**
   * Re-renders the blame elements for the given range of the diff.
   */
  protected abstract renderBlameByRange(
    blame: BlameInfo,
    start: number,
    end: number
  ): void;

  /**
   * Finds the next DIV.contentText element following the given element, and on
   * the same side. Will only search within a group.
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
  };

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
  protected getBlameCommitForBaseLine(lineNum: LineNumber) {
    if (!this.blameInfo) return undefined;

    for (const blameCommit of this.blameInfo) {
      for (const range of blameCommit.ranges) {
        if (range.start <= lineNum && range.end >= lineNum) {
          return blameCommit;
        }
      }
    }
    return undefined;
  }

  updateRenderPrefs(_: RenderPreferences) {}
}
