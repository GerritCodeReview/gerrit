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
    is: 'gr-project-branches',

    properties: {
      /**
       * URL params passed from the router.
       */
      params: {
        type: Object,
        observer: '_paramsChanged',
      },

      /**
       * Offset of currently visible query results.
       */
      _offset: Number,
      _project: Object,
      _branches: Array,
      /**
       * Because  we request one more than the projectsPerPage, _shownProjects
       * maybe one less than _projects.
       * */
      _shownBranches: {
        type: Array,
        computed: 'computeShownItems(_branches)',
      },
      _branchesPerPage: {
        type: Number,
        value: 25,
      },
      _loading: {
        type: Boolean,
        value: true,
      },
      _filter: String,
    },

    behaviors: [
      Gerrit.ListViewBehavior,
      Gerrit.URLEncodingBehavior,
    ],

    _paramsChanged(params) {
      this._loading = true;
      if (!params || !params.project) { return; }

      this._project = params.project;

      this._filter = this.getFilterValue(params);
      this._offset = this.getOffsetValue(params);

      return this._getBranches(this._filter, this._project,
          this._branchesPerPage, this._offset);
    },

    _getBranches(filter, project, projectsPerPage, offset) {
      this._projectsBranches = [];
      return this.$.restAPI.getProjectBranches(
          filter, project, projectsPerPage, offset) .then(branches => {
            if (!branches) { return; }
            this._branches = branches;
            this._loading = false;
          });
    },

    _getPath(project) {
      return '/admin/projects/' + this.encodeURL(project, true) + ',branches';
    },

    _computeWeblink(project) {
      if (!project.web_links) { return ''; }
      const webLinks = project.web_links;
      return webLinks.length ? webLinks : null;
    },

    _stripRefsHeads(item) {
      return item.replace('refs/heads/', '');
    },
  });
})();
