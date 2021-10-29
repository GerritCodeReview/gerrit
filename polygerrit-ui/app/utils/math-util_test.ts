/**
 * @license
 * Copyright (C) 2020 The Android Open Source Project
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
import {getRandomInt} from './math-util';

suite('math-util tests', () => {
  test('getRandomInt', () => {
    let r = 0;
    const randomStub = sinon.stub(Math, 'random').callsFake(() => r);

    assert.equal(getRandomInt(0, 0), 0);
    assert.equal(getRandomInt(0, 2), 0);
    assert.equal(getRandomInt(0, 100), 0);
    assert.equal(getRandomInt(10, 10), 10);
    assert.equal(getRandomInt(10, 12), 10);
    assert.equal(getRandomInt(10, 100), 10);

    r = 0.999;
    assert.equal(getRandomInt(0, 0), 0);
    assert.equal(getRandomInt(0, 2), 2);
    assert.equal(getRandomInt(0, 100), 100);
    assert.equal(getRandomInt(10, 10), 10);
    assert.equal(getRandomInt(10, 12), 12);
    assert.equal(getRandomInt(10, 100), 100);

    r = 0.5;
    assert.equal(getRandomInt(0, 0), 0);
    assert.equal(getRandomInt(0, 2), 1);
    assert.equal(getRandomInt(0, 100), 50);
    assert.equal(getRandomInt(10, 10), 10);
    assert.equal(getRandomInt(10, 12), 11);
    assert.equal(getRandomInt(10, 100), 55);

    r = 0.0;
    assert.equal(getRandomInt(0, 2), 0);
    r = 0.33;
    assert.equal(getRandomInt(0, 2), 0);
    r = 0.34;
    assert.equal(getRandomInt(0, 2), 1);
    r = 0.66;
    assert.equal(getRandomInt(0, 2), 1);
    r = 0.67;
    assert.equal(getRandomInt(0, 2), 2);
    r = 0.99;
    assert.equal(getRandomInt(0, 2), 2);

    randomStub.restore();
  });
});
