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

    async _loadGroupDetails() {
      if (!this.groupId) { return; }

      const promises = [];

      const errFn = response => {
        this.fire('page-error', {response});
      };

      const config = await this.$.restAPI.getGroupConfig(this.groupId, errFn);
      if (!config || !config.name) { return; }

      this._groupName = config.name;

      promises.push((async () => {
        this._isAdmin = !!(await this.$.restAPI.getIsAdmin());
      })());

      promises.push((async () => {
        this._groupOwner =
            !!(await this.$.restAPI.getIsGroupOwner(config.name));
      })());

      promises.push((async () => {
        this._groupMembers = await this.$.restAPI.getGroupMembers(config.name);
      })());

      promises.push((async () => {
        this._includedGroups =
            await this.$.restAPI.getIncludedGroup(config.name);
      })());

      await Promise.all(promises);
      this._loading = false;
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

    async _handleSavingGroupMember() {
      const config = await this.$.restAPI.saveGroupMembers(this._groupName,
          this._groupMemberSearchId);
      if (!config) {
        return;
      }
      this._groupMembers =
          await this.$.restAPI.getGroupMembers(this._groupName);
      this._groupMemberSearchName = '';
      this._groupMemberSearchId = '';
    },

    async _handleDeleteConfirm() {
      this.$.overlay.close();
      if (this._itemType === 'member') {
        const itemDeleted = await this.$.restAPI.deleteGroupMembers(
            this._groupName, this._itemId);
        if (itemDeleted.status === 204) {
          this._groupMembers =
              await this.$.restAPI.getGroupMembers(this._groupName);
        }
      } else if (this._itemType === 'includedGroup') {
        const itemDeleted = await this.$.restAPI.deleteIncludedGroup(
            this._groupName, this._itemId);
        if (itemDeleted.status === 204 || itemDeleted.status === 205) {
          this._includedGroups =
              await this.$.restAPI.getIncludedGroup(this._groupName);
        }
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

    async _handleSavingIncludedGroups() {
      const config = await this.$.restAPI.saveIncludedGroup(
          this._groupName,
          this._includedGroupSearchId,
          err => {
            if (err.status === 404) {
              this.dispatchEvent(new CustomEvent('show-alert', {
                detail: {message: SAVING_ERROR_TEXT},
                bubbles: true,
              }));
              return err;
            }
            throw Error(err.statusText);
          });

      if (!config) { return; }

      this._includedGroups =
          await this.$.restAPI.getIncludedGroup(this._groupName);
      this._includedGroupSearchName = '';
      this._includedGroupSearchId = '';
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

    async _getAccountSuggestions(input) {
      if (input.length === 0) { []; }
      const accounts = await this.$.restAPI.getSuggestedAccounts(
          input, SUGGESTIONS_LIMIT);
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
    },

    async _getGroupSuggestions(input) {
      const response = await this.$.restAPI.getSuggestedGroups(input);
      const groups = [];
      for (const key in response) {
        if (!response.hasOwnProperty(key)) { continue; }
        groups.push({
          name: key,
          value: decodeURIComponent(response[key].id),
        });
      }
      return groups;
    },

    _computeHideItemClass(owner, admin) {
      return admin || owner ? '' : 'canModify';
    },
  });
})();
