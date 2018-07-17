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
      requirements: {
        type: Array,
        computed: '_computeRequirements(change)',
      },
      labels: {
        type: Array,
        computed: '_computeLabels(change)',
      },
      _showWip: {
        type: Boolean,
        computed: '_computeShowWip(change)',
      },
      _showLabels: {
        type: Boolean,
        computed: '_computeShowLabelStatus(change)',
      },
    },

    behaviors: [
      Gerrit.RESTClientBehavior,
    ],

    _computeShowLabelStatus(change) {
      return change.status === this.ChangeStatus.NEW;
    },

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

      return _requirements;
    },

    _computeLabels(change) {
      const labels = change.labels;
      const _labels = [];

      for (const label in labels) {
        if (!labels.hasOwnProperty(label)) { continue; }
        const obj = labels[label];
        if (obj.optional) { continue; }

        const icon = this._computeRequirementIcon(obj.approved);
        const style = this._computeRequirementClass(obj.approved);
        _labels.push({label, icon, style});
      }

      return _labels;
    },

    _computeRequirementClass(requirementStatus) {
      if (requirementStatus) {
        return 'satisfied';
      } else {
        return 'unsatisfied';
      }
    },

    _computeRequirementIcon(requirementStatus) {
      if (requirementStatus) {
        return 'gr-icons:check';
      } else {
        return 'gr-icons:hourglass';
      }
    },

    _removeInvalidChars(text) {
      return text.replace(VALID_SELECTOR_REGEX, '');
    },
  });
})();
