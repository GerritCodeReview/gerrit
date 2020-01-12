/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
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

  const REQUEST_DEBOUNCE_INTERVAL_MS = 200;

  /**
   * @appliesMixin Gerrit.BaseUrlMixin
   * @appliesMixin Gerrit.FireMixin
   * @appliesMixin Gerrit.URLEncodingMixin
   * @extends Polymer.Element
   */
  class GrListView extends Polymer.mixinBehaviors( [
    Gerrit.BaseUrlBehavior,
    Gerrit.FireBehavior,
    Gerrit.URLEncodingBehavior,
  ], Polymer.GestureEventListeners(
      Polymer.LegacyElementMixin(
          Polymer.Element))) {
    static get is() { return 'gr-list-view'; }

    static get properties() {
      return {
        createNew: Boolean,
        items: Array,
        itemsPerPage: Number,
        filter: {
          type: String,
          observer: '_filterChanged',
        },
        offset: Number,
        loading: Boolean,
        path: String,
      };
    }

    /** @override */
    detached() {
      super.detached();
      this.cancelDebouncer('reload');
    }

    _filterChanged(newFilter, oldFilter) {
      if (!newFilter && !oldFilter) {
        return;
      }

      this._debounceReload(newFilter);
    }

    _debounceReload(filter) {
      this.debounce('reload', () => {
        if (filter) {
          return page.show(`${this.path}/q/filter:` +
              this.encodeURL(filter, false));
        }
        page.show(this.path);
      }, REQUEST_DEBOUNCE_INTERVAL_MS);
    }

    _createNewItem() {
      this.fire('create-clicked');
    }

    _computeNavLink(offset, direction, itemsPerPage, filter, path) {
      // Offset could be a string when passed from the router.
      offset = +(offset || 0);
      const newOffset = Math.max(0, offset + (itemsPerPage * direction));
      let href = this.getBaseUrl() + path;
      if (filter) {
        href += '/q/filter:' + this.encodeURL(filter, false);
      }
      if (newOffset > 0) {
        href += ',' + newOffset;
      }
      return href;
    }

    _computeCreateClass(createNew) {
      return createNew ? 'show' : '';
    }

    _hidePrevArrow(loading, offset) {
      return loading || offset === 0;
    }

    _hideNextArrow(loading, items) {
      if (loading || !items || !items.length) {
        return true;
      }
      const lastPage = items.length < this.itemsPerPage + 1;
      return lastPage;
    }

    // TODO: fix offset (including itemsPerPage)
    // to either support a decimal or make it go to the nearest
    // whole number (e.g 3).
    _computePage(offset, itemsPerPage) {
      return offset / itemsPerPage + 1;
    }
  }

  customElements.define(GrListView.is, GrListView);
})();
