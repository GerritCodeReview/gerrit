// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
(function() {
  'use strict';

  const PRIORITY_OPTIONS = [
    'BATCH',
    'INTERACTIVE',
  ];

  const DROPDOWN_OPTIONS = [
    'ALLOW',
    'DENY',
    'BLOCK',
  ];

  const FORCE_PUSH_OPTIONS = [
    {
      name: 'No Force Push',
      value: false,
    },
    {
      name: 'Force Push',
      value: true,
    },
  ];

  const FORCE_EDIT_OPTIONS = [
    {
      name: 'No Force Edit',
      value: false,
    },
    {
      name: 'Force Edit',
      value: true,
    },
  ];

  Polymer({
    is: 'gr-rule-editor',

    properties: {
      /** @type {?} */
      label: Object,
      group: String,
      permission: String,
      /** @type {?} */
      rule: Object,
      section: String,
      _modified: {
        type: Boolean,
        value: false,
      },
      _originalRuleValues: Object,
      _deleted: {
        type: Boolean,
        value: false,
      },
    },

    observers: [
      '_handleValueChange(rule.value.*)',
    ],

    _computeForce(permission) {
      return 'push' === permission || 'editTopicName' === permission;
    },

    _computeForceClass(permission) {
      return this._computeForce(permission) ? 'force' : '';
    },

    _computeDeletedClass(deleted) {
      return deleted ? 'deleted' : '';
    },

    _computeForceOptions(permission) {
      if (permission === 'push') {
        return FORCE_PUSH_OPTIONS;
      } else if (permission === 'editTopicName') {
        return FORCE_EDIT_OPTIONS;
      }
      return [];
    },

    _getDefaultRuleValues(permission, label) {
      const value = {};
      if (permission === 'priority') {
        value.action = PRIORITY_OPTIONS[0];
        return value;
      } else if (label) {
        value.min = label.values[0].value;
        value.max = label.values[label.values.length - 1].value;
      } else if (this._computeForce(permission)) {
        value.force = this._computeForceOptions(permission)[0].value;
      }
      value.action = DROPDOWN_OPTIONS[0];
      return value;
    },

    _setDefaultRuleValues() {
      this.set('rule.value',
          this._getDefaultRuleValues(this.permission, this.label));
      this._setOriginalRuleValues(this.rule.value);
    },

    _computeOptions(permission) {
      if (permission === 'priority') {
        return PRIORITY_OPTIONS;
      }
      return DROPDOWN_OPTIONS;
    },

    _handleRemoveRule() {
      this._deleted = true;
      this.rule.deleted = true;
    },

    _handleUndoRemove() {
      this._deleted = false;
      delete this.rule.deleted;
    },

    _handleUndoChange() {
      this.set('rule.value', Object.assign({}, this._originalRuleValues));
      this._modified = false;
    },

    _handleValueChange() {
      if (!this.rule.value) {
        this._setDefaultRuleValues();
      } else if (!this._originalRuleValues) {
        this._setOriginalRuleValues(this.rule.value);
      } else {
        // If it was triggered by an event, it should be modified, otherwise it
        // means it was triggered by the undo button, in which ase modified
        // should be false.
        this._modified = true;
      }
    },

    _setOriginalRuleValues(value) {
      this._originalRuleValues = Object.assign({}, value);
    },

    _computeModifiedClass(modified) {
      return modified ? 'modified' : '';
    },
  });
})();