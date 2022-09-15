/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {
  BasePatchSetNum,
  ChangeConfigInfo,
  ChangeInfo,
  CommentLinks,
  CommitId,
  DashboardId,
  GroupId,
  NumericChangeId,
  PARENT,
  PatchSetNum,
  RepoName,
  RevisionPatchSetNum,
  ServerInfo,
  UrlEncodedCommentId,
} from '../../../types/common';
import {GerritView} from '../../../services/router/router-model';
import {ParsedChangeInfo} from '../../../types/types';
import {
  DashboardSection,
  GenerateUrlParameters,
} from '../../../utils/router-util';
import {RepoDetailView} from '../../../models/views/repo';
import {GroupDetailView} from '../../../models/views/group';
import {createSearchUrl} from '../../../models/views/search';
import {createDiffUrl} from '../../../models/views/diff';

// Navigation parameters object format:
//
// Each object has a `view` property with a value from GerritView. The
// remaining properties depend on the value used for view.
// GenerateUrlParameters lists all the possible view parameters.

const uninitialized = () => {
  console.warn('Use of uninitialized routing');
};

const uninitializedNavigate: NavigateCallback = () => {
  uninitialized();
  return '';
};

const uninitializedGenerateUrl: GenerateUrlCallback = () => {
  uninitialized();
  return '';
};

const uninitializedGenerateWebLinks: GenerateWebLinksCallback = () => {
  uninitialized();
  return [];
};

const uninitializedMapCommentLinks: MapCommentLinksCallback = () => {
  uninitialized();
  return {};
};

const USER_PLACEHOLDER_PATTERN = /\${user}/g;

export interface UserDashboardConfig {
  change?: ChangeConfigInfo;
}

export interface UserDashboard {
  title?: string;
  sections: DashboardSection[];
}

// NOTE: These queries are tested in Java. Any changes made to definitions
// here require corresponding changes to:
// java/com/google/gerrit/httpd/raw/IndexPreloadingUtil.java
const HAS_DRAFTS: DashboardSection = {
  // Changes with unpublished draft comments. This section is omitted when
  // viewing other users, so we don't need to filter anything out.
  name: 'Has draft comments',
  query: 'has:draft',
  selfOnly: true,
  hideIfEmpty: true,
  suffixForDashboard: 'limit:10',
};
export const YOUR_TURN: DashboardSection = {
  // Changes where the user is in the attention set.
  name: 'Your Turn',
  query: 'attention:${user}',
  hideIfEmpty: false,
  suffixForDashboard: 'limit:25',
};
const WIP: DashboardSection = {
  // WIP open changes owned by viewing user. This section is omitted when
  // viewing other users, so we don't need to filter anything out.
  name: 'Work in progress',
  query: 'is:open owner:${user} is:wip',
  selfOnly: true,
  hideIfEmpty: true,
  suffixForDashboard: 'limit:25',
};
export const OUTGOING: DashboardSection = {
  // Non-WIP open changes owned by viewed user.
  name: 'Outgoing reviews',
  query: 'is:open owner:${user} -is:wip',
  suffixForDashboard: 'limit:25',
};
const INCOMING: DashboardSection = {
  // Non-WIP open changes not owned by the viewed user, that the viewed user
  // is associated with as a reviewer.
  name: 'Incoming reviews',
  query: 'is:open -owner:${user} -is:wip reviewer:${user}',
  suffixForDashboard: 'limit:25',
};
const CCED: DashboardSection = {
  // Open changes the viewed user is CCed on.
  name: 'CCed on',
  query: 'is:open -is:wip cc:${user}',
  suffixForDashboard: 'limit:10',
};
export const CLOSED: DashboardSection = {
  name: 'Recently closed',
  // Closed changes where viewed user is owner or reviewer.
  // WIP changes not owned by the viewing user (the one instance of
  // 'owner:self' is intentional and implements this logic) are filtered out.
  query:
    'is:closed (-is:wip OR owner:self) ' +
    '(owner:${user} OR reviewer:${user} OR cc:${user})',
  suffixForDashboard: '-age:4w limit:10',
};
const DEFAULT_SECTIONS: DashboardSection[] = [
  HAS_DRAFTS,
  YOUR_TURN,
  WIP,
  OUTGOING,
  INCOMING,
  CCED,
  CLOSED,
];

export interface GenerateWebLinksOptions {
  weblinks?: GeneratedWebLink[];
  config?: ServerInfo;
}

export interface GenerateWebLinksPatchsetParameters {
  type: WeblinkType.PATCHSET;
  repo: RepoName;
  commit?: CommitId;
  options?: GenerateWebLinksOptions;
}
export interface GenerateWebLinksResolveConflictsParameters {
  type: WeblinkType.RESOLVE_CONFLICTS;
  repo: RepoName;
  commit?: CommitId;
  options?: GenerateWebLinksOptions;
}
export interface GenerateWebLinksEditParameters {
  type: WeblinkType.EDIT;
  repo: RepoName;
  commit: CommitId;
  file: string;
  options?: GenerateWebLinksOptions;
}
export interface GenerateWebLinksFileParameters {
  type: WeblinkType.FILE;
  repo: RepoName;
  commit: CommitId;
  file: string;
  options?: GenerateWebLinksOptions;
}
export interface GenerateWebLinksChangeParameters {
  type: WeblinkType.CHANGE;
  repo: RepoName;
  commit: CommitId;
  options?: GenerateWebLinksOptions;
}

export type GenerateWebLinksParameters =
  | GenerateWebLinksPatchsetParameters
  | GenerateWebLinksResolveConflictsParameters
  | GenerateWebLinksEditParameters
  | GenerateWebLinksFileParameters
  | GenerateWebLinksChangeParameters;

export type NavigateCallback = (target: string, redirect?: boolean) => void;
export type GenerateUrlCallback = (params: GenerateUrlParameters) => string;
// TODO: Refactor to return only GeneratedWebLink[]
export type GenerateWebLinksCallback = (
  params: GenerateWebLinksParameters
) => GeneratedWebLink[] | GeneratedWebLink;

export type MapCommentLinksCallback = (patterns: CommentLinks) => CommentLinks;

export interface WebLink {
  name?: string;
  label: string;
  url: string;
}

export interface GeneratedWebLink {
  name?: string;
  label?: string;
  url?: string;
}

export enum WeblinkType {
  CHANGE = 'change',
  EDIT = 'edit',
  FILE = 'file',
  PATCHSET = 'patchset',
  RESOLVE_CONFLICTS = 'resolve-conflicts',
}

interface NavigateToChangeParams {
  patchNum?: RevisionPatchSetNum;
  basePatchNum?: BasePatchSetNum;
  isEdit?: boolean;
  redirect?: boolean;
  forceReload?: boolean;
  openReplyDialog?: boolean;
}

interface ChangeUrlParams extends NavigateToChangeParams {
  messageHash?: string;
  usp?: string;
}

// TODO(dmfilippov) Convert to class, extract consts, give better name and
// expose as a service from appContext
export const GerritNav = {
  _navigate: uninitializedNavigate,

  _generateUrl: uninitializedGenerateUrl,

  _generateWeblinks: uninitializedGenerateWebLinks,

  mapCommentlinks: uninitializedMapCommentLinks,

  _checkPatchRange(patchNum?: PatchSetNum, basePatchNum?: BasePatchSetNum) {
    if (basePatchNum && !patchNum) {
      throw new Error('Cannot use base patch number without patch number.');
    }
  },

  /**
   * Setup router implementation.
   *
   * @param navigate the router-abstracted equivalent of
   *     `window.location.href = ...` or window.location.replace(...). The
   *     string is a new location and boolean defines is it redirect or not
   *     (true means redirect, i.e. equivalent of window.location.replace).
   * @param generateUrl generates a URL given
   *     navigation parameters, detailed in the file header.
   * @param generateWeblinks weblinks generator
   *     function takes single payload parameter with type property that
   *  determines which
   *     part of the UI is the consumer of the weblinks. type property can
   *     be one of file, change, or patchset.
   *     - For file type, payload will also contain string properties: repo,
   *         commit, file.
   *     - For patchset type, payload will also contain string properties:
   *         repo, commit.
   *     - For change type, payload will also contain string properties:
   *         repo, commit. If server provides weblinks, those will be passed
   *         as options.weblinks property on the main payload object.
   * @param mapCommentlinks provides an escape
   *     hatch to modify the commentlinks object, e.g. if it contains any
   *     relative URLs.
   */
  setup(
    navigate: NavigateCallback,
    generateUrl: GenerateUrlCallback,
    generateWeblinks: GenerateWebLinksCallback,
    mapCommentlinks: MapCommentLinksCallback
  ) {
    this._navigate = navigate;
    this._generateUrl = generateUrl;
    this._generateWeblinks = generateWeblinks;
    this.mapCommentlinks = mapCommentlinks;
  },

  destroy() {
    this._navigate = uninitializedNavigate;
    this._generateUrl = uninitializedGenerateUrl;
    this._generateWeblinks = uninitializedGenerateWebLinks;
    this.mapCommentlinks = uninitializedMapCommentLinks;
  },

  /**
   * Generate a URL for the given route parameters.
   */
  _getUrlFor(params: GenerateUrlParameters) {
    return this._generateUrl(params);
  },

  /**
   * Navigate to a search for changes with the given status.
   */
  navigateToStatusSearch(status: string) {
    this._navigate(createSearchUrl({statuses: [status]}));
  },

  /**
   * Navigate to a search query
   */
  navigateToSearchQuery(query: string, offset?: number) {
    this._navigate(createSearchUrl({query, offset}));
  },

  /**
   * Navigate to the user's dashboard
   */
  navigateToUserDashboard() {
    this._navigate(this.getUrlForUserDashboard('self'));
  },

  /**
   * @param basePatchNum The string PARENT can be used for none.
   */
  getUrlForChange(
    change: Pick<ChangeInfo, '_number' | 'project'>,
    options: ChangeUrlParams = {}
  ) {
    let {
      patchNum,
      basePatchNum,
      isEdit,
      messageHash,
      forceReload,
      openReplyDialog,
      usp,
    } = options;
    if (basePatchNum === PARENT) {
      basePatchNum = undefined;
    }

    this._checkPatchRange(patchNum, basePatchNum);
    return this._getUrlFor({
      view: GerritView.CHANGE,
      changeNum: change._number,
      project: change.project,
      patchNum,
      basePatchNum,
      edit: isEdit,
      messageHash,
      forceReload,
      openReplyDialog,
      usp,
    });
  },

  /**
   * @param basePatchNum The string PARENT can be used for none.
   * @param redirect redirect to a change - if true, the current
   *     location (i.e. page which makes redirect) is not added to a history.
   *     I.e. back/forward buttons skip current location
   * @param forceReload Some views are smart about how to handle the reload
   *     of the view. In certain cases we want to force the view to reload
   *     and re-render everything.
   */
  navigateToChange(
    change: Pick<ChangeInfo, '_number' | 'project'>,
    options: NavigateToChangeParams = {}
  ) {
    const {
      patchNum,
      basePatchNum,
      isEdit,
      forceReload,
      redirect,
      openReplyDialog,
    } = options;
    this._navigate(
      this.getUrlForChange(change, {
        patchNum,
        basePatchNum,
        isEdit,
        forceReload,
        openReplyDialog,
      }),
      redirect
    );
  },

  getUrlForCommentsTab(
    changeNum: NumericChangeId,
    project: RepoName,
    commentId: UrlEncodedCommentId
  ) {
    return this._getUrlFor({
      view: GerritView.CHANGE,
      changeNum,
      project,
      commentId,
    });
  },

  /**
   * @param basePatchNum The string PARENT can be used for none.
   */
  navigateToDiff(
    change: ChangeInfo | ParsedChangeInfo,
    filePath: string,
    patchNum?: RevisionPatchSetNum,
    basePatchNum?: BasePatchSetNum,
    lineNum?: number
  ) {
    this._navigate(
      createDiffUrl({
        changeNum: change._number,
        project: change.project,
        path: filePath,
        patchNum,
        basePatchNum,
        lineNum,
      })
    );
  },

  /**
   * @param user The name of the user.
   */
  getUrlForUserDashboard(user: string) {
    return this._getUrlFor({
      view: GerritView.DASHBOARD,
      user,
    });
  },

  /**
   * @param repo The name of the repo.
   * @param dashboard The ID of the dashboard, in the form of '<ref>:<path>'.
   */
  getUrlForRepoDashboard(repo: RepoName, dashboard: DashboardId) {
    return this._getUrlFor({
      view: GerritView.DASHBOARD,
      project: repo,
      dashboard,
    });
  },

  /**
   * Navigate to an arbitrary relative URL.
   */
  navigateToRelativeUrl(relativeUrl: string) {
    if (!relativeUrl.startsWith('/')) {
      throw new Error('navigateToRelativeUrl with non-relative URL');
    }
    this._navigate(relativeUrl);
  },

  getUrlForRepo(repo: RepoName) {
    return this._getUrlFor({
      view: GerritView.REPO,
      detail: RepoDetailView.GENERAL,
      repo,
    });
  },

  /**
   * Navigate to a repo settings page.
   */
  navigateToRepo(repoName: RepoName) {
    this._navigate(this.getUrlForRepo(repoName));
  },

  getUrlForRepoTags(repo: RepoName) {
    return this._getUrlFor({
      view: GerritView.REPO,
      repo,
      detail: RepoDetailView.TAGS,
    });
  },

  getUrlForRepoBranches(repo: RepoName) {
    return this._getUrlFor({
      view: GerritView.REPO,
      repo,
      detail: RepoDetailView.BRANCHES,
    });
  },

  getUrlForRepoAccess(repo: RepoName) {
    return this._getUrlFor({
      view: GerritView.REPO,
      repo,
      detail: RepoDetailView.ACCESS,
    });
  },

  getUrlForRepoCommands(repo: RepoName) {
    return this._getUrlFor({
      view: GerritView.REPO,
      repo,
      detail: RepoDetailView.COMMANDS,
    });
  },

  getUrlForRepoDashboards(repo: RepoName) {
    return this._getUrlFor({
      view: GerritView.REPO,
      repo,
      detail: RepoDetailView.DASHBOARDS,
    });
  },

  getUrlForGroup(groupId: GroupId) {
    return this._getUrlFor({
      view: GerritView.GROUP,
      groupId,
    });
  },

  getUrlForGroupLog(groupId: GroupId) {
    return this._getUrlFor({
      view: GerritView.GROUP,
      groupId,
      detail: GroupDetailView.LOG,
    });
  },

  getUrlForGroupMembers(groupId: GroupId) {
    return this._getUrlFor({
      view: GerritView.GROUP,
      groupId,
      detail: GroupDetailView.MEMBERS,
    });
  },

  getUrlForSettings() {
    return this._getUrlFor({view: GerritView.SETTINGS});
  },

  getEditWebLinks(
    repo: RepoName,
    commit: CommitId,
    file: string,
    options?: GenerateWebLinksOptions
  ): GeneratedWebLink[] {
    const params: GenerateWebLinksEditParameters = {
      type: WeblinkType.EDIT,
      repo,
      commit,
      file,
    };
    if (options) {
      params.options = options;
    }
    return ([] as GeneratedWebLink[]).concat(this._generateWeblinks(params));
  },

  getFileWebLinks(
    repo: RepoName,
    commit: CommitId,
    file: string,
    options?: GenerateWebLinksOptions
  ): GeneratedWebLink[] {
    const params: GenerateWebLinksFileParameters = {
      type: WeblinkType.FILE,
      repo,
      commit,
      file,
    };
    if (options) {
      params.options = options;
    }
    return ([] as GeneratedWebLink[]).concat(this._generateWeblinks(params));
  },

  getPatchSetWeblink(
    repo: RepoName,
    commit?: CommitId,
    options?: GenerateWebLinksOptions
  ): GeneratedWebLink {
    const params: GenerateWebLinksPatchsetParameters = {
      type: WeblinkType.PATCHSET,
      repo,
      commit,
    };
    if (options) {
      params.options = options;
    }
    const result = this._generateWeblinks(params);
    if (Array.isArray(result)) {
      // TODO(TS): Unclear what to do with empty array.
      // Either write a comment why result can't be empty or change the return
      // type or add a check.
      return result.pop()!;
    } else {
      return result;
    }
  },

  getResolveConflictsWeblinks(
    repo: RepoName,
    commit?: CommitId,
    options?: GenerateWebLinksOptions
  ): GeneratedWebLink[] {
    const params: GenerateWebLinksResolveConflictsParameters = {
      type: WeblinkType.RESOLVE_CONFLICTS,
      repo,
      commit,
    };
    if (options) {
      params.options = options;
    }
    return ([] as GeneratedWebLink[]).concat(this._generateWeblinks(params));
  },

  getChangeWeblinks(
    repo: RepoName,
    commit: CommitId,
    options?: GenerateWebLinksOptions
  ): GeneratedWebLink[] {
    const params: GenerateWebLinksChangeParameters = {
      type: WeblinkType.CHANGE,
      repo,
      commit,
    };
    if (options) {
      params.options = options;
    }
    return ([] as GeneratedWebLink[]).concat(this._generateWeblinks(params));
  },

  getUserDashboard(
    user = 'self',
    sections = DEFAULT_SECTIONS,
    title = ''
  ): UserDashboard {
    sections = sections
      .filter(section => user === 'self' || !section.selfOnly)
      .map(section => {
        return {
          ...section,
          name: section.name,
          query: section.query.replace(USER_PLACEHOLDER_PATTERN, user),
        };
      });
    return {title, sections};
  },
};
