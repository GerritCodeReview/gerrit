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
import '@polymer/iron-input/iron-input';
import '../../../styles/gr-font-styles';
import '../../../styles/gr-form-styles';
import '../../../styles/shared-styles';
import '../../shared/gr-button/gr-button';
import '../../shared/gr-icons/gr-icons';
import '../gr-permission/gr-permission';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-access-section_html';
import {
  AccessPermissions,
  PermissionArray,
  PermissionArrayItem,
  toSortedPermissionsArray,
} from '../../../utils/access-util';
import {customElement, property} from '@polymer/decorators';
import {
  EditablePermissionInfo,
  PermissionAccessSection,
  EditableProjectAccessGroups,
} from '../gr-repo-access/gr-repo-access-interfaces';
import {
  CapabilityInfoMap,
  GitRef,
  LabelNameToLabelTypeInfoMap,
  RepoName,
} from '../../../types/common';
import {PolymerDomRepeatEvent} from '../../../types/types';
import {fireEvent} from '../../../utils/event-util';

/**
 * Fired when the section has been modified or removed.
 *
 * @event access-modified
 */

/**
 * Fired when a section that was previously added was removed.
 *
 * @event added-section-removed
 */

const GLOBAL_NAME = 'GLOBAL_CAPABILITIES';

// The name that gets automatically input when a new reference is added.
const NEW_NAME = 'refs/heads/*';
const REFS_NAME = 'refs/';
const ON_BEHALF_OF = '(On Behalf Of)';
const LABEL = 'Label';

export interface GrAccessSection {
  $: {
    permissionSelect: HTMLSelectElement;
  };
}

@customElement('gr-access-section')
export class GrAccessSection extends PolymerElement {
  static get template() {
    return htmlTemplate;
  }

  @property({type: String})
  repo?: RepoName;

  @property({type: Object})
  capabilities?: CapabilityInfoMap;

  @property({type: Object, notify: true, observer: '_updateSection'})
  section?: PermissionAccessSection;

  @property({type: Object})
  groups?: EditableProjectAccessGroups;

  @property({type: Object})
  labels?: LabelNameToLabelTypeInfoMap;

  @property({type: Boolean, observer: '_handleEditingChanged'})
  editing = false;

  @property({type: Boolean})
  canUpload?: boolean;

  @property({type: Array})
  ownerOf?: GitRef[];

  @property({type: String})
  _originalId?: GitRef;

  @property({type: Boolean})
  _editingRef = false;

  @property({type: Boolean})
  _deleted = false;

  @property({type: Array})
  _permissions?: PermissionArray<EditablePermissionInfo>;

  constructor() {
    super();
    this.addEventListener('access-saved', () => this._handleAccessSaved());
  }

  _updateSection(section: PermissionAccessSection) {
    this._permissions = toSortedPermissionsArray(section.value.permissions);
    this._originalId = section.id;
  }

  _handleAccessSaved() {
    if (!this.section) {
      return;
    }
    // Set a new 'original' value to keep track of after the value has been
    // saved.
    this._updateSection(this.section);
  }

  _handleValueChange() {
    if (!this.section) {
      return;
    }
    if (!this.section.value.added) {
      this.section.value.modified = this.section.id !== this._originalId;
      // Allows overall access page to know a change has been made.
      // For a new section, this is not fired because new permissions and
      // rules have to be added in order to save, modifying the ref is not
      // enough.
      fireEvent(this, 'access-modified');
    }
    this.section.value.updatedId = this.section.id;
  }

  _handleEditingChanged(editing: boolean, editingOld: boolean) {
    // Ignore when editing gets set initially.
    if (!editingOld) {
      return;
    }
    if (!this.section || !this._permissions) {
      return;
    }
    // Restore original values if no longer editing.
    if (!editing) {
      this._editingRef = false;
      this._deleted = false;
      delete this.section.value.deleted;
      // Restore section ref.
      this.set(['section', 'id'], this._originalId);
      // Remove any unsaved but added permissions.
      this._permissions = this._permissions.filter(p => !p.value.added);
      for (const key of Object.keys(this.section.value.permissions)) {
        if (this.section.value.permissions[key].added) {
          delete this.section.value.permissions[key];
        }
      }
    }
  }

  _computePermissions(
    name: string,
    capabilities?: CapabilityInfoMap,
    labels?: LabelNameToLabelTypeInfoMap,
    // This is just for triggering re-computation. We don't use the value.
    _?: unknown
  ) {
    let allPermissions;
    const section = this.section;
    if (!section || !section.value) {
      return [];
    }
    if (name === GLOBAL_NAME) {
      allPermissions = toSortedPermissionsArray(capabilities);
    } else {
      const labelOptions = this._computeLabelOptions(labels);
      allPermissions = labelOptions.concat(
        toSortedPermissionsArray(AccessPermissions)
      );
    }
    return allPermissions.filter(
      permission => !section.value.permissions[permission.id]
    );
  }

  _handleAddedPermissionRemoved(e: PolymerDomRepeatEvent) {
    if (!this._permissions) {
      return;
    }
    const index = e.model.index;
    this._permissions = this._permissions
      .slice(0, index)
      .concat(this._permissions.slice(index + 1, this._permissions.length));
  }

  _computeLabelOptions(labels?: LabelNameToLabelTypeInfoMap) {
    const labelOptions = [];
    if (!labels) {
      return [];
    }
    for (const labelName of Object.keys(labels)) {
      labelOptions.push({
        id: 'label-' + labelName,
        value: {
          name: `${LABEL} ${labelName}`,
          id: 'label-' + labelName,
        },
      });
      labelOptions.push({
        id: 'labelAs-' + labelName,
        value: {
          name: `${LABEL} ${labelName} ${ON_BEHALF_OF}`,
          id: 'labelAs-' + labelName,
        },
      });
    }
    return labelOptions;
  }

  _computePermissionName(
    name: string,
    permission: PermissionArrayItem<EditablePermissionInfo>,
    capabilities?: CapabilityInfoMap
  ): string | undefined {
    if (name === GLOBAL_NAME) {
      return capabilities?.[permission.id]?.name;
    } else if (AccessPermissions[permission.id]) {
      return AccessPermissions[permission.id]?.name;
    } else if (permission.value.label) {
      let behalfOf = '';
      if (permission.id.startsWith('labelAs-')) {
        behalfOf = ON_BEHALF_OF;
      }
      return `${LABEL} ${permission.value.label}${behalfOf}`;
    }
    return undefined;
  }

  _computeSectionName(name: string) {
    // When a new section is created, it doesn't yet have a ref. Set into
    // edit mode so that the user can input one.
    if (!name) {
      this._editingRef = true;
      // Needed for the title value. This is the same default as GWT.
      name = NEW_NAME;
      // Needed for the input field value.
      this.set('section.id', name);
    }
    if (name === GLOBAL_NAME) {
      return 'Global Capabilities';
    } else if (name.startsWith(REFS_NAME)) {
      return `Reference: ${name}`;
    }
    return name;
  }

  _handleRemoveReference() {
    if (!this.section) {
      return;
    }
    if (this.section.value.added) {
      fireEvent(this, 'added-section-removed');
    }
    this._deleted = true;
    this.section.value.deleted = true;
    fireEvent(this, 'access-modified');
  }

  _handleUndoRemove() {
    if (!this.section) {
      return;
    }
    this._deleted = false;
    delete this.section.value.deleted;
  }

  editRefInput() {
    return this.root!.querySelector(
      PolymerElement
        ? 'iron-input.editRefInput'
        : 'input[is=iron-input].editRefInput'
    ) as HTMLInputElement;
  }

  editReference() {
    this._editingRef = true;
    this.editRefInput().focus();
  }

  _isEditEnabled(
    canUpload: boolean | undefined,
    ownerOf: GitRef[] | undefined,
    sectionId: GitRef
  ) {
    return canUpload || (ownerOf && ownerOf.indexOf(sectionId) >= 0);
  }

  _computeSectionClass(
    editing: boolean,
    canUpload: boolean | undefined,
    ownerOf: GitRef[] | undefined,
    editingRef: boolean,
    deleted: boolean
  ) {
    const classList = [];
    if (
      editing &&
      this.section &&
      this._isEditEnabled(canUpload, ownerOf, this.section.id)
    ) {
      classList.push('editing');
    }
    if (editingRef) {
      classList.push('editingRef');
    }
    if (deleted) {
      classList.push('deleted');
    }
    return classList.join(' ');
  }

  _computeEditBtnClass(name: string) {
    return name === GLOBAL_NAME ? 'global' : '';
  }

  _handleAddPermission() {
    const value = this.$.permissionSelect.value as GitRef;
    const permission: PermissionArrayItem<EditablePermissionInfo> = {
      id: value,
      value: {rules: {}, added: true},
    };

    // This is needed to update the 'label' property of the
    // 'label-<label-name>' permission.
    //
    // The value from the add permission dropdown will either be
    // label-<label-name> or labelAs-<labelName>.
    // But, the format of the API response is as such:
    // "permissions": {
    //  "label-Code-Review": {
    //    "label": "Code-Review",
    //    "rules": {...}
    //    }
    //  }
    // }
    // When we add a new item, we have to push the new permission in the same
    // format as the ones that have been returned by the API.
    if (value.startsWith('label')) {
      permission.value.label = value
        .replace('label-', '')
        .replace('labelAs-', '');
    }
    // Add to the end of the array (used in dom-repeat) and also to the
    // section object that is two way bound with its parent element.
    this.push('_permissions', permission);
    this.set(['section.value.permissions', permission.id], permission.value);
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-access-section': GrAccessSection;
  }
}
