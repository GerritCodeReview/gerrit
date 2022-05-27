/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

/**
 * @fileoverview This file is a backwards-compatibility shim.
 * Before Polygerrit converted to ES Modules, it exposes some variables out onto
 * the global namespace. Plugins can depend on these variables and we must
 * expose these variables until plugins switch to direct import from polygerrit.
 */

import {GrAnnotation} from '../embed/diff/gr-diff-highlight/gr-annotation';
import {page} from '../utils/page-wrapper-utils';
import {GrPluginActionContext} from './shared/gr-js-api-interface/gr-plugin-action-context';
import {initGerritPluginApi} from './shared/gr-js-api-interface/gr-gerrit';
import {AppContext} from '../services/app-context';

export function initGlobalVariables(appContext: AppContext) {
  window.GrAnnotation = GrAnnotation;
  window.page = page;
  window.GrPluginActionContext = GrPluginActionContext;
  initGerritPluginApi(appContext);
}
