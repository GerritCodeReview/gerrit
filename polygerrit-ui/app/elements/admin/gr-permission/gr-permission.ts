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
import '../../../styles/gr-form-styles';
import '../../../styles/gr-menu-page-styles';
import '../../../styles/gr-paper-styles';
import '../../../styles/shared-styles';
import '../../shared/gr-autocomplete/gr-autocomplete';
import '../../shared/gr-button/gr-button';
import '../gr-rule-editor/gr-rule-editor';
import {flush} from '@polymer/polymer/lib/legacy/polymer.dom';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-permission_html';
import {
  toSortedPermissionsArray,
  PermissionArrayItem,
  PermissionArray,
} from '../../../utils/access-util';
import {customElement, property, observe} from '@polymer/decorators';
import {
  LabelNameToLabelTypeInfoMap,
  LabelTypeInfoValues,
  GroupInfo,
  ProjectAccessGroups,
  GroupId,
  GitRef,
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
import {PolymerDomRepeatEvent} from '../../../types/types';
import {appContext} from '../../../services/app-context';
import {fireEvent} from '../../../utils/event-util';

const MAX_AUTOCOMPLETE_RESULTS = 20;

const RANGE_NAMES = ['QUERY LIMIT', 'BATCH CHANGES LIMIT'];

type GroupsWithRulesMap = {[ruleId: string]: boolean};

export interface GrPermission {
  $: {
    groupAutocomplete: GrAutocomplete;
  };
}

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
export class GrPermission extends PolymerElement {
  static get template() {
    return htmlTemplate;
  }

  @property({type: Object})
  labels?: LabelNameToLabelTypeInfoMap;

  @property({type: String})
  name?: string;

  @property({type: Object, observer: '_sortPermission', notify: true})
  permission?: PermissionArrayItem<EditablePermissionInfo>;

  @property({type: Object})
  groups?: EditableProjectAccessGroups;

  @property({type: String})
  section?: GitRef;

  @property({type: Boolean, observer: '_handleEditingChanged'})
  editing = false;

  @property({type: Object, computed: '_computeLabel(permission, labels)'})
  _label?: ComputedLabel;

  @property({type: String})
  _groupFilter?: string;

  @property({type: Object})
  _query: AutocompleteQuery;

  @property({type: Array})
  _rules?: PermissionArray<EditablePermissionRuleInfo>;

  @property({type: Object})
  _groupsWithRules?: GroupsWithRulesMap;

  @property({type: Boolean})
  _deleted = false;

  @property({type: Boolean})
  _originalExclusiveValue?: boolean;

  private readonly restApiService = appContext.restApiService;

  constructor() {
    super();
    this._query = () => this._getGroupSuggestions();
    this.addEventListener('access-saved', () => this._handleAccessSaved());
  }

  override ready() {
    super.ready();
    this._setupValues();
  }

  _setupValues() {
    if (!this.permission) {
      return;
    }
    this._originalExclusiveValue = !!this.permission.value.exclusive;
    flush();
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
      for (const key of Object.keys(this.permission.value.rules)) {
        if (this.permission.value.rules[key].added) {
          delete this.permission.value.rules[key];
        }
      }

      // Restore exclusive bit to original.
      this.set(
        ['permission', 'value', 'exclusive'],
        this._originalExclusiveValue
      );
    }
  }

  _handleAddedRuleRemoved(e: PolymerDomRepeatEvent) {
    if (!this._rules) {
      return;
    }
    const index = e.model.index;
    this._rules = this._rules
      .slice(0, index)
      .concat(this._rules.slice(index + 1, this._rules.length));
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

  @observe('_rules.splices')
  _handleRulesChanged() {
    if (!this._rules) {
      return;
    }
    // Update the groups to exclude in the autocomplete.
    this._groupsWithRules = this._computeGroupsWithRules(this._rules);
  }

  _sortPermission(permission: PermissionArrayItem<EditablePermissionInfo>) {
    this._rules = toSortedPermissionsArray(permission.value.rules);
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

  _computeLabel(
    permission?: PermissionArrayItem<EditablePermissionInfo>,
    labels?: LabelNameToLabelTypeInfoMap
  ): ComputedLabel | undefined {
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

  _computeGroupName(groups: ProjectAccessGroups, groupId: GroupId) {
    return groups && groups[groupId] && groups[groupId].name
      ? groups[groupId].name
      : groupId;
  }

  _getGroupSuggestions(): Promise<AutocompleteSuggestion[]> {
    return this.restApiService
      .getSuggestedGroups(this._groupFilter || '', MAX_AUTOCOMPLETE_RESULTS)
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
    this.push('_rules', {
      id: groupId,
    });

    // Add the new group name to the groups object so the name renders
    // correctly.
    if (this.groups && !this.groups[groupId]) {
      this.groups[groupId] = {name: this.$.groupAutocomplete.text};
    }

    // Clear the text of the auto-complete box, so that the user can add the
    // next group.
    this.$.groupAutocomplete.text = '';

    // Wait for new rule to get value populated via gr-rule-editor, and then
    // add to permission values as well, so that the change gets propagated
    // back to the section. Since the rule is inside a dom-repeat, a flush
    // is needed.
    flush();
    const value = this._rules[this._rules.length - 1].value;
    value.added = true;
    // See comment above for why we cannot use "this.set(...)" here.
    this.permission.value.rules[groupId] = value;
    fireEvent(this, 'access-modified');
  }

  _computeHasRange(name: string) {
    if (!name) {
      return false;
    }

    return RANGE_NAMES.includes(name.toUpperCase());
  }

  /**
   * Work around a issue on iOS when clicking turns into double tap
   */
  _onTapExclusiveToggle(e: Event) {
    e.preventDefault();
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-permission': GrPermission;
  }
}
