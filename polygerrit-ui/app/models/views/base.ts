/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {PageContext} from '../../elements/core/gr-router/gr-page';
import {GerritView} from '../../services/router/router-model';

export interface ViewState {
  view: GerritView;
}

/**
 * Based on `urlPattern` knows whether a URL matches and if so, then
 * `createState()` can produce a `ViewState` from the matched URL.
 */
export interface Route<T extends ViewState> {
  urlPattern: RegExp;
  createState: (ctx: PageContext) => T;
}
