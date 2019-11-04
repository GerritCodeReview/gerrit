/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
(function() {
  'use strict';

  Polymer({
    is: 'gr-label-score-row',

    /**
     * Fired when any label is changed.
     *
     * @event labels-changed
     */

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
      _items: {
        type: Array,
        computed: '_computePermittedLabelValues(permittedLabels, label.name)',
      },
    },

    get selectedItem() {
      if (!this._ironSelector) { return undefined; }
      return this._ironSelector.selectedItem;
    },

    get selectedValue() {
      if (!this._ironSelector) { return undefined; }
      return this._ironSelector.selected;
    },

    setSelectedValue(value) {
      // The selector may not be present if it’s not at the latest patch set.
      if (!this._ironSelector) { return; }
      this._ironSelector.select(value);
    },

    get _ironSelector() {
      return this.$ && this.$.labelSelector;
    },

    _computeBlankItems(permittedLabels, label, side) {
      if (!permittedLabels || !permittedLabels[label] ||
          !permittedLabels[label].length || !this.labelValues ||
          !Object.keys(this.labelValues).length) {
        return [];
      }
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

    _computeButtonClass(value, index, totalItems) {
      const classes = [];
      if (value === this.selectedValue) {
        classes.push('iron-selected');
      }

      if (value < 0 && index === 0) {
        classes.push('min');
      } else if (value < 0) {
        classes.push('negative');
      } else if (value > 0 && index === totalItems - 1) {
        classes.push('max');
      } else if (value > 0) {
        classes.push('positive');
      } else {
        classes.push('neutral');
      }

      return classes.join(' ');
    },

    _computeLabelValue(labels, permittedLabels, label) {
      if ([labels, permittedLabels, label].some(arg => arg === undefined)) {
        return null;
      }
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
      // Needed to update the style of the selected button.
      this.updateStyles();
      const name = e.target.selectedItem.name;
      const value = e.target.selectedItem.getAttribute('value');
      this.dispatchEvent(new CustomEvent(
          'labels-changed',
          {detail: {name, value}, bubbles: true, composed: true}));
    },

    _computeAnyPermittedLabelValues(permittedLabels, label) {
      return permittedLabels && permittedLabels.hasOwnProperty(label) &&
        permittedLabels[label].length;
    },

    _computeHiddenClass(permittedLabels, label) {
      return !this._computeAnyPermittedLabelValues(permittedLabels, label) ?
          'hidden' : '';
    },

    _computePermittedLabelValues(permittedLabels, label) {
      // Polymer 2: check for undefined
      if ([permittedLabels, label].some(arg => arg === undefined)) {
        return undefined;
      }

      return permittedLabels[label];
    },

    _computeLabelValueTitle(labels, label, value) {
      return labels[label] &&
        labels[label].values &&
        labels[label].values[value];
    },
  });
})();
