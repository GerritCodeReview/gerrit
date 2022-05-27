/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../test/common-test-setup-karma.js';
import {assertFails} from '../../test/test-utils.js';
import {FakeScheduler} from './fake-scheduler';

suite('fake scheduler', () => {
  let scheduler: FakeScheduler<number>;
  setup(() => {
    scheduler = new FakeScheduler<number>();
  });
  test('schedules tasks', () => {
    scheduler.schedule(async () => 1);
    assert.equal(scheduler.scheduled.length, 1);
  });

  test('resolves tasks', async () => {
    const promise = scheduler.schedule(async () => 1);
    await scheduler.resolve();
    const val = await promise;
    assert.equal(val, 1);
  });

  test('rejects tasks', async () => {
    const promise = scheduler.schedule(async () => 1);
    assertFails(promise);
    await scheduler.reject(new Error('Fake Error'));
  });

  test('propagates errors', async () => {
    const error = new Error('This is an error');
    const promise = scheduler.schedule(async () => {
      throw error;
    });
    assertFails(promise, error);
    await scheduler.resolve();
    await promise.catch((reason: Error) => {
      assert.equal(reason, error);
    });
  });
});
