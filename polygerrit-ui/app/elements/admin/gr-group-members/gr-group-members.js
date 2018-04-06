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
  const SAVING_ERROR_TEXT = 'Group may not exist, or you may not have '+
      'permission to add it';

  const URL_REGEX = '^(?:[a-z]+:)?//';

  Polymer({
    is: 'gr-group-members',

    properties: {
      groupId: Number,
      _groupMemberSearchId: String,
      _groupMemberSearchName: String,
      _includedGroupSearch: String,
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

      return this.$.restAPI.getGroupConfig(this.groupId).then(
          config => {
            if (!config.name) { return; }

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
            this._itemName)
            .then(itemDeleted => {
              if (itemDeleted.status === 204) {
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
          this._includedGroupSearch, err => {
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
            this._includedGroupSearch = '';
          });
    },

    _handleDeleteIncludedGroup(e) {
      const name = e.model.get('item.name');
      if (!name) {
        return '';
      }
      this._itemName = name;
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
                value: response[key],
              });
            }
            return groups;
          });
    },

    _computeHideItemClass(owner, admin) {
      return admin || owner ? '' : 'canModify';
    },
  });
})();
