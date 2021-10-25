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

import '../test/common-test-setup-karma';
import {getComputedStyleValue} from '../utils/dom-util';
import './gr-app';
import {GrApp} from './gr-app';
import {GrAlert} from './shared/gr-alert/gr-alert';
import {getPluginLoader} from './shared/gr-js-api-interface/gr-plugin-loader';
import {removeTheme} from '../styles/themes/dark-theme';
import {query, queryAll} from '../test/test-utils';

const basicFixture = fixtureFromElement('gr-app');

suite('gr-app custom dark theme tests', () => {
  let element: GrApp;

  setup(async () => {
    window.localStorage.setItem('dark-theme', 'true');

    element = basicFixture.instantiate();
    getPluginLoader().loadPlugins([]);
    await getPluginLoader().awaitPluginsLoaded();
    await flush();
  });

  teardown(() => {
    window.localStorage.removeItem('dark-theme');
    removeTheme();
    // The app sends requests to server. This can lead to
    // unexpected gr-alert elements in document.body
    queryAll<GrAlert>(document.body, 'gr-alert').forEach(grAlert => {
      grAlert.remove();
    });
  });

  test('should tried to load dark theme', () => {
    assert.isTrue(!!query(document.head, '#dark-theme'));
  });

  test('applies the right theme', () => {
    assert.equal(
      getComputedStyleValue('--header-background-color', element).toLowerCase(),
      '#3c4043'
    );
    assert.equal(
      getComputedStyleValue('--footer-background-color', element).toLowerCase(),
      '#3c4043'
    );
  });
});
