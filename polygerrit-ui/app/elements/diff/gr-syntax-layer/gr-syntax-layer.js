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

  var LANGUAGE_MAP = {
    'application/json': 'json',
    'text/css': 'css',
    'text/html': 'html',
    'text/javascript': 'js',
    'text/x-c++src': 'cpp',
    'text/x-go': 'go',
    'text/x-haskell': 'haskell',
    'text/x-java': 'java',
    'text/x-markdown': 'markdown',
    'text/x-perl': 'perl',
    'text/x-python': 'python',
    'text/x-sh': 'bash',
    'text/x-sql': 'sql',
    'text/x-scala': 'scala',
  };

  Polymer({
    is: 'gr-syntax-layer',

    properties: {
      diff: {type: Object, observer: '_diffChanged'},
      _baseRanges: {
        type: Array,
        value: function() { return []; },
      },
      _revisionRanges: {
        type: Array,
        value: function() { return []; },
      },

      _baseLanguage: String,
      _revisionLanguage: String,

      _listeners: {
        type: Array,
        value: function() { return []; },
      },

      _processHandle: Number,
    },

    attached: function() {
      hljs.configure({classPrefix: 'gr-diff gr-syntax gr-syntax-'});
    },

    addListener: function(fn) {
      this.push('_listeners', fn);
    },

    annotate: function(el, line) {
      var side;
      if (line.type === GrDiffLine.Type.REMOVE || (
          line.type === GrDiffLine.Type.BOTH &&
          el.getAttribute('data-side') !== 'right')) {
        side = 'left';
      } else if (line.type === GrDiffLine.Type.ADD || (
          el.getAttribute('data-side') !== 'left')) {
        side = 'right';
      }

      var ranges = [];
      if (side === 'left' && this._baseRanges.length >= line.beforeNumber) {
        ranges = this._baseRanges[line.beforeNumber - 1] || [];
      } else if (side === 'right' &&
          this._revisionRanges.length >= line.afterNumber) {
        ranges = this._revisionRanges[line.afterNumber - 1] || [];
      }
      ranges.forEach(function(range) {
        GrAnnotation.annotateElement(
            el, range.start, range.length, range.className);
      });
    },

    process: function() {
      if (!this.diff.content.length) { return Promise.resolve(); }

      this.cancel();

      if (this.diff.meta_a) {
        this._baseLanguage = LANGUAGE_MAP[this.diff.meta_a.content_type];
      }
      if (this.diff.meta_b) {
        this._revisionLanguage = LANGUAGE_MAP[this.diff.meta_b.content_type];
      }

      var state = {
        sectionIndex: 0,
        lineIndex: 0,
        baseContext: undefined,
        revisionContext: undefined,
        lineNums: {left: 1, right: 1},
        lastNotify: {left: 1, right: 1},
      };
      this._baseRanges = [];
      this._revisionRanges = [];

      return new Promise(function(resolve) {
        var nextStep = function() {
          this._processHandle = null;
          this._processNextLine(state);

          // Move to the next line in the section.
          state.lineIndex++;

          // If the section has been exhausted, move to the next one.
          if (this._isSectionDone(state)) {
            state.lineIndex = 0;
            state.sectionIndex++;
          }

          // If all sections have been exhausted, finish.
          if (state.sectionIndex >= this.diff.content.length) {
            resolve();
            this._notify(state);
            return;
          }

          if (state.sectionIndex !== 0 && state.lineIndex % 100 === 0) {
            this._notify(state);
            this._processHandle = this.async(nextStep, 10);
          } else {
            nextStep.call(this);
          }
        };

        this._processHandle = this.async(nextStep, 1);
      }.bind(this));
    },

    cancel: function() {
      if (this._processHandle) {
        this.cancelAsync(this._processHandle);
        this._processHandle = null;
      }
    },

    _diffChanged: function() {
      this.cancel();
      this._baseRanges = [];
      this._revisionRanges = [];
    },

    _rangesFromString: function(str) {
      var div = document.createElement('div');
      div.innerHTML = str;
      return this._rangesFromElement(div, 0);
    },

    _rangesFromElement: function(elem, offset) {
      var result = [];
      for (var i = 0; i < elem.childNodes.length; i++) {
        var node = elem.childNodes[i];
        var nodeLength = GrAnnotation.getLength(node);
        // Npte: HLJS may emit a span with class undefined when it's confused.
        if (node.tagName === 'SPAN' && node.className !== 'undefined') {
          result.push({
            start: offset,
            length: nodeLength,
            className: node.className,
          });
          if (node.children.length) {
            result = result.concat(this._rangesFromElement(node, offset));
          }
        }
        offset += nodeLength;
      }
      return result;
    },

    _processNextLine: function(state) {
      var baseLine = undefined;
      var revisionLine = undefined;

      var section = this.diff.content[state.sectionIndex];
      if (section.ab) {
        baseLine = section.ab[state.lineIndex];
        revisionLine = section.ab[state.lineIndex];
        state.lineNums.left++;
        state.lineNums.right++;
      } else {
        if (section.a && section.a.length > state.lineIndex) {
          baseLine = section.a[state.lineIndex];
          state.lineNums.left++;
        }
        if (section.b && section.b.length > state.lineIndex) {
          revisionLine = section.b[state.lineIndex];
          state.lineNums.right++;
        }
      }

      var result;
      if (this._baseLanguage && baseLine !== undefined) {
        result = hljs.highlight(this._baseLanguage, baseLine, true,
            state.baseContext);
        this.push('_baseRanges', this._rangesFromString(result.value));
        state.baseContext = result.top;
      }

      if (this._revisionLanguage && revisionLine !== undefined) {
        result = hljs.highlight(this._revisionLanguage, revisionLine, true,
            state.revisionContext);
        this.push('_revisionRanges', this._rangesFromString(result.value));
        state.revisionContext = result.top;
      }
    },

    _isSectionDone: function(state) {
      var section = this.diff.content[state.sectionIndex]
      if (section.ab) {
        return state.lineIndex >= section.ab.length;
      } else {
        return (!section.a || state.lineIndex >= section.a.length) &&
            (!section.b || state.lineIndex >= section.b.length);
      }
    },

    _notify: function(state) {
      if (state.lineNums.left - state.lastNotify.left) {
        this._notifyRange(
          state.lastNotify.left,
          state.lineNums.left,
          'left');
        state.lastNotify.left = state.lineNums.left;
      }
      if (state.lineNums.right - state.lastNotify.right) {
        this._notifyRange(
          state.lastNotify.right,
          state.lineNums.right,
          'right');
        state.lastNotify.right = state.lineNums.right;
      }
    },

    _notifyRange: function(start, end, side) {
      this._listeners.forEach(function(fn) {
        fn(start, end, side);
      });
    },
  });
})();
