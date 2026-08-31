/**
 * @license
 * Copyright 2015 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import './font-roboto-local-loader';
import '../types/globals';

import {initGerrit, initGlobalVariables} from './gr-app-global-var-init';
import './gr-app-element';
import {
  DependencyError,
  DependencyToken,
  provide,
  Provider,
} from '../models/dependency';

import {
  createAppContext,
  createAppDependencies,
  Creator,
} from '../services/app-context-init';
import {html, LitElement} from 'lit';
import {customElement} from 'lit/decorators.js';
import {
  ServiceWorkerInstaller,
  serviceWorkerInstallerToken,
} from '../services/service-worker-installer';
import {pluginLoaderToken} from './shared/gr-js-api-interface/gr-plugin-loader';
import {getAppContext} from '../services/app-context';
import {Finalizable} from '../types/types';

initGlobalVariables(createAppContext(), true);

export const SCROLL_PADDING_TOP_CALC =
  'calc(var(--main-header-height) + var(--change-header-height) + var(--diff-header-height))';

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
      getAppContext(),
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

    // Defines top optimal viewing region for PageDown/PageUp keyboard paging
    // and anchor jumps when sticky headers are active.
    document.documentElement.style.setProperty(
      'scroll-padding-top',
      SCROLL_PADDING_TOP_CALC
    );
    document.addEventListener('focusin', this.handleFocusIn);
    document.addEventListener('focusout', this.handleFocusOut);
  }

  override disconnectedCallback() {
    document.removeEventListener('focusin', this.handleFocusIn);
    document.removeEventListener('focusout', this.handleFocusOut);
    for (const f of this.finalizables) {
      f.finalize();
    }
    this.finalizables = [];
    document.documentElement.style.removeProperty('scroll-padding-top');
    super.disconnectedCallback();
  }

  private readonly handleFocusIn = (e: FocusEvent) => {
    const path = e.composedPath();
    if (this.isInsideStickyContainer(path)) {
      document.documentElement.style.setProperty('scroll-padding-top', '0px');
    } else {
      document.documentElement.style.setProperty(
        'scroll-padding-top',
        SCROLL_PADDING_TOP_CALC
      );
    }
  };

  private readonly handleFocusOut = (e: FocusEvent) => {
    if (!e.relatedTarget) {
      document.documentElement.style.setProperty(
        'scroll-padding-top',
        SCROLL_PADDING_TOP_CALC
      );
    }
  };

  private isInsideStickyContainer(path: EventTarget[]): boolean {
    for (const target of path) {
      if (!(target instanceof HTMLElement)) continue;
      if (target === document.body || target === document.documentElement) {
        break;
      }
      const style = window.getComputedStyle(target);
      if (style.position === 'sticky' && style.top !== 'auto') {
        return true;
      }
    }
    return false;
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
