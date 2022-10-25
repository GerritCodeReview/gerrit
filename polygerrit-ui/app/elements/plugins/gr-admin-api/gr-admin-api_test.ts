/**
 * @license
 * Copyright 2018 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {assert} from '@open-wc/testing';
import {AdminPluginApi} from '../../../api/admin';
import {PluginApi} from '../../../api/plugin';
import {getAppContext} from '../../../services/app-context';
import '../../../test/common-test-setup';
import '../../shared/gr-js-api-interface/gr-js-api-interface';

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
    getAppContext().pluginLoader.loadPlugins([]);
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
