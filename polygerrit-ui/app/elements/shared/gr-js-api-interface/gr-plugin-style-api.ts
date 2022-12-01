/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {PluginApi} from '../../../api/plugin';
import {StylePluginApi} from '../../../api/styles';
import {ReportingService} from '../../../services/gr-reporting/gr-reporting';
import {safeStyleEl, SafeStyleSheet} from '../../../utils/inner-html-util';

export class GrPluginStyleApi implements StylePluginApi {
  constructor(
    private readonly reporting: ReportingService,
    private readonly plugin: PluginApi
  ) {
    this.reporting.trackApi(this.plugin, 'style', 'constructor');
  }

  addStyle(css: SafeStyleSheet): void {
    this.reporting.trackApi(this.plugin, 'style', 'addStyle');
    const styleEl = document.createElement('style');
    styleEl.setAttribute('id', this.plugin.getPluginName().toLowerCase());
    styleEl.classList.add('plugin-style');
    safeStyleEl.setTextContent(styleEl, css);

    // Append at the end so that they override the default light and dark theme
    // styles.
    document.head.appendChild(styleEl);
  }
}
