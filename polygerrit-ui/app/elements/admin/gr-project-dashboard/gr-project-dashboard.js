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

  Polymer({
    is: 'gr-project-dashboard',

    /**
     * Fired when the title of the page should change.
     *
     * @event title-change
     */

    properties: {
      /** @type {?} */
      _account: Object,
      /** @type {{ selectedChangeIndex: number }} */
      viewState: Object,
      params: {
        type: Object,
      },

      _results: Array,

      /**
       * For showing a "loading..." string during ajax requests.
       */
      _loading: {
        type: Boolean,
        value: true,
      },
    },

    attached() {
      this._loadData();

      this.fire('title-change', {title: 'Project Dashboard'});
    },

    observers: [
      '_paramsChanged(params.project, params.ref, params.path)',
    ],

    behaviors: [
      Gerrit.RESTClientBehavior,
    ],

    _loadData() {
      this.viewState = {
        dashboardView: {
          selectedChangeIndex: 0,
        },
      };
      return this.$.restAPI.getAccount().then(account => {
        this._account = account;
      });
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
    _paramsChanged(project, ref, path) {
      if (!project || !ref || !path) { return; }

      this._loading = true;
      return this.$.restAPI.getProjectDashboard(project, ref, path)
          .then(results => {
            const sections = results.sections.filter(
                section => (!section.selfOnly));
            const queries =
                sections.map(
                    section => section.query);
            return this.$.restAPI.getChanges(null, queries, null, this.options)
                .then(response => {
                  this._results = sections.map((section, i) => {
                    return {
                      sectionName: section.name,
                      query: queries[i],
                      results: response[i],
                    };
                  });
                  this._loading = false;
                }).catch(err => {
                  this._loading = false;
                  console.warn(err.message);
                });
          }).catch(err => {
            this._loading = false;
            console.warn(err.message);
          });
    },
  });
})();
