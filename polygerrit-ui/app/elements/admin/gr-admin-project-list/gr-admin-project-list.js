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

      _projectsPerPage: {
        type: Number,
        value: 25,
      },

      /**
       * For showing a "loading..." string during ajax requests.
       */
      _loading: {
        type: Boolean,
        value: true,
      },
    },

    behaviors: [
      Gerrit.BaseUrlBehavior,
    ],

    listeners: {
      'next-page': '_handleNextPage',
      'previous-page': '_handlePreviousPage',
    },

    _paramsChanged(value) {
      this._loading = true;

      if (value && value.offset) {
        this._offset = value.offset || 0;
      } else {
        this._offset = 0;
      }

      return this.$.restAPI.getProjects(this._projectsPerPage, this._offset)
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
      if (item.state == 'READ_ONLY') {
        return 'Read only';
      }

      return '';
    },

    _getUrl(item) {
      return this.getBaseUrl() + '/admin/projects/' + item;
    },

    _isProjectWebLink(link) {
      return link.name === 'gitiles' || link.name === 'gitweb';
    },

    _computeWeblink(project) {
      if (!project.web_links) {
        return '';
      }
      const webLinks = project.web_links.filter(
          l => !this._isProjectWebLink(l));
      return webLinks.length ? webLinks : null;
    },

    _computeNavLink(offset, direction, projectsPerPage) {
      // Offset could be a string when passed from the router.
      offset = +(offset || 0);
      const newOffset = Math.max(0, offset + (projectsPerPage * direction));
      // Double encode URI component.
      let href = this.getBaseUrl() + '/admin/projects';
      if (newOffset > 0) {
        href += ',' + newOffset;
      }
      return href;
    },

    _hidePrevArrow(offset) {
      return offset === 0;
    },

    _hideNextArrow(loading, projects) {
      if (projects.length < 25) {
        loading = true;
      }
      return loading || !projects || !projects.length;
    },

    _handleNextPage() {
      if (this.$.nextArrow.hidden) { return; }
      page.show(this._computeNavLink(
          this._offset, 1, this._projectsPerPage));
    },

    _handlePreviousPage() {
      if (this.$.prevArrow.hidden) { return; }
      page.show(this._computeNavLink(
          this._offset, -1, this._projectsPerPage));
    },
  });
})();
