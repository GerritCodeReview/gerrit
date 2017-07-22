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
    is: 'gr-create-item-dialog',

    properties: {
      projectName: String,
      hasNewItemName: {
        type: Boolean,
        notify: true,
        value: false,
      },
      itemDetail: String,
      _itemName: String,
      _itemRevision: String,
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
      if (this.itemDetail === 'branches') {
        return this.getBaseUrl() + '/admin/projects/' +
            this.encodeURL(this.projectName, true) + ',branches';
      } else if (this.itemDetail === 'tags') {
        return this.getBaseUrl() + '/admin/projects/' +
            this.encodeURL(this.projectName, true) + ',tags';
      }
    },

    itemDetailName(detail) {
      if (detail === 'branches') {
        return 'Branch';
      } else if (detail === 'tags') {
        return 'Tag';
      }
    },

    handleCreateItem() {
      if (this.itemDetail === 'branches') {
        return this.$.restAPI.createProjectBranch(this.projectName,
            this._itemName, {revision: this._itemRevision})
            .then(itemRegistered => {
              if (itemRegistered.status === 201) {
                page.show(this._computeItemUrl(this.itemDetail));
              }
            });
      } else if (this.itemDetail === 'tags') {
        return this.$.restAPI.createProjectTag(this.projectName,
            this._itemName, {revision: this._itemRevision})
            .then(itemRegistered => {
              if (itemRegistered.status === 201) {
                page.show(this._computeItemUrl(this.itemDetail));
              }
            });
      }
    },
  });
})();
