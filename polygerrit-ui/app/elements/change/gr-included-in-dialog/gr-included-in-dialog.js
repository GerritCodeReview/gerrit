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

    behaviors: [
      Gerrit.RESTClientBehavior,
    ],

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
      let branches;
      for (let i = 0; i < includedIn.length; i++) {
        branches = includedIn[i];

        if (i < includedIn.length - 1) {
          branches.append(", ");
        }
      }

      return branches;
    },
  });
})();
