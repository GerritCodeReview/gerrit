/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-js-api-interface';
import {EndpointType, GrPluginEndpoints} from './gr-plugin-endpoints';
import {PluginApi} from '../../../api/plugin';
import {HookApi, HookCallback, PluginElement} from '../../../api/hook';
import {assert} from '@open-wc/testing';

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
  let replacePlugin: PluginApi;
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
      type: EndpointType.DECORATE,
      moduleName: 'decorate-module',
      domHook,
    });
    window.Gerrit.install(
      plugin => (replacePlugin = plugin),
      '0.1',
      'http://test.com/plugins/testplugin/static/replace.js'
    );
    instance.registerModule(replacePlugin, {
      endpoint: 'my-endpoint',
      type: EndpointType.REPLACE,
      moduleName: 'replace-module',
      domHook,
    });
  });

  test('getDetails all', () => {
    assert.deepEqual(instance.getDetails('my-endpoint'), [
      {
        moduleName: 'decorate-module',
        plugin: decoratePlugin,
        pluginUrl: decoratePlugin._url,
        type: EndpointType.DECORATE,
        domHook,
        slot: undefined,
      },
      {
        moduleName: 'replace-module',
        plugin: replacePlugin,
        pluginUrl: replacePlugin._url,
        type: EndpointType.REPLACE,
        domHook,
        slot: undefined,
      },
    ]);
  });

  test('onNewEndpoint', () => {
    const newModuleStub = sinon.stub();
    instance.setPluginsReady();
    instance.onNewEndpoint('my-endpoint', newModuleStub);
    instance.registerModule(decoratePlugin, {
      endpoint: 'my-endpoint',
      type: EndpointType.REPLACE,
      moduleName: 'replace-module',
      domHook,
    });
    assert.deepEqual(newModuleStub.lastCall.args[0], {
      moduleName: 'replace-module',
      plugin: decoratePlugin,
      pluginUrl: decoratePlugin._url,
      type: EndpointType.REPLACE,
      domHook,
      slot: undefined,
    });
  });
});
