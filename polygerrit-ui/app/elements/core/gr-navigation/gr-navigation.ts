/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {
  BasePatchSetNum,
  BranchName,
  ChangeConfigInfo,
  ChangeInfo,
  CommentLinks,
  CommitId,
  DashboardId,
  EDIT,
  GroupId,
  Hashtag,
  NumericChangeId,
  PARENT,
  PatchSetNum,
  RepoName,
  RevisionPatchSetNum,
  ServerInfo,
  TopicName,
  UrlEncodedCommentId,
} from '../../../types/common';
import {GerritView} from '../../../services/router/router-model';
import {ParsedChangeInfo} from '../../../types/types';

// Navigation parameters object format:
//
// Each object has a `view` property with a value from GerritNav.View. The
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

export interface DashboardSection {
  name: string;
  query: string;
  suffixForDashboard?: string;
  selfOnly?: boolean;
  hideIfEmpty?: boolean;
  results?: ChangeInfo[];
}

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
  // Non-WIP open changes owned by viewed user. Filter out changes ignored
  // by the viewing user.
  name: 'Outgoing reviews',
  query: 'is:open owner:${user} -is:wip -is:ignored',
  suffixForDashboard: 'limit:25',
};
const INCOMING: DashboardSection = {
  // Non-WIP open changes not owned by the viewed user, that the viewed user
  // is associated with as a reviewer. Changes ignored by the viewing user are
  // filtered out.
  name: 'Incoming reviews',
  query: 'is:open -owner:${user} -is:wip -is:ignored reviewer:${user}',
  suffixForDashboard: 'limit:25',
};
const CCED: DashboardSection = {
  // Open changes the viewed user is CCed on. Changes ignored by the viewing
  // user are filtered out.
  name: 'CCed on',
  query: 'is:open -is:ignored -is:wip cc:${user}',
  suffixForDashboard: 'limit:10',
};
export const CLOSED: DashboardSection = {
  name: 'Recently closed',
  // Closed changes where viewed user is owner or reviewer.
  // Changes ignored by the viewing user are filtered out, and so are WIP
  // changes not owned by the viewing user (the one instance of
  // 'owner:self' is intentional and implements this logic).
  query:
    'is:closed -is:ignored (-is:wip OR owner:self) ' +
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

export interface GenerateUrlSearchViewParameters {
  view: GerritView.SEARCH;
  query?: string;
  offset?: number;
  project?: RepoName;
  branch?: BranchName;
  topic?: TopicName;
  // TODO(TS): Define more precise type (enum?)
  statuses?: string[];
  hashtag?: string;
  host?: string;
  owner?: string;
}

export interface GenerateUrlChangeViewParameters {
  view: GerritView.CHANGE;
  // TODO(TS): NumericChangeId - not sure about it, may be it can be removed
  changeNum: NumericChangeId;
  project: RepoName;
  patchNum?: RevisionPatchSetNum;
  basePatchNum?: BasePatchSetNum;
  edit?: boolean;
  host?: string;
  messageHash?: string;
  commentId?: UrlEncodedCommentId;
  forceReload?: boolean;
  tab?: string;
  /** regular expression for filtering check runs */
  filter?: string;
  /** regular expression for selecting check runs */
  select?: string;
  /** selected attempt for selected check runs */
  attempt?: number;
}

export interface GenerateUrlRepoViewParameters {
  view: GerritView.REPO;
  repoName: RepoName;
  detail?: RepoDetailView;
}

export interface GenerateUrlDashboardViewParameters {
  view: GerritView.DASHBOARD;
  user?: string;
  repo?: RepoName;
  dashboard?: DashboardId;

  // TODO(TS): properties bellow aren't set anywhere, try to remove
  project?: RepoName;
  sections?: DashboardSection[];
  title?: string;
}

export interface GenerateUrlGroupViewParameters {
  view: GerritView.GROUP;
  groupId: GroupId;
  detail?: GroupDetailView;
}

export interface GenerateUrlEditViewParameters {
  view: GerritView.EDIT;
  changeNum: NumericChangeId;
  project: RepoName;
  path: string;
  patchNum: RevisionPatchSetNum;
  lineNum?: number | string;
}

export interface GenerateUrlRootViewParameters {
  view: GerritView.ROOT;
}

export interface GenerateUrlSettingsViewParameters {
  view: GerritView.SETTINGS;
}

export interface GenerateUrlDiffViewParameters {
  view: GerritView.DIFF;
  changeNum: NumericChangeId;
  project: RepoName;
  path?: string;
  patchNum?: RevisionPatchSetNum;
  basePatchNum?: BasePatchSetNum;
  lineNum?: number | string;
  leftSide?: boolean;
  commentId?: UrlEncodedCommentId;
  // TODO(TS): remove - property is set but never used
  commentLink?: boolean;
}

export type GenerateUrlParameters =
  | GenerateUrlSearchViewParameters
  | GenerateUrlChangeViewParameters
  | GenerateUrlRepoViewParameters
  | GenerateUrlDashboardViewParameters
  | GenerateUrlGroupViewParameters
  | GenerateUrlEditViewParameters
  | GenerateUrlRootViewParameters
  | GenerateUrlSettingsViewParameters
  | GenerateUrlDiffViewParameters;

export function isGenerateUrlChangeViewParameters(
  x: GenerateUrlParameters
): x is GenerateUrlChangeViewParameters {
  return x.view === GerritView.CHANGE;
}

export function isGenerateUrlEditViewParameters(
  x: GenerateUrlParameters
): x is GenerateUrlEditViewParameters {
  return x.view === GerritView.EDIT;
}

export function isGenerateUrlDiffViewParameters(
  x: GenerateUrlParameters
): x is GenerateUrlDiffViewParameters {
  return x.view === GerritView.DIFF;
}

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

export enum GroupDetailView {
  MEMBERS = 'members',
  LOG = 'log',
}

export enum RepoDetailView {
  GENERAL = 'general',
  ACCESS = 'access',
  BRANCHES = 'branches',
  COMMANDS = 'commands',
  DASHBOARDS = 'dashboards',
  TAGS = 'tags',
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
}

interface ChangeUrlParams extends NavigateToChangeParams {
  messageHash?: string;
}

// TODO(dmfilippov) Convert to class, extract consts, give better name and
// expose as a service from appContext
export const GerritNav = {
  View: GerritView,

  GroupDetailView,

  RepoDetailView,

  WeblinkType,

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

  getUrlForSearchQuery(query: string, offset?: number) {
    return this._getUrlFor({
      view: GerritView.SEARCH,
      query,
      offset,
    });
  },

  /**
   * @param openOnly When true, only search open changes in the project.
   * @param host The host in which to search.
   */
  getUrlForProjectChanges(
    project: RepoName,
    openOnly?: boolean,
    host?: string
  ) {
    return this._getUrlFor({
      view: GerritView.SEARCH,
      project,
      statuses: openOnly ? ['open'] : [],
      host,
    });
  },

  /**
   * @param status The status to search.
   * @param host The host in which to search.
   */
  getUrlForBranch(
    branch: BranchName,
    project: RepoName,
    status?: string,
    host?: string
  ) {
    return this._getUrlFor({
      view: GerritView.SEARCH,
      branch,
      project,
      statuses: status ? [status] : undefined,
      host,
    });
  },

  /**
   * @param topic The name of the topic.
   * @param host The host in which to search.
   */
  getUrlForTopic(topic: TopicName, host?: string) {
    return this._getUrlFor({
      view: GerritView.SEARCH,
      topic,
      host,
    });
  },

  /**
   * @param hashtag The name of the hashtag.
   */
  getUrlForHashtag(hashtag: Hashtag) {
    return this._getUrlFor({
      view: GerritView.SEARCH,
      hashtag,
      statuses: ['open', 'merged'],
    });
  },

  /**
   * Navigate to a search for changes with the given status.
   */
  navigateToStatusSearch(status: string) {
    this._navigate(
      this._getUrlFor({
        view: GerritView.SEARCH,
        statuses: [status],
      })
    );
  },

  /**
   * Navigate to a search query
   */
  navigateToSearchQuery(query: string, offset?: number) {
    this._navigate(this.getUrlForSearchQuery(query, offset));
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
    change: Pick<ChangeInfo, '_number' | 'project' | 'internalHost'>,
    options: ChangeUrlParams = {}
  ) {
    let {patchNum, basePatchNum, isEdit, messageHash, forceReload} = options;
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
      host: change.internalHost || undefined,
      messageHash,
      forceReload,
    });
  },

  getUrlForChangeById(
    changeNum: NumericChangeId,
    project: RepoName,
    patchNum?: RevisionPatchSetNum
  ) {
    return this._getUrlFor({
      view: GerritView.CHANGE,
      changeNum,
      project,
      patchNum,
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
    change: Pick<ChangeInfo, '_number' | 'project' | 'internalHost'>,
    options: NavigateToChangeParams = {}
  ) {
    const {patchNum, basePatchNum, isEdit, forceReload, redirect} = options;
    this._navigate(
      this.getUrlForChange(change, {
        patchNum,
        basePatchNum,
        isEdit,
        forceReload,
      }),
      redirect
    );
  },

  /**
   * @param basePatchNum The string PARENT can be used for none.
   */
  getUrlForDiff(
    change: ChangeInfo | ParsedChangeInfo,
    filePath: string,
    patchNum?: RevisionPatchSetNum,
    basePatchNum?: BasePatchSetNum,
    lineNum?: number
  ) {
    return this.getUrlForDiffById(
      change._number,
      change.project,
      filePath,
      patchNum,
      basePatchNum,
      lineNum
    );
  },

  getUrlForComment(
    changeNum: NumericChangeId,
    project: RepoName,
    commentId: UrlEncodedCommentId
  ) {
    return this._getUrlFor({
      view: GerritView.DIFF,
      changeNum,
      project,
      commentId,
    });
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
  getUrlForDiffById(
    changeNum: NumericChangeId,
    project: RepoName,
    filePath: string,
    patchNum?: RevisionPatchSetNum,
    basePatchNum?: BasePatchSetNum,
    lineNum?: number,
    leftSide?: boolean
  ) {
    if (basePatchNum === PARENT) {
      basePatchNum = undefined;
    }

    this._checkPatchRange(patchNum, basePatchNum);
    return this._getUrlFor({
      view: GerritView.DIFF,
      changeNum,
      project,
      path: filePath,
      patchNum,
      basePatchNum,
      lineNum,
      leftSide,
    });
  },

  getEditUrlForDiff(
    change: ChangeInfo | ParsedChangeInfo,
    filePath: string,
    patchNum?: RevisionPatchSetNum,
    lineNum?: number
  ) {
    return this.getEditUrlForDiffById(
      change._number,
      change.project,
      filePath,
      patchNum,
      lineNum
    );
  },

  /**
   * @param patchNum The patchNum the file content should be based on, or
   *   ${EDIT} if left undefined.
   * @param lineNum The line number to pass to the inline editor.
   */
  getEditUrlForDiffById(
    changeNum: NumericChangeId,
    project: RepoName,
    filePath: string,
    patchNum?: RevisionPatchSetNum,
    lineNum?: number
  ) {
    return this._getUrlFor({
      view: GerritView.EDIT,
      changeNum,
      project,
      path: filePath,
      patchNum: patchNum || EDIT,
      lineNum,
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
      this.getUrlForDiff(change, filePath, patchNum, basePatchNum, lineNum)
    );
  },

  /**
   * @param owner The name of the owner.
   */
  getUrlForOwner(owner: string) {
    return this._getUrlFor({
      view: GerritView.SEARCH,
      owner,
    });
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

  getUrlForRoot() {
    return this._getUrlFor({
      view: GerritView.ROOT,
    });
  },

  /**
   * @param repo The name of the repo.
   * @param dashboard The ID of the dashboard, in the form of '<ref>:<path>'.
   */
  getUrlForRepoDashboard(repo: RepoName, dashboard: DashboardId) {
    return this._getUrlFor({
      view: GerritView.DASHBOARD,
      repo,
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

  getUrlForRepo(repoName: RepoName) {
    return this._getUrlFor({
      view: GerritView.REPO,
      detail: RepoDetailView.GENERAL,
      repoName,
    });
  },

  /**
   * Navigate to a repo settings page.
   */
  navigateToRepo(repoName: RepoName) {
    this._navigate(this.getUrlForRepo(repoName));
  },

  getUrlForRepoTags(repoName: RepoName) {
    return this._getUrlFor({
      view: GerritView.REPO,
      repoName,
      detail: RepoDetailView.TAGS,
    });
  },

  getUrlForRepoBranches(repoName: RepoName) {
    return this._getUrlFor({
      view: GerritView.REPO,
      repoName,
      detail: GerritNav.RepoDetailView.BRANCHES,
    });
  },

  getUrlForRepoAccess(repoName: RepoName) {
    return this._getUrlFor({
      view: GerritView.REPO,
      repoName,
      detail: GerritNav.RepoDetailView.ACCESS,
    });
  },

  getUrlForRepoCommands(repoName: RepoName) {
    return this._getUrlFor({
      view: GerritView.REPO,
      repoName,
      detail: GerritNav.RepoDetailView.COMMANDS,
    });
  },

  getUrlForRepoDashboards(repoName: RepoName) {
    return this._getUrlFor({
      view: GerritView.REPO,
      repoName,
      detail: GerritNav.RepoDetailView.DASHBOARDS,
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
      detail: GerritNav.GroupDetailView.LOG,
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
