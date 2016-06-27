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

      _content: Object,
    },

    process: function(content) {
      return new Promise(function(resolve) {
        var groups = [];
        this._processContent(content, groups, this.context);
        this.groups = groups;
        resolve(groups);
      }.bind(this));
    },

    _processContent: function(content, groups, context) {
      this._appendFileComments(groups);

      context = content.length > 1 ? context : WHOLE_FILE;

      var lineNums = {
        left: 0,
        right: 0,
      };
      content = this._splitCommonGroupsWithComments(content, lineNums);
      for (var i = 0; i < content.length; i++) {
        var group = content[i];
        var lines = [];

        if (group[DiffGroupType.BOTH] !== undefined) {
          var rows = group[DiffGroupType.BOTH];
          this._appendCommonLines(rows, lines, lineNums);

          var hiddenRange = [context, rows.length - context];
          if (i === 0) {
            hiddenRange[0] = 0;
          } else if (i === content.length - 1) {
            hiddenRange[1] = rows.length;
          }

          if (context !== WHOLE_FILE && hiddenRange[1] - hiddenRange[0] > 0) {
            this._insertContextGroups(groups, lines, hiddenRange);
          } else {
            groups.push(new GrDiffGroup(GrDiffGroup.Type.BOTH, lines));
          }
          continue;
        }

        if (group[DiffGroupType.REMOVED] !== undefined) {
          var highlights = undefined;
          if (group[DiffHighlights.REMOVED] !== undefined) {
            highlights = this._normalizeIntralineHighlights(
                group[DiffGroupType.REMOVED],
                group[DiffHighlights.REMOVED]);
          }
          this._appendRemovedLines(group[DiffGroupType.REMOVED], lines,
              lineNums, highlights);
        }

        if (group[DiffGroupType.ADDED] !== undefined) {
          var highlights = undefined;
          if (group[DiffHighlights.ADDED] !== undefined) {
            highlights = this._normalizeIntralineHighlights(
              group[DiffGroupType.ADDED],
              group[DiffHighlights.ADDED]);
          }
          this._appendAddedLines(group[DiffGroupType.ADDED], lines,
              lineNums, highlights);
        }
        groups.push(new GrDiffGroup(GrDiffGroup.Type.DELTA, lines));
      }
    },

    _appendFileComments: function(groups) {
      var line = new GrDiffLine(GrDiffLine.Type.BOTH);
      line.beforeNumber = GrDiffLine.FILE;
      line.afterNumber = GrDiffLine.FILE;
      groups.push(new GrDiffGroup(GrDiffGroup.Type.BOTH, [line]));
    },

    /**
     * In order to show comments out of the bounds of the selected context,
     * treat them as separate chunks within the model so that the content (and
     * context surrounding it) renders correctly.
     */
    _splitCommonGroupsWithComments: function(content, lineNums) {
      var result = [];
      var leftLineNum = lineNums.left;
      var rightLineNum = lineNums.right;
      for (var i = 0; i < content.length; i++) {
        if (!content[i].ab) {
          result.push(content[i]);
          if (content[i].a) {
            leftLineNum += content[i].a.length;
          }
          if (content[i].b) {
            rightLineNum += content[i].b.length;
          }
          continue;
        }
        var chunk = content[i].ab;
        var currentChunk = {ab: []};
        for (var j = 0; j < chunk.length; j++) {
          leftLineNum++;
          rightLineNum++;

          if (this.keyLocations[DiffSide.LEFT][leftLineNum] ||
              this.keyLocations[DiffSide.RIGHT][rightLineNum]) {
            if (currentChunk.ab && currentChunk.ab.length > 0) {
              result.push(currentChunk);
              currentChunk = {ab: []};
            }
            result.push({ab: [chunk[j]]});
          } else {
            currentChunk.ab.push(chunk[j]);
          }
        }
        // != instead of !== because we want to cover both undefined and null.
        if (currentChunk.ab != null && currentChunk.ab.length > 0) {
          result.push(currentChunk);
        }
      }
      return result;
    },

    _appendCommonLines: function(rows, lines, lineNums) {
      for (var i = 0; i < rows.length; i++) {
        var line = new GrDiffLine(GrDiffLine.Type.BOTH);
        line.text = rows[i];
        line.beforeNumber = ++lineNums.left;
        line.afterNumber = ++lineNums.right;
        lines.push(line);
      }
    },

    _insertContextGroups: function(groups, lines, hiddenRange) {
      var linesBeforeCtx = lines.slice(0, hiddenRange[0]);
      var hiddenLines = lines.slice(hiddenRange[0], hiddenRange[1]);
      var linesAfterCtx = lines.slice(hiddenRange[1]);

      if (linesBeforeCtx.length > 0) {
        groups.push(new GrDiffGroup(GrDiffGroup.Type.BOTH, linesBeforeCtx));
      }

      var ctxLine = new GrDiffLine(GrDiffLine.Type.CONTEXT_CONTROL);
      ctxLine.contextGroup =
          new GrDiffGroup(GrDiffGroup.Type.BOTH, hiddenLines);
      groups.push(new GrDiffGroup(GrDiffGroup.Type.CONTEXT_CONTROL,
          [ctxLine]));

      if (linesAfterCtx.length > 0) {
        groups.push(new GrDiffGroup(GrDiffGroup.Type.BOTH, linesAfterCtx));
      }
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

    _appendRemovedLines: function(rows, lines, lineNums, opt_highlights) {
      for (var i = 0; i < rows.length; i++) {
        var line = new GrDiffLine(GrDiffLine.Type.REMOVE);
        line.text = rows[i];
        line.beforeNumber = ++lineNums.left;
        if (opt_highlights) {
          line.highlights = opt_highlights.filter(function(hl) {
            return hl.contentIndex === i;
          });
        }
        lines.push(line);
      }
    },

    _appendAddedLines: function(rows, lines, lineNums, opt_highlights) {
      for (var i = 0; i < rows.length; i++) {
        var line = new GrDiffLine(GrDiffLine.Type.ADD);
        line.text = rows[i];
        line.afterNumber = ++lineNums.right;
        if (opt_highlights) {
          line.highlights = opt_highlights.filter(function(hl) {
            return hl.contentIndex === i;
          });
        }
        lines.push(line);
      }
    },
  });
})();
