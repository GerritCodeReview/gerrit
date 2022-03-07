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
