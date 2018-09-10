/**
@license
Copyright (C) 2017 The Android Open Source Project

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
import '../../../../@polymer/polymer/polymer-legacy.js';

import '../../../../@polymer/iron-selector/iron-selector.js';
import '../../shared/gr-button/gr-button.js';
import '../../../styles/gr-voting-styles.js';
import '../../../styles/shared-styles.js';

Polymer({
  _template: Polymer.html`
    <style include="gr-voting-styles"></style>
    <style include="shared-styles">
      .labelContainer {
        align-items: center;
        display: flex;
        margin-bottom: .5em;
      }
      .labelName {
        display: inline-block;
        flex: 0 0 auto;
        margin-right: .5em;
        min-width: 7em;
        text-align: left;
        width: 20%;
      }
      .labelMessage {
        color: var(--deemphasized-text-color);
      }
      .placeholder::before {
        content: ' ';
      }
      .selectedValueText {
        color: var(--deemphasized-text-color);
        font-style: italic;
        margin: 0 .5em 0 .5em;
      }
      .selectedValueText.hidden {
        display: none;
      }
      .buttonWrapper {
        flex: none;
      }
      gr-button {
        min-width: 40px;
        --gr-button: {
          background-color: var(--button-background-color, var(--table-header-background-color));
          color: var(--primary-text-color);
          padding: .2em .85em;
          @apply(--vote-chip-styles);
        }
      }
      gr-button.iron-selected.max {
        --button-background-color: var(--vote-color-approved);
      }
      gr-button.iron-selected.positive {
        --button-background-color: var(--vote-color-recommended);
      }
      gr-button.iron-selected.min {
        --button-background-color: var(--vote-color-rejected);
      }
      gr-button.iron-selected.negative {
        --button-background-color: var(--vote-color-disliked);
      }
      gr-button.iron-selected.neutral {
        --button-background-color: var(--vote-color-neutral);
      }
      .placeholder {
        display: inline-block;
        width: 40px;
      }
      @media only screen and (max-width: 50em) {
        .selectedValueText {
          display: none;
        }
      }
      @media only screen and (max-width: 25em) {
        .labelName {
          margin: 0;
          text-align: center;
          width: 100%;
        }
        .labelContainer {
          display: block;
        }
      }
    </style>
    <div class="labelContainer">
      <span class="labelName">[[label.name]]</span>
      <div class="buttonWrapper">
        <template is="dom-repeat" items="[[_computeBlankItems(permittedLabels, label.name, 'start')]]" as="value">
          <span class="placeholder" data-label\$="[[label.name]]"></span>
        </template>
        <iron-selector attr-for-selected="value" selected="[[_computeLabelValue(labels, permittedLabels, label)]]" hidden\$="[[!_computeAnyPermittedLabelValues(permittedLabels, label.name)]]" on-selected-item-changed="_setSelectedValueText">
          <template is="dom-repeat" items="[[_items]]" as="value">
            <gr-button class\$="[[_computeButtonClass(value, index, _items.length)]]" has-tooltip="" name="[[label.name]]" value\$="[[value]]" title\$="[[_computeLabelValueTitle(labels, label.name, value)]]">
              [[value]]</gr-button>
          </template>
        </iron-selector>
        <template is="dom-repeat" items="[[_computeBlankItems(permittedLabels, label.name, 'end')]]" as="value">
          <span class="placeholder" data-label\$="[[label.name]]"></span>
        </template>
        <span class="labelMessage" hidden\$="[[_computeAnyPermittedLabelValues(permittedLabels, label.name)]]">
          You don't have permission to edit this label.
        </span>
      </div>
      <div class\$="selectedValueText [[_computeHiddenClass(permittedLabels, label.name)]]">
        <span id="selectedValueLabel">[[_selectedValueText]]</span>
      </div>
    </div>
`,

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
    return this.$$('iron-selector');
  },

  _computeBlankItems(permittedLabels, label, side) {
    if (!permittedLabels || !permittedLabels[label] || !this.labelValues ||
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
    if (value < 0 && index === 0) {
      return 'min';
    } else if (value < 0) {
      return 'negative';
    } else if (value > 0 && index === totalItems - 1) {
      return 'max';
    } else if (value > 0) {
      return 'positive';
    }
    return 'neutral';
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
    // Needed to update the style of the selected button.
    this.updateStyles();
    const name = e.target.selectedItem.name;
    const value = e.target.selectedItem.getAttribute('value');
    this.dispatchEvent(new CustomEvent(
      'labels-changed', {detail: {name, value}, bubbles: true}));
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
    return labels[label] &&
      labels[label].values &&
      labels[label].values[value];
  }
});
