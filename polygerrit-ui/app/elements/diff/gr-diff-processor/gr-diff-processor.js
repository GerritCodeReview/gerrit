// Copyright (C) 2016 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
(function() {
  'use strict';

  var WHOLE_FILE = -1;

  var DiffSide = {
    LEFT: 'left',
    RIGHT: 'right',
  };

  var DiffGroupType = {
    ADDED: 'b',
    BOTH: 'ab',
    REMOVED: 'a',
  };

  var DiffHighlights = {
    ADDED: 'edit_b',
    REMOVED: 'edit_a',
  };

  /**
   * The maximum size for an addition or removal chunk before it is broken down
   * into a series of chunks that are this size at most.
   *
   * Note: The value of 70 is chosen so that it is larger than the default
   * _asyncThreshold of 64, but feel free to tune this constant to your
   * performance needs.
   */
  var MAX_GROUP_SIZE = 70;

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
        value: function() { return {left: {}, right: {}}; },
      },

      /**
       * The maximum number of lines to process synchronously.
       */
      _asyncThreshold: {
        type: Number,
        value: 64,
      },

      _nextStepHandle: Number,
      _isScrolling: Boolean,
    },

    attached: function() {
      this.listen(window, 'scroll', '_handleWindowScroll');
    },

    detached: function() {
      this.cancel();
      this.unlisten(window, 'scroll', '_handleWindowScroll');
    },

    _handleWindowScroll: function() {
      this._isScrolling = true;
      this.debounce('resetIsScrolling', function() {
        this._isScrolling = false;
      }, 50);
    },

    /**
     * Asynchronously process the diff object into groups. As it processes, it
     * will splice groups into the `groups` property of the component.
     * @return {Promise} A promise that resolves when the diff is completely
     *     processed.
     */
    process: function(content) {
      return new Promise(function(resolve) {
        this.groups = [];
        this.push('groups', this._makeFileComments());

        var state = {
          lineNums: {left: 0, right: 0},
          sectionIndex: 0,
        };

        content = this._splitCommonGroupsWithComments(content);

        var currentBatch = 0;
        var nextStep = function() {

          if (this._isScrolling) {
            this.async(nextStep, 100);
            return;
          }
          // If we are done, resolve the promise.
          if (state.sectionIndex >= content.length) {
            resolve(this.groups);
            this._nextStepHandle = undefined;
            return;
          }

          // Process the next section and incorporate the result.
          var result = this._processNext(state, content);
          result.groups.forEach(function(group) {
            this.push('groups', group);
            currentBatch += group.lines.length;
          }, this);
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
      }.bind(this));
    },

    /**
     * Cancel any jobs that are running.
     */
    cancel: function() {
      if (this._nextStepHandle !== undefined) {
        this.cancelAsync(this._nextStepHandle);
        this._nextStepHandle = undefined;
      }
    },

    /**
     * Process the next section of the diff.
     */
    _processNext: function(state, content) {
      var section = content[state.sectionIndex];

      var rows = {
        both: section[DiffGroupType.BOTH] || null,
        added: section[DiffGroupType.ADDED] || null,
        removed: section[DiffGroupType.REMOVED] || null,
      };

      var highlights = {
        added: section[DiffHighlights.ADDED] || null,
        removed: section[DiffHighlights.REMOVED] || null,
      };

      if (rows.both) { // If it's a shared section.
        var sectionEnd = null;
        if (state.sectionIndex === 0) {
          sectionEnd = 'first';
        } else if (state.sectionIndex === content.length - 1) {
          sectionEnd = 'last';
        }

        var sharedGroups = this._sharedGroupsFromRows(
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

        var deltaGroup = this._deltaGroupFromRows(
            rows.added,
            rows.removed,
            state.lineNums.left,
            state.lineNums.right,
            highlights);

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
     * @param {Array<String>} rows
     * @param {Number} context
     * @param {Number} startLineNumLeft
     * @param {Number} startLineNumRight
     * @param {String} opt_sectionEnd String representing whether this is the
     *     first section or the last section or neither. Use the values 'first',
     *     'last' and null respectively.
     * @return {Array<GrDiffGroup>}
     */
    _sharedGroupsFromRows: function(rows, context, startLineNumLeft,
        startLineNumRight, opt_sectionEnd) {
      var result = [];
      var lines = [];
      var line;

      // Map each row to a GrDiffLine.
      for (var i = 0; i < rows.length; i++) {
        line = new GrDiffLine(GrDiffLine.Type.BOTH);
        line.text = rows[i];
        line.beforeNumber = ++startLineNumLeft;
        line.afterNumber = ++startLineNumRight;
        lines.push(line);
      }

      // Find the hidden range based on the user's context preference. If this
      // is the first or the last section of the diff, make sure the collapsed
      // part of the section extends to the edge of the file.
      var hiddenRange = [context, rows.length - context];
      if (opt_sectionEnd === 'first') {
        hiddenRange[0] = 0;
      } else if (opt_sectionEnd === 'last') {
        hiddenRange[1] = rows.length;
      }

      // If there is a range to hide.
      if (context !== WHOLE_FILE && hiddenRange[1] - hiddenRange[0] > 1) {
        var linesBeforeCtx = lines.slice(0, hiddenRange[0]);
        var hiddenLines = lines.slice(hiddenRange[0], hiddenRange[1]);
        var linesAfterCtx = lines.slice(hiddenRange[1]);

        if (linesBeforeCtx.length > 0) {
          result.push(new GrDiffGroup(GrDiffGroup.Type.BOTH, linesBeforeCtx));
        }

        var ctxLine = new GrDiffLine(GrDiffLine.Type.CONTEXT_CONTROL);
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
     * @param {Array<String>} rowsAdded
     * @param {Array<String>} rowsRemoved
     * @param {Number} startLineNumLeft
     * @param {Number} startLineNumRight
     * @return {GrDiffGroup}
     */
    _deltaGroupFromRows: function(rowsAdded, rowsRemoved, startLineNumLeft,
        startLineNumRight, highlights) {
      var lines = [];
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
     * @return {Array<GrDiffLine>}
     */
    _deltaLinesFromRows: function(lineType, rows, startLineNum,
        opt_highlights) {
      // Normalize highlights if they have been passed.
      if (opt_highlights) {
        opt_highlights = this._normalizeIntralineHighlights(rows,
            opt_highlights);
      }

      var lines = [];
      var line;
      for (var i = 0; i < rows.length; i++) {
        line = new GrDiffLine(lineType);
        line.text = rows[i];
        if (lineType === GrDiffLine.Type.ADD) {
          line.afterNumber = ++startLineNum;
        } else {
          line.beforeNumber = ++startLineNum;
        }
        if (opt_highlights) {
          line.highlights = opt_highlights.filter(
              function(hl) { return hl.contentIndex === i; });
        }
        lines.push(line);
      }
      return lines;
    },

    _makeFileComments: function() {
      var line = new GrDiffLine(GrDiffLine.Type.BOTH);
      line.beforeNumber = GrDiffLine.FILE;
      line.afterNumber = GrDiffLine.FILE;
      return new GrDiffGroup(GrDiffGroup.Type.BOTH, [line]);
    },

    /**
     * In order to show comments out of the bounds of the selected context,
     * treat them as separate chunks within the model so that the content (and
     * context surrounding it) renders correctly.
     * @param {Object} content The diff content object.
     * @return {Object} A new diff content object with regions split up.
     */
    _splitCommonGroupsWithComments: function(content) {
      var result = [];
      var leftLineNum = 0;
      var rightLineNum = 0;

      // If the context is set to "whole file", then break down the shared
      // chunks so they can be rendered incrementally. Note: this is not enabled
      // for any other context preference because manipulating the chunks in
      // this way violates assumptions by the context grouper logic.
      if (this.context === -1) {
        var newContent = [];
        content.forEach(function(group) {
          if (group.ab) {
            newContent.push.apply(newContent, this._breakdownGroup(group));
          } else {
            newContent.push(group);
          }
        }.bind(this));
        content = newContent;
      }

      // For each section in the diff.
      for (var i = 0; i < content.length; i++) {

        // If it isn't a common group, append it as-is and update line numbers.
        if (!content[i].ab) {
          if (content[i].a) {
            leftLineNum += content[i].a.length;
          }
          if (content[i].b) {
            rightLineNum += content[i].b.length;
          }

          this._breakdownGroup(content[i]).forEach(function(group) {
            result.push(group);
          });

          continue;
        }

        var chunk = content[i].ab;
        var currentChunk = {ab: []};

        // For each line in the common group.
        for (var j = 0; j < chunk.length; j++) {
          leftLineNum++;
          rightLineNum++;

          // If this line should not be collapsed.
          if (this.keyLocations[DiffSide.LEFT][leftLineNum] ||
              this.keyLocations[DiffSide.RIGHT][rightLineNum]) {

            // If any lines have been accumulated into the chunk leading up to
            // this non-collapse line, then add them as a chunk and start a new
            // one.
            if (currentChunk.ab && currentChunk.ab.length > 0) {
              result.push(currentChunk);
              currentChunk = {ab: []};
            }

            // Add the non-collapse line as its own chunk.
            result.push({ab: [chunk[j]]});
          } else {
            // Append the current line to the current chunk.
            currentChunk.ab.push(chunk[j]);
          }
        }

        if (currentChunk.ab && currentChunk.ab.length > 0) {
          result.push(currentChunk);
        }
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
    _normalizeIntralineHighlights: function(content, highlights) {
      var contentIndex = 0;
      var idx = 0;
      var normalized = [];
      for (var i = 0; i < highlights.length; i++) {
        var line = content[contentIndex] + '\n';
        var hl = highlights[i];
        var j = 0;
        while (j < hl[0]) {
          if (idx === line.length) {
            idx = 0;
            line = content[++contentIndex] + '\n';
            continue;
          }
          idx++;
          j++;
        }
        var lineHighlight = {
          contentIndex: contentIndex,
          startIndex: idx,
        };

        j = 0;
        while (line && j < hl[1]) {
          if (idx === line.length) {
            idx = 0;
            line = content[++contentIndex] + '\n';
            normalized.push(lineHighlight);
            lineHighlight = {
              contentIndex: contentIndex,
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
     * @param {!Object} A raw chunk from a diff response.
     * @return {!Array<!Array<!Object>>}
     */
    _breakdownGroup: function(group) {
      var key = null;
      if (group.a && !group.b) {
        key = 'a';
      } else if (group.b && !group.a) {
        key = 'b';
      } else if (group.ab) {
        key = 'ab';
      }

      if (!key) { return [group]; }

      return this._breakdown(group[key], MAX_GROUP_SIZE)
        .map(function(subgroupLines) {
          var subGroup = {};
          subGroup[key] = subgroupLines;
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
    _breakdown: function(array, size) {
      if (!array.length) { return []; }
      if (array.length < size) { return [array]; }

      var head = array.slice(0, array.length - size);
      var tail = array.slice(array.length - size);

      return this._breakdown(head, size).concat([tail]);
    },
  });
})();
