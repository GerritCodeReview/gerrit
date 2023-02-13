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
import {injectAppContext} from '../services/app-context';
import {PluginLoader} from './shared/gr-js-api-interface/gr-plugin-loader';
import {
  initVisibilityReporter,
  initPerformanceReporter,
  initErrorReporter,
  initWebVitals,
} from '../services/gr-reporting/gr-reporting_impl';
import {createAppContext} from '../services/app-context-init';

export function initGlobalVariables() {
  const appContext = createAppContext();
  injectAppContext(appContext);
  const reportingService = appContext.reportingService;
  initVisibilityReporter(reportingService);
  initPerformanceReporter(reportingService);
  initWebVitals(reportingService);
  initErrorReporter(reportingService);
  window.GrAnnotation = GrAnnotation;
  window.GrPluginActionContext = GrPluginActionContext;
}

export function initGerrit(pluginLoader: PluginLoader) {
  window.Gerrit = pluginLoader;
}
