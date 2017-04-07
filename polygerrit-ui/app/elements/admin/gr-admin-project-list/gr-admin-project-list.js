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

    /*attached: function() {
      this._loadData();
    },*/

    _paramsChanged: function(value) {

      this._loading = true;

      this._offset = value.offset || 0;

      return this.$.restAPI.getProjects(this._projectsPerPage, this._offset)
        .then(function(projects) {
          if (!projects) {
            this._projects = [];
            this._loading = false;
            return;
          }
          this._projects = Object.keys(projects)
             .map(function(key) {
                var project = projects[key];
                project.name = key;
                return project;
              });
          this._loading = false;
      }.bind(this));
    },

    _getUrl: function(item) {
      return this.getBaseUrl() + '/admin/projects/' + item;
    },

    _computeWeblinkUrl: function(project) {
      if (!project.web_links) {
        return '';
      }
      return project.web_links[project.web_links.length - 1].url;
    },

    _computeWeblinkName: function(project) {
      if (!project.web_links) {
        return '';
      }
      return '(' + project.web_links[project.web_links.length - 1].name + ')';
    },

    _computeNavLink: function(offset, direction, projectsPerPage) {
      // Offset could be a string when passed from the router.
      offset = +(offset || 0);
      var newOffset = Math.max(0, offset + (projectsPerPage * direction));
      // Double encode URI component.
      var href = this.getBaseUrl() + '/admin/projects/';
      if (newOffset > 0) {
        href += ',' + newOffset;
      }
      return href;
    },

    _hidePrevArrow: function(offset) {
      return offset === 0;
    },

    _hideNextArrow: function(loading) {
      return loading || !this._projects || !this._projects.length;
    },

    _handleNextPage: function() {
      if (this.$.nextArrow.hidden) { return; }
      page.show(this._computeNavLink(
           this._offset, 1, this._projectsPerPage));
    },

    _handlePreviousPage: function() {
      if (this.$.prevArrow.hidden) { return; }
      page.show(this._computeNavLink(
          this._offset, -1, this._projectsPerPage));
    },
  });
})();
