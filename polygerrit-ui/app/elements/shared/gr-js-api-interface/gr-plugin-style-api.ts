/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {PluginApi} from '../../../api/plugin';
import {StylePluginApi} from '../../../api/styles';
import {ReportingService} from '../../../services/gr-reporting/gr-reporting';

function getOrCreatePluginStyleEl(): HTMLStyleElement {
  const el =
    document.head.querySelector<HTMLStyleElement>('style#plugin-style');
  if (el) return el;

  const styleEl = document.createElement('style');
  styleEl.setAttribute('id', 'plugin-style');
  // Append at the end so that they override the default light and dark theme
  // styles.
  document.head.appendChild(styleEl);
  return styleEl;
}

export class GrPluginStyleApi implements StylePluginApi {
  constructor(
    private readonly reporting: ReportingService,
    private readonly plugin: PluginApi
  ) {
    this.reporting.trackApi(this.plugin, 'style', 'constructor');
  }

  insertCSSRule(rule: string): void {
    this.reporting.trackApi(this.plugin, 'style', 'insertCSSRule');

    const styleEl = getOrCreatePluginStyleEl();
    try {
      styleEl.sheet?.insertRule(rule);
    } catch (error) {
      console.error(
        `Failed to insert CSS rule for plugin ${this.plugin.getPluginName()}: ${error}`
      );
    }
  }
}
