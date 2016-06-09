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
    is: 'gr-diff-selection',

    properties: {
      _cachedDiffBuilder: Object,
    },

    listeners: {
      'copy': '_handleCopy',
      'down': '_handleDown',
    },

    get diffBuilder() {
      if (!this._cachedDiffBuilder) {
        this._cachedDiffBuilder =
            Polymer.dom(this).querySelector('gr-diff-builder');
      }
      return this._cachedDiffBuilder;
    },

    _handleDown: function(e) {
      var lineEl = this.diffBuilder.getLineElByChild(e.target);
      if (!lineEl) {
        return;
      }
      var side = this.diffBuilder.getSideByLineEl(lineEl);
      this.classList.remove('selected-right', 'selected-left');
      this.classList.add('selected-' + side);
    },

    _handleCopy: function(e) {
      if (!e.target.classList.contains('content')) {
        return;
      }
      var lineEl = this.diffBuilder.getLineElByChild(e.target);
      if (!lineEl) {
        return;
      }
      var side = this.diffBuilder.getSideByLineEl(lineEl);
      var text = this._getSelectedText(side);
      e.clipboardData.setData('Text', text);
      e.preventDefault();
    },

    _getSelectedText: function(opt_side) {
      var sel = window.getSelection();
      if (sel.rangeCount != 1) {
        return; // No multi-select support yet.
      }
      var range = sel.getRangeAt(0);
      var fragment = range.cloneContents();
      var selector = '.content,td.content:nth-of-type(1)';
      if (opt_side) {
        selector = '.' + opt_side + ' + ' + selector;
      }
      var contentEls = Polymer.dom(fragment).querySelectorAll(selector);
      if (contentEls.length === 0) {
        return fragment.textContent;
      }

      var text = '';
      for (var i = 0; i < contentEls.length; i++) {
        text += contentEls[i].textContent + '\n';
      }
      return text;
    },
  });
})();
