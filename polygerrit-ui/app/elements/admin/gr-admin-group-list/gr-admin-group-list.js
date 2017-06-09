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
    is: 'gr-admin-group-list',

    properties: {
      /**
       * URL params passed from the router.
       */
      params: {
        type: Object,
        observer: '_paramsChanged',
      },

      /**
       * Offset of currently visible query results.
       */
      _offset: Number,

      _groups: Array,

      /**
       * Because  we request one more than the groupsPerPage, _shownGroups
       * may be one less than _groups.
       * */
      _shownGroups: {
        type: Array,
        computed: '_computeShownGroups(_groups)',
      },

      _groupsPerPage: {
        type: Number,
        value: 25,
      },

      _loading: {
        type: Boolean,
        value: true,
      },
      _filter: String,
    },

    behaviors: [
      Gerrit.BaseUrlBehavior,
      Gerrit.URLEncodingBehavior,
    ],

    listeners: {
      'next-page': '_handleNextPage',
      'previous-page': '_handlePreviousPage',
    },

    _paramsChanged(value) {
      this._loading = true;

      if (value) {
        this._filter = value.filter || null;
      }

      if (value && value.offset) {
        this._offset = value.offset;
      } else {
        this._offset = 0;
      }
      return this._getGroups(this._filter, this._groupsPerPage,
          this._offset);
    },

    _getGroups(filter, groupsPerPage, offset) {
      this._groups = [];
      return this.$.restAPI.getGroups(filter, groupsPerPage, offset)
          .then(groups => {
            if (!groups) {
              return;
            }
            this._groups = Object.keys(groups)
             .map(key => {
               const group = groups[key];
               group.name = key;
               return group;
             });
            this._loading = false;
          });
    },

    _computeLoadingClass(loading) {
      return loading ? 'loading' : '';
    },

    _visibleToAll(item) {
      return item.options.visible_to_all === true ? 'Y' : 'N';
    },

    _getUrl(item) {
      return this.getBaseUrl() + '/admin/groups/' +
          this.encodeURL(item, true);
    },

    _computeShownGroups(groups) {
      return groups.slice(0, 25);
    },
  });
})();
