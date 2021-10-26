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
import '../styles/themes/app-theme';
import {getPluginLoader} from './shared/gr-js-api-interface/gr-plugin-loader';
import {stubRestApi} from '../test/test-utils';
import {GrApp} from './gr-app';
import {
  createAccountDetailWithId,
  createServerInfo,
} from '../test/test-data-generators';

const basicFixture = fixtureFromElement('gr-app');

suite('gr-app custom light theme tests', () => {
  let element: GrApp;
  setup(async () => {
    window.localStorage.removeItem('dark-theme');
    stubRestApi('getConfig').returns(Promise.resolve(createServerInfo()));
    stubRestApi('getAccount').returns(
      Promise.resolve(createAccountDetailWithId())
    );
    stubRestApi('getDiffComments').returns(Promise.resolve({}));
    stubRestApi('getDiffRobotComments').returns(Promise.resolve({}));
    stubRestApi('getDiffDrafts').returns(Promise.resolve({}));
    element = basicFixture.instantiate();
    getPluginLoader().loadPlugins([]);
    await getPluginLoader().awaitPluginsLoaded();
    await flush();
  });
  teardown(() => {
    // The app sends requests to server. This can lead to
    // unexpected gr-alert elements in document.body
    document.body.querySelectorAll('gr-alert').forEach(grAlert => {
      grAlert.remove();
    });
  });

  test('should not load dark theme', () => {
    assert.isFalse(!!document.head.querySelector('#dark-theme'));
    assert.isTrue(!!document.head.querySelector('#light-theme'));
  });

  test('applies the right theme', () => {
    assert.equal(
      getComputedStyleValue('--header-background-color', element).toLowerCase(),
      '#f1f3f4'
    );
    assert.equal(
      getComputedStyleValue('--footer-background-color', element).toLowerCase(),
      'transparent'
    );
  });
});
