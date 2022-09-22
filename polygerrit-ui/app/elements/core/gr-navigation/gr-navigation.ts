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
}
