// Copyright (C) 2016 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the 'License');
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an 'AS IS' BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
(function(window, GrDiffGroup, GrDiffLine) {
  'use strict';

  var HTML_ENTITY_PATTERN = /[&<>"'`\/]/g;
  var HTML_ENTITY_MAP = {
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    '\'': '&#39;',
    '/': '&#x2F;',
    '`': '&#96;',
  };

  // Prevent redefinition.
  if (window.GrDiffBuilder) { return; }

  var REGEX_ASTRAL_SYMBOL = /[\uD800-\uDBFF][\uDC00-\uDFFF]/;

  function GrDiffBuilder(diff, comments, prefs, outputEl, layers) {
    this._diff = diff;
    this._comments = comments;
    this._prefs = prefs;
    this._outputEl = outputEl;
    this.groups = [];

    this.layers = layers || [];

    this.layers.forEach(function(layer) {
      if (layer.addListener) {
        layer.addListener(this._handleLayerUpdate.bind(this));
      }
    }.bind(this));
  }

  GrDiffBuilder.LESS_THAN_CODE = '<'.charCodeAt(0);
  GrDiffBuilder.GREATER_THAN_CODE = '>'.charCodeAt(0);
  GrDiffBuilder.AMPERSAND_CODE = '&'.charCodeAt(0);
  GrDiffBuilder.SEMICOLON_CODE = ';'.charCodeAt(0);
  GrDiffBuilder.TAB_CODE = '\t'.charCodeAt(0);

  GrDiffBuilder.LINE_FEED_HTML =
      '<span class="style-scope gr-diff br"></span>';

  GrDiffBuilder.GroupType = {
    ADDED: 'b',
    BOTH: 'ab',
    REMOVED: 'a',
  };

  GrDiffBuilder.Highlights = {
    ADDED: 'edit_b',
    REMOVED: 'edit_a',
  };

  GrDiffBuilder.Side = {
    LEFT: 'left',
    RIGHT: 'right',
  };

  GrDiffBuilder.ContextButtonType = {
    ABOVE: 'above',
    BELOW: 'below',
    ALL: 'all',
  };

  var PARTIAL_CONTEXT_AMOUNT = 10;

  /**
   * Abstract method
   * @param {string} outputEl
   * @param {number} fontSize
   */
  GrDiffBuilder.prototype.addColumns = function() {
    throw Error('Subclasses must implement addColumns');
  };

  /**
   * Abstract method
   * @param {Object} group
   */
  GrDiffBuilder.prototype.buildSectionElement = function() {
    throw Error('Subclasses must implement buildGroupElement');
  };

  GrDiffBuilder.prototype.emitGroup = function(group, opt_beforeSection) {
    var element = this.buildSectionElement(group);
    this._outputEl.insertBefore(element, opt_beforeSection);
    group.element = element;
  };

  GrDiffBuilder.prototype.renderSection = function(element) {
    for (var i = 0; i < this.groups.length; i++) {
      var group = this.groups[i];
      if (group.element === element) {
        var newElement = this.buildSectionElement(group);
        group.element.parentElement.replaceChild(newElement, group.element);
        group.element = newElement;
        break;
      }
    }
  };

  GrDiffBuilder.prototype.getGroupsByLineRange = function(
      startLine, endLine, opt_side) {
    var groups = [];
    for (var i = 0; i < this.groups.length; i++) {
      var group = this.groups[i];
      if (group.lines.length === 0) {
        continue;
      }
      var groupStartLine = 0;
      var groupEndLine = 0;
      if (opt_side) {
        groupStartLine = group.lineRange[opt_side].start;
        groupEndLine = group.lineRange[opt_side].end;
      }

      if (groupStartLine === 0) { // Line was removed or added.
        groupStartLine = groupEndLine;
      }
      if (groupEndLine === 0) {  // Line was removed or added.
        groupEndLine = groupStartLine;
      }
      if (startLine <= groupEndLine && endLine >= groupStartLine) {
        groups.push(group);
      }
    }
    return groups;
  };

  GrDiffBuilder.prototype.getContentByLine = function(lineNumber, opt_side,
      opt_root) {
    var root = Polymer.dom(opt_root || this._outputEl);
    var sideSelector = !!opt_side ? ('.' + opt_side) : '';
    return root.querySelector('td.lineNum[data-value="' + lineNumber +
        '"]' + sideSelector + ' ~ td.content .contentText');
  };

  /**
   * Find line elements or line objects by a range of line numbers and a side.
   *
   * @param {Number} start The first line number
   * @param {Number} end The last line number
   * @param {String} opt_side The side of the range. Either 'left' or 'right'.
   * @param {Array<GrDiffLine>} out_lines The output list of line objects. Use
   *     null if not desired.
   * @param  {Array<HTMLElement>} out_elements The output list of line elements.
   *     Use null if not desired.
   */
  GrDiffBuilder.prototype.findLinesByRange = function(start, end, opt_side,
      out_lines, out_elements) {
    var groups = this.getGroupsByLineRange(start, end, opt_side);
    groups.forEach(function(group) {
      var content = null;
      group.lines.forEach(function(line) {
        if ((opt_side === 'left' && line.type === GrDiffLine.Type.ADD) ||
            (opt_side === 'right' && line.type === GrDiffLine.Type.REMOVE)) {
          return;
        }
        var lineNumber = opt_side === 'left' ?
            line.beforeNumber : line.afterNumber;
        if (lineNumber < start || lineNumber > end) { return; }

        if (out_lines) { out_lines.push(line); }
        if (out_elements) {
          if (content) {
            content = this._getNextContentOnSide(content, opt_side);
          } else {
            content = this.getContentByLine(lineNumber, opt_side,
                group.element);
          }
          if (content) { out_elements.push(content); }
        }
      }.bind(this));
    }.bind(this));
  };

  /**
   * Re-renders the DIV.contentText elements for the given side and range of
   * diff content.
   */
  GrDiffBuilder.prototype._renderContentByRange = function(start, end, side) {
    var lines = [];
    var elements = [];
    var line;
    var el;
    this.findLinesByRange(start, end, side, lines, elements);
    for (var i = 0; i < lines.length; i++) {
      line = lines[i];
      el = elements[i];
      el.parentElement.replaceChild(this._createTextEl(line, side).firstChild,
          el);
    }
  };

  GrDiffBuilder.prototype.getSectionsByLineRange = function(
      startLine, endLine, opt_side) {
    return this.getGroupsByLineRange(startLine, endLine, opt_side).map(
        function(group) { return group.element; });
  };

  GrDiffBuilder.prototype._commentIsAtLineNum = function(side, lineNum) {
    return this._commentLocations[side][lineNum] === true;
  };

  // TODO(wyatta): Move this completely into the processor.
  GrDiffBuilder.prototype._insertContextGroups = function(groups, lines,
      hiddenRange) {
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
  };

  GrDiffBuilder.prototype._createContextControl = function(section, line) {
    if (!line.contextGroup || !line.contextGroup.lines.length) {
      return null;
    }

    var td = this._createElement('td');
    var showPartialLinks =
        line.contextGroup.lines.length > PARTIAL_CONTEXT_AMOUNT;

    if (showPartialLinks) {
      td.appendChild(this._createContextButton(
          GrDiffBuilder.ContextButtonType.ABOVE, section, line));
      td.appendChild(document.createTextNode(' - '));
    }

    td.appendChild(this._createContextButton(
        GrDiffBuilder.ContextButtonType.ALL, section, line));

    if (showPartialLinks) {
      td.appendChild(document.createTextNode(' - '));
      td.appendChild(this._createContextButton(
          GrDiffBuilder.ContextButtonType.BELOW, section, line));
    }

    return td;
  };

  GrDiffBuilder.prototype._createContextButton = function(type, section, line) {
    var contextLines = line.contextGroup.lines;
    var context = PARTIAL_CONTEXT_AMOUNT;

    var button = this._createElement('gr-button', 'showContext');
    button.setAttribute('link', true);

    var text;
    var groups = []; // The groups that replace this one if tapped.

    if (type === GrDiffBuilder.ContextButtonType.ALL) {
      text = 'Show ' + contextLines.length + ' common line';
      if (contextLines.length > 1) { text += 's'; }
      groups.push(line.contextGroup);
    } else if (type === GrDiffBuilder.ContextButtonType.ABOVE) {
      text = '+' + context + '↑';
      this._insertContextGroups(groups, contextLines,
          [context, contextLines.length]);
    } else if (type === GrDiffBuilder.ContextButtonType.BELOW) {
      text = '+' + context + '↓';
      this._insertContextGroups(groups, contextLines,
          [0, contextLines.length - context]);
    }

    button.textContent = text;

    button.addEventListener('tap', function(e) {
      e.detail = {
        groups: groups,
        section: section,
      };
      // Let it bubble up the DOM tree.
    });

    return button;
  };

  GrDiffBuilder.prototype._getCommentsForLine = function(comments, line,
      opt_side) {
    function byLineNum(lineNum) {
      return function(c) {
        return (c.line === lineNum) ||
               (c.line === undefined && lineNum === GrDiffLine.FILE);
      };
    }
    var leftComments =
        comments[GrDiffBuilder.Side.LEFT].filter(byLineNum(line.beforeNumber));
    var rightComments =
        comments[GrDiffBuilder.Side.RIGHT].filter(byLineNum(line.afterNumber));

    leftComments.forEach(function(c) { c.__commentSide = 'left'; });
    rightComments.forEach(function(c) { c.__commentSide = 'right'; });

    var result;

    switch (opt_side) {
      case GrDiffBuilder.Side.LEFT:
        result = leftComments;
        break;
      case GrDiffBuilder.Side.RIGHT:
        result = rightComments;
        break;
      default:
        result = leftComments.concat(rightComments);
        break;
    }

    return result;
  };

  GrDiffBuilder.prototype.createCommentThreadGroup = function(changeNum,
      patchNum, path, side, projectConfig, range) {
    var threadGroupEl =
        document.createElement('gr-diff-comment-thread-group');
    threadGroupEl.changeNum = changeNum;
    threadGroupEl.patchForNewThreads = patchNum;
    threadGroupEl.path = path;
    threadGroupEl.side = side;
    threadGroupEl.projectConfig = projectConfig;
    threadGroupEl.range = range;
    return threadGroupEl;
  };

  GrDiffBuilder.prototype._commentThreadGroupForLine =
      function(line, opt_side) {
    var comments = this._getCommentsForLine(this._comments, line, opt_side);
    if (!comments || comments.length === 0) {
      return null;
    }

    var patchNum = this._comments.meta.patchRange.patchNum;
    var side = comments[0].side || 'REVISION';
    if (line.type === GrDiffLine.Type.REMOVE ||
        opt_side === GrDiffBuilder.Side.LEFT) {
      if (this._comments.meta.patchRange.basePatchNum === 'PARENT') {
        side = 'PARENT';
      } else {
        patchNum = this._comments.meta.patchRange.basePatchNum;
      }
    }
    var threadGroupEl = this.createCommentThreadGroup(
        this._comments.meta.changeNum,
        patchNum,
        this._comments.meta.path,
        side,
        this._comments.meta.projectConfig);
    threadGroupEl.comments = comments;
    if (opt_side) {
      threadGroupEl.setAttribute('data-side', opt_side);
    }
    return threadGroupEl;
  };

  GrDiffBuilder.prototype._createLineEl = function(line, number, type,
      opt_class) {
    var td = this._createElement('td');
    if (opt_class) {
      td.classList.add(opt_class);
    }
    if (line.type === GrDiffLine.Type.BLANK) {
      return td;
    } else if (line.type === GrDiffLine.Type.CONTEXT_CONTROL) {
      td.classList.add('contextLineNum');
      td.setAttribute('data-value', '@@');
    } else if (line.type === GrDiffLine.Type.BOTH || line.type === type) {
      td.classList.add('lineNum');
      td.setAttribute('data-value', number);
    }
    return td;
  };

  GrDiffBuilder.prototype._createTextEl = function(line, opt_side) {
    var td = this._createElement('td');
    var text = line.text;
    if (line.type !== GrDiffLine.Type.BLANK) {
      td.classList.add('content');
    }
    td.classList.add(line.type);
    var html = this._escapeHTML(text);
    html = this._addTabWrappers(html, this._prefs.tab_size);

    if (!this._prefs.line_wrapping &&
        this._textLength(text, this._prefs.tab_size) >
        this._prefs.line_length) {
      html = this._addNewlines(text, html);
    }

    var contentText = this._createElement('div', 'contentText');
    if (opt_side) {
      contentText.setAttribute('data-side', opt_side);
    }

    // If the html is equivalent to the text then it didn't get highlighted
    // or escaped. Use textContent which is faster than innerHTML.
    if (html === text) {
      contentText.textContent = text;
    } else {
      contentText.innerHTML = html;
    }

    this.layers.forEach(function(layer) {
      layer.annotate(contentText, line);
    });

    td.appendChild(contentText);

    return td;
  };

  /**
   * Returns the text length after normalizing unicode and tabs.
   * @return {Number} The normalized length of the text.
   */
  GrDiffBuilder.prototype._textLength = function(text, tabSize) {
    text = text.replace(REGEX_ASTRAL_SYMBOL, '_');
    var numChars = 0;
    for (var i = 0; i < text.length; i++) {
      if (text[i] === '\t') {
        numChars += tabSize - (numChars % tabSize);
      } else {
        numChars++;
      }
    }
    return numChars;
  };

  // Advance `index` by the appropriate number of characters that would
  // represent one source code character and return that index. For
  // example, for source code '<span>' the escaped html string is
  // '&lt;span&gt;'. Advancing from index 0 on the prior html string would
  // return 4, since &lt; maps to one source code character ('<').
  GrDiffBuilder.prototype._advanceChar = function(html, index) {
    // TODO(andybons): Unicode is all kinds of messed up in JS. Account for it.
    // https://mathiasbynens.be/notes/javascript-unicode

    // Tags don't count as characters
    while (index < html.length &&
           html.charCodeAt(index) === GrDiffBuilder.LESS_THAN_CODE) {
      while (index < html.length &&
             html.charCodeAt(index) !== GrDiffBuilder.GREATER_THAN_CODE) {
        index++;
      }
      index++;  // skip the ">" itself
    }
    // An HTML entity (e.g., &lt;) counts as one character.
    if (index < html.length &&
        html.charCodeAt(index) === GrDiffBuilder.AMPERSAND_CODE) {
      while (index < html.length &&
             html.charCodeAt(index) !== GrDiffBuilder.SEMICOLON_CODE) {
        index++;
      }
    }
    return index + 1;
  };

  GrDiffBuilder.prototype._advanceTagClose = function(html, index) {
    while (index < html.length &&
           html.charCodeAt(index) !== GrDiffBuilder.GREATER_THAN_CODE) {
      index++;
    }
    return index + 1;
  };

  GrDiffBuilder.prototype._addNewlines = function(text, html) {
    var htmlIndex = 0;
    var indices = [];
    var numChars = 0;
    var prevHtmlIndex = 0;
    for (var i = 0; i < text.length; i++) {
      if (numChars > 0 && numChars % this._prefs.line_length === 0) {
        indices.push(htmlIndex);
      }
      htmlIndex = this._advanceChar(html, htmlIndex);
      if (text[i] === '\t') {
        // Advance past tab closing tag.
        htmlIndex = this._advanceTagClose(html, htmlIndex);
        if (~~(numChars / this._prefs.line_length) !==
            ~~((numChars + this._prefs.tab_size) / this._prefs.line_length)) {
          // Tab crosses line limit - push it to the next line.
          indices.push(prevHtmlIndex);
        }
        numChars += this._prefs.tab_size;
      } else {
        numChars++;
      }
      prevHtmlIndex = htmlIndex;
    }
    var result = html;
    // Since the result string is being altered in place, start from the end
    // of the string so that the insertion indices are not affected as the
    // result string changes.
    for (var i = indices.length - 1; i >= 0; i--) {
      result = result.slice(0, indices[i]) + GrDiffBuilder.LINE_FEED_HTML +
          result.slice(indices[i]);
    }
    return result;
  };

  /**
   * Takes a string of text (not HTML) and returns a string of HTML with tab
   * elements in place of tab characters. In each case tab elements are given
   * the width needed to reach the next tab-stop.
   *
   * @param {String} A line of text potentially containing tab characters.
   * @param {Number} The width for tabs.
   * @return {String} An HTML string potentially containing tab elements.
   */
  GrDiffBuilder.prototype._addTabWrappers = function(line, tabSize) {
    if (!line.length) { return ''; }

    var result = '';
    var offset = 0;
    var split = line.split('\t');
    var width;

    for (var i = 0; i < split.length - 1; i++) {
      offset += split[i].length;
      width = tabSize - (offset % tabSize);
      result += split[i] + this._getTabWrapper(width);
      offset += width;
    }
    if (split.length) {
      result += split[split.length - 1];
    }

    return result;
  };

  GrDiffBuilder.prototype._getTabWrapper = function(tabSize) {
    // Force this to be a number to prevent arbitrary injection.
    tabSize = +tabSize;
    if (isNaN(tabSize)) {
      throw Error('Invalid tab size from preferences.');
    }

    var str = '<span class="style-scope gr-diff tab ';
    str += '" style="';
    // TODO(andybons): CSS tab-size is not supported in IE.
    str += 'tab-size:' + tabSize + ';';
    str += '-moz-tab-size:' + tabSize + ';';
    str += '">\t</span>';
    return str;
  };

  GrDiffBuilder.prototype._createElement = function(tagName, className) {
    var el = document.createElement(tagName);
    // When Shady DOM is being used, these classes are added to account for
    // Polymer's polyfill behavior. In order to guarantee sufficient
    // specificity within the CSS rules, these are added to every element.
    // Since the Polymer DOM utility functions (which would do this
    // automatically) are not being used for performance reasons, this is
    // done manually.
    el.classList.add('style-scope', 'gr-diff');
    if (!!className) {
      el.classList.add(className);
    }
    return el;
  };

  GrDiffBuilder.prototype._handleLayerUpdate = function(start, end, side) {
    this._renderContentByRange(start, end, side);
  };

  /**
   * Finds the next DIV.contentText element following the given element, and on
   * the same side. Will only search within a group.
   * @param {HTMLElement} content
   * @param {String} side Either 'left' or 'right'
   * @return {HTMLElement}
   */
  GrDiffBuilder.prototype._getNextContentOnSide = function(content, side) {
    throw Error('Subclasses must implement _getNextContentOnSide');
  };

  /**
   * Determines whether the given group is either totally an addition or totally
   * a removal.
   * @param {GrDiffGroup} group
   * @return {Boolean}
   */
  GrDiffBuilder.prototype._isTotal = function(group) {
    return group.type === GrDiffGroup.Type.DELTA &&
        (!group.adds.length || !group.removes.length) &&
        !(!group.adds.length && !group.removes.length);
  };

  GrDiffBuilder.prototype._escapeHTML = function(str) {
    return str.replace(HTML_ENTITY_PATTERN, function(s) {
      return HTML_ENTITY_MAP[s];
    });
  };

  window.GrDiffBuilder = GrDiffBuilder;
})(window, GrDiffGroup, GrDiffLine);
