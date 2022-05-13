/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {AppContext} from '../services/app-context';
import '../test/common-test-setup-karma';
import {createDiffAppContext} from './gr-diff-app-context-init';

suite('gr diff app context initializer tests', () => {
  test('all services initialized and are singletons', () => {
    const appContext: AppContext = createDiffAppContext();
    for (const serviceName of Object.keys(appContext) as Array<
      keyof AppContext
    >) {
      const service = appContext[serviceName];
      assert.isNotNull(service);
      const service2 = appContext[serviceName];
      assert.strictEqual(service, service2);
    }
  });
});
