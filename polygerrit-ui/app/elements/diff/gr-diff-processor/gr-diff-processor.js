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
(function() {
  'use strict';

  const WHOLE_FILE = -1;

  const Defs = {};

  /**
   * The DiffIntralineInfo entity contains information about intraline edits in a
   * file.
   *
   * The information consists of a list of <skip length, mark length> pairs, where
   * the skip length is the number of characters between the end of the previous
   * edit and the start of this edit, and the mark length is the number of edited
   * characters following the skip. The start of the edits is from the beginning
   * of the related diff content lines.
   *
   * Note that the implied newline character at the end of each line is included
   * in the length calculation, and thus it is possible for the edits to span
   * newlines.
   * @typedef {!Array<number>}
   */
  Defs.IntralineInfo;

  /**
   * A portion of the diff that is treated the same.
   *
   * Called `DiffContent` in the API, see
   * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#diff-content
   *
   * @typedef {{
   *  ab: ?Array<!string>,
   *  a: ?Array<!string>,
   *  b: ?Array<!string>,
   *  skip: ?number,
   *  edit_a: ?Array<!Defs.IntralineInfo>,
   *  edit_b: ?Array<!Defs.IntralineInfo>,
   *  due_to_rebase: ?boolean,
   *  common: ?boolean
   * }}
   */
  Defs.Chunk;

  const DiffSide = {
    LEFT: 'left',
    RIGHT: 'right',
  };

  const DiffHighlights = {
    ADDED: 'edit_b',
    REMOVED: 'edit_a',
  };

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
  Polymer({
    is: 'gr-diff-processor',

    properties: {

      /**
       * The amount of context around collapsed groups.
       */
      context: Number,

      /**
       * The array of groups output by the processor.
       */
      groups: {
        type: Array,
        notify: true,
      },

      /**
       * Locations that should not be collapsed, including the locations of
       * comments.
       */
      keyLocations: {
        type: Object,
        value() { return {left: {}, right: {}}; },
      },

      /**
       * The maximum number of lines to process synchronously.
       */
      _asyncThreshold: {
        type: Number,
        value: 64,
      },

      /** @type {?number} */
      _nextStepHandle: Number,
      /**
       * The promise last returned from `process()` while the asynchronous
       * processing is running - `null` otherwise. Provides a `cancel()`
       * method that rejects it with `{isCancelled: true}`.
       * @type {?Object}
       */
      _processPromise: {
        type: Object,
        value: null,
      },
      _isScrolling: Boolean,
    },

    attached() {
      this.listen(window, 'scroll', '_handleWindowScroll');
    },

    detached() {
      this.cancel();
      this.unlisten(window, 'scroll', '_handleWindowScroll');
    },

    _handleWindowScroll() {
      this._isScrolling = true;
      this.debounce('resetIsScrolling', () => {
        this._isScrolling = false;
      }, 50);
    },

    /**
     * Asynchronously process the diff chunks into groups. As it processes, it
     * will splice groups into the `groups` property of the component.
     * @param {!Array<!Defs.Chunk>} chunks
     * @param {boolean} isBinary
     * @return {!Promise<!Array<!Object>>} A promise that resolves with an
     *     array of GrDiffGroups when the diff is completely processed.
     */
    process(chunks, isBinary) {
      // Cancel any still running process() calls, because they append to the
      // same groups field.
      this.cancel();

      this.groups = [];
      this.push('groups', this._makeFileComments());

      // If it's a binary diff, we won't be rendering hunks of text differences
      // so finish processing.
      if (isBinary) { return Promise.resolve(); }


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
                resolve(this.groups);
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
          }));
      return this._processPromise
          .finally(() => { this._processPromise = null; });
    },

    /**
     * Cancel any jobs that are running.
     */
    cancel() {
      if (this._nextStepHandle != null) {
        this.cancelAsync(this._nextStepHandle);
        this._nextStepHandle = null;
      }
      if (this._processPromise) {
        this._processPromise.cancel();
      }
    },

    /**
     * Process the next uncollapsible chunk, or the next collapsible chunks.
     *
     * @param {!Object} state
     * @param {!Array<!Object>} chunks
     * @return {{lineDelta: {left: number, right: number}, groups: !Array<!Object>, newChunkIndex: number}}
     */
    _processNext(state, chunks) {
      const firstUncollapsibleChunkIndex =
          this._firstUncollapsibleChunkIndex(chunks, state.chunkIndex);
      if (firstUncollapsibleChunkIndex === state.chunkIndex) {
        const chunk = chunks[state.chunkIndex];
        return {
          lineDelta: {
            left: this._linesLeft(chunk).length,
            right: this._linesRight(chunk).length,
          },
          groups: [this._chunkToGroup(
              chunk, state.lineNums.left + 1, state.lineNums.right + 1)],
          newChunkIndex: state.chunkIndex + 1,
        };
      }

      return this._processCollapsibleChunks(
          state, chunks, firstUncollapsibleChunkIndex);
    },

    _linesLeft(chunk) {
      return chunk.ab || chunk.a || [];
    },

    _linesRight(chunk) {
      return chunk.ab || chunk.b || [];
    },

    _firstUncollapsibleChunkIndex(chunks, offset) {
      let chunkIndex = offset;
      while (chunkIndex < chunks.length &&
          this._isCollapsibleChunk(chunks[chunkIndex])) {
        chunkIndex++;
      }
      return chunkIndex;
    },

    _isCollapsibleChunk(chunk) {
      return (chunk.ab || chunk.common) && !chunk.keyLocation;
    },

    /**
     * Process a stretch of collapsible chunks.
     *
     * Outputs up to three groups:
     *  1) Visible context before the hidden common code, unless it's the
     *     very beginning of the file.
     *  2) Context hidden behind a context bar, unless empty.
     *  3) Visible context after the hidden common code, unless it's the very
     *     end of the file.
     *
     * @param {!Object} state
     * @param {!Array<Object>} chunks
     * @param {number} firstUncollapsibleChunkIndex
     * @return {{lineDelta: {left: number, right: number}, groups: !Array<!Object>, newChunkIndex: number}}
     */
    _processCollapsibleChunks(
        state, chunks, firstUncollapsibleChunkIndex) {
      const collapsibleChunks = chunks.slice(
          state.chunkIndex, firstUncollapsibleChunkIndex);
      const lineCount = collapsibleChunks.reduce(
          (sum, chunk) => sum + this._commonChunkLength(chunk), 0);

      let groups = this._chunksToGroups(
          collapsibleChunks,
          state.lineNums.left + 1,
          state.lineNums.right + 1);

      if (this.context !== WHOLE_FILE) {
        const hiddenStart = state.chunkIndex === 0 ? 0 : this.context;
        const hiddenEnd = lineCount - (
            firstUncollapsibleChunkIndex === chunks.length ?
            0 : this.context);
        groups = GrDiffGroup.hideInContextControl(
            groups, hiddenStart, hiddenEnd);
      }

      return {
        lineDelta: {
          left: lineCount,
          right: lineCount,
        },
        groups,
        newChunkIndex: firstUncollapsibleChunkIndex,
      };
    },

    _commonChunkLength(chunk) {
      console.assert(chunk.ab || chunk.common);
      console.assert(
          !chunk.a || (chunk.b && chunk.a.length === chunk.b.length),
          `common chunk needs same number of a and b lines: `, chunk);
      return this._linesLeft(chunk).length;
    },

    /**
     * @param {!Array<!Object>} chunks
     * @param {number} offsetLeft
     * @param {number} offsetRight
     * @return {!Array<!Object>} (GrDiffGroup)
     */
    _chunksToGroups(chunks, offsetLeft, offsetRight) {
      return chunks.map(chunk => {
        const group = this._chunkToGroup(chunk, offsetLeft, offsetRight);
        const chunkLength = this._commonChunkLength(chunk);
        offsetLeft += chunkLength;
        offsetRight += chunkLength;
        return group;
      });
    },

    /**
     * @param {!Object} chunk
     * @param {number} offsetLeft
     * @param {number} offsetRight
     * @return {!Object} (GrDiffGroup)
     */
    _chunkToGroup(chunk, offsetLeft, offsetRight) {
      const type = chunk.ab ? GrDiffGroup.Type.BOTH : GrDiffGroup.Type.DELTA;
      const lines = this._linesFromChunk(chunk, offsetLeft, offsetRight);
      const group = new GrDiffGroup(type, lines);
      group.keyLocation = chunk.keyLocation;
      group.dueToRebase = chunk.due_to_rebase;
      group.ignoredWhitespaceOnly = chunk.common;
      return group;
    },

    _linesFromChunk(chunk, offsetLeft, offsetRight) {
      if (chunk.ab) {
        return chunk.ab.map((row, i) => this._lineFromRow(
            GrDiffLine.Type.BOTH, offsetLeft, offsetRight, row, i));
      }
      let lines = [];
      if (chunk.a) {
        // Avoiding a.push(...b) because that causes callstack overflows for
        // large b, which can occur when large files are added removed.
        lines = lines.concat(this._linesFromRows(
            GrDiffLine.Type.REMOVE, chunk.a, offsetLeft,
            chunk[DiffHighlights.REMOVED]));
      }
      if (chunk.b) {
        // Avoiding a.push(...b) because that causes callstack overflows for
        // large b, which can occur when large files are added removed.
        lines = lines.concat(this._linesFromRows(
            GrDiffLine.Type.ADD, chunk.b, offsetRight,
            chunk[DiffHighlights.ADDED]));
      }
      return lines;
    },

    /**
     * @param {string} lineType (GrDiffLine.Type)
     * @param {!Array<string>} rows
     * @param {number} offset
     * @param {?Array<!Defs.IntralineInfo>=} opt_intralineInfos
     * @return {!Array<!Object>} (GrDiffLine)
     */
    _linesFromRows(lineType, rows, offset, opt_intralineInfos) {
      const grDiffHighlights = opt_intralineInfos ?
        this._convertIntralineInfos(rows, opt_intralineInfos) : undefined;
      return rows.map((row, i) => this._lineFromRow(
          lineType, offset, offset, row, i, grDiffHighlights));
    },

    /**
     * @param {string} type (GrDiffLine.Type)
     * @param {number} offsetLeft
     * @param {number} offsetRight
     * @param {string} row
     * @param {number} i
     * @param {!Array<!Object>=} opt_highlights
     * @return {!Object} (GrDiffLine)
     */
    _lineFromRow(type, offsetLeft, offsetRight, row, i, opt_highlights) {
      const line = new GrDiffLine(type);
      line.text = row;
      if (type !== GrDiffLine.Type.ADD) line.beforeNumber = offsetLeft + i;
      if (type !== GrDiffLine.Type.REMOVE) line.afterNumber = offsetRight + i;
      if (opt_highlights) {
        line.hasIntralineInfo = true;
        line.highlights = opt_highlights.filter(hl => hl.contentIndex === i);
      } else {
        line.hasIntralineInfo = false;
      }
      return line;
    },

    _makeFileComments() {
      const line = new GrDiffLine(GrDiffLine.Type.BOTH);
      line.beforeNumber = GrDiffLine.FILE;
      line.afterNumber = GrDiffLine.FILE;
      return new GrDiffGroup(GrDiffGroup.Type.BOTH, [line]);
    },


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
     * @param {!Array<!Defs.Chunk>} chunks Chunks as returned from the server
     * @return {!Array<!Defs.Chunk>} Finer grained chunks.
     */
    _splitLargeChunks(chunks) {
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
    },

    /**
     * In order to show key locations, such as comments, out of the bounds of
     * the selected context, treat them as separate chunks within the model so
     * that the content (and context surrounding it) renders correctly.
     * @param {!Array<!Object>} chunks DiffContents as returned from server.
     * @return {!Array<!Object>} Finer grained DiffContents.
     */
    _splitCommonChunksWithKeyLocations(chunks) {
      const result = [];
      let leftLineNum = 1;
      let rightLineNum = 1;

      for (const chunk of chunks) {
        // If it isn't a common chunk, append it as-is and update line numbers.
        if (!chunk.ab && !chunk.common) {
          if (chunk.a) {
            leftLineNum += chunk.a.length;
          }
          if (chunk.b) {
            rightLineNum += chunk.b.length;
          }
          result.push(chunk);
          continue;
        }

        if (chunk.common && chunk.a.length != chunk.b.length) {
          throw new Error(
            'DiffContent with common=true must always have equal length');
        }
        const numLines = this._commonChunkLength(chunk);
        const chunkEnds = this._findChunkEndsAtKeyLocations(
            numLines, leftLineNum, rightLineNum);
        leftLineNum += numLines;
        rightLineNum += numLines;

        if (chunk.ab) {
          result.push(...this._splitAtChunkEnds(chunk.ab, chunkEnds)
              .map(({lines, keyLocation}) =>
                  Object.assign({}, chunk, {ab: lines, keyLocation})));
        } else if (chunk.common) {
          const aChunks = this._splitAtChunkEnds(chunk.a, chunkEnds);
          const bChunks = this._splitAtChunkEnds(chunk.b, chunkEnds);
          result.push(...aChunks.map(({lines, keyLocation}, i) =>
            Object.assign(
                {}, chunk, {a: lines, b: bChunks[i].lines, keyLocation})));
        }
      }

      return result;
    },

    /**
     * @return {!Array<{offset: number, keyLocation: boolean}>} Offsets of the
     *   new chunk ends, including whether it's a key location.
     */
    _findChunkEndsAtKeyLocations(numLines, leftOffset, rightOffset) {
      const result = [];
      let lastChunkEnd = 0;
      for (let i=0; i<numLines; i++) {
        // If this line should not be collapsed.
        if (this.keyLocations[DiffSide.LEFT][leftOffset + i] ||
            this.keyLocations[DiffSide.RIGHT][rightOffset + i]) {
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
    },

    _splitAtChunkEnds(lines, chunkEnds) {
      const result = [];
      let lastChunkEndOffset = 0;
      for (const {offset, keyLocation} of chunkEnds) {
        result.push(
            {lines: lines.slice(lastChunkEndOffset, offset), keyLocation});
        lastChunkEndOffset = offset;
      }
      return result;
    },

    /**
     * Converts `IntralineInfo`s return by the API to `GrLineHighlights` used
     * for rendering.
     *
     * @param {!Array<string>} rows
     * @param {!Array<!Defs.IntralineInfo>} intralineInfos
     * @return {!Array<!Object>} (GrDiffLine.Highlight)
     */
    _convertIntralineInfos(rows, intralineInfos) {
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
        let lineHighlight = {
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
    },

    /**
     * If a group is an addition or a removal, break it down into smaller groups
     * of that type using the MAX_GROUP_SIZE. If the group is a shared chunk
     * or a delta it is returned as the single element of the result array.
     * @param {!Defs.Chunk} chunk A raw chunk from a diff response.
     * @return {!Array<!Array<!Object>>}
     */
    _breakdownChunk(chunk) {
      let key = null;
      if (chunk.a && !chunk.b) {
        key = 'a';
      } else if (chunk.b && !chunk.a) {
        key = 'b';
      } else if (chunk.ab) {
        key = 'ab';
      }

      if (!key) { return [chunk]; }

      return this._breakdown(chunk[key], MAX_GROUP_SIZE)
          .map(subChunkLines => {
            const subChunk = {};
            subChunk[key] = subChunkLines;
            if (chunk.due_to_rebase) {
              subChunk.due_to_rebase = true;
            }
            return subChunk;
          });
    },

    /**
     * Given an array and a size, return an array of arrays where no inner array
     * is larger than that size, preserving the original order.
     * @param {!Array<T>} array
     * @param {number} size
     * @return {!Array<!Array<T>>}
     * @template T
     */
    _breakdown(array, size) {
      if (!array.length) { return []; }
      if (array.length < size) { return [array]; }

      const head = array.slice(0, array.length - size);
      const tail = array.slice(array.length - size);

      return this._breakdown(head, size).concat([tail]);
    },
  });
})();
