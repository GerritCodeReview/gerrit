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

import '../../../styles/gr-table-styles';
import '../../../styles/shared-styles';
import '../../shared/gr-dialog/gr-dialog';
import '../../shared/gr-list-view/gr-list-view';
import '../../shared/gr-overlay/gr-overlay';
import '../gr-create-group-dialog/gr-create-group-dialog';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-admin-group-list_html';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {customElement, property, observe, computed} from '@polymer/decorators';
import {AppElementAdminParams} from '../../gr-app-types';
import {GrOverlay} from '../../shared/gr-overlay/gr-overlay';
import {GroupId, GroupInfo, GroupName} from '../../../types/common';
import {GrCreateGroupDialog} from '../gr-create-group-dialog/gr-create-group-dialog';
import {fireTitleChange} from '../../../utils/event-util';
import {appContext} from '../../../services/app-context';
import {SHOWN_ITEMS_COUNT} from '../../../constants/constants';

declare global {
  interface HTMLElementTagNameMap {
    'gr-admin-group-list': GrAdminGroupList;
  }
}

export interface GrAdminGroupList {
  $: {
    createOverlay: GrOverlay;
    createNewModal: GrCreateGroupDialog;
  };
}

@customElement('gr-admin-group-list')
export class GrAdminGroupList extends PolymerElement {
  static get template() {
    return htmlTemplate;
  }

  @property({type: Object})
  params?: AppElementAdminParams;

  /**
   * Offset of currently visible query results.
   */
  @property({type: Number})
  _offset = 0;

  @property({type: String})
  readonly _path = '/admin/groups';

  @property({type: Boolean})
  _hasNewGroupName = false;

  @property({type: Boolean})
  _createNewCapability = false;

  @property({type: Array})
  _groups: GroupInfo[] = [];

  /**
   * Because  we request one more than the groupsPerPage, _shownGroups
   * may be one less than _groups.
   * */
  @computed('_groups')
  get _shownGroups() {
    return this._groups.slice(0, SHOWN_ITEMS_COUNT);
  }

  @property({type: Number})
  _groupsPerPage = 25;

  @property({type: Boolean})
  _loading = true;

  @property({type: String})
  _filter = '';

  private readonly restApiService = appContext.restApiService;

  override connectedCallback() {
    super.connectedCallback();
    this._getCreateGroupCapability();
    fireTitleChange(this, 'Groups');
    this._maybeOpenCreateOverlay(this.params);
  }

  @observe('params')
  _paramsChanged(params: AppElementAdminParams) {
    this._loading = true;
    this._filter = params?.filter ?? '';
    this._offset = Number(params?.offset ?? 0);

    return this._getGroups(this._filter, this._groupsPerPage, this._offset);
  }

  /**
   * Opens the create overlay if the route has a hash 'create'
   */
  _maybeOpenCreateOverlay(params?: AppElementAdminParams) {
    if (params?.openCreateModal) {
      this.$.createOverlay.open();
    }
  }

  /**
   * Generates groups link (/admin/groups/<uuid>)
   */
  _computeGroupUrl(id: string) {
    return GerritNav.getUrlForGroup(decodeURIComponent(id) as GroupId);
  }

  _getCreateGroupCapability() {
    return this.restApiService.getAccount().then(account => {
      if (!account) {
        return;
      }
      return this.restApiService
        .getAccountCapabilities(['createGroup'])
        .then(capabilities => {
          if (capabilities?.createGroup) {
            this._createNewCapability = true;
          }
        });
    });
  }

  _getGroups(filter: string, groupsPerPage: number, offset?: number) {
    this._groups = [];
    return this.restApiService
      .getGroups(filter, groupsPerPage, offset)
      .then(groups => {
        if (!groups) {
          return;
        }
        this._groups = Object.keys(groups).map(key => {
          const group = groups[key];
          group.name = key as GroupName;
          return group;
        });
        this._loading = false;
      });
  }

  _refreshGroupsList() {
    this.restApiService.invalidateGroupsCache();
    return this._getGroups(this._filter, this._groupsPerPage, this._offset);
  }

  _handleCreateGroup() {
    this.$.createNewModal.handleCreateGroup().then(() => {
      this._refreshGroupsList();
    });
  }

  _handleCloseCreate() {
    this.$.createOverlay.close();
  }

  _handleCreateClicked() {
    this.$.createOverlay.open().then(() => {
      this.$.createNewModal.focus();
    });
  }

  _visibleToAll(item: GroupInfo) {
    return item.options?.visible_to_all === true ? 'Y' : 'N';
  }

  computeLoadingClass(loading: boolean) {
    return loading ? 'loading' : '';
  }
}
