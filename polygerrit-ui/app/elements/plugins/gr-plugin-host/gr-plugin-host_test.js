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
import './gr-plugin-host.js';
import {pluginLoader} from '../../shared/gr-js-api-interface/gr-plugin-loader.js';

const basicFixture = fixtureFromElement('gr-plugin-host');

suite('gr-plugin-host tests', () => {
  let element;
  let sandbox;

  setup(() => {
    element = basicFixture.instantiate();
    sandbox = sinon.sandbox.create();
    sandbox.stub(document.body, 'appendChild');
  });

  teardown(() => {
    sandbox.restore();
  });

  test('load plugins should be called', () => {
    sandbox.stub(pluginLoader, 'loadPlugins');
    element.config = {
      plugin: {
        html_resource_paths: ['plugins/foo/bar', 'plugins/baz'],
        js_resource_paths: ['plugins/42'],
      },
    };
    assert.isTrue(pluginLoader.loadPlugins.calledOnce);
    assert.isTrue(pluginLoader.loadPlugins.calledWith([
      'plugins/42', 'plugins/foo/bar', 'plugins/baz',
    ], {}));
  });

  test('theme plugins should be loaded if enabled', () => {
    sandbox.stub(pluginLoader, 'loadPlugins');
    element.config = {
      default_theme: 'gerrit-theme.html',
      plugin: {
        html_resource_paths: ['plugins/foo/bar', 'plugins/baz'],
        js_resource_paths: ['plugins/42'],
      },
    };
    assert.isTrue(pluginLoader.loadPlugins.calledOnce);
    assert.isTrue(pluginLoader.loadPlugins.calledWith([
      'gerrit-theme.html', 'plugins/42', 'plugins/foo/bar', 'plugins/baz',
    ], {'gerrit-theme.html': {sync: true}}));
  });

  test('skip theme if preloaded', () => {
    sandbox.stub(pluginLoader, 'isPluginPreloaded')
        .withArgs('preloaded:gerrit-theme')
        .returns(true);
    sandbox.stub(pluginLoader, 'loadPlugins');
    element.config = {
      default_theme: '/oof',
      plugin: {},
    };
    assert.isTrue(pluginLoader.loadPlugins.calledOnce);
    assert.isTrue(pluginLoader.loadPlugins.calledWith([], {}));
  });
});

