/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../test/common-test-setup-karma';
import {FlagsServiceImplementation} from './flags_impl';

suite('flags tests', () => {
  let originalEnabledExperiments: string[] | undefined;
  let flags: FlagsServiceImplementation;

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
