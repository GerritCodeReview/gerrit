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
      /**
       * @type {{ name: string }}
       */
      label: Object,
      labels: Object,
      name: {
        type: String,
        reflectToAttribute: true,
      },
      permittedLabels: Object,
      labelValues: Object,
      _selectedValueText: {
        type: String,
        value: 'No value selected',
      },
    },

    get selectedItem() {
      if (!this._ironSelector) { return; }
      return this._ironSelector.selectedItem;
    },

    get selectedValue() {
      if (!this._ironSelector) { return; }
      return this._ironSelector.selected;
    },

    setSelectedValue(value) {
      // The selector may not be present if it’s not at the latest patch set.
      if (!this._ironSelector) { return; }
      this._ironSelector.select(value);
    },

    get _ironSelector() {
      return this.$$('iron-selector');
    },

    _computeBlankItems(permittedLabels, label, side) {
      if (!permittedLabels || !permittedLabels[label]) { return []; }
      const startPosition = this.labelValues[parseInt(
          permittedLabels[label][0], 10)];
      if (side === 'start') {
        return new Array(startPosition);
      }
      const endPosition = this.labelValues[parseInt(
          permittedLabels[label][permittedLabels[label].length - 1], 10)];
      return new Array(Object.keys(this.labelValues).length - endPosition - 1);
    },

    _getLabelValue(labels, permittedLabels, label) {
      if (label.value) {
        return label.value;
      } else if (labels[label.name].hasOwnProperty('default_value') &&
                 permittedLabels.hasOwnProperty(label.name)) {
        // default_value is an int, convert it to string label, e.g. "+1".
        return permittedLabels[label.name].find(
            value => parseInt(value, 10) === labels[label.name].default_value);
      }
    },

    _computeLabelValue(labels, permittedLabels, label) {
      if (!labels[label.name]) { return null; }
      const labelValue = this._getLabelValue(labels, permittedLabels, label);
      const len = permittedLabels[label.name] != null ?
          permittedLabels[label.name].length : 0;
      for (let i = 0; i < len; i++) {
        const val = permittedLabels[label.name][i];
        if (val === labelValue) {
          return val;
        }
      }
      return null;
    },

    _setSelectedValueText(e) {
      // Needed because when the selected item changes, it first changes to
      // nothing and then to the new item.
      if (!e.target.selectedItem) { return; }
      this._selectedValueText = e.target.selectedItem.getAttribute('title');
    },

    _computeAnyPermittedLabelValues(permittedLabels, label) {
      return permittedLabels.hasOwnProperty(label);
    },

    _computeHiddenClass(permittedLabels, label) {
      return !this._computeAnyPermittedLabelValues(permittedLabels, label) ?
          'hidden' : '';
    },

    _computePermittedLabelValues(permittedLabels, label) {
      return permittedLabels[label];
    },

    _computeLabelValueTitle(labels, label, value) {
      return labels[label] && labels[label].values[value];
    },
  });
})();
