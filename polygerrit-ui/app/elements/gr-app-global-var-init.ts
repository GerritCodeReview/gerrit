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
import {PluginLoader} from './shared/gr-js-api-interface/gr-plugin-loader';
import {
  initVisibilityReporter,
  initPerformanceReporter,
  initErrorReporter,
  initWebVitals,
  initClickReporter,
} from '../services/gr-reporting/gr-reporting_impl';
import {Finalizable} from '../services/registry';

export function initGlobalVariables(
  appContext: AppContext & Finalizable,
  initializeReporting: boolean
) {
  injectAppContext(appContext);
  if (initializeReporting) {
    const reportingService = appContext.reportingService;
    initVisibilityReporter(reportingService);
    initPerformanceReporter(reportingService);
    initWebVitals(reportingService);
    initErrorReporter(reportingService);
    initClickReporter(reportingService);
  }
  window.GrAnnotation = GrAnnotation;
  window.GrPluginActionContext = GrPluginActionContext;
}

export function initGerrit(pluginLoader: PluginLoader) {
  window.Gerrit = pluginLoader;
}
