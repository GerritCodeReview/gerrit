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

  const SECTIONS = {
    everywhere: '_everywhere',
    navigation: '_navigation',
    dashboard: '_dashboard',
    changeList: '_changeList',
    actions: '_actions',
    replyDialog: '_replyDialog',
    fileList: '_fileList',
    diffs: '_diffs',
  };

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
    },

    hostAttributes: {
      role: 'dialog',
    },

    _computeInView(currentView, view) {
      return view === currentView;
    },

    _handleCloseTap(e) {
      e.preventDefault();
      this.fire('close', null, {bubbles: false});
    },

    _onKeyboardShortcutsUpdated(e) {
      for (const section of Object.keys(SECTIONS)) {
        console.log(SECTIONS[section], '=', e.detail[section]);
        this.set(SECTIONS[section], e.detail[section]);
      }
    },

    _notEmpty(property) {
      return property && property.length > 0;
    },
  });
})();
