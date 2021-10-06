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
import '../../test/common-test-setup-karma';
import {ShortcutsService} from '../../services/shortcuts/shortcuts-service';
import {Shortcut, ShortcutSection} from './shortcuts-config';

suite('shortcuts-service tests', () => {
  test('getShortcut', () => {
    const mgr = new ShortcutsService();
    const NEXT_FILE = Shortcut.NEXT_FILE;
    assert.equal(mgr.getShortcut(NEXT_FILE), ']');
  });

  test('getShortcut with modifiers', () => {
    const mgr = new ShortcutsService();
    const NEXT_FILE = Shortcut.NEXT_FILE;
    assert.equal(mgr.getShortcut(NEXT_FILE), 'Shift+a');
  });

  suite('binding descriptions', () => {
    function mapToObject<K, V>(m: Map<K, V>) {
      const o: any = {};
      m.forEach((v: V, k: K) => (o[k] = v));
      return o;
    }

    test('single combo description', () => {
      const mgr = new ShortcutsService();
      assert.deepEqual(mgr.describeBinding('a'), ['a']);
      assert.deepEqual(mgr.describeBinding('a:keyup'), ['a']);
      assert.deepEqual(mgr.describeBinding('ctrl+a'), ['Ctrl', 'a']);
      assert.deepEqual(mgr.describeBinding('ctrl+shift+up:keyup'), [
        'Ctrl',
        'Shift',
        '↑',
      ]);
    });

    test('combo set description', () => {
      const mgr = new ShortcutsService();
      assert.isNull(mgr.describeBindings(Shortcut.NEXT_FILE));
      assert.deepEqual(mgr.describeBindings(Shortcut.GO_TO_OPENED_CHANGES), [
        ['g', 'o'],
      ]);
      assert.deepEqual(mgr.describeBindings(Shortcut.NEXT_FILE), [
        [']'],
        ['Ctrl', 'Shift', '→'],
      ]);
      assert.deepEqual(mgr.describeBindings(Shortcut.PREV_FILE), [['[']]);
    });

    test('combo set description width', () => {
      const mgr = new ShortcutsService();
      assert.strictEqual(mgr.comboSetDisplayWidth([['u']]), 1);
      assert.strictEqual(mgr.comboSetDisplayWidth([['g', 'o']]), 2);
      assert.strictEqual(mgr.comboSetDisplayWidth([['Shift', 'r']]), 6);
      assert.strictEqual(mgr.comboSetDisplayWidth([['x'], ['y']]), 4);
      assert.strictEqual(
        mgr.comboSetDisplayWidth([['x'], ['y'], ['Shift', 'z']]),
        12
      );
    });

    test('distribute shortcut help', () => {
      const mgr = new ShortcutsService();
      assert.deepEqual(mgr.distributeBindingDesc([['o']]), [[['o']]]);
      assert.deepEqual(mgr.distributeBindingDesc([['g', 'o']]), [[['g', 'o']]]);
      assert.deepEqual(
        mgr.distributeBindingDesc([['ctrl', 'shift', 'meta', 'enter']]),
        [[['ctrl', 'shift', 'meta', 'enter']]]
      );
      assert.deepEqual(
        mgr.distributeBindingDesc([['ctrl', 'shift', 'meta', 'enter'], ['o']]),
        [[['ctrl', 'shift', 'meta', 'enter']], [['o']]]
      );
      assert.deepEqual(
        mgr.distributeBindingDesc([
          ['ctrl', 'enter'],
          ['meta', 'enter'],
          ['ctrl', 's'],
          ['meta', 's'],
        ]),
        [
          [
            ['ctrl', 'enter'],
            ['meta', 'enter'],
          ],
          [
            ['ctrl', 's'],
            ['meta', 's'],
          ],
        ]
      );
    });

    test('active shortcuts by section', () => {
      const mgr = new ShortcutsService();
      assert.deepEqual(mapToObject(mgr.activeShortcutsBySection()), {});

      mgr.attachHost({}, new Map([[Shortcut.NEXT_FILE, 'null']]));
      assert.deepEqual(mapToObject(mgr.activeShortcutsBySection()), {
        [ShortcutSection.NAVIGATION]: [
          {shortcut: Shortcut.NEXT_FILE, text: 'Go to next file'},
        ],
      });

      mgr.attachHost({}, new Map([[Shortcut.NEXT_LINE, 'null']]));
      assert.deepEqual(mapToObject(mgr.activeShortcutsBySection()), {
        [ShortcutSection.DIFFS]: [
          {shortcut: Shortcut.NEXT_LINE, text: 'Go to next line'},
        ],
        [ShortcutSection.NAVIGATION]: [
          {shortcut: Shortcut.NEXT_FILE, text: 'Go to next file'},
        ],
      });

      mgr.attachHost(
        {},
        new Map([
          [Shortcut.SEARCH, 'null'],
          [Shortcut.GO_TO_OPENED_CHANGES, 'null'],
        ])
      );
      assert.deepEqual(mapToObject(mgr.activeShortcutsBySection()), {
        [ShortcutSection.DIFFS]: [
          {shortcut: Shortcut.NEXT_LINE, text: 'Go to next line'},
        ],
        [ShortcutSection.EVERYWHERE]: [
          {shortcut: Shortcut.SEARCH, text: 'Search'},
          {
            shortcut: Shortcut.GO_TO_OPENED_CHANGES,
            text: 'Go to Opened Changes',
          },
        ],
        [ShortcutSection.NAVIGATION]: [
          {shortcut: Shortcut.NEXT_FILE, text: 'Go to next file'},
        ],
      });
    });

    test('directory view', () => {
      const mgr = new ShortcutsService();

      assert.deepEqual(mapToObject(mgr.directoryView()), {});

      mgr.attachHost(
        {},
        new Map([
          [Shortcut.GO_TO_OPENED_CHANGES, 'null'],
          [Shortcut.NEXT_FILE, 'null'],
          [Shortcut.NEXT_LINE, 'null'],
          [Shortcut.SAVE_COMMENT, 'null'],
          [Shortcut.SEARCH, 'null'],
        ])
      );
      assert.deepEqual(mapToObject(mgr.directoryView()), {
        [ShortcutSection.DIFFS]: [
          {binding: [['j']], text: 'Go to next line'},
          {
            binding: [
              ['Ctrl', 'Enter'],
              ['Meta', 'Enter'],
            ],
            text: 'Save comment',
          },
          {
            binding: [
              ['Ctrl', 's'],
              ['Meta', 's'],
            ],
            text: 'Save comment',
          },
        ],
        [ShortcutSection.EVERYWHERE]: [
          {binding: [['/']], text: 'Search'},
          {binding: [['g', 'o']], text: 'Go to Opened Changes'},
        ],
        [ShortcutSection.NAVIGATION]: [
          {binding: [[']']], text: 'Go to next file'},
        ],
      });
    });
  });
});
