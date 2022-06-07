/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
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
      test('adjusts origin to maintain center when zooming in', () => {
        constrainer.setScale(2);
        assert.deepEqual(
            constrainer.getUnscaledFrame(),
            {origin: {x: 75, y: 75}, dimensions: {width: 50, height: 50}});
      });

      test('adjusts origin to maintain center when zooming out', () => {
        constrainer.setFrameSize({width: 20, height: 20});
        constrainer.setScale(0.5);
        assert.deepEqual(
            constrainer.getUnscaledFrame(),
            {origin: {x: 15, y: 15}, dimensions: {width: 20, height: 20}});
      });

      test('keeps frame in bounds when zooming out', () => {
        constrainer.setScale(5);
        constrainer.requestCenter({x: 100, y: 100});
        assert.deepEqual(
            constrainer.getUnscaledFrame(),
            {origin: {x: 450, y: 450}, dimensions: {width: 50, height: 50}});

        constrainer.setScale(1);
        assert.deepEqual(
            constrainer.getUnscaledFrame(),
            {origin: {x: 50, y: 50}, dimensions: {width: 50, height: 50}});
      });
    });

    suite('for scaled frame', () => {
      test('decreases frame size and maintains center when zooming in', () => {
        constrainer.setScale(2);
        assert.deepEqual(
            constrainer.getScaledFrame(),
            {origin: {x: 37.5, y: 37.5}, dimensions: {width: 25, height: 25}});
      });

      test('increases frame size and maintains center when zooming out', () => {
        constrainer.setFrameSize({width: 20, height: 20});
        constrainer.setScale(0.5);
        assert.deepEqual(
            constrainer.getScaledFrame(),
            {origin: {x: 30, y: 30}, dimensions: {width: 40, height: 40}});
      });

      test('keeps frame in bounds when zooming out', () => {
        constrainer.setScale(5);
        constrainer.requestCenter({x: 100, y: 100});
        assert.deepEqual(
            constrainer.getScaledFrame(),
            {origin: {x: 90, y: 90}, dimensions: {width: 10, height: 10}});

        constrainer.setScale(1);
        assert.deepEqual(
            constrainer.getScaledFrame(),
            {origin: {x: 50, y: 50}, dimensions: {width: 50, height: 50}});
      });
    });
  });
});