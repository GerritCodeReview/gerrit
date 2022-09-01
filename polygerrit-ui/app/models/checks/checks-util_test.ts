/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../test/common-test-setup-karma';
import './checks-model';
import {assert} from '@open-wc/testing';
import {
  ALL_ATTEMPTS,
  AttemptChoice,
  LATEST_ATTEMPT,
  sortAttemptChoices,
  stringToAttemptChoice,
} from './checks-util';

suite('checks-util tests', () => {
  setup(() => {});

  teardown(() => {});

  test('stringToAttemptChoice', () => {
    assert.equal(stringToAttemptChoice('0'), 0);
    assert.equal(stringToAttemptChoice('1'), 1);
    assert.equal(stringToAttemptChoice('999'), 999);
    assert.equal(stringToAttemptChoice('latest'), 'latest');
    assert.equal(stringToAttemptChoice('all'), 'all');

    assert.equal(stringToAttemptChoice(undefined), undefined);
    assert.equal(stringToAttemptChoice(''), undefined);
    assert.equal(stringToAttemptChoice('asdf'), undefined);
    assert.equal(stringToAttemptChoice('-1'), undefined);
    assert.equal(stringToAttemptChoice('1x'), undefined);
  });

  test('sortAttemptChoices', () => {
    const unsorted: (AttemptChoice | undefined)[] = [
      3,
      1,
      LATEST_ATTEMPT,
      ALL_ATTEMPTS,
      undefined,
      0,
      999,
    ];
    const sortedExpected: (AttemptChoice | undefined)[] = [
      LATEST_ATTEMPT,
      ALL_ATTEMPTS,
      0,
      1,
      3,
      999,
      undefined,
    ];
    assert.deepEqual(unsorted.sort(sortAttemptChoices), sortedExpected);
  });
});
