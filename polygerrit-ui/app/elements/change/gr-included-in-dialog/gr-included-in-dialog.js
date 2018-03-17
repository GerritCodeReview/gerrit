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
      changeNum: {
        type: Object,
        observer: '_resetData',
      },
      /** @type {?} */
      _includedIn: Object,
      _loaded: {
        type: Boolean,
        value: false,
      },
    },

    loadData() {
      if (!this.changeNum) { return; }
      return this.$.restAPI.getChangeIncludedIn(this.changeNum).then(
          configs => {
            if (!configs) { return; }
            this._includedIn = configs;
            this._loaded = true;
          });
    },

    _resetData() {
      this._includedIn = null;
      this._loaded = false;
    },

    _computeGroups(includedIn) {
      if (!includedIn) { return []; }

      const groups = [
        {title: 'Branches', items: includedIn.branches},
        {title: 'Tags', items: includedIn.tags},
      ];
      if (includedIn.external) {
        for (const externalKey of Object.keys(includedIn.external)) {
          groups.push({
            title: externalKey,
            items: includedIn.external[externalKey],
          });
        }
      }
      return groups;
    },

    _handleCloseTap(e) {
      e.preventDefault();
      this.fire('close', null, {bubbles: false});
    },

    _computeLoadingClass(loaded) {
      return loaded ? 'loading loaded' : 'loading';
    },
  });
})();
