/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {define} from '../../../models/dependency';

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

  /**
   * You can ask the router to block all navigation to other pages for a while,
   * e.g. while there are unsaved comments. You must make sure to call
   * `releaseNavigation()` with the same string shortly after to unblock the
   * router.
   *
   * The provided reason must be non-empty.
   */
  blockNavigation(reason: string): void;

  /**
   * See `blockNavigation()`.
   *
   * This API is not counting. If you block navigation with the same reason
   * multiple times, then one release call will unblock.
   */
  releaseNavigation(reason: string): void;
}
