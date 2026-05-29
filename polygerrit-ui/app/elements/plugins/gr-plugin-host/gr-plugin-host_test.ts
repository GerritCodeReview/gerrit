/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import * as sinon from 'sinon';
import '../../../test/common-test-setup';
import './gr-plugin-host';
import {GrPluginHost} from './gr-plugin-host';
import {assert, fixture, html} from '@open-wc/testing';
import {SinonStubbedMember} from 'sinon';
import {createServerInfo} from '../../../test/test-data-generators';
import {
  ConfigModel,
  configModelToken,
} from '../../../models/config/config-model';
import {testResolver} from '../../../test/common-test-setup';
import {
  PluginLoader,
  pluginLoaderToken,
} from '../../shared/gr-js-api-interface/gr-plugin-loader';

suite('gr-plugin-host tests', () => {
  let element: GrPluginHost;
  let loadPluginsStub: SinonStubbedMember<PluginLoader['loadPlugins']>;
  let configModel: ConfigModel;

  setup(async () => {
    loadPluginsStub = sinon.stub(
      testResolver(pluginLoaderToken),
      'loadPlugins'
    );
    element = await fixture<GrPluginHost>(html`
      <gr-plugin-host></gr-plugin-host>
    `);
    await element.updateComplete;
    configModel = testResolver(configModelToken);

    sinon.stub(document.body, 'appendChild');
  });

  test('load plugins should be called', async () => {
    loadPluginsStub.reset();
    configModel.updateServerConfig({
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
    configModel.updateServerConfig({
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
    configModel.updateServerConfig(config);
    assert.isTrue(loadPluginsStub.calledOnce);
    assert.isTrue(loadPluginsStub.calledWith([], 'test-id'));
  });
});
