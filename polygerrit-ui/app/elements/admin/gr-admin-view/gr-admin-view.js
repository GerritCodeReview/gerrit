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
    is: 'gr-admin-view',

    properties: {
      params: Object,
      path: String,
      adminView: String,

      _adminSideLinks: Object,
      _showDownload: {
        type: Boolean,
        value: false,
      },
      _showProjectMain: Boolean,
      _showTestView: Boolean,
    },

    behaviors: [
      Gerrit.BaseUrlBehavior,
    ],

    observers: [
      '_paramsChanged(params)',
    ],

    _paramsChanged(params) {
      this.set('_showCreateProject',
          params.adminView === 'gr-admin-create-project');
      this.set('_showProjectMain', params.adminView === 'gr-admin-project');
      this.set('_showProjectList',
          params.adminView === 'gr-admin-project-list');
      this.set('_showGroupList', params.adminView === 'gr-admin-group-list');
      this.set('_showPluginList', params.adminView === 'gr-admin-plugin-list');
    },

    // TODO (beckysiegel): Update these functions after router abstraction is
    // updated. They are currently copied from gr-dropdown (and should be
    // updated there as well once complete).
    _computeURLHelper(host, path) {
      return '//' + host + this.getBaseUrl() + path;
    },

    _computeRelativeURL(path) {
      const host = window.location.host;
      return this._computeURLHelper(host, path);
    },

    _computeLinkURL(link) {
      if (typeof link.url === 'undefined') {
        return '';
      }
      if (link.target) {
        return link.url;
      }
      return this._computeRelativeURL(link.url);
    },

    _computeLinkRel(link) {
      return link.target ? 'noopener' : null;
    },

    _computeSelectedClass(itemView, params) {
      return itemView === params.adminView ? 'selected' : '';
    },
  });
})();
