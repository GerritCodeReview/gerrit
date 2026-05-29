/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {GerritView} from '../../services/router/router-model';
import {getBaseUrl} from '../../utils/url-util';
import {define} from '../dependency';
import {Model} from '../base/model';
import {Route, ViewState} from './base';
import {
  AccountCapabilityInfo,
  AccountDetailInfo,
  GroupId,
  RepoName,
} from '../../types/common';
import {hasOwnProperty} from '../../utils/common-util';
import {MenuLink} from '../../api/admin';
import {createGroupUrl, GroupDetailView} from './group';
import {createRepoUrl, RepoDetailView} from './repo';

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
  url: string;
  view?: GerritView | AdminChildView;
  viewableToAll?: boolean;
  section?: string;
  capability?: string;
  target?: '_blank' | '_parent' | '_self' | '_top' | null;
  subsection?: SubsectionInterface;
  children?: SubsectionInterface[];
}

export const PLUGIN_LIST_ROUTE: Route<AdminViewState> = {
  urlPattern: /^\/admin\/plugins(\/)?$/,
  createState: () => {
    const state: AdminViewState = {
      view: GerritView.ADMIN,
      adminView: AdminChildView.PLUGINS,
    };
    return state;
  },
};

export const SERVER_INFO_ROUTE: Route<AdminViewState> = {
  urlPattern: /^\/admin\/server-info$/,
  createState: () => {
    const state: AdminViewState = {
      view: GerritView.ADMIN,
      adminView: AdminChildView.SERVER_INFO,
    };
    return state;
  },
};

export enum AdminChildView {
  REPOS = 'gr-repo-list',
  GROUPS = 'gr-admin-group-list',
  PLUGINS = 'gr-plugin-list',
  SERVER_INFO = 'gr-server-info',
}
const ADMIN_LINKS: NavLink[] = [
  {
    name: 'Repositories',
    url: createAdminUrl({adminView: AdminChildView.REPOS}),
    view: 'gr-repo-list' as GerritView,
    viewableToAll: true,
  },
  {
    name: 'Groups',
    section: 'Groups',
    url: createAdminUrl({adminView: AdminChildView.GROUPS}),
    view: 'gr-admin-group-list' as GerritView,
  },
  {
    name: 'Plugins',
    capability: 'viewPlugins',
    section: 'Plugins',
    url: createAdminUrl({adminView: AdminChildView.PLUGINS}),
    view: 'gr-plugin-list' as GerritView,
  },
  {
    name: 'Server Info',
    section: 'Server Info',
    url: createAdminUrl({adminView: AdminChildView.SERVER_INFO}),
    view: 'gr-server-info' as GerritView,
  },
];

export interface AdminLink {
  url: string;
  text: string;
  capability: string | null;
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
    ...getAdminMenuLinks().map((link: MenuLink): NavLink => {
      return {
        url: link.url,
        name: link.text,
        capability: link.capability || undefined,
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

function getGroupSubsections(
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

function getRepoSubsections(repo: RepoName) {
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
      {
        name: 'Submit Requirements',
        view: GerritView.REPO,
        detailType: RepoDetailView.SUBMIT_REQUIREMENTS,
        url: createRepoUrl({repo, detail: RepoDetailView.SUBMIT_REQUIREMENTS}),
      },
      {
        name: 'Labels',
        view: GerritView.REPO,
        detailType: RepoDetailView.LABELS,
        url: createRepoUrl({repo, detail: RepoDetailView.LABELS}),
      },
    ],
  };
}

export interface AdminViewState extends ViewState {
  view: GerritView.ADMIN;
  adminView: AdminChildView;
  openCreateModal?: boolean;
  filter?: string | null;
  offset?: number | string;
}

export function createAdminUrl(state: Omit<AdminViewState, 'view'>) {
  switch (state.adminView) {
    case AdminChildView.REPOS:
      return `${getBaseUrl()}/admin/repos`;
    case AdminChildView.GROUPS:
      return `${getBaseUrl()}/admin/groups`;
    case AdminChildView.PLUGINS:
      return `${getBaseUrl()}/admin/plugins`;
    case AdminChildView.SERVER_INFO:
      return `${getBaseUrl()}/admin/server-info`;
  }
}

export const adminViewModelToken = define<AdminViewModel>('admin-view-model');

export class AdminViewModel extends Model<AdminViewState | undefined> {
  constructor() {
    super(undefined);
  }
}
