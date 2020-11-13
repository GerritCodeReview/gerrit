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
  ResponseCode,
} from './gr-checks-api-types';

// IMPORTANT !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
// The entire API is currently in DRAFT state.
// Changes to all methods and objects are expected.
// !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!

const DEFAULT_CONFIG: ChecksApiConfig = {
  fetchPollingIntervalSeconds: 60,
};

enum State {
  NOT_REGISTERED,
  REGISTERED,
  FETCHING,
}

/**
 * Plugin API for checks.
 *
 * This object is created/returned to plugins that want to provide check data.
 * Plugins normally just call register() once at startup and then wait for
 * fetch() being called on the provider interface.
 */
export class GrChecksApi implements GrChecksApiInterface {
  private provider?: ChecksProvider;

  config?: ChecksApiConfig;

  private state = State.NOT_REGISTERED;

  constructor(readonly plugin: PluginApi) {}

  announceUpdate() {
    // TODO(brohlfs): Implement!
  }

  register(provider: ChecksProvider, config?: ChecksApiConfig): void {
    if (this.state !== State.NOT_REGISTERED || this.provider)
      throw new Error('Only one provider can be registered per plugin.');
    this.state = State.REGISTERED;
    this.provider = provider;
    this.config = config ?? DEFAULT_CONFIG;
  }

  async fetch(change: number, patchset: number) {
    if (this.state === State.NOT_REGISTERED || !this.provider)
      throw new Error('Cannot fetch checks without a registered provider.');
    if (this.state === State.FETCHING) return;
    this.state = State.FETCHING;
    const response = await this.provider.fetch(change, patchset);
    this.state = State.REGISTERED;
    if (response.responseCode === ResponseCode.OK) {
      // TODO(brohlfs): Do something with the response.
    }
  }
}
