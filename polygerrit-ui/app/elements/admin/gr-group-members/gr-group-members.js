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
import '../../../behaviors/base-url-behavior/base-url-behavior.js';

import '../../../behaviors/gr-url-encoding-behavior/gr-url-encoding-behavior.js';
import '../../../../@polymer/polymer/polymer-legacy.js';
import '../../../../@polymer/iron-autogrow-textarea/iron-autogrow-textarea.js';
import '../../../../@polymer/iron-input/iron-input.js';
import '../../../styles/gr-form-styles.js';
import '../../../styles/gr-subpage-styles.js';
import '../../../styles/gr-table-styles.js';
import '../../../styles/shared-styles.js';
import '../../shared/gr-account-link/gr-account-link.js';
import '../../shared/gr-autocomplete/gr-autocomplete.js';
import '../../shared/gr-button/gr-button.js';
import '../../shared/gr-overlay/gr-overlay.js';
import '../../shared/gr-rest-api-interface/gr-rest-api-interface.js';
import '../gr-confirm-delete-item-dialog/gr-confirm-delete-item-dialog.js';

const SUGGESTIONS_LIMIT = 15;
const SAVING_ERROR_TEXT = 'Group may not exist, or you may not have '+
    'permission to add it';

const URL_REGEX = '^(?:[a-z]+:)?//';

Polymer({
  _template: Polymer.html`
    <style include="gr-form-styles"></style>
    <style include="gr-table-styles"></style>
    <style include="gr-subpage-styles"></style>
    <style include="shared-styles">
      .input {
        width: 15em;
      }
      gr-autocomplete {
        width: 20em;
        --gr-autocomplete: {
          font-size: var(--font-size-normal);
          height: 2em;
          width: 20em;
        }
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
        font-family: var(--font-family-bold);
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
    <main class\$="gr-form-styles [[_computeHideItemClass(_groupOwner, _isAdmin)]]">
      <div id="loading" class\$="[[_computeLoadingClass(_loading)]]">
        Loading...
      </div>
      <div id="loadedContent" class\$="[[_computeLoadingClass(_loading)]]">
        <h1 id="Title">[[_groupName]]</h1>
        <div id="form">
          <h3 id="members">Members</h3>
          <fieldset>
            <span class="value">
              <gr-autocomplete id="groupMemberSearchInput" text="{{_groupMemberSearchName}}" value="{{_groupMemberSearchId}}" query="[[_queryMembers]]" placeholder="Name Or Email">
              </gr-autocomplete>
            </span>
            <gr-button id="saveGroupMember" on-tap="_handleSavingGroupMember" disabled="[[!_groupMemberSearchId]]">
              Add
            </gr-button>
            <table id="groupMembers">
              <tbody><tr class="headerRow">
                <th class="nameHeader">Name</th>
                <th class="emailAddressHeader">Email Address</th>
                <th class="deleteHeader">Delete Member</th>
              </tr>
              </tbody><tbody>
                <template is="dom-repeat" items="[[_groupMembers]]">
                  <tr>
                    <td class="nameColumn">
                      <gr-account-link account="[[item]]"></gr-account-link>
                    </td>
                    <td>[[item.email]]</td>
                    <td class="deleteColumn">
                      <gr-button class="deleteMembersButton" on-tap="_handleDeleteMember">
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
              <gr-autocomplete id="includedGroupSearchInput" text="{{_includedGroupSearchName}}" value="{{_includedGroupSearchId}}" query="[[_queryIncludedGroup]]" placeholder="Group Name">
              </gr-autocomplete>
            </span>
            <gr-button id="saveIncludedGroups" on-tap="_handleSavingIncludedGroups" disabled="[[!_includedGroupSearchId]]">
              Add
            </gr-button>
            <table id="includedGroups">
              <tbody><tr class="headerRow">
                <th class="groupNameHeader">Group Name</th>
                <th class="descriptionHeader">Description</th>
                <th class="deleteIncludedHeader">
                  Delete Group
                </th>
              </tr>
              </tbody><tbody>
                <template is="dom-repeat" items="[[_includedGroups]]">
                  <tr>
                    <td class="nameColumn">
                      <template is="dom-if" if="[[item.url]]">
                        <a href\$="[[_computeGroupUrl(item.url)]]" rel="noopener">
                          [[item.name]]
                        </a>
                      </template>
                      <template is="dom-if" if="[[!item.url]]">
                        [[item.name]]
                      </template>
                    </td>
                    <td>[[item.description]]</td>
                    <td class="deleteColumn">
                      <gr-button class="deleteIncludedGroupButton" on-tap="_handleDeleteIncludedGroup">
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
    <gr-overlay id="overlay" with-backdrop="">
      <gr-confirm-delete-item-dialog class="confirmDialog" on-confirm="_handleDeleteConfirm" on-cancel="_handleConfirmDialogCancel" item="[[_itemName]]" item-type="[[_itemType]]"></gr-confirm-delete-item-dialog>
    </gr-overlay>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
`,

  is: 'gr-group-members',

  properties: {
    groupId: Number,
    _groupMemberSearchId: String,
    _groupMemberSearchName: String,
    _includedGroupSearchId: String,
    _includedGroupSearchName: String,
    _loading: {
      type: Boolean,
      value: true,
    },
    _groupName: String,
    _groupMembers: Object,
    _includedGroups: Object,
    _itemName: String,
    _itemType: String,
    _queryMembers: {
      type: Function,
      value() {
        return this._getAccountSuggestions.bind(this);
      },
    },
    _queryIncludedGroup: {
      type: Function,
      value() {
        return this._getGroupSuggestions.bind(this);
      },
    },
    _groupOwner: {
      type: Boolean,
      value: false,
    },
    _isAdmin: {
      type: Boolean,
      value: false,
    },
  },

  behaviors: [
    Gerrit.BaseUrlBehavior,
    Gerrit.URLEncodingBehavior,
  ],

  attached() {
    this._loadGroupDetails();

    this.fire('title-change', {title: 'Members'});
  },

  _loadGroupDetails() {
    if (!this.groupId) { return; }

    const promises = [];

    const errFn = response => {
      this.fire('page-error', {response});
    };

    return this.$.restAPI.getGroupConfig(this.groupId, errFn)
        .then(config => {
          if (!config || !config.name) { return Promise.resolve(); }

          this._groupName = config.name;

          promises.push(this.$.restAPI.getIsAdmin().then(isAdmin => {
            this._isAdmin = isAdmin ? true : false;
          }));

          promises.push(this.$.restAPI.getIsGroupOwner(config.name)
              .then(isOwner => {
                this._groupOwner = isOwner ? true : false;
              }));

          promises.push(this.$.restAPI.getGroupMembers(config.name).then(
              members => {
                this._groupMembers = members;
              }));

          promises.push(this.$.restAPI.getIncludedGroup(config.name)
              .then(includedGroup => {
                this._includedGroups = includedGroup;
              }));

          return Promise.all(promises).then(() => {
            this._loading = false;
          });
        });
  },

  _computeLoadingClass(loading) {
    return loading ? 'loading' : '';
  },

  _isLoading() {
    return this._loading || this._loading === undefined;
  },

  _computeGroupUrl(url) {
    if (!url) { return; }

    const r = new RegExp(URL_REGEX, 'i');
    if (r.test(url)) {
      return url;
    }

    // For GWT compatibility
    if (url.startsWith('#')) {
      return this.getBaseUrl() + url.slice(1);
    }
    return this.getBaseUrl() + url;
  },

  _handleSavingGroupMember() {
    return this.$.restAPI.saveGroupMembers(this._groupName,
        this._groupMemberSearchId).then(config => {
          if (!config) {
            return;
          }
          this.$.restAPI.getGroupMembers(this._groupName).then(members => {
            this._groupMembers = members;
          });
          this._groupMemberSearchName = '';
          this._groupMemberSearchId = '';
        });
  },

  _handleDeleteConfirm() {
    this.$.overlay.close();
    if (this._itemType === 'member') {
      return this.$.restAPI.deleteGroupMembers(this._groupName,
          this._itemId)
          .then(itemDeleted => {
            if (itemDeleted.status === 204) {
              this.$.restAPI.getGroupMembers(this._groupName)
                  .then(members => {
                    this._groupMembers = members;
                  });
            }
          });
    } else if (this._itemType === 'includedGroup') {
      return this.$.restAPI.deleteIncludedGroup(this._groupName,
          this._itemId)
          .then(itemDeleted => {
            if (itemDeleted.status === 204 || itemDeleted.status === 205) {
              this.$.restAPI.getIncludedGroup(this._groupName)
                  .then(includedGroup => {
                    this._includedGroups = includedGroup;
                  });
            }
          });
    }
  },

  _handleConfirmDialogCancel() {
    this.$.overlay.close();
  },

  _handleDeleteMember(e) {
    const id = e.model.get('item._account_id');
    const name = e.model.get('item.name');
    const username = e.model.get('item.username');
    const email = e.model.get('item.email');
    const item = username || name || email || id;
    if (!item) {
      return '';
    }
    this._itemName = item;
    this._itemId = id;
    this._itemType = 'member';
    this.$.overlay.open();
  },

  _handleSavingIncludedGroups() {
    return this.$.restAPI.saveIncludedGroup(this._groupName,
        this._includedGroupSearchId, err => {
          if (err.status === 404) {
            this.dispatchEvent(new CustomEvent('show-alert', {
              detail: {message: SAVING_ERROR_TEXT},
              bubbles: true,
            }));
            return err;
          }
          throw Error(err.statusText);
        })
        .then(config => {
          if (!config) {
            return;
          }
          this.$.restAPI.getIncludedGroup(this._groupName)
              .then(includedGroup => {
                this._includedGroups = includedGroup;
              });
          this._includedGroupSearchName = '';
          this._includedGroupSearchId = '';
        });
  },

  _handleDeleteIncludedGroup(e) {
    const id = decodeURIComponent(e.model.get('item.id'));
    const name = e.model.get('item.name');
    const item = name || id;
    if (!item) { return ''; }
    this._itemName = item;
    this._itemId = id;
    this._itemType = 'includedGroup';
    this.$.overlay.open();
  },

  _getAccountSuggestions(input) {
    if (input.length === 0) { return Promise.resolve([]); }
    return this.$.restAPI.getSuggestedAccounts(
        input, SUGGESTIONS_LIMIT).then(accounts => {
          const accountSuggestions = [];
          let nameAndEmail;
          if (!accounts) { return []; }
          for (const key in accounts) {
            if (!accounts.hasOwnProperty(key)) { continue; }
            if (accounts[key].email !== undefined) {
              nameAndEmail = accounts[key].name +
                ' <' + accounts[key].email + '>';
            } else {
              nameAndEmail = accounts[key].name;
            }
            accountSuggestions.push({
              name: nameAndEmail,
              value: accounts[key]._account_id,
            });
          }
          return accountSuggestions;
        });
  },

  _getGroupSuggestions(input) {
    return this.$.restAPI.getSuggestedGroups(input)
        .then(response => {
          const groups = [];
          for (const key in response) {
            if (!response.hasOwnProperty(key)) { continue; }
            groups.push({
              name: key,
              value: decodeURIComponent(response[key].id),
            });
          }
          return groups;
        });
  },

  _computeHideItemClass(owner, admin) {
    return admin || owner ? '' : 'canModify';
  }
});
