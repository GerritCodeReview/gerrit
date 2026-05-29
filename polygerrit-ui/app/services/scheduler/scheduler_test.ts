/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {assert} from '@open-wc/testing';
import '../../test/common-test-setup';
import {assertFails} from '../../test/test-utils';
import {BaseScheduler} from './scheduler';

suite('naive scheduler', () => {
  let scheduler: BaseScheduler<number>;
  setup(() => {
    scheduler = new BaseScheduler<number>();
  });

  test('executes tasks', async () => {
    const promise = scheduler.schedule(async () => 1);
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
