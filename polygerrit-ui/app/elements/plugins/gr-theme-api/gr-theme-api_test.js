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

const headerTitleFixture = fixtureFromTemplate(html`
<gr-endpoint-decorator name="header-title">
      <span class="titleText"></span>
    </gr-endpoint-decorator>
`);

const pluginApi = _testOnly_initGerritPluginApi();

suite('gr-theme-api tests', () => {
  let sandbox;
  let theme;

  setup(() => {
    sandbox = sinon.sandbox.create();
    let plugin;
    pluginApi.install(p => { plugin = p; }, '0.1',
        'http://test.com/plugins/testplugin/static/test.js');
    theme = plugin.theme();
  });

  teardown(() => {
    theme = null;
    sandbox.restore();
  });

  test('exists', () => {
    assert.isOk(theme);
  });

  suite('header-title', () => {
    let customHeader;

    setup(() => {
      headerTitleFixture.instantiate();
      stub('gr-custom-plugin-header', {
        /** @override */
        ready() { customHeader = this; },
      });
      pluginLoader.loadPlugins([]);
    });

    test('sets logo and title', done => {
      theme.setHeaderLogoAndTitle('foo.jpg', 'bar');
      flush(() => {
        assert.isNotNull(customHeader);
        assert.equal(customHeader.logoUrl, 'foo.jpg');
        assert.equal(customHeader.title, 'bar');
        done();
      });
    });
  });
});

