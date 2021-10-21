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
import '../../../styles/gr-a11y-styles';
import '../../../styles/shared-styles';
import '../gr-change-list/gr-change-list';
import '../../shared/gr-button/gr-button';
import '../../shared/gr-dialog/gr-dialog';
import '../../shared/gr-overlay/gr-overlay';
import '../gr-create-commands-dialog/gr-create-commands-dialog';
import '../gr-create-change-help/gr-create-change-help';
import '../gr-create-destination-dialog/gr-create-destination-dialog';
import '../gr-user-header/gr-user-header';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-dashboard-view_html';
import {
  GerritNav,
  UserDashboard,
  YOUR_TURN,
} from '../../core/gr-navigation/gr-navigation';
import {appContext} from '../../../services/app-context';
import {changeIsOpen} from '../../../utils/change-util';
import {parseDate} from '../../../utils/date-util';
import {customElement, observe, property} from '@polymer/decorators';
import {
  AccountDetailInfo,
  ChangeInfo,
  DashboardId,
  ElementPropertyDeepChange,
  PreferencesInput,
  RepoName,
} from '../../../types/common';
import {AppElementDashboardParams, AppElementParams} from '../../gr-app-types';
import {GrDialog} from '../../shared/gr-dialog/gr-dialog';
import {GrCreateCommandsDialog} from '../gr-create-commands-dialog/gr-create-commands-dialog';
import {
  CreateDestinationConfirmDetail,
  GrCreateDestinationDialog,
} from '../gr-create-destination-dialog/gr-create-destination-dialog';
import {GrOverlay} from '../../shared/gr-overlay/gr-overlay';
import {ChangeStarToggleStarDetail} from '../../shared/gr-change-star/gr-change-star';
import {DashboardViewState} from '../../../types/types';
import {firePageError, fireTitleChange} from '../../../utils/event-util';
import {GerritView} from '../../../services/router/router-model';
import {RELOAD_DASHBOARD_INTERVAL_MS} from '../../../constants/constants';

const PROJECT_PLACEHOLDER_PATTERN = /\${project}/g;

export interface GrDashboardView {
  $: {
    confirmDeleteDialog: GrDialog;
    commandsDialog: GrCreateCommandsDialog;
    destinationDialog: GrCreateDestinationDialog;
    confirmDeleteOverlay: GrOverlay;
  };
}

interface DashboardChange {
  name: string;
  countLabel: string;
  query: string;
  results: ChangeInfo[];
  isOutgoing?: boolean;
}

@customElement('gr-dashboard-view')
export class GrDashboardView extends PolymerElement {
  static get template() {
    return htmlTemplate;
  }

  /**
   * Fired when the title of the page should change.
   *
   * @event title-change
   */

  @property({type: Object})
  account: AccountDetailInfo | null = null;

  @property({type: Object})
  preferences?: PreferencesInput;

  @property({type: Object})
  viewState?: DashboardViewState;

  @property({type: Object})
  params?: AppElementParams;

  @property({type: Array})
  _results?: DashboardChange[];

  @property({type: Boolean})
  _loading = true;

  @property({type: Boolean})
  _showDraftsBanner = false;

  @property({type: Boolean})
  _showNewUserHelp = false;

  @property({type: Number})
  _selectedChangeIndex?: number;

  private reporting = appContext.reportingService;

  private readonly restApiService = appContext.restApiService;

  private lastVisibleTimestampMs = 0;

  constructor() {
    super();
    this.addEventListener('reload', () => this._reload(this.params));
    // We are not currently verifying if the view is actually visible. We rely
    // on gr-app-element to restamp the component if view changes
    document.addEventListener('visibilitychange', () => {
      if (document.visibilityState === 'visible') {
        if (
          Date.now() - this.lastVisibleTimestampMs >
          RELOAD_DASHBOARD_INTERVAL_MS
        )
          this._reload(this.params);
      } else {
        this.lastVisibleTimestampMs = Date.now();
      }
    });
  }

  override connectedCallback() {
    super.connectedCallback();
    this._loadPreferences();
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

  _getProjectDashboard(
    project: RepoName,
    dashboard: DashboardId
  ): Promise<UserDashboard | undefined> {
    const errFn = (response?: Response | null) => {
      firePageError(response);
    };
    return this.restApiService
      .getDashboard(project, dashboard, errFn)
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
                project
              ),
            };
          }),
        };
      });
  }

  _computeTitle(user?: string) {
    if (!user || user === 'self') {
      return 'My Reviews';
    }
    return 'Dashboard for ' + user;
  }

  _isViewActive(params: AppElementParams): params is AppElementDashboardParams {
    return params.view === GerritView.DASHBOARD;
  }

  @observe('_selectedChangeIndex')
  _selectedChangeIndexChanged(selectedChangeIndex: number) {
    if (!this.params || !this._isViewActive(this.params)) return;
    if (!this.viewState) throw new Error('view state undefined');
    if (!this.params.user) throw new Error('user for dashboard is undefined');
    this.viewState[this.params.user] = selectedChangeIndex;
  }

  @observe('params.*')
  _paramsChanged(
    paramsChangeRecord: ElementPropertyDeepChange<GrDashboardView, 'params'>
  ) {
    const params = paramsChangeRecord.base;
    if (params && this._isViewActive(params) && params.user && this.viewState)
      this._selectedChangeIndex = this.viewState[params.user] || 0;
    return this._reload(params);
  }

  /**
   * Reloads the element.
   */
  _reload(params?: AppElementParams) {
    if (!params || !this._isViewActive(params)) {
      return Promise.resolve();
    }
    this._loading = true;
    const {project, dashboard, title, user, sections} = params;
    const dashboardPromise: Promise<UserDashboard | undefined> = project
      ? this._getProjectDashboard(project, dashboard)
      : Promise.resolve(
          GerritNav.getUserDashboard(
            user,
            sections,
            title || this._computeTitle(user)
          )
        );
    // Checking `this.account` to make sure that the user is logged in.
    // Otherwise sending a query for 'owner:self' will result in an error.
    const checkForNewUser = !project && !!this.account && user === 'self';
    return dashboardPromise
      .then(res => {
        if (res && res.title) {
          fireTitleChange(this, res.title);
        }
        return this._fetchDashboardChanges(res, checkForNewUser);
      })
      .then(() => {
        this._maybeShowDraftsBanner(params);
        this.reporting.dashboardDisplayed();
      })
      .catch(err => {
        fireTitleChange(this, title || this._computeTitle(user));
        this.reporting.error(err);
      })
      .then(() => {
        this._loading = false;
      });
  }

  /**
   * Fetches the changes for each dashboard section and sets this._results
   * with the response.
   */
  _fetchDashboardChanges(
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

    return this.restApiService.getChanges(undefined, queries).then(changes => {
      if (!changes) {
        throw new Error('getChanges returns undefined');
      }
      if (checkForNewUser) {
        // Last set of results is not meant for dashboard display.
        const lastResultSet = changes.pop();
        this._showNewUserHelp = lastResultSet!.length === 0;
      }
      this._results = changes
        .map((results, i) => {
          return {
            name: res.sections[i].name,
            countLabel: this._computeSectionCountLabel(results),
            query: res.sections[i].query,
            results: this._maybeSortResults(res.sections[i].name, results),
            isOutgoing: res.sections[i].isOutgoing,
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
  _maybeSortResults(name: string, results: ChangeInfo[]) {
    const userId = this.account && this.account._account_id;
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

  _computeSectionCountLabel(changes: ChangeInfo[]) {
    if (!changes || !changes.length || changes.length === 0) {
      return '';
    }
    const more = changes[changes.length - 1]._more_changes;
    const numChanges = changes.length;
    const andMore = more ? ' and more' : '';
    return `(${numChanges}${andMore})`;
  }

  _computeUserHeaderClass(params: AppElementParams) {
    if (
      !params ||
      params.view !== GerritView.DASHBOARD ||
      !!params.project ||
      !params.user ||
      params.user === 'self'
    ) {
      return 'hide';
    }
    return '';
  }

  _handleToggleStar(e: CustomEvent<ChangeStarToggleStarDetail>) {
    this.restApiService.saveChangeStarred(
      e.detail.change._number,
      e.detail.starred
    );
    if (e.detail.starred) {
      this.reporting.reportInteraction('change-starred-from-dashboard');
    }
    // When a change is updated the same change may appear elsewhere in the
    // dashboard (but is not the same object), so we must update other
    // occurrences of the same change.
    this._results?.forEach((dashboardChange, dashboardIndex) =>
      dashboardChange.results.forEach((change, changeIndex) => {
        if (change.id === e.detail.change.id) {
          this.set(
            `_results.${dashboardIndex}.results.${changeIndex}.starred`,
            e.detail.starred
          );
        }
      })
    );
  }

  /**
   * Banner is shown if a user is on their own dashboard and they have draft
   * comments on closed changes.
   */
  _maybeShowDraftsBanner(params: AppElementDashboardParams) {
    this._showDraftsBanner = false;
    if (!(params.user === 'self')) {
      return;
    }

    if (!this._results) {
      throw new Error('this._results must be set. restAPI returned undefined');
    }

    const draftSection = this._results.find(
      section => section.query === 'has:draft'
    );
    if (!draftSection || !draftSection.results.length) {
      return;
    }

    const closedChanges = draftSection.results.filter(
      change => !changeIsOpen(change)
    );
    if (!closedChanges.length) {
      return;
    }

    this._showDraftsBanner = true;
  }

  _computeBannerClass(show: boolean) {
    return show ? '' : 'hide';
  }

  _handleOpenDeleteDialog() {
    this.$.confirmDeleteOverlay.open();
  }

  _handleConfirmDelete() {
    this.$.confirmDeleteDialog.disabled = true;
    return this.restApiService.deleteDraftComments('-is:open').then(() => {
      this._closeConfirmDeleteOverlay();
      this._reload(this.params);
    });
  }

  _closeConfirmDeleteOverlay() {
    this.$.confirmDeleteOverlay.close();
  }

  _computeDraftsLink() {
    return GerritNav.getUrlForSearchQuery('has:draft -is:open');
  }

  _handleCreateChangeTap() {
    this.$.destinationDialog.open();
  }

  _handleDestinationConfirm(e: CustomEvent<CreateDestinationConfirmDetail>) {
    this.$.commandsDialog.branch = e.detail.branch;
    this.$.commandsDialog.open();
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
    'gr-dashboard-view': GrDashboardView;
  }
}
