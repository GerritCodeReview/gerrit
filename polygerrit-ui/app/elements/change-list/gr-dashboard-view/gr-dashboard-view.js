// Copyright (C) 2016 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
(function() {
  'use strict';

  const PROJECT_PLACEHOLDER_PATTERN = /\$\{project\}/g;
  const USER_PLACEHOLDER_PATTERN = /\$\{user\}/g;

  const DEFAULT_SECTIONS = [
    {
      name: 'Work in progress',
      query: 'is:open owner:${user} is:wip',
      selfOnly: true,
    },
    {
      name: 'Outgoing reviews',
      query: 'is:open owner:${user} -is:wip',
    },
    {
      name: 'Incoming reviews',
      query: 'is:open ((reviewer:${user} -owner:${user} -is:ignored) OR ' +
          'assignee:${user}) -is:wip',
    },
    {
      name: 'Recently closed',
      query: 'is:closed (owner:${user} OR reviewer:${user} OR ' +
          'assignee:${user})',
      suffixForDashboard: '-age:4w limit:10',
    },
  ];

  Polymer({
    is: 'gr-dashboard-view',

    /**
     * Fired when the title of the page should change.
     *
     * @event title-change
     */

    properties: {
      account: {
        type: Object,
        value() { return {}; },
      },
      /** @type {{ selectedChangeIndex: number }} */
      viewState: Object,
      params: {
        type: Object,
      },

      _results: Array,
      _sectionMetadata: {
        type: Array,
        value() { return DEFAULT_SECTIONS; },
      },

      /**
       * For showing a "loading..." string during ajax requests.
       */
      _loading: {
        type: Boolean,
        value: true,
      },
    },

    observers: [
      '_paramsChanged(params.*)',
    ],

    behaviors: [
      Gerrit.RESTClientBehavior,
    ],

    get options() {
      return this.listChangesOptionsToHex(
          this.ListChangesOption.LABELS,
          this.ListChangesOption.DETAILED_ACCOUNTS,
          this.ListChangesOption.REVIEWED
      );
    },

    _interpolateQuery(query, pattern, replacement, suffix) {
      return query.replace(pattern, replacement) + suffix;
    },

    _getProjectDashboard(project, dashboard) {
      return this.$.restAPI.getDashboard(project, dashboard).then(response => {
        return {
          title: response.title,
          sections: response.sections.map(section => {
            const suffix = response.foreach ? ' ' + response.foreach : '';
            return {
              name: section.name,
              query:
                  this._interpolateQuery(
                      section.query, PROJECT_PLACEHOLDER_PATTERN, project,
                      suffix),
            };
          }),
        };
      });
    },

    _getUserDashboard(user, sections, title) {
      sections = sections
        .filter(section => (user === 'self' || !section.selfOnly))
        .map(section => {
          const dashboardSection = {
            name: section.name,
            query:
                this._interpolateQuery(
                    section.query, USER_PLACEHOLDER_PATTERN, user, ''),
          };
          if (section.suffixForDashboard) {
            dashboardSection.suffixForDashboard = section.suffixForDashboard;
          }
          return dashboardSection;
        });
      return Promise.resolve({title, sections});
    },

    _computeTitle(user) {
      if (!user || user === 'self') {
        return 'My Reviews';
      }
      return 'Dashboard for ' + user;
    },

    _isViewActive(params) {
      return params.view === Gerrit.Nav.View.DASHBOARD;
    },

    _paramsChanged(paramsChangeRecord) {
      const params = paramsChangeRecord.base;

      if (!this._isViewActive(params)) {
        return Promise.resolve();
      }

      const user = params.user || 'self';

      // NOTE: This method may be called before attachment. Fire title-change
      // in an async so that attachment to the DOM can take place first.
      const title = params.title || this._computeTitle(user);
      this.async(() => this.fire('title-change', {title}));

      this._loading = true;

      const dashboardPromise = params.project ?
          this._getProjectDashboard(params.project, params.dashboard) :
          this._getUserDashboard(
              params.user || 'self',
              params.sections || DEFAULT_SECTIONS,
              params.title || this._computeTitle(params.user));

      return dashboardPromise.then(dashboard => {
        const queries = dashboard.sections.map(section => {
          if (section.suffixForDashboard) {
            return section.query + ' ' + section.suffixForDashboard;
          }
          return section.query;
        });
        const req =
            this.$.restAPI.getChanges(null, queries, null, this.options);
        return req.then(response => {
          this._loading = false;
          this._results = response.map((results, i) => {
            return {
              sectionName: dashboard.sections[i].name,
              query: dashboard.sections[i].query,
              results,
            };
          });
        });
      }).catch(err => {
        this._loading = false;
        console.warn(err);
      });
    },
  });
})();
