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

  // NOTE: These queries are tested in Java. Any changes made to definitions
  // here require corresponding changes to:
  // gerrit-server/src/test/java/com/google/gerrit/server/query/change/AbstractQueryChangesTest.java
  const DEFAULT_SECTIONS = [
    {
      // WIP open changes owned by viewing user. This section is omitted when
      // viewing other users, so we don't need to filter anything out.
      name: 'Work in progress',
      query: 'is:open owner:${user} is:wip',
      selfOnly: true,
    },
    {
      // Non-WIP open changes owned by viewed user. Filter out changes ignored
      // by the viewing user.
      name: 'Outgoing reviews',
      query: 'is:open owner:${user} -is:wip -is:ignored',
    },
    {
      // Non-WIP open changes not owned by the viewed user, that the viewed user
      // is associated with (as either a reviewer or the assignee). Changes
      // ignored by the viewing user are filtered out.
      name: 'Incoming reviews',
      query: 'is:open -owner:${user} -is:wip -is:ignored ' +
          '(reviewer:${user} OR assignee:${user})',
    },
    {
      name: 'Recently closed',
      // Closed changes where viewed user is owner, reviewer, or assignee.
      // Changes ignored by the viewing user are filtered out, and so are WIP
      // changes not owned by the viewing user (the one instance of
      // 'owner:self' is intentional and implements this logic).
      query: 'is:closed -is:ignored (-is:wip OR owner:self) ' +
          '(owner:${user} OR reviewer:${user} OR assignee:${user})',
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

      /** @type {{ user: string }} */
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

    _computeTitle(user) {
      if (user === 'self') {
        return 'My Reviews';
      }
      return 'Dashboard for ' + user;
    },

    _paramsChanged(paramsChangeRecord) {
      const params = paramsChangeRecord.base;

      if (!params.user && !params.sections) {
        return;
      }

      const user = params.user || 'self';
      const sections = (params.sections || DEFAULT_SECTIONS).filter(
          section => (user === 'self' || !section.selfOnly));
      const title = params.title || this._computeTitle(user);

      // NOTE: This method may be called before attachment. Fire title-change
      // in an async so that attachment to the DOM can take place first.
      this.async(() => this.fire('title-change', {title}));

      // Return if params indicate no longer in view.
      if (!user && sections === DEFAULT_SECTIONS) {
        return;
      }

      this._loading = true;
      const queries =
          sections.map(
              section => this._dashboardQueryForSection(section, user));
      this.$.restAPI.getChanges(null, queries, null, this.options)
          .then(results => {
            this._results = sections.map((section, i) => {
              return {
                sectionName: section.name,
                query: queries[i],
                results: results[i],
              };
            });
            this._loading = false;
          }).catch(err => {
            this._loading = false;
            console.warn(err.message);
          });
    },

    _dashboardQueryForSection(section, user) {
      const query =
          section.suffixForDashboard ?
          section.query + ' ' + section.suffixForDashboard :
          section.query;
      return query.replace(/\$\{user\}/g, user);
    },

    _computeUserHeaderClass(userParam) {
      return userParam === 'self' ? 'hide' : '';
    },
  });
})();
