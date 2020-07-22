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

// Navigation parameters object format:
//
// Each object has a `view` property with a value from GerritNav.View. The
// remaining properties depend on the value used for view.
//
//  - GerritNav.View.CHANGE:
//    - `changeNum`, required, String: the numeric ID of the change.
//    - `project`, optional, String: the project name.
//    - `patchNum`, optional, Number: the patch for the right-hand-side of
//        the diff.
//    - `basePatchNum`, optional, Number: the patch for the left-hand-side
//        of the diff. If `basePatchNum` is provided, then `patchNum` must
//        also be provided.
//    - `edit`, optional, Boolean: whether or not to load the file list with
//        edit controls.
//    - `messageHash`, optional, String: the hash of the change message to
//        scroll to.
//
// - GerritNav.View.SEARCH:
//    - `query`, optional, String: the literal search query. If provided,
//        the string will be used as the query, and all other params will be
//        ignored.
//    - `owner`, optional, String: the owner name.
//    - `project`, optional, String: the project name.
//    - `branch`, optional, String: the branch name.
//    - `topic`, optional, String: the topic name.
//    - `hashtag`, optional, String: the hashtag name.
//    - `statuses`, optional, Array<String>: the list of change statuses to
//        search for. If more than one is provided, the search will OR them
//        together.
//    - `offset`, optional, Number: the offset for the query.
//
//  - GerritNav.View.DIFF:
//    - `changeNum`, required, String: the numeric ID of the change.
//    - `path`, required, String: the filepath of the diff.
//    - `patchNum`, required, Number: the patch for the right-hand-side of
//        the diff.
//    - `basePatchNum`, optional, Number: the patch for the left-hand-side
//        of the diff. If `basePatchNum` is provided, then `patchNum` must
//        also be provided.
//    - `lineNum`, optional, Number: the line number to be selected on load.
//    - `leftSide`, optional, Boolean: if a `lineNum` is provided, a value
//        of true selects the line from base of the patch range. False by
//        default.
//
//  - GerritNav.View.GROUP:
//    - `groupId`, required, String: the ID of the group.
//    - `detail`, optional, String: the name of the group detail view.
//      Takes any value from GerritNav.GroupDetailView.
//
//  - GerritNav.View.REPO:
//    - `repoName`, required, String: the name of the repo
//    - `detail`, optional, String: the name of the repo detail view.
//      Takes any value from GerritNav.RepoDetailView.
//
//  - GerritNav.View.DASHBOARD
//    - `repo`, optional, String.
//    - `sections`, optional, Array of objects with `title` and `query`
//      strings.
//    - `user`, optional, String.
//
//  - GerritNav.View.ROOT:
//    - no possible parameters.

const uninitialized = () => {
  console.warn('Use of uninitialized routing');
};

const EDIT_PATCHNUM = 'edit';
const PARENT_PATCHNUM = 'PARENT';

const USER_PLACEHOLDER_PATTERN = /\${user}/g;

// NOTE: These queries are tested in Java. Any changes made to definitions
// here require corresponding changes to:
// java/com/google/gerrit/httpd/raw/IndexPreloadingUtil.java
const DEFAULT_SECTIONS = [
  {
    // Changes with unpublished draft comments. This section is omitted when
    // viewing other users, so we don't need to filter anything out.
    name: 'Has draft comments',
    query: 'has:draft',
    selfOnly: true,
    hideIfEmpty: true,
    suffixForDashboard: 'limit:10',
  },
  {
    // Changes where the user is in the attention set.
    name: 'Your Turn',
    query: 'attention:${user}',
    hideIfEmpty: false,
    suffixForDashboard: 'limit:25',
    attentionSetOnly: true,
  },
  {
    // Changes that are assigned to the viewed user.
    name: 'Assigned reviews',
    query: 'assignee:${user} (-is:wip OR owner:self OR assignee:self) ' +
        'is:open -is:ignored',
    hideIfEmpty: true,
    suffixForDashboard: 'limit:25',
    assigneeOnly: true,
  },
  {
    // WIP open changes owned by viewing user. This section is omitted when
    // viewing other users, so we don't need to filter anything out.
    name: 'Work in progress',
    query: 'is:open owner:${user} is:wip',
    selfOnly: true,
    hideIfEmpty: true,
    suffixForDashboard: 'limit:25',
  },
  {
    // Non-WIP open changes owned by viewed user. Filter out changes ignored
    // by the viewing user.
    name: 'Outgoing reviews',
    query: 'is:open owner:${user} -is:wip -is:ignored',
    isOutgoing: true,
    suffixForDashboard: 'limit:25',
  },
  {
    // Non-WIP open changes not owned by the viewed user, that the viewed user
    // is associated with (as either a reviewer or the assignee). Changes
    // ignored by the viewing user are filtered out.
    name: 'Incoming reviews',
    query: 'is:open -owner:${user} -is:wip -is:ignored ' +
        '(reviewer:${user} OR assignee:${user})',
    suffixForDashboard: 'limit:25',
  },
  {
    // Open changes the viewed user is CCed on. Changes ignored by the viewing
    // user are filtered out.
    name: 'CCed on',
    query: 'is:open -is:ignored cc:${user}',
    suffixForDashboard: 'limit:10',
  },
  {
    name: 'Recently closed',
    // Closed changes where viewed user is owner, reviewer, or assignee.
    // Changes ignored by the viewing user are filtered out, and so are WIP
    // changes not owned by the viewing user (the one instance of
    // 'owner:self' is intentional and implements this logic).
    query: 'is:closed -is:ignored (-is:wip OR owner:self) ' +
        '(owner:${user} OR reviewer:${user} OR assignee:${user} ' +
        'OR cc:${user})',
    suffixForDashboard: '-age:4w limit:10',
  },
];

// TODO(dmfilippov) Convert to class, extract consts, give better name and
// expose as a service from appContext
export const GerritNav = {

  View: {
    ADMIN: 'admin',
    AGREEMENTS: 'agreements',
    CHANGE: 'change',
    DASHBOARD: 'dashboard',
    DIFF: 'diff',
    DOCUMENTATION_SEARCH: 'documentation-search',
    EDIT: 'edit',
    GROUP: 'group',
    PLUGIN_SCREEN: 'plugin-screen',
    REPO: 'repo',
    ROOT: 'root',
    SEARCH: 'search',
    SETTINGS: 'settings',
  },

  GroupDetailView: {
    MEMBERS: 'members',
    LOG: 'log',
  },

  RepoDetailView: {
    ACCESS: 'access',
    BRANCHES: 'branches',
    COMMANDS: 'commands',
    DASHBOARDS: 'dashboards',
    TAGS: 'tags',
  },

  WeblinkType: {
    CHANGE: 'change',
    FILE: 'file',
    PATCHSET: 'patchset',
  },

  /** @type {Function} */
  _navigate: uninitialized,

  /** @type {Function} */
  _generateUrl: uninitialized,

  /** @type {Function} */
  _generateWeblinks: uninitialized,

  /** @type {Function} */
  mapCommentlinks: uninitialized,

  /**
   * @param {number=} patchNum
   * @param {number|string=} basePatchNum
   */
  _checkPatchRange(patchNum, basePatchNum) {
    if (basePatchNum && !patchNum) {
      throw new Error('Cannot use base patch number without patch number.');
    }
  },

  /**
   * Setup router implementation.
   *
   * @param {function(!string, boolean=)} navigate the router-abstracted equivalent of
   *     `window.location.href = ...` or window.location.replace(...). The
   *     string is a new location and boolean defines is it redirect or not
   *     (true means redirect, i.e. equivalent of window.location.replace).
   * @param {function(!Object): string} generateUrl generates a URL given
   *     navigation parameters, detailed in the file header.
   * @param {function(!Object): string} generateWeblinks weblinks generator
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
   * @param {function(!Object): Object} mapCommentlinks provides an escape
   *     hatch to modify the commentlinks object, e.g. if it contains any
   *     relative URLs.
   */
  setup(navigate, generateUrl, generateWeblinks, mapCommentlinks) {
    this._navigate = navigate;
    this._generateUrl = generateUrl;
    this._generateWeblinks = generateWeblinks;
    this.mapCommentlinks = mapCommentlinks;
  },

  destroy() {
    this._navigate = uninitialized;
    this._generateUrl = uninitialized;
    this._generateWeblinks = uninitialized;
    this.mapCommentlinks = uninitialized;
  },

  /**
   * Generate a URL for the given route parameters.
   *
   * @param {Object} params
   * @return {string}
   */
  _getUrlFor(params) {
    return this._generateUrl(params);
  },

  getUrlForSearchQuery(query, opt_offset) {
    return this._getUrlFor({
      view: GerritNav.View.SEARCH,
      query,
      offset: opt_offset,
    });
  },

  /**
   * @param {!string} project The name of the project.
   * @param {boolean=} opt_openOnly When true, only search open changes in
   *     the project.
   * @param {string=} opt_host The host in which to search.
   * @return {string}
   */
  getUrlForProjectChanges(project, opt_openOnly, opt_host) {
    return this._getUrlFor({
      view: GerritNav.View.SEARCH,
      project,
      statuses: opt_openOnly ? ['open'] : [],
      host: opt_host,
    });
  },

  /**
   * @param {string} branch The name of the branch.
   * @param {string} project The name of the project.
   * @param {string=} opt_status The status to search.
   * @param {string=} opt_host The host in which to search.
   * @return {string}
   */
  getUrlForBranch(branch, project, opt_status, opt_host) {
    return this._getUrlFor({
      view: GerritNav.View.SEARCH,
      branch,
      project,
      statuses: opt_status ? [opt_status] : undefined,
      host: opt_host,
    });
  },

  /**
   * @param {string} topic The name of the topic.
   * @param {string=} opt_host The host in which to search.
   * @return {string}
   */
  getUrlForTopic(topic, opt_host) {
    return this._getUrlFor({
      view: GerritNav.View.SEARCH,
      topic,
      statuses: ['open', 'merged'],
      host: opt_host,
    });
  },

  /**
   * @param {string} hashtag The name of the hashtag.
   * @return {string}
   */
  getUrlForHashtag(hashtag) {
    return this._getUrlFor({
      view: GerritNav.View.SEARCH,
      hashtag,
      statuses: ['open', 'merged'],
    });
  },

  /**
   * Navigate to a search for changes with the given status.
   *
   * @param {string} status
   */
  navigateToStatusSearch(status) {
    this._navigate(this._getUrlFor({
      view: GerritNav.View.SEARCH,
      statuses: [status],
    }));
  },

  /**
   * Navigate to a search query
   *
   * @param {string} query
   * @param {number=} opt_offset
   */
  navigateToSearchQuery(query, opt_offset) {
    return this._navigate(this.getUrlForSearchQuery(query, opt_offset));
  },

  /**
   * Navigate to the user's dashboard
   */
  navigateToUserDashboard() {
    return this._navigate(this.getUrlForUserDashboard('self'));
  },

  /**
   * @param {!Object} change The change object.
   * @param {number=} opt_patchNum
   * @param {number|string=} opt_basePatchNum The string 'PARENT' can be
   *     used for none.
   * @param {boolean=} opt_isEdit
   * @param {string=} opt_messageHash
   * @return {string}
   */
  getUrlForChange(change, opt_patchNum, opt_basePatchNum, opt_isEdit,
      opt_messageHash) {
    if (opt_basePatchNum === PARENT_PATCHNUM) {
      opt_basePatchNum = undefined;
    }

    this._checkPatchRange(opt_patchNum, opt_basePatchNum);
    return this._getUrlFor({
      view: GerritNav.View.CHANGE,
      changeNum: change._number,
      project: change.project,
      patchNum: opt_patchNum,
      basePatchNum: opt_basePatchNum,
      edit: opt_isEdit,
      host: change.internalHost || undefined,
      messageHash: opt_messageHash,
    });
  },

  /**
   * @param {number} changeNum
   * @param {string} project The name of the project.
   * @param {number=} opt_patchNum
   * @return {string}
   */
  getUrlForChangeById(changeNum, project, opt_patchNum) {
    return this._getUrlFor({
      view: GerritNav.View.CHANGE,
      changeNum,
      project,
      patchNum: opt_patchNum,
    });
  },

  /**
   * @param {!Object} change The change object.
   * @param {number=} opt_patchNum
   * @param {number|string=} opt_basePatchNum The string 'PARENT' can be
   *     used for none.
   * @param {boolean=} opt_isEdit
   * @param {boolean=} opt_redirect redirect to a change - if true, the current
   *     location (i.e. page which makes redirect) is not added to a history.
   *     I.e. back/forward buttons skip current location
   *
   */
  navigateToChange(change, opt_patchNum, opt_basePatchNum, opt_isEdit,
      opt_redirect) {
    this._navigate(this.getUrlForChange(change, opt_patchNum,
        opt_basePatchNum, opt_isEdit), opt_redirect);
  },

  /**
   * @param {{ _number: number, project: string }} change The change object.
   * @param {string} path The file path.
   * @param {number=} opt_patchNum
   * @param {number|string=} opt_basePatchNum The string 'PARENT' can be
   *     used for none.
   * @param {number|string=} opt_lineNum
   * @return {string}
   */
  getUrlForDiff(change, path, opt_patchNum, opt_basePatchNum, opt_lineNum) {
    return this.getUrlForDiffById(change._number, change.project, path,
        opt_patchNum, opt_basePatchNum, opt_lineNum);
  },

  /**
   * @param {number} changeNum
   * @param {string} project The name of the project.
   * @param {string} path The file path.
   * @param {number=} opt_patchNum
   * @param {number|string=} opt_basePatchNum The string 'PARENT' can be
   *     used for none.
   * @param {number=} opt_lineNum
   * @param {boolean=} opt_leftSide
   * @return {string}
   */
  getUrlForDiffById(changeNum, project, path, opt_patchNum,
      opt_basePatchNum, opt_lineNum, opt_leftSide) {
    if (opt_basePatchNum === PARENT_PATCHNUM) {
      opt_basePatchNum = undefined;
    }

    this._checkPatchRange(opt_patchNum, opt_basePatchNum);
    return this._getUrlFor({
      view: GerritNav.View.DIFF,
      changeNum,
      project,
      path,
      patchNum: opt_patchNum,
      basePatchNum: opt_basePatchNum,
      lineNum: opt_lineNum,
      leftSide: opt_leftSide,
    });
  },

  /**
   * @param {{ _number: number, project: string }} change The change object.
   * @param {string} path The file path.
   * @param {number=} opt_patchNum
   * @param {number=} opt_lineNum
   * @return {string}
   */
  getEditUrlForDiff(change, path, opt_patchNum, opt_lineNum) {
    return this.getEditUrlForDiffById(change._number, change.project, path,
        opt_patchNum, opt_lineNum);
  },

  /**
   * @param {number} changeNum
   * @param {string} project The name of the project.
   * @param {string} path The file path.
   * @param {number|string=} opt_patchNum The patchNum the file content
   *    should be based on, or ${EDIT_PATCHNUM} if left undefined.
   * @param {number=} opt_lineNum The line number to pass to the inline editor.
   * @return {string}
   */
  getEditUrlForDiffById(changeNum, project, path, opt_patchNum, opt_lineNum) {
    return this._getUrlFor({
      view: GerritNav.View.EDIT,
      changeNum,
      project,
      path,
      patchNum: opt_patchNum || EDIT_PATCHNUM,
      lineNum: opt_lineNum,
    });
  },

  /**
   * @param {!Object} change The change object.
   * @param {string} path The file path.
   * @param {number=} opt_patchNum
   * @param {number|string=} opt_basePatchNum The string 'PARENT' can be
   *     used for none.
   */
  navigateToDiff(change, path, opt_patchNum, opt_basePatchNum) {
    this._navigate(this.getUrlForDiff(change, path, opt_patchNum,
        opt_basePatchNum));
  },

  /**
   * @param {string} owner The name of the owner.
   * @return {string}
   */
  getUrlForOwner(owner) {
    return this._getUrlFor({
      view: GerritNav.View.SEARCH,
      owner,
    });
  },

  /**
   * @param {string} user The name of the user.
   * @return {string}
   */
  getUrlForUserDashboard(user) {
    return this._getUrlFor({
      view: GerritNav.View.DASHBOARD,
      user,
    });
  },

  /**
   * @return {string}
   */
  getUrlForRoot() {
    return this._getUrlFor({
      view: GerritNav.View.ROOT,
    });
  },

  /**
   * @param {string} repo The name of the repo.
   * @param {string} dashboard The ID of the dashboard, in the form of
   *     '<ref>:<path>'.
   * @return {string}
   */
  getUrlForRepoDashboard(repo, dashboard) {
    return this._getUrlFor({
      view: GerritNav.View.DASHBOARD,
      repo,
      dashboard,
    });
  },

  /**
   * Navigate to an arbitrary relative URL.
   *
   * @param {string} relativeUrl
   */
  navigateToRelativeUrl(relativeUrl) {
    if (!relativeUrl.startsWith('/')) {
      throw new Error('navigateToRelativeUrl with non-relative URL');
    }
    this._navigate(relativeUrl);
  },

  /**
   * @param {string} repoName
   * @return {string}
   */
  getUrlForRepo(repoName) {
    return this._getUrlFor({
      view: GerritNav.View.REPO,
      repoName,
    });
  },

  /**
   * Navigate to a repo settings page.
   *
   * @param {string} repoName
   */
  navigateToRepo(repoName) {
    this._navigate(this.getUrlForRepo(repoName));
  },

  /**
   * @param {string} repoName
   * @return {string}
   */
  getUrlForRepoTags(repoName) {
    return this._getUrlFor({
      view: GerritNav.View.REPO,
      repoName,
      detail: GerritNav.RepoDetailView.TAGS,
    });
  },

  /**
   * @param {string} repoName
   * @return {string}
   */
  getUrlForRepoBranches(repoName) {
    return this._getUrlFor({
      view: GerritNav.View.REPO,
      repoName,
      detail: GerritNav.RepoDetailView.BRANCHES,
    });
  },

  /**
   * @param {string} repoName
   * @return {string}
   */
  getUrlForRepoAccess(repoName) {
    return this._getUrlFor({
      view: GerritNav.View.REPO,
      repoName,
      detail: GerritNav.RepoDetailView.ACCESS,
    });
  },

  /**
   * @param {string} repoName
   * @return {string}
   */
  getUrlForRepoCommands(repoName) {
    return this._getUrlFor({
      view: GerritNav.View.REPO,
      repoName,
      detail: GerritNav.RepoDetailView.COMMANDS,
    });
  },

  /**
   * @param {string} repoName
   * @return {string}
   */
  getUrlForRepoDashboards(repoName) {
    return this._getUrlFor({
      view: GerritNav.View.REPO,
      repoName,
      detail: GerritNav.RepoDetailView.DASHBOARDS,
    });
  },

  /**
   * @param {string} groupId
   * @return {string}
   */
  getUrlForGroup(groupId) {
    return this._getUrlFor({
      view: GerritNav.View.GROUP,
      groupId,
    });
  },

  /**
   * @param {string} groupId
   * @return {string}
   */
  getUrlForGroupLog(groupId) {
    return this._getUrlFor({
      view: GerritNav.View.GROUP,
      groupId,
      detail: GerritNav.GroupDetailView.LOG,
    });
  },

  /**
   * @param {string} groupId
   * @return {string}
   */
  getUrlForGroupMembers(groupId) {
    return this._getUrlFor({
      view: GerritNav.View.GROUP,
      groupId,
      detail: GerritNav.GroupDetailView.MEMBERS,
    });
  },

  getUrlForSettings() {
    return this._getUrlFor({view: GerritNav.View.SETTINGS});
  },

  /**
   * @param {string} repo
   * @param {string} commit
   * @param {string} file
   * @param {Object=} opt_options
   * @return {
   *   Array<{label: string, url: string}>|
   *   {label: string, url: string}
   *  }
   */
  getFileWebLinks(repo, commit, file, opt_options) {
    const params = {type: GerritNav.WeblinkType.FILE, repo, commit, file};
    if (opt_options) {
      params.options = opt_options;
    }
    return [].concat(this._generateWeblinks(params));
  },

  /**
   * @param {string} repo
   * @param {string} commit
   * @param {Object=} opt_options
   * @return {{label: string, url: string}}
   */
  getPatchSetWeblink(repo, commit, opt_options) {
    const params = {type: GerritNav.WeblinkType.PATCHSET, repo, commit};
    if (opt_options) {
      params.options = opt_options;
    }
    const result = this._generateWeblinks(params);
    if (Array.isArray(result)) {
      return result.pop();
    } else {
      return result;
    }
  },

  /**
   * @param {string} repo
   * @param {string} commit
   * @param {Object=} opt_options
   * @return {
   *   Array<{label: string, url: string}>|
   *   {label: string, url: string}
   *  }
   */
  getChangeWeblinks(repo, commit, opt_options) {
    const params = {type: GerritNav.WeblinkType.CHANGE, repo, commit};
    if (opt_options) {
      params.options = opt_options;
    }
    return [].concat(this._generateWeblinks(params));
  },

  getUserDashboard(user = 'self', sections = DEFAULT_SECTIONS,
      title = '', config = {}) {
    const attentionEnabled =
        config.change && !!config.change.enable_attention_set;
    const assigneeEnabled =
        config.change && !!config.change.enable_assignee;
    sections = sections
        .filter(section => (attentionEnabled || !section.attentionSetOnly))
        .filter(section => (assigneeEnabled || !section.assigneeOnly))
        .filter(section => (user === 'self' || !section.selfOnly))
        .map(section => {
          return {...section, name: section.name,
            query: section.query.replace(USER_PLACEHOLDER_PATTERN, user)};
        });
    return {title, sections};
  },
};
