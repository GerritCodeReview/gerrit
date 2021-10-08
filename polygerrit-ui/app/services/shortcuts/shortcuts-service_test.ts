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
import * as MockInteractions from '@polymer/iron-test-helpers/mock-interactions';

async function keyEventOn(
  el: HTMLElement,
  callback: (e: KeyboardEvent) => void,
  keyCode = 75,
  key = 'k'
): Promise<KeyboardEvent> {
  let resolve: (e: KeyboardEvent) => void;
  const promise = new Promise<KeyboardEvent>(r => (resolve = r));
  el.addEventListener('keydown', (e: KeyboardEvent) => {
    callback(e);
    resolve(e);
  });
  MockInteractions.keyDownOn(el, keyCode, null, key);
  return await promise;
}

suite('shortcuts-service tests', () => {
  let service: ShortcutsService;

  setup(() => {
    service = new ShortcutsService();
  });

  suite('shouldSuppress', () => {
    test('do not suppress shortcut event from <div>', async () => {
      await keyEventOn(document.createElement('div'), e => {
        assert.isFalse(service.shouldSuppress(e));
      });
    });

    test('suppress shortcut event from <input>', async () => {
      await keyEventOn(document.createElement('input'), e => {
        assert.isTrue(service.shouldSuppress(e));
      });
    });

    test('suppress shortcut event from <textarea>', async () => {
      await keyEventOn(document.createElement('textarea'), e => {
        assert.isTrue(service.shouldSuppress(e));
      });
    });

    test('do not suppress shortcut event from checkbox <input>', async () => {
      const inputEl = document.createElement('input');
      inputEl.setAttribute('type', 'checkbox');
      await keyEventOn(inputEl, e => {
        assert.isFalse(service.shouldSuppress(e));
      });
    });

    test('suppress shortcut event from children of <gr-overlay>', async () => {
      const overlay = document.createElement('gr-overlay');
      const div = document.createElement('div');
      overlay.appendChild(div);
      await keyEventOn(div, e => {
        assert.isTrue(service.shouldSuppress(e));
      });
    });

    test('suppress "enter" shortcut event from <a>', async () => {
      await keyEventOn(document.createElement('a'), e => {
        assert.isFalse(service.shouldSuppress(e));
      });
      await keyEventOn(
        document.createElement('a'),
        e => assert.isTrue(service.shouldSuppress(e)),
        13,
        'enter'
      );
    });
  });

  test('getShortcut', () => {
    const NEXT_FILE = Shortcut.NEXT_FILE;
    assert.equal(service.getShortcut(NEXT_FILE), ']');
  });

  test('getShortcut with modifiers', () => {
    const NEXT_FILE = Shortcut.TOGGLE_LEFT_PANE;
    assert.equal(service.getShortcut(NEXT_FILE), 'Shift+a');
  });

  suite('binding descriptions', () => {
    function mapToObject<K, V>(m: Map<K, V>) {
      const o: any = {};
      m.forEach((v: V, k: K) => (o[k] = v));
      return o;
    }

    test('single combo description', () => {
      assert.deepEqual(service.describeBinding('a'), ['a']);
      assert.deepEqual(service.describeBinding('a:keyup'), ['a']);
      assert.deepEqual(service.describeBinding('ctrl+a'), ['Ctrl', 'a']);
      assert.deepEqual(service.describeBinding('ctrl+shift+up:keyup'), [
        'Ctrl',
        'Shift',
        '↑',
      ]);
    });

    test('combo set description', () => {
      assert.deepEqual(
        service.describeBindings(Shortcut.GO_TO_OPENED_CHANGES),
        [['g', 'o']]
      );
      assert.deepEqual(service.describeBindings(Shortcut.SAVE_COMMENT), [
        ['Ctrl', 'Enter'],
        ['Meta', 'Enter'],
        ['Ctrl', 's'],
        ['Meta', 's'],
      ]);
      assert.deepEqual(service.describeBindings(Shortcut.PREV_FILE), [['[']]);
    });

    test('combo set description width', () => {
      assert.strictEqual(service.comboSetDisplayWidth([['u']]), 1);
      assert.strictEqual(service.comboSetDisplayWidth([['g', 'o']]), 2);
      assert.strictEqual(service.comboSetDisplayWidth([['Shift', 'r']]), 6);
      assert.strictEqual(service.comboSetDisplayWidth([['x'], ['y']]), 4);
      assert.strictEqual(
        service.comboSetDisplayWidth([['x'], ['y'], ['Shift', 'z']]),
        12
      );
    });

    test('distribute shortcut help', () => {
      assert.deepEqual(service.distributeBindingDesc([['o']]), [[['o']]]);
      assert.deepEqual(service.distributeBindingDesc([['g', 'o']]), [
        [['g', 'o']],
      ]);
      assert.deepEqual(
        service.distributeBindingDesc([['ctrl', 'shift', 'meta', 'enter']]),
        [[['ctrl', 'shift', 'meta', 'enter']]]
      );
      assert.deepEqual(
        service.distributeBindingDesc([
          ['ctrl', 'shift', 'meta', 'enter'],
          ['o'],
        ]),
        [[['ctrl', 'shift', 'meta', 'enter']], [['o']]]
      );
      assert.deepEqual(
        service.distributeBindingDesc([
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
      assert.deepEqual(mapToObject(service.activeShortcutsBySection()), {});

      service.attachHost({}, new Map([[Shortcut.NEXT_FILE, 'null']]));
      assert.deepEqual(mapToObject(service.activeShortcutsBySection()), {
        [ShortcutSection.NAVIGATION]: [
          {
            shortcut: Shortcut.NEXT_FILE,
            text: 'Go to next file',
            bindings: [']'],
          },
        ],
      });

      service.attachHost({}, new Map([[Shortcut.NEXT_LINE, 'null']]));
      assert.deepEqual(mapToObject(service.activeShortcutsBySection()), {
        [ShortcutSection.DIFFS]: [
          {
            shortcut: Shortcut.NEXT_LINE,
            text: 'Go to next line',
            bindings: ['j', 'down'],
          },
        ],
        [ShortcutSection.NAVIGATION]: [
          {
            shortcut: Shortcut.NEXT_FILE,
            text: 'Go to next file',
            bindings: [']'],
          },
        ],
      });

      service.attachHost(
        {},
        new Map([
          [Shortcut.SEARCH, 'null'],
          [Shortcut.GO_TO_OPENED_CHANGES, 'null'],
        ])
      );
      assert.deepEqual(mapToObject(service.activeShortcutsBySection()), {
        [ShortcutSection.DIFFS]: [
          {
            shortcut: Shortcut.NEXT_LINE,
            text: 'Go to next line',
            bindings: ['j', 'down'],
          },
        ],
        [ShortcutSection.EVERYWHERE]: [
          {
            shortcut: Shortcut.SEARCH,
            text: 'Search',
            bindings: ['/'],
          },
          {
            shortcut: Shortcut.GO_TO_OPENED_CHANGES,
            text: 'Go to Opened Changes',
            bindings: ['GO_KEY', 'o'],
          },
        ],
        [ShortcutSection.NAVIGATION]: [
          {
            shortcut: Shortcut.NEXT_FILE,
            text: 'Go to next file',
            bindings: [']'],
          },
        ],
      });
    });

    test('directory view', () => {
      assert.deepEqual(mapToObject(service.directoryView()), {});

      service.attachHost(
        {},
        new Map([
          [Shortcut.GO_TO_OPENED_CHANGES, 'null'],
          [Shortcut.NEXT_FILE, 'null'],
          [Shortcut.NEXT_LINE, 'null'],
          [Shortcut.SAVE_COMMENT, 'null'],
          [Shortcut.SEARCH, 'null'],
        ])
      );
      assert.deepEqual(mapToObject(service.directoryView()), {
        [ShortcutSection.DIFFS]: [
          {binding: [['j'], ['↓']], text: 'Go to next line'},
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
