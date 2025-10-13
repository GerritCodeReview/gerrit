/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {PluginApi} from '../../../api/plugin';
import {ReportingService} from '../../../services/gr-reporting/gr-reporting';
import {PluginsModel} from '../../../models/plugins/plugins-model';
import {
  AiCodeReviewPluginApi,
  AiCodeReviewProvider,
} from '../../../api/ai-code-review';

enum State {
  NOT_REGISTERED,
  REGISTERED,
}

/**
 * Plugin API for AI Code Review.
 *
 * This object is returned to plugins that want to provide AI Code Review data.
 * Plugins normally just call register() once at startup and then wait for
 * calls on the provider interface.
 */
export class GrAiCodeReviewApi implements AiCodeReviewPluginApi {
  private state = State.NOT_REGISTERED;

  constructor(
    private readonly reporting: ReportingService,
    private readonly pluginsModel: PluginsModel,
    readonly plugin: PluginApi
  ) {
    this.reporting.trackApi(this.plugin, 'ai-code-review', 'constructor');
  }

  register(provider: AiCodeReviewProvider): void {
    this.reporting.trackApi(this.plugin, 'ai-code-review', 'register');
    if (this.state === State.REGISTERED) {
      throw new Error('Only one provider can be registered per plugin.');
    }
    this.state = State.REGISTERED;
    this.pluginsModel.aiCodeReviewRegister({
      pluginName: this.plugin.getPluginName(),
      provider,
    });
  }
}
