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

  const SUGGESTIONS_LIMIT = 15;

  Polymer({
    is: 'gr-group-members',

    properties: {
      groupId: Number,
      _groupMemberSearch: String,
      _includedGroupSearch: String,
      _loading: {
        type: Boolean,
        value: true,
      },
      _groupName: String,
      _groupMembers: String,
      _includedGroup: String,
      _memberName: String,
      _includedGroupName: String,
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
    },

    attached() {
      this._loadGroupDetails();
    },

    _loadGroupDetails() {
      if (!this.groupId) { return; }

      return this.$.restAPI.getGroupConfig(this.groupId).then(
          config => {
            this._groupName = config.name;
            this._loading = false;
            this.$.restAPI.getGroupMembers(config.name).then(members => {
              this._groupMembers = members;
            });
            this.$.restAPI.getIncludedGroup(config.name)
                .then(includedGroup => {
                  this._includedGroup = includedGroup;
                });
          });
    },

    _computeLoadingClass(loading) {
      return loading ? 'loading' : '';
    },

    _isLoading() {
      return this._loading || this._loading === undefined;
    },

    _handleSavingGroupMember() {
      return this.$.restAPI.saveGroupMembers(this._groupName,
          this._groupMemberSearch).then(config => {
            if (!config) {
              return;
            }
            this.$.restAPI.getGroupMembers(this._groupName).then(members => {
              this._groupMembers = members;
            });
            this._groupMemberSearch = '';
          });
    },

    _handleDeleteMemberConfirm() {
      this.$.overlay.close();
      return this.$.restAPI.deleteGroupMembers(this._groupName,
          this._memberName)
          .then(itemDeleted => {
            if (itemDeleted.status === 204) {
              this.$.restAPI.getGroupMembers(this._groupName).then(members => {
                this._groupMembers = members;
              });
            }
          });
    },

    _handleConfirmDialogCancel() {
      this.$.overlay.close();
    },

    _handleDeleteMember(e) {
      let item;
      const name = e.model.get('item.name');
      const username = e.model.get('item.username');
      const email = e.model.get('item.email');
      if (username) {
        item = username;
      } else if (name) {
        item = name;
      } else if (email) {
        item = email;
      }
      if (!item) {
        return '';
      }
      this._memberName = item;
      this.$.overlay.open();
    },


    _handleSavingIncludedGroups() {
      return this.$.restAPI.saveIncludedGroup(this._groupName,
          this._includedGroupSearch)
          .then(config => {
            if (!config) {
              return;
            }
            this.$.restAPI.getIncludedGroup(config.name)
                .then(includedGroup => {
                  this._includedGroup = includedGroup;
                });
            this._includedGroupSearch = '';
          });
    },

    _handleDeleteIncludedGroupConfirm() {
      this.$.overlayIncludedGroup.close();
      return this.$.restAPI.deleteIncludedGroup(this._groupName,
          this._includedGroupName)
          .then(itemDeleted => {
            if (itemDeleted.status === 204) {
              this.$.restAPI.getIncludedGroup(this._groupName)
                  .then(includedGroup => {
                    this._includedGroup = includedGroup;
                  });
            }
          });
    },

    _handleConfirmDialogCancelIncludedGroup() {
      this.$.overlayIncludedGroup.close();
    },

    _handleDeleteIncludedGroup(e) {
      const name = e.model.get('item.name');
      if (!name) {
        return '';
      }
      this._includedGroupName = name;
      this.$.overlayIncludedGroup.open();
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
                value: response[key],
              });
            }
            return groups;
          });
    },
  });
})();
