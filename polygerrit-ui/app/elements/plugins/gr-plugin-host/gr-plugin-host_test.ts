/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup-karma';
import './gr-plugin-host';
import {getPluginLoader} from '../../shared/gr-js-api-interface/gr-plugin-loader';
import {GrPluginHost} from './gr-plugin-host';
import {fixture, html} from '@open-wc/testing-helpers';
import {SinonStub} from 'sinon';
import {createServerInfo} from '../../../test/test-data-generators';

suite('gr-plugin-host tests', () => {
  let element: GrPluginHost;
  let loadPluginsStub: SinonStub;

  setup(async () => {
    loadPluginsStub = sinon.stub(getPluginLoader(), 'loadPlugins');
    element = await fixture<GrPluginHost>(html`
      <gr-plugin-host></gr-plugin-host>
    `);
    await element.updateComplete;

    sinon.stub(document.body, 'appendChild');
  });

  test('load plugins should be called', async () => {
    loadPluginsStub.reset();
    element.getConfigModel().updateServerConfig({
      ...createServerInfo(),
      plugin: {
        has_avatars: false,
        js_resource_paths: ['plugins/42', 'plugins/foo/bar', 'plugins/baz'],
      },
    });
    assert.isTrue(loadPluginsStub.calledOnce);
    assert.isTrue(
      loadPluginsStub.calledWith([
        'plugins/42',
        'plugins/foo/bar',
        'plugins/baz',
      ])
    );
  });

  test('theme plugins should be loaded if enabled', async () => {
    loadPluginsStub.reset();
    element.getConfigModel().updateServerConfig({
      ...createServerInfo(),
      default_theme: 'gerrit-theme.js',
      plugin: {
        has_avatars: false,
        js_resource_paths: ['plugins/42', 'plugins/foo/bar', 'plugins/baz'],
      },
    });
    assert.isTrue(loadPluginsStub.calledOnce);
    assert.isTrue(
      loadPluginsStub.calledWith([
        'gerrit-theme.js',
        'plugins/42',
        'plugins/foo/bar',
        'plugins/baz',
      ])
    );
  });

  test('plugins loaded with instanceId ', async () => {
    loadPluginsStub.reset();
    const config = createServerInfo();
    config.gerrit.instance_id = 'test-id';
    element.getConfigModel().updateServerConfig(config);
    assert.isTrue(loadPluginsStub.calledOnce);
    assert.isTrue(loadPluginsStub.calledWith([], 'test-id'));
  });
});
