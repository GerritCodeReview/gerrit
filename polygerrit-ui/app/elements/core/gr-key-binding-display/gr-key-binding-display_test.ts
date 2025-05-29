/**
 * @license
 * Copyright 2018 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {assert, fixture, html} from '@open-wc/testing';
import '../../../test/common-test-setup';
import './gr-key-binding-display';
import {GrKeyBindingDisplay} from './gr-key-binding-display';

const x = ['x'];
const ctrlX = ['Ctrl', 'x'];
const shiftMetaX = ['Shift', 'Meta', 'x'];

suite('gr-key-binding-display tests', () => {
  let element: GrKeyBindingDisplay;

  setup(async () => {
    element = await fixture(
      html`<gr-key-binding-display
        .binding=${[x, ctrlX, shiftMetaX]}
      ></gr-key-binding-display>`
    );
  });

  test('renders', () => {
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <span class="key"> x </span>
        or
        <span class="key modifier"> Ctrl </span>
        <span class="key"> x </span>
        or
        <span class="key modifier"> Shift </span>
        <span class="key modifier"> Meta </span>
        <span class="key"> x </span>
      `
    );
  });

  suite('_computeKey', () => {
    test('unmodified key', () => {
      assert.strictEqual(element._computeKey(x), 'x');
    });

    test('key with modifiers', () => {
      assert.strictEqual(element._computeKey(ctrlX), 'x');
      assert.strictEqual(element._computeKey(shiftMetaX), 'x');
    });
  });

  suite('_computeModifiers', () => {
    test('single unmodified key', () => {
      assert.deepEqual(element._computeModifiers(x), []);
    });

    test('key with modifiers', () => {
      assert.deepEqual(element._computeModifiers(ctrlX), ['Ctrl']);
      assert.deepEqual(element._computeModifiers(shiftMetaX), [
        'Shift',
        'Meta',
      ]);
    });
  });
});
