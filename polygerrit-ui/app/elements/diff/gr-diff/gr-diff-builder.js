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

  function GrDiffBuilder(diff, comments, prefs, outputEl) {
    this._comments = comments;
    this._prefs = prefs;
    this._outputEl = outputEl;
    this._groups = [];

    this._commentLocations = this._getCommentLocations(comments);
    this._processContent(diff.content, this._groups, prefs.context);
  }

  GrDiffBuilder.LESS_THAN_CODE = '<'.charCodeAt(0);
  GrDiffBuilder.GREATER_THAN_CODE = '>'.charCodeAt(0);
  GrDiffBuilder.AMPERSAND_CODE = '&'.charCodeAt(0);
  GrDiffBuilder.SEMICOLON_CODE = ';'.charCodeAt(0);

  GrDiffBuilder.TAB_REGEX = /\t/g;

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

  GrDiffBuilder.prototype.emitDiff = function() {
    for (var i = 0; i < this._groups.length; i++) {
      this.emitGroup(this._groups[i]);
    }
  };

  GrDiffBuilder.prototype.buildSectionElement = function(
      group, opt_beforeSection) {
    throw Error('Subclasses must implement buildGroupElement');
  };

  GrDiffBuilder.prototype.emitGroup = function(group, opt_beforeSection) {
    var element = this.buildSectionElement(group);
    this._outputEl.insertBefore(element, opt_beforeSection);
    group.element = element;
  };

  GrDiffBuilder.prototype.renderSection = function(element) {
    for (var i = 0; i < this._groups.length; i++) {
      var group = this._groups[i];
      if (group.element === element) {
        var newElement = this.buildSectionElement(group);
        group.element.parentElement.replaceChild(newElement, group.element);
        group.element = newElement;
        break;
      }
    }
  };

  GrDiffBuilder.prototype.getSectionsByLineRange = function(
      startLine, endLine, opt_side) {
    var sections = [];
    for (var i = 0; i < this._groups.length; i++) {
      var group = this._groups[i];
      if (group.lines.length === 0) {
        continue;
      }
      var groupStartLine;
      var groupEndLine;
      if (opt_side === GrDiffBuilder.Side.LEFT) {
        groupStartLine = group.lines[0].beforeNumber;
        groupEndLine = group.lines[group.lines.length - 1].beforeNumber;
      } else if (opt_side === GrDiffBuilder.Side.RIGHT) {
        groupStartLine = group.lines[0].afterNumber;
        groupEndLine = group.lines[group.lines.length - 1].afterNumber;
      }
      if (startLine <= groupEndLine && endLine >= groupStartLine) {
        sections.push(group.element);
      }
    }
    return sections;
  };

  GrDiffBuilder.prototype._processContent = function(content, groups, context) {
    this._appendFileComments(groups);

    var WHOLE_FILE = -1;
    context = content.length > 1 ? context : WHOLE_FILE;

    var lineNums = {
      left: 0,
      right: 0,
    };
    content = this._splitCommonGroupsWithComments(content, lineNums);
    for (var i = 0; i < content.length; i++) {
      var group = content[i];
      var lines = [];

      if (group[GrDiffBuilder.GroupType.BOTH] !== undefined) {
        var rows = group[GrDiffBuilder.GroupType.BOTH];
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

      if (group[GrDiffBuilder.GroupType.REMOVED] !== undefined) {
        var highlights = undefined;
        if (group[GrDiffBuilder.Highlights.REMOVED] !== undefined) {
          highlights = this._normalizeIntralineHighlights(
              group[GrDiffBuilder.GroupType.REMOVED],
              group[GrDiffBuilder.Highlights.REMOVED]);
        }
        this._appendRemovedLines(group[GrDiffBuilder.GroupType.REMOVED], lines,
            lineNums, highlights);
      }

      if (group[GrDiffBuilder.GroupType.ADDED] !== undefined) {
        var highlights = undefined;
        if (group[GrDiffBuilder.Highlights.ADDED] !== undefined) {
          highlights = this._normalizeIntralineHighlights(
            group[GrDiffBuilder.GroupType.ADDED],
            group[GrDiffBuilder.Highlights.ADDED]);
        }
        this._appendAddedLines(group[GrDiffBuilder.GroupType.ADDED], lines,
            lineNums, highlights);
      }
      groups.push(new GrDiffGroup(GrDiffGroup.Type.DELTA, lines));
    }
  };

  GrDiffBuilder.prototype._appendFileComments = function(groups) {
    var line = new GrDiffLine(GrDiffLine.Type.BOTH);
    line.beforeNumber = GrDiffLine.FILE;
    line.afterNumber = GrDiffLine.FILE;
    groups.push(new GrDiffGroup(GrDiffGroup.Type.BOTH, [line]));
  };

  GrDiffBuilder.prototype._getCommentLocations = function(comments) {
    var result = {
      left: {},
      right: {},
    };
    for (var side in comments) {
      if (side !== GrDiffBuilder.Side.LEFT &&
          side !== GrDiffBuilder.Side.RIGHT) {
        continue;
      }
      comments[side].forEach(function(c) {
        result[side][c.line || GrDiffLine.FILE] = true;
      });
    }
    return result;
  };

  GrDiffBuilder.prototype._commentIsAtLineNum = function(side, lineNum) {
    return this._commentLocations[side][lineNum] === true;
  };

  // In order to show comments out of the bounds of the selected context,
  // treat them as separate chunks within the model so that the content (and
  // context surrounding it) renders correctly.
  GrDiffBuilder.prototype._splitCommonGroupsWithComments = function(content,
      lineNums) {
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
        if (this._commentIsAtLineNum(GrDiffBuilder.Side.LEFT, leftLineNum) ||
            this._commentIsAtLineNum(GrDiffBuilder.Side.RIGHT, rightLineNum)) {
          if (currentChunk.ab && currentChunk.ab.length > 0) {
            result.push(currentChunk);
            currentChunk = {ab: []};
          }
          result.push({ab: [chunk[j]]});
        } else {
          currentChunk.ab.push(chunk[j]);
        }
      }
      // != instead of !== because we want to cover both undefined and null
      if (currentChunk.ab != null && currentChunk.ab.length > 0) {
        result.push(currentChunk);
      }
    }
    return result;
  };

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
  GrDiffBuilder.prototype._normalizeIntralineHighlights = function(content,
      highlights) {
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
  };

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

  GrDiffBuilder.prototype._appendCommonLines = function(rows, lines, lineNums) {
    for (var i = 0; i < rows.length; i++) {
      var line = new GrDiffLine(GrDiffLine.Type.BOTH);
      line.text = rows[i];
      line.beforeNumber = ++lineNums.left;
      line.afterNumber = ++lineNums.right;
      lines.push(line);
    }
  };

  GrDiffBuilder.prototype._appendRemovedLines = function(rows, lines, lineNums,
      opt_highlights) {
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
  };

  GrDiffBuilder.prototype._appendAddedLines = function(rows, lines, lineNums,
      opt_highlights) {
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
  };

  GrDiffBuilder.prototype._createContextControl = function(section, line) {
    if (!line.contextGroup || !line.contextGroup.lines.length) {
      return null;
    }
    var contextLines = line.contextGroup.lines;
    var td = this._createElement('td');
    var button = this._createElement('gr-button', 'showContext');
    button.setAttribute('link', true);
    var commonLines = contextLines.length;
    var text = 'Show ' + commonLines + ' common line';
    if (commonLines > 1) {
      text += 's';
    }
    text += '...';
    button.textContent = text;
    button.addEventListener('tap', function(e) {
      e.detail = {
        group: line.contextGroup,
        section: section,
      };
      // Let it bubble up the DOM tree.
    });
    td.appendChild(button);
    return td;
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

    td.classList.add(line.highlights.length > 0 ?
        'lightHighlight' : 'darkHighlight');

    if (line.highlights.length > 0) {
      html = this._addIntralineHighlights(text, html, line.highlights);
    }

    if (this._textLength(text, this._prefs.tab_size) >
        this._prefs.line_length) {
      html = this._addNewlines(text, html);
    }
    html = this._addTabWrappers(html);

    // If the html is equivalent to the text then it didn't get highlighted
    // or escaped. Use textContent which is faster than innerHTML.
    if (html === text) {
      td.textContent = text;
    } else {
      td.innerHTML = html;
    }
    return td;
  };

  GrDiffBuilder.prototype._textLength = function(text, tabSize) {
    // TODO(andybons): Unicode support.
    var numChars = 0;
    for (var i = 0; i < text.length; i++) {
      if (text[i] === '\t') {
        numChars += tabSize;
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

  GrDiffBuilder.prototype._addTabWrappers = function(html) {
    var htmlStr = this._getTabWrapper(this._prefs.tab_size,
        this._prefs.show_tabs);
    return html.replace(GrDiffBuilder.TAB_REGEX, htmlStr);
  };

  GrDiffBuilder.prototype._addIntralineHighlights = function(content, html,
      highlights) {
    var START_TAG = '<hl class="style-scope gr-diff">';
    var END_TAG = '</hl>';

    for (var i = 0; i < highlights.length; i++) {
      var hl = highlights[i];

      var htmlStartIndex = 0;
      // Find the index of the HTML string to insert the start tag.
      for (var j = 0; j < hl.startIndex; j++) {
        htmlStartIndex = this._advanceChar(html, htmlStartIndex);
      }

      var htmlEndIndex = 0;
      if (hl.endIndex !== undefined) {
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
      if (htmlStartIndex !== htmlEndIndex) {
        html = html.slice(0, htmlStartIndex) + START_TAG +
              html.slice(htmlStartIndex, htmlEndIndex) + END_TAG +
              html.slice(htmlEndIndex);
      }
    }
    return html;
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
