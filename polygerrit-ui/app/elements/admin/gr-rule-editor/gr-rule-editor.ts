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
import {customElement, property, state} from 'lit/decorators';
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

  // private but used in test
  @state() deleted = false;

  // private but used in test
  @state() originalRuleValues?: RuleValue;

  constructor() {
    super();
    this.addEventListener('access-saved', () => this.handleAccessSaved());
  }

  override connectedCallback() {
    super.connectedCallback();
    if (this.rule) {
      this.setupValues();
    }
    // Check needed for test purposes.
    if (!this.originalRuleValues && this.rule) {
      this.setOriginalRuleValues();
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
        class="gr-form-styles ${this.computeSectionClass()}"
      >
        <div id="options">
          <gr-select
            id="action"
            .bindValue=${this.rule?.value?.action}
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
          <a class="groupPath" href="${this.computeGroupPath(this.groupId)}">
            ${this.groupName}
          </a>
          <gr-select
            id="force"
            class="${this.computeForce(this.rule?.value?.action)
              ? 'force'
              : ''}"
            .bindValue=${this.rule?.value?.force}
            @bind-value-changed=${(e: BindValueChangeEvent) => {
              this.handleForceBindValueChanged(e);
            }}
          >
            <select ?disabled=${!this.editing}>
              ${this.computeForceOptions(this.rule?.value?.action).map(
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
            this.handleRemoveRule();
          }}
          >Remove</gr-button
        >
      </div>
      <div
        id="deletedContainer"
        class="gr-form-styles ${this.computeSectionClass()}"
      >
        ${this.groupName} was deleted
        <gr-button
          link
          id="undoRemoveBtn"
          @click=${() => {
            this.handleUndoRemove();
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
        @bind-value-changed=${(e: BindValueChangeEvent) => {
          this.handleMinBindValueChanged(e);
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
        @bind-value-changed=${(e: BindValueChangeEvent) => {
          this.handleMaxBindValueChanged(e);
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
        @bind-value-changed=${(e: BindValueChangeEvent) => {
          this.handleMinBindValueChanged(e);
        }}
      ></iron-autogrow-textarea>
      <iron-autogrow-textarea
        id="maxInput"
        class="max"
        autocomplete="on"
        placeholder="Max value"
        .bindValue=${this.rule?.value?.max}
        ?disabled=${!this.editing}
        @bind-value-changed=${(e: BindValueChangeEvent) => {
          this.handleMaxBindValueChanged(e);
        }}
      ></iron-autogrow-textarea>
    `;
  }

  override willUpdate(changedProperties: PropertyValues) {
    if (changedProperties.has('editing')) {
      this.handleEditingChanged(changedProperties.get('editing') as boolean);
    }
  }

  // private but used in test
  setupValues() {
    if (!this.rule?.value) {
      this.setDefaultRuleValues();
    }
  }

  // private but used in test
  computeForce(action?: string) {
    if (AccessPermissionId.PUSH === this.permission && action !== Action.DENY) {
      return true;
    }

    return AccessPermissionId.EDIT_TOPIC_NAME === this.permission;
  }

  // private but used in test
  computeGroupPath(groupId?: string) {
    if (!groupId) return;
    return `${getBaseUrl()}/admin/groups/${encodeURL(groupId, true)}`;
  }

  // private but used in test
  handleAccessSaved() {
    // Set a new 'original' value to keep track of after the value has been
    // saved.
    this.setOriginalRuleValues();
  }

  private handleEditingChanged(editingOld: boolean) {
    // Ignore when editing gets set initially.
    if (!editingOld) {
      return;
    }
    // Restore original values if no longer editing.
    if (!this.editing) {
      this.handleUndoChange();
    }
  }

  // private but used in test
  computeSectionClass() {
    const classList = [];
    if (this.editing) {
      classList.push('editing');
    }
    if (this.deleted) {
      classList.push('deleted');
    }
    return classList.join(' ');
  }

  // private but used in test
  computeForceOptions(action?: string) {
    if (this.permission === AccessPermissionId.PUSH) {
      if (action === Action.ALLOW) {
        return ForcePushOptions.ALLOW;
      } else if (action === Action.BLOCK) {
        return ForcePushOptions.BLOCK;
      } else {
        return [];
      }
    } else if (this.permission === AccessPermissionId.EDIT_TOPIC_NAME) {
      return FORCE_EDIT_OPTIONS;
    }
    return [];
  }

  // private but used in test
  getDefaultRuleValues() {
    const ruleAction = Action.ALLOW;
    const value: RuleValue = {};
    if (this.permission === AccessPermissionId.PRIORITY) {
      value.action = PRIORITY_OPTIONS[0];
      return value;
    } else if (this.label) {
      value.min = this.label.values[0].value;
      value.max = this.label.values[this.label.values.length - 1].value;
    } else if (this.computeForce(ruleAction)) {
      value.force = this.computeForceOptions(ruleAction)[0].value;
    }
    value.action = DROPDOWN_OPTIONS[0];
    return value;
  }

  // private but used in test
  setDefaultRuleValues() {
    this.rule!.value = this.getDefaultRuleValues();

    this.handleRuleChange();
  }

  // private but used in test
  computeOptions() {
    if (this.permission === 'priority') {
      return PRIORITY_OPTIONS;
    }
    return DROPDOWN_OPTIONS;
  }

  private handleRemoveRule() {
    if (!this.rule?.value) return;
    if (this.rule.value.added) {
      fireEvent(this, 'added-rule-removed');
    }
    this.deleted = true;
    this.rule.value.deleted = true;

    this.handleRuleChange();

    fireEvent(this, 'access-modified');
  }

  private handleUndoRemove() {
    if (!this.rule?.value) return;
    this.deleted = false;
    delete this.rule.value.deleted;

    this.handleRuleChange();
  }

  private handleUndoChange() {
    if (!this.rule?.value) return;
    // gr-permission will take care of removing rules that were added but
    // unsaved. We need to keep the added bit for the filter.
    if (this.rule.value.added) {
      return;
    }
    this.rule.value = {...this.originalRuleValues};
    this.deleted = false;
    delete this.rule.value.deleted;
    delete this.rule.value.modified;

    this.handleRuleChange();
  }

  // private but used in test
  handleValueChange() {
    if (!this.originalRuleValues || !this.rule?.value) {
      return;
    }
    this.rule.value.modified = true;

    this.handleRuleChange();

    // Allows overall access page to know a change has been made.
    fireEvent(this, 'access-modified');
  }

  // private but used in test
  setOriginalRuleValues() {
    if (!this.rule?.value) return;
    this.originalRuleValues = {...this.rule!.value};
  }

  private handleActionBindValueChanged(e: BindValueChangeEvent) {
    if (
      !this.rule?.value ||
      e.detail.value === undefined ||
      this.rule.value.action === String(e.detail.value)
    )
      return;

    this.rule.value.action = String(e.detail.value);

    this.handleValueChange();
  }

  private handleMinBindValueChanged(e: BindValueChangeEvent) {
    if (
      !this.rule?.value ||
      e.detail.value === undefined ||
      this.rule.value.min === Number(e.detail.value)
    )
      return;
    this.rule.value.min = Number(e.detail.value);

    this.handleValueChange();
  }

  private handleMaxBindValueChanged(e: BindValueChangeEvent) {
    if (
      !this.rule?.value ||
      e.detail.value === undefined ||
      this.rule.value.max === Number(e.detail.value)
    )
      return;
    this.rule.value.max = Number(e.detail.value);

    this.handleValueChange();
  }

  private handleForceBindValueChanged(e: BindValueChangeEvent) {
    const forceValue = String(e.detail.value) === 'true' ? true : false;
    if (
      !this.rule?.value ||
      e.detail.value === undefined ||
      this.rule.value.force === forceValue
    )
      return;
    this.rule.value.force = forceValue;

    this.handleValueChange();
  }

  private handleRuleChange() {
    this.requestUpdate('rule');

    this.dispatchEvent(
      new CustomEvent('rule-changed', {
        detail: {value: this.rule},
        composed: true,
        bubbles: true,
      })
    );
  }
}
