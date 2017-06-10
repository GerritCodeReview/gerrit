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

    _computeChangeURL(changeNum) {
      if (!changeNum) { return ''; }
      return this.getBaseUrl() + '/c/' + changeNum + '/';
    },

    _computeLabelTitle(change, labelName) {
      const label = change.labels[labelName];
      if (!label) { return 'Label not applicable'; }
      const significantLabel = label.rejected || label.approved ||
          label.disliked || label.recommended;
      if (significantLabel && significantLabel.name) {
        return labelName + '\nby ' + significantLabel.name;
      }
      return labelName;
    },

    _computeLabelClass(change, labelName) {
      const label = change.labels[labelName];
      // Mimic a Set.
      const classes = {
        cell: true,
        label: true,
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

    _computeLabelValue(change, labelName) {
      const label = change.labels[labelName];
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

    _computeProjectURL(project) {
      return this.getBaseUrl() + '/q/status:open+project:' +
          this.encodeURL(project, false);
    },

    _computeProjectBranchURL(change) {
      // @see Issue 4255, Issue 6195.
      let output = this._computeProjectURL(change.project);
      output += '+branch:' + this.encodeURL(change.branch, false);
      if (change.topic) {
        output += '+topic:' + this.encodeURL(change.topic, false);
      }
      return output;
    },

    _computeBranchText(change) {
      let output = change.branch;
      if (change.topic) { output += ` (${change.topic})`; }
      return output;
    },
  });
})();
