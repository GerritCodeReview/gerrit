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
import {FlagsServiceImplementation} from './flags_impl.js';

suite('flags tests', () => {
  let originalEnabledExperiments;
  let flags;

  suiteSetup(() => {
    originalEnabledExperiments = window.ENABLED_EXPERIMENTS;
    window.ENABLED_EXPERIMENTS = ['a', 'a'];
    flags = new FlagsServiceImplementation();
  });

  suiteTeardown(() => {
    window.ENABLED_EXPERIMENTS = originalEnabledExperiments;
  });

  test('isEnabled', () => {
    assert.equal(flags.isEnabled('a'), true);
    assert.equal(flags.isEnabled('random'), false);
  });

  test('enabledExperiments', () => {
    assert.deepEqual(flags.enabledExperiments, ['a']);
  });
});

