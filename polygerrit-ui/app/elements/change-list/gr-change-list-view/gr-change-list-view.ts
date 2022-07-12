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
<<<<<<< HEAD   (aa3a35 Merge branch 'stable-3.4' into stable-3.5)
import {RELOAD_DASHBOARD_INTERVAL_MS} from '../../../constants/constants';
=======
import {sharedStyles} from '../../../styles/shared-styles';
import {LitElement, PropertyValues, html, css} from 'lit';
import {customElement, property, state, query} from 'lit/decorators';
import {ValueChangedEvent} from '../../../types/events';
>>>>>>> CHANGE (a1f02b Remove the feature of auto-reloading from the search results)

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

  constructor() {
    super();
    this.addEventListener('next-page', () => this._handleNextPage());
    this.addEventListener('previous-page', () => this._handlePreviousPage());
    this.addEventListener('reload', () => this.reload());
<<<<<<< HEAD   (aa3a35 Merge branch 'stable-3.4' into stable-3.5)
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
=======
  }

  override connectedCallback() {
    super.connectedCallback();
    this.loadPreferences();
  }

  override disconnectedCallback() {
    super.disconnectedCallback();
  }

  static override get styles() {
    return [
      sharedStyles,
      css`
        :host {
          display: block;
        }
        .loading {
          color: var(--deemphasized-text-color);
          padding: var(--spacing-l);
        }
        gr-change-list {
          width: 100%;
        }
        gr-user-header,
        gr-repo-header {
          border-bottom: 1px solid var(--border-color);
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
        .hide {
          display: none;
        }
        @media only screen and (max-width: 50em) {
          .loading,
          .error {
            padding: 0 var(--spacing-l);
          }
        }
      `,
    ];
  }

  override render() {
    const loggedIn = !!(this.account && Object.keys(this.account).length > 0);
    // In case of an internal reload we want the ChangeList section components
    // to remain in the DOM so that the Bulk Actions Model associated with them
    // is not recreated after the reload resulting in user selections being lost
    return html`
      <div class="loading" ?hidden=${!this.loading}>Loading...</div>
      <div ?hidden=${this.loading}>
        ${this.renderRepoHeader()} ${this.renderUserHeader(loggedIn)}
        <gr-change-list
          .account=${this.account}
          .changes=${this.changes}
          .preferences=${this.preferences}
          .showStar=${loggedIn}
          .selectedIndex=${this.selectedIndex}
          @selected-index-changed=${(e: ValueChangedEvent<number>) => {
            this.selectedIndex = e.detail.value;
          }}
          @toggle-star=${(e: CustomEvent<ChangeStarToggleStarDetail>) => {
            this.handleToggleStar(e);
          }}
          .usp=${'search'}
        ></gr-change-list>
        ${this.renderChangeListViewNav()}
      </div>
    `;
  }

  private renderRepoHeader() {
    if (!this.repo) return;

    return html` <gr-repo-header .repo=${this.repo}></gr-repo-header> `;
  }

  private renderUserHeader(loggedIn: boolean) {
    if (!this.userId) return;

    return html`
      <gr-user-header
        .userId=${this.userId}
        showDashboardLink
        .loggedIn=${loggedIn}
      ></gr-user-header>
    `;
  }

  private renderChangeListViewNav() {
    if (this.loading || !this.changes || !this.changes.length) return;

    return html`
      <nav>
        Page ${this.computePage()} ${this.renderPrevArrow()}
        ${this.renderNextArrow()}
      </nav>
    `;
  }

  private renderPrevArrow() {
    if (this.offset === 0) return;

    return html`
      <a id="prevArrow" href=${this.computeNavLink(-1)}>
        <iron-icon icon="gr-icons:chevron-left" aria-label="Older"> </iron-icon>
      </a>
    `;
  }

  private renderNextArrow() {
    if (
      !(
        this.changes?.length &&
        this.changes[this.changes.length - 1]._more_changes
      )
    )
      return;

    return html`
      <a id="nextArrow" href=${this.computeNavLink(1)}>
        <iron-icon icon="gr-icons:chevron-right" aria-label="Newer">
        </iron-icon>
      </a>
    `;
  }

  override willUpdate(changedProperties: PropertyValues) {
    if (changedProperties.has('params')) {
      this.paramsChanged();
    }

    if (changedProperties.has('changes')) {
      this.changesChanged();
    }
  }

  reload() {
    if (this.loading) return;
    this.loading = true;
    this.getChanges().then(changes => {
      this.changes = changes || [];
      this.loading = false;
>>>>>>> CHANGE (a1f02b Remove the feature of auto-reloading from the search results)
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
              GerritNav.navigateToChange(
                changes[0],
                undefined,
                undefined,
                undefined,
                true
              );
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
