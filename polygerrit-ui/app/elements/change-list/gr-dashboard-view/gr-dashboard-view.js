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
      // Non-WIP open changes owned by viewed user. Only filter out ignored
      // changes if viewing a user other than oneself (the one instance of
      // 'owner:self' is intended).
      name: 'Outgoing reviews',
      query: 'is:open owner:${user} -is:wip (owner:self OR -is:ignored)',
    },
    {
      // Non-WIP open changes not owned by the viewed user, that the viewed user
      // is associated with (as either a reviewer or the assignee). Changes
      // ignored by the viewing user are filtered out.
      name: 'Incoming reviews',
      query: 'is:open (reviewer:${user} OR assignee:${user}) -owner:${user} ' +
          '-is:ignored -is:wip',
    },
    {
      name: 'Recently closed',
      // Closed changes where viewed user is owner, reviewer, or assignee.
      // WIP changes and changes ignored by the viewing user are filtered out,
      // unless owned by the viewing user (the one instance of 'owner:self' is
      // intended).
      query: 'is:closed ((owner:${user} (owner:self OR ' +
          '(-is:ignored -is:wip))) OR ((reviewer:${user} OR assignee:${user}) ' +
          '-is:ignored -is:wip))',
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
      '_userChanged(params.user)',
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

    /**
     * Allows a refresh if menu item is selected again.
     */
    _userChanged(user) {
      if (!user) { return; }

      // NOTE: This method may be called before attachment. Fire title-change
      // in an async so that attachment to the DOM can take place first.
      this.async(
          () => this.fire('title-change', {title: this._computeTitle(user)}));

      this._loading = true;
      const sections = this._sectionMetadata.filter(
          section => (user === 'self' || !section.selfOnly));
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

  });
})();
