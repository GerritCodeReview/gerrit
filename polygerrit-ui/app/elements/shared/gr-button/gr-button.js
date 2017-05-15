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
    is: 'gr-button',

    properties: {
      link: {
        type: Boolean,
        reflectToAttribute: true,
      },
      disabled: {
        type: Boolean,
        observer: '_disabledChanged',
        reflectToAttribute: true,
      },
      _enabledTabindex: {
        type: String,
        value: '0',
      },
    },

    listeners: {
      tap: '_handleAction',
      click: '_handleAction',
    },

    behaviors: [
      Gerrit.KeyboardShortcutBehavior,
      Gerrit.TooltipBehavior,
    ],

    hostAttributes: {
      role: 'button',
      tabindex: '0',
    },

    keyBindings: {
      'space enter': '_handleCommitKey',
    },

    _handleAction(e) {
      if (this.disabled) {
        e.preventDefault();
        e.stopImmediatePropagation();
      }
    },

    _disabledChanged(disabled) {
      if (disabled) {
        this._enabledTabindex = this.getAttribute('tabindex');
      }
      this.setAttribute('tabindex', disabled ? '-1' : this._enabledTabindex);
    },

    _handleCommitKey(e) {
      e.preventDefault();
      this.click();
    },
  });
})();
