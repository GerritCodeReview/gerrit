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

  class GrLabelScores extends Polymer.GestureEventListeners(
      Polymer.LegacyElementMixin(
          Polymer.Element)) {
    static get is() { return 'gr-label-scores'; }

    static get properties() {
      return {
        _labels: {
          type: Array,
          computed: '_computeLabels(change.labels.*, account)',
        },
        permittedLabels: {
          type: Object,
          observer: '_computeColumns',
        },
        /** @type {?} */
        change: Object,
        /** @type {?} */
        account: Object,

        _labelValues: Object,
      };
    }

    getLabelValues() {
      const labels = {};
      for (const label in this.permittedLabels) {
        if (!this.permittedLabels.hasOwnProperty(label)) { continue; }

        const selectorEl = this.$$(`gr-label-score-row[name="${label}"]`);
        if (!selectorEl) { continue; }

        // The user may have not voted on this label.
        if (!selectorEl.selectedItem) { continue; }

        const selectedVal = parseInt(selectorEl.selectedValue, 10);

        // Only send the selection if the user changed it.
        let prevVal = this._getVoteForAccount(this.change.labels, label,
            this.account);
        if (prevVal !== null) {
          prevVal = parseInt(prevVal, 10);
        }
        if (selectedVal !== prevVal) {
          labels[label] = selectedVal;
        }
      }
      return labels;
    }

    _getStringLabelValue(labels, labelName, numberValue) {
      for (const k in labels[labelName].values) {
        if (parseInt(k, 10) === numberValue) {
          return k;
        }
      }
      return numberValue;
    }

    _getVoteForAccount(labels, labelName, account) {
      const votes = labels[labelName];
      if (votes.all && votes.all.length > 0) {
        for (let i = 0; i < votes.all.length; i++) {
          if (votes.all[i]._account_id == account._account_id) {
            return this._getStringLabelValue(
                labels, labelName, votes.all[i].value);
          }
        }
      }
      return null;
    }

    _computeLabels(labelRecord, account) {
      // Polymer 2: check for undefined
      if ([labelRecord, account].some(arg => arg === undefined)) {
        return undefined;
      }

      const labelsObj = labelRecord.base;
      if (!labelsObj) { return []; }
      return Object.keys(labelsObj).sort().map(key => {
        return {
          name: key,
          value: this._getVoteForAccount(labelsObj, key, this.account),
        };
      });
    }

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
    }

    _changeIsMerged(changeStatus) {
      return changeStatus === 'MERGED';
    }

    /**
     * @param label {string|undefined}
     * @param permittedLabels {Object|undefined}
     * @return {string}
     */
    _computeLabelAccessClass(label, permittedLabels) {
      if (label == null || permittedLabels == null) {
        return '';
      }

      return permittedLabels.hasOwnProperty(label) &&
        permittedLabels[label].length ? 'access' : 'no-access';
    }
  }

  customElements.define(GrLabelScores.is, GrLabelScores);
})();
