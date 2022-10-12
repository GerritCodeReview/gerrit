/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {getAppContext} from './app-context';
import '../test/common-test-setup';
import {ServiceWorkerInstaller} from './service-worker-installer';
import {assert} from '@open-wc/testing';
import {createDefaultPreferences} from '../constants/constants';
import {waitUntilObserved} from '../test/test-utils';
import {UserModel} from '../models/user/user-model';
import {FlagsService} from './flags/flags';

suite('service worker installer tests', () => {
  let serviceWorkerInstaller: ServiceWorkerInstaller;
  let userModel: UserModel;
  let flagsService: FlagsService;

  setup(async () => {
    flagsService = getAppContext().flagsService;
    userModel = getAppContext().userModel;
    sinon.stub(flagsService, 'isEnabled').returns(true);
    serviceWorkerInstaller = new ServiceWorkerInstaller(
      flagsService,
      userModel
    );
    const prefs = {
      ...createDefaultPreferences(),
      allow_browser_notifications: true,
    };
    userModel.setPreferences(prefs);
    await waitUntilObserved(
      userModel.preferences$,
      pref => pref.allow_browser_notifications === true
    );
  });

  test('init', async () => {
    const registerStub = sinon.stub(window.navigator.serviceWorker, 'register');
    await serviceWorkerInstaller.init();
    assert.isTrue(registerStub.called);
  });
});
