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

  function GrDiffBuilder(diff, prefs, outputEl) {
    this._prefs = prefs;
    this._outputEl = outputEl;
    this._groups = [];

    this._processContent(diff.content, this._groups, prefs.context);
  }

  GrDiffBuilder.GroupType = {
    ADDED: 'b',
    BOTH: 'ab',
    REMOVED: 'a',
  };

  GrDiffBuilder.prototype.emitDiff = function() {
    for (var i = 0; i < this._groups.length; i++) {
      this.emitGroup(this._groups[i]);
    }
  };

  GrDiffBuilder.prototype.emitGroup = function(group, opt_beforeSection) {
    throw Error('Subclasses must implement emitGroup');
  },

  GrDiffBuilder.prototype._processContent = function(content, groups, context) {
    var WHOLE_FILE = -1;
    context = content.length > 1 ? context : WHOLE_FILE;

    var lineNums = {
      left: 0,
      right: 0,
    };

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
        this._appendRemovedLines(group[GrDiffBuilder.GroupType.REMOVED], lines,
            lineNums);
      }
      if (group[GrDiffBuilder.GroupType.ADDED] !== undefined) {
        this._appendAddedLines(group[GrDiffBuilder.GroupType.ADDED], lines,
            lineNums);
      }
      groups.push(new GrDiffGroup(GrDiffGroup.Type.DELTA, lines));
    }
  };

  GrDiffBuilder.prototype._insertContextGroups = function(groups, lines,
      hiddenRange) {
    // TODO: Split around comments as well.
    var linesBeforeCtx = lines.slice(0, hiddenRange[0]);
    var hiddenLines = lines.slice(hiddenRange[0], hiddenRange[1]);
    var linesAfterCtx = lines.slice(hiddenRange[1]);

    if (linesBeforeCtx.length > 0) {
      groups.push(new GrDiffGroup(GrDiffGroup.Type.BOTH, linesBeforeCtx));
    }

    var ctxLine = new GrDiffLine(GrDiffLine.Type.CONTEXT_CONTROL);
    ctxLine.contextLines = hiddenLines;
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

  GrDiffBuilder.prototype._appendRemovedLines = function(rows, lines,
      lineNums) {
    for (var i = 0; i < rows.length; i++) {
      var line = new GrDiffLine(GrDiffLine.Type.REMOVE);
      line.text = rows[i];
      line.beforeNumber = ++lineNums.left;
      lines.push(line);
    }
  };

  GrDiffBuilder.prototype._appendAddedLines = function(rows, lines, lineNums) {
    for (var i = 0; i < rows.length; i++) {
      var line = new GrDiffLine(GrDiffLine.Type.ADD);
      line.text = rows[i];
      line.afterNumber = ++lineNums.right;
      lines.push(line);
    }
  };

  GrDiffBuilder.prototype._createContextControl = function(section, line) {
    if (!line.contextLines.length) {
      return null;
    }
    var td = this._createElement('td');
    var button = this._createElement('gr-button', 'showContext');
    button.setAttribute('link', true);
    var commonLines = line.contextLines.length;
    var text = 'Show ' + commonLines + ' common line';
    if (commonLines > 1) {
      text += 's';
    }
    text += '...';
    button.textContent = text;
    button.addEventListener('tap', function(e) {
      e.detail = {
        group: new GrDiffGroup(GrDiffGroup.Type.BOTH, line.contextLines),
        section: section,
      };
      // Let it bubble up the DOM tree.
    });
    td.appendChild(button);
    return td;
  };

  GrDiffBuilder.prototype._createBlankSideEl = function() {
    var td = this._createElement('td');
    td.setAttribute('colspan', '2');
    return td;
  };

  GrDiffBuilder.prototype._createLineEl = function(line, number, type) {
    var td = this._createElement('td', 'lineNum');
    if (line.type === GrDiffLine.Type.CONTEXT_CONTROL) {
      td.setAttribute('data-value', '@@');
    } else if (line.type === GrDiffLine.Type.BOTH || line.type == type) {
      td.setAttribute('data-value', number);
    }
    return td;
  };

  GrDiffBuilder.prototype._createTextEl = function(line) {
    var td = this._createElement('td', 'content');
    td.classList.add(line.type);
    var text = line.text || '\n';
    var html = util.escapeHTML(text);

    // If the html is equivalent to the text then it didn't get highlighted
    // or escaped. Use textContent which is faster than innerHTML.
    if (html == text) {
      td.textContent = text;
    } else {
      td.innerHTML = html;
    }
    return td;
  };

  GrDiffBuilder.prototype._createElement = function(tagName, className) {
    var el = document.createElement(tagName);
    // When Shady DOM is being used, these classes are added to account for
    // Polymer's polyfill behavior. In order to guarantee sufficient
    // specificity within the CSS rules, these are added to every element.
    // Since the Polymer DOM utility functions (which would do this
    // automatically) are not being used for performance reasons, this is
    // done manually.
    el.classList.add('style-scope', 'gr-new-diff');
    if (!!className) {
      el.classList.add(className);
    }
    return el;
  };

  window.GrDiffBuilder = GrDiffBuilder;
})(window, GrDiffGroup, GrDiffLine);
