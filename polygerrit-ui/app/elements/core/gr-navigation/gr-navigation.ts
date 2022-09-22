/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {
  BasePatchSetNum,
  ChangeInfo,
  RevisionPatchSetNum,
} from '../../../types/common';
import {ParsedChangeInfo} from '../../../types/types';
import {createDiffUrl} from '../../../models/views/diff';
import {define} from '../../../models/dependency';

const uninitialized = () => {
  console.warn('Use of uninitialized routing');
};

const uninitializedNavigate: NavigateCallback = () => {
  uninitialized();
  return '';
};

export type NavigateCallback = (target: string, redirect?: boolean) => void;

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
};
