/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../test/common-test-setup-karma';
import {getComputedStyleValue} from '../utils/dom-util';
import './gr-app';
import {getPluginLoader} from './shared/gr-js-api-interface/gr-plugin-loader';
import {GrApp} from './gr-app';

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
    // The app sends requests to server. This can lead to
    // unexpected gr-alert elements in document.body
    document.body.querySelectorAll('gr-alert').forEach(grAlert => {
      grAlert.remove();
    });
  });

  test('should tried to load dark theme', () => {
    assert.isTrue(!!document.head.querySelector('#dark-theme'));
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
