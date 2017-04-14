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

  var LookupQueryPatterns = {
    CHANGE_ID: /^\s*i?[0-9a-f]{8,40}\s*$/i,
    CHANGE_NUM: /^\s*[1-9][0-9]*\s*$/g,
  };

  Polymer({
    is: 'gr-change-list-view',

    /**
     * Fired when the title of the page should change.
     *
     * @event title-change
     */

    behaviors: [
      Gerrit.BaseUrlBehavior,
      Gerrit.URLEncodingBehavior,
    ],

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

      _changesPerPage: Number,

      /**
       * Currently active query.
       */
      _query: {
        type: String,
        value: '',
      },

      /**
       * Offset of currently visible query results.
       */
      _offset: Number,

      /**
       * Change objects loaded from the server.
       */
      _changes: Array,

      /**
       * For showing a "loading..." string during ajax requests.
       */
      _loading: {
        type: Boolean,
        value: true,
      },
    },

    listeners: {
      'next-page': '_handleNextPage',
      'previous-page': '_handlePreviousPage',
    },

    attached: function() {
      this.fire('title-change', {title: this._query});
    },

    _paramsChanged: function(value) {
      if (value.view != this.tagName.toLowerCase()) { return; }

      this._loading = true;
      this._query = value.query;
      this._offset = value.offset || 0;
      if (this.viewState.query != this._query ||
          this.viewState.offset != this._offset) {
        this.set('viewState.selectedChangeIndex', 0);
        this.set('viewState.query', this._query);
        this.set('viewState.offset', this._offset);
      }

      this.fire('title-change', {title: this._query});

      this._getPreferences().then(function(prefs) {
        this._changesPerPage = prefs.changes_per_page;
        return this._getChanges();
      }.bind(this)).then(function(changes) {
        if (this._query && changes.length === 1) {
          for (var query in LookupQueryPatterns) {
            if (LookupQueryPatterns.hasOwnProperty(query) &&
                this._query.match(LookupQueryPatterns[query])) {
              page.show('/c/' + changes[0]._number);
              return;
            }
          }
        }
        this._changes = changes;
        this._loading = false;
      }.bind(this));
    },

    _getChanges: function() {
      return this.$.restAPI.getChanges(this._changesPerPage, this._query,
          this._offset);
    },

    _getPreferences: function() {
      return this.$.restAPI.getPreferences();
    },

    _computeNavLink: function(query, offset, direction, changesPerPage) {
      // Offset could be a string when passed from the router.
      offset = +(offset || 0);
      var newOffset = Math.max(0, offset + (changesPerPage * direction));
      // Double encode URI component.
      var href = this.getBaseUrl() + '/q/' + this.encodeURL(query, false);
      if (newOffset > 0) {
        href += ',' + newOffset;
      }
      return href;
    },

    _hidePrevArrow: function(offset) {
      return offset === 0;
    },

    _hideNextArrow: function(loading) {
      return loading || !this._changes || !this._changes.length ||
          !this._changes[this._changes.length - 1]._more_changes;
    },

    _handleNextPage: function() {
      if (this.$.nextArrow.hidden) { return; }
      page.show(this._computeNavLink(
          this._query, this._offset, 1, this._changesPerPage));
    },

    _handlePreviousPage: function() {
      if (this.$.prevArrow.hidden) { return; }
      page.show(this._computeNavLink(
          this._query, this._offset, -1, this._changesPerPage));
    },
  });
})();
