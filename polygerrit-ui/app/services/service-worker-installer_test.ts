/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {getAppContext} from './app-context';
import '../test/common-test-setup-karma';
import {ServiceWorkerInstaller} from './service-worker-installer';

suite('service woerker installler tests', () => {
  let serviceWorkerInstaller: ServiceWorkerInstaller;

  setup(() => {
    const flagsService = getAppContext().flagsService;
    sinon.stub(flagsService, 'isEnabled').returns(true);
    serviceWorkerInstaller = new ServiceWorkerInstaller(flagsService);
  });

  test('init', () => {
    const registerStub = sinon.stub(window.navigator.serviceWorker, 'register');
    serviceWorkerInstaller.init();
    assert.isTrue(registerStub.called);
  });
});
