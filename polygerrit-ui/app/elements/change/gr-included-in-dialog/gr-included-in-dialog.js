/**
 * @license
 * Copyright (C) 2018 The Android Open Source Project
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

  /**
    * @appliesMixin Gerrit.FireMixin
    */
  class GrIncludedInDialog extends Polymer.mixinBehaviors( [
    Gerrit.FireBehavior,
  ], Polymer.GestureEventListeners(
      Polymer.LegacyElementMixin(
          Polymer.Element))) {
    static get is() { return 'gr-included-in-dialog'; }
    /**
     * Fired when the user presses the close button.
     *
     * @event close
     */

    static get properties() {
      return {
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
        _filterText: {
          type: String,
          value: '',
        },
      };
    }

    loadData() {
      if (!this.changeNum) { return; }
      this._filterText = '';
      return this.$.restAPI.getChangeIncludedIn(this.changeNum).then(
          configs => {
            if (!configs) { return; }
            this._includedIn = configs;
            this._loaded = true;
          });
    }

    _resetData() {
      this._includedIn = null;
      this._loaded = false;
    }

    _computeGroups(includedIn, filterText) {
      if (!includedIn) { return []; }

      const filter = item => !filterText.length ||
          item.toLowerCase().indexOf(filterText.toLowerCase()) !== -1;

      const groups = [
        {title: 'Branches', items: includedIn.branches.filter(filter)},
        {title: 'Tags', items: includedIn.tags.filter(filter)},
      ];
      if (includedIn.external) {
        for (const externalKey of Object.keys(includedIn.external)) {
          groups.push({
            title: externalKey,
            items: includedIn.external[externalKey].filter(filter),
          });
        }
      }
      return groups.filter(g => g.items.length);
    }

    _handleCloseTap(e) {
      e.preventDefault();
      e.stopPropagation();
      this.fire('close', null, {bubbles: false});
    }

    _computeLoadingClass(loaded) {
      return loaded ? 'loading loaded' : 'loading';
    }

    _onFilterChanged() {
      this.debounce('filter-change', () => {
        this._filterText = this.$.filterInput.bindValue;
      }, 100);
    }
  }

  customElements.define(GrIncludedInDialog.is, GrIncludedInDialog);
})();
