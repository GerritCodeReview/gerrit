/**
 * @license
 * Copyright (C) 2020 The Android Open Source Settings
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import {PluginApi} from '../gr-plugin-types';
import {
  ChecksApiConfig,
  ChecksProvider,
  GrChecksApiInterface,
} from '../../../api/checks';
import {appContext} from '../../../services/app-context';

const DEFAULT_CONFIG: ChecksApiConfig = {
  fetchPollingIntervalSeconds: 60,
};

enum State {
  NOT_REGISTERED,
  REGISTERED,
}

/**
 * Plugin API for checks.
 *
 * This object is created/returned to plugins that want to provide check data.
 * Plugins normally just call register() once at startup and then wait for
 * fetch() being called on the provider interface.
 */
export class GrChecksApi implements GrChecksApiInterface {
  private state = State.NOT_REGISTERED;

  private readonly checksService = appContext.checksService;

  constructor(readonly plugin: PluginApi) {}

  announceUpdate() {
    this.checksService.reload(this.plugin.getPluginName());
  }

  register(provider: ChecksProvider, config?: ChecksApiConfig): void {
    if (this.state === State.REGISTERED)
      throw new Error('Only one provider can be registered per plugin.');
    this.state = State.REGISTERED;
    this.checksService.register(
      this.plugin.getPluginName(),
      provider,
      config ?? DEFAULT_CONFIG
    );
  }
}
