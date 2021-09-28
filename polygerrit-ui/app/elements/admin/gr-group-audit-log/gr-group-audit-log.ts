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
import '../../shared/gr-date-formatter/gr-date-formatter';
import '../../shared/gr-account-link/gr-account-link';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-group-audit-log_html';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {customElement, property} from '@polymer/decorators';
import {
  GroupInfo,
  AccountInfo,
  EncodedGroupId,
  GroupAuditEventInfo,
  GroupAuditGroupEventInfo,
  isGroupAuditGroupEventInfo,
} from '../../../types/common';
import {firePageError, fireTitleChange} from '../../../utils/event-util';
import {appContext} from '../../../services/app-context';
import {ErrorCallback} from '../../../api/rest';

@customElement('gr-group-audit-log')
export class GrGroupAuditLog extends PolymerElement {
  static get template() {
    return htmlTemplate;
  }

  @property({type: String})
  groupId?: EncodedGroupId;

  @property({type: Array})
  _auditLog?: GroupAuditEventInfo[];

  @property({type: Boolean})
  _loading = true;

  private readonly restApiService = appContext.restApiService;

  override connectedCallback() {
    super.connectedCallback();
    fireTitleChange(this, 'Audit Log');
  }

  override ready() {
    super.ready();
    this._getAuditLogs();
  }

  _getAuditLogs() {
    if (!this.groupId) {
      return '';
    }

    const errFn: ErrorCallback = response => {
      firePageError(response);
    };

    return this.restApiService
      .getGroupAuditLog(this.groupId, errFn)
      .then(auditLog => {
        if (!auditLog) {
          this._auditLog = [];
          return;
        }
        this._auditLog = auditLog;
        this._loading = false;
      });
  }

  itemType(type: string) {
    let item;
    switch (type) {
      case 'ADD_GROUP':
      case 'ADD_USER':
        item = 'Added';
        break;
      case 'REMOVE_GROUP':
      case 'REMOVE_USER':
        item = 'Removed';
        break;
      default:
        item = '';
    }
    return item;
  }

  _isGroupEvent(event: GroupAuditEventInfo): event is GroupAuditGroupEventInfo {
    return isGroupAuditGroupEventInfo(event);
  }

  _computeGroupUrl(group: GroupInfo) {
    if (group && group.url && group.id) {
      return GerritNav.getUrlForGroup(group.id);
    }

    return '';
  }

  _getIdForUser(account: AccountInfo) {
    return account._account_id ? ` (${account._account_id})` : '';
  }

  _getNameForGroup(group: GroupInfo) {
    if (group && group.name) {
      return group.name;
    } else if (group && group.id) {
      // The URL encoded id of the member
      return decodeURIComponent(group.id);
    }

    return '';
  }

  computeLoadingClass(loading: boolean) {
    return loading ? 'loading' : '';
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-group-audit-log': GrGroupAuditLog;
  }
}
