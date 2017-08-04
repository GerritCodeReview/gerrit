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
      groupSearch: String,
      _loading: {
        type: Boolean,
        value: true,
      },
      _groupName: String,
      _groupMembers: String,
      _query: {
        type: Function,
        value() {
          return this._getAccountSuggestions.bind(this);
        },
      },
    },

    attached() {
      this._loadGroupMembers();
    },

    _loadGroupMembers() {
      if (!this.groupId) { return; }

      return this.$.restAPI.getGroupConfig(this.groupId).then(
          config => {
            this._groupName = config.name;
            this._loading = false;
            this.$.restAPI.getGroupMembers(config.name).then(members => {
              this._groupMembers = members;
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
      return this.$.restAPI.saveGroupMembers(this._groupName, this.groupSearch)
          .then(config => {
            if (config.status === 201) {
              this._groupMembers = config;
            }
          });
    },

    _getAccountSuggestions(input) {
      if (input.length === 0) { return Promise.resolve([]); }
      return this.$.restAPI.getSuggestedAccounts(
          input, SUGGESTIONS_LIMIT).then(accounts => {
            const accountSuggestions = [];
            if (!accounts) { return []; }
            for (const key in accounts) {
              accountSuggestions.push({
                name: '"' + accounts[key].name + ' <' + accounts[key].email + '>"',//key.map(acct => acct.name + ' <' + acct.email + '>"'),
              });
            }
            return accountSuggestions;
          });
    },

    /*_getProjectBranchesSuggestions(input) {
      if (input.startsWith('refs/heads/')) {
        input = input.substring('refs/heads/'.length);
      }
      return this.$.restAPI.getProjectBranches(
          input, this.project, SUGGESTIONS_LIMIT).then(accounts => {
            const accountSuggestions = [];
            for (const key in accounts) {
              if (!accounts.hasOwnProperty(key)) { continue; }
              if (response[key].ref.startsWith('refs/heads/')) {
                branch = response[key].ref.substring('refs/heads/'.length);
              } else {
                branch = response[key].ref;
              }
              branches.push({
                name: branch,
              });
            }
            return branches;
          });
    },

    _getProjectBranchesSuggestions(input) {
      if (input.startsWith('refs/heads/')) {
        input = input.substring('refs/heads/'.length);
      }
      return this.$.restAPI.getProjectBranches(
          input, this.project, SUGGESTIONS_LIMIT).then(response => {
            const branches = [];
            let branch;
            for (const key in response) {
              if (!response.hasOwnProperty(key)) { continue; }
              if (response[key].ref.startsWith('refs/heads/')) {
                branch = response[key].ref.substring('refs/heads/'.length);
              } else {
                branch = response[key].ref;
              }
              branches.push({
                name: branch,
              });
            }
            return branches;
          });
    },*/
  });
})();
