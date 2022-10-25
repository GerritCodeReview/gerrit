/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import {PluginApi} from '../../../api/plugin';
import {ChecksPluginApi} from '../../../api/checks';
import {assert} from '@open-wc/testing';
import {getAppContext} from '../../../services/app-context';

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
    getAppContext().pluginLoader.loadPlugins([]);
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
