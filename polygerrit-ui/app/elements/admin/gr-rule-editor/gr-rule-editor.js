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

  /**
   * Fired when the rule has been modified or removed.
   *
   * @event access-modified
   */

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
      name: 'Block all pushes, block force push only',
      value: false,
    },
    {
      name: 'Allow fast-forward only push, allow all pushes',
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
      editing: {
        type: Boolean,
        value: false,
        observer: '_handleEditingChanged',
      },
      groupId: String,
      groupName: String,
      permission: String,
      /** @type {?} */
      rule: {
        type: Object,
        notify: true,
      },
      section: String,

      _deleted: {
        type: Boolean,
        value: false,
      },
      _originalRuleValues: Object,
    },

    behaviors: [
      Gerrit.AccessBehavior,
      Gerrit.BaseUrlBehavior,
      Gerrit.URLEncodingBehavior,
    ],

    observers: [
      '_handleValueChange(rule.value.*)',
    ],

    listeners: {
      'access-saved': '_handleAccessSaved',
    },

    ready() {
      // Called on ready rather than the observer because when new rules are
      // added, the observer is triggered prior to being ready.
      if (!this.rule) { return; } // Check needed for test purposes.
      this._setupValues(this.rule);
    },

    _setupValues(rule) {
      if (!rule.value) {
        this._setDefaultRuleValues();
      }
      this._setOriginalRuleValues(rule.value);
    },

    _computeForce(permission) {
      return this.permissionValues.push.id === permission ||
          this.permissionValues.editTopicName.id === permission;
    },

    _computeForceClass(permission) {
      return this._computeForce(permission) ? 'force' : '';
    },

    _computeGroupPath(group) {
      return `${this.getBaseUrl()}/admin/groups/${this.encodeURL(group, true)}`;
    },

    _handleAccessSaved() {
      // Set a new 'original' value to keep track of after the value has been
      // saved.
      this._setOriginalRuleValues(this.rule.value);
    },

    _handleEditingChanged(editing, editingOld) {
      // Ignore when editing gets set initially.
      if (!editingOld) { return; }
      // Restore original values if no longer editing.
      if (!editing) {
        this._handleUndoChange();
      }
    },

    _computeSectionClass(editing, deleted) {
      const classList = [];
      if (editing) {
        classList.push('editing');
      }
      if (deleted) {
        classList.push('deleted');
      }
      return classList.join(' ');
    },

    _computeForceOptions(permission) {
      if (permission === this.permissionValues.push.id) {
        return FORCE_PUSH_OPTIONS;
      } else if (permission === this.permissionValues.editTopicName.id) {
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
    },

    _computeOptions(permission) {
      if (permission === 'priority') {
        return PRIORITY_OPTIONS;
      }
      return DROPDOWN_OPTIONS;
    },

    _handleRemoveRule() {
      this._deleted = true;
      this.rule.value.deleted = true;
      this.dispatchEvent(new CustomEvent('access-modified', {bubbles: true}));
    },

    _handleUndoRemove() {
      this._deleted = false;
      delete this.rule.value.deleted;
    },

    _handleUndoChange() {
      // gr-permission will take care of removing rules that were added but
      // unsaved. We need to keep the added bit for the filter.
      if (this.rule.value.added) { return; }
      this.set('rule.value', Object.assign({}, this._originalRuleValues));
      this._deleted = false;
      delete this.rule.value.deleted;
      delete this.rule.value.modified;
    },

    _handleValueChange() {
      if (!this._originalRuleValues) { return; }
      this.rule.value.modified = true;
      // Allows overall access page to know a change has been made.
      this.dispatchEvent(new CustomEvent('access-modified', {bubbles: true}));
    },

    _setOriginalRuleValues(value) {
      this._originalRuleValues = Object.assign({}, value);
    },
  });
})();
