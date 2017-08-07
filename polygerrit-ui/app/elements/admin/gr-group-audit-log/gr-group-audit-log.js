// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
(function() {
  'use strict';

  Polymer({
    is: 'gr-group-audit-log',

    properties: {
      groupId: Object,
      _auditLog: Array,
      _loading: {
        type: Boolean,
        value: true,
      },
    },

    behaviors: [
      Gerrit.ListViewBehavior,
    ],

    ready() {
      this._getAuditLogs();
    },

    _getAuditLogs() {
      return this.$.restAPI.getGroupAuditLog(this.groupId).then(auditLog => {
        if (!auditLog) {
          this._auditLog = [];
          return;
        }
        this._auditLog = Object.keys(auditLog).map(key => {
          const audit = auditLog[key];
          audit.name = key;
          return audit;
        });
        this._loading = false;
      });
    },

    _status(item) {
      return item.disabled ? 'Disabled' : 'Enabled';
    },

    _computeGroupUrl(id) {
      return this.getBaseUrl() + '/admin/groups/' + id;
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

    _getName(name) {
      if (name.username) {
        return name.username + ' (' + name.username._account_id + ')';
      } else if (name.name) {
        return name.name + ' (' + name.name._account_id + ')';
      }
    },
  });
})();
