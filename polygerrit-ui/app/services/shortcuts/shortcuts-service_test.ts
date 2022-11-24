/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../test/common-test-setup';
import {
  COMBO_TIMEOUT_MS,
  describeBinding,
  ShortcutsService,
} from '../../services/shortcuts/shortcuts-service';
import {Shortcut, ShortcutSection} from './shortcuts-config';
import {SinonFakeTimers, SinonSpy} from 'sinon';
import {Binding, Key, Modifier} from '../../utils/dom-util';
import {getAppContext} from '../app-context';
import {pressKey} from '../../test/test-utils';
import {assert} from '@open-wc/testing';
import {testResolver} from '../../test/common-test-setup';
import {userModelToken} from '../../models/user/user-model';

const KEY_A: Binding = {key: 'a'};

suite('shortcuts-service tests', () => {
  let service: ShortcutsService;

  setup(() => {
    service = new ShortcutsService(
      testResolver(userModelToken),
      getAppContext().reportingService
    );
  });

  test('getShortcut', () => {
    assert.equal(service.getShortcut(Shortcut.NEXT_FILE), ']');
    assert.equal(service.getShortcut(Shortcut.TOGGLE_LEFT_PANE), 'Shift + a');
  });

  suite('addShortcut()', () => {
    let el: HTMLElement;
    let listener: SinonSpy<[KeyboardEvent], void>;

    setup(() => {
      el = document.createElement('div');
      listener = sinon.spy() as SinonSpy<[KeyboardEvent], void>;
    });

    test('standard call', () => {
      service.addShortcut(el, KEY_A, listener);
      assert.isTrue(listener.notCalled);
      pressKey(el, KEY_A.key);
      assert.isTrue(listener.calledOnce);
    });

    test('preventDefault option default false', () => {
      service.addShortcut(el, KEY_A, listener);
      pressKey(el, KEY_A.key);
      assert.isTrue(listener.calledOnce);
      assert.isTrue(listener.lastCall.firstArg?.defaultPrevented);
    });

    test('preventDefault option force false', () => {
      service.addShortcut(el, KEY_A, listener, {preventDefault: false});
      pressKey(el, KEY_A.key);
      assert.isTrue(listener.calledOnce);
      assert.isFalse(listener.lastCall.firstArg?.defaultPrevented);
    });

    test('preventDefault option force true', () => {
      service.addShortcut(el, KEY_A, listener, {preventDefault: true});
      pressKey(el, KEY_A.key);
      assert.isTrue(listener.calledOnce);
      assert.isTrue(listener.lastCall.firstArg?.defaultPrevented);
    });

    test('shouldSuppress option default true', () => {
      service.shortcutsDisabled = true;
      service.addShortcut(el, KEY_A, listener);
      pressKey(el, KEY_A.key);
      assert.isTrue(listener.notCalled);
    });

    test('shouldSuppress option force true', () => {
      service.shortcutsDisabled = true;
      service.addShortcut(el, KEY_A, listener, {shouldSuppress: true});
      pressKey(el, KEY_A.key);
      assert.isTrue(listener.notCalled);
    });

    test('shouldSuppress option force false', () => {
      service.shortcutsDisabled = true;
      service.addShortcut(el, KEY_A, listener, {shouldSuppress: false});
      pressKey(el, KEY_A.key);
      assert.isTrue(listener.calledOnce);
    });
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

      service.addShortcutListener(Shortcut.NEXT_FILE, _ => {});
      assert.deepEqual(mapToObject(service.activeShortcutsBySection()), {
        [ShortcutSection.NAVIGATION]: [
          {
            shortcut: Shortcut.NEXT_FILE,
            text: 'Go to next file',
            bindings: [{key: ']'}],
          },
        ],
      });
      service.addShortcutListener(Shortcut.NEXT_LINE, _ => {});
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

      service.addShortcutListener(Shortcut.SEARCH, _ => {});
      service.addShortcutListener(Shortcut.GO_TO_OPENED_CHANGES, _ => {});
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

      service.addShortcutListener(Shortcut.GO_TO_OPENED_CHANGES, _ => {});
      service.addShortcutListener(Shortcut.NEXT_FILE, _ => {});
      service.addShortcutListener(Shortcut.NEXT_LINE, _ => {});
      service.addShortcutListener(Shortcut.SAVE_COMMENT, _ => {});
      service.addShortcutListener(Shortcut.SEARCH, _ => {});
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
