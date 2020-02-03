/**
 * @license
 * Copyright (C) 2020 The Android Open Source Project
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
    is: 'gr-message-header',

    properties: {
      message: {
        type: Object,
        reflectToAttribute: true,
      },
      _hidden: {
        type: Boolean,
        value: true,
      },
    },

    attached() {
      if (!this.message || !this.message.html) {
        return;
      }
      this._isHidden();
      this.$.message.innerHTML = this.message.html;
    },

    _handleDismissMessage() {
      document.cookie =
        `msg-${this.message.id}=1; expires=${this.message.redisplay}`;
      this._hidden = true;
    },

    _isHidden() {
      this._hidden = window.util.getCookie(`msg-${this.message.id}`) === '1';
    },
  });
})();
