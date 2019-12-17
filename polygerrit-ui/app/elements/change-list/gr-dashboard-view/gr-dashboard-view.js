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

  const PROJECT_PLACEHOLDER_PATTERN = /\$\{project\}/g;

  /**
   * @appliesMixin Gerrit.FireMixin
   * @appliesMixin Gerrit.RESTClientMixin
   */
  class GrDashboardView extends Polymer.mixinBehaviors( [
    Gerrit.FireBehavior,
    Gerrit.RESTClientBehavior,
  ], Polymer.GestureEventListeners(
      Polymer.LegacyElementMixin(
          Polymer.Element))) {
    static get is() { return 'gr-dashboard-view'; }
    /**
     * Fired when the title of the page should change.
     *
     * @event title-change
     */

    static get properties() {
      return {
        account: {
          type: Object,
          value: null,
        },
        preferences: Object,
        /** @type {{ selectedChangeIndex: number }} */
        viewState: Object,

        /** @type {{ project: string, user: string }} */
        params: {
          type: Object,
        },

        createChangeTap: {
          type: Function,
          value() {
            return this._createChangeTap.bind(this);
          },
        },

        _results: Array,

        /**
         * For showing a "loading..." string during ajax requests.
         */
        _loading: {
          type: Boolean,
          value: true,
        },

        _showDraftsBanner: {
          type: Boolean,
          value: false,
        },

        _showNewUserHelp: {
          type: Boolean,
          value: false,
        },
      };
    }

    static get observers() {
      return [
        '_paramsChanged(params.*)',
      ];
    }

    get options() {
      return this.listChangesOptionsToHex(
          this.ListChangesOption.LABELS,
          this.ListChangesOption.DETAILED_ACCOUNTS,
          this.ListChangesOption.REVIEWED
      );
    }

    attached() {
      super.attached();
      this._loadPreferences();
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

    _getProjectDashboard(project, dashboard) {
      const errFn = response => {
        this.fire('page-error', {response});
      };
      return this.$.restAPI.getDashboard(
          project, dashboard, errFn).then(response => {
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
                  PROJECT_PLACEHOLDER_PATTERN, project),
            };
          }),
        };
      });
    }

    _computeTitle(user) {
      if (!user || user === 'self') {
        return 'My Reviews';
      }
      return 'Dashboard for ' + user;
    }

    _isViewActive(params) {
      return params.view === Gerrit.Nav.View.DASHBOARD;
    }

    _paramsChanged(paramsChangeRecord) {
      const params = paramsChangeRecord.base;

      if (!this._isViewActive(params)) {
        return Promise.resolve();
      }

      return this._reload();
    }

    /**
     * Reloads the element.
     *
     * @return {Promise<!Object>}
     */
    _reload() {
      this._loading = true;
      const {project, dashboard, title, user, sections} = this.params;
      const dashboardPromise = project ?
        this._getProjectDashboard(project, dashboard) :
        Promise.resolve(Gerrit.Nav.getUserDashboard(
            user,
            sections,
            title || this._computeTitle(user)));

      const checkForNewUser = !project && user === 'self';
      return dashboardPromise
          .then(res => {
            if (res && res.title) {
              this.fire('title-change', {title: res.title});
            }
            return this._fetchDashboardChanges(res, checkForNewUser);
          })
          .then(() => {
            this._maybeShowDraftsBanner();
            this.$.reporting.dashboardDisplayed();
          }).catch(err => {
            this.fire('title-change', {
              title: title || this._computeTitle(user),
            });
            console.warn(err);
          }).then(() => { this._loading = false; });
    }

    /**
     * Fetches the changes for each dashboard section and sets this._results
     * with the response.
     *
     * @param {!Object} res
     * @param {boolean} checkForNewUser
     * @return {Promise}
     */
    _fetchDashboardChanges(res, checkForNewUser) {
      if (!res) { return Promise.resolve(); }

      const queries = res.sections
          .map(section => section.suffixForDashboard ?
            section.query + ' ' + section.suffixForDashboard :
            section.query);

      if (checkForNewUser) {
        queries.push('owner:self limit:1');
      }

      return this.$.restAPI.getChanges(null, queries, null, this.options)
          .then(changes => {
            if (checkForNewUser) {
              // Last set of results is not meant for dashboard display.
              const lastResultSet = changes.pop();
              this._showNewUserHelp = lastResultSet.length == 0;
            }
            this._results = changes.map((results, i) => ({
              name: res.sections[i].name,
              countLabel: this._computeSectionCountLabel(results),
              query: res.sections[i].query,
              results,
              isOutgoing: res.sections[i].isOutgoing,
            })).filter((section, i) => i < res.sections.length && (
              !res.sections[i].hideIfEmpty ||
                section.results.length));
          });
    }

    _computeSectionCountLabel(changes) {
      if (!changes || !changes.length || changes.length == 0) {
        return '';
      }
      const more = changes[changes.length - 1]._more_changes;
      const numChanges = changes.length;
      const andMore = more ? ' and more' : '';
      return `(${numChanges}${andMore})`;
    }

    _computeUserHeaderClass(params) {
      if (!params || !!params.project || !params.user ||
          params.user === 'self') {
        return 'hide';
      }
      return '';
    }

    _handleToggleStar(e) {
      this.$.restAPI.saveChangeStarred(e.detail.change._number,
          e.detail.starred);
    }

    _handleToggleReviewed(e) {
      this.$.restAPI.saveChangeReviewed(e.detail.change._number,
          e.detail.reviewed);
    }

    /**
     * Banner is shown if a user is on their own dashboard and they have draft
     * comments on closed changes.
     */
    _maybeShowDraftsBanner() {
      this._showDraftsBanner = false;
      if (!(this.params.user === 'self')) { return; }

      const draftSection = this._results
          .find(section => section.query === 'has:draft');
      if (!draftSection || !draftSection.results.length) { return; }

      const closedChanges = draftSection.results
          .filter(change => !this.changeIsOpen(change));
      if (!closedChanges.length) { return; }

      this._showDraftsBanner = true;
    }

    _computeBannerClass(show) {
      return show ? '' : 'hide';
    }

    _handleOpenDeleteDialog() {
      this.$.confirmDeleteOverlay.open();
    }

    _handleConfirmDelete() {
      this.$.confirmDeleteDialog.disabled = true;
      return this.$.restAPI.deleteDraftComments('-is:open').then(() => {
        this._closeConfirmDeleteOverlay();
        this._reload();
      });
    }

    _closeConfirmDeleteOverlay() {
      this.$.confirmDeleteOverlay.close();
    }

    _computeDraftsLink() {
      return Gerrit.Nav.getUrlForSearchQuery('has:draft -is:open');
    }

    _createChangeTap(e) {
      this.$.destinationDialog.open();
    }

    _handleDestinationConfirm(e) {
      this.$.commandsDialog.branch = e.detail.branch;
      this.$.commandsDialog.open();
    }
  }

  customElements.define(GrDashboardView.is, GrDashboardView);
})();
