/**
 * @license
 * Copyright (C) 2018 The Android Open Source Project
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
import {GerritNav} from '../elements/core/gr-navigation/gr-navigation.js';

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
export function getAdminLinks(account, getAccountCapabilities,
    getAdminMenuLinks, opt_options) {
  if (!account) {
    return Promise.resolve(_filterLinks(link => link.viewableToAll,
        getAdminMenuLinks, opt_options));
  }
  return getAccountCapabilities()
      .then(capabilities => _filterLinks(
          link => !link.capability
          || capabilities.hasOwnProperty(link.capability),
          getAdminMenuLinks,
          opt_options));
}

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
function _filterLinks(filterFn, getAdminMenuLinks, opt_options) {
  let links = ADMIN_LINKS.slice(0);
  let expandedSection;

  const isExternalLink = link => link.url[0] !== '/';

  // Append top-level links that are defined by plugins.
  links.push(...getAdminMenuLinks().map(link => {
    return {
      url: link.url,
      name: link.text,
      capability: link.capability || null,
      noBaseUrl: !isExternalLink(link),
      view: null,
      viewableToAll: !link.capability,
      target: isExternalLink(link) ? '_blank' : null,
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
    const linkCopy = {...link};
    if (linkCopy.name === 'Repositories' && repoName) {
      linkCopy.subsection = getRepoSubsections(repoName);
      expandedSection = linkCopy.subsection;
    } else if (linkCopy.name === 'Groups' && groupId && groupName) {
      linkCopy.subsection = getGroupSubsections(groupId, groupName,
          groupIsInternal, isAdmin, groupOwner);
      expandedSection = linkCopy.subsection;
    }
    filteredLinks.push(linkCopy);
  }
  return {links: filteredLinks, expandedSection};
}

export function getGroupSubsections(groupId, groupName, groupIsInternal,
    isAdmin, groupOwner) {
  const subsection = {
    name: groupName,
    view: GerritNav.View.GROUP,
    url: GerritNav.getUrlForGroup(groupId),
    children: [],
  };
  if (groupIsInternal) {
    subsection.children.push({
      name: 'Members',
      detailType: GerritNav.GroupDetailView.MEMBERS,
      view: GerritNav.View.GROUP,
      url: GerritNav.getUrlForGroupMembers(groupId),
    });
  }
  if (groupIsInternal && (isAdmin || groupOwner)) {
    subsection.children.push(
        {
          name: 'Audit Log',
          detailType: GerritNav.GroupDetailView.LOG,
          view: GerritNav.View.GROUP,
          url: GerritNav.getUrlForGroupLog(groupId),
        }
    );
  }
  return subsection;
}

export function getRepoSubsections(repoName) {
  return {
    name: repoName,
    view: GerritNav.View.REPO,
    url: GerritNav.getUrlForRepo(repoName),
    children: [{
      name: 'Access',
      view: GerritNav.View.REPO,
      detailType: GerritNav.RepoDetailView.ACCESS,
      url: GerritNav.getUrlForRepoAccess(repoName),
    },
    {
      name: 'Commands',
      view: GerritNav.View.REPO,
      detailType: GerritNav.RepoDetailView.COMMANDS,
      url: GerritNav.getUrlForRepoCommands(repoName),
    },
    {
      name: 'Branches',
      view: GerritNav.View.REPO,
      detailType: GerritNav.RepoDetailView.BRANCHES,
      url: GerritNav.getUrlForRepoBranches(repoName),
    },
    {
      name: 'Tags',
      view: GerritNav.View.REPO,
      detailType: GerritNav.RepoDetailView.TAGS,
      url: GerritNav.getUrlForRepoTags(repoName),
    },
    {
      name: 'Dashboards',
      view: GerritNav.View.REPO,
      detailType: GerritNav.RepoDetailView.DASHBOARDS,
      url: GerritNav.getUrlForRepoDashboards(repoName),
    }],
  };
}
