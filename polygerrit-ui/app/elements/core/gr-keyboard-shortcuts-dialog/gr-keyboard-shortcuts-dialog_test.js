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

import '../../../test/common-test-setup-karma.js';
import './gr-keyboard-shortcuts-dialog.js';
import {ShortcutSection} from '../../../mixins/keyboard-shortcut-mixin/keyboard-shortcut-mixin.js';

const basicFixture = fixtureFromElement('gr-keyboard-shortcuts-dialog');

suite('gr-keyboard-shortcuts-dialog tests', () => {
  let element;

  setup(() => {
    element = basicFixture.instantiate();
  });

  function update(directory) {
    element._onDirectoryUpdated(directory);
    flush();
  }

  suite('_left and _right contents', () => {
    test('empty dialog', () => {
      assert.strictEqual(element._left.length, 0);
      assert.strictEqual(element._right.length, 0);
    });

    test('everywhere goes on left', () => {
      update(new Map([
        [ShortcutSection.EVERYWHERE, ['everywhere shortcuts']],
      ]));
      assert.deepEqual(
          element._left,
          [
            {
              section: ShortcutSection.EVERYWHERE,
              shortcuts: ['everywhere shortcuts'],
            },
          ]);
      assert.strictEqual(element._right.length, 0);
    });

    test('navigation goes on left', () => {
      update(new Map([
        [ShortcutSection.NAVIGATION, ['navigation shortcuts']],
      ]));
      assert.deepEqual(
          element._left,
          [
            {
              section: ShortcutSection.NAVIGATION,
              shortcuts: ['navigation shortcuts'],
            },
          ]);
      assert.strictEqual(element._right.length, 0);
    });

    test('actions go on right', () => {
      update(new Map([
        [ShortcutSection.ACTIONS, ['actions shortcuts']],
      ]));
      assert.deepEqual(
          element._right,
          [
            {
              section: ShortcutSection.ACTIONS,
              shortcuts: ['actions shortcuts'],
            },
          ]);
      assert.strictEqual(element._left.length, 0);
    });

    test('reply dialog goes on right', () => {
      update(new Map([
        [ShortcutSection.REPLY_DIALOG, ['reply dialog shortcuts']],
      ]));
      assert.deepEqual(
          element._right,
          [
            {
              section: ShortcutSection.REPLY_DIALOG,
              shortcuts: ['reply dialog shortcuts'],
            },
          ]);
      assert.strictEqual(element._left.length, 0);
    });

    test('file list goes on right', () => {
      update(new Map([
        [ShortcutSection.FILE_LIST, ['file list shortcuts']],
      ]));
      assert.deepEqual(
          element._right,
          [
            {
              section: ShortcutSection.FILE_LIST,
              shortcuts: ['file list shortcuts'],
            },
          ]);
      assert.strictEqual(element._left.length, 0);
    });

    test('diffs go on right', () => {
      update(new Map([
        [ShortcutSection.DIFFS, ['diffs shortcuts']],
      ]));
      assert.deepEqual(
          element._right,
          [
            {
              section: ShortcutSection.DIFFS,
              shortcuts: ['diffs shortcuts'],
            },
          ]);
      assert.strictEqual(element._left.length, 0);
    });

    test('multiple sections on each side', () => {
      update(new Map([
        [ShortcutSection.ACTIONS, ['actions shortcuts']],
        [ShortcutSection.DIFFS, ['diffs shortcuts']],
        [ShortcutSection.EVERYWHERE, ['everywhere shortcuts']],
        [ShortcutSection.NAVIGATION, ['navigation shortcuts']],
      ]));
      assert.deepEqual(
          element._left,
          [
            {
              section: ShortcutSection.EVERYWHERE,
              shortcuts: ['everywhere shortcuts'],
            },
            {
              section: ShortcutSection.NAVIGATION,
              shortcuts: ['navigation shortcuts'],
            },
          ]);
      assert.deepEqual(
          element._right,
          [
            {
              section: ShortcutSection.ACTIONS,
              shortcuts: ['actions shortcuts'],
            },
            {
              section: ShortcutSection.DIFFS,
              shortcuts: ['diffs shortcuts'],
            },
          ]);
    });
  });
});

