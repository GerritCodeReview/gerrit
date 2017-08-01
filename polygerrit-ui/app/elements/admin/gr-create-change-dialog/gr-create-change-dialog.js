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
    is: 'gr-create-change-dialog',

    properties: {
      detailType: String,
      projectName: String,
      hasNewItemName: {
        type: Boolean,
        notify: true,
        value: false,
      },
      itemDetail: String,
      _itemName: String,
      _itemRevision: String,
      _query: {
        type: Function,
        value() {
          return this._getProjectBranchesSuggestions.bind(this);
        },
      },
    },

    behaviors: [
      Gerrit.BaseUrlBehavior,
      Gerrit.URLEncodingBehavior,
    ],

    observers: [
      '_updateItemName(_itemName)',
    ],

    _updateItemName(name) {
      this.hasNewItemName = !!name;
    },

    _computeItemUrl(project) {
      if (this.itemDetail === DETAIL_TYPES.branches) {
        return this.getBaseUrl() + '/admin/projects/' +
            this.encodeURL(this.projectName, true) + ',branches';
      } else if (this.itemDetail === DETAIL_TYPES.tags) {
        return this.getBaseUrl() + '/admin/projects/' +
            this.encodeURL(this.projectName, true) + ',tags';
      }
    },

    handleCreateItem() {
      const USE_HEAD = this._itemRevision ? this._itemRevision : 'HEAD';
      if (this.itemDetail === DETAIL_TYPES.branches) {
        return this.$.restAPI.createProjectBranch(this.projectName,
            this._itemName, {revision: USE_HEAD})
            .then(itemRegistered => {
              if (itemRegistered.status === 201) {
                page.show(this._computeItemUrl(this.itemDetail));
              }
            });
      } else if (this.itemDetail === DETAIL_TYPES.tag) {
        return this.$.restAPI.createProjectTag(this.projectName,
            this._itemName, {revision: USE_HEAD})
            .then(itemRegistered => {
              if (itemRegistered.status === 201) {
                page.show(this._computeItemUrl(this.itemDetail));
              }
            });
      }
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
    },
  });
})();
