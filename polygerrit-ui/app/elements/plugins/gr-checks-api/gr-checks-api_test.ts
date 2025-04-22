/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import {PluginApi} from '../../../api/plugin';
import {ChecksPluginApi} from '../../../api/checks';
import {assert} from '@open-wc/testing';
import {testResolver} from '../../../test/common-test-setup';
import {pluginLoaderToken} from '../../shared/gr-js-api-interface/gr-plugin-loader';

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
    testResolver(pluginLoaderToken).loadPlugins([]);
    assert.isOk(pluginApi);
    checksApi = (pluginApi as PluginApi).checks();
  });

  teardown(() => {
    checksApi = undefined;
  });

  test('exists', () => {
    assert.isOk(checksApi);
  });
});
