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
    is: 'gr-admin-project-list',

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

      _projects: Array,

      /**
       * Because  we request one more than the projectsPerPage, _shownProjects
       * maybe one less than _projects.
       * */
      _shownProjects: {
        type: Array,
        computed: '_computeShownProjects(_projects)',
      },

      _projectsPerPage: {
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
      Gerrit.BaseUrlBehavior,
      Gerrit.URLEncodingBehavior,
    ],

    _paramsChanged(value) {
      this._loading = true;

      if (value) {
        this._filter = value.filter || null;
      }

      if (value && value.offset) {
        this._offset = value.offset;
      } else {
        this._offset = 0;
      }

      return this._getProjects(this._filter, this._projectsPerPage,
          this._offset);
    },

    _getProjects(filter, projectsPerPage, offset) {
      return this.$.restAPI.getProjects(filter, projectsPerPage, offset)
          .then(projects => {
            if (!projects) {
              this._projects = [];
              return;
            }
            this._projects = Object.keys(projects)
             .map(key => {
               const project = projects[key];
               project.name = key;
               return project;
             });
            this._loading = false;
          });
    },

    _readOnly(item) {
      return item.state === 'READ_ONLY' ? 'Y' : 'N';
    },

    _getUrl(item) {
      return this.getBaseUrl() + '/admin/projects/' +
          this.encodeURL(item, true);
    },

    _computeWeblink(project) {
      if (!project.web_links) {
        return '';
      }
      const webLinks = project.web_links;
      return webLinks.length ? webLinks : null;
    },

    _computeShownProjects(projects) {
      return projects.slice(0, 25);
    },
  });
})();
