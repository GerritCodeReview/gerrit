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

  // Note: noBaseUrl: true is set on entries where the URL is not yet supported
  // by router abstraction.
  const ADMIN_LINKS = [{
    name: 'Repositories',
    noBaseUrl: true,
    url: '/admin/repos',
    view: 'gr-repo-list',
    viewableToAll: true,
    children: [],
  }, {
    name: 'Groups',
    section: 'Groups',
    noBaseUrl: true,
    url: '/admin/groups',
    view: 'gr-admin-group-list',
    children: [],
  }, {
    name: 'Plugins',
    capability: 'viewPlugins',
    section: 'Plugins',
    noBaseUrl: true,
    url: '/admin/plugins',
    view: 'gr-plugin-list',
  }];

  const INTERNAL_GROUP_REGEX = /^[\da-f]{40}$/;

  const ACCOUNT_CAPABILITIES = ['createProject', 'createGroup', 'viewPlugins'];

  Polymer({
    is: 'gr-admin-view',

    properties: {
      /** @type {?} */
      params: Object,
      path: String,
      adminView: String,

      _repoName: String,
      _groupId: {
        type: Number,
        observer: '_computeGroupName',
      },
      _groupIsInternal: Boolean,
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
      _isAdmin: {
        type: Boolean,
        value: false,
      },
      _showGroup: Boolean,
      _showGroupAuditLog: Boolean,
      _showGroupList: Boolean,
      _showGroupMembers: Boolean,
      _showRepoAccess: Boolean,
      _showRepoCommands: Boolean,
      _showRepoDashboards: Boolean,
      _showRepoDetailList: Boolean,
      _showRepoMain: Boolean,
      _showRepoList: Boolean,
      _showPluginList: Boolean,
      adminMenu: Boolean,
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
      const promises = [
        this.$.restAPI.getAccount(),
        Gerrit.awaitPluginsLoaded(),
      ];
      return Promise.all(promises).then(result => {
        this._account = result[0];
        if (!this._account) {
          // Return so that  account capabilities don't load with no account.
          return this._filteredLinks = this._filterLinks(link => {
            return link.viewableToAll;
          });
        }
        this._loadAccountCapabilities();
      });
    },

    _filterLinks(filterFn) {
      let links = ADMIN_LINKS.slice(0);

      // Append top-level links that are defined by plugins.
      links.push(...this.$.jsAPI.getAdminMenuLinks().map(link => ({
        url: link.url,
        name: link.text,
        children: [],
        noBaseUrl: link.url[0] === '/',
        view: null,
        viewableToAll: true,
      })));

      links = links.filter(filterFn);

      const filteredLinks = [];
      for (const link of links) {
        const linkCopy = Object.assign({}, link);
        linkCopy.children = linkCopy.children ?
            linkCopy.children.filter(filterFn) : [];
        if (linkCopy.name === 'Repositories' && this._repoName) {
          linkCopy.subsection = {
            name: this._repoName,
            view: Gerrit.Nav.View.REPO,
            url: Gerrit.Nav.getUrlForRepo(this._repoName),
            children: [{
              name: 'Access',
              view: Gerrit.Nav.View.REPO,
              detailType: Gerrit.Nav.RepoDetailView.ACCESS,
              url: Gerrit.Nav.getUrlForRepoAccess(this._repoName),
            },
            {
              name: 'Commands',
              view: Gerrit.Nav.View.REPO,
              detailType: Gerrit.Nav.RepoDetailView.COMMANDS,
              url: Gerrit.Nav.getUrlForRepoCommands(this._repoName),
            },
            {
              name: 'Branches',
              view: Gerrit.Nav.View.REPO,
              detailType: Gerrit.Nav.RepoDetailView.BRANCHES,
              url: Gerrit.Nav.getUrlForRepoBranches(this._repoName),
            },
            {
              name: 'Tags',
              view: Gerrit.Nav.View.REPO,
              detailType: Gerrit.Nav.RepoDetailView.TAGS,
              url: Gerrit.Nav.getUrlForRepoTags(this._repoName),
            },
            {
              name: 'Dashboards',
              view: Gerrit.Nav.View.REPO,
              detailType: Gerrit.Nav.RepoDetailView.DASHBOARDS,
              url: Gerrit.Nav.getUrlForRepoDashboards(this._repoName),
            }],
          };
        }
        if (linkCopy.name === 'Groups' && this._groupId && this._groupName) {
          linkCopy.subsection = {
            name: this._groupName,
            view: Gerrit.Nav.View.GROUP,
            url: Gerrit.Nav.getUrlForGroup(this._groupId),
            children: [],
          };
          if (this._groupIsInternal) {
            linkCopy.subsection.children.push({
              name: 'Members',
              detailType: Gerrit.Nav.GroupDetailView.MEMBERS,
              view: Gerrit.Nav.View.GROUP,
              url: Gerrit.Nav.getUrlForGroupMembers(this._groupId),
            });
          }
          if (this._groupIsInternal && (this._isAdmin || this._groupOwner)) {
            linkCopy.subsection.children.push(
                {
                  name: 'Audit Log',
                  detailType: Gerrit.Nav.GroupDetailView.LOG,
                  view: Gerrit.Nav.View.GROUP,
                  url: Gerrit.Nav.getUrlForGroupLog(this._groupId),
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
      const isGroupView = params.view === Gerrit.Nav.View.GROUP;
      const isRepoView = params.view === Gerrit.Nav.View.REPO;
      const isAdminView = params.view === Gerrit.Nav.View.ADMIN;

      this.set('_showGroup', isGroupView && !params.detail);
      this.set('_showGroupAuditLog', isGroupView &&
          params.detail === Gerrit.Nav.GroupDetailView.LOG);
      this.set('_showGroupMembers', isGroupView &&
          params.detail === Gerrit.Nav.GroupDetailView.MEMBERS);

      this.set('_showGroupList', isAdminView &&
          params.adminView === 'gr-admin-group-list');

      this.set('_showRepoAccess', isRepoView &&
          params.detail === Gerrit.Nav.RepoDetailView.ACCESS);
      this.set('_showRepoCommands', isRepoView &&
          params.detail === Gerrit.Nav.RepoDetailView.COMMANDS);
      this.set('_showRepoDetailList', isRepoView &&
          (params.detail === Gerrit.Nav.RepoDetailView.BRANCHES ||
           params.detail === Gerrit.Nav.RepoDetailView.TAGS));
      this.set('_showRepoDashboards', isRepoView &&
          params.detail === Gerrit.Nav.RepoDetailView.DASHBOARDS);
      this.set('_showRepoMain', isRepoView && !params.detail);

      this.set('_showRepoList', isAdminView &&
          params.adminView === 'gr-repo-list');

      this.set('_showPluginList', isAdminView &&
          params.adminView === 'gr-plugin-list');

      if (params.repo !== this._repoName) {
        this._repoName = params.repo || '';
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
      if (link.target || !link.noBaseUrl) {
        return link.url;
      }
      return this._computeRelativeURL(link.url);
    },

    /**
     * @param {string} itemView
     * @param {Object} params
     * @param {string=} opt_detailType
     */
    _computeSelectedClass(itemView, params, opt_detailType) {
      // Group params are structured differently from admin params. Compute
      // selected differently for groups.
      // TODO(wyatta): Simplify this when all routes work like group params.
      if (params.view === Gerrit.Nav.View.GROUP &&
          itemView === Gerrit.Nav.View.GROUP) {
        if (!params.detail && !opt_detailType) { return 'selected'; }
        if (params.detail === opt_detailType) { return 'selected'; }
        return '';
      }

      if (params.view === Gerrit.Nav.View.REPO &&
          itemView === Gerrit.Nav.View.REPO) {
        if (!params.detail && !opt_detailType) { return 'selected'; }
        if (params.detail === opt_detailType) { return 'selected'; }
        return '';
      }

      if (params.detailType && params.detailType !== opt_detailType) {
        return '';
      }
      return itemView === params.adminView ? 'selected' : '';
    },

    _computeGroupName(groupId) {
      if (!groupId) { return ''; }

      const promises = [];
      this.$.restAPI.getGroupConfig(groupId).then(group => {
        if (!group || !group.name) { return; }

        this._groupName = group.name;
        this._groupIsInternal = !!group.id.match(INTERNAL_GROUP_REGEX);
        this.reload();

        promises.push(this.$.restAPI.getIsAdmin().then(isAdmin => {
          this._isAdmin = isAdmin;
        }));

        promises.push(this.$.restAPI.getIsGroupOwner(group.name).then(
            isOwner => {
              this._groupOwner = isOwner;
            }));

        return Promise.all(promises).then(() => {
          this.reload();
        });
      });
    },

    _updateGroupName(e) {
      this._groupName = e.detail.name;
      this.reload();
    },

    _adminMobile(mobile) {
      return mobile ? 'adminMobile' : '';
    },
  });
})();
