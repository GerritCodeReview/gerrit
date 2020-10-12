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
import '../../shared/gr-rest-api-interface/gr-rest-api-interface';
import '../gr-create-group-dialog/gr-create-group-dialog';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-admin-group-list_html';
import {ListViewMixin} from '../../../mixins/gr-list-view-mixin/gr-list-view-mixin';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {customElement, property, observe, computed} from '@polymer/decorators';
import {AppElementAdminParams} from '../../gr-app-types';
import {GrOverlay} from '../../shared/gr-overlay/gr-overlay';
import {GroupId, GroupInfo, GroupName} from '../../../types/common';
import {RestApiService} from '../../../services/services/gr-rest-api/gr-rest-api';
import {GrCreateGroupDialog} from '../gr-create-group-dialog/gr-create-group-dialog';

declare global {
  interface HTMLElementTagNameMap {
    'gr-admin-group-list': GrAdminGroupList;
  }
}

export interface GrAdminGroupList {
  $: {
    createOverlay: GrOverlay;
    createNewModal: GrCreateGroupDialog;
    restAPI: RestApiService & Element;
  };
}

@customElement('gr-admin-group-list')
export class GrAdminGroupList extends ListViewMixin(
  GestureEventListeners(LegacyElementMixin(PolymerElement))
) {
  static get template() {
    return htmlTemplate;
  }

  @property({type: Object})
  params?: AppElementAdminParams;

  /**
   * Offset of currently visible query results.
   */
  @property({type: Number})
  _offset?: number;

  @property({type: String})
  readonly _path = '/admin/groups';

  @property({type: Boolean})
  _hasNewGroupName?: boolean;

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
    return this.computeShownItems(this._groups);
  }

  @property({type: Number})
  _groupsPerPage = 25;

  @property({type: Boolean})
  _loading = true;

  @property({type: String})
  _filter = '';

  /** @override */
  attached() {
    super.attached();
    this._getCreateGroupCapability();
    this.dispatchEvent(
      new CustomEvent('title-change', {
        detail: {title: 'Groups'},
        composed: true,
        bubbles: true,
      })
    );
    this._maybeOpenCreateOverlay(this.params);
  }

  @observe('params')
  _paramsChanged(params: AppElementAdminParams) {
    this._loading = true;
    this._filter = this.getFilterValue(params);
    this._offset = this.getOffsetValue(params);

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
    return this.$.restAPI.getAccount().then(account => {
      if (!account) {
        return;
      }
      return this.$.restAPI
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
    return this.$.restAPI
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
    this.$.restAPI.invalidateGroupsCache();
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
    this.$.createOverlay.open();
  }

  _visibleToAll(item: GroupInfo) {
    return item.options?.visible_to_all === true ? 'Y' : 'N';
  }
}
