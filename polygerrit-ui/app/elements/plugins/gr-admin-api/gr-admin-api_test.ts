/**
 * @license
 * Copyright 2018 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {AdminPluginApi} from '../../../api/admin';
import {PluginApi} from '../../../api/plugin';
import '../../../test/common-test-setup-karma';
import '../../shared/gr-js-api-interface/gr-js-api-interface';
import {getPluginLoader} from '../../shared/gr-js-api-interface/gr-plugin-loader';

suite('gr-admin-api tests', () => {
  let adminApi: AdminPluginApi;
  let plugin: PluginApi;

  setup(() => {
    window.Gerrit.install(
      p => {
        plugin = p;
      },
      '0.1',
      'http://test.com/plugins/testplugin/static/test.js'
    );
    getPluginLoader().loadPlugins([]);
    adminApi = plugin.admin();
  });

  test('exists', () => {
    assert.isOk(adminApi);
  });

  test('addMenuLink', () => {
    adminApi.addMenuLink('text', 'url');
    const links = adminApi.getMenuLinks();
    assert.equal(links.length, 1);
    assert.deepEqual(links[0], {text: 'text', url: 'url', capability: null});
  });

  test('addMenuLinkWithCapability', () => {
    adminApi.addMenuLink('text', 'url', 'capability');
    const links = adminApi.getMenuLinks();
    assert.equal(links.length, 1);
    assert.deepEqual(links[0], {
      text: 'text',
      url: 'url',
      capability: 'capability',
    });
  });
});
