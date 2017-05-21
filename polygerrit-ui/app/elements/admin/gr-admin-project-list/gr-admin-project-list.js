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

      _project: {
        type: Object,
        value: null,
      },

      _projectsPerPage: {
        type: Number,
        value: 25,
      },

      _loading: {
        type: Boolean,
        value: true,
      },

      keyEventTarget: {
        type: Object,
        value() { return document.body; },
      },

      value: {
        type: String,
        value: '',
        notify: true,
        observer: '_valueChanged',
      },

      _inputVal: String,
    },

    behaviors: [
      Gerrit.KeyboardShortcutBehavior,
      Gerrit.BaseUrlBehavior,
      Gerrit.URLEncodingBehavior,
    ],

    listeners: {
      'next-page': '_handleNextPage',
      'previous-page': '_handlePreviousPage',
    },

    _valueChanged(value) {
      this._inputVal = value;
    },

    _onValueChange() {
      if (this._inputVal) {
        page.show(
            '/admin/projects' + ',' + this.encodeURL(this._inputVal, false));
      }
    },

    _handleInputCommit(e) {
      this._preventDefaultAndNavigateToInputVal(e);
    },

    /**
     * This function is called in a few different cases:
     *   - e.target is the search button
     *   - e.target is the gr-autocomplete widget (#searchInput)
     *   - e.target is the input element wrapped within #searchInput
     *
     * @param {!Event} e
     */
    _preventDefaultAndNavigateToInputVal(e) {
      e.preventDefault();
      const target = Polymer.dom(e).rootTarget;
      // If the target is the #searchInput or has a sub-input component, that
      // is what holds the focus as opposed to the target from the DOM event.
      if (target.$.input) {
        target.$.input.blur();
      } else {
        target.blur();
      }
      if (this._inputVal) {
        page.show(
            '/admin/projects' + ',' + this.encodeURL(this._inputVal, false));
      }
    },

    _paramsChanged(value) {
      this._loading = true;

      if (value && value.offset) {
        this._offset = value.offset;
      } else {
        this._offset = 0;
      }

      if (value && value.project !== null) {
        this._project = value.project;
      }

      return this.$.restAPI.getProjects(
          this._project, this._projectsPerPage, this._offset).then(
          projects => {
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
          this.encodeURL(item, false);
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

    _computeNavLink(offset, direction, projectsPerPage, project) {
      // Offset could be a string when passed from the router.
      offset = +(offset || 0);
      const newOffset = Math.max(0, offset + (projectsPerPage * direction));
      let href = this.getBaseUrl() + '/admin/projects';
      if (project !== null) {
        href += ',' + project;
      }
      if (newOffset > 0) {
        href += ',' + newOffset;
      }
      return href;
    },

    _computeShownProjects(projects) {
      return projects.slice(0, 25);
    },

    _hidePrevArrow(offset) {
      return offset === 0;
    },

    _hideNextArrow(loading, projects) {
      let lastPage = false;
      if (projects.length < this._projectsPerPage + 1) {
        lastPage = true;
      }
      return loading || lastPage || !projects || !projects.length;
    },

    _handleNextPage() {
      if (this.$.nextArrow.hidden) { return; }
      page.show(this._computeNavLink(
          this._offset, 1, this._projectsPerPage, this._project));
    },

    _handlePreviousPage() {
      if (this.$.prevArrow.hidden) { return; }
      page.show(this._computeNavLink(
          this._offset, -1, this._projectsPerPage, this._project));
    },
  });
})();
