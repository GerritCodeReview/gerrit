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
import '../../shared/gr-account-label/gr-account-label';
import '../../shared/gr-autocomplete/gr-autocomplete';
import '../../shared/gr-button/gr-button';
import '../../shared/gr-overlay/gr-overlay';
import '../gr-confirm-delete-item-dialog/gr-confirm-delete-item-dialog';
import {getBaseUrl} from '../../../utils/url-util';
import {GrOverlay} from '../../shared/gr-overlay/gr-overlay';
import {
  GroupId,
  AccountId,
  AccountInfo,
  GroupInfo,
  GroupName,
} from '../../../types/common';
import {
  AutocompleteQuery,
  AutocompleteSuggestion,
} from '../../shared/gr-autocomplete/gr-autocomplete';
import {
  fireAlert,
  firePageError,
  fireTitleChange,
} from '../../../utils/event-util';
import {getAppContext} from '../../../services/app-context';
import {ErrorCallback} from '../../../api/rest';
import {assertNever} from '../../../utils/common-util';
import {GrButton} from '../../shared/gr-button/gr-button';
import {fontStyles} from '../../../styles/gr-font-styles';
import {formStyles} from '../../../styles/gr-form-styles';
import {sharedStyles} from '../../../styles/shared-styles';
import {subpageStyles} from '../../../styles/gr-subpage-styles';
import {tableStyles} from '../../../styles/gr-table-styles';
import {LitElement, css, html} from 'lit';
import {customElement, property, query, state} from 'lit/decorators';
import {ifDefined} from 'lit/directives/if-defined';

const SUGGESTIONS_LIMIT = 15;
const SAVING_ERROR_TEXT =
  'Group may not exist, or you may not have ' + 'permission to add it';

const URL_REGEX = '^(?:[a-z]+:)?//';

export enum ItemType {
  MEMBER = 'member',
  INCLUDED_GROUP = 'includedGroup',
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-group-members': GrGroupMembers;
  }
}

@customElement('gr-group-members')
export class GrGroupMembers extends LitElement {
  @query('#overlay') protected overlay!: GrOverlay;

  @property({type: String})
  groupId?: GroupId;

  @state() protected groupMemberSearchId?: number;

  @state() protected groupMemberSearchName?: string;

  @state() protected includedGroupSearchId?: string;

  @state() protected includedGroupSearchName?: string;

  @state() protected loading = true;

  /* private but used in test */
  @state() groupName?: GroupName;

  @state() protected groupMembers?: AccountInfo[];

  /* private but used in test */
  @state() includedGroups?: GroupInfo[];

  /* private but used in test */
  @state() itemName?: string;

  @state() protected itemType?: ItemType;

  @state() protected queryMembers?: AutocompleteQuery;

  @state() protected queryIncludedGroup?: AutocompleteQuery;

  /* private but used in test */
  @state() groupOwner = false;

  @state() protected isAdmin = false;

  /* private but used in test */
  @state() itemId?: AccountId | GroupId;

  private readonly restApiService = getAppContext().restApiService;

  constructor() {
    super();
    this.queryMembers = input => this.getAccountSuggestions(input);
    this.queryIncludedGroup = input => this.getGroupSuggestions(input);
  }

  override connectedCallback() {
    super.connectedCallback();
    this.loadGroupDetails();

    fireTitleChange(this, 'Members');
  }

  static override get styles() {
    return [
      fontStyles,
      formStyles,
      sharedStyles,
      subpageStyles,
      tableStyles,
      css`
        .input {
          width: 15em;
        }
        gr-autocomplete {
          width: 20em;
        }
        a {
          color: var(--primary-text-color);
          text-decoration: none;
        }
        a:hover {
          text-decoration: underline;
        }
        th {
          border-bottom: 1px solid var(--border-color);
          font-weight: var(--font-weight-bold);
          text-align: left;
        }
        .canModify #groupMemberSearchInput,
        .canModify #saveGroupMember,
        .canModify .deleteHeader,
        .canModify .deleteColumn,
        .canModify #includedGroupSearchInput,
        .canModify #saveIncludedGroups,
        .canModify .deleteIncludedHeader,
        .canModify #saveIncludedGroups {
          display: none;
        }
      `,
    ];
  }

  override render() {
    return html`
      <div
        class="main gr-form-styles ${this.isAdmin || this.groupOwner
          ? ''
          : 'canModify'}"
      >
        <div id="loading" class=${this.loading ? 'loading' : ''}>
          Loading...
        </div>
        <div id="loadedContent" class=${this.loading ? 'loading' : ''}>
          <h1 id="Title" class="heading-1">${this.groupName}</h1>
          <div id="form">
            <h3 id="members" class="heading-3">Members</h3>
            <fieldset>
              <span class="value">
                <gr-autocomplete
                  id="groupMemberSearchInput"
                  .text=${this.groupMemberSearchName}
                  .value=${this.groupMemberSearchId}
                  .query=${this.queryMembers}
                  placeholder="Name Or Email"
                  @text-changed=${this.handleGroupMemberTextChanged}
                  @value-changed=${this.handleGroupMemberValueChanged}
                >
                </gr-autocomplete>
              </span>
              <gr-button
                id="saveGroupMember"
                ?disabled=${!this.groupMemberSearchId}
                @click=${this.handleSavingGroupMember}
              >
                Add
              </gr-button>
              <table id="groupMembers">
                <tbody>
                  <tr class="headerRow">
                    <th class="nameHeader">Name</th>
                    <th class="emailAddressHeader">Email Address</th>
                    <th class="deleteHeader">Delete Member</th>
                  </tr>
                </tbody>
                <tbody>
                  ${this.groupMembers?.map((member, index) =>
                    this.renderGroupMember(member, index)
                  )}
                </tbody>
              </table>
            </fieldset>
            <h3 id="includedGroups" class="heading-3">Included Groups</h3>
            <fieldset>
              <span class="value">
                <gr-autocomplete
                  id="includedGroupSearchInput"
                  .text=${this.includedGroupSearchName}
                  .value=${this.includedGroupSearchId}
                  .query=${this.queryIncludedGroup}
                  placeholder="Group Name"
                  @text-changed=${this.handleIncludedGroupTextChanged}
                  @value-changed=${this.handleIncludedGroupValueChanged}
                >
                </gr-autocomplete>
              </span>
              <gr-button
                id="saveIncludedGroups"
                ?disabled=${!this.includedGroupSearchId}
                @click=${this.handleSavingIncludedGroups}
              >
                Add
              </gr-button>
              <table id="includedGroups">
                <tbody>
                  <tr class="headerRow">
                    <th class="groupNameHeader">Group Name</th>
                    <th class="descriptionHeader">Description</th>
                    <th class="deleteIncludedHeader">Delete Group</th>
                  </tr>
                </tbody>
                <tbody>
                  ${this.includedGroups?.map((group, index) =>
                    this.renderIncludedGroup(group, index)
                  )}
                </tbody>
              </table>
            </fieldset>
          </div>
        </div>
      </div>
      <gr-overlay id="overlay" with-backdrop>
        <gr-confirm-delete-item-dialog
          class="confirmDialog"
          .item=${this.itemName}
          .itemTypeName=${this.computeItemTypeName(this.itemType)}
          @confirm=${this.handleDeleteConfirm}
          @cancel=${this.handleConfirmDialogCancel}
        ></gr-confirm-delete-item-dialog>
      </gr-overlay>
    `;
  }

  private renderGroupMember(member: AccountInfo, index: number) {
    return html`
      <tr>
        <td class="nameColumn">
          <gr-account-label .account=${member} clickable></gr-account-label>
        </td>
        <td>${member.email}</td>
        <td class="deleteColumn">
          <gr-button
            class="deleteMembersButton"
            data-index=${index}
            @click=${this.handleDeleteMember}
          >
            Delete
          </gr-button>
        </td>
      </tr>
    `;
  }

  private renderIncludedGroup(group: GroupInfo, index: number) {
    return html`
      <tr>
        <td class="nameColumn">${this.renderIncludedGroupHref(group)}</td>
        <td>${group.description}</td>
        <td class="deleteColumn">
          <gr-button
            class="deleteIncludedGroupButton"
            data-index=${index}
            @click=${this.handleDeleteIncludedGroup}
          >
            Delete
          </gr-button>
        </td>
      </tr>
    `;
  }

  private renderIncludedGroupHref(group: GroupInfo) {
    if (group.url) {
      return html`
        <a href=${ifDefined(this.computeGroupUrl(group.url))} rel="noopener">
          ${group.name}
        </a>
      `;
    }

    return group.name;
  }

  /* private but used in test */
  loadGroupDetails() {
    if (!this.groupId) return;

    const promises: Promise<void>[] = [];

    const errFn: ErrorCallback = response => {
      firePageError(response);
    };

    return this.restApiService
      .getGroupConfig(this.groupId, errFn)
      .then(config => {
        if (!config || !config.name) {
          return Promise.resolve();
        }

        this.groupName = config.name;

        promises.push(
          this.restApiService.getIsAdmin().then(isAdmin => {
            this.isAdmin = !!isAdmin;
          })
        );

        promises.push(
          this.restApiService.getIsGroupOwner(this.groupName).then(isOwner => {
            this.groupOwner = !!isOwner;
          })
        );

        promises.push(
          this.restApiService.getGroupMembers(this.groupName).then(members => {
            this.groupMembers = members;
          })
        );

        promises.push(
          this.restApiService
            .getIncludedGroup(this.groupName)
            .then(includedGroup => {
              this.includedGroups = includedGroup;
            })
        );

        return Promise.all(promises).then(() => {
          this.loading = false;
        });
      });
  }

  /* private but used in test */
  computeGroupUrl(url?: string) {
    if (!url) return;

    const r = new RegExp(URL_REGEX, 'i');
    if (r.test(url)) {
      return url;
    }

    // For GWT compatibility
    if (url.startsWith('#')) {
      return getBaseUrl() + url.slice(1);
    }
    return getBaseUrl() + url;
  }

  /* private but used in test */
  handleSavingGroupMember() {
    if (!this.groupName) {
      return Promise.reject(new Error('group name undefined'));
    }
    return this.restApiService
      .saveGroupMember(this.groupName, this.groupMemberSearchId as AccountId)
      .then(config => {
        if (!config || !this.groupName) {
          return;
        }
        this.restApiService.getGroupMembers(this.groupName).then(members => {
          this.groupMembers = members;
        });
        this.groupMemberSearchName = '';
        this.groupMemberSearchId = undefined;
      });
  }

  /* private but used in test */
  handleDeleteConfirm() {
    if (!this.groupName) {
      return Promise.reject(new Error('group name undefined'));
    }
    this.overlay.close();
    if (this.itemType === ItemType.MEMBER) {
      return this.restApiService
        .deleteGroupMember(this.groupName, this.itemId! as AccountId)
        .then(itemDeleted => {
          if (itemDeleted.status === 204 && this.groupName) {
            this.restApiService
              .getGroupMembers(this.groupName)
              .then(members => {
                this.groupMembers = members;
              });
          }
        });
    } else if (this.itemType === ItemType.INCLUDED_GROUP) {
      return this.restApiService
        .deleteIncludedGroup(this.groupName, this.itemId! as GroupId)
        .then(itemDeleted => {
          if (
            (itemDeleted.status === 204 || itemDeleted.status === 205) &&
            this.groupName
          ) {
            this.restApiService
              .getIncludedGroup(this.groupName)
              .then(includedGroup => {
                this.includedGroups = includedGroup;
              });
          }
        });
    }
    return Promise.reject(new Error('Unrecognized item type'));
  }

  /* private but used in test */
  computeItemTypeName(itemType?: ItemType): string {
    if (itemType === undefined) return '';
    switch (itemType) {
      case ItemType.INCLUDED_GROUP:
        return 'Included Group';
      case ItemType.MEMBER:
        return 'Member';
      default:
        assertNever(itemType, 'unknown item type: ${itemType}');
    }
  }

  private handleConfirmDialogCancel() {
    this.overlay.close();
  }

  private handleDeleteMember(e: Event) {
    if (!this.groupMembers) return;

    const el = e.target as GrButton;
    const index = Number(el.getAttribute('data-index')!);
    const keys = this.groupMembers[index];
    const item =
      keys.username || keys.name || keys.email || keys._account_id?.toString();
    if (!item) return;
    this.itemName = item;
    this.itemId = keys._account_id;
    this.itemType = ItemType.MEMBER;
    this.overlay.open();
  }

  /* private but used in test */
  handleSavingIncludedGroups() {
    if (!this.groupName || !this.includedGroupSearchId) {
      return Promise.reject(
        new Error('group name or includedGroupSearchId undefined')
      );
    }
    return this.restApiService
      .saveIncludedGroup(
        this.groupName,
        this.includedGroupSearchId.replace(/\+/g, ' ') as GroupId,
        (errResponse, err) => {
          if (errResponse) {
            if (errResponse.status === 404) {
              fireAlert(this, SAVING_ERROR_TEXT);
              return errResponse;
            }
            throw Error(errResponse.statusText);
          }
          throw err;
        }
      )
      .then(config => {
        if (!config || !this.groupName) {
          return;
        }
        this.restApiService
          .getIncludedGroup(this.groupName)
          .then(includedGroup => {
            this.includedGroups = includedGroup;
          });
        this.includedGroupSearchName = '';
        this.includedGroupSearchId = '';
      });
  }

  private handleDeleteIncludedGroup(e: Event) {
    if (!this.includedGroups) return;

    const el = e.target as GrButton;
    const index = Number(el.getAttribute('data-index')!);
    const keys = this.includedGroups[index];

    const id = decodeURIComponent(keys.id).replace(/\+/g, ' ') as GroupId;
    const name = keys.name;
    const item = name || id;
    if (!item) return;
    this.itemName = item;
    this.itemId = id;
    this.itemType = ItemType.INCLUDED_GROUP;
    this.overlay.open();
  }

  /* private but used in test */
  getAccountSuggestions(input: string) {
    if (input.length === 0) {
      return Promise.resolve([]);
    }
    return this.restApiService
      .getSuggestedAccounts(input, SUGGESTIONS_LIMIT)
      .then(accounts => {
        if (!accounts) return [];
        const accountSuggestions = [];
        for (const account of accounts) {
          let nameAndEmail;
          if (account.email !== undefined) {
            nameAndEmail = `${account.name} <${account.email}>`;
          } else {
            nameAndEmail = account.name;
          }
          accountSuggestions.push({
            name: nameAndEmail,
            value: account._account_id?.toString(),
          });
        }
        return accountSuggestions;
      });
  }

  /* private but used in test */
  getGroupSuggestions(input: string) {
    return this.restApiService.getSuggestedGroups(input).then(response => {
      const groups: AutocompleteSuggestion[] = [];
      for (const [name, group] of Object.entries(response ?? {})) {
        groups.push({name, value: decodeURIComponent(group.id)});
      }
      return groups;
    });
  }

  private handleGroupMemberTextChanged(e: CustomEvent) {
    if (this.loading) return;
    this.groupMemberSearchName = e.detail.value;
  }

  private handleGroupMemberValueChanged(e: CustomEvent) {
    if (this.loading) return;
    this.groupMemberSearchId = e.detail.value;
  }

  private handleIncludedGroupTextChanged(e: CustomEvent) {
    if (this.loading) return;
    this.includedGroupSearchName = e.detail.value;
  }

  private handleIncludedGroupValueChanged(e: CustomEvent) {
    if (this.loading) return;
    this.includedGroupSearchId = e.detail.value;
  }
}
