/**
 * @license
 * Copyright 2018 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import '../../../test/common-test-setup-karma';
import './gr-key-binding-display';
import {GrKeyBindingDisplay} from './gr-key-binding-display';

const basicFixture = fixtureFromElement('gr-key-binding-display');

suite('gr-key-binding-display tests', () => {
  let element: GrKeyBindingDisplay;

  setup(() => {
    element = basicFixture.instantiate();
  });

  suite('_computeKey', () => {
    test('unmodified key', () => {
      assert.strictEqual(element._computeKey(['x']), 'x');
    });

    test('key with modifiers', () => {
      assert.strictEqual(element._computeKey(['Ctrl', 'x']), 'x');
      assert.strictEqual(element._computeKey(['Shift', 'Meta', 'x']), 'x');
    });
  });

  suite('_computeModifiers', () => {
    test('single unmodified key', () => {
      assert.deepEqual(element._computeModifiers(['x']), []);
    });

    test('key with modifiers', () => {
      assert.deepEqual(element._computeModifiers(['Ctrl', 'x']), ['Ctrl']);
      assert.deepEqual(element._computeModifiers(['Shift', 'Meta', 'x']), [
        'Shift',
        'Meta',
      ]);
    });
  });
});
