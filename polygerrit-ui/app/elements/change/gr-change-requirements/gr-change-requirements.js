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
      _showOptionalLabels: {
        type: Boolean,
        value: true,
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
          requirement.style =
              this._computeRequirementClass(requirement.satisfied);
          _requirements.push(requirement);
        }
      }
      if (change.work_in_progress) {
        _requirements.push({
          fallback_text: 'Work-in-progress',
          tooltip: 'Change must not be in \'Work in Progress\' state.',
        });
      }

      return _requirements;
    },

    _computeRequirementClass(requirementStatus) {
      return requirementStatus ? 'approved' : '';
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
     * @return {string} The icon name, or undefined if no icon should
     *     be used.
     */
    _computeLabelIcon(labelInfo) {
      if (labelInfo.approved) { return 'gr-icons:check'; }
      if (labelInfo.rejected) { return 'gr-icons:close'; }
      return 'gr-icons:hourglass';
    },

    /**
     * @param {Object} labelInfo
     */
    _computeLabelClass(labelInfo) {
      if (labelInfo.approved) { return 'approved'; }
      if (labelInfo.rejected) { return 'rejected'; }
      return '';
    },

    _computeShowOptional(optionalFieldsRecord) {
      return optionalFieldsRecord.base.length ? '' : 'hidden';
    },

    _computeLabelValue(value) {
      return (value > 0 ? '+' : '') + value;
    },

    _computeShowHideIcon(showOptionalLabels) {
      return showOptionalLabels ?
        'gr-icons:expand-less' :
        'gr-icons:expand-more';
    },

    _computeSectionClass(show) {
      return show ? '' : 'hidden';
    },

    _handleShowHide(e) {
      this._showOptionalLabels = !this._showOptionalLabels;
    },
  });
})();
