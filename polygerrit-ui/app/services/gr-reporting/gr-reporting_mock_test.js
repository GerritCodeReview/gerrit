/**
 * @license
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import '../../test/common-test-setup-karma.js';
import {GrReporting} from './gr-reporting_impl.js';
import {grReportingMock} from './gr-reporting_mock.js';
suite('gr-reporting_mock tests', () => {
  test('mocks all public methods', () => {
    const methods = Object.getOwnPropertyNames(GrReporting.prototype)
        .filter(name => typeof GrReporting.prototype[name] === 'function')
        .filter(name => !name.startsWith('_') && name !== 'constructor')
        .sort();
    const mockMethods = Object.getOwnPropertyNames(grReportingMock)
        .sort();
    assert.deepEqual(methods, mockMethods);
  });
});

