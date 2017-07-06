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

  const REQUEST_DEBOUNCE_INTERVAL_MS = 200;

  Polymer({
    is: 'gr-list-view',

    properties: {
      createNew: {
        type: Boolean,
        observer: '_addEventListeners',
        value: false,
      },
      items: Array,
      itemsPerPage: Number,
      _filter: {
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

    listeners: {
      'next-page': '_handleNextPage',
      'previous-page': '_handlePreviousPage',
    },

    detached() {
      this.cancelDebouncer('reload');
      this._removeEventListeners(this.createNew);
    },

    _addEventListeners(createNew) {
      if (createNew) {
        Polymer.dom(this).querySelector('#createNewModal')
            .addEventListener('create', this._handleCreated.bind(this));

        Polymer.dom(this).querySelector('#createDialog')
            .addEventListener('cancel', this._closeCreateModal.bind(this));
      }
    },

    _removeEventListeners(createNew) {
      if (createNew) {
        Polymer.dom(this).querySelector('#createNewModal')
            .removeEventListener('create', this._handleCreated);

        Polymer.dom(this).querySelector('#createDialog')
            .removeEventListener('cancel', this._closeCreateModal);
      }
    },

    _filterChanged(filter) {
      this.debounce('reload', () => {
        if (filter) {
          return page.show(`${this.path}/q/filter:` +
              this.encodeURL(filter, false));
        }
        page.show(this.path);
      }, REQUEST_DEBOUNCE_INTERVAL_MS);
    },

    _createNewItem() {
      Polymer.dom(this).querySelector('#createOverlay').open();
    },

    _closeCreateModal() {
      Polymer.dom(this).querySelector('#createOverlay').close();
    },

    _handleCreated(e) {
      this._closeCreateModal();
      page.show(e.detail.url);
    },

    _computeNavLink(offset, direction, itemsPerPage, filter) {
      // Offset could be a string when passed from the router.
      offset = +(offset || 0);
      const newOffset = Math.max(0, offset + (itemsPerPage * direction));
      let href = this.getBaseUrl() + this.path;
      if (filter) {
        href += '/q/filter:' + filter;
      }
      if (newOffset > 0) {
        href += ',' + newOffset;
      }
      return href;
    },

    _hidePrevArrow(offset) {
      return offset === 0;
    },

    _hideNextArrow(loading, items) {
      let lastPage = false;
      if (items.length < this.itemsPerPage + 1) {
        lastPage = true;
      }
      return loading || lastPage || !items || !items.length;
    },
  });
})();
