/**
@license
Copyright (C) 2017 The Android Open Source Project

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
import '../../../../@polymer/polymer/polymer-legacy.js';

import '../../../behaviors/base-url-behavior/base-url-behavior.js';
import '../../../behaviors/gr-admin-nav-behavior/gr-admin-nav-behavior.js';
import '../../../behaviors/gr-url-encoding-behavior/gr-url-encoding-behavior.js';
import '../../../styles/gr-menu-page-styles.js';
import '../../../styles/gr-page-nav-styles.js';
import '../../../styles/shared-styles.js';
import '../../core/gr-navigation/gr-navigation.js';
import '../../shared/gr-dropdown-list/gr-dropdown-list.js';
import '../../shared/gr-icons/gr-icons.js';
import '../../shared/gr-js-api-interface/gr-js-api-interface.js';
import '../../shared/gr-page-nav/gr-page-nav.js';
import '../../shared/gr-rest-api-interface/gr-rest-api-interface.js';
import '../gr-admin-group-list/gr-admin-group-list.js';
import '../gr-group/gr-group.js';
import '../gr-group-audit-log/gr-group-audit-log.js';
import '../gr-group-members/gr-group-members.js';
import '../gr-plugin-list/gr-plugin-list.js';
import '../gr-repo/gr-repo.js';
import '../gr-repo-access/gr-repo-access.js';
import '../gr-repo-commands/gr-repo-commands.js';
import '../gr-repo-dashboards/gr-repo-dashboards.js';
import '../gr-repo-detail-list/gr-repo-detail-list.js';
import '../gr-repo-list/gr-repo-list.js';


const INTERNAL_GROUP_REGEX = /^[\da-f]{40}$/;

Polymer({
  _template: Polymer.html`
    <style include="shared-styles"></style>
    <style include="gr-menu-page-styles"></style>
    <style include="gr-page-nav-styles">
      gr-dropdown-list {
        --trigger-style: {
          text-transform: none;
        }
      }
      .breadcrumbText {
        /* Same as dropdown trigger so chevron spacing is consistent. */
        padding: 5px 4px;
      }
      iron-icon {
        margin: 0 .2em;
      }
      .breadcrumb {
        align-items: center;
        display: flex;
      }
      .mainHeader {
        align-items: baseline;
        border-bottom: 1px solid var(--border-color);
        display: flex;
      }
      .selectText {
        display: none;
      }
      .selectText.show {
        display: inline-block;
      }
      main.breadcrumbs:not(.table) {
        margin-top: 1em;
      }
    </style>
    <gr-page-nav class="navStyles">
      <ul class="sectionContent">
        <template id="adminNav" is="dom-repeat" items="[[_filteredLinks]]">
          <li class\$="sectionTitle [[_computeSelectedClass(item.view, params)]]">
            <a class="title" href="[[_computeLinkURL(item)]]" rel="noopener">[[item.name]]</a>
          </li>
          <template is="dom-repeat" items="[[item.children]]" as="child">
            <li class\$="[[_computeSelectedClass(child.view, params)]]">
              <a href\$="[[_computeLinkURL(child)]]" rel="noopener">[[child.name]]</a>
            </li>
          </template>
          <template is="dom-if" if="[[item.subsection]]">
            <!--If a section has a subsection, render that.-->
            <li class\$="[[_computeSelectedClass(item.subsection.view, params)]]">
              <a class="title" href\$="[[_computeLinkURL(item.subsection)]]" rel="noopener">
                [[item.subsection.name]]</a>
            </li>
            <!--Loop through the links in the sub-section.-->
            <template is="dom-repeat" items="[[item.subsection.children]]" as="child">
              <li class\$="subsectionItem [[_computeSelectedClass(child.view, params, child.detailType)]]">
                <a href\$="[[_computeLinkURL(child)]]">[[child.name]]</a>
              </li>
            </template>
          </template>
        </template>
      </ul>
    </gr-page-nav>
    <template is="dom-if" if="[[_subsectionLinks.length]]">
      <section class="mainHeader">
        <span class="breadcrumb">
          <span class="breadcrumbText">[[_breadcrumbParentName]]</span>
          <iron-icon icon="gr-icons:chevron-right"></iron-icon>
        </span>
        <gr-dropdown-list lowercase="" id="pageSelect" value="[[_computeSelectValue(params)]]" items="[[_subsectionLinks]]" on-value-change="_handleSubsectionChange">
        </gr-dropdown-list>
      </section>
    </template>
    <template is="dom-if" if="[[_showRepoList]]" restamp="true">
      <main class="table">
        <gr-repo-list class="table" params="[[params]]"></gr-repo-list>
      </main>
    </template>
    <template is="dom-if" if="[[_showGroupList]]" restamp="true">
      <main class="table">
        <gr-admin-group-list class="table" params="[[params]]">
        </gr-admin-group-list>
      </main>
    </template>
    <template is="dom-if" if="[[_showPluginList]]" restamp="true">
      <main class="table">
        <gr-plugin-list class="table" params="[[params]]"></gr-plugin-list>
      </main>
    </template>
    <template is="dom-if" if="[[_showRepoMain]]" restamp="true">
      <main class="breadcrumbs">
        <gr-repo repo="[[params.repo]]"></gr-repo>
      </main>
    </template>
    <template is="dom-if" if="[[_showGroup]]" restamp="true">
      <main class="breadcrumbs">
        <gr-group group-id="[[params.groupId]]" on-name-changed="_updateGroupName"></gr-group>
      </main>
    </template>
    <template is="dom-if" if="[[_showGroupMembers]]" restamp="true">
      <main class="breadcrumbs">
        <gr-group-members group-id="[[params.groupId]]"></gr-group-members>
      </main>
    </template>
    <template is="dom-if" if="[[_showRepoDetailList]]" restamp="true">
      <main class="table breadcrumbs">
        <gr-repo-detail-list params="[[params]]" class="table"></gr-repo-detail-list>
      </main>
    </template>
    <template is="dom-if" if="[[_showGroupAuditLog]]" restamp="true">
      <main class="table breadcrumbs">
        <gr-group-audit-log group-id="[[params.groupId]]" class="table"></gr-group-audit-log>
      </main>
    </template>
    <template is="dom-if" if="[[_showRepoCommands]]" restamp="true">
      <main class="breadcrumbs">
        <gr-repo-commands repo="[[params.repo]]"></gr-repo-commands>
      </main>
    </template>
    <template is="dom-if" if="[[_showRepoAccess]]" restamp="true">
      <main class="breadcrumbs">
        <gr-repo-access path="[[path]]" repo="[[params.repo]]"></gr-repo-access>
      </main>
    </template>
    <template is="dom-if" if="[[_showRepoDashboards]]" restamp="true">
      <main class="table breadcrumbs">
        <gr-repo-dashboards repo="[[params.repo]]"></gr-repo-dashboards>
      </main>
    </template>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
    <gr-js-api-interface id="jsAPI"></gr-js-api-interface>
`,

  is: 'gr-admin-view',

  properties: {
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
  },

  behaviors: [
    Gerrit.AdminNavBehavior,
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
  },

  _computeSelectValue(params) {
    if (!params || !params.view) { return; }
    return params.view + (params.detail || '');
  },

  _selectedIsCurrentPage(selected) {
    return (selected.parent === (this._repoName || this._groupId) &&
        selected.view === this.params.view &&
        selected.detailType === this.params.detail);
  },

  _handleSubsectionChange(e) {
    const selected = this._subsectionLinks
        .find(section => section.value === e.detail.value);

    // This is when it gets set initially.
    if (this._selectedIsCurrentPage(selected)) {
      return;
    }
    Gerrit.Nav.navigateToRelativeUrl(selected.url);
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
  },

  _computeSelectedTitle(params) {
    return this.getSelectedTitle(params.view);
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
  }
});
