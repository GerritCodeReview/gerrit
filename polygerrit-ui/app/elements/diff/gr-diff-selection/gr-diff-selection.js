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

  /**
   * Possible CSS classes indicating the state of selection. Dynamically added/
   * removed based on where the user clicks within the diff.
   */
  var SELECTION_CLASSES = {
    COMMENT: 'selected-comment',
    LEFT: 'selected-left',
    RIGHT: 'selected-right',
  };

  Polymer({
    is: 'gr-diff-selection',

    properties: {
      _cachedDiffBuilder: Object,
    },

    listeners: {
      'copy': '_handleCopy',
      'down': '_handleDown',
    },

    attached: function() {
      this.classList.add(SELECTION_CLASSES.RIGHT);
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
      var commentSelected =
          e.target.parentNode.classList.contains('gr-diff-comment');
      var side = this.diffBuilder.getSideByLineEl(lineEl);
      var targetClass = side === 'left' ?
          SELECTION_CLASSES.LEFT :
          SELECTION_CLASSES.RIGHT;

      for (var key in SELECTION_CLASSES) {
        if (SELECTION_CLASSES.hasOwnProperty(key)) {
          this.classList.remove(SELECTION_CLASSES[key]);
        }
      }
      this.classList.add(targetClass);
      if (commentSelected) {
        this.classList.add(SELECTION_CLASSES.COMMENT);
      }
    },

    _handleCopy: function(e) {
      var commentSelected = false;
      if (e.currentTarget &&
          e.currentTarget.classList.contains(SELECTION_CLASSES.COMMENT)) {
        commentSelected = true;
      } else {
        // Element.closest() not supported in IE.
        var el = e.target;
        while (!el.classList.contains('content')) {
          if (!el.parentElement) {
            return;
          }
          el = el.parentElement;
        }
      }
      var lineEl = this.diffBuilder.getLineElByChild(e.target);
      if (!lineEl) {
        return;
      }
      var side = this.diffBuilder.getSideByLineEl(lineEl);
      var text = this._getSelectedText(side, commentSelected);
      if (text) {
        e.clipboardData.setData('Text', text);
        e.preventDefault();
      }
    },

    /**
     * Due to a bug in Polymer, multiple nested layers of dom-repeat generated
     * objects are not represented in the cloned range contents.
     * Because of this, handling for selection in comments requires more work.
     *
     * @param {!string} The side that is selected.
     * @param {boolean} Whether or not a comment is selected.
     * @return {string} The selected text.
     */
    _getSelectedText: function(side, commentSelected) {
      var sel = window.getSelection();
      if (sel.rangeCount != 1) {
        return; // No multi-select support yet.
      }
      var range = sel.getRangeAt(0);
      var fragment = range.cloneContents();
      var selector = '';
      var contentEls = [];
      if (commentSelected) {
        if (range.startContainer === range.endContainer) {
          return; // Fall back to default copy behavior.
        }
        selector += '[data-side="' + side + '"]';
        selector += ' .message *, .unified .message *';
        // Query the whole DOM for comments.
        var possibleEls = document.querySelectorAll(selector);
        for (var i = 0; i < possibleEls.length; i++) {
          var el = possibleEls[i];
          // Check if the comment element exists inside the selection.
          if (sel.containsNode(el, true)) {
            contentEls.push(el);
          }
        }
        // Deal with offsets.
        var startEl = contentEls[0].cloneNode(true);
        startEl.innerHTML = startEl.textContent.substring(
            range.startOffset, startEl.textContent.length);
        contentEls[0] = startEl;
        if (range.endOffset) {
          var endEl = contentEls[contentEls.length - 1].cloneNode(true);
          endEl.innerHTML = endEl.textContent.substring(0, range.endOffset);
          contentEls[contentEls.length - 1] = endEl;
        }
      } else {
        selector += '.contentText[data-side="' + side + '"]';
        selector += ':not(:empty)';
        contentEls = Polymer.dom(fragment).querySelectorAll(selector);
      }
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
