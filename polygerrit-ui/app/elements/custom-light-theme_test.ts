/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
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
