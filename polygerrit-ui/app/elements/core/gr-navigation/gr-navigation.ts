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
import {createDiffUrl} from '../../../models/views/diff';
import {createChangeUrl} from '../../../models/views/change';
import {define} from '../../../models/dependency';

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

export const navigationToken = define<NavigationService>('navigation');

export interface NavigationService {
  /**
   * This is similar to letting the browser navigate to this URL when the user
   * clicks it, or to just setting `window.location.href` directly.
   *
   * This adds a new entry to the browser location history. Consier using
   * `replaceUrl()`, if you want to avoid that.
   *
   * page.show() eventually just calls `window.history.pushState()`.
   */
  setUrl(url: string): void;

  /**
   * Navigate to this URL, but replace the current URL in the history instead of
   * adding a new one (which is what `setUrl()` would do).
   *
   * page.redirect() eventually just calls `window.history.replaceState()`.
   */
  replaceUrl(url: string): void;
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
        change,
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
