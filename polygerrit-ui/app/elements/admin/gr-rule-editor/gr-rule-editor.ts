/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../shared/gr-autogrow-textarea/gr-autogrow-textarea';
import '../../shared/gr-button/gr-button';
import {encodeURL, getBaseUrl} from '../../../utils/url-util';
import {AccessPermissionId} from '../../../utils/access-util';
import {fire} from '../../../utils/event-util';
import {grFormStyles} from '../../../styles/gr-form-styles';
import {sharedStyles} from '../../../styles/shared-styles';
import {css, html, LitElement, PropertyValues} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {ValueChangedEvent} from '../../../types/events';
import {ifDefined} from 'lit/directives/if-defined.js';
import {EditablePermissionRuleInfo} from '../gr-repo-access/gr-repo-access-interfaces';
import {PermissionAction} from '../../../constants/constants';
import {formStyles} from '../../../styles/form-styles';
import {GrAutogrowTextarea} from '../../shared/gr-autogrow-textarea/gr-autogrow-textarea';
import '@material/web/select/outlined-select';
import '@material/web/select/select-option';
import {materialStyles} from '../../../styles/gr-material-styles';
import {convertToString} from '../../../utils/string-util';

const PRIORITY_OPTIONS = [PermissionAction.BATCH, PermissionAction.INTERACTIVE];

const Action = {
  ALLOW: PermissionAction.ALLOW,
  DENY: PermissionAction.DENY,
  BLOCK: PermissionAction.BLOCK,
};

const DROPDOWN_OPTIONS = [
  PermissionAction.ALLOW,
  PermissionAction.DENY,
  PermissionAction.BLOCK,
];

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

type Rule = {value?: EditablePermissionRuleInfo};

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
  interface HTMLElementEventMap {
    /** Fired when a rule that was previously added was removed. */
    'added-rule-removed': CustomEvent<{}>;
    'rule-changed': ValueChangedEvent<Rule | undefined>;
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
  @state() originalRuleValues?: EditablePermissionRuleInfo;

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
      grFormStyles,
      formStyles,
      sharedStyles,
      materialStyles,
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
        gr-autogrow-textarea {
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
          <md-outlined-select
            id="action"
            value=${convertToString(this.rule?.value?.action)}
            ?disabled=${!this.editing}
            @change=${this.handleActionChange}
          >
            ${this.computeOptions().map(
              item => html`
                <md-select-option value=${item}>
                  <div slot="headline">${item}</div>
                </md-select-option>
              `
            )}
          </md-outlined-select>
          ${this.renderMinAndMaxLabel()} ${this.renderMinAndMaxInput()}
          <a
            class="groupPath"
            href=${ifDefined(this.computeGroupPath(this.groupId))}
          >
            ${this.groupName}
          </a>
          <md-outlined-select
            id="force"
            class=${this.computeForce(this.rule?.value?.action) ? 'force' : ''}
            value=${convertToString(this.rule?.value?.force)}
            ?disabled=${!this.editing}
            @change=${this.handleForceChange}
          >
            ${this.computeForceOptions(this.rule?.value?.action).map(
              item => html`
                <md-select-option value=${item.value}>
                  <div slot="headline">${item.name}</div>
                </md-select-option>
              `
            )}
          </md-outlined-select>
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
      <md-outlined-select
        id="labelMin"
        value=${convertToString(this.rule?.value?.min)}
        ?disabled=${!this.editing}
        @change=${this.handleMinChange}
      >
        ${this.label.values.map(
          item => html`
            <md-select-option value=${item.value}>
              <div slot="headline">${item.value}</div>
            </md-select-option>
          `
        )}
      </md-outlined-select>
      <md-outlined-select
        id="labelMax"
        value=${convertToString(this.rule?.value?.max)}
        ?disabled=${!this.editing}
        @change=${this.handleMaxChange}
      >
        ${this.label.values.map(
          item => html`
            <md-select-option value=${item.value}>
              <div slot="headline">${item.value}</div>
            </md-select-option>
          `
        )}
      </md-outlined-select>
    `;
  }

  private renderMinAndMaxInput() {
    if (!this.hasRange) return;

    return html`
      <gr-autogrow-textarea
        id="minInput"
        class="min"
        autocomplete="on"
        placeholder="Min value"
        .value=${this.rule?.value?.min?.toString() ?? ''}
        ?disabled=${!this.editing}
        @input=${(e: InputEvent) => {
          const value = (e.target as GrAutogrowTextarea).value ?? '';
          if (
            !this.rule?.value ||
            value === undefined ||
            this.rule.value.min === Number(value)
          )
            return;
          this.rule.value.min = Number(value);

          this.handleValueChange();
        }}
      ></gr-autogrow-textarea>
      <gr-autogrow-textarea
        id="maxInput"
        class="max"
        autocomplete="on"
        placeholder="Max value"
        .value=${this.rule?.value?.max?.toString() ?? ''}
        ?disabled=${!this.editing}
        @input=${(e: InputEvent) => {
          const value = (e.target as GrAutogrowTextarea).value ?? '';
          if (
            !this.rule?.value ||
            value === undefined ||
            this.rule.value.max === Number(value)
          )
            return;
          this.rule.value.max = Number(value);

          this.handleValueChange();
        }}
      ></gr-autogrow-textarea>
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
    return `${getBaseUrl()}/admin/groups/${encodeURL(groupId)}`;
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
      }
      return [];
    } else if (this.permission === AccessPermissionId.EDIT_TOPIC_NAME) {
      return FORCE_EDIT_OPTIONS;
    }
    return [];
  }

  // private but used in test
  getDefaultRuleValues(): EditablePermissionRuleInfo {
    if (this.permission === AccessPermissionId.PRIORITY) {
      return {action: PRIORITY_OPTIONS[0]};
    }
    if (this.label) {
      return {
        action: DROPDOWN_OPTIONS[0],
        min: this.label.values[0].value,
        max: this.label.values[this.label.values.length - 1].value,
      };
    }
    if (this.computeForce(Action.ALLOW)) {
      return {
        action: DROPDOWN_OPTIONS[0],
        force: this.computeForceOptions(Action.ALLOW)[0].value,
      };
    }
    return {action: DROPDOWN_OPTIONS[0]};
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
      fire(this, 'added-rule-removed', {});
    }
    this.deleted = true;
    this.rule.value.deleted = true;

    this.handleRuleChange();

    fire(this, 'access-modified', {});
  }

  private handleUndoRemove() {
    if (!this.rule?.value) return;
    this.deleted = false;
    delete this.rule.value.deleted;

    this.handleRuleChange();
  }

  private handleUndoChange() {
    if (!this.originalRuleValues || !this.rule?.value) {
      return;
    }
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
    fire(this, 'access-modified', {});
  }

  // private but used in test
  setOriginalRuleValues() {
    if (!this.rule?.value) return;
    this.originalRuleValues = {...this.rule.value};
  }

  private handleActionChange(e: Event) {
    const select = e.target as HTMLSelectElement;
    if (
      !this.rule?.value ||
      select.value === undefined ||
      this.rule.value.action === select.value
    )
      return;

    this.rule.value.action = select.value as PermissionAction;

    this.handleValueChange();
  }

  private handleMinChange(e: Event) {
    const select = e.target as HTMLSelectElement;
    if (
      !this.rule?.value ||
      select.value === undefined ||
      this.rule.value.min === Number(select.value)
    )
      return;
    this.rule.value.min = Number(select.value);

    this.handleValueChange();
  }

  private handleMaxChange(e: Event) {
    const select = e.target as HTMLSelectElement;
    if (
      !this.rule?.value ||
      select.value === undefined ||
      this.rule.value.max === Number(select.value)
    )
      return;
    this.rule.value.max = Number(select.value);

    this.handleValueChange();
  }

  private handleForceChange(e: Event) {
    const select = e.target as HTMLSelectElement;
    const forceValue = String(select.value) === 'true' ? true : false;
    if (
      !this.rule?.value ||
      select.value === undefined ||
      this.rule.value.force === forceValue
    )
      return;
    this.rule.value.force = forceValue;

    this.handleValueChange();
  }

  private handleRuleChange() {
    this.requestUpdate('rule');
    fire(this, 'rule-changed', {value: this.rule});
  }
}
