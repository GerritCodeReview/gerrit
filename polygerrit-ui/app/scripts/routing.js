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
(function(window) {
  'use strict';

  window.Gerrit = window.Gerrit || {};
  if (window.Gerrit.hasOwnProperty('Routing')) { return; }

  window.Gerrit.Routing = {

    View: {
      CHANGE: 'change',
      SEARCH: 'search',
    },

    /** @type {Function} */
    _navigate: null,

    /** @type {Function} */
    _generateUrl: null,

    /** @type {Boolean} */
    _isInitialized: false,

    /**
     * Setup router implementation.
     * @param {Function} handleNavigate
     * @param {Function} generateUrl
     */
    setup(navigate, generateUrl) {
      this._navigate = navigate;
      this._generateUrl = generateUrl;
      this._isInitialized = true;
    },

    destroy() {
      this._navigate = null;
      this._generateUrl = null;
      this._isInitialized = false;
    },

    /**
     * Generate a URL for the given route parameters.
     * @param {Object} params
     * @return {String}
     */
    getUrlFor(params) {
      this._checkInit();
      return this._generateUrl(params);
    },

    getUrlForProject(project) {
      return this.getUrlFor({
        view: Gerrit.Routing.View.SEARCH,
        project,
      });
    },

    getUrlForBranch(branch, project, status) {
      return this.getUrlFor({
        view: Gerrit.Routing.View.SEARCH,
        branch,
        project,
        statuses: [status],
      });
    },

    getUrlForTopic(topic) {
      return this.getUrlFor({
        view: Gerrit.Routing.View.SEARCH,
        topic,
        statuses: ['open', 'merged'],
      });
    },

    getUrlForChange(change) {
      return this.getUrlFor({
        view: Gerrit.Routing.View.CHANGE,
        id: change._number,
      });
    },

    navigateToChange(change) {
      this._checkInit();
      this._navigate(this.getUrlForChange(change));
    },

    _checkInit() {
      if (!this._isInitialized) {
        throw new Error('Use of uninitialized routing');
      }
    },
  };
})(window);
