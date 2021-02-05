/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {
  GrDiffLine,
  GrDiffLineType,
  FILE,
  Highlights,
} from '../gr-diff/gr-diff-line';
import {
  GrDiffGroup,
  GrDiffGroupType,
  hideInContextControl,
} from '../gr-diff/gr-diff-group';
import {CancelablePromise, util} from '../../../scripts/util';
import {customElement, property} from '@polymer/decorators';
import {DiffContent} from '../../../types/diff';
import {Side} from '../../../constants/constants';

const WHOLE_FILE = -1;

interface State {
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

export interface KeyLocations {
  left: {[key: string]: boolean};
  right: {[key: string]: boolean};
}

/**
 * The maximum size for an addition or removal chunk before it is broken down
 * into a series of chunks that are this size at most.
 *
 * Note: The value of 120 is chosen so that it is larger than the default
 * _asyncThreshold of 64, but feel free to tune this constant to your
 * performance needs.
 */
const MAX_GROUP_SIZE = 120;

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
@customElement('gr-diff-processor')
export class GrDiffProcessor extends GestureEventListeners(
  LegacyElementMixin(PolymerElement)
) {
  @property({type: Number})
  context = 3;

  @property({type: Array, notify: true})
  groups: GrDiffGroup[] = [];

  @property({type: Object})
  keyLocations: KeyLocations = {left: {}, right: {}};

  @property({type: Number})
  _asyncThreshold = 64;

  @property({type: Number})
  _nextStepHandle: number | null = null;

  @property({type: Object})
  _processPromise: CancelablePromise<void> | null = null;

  @property({type: Boolean})
  _isScrolling?: boolean;

  /** @override */
  attached() {
    super.attached();
    this.listen(window, 'scroll', '_handleWindowScroll');
  }

  /** @override */
  detached() {
    super.detached();
    this.cancel();
    this.unlisten(window, 'scroll', '_handleWindowScroll');
  }

  _handleWindowScroll() {
    this._isScrolling = true;
    this.debounce(
      'resetIsScrolling',
      () => {
        this._isScrolling = false;
      },
      50
    );
  }

  /**
   * Asynchronously process the diff chunks into groups. As it processes, it
   * will splice groups into the `groups` property of the component.
   *
   * @return A promise that resolves with an
   * array of GrDiffGroups when the diff is completely processed.
   */
  process(chunks: DiffContent[], isBinary: boolean) {
    // Cancel any still running process() calls, because they append to the
    // same groups field.
    this.cancel();

    this.groups = [];
    this.push('groups', this._makeFileComments());

    // If it's a binary diff, we won't be rendering hunks of text differences
    // so finish processing.
    if (isBinary) {
      return Promise.resolve();
    }

    this._processPromise = util.makeCancelable(
      new Promise(resolve => {
        const state = {
          lineNums: {left: 0, right: 0},
          chunkIndex: 0,
        };

        chunks = this._splitLargeChunks(chunks);
        chunks = this._splitCommonChunksWithKeyLocations(chunks);

        let currentBatch = 0;
        const nextStep = () => {
          if (this._isScrolling) {
            this._nextStepHandle = this.async(nextStep, 100);
            return;
          }
          // If we are done, resolve the promise.
          if (state.chunkIndex >= chunks.length) {
            resolve();
            this._nextStepHandle = null;
            return;
          }

          // Process the next chunk and incorporate the result.
          const stateUpdate = this._processNext(state, chunks);
          for (const group of stateUpdate.groups) {
            this.push('groups', group);
            currentBatch += group.lines.length;
          }
          state.lineNums.left += stateUpdate.lineDelta.left;
          state.lineNums.right += stateUpdate.lineDelta.right;

          // Increment the index and recurse.
          state.chunkIndex = stateUpdate.newChunkIndex;
          if (currentBatch >= this._asyncThreshold) {
            currentBatch = 0;
            this._nextStepHandle = this.async(nextStep, 1);
          } else {
            nextStep.call(this);
          }
        };

        nextStep.call(this);
      })
    );
    return this._processPromise.finally(() => {
      this._processPromise = null;
    });
  }

  /**
   * Cancel any jobs that are running.
   */
  cancel() {
    if (this._nextStepHandle !== null) {
      this.cancelAsync(this._nextStepHandle);
      this._nextStepHandle = null;
    }
    if (this._processPromise) {
      this._processPromise.cancel();
    }
  }

  /**
   * Process the next uncollapsible chunk, or the next collapsible chunks.
   */
  _processNext(state: State, chunks: DiffContent[]) {
    const firstUncollapsibleChunkIndex = this._firstUncollapsibleChunkIndex(
      chunks,
      state.chunkIndex
    );
    if (firstUncollapsibleChunkIndex === state.chunkIndex) {
      const chunk = chunks[state.chunkIndex];
      return {
        lineDelta: {
          left: this._linesLeft(chunk).length,
          right: this._linesRight(chunk).length,
        },
        groups: [
          this._chunkToGroup(
            chunk,
            state.lineNums.left + 1,
            state.lineNums.right + 1
          ),
        ],
        newChunkIndex: state.chunkIndex + 1,
      };
    }

    return this._processCollapsibleChunks(
      state,
      chunks,
      firstUncollapsibleChunkIndex
    );
  }

  _linesLeft(chunk: DiffContent) {
    return chunk.ab || chunk.a || [];
  }

  _linesRight(chunk: DiffContent) {
    return chunk.ab || chunk.b || [];
  }

  _firstUncollapsibleChunkIndex(chunks: DiffContent[], offset: number) {
    let chunkIndex = offset;
    while (
      chunkIndex < chunks.length &&
      this._isCollapsibleChunk(chunks[chunkIndex])
    ) {
      chunkIndex++;
    }
    return chunkIndex;
  }

  _isCollapsibleChunk(chunk: DiffContent) {
    return (chunk.ab || chunk.common || chunk.skip) && !chunk.keyLocation;
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
  _processCollapsibleChunks(
    state: State,
    chunks: DiffContent[],
    firstUncollapsibleChunkIndex: number
  ) {
    const collapsibleChunks = chunks.slice(
      state.chunkIndex,
      firstUncollapsibleChunkIndex
    );
    const lineCount = collapsibleChunks.reduce(
      (sum, chunk) => sum + this._commonChunkLength(chunk),
      0
    );

    let groups = this._chunksToGroups(
      collapsibleChunks,
      state.lineNums.left + 1,
      state.lineNums.right + 1
    );

    const hasSkippedGroup = !!groups.find(g => g.skip);
    if (this.context !== WHOLE_FILE || hasSkippedGroup) {
      const contextNumLines = this.context > 0 ? this.context : 0;
      const hiddenStart = state.chunkIndex === 0 ? 0 : contextNumLines;
      const hiddenEnd =
        lineCount -
        (firstUncollapsibleChunkIndex === chunks.length ? 0 : this.context);
      groups = hideInContextControl(groups, hiddenStart, hiddenEnd);
    }

    return {
      lineDelta: {
        left: lineCount,
        right: lineCount,
      },
      groups,
      newChunkIndex: firstUncollapsibleChunkIndex,
    };
  }

  _commonChunkLength(chunk: DiffContent) {
    if (chunk.skip) {
      return chunk.skip;
    }
    console.assert(!!chunk.ab || !!chunk.common);

    console.assert(
      !chunk.a || (!!chunk.b && chunk.a.length === chunk.b.length),
      'common chunk needs same number of a and b lines: ',
      chunk
    );
    return this._linesLeft(chunk).length;
  }

  _chunksToGroups(
    chunks: DiffContent[],
    offsetLeft: number,
    offsetRight: number
  ): GrDiffGroup[] {
    return chunks.map(chunk => {
      const group = this._chunkToGroup(chunk, offsetLeft, offsetRight);
      const chunkLength = this._commonChunkLength(chunk);
      offsetLeft += chunkLength;
      offsetRight += chunkLength;
      return group;
    });
  }

  _chunkToGroup(
    chunk: DiffContent,
    offsetLeft: number,
    offsetRight: number
  ): GrDiffGroup {
    const type =
      chunk.ab || chunk.skip ? GrDiffGroupType.BOTH : GrDiffGroupType.DELTA;
    const lines = this._linesFromChunk(chunk, offsetLeft, offsetRight);
    const group = new GrDiffGroup(type, lines);
    group.keyLocation = !!chunk.keyLocation;
    group.dueToRebase = !!chunk.due_to_rebase;
    group.moveDetails = chunk.move_details;
    group.skip = chunk.skip;
    group.ignoredWhitespaceOnly = !!chunk.common;
    if (chunk.skip) {
      group.lineRange = {
        left: {start_line: offsetLeft, end_line: offsetLeft + chunk.skip - 1},
        right: {
          start_line: offsetRight,
          end_line: offsetRight + chunk.skip - 1,
        },
      };
    }
    return group;
  }

  _linesFromChunk(chunk: DiffContent, offsetLeft: number, offsetRight: number) {
    if (chunk.ab) {
      return chunk.ab.map((row, i) =>
        this._lineFromRow(GrDiffLineType.BOTH, offsetLeft, offsetRight, row, i)
      );
    }
    let lines: GrDiffLine[] = [];
    if (chunk.a) {
      // Avoiding a.push(...b) because that causes callstack overflows for
      // large b, which can occur when large files are added removed.
      lines = lines.concat(
        this._linesFromRows(
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
        this._linesFromRows(
          GrDiffLineType.ADD,
          chunk.b,
          offsetRight,
          chunk.edit_b
        )
      );
    }
    return lines;
  }

  _linesFromRows(
    lineType: GrDiffLineType,
    rows: string[],
    offset: number,
    intralineInfos?: number[][]
  ): GrDiffLine[] {
    const grDiffHighlights = intralineInfos
      ? this._convertIntralineInfos(rows, intralineInfos)
      : undefined;
    return rows.map((row, i) =>
      this._lineFromRow(lineType, offset, offset, row, i, grDiffHighlights)
    );
  }

  _lineFromRow(
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

  _makeFileComments() {
    const line = new GrDiffLine(GrDiffLineType.BOTH);
    line.beforeNumber = FILE;
    line.afterNumber = FILE;
    return new GrDiffGroup(GrDiffGroupType.BOTH, [line]);
  }

  /**
   * Split chunks into smaller chunks of the same kind.
   *
   * This is done to prevent doing too much work on the main thread in one
   * uninterrupted rendering step, which would make the browser unresponsive.
   *
   * Note that in the case of unmodified chunks, we only split chunks if the
   * context is set to file (because otherwise they are split up further down
   * the processing into the visible and hidden context), and only split it
   * into 2 chunks, one max sized one and the rest (for reasons that are
   * unclear to me).
   *
   * @param chunks Chunks as returned from the server
   * @return Finer grained chunks.
   */
  _splitLargeChunks(chunks: DiffContent[]): DiffContent[] {
    const newChunks = [];

    for (const chunk of chunks) {
      if (!chunk.ab) {
        for (const subChunk of this._breakdownChunk(chunk)) {
          newChunks.push(subChunk);
        }
        continue;
      }

      // If the context is set to "whole file", then break down the shared
      // chunks so they can be rendered incrementally. Note: this is not
      // enabled for any other context preference because manipulating the
      // chunks in this way violates assumptions by the context grouper logic.
      if (this.context === -1 && chunk.ab.length > MAX_GROUP_SIZE * 2) {
        // Split large shared chunks in two, where the first is the maximum
        // group size.
        newChunks.push({ab: chunk.ab.slice(0, MAX_GROUP_SIZE)});
        newChunks.push({ab: chunk.ab.slice(MAX_GROUP_SIZE)});
      } else {
        newChunks.push(chunk);
      }
    }
    return newChunks;
  }

  /**
   * In order to show key locations, such as comments, out of the bounds of
   * the selected context, treat them as separate chunks within the model so
   * that the content (and context surrounding it) renders correctly.
   *
   * @param chunks DiffContents as returned from server.
   * @return Finer grained DiffContents.
   */
  _splitCommonChunksWithKeyLocations(chunks: DiffContent[]): DiffContent[] {
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
      const numLines = this._commonChunkLength(chunk);
      const chunkEnds = this._findChunkEndsAtKeyLocations(
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
          ...this._splitAtChunkEnds(chunk.ab, chunkEnds).map(
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
        const aChunks = this._splitAtChunkEnds(chunk.a!, chunkEnds);
        const bChunks = this._splitAtChunkEnds(chunk.b!, chunkEnds);
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
  _findChunkEndsAtKeyLocations(
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

  _splitAtChunkEnds(lines: string[], chunkEnds: ChunkEnd[]) {
    const result = [];
    let lastChunkEndOffset = 0;
    for (const {offset, keyLocation} of chunkEnds) {
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
  _convertIntralineInfos(
    rows: string[],
    intralineInfos: number[][]
  ): Highlights[] {
    let rowIndex = 0;
    let idx = 0;
    const normalized = [];
    for (const [skipLength, markLength] of intralineInfos) {
      let line = rows[rowIndex] + '\n';
      let j = 0;
      while (j < skipLength) {
        if (idx === line.length) {
          idx = 0;
          line = rows[++rowIndex] + '\n';
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
      while (line && j < markLength) {
        if (idx === line.length) {
          idx = 0;
          line = rows[++rowIndex] + '\n';
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

  /**
   * If a group is an addition or a removal, break it down into smaller groups
   * of that type using the MAX_GROUP_SIZE. If the group is a shared chunk
   * or a delta it is returned as the single element of the result array.
   */
  _breakdownChunk(chunk: DiffContent): DiffContent[] {
    let key: 'a' | 'b' | 'ab' | null = null;
    const {a, b, ab, move_details} = chunk;
    if (a?.length && !b?.length) {
      key = 'a';
    } else if (b?.length && !a?.length) {
      key = 'b';
    } else if (ab?.length) {
      key = 'ab';
    }

    // Move chunks should not be divided because of move label
    // positioned in the top of the chunk
    if (!key || move_details) {
      return [chunk];
    }

    return this._breakdown(chunk[key]!, MAX_GROUP_SIZE).map(subChunkLines => {
      const subChunk: DiffContent = {};
      subChunk[key!] = subChunkLines;
      if (chunk.due_to_rebase) {
        subChunk.due_to_rebase = true;
      }
      if (chunk.move_details) {
        subChunk.move_details = chunk.move_details;
      }
      return subChunk;
    });
  }

  /**
   * Given an array and a size, return an array of arrays where no inner array
   * is larger than that size, preserving the original order.
   */
  _breakdown<T>(array: T[], size: number): T[][] {
    if (!array.length) {
      return [];
    }
    if (array.length < size) {
      return [array];
    }

    const head = array.slice(0, array.length - size);
    const tail = array.slice(array.length - size);

    return this._breakdown(head, size).concat([tail]);
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-diff-processor': GrDiffProcessor;
  }
}
