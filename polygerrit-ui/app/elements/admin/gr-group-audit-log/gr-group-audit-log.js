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
(function() {
  'use strict';

  const GROUP_EVENTS = ['ADD_GROUP', 'REMOVE_GROUP'];

  Polymer({
    is: 'gr-group-audit-log',
    _legacyUndefinedCheck: true,

    properties: {
      groupId: String,
      _auditLog: Array,
      _loading: {
        type: Boolean,
        value: true,
      },
    },

    behaviors: [
      Gerrit.ListViewBehavior,
    ],

    attached() {
      this.fire('title-change', {title: 'Audit Log'});
    },

    ready() {
      this._getAuditLogs();
    },

    _getAuditLogs() {
      if (!this.groupId) { return ''; }

      const errFn = response => {
        this.fire('page-error', {response});
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
    },

    _status(item) {
      return item.disabled ? 'Disabled' : 'Enabled';
    },

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
    },

    _isGroupEvent(type) {
      return GROUP_EVENTS.indexOf(type) !== -1;
    },

    _computeGroupUrl(group) {
      if (group && group.url && group.id) {
        return Gerrit.Nav.getUrlForGroup(group.id);
      }

      return '';
    },

    _getIdForUser(account) {
      return account._account_id ? ' (' + account._account_id + ')' : '';
    },

    _getNameForGroup(group) {
      if (group && group.name) {
        return group.name;
      } else if (group && group.id) {
        // The URL encoded id of the member
        return decodeURIComponent(group.id);
      }

      return '';
    },
  });
})();
