/**
 * @license
 * Copyright 2026 Monty Taylor
 * SPDX-License-Identifier: Apache-2.0
 */
import {PluginApi} from '../../../api/plugin';
import {
  ChangeDependenciesPluginApi,
  ChangeDependenciesProvider,
} from '../../../api/change-dependencies';
import {PluginsModel} from '../../../models/plugins/plugins-model';

export class GrChangeDependenciesApi implements ChangeDependenciesPluginApi {
  constructor(
    private readonly pluginsModel: PluginsModel,
    readonly plugin: PluginApi
  ) {}

  register(provider: ChangeDependenciesProvider): void {
    this.pluginsModel.changeDependenciesRegister({
      pluginName: this.plugin.getPluginName(),
      provider,
    });
  }
}
