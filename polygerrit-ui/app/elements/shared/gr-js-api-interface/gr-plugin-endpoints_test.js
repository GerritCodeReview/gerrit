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

import '../../../test/common-test-setup-karma.js';
import {resetPlugins} from '../../../test/test-utils.js';
import './gr-js-api-interface.js';
import {GrPluginEndpoints} from './gr-plugin-endpoints.js';
import {pluginLoader} from './gr-plugin-loader.js';
import {_testOnly_initGerritPluginApi} from './gr-gerrit.js';

const pluginApi = _testOnly_initGerritPluginApi();

suite('gr-plugin-endpoints tests', () => {
  let sandbox;
  let instance;
  let pluginFoo;
  let pluginBar;
  let domHook;

  setup(() => {
    sandbox = sinon.sandbox.create();
    domHook = {};
    instance = new GrPluginEndpoints();
    pluginApi.install(p => { pluginFoo = p; }, '0.1',
        'http://test.com/plugins/testplugin/static/foo.html');
    instance.registerModule(
        pluginFoo,
        {
          endpoint: 'a-place',
          type: 'decorate',
          moduleName: 'foo-module',
          domHook,
        }
    );
    pluginApi.install(p => { pluginBar = p; }, '0.1',
        'http://test.com/plugins/testplugin/static/bar.html');
    instance.registerModule(
        pluginBar,
        {
          endpoint: 'a-place',
          type: 'style',
          moduleName: 'bar-module',
          domHook,
        }
    );
    sandbox.stub(pluginLoader, 'arePluginsLoaded').returns(true);
    sandbox.spy(instance, 'import');
  });

  teardown(() => {
    sandbox.restore();
    resetPlugins();
  });

  test('getDetails all', () => {
    assert.deepEqual(instance.getDetails('a-place'), [
      {
        moduleName: 'foo-module',
        plugin: pluginFoo,
        pluginUrl: pluginFoo._url,
        type: 'decorate',
        domHook,
        slot: undefined,
      },
      {
        moduleName: 'bar-module',
        plugin: pluginBar,
        pluginUrl: pluginBar._url,
        type: 'style',
        domHook,
        slot: undefined,
      },
    ]);
  });

  test('getDetails by type', () => {
    assert.deepEqual(instance.getDetails('a-place', {type: 'style'}), [
      {
        moduleName: 'bar-module',
        plugin: pluginBar,
        pluginUrl: pluginBar._url,
        type: 'style',
        domHook,
        slot: undefined,
      },
    ]);
  });

  test('getDetails by module', () => {
    assert.deepEqual(
        instance.getDetails('a-place', {moduleName: 'foo-module'}),
        [
          {
            moduleName: 'foo-module',
            plugin: pluginFoo,
            pluginUrl: pluginFoo._url,
            type: 'decorate',
            domHook,
            slot: undefined,
          },
        ]);
  });

  test('getModules', () => {
    assert.deepEqual(
        instance.getModules('a-place'), ['foo-module', 'bar-module']);
  });

  test('getPlugins', () => {
    assert.deepEqual(
        instance.getPlugins('a-place'), [pluginFoo._url]);
  });

  test('getAndImportPlugins', () => {
    instance.getAndImportPlugins('a-place');
    assert.isTrue(instance.import.called);
    assert.isTrue(instance.import.calledOnce);
    instance.getAndImportPlugins('a-place');
    assert.isTrue(instance.import.calledOnce);
  });

  test('onNewEndpoint', () => {
    const newModuleStub = sandbox.stub();
    instance.onNewEndpoint('a-place', newModuleStub);
    instance.registerModule(
        pluginFoo,
        {
          endpoint: 'a-place',
          type: 'replace',
          moduleName: 'zaz-module',
          domHook,
        });
    assert.deepEqual(newModuleStub.lastCall.args[0], {
      moduleName: 'zaz-module',
      plugin: pluginFoo,
      pluginUrl: pluginFoo._url,
      type: 'replace',
      domHook,
      slot: undefined,
    });
  });

  test('reuse dom hooks', () => {
    instance.registerModule(
        pluginFoo, 'a-place', 'decorate', 'foo-module', domHook);
    assert.deepEqual(instance.getDetails('a-place'), [
      {
        moduleName: 'foo-module',
        plugin: pluginFoo,
        pluginUrl: pluginFoo._url,
        type: 'decorate',
        domHook,
        slot: undefined,
      },
      {
        moduleName: 'bar-module',
        plugin: pluginBar,
        pluginUrl: pluginBar._url,
        type: 'style',
        domHook,
        slot: undefined,
      },
    ]);
  });
});