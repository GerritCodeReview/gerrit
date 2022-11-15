/**
 * @license
 * Copyright 2015 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../gr-change-list/gr-change-list';
import '../../shared/gr-button/gr-button';
import '../../shared/gr-dialog/gr-dialog';
import '../gr-create-commands-dialog/gr-create-commands-dialog';
import '../gr-create-change-help/gr-create-change-help';
import '../gr-create-destination-dialog/gr-create-destination-dialog';
import '../gr-user-header/gr-user-header';
import {getAppContext} from '../../../services/app-context';
import {changeIsOpen} from '../../../utils/change-util';
import {parseDate} from '../../../utils/date-util';
import {
  AccountDetailInfo,
  ChangeInfo,
  DashboardId,
  PreferencesInput,
  RepoName,
} from '../../../types/common';
import {GrDialog} from '../../shared/gr-dialog/gr-dialog';
import {GrCreateCommandsDialog} from '../gr-create-commands-dialog/gr-create-commands-dialog';
import {
  CreateDestinationConfirmDetail,
  GrCreateDestinationDialog,
} from '../gr-create-destination-dialog/gr-create-destination-dialog';
import {ChangeStarToggleStarDetail} from '../../shared/gr-change-star/gr-change-star';
import {
  fireAlert,
  fireEvent,
  firePageError,
  fireTitleChange,
} from '../../../utils/event-util';
import {RELOAD_DASHBOARD_INTERVAL_MS} from '../../../constants/constants';
import {ChangeListSection} from '../gr-change-list/gr-change-list';
import {a11yStyles} from '../../../styles/gr-a11y-styles';
import {sharedStyles} from '../../../styles/shared-styles';
import {LitElement, html, css, nothing} from 'lit';
import {customElement, property, state, query} from 'lit/decorators.js';
import {assertIsDefined} from '../../../utils/common-util';
import {Shortcut} from '../../../services/shortcuts/shortcuts-config';
import {ShortcutController} from '../../lit/shortcut-controller';
import {
  dashboardViewModelToken,
  DashboardViewState,
} from '../../../models/views/dashboard';
import {createSearchUrl} from '../../../models/views/search';
import {subscribe} from '../../lit/subscription-controller';
import {resolve} from '../../../models/dependency';
import {
  getUserDashboard,
  OUTGOING,
  UserDashboard,
  YOUR_TURN,
} from '../../../utils/dashboard-util';
import {userModelToken} from '../../../models/user/user-model';
import {Timing} from '../../../constants/reporting';
import {modalStyles} from '../../../styles/gr-modal-styles';

const PROJECT_PLACEHOLDER_PATTERN = /\${project}/g;

const slotNameBySectionName = new Map<string, string>([
  [YOUR_TURN.name, 'your-turn-slot'],
  [OUTGOING.name, 'outgoing-slot'],
]);

@customElement('gr-dashboard-view')
export class GrDashboardView extends LitElement {
  /**
   * Fired when the title of the page should change.
   *
   * @event title-change
   */

  @query('#confirmDeleteDialog') protected confirmDeleteDialog?: GrDialog;

  @query('#commandsDialog') protected commandsDialog?: GrCreateCommandsDialog;

  @query('#destinationDialog')
  protected destinationDialog?: GrCreateDestinationDialog;

  @query('#confirmDeleteModal')
  protected confirmDeleteModal?: HTMLDialogElement;

  @property({type: Object})
  account?: AccountDetailInfo;

  @property({type: Object})
  preferences?: PreferencesInput;

  @state()
  viewState?: DashboardViewState;

  // private but used in test
  @state() results?: ChangeListSection[];

  // private but used in test
  @state() loading = true;

  // private but used in test
  @state() showDraftsBanner = false;

  // private but used in test
  @state() showNewUserHelp = false;

  private reporting = getAppContext().reportingService;

  private readonly restApiService = getAppContext().restApiService;

  private readonly getUserModel = resolve(this, userModelToken);

  private readonly getViewModel = resolve(this, dashboardViewModelToken);

  private lastVisibleTimestampMs = 0;

  /**
   * For `DASHBOARD_DISPLAYED` timing we can only rely on the router to have
   * reset the timer properly when the dashboard loads for the first time.
   * Later we won't have a guarantee that the timer was just reset. So we will
   * just reset the timer at the beginning of `reload()`. The dashboard view
   * is cached anyway, so there is unlikely a lot of time that has passed
   * initiating the reload and the reload() method being executed.
   */
  private firstTimeLoad = true;

  private readonly shortcuts = new ShortcutController(this);

  constructor() {
    super();
    subscribe(
      this,
      () => this.getUserModel().account$,
      x => (this.account = x)
    );
    subscribe(
      this,
      () => this.getViewModel().state$,
      x => {
        this.viewState = x;
        this.reload();
      }
    );
    this.addEventListener('reload', () => this.reload());
    this.shortcuts.addAbstract(Shortcut.UP_TO_DASHBOARD, () => this.reload());
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
      a11yStyles,
      sharedStyles,
      modalStyles,
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
        gr-user-header {
          border-bottom: 1px solid var(--border-color);
        }
        .banner {
          align-items: center;
          background-color: var(--comment-background-color);
          border-bottom: 1px solid var(--border-color);
          display: flex;
          justify-content: space-between;
          padding: var(--spacing-xs) var(--spacing-l);
        }
        .hide {
          display: none;
        }
        #emptyOutgoing {
          display: block;
        }
        @media only screen and (max-width: 50em) {
          .loading {
            padding: 0 var(--spacing-l);
          }
        }
      `,
    ];
  }

  override render() {
    if (!this.viewState) return nothing;
    return html`
      ${this.renderBanner()} ${this.renderContent()}
      <dialog id="confirmDeleteModal" tabindex="-1">
        <gr-dialog
          id="confirmDeleteDialog"
          confirm-label="Delete"
          @confirm=${() => {
            this.handleConfirmDelete();
          }}
          @cancel=${() => {
            this.closeConfirmDeleteModal();
          }}
        >
          <div class="header" slot="header">Delete comments</div>
          <div class="main" slot="main">
            Are you sure you want to delete all your draft comments in closed
            changes? This action cannot be undone.
          </div>
        </gr-dialog>
      </dialog>
      <gr-create-destination-dialog
        id="destinationDialog"
        @confirm=${(e: CustomEvent<CreateDestinationConfirmDetail>) => {
          this.handleDestinationConfirm(e);
        }}
      ></gr-create-destination-dialog>
      <gr-create-commands-dialog
        id="commandsDialog"
      ></gr-create-commands-dialog>
    `;
  }

  private renderBanner() {
    if (!this.showDraftsBanner) return;

    return html`
      <div class="banner">
        <div>
          You have draft comments on closed changes.
          <a href=${this.computeDraftsLink()} target="_blank">(view all)</a>
        </div>
        <div>
          <gr-button
            class="delete"
            link
            @click=${() => {
              this.handleOpenDeleteDialog();
            }}
            >Delete All</gr-button
          >
        </div>
      </div>
    `;
  }

  private renderContent() {
    // In case of an internal reload we want the ChangeList section components
    // to remain in the DOM so that the Bulk Actions Model associated with them
    // is not recreated after the reload resulting in user selections being lost
    return html`
      <div class="loading" ?hidden=${!this.loading}>Loading...</div>
      <div ?hidden=${this.loading}>
        ${this.renderUserHeader()}
        <h1 class="assistive-tech-only">Dashboard</h1>
        <gr-change-list
          ?showStar=${true}
          .account=${this.account}
          .preferences=${this.preferences}
          .sections=${this.results}
          .usp=${'dashboard'}
          @toggle-star=${(e: CustomEvent<ChangeStarToggleStarDetail>) => {
            this.handleToggleStar(e);
          }}
        >
          <div id="emptyOutgoing" slot="outgoing-slot">
            ${this.renderShowNewUserHelp()}
          </div>
          <div id="emptyYourTurn" slot="your-turn-slot">
            <span>No changes need your attention &nbsp;&#x1f389;</span>
          </div>
        </gr-change-list>
      </div>
    `;
  }

  private renderUserHeader() {
    if (
      !!this.viewState?.project ||
      !this.viewState?.user ||
      this.viewState?.user === 'self'
    ) {
      return;
    }

    return html`
      <gr-user-header .userId=${this.viewState?.user}></gr-user-header>
    `;
  }

  private renderShowNewUserHelp() {
    if (!this.showNewUserHelp) return ' No changes ';

    return html`
      <gr-create-change-help
        @create-tap=${() => {
          this.handleCreateChangeTap();
        }}
      ></gr-create-change-help>
    `;
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
  getRepositoryDashboard(
    repo: RepoName,
    dashboard?: DashboardId
  ): Promise<UserDashboard | undefined> {
    const errFn = (response?: Response | null) => {
      firePageError(response);
    };
    assertIsDefined(dashboard, 'project dashboard must have id');
    return this.restApiService
      .getDashboard(repo, dashboard, errFn)
      .then(response => {
        if (!response) {
          return;
        }
        return {
          title: response.title,
          sections: response.sections.map(section => {
            const suffix = response.foreach ? ' ' + response.foreach : '';
            return {
              name: section.name,
              query: (section.query + suffix).replace(
                PROJECT_PLACEHOLDER_PATTERN,
                repo
              ),
            };
          }),
        };
      });
  }

  // private but used in test
  computeTitle(user?: string) {
    if (!user || user === 'self') {
      return 'My Reviews';
    }
    return 'Dashboard for ' + user;
  }

  /**
   * Reloads the element.
   *
   * private but used in test
   */
  reload() {
    if (!this.viewState) return Promise.resolve();

    // See `firstTimeLoad` comment above.
    if (!this.firstTimeLoad) {
      this.reporting.time(Timing.DASHBOARD_DISPLAYED);
    }
    this.firstTimeLoad = false;

    this.loading = true;
    const {project, dashboard, title, user, sections} = this.viewState;

    const dashboardPromise: Promise<UserDashboard | undefined> = project
      ? this.getRepositoryDashboard(project, dashboard)
      : Promise.resolve(
          getUserDashboard(user, sections, title || this.computeTitle(user))
        );
    // Checking `this.account` to make sure that the user is logged in.
    // Otherwise sending a query for 'owner:self' will result in an error.
    const checkForNewUser = !project && !!this.account && user === 'self';
    return dashboardPromise
      .then(res => {
        if (res && res.title) {
          fireTitleChange(this, res.title);
        }
        return this.fetchDashboardChanges(res, checkForNewUser);
      })
      .then(() => {
        this.maybeShowDraftsBanner();
        this.reporting.dashboardDisplayed();
      })
      .catch(err => {
        fireTitleChange(this, title || this.computeTitle(user));
        this.reporting.error('Dashboard reload', err);
      })
      .finally(() => {
        this.loading = false;
      });
  }

  /**
   * Fetches the changes for each dashboard section and sets this.results
   * with the response.
   *
   * private but used in test
   */
  fetchDashboardChanges(
    res: UserDashboard | undefined,
    checkForNewUser: boolean
  ): Promise<void> {
    if (!res) {
      return Promise.resolve();
    }

    let queries: string[];

    if (window.PRELOADED_QUERIES && window.PRELOADED_QUERIES.dashboardQuery) {
      queries = window.PRELOADED_QUERIES.dashboardQuery;
      // we use preloaded query from index only on first page load
      window.PRELOADED_QUERIES.dashboardQuery = undefined;
    } else {
      queries = res.sections.map(section =>
        section.suffixForDashboard
          ? section.query + ' ' + section.suffixForDashboard
          : section.query
      );

      if (checkForNewUser) {
        queries.push('owner:self limit:1');
      }
    }

    return this.restApiService
      .getChangesForMultipleQueries(undefined, queries)
      .then(changes => {
        if (!changes) {
          throw new Error('getChanges returns undefined');
        }
        if (checkForNewUser) {
          // Last set of results is not meant for dashboard display.
          const lastResultSet = changes.pop();
          this.showNewUserHelp = lastResultSet!.length === 0;
        }
        this.results = changes
          .map((results, i) => {
            return {
              name: res.sections[i].name,
              countLabel: this.computeSectionCountLabel(results),
              query: res.sections[i].query,
              results: this.maybeSortResults(res.sections[i].name, results),
              emptyStateSlotName: slotNameBySectionName.get(
                res.sections[i].name
              ),
            };
          })
          .filter(
            (section, i) =>
              i < res.sections.length &&
              (!res.sections[i].hideIfEmpty || section.results.length)
          );
      });
  }

  /**
   * Usually we really want to stick to the sorting that the backend provides,
   * but for the "Your Turn" section it is important to put the changes at the
   * top where the current user is a reviewer. Owned changes are less important.
   * And then we want to emphasize the changes where the waiting time is larger.
   */
  private maybeSortResults(name: string, results: ChangeInfo[]) {
    const userId = this.account?._account_id;
    const sortedResults = [...results];
    if (name === YOUR_TURN.name && userId) {
      sortedResults.sort((c1, c2) => {
        const c1Owner = c1.owner._account_id === userId;
        const c2Owner = c2.owner._account_id === userId;
        if (c1Owner !== c2Owner) return c1Owner ? 1 : -1;
        // Should never happen, because the change is in the 'Your Turn'
        // section, so the userId should be found in the attention set of both.
        if (!c1.attention_set || !c1.attention_set[userId]) return 0;
        if (!c2.attention_set || !c2.attention_set[userId]) return 0;
        const c1Update = c1.attention_set[userId].last_update;
        const c2Update = c2.attention_set[userId].last_update;
        // Should never happen that an attention set entry has no update.
        if (!c1Update || !c2Update) return c1Update ? 1 : -1;
        return parseDate(c1Update).valueOf() - parseDate(c2Update).valueOf();
      });
    }
    return sortedResults;
  }

  // private but used in test
  computeSectionCountLabel(changes: ChangeInfo[]) {
    if (!changes || !changes.length || changes.length === 0) {
      return '';
    }
    const more = changes[changes.length - 1]._more_changes;
    const numChanges = changes.length;
    const andMore = more ? ' and more' : '';
    return `(${numChanges}${andMore})`;
  }

  // private but used in test
  async handleToggleStar(e: CustomEvent<ChangeStarToggleStarDetail>) {
    const msg = e.detail.starred
      ? 'Starring change...'
      : 'Unstarring change...';
    fireAlert(this, msg);
    await this.restApiService.saveChangeStarred(
      e.detail.change._number,
      e.detail.starred
    );
    fireEvent(this, 'hide-alert');
    if (e.detail.starred) {
      this.reporting.reportInteraction('change-starred-from-dashboard');
    }
    // When a change is updated the same change may appear elsewhere in the
    // dashboard (but is not the same object), so we must update other
    // occurrences of the same change.
    this.results?.forEach((dashboardChange, dashboardIndex) =>
      dashboardChange.results.forEach((change, changeIndex) => {
        if (change.id === e.detail.change.id) {
          this.results![dashboardIndex].results[changeIndex].starred =
            e.detail.starred;
          this.requestUpdate('results');
        }
      })
    );
  }

  /**
   * Banner is shown if a user is on their own dashboard and they have draft
   * comments on closed changes.
   *
   * private but used in test
   */
  maybeShowDraftsBanner() {
    this.showDraftsBanner = false;
    if (!(this.viewState?.user === 'self')) return;

    if (!this.results) {
      throw new Error('this.results must be set. restAPI returned undefined');
    }

    const draftSection = this.results.find(
      section => section.query === 'has:draft'
    );
    if (!draftSection || !draftSection.results.length) return;

    const closedChanges = draftSection.results.filter(
      change => !changeIsOpen(change)
    );
    if (!closedChanges.length) return;

    this.showDraftsBanner = true;
  }

  // private but used in test
  handleOpenDeleteDialog() {
    assertIsDefined(this.confirmDeleteModal, 'confirmDeleteModal');
    this.confirmDeleteModal.showModal();
  }

  // private but used in test
  handleConfirmDelete() {
    assertIsDefined(this.confirmDeleteDialog, 'confirmDeleteDialog');
    this.confirmDeleteDialog.disabled = true;
    return this.restApiService.deleteDraftComments('-is:open').then(() => {
      this.closeConfirmDeleteModal();
      this.reload();
    });
  }

  private closeConfirmDeleteModal() {
    assertIsDefined(this.confirmDeleteModal, 'confirmDeleteModal');
    this.confirmDeleteModal.close();
  }

  private computeDraftsLink() {
    return createSearchUrl({query: 'has:draft -is:open'});
  }

  private handleCreateChangeTap() {
    assertIsDefined(this.destinationDialog, 'destinationDialog');
    this.destinationDialog.open();
  }

  private handleDestinationConfirm(
    e: CustomEvent<CreateDestinationConfirmDetail>
  ) {
    assertIsDefined(this.commandsDialog, 'commandsDialog');
    this.commandsDialog.branch = e.detail.branch;
    this.commandsDialog.open();
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-dashboard-view': GrDashboardView;
  }
}
