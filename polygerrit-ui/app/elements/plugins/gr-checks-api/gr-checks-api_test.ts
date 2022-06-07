/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
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
