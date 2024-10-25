/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import * as sinon from 'sinon';
import {getAppContext} from './app-context';
import '../test/common-test-setup';
import {ServiceWorkerInstaller} from './service-worker-installer';
import {assert} from '@open-wc/testing';
import {createDefaultPreferences} from '../constants/constants';
import {waitUntilObserved} from '../test/test-utils';
import {testResolver} from '../test/common-test-setup';
import {userModelToken} from '../models/user/user-model';

suite('service worker installer tests', () => {
  test('init', async () => {
    const registerStub = sinon.stub(window.navigator.serviceWorker, 'register');
    const flagsService = getAppContext().flagsService;
    const reportingService = getAppContext().reportingService;
    const userModel = testResolver(userModelToken);
    sinon.stub(flagsService, 'isEnabled').returns(true);
    new ServiceWorkerInstaller(flagsService, reportingService, userModel);
    // TODO: There is a race-condition betweeen preferences being set here
    // and being loaded from the rest-api-service when the user-model gets created.
    // So we explicitly wait for the allow_browser_notifications to be false
    // before continuing with the test.
    // Ideally there's a way to wait for models to stabilize.
    const prefs = {
      ...createDefaultPreferences(),
      allow_browser_notifications: false,
    };
    await waitUntilObserved(
      userModel.preferences$,
      pref => pref.allow_browser_notifications === false
    );
    userModel.setPreferences(prefs);

    const prefs2 = {
      ...createDefaultPreferences(),
      allow_browser_notifications: true,
    };
    userModel.setPreferences(prefs2);
    await waitUntilObserved(
      userModel.preferences$,
      pref => pref.allow_browser_notifications === true
    );

    assert.isTrue(registerStub.called);
  });
});
