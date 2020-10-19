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
import '@polymer/iron-selector/iron-selector';
import '../../shared/gr-button/gr-button';
import '../../../styles/gr-voting-styles';
import '../../../styles/shared-styles';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-label-score-row_html';
import {customElement, property} from '@polymer/decorators';
import {IronSelectorElement} from '@polymer/iron-selector/iron-selector';
import {
  LabelNameToValueMap,
  LabelNameToInfoMap,
  QuickLabelInfo,
  DetailedLabelInfo,
} from '../../../types/common';
import {hasOwnProperty} from '../../../utils/common-util';

export interface Label {
  name: string;
  value: string | null;
}

// TODO(TS): add description to explain what this is after moving
// gr-label-scores to ts
export interface LabelValuesMap {
  [key: number]: number;
}

export interface GrLabelScoreRow {
  $: {
    labelSelector: IronSelectorElement;
  };
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-label-score-row': GrLabelScoreRow;
  }
}

@customElement('gr-label-score-row')
export class GrLabelScoreRow extends GestureEventListeners(
  LegacyElementMixin(PolymerElement)
) {
  static get template() {
    return htmlTemplate;
  }

  /**
   * Fired when any label is changed.
   *
   * @event labels-changed
   */

  @property({type: Object})
  label: Label | undefined | null;

  @property({type: Object})
  labels?: LabelNameToInfoMap;

  @property({type: String, reflectToAttribute: true})
  name?: string;

  @property({type: Object})
  permittedLabels: LabelNameToValueMap | undefined | null;

  @property({type: Object})
  labelValues?: LabelValuesMap;

  @property({type: String})
  _selectedValueText = 'No value selected';

  @property({
    computed: '_computePermittedLabelValues(permittedLabels, label.name)',
    type: Array,
  })
  _items!: string[];

  get selectedItem() {
    if (!this._ironSelector) {
      return undefined;
    }
    return this._ironSelector.selectedItem;
  }

  get selectedValue() {
    if (!this._ironSelector) {
      return undefined;
    }
    return this._ironSelector.selected;
  }

  setSelectedValue(value: string) {
    // The selector may not be present if it’s not at the latest patch set.
    if (!this._ironSelector) {
      return;
    }
    this._ironSelector.select(value);
  }

  get _ironSelector() {
    return this.$ && this.$.labelSelector;
  }

  _computeBlankItems(
    permittedLabels: LabelNameToValueMap,
    label: string,
    side: string
  ) {
    if (
      !permittedLabels ||
      !permittedLabels[label] ||
      !permittedLabels[label].length ||
      !this.labelValues ||
      !Object.keys(this.labelValues).length
    ) {
      return [];
    }
    const startPosition = this.labelValues[Number(permittedLabels[label][0])];
    if (side === 'start') {
      return new Array(startPosition);
    }
    const endPosition = this.labelValues[
      Number(permittedLabels[label][permittedLabels[label].length - 1])
    ];
    return new Array(Object.keys(this.labelValues).length - endPosition - 1);
  }

  _getLabelValue(
    labels: LabelNameToInfoMap,
    permittedLabels: LabelNameToValueMap,
    label: Label
  ) {
    if (label.value) {
      return label.value;
    } else if (
      hasOwnProperty(labels[label.name], 'default_value') &&
      hasOwnProperty(permittedLabels, label.name)
    ) {
      // default_value is an int, convert it to string label, e.g. "+1".
      return permittedLabels[label.name].find(
        value =>
          Number(value) === (labels[label.name] as QuickLabelInfo).default_value
      );
    }
    return;
  }

  /**
   * Maps the label value to exactly one of: min, max, positive, negative,
   * neutral. Used for the 'vote' attribute, because we don't want to
   * interfere with <iron-selector> using the 'class' attribute for setting
   * 'iron-selected'.
   */
  _computeVoteAttribute(value: number, index: number, totalItems: number) {
    if (value < 0 && index === 0) {
      return 'min';
    } else if (value < 0) {
      return 'negative';
    } else if (value > 0 && index === totalItems - 1) {
      return 'max';
    } else if (value > 0) {
      return 'positive';
    } else {
      return 'neutral';
    }
  }

  _computeLabelValue(
    labels?: LabelNameToInfoMap,
    permittedLabels?: LabelNameToValueMap,
    label?: Label
  ) {
    // Polymer 2+ undefined check
    if (
      labels === undefined ||
      permittedLabels === undefined ||
      label === undefined
    ) {
      return null;
    }

    if (!labels[label.name]) {
      return null;
    }
    const labelValue = this._getLabelValue(labels, permittedLabels, label);
    const len = permittedLabels[label.name]
      ? permittedLabels[label.name].length
      : 0;
    for (let i = 0; i < len; i++) {
      const val = permittedLabels[label.name][i];
      if (val === labelValue) {
        return val;
      }
    }
    return null;
  }

  _setSelectedValueText(e: Event) {
    // Needed because when the selected item changes, it first changes to
    // nothing and then to the new item.
    const selectedItem = (e.target as IronSelectorElement)
      .selectedItem as HTMLElement;
    if (!selectedItem) {
      return;
    }
    if (!this.$.labelSelector.items) {
      return;
    }
    for (const item of this.$.labelSelector.items) {
      if (selectedItem === item) {
        item.setAttribute('aria-checked', 'true');
      } else {
        item.removeAttribute('aria-checked');
      }
    }
    this._selectedValueText = selectedItem.getAttribute('title') || '';
    // Needed to update the style of the selected button.
    this.updateStyles();
    const name = selectedItem.dataset['name'];
    const value = selectedItem.dataset['value'];
    this.dispatchEvent(
      new CustomEvent('labels-changed', {
        detail: {name, value},
        bubbles: true,
        composed: true,
      })
    );
  }

  _computeAnyPermittedLabelValues(
    permittedLabels: LabelNameToValueMap,
    labelName: string
  ) {
    return (
      permittedLabels &&
      hasOwnProperty(permittedLabels, labelName) &&
      permittedLabels[labelName].length
    );
  }

  _computeHiddenClass(permittedLabels: LabelNameToValueMap, labelName: string) {
    return !this._computeAnyPermittedLabelValues(permittedLabels, labelName)
      ? 'hidden'
      : '';
  }

  _computePermittedLabelValues(
    permittedLabels?: LabelNameToValueMap,
    labelName?: string
  ) {
    // Polymer 2: check for undefined
    if (permittedLabels === undefined || labelName === undefined) {
      return [];
    }

    return permittedLabels[labelName] || [];
  }

  _computeLabelValueTitle(
    labels: LabelNameToInfoMap,
    label: string,
    value: string
  ) {
    // TODO(TS): maybe add a type guard for DetailedLabelInfo and QuickLabelInfo
    return (
      labels[label] &&
      (labels[label] as DetailedLabelInfo).values &&
      (labels[label] as DetailedLabelInfo).values![value]
    );
  }
}
