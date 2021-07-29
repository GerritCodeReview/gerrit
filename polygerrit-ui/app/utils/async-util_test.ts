/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
