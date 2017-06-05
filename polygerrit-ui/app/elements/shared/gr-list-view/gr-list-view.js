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
      items: Array,
      itemsPerPage: Number,
      filter: String,
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

    _onValueChange(e) {
      this.debounce('reload', () => {
        if (e.target.value) {
          return page.show(`${this.path}/q/filter:` +
              this.encodeURL(e.target.value, false));
        }
        page.show(this.path);
      }, REQUEST_DEBOUNCE_INTERVAL_MS);
    },

    _computeNavLink(offset, direction, projectsPerPage, filter) {
      // Offset could be a string when passed from the router.
      offset = +(offset || 0);
      const newOffset = Math.max(0, offset + (projectsPerPage * direction));
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

    _hideNextArrow(loading, projects) {
      let lastPage = false;
      if (projects.length < this.itemsPerPage + 1) {
        lastPage = true;
      }
      return loading || lastPage || !projects || !projects.length;
    },
  });
})();
