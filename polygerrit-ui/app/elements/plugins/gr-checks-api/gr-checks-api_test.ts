/**
 * @license
 * Copyright (C) 2020 The Android Open Source Project
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

import '../../../test/common-test-setup-karma';
import {getPluginLoader} from '../../shared/gr-js-api-interface/gr-plugin-loader';
import {PluginApi} from '../../../api/plugin';
import {ChecksPluginApi} from '../../../api/checks';

suite('gr-settings-api tests', () => {
  let checksApi: ChecksPluginApi | undefined;

  setup(() => {
    let pluginApi: PluginApi | undefined = undefined;
    window.Gerrit.install(
      p => {
        pluginApi = p;
      },
      '0.1',
      'http://test.com/plugins/testplugin/static/test.js'
    );
    getPluginLoader().loadPlugins([]);
    assert.isOk(pluginApi);
    checksApi = pluginApi!.checks();
  });

  teardown(() => {
    checksApi = undefined;
  });

  test('exists', () => {
    assert.isOk(checksApi);
  });
});
