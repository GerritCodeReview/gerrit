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

  const INTERNAL_GROUP_REGEX = /^[\da-f]{40}$/;

  /**
   * @appliesMixin Gerrit.AdminNavMixin
   * @appliesMixin Gerrit.BaseUrlMixin
   * @appliesMixin Gerrit.URLEncodingMixin
   */
  class GrAdminView extends Polymer.mixinBehaviors( [
    Gerrit.AdminNavBehavior,
    Gerrit.BaseUrlBehavior,
    Gerrit.URLEncodingBehavior,
  ], Polymer.GestureEventListeners(
      Polymer.LegacyElementMixin(
          Polymer.Element))) {
    static get is() { return 'gr-admin-view'; }

    static get properties() {
      return {
      /** @type {?} */
        params: Object,
        path: String,
        adminView: String,

        _breadcrumbParentName: String,
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
        _subsectionLinks: Array,
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
      };
    }

    static get observers() {
      return [
        '_paramsChanged(params)',
      ];
    }

    attached() {
      super.attached();
      this.reload();
    }

    reload() {
      const promises = [
        this.$.restAPI.getAccount(),
        Gerrit.awaitPluginsLoaded(),
      ];
      return Promise.all(promises).then(result => {
        this._account = result[0];
        let options;
        if (this._repoName) {
          options = {repoName: this._repoName};
        } else if (this._groupId) {
          options = {
            groupId: this._groupId,
            groupName: this._groupName,
            groupIsInternal: this._groupIsInternal,
            isAdmin: this._isAdmin,
            groupOwner: this._groupOwner,
          };
        }

        return this.getAdminLinks(this._account,
            this.$.restAPI.getAccountCapabilities.bind(this.$.restAPI),
            this.$.jsAPI.getAdminMenuLinks.bind(this.$.jsAPI),
            options)
            .then(res => {
              this._filteredLinks = res.links;
              this._breadcrumbParentName = res.expandedSection ?
                res.expandedSection.name : '';

              if (!res.expandedSection) {
                this._subsectionLinks = [];
                return;
              }
              this._subsectionLinks = [res.expandedSection]
                  .concat(res.expandedSection.children).map(section => {
                    return {
                      text: !section.detailType ? 'Home' : section.name,
                      value: section.view + (section.detailType || ''),
                      view: section.view,
                      url: section.url,
                      detailType: section.detailType,
                      parent: this._groupId || this._repoName || '',
                    };
                  });
            });
      });
    }

    _computeSelectValue(params) {
      if (!params || !params.view) { return; }
      return params.view + (params.detail || '');
    }

    _selectedIsCurrentPage(selected) {
      return (selected.parent === (this._repoName || this._groupId) &&
          selected.view === this.params.view &&
          selected.detailType === this.params.detail);
    }

    _handleSubsectionChange(e) {
      const selected = this._subsectionLinks
          .find(section => section.value === e.detail.value);

      // This is when it gets set initially.
      if (this._selectedIsCurrentPage(selected)) {
        return;
      }
      Gerrit.Nav.navigateToRelativeUrl(selected.url);
    }

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

      let needsReload = false;
      if (params.repo !== this._repoName) {
        this._repoName = params.repo || '';
        // Reloads the admin menu.
        needsReload = true;
      }
      if (params.groupId !== this._groupId) {
        this._groupId = params.groupId || '';
        // Reloads the admin menu.
        needsReload = true;
      }
      if (this._breadcrumbParentName && !params.groupId && !params.repo) {
        needsReload = true;
      }
      if (!needsReload) { return; }
      this.reload();
    }

    // TODO (beckysiegel): Update these functions after router abstraction is
    // updated. They are currently copied from gr-dropdown (and should be
    // updated there as well once complete).
    _computeURLHelper(host, path) {
      return '//' + host + this.getBaseUrl() + path;
    }

    _computeRelativeURL(path) {
      const host = window.location.host;
      return this._computeURLHelper(host, path);
    }

    _computeLinkURL(link) {
      if (!link || typeof link.url === 'undefined') { return ''; }
      if (link.target || !link.noBaseUrl) {
        return link.url;
      }
      return this._computeRelativeURL(link.url);
    }

    /**
     * @param {string} itemView
     * @param {Object} params
     * @param {string=} opt_detailType
     */
    _computeSelectedClass(itemView, params, opt_detailType) {
      if (!params) return '';
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
    }

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
    }

    _updateGroupName(e) {
      this._groupName = e.detail.name;
      this.reload();
    }
  }

  customElements.define(GrAdminView.is, GrAdminView);
})();
