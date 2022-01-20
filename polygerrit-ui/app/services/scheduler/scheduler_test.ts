/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import '../../test/common-test-setup-karma.js';
import {assertFails} from '../../test/test-utils.js';
import {NaiveScheduler} from './scheduler';

suite('naive scheduler', () => {
  let scheduler: NaiveScheduler<number>;
  setup(() => {
    scheduler = new NaiveScheduler<number>();
  });

  test('executes tasks', async () => {
    const promise = scheduler.schedule(async () => {
      return 1;
    })
    const val = await promise;
    assert.equal(val, 1);
  });

  test('propagates errors', async () => {
    const error = new Error('This is an error');
    const promise = scheduler.schedule(async () => {
      throw error;
    });
    assertFails(promise, error);
    await promise.catch((reason: Error) => {
      assert.equal(reason, error);
    });
  });
 });
