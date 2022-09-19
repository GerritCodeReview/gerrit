/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {
  BasePatchSetNum,
  ChangeConfigInfo,
  ChangeInfo,
  CommitId,
  PatchSetNum,
  RepoName,
  RevisionPatchSetNum,
} from '../../../types/common';
import {ParsedChangeInfo} from '../../../types/types';
import {createRepoUrl} from '../../../models/views/repo';
import {createSearchUrl} from '../../../models/views/search';
import {createDiffUrl} from '../../../models/views/diff';
import {
  createDashboardUrl,
  DashboardSection,
} from '../../../models/views/dashboard';
import {createChangeUrl} from '../../../models/views/change';
import {
  MapCommentLinksCallback,
  GenerateWebLinksOptions,
  GeneratedWebLink,
  WeblinkType,
  GenerateWebLinksPatchsetParameters,
  GenerateWebLinksResolveConflictsParameters,
  GenerateWebLinksChangeParameters,
  generateWeblinks,
} from '../../../utils/weblink-util';

const uninitialized = () => {
  console.warn('Use of uninitialized routing');
};

const uninitializedNavigate: NavigateCallback = () => {
  uninitialized();
  return '';
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

export type NavigateCallback = (target: string, redirect?: boolean) => void;

interface NavigateToChangeParams {
  patchNum?: RevisionPatchSetNum;
  basePatchNum?: BasePatchSetNum;
  isEdit?: boolean;
  redirect?: boolean;
  forceReload?: boolean;
  openReplyDialog?: boolean;
}

// TODO(dmfilippov) Convert to class, extract consts, give better name and
// expose as a service from appContext
export const GerritNav = {
  _navigate: uninitializedNavigate,

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
  setup(navigate: NavigateCallback, mapCommentlinks: MapCommentLinksCallback) {
    this._navigate = navigate;
    this.mapCommentlinks = mapCommentlinks;
  },

  destroy() {
    this._navigate = uninitializedNavigate;
    this.mapCommentlinks = uninitializedMapCommentLinks;
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
    this._navigate(createDashboardUrl({user: 'self'}));
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
      createChangeUrl({
        changeNum: change._number,
        project: change.project,
        patchNum,
        basePatchNum,
        edit: isEdit,
        forceReload,
        openReplyDialog,
      }),
      redirect
    );
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
   * Navigate to an arbitrary relative URL.
   */
  navigateToRelativeUrl(relativeUrl: string) {
    if (!relativeUrl.startsWith('/')) {
      throw new Error('navigateToRelativeUrl with non-relative URL');
    }
    this._navigate(relativeUrl);
  },

  /**
   * Navigate to a repo settings page.
   */
  navigateToRepo(repo: RepoName) {
    this._navigate(createRepoUrl({repo}));
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
    const result = generateWeblinks(params);
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
    return ([] as GeneratedWebLink[]).concat(generateWeblinks(params));
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
    return ([] as GeneratedWebLink[]).concat(generateWeblinks(params));
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
