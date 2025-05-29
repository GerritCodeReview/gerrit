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

import {GrPluginActionContext} from './shared/gr-js-api-interface/gr-plugin-action-context';
import {AppContext, injectAppContext} from '../services/app-context';
import {PluginLoader} from './shared/gr-js-api-interface/gr-plugin-loader';
import {
  initClickReporter,
  initErrorReporter,
  initInteractionReporter,
  initPerformanceReporter,
  initVisibilityReporter,
  initWebVitals,
} from '../services/gr-reporting/gr-reporting_impl';
import {Finalizable} from '../types/types';

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
    initInteractionReporter(reportingService);
  }
  window.GrPluginActionContext = GrPluginActionContext;
}

export function initGerrit(pluginLoader: PluginLoader) {
  window.Gerrit = pluginLoader;
}
