/**
@license
Copyright (C) 2017 The Android Open Source Project

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
import '../../../../@polymer/polymer/polymer-legacy.js';

import '../../../behaviors/base-url-behavior/base-url-behavior.js';
import '../../../behaviors/gr-access-behavior/gr-access-behavior.js';
import '../../../behaviors/gr-url-encoding-behavior/gr-url-encoding-behavior.js';
import '../../../styles/gr-form-styles.js';
import '../../../styles/shared-styles.js';
import '../../shared/gr-button/gr-button.js';
import '../../shared/gr-select/gr-select.js';

/**
 * Fired when the rule has been modified or removed.
 *
 * @event access-modified
 */

/**
 * Fired when a rule that was previously added was removed.
 * @event added-rule-removed
 */

const PRIORITY_OPTIONS = [
  'BATCH',
  'INTERACTIVE',
];

const Action = {
  ALLOW: 'ALLOW',
  DENY: 'DENY',
  BLOCK: 'BLOCK',
};

const DROPDOWN_OPTIONS = [Action.ALLOW, Action.DENY, Action.BLOCK];

const ForcePushOptions = {
  ALLOW: [
    {name: 'Allow pushing (but not force pushing)', value: false},
    {name: 'Allow pushing with or without force', value: true},
  ],
  BLOCK: [
    {name: 'Block pushing with or without force', value: false},
    {name: 'Block force pushing', value: true},
  ],
};

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
  _template: Polymer.html`
    <style include="shared-styles">
      :host {
        border-bottom: 1px solid var(--border-color);
        padding: .7em;
        display: block;
      }
      #removeBtn {
        display: none;
      }
      .editing #removeBtn  {
        display: flex;
      }
      #options {
        align-items: baseline;
        display: flex;
      }
      #options > * {
        margin-right: .5em;
      }
      #mainContainer {
        align-items: baseline;
        display: flex;
        flex-wrap: nowrap;
        justify-content: space-between;
      }
      #deletedContainer.deleted {
        align-items: baseline;
        display: flex;
        justify-content: space-between;
      }
      #undoBtn,
      #force,
      #deletedContainer,
      #mainContainer.deleted {
        display: none;
      }
      #undoBtn.modified,
      #force.force {
        display: block;
      }
      .groupPath {
        color: var(--deemphasized-text-color);
      }
    </style>
    <style include="gr-form-styles"></style>
    <div id="mainContainer" class\$="gr-form-styles [[_computeSectionClass(editing, _deleted)]]">
      <div id="options">
        <gr-select id="action" bind-value="{{rule.value.action}}" on-change="_handleValueChange">
          <select disabled\$="[[!editing]]">
            <template is="dom-repeat" items="[[_computeOptions(permission)]]">
              <option value="[[item]]">[[item]]</option>
            </template>
          </select>
        </gr-select>
        <template is="dom-if" if="[[label]]">
          <gr-select id="labelMin" bind-value="{{rule.value.min}}" on-change="_handleValueChange">
            <select disabled\$="[[!editing]]">
              <template is="dom-repeat" items="[[label.values]]">
                <option value="[[item.value]]">[[item.value]]</option>
              </template>
            </select>
          </gr-select>
          <gr-select id="labelMax" bind-value="{{rule.value.max}}" on-change="_handleValueChange">
            <select disabled\$="[[!editing]]">
              <template is="dom-repeat" items="[[label.values]]">
                <option value="[[item.value]]">[[item.value]]</option>
              </template>
            </select>
          </gr-select>
        </template>
        <a class="groupPath" href\$="[[_computeGroupPath(groupId)]]">
          [[groupName]]
        </a>
        <gr-select id="force" class\$="[[_computeForceClass(permission, rule.value.action)]]" bind-value="{{rule.value.force}}" on-change="_handleValueChange">
          <select disabled\$="[[!editing]]">
            <template is="dom-repeat" items="[[_computeForceOptions(permission, rule.value.action)]]">
              <option value="[[item.value]]">[[item.name]]</option>
            </template>
          </select>
        </gr-select>
      </div>
      <gr-button link="" id="removeBtn" on-tap="_handleRemoveRule">Remove</gr-button>
    </div>
    <div id="deletedContainer" class\$="gr-form-styles [[_computeSectionClass(editing, _deleted)]]">
      [[groupName]] was deleted
      <gr-button link="" id="undoRemoveBtn" on-tap="_handleUndoRemove">Undo</gr-button>
    </div>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
`,

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

  _computeForce(permission, action) {
    if (this.permissionValues.push.id === permission &&
        action !== Action.DENY) {
      return true;
    }

    return this.permissionValues.editTopicName.id === permission;
  },

  _computeForceClass(permission, action) {
    return this._computeForce(permission, action) ? 'force' : '';
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

  _computeForceOptions(permission, action) {
    if (permission === this.permissionValues.push.id) {
      if (action === Action.ALLOW) {
        return ForcePushOptions.ALLOW;
      } else if (action === Action.BLOCK) {
        return ForcePushOptions.BLOCK;
      } else {
        return [];
      }
    } else if (permission === this.permissionValues.editTopicName.id) {
      return FORCE_EDIT_OPTIONS;
    }
    return [];
  },

  _getDefaultRuleValues(permission, label) {
    const ruleAction = Action.ALLOW;
    const value = {};
    if (permission === 'priority') {
      value.action = PRIORITY_OPTIONS[0];
      return value;
    } else if (label) {
      value.min = label.values[0].value;
      value.max = label.values[label.values.length - 1].value;
    } else if (this._computeForce(permission, ruleAction)) {
      value.force =
          this._computeForceOptions(permission, ruleAction)[0].value;
    }
    value.action = DROPDOWN_OPTIONS[0];
    return value;
  },

  _setDefaultRuleValues() {
    this.set('rule.value', this._getDefaultRuleValues(this.permission,
        this.label));
  },

  _computeOptions(permission) {
    if (permission === 'priority') {
      return PRIORITY_OPTIONS;
    }
    return DROPDOWN_OPTIONS;
  },

  _handleRemoveRule() {
    if (this.rule.value.added) {
      this.dispatchEvent(new CustomEvent('added-rule-removed',
          {bubbles: true}));
    }
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
  }
});
