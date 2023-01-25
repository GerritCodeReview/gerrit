/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {GerritView} from '../../services/router/router-model';

export interface ViewState {
  view: GerritView;
}

/**
 * While we are using page.js this interface will normally be implemented by
 * PageContext, but it helps testing and independence to have our own type
 * here.
 */
export interface UrlInfo {
  querystring?: string;
  hash?: string;
  /** What the regular expression matching returns. */
  params?: {[paramIndex: string]: string};
}

/**
 * Based on `urlPattern` knows whether a URL matches and if so, then
 * `createState()` can produce a `ViewState` from the matched URL.
 */
export interface Route<T extends ViewState> {
  urlPattern: RegExp;
  createState: (info: UrlInfo) => T;
}
