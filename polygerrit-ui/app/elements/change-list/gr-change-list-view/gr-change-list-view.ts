/**
 * @license
 * Copyright 2015 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../gr-change-list/gr-change-list';
import '../gr-repo-header/gr-repo-header';
import '../gr-user-header/gr-user-header';
import {page} from '../../../utils/page-wrapper-utils';
import {
  AccountDetailInfo,
  AccountId,
  ChangeInfo,
  EmailAddress,
  PreferencesInput,
  RepoName,
} from '../../../types/common';
import {ChangeStarToggleStarDetail} from '../../shared/gr-change-star/gr-change-star';
import {fireAlert, fireEvent, fireTitleChange} from '../../../utils/event-util';
import {getAppContext} from '../../../services/app-context';
import {sharedStyles} from '../../../styles/shared-styles';
import {LitElement, PropertyValues, html, css, nothing} from 'lit';
import {customElement, state, query} from 'lit/decorators.js';
import {
  createSearchUrl,
  searchViewModelToken,
} from '../../../models/views/search';
import {resolve} from '../../../models/dependency';
import {subscribe} from '../../lit/subscription-controller';
import {userModelToken} from '../../../models/user/user-model';

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

  // private but used in test
  @state() account?: AccountDetailInfo;

  // private but used in test
  @state() loggedIn = false;

  // private but used in test
  @state() preferences?: PreferencesInput;

  // private but used in test
  @state() changesPerPage?: number;

  // private but used in test
  @state() query = '';

  // private but used in test
  @state() offset = 0;

  // private but used in test
  @state() changes: ChangeInfo[] = [];

  // private but used in test
  @state() loading = true;

  // private but used in test
  @state() userId?: AccountId | EmailAddress;

  // private but used in test
  @state() repo?: RepoName;

  private readonly restApiService = getAppContext().restApiService;

  private reporting = getAppContext().reportingService;

  private readonly getUserModel = resolve(this, userModelToken);

  private readonly getViewModel = resolve(this, searchViewModelToken);

  constructor() {
    super();
    this.addEventListener('next-page', () => this.handleNextPage());
    this.addEventListener('previous-page', () => this.handlePreviousPage());

    subscribe(
      this,
      () => this.getViewModel().query$,
      x => (this.query = x)
    );
    subscribe(
      this,
      () => this.getViewModel().offsetNumber$,
      x => (this.offset = x)
    );
    subscribe(
      this,
      () => this.getViewModel().loading$,
      x => (this.loading = x)
    );
    subscribe(
      this,
      () => this.getViewModel().changes$,
      x => (this.changes = x)
    );
    subscribe(
      this,
      () => this.getViewModel().userId$,
      x => (this.userId = x)
    );
    subscribe(
      this,
      () => this.getViewModel().repo$,
      x => (this.repo = x)
    );
    subscribe(
      this,
      () => this.getUserModel().account$,
      x => (this.account = x)
    );
    subscribe(
      this,
      () => this.getUserModel().loggedIn$,
      x => (this.loggedIn = x)
    );
    subscribe(
      this,
      () => this.getUserModel().preferenceChangesPerPage$,
      x => (this.changesPerPage = x)
    );
    subscribe(
      this,
      () => this.getUserModel().preferences$,
      x => (this.preferences = x)
    );
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
          color: var(--deemphasized-text-color);
        }
        gr-icon {
          font-size: 1.85rem;
          margin-left: 16px;
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
    // In case of an internal reload we want the ChangeList section components
    // to remain in the DOM so that the Bulk Actions Model associated with them
    // is not recreated after the reload resulting in user selections being lost
    return html`
      <div class="loading" ?hidden=${!this.loading}>Loading...</div>
      <div ?hidden=${this.loading}>
        ${this.renderRepoHeader()} ${this.renderUserHeader()}
        <gr-change-list
          .account=${this.account}
          .changes=${this.changes}
          .preferences=${this.preferences}
          .showStar=${this.loggedIn}
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
    if (!this.repo) return nothing;

    return html` <gr-repo-header .repo=${this.repo}></gr-repo-header> `;
  }

  private renderUserHeader() {
    if (!this.userId) return nothing;

    return html`
      <gr-user-header
        .userId=${this.userId}
        showDashboardLink
        .loggedIn=${this.loggedIn}
      ></gr-user-header>
    `;
  }

  private renderChangeListViewNav() {
    if (this.loading || !this.changes || !this.changes.length) return nothing;

    return html`
      <nav>
        Page ${this.computePage()} ${this.renderPrevArrow()}
        ${this.renderNextArrow()}
      </nav>
    `;
  }

  private renderPrevArrow() {
    if (this.offset === 0) return nothing;

    return html`
      <a id="prevArrow" href=${this.computeNavLink(-1)}>
        <gr-icon icon="chevron_left" aria-label="Older"></gr-icon>
      </a>
    `;
  }

  private renderNextArrow() {
    const changesCount = this.changes?.length ?? 0;
    if (changesCount === 0) return nothing;
    if (!this.changes?.[changesCount - 1]._more_changes) return nothing;

    return html`
      <a id="nextArrow" href=${this.computeNavLink(1)}>
        <gr-icon icon="chevron_right" aria-label="Newer"></gr-icon>
      </a>
    `;
  }

  override updated(changedProperties: PropertyValues) {
    if (changedProperties.has('query')) {
      fireTitleChange(this, this.query);
    }
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
    return createSearchUrl({query: this.query, offset: newOffset});
  }

  // private but used in test
  handleNextPage() {
    if (!this.nextArrow || !this.changesPerPage) return;
    // TODO: Use navigation service instead of `page.show()` directly.
    page.show(this.computeNavLink(1));
  }

  // private but used in test
  handlePreviousPage() {
    if (!this.prevArrow || !this.changesPerPage) return;
    // TODO: Use navigation service instead of `page.show()` directly.
    page.show(this.computeNavLink(-1));
  }

  // private but used in test
  computePage() {
    if (this.offset === undefined || this.changesPerPage === undefined) return;
    // We use Math.ceil in case the offset is not divisible by changesPerPage.
    // If we did not do this, you'd have page '1.2' and then when pressing left
    // arrow 'Page 1'.  This way page '1.2' becomes page '2'.
    return (
      Math.ceil(this.offset / this.limitFor(this.query, this.changesPerPage)) +
      1
    );
  }

  private async handleToggleStar(e: CustomEvent<ChangeStarToggleStarDetail>) {
    if (e.detail.starred) {
      this.reporting.reportInteraction('change-starred-from-change-list');
    }
    const msg = e.detail.starred
      ? 'Starring change...'
      : 'Unstarring change...';
    fireAlert(this, msg);
    await this.restApiService.saveChangeStarred(
      e.detail.change._number,
      e.detail.starred
    );
    fireEvent(this, 'hide-alert');
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-change-list-view': GrChangeListView;
  }
}
