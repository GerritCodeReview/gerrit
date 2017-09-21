// Copyright (C) 2018 The Android Open Source Project
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
    is: 'gr-included-in-dialog',

    /**
     * Fired when the user presses the close button.
     *
     * @event close
     */

    properties: {
      /** @type {?} */
      changeNum: Object,
      /** @type {?} */
      _includedIn: Object,
    },

    behaviors: [],

    attached() {
      this._loadIncludedIn();
    },

    _loadIncludedIn() {
      if (!this.changeNum) { return; }

      return this.$.restAPI.getChangeIncludedIn(this.changeNum).then(
          configs => {
            if (!configs) { return; }

            this._includedIn = configs;
          });
    },

    _toArray(obj) {
      if (!obj) { return; }

      return Object.keys(obj).map(key => {
        return {
          name: key,
          value: obj[key],
        };
      });
    },

    _formatData(value) {
      if (!value) { return; }

      let item;

      for (let i = 0; i < value.length; i++) {
        item = value;

        if (i < item.length - 1) {
          item.filter(items => items).join(', ');
        }
      }

      return item;
    },

    _handleCloseTap(e) {
      e.preventDefault();
      this.fire('close', null, {bubbles: false});
    },
  });
})();
