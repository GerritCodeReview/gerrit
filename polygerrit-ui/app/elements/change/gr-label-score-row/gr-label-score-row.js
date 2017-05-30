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
    is: 'gr-label-score-row',
    properties: {
      label: Object,
      change: Object,
      name: {
        type: String,
        reflectToAttribute: true,
      },
      permittedLabels: Object,
      labelValues: Object,
      _selectedValueText: String,
    },

    getIronSelector() {
      return this.$$('iron-selector');
    },

    getSelectedItem() {
      return this.$$('iron-selector .iron-selected');
    },

    _computeBlankItems(permittedLabels, label, side) {
      if (!permittedLabels || !permittedLabels[label]) { return []; }
      const startPosition = this.labelValues[parseInt(
          permittedLabels[label][0])];
      if (side === 'start') {
        return new Array(startPosition);
      }
      const endPosition = this.labelValues[parseInt(
          permittedLabels[label][permittedLabels[label].length - 1])];
      return new Array(Object.keys(this.labelValues).length - endPosition - 1);
    },

    _computeIndexOfLabelValue(labels, permittedLabels, label) {
      if (!labels[label.name]) { return null; }
      const labelValue = label.value;
      const len = permittedLabels[label.name] != null ?
          permittedLabels[label.name].length : 0;
      for (let i = 0; i < len; i++) {
        const val = parseInt(permittedLabels[label.name][i], 10);
        if (val === labelValue) {
          return i;
        }
      }
      return null;
    },

    _computeAnyPermittedLabelValues(permittedLabels, label) {
      return permittedLabels.hasOwnProperty(label);
    },

    _computePermittedLabelValues(permittedLabels, label) {
      return permittedLabels[label];
    },

    _computeLabelValueTitle(labels, label, value) {
      return labels[label] && labels[label].values[value];
    },
  });
})();
