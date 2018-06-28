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
    is: 'gr-commit-info',

    properties: {
      change: Object,
      commitInfo: Object,
      serverConfig: Object,
      _showWebLink: {
        type: Boolean,
        computed: '_computeShowWebLink(change, commitInfo, serverConfig)',
      },
      _webLink: {
        type: String,
        computed: '_computeWebLink(change, commitInfo, serverConfig)',
      },
    },

    _isWebLink(link) {
      // This is a whitelist of web link types that provide direct links to
      // the commit in the url property.
      return link.name === 'gitiles' || link.name === 'gitweb';
    },

    _computeShowWebLink(change, commitInfo, serverConfig) {
      if (serverConfig.gitweb && serverConfig.gitweb.url &&
          serverConfig.gitweb.type && serverConfig.gitweb.type.revision) {
        return true;
      }

      if (!commitInfo.web_links) {
        return false;
      }

      for (const link of commitInfo.web_links) {
        if (this._isWebLink(link)) {
          return true;
        }
      }

      return false;
    },

    _computeWebLink(change, commitInfo, serverConfig) {
      if (!this._computeShowWebLink(change, commitInfo, serverConfig)) {
        return;
      }

      if (serverConfig.gitweb && serverConfig.gitweb.url &&
          serverConfig.gitweb.type && serverConfig.gitweb.type.revision) {
        return serverConfig.gitweb.url +
            serverConfig.gitweb.type.revision
                .replace('${project}', change.project)
                .replace('${commit}', commitInfo.commit);
      }

      let webLink = null;
      for (const link of commitInfo.web_links) {
        if (this._isWebLink(link)) {
          webLink = link.url;
          break;
        }
      }

      if (!webLink) {
        return;
      }

      return webLink;
    },

    _computeShortHash(commitInfo) {
      if (!commitInfo || !commitInfo.commit) {
        return;
      }
      return commitInfo.commit.slice(0, 7);
    },
  });
})();
