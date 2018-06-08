/**
 * @license
 * Copyright (C) 2018 The Android Open Source Project
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
    is: 'gr-label-info',

    properties: {
      labelInfo: Object,
      label: String,
      /** @type {?} */
      change: Object,
      account: Object,
      mutable: Boolean,
    },

    /**
     * @param {!Object} labelInfo
     * @param {!Object} account
     * @param {Object} changeLabelsRecord not used, but added as a parameter in
     *    order to trigger computation when a label is removed from the change.
     */
    _mapLabelInfo(labelInfo, account, changeLabelsRecord) {
      const result = [];
      if (!labelInfo) { return result; }
      if (!labelInfo.values) {
        if (labelInfo.rejected || labelInfo.approved) {
          const ok = labelInfo.approved || !labelInfo.rejected;
          return [{
            value: ok ? '👍️' : '👎️',
            className: ok ? 'positive' : 'negative',
            account: ok ? labelInfo.approved : labelInfo.rejected,
          }];
        }
        return result;
      }
      // Sort votes by positivity.
      const votes = (labelInfo.all || []).sort((a, b) => a.value - b.value);
      const values = Object.keys(labelInfo.values);
      for (const label of votes) {
        if (label.value && label.value != labelInfo.default_value) {
          let labelClassName;
          let labelValPrefix = '';
          if (label.value > 0) {
            labelValPrefix = '+';
            if (parseInt(label.value, 10) ===
                parseInt(values[values.length - 1], 10)) {
              labelClassName = 'max';
            } else {
              labelClassName = 'positive';
            }
          } else if (label.value < 0) {
            if (parseInt(label.value, 10) === parseInt(values[0], 10)) {
              labelClassName = 'min';
            } else {
              labelClassName = 'negative';
            }
          }
          if (label._account_id === account._account_id) {
            // Put self-votes at the top, and add a flag.
            result.unshift({
              value: labelValPrefix + label.value,
              className: labelClassName,
              account: label,
              isCurrentUser: true,
            });
          } else {
            result.push({
              value: labelValPrefix + label.value,
              className: labelClassName,
              account: label,
            });
          }
        }
      }
      return result;
    },

    /**
     * A user is able to delete a vote iff the mutable property is true and the
     * reviewer that left the vote exists in the list of removable_reviewers
     * received from the backend.
     *
     * @param {!Object} reviewer An object describing the reviewer that left the
     *     vote.
     * @param {Boolean} mutable
     * @param {!Object} change
     */
    _computeDeleteClass(reviewer, mutable, change) {
      if (!mutable || !change || !change.removable_reviewers) {
        return 'hidden';
      }
      const removable = change.removable_reviewers;
      if (removable.find(r => r._account_id === reviewer._account_id)) {
        return '';
      }
      return 'hidden';
    },

    /**
     * Closure annotation for Polymer.prototype.splice is off.
     * For now, supressing annotations.
     *
     * @suppress {checkTypes} */
    _onDeleteVote(e) {
      e.preventDefault();
      let target = Polymer.dom(e).rootTarget;
      while (!target.classList.contains('deleteBtn')) {
        if (!target.parentElement) { return; }
        target = target.parentElement;
      }

      target.disabled = true;
      const accountID = parseInt(target.getAttribute('data-account-id'), 10);
      this._xhrPromise =
          this.$.restAPI.deleteVote(this.change._number, accountID, this.label)
          .then(response => {
            target.disabled = false;
            if (!response.ok) { return response; }

            const label = this.change.labels[this.label];
            const labels = label.all || [];
            let wasChanged = false;
            for (let i = 0; i < labels.length; i++) {
              if (labels[i]._account_id === accountID) {
                for (const key in label) {
                  if (label.hasOwnProperty(key) &&
                      label[key]._account_id === accountID) {
                    // Remove special label field, keeping change label values
                    // in sync with the backend.
                    this.change.labels[this.label][key] = null;
                  }
                }
                this.change.labels[this.label].all.splice(i, 1);
                wasChanged = true;
                break;
              }
            }
            if (wasChanged) { this.notifySplices('change.labels'); }
          }).catch(err => {
            target.disabled = false;
            return;
          });
    },

    _computeValueTooltip(labelInfo, score) {
      if (!labelInfo || !labelInfo.values || !labelInfo.values[score]) {
        return '';
      }
      return labelInfo.values[score];
    },

    _computeLabelContainerClass(label) {
      return label.isCurrentUser ? 'currentUser' : '';
    },

    /**
     * @param {!Object} labelInfo
     * @param {Object} changeLabelsRecord not used, but added as a parameter in
     *    order to trigger computation when a label is removed from the change.
     */
    _computeShowPlaceholder(labelInfo, changeLabelsRecord) {
      if (labelInfo.all) {
        for (const label of labelInfo.all) {
          if (label.value && label.value != labelInfo.default_value) {
            return 'hidden';
          }
        }
      }
      return '';
    },
  });
})();