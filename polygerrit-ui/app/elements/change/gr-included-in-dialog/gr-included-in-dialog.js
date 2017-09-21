// Copyright (C) 2017 The Android Open Source Project
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
      return this.$.restAPI.getChangeIncludedIn(this.changeNum).then(
          config => {
            if (!config) { return; }

            this._includedIn = config;
          });
    },

    _getBranches(includedIn) {
      if (!includedIn || !includedIn.branches) { return; }
      let branches;
      for (let i = 0; i < includedIn.branches.length; i++) {
        branches = includedIn.branches;

        if (i < includedIn.branches.length - 1) {
          branches.filter(item => item).join(', ');
        }
      }

      return branches;
    },

    _getTags(includedIn) {
      if (!includedIn || !includedIn.tags) { return; }
      let tags;
      for (let i = 0; i < includedIn.tags.length; i++) {
        tags = includedIn.tags;

        if (i < includedIn.tags.length - 1) {
          tags.filter(item => item).join(', ');
        }
      }

      return tags;
    },

    _handleCloseTap(e) {
      e.preventDefault();
      this.fire('close', null, {bubbles: false});
    },
  });
})();
