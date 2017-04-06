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
    is: 'gr-labels',
    properties: {
      _labels: {
        type: Array,
        computed: '_computeLabels(change.labels.*, account)',
      },
      permittedLabels: {
        type: Object,
        observer: '_computeColumns',
      },
      change: Object,
      _labelValues: Object,
    },

    _getVoteForAccount: function(labels, labelName, account) {
      var votes = labels[labelName];
      if (votes.all && votes.all.length > 0) {
        for (var i = 0; i < votes.all.length; i++) {
          if (votes.all[i]._account_id == account._account_id) {
            return votes.all[i].value;
          }
        }
      }
      return null;
    },

    _computeLabels: function(labelRecord) {
      var labelsObj = labelRecord.base;
      if (!labelsObj) { return []; }
      return Object.keys(labelsObj).sort().map(function(key) {
        return {
          name: key,
          value: this._getVoteForAccount(labelsObj, key, this.account),
        };
      }.bind(this));
    },

    _computeColumns: function(permittedLabels) {
      var labels = Object.keys(permittedLabels);
      var values = {};

      labels.forEach(function(label) {
        permittedLabels[label].forEach(function(value) {
          values[parseInt(value)] = true;
        });
      });

      var orderedValues = Object.keys(values).sort(function(a, b) {
        return a - b;
      });

      for (var i = 0; i < orderedValues.length; i++) {
        values[orderedValues[i]] = i;
      }
      this._labelValues = values;
    },

    _computeIndexOfLabelValue: function(labels, permittedLabels, label) {
      if (!labels[label.name]) { return null; }
      var labelValue = label.value;
      var len = permittedLabels[label.name] != null ?
          permittedLabels[label.name].length : 0;
      var numberBlanks = this._computeBlankItems(permittedLabels, label.name)
          .length;
      for (var i = 0; i < len; i++) {
        var val = parseInt(permittedLabels[label.name][i], 10);
        if (val == labelValue) {
          return i + numberBlanks;
        }
      }
      return null;
    },

    _computePermittedLabelValues: function(permittedLabels, label) {
      return permittedLabels[label];
    },

    _computeBlankItems: function(permittedLabels, label) {
      var startPosition = this._labelValues[parseInt(
          permittedLabels[label][0])];
      return new Array(startPosition);
    },

    _computeAnyPermittedLabelValues: function(permittedLabels, label) {
      return permittedLabels.hasOwnProperty(label);
    },

     _computeLabelValueTitle: function(labels, label, value) {
      return labels[label] && labels[label].values[value];
    },
  });
})();
