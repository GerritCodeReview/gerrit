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
import {resetPlugins} from '../../../test/test-utils';
import './gr-js-api-interface';
import {GrPluginEndpoints} from './gr-plugin-endpoints';
import {_testOnly_initGerritPluginApi} from './gr-gerrit';
import {PluginApi} from '../../../api/plugin';
import {HookApi, PluginElement} from '../../../api/hook';

const pluginApi = _testOnly_initGerritPluginApi();

suite('gr-plugin-endpoints tests', () => {
  let instance: GrPluginEndpoints;
  let decoratePlugin: PluginApi;
  let stylePlugin: PluginApi;
  let domHook: HookApi<PluginElement>;

  setup(() => {
    domHook = {} as HookApi<PluginElement>;
    instance = new GrPluginEndpoints();
    pluginApi.install(
      plugin => (decoratePlugin = plugin),
      '0.1',
      'http://test.com/plugins/testplugin/static/decorate.js'
    );
    instance.registerModule(decoratePlugin, {
      endpoint: 'my-endpoint',
      type: 'decorate',
      moduleName: 'decorate-module',
      domHook,
    });
    pluginApi.install(
      plugin => (stylePlugin = plugin),
      '0.1',
      'http://test.com/plugins/testplugin/static/style.js'
    );
    instance.registerModule(stylePlugin, {
      endpoint: 'my-endpoint',
      type: 'style',
      moduleName: 'style-module',
      domHook,
    });
  });

  teardown(() => {
    resetPlugins();
  });

  test('getDetails all', () => {
    assert.deepEqual(instance.getDetails('my-endpoint'), [
      {
        moduleName: 'decorate-module',
        plugin: decoratePlugin,
        pluginUrl: decoratePlugin._url,
        type: 'decorate',
        domHook,
        slot: undefined,
      },
      {
        moduleName: 'style-module',
        plugin: stylePlugin,
        pluginUrl: stylePlugin._url,
        type: 'style',
        domHook,
        slot: undefined,
      },
    ]);
  });

  test('getDetails by type', () => {
    assert.deepEqual(
      instance.getDetails('my-endpoint', {endpoint: 'a-place', type: 'style'}),
      [
        {
          moduleName: 'style-module',
          plugin: stylePlugin,
          pluginUrl: stylePlugin._url,
          type: 'style',
          domHook,
          slot: undefined,
        },
      ]
    );
  });

  test('getDetails by module', () => {
    assert.deepEqual(
      instance.getDetails('my-endpoint', {
        endpoint: 'my-endpoint',
        moduleName: 'decorate-module',
      }),
      [
        {
          moduleName: 'decorate-module',
          plugin: decoratePlugin,
          pluginUrl: decoratePlugin._url,
          type: 'decorate',
          domHook,
          slot: undefined,
        },
      ]
    );
  });

  test('getModules', () => {
    assert.deepEqual(instance.getModules('my-endpoint'), [
      'decorate-module',
      'style-module',
    ]);
  });

  test('getPlugins URLs are unique', () => {
    assert.equal(decoratePlugin._url, stylePlugin._url);
    assert.deepEqual(instance.getPlugins('my-endpoint'), [decoratePlugin._url]);
  });

  test('onNewEndpoint', () => {
    const newModuleStub = sinon.stub();
    instance.setPluginsReady();
    instance.onNewEndpoint('my-endpoint', newModuleStub);
    instance.registerModule(decoratePlugin, {
      endpoint: 'my-endpoint',
      type: 'replace',
      moduleName: 'replace-module',
      domHook,
    });
    assert.deepEqual(newModuleStub.lastCall.args[0], {
      moduleName: 'replace-module',
      plugin: decoratePlugin,
      pluginUrl: decoratePlugin._url,
      type: 'replace',
      domHook,
      slot: undefined,
    });
  });
});
