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
import '../../shared/gr-button/gr-button';
import '../../shared/gr-select/gr-select';
import {encodeURL, getBaseUrl} from '../../../utils/url-util';
import {AccessPermissionId} from '../../../utils/access-util';
import {fireEvent} from '../../../utils/event-util';
import {formStyles} from '../../../styles/gr-form-styles';
import {sharedStyles} from '../../../styles/shared-styles';
import {LitElement, PropertyValues, html, css} from 'lit';
import {customElement, property /* , state*/} from 'lit/decorators';
import {BindValueChangeEvent} from '../../../types/events';

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
  value?: RuleValue;
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
export class GrRuleEditor extends LitElement {
  @property({type: Boolean})
  hasRange?: boolean;

  @property({type: Object})
  label?: RuleLabel;

  @property({type: Boolean})
  editing = false;

  @property({type: String})
  groupId?: string;

  @property({type: String})
  groupName?: string;

  // This is required value for this component
  @property({type: String})
  permission!: AccessPermissionId;

  @property({type: Object})
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

  override connectedCallback() {
    super.connectedCallback();
    if (this.rule) {
      this._setupValues(this.rule);
    }
    // Check needed for test purposes.
    if (!this._originalRuleValues && this.rule) {
      // Observer _handleValueChange is called after the ready()
      // method finishes. Original values must be set later to
      // avoid set .modified flag to true
      this._setOriginalRuleValues(this.rule?.value);
    }
  }

  static override get styles() {
    return [
      formStyles,
      sharedStyles,
      css`
        :host {
          border-bottom: 1px solid var(--border-color);
          padding: var(--spacing-m);
          display: block;
        }
        #removeBtn {
          display: none;
        }
        .editing #removeBtn {
          display: flex;
        }
        #options {
          align-items: baseline;
          display: flex;
        }
        #options > * {
          margin-right: var(--spacing-m);
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
        iron-autogrow-textarea {
          width: 14em;
        }
      `,
    ];
  }

  override render() {
    return html`
      <div
        id="mainContainer"
        class="gr-form-styles ${this._computeSectionClass(
          this.editing,
          this._deleted
        )}"
      >
        <div id="options">
          <gr-select
            id="action"
            .bindValue=${this.rule?.value?.action}
            @change=${() => {
              this._handleValueChange();
            }}
            @bind-value-changed=${(e: BindValueChangeEvent) => {
              this.handleActionBindValueChanged(e);
            }}
          >
            <select ?disabled=${!this.editing}>
              ${this.computeOptions().map(
                item => html` <option value=${item}>${item}</option> `
              )}
            </select>
          </gr-select>
          ${this.renderMinAndMaxLabel()} ${this.renderMinAndMaxInput()}
          <a class="groupPath" href="${this._computeGroupPath(this.groupId)}">
            ${this.groupName}
          </a>
          <gr-select
            id="force"
            class="${this._computeForceClass(
              this.permission,
              this.rule?.value?.action
            )}"
            .bindValue=${this.rule?.value?.force}
            @change=${() => {
              this._handleValueChange();
            }}
            bind-value-changed=${(e: BindValueChangeEvent) => {
              this.handleForceBindValueChanged(e);
            }}
          >
            <select ?disabled=${!this.editing}>
              ${this._computeForceOptions(
                this.permission,
                this.rule?.value?.action
              ).map(
                item => html`
                  <option value=${item.value}>${item.value}</option>
                `
              )}
            </select>
          </gr-select>
        </div>
        <gr-button
          link
          id="removeBtn"
          @click=${() => {
            this._handleRemoveRule();
          }}
          >Remove</gr-button
        >
      </div>
      <div
        id="deletedContainer"
        class="gr-form-styles ${this._computeSectionClass(
          this.editing,
          this._deleted
        )}"
      >
        ${this.groupName} was deleted
        <gr-button
          link
          id="undoRemoveBtn"
          @click=${() => {
            this._handleUndoRemove();
          }}
          >Undo</gr-button
        >
      </div>
    `;
  }

  private renderMinAndMaxLabel() {
    if (!this.label) return;

    return html`
      <gr-select
        id="labelMin"
        .bindValue=${this.rule?.value?.min}
        @change=${() => {
          this._handleValueChange();
        }}
        @bind-value-changed=${(e: BindValueChangeEvent) => {
          this.handleLabelMinBindValueChanged(e);
        }}
      >
        <select ?disabled=${!this.editing}>
          ${this.label.values.map(
            item => html` <option value=${item.value}>${item.value}</option> `
          )}
        </select>
      </gr-select>
      <gr-select
        id="labelMax"
        .bindValue=${this.rule?.value?.max}
        @change=${() => {
          this._handleValueChange();
        }}
        bind-value-changed=${(e: BindValueChangeEvent) => {
          this.handleLabelMaxBindValueChanged(e);
        }}
      >
        <select ?disabled=${!this.editing}>
          ${this.label.values.map(
            item => html` <option value=${item.value}>${item.value}</option> `
          )}
        </select>
      </gr-select>
    `;
  }

  private renderMinAndMaxInput() {
    if (!this.hasRange) return;

    return html`
      <iron-autogrow-textarea
        id="minInput"
        class="min"
        autocomplete="on"
        placeholder="Min value"
        .bindValue=${this.rule?.value?.min}
        ?disabled=${!this.editing}
        bind-value-changed=${(e: BindValueChangeEvent) => {
          this.handleInputMinBindValueChanged(e);
        }}
      ></iron-autogrow-textarea>
      <iron-autogrow-textarea
        id="maxInput"
        class="max"
        autocomplete="on"
        placeholder="Max value"
        .bindValue=${this.rule?.value?.max}
        ?disabled=${!this.editing}
        bind-value-changed=${(e: BindValueChangeEvent) => {
          this.handleInputMaxBindValueChanged(e);
        }}
      ></iron-autogrow-textarea>
    `;
  }

  override willUpdate(changedProperties: PropertyValues) {
    if (changedProperties.has('editing')) {
      this._handleEditingChanged(
        this.editing,
        changedProperties.get('editing') as boolean
      );
    }

    if (changedProperties.has('rule')) {
      this.handleRuleChange(this.rule, changedProperties.get('rule') as Rule);
    }
  }

  _setupValues(rule?: Rule) {
    if (!rule?.value) {
      this._setDefaultRuleValues();
    }
  }

  _computeForce(permission: AccessPermissionId, action?: string) {
    if (AccessPermissionId.PUSH === permission && action !== Action.DENY) {
      return true;
    }

    return AccessPermissionId.EDIT_TOPIC_NAME === permission;
  }

  _computeForceClass(permission: AccessPermissionId, action?: string) {
    return this._computeForce(permission, action) ? 'force' : '';
  }

  _computeGroupPath(groupId?: string) {
    if (!groupId) return;
    return `${getBaseUrl()}/admin/groups/${encodeURL(groupId, true)}`;
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

  handleRuleChange(rule?: Rule, oldRule?: Rule) {
    // Do not process if oldRule is undefined
    // or .value is undefined
    if (!rule?.value || !oldRule?.value) return;
    if (
      rule?.value?.min === oldRule?.value?.min &&
      rule?.value?.max === oldRule?.value?.max &&
      rule?.value?.force === oldRule?.value?.force &&
      rule?.value?.action === oldRule?.value?.action &&
      rule?.value?.added === oldRule?.value?.added &&
      rule?.value?.added === oldRule?.value?.added &&
      rule?.value?.modified === oldRule?.value?.modified &&
      rule?.value?.deleted === oldRule?.value?.deleted
    ) {
      return;
    }

    console.log('works');

    this._handleValueChange();
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

  _computeForceOptions(permission: string, action?: string) {
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
    this.rule!.value = this._getDefaultRuleValues(this.permission, this.label);
  }

  // private but used in test
  computeOptions() {
    if (this.permission === 'priority') {
      return PRIORITY_OPTIONS;
    }
    return DROPDOWN_OPTIONS;
  }

  _handleRemoveRule() {
    if (!this.rule?.value) return;
    if (this.rule.value.added) {
      fireEvent(this, 'added-rule-removed');
    }
    this._deleted = true;
    this.rule.value.deleted = true;

    new CustomEvent('rule-changed', {
      detail: {value: this.rule},
      composed: true,
      bubbles: true,
    });

    fireEvent(this, 'access-modified');

    this.requestUpdate();
  }

  _handleUndoRemove() {
    if (!this.rule?.value) return;
    this._deleted = false;
    delete this.rule.value.deleted;

    new CustomEvent('rule-changed', {
      detail: {value: this.rule},
      composed: true,
      bubbles: true,
    });

    this.requestUpdate();
  }

  _handleUndoChange() {
    if (!this.rule?.value) return;
    // gr-permission will take care of removing rules that were added but
    // unsaved. We need to keep the added bit for the filter.
    if (this.rule.value.added) {
      return;
    }
    this.rule.value = {...this._originalRuleValues};
    this._deleted = false;
    delete this.rule.value.deleted;
    delete this.rule.value.modified;

    new CustomEvent('rule-changed', {
      detail: {value: this.rule},
      composed: true,
      bubbles: true,
    });

    this.requestUpdate();
  }

  _handleValueChange() {
    if (!this._originalRuleValues || !this.rule?.value) {
      return;
    }
    this.rule.value.modified = true;

    new CustomEvent('rule-changed', {
      detail: {value: this.rule},
      composed: true,
      bubbles: true,
    });

    // Allows overall access page to know a change has been made.
    fireEvent(this, 'access-modified');
  }

  _setOriginalRuleValues(value?: RuleValue) {
    if (value === undefined) return;
    this._originalRuleValues = {...value};
  }

  private handleActionBindValueChanged(e: BindValueChangeEvent) {
    if (!this.rule?.value || e.detail.value === undefined) return;
    if (this.rule.value.action === e.detail.value) return;
    this.rule.value.action = e.detail.value;

    new CustomEvent('rule-changed', {
      detail: {value: this.rule},
      composed: true,
      bubbles: true,
    });

    this.requestUpdate();
  }

  private handleLabelMinBindValueChanged(e: BindValueChangeEvent) {
    if (!this.rule?.value || e.detail.value === undefined) return;
    if (this.rule.value.min === Number(e.detail.value)) return;
    this.rule.value.min = Number(e.detail.value);

    new CustomEvent('rule-changed', {
      detail: {value: this.rule},
      composed: true,
      bubbles: true,
    });

    this.requestUpdate();
  }

  private handleLabelMaxBindValueChanged(e: BindValueChangeEvent) {
    if (!this.rule?.value || e.detail.value === undefined) return;
    if (this.rule.value.max === Number(e.detail.value)) return;
    this.rule.value.max = Number(e.detail.value);

    new CustomEvent('rule-changed', {
      detail: {value: this.rule},
      composed: true,
      bubbles: true,
    });

    this.requestUpdate();
  }

  private handleInputMinBindValueChanged(e: BindValueChangeEvent) {
    if (!this.rule?.value || e.detail.value === undefined) return;
    if (this.rule.value.min === Number(e.detail.value)) return;
    this.rule.value.min = Number(e.detail.value);

    new CustomEvent('rule-changed', {
      detail: {value: this.rule},
      composed: true,
      bubbles: true,
    });

    this.requestUpdate();
  }

  private handleInputMaxBindValueChanged(e: BindValueChangeEvent) {
    if (!this.rule?.value || e.detail.value === undefined) return;
    if (this.rule.value.max === Number(e.detail.value)) return;
    this.rule.value.max = Number(e.detail.value);

    new CustomEvent('rule-changed', {
      detail: {value: this.rule},
      composed: true,
      bubbles: true,
    });

    this.requestUpdate();
  }

  private handleForceBindValueChanged(e: BindValueChangeEvent) {
    if (!this.rule?.value || e.detail.value === undefined) return;
    const value = e.detail.value === 'true' ? true : false;
    if (this.rule.value.force === value) return;
    this.rule.value.force = value;

    new CustomEvent('rule-changed', {
      detail: {value: this.rule},
      composed: true,
      bubbles: true,
    });

    this.requestUpdate();
  }
}
