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
import {getPluginLoader} from '../../shared/gr-js-api-interface/gr-plugin-loader.js';

const basicFixture = fixtureFromElement('gr-plugin-host');

suite('gr-plugin-host tests', () => {
  let element;

  setup(() => {
    element = basicFixture.instantiate();

    sinon.stub(document.body, 'appendChild');
  });

  test('load plugins should be called', () => {
    sinon.stub(getPluginLoader(), 'loadPlugins');
    element.config = {
      plugin: {
        js_resource_paths: ['plugins/42', 'plugins/foo/bar', 'plugins/baz'],
      },
    };
    assert.isTrue(getPluginLoader().loadPlugins.calledOnce);
    assert.isTrue(getPluginLoader().loadPlugins.calledWith([
      'plugins/42', 'plugins/foo/bar', 'plugins/baz',
    ]));
  });

  test('theme plugins should be loaded if enabled', () => {
    sinon.stub(getPluginLoader(), 'loadPlugins');
    element.config = {
      default_theme: 'gerrit-theme.js',
      plugin: {
        js_resource_paths: ['plugins/42', 'plugins/foo/bar', 'plugins/baz'],
      },
    };
    assert.isTrue(getPluginLoader().loadPlugins.calledOnce);
    assert.isTrue(getPluginLoader().loadPlugins.calledWith([
      'gerrit-theme.js', 'plugins/42', 'plugins/foo/bar', 'plugins/baz',
    ]));
  });
});

