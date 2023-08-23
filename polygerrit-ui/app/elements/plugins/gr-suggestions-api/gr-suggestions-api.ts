/**
 * @license
 * Copyright 2023 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {PluginApi} from '../../../api/plugin';
import {ReportingService} from '../../../services/gr-reporting/gr-reporting';
import {PluginsModel} from '../../../models/plugins/plugins-model';
import {
  SuggestionsPluginApi,
  SuggestionsProvider,
} from '../../../api/suggestions';

enum State {
  NOT_REGISTERED,
  REGISTERED,
}

/**
 * Plugin API for suggestions.
 *
 * This object is returned to plugins that want to provide suggestions data.
 * Plugins normally just call register() once at startup and then wait for
 * suggestCode() being called on the provider interface.
 */
export class GrSuggestionsApi implements SuggestionsPluginApi {
  private state = State.NOT_REGISTERED;

  constructor(
    private readonly reporting: ReportingService,
    private readonly pluginsModel: PluginsModel,
    readonly plugin: PluginApi
  ) {
    this.reporting.trackApi(this.plugin, 'suggestions', 'constructor');
  }

  register(provider: SuggestionsProvider): void {
    this.reporting.trackApi(this.plugin, 'suggestions', 'register');
    if (this.state === State.REGISTERED) {
      throw new Error('Only one provider can be registered per plugin.');
    }
    this.state = State.REGISTERED;
    this.pluginsModel.suggestionsRegister({
      pluginName: this.plugin.getPluginName(),
      provider,
    });
  }
}
