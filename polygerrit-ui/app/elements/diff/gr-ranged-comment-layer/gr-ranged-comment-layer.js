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

  var HOVER_PATH_PATTERN = /^comments\.(left|right)\.\#(\d+)\.__hovering$/;
  var SPLICE_PATH_PATTERN = /^comments\.(left|right)\.splices$/;

  var RANGE_HIGHLIGHT = 'range';
  var HOVER_HIGHLIGHT = 'rangeHighlight';

  var NORMALIZE_RANGE_EVENT = 'normalize-range';

  Polymer({
    is: 'gr-ranged-comment-layer',

    properties: {
      comments: Object,
      _listeners: {
        type: Array,
        value: function() { return []; },
      },
      _commentMap: {
        type: Object,
        value: function() { return {left: [], right: []}; },
      },
    },

    observers: [
      '_handleCommentChange(comments.*)',
    ],

    /**
     * Layer method to add annotations to a line.
     * @param {HTMLElement} el The DIV.contentText element to apply the
     *     annotation to.
     * @param {GrDiffLine} line The line object.
     */
    annotate: function(el, line) {
      var ranges = [];
      if (line.type === GrDiffLine.Type.REMOVE || (
          line.type === GrDiffLine.Type.BOTH &&
          el.getAttribute('data-side') !== 'right')) {
        ranges = ranges.concat(this._getRangesForLine(line, 'left'));
      }
      if (line.type === GrDiffLine.Type.ADD || (
          line.type === GrDiffLine.Type.BOTH &&
          el.getAttribute('data-side') !== 'left')) {
        ranges = ranges.concat(this._getRangesForLine(line, 'right'));
      }

      ranges.forEach(function(range) {
        GrAnnotation.annotateElement(el, range.start,
            range.end - range.start,
            range.hovering ? HOVER_HIGHLIGHT : RANGE_HIGHLIGHT);
      });
    },

    /**
     * Register a listener for layer updates.
     * @param {Function<Number, Number, String>} fn The update handler function.
     *     Should accept as arguments the line numbers for the start and end of
     *     the update and the side as a string.
     */
    addListener: function(fn) {
      this._listeners.push(fn);
    },

    /**
     * Notify Layer listeners of changes to annotations.
     * @param {Number} start The line where the update starts.
     * @param {Number} end The line where the update ends.
     * @param {String} side The side of the update. ('left' or 'right')
     */
    _notifyUpdateRange: function(start, end, side) {
      this._listeners.forEach(function(listener) {
        listener(start, end, side);
      });
    },

    /**
     * Handle change in the comments by updating the comment maps and by
     * emitting appropriate update notifications.
     * @param {Object} record The change record.
     */
    _handleCommentChange: function(record) {
      if (!record.path) { return; }

      // If the entire set of comments was changed.
      if (record.path === 'comments') {
        this._commentMap.left = this._computeCommentMap(this.comments.left);
        this._commentMap.right = this._computeCommentMap(this.comments.right);
        return;
      }

      // If the change only changed the `hovering` property of a comment.
      var match = record.path.match(HOVER_PATH_PATTERN);
      if (match) {
        var side = match[1];
        var index = match[2];
        var comment = this.comments[side][index];
        if (comment && comment.range) {
          this._commentMap[side] = this._computeCommentMap(this.comments[side]);
          this._notifyUpdateRange(
              comment.range.start_line, comment.range.end_line, side);
        }
        return;
      }

      // If comments were spliced in or out.
      match = record.path.match(SPLICE_PATH_PATTERN);
      if (match) {
        var side = match[1];
        this._commentMap[side] = this._computeCommentMap(this.comments[side]);
        this._handleCommentSplice(record.value, side);
      }
    },

    /**
     * Take a list of comments and return a sparse list mapping line numbers to
     * partial ranges. Uses an end-character-index of -1 to indicate the end of
     * the line.
     * @param {Array<Object>} commentList The list of comments.
     * @return {Object} The sparse list.
     */
    _computeCommentMap: function(commentList) {
      var result = {};
      commentList.forEach(function(comment) {
        if (!comment.range) { return; }
        var range = comment.range;
        for (var line = range.start_line; line <= range.end_line; line++) {
          if (!result[line]) { result[line] = []; }
          result[line].push({
            comment: comment,
            start: line === range.start_line ? range.start_character : 0,
            end: line === range.end_line ? range.end_character : -1,
          });
        }
      });
      return result;
    },

    /**
     * Translate a splice record into range update notifications.
     */
    _handleCommentSplice: function(record, side) {
      if (!record || !record.indexSplices) { return; }
      record.indexSplices.forEach(function(splice) {
        var ranges = splice.removed.length ?
          splice.removed.map(function(c) { return c.range; }) :
          [splice.object[splice.index].range];
        ranges.forEach(function(range) {
          if (!range) { return; }
          this._notifyUpdateRange(range.start_line, range.end_line, side);
        }.bind(this));
      }.bind(this));
    },

    _getRangesForLine: function(line, side) {
      var lineNum = side === 'left' ? line.beforeNumber : line.afterNumber;
      var ranges = this.get(['_commentMap', side, lineNum]) || [];
      return ranges
          .map(function(range) {
            range = {
              start: range.start,
              end: range.end === -1 ? line.text.length : range.end,
              hovering: !!range.comment.__hovering,
            };

            // Normalize invalid ranges where the start is after the end but the
            // start still makes sense. Set the end to the end of the line.
            // @see Issue 5744
            if (range.start >= range.end && range.start < line.text.length) {
              range.end = line.text.length;
              this.$.reporting.reportInteraction(NORMALIZE_RANGE_EVENT,
                  'Modified invalid comment range on l.' + lineNum +
                  ' of the ' + side + ' side');
            }

            return range;
          }.bind(this))
          .sort(function(a, b) {
            // Sort the ranges so that hovering highlights are on top.
            return a.hovering && !b.hovering ? 1 : 0;
          });
    },
  });
})();
