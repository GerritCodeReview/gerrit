/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '@polymer/iron-input/iron-input';
import '../../shared/gr-button/gr-button';
import '../../shared/gr-icons/gr-icons';
import '../gr-permission/gr-permission';
import {
  AccessPermissions,
  PermissionArray,
  PermissionArrayItem,
  toSortedPermissionsArray,
} from '../../../utils/access-util';
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
import {fire, fireEvent} from '../../../utils/event-util';
import {IronInputElement} from '@polymer/iron-input/iron-input';
import {fontStyles} from '../../../styles/gr-font-styles';
import {formStyles} from '../../../styles/gr-form-styles';
import {sharedStyles} from '../../../styles/shared-styles';
import {LitElement, PropertyValues, html, css} from 'lit';
import {customElement, property, query, state} from 'lit/decorators';
import {BindValueChangeEvent, ValueChangedEvent} from '../../../types/events';
import {assertIsDefined, queryAndAssert} from '../../../utils/common-util';

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

@customElement('gr-access-section')
export class GrAccessSection extends LitElement {
  @query('#permissionSelect') private permissionSelect?: HTMLSelectElement;

  @property({type: String})
  repo?: RepoName;

  @property({type: Object})
  capabilities?: CapabilityInfoMap;

  @property({type: Object})
  section?: PermissionAccessSection;

  @property({type: Object})
  groups?: EditableProjectAccessGroups;

  @property({type: Object})
  labels?: LabelNameToLabelTypeInfoMap;

  @property({type: Boolean})
  editing = false;

  @property({type: Boolean})
  canUpload?: boolean;

  @property({type: Array})
  ownerOf?: GitRef[];

  // private but used in test
  @state() originalId?: GitRef;

  // private but used in test
  @state() editingRef = false;

  // private but used in test
  @state() deleted = false;

  // private but used in test
  @state() permissions?: PermissionArray<EditablePermissionInfo>;

  constructor() {
    super();
    this.addEventListener('access-saved', () => this.handleAccessSaved());
  }

  static override get styles() {
    return [
      formStyles,
      fontStyles,
      sharedStyles,
      css`
        :host {
          display: block;
          margin-bottom: var(--spacing-l);
        }
        fieldset {
          border: 1px solid var(--border-color);
        }
        .name {
          align-items: center;
          display: flex;
        }
        .header,
        #deletedContainer {
          align-items: center;
          background: var(--table-header-background-color);
          border-bottom: 1px dotted var(--border-color);
          display: flex;
          justify-content: space-between;
          min-height: 3em;
          padding: 0 var(--spacing-m);
        }
        #deletedContainer {
          border-bottom: 0;
        }
        .sectionContent {
          padding: var(--spacing-m);
        }
        #editBtn,
        .editing #editBtn.global,
        #deletedContainer,
        .deleted #mainContainer,
        #addPermission,
        #deleteBtn,
        .editingRef .name,
        .editRefInput {
          display: none;
        }
        .editing #editBtn,
        .editingRef .editRefInput {
          display: flex;
        }
        .deleted #deletedContainer {
          display: flex;
        }
        .editing #addPermission,
        #mainContainer,
        .editing #deleteBtn {
          display: block;
        }
        .editing #deleteBtn,
        #undoRemoveBtn {
          padding-right: var(--spacing-m);
        }
      `,
    ];
  }

  override render() {
    if (!this.section) return;
    return html`
      <fieldset
        id="section"
        class="gr-form-styles ${this.computeSectionClass()}"
      >
        <div id="mainContainer">
          <div class="header">
            <div class="name">
              <h3 class="heading-3">${this.computeSectionName()}</h3>
              <gr-button
                id="editBtn"
                link
                class=${this.section?.id === GLOBAL_NAME ? 'global' : ''}
                @click=${this.editReference}
              >
                <iron-icon id="icon" icon="gr-icons:create"></iron-icon>
              </gr-button>
            </div>
            <iron-input
              class="editRefInput"
              .bindValue=${this.section?.id}
              @input=${this.handleValueChange}
              @bind-value-changed=${this.handleIdBindValueChanged}
            >
              <input
                class="editRefInput"
                type="text"
                @input=${this.handleValueChange}
              />
            </iron-input>
            <gr-button link id="deleteBtn" @click=${this.handleRemoveReference}
              >Remove</gr-button
            >
          </div>
          <!-- end header -->
          <div class="sectionContent">
            ${this.permissions?.map((permission, index) =>
              this.renderPermission(permission, index)
            )}
            <div id="addPermission">
              Add permission:
              <select id="permissionSelect">
                ${this.computePermissions().map(item =>
                  this.renderPermissionOptions(item)
                )}
              </select>
              <gr-button link id="addBtn" @click=${this.handleAddPermission}
                >Add</gr-button
              >
            </div>
            <!-- end addPermission -->
          </div>
          <!-- end sectionContent -->
        </div>
        <!-- end mainContainer -->
        <div id="deletedContainer">
          <span>${this.computeSectionName()} was deleted</span>
          <gr-button link="" id="undoRemoveBtn" @click=${this._handleUndoRemove}
            >Undo</gr-button
          >
        </div>
        <!-- end deletedContainer -->
      </fieldset>
    `;
  }

  private renderPermission(
    permission: PermissionArrayItem<EditablePermissionInfo>,
    index: number
  ) {
    return html`
      <gr-permission
        .name=${this.computePermissionName(permission)}
        .permission=${permission}
        .labels=${this.labels}
        .section=${this.section?.id}
        .editing=${this.editing}
        .groups=${this.groups}
        .repo=${this.repo}
        @added-permission-removed=${() => {
          this.handleAddedPermissionRemoved(index);
        }}
        @permission-changed=${(
          e: ValueChangedEvent<PermissionArrayItem<EditablePermissionInfo>>
        ) => {
          this.handlePermissionChanged(e, index);
        }}
      >
      </gr-permission>
    `;
  }

  private renderPermissionOptions(item: {
    id: string;
    value: {name: string; id: string};
  }) {
    return html`<option value=${item.value.id}>${item.value.name}</option>`;
  }

  override willUpdate(changedProperties: PropertyValues) {
    if (changedProperties.has('section')) {
      this.updateSection();
    }
    if (changedProperties.has('editing')) {
      this.handleEditingChanged(changedProperties.get('editing') as boolean);
    }
  }

  // private but used in test
  updateSection() {
    this.permissions = toSortedPermissionsArray(
      this.section!.value.permissions
    );
    this.originalId = this.section!.id;
  }

  // private but used in test
  handleAccessSaved() {
    if (!this.section) return;
    // Set a new 'original' value to keep track of after the value has been
    // saved.
    this.updateSection();
  }

  // private but used in test
  handleValueChange() {
    if (!this.section) {
      return;
    }
    if (!this.section.value.added) {
      this.section.value.modified = this.section.id !== this.originalId;
      this.requestUpdate();
      // Allows overall access page to know a change has been made.
      // For a new section, this is not fired because new permissions and
      // rules have to be added in order to save, modifying the ref is not
      // enough.
      fireEvent(this, 'access-modified');
    }
    this.section.value.updatedId = this.section.id;
    this.requestUpdate();
  }

  private handleEditingChanged(editingOld: boolean) {
    // Ignore when editing gets set initially.
    if (!editingOld) {
      return;
    }
    if (!this.section || !this.permissions) {
      return;
    }
    // Restore original values if no longer editing.
    if (!this.editing) {
      this.editingRef = false;
      this.deleted = false;
      delete this.section.value.deleted;
      // Restore section ref.
      this.section.id = this.originalId as GitRef;
      this.requestUpdate();
      fire(this, 'section-changed', {value: this.section});
      // Remove any unsaved but added permissions.
      this.permissions = this.permissions.filter(p => !p.value.added);
      for (const key of Object.keys(this.section.value.permissions)) {
        if (this.section.value.permissions[key].added) {
          delete this.section.value.permissions[key];
        }
      }
    }
  }

  // private but used in test
  computePermissions() {
    let allPermissions;
    const section = this.section;
    if (!section || !section.value) {
      return [];
    }
    if (section.id === GLOBAL_NAME) {
      allPermissions = toSortedPermissionsArray(this.capabilities);
    } else {
      const labelOptions = this.computeLabelOptions();
      allPermissions = labelOptions.concat(
        toSortedPermissionsArray(AccessPermissions)
      );
    }
    return allPermissions.filter(
      permission => !section.value.permissions[permission.id]
    );
  }

  private handleAddedPermissionRemoved(index: number) {
    if (!this.permissions) {
      return;
    }
    this.permissions = this.permissions
      .slice(0, index)
      .concat(this.permissions.slice(index + 1, this.permissions.length));
  }

  computeLabelOptions() {
    const labelOptions = [];
    if (!this.labels) {
      return [];
    }
    for (const labelName of Object.keys(this.labels)) {
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

  // private but used in test
  computePermissionName(
    permission: PermissionArrayItem<EditablePermissionInfo>
  ): string | undefined {
    if (this.section?.id === GLOBAL_NAME) {
      return this.capabilities?.[permission.id]?.name;
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

  // private but used in test
  computeSectionName() {
    let name = this.section?.id;
    // When a new section is created, it doesn't yet have a ref. Set into
    // edit mode so that the user can input one.
    if (!name) {
      this.editingRef = true;
      // Needed for the title value. This is the same default as GWT.
      name = NEW_NAME as GitRef;
      // Needed for the input field value.
      this.section!.id = name;
      fire(this, 'section-changed', {value: this.section!});
      this.requestUpdate();
    }
    if (name === GLOBAL_NAME) {
      return 'Global Capabilities';
    } else if (name.startsWith(REFS_NAME)) {
      return `Reference: ${name}`;
    }
    return name;
  }

  private handleRemoveReference() {
    if (!this.section) {
      return;
    }
    if (this.section.value.added) {
      fireEvent(this, 'added-section-removed');
    }
    this.deleted = true;
    this.section.value.deleted = true;
    fireEvent(this, 'access-modified');
  }

  _handleUndoRemove() {
    if (!this.section) {
      return;
    }
    this.deleted = false;
    delete this.section.value.deleted;
    this.requestUpdate();
  }

  editRefInput() {
    return queryAndAssert<IronInputElement>(this, 'iron-input.editRefInput');
  }

  editReference() {
    this.editingRef = true;
    this.editRefInput().focus();
  }

  private isEditEnabled() {
    return (
      this.canUpload ||
      (this.ownerOf && this.ownerOf.indexOf(this.section!.id) >= 0)
    );
  }

  // private but used in test
  computeSectionClass() {
    const classList = [];
    if (this.editing && this.section && this.isEditEnabled()) {
      classList.push('editing');
    }
    if (this.editingRef) {
      classList.push('editingRef');
    }
    if (this.deleted) {
      classList.push('deleted');
    }
    return classList.join(' ');
  }

  // private but used in test
  handleAddPermission() {
    assertIsDefined(this.permissionSelect, 'permissionSelect');
    const value = this.permissionSelect.value as GitRef;
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
    this.permissions!.push(permission);
    this.section!.value.permissions[permission.id] = permission.value;
    this.requestUpdate();
    fire(this, 'section-changed', {value: this.section!});
  }

  private handleIdBindValueChanged = (e: BindValueChangeEvent) => {
    this.section!.id = e.detail.value as GitRef;
    this.requestUpdate();
    fire(this, 'section-changed', {value: this.section!});
  };

  private handlePermissionChanged = (
    e: ValueChangedEvent<PermissionArrayItem<EditablePermissionInfo>>,
    index: number
  ) => {
    this.permissions![index] = e.detail.value;
    this.requestUpdate();
  };
}

declare global {
  interface HTMLElementEventMap {
    'section-changed': ValueChangedEvent<PermissionAccessSection>;
  }
  interface HTMLElementTagNameMap {
    'gr-access-section': GrAccessSection;
  }
}
