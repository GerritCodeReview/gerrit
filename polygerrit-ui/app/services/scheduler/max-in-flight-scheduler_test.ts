/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../test/common-test-setup';
import {assertFails, waitEventLoop} from '../../test/test-utils';
import {Scheduler} from './scheduler';
import {MaxInFlightScheduler} from './max-in-flight-scheduler';
import {FakeScheduler} from './fake-scheduler';
import {assert} from '@open-wc/testing';

suite('max-in-flight scheduler', () => {
  let fakeScheduler: FakeScheduler<number>;
  let scheduler: Scheduler<number>;
  setup(() => {
    fakeScheduler = new FakeScheduler<number>();
    scheduler = new MaxInFlightScheduler<number>(fakeScheduler, 2);
  });

  test('executes tasks', async () => {
    const promise = scheduler.schedule(async () => 1);
    assert.equal(fakeScheduler.scheduled.length, 1);
    fakeScheduler.resolve();
    assert.equal(fakeScheduler.scheduled.length, 0);
    const val = await promise;
    assert.equal(val, 1);
  });

  test('propagates errors', async () => {
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

  test('allows up to 2 in flight', async () => {
    for (let i = 0; i < 3; ++i) {
      scheduler.schedule(async () => i);
    }
    assert.equal(fakeScheduler.scheduled.length, 2);
  });

  test('resumes when promise resolves', async () => {
    for (let i = 0; i < 3; ++i) {
      scheduler.schedule(async () => i);
    }
    assert.equal(fakeScheduler.scheduled.length, 2);
    fakeScheduler.resolve();
    assert.equal(fakeScheduler.scheduled.length, 1);
    await waitEventLoop();
    assert.equal(fakeScheduler.scheduled.length, 2);
  });

  test('resumes when promise fails', async () => {
    for (let i = 0; i < 3; ++i) {
      scheduler.schedule(async () => i).catch(() => {});
    }
    assert.equal(fakeScheduler.scheduled.length, 2);
    fakeScheduler.reject(new Error('Fake Error'));
    assert.equal(fakeScheduler.scheduled.length, 1);
    await waitEventLoop();
    assert.equal(fakeScheduler.scheduled.length, 2);
  });

  test('eventually resumes all', async () => {
    const promises = [];
    for (let i = 0; i < 3; ++i) {
      promises.push(scheduler.schedule(async () => i));
    }
    for (let i = 0; i < 3; ++i) {
      fakeScheduler.resolve();
      await waitEventLoop();
    }
    const res = await Promise.all(promises);
    assert.deepEqual(res, [0, 1, 2]);
  });
});
