/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
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

  const LookupQueryPatterns = {
    CHANGE_ID: /^\s*i?[0-9a-f]{8,40}\s*$/i,
    CHANGE_NUM: /^\s*[1-9][0-9]*\s*$/g,
  };

  const USER_QUERY_PATTERN = /^owner:\s?("[^"]+"|[^ ]+)$/;

  const REPO_QUERY_PATTERN =
      /^project:\s?("[^"]+"|[^ ]+)(\sstatus\s?:(open|"open"))?$/;

  const LIMIT_OPERATOR_PATTERN = /\blimit:(\d+)/i;

  /**
   * @appliesMixin Gerrit.BaseUrlMixin
   * @appliesMixin Gerrit.FireMixin
   * @appliesMixin Gerrit.URLEncodingMixin
   * @extends Polymer.Element
   */
  class GrChangeListView extends Polymer.mixinBehaviors( [
    Gerrit.BaseUrlBehavior,
    Gerrit.FireBehavior,
    Gerrit.URLEncodingBehavior,
  ], Polymer.GestureEventListeners(
      Polymer.LegacyElementMixin(
          Polymer.Element))) {
    static get is() { return 'gr-change-list-view'; }
    /**
     * Fired when the title of the page should change.
     *
     * @event title-change
     */

    static get properties() {
      return {
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
        _loggedIn: {
          type: Boolean,
          computed: '_computeLoggedIn(account)',
        },

        account: {
          type: Object,
          value: null,
        },

        /**
         * State persisted across restamps of the element.
         *
         * Need sub-property declaration since it is used in template before
         * assignment.
         *
         * @type {{ selectedChangeIndex: (number|undefined) }}
         *
         */
        viewState: {
          type: Object,
          notify: true,
          value() { return {}; },
        },

        preferences: Object,

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
        _changes: {
          type: Array,
          observer: '_changesChanged',
        },

        /**
         * For showing a "loading..." string during ajax requests.
         */
        _loading: {
          type: Boolean,
          value: true,
        },

        /** @type {?string} */
        _userId: {
          type: String,
          value: null,
        },

        /** @type {?string} */
        _repo: {
          type: String,
          value: null,
        },
      };
    }

    /** @override */
    created() {
      super.created();
      this.addEventListener('next-page',
          () => this._handleNextPage());
      this.addEventListener('previous-page',
          () => this._handlePreviousPage());
    }

    /** @override */
    attached() {
      super.attached();
      this._loadPreferences();
    }

    _paramsChanged(value) {
      if (value.view !== Gerrit.Nav.View.SEARCH) { return; }

      this._loading = true;
      this._query = value.query;
      this._offset = value.offset || 0;
      if (this.viewState.query != this._query ||
          this.viewState.offset != this._offset) {
        this.set('viewState.selectedChangeIndex', 0);
        this.set('viewState.query', this._query);
        this.set('viewState.offset', this._offset);
      }

      // NOTE: This method may be called before attachment. Fire title-change
      // in an async so that attachment to the DOM can take place first.
      this.async(() => this.fire('title-change', {title: this._query}));

      this._getPreferences()
          .then(prefs => {
            this._changesPerPage = prefs.changes_per_page;
            return this._getChanges();
          })
          .then(changes => {
            changes = changes || [];
            if (this._query && changes.length === 1) {
              for (const query in LookupQueryPatterns) {
                if (LookupQueryPatterns.hasOwnProperty(query) &&
                this._query.match(LookupQueryPatterns[query])) {
                  this._replaceCurrentLocation(
                      Gerrit.Nav.getUrlForChange(changes[0]));
                  return;
                }
              }
            }
            this._changes = changes;
            this._loading = false;
          });
    }

    _loadPreferences() {
      return this.$.restAPI.getLoggedIn().then(loggedIn => {
        if (loggedIn) {
          this._getPreferences().then(preferences => {
            this.preferences = preferences;
          });
        } else {
          this.preferences = {};
        }
      });
    }

    _replaceCurrentLocation(url) {
      window.location.replace(url);
    }

    _getChanges() {
      return this.$.restAPI.getChanges(this._changesPerPage, this._query,
          this._offset);
    }

    _getPreferences() {
      return this.$.restAPI.getPreferences();
    }

    _limitFor(query, defaultLimit) {
      const match = query.match(LIMIT_OPERATOR_PATTERN);
      if (!match) {
        return defaultLimit;
      }
      return parseInt(match[1], 10);
    }

    _computeNavLink(query, offset, direction, changesPerPage) {
      // Offset could be a string when passed from the router.
      offset = +(offset || 0);
      const limit = this._limitFor(query, changesPerPage);
      const newOffset = Math.max(0, offset + (limit * direction));
      return Gerrit.Nav.getUrlForSearchQuery(query, newOffset);
    }

    _computePrevArrowClass(offset) {
      return offset === 0 ? 'hide' : '';
    }

    _computeNextArrowClass(changes) {
      const more = changes.length && changes[changes.length - 1]._more_changes;
      return more ? '' : 'hide';
    }

    _computeNavClass(loading) {
      return loading || !this._changes || !this._changes.length ? 'hide' : '';
    }

    _handleNextPage() {
      if (this.$.nextArrow.hidden) { return; }
      page.show(this._computeNavLink(
          this._query, this._offset, 1, this._changesPerPage));
    }

    _handlePreviousPage() {
      if (this.$.prevArrow.hidden) { return; }
      page.show(this._computeNavLink(
          this._query, this._offset, -1, this._changesPerPage));
    }

    _changesChanged(changes) {
      this._userId = null;
      this._repo = null;
      if (!changes || !changes.length) {
        return;
      }
      if (USER_QUERY_PATTERN.test(this._query)) {
        const owner = changes[0].owner;
        const userId = owner._account_id ? owner._account_id : owner.email;
        if (userId) {
          this._userId = userId;
          return;
        }
      }
      if (REPO_QUERY_PATTERN.test(this._query)) {
        this._repo = changes[0].project;
      }
    }

    _computeHeaderClass(id) {
      return id ? '' : 'hide';
    }

    _computePage(offset, changesPerPage) {
      return offset / changesPerPage + 1;
    }

    _computeLoggedIn(account) {
      return !!(account && Object.keys(account).length > 0);
    }

    _handleToggleStar(e) {
      this.$.restAPI.saveChangeStarred(e.detail.change._number,
          e.detail.starred);
    }

    _handleToggleReviewed(e) {
      this.$.restAPI.saveChangeReviewed(e.detail.change._number,
          e.detail.reviewed);
    }
  }

  customElements.define(GrChangeListView.is, GrChangeListView);
})();
