/**
 * @license
 * Copyright (C) 2018 The Android Open Source Project
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
import '../../shared/gr-js-api-interface/gr-js-api-interface.js';
import {pluginLoader} from '../../shared/gr-js-api-interface/gr-plugin-loader.js';
import {_testOnly_initGerritPluginApi} from '../../shared/gr-js-api-interface/gr-gerrit.js';

const pluginApi = _testOnly_initGerritPluginApi();

suite('gr-admin-api tests', () => {
  let sandbox;
  let adminApi;

  setup(() => {
    sandbox = sinon.sandbox.create();
    let plugin;
    pluginApi.install(p => { plugin = p; }, '0.1',
        'http://test.com/plugins/testplugin/static/test.js');
    pluginLoader.loadPlugins([]);
    adminApi = plugin.admin();
  });

  teardown(() => {
    adminApi = null;
    sandbox.restore();
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
    assert.deepEqual(links[0],
        {text: 'text', url: 'url', capability: 'capability'});
  });
});

