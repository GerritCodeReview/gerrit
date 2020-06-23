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

import '../test/common-test-setup-karma.js';
import './shared/gr-rest-api-interface/gr-rest-api-interface.js';
import './gr-app.js';
import {pluginLoader} from './shared/gr-js-api-interface/gr-plugin-loader.js';
const basicFixture = fixtureFromElement('gr-app');
suite('gr-app custom dark theme tests', () => {
  let sandbox;
  setup(done => {
    window.localStorage.setItem('dark-theme', 'true');
    sandbox = sinon.sandbox.create();
    basicFixture.instantiate();
    pluginLoader.loadPlugins([]);
    pluginLoader.awaitPluginsLoaded().then(() => flush(done));
  });
  teardown(() => {
    sandbox.restore();
    window.localStorage.removeItem('dark-theme');
    const addedTheme = document
        .querySelector('link[href="/styles/themes/dark-theme.html"]');
    if (addedTheme) {
      addedTheme.remove();
    }
  });

  test('should tried to load dark theme', () => {
    assert.isTrue(
        !!document.querySelector('link[href="/styles/themes/dark-theme.html"]')
    );
  });
});