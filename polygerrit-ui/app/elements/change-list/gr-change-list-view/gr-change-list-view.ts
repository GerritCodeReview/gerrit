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

import '../../shared/gr-icons/gr-icons';
import '../gr-change-list/gr-change-list';
import '../gr-repo-header/gr-repo-header';
import '../gr-user-header/gr-user-header';
import '../../../styles/shared-styles';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-change-list-view_html';
import {page} from '../../../utils/page-wrapper-utils';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {customElement, property} from '@polymer/decorators';
import {AppElementParams} from '../../gr-app-types';
import {
  AccountDetailInfo,
  AccountId,
  ChangeInfo,
  EmailAddress,
  PreferencesInput,
  RepoName,
} from '../../../types/common';
import {ChangeStarToggleStarDetail} from '../../shared/gr-change-star/gr-change-star';
import {ChangeListViewState} from '../../../types/types';
import {fireTitleChange} from '../../../utils/event-util';
import {appContext} from '../../../services/app-context';
import {GerritView} from '../../../services/router/router-model';
import {RELOAD_DASHBOARD_INTERVAL_MS} from '../../../constants/constants';

const LOOKUP_QUERY_PATTERNS: RegExp[] = [
  /^\s*i?[0-9a-f]{7,40}\s*$/i, // CHANGE_ID
  /^\s*[1-9][0-9]*\s*$/g, // CHANGE_NUM
  /[0-9a-f]{40}/, // COMMIT
];

const USER_QUERY_PATTERN = /^owner:\s?("[^"]+"|[^ ]+)$/;

const REPO_QUERY_PATTERN =
  /^project:\s?("[^"]+"|[^ ]+)(\sstatus\s?:(open|"open"))?$/;

const LIMIT_OPERATOR_PATTERN = /\blimit:(\d+)/i;

export interface GrChangeListView {
  $: {
    prevArrow: HTMLAnchorElement;
    nextArrow: HTMLAnchorElement;
  };
}

@customElement('gr-change-list-view')
export class GrChangeListView extends PolymerElement {
  static get template() {
    return htmlTemplate;
  }

  /**
   * Fired when the title of the page should change.
   *
   * @event title-change
   */

  @property({type: Object, observer: '_paramsChanged'})
  params?: AppElementParams;

  @property({type: Boolean, computed: '_computeLoggedIn(account)'})
  _loggedIn?: boolean;

  @property({type: Object})
  account: AccountDetailInfo | null = null;

  @property({type: Object, notify: true})
  viewState: ChangeListViewState = {};

  @property({type: Object})
  preferences?: PreferencesInput;

  @property({type: Number})
  _changesPerPage?: number;

  @property({type: String})
  _query = '';

  @property({type: Number})
  _offset?: number;

  @property({type: Array, observer: '_changesChanged'})
  _changes?: ChangeInfo[];

  @property({type: Boolean})
  _loading = true;

  @property({type: String})
  _userId: AccountId | EmailAddress | null = null;

  @property({type: String})
  _repo: RepoName | null = null;

  private readonly restApiService = appContext.restApiService;

  private reporting = appContext.reportingService;

  private lastVisibleTimestampMs = 0;

  constructor() {
    super();
    this.addEventListener('next-page', () => this._handleNextPage());
    this.addEventListener('previous-page', () => this._handlePreviousPage());
    this.addEventListener('reload', () => this.reload());
    // We are not currently verifying if the view is actually visible. We rely
    // on gr-app-element to restamp the component if view changes
    document.addEventListener('visibilitychange', () => {
      if (document.visibilityState === 'visible') {
        if (
          Date.now() - this.lastVisibleTimestampMs >
          RELOAD_DASHBOARD_INTERVAL_MS
        )
          this.reload();
      } else {
        this.lastVisibleTimestampMs = Date.now();
      }
    });
  }

  override connectedCallback() {
    super.connectedCallback();
    this._loadPreferences();
  }

  reload() {
    if (this._loading) return;
    this._loading = true;
    this._getChanges().then(changes => {
      this._changes = changes || [];
      this._loading = false;
    });
  }

  _paramsChanged(value: AppElementParams) {
    if (value.view !== GerritView.SEARCH) return;

    this._loading = true;
    this._query = value.query;
    const offset = Number(value.offset);
    this._offset = isNaN(offset) ? 0 : offset;
    if (
      this.viewState.query !== this._query ||
      this.viewState.offset !== this._offset
    ) {
      this.set('viewState.selectedChangeIndex', 0);
      this.set('viewState.query', this._query);
      this.set('viewState.offset', this._offset);
    }

    // NOTE: This method may be called before attachment. Fire title-change
    // in an async so that attachment to the DOM can take place first.
    setTimeout(() => fireTitleChange(this, this._query));

    this.restApiService
      .getPreferences()
      .then(prefs => {
        if (!prefs) {
          throw new Error('getPreferences returned undefined');
        }
        this._changesPerPage = prefs.changes_per_page;
        return this._getChanges();
      })
      .then(changes => {
        changes = changes || [];
        if (this._query && changes.length === 1) {
          for (const queryPattern of LOOKUP_QUERY_PATTERNS) {
            if (this._query.match(queryPattern)) {
              // "Back"/"Forward" buttons work correctly only with
              // opt_redirect options
              GerritNav.navigateToChange(changes[0], {
                redirect: true,
              });
              return;
            }
          }
        }
        this._changes = changes;
        this._loading = false;
      });
  }

  _loadPreferences() {
    return this.restApiService.getLoggedIn().then(loggedIn => {
      if (loggedIn) {
        this.restApiService.getPreferences().then(preferences => {
          this.preferences = preferences;
        });
      } else {
        this.preferences = {};
      }
    });
  }

  _getChanges() {
    return this.restApiService.getChanges(
      this._changesPerPage,
      this._query,
      this._offset
    );
  }

  _limitFor(query: string, defaultLimit: number) {
    const match = query.match(LIMIT_OPERATOR_PATTERN);
    if (!match) {
      return defaultLimit;
    }
    return Number(match[1]);
  }

  _computeNavLink(
    query: string,
    offset: number | undefined,
    direction: number,
    changesPerPage: number
  ) {
    offset = offset ?? 0;
    const limit = this._limitFor(query, changesPerPage);
    const newOffset = Math.max(0, offset + limit * direction);
    return GerritNav.getUrlForSearchQuery(query, newOffset);
  }

  _computePrevArrowClass(offset?: number) {
    return offset === 0 ? 'hide' : '';
  }

  _computeNextArrowClass(changes?: ChangeInfo[]) {
    const more = changes?.length && changes[changes.length - 1]._more_changes;
    return more ? '' : 'hide';
  }

  _computeNavClass(loading?: boolean) {
    return loading || !this._changes || !this._changes.length ? 'hide' : '';
  }

  _handleNextPage() {
    if (this.$.nextArrow.hidden || !this._changesPerPage) return;
    page.show(
      this._computeNavLink(this._query, this._offset, 1, this._changesPerPage)
    );
  }

  _handlePreviousPage() {
    if (this.$.prevArrow.hidden || !this._changesPerPage) return;
    page.show(
      this._computeNavLink(this._query, this._offset, -1, this._changesPerPage)
    );
  }

  _changesChanged(changes?: ChangeInfo[]) {
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

  _computeHeaderClass(id?: string) {
    return id ? '' : 'hide';
  }

  _computePage(offset?: number, changesPerPage?: number) {
    if (offset === undefined || changesPerPage === undefined) return;
    return offset / changesPerPage + 1;
  }

  _computeLoggedIn(account?: AccountDetailInfo) {
    return !!(account && Object.keys(account).length > 0);
  }

  _handleToggleStar(e: CustomEvent<ChangeStarToggleStarDetail>) {
    if (e.detail.starred) {
      this.reporting.reportInteraction('change-starred-from-change-list');
    }
    this.restApiService.saveChangeStarred(
      e.detail.change._number,
      e.detail.starred
    );
  }

  /**
   * Returns `this` as the visibility observer target for the keyboard shortcut
   * mixin to decide whether shortcuts should be enabled or not.
   */
  _computeObserverTarget() {
    return this;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-change-list-view': GrChangeListView;
  }
}
