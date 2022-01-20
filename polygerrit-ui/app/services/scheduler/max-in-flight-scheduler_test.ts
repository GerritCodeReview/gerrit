/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../test/common-test-setup-karma.js';
import {assertFails} from '../../test/test-utils.js';
import {Scheduler} from './scheduler';
import {MaxInFlightScheduler} from './max-in-flight-scheduler';
import {FakeScheduler} from './fake-scheduler';

suite('retry scheduler', () => {
  let fake: FakeScheduler<number>;
  let scheduler: Scheduler<number>;
  setup(() => {
    fake = new FakeScheduler<number>();
    scheduler = new MaxInFlightScheduler<number>(fake, 2);
  });

  test('executes tasks', async () => {
    const promise = scheduler.schedule(async () => {
      return 1;
    })
    assert.equal(fake.scheduled.length, 1);
    fake.resolve();
    assert.equal(fake.scheduled.length, 0);
    const val = await promise;
    assert.equal(val, 1);
  });

  test('propagates errors', async () => {
    const error = new Error('This is an error');
    const promise = scheduler.schedule(async () => {
      throw error;
    });
    assert.equal(fake.scheduled.length, 1);
    assertFails(promise, error);
    fake.resolve();
    assert.equal(fake.scheduled.length, 0);
    await promise.catch((reason: Error) => {
      assert.equal(reason, error);
    });
  });

  test('propagates subscheduler errors', async () => {
    const error = new Error('This is an error');
    const promise = scheduler.schedule(async () => {
      return 1;
    });
    assert.equal(fake.scheduled.length, 1);
    assertFails(promise, error);
    fake.reject(error);
    assert.equal(fake.scheduled.length, 0);
    await promise.catch((reason: Error) => {
      assert.equal(reason, error);
    });
  });

  test('allows up to 2 in flight', async () => {
    for (let i = 0; i < 3; ++i) {
      scheduler.schedule(async () => {
        return i;
      });
    }
    assert.equal(fake.scheduled.length, 2);
  });

  test('resumes when promise resolves', async () => {
    for (let i = 0; i < 3; ++i) {
      scheduler.schedule(async () => {
        return i;
      });
    }
    assert.equal(fake.scheduled.length, 2);
    fake.resolve();
    assert.equal(fake.scheduled.length, 1);
    await flush();
    assert.equal(fake.scheduled.length, 2);
  });

  test('resumes when promise fails', async () => {
    for (let i = 0; i < 3; ++i) {
      scheduler.schedule(async () => {
        return i;
      });
    }
    assert.equal(fake.scheduled.length, 2);
    fake.reject(new Error('Fake Error'));
    assert.equal(fake.scheduled.length, 1);
    await flush();
    assert.equal(fake.scheduled.length, 2);
  });

  test('eventually resumes all', async () => {
    const promises = [];
    for (let i = 0; i < 3; ++i) {
      promises.push(scheduler.schedule(async () => {
        return i;
      }));
    }
    for (let i = 0; i < 3; ++i) {
      fake.resolve();
      await flush();
    }
    let res = await Promise.all(promises);
    assert.deepEqual(res, [0, 1, 2]);
  });
});
