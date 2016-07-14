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

  // Prevent redefinition.
  if (window.GrDiffBuilder) { return; }

  var REGEX_ASTRAL_SYMBOL = /[\uD800-\uDBFF][\uDC00-\uDFFF]/;

  function GrDiffBuilder(diff, comments, prefs, outputEl) {
    this._diff = diff;
    this._comments = comments;
    this._prefs = prefs;
    this._outputEl = outputEl;
    this.groups = [];
  }

  GrDiffBuilder.LESS_THAN_CODE = '<'.charCodeAt(0);
  GrDiffBuilder.GREATER_THAN_CODE = '>'.charCodeAt(0);
  GrDiffBuilder.AMPERSAND_CODE = '&'.charCodeAt(0);
  GrDiffBuilder.SEMICOLON_CODE = ';'.charCodeAt(0);

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

  GrDiffBuilder.prototype.buildSectionElement = function(group) {
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

  GrDiffBuilder.prototype.createCommentThread = function(changeNum, patchNum,
      path, side, projectConfig) {
    var threadEl = document.createElement('gr-diff-comment-thread');
    threadEl.changeNum = changeNum;
    threadEl.patchNum = patchNum;
    threadEl.path = path;
    threadEl.side = side;
    threadEl.projectConfig = projectConfig;
    return threadEl;
  };

  GrDiffBuilder.prototype._commentThreadForLine = function(line, opt_side) {
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
    var threadEl = this.createCommentThread(
        this._comments.meta.changeNum,
        patchNum,
        this._comments.meta.path,
        side,
        this._comments.meta.projectConfig);
    threadEl.comments = comments;
    return threadEl;
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

  GrDiffBuilder.prototype._createTextEl = function(line) {
    var td = this._createElement('td');
    if (line.type !== GrDiffLine.Type.BLANK) {
      td.classList.add('content');
    }
    td.classList.add(line.type);
    var text = line.text;
    var html = util.escapeHTML(text);
    html = this._addTabWrappers(html, this._prefs.tab_size);

    if (this._textLength(text, this._prefs.tab_size) >
        this._prefs.line_length) {
      html = this._addNewlines(text, html);
    }

    var contentText = this._createElement('div', 'contentText');

    // If the html is equivalent to the text then it didn't get highlighted
    // or escaped. Use textContent which is faster than innerHTML.
    if (html === text) {
      contentText.textContent = text;
    } else {
      contentText.innerHTML = html;
    }

    td.classList.add(line.highlights.length > 0 ?
        'lightHighlight' : 'darkHighlight');
    if (line.highlights.length > 0) {
      this._addIntralineHighlights(contentText, line);
    }

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

  GrDiffBuilder.prototype._addNewlines = function(text, html) {
    var htmlIndex = 0;
    var indices = [];
    var numChars = 0;
    for (var i = 0; i < text.length; i++) {
      if (numChars > 0 && numChars % this._prefs.line_length === 0) {
        indices.push(htmlIndex);
      }
      htmlIndex = this._advanceChar(html, htmlIndex);
      if (text[i] === '\t') {
        numChars += this._prefs.tab_size;
      } else {
        numChars++;
      }
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
      result += split[i] + this._getTabWrapper(width, this._prefs.show_tabs);
      offset += width;
    }
    if (split.length) {
      result += split[split.length - 1];
    }

    return result;
  };

  /**
   * Take a DIV.contentText element and a line object with intraline differences
   * to highlight and apply them to the element as annotations.
   * @param {HTMLDivElement} el
   * @param {[type]} line
   */
  GrDiffBuilder.prototype._addIntralineHighlights = function(el, line) {
    var HL_CLASS = 'style-scope gr-diff';

    line.highlights.forEach(function(highlight) {
      // The start and end indices could be the same if a highlight is meant
      // to start at the end of a line and continue onto the next one.
      // Ignore it.
      if (highlight.startIndex === highlight.endIndex) { return; }

      // If endIndex isn't present, continue to the end of the line.
      var endIndex = highlight.endIndex === undefined ?
          line.text.length : highlight.endIndex;

      GrAnnotation.annotateElement(
          el,
          highlight.startIndex,
          endIndex - highlight.startIndex,
          HL_CLASS);
    });
  };

  GrDiffBuilder.prototype._getTabWrapper = function(tabSize, showTabs) {
    // Force this to be a number to prevent arbitrary injection.
    tabSize = +tabSize;
    if (isNaN(tabSize)) {
      throw Error('Invalid tab size from preferences.');
    }

    var str = '<span class="style-scope gr-diff tab ';
    if (showTabs) {
      str += 'withIndicator';
    }
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

  window.GrDiffBuilder = GrDiffBuilder;
})(window, GrDiffGroup, GrDiffLine);
