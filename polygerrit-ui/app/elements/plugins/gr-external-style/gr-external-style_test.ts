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
import '../../../test/common-test-setup-karma';
import {resetPlugins} from '../../../test/test-utils';
import './gr-external-style.js';
import {GrExternalStyle} from './gr-external-style.js';
import {getPluginLoader} from '../../shared/gr-js-api-interface/gr-plugin-loader';
import {html} from '@polymer/polymer/lib/utils/html-tag';
import {PluginApi} from '../../../api/plugin';

const basicFixture = fixtureFromTemplate(
  html`<gr-external-style name="foo"></gr-external-style>`
);

suite('gr-external-style integration tests', () => {
  const TEST_URL = 'http://some.com/plugins/url.js';

  let element: GrExternalStyle;
  let plugin: PluginApi;
  let applyStyleSpy: sinon.SinonSpy;

  const installPlugin = () => {
    if (plugin) {
      return;
    }
    window.Gerrit.install(
      p => {
        plugin = p;
      },
      '0.1',
      TEST_URL
    );
  };

  const createElement = async () => {
    element = basicFixture.instantiate() as GrExternalStyle;
    applyStyleSpy = sinon.spy(element, 'applyStyle');
    await element.updateComplete;
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
    sinon
      .stub(getPluginLoader(), 'awaitPluginsLoaded')
      .returns(Promise.resolve());
  });

  teardown(() => {
    resetPlugins();
    document.body
      .querySelectorAll('custom-style')
      .forEach(style => style.remove());
  });

  test('applies plugin-provided styles', async () => {
    lateRegister();
    await element.updateComplete;
    assert.isTrue(applyStyleSpy.calledWith('some-module'));
  });

  test('does not double apply', async () => {
    earlyRegister();
    await element.updateComplete;
    plugin.registerStyleModule('foo', 'some-module');
    await element.updateComplete;
    const stylesApplied = element.stylesApplied.filter(
      name => name === 'some-module'
    );
    assert.strictEqual(stylesApplied.length, 1);
  });

  test('loads and applies preloaded modules', async () => {
    earlyRegister();
    await element.updateComplete;
    assert.isTrue(applyStyleSpy.calledWith('some-module'));
  });
});
