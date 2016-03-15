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

    this._processContent(diff.content, this._groups);
  }

  GrDiffBuilder.GroupType = {
    ADDED: 'b',
    BOTH: 'ab',
    REMOVED: 'a',
  };

  GrDiffBuilder.prototype.emitDiff = function() {
    for (var i = 0; i < this._groups.length; i++) {
      this._emitGroup(this._groups[i]);
    }
  };

  GrDiffBuilder.prototype._emitGroup = function(group, opt_beforeSection) {
    throw Error('Subclasses must implement emitGroup');
  },

  GrDiffBuilder.prototype._processContent = function(content, groups) {
    var leftLineNum = 0;
    var rightLineNum = 0;
    for (var i = 0; i < content.length; i++) {
      var group = content[i];
      var lines = [];

      if (group[GrDiffBuilder.GroupType.BOTH] !== undefined) {
        var rows = group[GrDiffBuilder.GroupType.BOTH];
        for (var j = 0; j < rows.length; j++) {
          var line = new GrDiffLine(GrDiffLine.Type.BOTH);
          line.text = rows[j];
          line.beforeNumber = ++leftLineNum;
          line.afterNumber = ++rightLineNum;
          lines.push(line);
        }
        groups.push(new GrDiffGroup(GrDiffGroup.Type.BOTH, lines));
        continue;
      }

      if (group[GrDiffBuilder.GroupType.REMOVED] !== undefined) {
        var rows = group[GrDiffBuilder.GroupType.REMOVED];
        for (var j = 0; j < rows.length; j++) {
          var line = new GrDiffLine(GrDiffLine.Type.REMOVE);
          line.text = rows[j];
          line.beforeNumber = ++leftLineNum;
          lines.push(line);
        }
      }
      if (group[GrDiffBuilder.GroupType.ADDED] !== undefined) {
        var rows = group[GrDiffBuilder.GroupType.ADDED];
        for (var j = 0; j < rows.length; j++) {
          var line = new GrDiffLine(GrDiffLine.Type.ADD);
          line.text = rows[j];
          line.afterNumber = ++rightLineNum;
          lines.push(line);
        }
      }
      groups.push(new GrDiffGroup(GrDiffGroup.Type.DELTA, lines));
    }

    if (this._prefs.context !== -1) {
      this._applyContext(groups, this._prefs.context);
    }
  };

  GrDiffBuilder.prototype._applyContext = function(groups, context) {
    return;
    if (groups.length === 1) { return; }

    var headers = {};
    for (var i = 0; i < groups.length; i++) {
      if (groups[i].type === GrDiffGroup.Type.DELTA) {
        continue;
      }

      var hiddenStart = context - 1;
      var hiddenEnd = groups[i].lines.length - context - 1;
      if (i === 0) {
        hiddenStart = 0;
      } else if (i === groups.length - 1) {
        hiddenEnd = groups[i].lines.length - 1;
      }
      if (hiddenEnd - hiddenStart > 0) {
        groups[i].hiddenRange = [hiddenStart, hiddenEnd];
        // Split the group up since a context control will be inserted right in
        // the middle of it.
        var topLines = groups[i].lines.splice(0, hiddenStart + 1);
        var topGroup = new GrDiffGroup(GrDiffGroup.Type.BOTH, topLines);

        topLines.forEach(function(line) {
          console.log(line.text);
        });
        console.log('---')
      } else {
        continue;
      }

      var line = new GrDiffLine(GrDiffLine.Type.CONTEXT_CONTROL);
      line.context = true;
      line.contextLinesStart = hiddenStart + 1;
      line.contextLinesEnd = hiddenEnd + 1;
      headers[i] = new GrDiffGroup(GrDiffGroup.Type.CONTEXT_CONTROL, [line]);
    }
    for (var idx in headers) {
      // groups.splice(idx, 0, headers[idx]);
    }
  };

  GrDiffBuilder.prototype._createContextControl = function(section, line) {
    if (!line.context) {
      return null;
    }
    var td = this._createElement('td');
    var button = this._createElement('gr-button', 'showContext');
    button.setAttribute('link', true);
    var commonLines = line.contextLinesEnd - line.contextLinesStart;
    var text = 'Show ' + commonLines + ' common line';
    if (commonLines > 1) {
      text += 's';
    }
    text += '...';
    button.textContent = text;
  // TODO:andybons: need a way to propagate these values up to the tap handler.
    // action.line = line;
    // action.section = section;
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
