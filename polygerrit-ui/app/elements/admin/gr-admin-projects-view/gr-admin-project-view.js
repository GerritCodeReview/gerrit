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
    is: 'gr-admin-project-view',

    properties: {
      visibleChangeTableColumns: Array,
      labelNames: {
        type: Array,
      },
      change: Object,
      changeURL: {
        type: String,
        computed: '_computeChangeURL(change._number)',
      },
      showStar: {
        type: Boolean,
        value: false,
      },
    },

    behaviors: [
      Gerrit.AdminProjectsBehavior,
      Gerrit.RESTClientBehavior,
      Gerrit.URLEncodingBehavior,
    ],

    _computeProjectURL: function(project) {
      return '/admin/projects/' +
          this.encodeURL(project, false);
    },

    /**
     * Fetch from the API the predicted projects.
     * @param {string} predicate - The first part of the search term, e.g.
     *     'project'
     * @param {string} expression - The second part of the search term, e.g.
     *     'gerr'
     * @return {!Promise} This returns a promise that resolves to an array of
     *     strings.
     */
    _fetchProjects: function() {
      return this.$.restAPI.getSuggestedProjects(
          null,
          null)
          .then(function(projects) {
            if (!projects) { return []; }
            var keys = Object.keys(projects);
            return keys.map(function(key) { return key; });
          });
    },
  });
})();
