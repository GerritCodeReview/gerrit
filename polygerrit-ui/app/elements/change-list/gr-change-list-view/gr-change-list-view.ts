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
import {page} from '../../../utils/page-wrapper-utils';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
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
import {fire, fireTitleChange} from '../../../utils/event-util';
import {getAppContext} from '../../../services/app-context';
import {GerritView} from '../../../services/router/router-model';
import {RELOAD_DASHBOARD_INTERVAL_MS} from '../../../constants/constants';
import {sharedStyles} from '../../../styles/shared-styles';
import {LitElement, PropertyValues, html, css} from 'lit';
import {customElement, property, state, query} from 'lit/decorators';
import {ValueChangedEvent} from '../../../types/events';
import {resolve} from '../../../models/dependency';
import {viewModelToken} from '../../../models/view/view-model';
import {CHANGE_LIST} from '../gr-change-list/gr-change-list';

const LOOKUP_QUERY_PATTERNS: RegExp[] = [
  /^\s*i?[0-9a-f]{7,40}\s*$/i, // CHANGE_ID
  /^\s*[1-9][0-9]*\s*$/g, // CHANGE_NUM
  /[0-9a-f]{40}/, // COMMIT
];

const USER_QUERY_PATTERN = /^owner:\s?("[^"]+"|[^ ]+)$/;

const REPO_QUERY_PATTERN =
  /^project:\s?("[^"]+"|[^ ]+)(\sstatus\s?:(open|"open"))?$/;

const LIMIT_OPERATOR_PATTERN = /\blimit:(\d+)/i;

@customElement('gr-change-list-view')
export class GrChangeListView extends LitElement {
  /**
   * Fired when the title of the page should change.
   *
   * @event title-change
   */

  @query('#prevArrow') protected prevArrow?: HTMLAnchorElement;

  @query('#nextArrow') protected nextArrow?: HTMLAnchorElement;

  @property({type: Object})
  params?: AppElementParams;

  @property({type: Object})
  account: AccountDetailInfo | null = null;

  @property({type: Object})
  viewState: ChangeListViewState = {};

  @property({type: Object})
  preferences?: PreferencesInput;

  // private but used in test
  @state() changesPerPage?: number;

  // private but used in test
  @state() query = '';

  // private but used in test
  @state() offset?: number;

  // private but used in test
  @state() changes?: ChangeInfo[];

  // private but used in test
  @state() loading = true;

  // private but used in test
  @state() userId: AccountId | EmailAddress | null = null;

  // private but used in test
  @state() repo: RepoName | null = null;

  private readonly restApiService = getAppContext().restApiService;

  private reporting = getAppContext().reportingService;

  private readonly getViewModel = resolve(this, viewModelToken);

  private lastVisibleTimestampMs = 0;

  constructor() {
    super();
    this.addEventListener('next-page', () => this.handleNextPage());
    this.addEventListener('previous-page', () => this.handlePreviousPage());
    this.addEventListener('reload', () => this.reload());
  }

  private readonly visibilityChangeListener = () => {
    if (document.visibilityState === 'visible') {
      if (
        Date.now() - this.lastVisibleTimestampMs >
        RELOAD_DASHBOARD_INTERVAL_MS
      )
        this.reload();
    } else {
      this.lastVisibleTimestampMs = Date.now();
    }
  };

  override connectedCallback() {
    super.connectedCallback();
    this.loadPreferences();
    document.addEventListener(
      'visibilitychange',
      this.visibilityChangeListener
    );
  }

  override disconnectedCallback() {
    document.removeEventListener(
      'visibilitychange',
      this.visibilityChangeListener
    );
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
          .selectedIndex=${this.viewState.selectedChangeIndex}
          .showStar=${loggedIn}
          @selected-index-changed=${(e: ValueChangedEvent<number>) => {
            this.handleSelectedIndexChanged(e);
          }}
          @toggle-star=${(e: CustomEvent<ChangeStarToggleStarDetail>) => {
            this.handleToggleStar(e);
          }}
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
    });
  }

  private paramsChanged() {
    const value = this.params;
    if (!value || value.view !== GerritView.SEARCH) return;

    this.loading = true;
    if (this.query !== value.query) {
      this.getViewModel().setSelectedIndexForDashboard(CHANGE_LIST, 0);
    }
    this.query = value.query;
    const offset = Number(value.offset);
    this.offset = isNaN(offset) ? 0 : offset;
    if (
      this.viewState.query !== this.query ||
      this.viewState.offset !== this.offset
    ) {
      this.viewState.selectedChangeIndex = 0;
      this.viewState.query = this.query;
      this.viewState.offset = this.offset;
      fire(this, 'view-state-change-list-view-changed', {
        value: this.viewState,
      });
    }

    // NOTE: This method may be called before attachment. Fire title-change
    // in an async so that attachment to the DOM can take place first.
    setTimeout(() => fireTitleChange(this, this.query));

    this.restApiService
      .getPreferences()
      .then(prefs => {
        if (!prefs) {
          throw new Error('getPreferences returned undefined');
        }
        this.changesPerPage = prefs.changes_per_page;
        return this.getChanges();
      })
      .then(changes => {
        changes = changes || [];
        if (this.query && changes.length === 1) {
          for (const queryPattern of LOOKUP_QUERY_PATTERNS) {
            if (this.query.match(queryPattern)) {
              // "Back"/"Forward" buttons work correctly only with
              // opt_redirect options
              GerritNav.navigateToChange(changes[0], {
                redirect: true,
              });
              return;
            }
          }
        }
        this.changes = changes;
        this.loading = false;
      });
  }

  private loadPreferences() {
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

  // private but used in test
  getChanges() {
    return this.restApiService.getChanges(
      this.changesPerPage,
      this.query,
      this.offset
    );
  }

  // private but used in test
  limitFor(query: string, defaultLimit?: number) {
    if (defaultLimit === undefined) return 0;
    const match = query.match(LIMIT_OPERATOR_PATTERN);
    if (!match) {
      return defaultLimit;
    }
    return Number(match[1]);
  }

  // private but used in test
  computeNavLink(direction: number) {
    const offset = this.offset ?? 0;
    const limit = this.limitFor(this.query, this.changesPerPage);
    const newOffset = Math.max(0, offset + limit * direction);
    return GerritNav.getUrlForSearchQuery(this.query, newOffset);
  }

  // private but used in test
  handleNextPage() {
    if (!this.nextArrow || !this.changesPerPage) return;
    page.show(this.computeNavLink(1));
  }

  // private but used in test
  handlePreviousPage() {
    if (!this.prevArrow || !this.changesPerPage) return;
    page.show(this.computeNavLink(-1));
  }

  private changesChanged() {
    this.userId = null;
    this.repo = null;
    const changes = this.changes;
    if (!changes || !changes.length) {
      return;
    }
    if (USER_QUERY_PATTERN.test(this.query)) {
      const owner = changes[0].owner;
      const userId = owner._account_id ? owner._account_id : owner.email;
      if (userId) {
        this.userId = userId;
        return;
      }
    }
    if (REPO_QUERY_PATTERN.test(this.query)) {
      this.repo = changes[0].project;
    }
  }

  // private but used in test
  computePage() {
    if (this.offset === undefined || this.changesPerPage === undefined) return;
    return this.offset / this.changesPerPage + 1;
  }

  private handleToggleStar(e: CustomEvent<ChangeStarToggleStarDetail>) {
    if (e.detail.starred) {
      this.reporting.reportInteraction('change-starred-from-change-list');
    }
    this.restApiService.saveChangeStarred(
      e.detail.change._number,
      e.detail.starred
    );
  }

  private handleSelectedIndexChanged(e: ValueChangedEvent<number>) {
    if (!this.viewState) return;
    this.viewState.selectedChangeIndex = e.detail.value;
    fire(this, 'view-state-change-list-view-changed', {value: this.viewState});
  }
}

declare global {
  interface HTMLElementEventMap {
    'view-state-change-list-view-changed': ValueChangedEvent<ChangeListViewState>;
  }
  interface HTMLElementTagNameMap {
    'gr-change-list-view': GrChangeListView;
  }
}
