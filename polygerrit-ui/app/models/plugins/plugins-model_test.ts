/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../test/common-test-setup';
import * as sinon from 'sinon';
import './plugins-model';
import {ChecksApiConfig, ChecksProvider, ResponseCode} from '../../api/checks';
import {
  ChecksPlugin,
  ChecksUpdate,
  DiffLayerPlugin,
  PluginsModel,
} from './plugins-model';
import {createRun, createRunResult} from '../../test/test-data-generators';
import {assert} from '@open-wc/testing';

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
  let diffLayerPlugins: DiffLayerPlugin[] = [];
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
      diffLayerPlugins = s.diffLayerPlugins;
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
      run: createRun(),
      result: createRunResult(),
    });

    assert.equal(update?.pluginName, PLUGIN_NAME);
  });

  test('diffLayerRegister', async () => {
    assert.isFalse(diffLayerPlugins.some(p => p.pluginName === PLUGIN_NAME));

    const factory = () => {
      return {
        annotate: () => {},
      };
    };
    model.diffLayerRegister({
      pluginName: PLUGIN_NAME,
      factory,
    });

    assert.isTrue(diffLayerPlugins.some(p => p.pluginName === PLUGIN_NAME));
    assert.equal(
      diffLayerPlugins.find(p => p.pluginName === PLUGIN_NAME)?.factory,
      factory
    );

    // Try to register again
    const consoleWarnStub = sinon.stub(console, 'warn');
    model.diffLayerRegister({
      pluginName: PLUGIN_NAME,
      factory,
    });
    assert.isTrue(consoleWarnStub.calledOnce);
    consoleWarnStub.restore();
  });
});
