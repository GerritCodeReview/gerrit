/**
 * @license
 * Copyright (C) 2015 The Android Open Source Project
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

import '../../shared/gr-icons/gr-icons.js';
import '../../shared/gr-rest-api-interface/gr-rest-api-interface.js';
import '../gr-change-list/gr-change-list.js';
import '../gr-repo-header/gr-repo-header.js';
import '../gr-user-header/gr-user-header.js';
import '../../../styles/shared-styles.js';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners.js';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin.js';
import {PolymerElement} from '@polymer/polymer/polymer-element.js';
import {htmlTemplate} from './gr-change-list-view_html.js';
import {page} from '../../../utils/page-wrapper-utils.js';
import {GerritNav} from '../../core/gr-navigation/gr-navigation.js';
import {customElement, property} from '@polymer/decorators';

const LookupQueryPatterns = {
  CHANGE_ID: /^\s*i?[0-9a-f]{7,40}\s*$/i,
  CHANGE_NUM: /^\s*[1-9][0-9]*\s*$/g,
  COMMIT: /[0-9a-f]{40}/,
};

const USER_QUERY_PATTERN = /^owner:\s?("[^"]+"|[^ ]+)$/;

const REPO_QUERY_PATTERN = /^project:\s?("[^"]+"|[^ ]+)(\sstatus\s?:(open|"open"))?$/;

const LIMIT_OPERATOR_PATTERN = /\blimit:(\d+)/i;

/**
 * @extends PolymerElement
 */
@customElement('gr-change-list-view')
class GrChangeListView extends GestureEventListeners(
  LegacyElementMixin(PolymerElement)
) {
  static get template() {
    return htmlTemplate;
  }

  /**
   * Fired when the title of the page should change.
   *
   * @event title-change
   */

  @property({type: Object, observer: '_paramsChanged'})
  params?: unknown;

  @property({type: Boolean, computed: '_computeLoggedIn(account)'})
  _loggedIn?: boolean;

  @property({type: Object})
  account: unknown | null = null;

  @property({type: Object, notify: true})
  viewState: unknown = {};

  @property({type: Object})
  preferences?: unknown;

  @property({type: Number})
  _changesPerPage?: number;

  @property({type: String})
  _query = '';

  @property({type: Number})
  _offset?: number;

  @property({type: Array, observer: '_changesChanged'})
  _changes?: unknown;

  @property({type: Boolean})
  _loading = true;

  @property({type: String})
  _userId: string | null = null;

  @property({type: String})
  _repo: string | null = null;

  /** @override */
  created() {
    super.created();
    this.addEventListener('next-page', () => this._handleNextPage());
    this.addEventListener('previous-page', () => this._handlePreviousPage());
  }

  /** @override */
  attached() {
    super.attached();
    this._loadPreferences();
  }

  _paramsChanged(value) {
    if (value.view !== GerritNav.View.SEARCH) {
      return;
    }

    this._loading = true;
    this._query = value.query;
    this._offset = value.offset || 0;
    if (
      this.viewState.query != this._query ||
      this.viewState.offset != this._offset
    ) {
      this.set('viewState.selectedChangeIndex', 0);
      this.set('viewState.query', this._query);
      this.set('viewState.offset', this._offset);
    }

    // NOTE: This method may be called before attachment. Fire title-change
    // in an async so that attachment to the DOM can take place first.
    this.async(() =>
      this.dispatchEvent(
        new CustomEvent('title-change', {
          detail: {title: this._query},
          composed: true,
          bubbles: true,
        })
      )
    );

    this._getPreferences()
      .then(prefs => {
        this._changesPerPage = prefs.changes_per_page;
        return this._getChanges();
      })
      .then(changes => {
        changes = changes || [];
        if (this._query && changes.length === 1) {
          for (const query in LookupQueryPatterns) {
            if (
              LookupQueryPatterns.hasOwnProperty(query) &&
              this._query.match(LookupQueryPatterns[query])
            ) {
              // "Back"/"Forward" buttons work correctly only with
              // opt_redirect options
              GerritNav.navigateToChange(changes[0], null, null, null, true);
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

  _getChanges() {
    return this.$.restAPI.getChanges(
      this._changesPerPage,
      this._query,
      this._offset
    );
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
    const newOffset = Math.max(0, offset + limit * direction);
    return GerritNav.getUrlForSearchQuery(query, newOffset);
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
    if (this.$.nextArrow.hidden) {
      return;
    }
    page.show(
      this._computeNavLink(this._query, this._offset, 1, this._changesPerPage)
    );
  }

  _handlePreviousPage() {
    if (this.$.prevArrow.hidden) {
      return;
    }
    page.show(
      this._computeNavLink(this._query, this._offset, -1, this._changesPerPage)
    );
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
    this.$.restAPI.saveChangeStarred(e.detail.change._number, e.detail.starred);
  }

  _handleToggleReviewed(e) {
    this.$.restAPI.saveChangeReviewed(
      e.detail.change._number,
      e.detail.reviewed
    );
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-change-list-view': GrChangeListView;
  }
}
