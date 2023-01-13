/**
 * @license
 * Copyright 2018 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {
  RepoName,
  GroupId,
  AccountDetailInfo,
  AccountCapabilityInfo,
} from '../types/common';
import {hasOwnProperty} from './common-util';
import {GerritView} from '../services/router/router-model';
import {MenuLink} from '../api/admin';
import {AdminChildView, createAdminUrl} from '../models/views/admin';
import {createGroupUrl, GroupDetailView} from '../models/views/group';
import {createRepoUrl, RepoDetailView} from '../models/views/repo';

const ADMIN_LINKS: NavLink[] = [
  {
    name: 'Repositories',
    noBaseUrl: true,
    url: createAdminUrl({adminView: AdminChildView.REPOS}),
    view: 'gr-repo-list' as GerritView,
    viewableToAll: true,
  },
  {
    name: 'Groups',
    section: 'Groups',
    noBaseUrl: true,
    url: createAdminUrl({adminView: AdminChildView.GROUPS}),
    view: 'gr-admin-group-list' as GerritView,
  },
  {
    name: 'Plugins',
    capability: 'viewPlugins',
    section: 'Plugins',
    noBaseUrl: true,
    url: createAdminUrl({adminView: AdminChildView.PLUGINS}),
    view: 'gr-plugin-list' as GerritView,
  },
];

export interface AdminLink {
  url: string;
  text: string;
  capability: string | null;
  noBaseUrl: boolean;
  view: null;
  viewableToAll: boolean;
  target: '_blank' | null;
}

export interface AdminLinks {
  links: NavLink[];
  expandedSection?: SubsectionInterface;
}

export function getAdminLinks(
  account: AccountDetailInfo | undefined,
  getAccountCapabilities: () => Promise<AccountCapabilityInfo>,
  getAdminMenuLinks: () => MenuLink[],
  options?: AdminNavLinksOption
): Promise<AdminLinks> {
  if (!account) {
    return Promise.resolve(
      filterLinks(link => !!link.viewableToAll, getAdminMenuLinks, options)
    );
  }
  return getAccountCapabilities().then(capabilities =>
    filterLinks(
      link => !link.capability || hasOwnProperty(capabilities, link.capability),
      getAdminMenuLinks,
      options
    )
  );
}

function filterLinks(
  filterFn: (link: NavLink) => boolean,
  getAdminMenuLinks: () => MenuLink[],
  options?: AdminNavLinksOption
): AdminLinks {
  let links: NavLink[] = ADMIN_LINKS.slice(0);
  let expandedSection: SubsectionInterface | undefined = undefined;

  const isExternalLink = (link: MenuLink) => link.url[0] !== '/';

  // Append top-level links that are defined by plugins.
  links.push(
    ...getAdminMenuLinks().map((link: MenuLink) => {
      return {
        url: link.url,
        name: link.text,
        capability: link.capability || undefined,
        noBaseUrl: !isExternalLink(link),
        view: undefined,
        viewableToAll: !link.capability,
        target: isExternalLink(link) ? '_blank' : null,
      };
    })
  );

  links = links.filter(filterFn);

  const filteredLinks: NavLink[] = [];
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
    const linkCopy = {...link};
    if (linkCopy.name === 'Repositories' && repoName) {
      linkCopy.subsection = getRepoSubsections(repoName);
      expandedSection = linkCopy.subsection;
    } else if (linkCopy.name === 'Groups' && groupId && groupName) {
      linkCopy.subsection = getGroupSubsections(
        groupId,
        groupName,
        groupIsInternal,
        isAdmin,
        groupOwner
      );
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
  const children: SubsectionInterface[] = [];
  const subsection: SubsectionInterface = {
    name: groupName,
    view: GerritView.GROUP,
    url: createGroupUrl({groupId}),
    children,
  };
  if (groupIsInternal) {
    children.push({
      name: 'Members',
      detailType: GroupDetailView.MEMBERS,
      view: GerritView.GROUP,
      url: createGroupUrl({groupId, detail: GroupDetailView.MEMBERS}),
    });
  }
  if (groupIsInternal && (isAdmin || groupOwner)) {
    children.push({
      name: 'Audit Log',
      detailType: GroupDetailView.LOG,
      view: GerritView.GROUP,
      url: createGroupUrl({groupId, detail: GroupDetailView.LOG}),
    });
  }
  return subsection;
}

export function getRepoSubsections(repo: RepoName) {
  return {
    name: repo,
    view: GerritView.REPO,
    children: [
      {
        name: 'General',
        view: GerritView.REPO,
        detailType: RepoDetailView.GENERAL,
        url: createRepoUrl({repo, detail: RepoDetailView.GENERAL}),
      },
      {
        name: 'Access',
        view: GerritView.REPO,
        detailType: RepoDetailView.ACCESS,
        url: createRepoUrl({repo, detail: RepoDetailView.ACCESS}),
      },
      {
        name: 'Commands',
        view: GerritView.REPO,
        detailType: RepoDetailView.COMMANDS,
        url: createRepoUrl({repo, detail: RepoDetailView.COMMANDS}),
      },
      {
        name: 'Branches',
        view: GerritView.REPO,
        detailType: RepoDetailView.BRANCHES,
        url: createRepoUrl({repo, detail: RepoDetailView.BRANCHES}),
      },
      {
        name: 'Tags',
        view: GerritView.REPO,
        detailType: RepoDetailView.TAGS,
        url: createRepoUrl({repo, detail: RepoDetailView.TAGS}),
      },
      {
        name: 'Dashboards',
        view: GerritView.REPO,
        detailType: RepoDetailView.DASHBOARDS,
        url: createRepoUrl({repo, detail: RepoDetailView.DASHBOARDS}),
      },
    ],
  };
}

export interface SubsectionInterface {
  name: string;
  view: GerritView;
  detailType?: RepoDetailView | GroupDetailView;
  url?: string;
  children?: SubsectionInterface[];
}

export interface AdminNavLinksOption {
  repoName?: RepoName;
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
  view?: GerritView | AdminChildView;
  viewableToAll?: boolean;
  section?: string;
  capability?: string;
  target?: string | null;
  subsection?: SubsectionInterface;
  children?: SubsectionInterface[];
}
