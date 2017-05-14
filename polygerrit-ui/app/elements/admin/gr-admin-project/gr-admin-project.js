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
    is: 'gr-admin-project',

    properties: {
      /**
       * URL params passed from the router.
       */
      params: {
        type: Object,
        observer: '_paramsChanged',
      },

      _project: Array,

      _projectConfig: Array,

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

    _paramsChanged: function(value) {

      if (value.project) {
        this._project = value.project
      }

      return this.$.restAPI.getProjectConfig(this._project)
        .then(function(project) {
          if (!project) {
            this._projectConfig = [];
            return;
          }
          this._projectConfig = project;
      }.bind(this));
    },

    _getUrl: function(item) {
      return this.getBaseUrl() + '/admin/projects/' + item;
    },

    _isProjectWebLink: function(link) {
      return link.name === 'gitiles' || link.name === 'gitweb';
    },

    _computeWeblink: function(project) {
      if (!project.web_links) {
        return '';
      }
      var webLinks = project.web_links.filter(
          function(l) {return !this._isProjectWebLink(l); }.bind(this));
      return webLinks.length ? webLinks : null;
    },

    submitType: function(submitType) {
      if (submitType == "FAST_FORWARD_ONLY") {
        return "Fast Forward Only";
      } else if (submitType == "MERGE_IF_NECESSARY") {
        return "Merge If Necessary";
      } else if (submitType == "REBASE_IF_NECESSARY") {
        return "Rebase If Necessary";
      } //else if (submitType == "
    },
  });
})();
