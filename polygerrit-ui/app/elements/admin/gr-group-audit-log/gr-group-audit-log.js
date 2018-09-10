/**
@license
Copyright (C) 2017 The Android Open Source Project

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
import '../../../behaviors/gr-list-view-behavior/gr-list-view-behavior.js';

import '../../../../@polymer/polymer/polymer-legacy.js';
import '../../../styles/gr-table-styles.js';
import '../../../styles/shared-styles.js';
import '../../core/gr-navigation/gr-navigation.js';
import '../../shared/gr-date-formatter/gr-date-formatter.js';
import '../../shared/gr-rest-api-interface/gr-rest-api-interface.js';

const GROUP_EVENTS = ['ADD_GROUP', 'REMOVE_GROUP'];

Polymer({
  _template: Polymer.html`
    <style include="shared-styles"></style>
    <style include="gr-table-styles"></style>
    <table id="list" class="genericList">
      <tbody><tr class="headerRow">
        <th class="date topHeader">Date</th>
        <th class="type topHeader">Type</th>
        <th class="member topHeader">Member</th>
        <th class="by-user topHeader">By User</th>
      </tr>
      <tr id="loading" class\$="loadingMsg [[computeLoadingClass(_loading)]]">
        <td>Loading...</td>
      </tr>
      <template is="dom-repeat" items="[[_auditLog]]" class\$="[[computeLoadingClass(_loading)]]">
        <tr class="table">
          <td class="date">
            <gr-date-formatter has-tooltip="" date-str="[[item.date]]">
            </gr-date-formatter>
          </td>
          <td class="type">[[itemType(item.type)]]</td>
          <td class="member">
            <template is="dom-if" if="[[_isGroupEvent(item.type)]]">
              <a href\$="[[_computeGroupUrl(item.member)]]">
                [[_getNameForGroup(item.member)]]
              </a>
            </template>
            <template is="dom-if" if="[[!_isGroupEvent(item.type)]]">
              <gr-account-link account="[[item.member]]"></gr-account-link>
              [[_getIdForUser(item.member)]]
            </template>
          </td>
          <td class="by-user">
            <gr-account-link account="[[item.user]]"></gr-account-link>
            [[_getIdForUser(item.user)]]
          </td>
        </tr>
      </template>
    </tbody></table>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
`,

  is: 'gr-group-audit-log',

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
  }
});
