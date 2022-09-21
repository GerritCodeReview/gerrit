/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {
  BasePatchSetNum,
  ChangeInfo,
  RepoName,
  RevisionPatchSetNum,
} from '../../../types/common';
import {ParsedChangeInfo} from '../../../types/types';
import {createRepoUrl} from '../../../models/views/repo';
import {createSearchUrl} from '../../../models/views/search';
import {createDiffUrl} from '../../../models/views/diff';
import {createDashboardUrl} from '../../../models/views/dashboard';
import {createChangeUrl} from '../../../models/views/change';

const uninitialized = () => {
  console.warn('Use of uninitialized routing');
};

const uninitializedNavigate: NavigateCallback = () => {
  uninitialized();
  return '';
};

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

  /**
   * Setup router implementation.
   *
   * @param navigate the router-abstracted equivalent of
   *     `window.location.href = ...` or window.location.replace(...). The
   *     string is a new location and boolean defines is it redirect or not
   *     (true means redirect, i.e. equivalent of window.location.replace).
   */
  setup(navigate: NavigateCallback) {
    this._navigate = navigate;
  },

  destroy() {
    this._navigate = uninitializedNavigate;
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
};
