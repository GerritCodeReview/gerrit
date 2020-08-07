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
import {
  GerritNav,
  GerritView,
  RepoDetailView,
  GroupDetailView,
} from '../elements/core/gr-navigation/gr-navigation';
import {
  RepositoryName,
  GroupId,
  AccountDetailInfo,
  CapabilityInfo,
} from '../types/common';
import {MenuLink} from '../elements/plugins/gr-admin-api/gr-admin-api';

const ADMIN_LINKS: NavLink[] = [
  {
    name: 'Repositories',
    noBaseUrl: true,
    url: '/admin/repos',
    view: 'gr-repo-list',
    viewableToAll: true,
  },
  {
    name: 'Groups',
    section: 'Groups',
    noBaseUrl: true,
    url: '/admin/groups',
    view: 'gr-admin-group-list',
  },
  {
    name: 'Plugins',
    capability: 'viewPlugins',
    section: 'Plugins',
    noBaseUrl: true,
    url: '/admin/plugins',
    view: 'gr-plugin-list',
  },
];

export function getAdminLinks(
  account: AccountDetailInfo,
  getAccountCapabilities: (params?: string[]) => Promise<CapabilityInfo>,
  getAdminMenuLinks: () => MenuLink[],
  options?: AdminNavLinksOption
) {
  if (!account) {
    return Promise.resolve(
      _filterLinks(link => !!link.viewableToAll, getAdminMenuLinks, options)
    );
  }
  return getAccountCapabilities().then(capabilities =>
    _filterLinks(
      link =>
        !link.capability ||
        Object.prototype.hasOwnProperty.call(capabilities, link.capability),
      getAdminMenuLinks,
      options
    )
  );
}

function _filterLinks(
  filterFn: (link: NavLink) => boolean,
  getAdminMenuLinks: () => MenuLink[],
  options?: AdminNavLinksOption
) {
  let links = ADMIN_LINKS.slice(0);
  let expandedSection;

  const isExternalLink = (link: MenuLink) => link.url[0] !== '/';

  // Append top-level links that are defined by plugins.
  links.push(
    ...getAdminMenuLinks().map((link: MenuLink) => {
      return {
        url: link.url,
        name: link.text,
        capability: link.capability || null,
        noBaseUrl: !isExternalLink(link),
        view: null,
        viewableToAll: !link.capability,
        target: isExternalLink(link) ? '_blank' : null,
      } as NavLink;
    })
  );

  links = links.filter(filterFn);

  const filteredLinks = [];
  const repoName = options && options.repoName;
  const groupId = options && options.groupId;
  const groupName = options && options.groupName;
  const groupIsInternal = options && options.groupIsInternal;
  const isAdmin = options && options.isAdmin;
  const groupOwner = options && options.groupOwner;

  // Don't bother to get sub-navigation items if only the top level links
  // are needed. This is used by the main header dropdown.
  if (!repoName && !groupId) {
    return {links, expandedSection};
  }

  // Otherwise determine the full set of links and return both the full
  // set in addition to the subsection that should be displayed if it
  // exists.
  for (const link of links) {
    let linkCopy;
    if (link.name === 'Repositories' && repoName) {
      linkCopy = {...link, subsection: getRepoSubsections(repoName)};
      expandedSection = linkCopy.subsection;
    } else if (link.name === 'Groups' && groupId && groupName) {
      linkCopy = {
        ...link,
        subsection: getGroupSubsections(
          groupId,
          groupName,
          groupIsInternal,
          isAdmin,
          groupOwner
        ),
      };
      expandedSection = linkCopy.subsection;
    }
    filteredLinks.push(linkCopy);
  }
  return {links: filteredLinks, expandedSection};
}

export function getGroupSubsections(
  groupId: GroupId,
  groupName: string,
  groupIsInternal?: boolean,
  isAdmin?: boolean,
  groupOwner?: boolean
) {
  const subsection = {
    name: groupName,
    view: GerritNav.View.GROUP,
    url: GerritNav.getUrlForGroup(groupId),
    children: [] as SubsectionsChildren[],
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
    subsection.children.push({
      name: 'Audit Log',
      detailType: GerritNav.GroupDetailView.LOG,
      view: GerritNav.View.GROUP,
      url: GerritNav.getUrlForGroupLog(groupId),
    });
  }
  return subsection;
}

export function getRepoSubsections(repoName: RepositoryName) {
  return {
    name: repoName,
    view: GerritNav.View.REPO,
    url: GerritNav.getUrlForRepo(repoName),
    children: [
      {
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
      },
    ],
  };
}

export interface SubsectionsChildren {
  name: string;
  view: GerritView;
  detailType: RepoDetailView | GroupDetailView;
  url: string;
}

export interface AdminNavLinksOption {
  repoName?: RepositoryName;
  groupId?: GroupId;
  groupName?: string;
  groupIsInternal?: boolean;
  isAdmin?: boolean;
  groupOwner?: boolean;
}

export interface NavLink {
  name: string;
  noBaseUrl: boolean;
  url: string;
  view: string | null;
  viewableToAll?: boolean;
  section?: string;
  capability?: string;
  target?: string | null;
}
