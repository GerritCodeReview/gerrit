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
    is: 'gr-keyboard-shortcuts-dialog',

    /**
     * Fired when the user presses the close button.
     *
     * @event close
     */

    properties: {
      _left: Array,
      _right: Array,

      _propertyBySection: {
        type: Object,
        value() {
          return {
            [this.ShortcutSection.EVERYWHERE]: '_everywhere',
            [this.ShortcutSection.NAVIGATION]: '_navigation',
            [this.ShortcutSection.DASHBOARD]: '_dashboard',
            [this.ShortcutSection.CHANGE_LIST]: '_changeList',
            [this.ShortcutSection.ACTIONS]: '_actions',
            [this.ShortcutSection.REPLY_DIALOG]: '_replyDialog',
            [this.ShortcutSection.FILE_LIST]: '_fileList',
            [this.ShortcutSection.DIFFS]: '_diffs',
          };
        },
      },
    },

    behaviors: [
      Gerrit.KeyboardShortcutBehavior,
    ],

    hostAttributes: {
      role: 'dialog',
    },

    attached() {
      this.addKeyboardShortcutDirectoryListener(
          this._onDirectoryUpdated.bind(this));
    },

    detached() {
      this.removeKeyboardShortcutDirectoryListener(
          this._onDirectoryUpdated.bind(this));
    },

    _handleCloseTap(e) {
      e.preventDefault();
      this.fire('close', null, {bubbles: false});
    },

    _onDirectoryUpdated(directory) {
      const left = [];
      const right = [];

      if (directory.has(this.ShortcutSection.EVERYWHERE)) {
        left.push({
          section: this.ShortcutSection.EVERYWHERE,
          shortcuts: directory.get(this.ShortcutSection.EVERYWHERE),
        });
      }

      if (directory.has(this.ShortcutSection.NAVIGATION)) {
        left.push({
          section: this.ShortcutSection.NAVIGATION,
          shortcuts: directory.get(this.ShortcutSection.NAVIGATION),
        });
      }

      if (directory.has(this.ShortcutSection.ACTIONS)) {
        right.push({
          section: this.ShortcutSection.ACTIONS,
          shortcuts: directory.get(this.ShortcutSection.ACTIONS),
        });
      }

      if (directory.has(this.ShortcutSection.REPLY_DIALOG)) {
        right.push({
          section: this.ShortcutSection.REPLY_DIALOG,
          shortcuts: directory.get(this.ShortcutSection.REPLY_DIALOG),
        });
      }

      if (directory.has(this.ShortcutSection.FILE_LIST)) {
        right.push({
          section: this.ShortcutSection.FILE_LIST,
          shortcuts: directory.get(this.ShortcutSection.FILE_LIST),
        });
      }

      if (directory.has(this.ShortcutSection.DIFFS)) {
        right.push({
          section: this.ShortcutSection.DIFFS,
          shortcuts: directory.get(this.ShortcutSection.DIFFS),
        });
      }

      this.set('_left', left);
      this.set('_right', right);
    },
  });
})();
