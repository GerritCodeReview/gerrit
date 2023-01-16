/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../test/common-test-setup';
import {assertFails, waitEventLoop} from '../../test/test-utils';
import {Scheduler} from './scheduler';
import {FakeScheduler} from './fake-scheduler';
import {assert} from '@open-wc/testing';
import {QueueingScheduler} from './queueing-scheduler';
import {SinonFakeTimers} from 'sinon';

suite('queueing scheduler', () => {
  let fakeScheduler: FakeScheduler<number>;
  let scheduler: Scheduler<number>;
  let clock: SinonFakeTimers;
  setup(() => {
    fakeScheduler = new FakeScheduler<number>();
    scheduler = new QueueingScheduler<number>(fakeScheduler, 100);
    clock = sinon.useFakeTimers();
    clock.tick(1000);
  });

  teardown(() => {
    clock.restore();
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

  test('throttles RPCs', async () => {
    const results = [];
    for (let i = 0; i < 3; ++i) {
      scheduler.schedule(async () => i).then(() => results.push(i));
    }
    assert.equal(fakeScheduler.scheduled.length, 1);
    fakeScheduler.resolve();
    assert.equal(fakeScheduler.scheduled.length, 0);
    await waitEventLoop();
    assert.equal(fakeScheduler.scheduled.length, 0);
    clock.tick(99);
    assert.equal(fakeScheduler.scheduled.length, 0);
    clock.tick(1);
    assert.equal(fakeScheduler.scheduled.length, 1);
    fakeScheduler.resolve();
    assert.equal(fakeScheduler.scheduled.length, 0);
    await waitEventLoop();
    clock.tick(50);
    assert.equal(fakeScheduler.scheduled.length, 0);
    clock.tick(50);
    assert.equal(fakeScheduler.scheduled.length, 1);
    fakeScheduler.resolve();
    assert.equal(fakeScheduler.scheduled.length, 0);
  });
});
