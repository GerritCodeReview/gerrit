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
    is: 'gr-label-scores',
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

    getLabelValues() {
      const labels = {};
      for (const label in this.permittedLabels) {
        if (!this.permittedLabels.hasOwnProperty(label)) { continue; }

        const selectorEl = this.$$(`iron-selector[label="${label}"]`);
        // The user may have not voted on this label.
        if (!selectorEl || !selectorEl.selectedItem) { continue; }

        let selectedVal = parseInt(selectorEl.selected, 10);

        // Only send the selection if the user changed it.
        let prevVal =
            this._getVoteForAccount(this.change.labels, label, this.account);
        if (prevVal !== null) {
          prevVal = parseInt(prevVal, 10);
        }
        if (selectedVal !== prevVal) {
          labels[label] = selectedVal;
        }
      }
      return labels;
    },

    _getVoteForAccount(labels, labelName, account) {
      const votes = labels[labelName];
      if (votes.all && votes.all.length > 0) {
        for (let i = 0; i < votes.all.length; i++) {
          if (votes.all[i]._account_id == account._account_id) {
            return votes.all[i].value;
          }
        }
      }
      return null;
    },

    _computeLabels(labelRecord) {
      const labelsObj = labelRecord.base;
      if (!labelsObj) { return []; }
      return Object.keys(labelsObj).sort().map(key => {
        return {
          name: key,
          value: this._getVoteForAccount(labelsObj, key, this.account),
        };
      });
    },

    _computeColumns(permittedLabels) {
      const labels = Object.keys(permittedLabels);
      const values = {};
      for (const label of labels) {
        for (const value of permittedLabels[label]) {
          values[parseInt(value, 10)] = true;
        }
      }

      const orderedValues = Object.keys(values).sort((a, b) => {
        return a - b;
      });

      for (let i = 0; i < orderedValues.length; i++) {
        values[orderedValues[i]] = i;
      }
      this._labelValues = values;
    },

    _getLabelValueByIntScore: function(labels, score) {
      for(const labelValue in labels) {
        if (parseInt(labelValue, 10) === score) {
          return labelValue;
        }
      }
    },

    _computeLabelValue(labels, permittedLabels, label) {
      if (!labels[label.name]) { return null; }
      const labelValue = label.value;
      const len = permittedLabels[label.name] != null ?
          permittedLabels[label.name].length : 0;
      for (let i = 0; i < len; i++) {
        const val = parseInt(permittedLabels[label.name][i], 10);
        if (val == labelValue) {
          return this._getLabelValueByIntScore(labels[label.name].values, val);
        }
      }
      return null;
    },

    _computeIndexOfLabelValue(labels, permittedLabels, label) {
      if (!labels[label.name]) { return null; }
      const labelValue = label.value;
      const len = permittedLabels[label.name] != null ?
          permittedLabels[label.name].length : 0;
      for (let i = 0; i < len; i++) {
        const val = parseInt(permittedLabels[label.name][i], 10);
        if (val == labelValue) {
          return i;
        }
      }
      return null;
    },

    _computePermittedLabelValues(permittedLabels, label) {
      return permittedLabels[label];
    },

    _computeBlankItems(permittedLabels, label, side) {
      if (!permittedLabels[label]) { return []; }
      const startPosition = this._labelValues[parseInt(
          permittedLabels[label][0])];
      if (side === 'start') {
        return new Array(startPosition);
      }
      const endPosition = this._labelValues[parseInt(
          permittedLabels[label][permittedLabels[label].length - 1])];
      return new Array(Object.keys(this._labelValues).length - endPosition - 1);
    },

    _computeAnyPermittedLabelValues(permittedLabels, label) {
      return permittedLabels.hasOwnProperty(label);
    },

    _changeIsMerged(changeStatus) {
      return changeStatus === 'MERGED';
    },

    _computeLabelValueTitle(labels, label, value) {
      return labels[label] && labels[label].values[value];
    },
  });
})();
