<!--
@license
Copyright (C) 2018 The Android Open Source Project

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->
<script>
(function(window) {
  'use strict';

  const ADMIN_LINKS = [{
    name: 'Repositories',
    noBaseUrl: true,
    url: '/admin/repos',
    view: 'gr-repo-list',
    viewableToAll: true,
  }, {
    name: 'Groups',
    section: 'Groups',
    noBaseUrl: true,
    url: '/admin/groups',
    view: 'gr-admin-group-list',
  }, {
    name: 'Plugins',
    capability: 'viewPlugins',
    section: 'Plugins',
    noBaseUrl: true,
    url: '/admin/plugins',
    view: 'gr-plugin-list',
  }];

  window.Gerrit = window.Gerrit || {};

  /** @polymerBehavior Gerrit.AdminNavBehavior */
  Gerrit.AdminNavBehavior = {
    /**
     * @param {!Object} account
     * @param {!Function} getAccountCapabilities
     * @param {!Function} getAdminMenuLinks
     *  Possible aguments in options:
     *    repoName?: string
     *    groupId?: string,
     *    groupName?: string,
     *    groupIsInternal?: boolean,
     *    isAdmin?: boolean,
     *    groupOwner?: boolean,
     * @param {!Object=} opt_options
     * @return {Promise<!Object>}
     */
    getAdminLinks(account, getAccountCapabilities, getAdminMenuLinks,
        opt_options) {
      if (!account) {
        return Promise.resolve(this._filterLinks(link => link.viewableToAll,
            getAdminMenuLinks, opt_options));
      }
      return getAccountCapabilities()
          .then(capabilities => this._filterLinks(
              link => !link.capability
              || capabilities.hasOwnProperty(link.capability),
              getAdminMenuLinks,
              opt_options));
    },

    /**
     * @param {!Function} filterFn
     * @param {!Function} getAdminMenuLinks
     *  Possible aguments in options:
     *    repoName?: string
     *    groupId?: string,
     *    groupName?: string,
     *    groupIsInternal?: boolean,
     *    isAdmin?: boolean,
     *    groupOwner?: boolean,
     * @param {!Object|undefined} opt_options
     * @return {Promise<!Object>}
     */
    _filterLinks(filterFn, getAdminMenuLinks, opt_options) {
      let links = ADMIN_LINKS.slice(0);
      let expandedSection;

      const isExernalLink = link => link.url[0] !== '/';

      // Append top-level links that are defined by plugins.
      links.push(...getAdminMenuLinks().map(link => {
        return {
          url: link.url,
          name: link.text,
          capability: link.capability || null,
          noBaseUrl: !isExernalLink(link),
          view: null,
          viewableToAll: !link.capability,
          target: isExernalLink(link) ? '_blank' : null,
        };
      }));

      links = links.filter(filterFn);

      const filteredLinks = [];
      const repoName = opt_options && opt_options.repoName;
      const groupId = opt_options && opt_options.groupId;
      const groupName = opt_options && opt_options.groupName;
      const groupIsInternal = opt_options && opt_options.groupIsInternal;
      const isAdmin = opt_options && opt_options.isAdmin;
      const groupOwner = opt_options && opt_options.groupOwner;

      // Don't bother to get sub-navigation items if only the top level links
      // are needed. This is used by the main header dropdown.
      if (!repoName && !groupId) { return {links, expandedSection}; }

      // Otherwise determine the full set of links and return both the full
      // set in addition to the subsection that should be displayed if it
      // exists.
      for (const link of links) {
        const linkCopy = Object.assign({}, link);
        if (linkCopy.name === 'Repositories' && repoName) {
          linkCopy.subsection = this.getRepoSubsections(repoName);
          expandedSection = linkCopy.subsection;
        } else if (linkCopy.name === 'Groups' && groupId && groupName) {
          linkCopy.subsection = this.getGroupSubsections(groupId, groupName,
              groupIsInternal, isAdmin, groupOwner);
          expandedSection = linkCopy.subsection;
        }
        filteredLinks.push(linkCopy);
      }
      return {links: filteredLinks, expandedSection};
    },

    getGroupSubsections(groupId, groupName, groupIsInternal, isAdmin,
        groupOwner) {
      const subsection = {
        name: groupName,
        view: Gerrit.Nav.View.GROUP,
        url: Gerrit.Nav.getUrlForGroup(groupId),
        children: [],
      };
      if (groupIsInternal) {
        subsection.children.push({
          name: 'Members',
          detailType: Gerrit.Nav.GroupDetailView.MEMBERS,
          view: Gerrit.Nav.View.GROUP,
          url: Gerrit.Nav.getUrlForGroupMembers(groupId),
        });
      }
      if (groupIsInternal && (isAdmin || groupOwner)) {
        subsection.children.push(
            {
              name: 'Audit Log',
              detailType: Gerrit.Nav.GroupDetailView.LOG,
              view: Gerrit.Nav.View.GROUP,
              url: Gerrit.Nav.getUrlForGroupLog(groupId),
            }
        );
      }
      return subsection;
    },

    getRepoSubsections(repoName) {
      return {
        name: repoName,
        view: Gerrit.Nav.View.REPO,
        url: Gerrit.Nav.getUrlForRepo(repoName),
        children: [{
          name: 'Access',
          view: Gerrit.Nav.View.REPO,
          detailType: Gerrit.Nav.RepoDetailView.ACCESS,
          url: Gerrit.Nav.getUrlForRepoAccess(repoName),
        },
        {
          name: 'Commands',
          view: Gerrit.Nav.View.REPO,
          detailType: Gerrit.Nav.RepoDetailView.COMMANDS,
          url: Gerrit.Nav.getUrlForRepoCommands(repoName),
        },
        {
          name: 'Branches',
          view: Gerrit.Nav.View.REPO,
          detailType: Gerrit.Nav.RepoDetailView.BRANCHES,
          url: Gerrit.Nav.getUrlForRepoBranches(repoName),
        },
        {
          name: 'Tags',
          view: Gerrit.Nav.View.REPO,
          detailType: Gerrit.Nav.RepoDetailView.TAGS,
          url: Gerrit.Nav.getUrlForRepoTags(repoName),
        },
        {
          name: 'Dashboards',
          view: Gerrit.Nav.View.REPO,
          detailType: Gerrit.Nav.RepoDetailView.DASHBOARDS,
          url: Gerrit.Nav.getUrlForRepoDashboards(repoName),
        }],
      };
    },
  };

  // eslint-disable-next-line no-unused-vars
  function defineEmptyMixin() {
    // This is a temporary function.
    // Polymer linter doesn't process correctly the following code:
    // class MyElement extends Polymer.mixinBehaviors([legacyBehaviors], ...) {...}
    // To workaround this issue, the mock mixin is declared in this method.
    // In the following changes, legacy behaviors will be converted to mixins.

    /**
     * @polymer
     * @mixinFunction
     */
    Gerrit.AdminNavMixin = base =>
      class extends base {
      };
  }
})(window);
</script>
