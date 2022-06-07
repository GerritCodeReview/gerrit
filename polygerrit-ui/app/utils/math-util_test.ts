/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
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
