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

  const DETAIL_TYPES = {
    branches: 'branches',
    tags: 'tags',
  };

  Polymer({
    is: 'gr-create-pointer-dialog',

    properties: {
      detailType: String,
      projectName: String,
      hasNewItemName: {
        type: Boolean,
        notify: true,
        value: false,
      },
      itemDetail: String,
      _itemConfig: {
        type: Object,
        value: () => { return {}; },
      },
      _showCreateProject: Boolean,
      _showCreateItem: Boolean,
      _query: {
        type: Function,
        value() {
          return this._getItemSuggestions.bind(this);
        },
      },
    },

    behaviors: [
      Gerrit.BaseUrlBehavior,
      Gerrit.URLEncodingBehavior,
    ],

    observers: [
      '_updateItemName(_itemConfig.name)',
      '_paramsChanged(itemDetail)',
    ],

    _updateItemName(name) {
      this.hasNewItemName = !!name;
    },

    _paramsChanged(params) {
      this.set('_showCreateProject', params === 'projects');
      this.set('_showCreateItem', params === 'branches' || params === 'tags');
    },

    _computeItemUrl(item, name) {
      if (item === 'branches') {
        return this.getBaseUrl() + '/admin/projects/' +
            this.encodeURL(name, true) + ',branches';
      } else if (item === 'tags') {
        return this.getBaseUrl() + '/admin/projects/' +
            this.encodeURL(name, true) + ',tags';
      } else if (item === 'projects') {
        return this.getBaseUrl() + '/admin/projects/' +
            this.encodeURL(name, true);
      } else if (item === 'groups') {
        return this.getBaseUrl() + '/admin/groups/' +
            this.encodeURL(name, true);
      }
    },

    handleCreateItem() {
      const USE_HEAD = this._itemConfig.revision ?
            this._itemConfig.revision : 'HEAD';
      if (this.itemDetail === DETAIL_TYPES.branches) {
        return this.$.restAPI.createProjectBranch(this.projectName,
            this._itemName, {revision: USE_HEAD})
            .then(itemRegistered => {
              if (itemRegistered.status === 201) {
                page.show(
                    this._computeItemUrl(this.itemDetail, this.projectName));
              }
            });
      } else if (this.itemDetail === DETAIL_TYPES.tag) {
        return this.$.restAPI.createProjectTag(this.projectName,
            this._itemConfig.name, {revision: USE_HEAD})
            .then(itemRegistered => {
              if (itemRegistered.status === 201) {
                page.show(
                    this._computeItemUrl(this.itemDetail, this.projectName));
              }
            });
      } else if (this.itemDetail === 'projects') {
        return this.$.restAPI.createProject(this._itemConfig)
            .then(projectRegistered => {
              if (projectRegistered.status === 201) {
                page.show(this._computeItemUrl(
                    this.itemDetail, this._itemConfig.name));
              }
            });
      } else if (this.itemDetail === 'groups') {
        return this.$.restAPI.createGroup({name: this._itemConfig.name})
            .then(groupRegistered => {
              if (groupRegistered.status !== 201) { return; }
              return this.$.restAPI.getGroupConfig(this._itemConfig.name)
                  .then(group => {
                    page.show(
                        this._computeItemUrl(this.itemDetail, group.group_id));
                  });
            });
      }
    },

    _getItemSuggestions(input) {
      if (this.itemDetail === 'projects') {
        return this.$.restAPI.getSuggestedProjects(input)
            .then(response => {
              const projects = [];
              for (const key in response) {
                if (!response.hasOwnProperty(key)) { continue; }
                projects.push({
                  name: key,
                  value: response[key],
                });
              }
              return projects;
            });
      }
    },
  });
})();
