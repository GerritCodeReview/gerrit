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
import '../../../styles/gr-form-styles';
import '../../../styles/gr-subpage-styles';
import '../../../styles/gr-table-styles';
import '../../../styles/shared-styles';
import '../../shared/gr-account-link/gr-account-link';
import '../../shared/gr-autocomplete/gr-autocomplete';
import '../../shared/gr-button/gr-button';
import '../../shared/gr-overlay/gr-overlay';
import '../gr-confirm-delete-item-dialog/gr-confirm-delete-item-dialog';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-group-members_html';
import {getBaseUrl} from '../../../utils/url-util';
import {customElement, property} from '@polymer/decorators';
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
import {PolymerDomRepeatEvent} from '../../../types/types';
import {
  fireAlert,
  firePageError,
  fireTitleChange,
} from '../../../utils/event-util';
import {appContext} from '../../../services/app-context';
import {ErrorCallback} from '../../../api/rest';

const SUGGESTIONS_LIMIT = 15;
const SAVING_ERROR_TEXT =
  'Group may not exist, or you may not have ' + 'permission to add it';

const URL_REGEX = '^(?:[a-z]+:)?//';

export interface GrGroupMembers {
  $: {
    overlay: GrOverlay;
  };
}
@customElement('gr-group-members')
export class GrGroupMembers extends GestureEventListeners(
  LegacyElementMixin(PolymerElement)
) {
  static get template() {
    return htmlTemplate;
  }

  @property({type: Number})
  groupId?: GroupId;

  @property({type: Number})
  _groupMemberSearchId?: number;

  @property({type: String})
  _groupMemberSearchName?: string;

  @property({type: String})
  _includedGroupSearchId?: string;

  @property({type: String})
  _includedGroupSearchName?: string;

  @property({type: Boolean})
  _loading = true;

  @property({type: String})
  _groupName?: GroupName;

  @property({type: Object})
  _groupMembers?: AccountInfo[];

  @property({type: Object})
  _includedGroups?: GroupInfo[];

  @property({type: String})
  _itemName?: GroupInfo | AccountInfo;

  @property({type: String})
  _itemType?: string;

  @property({type: Object})
  _queryMembers: AutocompleteQuery;

  @property({type: Object})
  _queryIncludedGroup: AutocompleteQuery;

  @property({type: Boolean})
  _groupOwner = false;

  @property({type: Boolean})
  _isAdmin = false;

  _itemId?: AccountId | GroupId;

  private readonly restApiService = appContext.restApiService;

  constructor() {
    super();
    this._queryMembers = input => this._getAccountSuggestions(input);
    this._queryIncludedGroup = input => this._getGroupSuggestions(input);
  }

  /** @override */
  attached() {
    super.attached();
    this._loadGroupDetails();

    fireTitleChange(this, 'Members');
  }

  _loadGroupDetails() {
    if (!this.groupId) {
      return;
    }

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

        this._groupName = config.name;

        promises.push(
          this.restApiService.getIsAdmin().then(isAdmin => {
            this._isAdmin = !!isAdmin;
          })
        );

        promises.push(
          this.restApiService.getIsGroupOwner(this._groupName).then(isOwner => {
            this._groupOwner = !!isOwner;
          })
        );

        promises.push(
          this.restApiService.getGroupMembers(this._groupName).then(members => {
            this._groupMembers = members;
          })
        );

        promises.push(
          this.restApiService
            .getIncludedGroup(this._groupName)
            .then(includedGroup => {
              this._includedGroups = includedGroup;
            })
        );

        return Promise.all(promises).then(() => {
          this._loading = false;
        });
      });
  }

  _computeLoadingClass(loading: boolean) {
    return loading ? 'loading' : '';
  }

  _isLoading() {
    return this._loading || this._loading === undefined;
  }

  _computeGroupUrl(url: string) {
    if (!url) {
      return;
    }

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

  _handleSavingGroupMember() {
    if (!this._groupName) {
      return Promise.reject(new Error('group name undefined'));
    }
    return this.restApiService
      .saveGroupMember(this._groupName, this._groupMemberSearchId as AccountId)
      .then(config => {
        if (!config || !this._groupName) {
          return;
        }
        this.restApiService.getGroupMembers(this._groupName).then(members => {
          this._groupMembers = members;
        });
        this._groupMemberSearchName = '';
        this._groupMemberSearchId = undefined;
      });
  }

  _handleDeleteConfirm() {
    if (!this._groupName) {
      return Promise.reject(new Error('group name undefined'));
    }
    this.$.overlay.close();
    if (this._itemType === 'member') {
      return this.restApiService
        .deleteGroupMember(this._groupName, this._itemId! as AccountId)
        .then(itemDeleted => {
          if (itemDeleted.status === 204 && this._groupName) {
            this.restApiService
              .getGroupMembers(this._groupName)
              .then(members => {
                this._groupMembers = members;
              });
          }
        });
    } else if (this._itemType === 'includedGroup') {
      return this.restApiService
        .deleteIncludedGroup(this._groupName, this._itemId! as GroupId)
        .then(itemDeleted => {
          if (
            (itemDeleted.status === 204 || itemDeleted.status === 205) &&
            this._groupName
          ) {
            this.restApiService
              .getIncludedGroup(this._groupName)
              .then(includedGroup => {
                this._includedGroups = includedGroup;
              });
          }
        });
    }
    return Promise.reject(new Error('Unrecognized item type'));
  }

  _handleConfirmDialogCancel() {
    this.$.overlay.close();
  }

  _handleDeleteMember(e: PolymerDomRepeatEvent<AccountInfo>) {
    const id = (e.model.get('item._account_id') as unknown) as AccountId;
    const name = e.model.get('item.name');
    const username = e.model.get('item.username');
    const email = e.model.get('item.email');
    const item = username || name || email || id;
    if (!item) {
      return;
    }
    this._itemName = item;
    this._itemId = id;
    this._itemType = 'member';
    this.$.overlay.open();
  }

  _handleSavingIncludedGroups() {
    if (!this._groupName || !this._includedGroupSearchId) {
      return Promise.reject(
        new Error('group name or includedGroupSearchId undefined')
      );
    }
    return this.restApiService
      .saveIncludedGroup(
        this._groupName,
        this._includedGroupSearchId.replace(/\+/g, ' ') as GroupId,
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
        if (!config || !this._groupName) {
          return;
        }
        this.restApiService
          .getIncludedGroup(this._groupName)
          .then(includedGroup => {
            this._includedGroups = includedGroup;
          });
        this._includedGroupSearchName = '';
        this._includedGroupSearchId = '';
      });
  }

  _handleDeleteIncludedGroup(e: PolymerDomRepeatEvent<GroupInfo>) {
    const id = decodeURIComponent(`${e.model.get('item.id')}`).replace(
      /\+/g,
      ' '
    ) as GroupId;
    const name = e.model.get('item.name');
    const item = name || id;
    if (!item) {
      return;
    }
    this._itemName = item;
    this._itemId = id;
    this._itemType = 'includedGroup';
    this.$.overlay.open();
  }

  _getAccountSuggestions(input: string) {
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

  _getGroupSuggestions(input: string) {
    return this.restApiService.getSuggestedGroups(input).then(response => {
      const groups: AutocompleteSuggestion[] = [];
      for (const [name, group] of Object.entries(response ?? {})) {
        groups.push({name, value: decodeURIComponent(group.id)});
      }
      return groups;
    });
  }

  _computeHideItemClass(owner: boolean, admin: boolean) {
    return admin || owner ? '' : 'canModify';
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-group-members': GrGroupMembers;
  }
}
