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

    /**
     * Fired when the change topic is changed.
     *
     * @event topic-changed
     */

    properties: {
      /** @type {?} */
      change: Object,
      requirements: {
        type: Array,
        computed: '_computeRequirements(change)',
      },
      _showRequirements: {
        type: Boolean,
        computed: '_computeShowRequirements(requirements)',
      },
    },

    _computeRequirements(change) {
      const _requirements = [];

      if (!!change.work_in_progress) {
        _requirements.push({fallbackText: "Work in Progress", type: "is_wip", status: "not_ready", data: {}});
      }

      const labels = this.change.labels;
      for (const label in labels) {
        if (!labels.hasOwnProperty(label)) { continue; }
        const obj = labels[label];
        if (!!obj.optional) { continue; }

        const status = obj.approved ? "ok" : "not_ready";
        _requirements.push({fallbackText: "Label " + label, type: "need_label", status: status, data: {label: label}});
      }

      if (change.requirements) {
        for (const requirement in change.requirements) {
          _requirements.push(requirement);
        }
      }

      return _requirements;
    },

    _computeShowRequirements(requirements) {
      const hasRequirements = Object.keys(requirements).length > 0;
      return hasRequirements;
    },

    _computeRequirementStatus(requirement) {
      return requirement.status == "ok" ? "✔" : "✘";
    }
  });
})();
