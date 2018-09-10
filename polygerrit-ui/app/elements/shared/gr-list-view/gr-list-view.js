/**
@license
Copyright (C) 2017 The Android Open Source Project

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
import '../../../../@polymer/polymer/polymer-legacy.js';

import '../../../behaviors/base-url-behavior/base-url-behavior.js';
import '../../../behaviors/gr-url-encoding-behavior/gr-url-encoding-behavior.js';
import '../../../styles/shared-styles.js';
import '../gr-button/gr-button.js';

const REQUEST_DEBOUNCE_INTERVAL_MS = 200;

Polymer({
  _template: Polymer.html`
    <style include="shared-styles">
      #filter {
        font-size: var(--font-size-normal);
        max-width: 25em;
      }
      #filter:focus {
        outline: none;
      }
      #topContainer {
        align-items: center;
        display: flex;
        height: 3rem;
        justify-content: space-between;
        margin: 0 1em;
      }
      #createNewContainer:not(.show) {
        display: none;
      }
      a {
        color: var(--primary-text-color);
        text-decoration: none;
      }
      a:hover {
        text-decoration: underline;
      }
      nav {
        align-items: center;
        display: flex;
        height: 3rem;
        justify-content: flex-end;
        margin-right: 20px;
      }
      nav,
      iron-icon {
        color: var(--deemphasized-text-color);
      }
      iron-icon {
        height: 1.85rem;
        margin-left: 16px;
        width: 1.85rem;
      }
    </style>
    <div id="topContainer">
      <div class="filterContainer">
        <label>Filter:</label>
        <input is="iron-input" type="text" id="filter" bind-value="{{filter}}">
      </div>
      <div id="createNewContainer" class\$="[[_computeCreateClass(createNew)]]">
        <gr-button primary="" link="" id="createNew" on-tap="_createNewItem">
          Create New
        </gr-button>
      </div>
    </div>
    <slot></slot>
    <nav>
      <a id="prevArrow" href\$="[[_computeNavLink(offset, -1, itemsPerPage, filter, path)]]" hidden\$="[[_hidePrevArrow(loading, offset)]]" hidden="">
        <iron-icon icon="gr-icons:chevron-left"></iron-icon>
      </a>
      <a id="nextArrow" href\$="[[_computeNavLink(offset, 1, itemsPerPage, filter, path)]]" hidden\$="[[_hideNextArrow(loading, items)]]" hidden="">
        <iron-icon icon="gr-icons:chevron-right"></iron-icon>
      </a>
    </nav>
`,

  is: 'gr-list-view',

  properties: {
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
  },

  behaviors: [
    Gerrit.BaseUrlBehavior,
    Gerrit.URLEncodingBehavior,
  ],

  detached() {
    this.cancelDebouncer('reload');
  },

  _filterChanged(newFilter, oldFilter) {
    if (!newFilter && !oldFilter) {
      return;
    }

    this._debounceReload(newFilter);
  },

  _debounceReload(filter) {
    this.debounce('reload', () => {
      if (filter) {
        return page.show(`${this.path}/q/filter:` +
            this.encodeURL(filter, false));
      }
      page.show(this.path);
    }, REQUEST_DEBOUNCE_INTERVAL_MS);
  },

  _createNewItem() {
    this.fire('create-clicked');
  },

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
  },

  _computeCreateClass(createNew) {
    return createNew ? 'show' : '';
  },

  _hidePrevArrow(loading, offset) {
    return loading || offset === 0;
  },

  _hideNextArrow(loading, items) {
    let lastPage = false;
    if (items.length < this.itemsPerPage + 1) {
      lastPage = true;
    }
    return loading || lastPage || !items || !items.length;
  }
});
