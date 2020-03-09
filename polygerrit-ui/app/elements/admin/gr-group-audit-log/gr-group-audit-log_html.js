<!--
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
-->

<link rel="import" href="../../../behaviors/gr-list-view-behavior/gr-list-view-behavior.html">
<link rel="import" href="/bower_components/polymer/polymer.html">
<link rel="import" href="../../../behaviors/fire-behavior/fire-behavior.html">
<link rel="import" href="../../../styles/gr-table-styles.html">
<link rel="import" href="../../../styles/shared-styles.html">
<link rel="import" href="../../core/gr-navigation/gr-navigation.html">
<link rel="import" href="../../shared/gr-date-formatter/gr-date-formatter.html">
<link rel="import" href="../../shared/gr-rest-api-interface/gr-rest-api-interface.html">
<link rel="import" href="../../shared/gr-account-link/gr-account-link.html">

<dom-module id="gr-group-audit-log">
  <template>
    <style include="shared-styles">
      /* Workaround for empty style block - see https://github.com/Polymer/tools/issues/408 */
    </style>
    <style include="gr-table-styles">
      /* GenericList style centers the last column, but we don't want that here. */
      .genericList tr th:last-of-type,
      .genericList tr td:last-of-type {
        text-align: left;
      }
    </style>
    <table id="list" class="genericList">
      <tr class="headerRow">
        <th class="date topHeader">Date</th>
        <th class="type topHeader">Type</th>
        <th class="member topHeader">Member</th>
        <th class="by-user topHeader">By User</th>
      </tr>
      <tr id="loading" class$="loadingMsg [[computeLoadingClass(_loading)]]">
        <td>Loading...</td>
      </tr>
      <tbody class$="[[computeLoadingClass(_loading)]]">
        <template is="dom-repeat" items="[[_auditLog]]">
          <tr class="table">
            <td class="date">
              <gr-date-formatter
                  has-tooltip
                  date-str="[[item.date]]">
              </gr-date-formatter>
            </td>
            <td class="type">[[itemType(item.type)]]</td>
            <td class="member">
              <template is="dom-if" if="[[_isGroupEvent(item.type)]]">
                <a href$="[[_computeGroupUrl(item.member)]]">
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
      </tbody>
    </table>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
  </template>
  <script src="gr-group-audit-log.js"></script>
</dom-module>
