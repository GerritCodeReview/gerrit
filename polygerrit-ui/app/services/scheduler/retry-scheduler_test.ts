/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import * as sinon from 'sinon';
import '../../test/common-test-setup';
import {assertFails, waitEventLoop} from '../../test/test-utils';
import {Scheduler} from './scheduler';
import {RetryScheduler, RetryError} from './retry-scheduler';
import {FakeScheduler} from './fake-scheduler';
import {SinonFakeTimers} from 'sinon';
import {assert} from '@open-wc/testing';

suite('retry scheduler', () => {
  let clock: SinonFakeTimers;
  let fakeScheduler: FakeScheduler<number>;
  let scheduler: Scheduler<number>;
  setup(() => {
    clock = sinon.useFakeTimers();
    fakeScheduler = new FakeScheduler<number>();
    scheduler = new RetryScheduler<number>(fakeScheduler, 3, 50, 1);
  });

  async function waitForRetry(ms: number) {
    // Flush the promise so that we can reach untilTimeout
    await waitEventLoop();
    // Advance the clock.
    clock.tick(ms);
    // Flush the promise that waits for the clock.
    await waitEventLoop();
  }

  test('executes tasks', async () => {
    const promise = scheduler.schedule(async () => 1);
    assert.equal(fakeScheduler.scheduled.length, 1);
    fakeScheduler.resolve();
    assert.equal(fakeScheduler.scheduled.length, 0);
    const val = await promise;
    assert.equal(val, 1);
  });

  test('propagates task errors', async () => {
    const error = new Error('This is an error');
    const promise = scheduler.schedule(async () => {
      throw error;
    });
    assert.equal(fakeScheduler.scheduled.length, 1);
    assertFails(promise, error);
    fakeScheduler.resolve();
    assert.equal(fakeScheduler.scheduled.length, 0);
    await promise.catch((reason: Error) => {
      assert.equal(reason, error);
    });
  });

  test('propagates subscheduler errors', async () => {
    const error = new Error('This is an error');
    const promise = scheduler.schedule(async () => 1);
    assert.equal(fakeScheduler.scheduled.length, 1);
    assertFails(promise, error);
    fakeScheduler.reject(error);
    assert.equal(fakeScheduler.scheduled.length, 0);
    await promise.catch((reason: Error) => {
      assert.equal(reason, error);
    });
  });

  test('retries on retryable error', async () => {
    let retries = 1;
    const promise = scheduler.schedule(async () => {
      if (retries-- > 0) throw new RetryError('Retrying');
      return 1;
    });
    assert.equal(fakeScheduler.scheduled.length, 1);
    fakeScheduler.resolve();
    assert.equal(fakeScheduler.scheduled.length, 0);
    await waitForRetry(50);
    assert.equal(fakeScheduler.scheduled.length, 1);
    fakeScheduler.resolve();
    const val = await promise;
    assert.equal(val, 1);
  });

  test('retries up to 3 times', async () => {
    let retries = 3;
    const promise = scheduler.schedule(async () => {
      if (retries-- > 0) throw new RetryError('Retrying');
      return 1;
    });
    assert.equal(fakeScheduler.scheduled.length, 1);
    for (let i = 0; i < 3; i++) {
      fakeScheduler.resolve();
      assert.equal(fakeScheduler.scheduled.length, 0);
      await waitForRetry(50);
      assert.equal(fakeScheduler.scheduled.length, 1);
    }
    fakeScheduler.resolve();
    const val = await promise;
    assert.equal(val, 1);
  });

  test('fails after more than 3 times', async () => {
    let retries = 4;
    const promise = scheduler.schedule(async () => {
      if (retries-- > 0) throw new RetryError(retries, `Retrying ${retries}`);
      return 1;
    });
    assert.equal(fakeScheduler.scheduled.length, 1);
    for (let i = 0; i < 3; i++) {
      fakeScheduler.resolve();
      assert.equal(fakeScheduler.scheduled.length, 0);
      await waitForRetry(50);
      assert.equal(fakeScheduler.scheduled.length, 1);
    }
    fakeScheduler.resolve();
    assertFails(promise);
    // The error we get back should be the last error.
    await promise.catch((reason: RetryError<number>) => {
      assert.equal(reason.payload, 0);
      assert.equal(reason.message, 'Retrying 0');
    });
  });
});
