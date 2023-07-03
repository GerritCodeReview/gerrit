/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {AnnotationPluginApi, CoverageProvider} from '../../../api/annotation';
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

  addHoverCallback(cb: (element: any) => void): void {
    this.pluginsModel.hoverCallbackRegister({
      pluginName: this.plugin.getPluginName(),
      cb,
    });
  }
}
