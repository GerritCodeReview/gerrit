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

  function GrDiffBuilder(diff, outputEl) {
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
  };

  GrDiffBuilder.prototype._createBlankSideEl = function() {
    var td = this._createElement('td');
    td.setAttribute('colspan', '2');
    return td;
  };

  GrDiffBuilder.prototype._createLineEl = function(line, number, type) {
    var td = this._createElement('td', 'lineNum');
    if (line.type === GrDiffLine.Type.BOTH || line.type == type) {
      td.setAttribute('data-line-num', number);
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
