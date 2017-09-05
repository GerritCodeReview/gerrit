// Copyright (C) 2017 The Android Open Source Project
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
      query: 'is:open owner:self is:wip',
    },
    {
      name: 'Outgoing reviews',
      query: 'is:open owner:self -is:wip',
    },
    {
      name: 'Incoming reviews',
      query: 'is:open ((reviewer:self -owner:self -is:ignored) OR ' +
          'assignee:self) -is:wip',
    },
    {
      name: 'Recently closed',
      query: 'is:closed (owner:self OR reviewer:self OR assignee:self)',
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
        observer: '_paramsChanged',
      },

      _results: Array,
      sectionMetadata: Object,

      /**
       * For showing a "loading..." string during ajax requests.
       */
      _loading: {
        type: Boolean,
        value: true,
      },
      projectName: String,
    },

    behaviors: [
      Gerrit.RESTClientBehavior,
    ],

    attached() {
      this.fire('title-change', {title: 'My Reviews'});
    },

    get options() {
      return this.listChangesOptionsToHex(
          this.ListChangesOption.LABELS,
          this.ListChangesOption.DETAILED_ACCOUNTS,
          this.ListChangesOption.REVIEWED
      );
    },

    /**
     * Allows a refresh if menu item is selected again.
     */
    _paramsChanged() {
      this._loading = true;
      this._getDashboard().then(results => {
        this._results = results;
        this._loading = false;
      }).catch(err => {
        this._loading = false;
        console.warn(err.message);
      });
    },

    _getDashboard() {
      if (!this.projectName) { return; }
      return this.$.restAPI.getDashboards(this.projectName);
    },

    _dashboardQueryForSection(section) {
      if (section.suffixForDashboard) {
        return section.query + ' ' + section.suffixForDashboard;
      }
      return section.query;
    },
  });
})();
