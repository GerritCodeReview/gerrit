/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../shared/gr-dialog/gr-dialog';
import '../../shared/gr-list-view/gr-list-view';
import '../gr-create-group-dialog/gr-create-group-dialog';
import {GroupId, GroupInfo, GroupName} from '../../../types/common';
import {GrCreateGroupDialog} from '../gr-create-group-dialog/gr-create-group-dialog';
import {fireTitleChange} from '../../../utils/event-util';
import {getAppContext} from '../../../services/app-context';
import {tableStyles} from '../../../styles/gr-table-styles';
import {sharedStyles} from '../../../styles/shared-styles';
import {LitElement, PropertyValues, css, html} from 'lit';
import {customElement, query, property, state} from 'lit/decorators.js';
import {assertIsDefined} from '../../../utils/common-util';
import {
  AdminChildView,
  AdminViewState,
  createAdminUrl,
} from '../../../models/views/admin';
import {createGroupUrl} from '../../../models/views/group';
import {whenVisible} from '../../../utils/dom-util';
import {modalStyles} from '../../../styles/gr-modal-styles';

declare global {
  interface HTMLElementTagNameMap {
    'gr-admin-group-list': GrAdminGroupList;
  }
}

@customElement('gr-admin-group-list')
export class GrAdminGroupList extends LitElement {
  @query('#createModal') private createModal?: HTMLDialogElement;

  @query('#createNewModal') private createNewModal?: GrCreateGroupDialog;

  @property({type: Object})
  params?: AdminViewState;

  /**
   * Offset of currently visible query results.
   */
  @state() offset = 0;

  @state() hasNewGroupName = false;

  @state() createNewCapability = false;

  @state() groups: GroupInfo[] = [];

  @state() groupsPerPage = 25;

  @state() loading = true;

  @state() filter = '';

  private readonly restApiService = getAppContext().restApiService;

  override connectedCallback() {
    super.connectedCallback();
    this.getCreateGroupCapability();
    fireTitleChange(this, 'Groups');
  }

  static override get styles() {
    return [
      tableStyles,
      sharedStyles,
      modalStyles,
      css`
        gr-list-view {
          --generic-list-description-width: 70%;
        }
      `,
    ];
  }

  override render() {
    return html`
      <gr-list-view
        .createNew=${this.createNewCapability}
        .filter=${this.filter}
        .items=${this.groups}
        .itemsPerPage=${this.groupsPerPage}
        .loading=${this.loading}
        .offset=${this.offset}
        .path=${createAdminUrl({adminView: AdminChildView.GROUPS})}
        @create-clicked=${() => this.handleCreateClicked()}
      >
        <table id="list" class="genericList">
          <tbody>
            <tr class="headerRow">
              <th class="name topHeader">Group Name</th>
              <th class="description topHeader">Group Description</th>
              <th class="visibleToAll topHeader">Visible To All</th>
            </tr>
            <tr
              id="loading"
              class="loadingMsg ${this.loading ? 'loading' : ''}"
            >
              <td>Loading...</td>
            </tr>
          </tbody>
          <tbody class=${this.loading ? 'loading' : ''}>
            ${this.groups
              .slice(0, this.groupsPerPage)
              .map(group => this.renderGroupList(group))}
          </tbody>
        </table>
      </gr-list-view>
      <dialog id="createModal" tabindex="-1">
        <gr-dialog
          id="createDialog"
          class="confirmDialog"
          ?disabled=${!this.hasNewGroupName}
          confirm-label="Create"
          confirm-on-enter
          @confirm=${() => this.handleCreateGroup()}
          @cancel=${() => this.handleCloseCreate()}
        >
          <div class="header" slot="header">Create Group</div>
          <div class="main" slot="main">
            <gr-create-group-dialog
              id="createNewModal"
              @has-new-group-name=${this.handleHasNewGroupName}
            ></gr-create-group-dialog>
          </div>
        </gr-dialog>
      </dialog>
    `;
  }

  private renderGroupList(group: GroupInfo) {
    return html`
      <tr class="table">
        <td class="name">
          <a href=${this.computeGroupUrl(group.id)}>${group.name}</a>
        </td>
        <td class="description">${group.description}</td>
        <td class="visibleToAll">
          ${group.options?.visible_to_all === true ? 'Y' : 'N'}
        </td>
      </tr>
    `;
  }

  override willUpdate(changedProperties: PropertyValues) {
    if (changedProperties.has('params')) {
      this.paramsChanged();
    }
  }

  // private but used in test
  paramsChanged() {
    this.filter = this.params?.filter ?? '';
    this.offset = Number(this.params?.offset ?? 0);
    this.maybeOpenCreateModal(this.params);

    return this.getGroups(this.filter, this.groupsPerPage, this.offset);
  }

  /**
   * Opens the create overlay if the route has a hash 'create'
   *
   * private but used in test
   */
  async maybeOpenCreateModal(params?: AdminViewState) {
    if (params?.openCreateModal) {
      await this.updateComplete;
      if (!this.createModal?.open) this.createModal?.showModal();
    }
  }

  // private but used in test
  computeGroupUrl(encodedId: string) {
    const groupId = decodeURIComponent(encodedId) as GroupId;
    return createGroupUrl({groupId});
  }

  private getCreateGroupCapability() {
    return this.restApiService.getAccount().then(account => {
      if (!account) return;
      return this.restApiService
        .getAccountCapabilities(['createGroup'])
        .then(capabilities => {
          if (capabilities?.createGroup) {
            this.createNewCapability = true;
          }
        });
    });
  }

  private getGroups(filter: string, groupsPerPage: number, offset?: number) {
    this.groups = [];
    this.loading = true;
    return this.restApiService
      .getGroups(filter, groupsPerPage, offset)
      .then(groups => {
        if (!groups) return;
        this.groups = Object.keys(groups).map(key => {
          const group = groups[key];
          group.name = key as GroupName;
          return group;
        });
      })
      .finally(() => {
        this.loading = false;
      });
  }

  private refreshGroupsList() {
    this.restApiService.invalidateGroupsCache();
    return this.getGroups(this.filter, this.groupsPerPage, this.offset);
  }

  // private but used in test
  handleCreateGroup() {
    assertIsDefined(this.createNewModal, 'createNewModal');
    this.createNewModal.handleCreateGroup().then(() => {
      this.refreshGroupsList();
    });
  }

  // private but used in test
  handleCloseCreate() {
    assertIsDefined(this.createModal, 'createModal');
    this.createModal.close();
  }

  // private but used in test
  handleCreateClicked() {
    assertIsDefined(this.createModal, 'createModal');
    this.createModal.showModal();
    whenVisible(this.createModal, () => {
      assertIsDefined(this.createNewModal, 'createNewModal');
      this.createNewModal.focus();
    });
  }

  private handleHasNewGroupName() {
    assertIsDefined(this.createNewModal, 'createNewModal');
    this.hasNewGroupName = !!this.createNewModal.name;
  }
}
