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
    is: 'gr-selection-action-box',

    properties: {
      keyEventTarget: {
        type: Object,
        value: function() { return document.body; },
      },
      range: {
        type: Object,
        value: {
          startLine: NaN,
          startChar: NaN,
          endLine: NaN,
          endChar: NaN,
        },
      },
      side: {
        type: String,
        value: '',
      },
    },

    behaviors: [
      Gerrit.KeyboardShortcutBehavior,
    ],

    placeAbove: function(el) {
      var rect;
      if (!(el instanceof Element)) {
        var range = document.createRange();
        range.selectNode(el);
        rect = range.getBoundingClientRect();
        range.detach();
      } else {
        rect = el.getBoundingClientRect();
      }
      var boxRect = this.getBoundingClientRect();
      var parentRect = this.parentElement.getBoundingClientRect();
      this.style.top =
          rect.top - parentRect.top - boxRect.height - 4 + 'px';
      this.style.left =
          rect.left - parentRect.left + (rect.width - boxRect.width)/2 + 'px';
    },

    _handleKey: function(e) {
      if (this.shouldSupressKeyboardShortcut(e)) { return; }
      if (e.keyCode === 67) { // 'c'
        e.preventDefault();
        this.fire('create-comment', {side: this.side, range: this.range});
      };
    },
  });
})();
