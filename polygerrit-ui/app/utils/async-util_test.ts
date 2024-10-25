/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import * as sinon from 'sinon';
import {assert} from '@open-wc/testing';
import {SinonFakeTimers} from 'sinon';
import '../test/common-test-setup';
import {mockPromise, waitEventLoop, waitUntil} from '../test/test-utils';
import {
  asyncForeach,
  debounceP,
  DelayedTask,
  interactivePromise,
  timeoutPromise,
} from './async-util';

suite('async-util tests', () => {
  suite('interactivePromise', () => {
    test('simple test', async () => {
      let resolved = false;
      const promise = interactivePromise();
      promise.then(() => (resolved = true));
      assert.isFalse(resolved);
      promise.resolve();
      await promise;
      assert.isTrue(resolved);
    });
  });

  suite('timeoutPromise', () => {
    let clock: SinonFakeTimers;
    setup(() => {
      clock = sinon.useFakeTimers();
    });
    test('simple test', async () => {
      let resolved = false;
      const promise = timeoutPromise(1000);
      promise.then(() => (resolved = true));
      assert.isFalse(resolved);
      clock.tick(999);
      assert.isFalse(resolved);
      clock.tick(1);
      await promise;
      assert.isTrue(resolved);
    });
  });

  suite('asyncForeach', () => {
    test('loops over each item', async () => {
      const fn = sinon.stub().resolves();

      await asyncForeach([1, 2, 3], fn);

      assert.isTrue(fn.calledThrice);
      assert.equal(fn.firstCall.firstArg, 1);
      assert.equal(fn.secondCall.firstArg, 2);
      assert.equal(fn.thirdCall.firstArg, 3);
    });

    test('halts on stop condition', async () => {
      const stub = sinon.stub();
      const fn = (item: number, stopCallback: () => void) => {
        stub(item);
        stopCallback();
        return Promise.resolve();
      };

      await asyncForeach([1, 2, 3], fn);

      assert.isTrue(stub.calledOnce);
      assert.equal(stub.lastCall.firstArg, 1);
    });
  });

  suite('DelayedPromise', () => {
    let clock: SinonFakeTimers;
    setup(() => {
      clock = sinon.useFakeTimers();
    });

    test('It resolves after timeout', async () => {
      const promise = debounceP<number>(
        undefined,
        () => Promise.resolve(5),
        100
      );
      let hasResolved = false;
      promise.then((value: number) => {
        hasResolved = true;
        assert.equal(value, 5);
      });
      promise.catch((_reason?: any) => {
        assert.fail();
      });
      await waitEventLoop();
      assert.isFalse(hasResolved);
      clock.tick(99);
      await waitEventLoop();
      assert.isFalse(hasResolved);
      clock.tick(1);
      await waitEventLoop();
      assert.isTrue(hasResolved);
      await promise;
      // Shouldn't do anything.
      promise.cancel();
      await waitEventLoop();
    });

    test('It resolves immediately on flush and finalizes', async () => {
      const promise = debounceP<number>(
        undefined,
        () => Promise.resolve(5),
        100
      );
      let hasResolved = false;
      promise.then((value: number) => {
        hasResolved = true;
        assert.equal(value, 5);
      });
      promise.catch((_reason?: any) => {
        assert.fail();
      });
      promise.flush();
      await waitEventLoop();
      assert.isTrue(hasResolved);
      // Shouldn't do anything.
      promise.cancel();
      await waitEventLoop();
    });

    test('It rejects on cancel', async () => {
      const promise = debounceP<number>(
        undefined,
        () => Promise.resolve(5),
        100
      );
      let hasCanceled = false;
      promise.then((_value: number) => {
        assert.fail();
      });
      promise.catch((reason?: any) => {
        hasCanceled = true;
        assert.strictEqual(reason, 'because');
      });
      await waitEventLoop();
      assert.isFalse(hasCanceled);
      promise.cancel('because');
      await waitEventLoop();
      assert.isTrue(hasCanceled);
      // Shouldn't do anything.
      promise.flush();
      await waitEventLoop();
    });

    test('It delegates correctly', async () => {
      const promise1 = debounceP<number>(
        undefined,
        () => Promise.resolve(5),
        100
      );
      let hasResolved1 = false;
      promise1.then((value: number) => {
        hasResolved1 = true;
        assert.equal(value, 6);
      });
      promise1.catch((_reason?: any) => {
        assert.fail();
      });
      await waitEventLoop();
      assert.isFalse(hasResolved1);
      clock.tick(99);
      await waitEventLoop();
      const promise2 = debounceP<number>(
        promise1,
        () => Promise.resolve(6),
        100
      );
      let hasResolved2 = false;
      promise2.then((value: number) => {
        hasResolved2 = true;
        assert.equal(value, 6);
      });
      promise2.catch((_reason?: any) => {
        assert.fail();
      });
      clock.tick(99);
      await waitEventLoop();
      assert.isFalse(hasResolved1);
      assert.isFalse(hasResolved2);
      clock.tick(2);
      await waitEventLoop();
      assert.isTrue(hasResolved1);
      assert.isTrue(hasResolved2);
      // Shouldn't do anything.
      promise1.cancel();
      await waitEventLoop();
    });

    test('It does not delegate after timeout', async () => {
      const promise1 = debounceP<number>(
        undefined,
        () => Promise.resolve(5),
        100
      );
      let hasResolved1 = false;
      promise1.then((value: number) => {
        hasResolved1 = true;
        assert.equal(value, 5);
      });
      promise1.catch((_reason?: any) => {
        assert.fail();
      });
      await waitEventLoop();
      assert.isFalse(hasResolved1);
      clock.tick(100);
      await waitEventLoop();
      assert.isTrue(hasResolved1);

      const promise2 = debounceP<number>(
        promise1,
        () => Promise.resolve(6),
        100
      );
      let hasResolved2 = false;
      promise2.then((value: number) => {
        hasResolved2 = true;
        assert.equal(value, 6);
      });
      promise2.catch((_reason?: any) => {
        assert.fail();
      });
      clock.tick(99);
      await waitEventLoop();
      assert.isFalse(hasResolved2);
      clock.tick(1);
      await waitEventLoop();
      assert.isTrue(hasResolved2);
      // Shouldn't do anything.
      promise1.cancel();
      await waitEventLoop();
    });
  });

  test('DelayedTask promise resolved when callback is done', async () => {
    const callbackPromise = mockPromise<void>();
    const task = new DelayedTask(() => callbackPromise);
    let completed = false;
    task.promise.then(() => (completed = true));
    await waitUntil(() => !task.isActive());

    assert.isFalse(completed);
    callbackPromise.resolve();
    await waitUntil(() => completed);
  });
});
