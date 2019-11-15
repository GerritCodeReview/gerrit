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

  const MAX_AUTOCOMPLETE_RESULTS = 20;

  const RANGE_NAMES = [
    'QUERY LIMIT',
    'BATCH CHANGES LIMIT',
  ];

  /**
    * @appliesMixin Gerrit.AccessMixin
    * @appliesMixin Gerrit.FireMixin
    */
  /**
   * Fired when the permission has been modified or removed.
   *
   * @event access-modified
   */
  /**
   * Fired when a permission that was previously added was removed.
   * @event added-permission-removed
   */
  class GrPermission extends Polymer.mixinBehaviors( [
    Gerrit.AccessBehavior,
    /**
       * Unused in this element, but called by other elements in tests
       * e.g gr-access-section_test.
       */
    Gerrit.FireBehavior,
  ], Polymer.GestureEventListeners(
      Polymer.LegacyElementMixin(
          Polymer.Element))) {
    static get is() { return 'gr-permission'; }

    static get properties() {
      return {
        labels: Object,
        name: String,
        /** @type {?} */
        permission: {
          type: Object,
          observer: '_sortPermission',
          notify: true,
        },
        groups: Object,
        section: String,
        editing: {
          type: Boolean,
          value: false,
          observer: '_handleEditingChanged',
        },
        _label: {
          type: Object,
          computed: '_computeLabel(permission, labels)',
        },
        _groupFilter: String,
        _query: {
          type: Function,
          value() {
            return this._getGroupSuggestions.bind(this);
          },
        },
        _rules: Array,
        _groupsWithRules: Object,
        _deleted: {
          type: Boolean,
          value: false,
        },
        _originalExclusiveValue: Boolean,
      };
    }

    static get observers() {
      return [
        '_handleRulesChanged(_rules.splices)',
      ];
    }

    created() {
      super.created();
      this.addEventListener('access-saved',
          () => this._handleAccessSaved());
    }

    ready() {
      super.ready();
      this._setupValues();
    }

    _setupValues() {
      if (!this.permission) { return; }
      this._originalExclusiveValue = !!this.permission.value.exclusive;
      Polymer.dom.flush();
    }

    _handleAccessSaved() {
      // Set a new 'original' value to keep track of after the value has been
      // saved.
      this._setupValues();
    }

    _permissionIsOwnerOrGlobal(permissionId, section) {
      return permissionId === 'owner' || section === 'GLOBAL_CAPABILITIES';
    }

    _handleEditingChanged(editing, editingOld) {
      // Ignore when editing gets set initially.
      if (!editingOld) { return; }
      // Restore original values if no longer editing.
      if (!editing) {
        this._deleted = false;
        delete this.permission.value.deleted;
        this._groupFilter = '';
        this._rules = this._rules.filter(rule => !rule.value.added);
        for (const key of Object.keys(this.permission.value.rules)) {
          if (this.permission.value.rules[key].added) {
            delete this.permission.value.rules[key];
          }
        }

        // Restore exclusive bit to original.
        this.set(['permission', 'value', 'exclusive'],
            this._originalExclusiveValue);
      }
    }

    _handleAddedRuleRemoved(e) {
      const index = e.model.index;
      this._rules = this._rules.slice(0, index)
          .concat(this._rules.slice(index + 1, this._rules.length));
    }

    _handleValueChange() {
      this.permission.value.modified = true;
      // Allows overall access page to know a change has been made.
      this.dispatchEvent(
          new CustomEvent('access-modified', {bubbles: true, composed: true}));
    }

    _handleRemovePermission() {
      if (this.permission.value.added) {
        this.dispatchEvent(new CustomEvent(
            'added-permission-removed', {bubbles: true, composed: true}));
      }
      this._deleted = true;
      this.permission.value.deleted = true;
      this.dispatchEvent(
          new CustomEvent('access-modified', {bubbles: true, composed: true}));
    }

    _handleRulesChanged(changeRecord) {
      // Update the groups to exclude in the autocomplete.
      this._groupsWithRules = this._computeGroupsWithRules(this._rules);
    }

    _sortPermission(permission) {
      this._rules = this.toSortedArray(permission.value.rules);
    }

    _computeSectionClass(editing, deleted) {
      const classList = [];
      if (editing) {
        classList.push('editing');
      }
      if (deleted) {
        classList.push('deleted');
      }
      return classList.join(' ');
    }

    _handleUndoRemove() {
      this._deleted = false;
      delete this.permission.value.deleted;
    }

    _computeLabel(permission, labels) {
      if (!labels || !permission ||
          !permission.value || !permission.value.label) { return; }

      const labelName = permission.value.label;

      // It is possible to have a label name that is not included in the
      // 'labels' object. In this case, treat it like anything else.
      if (!labels[labelName]) { return; }
      const label = {
        name: labelName,
        values: this._computeLabelValues(labels[labelName].values),
      };
      return label;
    }

    _computeLabelValues(values) {
      const valuesArr = [];
      const keys = Object.keys(values).sort((a, b) => {
        return parseInt(a, 10) - parseInt(b, 10);
      });

      for (const key of keys) {
        let text = values[key];
        if (!text) { text = ''; }
        // The value from the server being used to choose which item is
        // selected is in integer form, so this must be converted.
        valuesArr.push({value: parseInt(key, 10), text});
      }
      return valuesArr;
    }

    /**
     * @param {!Array} rules
     * @return {!Object} Object with groups with rues as keys, and true as
     *    value.
     */
    _computeGroupsWithRules(rules) {
      const groups = {};
      for (const rule of rules) {
        groups[rule.id] = true;
      }
      return groups;
    }

    _computeGroupName(groups, groupId) {
      return groups && groups[groupId] && groups[groupId].name ?
        groups[groupId].name : groupId;
    }

    _getGroupSuggestions() {
      return this.$.restAPI.getSuggestedGroups(
          this._groupFilter,
          MAX_AUTOCOMPLETE_RESULTS)
          .then(response => {
            const groups = [];
            for (const key in response) {
              if (!response.hasOwnProperty(key)) { continue; }
              groups.push({
                name: key,
                value: response[key],
              });
            }
            // Does not return groups in which we already have rules for.
            return groups.filter(group => {
              return !this._groupsWithRules[group.value.id];
            });
          });
    }

    /**
     * Handles adding a skeleton item to the dom-repeat.
     * gr-rule-editor handles setting the default values.
     */
    _handleAddRuleItem(e) {
      // The group id is encoded, but have to decode in order for the access
      // API to work as expected.
      const groupId = decodeURIComponent(e.detail.value.id).replace(/\+/g, ' ');
      this.set(['permission', 'value', 'rules', groupId], {});

      // Purposely don't recompute sorted array so that the newly added rule
      // is the last item of the array.
      this.push('_rules', {
        id: groupId,
      });

      // Add the new group name to the groups object so the name renders
      // correctly.
      if (this.groups && !this.groups[groupId]) {
        this.groups[groupId] = {name: this.$.groupAutocomplete.text};
      }

      // Wait for new rule to get value populated via gr-rule-editor, and then
      // add to permission values as well, so that the change gets propogated
      // back to the section. Since the rule is inside a dom-repeat, a flush
      // is needed.
      Polymer.dom.flush();
      const value = this._rules[this._rules.length - 1].value;
      value.added = true;
      this.set(['permission', 'value', 'rules', groupId], value);
      this.dispatchEvent(
          new CustomEvent('access-modified', {bubbles: true, composed: true}));
    }

    _computeHasRange(name) {
      if (!name) { return false; }

      return RANGE_NAMES.includes(name.toUpperCase());
    }
  }

  customElements.define(GrPermission.is, GrPermission);
})();
