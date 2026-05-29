/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../test/common-test-setup';
import {AppContext} from './app-context';
import {createTestAppContext} from '../test/test-app-context-init';
import {assert} from '@open-wc/testing';
import {Finalizable} from '../types/types';

suite('app context initializer tests', () => {
  let appContext: AppContext & Finalizable;
  setup(() => {
    appContext = createTestAppContext();
  });

  teardown(() => {
    appContext.finalize();
  });

  test('all services initialized and are singletons', () => {
    Object.keys(appContext).forEach(serviceName => {
      const service = appContext[serviceName as keyof AppContext];
      assert.isDefined(service);
      assert.isNotNull(service);
      const service2 = appContext[serviceName as keyof AppContext];
      assert.strictEqual(service, service2);
    });
  });
});
