/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '@polymer/paper-toggle-button/paper-toggle-button';
import '../../shared/gr-autocomplete/gr-autocomplete';
import '../../shared/gr-button/gr-button';
import '../gr-rule-editor/gr-rule-editor';
import {css, html, LitElement, PropertyValues} from 'lit';
import {
  toSortedPermissionsArray,
  PermissionArrayItem,
  PermissionArray,
  AccessPermissionId,
} from '../../../utils/access-util';
import {customElement, property, query, state} from 'lit/decorators.js';
import {
  LabelNameToLabelTypeInfoMap,
  LabelTypeInfoValues,
  GroupInfo,
  GitRef,
  RepoName,
} from '../../../types/common';
import {
  AutocompleteQuery,
  GrAutocomplete,
  AutocompleteSuggestion,
  AutocompleteCommitEvent,
} from '../../shared/gr-autocomplete/gr-autocomplete';
import {
  EditablePermissionInfo,
  EditablePermissionRuleInfo,
  EditableRepoAccessGroups,
} from '../gr-repo-access/gr-repo-access-interfaces';
import {getAppContext} from '../../../services/app-context';
import {fire, fireEvent} from '../../../utils/event-util';
import {sharedStyles} from '../../../styles/shared-styles';
import {paperStyles} from '../../../styles/gr-paper-styles';
import {formStyles} from '../../../styles/gr-form-styles';
import {menuPageStyles} from '../../../styles/gr-menu-page-styles';
import {when} from 'lit/directives/when.js';
import {ValueChangedEvent} from '../../../types/events';

const MAX_AUTOCOMPLETE_RESULTS = 20;

const RANGE_NAMES = ['QUERY LIMIT', 'BATCH CHANGES LIMIT'];

type GroupsWithRulesMap = {[ruleId: string]: boolean};

interface ComputedLabelValue {
  value: number;
  text: string;
}

interface ComputedLabel {
  name: string;
  values: ComputedLabelValue[];
}

interface GroupSuggestion {
  name: string;
  value: GroupInfo;
}

/**
 * Fired when the permission has been modified or removed.
 *
 * @event access-modified
 */
/**
 * Fired when a permission that was previously added was removed.
 *
 * @event added-permission-removed
 */
@customElement('gr-permission')
export class GrPermission extends LitElement {
  @property({type: String})
  repo?: RepoName;

  @property({type: Object})
  labels?: LabelNameToLabelTypeInfoMap;

  @property({type: String})
  name?: string;

  @property({type: Object})
  permission?: PermissionArrayItem<EditablePermissionInfo>;

  @property({type: Object})
  groups?: EditableRepoAccessGroups;

  @property({type: String})
  section?: GitRef;

  @property({type: Boolean})
  editing = false;

  @state()
  private label?: ComputedLabel;

  @state()
  private groupFilter?: string;

  @state()
  private query: AutocompleteQuery;

  @state()
  rules?: PermissionArray<EditablePermissionRuleInfo | undefined>;

  @state()
  groupsWithRules?: GroupsWithRulesMap;

  @state()
  deleted = false;

  @state()
  originalExclusiveValue?: boolean;

  @query('#groupAutocomplete')
  private groupAutocomplete!: GrAutocomplete;

  private readonly restApiService = getAppContext().restApiService;

  constructor() {
    super();
    this.query = () => this.getGroupSuggestions();
    this.addEventListener('access-saved', () => this.handleAccessSaved());
  }

  override connectedCallback() {
    super.connectedCallback();
    this.setupValues();
  }

  override willUpdate(changedProperties: PropertyValues<GrPermission>): void {
    if (changedProperties.has('editing')) {
      this.handleEditingChanged(changedProperties.get('editing'));
    }
    if (
      changedProperties.has('permission') ||
      changedProperties.has('labels')
    ) {
      this.label = this.computeLabel();
    }
    if (changedProperties.has('permission')) {
      this.sortPermission(this.permission);
    }
  }

  static override styles = [
    sharedStyles,
    paperStyles,
    formStyles,
    menuPageStyles,
    css`
      :host {
        display: block;
        margin-bottom: var(--spacing-m);
      }
      .header {
        align-items: baseline;
        display: flex;
        justify-content: space-between;
        margin: var(--spacing-s) var(--spacing-m);
      }
      .rules {
        background: var(--table-header-background-color);
        border: 1px solid var(--border-color);
        border-bottom: 0;
      }
      .editing .rules {
        border-bottom: 1px solid var(--border-color);
      }
      .title {
        margin-bottom: var(--spacing-s);
      }
      #addRule,
      #removeBtn {
        display: none;
      }
      .right {
        display: flex;
        align-items: center;
      }
      .editing #removeBtn {
        display: block;
        margin-left: var(--spacing-xl);
      }
      .editing #addRule {
        display: block;
        padding: var(--spacing-m);
      }
      #deletedContainer,
      .deleted #mainContainer {
        display: none;
      }
      .deleted #deletedContainer {
        align-items: baseline;
        border: 1px solid var(--border-color);
        display: flex;
        justify-content: space-between;
        padding: var(--spacing-m);
      }
      #mainContainer {
        display: block;
      }
    `,
  ];

  override render() {
    if (!this.section || !this.permission) {
      return;
    }
    return html`
      <section
        id="permission"
        class="gr-form-styles ${this.computeSectionClass(
          this.editing,
          this.deleted
        )}"
      >
        <div id="mainContainer">
          <div class="header">
            <span class="title">${this.name}</span>
            <div class="right">
              ${when(
                !this.permissionIsOwnerOrGlobal(
                  this.permission.id ?? '',
                  this.section
                ),
                () => html`
                  <paper-toggle-button
                    id="exclusiveToggle"
                    ?checked=${this.permission?.value.exclusive}
                    ?disabled=${!this.editing}
                    @change=${this.handleValueChange}
                    @click=${this.onTapExclusiveToggle}
                  ></paper-toggle-button
                  >${this.computeExclusiveLabel(this.permission?.value)}
                `
              )}
              <gr-button
                link=""
                id="removeBtn"
                @click=${this.handleRemovePermission}
                >Remove</gr-button
              >
            </div>
          </div>
          <!-- end header -->
          <div class="rules">
            ${this.rules?.map(
              (rule, index) => html`
                <gr-rule-editor
                  .hasRange=${this.computeHasRange(this.name)}
                  .label=${this.label}
                  .editing=${this.editing}
                  .groupId=${rule.id}
                  .groupName=${this.computeGroupName(this.groups, rule.id)}
                  .permission=${this.permission!.id as AccessPermissionId}
                  .rule=${rule}
                  .section=${this.section}
                  @rule-changed=${(e: CustomEvent) =>
                    this.handleRuleChanged(e, index)}
                  @added-rule-removed=${(_: Event) =>
                    this.handleAddedRuleRemoved(index)}
                ></gr-rule-editor>
              `
            )}
            <div id="addRule">
              <gr-autocomplete
                id="groupAutocomplete"
                .text=${this.groupFilter ?? ''}
                @text-changed=${(e: ValueChangedEvent) =>
                  (this.groupFilter = e.detail.value)}
                .query=${this.query}
                placeholder="Add group"
                @commit=${this.handleAddRuleItem}
              >
              </gr-autocomplete>
            </div>
            <!-- end addRule -->
          </div>
          <!-- end rules -->
        </div>
        <!-- end mainContainer -->
        <div id="deletedContainer">
          <span>${this.name} was deleted</span>
          <gr-button link="" id="undoRemoveBtn" @click=${this.handleUndoRemove}
            >Undo</gr-button
          >
        </div>
        <!-- end deletedContainer -->
      </section>
    `;
  }

  setupValues() {
    if (!this.permission) {
      return;
    }
    this.originalExclusiveValue = !!this.permission.value.exclusive;
    this.requestUpdate();
  }

  private handleAccessSaved() {
    // Set a new 'original' value to keep track of after the value has been
    // saved.
    this.setupValues();
  }

  private permissionIsOwnerOrGlobal(permissionId: string, section: string) {
    return permissionId === 'owner' || section === 'GLOBAL_CAPABILITIES';
  }

  private handleEditingChanged(editingOld: boolean) {
    // Ignore when editing gets set initially.
    if (!editingOld) {
      return;
    }
    if (!this.permission || !this.rules) {
      return;
    }

    // Restore original values if no longer editing.
    if (!this.editing) {
      this.deleted = false;
      delete this.permission.value.deleted;
      this.groupFilter = '';
      this.rules = this.rules.filter(rule => !rule.value!.added);
      this.handleRulesChanged();
      for (const key of Object.keys(this.permission.value.rules)) {
        if (this.permission.value.rules[key].added) {
          delete this.permission.value.rules[key];
        }
      }

      // Restore exclusive bit to original.
      this.permission.value.exclusive = this.originalExclusiveValue;
      fire(this, 'permission-changed', {value: this.permission});
      this.requestUpdate();
    }
  }

  private handleAddedRuleRemoved(index: number) {
    if (!this.rules) {
      return;
    }
    this.rules = this.rules
      .slice(0, index)
      .concat(this.rules.slice(index + 1, this.rules.length));
    this.handleRulesChanged();
  }

  handleValueChange(e: Event) {
    if (!this.permission) {
      return;
    }
    this.permission.value.modified = true;
    this.permission.value.exclusive = (e.target as HTMLInputElement).checked;
    // Allows overall access page to know a change has been made.
    fireEvent(this, 'access-modified');
  }

  handleRemovePermission() {
    if (!this.permission) {
      return;
    }
    if (this.permission.value.added) {
      fireEvent(this, 'added-permission-removed');
    }
    this.deleted = true;
    this.permission.value.deleted = true;
    fireEvent(this, 'access-modified');
  }

  private handleRulesChanged() {
    if (!this.rules) {
      return;
    }
    // Update the groups to exclude in the autocomplete.
    this.groupsWithRules = this.computeGroupsWithRules(this.rules);
  }

  sortPermission(permission?: PermissionArrayItem<EditablePermissionInfo>) {
    this.rules = toSortedPermissionsArray(permission?.value.rules);
    this.handleRulesChanged();
  }

  computeSectionClass(editing: boolean, deleted: boolean) {
    const classList = [];
    if (editing) {
      classList.push('editing');
    }
    if (deleted) {
      classList.push('deleted');
    }
    return classList.join(' ');
  }

  handleUndoRemove() {
    if (!this.permission) {
      return;
    }
    this.deleted = false;
    delete this.permission.value.deleted;
  }

  computeLabel(): ComputedLabel | undefined {
    const {permission, labels} = this;
    if (
      !labels ||
      !permission ||
      !permission.value ||
      !permission.value.label
    ) {
      return;
    }

    const labelName = permission.value.label;

    // It is possible to have a label name that is not included in the
    // 'labels' object. In this case, treat it like anything else.
    if (!labels[labelName]) {
      return;
    }
    return {
      name: labelName,
      values: this.computeLabelValues(labels[labelName].values),
    };
  }

  computeLabelValues(values: LabelTypeInfoValues): ComputedLabelValue[] {
    const valuesArr: ComputedLabelValue[] = [];
    const keys = Object.keys(values).sort((a, b) => Number(a) - Number(b));

    for (const key of keys) {
      let text = values[key];
      if (!text) {
        text = '';
      }
      // The value from the server being used to choose which item is
      // selected is in integer form, so this must be converted.
      valuesArr.push({value: Number(key), text});
    }
    return valuesArr;
  }

  computeGroupsWithRules(
    rules: PermissionArray<EditablePermissionRuleInfo | undefined>
  ): GroupsWithRulesMap {
    const groups: GroupsWithRulesMap = {};
    for (const rule of rules) {
      groups[rule.id] = true;
    }
    return groups;
  }

  computeGroupName(
    groups: EditableRepoAccessGroups | undefined,
    groupId: GitRef
  ) {
    return groups && groups[groupId] && groups[groupId].name
      ? groups[groupId].name
      : groupId;
  }

  getGroupSuggestions(): Promise<AutocompleteSuggestion[]> {
    return this.restApiService
      .getSuggestedGroups(
        this.groupFilter || '',
        this.repo,
        MAX_AUTOCOMPLETE_RESULTS
      )
      .then(response => {
        const groups: GroupSuggestion[] = [];
        for (const [name, value] of Object.entries(response ?? {})) {
          groups.push({name, value});
        }
        // Does not return groups in which we already have rules for.
        return groups
          .filter(
            group =>
              this.groupsWithRules && !this.groupsWithRules[group.value.id]
          )
          .map((group: GroupSuggestion) => {
            const autocompleteSuggestion: AutocompleteSuggestion = {
              name: group.name,
              value: group.value.id,
            };
            return autocompleteSuggestion;
          });
      });
  }

  /**
   * Handles adding a skeleton item to the dom-repeat.
   * gr-rule-editor handles setting the default values.
   */
  async handleAddRuleItem(e: AutocompleteCommitEvent) {
    if (!this.permission || !this.rules) {
      return;
    }

    // The group id is encoded, but have to decode in order for the access
    // API to work as expected.
    const groupId = decodeURIComponent(e.detail.value).replace(/\+/g, ' ');
    // We cannot use "this.set(...)" here, because groupId may contain dots,
    // and dots in property path names are totally unsupported by Polymer.
    // Apparently Polymer picks up this change anyway, otherwise we should
    // have looked at using MutableData:
    // https://polymer-library.polymer-project.org/2.0/docs/devguide/data-system#mutable-data
    // Actual value assigned below, after the flush
    this.permission.value.rules[groupId] = {} as EditablePermissionRuleInfo;

    // Purposely don't recompute sorted array so that the newly added rule
    // is the last item of the array.
    this.rules.push({
      id: groupId as GitRef,
      value: undefined,
    });
    // Wait for new rule to get value populated via gr-rule-editor, and then
    // add to permission values as well, so that the change gets propagated
    // back to the section. Since the rule is inside a dom-repeat, a flush
    // is needed.
    this.requestUpdate();
    await this.updateComplete;

    // Add the new group name to the groups object so the name renders
    // correctly.
    if (this.groups && !this.groups[groupId]) {
      this.groups[groupId] = {name: this.groupAutocomplete.text};
    }

    // Clear the text of the auto-complete box, so that the user can add the
    // next group.
    this.groupAutocomplete.text = '';

    const value = this.rules[this.rules.length - 1].value;
    value!.added = true;
    this.permission.value.rules[groupId] = value!;
    fireEvent(this, 'access-modified');
    this.requestUpdate();
  }

  computeHasRange(name?: string) {
    if (!name) {
      return false;
    }

    return RANGE_NAMES.includes(name.toUpperCase());
  }

  private computeExclusiveLabel(permission?: EditablePermissionInfo) {
    return permission?.exclusive ? 'Exclusive' : 'Not Exclusive';
  }

  /**
   * Work around a issue on iOS when clicking turns into double tap
   */
  private onTapExclusiveToggle(e: Event) {
    e.preventDefault();
  }

  private handleRuleChanged(e: CustomEvent, index: number) {
    this.rules!.splice(index, e.detail.value);
    this.handleRulesChanged();
    this.requestUpdate();
  }
}

declare global {
  interface HTMLElementEventMap {
    'permission-changed': ValueChangedEvent<
      PermissionArrayItem<EditablePermissionInfo>
    >;
  }
  interface HTMLElementTagNameMap {
    'gr-permission': GrPermission;
  }
}
