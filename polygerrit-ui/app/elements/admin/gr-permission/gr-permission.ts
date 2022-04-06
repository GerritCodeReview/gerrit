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

import '@polymer/paper-toggle-button/paper-toggle-button';
import '../../shared/gr-autocomplete/gr-autocomplete';
import '../../shared/gr-button/gr-button';
import '../gr-rule-editor/gr-rule-editor';
import {css, html, LitElement, PropertyValues} from 'lit';
import {
  toSortedPermissionsArray,
  PermissionArrayItem,
  PermissionArray,
} from '../../../utils/access-util';
import {customElement, property, query, state} from 'lit/decorators';
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
  EditableProjectAccessGroups,
} from '../gr-repo-access/gr-repo-access-interfaces';
import {getAppContext} from '../../../services/app-context';
import {fireEvent} from '../../../utils/event-util';
import {sharedStyles} from '../../../styles/shared-styles';
import {paperStyles} from '../../../styles/gr-paper-styles';
import {formStyles} from '../../../styles/gr-form-styles';
import {menuPageStyles} from '../../../styles/gr-menu-page-styles';
import {when} from 'lit/directives/when';

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
  groups?: EditableProjectAccessGroups;

  @property({type: String})
  section?: GitRef;

  @property({type: Boolean})
  editing = false;

  @state()
  _label?: ComputedLabel;

  @state()
  _groupFilter?: string;

  @state()
  _query: AutocompleteQuery;

  @state()
  _rules?: PermissionArray<EditablePermissionRuleInfo>;

  @state()
  _groupsWithRules?: GroupsWithRulesMap;

  @state()
  _deleted = false;

  @state()
  _originalExclusiveValue?: boolean;

  @query('#groupAutocomplete')
  private groupAutocomplete!: GrAutocomplete;

  private readonly restApiService = getAppContext().restApiService;

  constructor() {
    super();
    this._query = () => this._getGroupSuggestions();
    this.addEventListener('access-saved', () => this._handleAccessSaved());
  }

  // override ready() {
  //   super.ready();
  //   this._setupValues();
  // }

  override connectedCallback() {
    super.connectedCallback();
    this._setupValues();
  }

  override willUpdate(changedProperties: PropertyValues<GrPermission>): void {
    if (changedProperties.has('editing')) {
      this._handleEditingChanged(
        this.editing,
        changedProperties.get('editing') as boolean
      );
    }
    if (
      changedProperties.has('permission') ||
      changedProperties.has('labels')
    ) {
      this._label = this._computeLabel();
    }
    if (changedProperties.has('permission')) {
      this._sortPermission(this.permission);
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
        class="gr-form-styles ${this._computeSectionClass(
          this.editing,
          this._deleted
        )}"
      >
        <div id="mainContainer">
          <div class="header">
            <span class="title">${this.name}</span>
            <div class="right">
              ${when(
                !this._permissionIsOwnerOrGlobal(
                  this.permission.id ?? '',
                  this.section
                ),
                () => html`
                  <paper-toggle-button
                    id="exclusiveToggle"
                    ?checked=${this.permission?.value.exclusive}
                    ?disabled=${!this.editing}
                    @change=${this._handleValueChange}
                    @click=${this._onTapExclusiveToggle}
                  ></paper-toggle-button
                  >${this._computeExclusiveLabel(this.permission?.value)}
                `
              )}
              <gr-button
                link=""
                id="removeBtn"
                @click=${this._handleRemovePermission}
                >Remove</gr-button
              >
            </div>
          </div>
          <!-- end header -->
          <div class="rules">
            ${this._rules?.map(
              (rule, index) => html`
                <gr-rule-editor
                  .hasRange=${this._computeHasRange(this.name)}
                  .label=${this._label}
                  .editing=${this.editing}
                  .groupId=${rule.id}
                  .groupName=${this._computeGroupName(this.groups, rule.id)}
                  .permission=${this.permission}
                  .rule=${rule}
                  .section=${this.section}
                  @rule-changed=${(e: CustomEvent) =>
                    this._handleRuleChanged(e, index)}
                  @added-rule-removed=${(_: Event) =>
                    this._handleAddedRuleRemoved(index)}
                ></gr-rule-editor>
              `
            )}
            <div id="addRule">
              <gr-autocomplete
                id="groupAutocomplete"
                .text=${this._groupFilter ?? ''}
                .query=${this._query}
                placeholder="Add group"
                @commit=${this._handleAddRuleItem}
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
          <gr-button link="" id="undoRemoveBtn" @click=${this._handleUndoRemove}
            >Undo</gr-button
          >
        </div>
        <!-- end deletedContainer -->
      </section>
    `;
  }

  _setupValues() {
    if (!this.permission) {
      return;
    }
    this._originalExclusiveValue = !!this.permission.value.exclusive;
    // flush();
  }

  _handleAccessSaved() {
    // Set a new 'original' value to keep track of after the value has been
    // saved.
    this._setupValues();
  }

  _permissionIsOwnerOrGlobal(permissionId: string, section: string) {
    return permissionId === 'owner' || section === 'GLOBAL_CAPABILITIES';
  }

  _handleEditingChanged(editing: boolean, editingOld: boolean) {
    // Ignore when editing gets set initially.
    if (!editingOld) {
      return;
    }
    if (!this.permission || !this._rules) {
      return;
    }

    // Restore original values if no longer editing.
    if (!editing) {
      this._deleted = false;
      delete this.permission.value.deleted;
      this._groupFilter = '';
      this._rules = this._rules.filter(rule => !rule.value.added);
      this._handleRulesChanged();
      for (const key of Object.keys(this.permission.value.rules)) {
        if (this.permission.value.rules[key].added) {
          delete this.permission.value.rules[key];
        }
      }

      // Restore exclusive bit to original.
      this.permission.value.exclusive = this._originalExclusiveValue;
      // this.set(
      //   ['permission', 'value', 'exclusive'],
      //   this._originalExclusiveValue
      // );
    }
  }

  _handleAddedRuleRemoved(index: number) {
    if (!this._rules) {
      return;
    }
    // const index = e.model.index;
    this._rules = this._rules
      .slice(0, index)
      .concat(this._rules.slice(index + 1, this._rules.length));
    this._handleRulesChanged();
  }

  _handleValueChange() {
    if (!this.permission) {
      return;
    }
    this.permission.value.modified = true;
    // Allows overall access page to know a change has been made.
    fireEvent(this, 'access-modified');
  }

  _handleRemovePermission() {
    if (!this.permission) {
      return;
    }
    if (this.permission.value.added) {
      fireEvent(this, 'added-permission-removed');
    }
    this._deleted = true;
    this.permission.value.deleted = true;
    fireEvent(this, 'access-modified');
  }

  // @observe('_rules.splices')
  _handleRulesChanged() {
    if (!this._rules) {
      return;
    }
    // Update the groups to exclude in the autocomplete.
    this._groupsWithRules = this._computeGroupsWithRules(this._rules);
  }

  _sortPermission(permission?: PermissionArrayItem<EditablePermissionInfo>) {
    this._rules = toSortedPermissionsArray(permission?.value.rules);
    this._handleRulesChanged();
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

  _handleUndoRemove() {
    if (!this.permission) {
      return;
    }
    this._deleted = false;
    delete this.permission.value.deleted;
  }

  _computeLabel(): // permission?: PermissionArrayItem<EditablePermissionInfo>,
  // labels?: LabelNameToLabelTypeInfoMap
  ComputedLabel | undefined {
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
      values: this._computeLabelValues(labels[labelName].values),
    };
  }

  _computeLabelValues(values: LabelTypeInfoValues): ComputedLabelValue[] {
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

  _computeGroupsWithRules(
    rules: PermissionArray<EditablePermissionRuleInfo>
  ): GroupsWithRulesMap {
    const groups: GroupsWithRulesMap = {};
    for (const rule of rules) {
      groups[rule.id] = true;
    }
    return groups;
  }

  _computeGroupName(
    groups: EditableProjectAccessGroups | undefined,
    groupId: GitRef
  ) {
    return groups && groups[groupId] && groups[groupId].name
      ? groups[groupId].name
      : groupId;
  }

  _getGroupSuggestions(): Promise<AutocompleteSuggestion[]> {
    return this.restApiService
      .getSuggestedGroups(
        this._groupFilter || '',
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
              this._groupsWithRules && !this._groupsWithRules[group.value.id]
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
  _handleAddRuleItem(e: AutocompleteCommitEvent) {
    if (!this.permission || !this._rules) {
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
    this._rules.push({
      id: groupId as GitRef,
      value: this.permission.value.rules[groupId],
    });
    // this.push('_rules', {
    //   id: groupId,
    // });

    // Add the new group name to the groups object so the name renders
    // correctly.
    if (this.groups && !this.groups[groupId]) {
      this.groups[groupId] = {name: this.groupAutocomplete.text};
    }

    // Clear the text of the auto-complete box, so that the user can add the
    // next group.
    this.groupAutocomplete.text = '';

    // Wait for new rule to get value populated via gr-rule-editor, and then
    // add to permission values as well, so that the change gets propagated
    // back to the section. Since the rule is inside a dom-repeat, a flush
    // is needed.
    // flush();
    const value = this._rules[this._rules.length - 1].value;
    value.added = true;
    // See comment above for why we cannot use "this.set(...)" here.
    this.permission.value.rules[groupId] = value;
    fireEvent(this, 'access-modified');
    // this.requestUpdate();
  }

  _computeHasRange(name?: string) {
    if (!name) {
      return false;
    }

    return RANGE_NAMES.includes(name.toUpperCase());
  }

  _computeExclusiveLabel(permission?: EditablePermissionInfo) {
    return permission?.exclusive ? 'Exclusive' : 'Not Exclusive';
  }

  /**
   * Work around a issue on iOS when clicking turns into double tap
   */
  _onTapExclusiveToggle(e: Event) {
    e.preventDefault();
  }

  _handleRuleChanged(e: CustomEvent, index: number) {
    if (
      this._rules === undefined ||
      (e as CustomEvent).detail.value === undefined
    )
      return;
    // const index = Number(e.model.index);
    if (isNaN(index)) {
      return;
    }
    this._rules.splice(index, (e as CustomEvent).detail.value);
    this._handleRulesChanged();
    this.requestUpdate();
    // this.splice('_rules', index, (e as CustomEvent).detail.value);
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-permission': GrPermission;
  }
}
