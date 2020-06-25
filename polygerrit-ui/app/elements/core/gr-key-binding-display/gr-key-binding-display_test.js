
/**
 * @license
 * Copyright (C) 2018 The Android Open Source Project
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

<meta charset="utf-8">







<test-fixture id="basic">
  <template>
    <gr-key-binding-display></gr-key-binding-display>
  </template>
</test-fixture>


import '../../../test/common-test-setup.js';
import './gr-key-binding-display.js';
suite('gr-key-binding-display tests', () => {
  let element;

  setup(() => {
    element = fixture('basic');
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
      assert.deepEqual(
          element._computeModifiers(['Shift', 'Meta', 'x']),
          ['Shift', 'Meta']);
    });
  });
});

