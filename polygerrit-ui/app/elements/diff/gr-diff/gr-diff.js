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

  Polymer({
    is: 'gr-diff',

    /**
     * Fired when the diff is rendered.
     *
     * @event render
     */

    properties: {
      availablePatches: Array,
      changeNum: String,
      /*
       * A single object to encompass basePatchNum and patchNum is used
       * so that both can be set at once without incremental observers
       * firing after each property changes.
       */
      patchRange: Object,
      path: String,
      prefs: {
        type: Object,
        notify: true,
      },
      projectConfig: Object,

      _prefsReady: {
        type: Object,
        readOnly: true,
        value: function() {
          return new Promise(function(resolve) {
            this._resolvePrefsReady = resolve;
          }.bind(this));
        },
      },
      _baseComments: Array,
      _comments: Array,
      _drafts: Array,
      _baseDrafts: Array,
      /**
       * Base (left side) comments and drafts grouped by line number.
       * Only used for initial rendering.
       */
      _groupedBaseComments: {
        type: Object,
        value: function() { return {}; },
      },
      /**
       * Comments and drafts (right side) grouped by line number.
       * Only used for initial rendering.
       */
      _groupedComments: {
        type: Object,
        value: function() { return {}; },
      },
      _diffResponse: Object,
      _diff: {
        type: Object,
        value: function() { return {}; },
      },
      _loggedIn: {
        type: Boolean,
        value: false,
      },
      _initialRenderComplete: {
        type: Boolean,
        value: false,
      },
      _loading: {
        type: Boolean,
        value: true,
      },
      _savedPrefs: Object,

      _diffRequestsPromise: Object,  // Used for testing.
      _diffPreferencesPromise: Object,  // Used for testing.
    },

    behaviors: [
      Gerrit.RESTClientBehavior,
    ],

    observers: [
      '_prefsChanged(prefs.*)',
    ],

    ready: function() {
      app.accountReady.then(function() {
        this._loggedIn = app.loggedIn;
      }.bind(this));
    },

    scrollToLine: function(lineNum) {
      // TODO(andybons): Should this always be the right side?
      this.$.rightDiff.scrollToLine(lineNum);
    },

    scrollToNextDiffChunk: function() {
      this.$.rightDiff.scrollToNextDiffChunk();
    },

    scrollToPreviousDiffChunk: function() {
      this.$.rightDiff.scrollToPreviousDiffChunk();
    },

    scrollToNextCommentThread: function() {
      this.$.rightDiff.scrollToNextCommentThread();
    },

    scrollToPreviousCommentThread: function() {
      this.$.rightDiff.scrollToPreviousCommentThread();
    },

    reload: function(changeNum, patchRange, path) {
      // If a diff takes a considerable amount of time to render, the previous
      // diff can end up showing up while the DOM is constructed. Clear the
      // content on a reload to prevent this.
      this._diff = {
        leftSide: [],
        rightSide: [],
      };

      var promises = [
        this._prefsReady,
        this.$.diffXHR.generateRequest().completes
      ];

      var basePatchNum = this.patchRange.basePatchNum;

      return app.accountReady.then(function() {
        promises.push(this._getCommentsAndDrafts(basePatchNum, app.loggedIn));
        this._diffRequestsPromise = Promise.all(promises).then(function() {
          this._render();
        }.bind(this)).catch(function(err) {
          alert('Oops. Something went wrong. Check the console and bug the ' +
              'PolyGerrit team for assistance.');
          throw err;
        });
      }.bind(this));
    },

    showDiffPreferences: function() {
      this.$.prefsOverlay.open();
    },

    _prefsChanged: function(changeRecord) {
      if (this._initialRenderComplete) {
        this._render();
      }
      this._resolvePrefsReady(changeRecord.base);
    },

    _render: function() {
      this._groupCommentsAndDrafts();
      this._processContent();

      // Allow for the initial rendering to complete before firing the event.
      this.async(function() {
        this.fire('render', null, {bubbles: false});
      }.bind(this), 1);

      this._initialRenderComplete = true;
    },

    _getCommentsAndDrafts: function(basePatchNum, loggedIn) {
      function onlyParent(c) { return c.side == 'PARENT'; }
      function withoutParent(c) { return c.side != 'PARENT'; }

      var promises = [];
      var commentsPromise = this.$.commentsXHR.generateRequest().completes;
      promises.push(commentsPromise.then(function(req) {
        var comments = req.response[this.path] || [];
        if (basePatchNum == 'PARENT') {
          this._baseComments = comments.filter(onlyParent);
        }
        this._comments = comments.filter(withoutParent);
      }.bind(this)));

      if (basePatchNum != 'PARENT') {
        commentsPromise = this.$.baseCommentsXHR.generateRequest().completes;
        promises.push(commentsPromise.then(function(req) {
          this._baseComments =
            (req.response[this.path] || []).filter(withoutParent);
        }.bind(this)));
      }

      if (!loggedIn) {
        this._baseDrafts = [];
        this._drafts = [];
        return Promise.all(promises);
      }

      var draftsPromise = this.$.draftsXHR.generateRequest().completes;
      promises.push(draftsPromise.then(function(req) {
        var drafts = req.response[this.path] || [];
        if (basePatchNum == 'PARENT') {
          this._baseDrafts = drafts.filter(onlyParent);
        }
        this._drafts = drafts.filter(withoutParent);
      }.bind(this)));

      if (basePatchNum != 'PARENT') {
        draftsPromise = this.$.baseDraftsXHR.generateRequest().completes;
        promises.push(draftsPromise.then(function(req) {
          this._baseDrafts =
              (req.response[this.path] || []).filter(withoutParent);
        }.bind(this)));
      }

      return Promise.all(promises);
    },

    _computeDiffPath: function(changeNum, patchNum, path) {
      return this.changeBaseURL(changeNum, patchNum) + '/files/' +
          encodeURIComponent(path) + '/diff';
    },

    _computeCommentsPath: function(changeNum, patchNum) {
      return this.changeBaseURL(changeNum, patchNum) + '/comments';
    },

    _computeDraftsPath: function(changeNum, patchNum) {
      return this.changeBaseURL(changeNum, patchNum) + '/drafts';
    },

    _computeDiffQueryParams: function(basePatchNum) {
      var params =  {
        context: 'ALL',
        intraline: null
      };
      if (basePatchNum != 'PARENT') {
        params.base = basePatchNum;
      }
      return params;
    },

    _handlePrefsTap: function(e) {
      e.preventDefault();

      // TODO(andybons): This is not supported in IE. Implement a polyfill.
      // NOTE: Object.assign is NOT automatically a deep copy. If prefs adds
      // an object as a value, it must be marked enumerable.
      this._savedPrefs = Object.assign({}, this.prefs);
      this.$.prefsOverlay.open();
    },

    _handlePrefsSave: function(e) {
      e.stopPropagation();
      var el = Polymer.dom(e).rootTarget;
      el.disabled = true;
      app.accountReady.then(function() {
        if (!this._loggedIn) {
          el.disabled = false;
          this.$.prefsOverlay.close();
          return;
        }
        this._saveDiffPreferences().then(function() {
          this.$.prefsOverlay.close();
          el.disabled = false;
        }.bind(this)).catch(function(err) {
          el.disabled = false;
          alert('Oops. Something went wrong. Check the console and bug the ' +
                'PolyGerrit team for assistance.');
          throw err;
        });
      }.bind(this));
    },

    _saveDiffPreferences: function() {
      var xhr = document.createElement('gr-request');
      this._diffPreferencesPromise = xhr.send({
        method: 'PUT',
        url: '/accounts/self/preferences.diff',
        body: this.prefs,
      });
      return this._diffPreferencesPromise;
    },

    _handlePrefsCancel: function(e) {
      e.stopPropagation();
      this.prefs = this._savedPrefs;
      this.$.prefsOverlay.close();
    },

    _handleExpandContext: function(e) {
      var ctx = e.detail.context;
      var contextControlIndex = -1;
      for (var i = ctx.start; i <= ctx.end; i++) {
        this._diff.leftSide[i].hidden = false;
        this._diff.rightSide[i].hidden = false;
        if (this._diff.leftSide[i].type == 'CONTEXT_CONTROL' &&
            this._diff.rightSide[i].type == 'CONTEXT_CONTROL') {
          contextControlIndex = i;
        }
      }
      this._diff.leftSide[contextControlIndex].hidden = true;
      this._diff.rightSide[contextControlIndex].hidden = true;

      this.$.leftDiff.hideElementsWithIndex(contextControlIndex);
      this.$.rightDiff.hideElementsWithIndex(contextControlIndex);

      this.$.leftDiff.renderLineIndexRange(ctx.start, ctx.end);
      this.$.rightDiff.renderLineIndexRange(ctx.start, ctx.end);
    },

    _handleThreadHeightChange: function(e) {
      var index = e.detail.index;
      var diffEl = Polymer.dom(e).rootTarget;
      var otherSide = diffEl == this.$.leftDiff ?
          this.$.rightDiff : this.$.leftDiff;

      var threadHeight = e.detail.height;
      var otherSideHeight;
      if (otherSide.content[index].type == 'COMMENT_THREAD') {
        otherSideHeight = otherSide.getRowNaturalHeight(index);
      } else {
        otherSideHeight = otherSide.getRowHeight(index);
      }
      var maxHeight = Math.max(threadHeight, otherSideHeight);
      this.$.leftDiff.setRowHeight(index, maxHeight);
      this.$.rightDiff.setRowHeight(index, maxHeight);
    },

    _handleAddDraft: function(e) {
      var insertIndex = e.detail.index + 1;
      var diffEl = Polymer.dom(e).rootTarget;
      var content = diffEl.content;
      if (content[insertIndex] &&
          content[insertIndex].type == 'COMMENT_THREAD') {
        // A thread is already here. Do nothing.
        return;
      }
      var comment = {
        type: 'COMMENT_THREAD',
        comments: [{
          __draft: true,
          __draftID: Math.random().toString(36),
          line: e.detail.line,
          path: this.path,
        }]
      };
      if (diffEl == this.$.leftDiff &&
          this.patchRange.basePatchNum == 'PARENT') {
        comment.comments[0].side = 'PARENT';
        comment.patchNum = this.patchRange.patchNum;
      }

      if (content[insertIndex] &&
          content[insertIndex].type == 'FILLER') {
        content[insertIndex] = comment;
        diffEl.rowUpdated(insertIndex);
      } else {
        content.splice(insertIndex, 0, comment);
        diffEl.rowInserted(insertIndex);
      }

      var otherSide = diffEl == this.$.leftDiff ?
          this.$.rightDiff : this.$.leftDiff;
      if (otherSide.content[insertIndex] == null ||
          otherSide.content[insertIndex].type != 'COMMENT_THREAD') {
        otherSide.content.splice(insertIndex, 0, {
          type: 'FILLER',
        });
        otherSide.rowInserted(insertIndex);
      }
    },

    _handleRemoveThread: function(e) {
      var diffEl = Polymer.dom(e).rootTarget;
      var otherSide = diffEl == this.$.leftDiff ?
          this.$.rightDiff : this.$.leftDiff;
      var index = e.detail.index;

      if (otherSide.content[index].type == 'FILLER') {
        otherSide.content.splice(index, 1);
        otherSide.rowRemoved(index);
        diffEl.content.splice(index, 1);
        diffEl.rowRemoved(index);
      } else if (otherSide.content[index].type == 'COMMENT_THREAD') {
        diffEl.content[index] = {type: 'FILLER'};
        diffEl.rowUpdated(index);
        var height = otherSide.setRowNaturalHeight(index);
        diffEl.setRowHeight(index, height);
      } else {
        throw Error('A thread cannot be opposite anything but filler or ' +
            'another thread');
      }
    },

    _processContent: function() {
      var leftSide = [];
      var rightSide = [];
      var initialLineNum = 0 + (this._diffResponse.content.skip || 0);
      var ctx = {
        hidingLines: false,
        lastNumLinesHidden: 0,
        left: {
          lineNum: initialLineNum,
        },
        right: {
          lineNum: initialLineNum,
        }
      };
      var content = this._breakUpCommonChunksWithComments(ctx,
          this._diffResponse.content);
      var context = this.prefs.context;
      if (context == -1) {
        // Show the entire file.
        context = Infinity;
      }
      for (var i = 0; i < content.length; i++) {
        if (i == 0) {
          ctx.skipRange = [0, context];
        } else if (i == content.length - 1) {
          ctx.skipRange = [context, 0];
        } else {
          ctx.skipRange = [context, context];
        }
        ctx.diffChunkIndex = i;
        this._addDiffChunk(ctx, content[i], leftSide, rightSide);
      }

      this._diff = {
        leftSide: leftSide,
        rightSide: rightSide,
      };
    },

    // In order to show comments out of the bounds of the selected context,
    // treat them as diffs within the model so that the content (and context
    // surrounding it) renders correctly.
    _breakUpCommonChunksWithComments: function(ctx, content) {
      var result = [];
      var leftLineNum = ctx.left.lineNum;
      var rightLineNum = ctx.right.lineNum;
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
          if (this._groupedBaseComments[leftLineNum] == null &&
              this._groupedComments[rightLineNum] == null) {
            currentChunk.ab.push(chunk[j]);
          } else {
            if (currentChunk.ab && currentChunk.ab.length > 0) {
              result.push(currentChunk);
              currentChunk = {ab: []};
            }
            // Append an annotation to indicate that this line should not be
            // highlighted even though it's implied with both `a` and `b`
            // defined. This is needed since there may be two lines that
            // should be highlighted but are equal (blank lines, for example).
            result.push({
              __noHighlight: true,
              a: [chunk[j]],
              b: [chunk[j]],
            });
          }
        }
        if (currentChunk.ab != null && currentChunk.ab.length > 0) {
          result.push(currentChunk);
        }
      }
      return result;
    },

    _groupCommentsAndDrafts: function() {
      this._baseDrafts.forEach(function(d) { d.__draft = true; });
      this._drafts.forEach(function(d) { d.__draft = true; });
      var allLeft = this._baseComments.concat(this._baseDrafts);
      var allRight = this._comments.concat(this._drafts);

      var leftByLine = {};
      var rightByLine = {};
      var mapFunc = function(byLine) {
        return function(c) {
          // File comments/drafts are grouped with line 1 for now.
          var line = c.line || 1;
          if (byLine[line] == null) {
            byLine[line] = [];
          }
          byLine[line].push(c);
        };
      };
      allLeft.forEach(mapFunc(leftByLine));
      allRight.forEach(mapFunc(rightByLine));

      this._groupedBaseComments = leftByLine;
      this._groupedComments = rightByLine;
    },

    _addContextControl: function(ctx, leftSide, rightSide) {
      var numLinesHidden = ctx.lastNumLinesHidden;
      var leftStart = leftSide.length - numLinesHidden;
      var leftEnd = leftSide.length;
      var rightStart = rightSide.length - numLinesHidden;
      var rightEnd = rightSide.length;
      if (leftStart != rightStart || leftEnd != rightEnd) {
        throw Error(
            'Left and right ranges for context control should be equal:' +
            'Left: [' + leftStart + ', ' + leftEnd + '] ' +
            'Right: [' + rightStart + ', ' + rightEnd + ']');
      }
      var obj = {
        type: 'CONTEXT_CONTROL',
        numLines: numLinesHidden,
        start: leftStart,
        end: leftEnd,
      };
      // NOTE: Be careful, here. This object is meant to be immutable. If the
      // object is altered within one side's array it will reflect the
      // alterations in another.
      leftSide.push(obj);
      rightSide.push(obj);
    },

    _addCommonDiffChunk: function(ctx, chunk, leftSide, rightSide) {
      for (var i = 0; i < chunk.ab.length; i++) {
        var numLines = Math.ceil(
            this._visibleLineLength(chunk.ab[i]) / this.prefs.line_length);
        var hidden = i >= ctx.skipRange[0] &&
            i < chunk.ab.length - ctx.skipRange[1];
        if (ctx.hidingLines && hidden == false) {
          // No longer hiding lines. Add a context control.
          this._addContextControl(ctx, leftSide, rightSide);
          ctx.lastNumLinesHidden = 0;
        }
        ctx.hidingLines = hidden;
        if (hidden) {
          ctx.lastNumLinesHidden++;
        }

        // Blank lines within a diff content array indicate a newline.
        leftSide.push({
          type: 'CODE',
          hidden: hidden,
          content: chunk.ab[i] || '\n',
          numLines: numLines,
          lineNum: ++ctx.left.lineNum,
        });
        rightSide.push({
          type: 'CODE',
          hidden: hidden,
          content: chunk.ab[i] || '\n',
          numLines: numLines,
          lineNum: ++ctx.right.lineNum,
        });

        this._addCommentsIfPresent(ctx, leftSide, rightSide);
      }
      if (ctx.lastNumLinesHidden > 0) {
        this._addContextControl(ctx, leftSide, rightSide);
      }
    },

    _addDiffChunk: function(ctx, chunk, leftSide, rightSide) {
      if (chunk.ab) {
        this._addCommonDiffChunk(ctx, chunk, leftSide, rightSide);
        return;
      }

      var leftHighlights = [];
      if (chunk.edit_a) {
        leftHighlights =
            this._normalizeIntralineHighlights(chunk.a, chunk.edit_a);
      }
      var rightHighlights = [];
      if (chunk.edit_b) {
        rightHighlights =
            this._normalizeIntralineHighlights(chunk.b, chunk.edit_b);
      }

      var aLen = (chunk.a && chunk.a.length) || 0;
      var bLen = (chunk.b && chunk.b.length) || 0;
      var maxLen = Math.max(aLen, bLen);
      for (var i = 0; i < maxLen; i++) {
        var hasLeftContent = chunk.a && i < chunk.a.length;
        var hasRightContent = chunk.b && i < chunk.b.length;
        var leftContent = hasLeftContent ? chunk.a[i] : '';
        var rightContent = hasRightContent ? chunk.b[i] : '';
        var highlight = !chunk.__noHighlight;
        var maxNumLines = this._maxLinesSpanned(leftContent, rightContent);
        if (hasLeftContent) {
          leftSide.push({
            type: 'CODE',
            content: leftContent || '\n',
            numLines: maxNumLines,
            lineNum: ++ctx.left.lineNum,
            highlight: highlight,
            intraline: highlight && leftHighlights.filter(function(hl) {
              return hl.contentIndex == i;
            }),
          });
        } else {
          leftSide.push({
            type: 'FILLER',
            numLines: maxNumLines,
          });
        }
        if (hasRightContent) {
          rightSide.push({
            type: 'CODE',
            content: rightContent || '\n',
            numLines: maxNumLines,
            lineNum: ++ctx.right.lineNum,
            highlight: highlight,
            intraline: highlight && rightHighlights.filter(function(hl) {
              return hl.contentIndex == i;
            }),
          });
        } else {
          rightSide.push({
            type: 'FILLER',
            numLines: maxNumLines,
          });
        }
        this._addCommentsIfPresent(ctx, leftSide, rightSide);
      }
    },

    _addCommentsIfPresent: function(ctx, leftSide, rightSide) {
      var leftComments = this._groupedBaseComments[ctx.left.lineNum];
      var rightComments = this._groupedComments[ctx.right.lineNum];
      if (leftComments) {
        var thread = {
          type: 'COMMENT_THREAD',
          comments: leftComments,
        };
        if (this.patchRange.basePatchNum == 'PARENT') {
          thread.patchNum = this.patchRange.patchNum;
        }
        leftSide.push(thread);
      }
      if (rightComments) {
        rightSide.push({
          type: 'COMMENT_THREAD',
          comments: rightComments,
        });
      }
      if (leftComments && !rightComments) {
        rightSide.push({type: 'FILLER'});
      } else if (!leftComments && rightComments) {
        leftSide.push({type: 'FILLER'});
      }
      this._groupedBaseComments[ctx.left.lineNum] = null;
      this._groupedComments[ctx.right.lineNum] = null;
    },

    // The `highlights` array consists of a list of <skip length, mark length>
    // pairs, where the skip length is the number of characters between the
    // end of the previous edit and the start of this edit, and the mark
    // length is the number of edited characters following the skip. The start
    // of the edits is from the beginning of the related diff content lines.
    //
    // Note that the implied newline character at the end of each line is
    // included in the length calculation, and thus it is possible for the
    // edits to span newlines.
    //
    // A line highlight object consists of three fields:
    // - contentIndex: The index of the diffChunk `content` field (the line
    //   being referred to).
    // - startIndex: Where the highlight should begin.
    // - endIndex: (optional) Where the highlight should end. If omitted, the
    //   highlight is meant to be a continuation onto the next line.
    _normalizeIntralineHighlights: function(content, highlights) {
      var contentIndex = 0;
      var idx = 0;
      var normalized = [];
      for (var i = 0; i < highlights.length; i++) {
        var line = content[contentIndex] + '\n';
        var hl = highlights[i];
        var j = 0;
        while (j < hl[0]) {
          if (idx == line.length) {
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
          if (idx == line.length) {
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

    _visibleLineLength: function(contents) {
      // http://jsperf.com/performance-of-match-vs-split
      var numTabs = contents.split('\t').length - 1;
      return contents.length - numTabs + (this.prefs.tab_size * numTabs);
    },

    _maxLinesSpanned: function(left, right) {
      return Math.max(
          Math.ceil(this._visibleLineLength(left) / this.prefs.line_length),
          Math.ceil(this._visibleLineLength(right) / this.prefs.line_length));
    },
  });
})();
