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
import '@polymer/iron-autogrow-textarea/iron-autogrow-textarea';
import '../../../styles/gr-form-styles';
import '../../../styles/shared-styles';
import '../../shared/gr-button/gr-button';
import '../../shared/gr-select/gr-select';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-rule-editor_html';
import {encodeURL, getBaseUrl} from '../../../utils/url-util';
import {AccessPermissionId} from '../../../utils/access-util';
import {property, customElement, observe} from '@polymer/decorators';
import {fireEvent} from '../../../utils/event-util';

/**
 * Fired when the rule has been modified or removed.
 *
 * @event access-modified
 */

/**
 * Fired when a rule that was previously added was removed.
 *
 * @event added-rule-removed
 */

const PRIORITY_OPTIONS = ['BATCH', 'INTERACTIVE'];

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

interface Rule {
  value: RuleValue;
}

interface RuleValue {
  min?: number;
  max?: number;
  force?: boolean;
  action?: string;
  added?: boolean;
  modified?: boolean;
  deleted?: boolean;
}

interface RuleLabel {
  values: RuleLabelValue[];
}

interface RuleLabelValue {
  value: number;
  text: string;
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-rule-editor': GrRuleEditor;
  }
}

@customElement('gr-rule-editor')
export class GrRuleEditor extends PolymerElement {
  static get template() {
    return htmlTemplate;
  }

  @property({type: Boolean})
  hasRange?: boolean;

  @property({type: Object})
  label?: RuleLabel;

  @property({type: Boolean, observer: '_handleEditingChanged'})
  editing = false;

  @property({type: String})
  groupId?: string;

  @property({type: String})
  groupName?: string;

  // This is required value for this component
  @property({type: String})
  permission!: AccessPermissionId;

  @property({type: Object, notify: true})
  rule?: Rule;

  @property({type: String})
  section?: string;

  @property({type: Boolean})
  _deleted = false;

  @property({type: Object})
  _originalRuleValues?: RuleValue;

  constructor() {
    super();
    this.addEventListener('access-saved', () => this._handleAccessSaved());
  }

  override ready() {
    super.ready();
    // Called on ready rather than the observer because when new rules are
    // added, the observer is triggered prior to being ready.
    if (!this.rule) {
      return;
    } // Check needed for test purposes.
    this._setupValues(this.rule);
  }

  override connectedCallback() {
    super.connectedCallback();
    // Check needed for test purposes.
    if (!this._originalRuleValues && this.rule) {
      // Observer _handleValueChange is called after the ready()
      // method finishes. Original values must be set later to
      // avoid set .modified flag to true
      this._setOriginalRuleValues(this.rule.value);
    }
  }

  _setupValues(rule: Rule) {
    if (!rule.value) {
      this._setDefaultRuleValues();
    }
  }

  _computeForce(permission: AccessPermissionId, action: string) {
    if (AccessPermissionId.PUSH === permission && action !== Action.DENY) {
      return true;
    }

    return AccessPermissionId.EDIT_TOPIC_NAME === permission;
  }

  _computeForceClass(permission: AccessPermissionId, action: string) {
    return this._computeForce(permission, action) ? 'force' : '';
  }

  _computeGroupPath(group: string) {
    return `${getBaseUrl()}/admin/groups/${encodeURL(group, true)}`;
  }

  _handleAccessSaved() {
    if (!this.rule) return;
    // Set a new 'original' value to keep track of after the value has been
    // saved.
    this._setOriginalRuleValues(this.rule.value);
  }

  _handleEditingChanged(editing: boolean, editingOld: boolean) {
    // Ignore when editing gets set initially.
    if (!editingOld) {
      return;
    }
    // Restore original values if no longer editing.
    if (!editing) {
      this._handleUndoChange();
    }
  }

  _computeSectionClass(editing: boolean, deleted: boolean) {
    const classList = [];
    if (editing) {
      classList.push('editing');
    }
    if (deleted) {
      classList.push('deleted');
    }
    return classList.join(' ');
  }

  _computeForceOptions(permission: string, action: string) {
    if (permission === AccessPermissionId.PUSH) {
      if (action === Action.ALLOW) {
        return ForcePushOptions.ALLOW;
      } else if (action === Action.BLOCK) {
        return ForcePushOptions.BLOCK;
      } else {
        return [];
      }
    } else if (permission === AccessPermissionId.EDIT_TOPIC_NAME) {
      return FORCE_EDIT_OPTIONS;
    }
    return [];
  }

  _getDefaultRuleValues(permission: AccessPermissionId, label?: RuleLabel) {
    const ruleAction = Action.ALLOW;
    const value: RuleValue = {};
    if (permission === AccessPermissionId.PRIORITY) {
      value.action = PRIORITY_OPTIONS[0];
      return value;
    } else if (label) {
      value.min = label.values[0].value;
      value.max = label.values[label.values.length - 1].value;
    } else if (this._computeForce(permission, ruleAction)) {
      value.force = this._computeForceOptions(permission, ruleAction)[0].value;
    }
    value.action = DROPDOWN_OPTIONS[0];
    return value;
  }

  _setDefaultRuleValues() {
    this.set(
      'rule.value',
      this._getDefaultRuleValues(this.permission, this.label)
    );
  }

  _computeOptions(permission: string) {
    if (permission === 'priority') {
      return PRIORITY_OPTIONS;
    }
    return DROPDOWN_OPTIONS;
  }

  _handleRemoveRule() {
    if (!this.rule) return;
    if (this.rule.value.added) {
      fireEvent(this, 'added-rule-removed');
    }
    this._deleted = true;
    this.rule.value.deleted = true;
    fireEvent(this, 'access-modified');
  }

  _handleUndoRemove() {
    if (!this.rule) return;
    this._deleted = false;
    delete this.rule.value.deleted;
  }

  _handleUndoChange() {
    if (!this.rule) return;
    // gr-permission will take care of removing rules that were added but
    // unsaved. We need to keep the added bit for the filter.
    if (this.rule.value.added) {
      return;
    }
    this.set('rule.value', {...this._originalRuleValues});
    this._deleted = false;
    delete this.rule.value.deleted;
    delete this.rule.value.modified;
  }

  @observe('rule.value.*')
  _handleValueChange() {
    if (!this._originalRuleValues || !this.rule) {
      return;
    }
    this.rule.value.modified = true;
    // Allows overall access page to know a change has been made.
    fireEvent(this, 'access-modified');
  }

  _setOriginalRuleValues(value: RuleValue) {
    this._originalRuleValues = {...value};
  }
}
