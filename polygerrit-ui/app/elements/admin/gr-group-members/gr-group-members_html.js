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

<link rel="import" href="../../../behaviors/base-url-behavior/base-url-behavior.html">
<link rel="import" href="../../../behaviors/fire-behavior/fire-behavior.html">
<link rel="import" href="../../../behaviors/gr-url-encoding-behavior/gr-url-encoding-behavior.html">
<link rel="import" href="/bower_components/polymer/polymer.html">
<link rel="import" href="/bower_components/iron-autogrow-textarea/iron-autogrow-textarea.html">
<link rel="import" href="../../../styles/gr-form-styles.html">
<link rel="import" href="../../../styles/gr-subpage-styles.html">
<link rel="import" href="../../../styles/gr-table-styles.html">
<link rel="import" href="../../../styles/shared-styles.html">
<link rel="import" href="../../shared/gr-account-link/gr-account-link.html">
<link rel="import" href="../../shared/gr-autocomplete/gr-autocomplete.html">
<link rel="import" href="../../shared/gr-button/gr-button.html">
<link rel="import" href="../../shared/gr-overlay/gr-overlay.html">
<link rel="import" href="../../shared/gr-rest-api-interface/gr-rest-api-interface.html">
<link rel="import" href="../gr-confirm-delete-item-dialog/gr-confirm-delete-item-dialog.html">

<dom-module id="gr-group-members">
  <template>
    <style include="gr-form-styles">
      /* Workaround for empty style block - see https://github.com/Polymer/tools/issues/408 */
    </style>
    <style include="gr-table-styles">
      /* Workaround for empty style block - see https://github.com/Polymer/tools/issues/408 */
    </style>
    <style include="gr-subpage-styles">
      /* Workaround for empty style block - see https://github.com/Polymer/tools/issues/408 */
    </style>
    <style include="shared-styles">
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
    </style>
    <main class$="gr-form-styles [[_computeHideItemClass(_groupOwner, _isAdmin)]]">
      <div id="loading" class$="[[_computeLoadingClass(_loading)]]">
        Loading...
      </div>
      <div id="loadedContent" class$="[[_computeLoadingClass(_loading)]]">
        <h1 id="Title">[[_groupName]]</h1>
        <div id="form">
          <h3 id="members">Members</h3>
          <fieldset>
            <span class="value">
              <gr-autocomplete
                  id="groupMemberSearchInput"
                  text="{{_groupMemberSearchName}}"
                  value="{{_groupMemberSearchId}}"
                  query="[[_queryMembers]]"
                  placeholder="Name Or Email">
              </gr-autocomplete>
            </span>
            <gr-button
                id="saveGroupMember"
                on-click="_handleSavingGroupMember"
                disabled="[[!_groupMemberSearchId]]">
              Add
            </gr-button>
            <table id="groupMembers">
              <tr class="headerRow">
                <th class="nameHeader">Name</th>
                <th class="emailAddressHeader">Email Address</th>
                <th class="deleteHeader">Delete Member</th>
              </tr>
              <tbody>
                <template is="dom-repeat" items="[[_groupMembers]]">
                  <tr>
                    <td class="nameColumn">
                      <gr-account-link account="[[item]]"></gr-account-link>
                    </td>
                    <td>[[item.email]]</td>
                    <td class="deleteColumn">
                      <gr-button
                          class="deleteMembersButton"
                          on-click="_handleDeleteMember">
                        Delete
                      </gr-button>
                    </td>
                  </tr>
                </template>
              </tbody>
            </table>
          </fieldset>
          <h3 id="includedGroups">Included Groups</h3>
          <fieldset>
            <span class="value">
              <gr-autocomplete
                  id="includedGroupSearchInput"
                  text="{{_includedGroupSearchName}}"
                  value="{{_includedGroupSearchId}}"
                  query="[[_queryIncludedGroup]]"
                  placeholder="Group Name">
              </gr-autocomplete>
            </span>
            <gr-button
                id="saveIncludedGroups"
                on-click="_handleSavingIncludedGroups"
                disabled="[[!_includedGroupSearchId]]">
              Add
            </gr-button>
            <table id="includedGroups">
              <tr class="headerRow">
                <th class="groupNameHeader">Group Name</th>
                <th class="descriptionHeader">Description</th>
                <th class="deleteIncludedHeader">
                  Delete Group
                </th>
              </tr>
              <tbody>
                <template is="dom-repeat" items="[[_includedGroups]]">
                  <tr>
                    <td class="nameColumn">
                      <template is="dom-if" if="[[item.url]]">
                        <a href$="[[_computeGroupUrl(item.url)]]"
                            rel="noopener">
                          [[item.name]]
                        </a>
                      </template>
                      <template is="dom-if" if="[[!item.url]]">
                        [[item.name]]
                      </template>
                    </td>
                    <td>[[item.description]]</td>
                    <td class="deleteColumn">
                      <gr-button
                          class="deleteIncludedGroupButton"
                          on-click="_handleDeleteIncludedGroup">
                        Delete
                      </gr-button>
                    </td>
                  </tr>
                </template>
              </tbody>
            </table>
          </fieldset>
        </div>
      </div>
    </main>
    <gr-overlay id="overlay" with-backdrop>
      <gr-confirm-delete-item-dialog
          class="confirmDialog"
          on-confirm="_handleDeleteConfirm"
          on-cancel="_handleConfirmDialogCancel"
          item="[[_itemName]]"
          item-type="[[_itemType]]"></gr-confirm-delete-item-dialog>
    </gr-overlay>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
  </template>
  <script src="gr-group-members.js"></script>
</dom-module>
