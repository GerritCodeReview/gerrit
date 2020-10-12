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
import '../../../styles/shared-styles';
import '../gr-change-list/gr-change-list';
import '../../shared/gr-button/gr-button';
import '../../shared/gr-dialog/gr-dialog';
import '../../shared/gr-overlay/gr-overlay';
import '../../shared/gr-rest-api-interface/gr-rest-api-interface';
import '../gr-create-commands-dialog/gr-create-commands-dialog';
import '../gr-create-change-help/gr-create-change-help';
import '../gr-create-destination-dialog/gr-create-destination-dialog';
import '../gr-user-header/gr-user-header';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-dashboard-view_html';
import {
  GerritNav,
  GerritView,
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
import {RestApiService} from '../../../services/services/gr-rest-api/gr-rest-api';
import {GrDialog} from '../../shared/gr-dialog/gr-dialog';
import {GrCreateCommandsDialog} from '../gr-create-commands-dialog/gr-create-commands-dialog';
import {
  CreateDestinationConfirmDetail,
  GrCreateDestinationDialog,
} from '../gr-create-destination-dialog/gr-create-destination-dialog';
import {GrOverlay} from '../../shared/gr-overlay/gr-overlay';
import {ChangeListToggleReviewedDetail} from '../gr-change-list-item/gr-change-list-item';
import {ChangeStarToggleStarDetail} from '../../shared/gr-change-star/gr-change-star';

const PROJECT_PLACEHOLDER_PATTERN = /\$\{project\}/g;

export interface DashboardViewState {
  selectedChangeIndex: number;
}

export interface GrDashboardView {
  $: {
    restAPI: RestApiService & Element;
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
export class GrDashboardView extends GestureEventListeners(
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

  private reporting = appContext.reportingService;

  constructor() {
    super();
  }

  /** @override */
  attached() {
    super.attached();
    this._loadPreferences();
    this.addEventListener('reload', e => {
      e.stopPropagation();
      this._reload();
    });
  }

  _loadPreferences() {
    return this.$.restAPI.getLoggedIn().then(loggedIn => {
      if (loggedIn) {
        this.$.restAPI.getPreferences().then(preferences => {
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
      this.dispatchEvent(
        new CustomEvent('page-error', {
          detail: {response},
          composed: true,
          bubbles: true,
        })
      );
    };
    return this.$.restAPI
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

  @observe('params.*')
  _paramsChanged(
    paramsChangeRecord: ElementPropertyDeepChange<GrDashboardView, 'params'>
  ) {
    const params = paramsChangeRecord.base;

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
      : this.$.restAPI
          .getConfig()
          .then(config =>
            Promise.resolve(
              GerritNav.getUserDashboard(
                user,
                sections,
                title || this._computeTitle(user),
                config
              )
            )
          );

    const checkForNewUser = !project && user === 'self';
    return dashboardPromise
      .then(res => {
        if (res && res.title) {
          this.dispatchEvent(
            new CustomEvent('title-change', {
              detail: {title: res.title},
              composed: true,
              bubbles: true,
            })
          );
        }
        return this._fetchDashboardChanges(res, checkForNewUser);
      })
      .then(() => {
        this._maybeShowDraftsBanner(params);
        this.reporting.dashboardDisplayed();
      })
      .catch(err => {
        this.dispatchEvent(
          new CustomEvent('title-change', {
            detail: {
              title: title || this._computeTitle(user),
            },
            composed: true,
            bubbles: true,
          })
        );
        console.warn(err);
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

    return this.$.restAPI.getChanges(undefined, queries).then(changes => {
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
    this.$.restAPI.saveChangeStarred(e.detail.change._number, e.detail.starred);
  }

  _handleToggleReviewed(e: CustomEvent<ChangeListToggleReviewedDetail>) {
    this.$.restAPI.saveChangeReviewed(
      e.detail.change._number,
      e.detail.reviewed
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
    return this.$.restAPI.deleteDraftComments('-is:open').then(() => {
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
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-dashboard-view': GrDashboardView;
  }
}
