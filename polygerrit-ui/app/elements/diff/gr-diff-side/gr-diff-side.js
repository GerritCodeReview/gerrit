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

  var CharCode = {
    LESS_THAN: '<'.charCodeAt(0),
    GREATER_THAN: '>'.charCodeAt(0),
    AMPERSAND: '&'.charCodeAt(0),
    SEMICOLON: ';'.charCodeAt(0),
  };

  var TAB_REGEX = /\t/g;

  Polymer({
    is: 'gr-diff-side',

    /**
     * Fired when an expand context control is clicked.
     *
     * @event expand-context
     */

    /**
     * Fired when a thread's height is changed.
     *
     * @event thread-height-change
     */

    /**
     * Fired when a draft should be added.
     *
     * @event add-draft
     */

    /**
     * Fired when a thread is removed.
     *
     * @event remove-thread
     */

    properties: {
      canComment: {
        type: Boolean,
        value: false,
      },
      content: {
        type: Array,
        notify: true,
        observer: '_contentChanged',
      },
      prefs: {
        type: Object,
        value: function() { return {}; },
      },
      changeNum: String,
      patchNum: String,
      path: String,
      projectConfig: {
        type: Object,
        observer: '_projectConfigChanged',
      },

      _lineFeedHTML: {
        type: String,
        value: '<span class="style-scope gr-diff-side br"></span>',
        readOnly: true,
      },
      _highlightStartTag: {
        type: String,
        value: '<hl class="style-scope gr-diff-side">',
        readOnly: true,
      },
      _highlightEndTag: {
        type: String,
        value: '</hl>',
        readOnly: true,
      },
      _diffChunkLineNums: {
        type: Array,
        value: function() { return []; },
      },
      _commentThreadLineNums: {
        type: Array,
        value: function() { return []; },
      },
      _focusedLineNum: {
        type: Number,
        value: 1,
      },
    },

    listeners: {
      'tap': '_tapHandler',
    },

    observers: [
      '_prefsChanged(prefs.*)',
    ],

    rowInserted: function(index) {
      this.renderLineIndexRange(index, index);
      this._updateDOMIndices();
      this._updateJumpIndices();
    },

    rowRemoved: function(index) {
      var removedEls = Polymer.dom(this.root).querySelectorAll(
          '[data-index="' + index + '"]');
      for (var i = 0; i < removedEls.length; i++) {
        removedEls[i].parentNode.removeChild(removedEls[i]);
      }
      this._updateDOMIndices();
      this._updateJumpIndices();
    },

    rowUpdated: function(index) {
      var removedEls = Polymer.dom(this.root).querySelectorAll(
          '[data-index="' + index + '"]');
      for (var i = 0; i < removedEls.length; i++) {
        removedEls[i].parentNode.removeChild(removedEls[i]);
      }
      this.renderLineIndexRange(index, index);
    },

    scrollToLine: function(lineNum) {
      if (isNaN(lineNum) || lineNum < 1) { return; }

      var el = this.$$('.numbers .lineNum[data-line-num="' + lineNum + '"]');
      if (!el) { return; }

      // Calculate where the line is relative to the window.
      var top = el.offsetTop;
      for (var offsetParent = el.offsetParent;
           offsetParent;
           offsetParent = offsetParent.offsetParent) {
        top += offsetParent.offsetTop;
      }

      // Scroll the element to the middle of the window. Dividing by a third
      // instead of half the inner height feels a bit better otherwise the
      // element appears to be below the center of the window even when it
      // isn't.
      window.scrollTo(0, top - (window.innerHeight / 3) - el.offsetHeight);
    },

    scrollToNextDiffChunk: function() {
      this._scrollToNextChunkOrThread(this._diffChunkLineNums);
    },

    scrollToPreviousDiffChunk: function() {
      this._scrollToPreviousChunkOrThread(this._diffChunkLineNums);
    },

    scrollToNextCommentThread: function() {
      this._scrollToNextChunkOrThread(this._commentThreadLineNums);
    },

    scrollToPreviousCommentThread: function() {
      this._scrollToPreviousChunkOrThread(this._commentThreadLineNums);
    },

    renderLineIndexRange: function(startIndex, endIndex) {
      this._render(this.content, startIndex, endIndex);
    },

    hideElementsWithIndex: function(index) {
      var els = Polymer.dom(this.root).querySelectorAll(
          '[data-index="' + index + '"]');
      for (var i = 0; i < els.length; i++) {
        els[i].setAttribute('hidden', true);
      }
    },

    getRowHeight: function(index) {
      var row = this.content[index];
      // Filler elements should not be taken into account when determining
      // height calculations.
      if (row.type == 'FILLER') {
        return 0;
      }
      if (row.height != null) {
        return row.height;
      }

      var selector = '[data-index="' + index + '"]';
      var els = Polymer.dom(this.root).querySelectorAll(selector);
      if (els.length != 2) {
        throw Error('Rows should only consist of two elements');
      }
      return Math.max(els[0].offsetHeight, els[1].offsetHeight);
    },

    getRowNaturalHeight: function(index) {
      var contentEl = this.$$('.content [data-index="' + index + '"]');
      return contentEl.naturalHeight || contentEl.offsetHeight;
    },

    setRowNaturalHeight: function(index) {
      var lineEl = this.$$('.numbers [data-index="' + index + '"]');
      var contentEl = this.$$('.content [data-index="' + index + '"]');
      contentEl.style.height = null;
      var height = contentEl.offsetHeight;
      lineEl.style.height = height + 'px';
      this.content[index].height = height;
      return height;
    },

    setRowHeight: function(index, height) {
      var selector = '[data-index="' + index + '"]';
      var els = Polymer.dom(this.root).querySelectorAll(selector);
      for (var i = 0; i < els.length; i++) {
        els[i].style.height = height + 'px';
      }
      this.content[index].height = height;
    },

    _scrollToNextChunkOrThread: function(lineNums) {
      for (var i = 0; i < lineNums.length; i++) {
        if (lineNums[i] > this._focusedLineNum) {
          this._focusedLineNum = lineNums[i];
          this.scrollToLine(this._focusedLineNum);
          return;
        }
      }
    },

    _scrollToPreviousChunkOrThread: function(lineNums) {
      for (var i = lineNums.length - 1; i >= 0; i--) {
        if (this._focusedLineNum > lineNums[i]) {
          this._focusedLineNum = lineNums[i];
          this.scrollToLine(this._focusedLineNum);
          return;
        }
      }
    },

    _updateJumpIndices: function() {
      this._commentThreadLineNums = [];
      this._diffChunkLineNums = [];
      var inHighlight = false;
      for (var i = 0; i < this.content.length; i++) {
        switch (this.content[i].type) {
          case 'COMMENT_THREAD':
            this._commentThreadLineNums.push(
                this.content[i].comments[0].line);
            break;
          case 'CODE':
            // Only grab the first line of the highlighted chunk.
            if (!inHighlight && this.content[i].highlight) {
              this._diffChunkLineNums.push(this.content[i].lineNum);
              inHighlight = true;
            } else if (!this.content[i].highlight) {
              inHighlight = false;
            }
            break;
        }
      }
    },

    _updateDOMIndices: function() {
      // There is no way to select elements with a data-index greater than a
      // given value. For now, just update all DOM elements.
      var lineEls = Polymer.dom(this.root).querySelectorAll(
          '.numbers [data-index]');
      var contentEls = Polymer.dom(this.root).querySelectorAll(
          '.content [data-index]');
      if (lineEls.length != contentEls.length) {
        throw Error(
            'There must be the same number of line and content elements');
      }
      var index = 0;
      for (var i = 0; i < this.content.length; i++) {
        if (this.content[i].hidden) { continue; }

        lineEls[index].setAttribute('data-index', i);
        contentEls[index].setAttribute('data-index', i);
        index++;
      }
    },

    _prefsChanged: function(changeRecord) {
      var prefs = changeRecord.base;
      this.$.content.style.width = prefs.line_length + 'ch';
    },

    _projectConfigChanged: function(projectConfig) {
      var threadEls =
          Polymer.dom(this.root).querySelectorAll('gr-diff-comment-thread');
      for (var i = 0; i < threadEls.length; i++) {
        threadEls[i].projectConfig = projectConfig;
      }
    },

    _contentChanged: function(diff) {
      this._clearChildren(this.$.numbers);
      this._clearChildren(this.$.content);
      this._render(diff, 0, diff.length - 1);
      this._updateJumpIndices();
    },

    _computeContainerClass: function(canComment) {
      return 'container' + (canComment ? ' canComment' : '');
    },

    _tapHandler: function(e) {
      var lineEl = Polymer.dom(e).rootTarget;
      if (!this.canComment || !lineEl.classList.contains('lineNum')) {
        return;
      }

      e.preventDefault();
      var index = parseInt(lineEl.getAttribute('data-index'), 10);
      var line = parseInt(lineEl.getAttribute('data-line-num'), 10);
      this.fire('add-draft', {
        index: index,
        line: line
      }, {bubbles: false});
    },

    _clearChildren: function(el) {
      while (el.firstChild) {
        el.removeChild(el.firstChild);
      }
    },

    _handleContextControlClick: function(context, e) {
      e.preventDefault();
      this.fire('expand-context', {context: context}, {bubbles: false});
    },

    _render: function(diff, startIndex, endIndex) {
      var beforeLineEl;
      var beforeContentEl;
      if (endIndex != diff.length - 1) {
        beforeLineEl = this.$$('.numbers [data-index="' + endIndex + '"]');
        beforeContentEl = this.$$('.content [data-index="' + endIndex + '"]');
        if (!beforeLineEl && !beforeContentEl) {
          // `endIndex` may be present within the model, but not in the DOM.
          // Insert it before its successive element.
          beforeLineEl = this.$$(
              '.numbers [data-index="' + (endIndex + 1) + '"]');
          beforeContentEl = this.$$(
              '.content [data-index="' + (endIndex + 1) + '"]');
        }
      }

      for (var i = startIndex; i <= endIndex; i++) {
        if (diff[i].hidden) { continue; }

        switch (diff[i].type) {
          case 'CODE':
            this._renderCode(diff[i], i, beforeLineEl, beforeContentEl);
            break;
          case 'FILLER':
            this._renderFiller(diff[i], i, beforeLineEl, beforeContentEl);
            break;
          case 'CONTEXT_CONTROL':
            this._renderContextControl(diff[i], i, beforeLineEl,
                beforeContentEl);
            break;
          case 'COMMENT_THREAD':
            this._renderCommentThread(diff[i], i, beforeLineEl,
                beforeContentEl);
            break;
        }
      }
    },

    _handleCommentThreadHeightChange: function(e) {
      var threadEl = Polymer.dom(e).rootTarget;
      var index = parseInt(threadEl.getAttribute('data-index'), 10);
      this.content[index].height = e.detail.height;
      var lineEl = this.$$('.numbers [data-index="' + index + '"]');
      lineEl.style.height = e.detail.height + 'px';
      this.fire('thread-height-change', {
        index: index,
        height: e.detail.height,
      }, {bubbles: false});
    },

    _handleCommentThreadDiscard: function(e) {
      var threadEl = Polymer.dom(e).rootTarget;
      var index = parseInt(threadEl.getAttribute('data-index'), 10);
      this.fire('remove-thread', {index: index}, {bubbles: false});
    },

    _renderCommentThread: function(thread, index, beforeLineEl,
        beforeContentEl) {
      var lineEl = this._createElement('div', 'commentThread');
      lineEl.classList.add('filler');
      lineEl.setAttribute('data-index', index);
      var threadEl = document.createElement('gr-diff-comment-thread');
      threadEl.addEventListener('height-change',
          this._handleCommentThreadHeightChange.bind(this));
      threadEl.addEventListener('discard',
          this._handleCommentThreadDiscard.bind(this));
      threadEl.setAttribute('data-index', index);
      threadEl.changeNum = this.changeNum;
      threadEl.patchNum = thread.patchNum || this.patchNum;
      threadEl.path = this.path;
      threadEl.comments = thread.comments;
      threadEl.projectConfig = this.projectConfig;

      this.$.numbers.insertBefore(lineEl, beforeLineEl);
      this.$.content.insertBefore(threadEl, beforeContentEl);
    },

    _renderContextControl: function(control, index, beforeLineEl,
        beforeContentEl) {
      var lineEl = this._createElement('div', 'contextControl');
      lineEl.setAttribute('data-index', index);
      lineEl.textContent = '@@';
      var contentEl = this._createElement('div', 'contextControl');
      contentEl.setAttribute('data-index', index);
      var a = this._createElement('a');
      a.href = '#';
      a.textContent = 'Show ' + control.numLines + ' common ' +
          (control.numLines == 1 ? 'line' : 'lines') + '...';
      a.addEventListener('click',
          this._handleContextControlClick.bind(this, control));
      contentEl.appendChild(a);

      this.$.numbers.insertBefore(lineEl, beforeLineEl);
      this.$.content.insertBefore(contentEl, beforeContentEl);
    },

    _renderFiller: function(filler, index, beforeLineEl, beforeContentEl) {
      var lineFillerEl = this._createElement('div', 'filler');
      lineFillerEl.setAttribute('data-index', index);
      var fillerEl = this._createElement('div', 'filler');
      fillerEl.setAttribute('data-index', index);
      var numLines = filler.numLines || 1;

      lineFillerEl.textContent = '\n'.repeat(numLines);
      for (var i = 0; i < numLines; i++) {
        var newlineEl = this._createElement('span', 'br');
        fillerEl.appendChild(newlineEl);
      }

      this.$.numbers.insertBefore(lineFillerEl, beforeLineEl);
      this.$.content.insertBefore(fillerEl, beforeContentEl);
    },

    _renderCode: function(code, index, beforeLineEl, beforeContentEl) {
      var lineNumEl = this._createElement('div', 'lineNum');
      lineNumEl.setAttribute('data-line-num', code.lineNum);
      lineNumEl.setAttribute('data-index', index);
      var numLines = code.numLines || 1;
      lineNumEl.textContent = code.lineNum + '\n'.repeat(numLines);

      var contentEl = this._createElement('div', 'code');
      contentEl.setAttribute('data-line-num', code.lineNum);
      contentEl.setAttribute('data-index', index);

      if (code.highlight) {
        contentEl.classList.add(code.intraline.length > 0 ?
            'lightHighlight' : 'darkHighlight');
      }

      var html = util.escapeHTML(code.content);
      if (code.highlight && code.intraline.length > 0) {
        html = this._addIntralineHighlights(code.content, html,
            code.intraline);
      }
      if (numLines > 1) {
        html = this._addNewLines(code.content, html, numLines);
      }
      html = this._addTabWrappers(code.content, html);

      // If the html is equivalent to the text then it didn't get highlighted
      // or escaped. Use textContent which is faster than innerHTML.
      if (code.content == html) {
        contentEl.textContent = code.content;
      } else {
        contentEl.innerHTML = html;
      }

      this.$.numbers.insertBefore(lineNumEl, beforeLineEl);
      this.$.content.insertBefore(contentEl, beforeContentEl);
    },

    // Advance `index` by the appropriate number of characters that would
    // represent one source code character and return that index. For
    // example, for source code '<span>' the escaped html string is
    // '&lt;span&gt;'. Advancing from index 0 on the prior html string would
    // return 4, since &lt; maps to one source code character ('<').
    _advanceChar: function(html, index) {
      // Any tags don't count as characters
      while (index < html.length &&
             html.charCodeAt(index) == CharCode.LESS_THAN) {
        while (index < html.length &&
               html.charCodeAt(index) != CharCode.GREATER_THAN) {
          index++;
        }
        index++;  // skip the ">" itself
      }
      // An HTML entity (e.g., &lt;) counts as one character.
      if (index < html.length &&
          html.charCodeAt(index) == CharCode.AMPERSAND) {
        while (index < html.length &&
               html.charCodeAt(index) != CharCode.SEMICOLON) {
          index++;
        }
      }
      return index + 1;
    },

    _addIntralineHighlights: function(content, html, highlights) {
      var startTag = this._highlightStartTag;
      var endTag = this._highlightEndTag;

      for (var i = 0; i < highlights.length; i++) {
        var hl = highlights[i];

        var htmlStartIndex = 0;
        for (var j = 0; j < hl.startIndex; j++) {
          htmlStartIndex = this._advanceChar(html, htmlStartIndex);
        }

        var htmlEndIndex = 0;
        if (hl.endIndex != null) {
          for (var j = 0; j < hl.endIndex; j++) {
            htmlEndIndex = this._advanceChar(html, htmlEndIndex);
          }
        } else {
          // If endIndex isn't present, continue to the end of the line.
          htmlEndIndex = html.length;
        }
        // The start and end indices could be the same if a highlight is meant
        // to start at the end of a line and continue onto the next one.
        // Ignore it.
        if (htmlStartIndex != htmlEndIndex) {
          html = html.slice(0, htmlStartIndex) + startTag +
                html.slice(htmlStartIndex, htmlEndIndex) + endTag +
                html.slice(htmlEndIndex);
        }
      }
      return html;
    },

    _addNewLines: function(content, html, numLines) {
      var htmlIndex = 0;
      var indices = [];
      var numChars = 0;
      for (var i = 0; i < content.length; i++) {
        if (numChars > 0 && numChars % this.prefs.line_length == 0) {
          indices.push(htmlIndex);
        }
        htmlIndex = this._advanceChar(html, htmlIndex);
        if (content[i] == '\t') {
          numChars += this.prefs.tab_size;
        } else {
          numChars++;
        }
      }
      var result = html;
      var linesLeft = numLines;
      // Since the result string is being altered in place, start from the end
      // of the string so that the insertion indices are not affected as the
      // result string changes.
      for (var i = indices.length - 1; i >= 0; i--) {
        result = result.slice(0, indices[i]) + this._lineFeedHTML +
            result.slice(indices[i]);
        linesLeft--;
      }
      // numLines is the total number of lines this code block should take up.
      // Fill in the remaining ones.
      for (var i = 0; i < linesLeft; i++) {
        result += this._lineFeedHTML;
      }
      return result;
    },

    _addTabWrappers: function(content, html) {
      // TODO(andybons): CSS tab-size is not supported in IE.
      // Force this to be a number to prevent arbitrary injection.
      var tabSize = +this.prefs.tab_size;
      var htmlStr = '<span class="style-scope gr-diff-side tab ' +
          (this.prefs.show_tabs ? 'withIndicator" ' : '" ') +
          'style="tab-size:' + tabSize + ';' +
          '-moz-tab-size:' + tabSize + ';">\t</span>';
      return html.replace(TAB_REGEX, htmlStr);
    },

    _createElement: function(tagName, className) {
      var el = document.createElement(tagName);
      // When Shady DOM is being used, these classes are added to account for
      // Polymer's polyfill behavior. In order to guarantee sufficient
      // specificity within the CSS rules, these are added to every element.
      // Since the Polymer DOM utility functions (which would do this
      // automatically) are not being used for performance reasons, this is
      // done manually.
      el.classList.add('style-scope', 'gr-diff-side');
      if (!!className) {
        el.classList.add(className);
      }
      return el;
    },
  });
})();
