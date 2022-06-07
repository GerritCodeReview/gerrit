/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../test/common-test-setup-karma';
import {GrReporting} from './gr-reporting_impl';
import {grReportingMock} from './gr-reporting_mock';

suite('gr-reporting_mock tests', () => {
  test('mocks all public methods', () => {
    const methods = Object.getOwnPropertyNames(GrReporting.prototype)
      .filter(
        name => typeof (GrReporting as any).prototype[name] === 'function'
      )
      .filter(name => !name.startsWith('_') && name !== 'constructor')
      .sort();
    const mockMethods = Object.getOwnPropertyNames(grReportingMock).sort();
    assert.deepEqual(methods, mockMethods);
  });
});
