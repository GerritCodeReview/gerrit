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
import '../gr-label-score-row/gr-label-score-row';
import '../../../styles/shared-styles';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-label-scores_html';
import {customElement, property} from '@polymer/decorators';
import {hasOwnProperty} from '../../../utils/common-util';
import {
  LabelNameToValueMap,
  ChangeInfo,
  AccountInfo,
  DetailedLabelInfo,
  LabelNameToInfoMap,
  LabelNameToValuesMap,
} from '../../../types/common';
import {
  GrLabelScoreRow,
  LabelValuesMap,
} from '../gr-label-score-row/gr-label-score-row';
import {PolymerDeepPropertyChange} from '@polymer/polymer/interfaces';

type Labels = {[label: string]: number};
@customElement('gr-label-scores')
export class GrLabelScores extends GestureEventListeners(
  LegacyElementMixin(PolymerElement)
) {
  static get template() {
    return htmlTemplate;
  }

  @property({type: Array, computed: '_computeLabels(change.labels.*, account)'})
  _labels?: Labels;

  @property({type: Object, observer: '_computeColumns'})
  permittedLabels?: LabelNameToValueMap;

  @property({type: Object})
  change?: ChangeInfo;

  @property({type: Object})
  account?: AccountInfo;

  @property({type: Object})
  _labelValues?: LabelValuesMap;

  getLabelValues(includeDefaults = true): LabelNameToValuesMap {
    const labels: LabelNameToValuesMap = {};
    if (this.shadowRoot === null || !this.change) {
      return labels;
    }
    for (const label of Object.keys(this.permittedLabels ?? {})) {
      const selectorEl = this.shadowRoot.querySelector(
        `gr-label-score-row[name="${label}"]`
      ) as null | GrLabelScoreRow;
      if (!selectorEl) {
        continue;
      }

      // The user may have not voted on this label.
      if (!selectorEl.selectedItem) {
        continue;
      }

      const selectedVal =
        typeof selectorEl.selectedValue === 'string'
          ? Number(selectorEl.selectedValue)
          : selectorEl.selectedValue;

      if (selectedVal === undefined) {
        continue;
      }

      const defValNum = this._getDefaultValue(this.change.labels, label);
      if (includeDefaults || selectedVal !== defValNum) {
        labels[label] = selectedVal;
      }
    }
    return labels;
  }

  _getStringLabelValue(
    labels: LabelNameToInfoMap,
    labelName: string,
    numberValue?: number
  ) {
    const detailedInfo = labels[labelName] as DetailedLabelInfo;
    for (const labelValue of Object.keys(detailedInfo.values)) {
      if (Number(labelValue) === numberValue) {
        return labelValue;
      }
    }
    return numberValue;
  }

  _getDefaultValue(labels?: LabelNameToInfoMap, labelName?: string) {
    if (!labelName || !labels?.[labelName]) return undefined;
    const labelInfo = labels[labelName] as DetailedLabelInfo;
    return labelInfo.default_value;
  }

  _getVoteForAccount(
    labels: LabelNameToInfoMap | undefined,
    labelName: string,
    account?: AccountInfo
  ) {
    if (!labels) return null;
    const votes = labels[labelName] as DetailedLabelInfo;
    if (votes.all && votes.all.length > 0) {
      for (let i = 0; i < votes.all.length; i++) {
        // TODO(TS): Replace == with === and check code can assign string to _account_id instead of number
        // eslint-disable-next-line eqeqeq
        if (account && votes.all[i]._account_id == account._account_id) {
          return this._getStringLabelValue(
            labels,
            labelName,
            votes.all[i].value
          );
        }
      }
    }
    return null;
  }

  _computeLabels(
    labelRecord: PolymerDeepPropertyChange<
      LabelNameToInfoMap,
      LabelNameToInfoMap
    >,
    account?: AccountInfo
  ) {
    // Polymer 2: check for undefined
    if ([labelRecord, account].includes(undefined)) {
      return undefined;
    }

    const labelsObj = labelRecord.base;
    if (!labelsObj) {
      return [];
    }
    return Object.keys(labelsObj)
      .sort()
      .map(key => {
        return {
          name: key,
          value: this._getVoteForAccount(labelsObj, key, this.account),
        };
      });
  }

  _computeColumns(permittedLabels?: LabelNameToValueMap) {
    if (!permittedLabels) return;
    const labels = Object.keys(permittedLabels);
    const values: Set<number> = new Set();
    for (const label of labels) {
      for (const value of permittedLabels[label]) {
        values.add(Number(value));
      }
    }

    const orderedValues = Array.from(values.values()).sort((a, b) => a - b);

    const labelValues: LabelValuesMap = {};
    for (let i = 0; i < orderedValues.length; i++) {
      labelValues[orderedValues[i]] = i;
    }
    this._labelValues = labelValues;
  }

  _changeIsMerged(changeStatus: string) {
    return changeStatus === 'MERGED';
  }

  _computeLabelAccessClass(
    label?: string,
    permittedLabels?: LabelNameToValueMap
  ) {
    if (!permittedLabels || !label) {
      return '';
    }

    return hasOwnProperty(permittedLabels, label) &&
      permittedLabels[label].length
      ? 'access'
      : 'no-access';
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-label-scores': GrLabelScores;
  }
}
