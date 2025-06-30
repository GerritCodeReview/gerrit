/**
 * @license
 * Copyright 2023 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {PluginApi} from '../../../api/plugin';
import {PluginsModel} from '../../../models/plugins/plugins-model';
import {
  ChangeUpdatesPluginApi,
  ChangeUpdatesPublisher,
} from '../../../api/change-updates';

enum State {
  NOT_REGISTERED,
  REGISTERED,
}

/**
 * Plugin API for change updates.
 *
 * Instance of this type created and returned to plugins that want to provide change updates data.
 * Plugins normally just call register() once at startup.
 */
export class GrChangeUpdatesApi implements ChangeUpdatesPluginApi {
  private state = State.NOT_REGISTERED;

  constructor(
    private readonly pluginsModel: PluginsModel,
    readonly plugin: PluginApi
  ) {}

  register(publisher: ChangeUpdatesPublisher): void {
    if (this.state === State.REGISTERED) {
      throw new Error('Only one publisher can be registered per plugin.');
    }
    this.state = State.REGISTERED;
    this.pluginsModel.changeUpdatesRegister({
      pluginName: this.plugin.getPluginName(),
      publisher,
    });
  }
}
