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
import {
  COMBO_TIMEOUT_MS,
  describeBinding,
  ShortcutsService,
} from '../../services/shortcuts/shortcuts-service';
import {Shortcut, ShortcutSection} from './shortcuts-config';
import * as MockInteractions from '@polymer/iron-test-helpers/mock-interactions';
import {SinonFakeTimers} from 'sinon';
import {Key, Modifier} from '../../utils/dom-util';

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
    assert.equal(service.getShortcut(Shortcut.NEXT_FILE), ']');
    assert.equal(service.getShortcut(Shortcut.TOGGLE_LEFT_PANE), 'A');
    assert.equal(
      service.getShortcut(Shortcut.SEND_REPLY),
      'Ctrl+Enter,Meta/Cmd+Enter'
    );
  });

  suite('binding descriptions', () => {
    function mapToObject<K, V>(m: Map<K, V>) {
      const o: any = {};
      m.forEach((v: V, k: K) => (o[k] = v));
      return o;
    }

    test('single combo description', () => {
      assert.deepEqual(describeBinding({key: 'a'}), ['a']);
      assert.deepEqual(
        describeBinding({key: 'a', modifiers: [Modifier.CTRL_KEY]}),
        ['Ctrl', 'a']
      );
      assert.deepEqual(
        describeBinding({
          key: Key.UP,
          modifiers: [Modifier.CTRL_KEY, Modifier.SHIFT_KEY],
        }),
        ['Shift', 'Ctrl', '↑']
      );
    });

    test('combo set description', () => {
      assert.deepEqual(
        service.describeBindings(Shortcut.GO_TO_OPENED_CHANGES),
        [['g', 'o']]
      );
      assert.deepEqual(service.describeBindings(Shortcut.SAVE_COMMENT), [
        ['Ctrl', 'Enter'],
        ['Meta/Cmd', 'Enter'],
        ['Ctrl', 's'],
        ['Meta/Cmd', 's'],
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

      service.attachHost(document.createElement('div'), [
        {shortcut: Shortcut.NEXT_FILE, listener: _ => {}},
      ]);
      assert.deepEqual(mapToObject(service.activeShortcutsBySection()), {
        [ShortcutSection.NAVIGATION]: [
          {
            shortcut: Shortcut.NEXT_FILE,
            text: 'Go to next file',
            bindings: [{key: ']'}],
          },
        ],
      });

      service.attachHost(document.createElement('div'), [
        {shortcut: Shortcut.NEXT_LINE, listener: _ => {}},
      ]);
      assert.deepEqual(mapToObject(service.activeShortcutsBySection()), {
        [ShortcutSection.DIFFS]: [
          {
            shortcut: Shortcut.NEXT_LINE,
            text: 'Go to next line',
            bindings: [{key: 'j'}, {key: 'ArrowDown'}],
          },
        ],
        [ShortcutSection.NAVIGATION]: [
          {
            shortcut: Shortcut.NEXT_FILE,
            text: 'Go to next file',
            bindings: [{key: ']'}],
          },
        ],
      });

      service.attachHost(document.createElement('div'), [
        {shortcut: Shortcut.SEARCH, listener: _ => {}},
        {shortcut: Shortcut.GO_TO_OPENED_CHANGES, listener: _ => {}},
      ]);
      assert.deepEqual(mapToObject(service.activeShortcutsBySection()), {
        [ShortcutSection.DIFFS]: [
          {
            shortcut: Shortcut.NEXT_LINE,
            text: 'Go to next line',
            bindings: [{key: 'j'}, {key: 'ArrowDown'}],
          },
        ],
        [ShortcutSection.EVERYWHERE]: [
          {
            shortcut: Shortcut.SEARCH,
            text: 'Search',
            bindings: [{key: '/'}],
          },
          {
            shortcut: Shortcut.GO_TO_OPENED_CHANGES,
            text: 'Go to Opened Changes',
            bindings: [{key: 'o', combo: 'g'}],
          },
        ],
        [ShortcutSection.NAVIGATION]: [
          {
            shortcut: Shortcut.NEXT_FILE,
            text: 'Go to next file',
            bindings: [{key: ']'}],
          },
        ],
      });
    });

    test('directory view', () => {
      assert.deepEqual(mapToObject(service.directoryView()), {});

      service.attachHost(document.createElement('div'), [
        {shortcut: Shortcut.GO_TO_OPENED_CHANGES, listener: _ => {}},
        {shortcut: Shortcut.NEXT_FILE, listener: _ => {}},
        {shortcut: Shortcut.NEXT_LINE, listener: _ => {}},
        {shortcut: Shortcut.SAVE_COMMENT, listener: _ => {}},
        {shortcut: Shortcut.SEARCH, listener: _ => {}},
      ]);
      assert.deepEqual(mapToObject(service.directoryView()), {
        [ShortcutSection.DIFFS]: [
          {binding: [['j'], ['↓']], text: 'Go to next line'},
          {
            binding: [['Ctrl', 'Enter']],
            text: 'Save comment',
          },
          {
            binding: [
              ['Meta/Cmd', 'Enter'],
              ['Ctrl', 's'],
            ],
            text: 'Save comment',
          },
          {
            binding: [['Meta/Cmd', 's']],
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

  suite('combo keys', () => {
    let clock: SinonFakeTimers;
    setup(() => {
      clock = sinon.useFakeTimers();
      clock.tick(1000);
    });

    teardown(() => {
      clock.restore();
    });

    test('not in combo key mode initially', () => {
      assert.isFalse(service.isInComboKeyMode());
    });

    test('pressing f does not switch into combo key mode', () => {
      const event = new KeyboardEvent('keydown', {key: 'f'});
      document.dispatchEvent(event);
      assert.isFalse(service.isInComboKeyMode());
    });

    test('pressing g switches into combo key mode', () => {
      const event = new KeyboardEvent('keydown', {key: 'g'});
      document.dispatchEvent(event);
      assert.isTrue(service.isInComboKeyMode());
    });

    test('pressing v switches into combo key mode', () => {
      const event = new KeyboardEvent('keydown', {key: 'v'});
      document.dispatchEvent(event);
      assert.isTrue(service.isInComboKeyMode());
    });

    test('combo key mode timeout', () => {
      const event = new KeyboardEvent('keydown', {key: 'g'});
      document.dispatchEvent(event);
      assert.isTrue(service.isInComboKeyMode());
      clock.tick(COMBO_TIMEOUT_MS / 2);
      assert.isTrue(service.isInComboKeyMode());
      clock.tick(COMBO_TIMEOUT_MS);
      assert.isFalse(service.isInComboKeyMode());
    });
  });
});
