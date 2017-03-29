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
        value: function() { return {}; },
      },
      viewState: Object,
      params: {
        type: Object,
        observer: '_paramsChanged',
      },

      _results: Array,
      _groupTitles: {
        type: Array,
        value: [
          'Outgoing reviews',
          'Incoming reviews',
          'Recently closed',
        ],
      },

      /**
       * For showing a "loading..." string during ajax requests.
       */
      _loading: {
        type: Boolean,
        value: true,
      },
    },

    attached: function() {
      this.fire('title-change', {title: 'My Reviews'});
    },

    /**
     * Allows a refresh if menu item is selected again.
     */
    _paramsChanged: function() {
      this._loading = true;
      this._getDashboardChanges().then(function(results) {
        this._results = results;
        this._loading = false;
      }.bind(this)).catch(function(err) {
        this._loading = false;
        console.warn(err.message);
      }.bind(this));
    },

    _getDashboardChanges: function() {
      return this.$.restAPI.getDashboardChanges();
    },
  });
})();
