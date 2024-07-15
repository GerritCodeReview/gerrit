/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {GrDiffLine, Highlights} from '../gr-diff/gr-diff-line';
import {
  GrDiffGroup,
  GrDiffGroupType,
  hideInContextControl,
} from '../gr-diff/gr-diff-group';
import {DiffContent, DiffRangesToFocus} from '../../../types/diff';
import {Side} from '../../../constants/constants';
import {getStringLength} from '../gr-diff-highlight/gr-annotation';
import {GrDiffLineType, LineNumber} from '../../../api/diff';
import {FULL_CONTEXT, KeyLocations} from '../gr-diff/gr-diff-utils';

// visible for testing
export interface State {
  lineNums: {
    left: number;
    right: number;
  };
  chunkIndex: number;
}

interface ChunkEnd {
  offset: number;
  keyLocation: boolean;
}

/** Interface for listening to the output of the processor. */
export interface GroupConsumer {
  addGroup(group: GrDiffGroup): void;
  clearGroups(): void;
}

/** Interface for listening to the output of the processor. */
export interface ProcessingOptions {
  context: number;
  keyLocations?: KeyLocations;
  asyncThreshold?: number;
  isBinary?: boolean;
  diffRangesToFocus?: DiffRangesToFocus;
}

/**
 * Converts the API's `DiffContent`s  to `GrDiffGroup`s for rendering.
 *
 * Glossary:
 * - "chunk": A single `DiffContent` as returned by the API.
 * - "group": A single `GrDiffGroup` as used for rendering.
 * - "common" chunk/group: A chunk/group that should be considered unchanged
 *   for diffing purposes. This can mean its either actually unchanged, or it
 *   has only whitespace changes.
 * - "key location": A line number and side of the diff that should not be
 *   collapsed e.g. because a comment is attached to it, or because it was
 *   provided in the URL and thus should be visible
 * - "uncollapsible" chunk/group: A chunk/group that is either not "common",
 *   or cannot be collapsed because it contains a key location
 *
 * Here a a number of tasks this processor performs:
 *  - splitting large chunks to allow more granular async rendering
 *  - adding a group for the "File" pseudo line that file-level comments can
 *    be attached to
 *  - replacing common parts of the diff that are outside the user's
 *    context setting and do not have comments with a group representing the
 *    "expand context" widget. This may require splitting a chunk/group so
 *    that the part that is within the context or has comments is shown, while
 *    the rest is not.
 */
export class GrDiffProcessor {
  // visible for testing
  context: number;

  // visible for testing
  keyLocations: KeyLocations;

  private isBinary = false;

  private groups: GrDiffGroup[] = [];

  // visible for testing
  diffRangesToFocus?: DiffRangesToFocus;

  constructor(options: ProcessingOptions) {
    this.context = options.context;
    this.keyLocations = options.keyLocations ?? {left: {}, right: {}};
    this.isBinary = options.isBinary ?? false;
    this.diffRangesToFocus = options.diffRangesToFocus;
  }

  /**
   * Process the diff chunks into GrDiffGroups.
   *
   * @return an array of GrDiffGroups
   */
  process(chunks: DiffContent[]): GrDiffGroup[] {
    this.groups = [];
    this.groups.push(this.makeGroup('LOST'));
    this.groups.push(this.makeGroup('FILE'));

    this.processChunks(chunks);
    return this.groups;
  }

  processChunks(chunks: DiffContent[]) {
    if (this.isBinary) return;

    const state = {
      lineNums: {left: 0, right: 0},
      chunkIndex: 0,
    };
    chunks = this.splitCommonChunksWithKeyLocations(chunks);

    while (state.chunkIndex < chunks.length) {
      const stateUpdate = this.processNext(state, chunks);
      for (const group of stateUpdate.groups) {
        this.groups.push(group);
      }
      state.lineNums.left += stateUpdate.lineDelta.left;
      state.lineNums.right += stateUpdate.lineDelta.right;
      state.chunkIndex = stateUpdate.newChunkIndex;
    }
  }

  /**
   * Process the next uncollapsible chunk, or the next collapsible chunks.
   */
  // visible for testing
  processNext(state: State, chunks: DiffContent[]) {
    const firstUncollapsibleChunkIndex = this.firstUncollapsibleChunkIndex(
      chunks,
      state
    );
    if (firstUncollapsibleChunkIndex === state.chunkIndex) {
      const chunk = chunks[state.chunkIndex];
      return {
        lineDelta: {
          left: this.linesLeft(chunk).length,
          right: this.linesRight(chunk).length,
        },
        groups: [
          this.chunkToGroup(
            chunk,
            state.lineNums.left + 1,
            state.lineNums.right + 1
          ),
        ],
        newChunkIndex: state.chunkIndex + 1,
      };
    }

    return this.processCollapsibleChunks(
      state,
      chunks,
      firstUncollapsibleChunkIndex
    );
  }

  private linesLeft(chunk: DiffContent) {
    return chunk.ab || chunk.a || [];
  }

  private linesRight(chunk: DiffContent) {
    return chunk.ab || chunk.b || [];
  }

  private firstUncollapsibleChunkIndex(chunks: DiffContent[], state: State) {
    let chunkIndex = state.chunkIndex;
    let offsetLeft = state.lineNums.left;
    let offsetRight = state.lineNums.right;
    while (
      chunkIndex < chunks.length &&
      this.isCollapsibleChunk(chunks[chunkIndex], offsetLeft, offsetRight)
    ) {
      offsetLeft += this.chunkLength(chunks[chunkIndex], Side.LEFT);
      offsetRight += this.chunkLength(chunks[chunkIndex], Side.RIGHT);
      chunkIndex++;
    }
    return chunkIndex;
  }

  private isCollapsibleChunk(
    chunk: DiffContent,
    offsetLeft: number,
    offsetRight: number
  ) {
    return (
      (chunk.ab ||
        chunk.common ||
        chunk.skip ||
        this.isChunkOutsideOfFocusRange(chunk, offsetLeft, offsetRight)) &&
      !chunk.keyLocation
    );
  }

  private isChunkOutsideOfFocusRange(
    chunk: DiffContent,
    offsetLeft: number,
    offsetRight: number
  ) {
    if (!this.diffRangesToFocus) {
      return false;
    }
    const leftLineCount = this.linesLeft(chunk).length;
    const rightLineCount = this.linesRight(chunk).length;
    const hasLeftSideOverlap = this.diffRangesToFocus.left.some(range =>
      this.hasAnyOverlap(
        {start: offsetLeft, end: offsetLeft + leftLineCount},
        range
      )
    );
    const hasRightSideOverlap = this.diffRangesToFocus.right.some(range =>
      this.hasAnyOverlap(
        {start: offsetRight, end: offsetRight + rightLineCount},
        range
      )
    );
    return !hasLeftSideOverlap && !hasRightSideOverlap;
  }

  private hasAnyOverlap(
    firstRange: {start: number; end: number},
    secondRange: {start: number; end: number}
  ) {
    const startOverlap = Math.max(firstRange.start, secondRange.start);
    const endOverlap = Math.min(firstRange.end, secondRange.end);
    return startOverlap <= endOverlap;
  }

  /**
   * Process a stretch of collapsible chunks.
   *
   * Outputs up to three groups:
   * 1) Visible context before the hidden common code, unless it's the
   * very beginning of the file.
   * 2) Context hidden behind a context bar, unless empty.
   * 3) Visible context after the hidden common code, unless it's the very
   * end of the file.
   */
  private processCollapsibleChunks(
    state: State,
    chunks: DiffContent[],
    firstUncollapsibleChunkIndex: number
  ) {
    const collapsibleChunks = chunks.slice(
      state.chunkIndex,
      firstUncollapsibleChunkIndex
    );
    const leftLineCount = collapsibleChunks.reduce(
      (sum, chunk) => sum + this.chunkLength(chunk, Side.LEFT),
      0
    );
    const rightLineCount = collapsibleChunks.reduce(
      (sum, chunk) => sum + this.chunkLength(chunk, Side.RIGHT),
      0
    );

    let groups = this.chunksToGroups(
      collapsibleChunks,
      state.lineNums.left + 1,
      state.lineNums.right + 1
    );

    const hasSkippedGroup = !!groups.find(g => g.skip);
    const hasNonCommonDeltaGroup = !!groups.find(
      g => g.type === GrDiffGroupType.DELTA && !g.ignoredWhitespaceOnly
    );
    if (
      this.context !== FULL_CONTEXT ||
      hasSkippedGroup ||
      hasNonCommonDeltaGroup
    ) {
      const contextNumLines = this.context > 0 ? this.context : 0;
      const hiddenStart = state.chunkIndex === 0 ? 0 : contextNumLines;
      const hiddenEndLeft =
        leftLineCount -
        (firstUncollapsibleChunkIndex === chunks.length ? 0 : this.context);
      const hiddenEndRight =
        rightLineCount -
        (firstUncollapsibleChunkIndex === chunks.length ? 0 : this.context);
      groups = hideInContextControl(
        groups,
        hiddenStart,
        hiddenEndLeft,
        hiddenEndRight
      );
    }

    return {
      lineDelta: {
        left: leftLineCount,
        right: rightLineCount,
      },
      groups,
      newChunkIndex: firstUncollapsibleChunkIndex,
    };
  }

  private chunkLength(chunk: DiffContent, side: Side) {
    if (chunk.skip || chunk.common || chunk.ab) {
      return this.commonChunkLength(chunk);
    } else if (side === Side.LEFT) {
      return this.linesLeft(chunk).length;
    } else {
      return this.linesRight(chunk).length;
    }
  }

  private commonChunkLength(chunk: DiffContent) {
    if (chunk.skip) {
      return chunk.skip;
    }
    console.assert(!!chunk.ab || !!chunk.common);

    console.assert(
      !chunk.a || (!!chunk.b && chunk.a.length === chunk.b.length),
      'common chunk needs same number of a and b lines: ',
      chunk
    );
    return this.linesLeft(chunk).length;
  }

  private chunksToGroups(
    chunks: DiffContent[],
    offsetLeft: number,
    offsetRight: number
  ): GrDiffGroup[] {
    return chunks.map(chunk => {
      const group = this.chunkToGroup(chunk, offsetLeft, offsetRight);
      offsetLeft += this.chunkLength(chunk, Side.LEFT);
      offsetRight += this.chunkLength(chunk, Side.RIGHT);
      return group;
    });
  }

  private chunkToGroup(
    chunk: DiffContent,
    offsetLeft: number,
    offsetRight: number
  ): GrDiffGroup {
    const type =
      chunk.ab || chunk.skip ? GrDiffGroupType.BOTH : GrDiffGroupType.DELTA;
    const lines = this.linesFromChunk(chunk, offsetLeft, offsetRight);
    const options = {
      moveDetails: chunk.move_details,
      dueToRebase: !!chunk.due_to_rebase,
      ignoredWhitespaceOnly: !!chunk.common,
      keyLocation: !!chunk.keyLocation,
    };
    if (chunk.skip !== undefined) {
      return new GrDiffGroup({
        type,
        skip: chunk.skip,
        offsetLeft,
        offsetRight,
        ...options,
      });
    } else {
      return new GrDiffGroup({
        type,
        lines,
        ...options,
      });
    }
  }

  private linesFromChunk(
    chunk: DiffContent,
    offsetLeft: number,
    offsetRight: number
  ) {
    if (chunk.ab) {
      return chunk.ab.map((row, i) =>
        this.lineFromRow(GrDiffLineType.BOTH, offsetLeft, offsetRight, row, i)
      );
    }
    let lines: GrDiffLine[] = [];
    if (chunk.a) {
      // Avoiding a.push(...b) because that causes callstack overflows for
      // large b, which can occur when large files are added removed.
      lines = lines.concat(
        this.linesFromRows(
          GrDiffLineType.REMOVE,
          chunk.a,
          offsetLeft,
          chunk.edit_a
        )
      );
    }
    if (chunk.b) {
      // Avoiding a.push(...b) because that causes callstack overflows for
      // large b, which can occur when large files are added removed.
      lines = lines.concat(
        this.linesFromRows(
          GrDiffLineType.ADD,
          chunk.b,
          offsetRight,
          chunk.edit_b
        )
      );
    }
    return lines;
  }

  // visible for testing
  linesFromRows(
    lineType: GrDiffLineType,
    rows: string[],
    offset: number,
    intralineInfos?: number[][]
  ): GrDiffLine[] {
    const grDiffHighlights = intralineInfos
      ? this.convertIntralineInfos(rows, intralineInfos)
      : undefined;
    return rows.map((row, i) =>
      this.lineFromRow(lineType, offset, offset, row, i, grDiffHighlights)
    );
  }

  private lineFromRow(
    type: GrDiffLineType,
    offsetLeft: number,
    offsetRight: number,
    row: string,
    i: number,
    highlights?: Highlights[]
  ): GrDiffLine {
    const line = new GrDiffLine(type);
    line.text = row;
    if (type !== GrDiffLineType.ADD) line.beforeNumber = offsetLeft + i;
    if (type !== GrDiffLineType.REMOVE) line.afterNumber = offsetRight + i;
    if (highlights) {
      line.hasIntralineInfo = true;
      line.highlights = highlights.filter(hl => hl.contentIndex === i);
    } else {
      line.hasIntralineInfo = false;
    }
    return line;
  }

  private makeGroup(number: LineNumber) {
    const line = new GrDiffLine(GrDiffLineType.BOTH);
    line.beforeNumber = number;
    line.afterNumber = number;
    return new GrDiffGroup({type: GrDiffGroupType.BOTH, lines: [line]});
  }

  /**
   * In order to show key locations, such as comments, out of the bounds of
   * the selected context, treat them as separate chunks within the model so
   * that the content (and context surrounding it) renders correctly.
   *
   * @param chunks DiffContents as returned from server.
   * @return Finer grained DiffContents.
   */
  // visible for testing
  splitCommonChunksWithKeyLocations(chunks: DiffContent[]): DiffContent[] {
    const result = [];
    let leftLineNum = 1;
    let rightLineNum = 1;

    for (const chunk of chunks) {
      // If it isn't a common chunk, append it as-is and update line numbers.
      if (!chunk.ab && !chunk.skip && !chunk.common) {
        if (chunk.a) {
          leftLineNum += chunk.a.length;
        }
        if (chunk.b) {
          rightLineNum += chunk.b.length;
        }
        result.push(chunk);
        continue;
      }

      if (chunk.common && chunk.a!.length !== chunk.b!.length) {
        throw new Error(
          'DiffContent with common=true must always have equal length'
        );
      }
      const numLines = this.commonChunkLength(chunk);
      const chunkEnds = this.findChunkEndsAtKeyLocations(
        numLines,
        leftLineNum,
        rightLineNum
      );
      leftLineNum += numLines;
      rightLineNum += numLines;

      if (chunk.skip) {
        result.push({
          ...chunk,
          skip: chunk.skip,
          keyLocation: false,
        });
      } else if (chunk.ab) {
        result.push(
          ...this.splitAtChunkEnds(chunk.ab, chunkEnds).map(
            ({lines, keyLocation}) => {
              return {
                ...chunk,
                ab: lines,
                keyLocation,
              };
            }
          )
        );
      } else if (chunk.common) {
        const aChunks = this.splitAtChunkEnds(chunk.a!, chunkEnds);
        const bChunks = this.splitAtChunkEnds(chunk.b!, chunkEnds);
        result.push(
          ...aChunks.map(({lines, keyLocation}, i) => {
            return {
              ...chunk,
              a: lines,
              b: bChunks[i].lines,
              keyLocation,
            };
          })
        );
      }
    }

    return result;
  }

  /**
   * @return Offsets of the new chunk ends, including whether it's a key
   * location.
   */
  private findChunkEndsAtKeyLocations(
    numLines: number,
    leftOffset: number,
    rightOffset: number
  ): ChunkEnd[] {
    const result = [];
    let lastChunkEnd = 0;
    for (let i = 0; i < numLines; i++) {
      // If this line should not be collapsed.
      if (
        this.keyLocations[Side.LEFT][leftOffset + i] ||
        this.keyLocations[Side.RIGHT][rightOffset + i]
      ) {
        // If any lines have been accumulated into the chunk leading up to
        // this non-collapse line, then add them as a chunk and start a new
        // one.
        if (i > lastChunkEnd) {
          result.push({offset: i, keyLocation: false});
          lastChunkEnd = i;
        }

        // Add the non-collapse line as its own chunk.
        result.push({offset: i + 1, keyLocation: true});
      }
    }

    if (numLines > lastChunkEnd) {
      result.push({offset: numLines, keyLocation: false});
    }

    return result;
  }

  private splitAtChunkEnds(lines: string[], chunkEnds: ChunkEnd[]) {
    const result = [];
    let lastChunkEndOffset = 0;
    for (const {offset, keyLocation} of chunkEnds) {
      if (lastChunkEndOffset === offset) continue;
      result.push({
        lines: lines.slice(lastChunkEndOffset, offset),
        keyLocation,
      });
      lastChunkEndOffset = offset;
    }
    return result;
  }

  /**
   * Converts `IntralineInfo`s return by the API to `GrLineHighlights` used
   * for rendering.
   */
  // visible for testing
  convertIntralineInfos(
    rows: string[],
    intralineInfos: number[][]
  ): Highlights[] {
    // +1 to account for the \n that is not part of the rows passed here
    const lineLengths = rows.map(r => getStringLength(r) + 1);

    let rowIndex = 0;
    let idx = 0;
    const normalized = [];
    for (const [skipLength, markLength] of intralineInfos) {
      let lineLength = lineLengths[rowIndex];
      let j = 0;
      while (j < skipLength) {
        if (idx === lineLength) {
          idx = 0;
          lineLength = lineLengths[++rowIndex];
          continue;
        }
        idx++;
        j++;
      }
      let lineHighlight: Highlights = {
        contentIndex: rowIndex,
        startIndex: idx,
      };

      j = 0;
      while (lineLength && j < markLength) {
        if (idx === lineLength) {
          idx = 0;
          lineLength = lineLengths[++rowIndex];
          normalized.push(lineHighlight);
          lineHighlight = {
            contentIndex: rowIndex,
            startIndex: idx,
          };
          continue;
        }
        idx++;
        j++;
      }
      lineHighlight.endIndex = idx;
      normalized.push(lineHighlight);
    }
    return normalized;
  }
}
