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
import {GrPluginActionContext} from './shared/gr-js-api-interface/gr-plugin-action-context';
import {AppContext, injectAppContext} from '../services/app-context';
import {Finalizable} from '../services/registry';
import {PluginLoader} from './shared/gr-js-api-interface/gr-plugin-loader';

export function initGlobalVariables(appContext: AppContext & Finalizable) {
  injectAppContext(appContext);
  window.GrAnnotation = GrAnnotation;
  window.GrPluginActionContext = GrPluginActionContext;
}

export function initGerrit(pluginLoader: PluginLoader) {
  window.Gerrit = pluginLoader;
}
