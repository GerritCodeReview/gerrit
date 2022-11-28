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

import {initGerrit, initGlobalVariables} from './gr-app-global-var-init';
import './gr-app-element';
import {Finalizable} from '../services/registry';
import {
  DependencyError,
  DependencyToken,
  provide,
  Provider,
} from '../models/dependency';
import {installPolymerResin} from '../scripts/polymer-resin-install';

import {
  createAppContext,
  createAppDependencies,
  Creator,
} from '../services/app-context-init';
import {
  initVisibilityReporter,
  initPerformanceReporter,
  initErrorReporter,
  initWebVitals,
} from '../services/gr-reporting/gr-reporting_impl';
import {html, LitElement} from 'lit';
import {customElement} from 'lit/decorators.js';
import {
  ServiceWorkerInstaller,
  serviceWorkerInstallerToken,
} from '../services/service-worker-installer';
import {pluginLoaderToken} from './shared/gr-js-api-interface/gr-plugin-loader';

const appContext = createAppContext();
initGlobalVariables(appContext);
const reportingService = appContext.reportingService;
initVisibilityReporter(reportingService);
initPerformanceReporter(reportingService);
initWebVitals(reportingService);
initErrorReporter(reportingService);

installPolymerResin(safeTypesBridge);

@customElement('gr-app')
export class GrApp extends LitElement {
  private finalizables: Finalizable[] = [];

  private serviceWorkerInstaller?: ServiceWorkerInstaller;

  override connectedCallback() {
    super.connectedCallback();
    const dependencies = new Map<DependencyToken<unknown>, Provider<unknown>>();

    const injectDependency = <T>(
      token: DependencyToken<T>,
      creator: Creator<T>
    ) => {
      let service: (T & Finalizable) | undefined = undefined;
      dependencies.set(token, () => {
        if (service) return service;
        service = creator();
        this.finalizables.push(service);
        return service;
      });
    };

    const resolver = <T>(token: DependencyToken<T>): T => {
      const provider = dependencies.get(token);
      if (provider) {
        return provider() as T;
      } else {
        throw new DependencyError(
          token,
          'Forgot to set up dependency for gr-app'
        );
      }
    };

    for (const [token, creator] of createAppDependencies(
      appContext,
      resolver
    )) {
      injectDependency(token, creator);
    }
    for (const [token, provider] of dependencies) {
      provide(this, token, provider);
    }

    initGerrit(resolver(pluginLoaderToken));

    if (!this.serviceWorkerInstaller) {
      this.serviceWorkerInstaller = resolver(serviceWorkerInstallerToken);
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
