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
    is: 'gr-change-list-item',

    properties: {
      visibleChangeTableColumns: Array,
      labelNames: {
        type: Array,
      },
      change: Object,
      changeURL: {
        type: String,
        computed: '_computeChangeURL(change._number)',
      },
      showStar: {
        type: Boolean,
        value: false,
      },
    },

    behaviors: [
      Gerrit.BaseUrlBehavior,
      Gerrit.ChangeTableBehavior,
      Gerrit.RESTClientBehavior,
      Gerrit.URLEncodingBehavior,
    ],

    _computeChangeURL: function(changeNum) {
      if (!changeNum) { return ''; }
      return this.getBaseUrl() + '/c/' + changeNum + '/';
    },

    _computeLabelTitle: function(change, labelName) {
      var label = change.labels[labelName];
      if (!label) { return 'Label not applicable'; }
      var significantLabel = label.rejected || label.approved ||
          label.disliked || label.recommended;
      if (significantLabel && significantLabel.name) {
        return labelName + '\nby ' + significantLabel.name;
      }
      return labelName;
    },

    _computeLabelClass: function(change, labelName) {
      var label = change.labels[labelName];
      // Mimic a Set.
      var classes = {
        'cell': true,
        'label': true,
      };
      if (label) {
        if (label.approved) {
          classes['u-green'] = true;
        }
        if (label.value == 1) {
          classes['u-monospace'] = true;
          classes['u-green'] = true;
        } else if (label.value == -1) {
          classes['u-monospace'] = true;
          classes['u-red'] = true;
        }
        if (label.rejected) {
          classes['u-red'] = true;
        }
      } else {
        classes['u-gray-background'] = true;
      }
      return Object.keys(classes).sort().join(' ');
    },

    _computeLabelValue: function(change, labelName) {
      var label = change.labels[labelName];
      if (!label) { return ''; }
      if (label.approved) {
        return '✓';
      }
      if (label.rejected) {
        return '✕';
      }
      if (label.value > 0) {
        return '+' + label.value;
      }
      if (label.value < 0) {
        return label.value;
      }
      return '';
    },

    _computeProjectURL: function(project) {
      return this.getBaseUrl() + '/q/status:open+project:' +
          this.encodeURL(project, false);
    },

    _computeProjectBranchURL: function(project, branch) {
      // @see Issue 4255.
      return this._computeProjectURL(project) +
          '+branch:' + this.encodeURL(branch, false);
    },
  });
})();
