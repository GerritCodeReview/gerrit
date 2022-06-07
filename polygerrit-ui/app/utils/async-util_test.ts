/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../test/common-test-setup-karma';
import {asyncForeach} from './async-util';

suite('async-util tests', () => {
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
