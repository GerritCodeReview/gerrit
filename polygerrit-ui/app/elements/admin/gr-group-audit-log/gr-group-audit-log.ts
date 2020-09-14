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

import '../../../styles/gr-table-styles.js';
import '../../../styles/shared-styles.js';
import '../../shared/gr-date-formatter/gr-date-formatter.js';
import '../../shared/gr-rest-api-interface/gr-rest-api-interface.js';
import '../../shared/gr-account-link/gr-account-link.js';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners.js';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin.js';
import {PolymerElement} from '@polymer/polymer/polymer-element.js';
import {htmlTemplate} from './gr-group-audit-log_html.js';
import {ListViewMixin} from '../../../mixins/gr-list-view-mixin/gr-list-view-mixin.js';
import {GerritNav} from '../../core/gr-navigation/gr-navigation.js';

const GROUP_EVENTS = ['ADD_GROUP', 'REMOVE_GROUP'];

/**
 * @extends PolymerElement
 */
class GrGroupAuditLog extends ListViewMixin(GestureEventListeners(
    LegacyElementMixin(
        PolymerElement))) {
  static get template() { return htmlTemplate; }

  static get is() { return 'gr-group-audit-log'; }

  static get properties() {
    return {
      groupId: String,
      _auditLog: Array,
      _loading: {
        type: Boolean,
        value: true,
      },
    };
  }

  /** @override */
  attached() {
    super.attached();
    this.dispatchEvent(new CustomEvent('title-change', {
      detail: {title: 'Audit Log'},
      composed: true, bubbles: true,
    }));
  }

  /** @override */
  ready() {
    super.ready();
    this._getAuditLogs();
  }

  _getAuditLogs() {
    if (!this.groupId) { return ''; }

    const errFn = response => {
      this.dispatchEvent(new CustomEvent('page-error', {
        detail: {response},
        composed: true, bubbles: true,
      }));
    };

    return this.$.restAPI.getGroupAuditLog(this.groupId, errFn)
        .then(auditLog => {
          if (!auditLog) {
            this._auditLog = [];
            return;
          }
          this._auditLog = auditLog;
          this._loading = false;
        });
  }

  _status(item) {
    return item.disabled ? 'Disabled' : 'Enabled';
  }

  itemType(type) {
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

  _isGroupEvent(type) {
    return GROUP_EVENTS.indexOf(type) !== -1;
  }

  _computeGroupUrl(group) {
    if (group && group.url && group.id) {
      return GerritNav.getUrlForGroup(group.id);
    }

    return '';
  }

  _getIdForUser(account) {
    return account._account_id ? ' (' + account._account_id + ')' : '';
  }

  _getNameForGroup(group) {
    if (group && group.name) {
      return group.name;
    } else if (group && group.id) {
      // The URL encoded id of the member
      return decodeURIComponent(group.id);
    }

    return '';
  }
}

customElements.define(GrGroupAuditLog.is, GrGroupAuditLog);
