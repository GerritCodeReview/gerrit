/**
 * @license
 * Copyright 2015 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {safeTypesBridge} from '../utils/safe-types-util';
import './font-roboto-local-loader';
// Sets up global Polymer variable, because plugins requires it.
import '../scripts/bundled-polymer';

/**
 * setCancelSyntheticClickEvents is set to true by
 * default which will cancel synthetic click events
 * on older touch device.
 * See https://github.com/Polymer/polymer/issues/5289
 */
import {
  setPassiveTouchGestures,
  setCancelSyntheticClickEvents,
} from '@polymer/polymer/lib/utils/settings';
setCancelSyntheticClickEvents(false);
setPassiveTouchGestures(true);

import {initGlobalVariables} from './gr-app-global-var-init';
import './gr-app-element';
import {Finalizable} from '../services/registry';
import {provide} from '../models/dependency';
import {installPolymerResin} from '../scripts/polymer-resin-install';

import {
  createAppContext,
  createAppDependencies,
} from '../services/app-context-init';
import {
  initVisibilityReporter,
  initPerformanceReporter,
  initErrorReporter,
} from '../services/gr-reporting/gr-reporting_impl';
import {injectAppContext} from '../services/app-context';
import {html, LitElement} from 'lit';
import {customElement} from 'lit/decorators';

const appContext = createAppContext();
injectAppContext(appContext);
const reportingService = appContext.reportingService;
initVisibilityReporter(reportingService);
initPerformanceReporter(reportingService);
initErrorReporter(reportingService);

installPolymerResin(safeTypesBridge);

@customElement('gr-app')
export class GrApp extends LitElement {
  private finalizables: Finalizable[] = [];

  override connectedCallback() {
    super.connectedCallback();
    const dependencies = createAppDependencies(appContext);
    for (const [token, service] of dependencies) {
      this.finalizables.push(service);
      provide(this, token, () => service);
    }
  }

  override disconnectedCallback() {
    for (const f of this.finalizables) {
      f.finalize();
    }
    this.finalizables = [];
    super.disconnectedCallback();
  }

  override render() {
    return html`<gr-app-element id="app-element"></gr-app-element>`;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-app': GrApp;
  }
}

initGlobalVariables(appContext);
