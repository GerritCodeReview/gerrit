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
      view: String,

      _everywhere: Array,
      _navigation: Array,
      _dashboard: Array,
      _changeList: Array,
      _actions: Array,
      _replyDialog: Array,
      _fileList: Array,
      _diffs: Array,

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
      console.log('ONDIRECTORYUPDATED', directory);
      for (const section of Object.keys(this.ShortcutSection)) {
        const sectionTitle = this.ShortcutSection[section];
        const prop = this._propertyBySection[sectionTitle];
        if (directory.has(sectionTitle)) {
          console.log('SET:', prop, directory.get(sectionTitle));
          this.set(prop, directory.get(sectionTitle));
        } else {
          this.set(prop, []);
        }
      }
    },

    empty(property) {
      return !property || property.length === 0;
    },
  });
})();
