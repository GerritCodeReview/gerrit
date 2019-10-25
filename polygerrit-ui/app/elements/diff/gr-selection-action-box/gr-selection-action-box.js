/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
(function() {
  'use strict';

  Polymer({
    is: 'gr-selection-action-box',

    /**
     * Fired when the comment creation action was taken (hotkey, click).
     *
     * @event create-range-comment
     */

    properties: {
      keyEventTarget: {
        type: Object,
        value() { return document.body; },
      },
      range: {
        type: Object,
        value: {
          start_line: NaN,
          start_character: NaN,
          end_line: NaN,
          end_character: NaN,
        },
      },
      positionBelow: Boolean,
      side: {
        type: String,
        value: '',
      },
    },

    behaviors: [
      Gerrit.FireBehavior,
      Gerrit.KeyboardShortcutBehavior,
    ],

    listeners: {
      mousedown: '_handleMouseDown', // See https://crbug.com/gerrit/4767
    },

    keyBindings: {
      c: '_handleCKey',
    },

    placeAbove(el) {
      Polymer.dom.flush();
      const rect = this._getTargetBoundingRect(el);
      const boxRect = this.$.tooltip.getBoundingClientRect();
      const parentRect = this._getParentBoundingClientRect();
      this.style.top =
          rect.top - parentRect.top - boxRect.height - 6 + 'px';
      this.style.left =
          rect.left - parentRect.left + (rect.width - boxRect.width) / 2 + 'px';
    },

    placeBelow(el) {
      Polymer.dom.flush();
      const rect = this._getTargetBoundingRect(el);
      const boxRect = this.$.tooltip.getBoundingClientRect();
      const parentRect = this._getParentBoundingClientRect();
      this.style.top =
      rect.top - parentRect.top + boxRect.height - 6 + 'px';
      this.style.left =
      rect.left - parentRect.left + (rect.width - boxRect.width) / 2 + 'px';
    },

    _getParentBoundingClientRect() {
      // With native shadow DOM, the parent is the shadow root, not the gr-diff
      // element
      const parent = this.parentElement || this.parentNode.host;
      return parent.getBoundingClientRect();
    },

    _getTargetBoundingRect(el) {
      let rect;
      if (el instanceof Text) {
        const range = document.createRange();
        range.selectNode(el);
        rect = range.getBoundingClientRect();
        range.detach();
      } else {
        rect = el.getBoundingClientRect();
      }
      return rect;
    },

    _handleCKey(e) {
      if (this.shouldSuppressKeyboardShortcut(e) ||
          this.modifierPressed(e)) { return; }

      e.preventDefault();
      this._fireCreateComment();
    },

    _handleMouseDown(e) {
      if (e.button !== 0) { return; } // 0 = main button
      e.preventDefault();
      e.stopPropagation();
      this._fireCreateComment();
    },

    _fireCreateComment() {
      this.fire('create-range-comment', {side: this.side, range: this.range});
    },
  });
})();
