/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {
  AnnotationPluginApi,
  CoverageProvider,
  TokenHoverListener,
} from '../../../api/annotation';
import {PluginApi} from '../../../api/plugin';
import {PluginsModel} from '../../../models/plugins/plugins-model';
import {ReportingService} from '../../../services/gr-reporting/gr-reporting';

export class GrAnnotationActionsInterface implements AnnotationPluginApi {
  constructor(
    private readonly reporting: ReportingService,
    private readonly pluginsModel: PluginsModel,
    private readonly plugin: PluginApi
  ) {
    this.reporting.trackApi(this.plugin, 'annotation', 'constructor');
  }

  setCoverageProvider(provider: CoverageProvider) {
    this.reporting.trackApi(this.plugin, 'annotation', 'setCoverageProvider');
    this.pluginsModel.coverageRegister({
      pluginName: this.plugin.getPluginName(),
      provider,
    });
  }

  addTokenHoverListener(listener: TokenHoverListener): void {
    this.reporting.trackApi(this.plugin, 'annotation', 'addTokenHoverListener');
    this.pluginsModel.tokenHoverListenerRegister({
      pluginName: this.plugin.getPluginName(),
      listener,
    });
  }
}
