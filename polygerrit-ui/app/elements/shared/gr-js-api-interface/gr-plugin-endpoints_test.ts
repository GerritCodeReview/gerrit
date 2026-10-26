/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import * as sinon from 'sinon';
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

  test('getDetails sorts by order before plugin name', () => {
    instance.registerModule(replacePlugin, {
      endpoint: 'ordered-endpoint',
      type: EndpointType.DECORATE,
      moduleName: 'unordered-module',
      domHook,
    });
    instance.registerModule(replacePlugin, {
      endpoint: 'ordered-endpoint',
      type: EndpointType.DECORATE,
      moduleName: 'ordered-second',
      domHook,
      order: 2,
    });
    instance.registerModule(decoratePlugin, {
      endpoint: 'ordered-endpoint',
      type: EndpointType.DECORATE,
      moduleName: 'ordered-first',
      domHook,
      order: 1,
    });

    assert.deepEqual(
      instance.getDetails('ordered-endpoint').map(m => m.moduleName),
      ['ordered-first', 'ordered-second', 'unordered-module']
    );
  });

  test('getDetails handles MAX_SAFE_INTEGER, non-finite orders, and does not mutate internal array', () => {
    let pluginAlpha!: PluginApi;
    let pluginBeta!: PluginApi;
    window.Gerrit.install(
      plugin => (pluginAlpha = plugin),
      '0.1',
      'http://test.com/plugins/alpha/static/alpha.js'
    );
    window.Gerrit.install(
      plugin => (pluginBeta = plugin),
      '0.1',
      'http://test.com/plugins/beta/static/beta.js'
    );

    instance.registerModule(pluginBeta, {
      endpoint: 'edge-endpoint',
      type: EndpointType.DECORATE,
      moduleName: 'unordered-nan',
      domHook,
      order: Number.NaN,
    });
    instance.registerModule(pluginAlpha, {
      endpoint: 'edge-endpoint',
      type: EndpointType.DECORATE,
      moduleName: 'unordered-infinity',
      domHook,
      order: Number.POSITIVE_INFINITY,
    });
    instance.registerModule(pluginBeta, {
      endpoint: 'edge-endpoint',
      type: EndpointType.DECORATE,
      moduleName: 'max-safe-int',
      domHook,
      order: Number.MAX_SAFE_INTEGER,
    });

    const details = instance.getDetails('edge-endpoint');
    assert.deepEqual(
      details.map(m => m.moduleName),
      ['max-safe-int', 'unordered-infinity', 'unordered-nan']
    );
    assert.strictEqual(details[1].order, undefined);
    assert.strictEqual(details[2].order, undefined);

    // Mutating the returned array must not affect subsequent calls.
    details.reverse();
    assert.deepEqual(
      instance.getDetails('edge-endpoint').map(m => m.moduleName),
      ['max-safe-int', 'unordered-infinity', 'unordered-nan']
    );
  });
});
