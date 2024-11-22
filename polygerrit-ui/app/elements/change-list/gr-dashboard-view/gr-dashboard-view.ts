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
import '../../core/gr-notifications-prompt/gr-notifications-prompt';
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
  fire,
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
  DashboardType,
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
  @query('#confirmDeleteDialog') protected confirmDeleteDialog?: GrDialog;

  @query('#commandsDialog') protected commandsDialog?: GrCreateCommandsDialog;

  @query('#destinationDialog')
  protected destinationDialog?: GrCreateDestinationDialog;

  @query('#confirmDeleteModal')
  protected confirmDeleteModal?: HTMLDialogElement;

  @property({type: Object})
  loggedInUser?: AccountDetailInfo;

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

  // private but used in test
  @state() showNotificationsPrompt = false;

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
      x => (this.loggedInUser = x)
    );
    subscribe(
      this,
      () => this.getUserModel().preferences$,
      prefs => {
        this.preferences = prefs ?? {};
      }
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
        @confirm-destination=${(
          e: CustomEvent<CreateDestinationConfirmDetail>
        ) => {
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
          .loggedInUser=${this.loggedInUser}
          .dashboardUser=${this.viewState?.user}
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
        ${this.renderShowNotificationsPrompt()}
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

  private renderShowNotificationsPrompt() {
    if (!this.showNotificationsPrompt) return;

    return html`<gr-notifications-prompt></gr-notifications-prompt>`;
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
  async reload() {
    if (!this.viewState) return;

    // See `firstTimeLoad` comment above.
    if (!this.firstTimeLoad) {
      this.reporting.time(Timing.DASHBOARD_DISPLAYED);
    }
    this.firstTimeLoad = false;

    this.loading = true;
    const {project, type, dashboard, title, user, sections} = this.viewState;

    const dashboardPromise: Promise<UserDashboard | undefined> = project
      ? this.getRepositoryDashboard(project, dashboard)
      : Promise.resolve(
          getUserDashboard(user, sections, title || this.computeTitle(user))
        );
    // Checking `this.loggedInUser` to make sure that the user is logged in.
    // Otherwise sending a query for 'owner:self' will result in an error.
    const isLoggedInUserDashboard =
      !project && !!this.loggedInUser && user === 'self';
    try {
      const res = await dashboardPromise;
      if (res && res.title) {
        fireTitleChange(res.title);
      }
      await this.fetchDashboardChanges(res, isLoggedInUserDashboard);
      this.maybeShowDraftsBanner();
      // Only report the metric for the default personal dashboard.
      if (type === DashboardType.USER && isLoggedInUserDashboard) {
        this.reporting.dashboardDisplayed();
      }
    } catch (err) {
      fireTitleChange(title || this.computeTitle(user));
      this.reporting.error('Dashboard reload', err as Error);
    } finally {
      this.loading = false;
    }
  }

  /**
   * Fetches the changes for each dashboard section and sets this.results
   * with the response.
   *
   * private but used in test
   */
  async fetchDashboardChanges(
    res: UserDashboard | undefined,
    isLoggedInUserDashboard: boolean
  ) {
    if (!res) return;
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

      if (isLoggedInUserDashboard) {
        // The query to check if the user created any changes yet.
        queries.push('owner:self limit:1');
      }
    }

    const changes = await this.restApiService.getChangesForDashboard(
      undefined,
      queries
    );
    if (!changes) {
      throw new Error('getChanges returns undefined');
    }
    if (isLoggedInUserDashboard) {
      // Last query ('owner:self limit:1') is only for evaluation if
      // the user is "New" ie. haven't created any changes yet.
      const lastResultSet = changes.pop();
      this.showNewUserHelp = lastResultSet!.length === 0;
      // Show the notifications prompt if the user has created any changes
      // (meaning they are not a "New" user).
      this.showNotificationsPrompt = !this.showNewUserHelp;
    }
    this.results = changes
      .map((results, i) => {
        return {
          name: res.sections[i].name,
          countLabel: this.computeSectionCountLabel(results),
          query: res.sections[i].query,
          results: this.maybeSortResults(res.sections[i].name, results),
          emptyStateSlotName: slotNameBySectionName.get(res.sections[i].name),
        };
      })
      .filter(
        (section, i) =>
          i < res.sections.length &&
          (!res.sections[i].hideIfEmpty || section.results.length)
      );

    // Show the notifications prompt if the user has any results in their attention set.
    this.showNotificationsPrompt =
      this.showNotificationsPrompt ||
      this.results.filter(
        changelistSection =>
          changelistSection.name === YOUR_TURN.name &&
          changelistSection.results.length > 0
      ).length !== 0;
  }

  /**
   * Usually we really want to stick to the sorting that the backend provides,
   * but for the "Your turn" section it is important to put the changes at the
   * top where the current user is a reviewer. Owned changes are less important.
   * And then we want to emphasize the changes where the waiting time is larger.
   */
  private maybeSortResults(name: string, results: ChangeInfo[]) {
    // TODO: viewState?.user can be an Email Address. In this case the
    // attention_set lookups will return undefined.
    const userId =
      this.viewState?.user === 'self'
        ? this.loggedInUser?._account_id
        : this.viewState?.user;
    const sortedResults = [...results];
    if (name === YOUR_TURN.name && userId) {
      sortedResults.sort((c1, c2) => {
        const c1Owner = c1.owner._account_id === userId;
        const c2Owner = c2.owner._account_id === userId;
        if (c1Owner !== c2Owner) return c1Owner ? 1 : -1;
        // Should never happen, because the change is in the 'Your turn'
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
    fire(this, 'hide-alert', {});
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
