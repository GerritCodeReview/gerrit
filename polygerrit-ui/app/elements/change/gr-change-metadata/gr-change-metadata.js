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

  var SubmitTypeLabel = {
    FAST_FORWARD_ONLY: 'Fast Forward Only',
    MERGE_IF_NECESSARY: 'Merge if Necessary',
    REBASE_IF_NECESSARY: 'Rebase if Necessary',
    MERGE_ALWAYS: 'Always Merge',
    CHERRY_PICK: 'Cherry Pick',
  };

  Polymer({
    is: 'gr-change-metadata',

    properties: {
      change: Object,
      commitInfo: Object,
      mutable: Boolean,
      serverConfig: Object,
      _showWebLink: {
        type: Boolean,
        computed: '_computeShowWebLink(change, commitInfo, serverConfig)',
      },
      _webLink: {
        type: String,
        computed: '_computeWebLink(change, commitInfo, serverConfig)',
      },
      _topicReadOnly: {
        type: Boolean,
        computed: '_computeTopicReadOnly(mutable, change)',
      },
    },

    behaviors: [
      Gerrit.RESTClientBehavior,
    ],

    _computeShowWebLink: function(change, commitInfo, serverConfig) {
      var webLink = commitInfo.web_links && commitInfo.web_links.length;
      var gitWeb = serverConfig.gitweb && serverConfig.gitweb.url &&
          serverConfig.gitweb.type && serverConfig.gitweb.type.revision;
      return webLink || gitWeb;
    },

    _computeWebLink: function(change, commitInfo, serverConfig) {
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

      var webLink = commitInfo.web_links[0].url;
      if (!/^https?\:\/\//.test(webLink)) {
        webLink = '../../' + webLink;
      }

      return webLink;
    },

    _computeShortHash: function(commitInfo) {
      return commitInfo.commit.slice(0, 7);
    },

    _computeHideStrategy: function(change) {
      return !this.changeIsOpen(change.status);
    },

    _computeStrategy: function(change) {
      return SubmitTypeLabel[change.submit_type];
    },

    _computeLabelNames: function(labels) {
      return Object.keys(labels).sort();
    },

    _computeLabelValues: function(labelName, labels) {
      var result = [];
      var t = labels[labelName];
      if (!t) { return result; }
      var approvals = t.all || [];
      approvals.forEach(function(label) {
        if (label.value && label.value != labels[labelName].default_value) {
          var labelClassName;
          var labelValPrefix = '';
          if (label.value > 0) {
            labelValPrefix = '+';
            labelClassName = 'approved';
          } else if (label.value < 0) {
            labelClassName = 'notApproved';
          }
          result.push({
            value: labelValPrefix + label.value,
            className: labelClassName,
            account: label,
          });
        }
      });
      return result;
    },

    _computeValueTooltip: function(score, labelName) {
      var values = this.change.labels[labelName].values;
      return values[score];
    },

    _handleTopicChanged: function(e, topic) {
      if (!topic.length) { topic = null; }
      this.$.restAPI.setChangeTopic(this.change.id, topic);
    },

    _computeTopicReadOnly: function(mutable, change) {
      return !mutable || !change.actions.topic || !change.actions.topic.enabled;
    },

    _computeTopicPlaceholder: function(_topicReadOnly) {
      return _topicReadOnly ? 'No Topic' : 'Click to add topic';
    },
  });
})();
