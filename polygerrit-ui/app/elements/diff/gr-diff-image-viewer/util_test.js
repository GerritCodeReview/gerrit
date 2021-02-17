/**
 * @license
 * Copyright (C) 2021 The Android Open Source Project
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
import '../../../test/common-test-setup-karma.js';
import {FrameConstrainer} from './util.js';

suite('FrameConstrainer tests', () => {
  let constrainer;

  setup(() => {
    constrainer = new FrameConstrainer();
    constrainer.setBounds({width: 100, height: 100});
    constrainer.setFrameSize({width: 50, height: 50});
    constrainer.requestCenter({x: 50, y: 50});
  });

  suite('changing center', () => {
    test('moves frame to requested position', () => {
      constrainer.requestCenter({x: 30, y: 30});
      assert.deepEqual(
        constrainer.getUnscaledFrame(),
        {origin: {x: 5, y: 5}, dimensions: {width: 50, height: 50}});
    });

    test('keeps frame in bounds for top left corner', () => {
      constrainer.requestCenter({x: 5, y: 5});
      assert.deepEqual(
        constrainer.getUnscaledFrame(),
        {origin: {x: 0, y: 0}, dimensions: {width: 50, height: 50}});
    });

    test('keeps frame in bounds for bottom right corner', () => {
      constrainer.requestCenter({x: 95, y: 95});
      assert.deepEqual(
        constrainer.getUnscaledFrame(),
        {origin: {x: 50, y: 50}, dimensions: {width: 50, height: 50}});
    });

    test('handles out-of-bounds center left', () => {
      constrainer.requestCenter({x: -5, y: 50});
      assert.deepEqual(
        constrainer.getUnscaledFrame(),
        {origin: {x: 0, y: 25}, dimensions: {width: 50, height: 50}});
    });

    test('handles out-of-bounds center right', () => {
      constrainer.requestCenter({x: 105, y: 50});
      assert.deepEqual(
        constrainer.getUnscaledFrame(),
        {origin: {x: 50, y: 25}, dimensions: {width: 50, height: 50}});
    });

    test('handles out-of-bounds center top', () => {
      constrainer.requestCenter({x: 50, y: -5});
      assert.deepEqual(
        constrainer.getUnscaledFrame(),
        {origin: {x: 25, y: 0}, dimensions: {width: 50, height: 50}});
    });

    test('handles out-of-bounds center bottom', () => {
      constrainer.requestCenter({x: 50, y: 105});
      assert.deepEqual(
        constrainer.getUnscaledFrame(),
        {origin: {x: 25, y: 50}, dimensions: {width: 50, height: 50}});
    });
  });

  suite('changing frame size', () => {
    test('maintains center when decreased', () => {
      constrainer.setFrameSize({width: 10, height: 10});
      assert.deepEqual(
          constrainer.getUnscaledFrame(),
          {origin: {x: 45, y: 45}, dimensions: {width: 10, height: 10}});
    });

    test('maintains center when increased', () => {
      constrainer.setFrameSize({width: 80, height: 80});
      assert.deepEqual(
          constrainer.getUnscaledFrame(),
          {origin: {x: 10, y: 10}, dimensions: {width: 80, height: 80}});
    });

    test('updates center to remain in bounds when increased', () => {
      constrainer.setFrameSize({width: 10, height: 10});
      constrainer.requestCenter({x: 95, y: 95});
      assert.deepEqual(
          constrainer.getUnscaledFrame(),
          {origin: {x: 90, y: 90}, dimensions: {width: 10, height: 10}});

      constrainer.setFrameSize({width: 20, height: 20});
      assert.deepEqual(
          constrainer.getUnscaledFrame(),
          {origin: {x: 80, y: 80}, dimensions: {width: 20, height: 20}});
    });
  });

  suite('changing scale', () => {
    suite('for unscaled frame', () => {

    });

    suite('for scaled frame', () => {

    });
  });

  suite('unscaled frame', () => {
    const FRAME_SIZE = {width: 50, height: 50};
    setup(() => {
      constrainer.requestCenter({x: 50, y: 50});
    });

    test('updates origin when zooming in', () => {
      constrainer.setScale(2);
      assert.deepEqual(
          constrainer.getUnscaledFrame(),
          {origin: {x: 75, y: 75}, dimensions: FRAME_SIZE});
    });
  });

  suite('scaled frame', () => {
    setup(() => {
      constrainer.requestCenter({x: 50, y: 50});
    });

    test('updates frame size when zooming in', () => {
      constrainer.setScale(2);
      assert.deepEqual(
          constrainer.getScaledFrame(),
          {origin: {x: 37.5, y: 37.5}, dimensions: {width: 25, height: 25}});
    });
  });
});