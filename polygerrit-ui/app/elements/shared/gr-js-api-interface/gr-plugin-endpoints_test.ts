/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup-karma';
import {resetPlugins} from '../../../test/test-utils';
import './gr-js-api-interface';
import {GrPluginEndpoints} from './gr-plugin-endpoints';
import {PluginApi} from '../../../api/plugin';
import {HookApi, HookCallback, PluginElement} from '../../../api/hook';

export class MockHook<T extends PluginElement> implements HookApi<T> {
  handleInstanceDetached(_: T) {}

  handleInstanceAttached(_: T) {}

  getLastAttached(): Promise<HTMLElement> {
    throw new Error('unimplemented in mock');
  }

  getAllAttached() {
    return [];
  }

  onAttached(_: HookCallback<T>) {
    return this;
  }

  onDetached(_: HookCallback<T>) {
    return this;
  }

  getModuleName() {
    return 'MockHookApi-ModuleName';
  }
}

suite('gr-plugin-endpoints tests', () => {
  let instance: GrPluginEndpoints;
  let decoratePlugin: PluginApi;
  let stylePlugin: PluginApi;
  let domHook: HookApi<PluginElement>;

  setup(() => {
    domHook = new MockHook<PluginElement>();
    instance = new GrPluginEndpoints();
    window.Gerrit.install(
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
    window.Gerrit.install(
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
