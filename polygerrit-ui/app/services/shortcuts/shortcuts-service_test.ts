/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../test/common-test-setup-karma';
import {
  COMBO_TIMEOUT_MS,
  describeBinding,
  ShortcutsService,
} from '../../services/shortcuts/shortcuts-service';
import {Shortcut, ShortcutSection} from './shortcuts-config';
import {SinonFakeTimers} from 'sinon';
import {Key, Modifier} from '../../utils/dom-util';
import {getAppContext} from '../app-context';

suite('shortcuts-service tests', () => {
  let service: ShortcutsService;

  setup(() => {
    service = new ShortcutsService(
      getAppContext().userModel,
      getAppContext().reportingService
    );
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
            bindings: [
              {allowRepeat: true, key: 'j'},
              {allowRepeat: true, key: 'ArrowDown'},
            ],
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
            bindings: [
              {allowRepeat: true, key: 'j'},
              {allowRepeat: true, key: 'ArrowDown'},
            ],
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
