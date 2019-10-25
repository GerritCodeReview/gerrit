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

  Polymer({
    is: 'gr-documentation-search',

    properties: {
      /**
       * URL params passed from the router.
       */
      params: {
        type: Object,
        observer: '_paramsChanged',
      },

      _path: {
        type: String,
        readOnly: true,
        value: '/Documentation',
      },
      _documentationSearches: Array,

      _loading: {
        type: Boolean,
        value: true,
      },
      _filter: {
        type: String,
        value: '',
      },
    },

    behaviors: [
      Gerrit.ListViewBehavior,
    ],

    attached() {
      this.dispatchEvent(
          new CustomEvent('title-change', {title: 'Documentation Search'}));
    },

    _paramsChanged(params) {
      this._loading = true;
      this._filter = this.getFilterValue(params);

      return this._getDocumentationSearches(this._filter);
    },

    _getDocumentationSearches(filter) {
      this._documentationSearches = [];
      return this.$.restAPI.getDocumentationSearches(filter)
          .then(searches => {
            // Late response.
            if (filter !== this._filter || !searches) { return; }
            this._documentationSearches = searches;
            this._loading = false;
          });
    },

    _computeSearchUrl(url) {
      if (!url) { return ''; }
      return this.getBaseUrl() + '/' + url;
    },
  });
})();
