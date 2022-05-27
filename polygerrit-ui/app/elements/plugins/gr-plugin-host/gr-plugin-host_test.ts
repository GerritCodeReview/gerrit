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
import {ServerInfo} from '../../../api/rest-api';

suite('gr-plugin-host tests', () => {
  let element: GrPluginHost;

  setup(async () => {
    element = await fixture<GrPluginHost>(html`
      <gr-plugin-host></gr-plugin-host>
    `);

    sinon.stub(document.body, 'appendChild');
  });

  test('load plugins should be called', async () => {
    const loadPluginsStub = sinon.stub(getPluginLoader(), 'loadPlugins');
    element.config = {
      plugin: {
        has_avatars: false,
        js_resource_paths: ['plugins/42', 'plugins/foo/bar', 'plugins/baz'],
      },
    } as ServerInfo;
    await flush();
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
    const loadPluginsStub = sinon.stub(getPluginLoader(), 'loadPlugins');
    element.config = {
      default_theme: 'gerrit-theme.js',
      plugin: {
        has_avatars: false,
        js_resource_paths: ['plugins/42', 'plugins/foo/bar', 'plugins/baz'],
      },
    } as ServerInfo;
    await flush();
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
});
