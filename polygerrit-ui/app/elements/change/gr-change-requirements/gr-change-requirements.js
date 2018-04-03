/**
 * @license
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the 'License');
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
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
      requirements: {
        type: Array,
        computed: '_computeRequirements(change)',
      },
      labels: {
        type: Array,
        computed: '_computeLabels(change)',
      },
      _showRequirements: {
        type: Boolean,
        computed: '_computeShowRequirements(requirements, labels, change)',
      },
      _showWip: {
        type: Boolean,
        computed: '_computeShowWip(change)',
      },
    },

    _computeShowWip(change) {
      return change.work_in_progress;
    },

    _computeRequirements(change) {
      const _requirements = [];

      if (change.requirements) {
        for (const requirement of change.requirements) {
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

        const status = obj.approved ? '✔' : '✘';
        const style = obj.approved ? 'ok' : 'not_ready';
        _labels.push({label, status, style});
      }

      return _labels;
    },

    _computeRequirementClass(requirement) {
      return requirement.status == 'OK' ? 'ok' : 'not_ready';
    },

    _computeShowRequirements(requirements, labels, change) {
      const hasRequirements = Object.keys(requirements).length > 0;
      const hasLabels = Object.keys(requirements).length > 0;
      return hasRequirements || hasLabels || change.work_in_progress;
    },

    _computeRequirementStatus(requirement) {
      return requirement.status == 'OK' ? '✔' : '✘';
    },
  });
})();
