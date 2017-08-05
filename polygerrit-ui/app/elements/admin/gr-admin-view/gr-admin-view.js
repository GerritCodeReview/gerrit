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

  const ADMIN_LINKS = [{
    name: 'Projects',
    url: '/admin/projects',
    view: 'gr-admin-project-list',
    viewableToAll: true,
    children: [],
  }, {
    name: 'Groups',
    section: 'Groups',
    url: '/admin/groups',
    view: 'gr-admin-group-list',
    children: [],
  }, {
    name: 'Plugins',
    capability: 'viewPlugins',
    section: 'Plugins',
    url: '/admin/plugins',
    view: 'gr-plugin-list',
  }];

  const ACCOUNT_CAPABILITIES = ['createProject', 'createGroup', 'viewPlugins'];

  Polymer({
    is: 'gr-admin-view',

    properties: {
      params: Object,
      path: String,
      adminView: String,

      _projectName: String,
      _groupId: {
        type: Number,
        observer: '_computeGroupName',
      },
      _groupName: String,
      _groupOwner: {
        type: Boolean,
        value: false,
      },
      _filteredLinks: Array,
      _showDownload: {
        type: Boolean,
        value: false,
      },
      _showGroup: Boolean,
      _showGroupAuditLog: Boolean,
      _showGroupList: Boolean,
      _showProjectMain: Boolean,
      _showProjectList: Boolean,
      _showProjectDetailList: Boolean,
      _showPluginList: Boolean,
    },

    behaviors: [
      Gerrit.BaseUrlBehavior,
      Gerrit.URLEncodingBehavior,
    ],

    observers: [
      '_paramsChanged(params)',
    ],

    attached() {
      this.reload();
    },

    reload() {
      return this.$.restAPI.getAccount().then(account => {
        this._account = account;
        if (!account) {
          // Return so that  account capabilities don't load with no account.
          return this._filteredLinks = this._filterLinks(link => {
            return link.viewableToAll;
          });
        }
        this._loadAccountCapabilities();
      });
    },

    _filterLinks(filterFn) {
      const links = ADMIN_LINKS.filter(filterFn);
      const filteredLinks = [];
      for (const link of links) {
        const linkCopy = Object.assign({}, link);
        linkCopy.children = linkCopy.children ?
            linkCopy.children.filter(filterFn) : [];
        if (linkCopy.name === 'Projects' && this._projectName) {
          linkCopy.subsection = {
            name: this._projectName,
            view: 'gr-project',
            url: `/admin/projects/${this.encodeURL(this._projectName, true)}`,
            children: [{
              name: 'Branches',
              detailType: 'branches',
              view: 'gr-project-detail-list',
              url: `/admin/projects/` +
                  `${this.encodeURL(this._projectName, true)},branches`,
            },
            {
              name: 'Tags',
              detailType: 'tags',
              view: 'gr-project-detail-list',
              url: `/admin/projects/` +
                  `${this.encodeURL(this._projectName, true)},tags`,
            }],
          };
        }
        if (linkCopy.name === 'Groups' && this._groupId && this._groupName) {
          linkCopy.subsection = {
            name: this._groupName,
            view: 'gr-group',
            url: `/admin/groups/${this.encodeURL(this._groupId, true)}`,
            children: [],
          };
          if (!this._groupOwner) {
            linkCopy.subsection.children.push(
              {
                name: 'Audit Log',
                detailType: 'audit-log',
                view: 'gr-group-audit-log',
                url: `/admin/groups/${this.encodeURL(this._groupId, true)}` +
                      ',audit-log',
              }
            );
          }
        }
        filteredLinks.push(linkCopy);
      }
      return filteredLinks;
    },

    _loadAccountCapabilities() {
      return this.$.restAPI.getAccountCapabilities(ACCOUNT_CAPABILITIES)
          .then(capabilities => {
            this._filteredLinks = this._filterLinks(link => {
              return !link.capability ||
                  capabilities.hasOwnProperty(link.capability);
            });
          });
    },

    _paramsChanged(params) {
      this.set('_showGroup', params.adminView === 'gr-group');
      this.set('_showGroupAuditLog', params.adminView === 'gr-group-audit-log');
      this.set('_showGroupList', params.adminView === 'gr-admin-group-list');
      this.set('_showProjectMain', params.adminView === 'gr-project');
      this.set('_showProjectList',
          params.adminView === 'gr-admin-project-list');
      this.set('_showProjectDetailList',
          params.adminView === 'gr-project-detail-list');
      this.set('_showPluginList', params.adminView === 'gr-plugin-list');
      if (params.project !== this._projectName) {
        this._projectName = params.project || '';
        // Reloads the admin menu.
        this.reload();
      }
      if (params.groupId !== this._groupId) {
        this._groupId = params.groupId || '';
        // Reloads the admin menu.
        this.reload();
      }
    },

    // TODO (beckysiegel): Update these functions after router abstraction is
    // updated. They are currently copied from gr-dropdown (and should be
    // updated there as well once complete).
    _computeURLHelper(host, path) {
      return '//' + host + this.getBaseUrl() + path;
    },

    _computeRelativeURL(path) {
      const host = window.location.host;
      return this._computeURLHelper(host, path);
    },

    _computeLinkURL(link) {
      if (!link || typeof link.url === 'undefined') { return ''; }
      if (link.target) {
        return link.url;
      }
      return this._computeRelativeURL(link.url);
    },

    _computeSelectedClass(itemView, params, opt_detailType) {
      if (params.detailType && params.detailType !== opt_detailType) {
        return '';
      }
      return itemView === params.adminView ? 'selected' : '';
    },

    _computeGroupName(groupId) {
      if (!groupId) { return ''; }
      this.$.restAPI.getGroupConfig(groupId).then(group => {
        this._groupName = group.name;
        this.reload();
        this.$.restAPI.getIsGroupOwner(group.name).then(
            configs => {
              if (Object.keys(configs).length === 0 &&
                  configs.constructor === Object) {
                this._groupOwner = true;
                this.reload();
              }
            });
      });
    },

    _updateGroupName(e) {
      this._groupName = e.detail.name;
      this.reload();
    },
  });
})();
