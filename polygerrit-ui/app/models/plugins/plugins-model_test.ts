/**
 * @license
 * Copyright (C) 2022 The Android Open Source Project
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
import '../../test/common-test-setup-karma';
import './plugins-model';
import {ChecksApiConfig, ChecksProvider, ResponseCode} from '../../api/checks';
import {ChecksPlugin, ChecksUpdate, PluginsModel} from './plugins-model';
import {createRunResult} from '../../test/test-data-generators';

const PLUGIN_NAME = 'test-plugin';

const CONFIG: ChecksApiConfig = {
  fetchPollingIntervalSeconds: 1000,
};

function createProvider(): ChecksProvider {
  return {
    fetch: () =>
      Promise.resolve({
        responseCode: ResponseCode.OK,
        runs: [],
      }),
  };
}

suite('plugins-model tests', () => {
  let model: PluginsModel;
  let checksPlugins: ChecksPlugin[] = [];
  const register = function () {
    model.checksRegister({
      pluginName: PLUGIN_NAME,
      provider: createProvider(),
      config: CONFIG,
    });
  };

  setup(() => {
    model = new PluginsModel();
    model.state$.subscribe(s => {
      checksPlugins = s.checksPlugins;
    });
  });

  teardown(() => {
    model.finalize();
  });

  test('checksRegister', async () => {
    assert.isFalse(checksPlugins.some(p => p.pluginName === PLUGIN_NAME));

    register();

    assert.isTrue(checksPlugins.some(p => p.pluginName === PLUGIN_NAME));
  });

  test('checksAnnounce', async () => {
    let announcement: ChecksPlugin | undefined;
    model.checksAnnounce$.subscribe(a => (announcement = a));
    assert.isUndefined(announcement?.pluginName);

    register();
    model.checksAnnounce(PLUGIN_NAME);

    assert.equal(announcement?.pluginName, PLUGIN_NAME);
  });

  test('checksUpdate', async () => {
    let update: ChecksUpdate | undefined;
    model.checksUpdate$.subscribe(u => (update = u));
    assert.isUndefined(update?.pluginName);

    register();
    model.checksUpdate({
      pluginName: PLUGIN_NAME,
      run: createRunResult(),
      result: createRunResult(),
    });

    assert.equal(update?.pluginName, PLUGIN_NAME);
  });
});
