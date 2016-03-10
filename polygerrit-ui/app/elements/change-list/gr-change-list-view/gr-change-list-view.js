// Copyright (C) 2016 The Android Open Source Project
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
    is: 'gr-change-list-view',

    /**
     * Fired when the title of the page should change.
     *
     * @event title-change
     */

    properties: {
      /**
       * URL params passed from the router.
       */
      params: {
        type: Object,
        observer: '_paramsChanged',
      },

      /**
       * True when user is logged in.
       */
      loggedIn: {
        type: Boolean,
        value: false,
      },

      /**
       * State persisted across restamps of the element.
       */
      viewState: {
        type: Object,
        notify: true,
        value: function() { return {}; },
      },

      changesPerPage: Number,

      /**
       * Currently active query.
       */
      _query: String,

      /**
       * Offset of currently visible query results.
       */
      _offset: Number,

      /**
       * Change objects loaded from the server.
       */
      _changes: Array,

      /**
       * Contains error of last request (in case of change loading error).
       */
      _lastError: Object,

      /**
       * For showing a "loading..." string during ajax requests.
       */
      _loading: {
        type: Boolean,
        value: true,
      },
    },

    behaviors: [
      Gerrit.RESTClientBehavior,
    ],

    attached: function() {
      this.fire('title-change', {title: this._query});
    },

    _paramsChanged: function(value) {
      if (value.view != this.tagName.toLowerCase()) { return; }

      this._query = value.query;
      this._offset = value.offset || 0;
      if (this.viewState.query != this._query ||
          this.viewState.offset != this._offset) {
        this.set('viewState.selectedChangeIndex', 0);
        this.set('viewState.query', this._query);
        this.set('viewState.offset', this._offset);
      }

      this.fire('title-change', {title: this._query});
    },

    _computeQueryParams: function(query, offset, changesPerPage) {
      var options = this.listChangesOptionsToHex(
          this.ListChangesOption.LABELS,
          this.ListChangesOption.DETAILED_ACCOUNTS
      );
      var obj = {
        n: changesPerPage,
        O: options,
        S: offset || 0,
      };
      if (query && query.length > 0) {
        obj.q = query;
      }
      return obj;
    },

    _computeNavLink: function(query, offset, direction, changesPerPage) {
      // Offset could be a string when passed from the router.
      offset = +(offset || 0);
      var newOffset = Math.max(0, offset + (changesPerPage * direction));
      var href = '/q/' + query;
      if (newOffset > 0) {
        href += ',' + newOffset;
      }
      return href;
    },

    _computeErrorHidden: function(loading, lastError) {
      return loading || lastError == null;
    },

    _computeListHidden: function(loading, lastError) {
      return loading || lastError != null;
    },

    _hidePrevArrow: function(offset) {
      return offset == 0;
    },

    _hideNextArrow: function(changesLen, changesPerPage) {
      return changesLen < changesPerPage;
    },
  });
})();
