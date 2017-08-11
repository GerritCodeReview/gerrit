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

    attached() {
      this.fire('title-change', {title: 'My Reviews'});
    },

    /**
     * Allows a refresh if menu item is selected again.
     */
    _userChanged(user) {
      if (!user) { return; }
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
