/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {getAppContext} from './app-context';
import '../test/common-test-setup';
import {ServiceWorkerInstaller} from './service-worker-installer';
import {assert} from '@open-wc/testing';

suite('service woerker installler tests', () => {
  let serviceWorkerInstaller: ServiceWorkerInstaller;

  setup(() => {
    const flagsService = getAppContext().flagsService;
    const userModel = getAppContext().userModel;
    sinon.stub(flagsService, 'isEnabled').returns(true);
    serviceWorkerInstaller = new ServiceWorkerInstaller(
      flagsService,
      userModel
    );
  });

  test('init', () => {
    const registerStub = sinon.stub(window.navigator.serviceWorker, 'register');
    serviceWorkerInstaller.init();
    assert.isTrue(registerStub.called);
  });
});
