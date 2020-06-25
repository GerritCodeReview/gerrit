/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
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
import '../gr-endpoint-decorator/gr-endpoint-decorator.js';
import {pluginLoader} from '../../shared/gr-js-api-interface/gr-plugin-loader.js';
import {_testOnly_initGerritPluginApi} from '../../shared/gr-js-api-interface/gr-gerrit.js';
import {html} from '@polymer/polymer/lib/utils/html-tag.js';

const basicFixture = fixtureFromTemplate(html`
<gr-endpoint-decorator name="settings-menu-item">
    </gr-endpoint-decorator>
    <gr-endpoint-decorator name="settings-screen">
    </gr-endpoint-decorator>
`);

const pluginApi = _testOnly_initGerritPluginApi();

suite('gr-settings-api tests', () => {
  let sandbox;
  let settingsApi;

  setup(() => {
    sandbox = sinon.sandbox.create();
    let plugin;
    pluginApi.install(p => { plugin = p; }, '0.1',
        'http://test.com/plugins/testplugin/static/test.js');
    pluginLoader.loadPlugins([]);
    settingsApi = plugin.settings();
  });

  teardown(() => {
    settingsApi = null;
    sandbox.restore();
  });

  test('exists', () => {
    assert.isOk(settingsApi);
  });

  test('works', done => {
    settingsApi
        .title('foo')
        .token('bar')
        .module('some-settings-screen')
        .build();
    const element = basicFixture.instantiate();
    flush(() => {
      const [menuItemEl, itemEl] = element;
      const menuItem = menuItemEl.shadowRoot
          .querySelector('gr-settings-menu-item');
      assert.isOk(menuItem);
      assert.equal(menuItem.title, 'foo');
      assert.equal(menuItem.href, '#x/testplugin/bar');
      const item = itemEl.shadowRoot
          .querySelector('gr-settings-item');
      assert.isOk(item);
      assert.equal(item.title, 'foo');
      assert.equal(item.anchor, 'x/testplugin/bar');
      done();
    });
  });
});

