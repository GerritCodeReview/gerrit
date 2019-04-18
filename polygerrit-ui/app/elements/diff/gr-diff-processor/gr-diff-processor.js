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

  const DiffSide = {
    LEFT: 'left',
    RIGHT: 'right',
  };

  const DiffGroupType = {
    ADDED: 'b',
    BOTH: 'ab',
    REMOVED: 'a',
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
   * This includes a number of tasks:
   *  - adding a group for the "File" pseudo line that file-level comments can
   *    be attached to
   *  - replacing unchanged parts of the diff that are outside the user's
   *    context setting and do not have comments with a group representing the
   *    "expand context" widget. This may require splitting a `DiffContent` so
   *    that the part that is within the context or has comments is shown, while
   *    the rest is not.
   *  - splitting large `DiffContent`s to allow more granular async rendering
   */
  Polymer({
    is: 'gr-diff-processor',
    _legacyUndefinedCheck: true,

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
     * Asynchronously process the diff object into groups. As it processes, it
     * will splice groups into the `groups` property of the component.
     * @return {Promise} A promise that resolves when the diff is completely
     *     processed.
     */
    process(content, isBinary) {
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
              sectionIndex: 0,
            };

            content = this._splitLargeChunks(content);
            content = this._splitUnchangedChunksWithComments(content);

            let currentBatch = 0;
            const nextStep = () => {
              if (this._isScrolling) {
                this.async(nextStep, 100);
                return;
              }
              // If we are done, resolve the promise.
              if (state.sectionIndex >= content.length) {
                resolve(this.groups);
                this._nextStepHandle = null;
                return;
              }

              // Process the next section and incorporate the result.
              const result = this._processNext(state, content);
              for (const group of result.groups) {
                this.push('groups', group);
                currentBatch += group.lines.length;
              }
              state.lineNums.left += result.lineDelta.left;
              state.lineNums.right += result.lineDelta.right;

              // Increment the index and recurse.
              state.sectionIndex++;
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
     * Process the next section of the diff.
     */
    _processNext(state, content) {
      const section = content[state.sectionIndex];

      const rows = {
        both: section[DiffGroupType.BOTH] || null,
        added: section[DiffGroupType.ADDED] || null,
        removed: section[DiffGroupType.REMOVED] || null,
      };

      const highlights = {
        added: section[DiffHighlights.ADDED] || null,
        removed: section[DiffHighlights.REMOVED] || null,
      };

      if (rows.both) { // If it's a shared section.
        let sectionEnd = null;
        if (state.sectionIndex === 0) {
          sectionEnd = 'first';
        } else if (state.sectionIndex === content.length - 1) {
          sectionEnd = 'last';
        }

        const sharedGroups = this._sharedGroupsFromRows(
            rows.both,
            content.length > 1 ? this.context : WHOLE_FILE,
            state.lineNums.left,
            state.lineNums.right,
            sectionEnd);

        return {
          lineDelta: {
            left: rows.both.length,
            right: rows.both.length,
          },
          groups: sharedGroups,
        };
      } else { // Otherwise it's a delta section.
        const deltaGroup = this._deltaGroupFromRows(
            rows.added,
            rows.removed,
            state.lineNums.left,
            state.lineNums.right,
            highlights);
        deltaGroup.dueToRebase = section.due_to_rebase;

        return {
          lineDelta: {
            left: rows.removed ? rows.removed.length : 0,
            right: rows.added ? rows.added.length : 0,
          },
          groups: [deltaGroup],
        };
      }
    },

    /**
     * Take rows of a shared diff section and produce an array of corresponding
     * (potentially collapsed) groups.
     * @param {!Array<string>} rows
     * @param {number} context
     * @param {number} startLineNumLeft
     * @param {number} startLineNumRight
     * @param {?string=} opt_sectionEnd String representing whether this is the
     *     first section or the last section or neither. Use the values 'first',
     *     'last' and null respectively.
     * @return {!Array<!Object>} Array of GrDiffGroup
     */
    _sharedGroupsFromRows(rows, context, startLineNumLeft,
        startLineNumRight, opt_sectionEnd) {
      const result = [];
      const lines = [];
      let line;

      // Map each row to a GrDiffLine.
      for (let i = 0; i < rows.length; i++) {
        line = new GrDiffLine(GrDiffLine.Type.BOTH);
        line.text = rows[i];
        line.beforeNumber = ++startLineNumLeft;
        line.afterNumber = ++startLineNumRight;
        lines.push(line);
      }

      // Find the hidden range based on the user's context preference. If this
      // is the first or the last section of the diff, make sure the collapsed
      // part of the section extends to the edge of the file.
      const hiddenRange = [context, rows.length - context];
      if (opt_sectionEnd === 'first') {
        hiddenRange[0] = 0;
      } else if (opt_sectionEnd === 'last') {
        hiddenRange[1] = rows.length;
      }

      // If there is a range to hide.
      if (context !== WHOLE_FILE && hiddenRange[1] - hiddenRange[0] > 1) {
        const linesBeforeCtx = lines.slice(0, hiddenRange[0]);
        const hiddenLines = lines.slice(hiddenRange[0], hiddenRange[1]);
        const linesAfterCtx = lines.slice(hiddenRange[1]);

        if (linesBeforeCtx.length > 0) {
          result.push(new GrDiffGroup(GrDiffGroup.Type.BOTH, linesBeforeCtx));
        }

        const ctxLine = new GrDiffLine(GrDiffLine.Type.CONTEXT_CONTROL);
        ctxLine.contextGroup =
            new GrDiffGroup(GrDiffGroup.Type.BOTH, hiddenLines);
        result.push(new GrDiffGroup(GrDiffGroup.Type.CONTEXT_CONTROL,
            [ctxLine]));

        if (linesAfterCtx.length > 0) {
          result.push(new GrDiffGroup(GrDiffGroup.Type.BOTH, linesAfterCtx));
        }
      } else {
        result.push(new GrDiffGroup(GrDiffGroup.Type.BOTH, lines));
      }

      return result;
    },

    /**
     * Take the rows of a delta diff section and produce the corresponding
     * group.
     * @param {!Array<string>} rowsAdded
     * @param {!Array<string>} rowsRemoved
     * @param {number} startLineNumLeft
     * @param {number} startLineNumRight
     * @return {!Object} (Gr-Diff-Group)
     */
    _deltaGroupFromRows(rowsAdded, rowsRemoved, startLineNumLeft,
        startLineNumRight, highlights) {
      let lines = [];
      if (rowsRemoved) {
        lines = lines.concat(this._deltaLinesFromRows(GrDiffLine.Type.REMOVE,
            rowsRemoved, startLineNumLeft, highlights.removed));
      }
      if (rowsAdded) {
        lines = lines.concat(this._deltaLinesFromRows(GrDiffLine.Type.ADD,
            rowsAdded, startLineNumRight, highlights.added));
      }
      return new GrDiffGroup(GrDiffGroup.Type.DELTA, lines);
    },

    /**
     * @return {!Array<!Object>} Array of GrDiffLines
     */
    _deltaLinesFromRows(lineType, rows, startLineNum,
        opt_highlights) {
      // Normalize highlights if they have been passed.
      if (opt_highlights) {
        opt_highlights = this._normalizeIntralineHighlights(rows,
            opt_highlights);
      }

      const lines = [];
      let line;
      for (let i = 0; i < rows.length; i++) {
        line = new GrDiffLine(lineType);
        line.text = rows[i];
        if (lineType === GrDiffLine.Type.ADD) {
          line.afterNumber = ++startLineNum;
        } else {
          line.beforeNumber = ++startLineNum;
        }
        if (opt_highlights) {
          line.highlights = opt_highlights.filter(hl => hl.contentIndex === i);
        }
        lines.push(line);
      }
      return lines;
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
     * @param {!Array<!Object>} chunks Chunks as returned from the server
     * @return {!Array<!Object>} Finer grained chunks.
     */
    _splitLargeChunks(chunks) {
      const newChunks = [];

      for (const chunk of chunks) {
        if (!chunk.ab) {
          for (const group of this._breakdownGroup(chunk)) {
            newChunks.push(group);
          }
          continue;
        }

        // If the context is set to "whole file", then break down the shared
        // chunks so they can be rendered incrementally. Note: this is not
        // enabled for any other context preference because manipulating the
        // chunks in this way violates assumptions by the context grouper logic.
        if (this.context === -1 && chunk.ab.length > MAX_GROUP_SIZE * 2) {
          // Split large shared groups in two, where the first is the maximum
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
     * In order to show comments out of the bounds of the selected context,
     * treat them as separate chunks within the model so that the content (and
     * context surrounding it) renders correctly.
     * @param {!Array<!Object>} chunks DiffContents as returned from server.
     * @return {!Array<!Object>} Finer grained DiffContents.
     */
    _splitUnchangedChunksWithComments(chunks) {
      const result = [];
      let leftLineNum = 1;
      let rightLineNum = 1;

      for (const chunk of chunks) {
        // If it isn't a common chunk, append it as-is and update line numbers.
        if (!chunk.ab) {
          if (chunk.a) {
            leftLineNum += chunk.a.length;
          }
          if (chunk.b) {
            rightLineNum += chunk.b.length;
          }
          result.push(chunk);
          continue;
        }

        const numLines = chunk.ab.length;
        const chunkEnds = this._findChunkEndsAtKeyLocations(
            numLines, leftLineNum, rightLineNum);
        leftLineNum += numLines;
        rightLineNum += numLines;
        result.push(...this._splitAtChunkEnds(chunk.ab, chunkEnds)
            .map(lines => Object.assign({}, chunk, {ab: lines})));
      }

      return result;
    },

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
            result.push(i);
            lastChunkEnd = i;
          }

          // Add the non-collapse line as its own chunk.
          result.push(i + 1);
        }
      }

      if (numLines > lastChunkEnd) {
        result.push(numLines);
      }

      return result;
    },

    _splitAtChunkEnds(lines, chunkEnds) {
      const result = [];
      let lastChunkEnd = 0;
      for (const chunkEnd of chunkEnds) {
        result.push(lines.slice(lastChunkEnd, chunkEnd));
        lastChunkEnd = chunkEnd;
      }
      return result;
    },

    /**
     * The `highlights` array consists of a list of <skip length, mark length>
     * pairs, where the skip length is the number of characters between the
     * end of the previous edit and the start of this edit, and the mark
     * length is the number of edited characters following the skip. The start
     * of the edits is from the beginning of the related diff content lines.
     *
     * Note that the implied newline character at the end of each line is
     * included in the length calculation, and thus it is possible for the
     * edits to span newlines.
     *
     * A line highlight object consists of three fields:
     * - contentIndex: The index of the diffChunk `content` field (the line
     *   being referred to).
     * - startIndex: Where the highlight should begin.
     * - endIndex: (optional) Where the highlight should end. If omitted, the
     *   highlight is meant to be a continuation onto the next line.
     */
    _normalizeIntralineHighlights(content, highlights) {
      let contentIndex = 0;
      let idx = 0;
      const normalized = [];
      for (const hl of highlights) {
        let line = content[contentIndex] + '\n';
        let j = 0;
        while (j < hl[0]) {
          if (idx === line.length) {
            idx = 0;
            line = content[++contentIndex] + '\n';
            continue;
          }
          idx++;
          j++;
        }
        let lineHighlight = {
          contentIndex,
          startIndex: idx,
        };

        j = 0;
        while (line && j < hl[1]) {
          if (idx === line.length) {
            idx = 0;
            line = content[++contentIndex] + '\n';
            normalized.push(lineHighlight);
            lineHighlight = {
              contentIndex,
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
     * of that type using the MAX_GROUP_SIZE. If the group is a shared section
     * or a delta it is returned as the single element of the result array.
     * @param {!Object} group A raw chunk from a diff response.
     * @return {!Array<!Array<!Object>>}
     */
    _breakdownGroup(group) {
      let key = null;
      if (group.a && !group.b) {
        key = 'a';
      } else if (group.b && !group.a) {
        key = 'b';
      } else if (group.ab) {
        key = 'ab';
      }

      if (!key) { return [group]; }

      return this._breakdown(group[key], MAX_GROUP_SIZE)
          .map(subgroupLines => {
            const subGroup = {};
            subGroup[key] = subgroupLines;
            if (group.due_to_rebase) {
              subGroup.due_to_rebase = true;
            }
            return subGroup;
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
