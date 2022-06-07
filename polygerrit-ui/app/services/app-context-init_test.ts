/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../test/common-test-setup-karma.js';
import {AppContext} from './app-context.js';
import {Finalizable} from './registry';
import {createTestAppContext} from '../test/test-app-context-init.js';

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
