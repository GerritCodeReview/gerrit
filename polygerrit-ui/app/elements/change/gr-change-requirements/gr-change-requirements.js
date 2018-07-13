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

  const VALID_SELECTOR_REGEX = /[^A-Za-z0-9\-]/g;

  Polymer({
    is: 'gr-change-requirements',

    properties: {
      /** @type {?} */
      change: Object,
      account: Object,
      mutable: Boolean,
      _requirements: {
        type: Array,
        computed: '_computeRequirements(change)',
      },
      _requiredLabels: {
        type: Array,
        value: () => [],
      },
      _optionalLabels: {
        type: Array,
        value: () => [],
      },
      _showWip: {
        type: Boolean,
        computed: '_computeShowWip(change)',
      },
    },

    behaviors: [
      Gerrit.RESTClientBehavior,
    ],

    observers: [
      '_computeLabels(change.labels.*)',
    ],

    _computeShowWip(change) {
      return change.work_in_progress;
    },

    _computeRequirements(change) {
      const _requirements = [];

      if (change.requirements) {
        for (const requirement of change.requirements) {
          requirement.satisfied = requirement.status === 'OK';
          _requirements.push(requirement);
        }
      }
      if (change.work_in_progress) {
        _requirements.push({
          fallback_text: 'WIP',
          tooltip: 'Change must not be in \'Work in Progress\' state.',
        });
      }

      return _requirements;
    },

    _computeRequirementClass(requirementStatus) {
      return requirementStatus ? 'positive' : 'neutral';
    },

    _computeRequirementIcon(requirementStatus) {
      return requirementStatus ? 'gr-icons:check' : 'gr-icons:hourglass';
    },

    _computeLabels(labelsRecord) {
      const labels = labelsRecord.base;
      this._optionalLabels = [];
      this._requiredLabels = [];

      for (const label in labels) {
        if (!labels.hasOwnProperty(label)) { continue; }

        const labelInfo = labels[label];
        const icon = this._computeLabelIcon(labelInfo);
        const style = this._computeLabelClass(labelInfo);
        const path = labelInfo.optional ? '_optionalLabels' : '_requiredLabels';

        this.push(path, {label, icon, style, labelInfo});
      }
    },

    /**
     * @param {Object} labelInfo
     * @return {string|undefined} The icon name, or undefined if no icon should
     *     be used.
     */
    _computeLabelIcon(labelInfo) {
      if (labelInfo.approved) { return 'gr-icons:check'; }
      if (labelInfo.rejected) { return 'gr-icons:close'; }
      // If there is an intermediate vote (e.g. +1 on a label with max value
      // +2), the value field will be populated.
      if (!labelInfo.value) { return 'gr-icons:hourglass'; }
      return undefined;
    },

    /**
     * @param {Object} labelInfo
     */
    _computeLabelClass(labelInfo) {
      const value = labelInfo.value || 0;
      if (value > 0 || labelInfo.approved) { return 'positive'; }
      if (value < 0 || labelInfo.rejected) { return 'negative'; }
      return 'neutral';
    },

    _computeLabelShortcut(label) {
      return label.split('-').reduce((a, i) => a + i[0].toUpperCase(), '');
    },

    _computeShowOptional(optionalFieldsRecord) {
      return optionalFieldsRecord.base.length ? '' : 'hidden';
    },

    _computeLabelValue(value) {
      return (value > 0 ? '+' : '') + value;
    },

    _removeInvalidChars(text) {
      return text.replace(VALID_SELECTOR_REGEX, '');
    },
  });
})();
