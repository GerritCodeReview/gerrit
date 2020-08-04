/**
 * @license
 * Copyright (C) 2020 The Android Open Source Project
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
import {resetPlugins} from '../../../test/test-utils.js';
import './gr-external-style.js';
import {pluginLoader} from '../../shared/gr-js-api-interface/gr-plugin-loader.js';
import {getPluginEndpoints} from '../../shared/gr-js-api-interface/gr-plugin-endpoints.js';
import {_testOnly_initGerritPluginApi} from '../../shared/gr-js-api-interface/gr-gerrit.js';
import {html} from '@polymer/polymer/lib/utils/html-tag.js';

const pluginApi = _testOnly_initGerritPluginApi();

const basicFixture = fixtureFromTemplate(
    html`<gr-external-style name="foo"></gr-external-style>`
);

suite('gr-external-style integration tests', () => {
  const TEST_URL = 'http://some.com/plugins/url.html';

  let element;
  let plugin;

  const installPlugin = () => {
    if (plugin) { return; }
    pluginApi.install(p => {
      plugin = p;
    }, '0.1', TEST_URL);
  };

  const createElement = () => {
    element = basicFixture.instantiate();
    sinon.spy(element, '_applyStyle');
  };

  /**
   * Installs the plugin, creates the element, registers style module.
   */
  const lateRegister = () => {
    installPlugin();
    createElement();
    plugin.registerStyleModule('foo', 'some-module');
  };

  /**
   * Installs the plugin, registers style module, creates the element.
   */
  const earlyRegister = () => {
    installPlugin();
    plugin.registerStyleModule('foo', 'some-module');
    createElement();
  };

  setup(() => {
    sinon.stub(getPluginEndpoints(), 'importUrl')
        .callsFake( url => Promise.resolve());
    sinon.stub(pluginLoader, 'awaitPluginsLoaded')
        .returns(Promise.resolve());
  });

  teardown(() => {
    resetPlugins();
    document.body.querySelectorAll('custom-style')
        .forEach(style => style.remove());
  });

  test('imports plugin-provided module', async () => {
    lateRegister();
    await new Promise(flush);
    assert.isTrue(getPluginEndpoints().importUrl.calledWith(new URL(TEST_URL)));
  });

  test('applies plugin-provided styles', async () => {
    lateRegister();
    await new Promise(flush);
    assert.isTrue(element._applyStyle.calledWith('some-module'));
  });

  test('does not double import', async () => {
    earlyRegister();
    await new Promise(flush);
    plugin.registerStyleModule('foo', 'some-module');
    await new Promise(flush);
    // since loaded, should not call again
    assert.isFalse(getPluginEndpoints().importUrl.calledOnce);
  });

  test('does not double apply', async () => {
    earlyRegister();
    await new Promise(flush);
    plugin.registerStyleModule('foo', 'some-module');
    await new Promise(flush);
    const stylesApplied =
        element._stylesApplied.filter(name => name === 'some-module');
    assert.strictEqual(stylesApplied.length, 1);
  });

  test('loads and applies preloaded modules', async () => {
    earlyRegister();
    await new Promise(flush);
    assert.isTrue(element._applyStyle.calledWith('some-module'));
  });
});
