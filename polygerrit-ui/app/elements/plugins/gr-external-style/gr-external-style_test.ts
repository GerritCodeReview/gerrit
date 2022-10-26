/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import {mockPromise, MockPromise, resetPlugins} from '../../../test/test-utils';
import './gr-external-style';
import {GrExternalStyle} from './gr-external-style';
import {PluginApi} from '../../../api/plugin';
import {fixture, html, assert} from '@open-wc/testing';
import {testResolver} from '../../../test/common-test-setup';
import {pluginLoaderToken} from '../../shared/gr-js-api-interface/gr-plugin-loader';

suite('gr-external-style integration tests', () => {
  const TEST_URL = 'http://some.com/plugins/url.js';

  let element: GrExternalStyle;
  let plugin: PluginApi;
  let pluginsLoaded: MockPromise<void>;
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
    applyStyleSpy = sinon.spy(GrExternalStyle.prototype, 'applyStyle');
    element = await fixture(
      html`<gr-external-style .name=${'foo'}></gr-external-style>`
    );
    await element.updateComplete;
  };

  /**
   * Installs the plugin, creates the element, registers style module.
   */
  const lateRegister = async () => {
    installPlugin();
    await createElement();
    plugin.registerStyleModule('foo', 'some-module');
  };

  /**
   * Installs the plugin, registers style module, creates the element.
   */
  const earlyRegister = async () => {
    installPlugin();
    plugin.registerStyleModule('foo', 'some-module');
    await createElement();
  };

  setup(() => {
    pluginsLoaded = mockPromise();
    sinon
      .stub(testResolver(pluginLoaderToken), 'awaitPluginsLoaded')
      .returns(pluginsLoaded);
  });

  teardown(() => {
    resetPlugins();
    document.body
      .querySelectorAll('custom-style')
      .forEach(style => style.remove());
  });

  test('applies plugin-provided styles', async () => {
    await lateRegister();
    pluginsLoaded.resolve();
    await element.updateComplete;
    assert.isTrue(applyStyleSpy.calledWith('some-module'));
  });

  test('does not double apply', async () => {
    await earlyRegister();
    await element.updateComplete;
    plugin.registerStyleModule('foo', 'some-module');
    await element.updateComplete;
    const stylesApplied = element.stylesApplied.filter(
      name => name === 'some-module'
    );
    assert.strictEqual(stylesApplied.length, 1);
  });

  test('loads and applies preloaded modules', async () => {
    await earlyRegister();
    await element.updateComplete;
    assert.isTrue(applyStyleSpy.calledWith('some-module'));
  });

  test('removes old custom-style if name is changed', async () => {
    installPlugin();
    plugin.registerStyleModule('bar', 'some-module');
    await earlyRegister();
    await element.updateComplete;
    let customStyles = document.body.querySelectorAll('custom-style');
    assert.strictEqual(customStyles.length, 1);
    element.name = 'bar';
    await element.updateComplete;
    customStyles = document.body.querySelectorAll('custom-style');
    assert.strictEqual(customStyles.length, 1);
    element.name = 'baz';
    await element.updateComplete;
    customStyles = document.body.querySelectorAll('custom-style');
    assert.strictEqual(customStyles.length, 0);
  });

  test('can apply more than one style', async () => {
    await earlyRegister();
    await element.updateComplete;
    plugin.registerStyleModule('foo', 'some-module2');
    pluginsLoaded.resolve();
    await element.updateComplete;
    assert.strictEqual(element.stylesApplied.length, 2);
    const customStyles = document.body.querySelectorAll('custom-style');
    assert.strictEqual(customStyles.length, 2);
  });
});
