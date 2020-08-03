/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
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

import '../../test/common-test-setup-karma.js';
import {
  KeyboardShortcutMixin, Shortcut,
  ShortcutManager, ShortcutSection, SPECIAL_SHORTCUT,
} from './keyboard-shortcut-mixin.js';
import {html} from '@polymer/polymer/lib/utils/html-tag.js';
import {PolymerElement} from '@polymer/polymer/polymer-element.js';

const basicFixture =
    fixtureFromElement('keyboard-shortcut-mixin-test-element');

const withinOverlayFixture = fixtureFromTemplate(html`
<gr-overlay>
  <keyboard-shortcut-mixin-test-element>
  </keyboard-shortcut-mixin-test-element>
</gr-overlay>
`);

class GrKeyboardShortcutMixinTestElement extends
  KeyboardShortcutMixin(PolymerElement) {
  static get is() {
    return 'keyboard-shortcut-mixin-test-element';
  }

  get keyBindings() {
    return {
      k: '_handleKey',
      enter: '_handleKey',
    };
  }

  _handleKey() {}
}

customElements.define(GrKeyboardShortcutMixinTestElement.is,
    GrKeyboardShortcutMixinTestElement);

suite('keyboard-shortcut-mixin tests', () => {
  let element;
  let overlay;

  setup(() => {
    element = basicFixture.instantiate();
    overlay = withinOverlayFixture.instantiate();
  });

  suite('ShortcutManager', () => {
    test('bindings management', () => {
      const mgr = new ShortcutManager();
      const NEXT_FILE = Shortcut.NEXT_FILE;

      assert.isUndefined(mgr.getBindingsForShortcut(NEXT_FILE));
      mgr.bindShortcut(NEXT_FILE, ']', '}', 'right');
      assert.deepEqual(
          mgr.getBindingsForShortcut(NEXT_FILE),
          [']', '}', 'right']);
    });

    test('getShortcut', () => {
      const mgr = new ShortcutManager();
      const NEXT_FILE = Shortcut.NEXT_FILE;

      assert.isUndefined(mgr.getBindingsForShortcut(NEXT_FILE));
      mgr.bindShortcut(NEXT_FILE, ']', '}', 'right');
      assert.equal(mgr.getShortcut(NEXT_FILE), '], }, →');
    });

    suite('binding descriptions', () => {
      function mapToObject(m) {
        const o = {};
        m.forEach((v, k) => o[k] = v);
        return o;
      }

      test('single combo description', () => {
        const mgr = new ShortcutManager();
        assert.deepEqual(mgr.describeBinding('a'), ['a']);
        assert.deepEqual(mgr.describeBinding('a:keyup'), ['a']);
        assert.deepEqual(mgr.describeBinding('ctrl+a'), ['Ctrl', 'a']);
        assert.deepEqual(
            mgr.describeBinding('ctrl+shift+up:keyup'),
            ['Ctrl', 'Shift', '↑']);
      });

      test('combo set description', () => {
        const mgr = new ShortcutManager();
        assert.isNull(mgr.describeBindings(Shortcut.NEXT_FILE));

        mgr.bindShortcut(Shortcut.GO_TO_OPENED_CHANGES,
            SPECIAL_SHORTCUT.GO_KEY, 'o');
        assert.deepEqual(
            mgr.describeBindings(Shortcut.GO_TO_OPENED_CHANGES),
            [['g', 'o']]);

        mgr.bindShortcut(Shortcut.NEXT_FILE, SPECIAL_SHORTCUT.DOC_ONLY,
            ']', 'ctrl+shift+right:keyup');
        assert.deepEqual(
            mgr.describeBindings(Shortcut.NEXT_FILE),
            [[']'], ['Ctrl', 'Shift', '→']]);

        mgr.bindShortcut(Shortcut.PREV_FILE, '[');
        assert.deepEqual(mgr.describeBindings(Shortcut.PREV_FILE), [['[']]);
      });

      test('combo set description width', () => {
        const mgr = new ShortcutManager();
        assert.strictEqual(mgr.comboSetDisplayWidth([['u']]), 1);
        assert.strictEqual(mgr.comboSetDisplayWidth([['g', 'o']]), 2);
        assert.strictEqual(mgr.comboSetDisplayWidth([['Shift', 'r']]), 6);
        assert.strictEqual(mgr.comboSetDisplayWidth([['x'], ['y']]), 4);
        assert.strictEqual(
            mgr.comboSetDisplayWidth([['x'], ['y'], ['Shift', 'z']]),
            12);
      });

      test('distribute shortcut help', () => {
        const mgr = new ShortcutManager();
        assert.deepEqual(mgr.distributeBindingDesc([['o']]), [[['o']]]);
        assert.deepEqual(
            mgr.distributeBindingDesc([['g', 'o']]),
            [[['g', 'o']]]);
        assert.deepEqual(
            mgr.distributeBindingDesc([['ctrl', 'shift', 'meta', 'enter']]),
            [[['ctrl', 'shift', 'meta', 'enter']]]);
        assert.deepEqual(
            mgr.distributeBindingDesc([
              ['ctrl', 'shift', 'meta', 'enter'],
              ['o'],
            ]),
            [
              [['ctrl', 'shift', 'meta', 'enter']],
              [['o']],
            ]);
        assert.deepEqual(
            mgr.distributeBindingDesc([
              ['ctrl', 'enter'],
              ['meta', 'enter'],
              ['ctrl', 's'],
              ['meta', 's'],
            ]),
            [
              [['ctrl', 'enter'], ['meta', 'enter']],
              [['ctrl', 's'], ['meta', 's']],
            ]);
      });

      test('active shortcuts by section', () => {
        const mgr = new ShortcutManager();
        mgr.bindShortcut(Shortcut.NEXT_FILE, ']');
        mgr.bindShortcut(Shortcut.NEXT_LINE, 'j');
        mgr.bindShortcut(Shortcut.GO_TO_OPENED_CHANGES, 'g+o');
        mgr.bindShortcut(Shortcut.SEARCH, '/');

        assert.deepEqual(
            mapToObject(mgr.activeShortcutsBySection()),
            {});

        mgr.attachHost({
          keyboardShortcuts() {
            return {
              [Shortcut.NEXT_FILE]: null,
            };
          },
        });
        assert.deepEqual(
            mapToObject(mgr.activeShortcutsBySection()),
            {
              [ShortcutSection.NAVIGATION]: [
                {shortcut: Shortcut.NEXT_FILE, text: 'Go to next file'},
              ],
            });

        mgr.attachHost({
          keyboardShortcuts() {
            return {
              [Shortcut.NEXT_LINE]: null,
            };
          },
        });
        assert.deepEqual(
            mapToObject(mgr.activeShortcutsBySection()),
            {
              [ShortcutSection.DIFFS]: [
                {shortcut: Shortcut.NEXT_LINE, text: 'Go to next line'},
              ],
              [ShortcutSection.NAVIGATION]: [
                {shortcut: Shortcut.NEXT_FILE, text: 'Go to next file'},
              ],
            });

        mgr.attachHost({
          keyboardShortcuts() {
            return {
              [Shortcut.SEARCH]: null,
              [Shortcut.GO_TO_OPENED_CHANGES]: null,
            };
          },
        });
        assert.deepEqual(
            mapToObject(mgr.activeShortcutsBySection()),
            {
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
        const mgr = new ShortcutManager();
        mgr.bindShortcut(Shortcut.NEXT_FILE, ']');
        mgr.bindShortcut(Shortcut.NEXT_LINE, 'j');
        mgr.bindShortcut(Shortcut.GO_TO_OPENED_CHANGES,
            SPECIAL_SHORTCUT.GO_KEY, 'o');
        mgr.bindShortcut(Shortcut.SEARCH, '/');
        mgr.bindShortcut(
            Shortcut.SAVE_COMMENT, 'ctrl+enter', 'meta+enter',
            'ctrl+s', 'meta+s');

        assert.deepEqual(mapToObject(mgr.directoryView()), {});

        mgr.attachHost({
          keyboardShortcuts() {
            return {
              [Shortcut.GO_TO_OPENED_CHANGES]: null,
              [Shortcut.NEXT_FILE]: null,
              [Shortcut.NEXT_LINE]: null,
              [Shortcut.SAVE_COMMENT]: null,
              [Shortcut.SEARCH]: null,
            };
          },
        });
        assert.deepEqual(
            mapToObject(mgr.directoryView()),
            {
              [ShortcutSection.DIFFS]: [
                {binding: [['j']], text: 'Go to next line'},
                {
                  binding: [['Ctrl', 'Enter'], ['Meta', 'Enter']],
                  text: 'Save comment',
                },
                {
                  binding: [['Ctrl', 's'], ['Meta', 's']],
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

  test('doesn’t block kb shortcuts for non-allowed els', done => {
    const divEl = document.createElement('div');
    element.appendChild(divEl);
    element._handleKey = e => {
      assert.isFalse(element.shouldSuppressKeyboardShortcut(e));
      done();
    };
    MockInteractions.keyDownOn(divEl, 75, null, 'k');
  });

  test('blocks kb shortcuts for input els', done => {
    const inputEl = document.createElement('input');
    element.appendChild(inputEl);
    element._handleKey = e => {
      assert.isTrue(element.shouldSuppressKeyboardShortcut(e));
      done();
    };
    MockInteractions.keyDownOn(inputEl, 75, null, 'k');
  });

  test('blocks kb shortcuts for textarea els', done => {
    const textareaEl = document.createElement('textarea');
    element.appendChild(textareaEl);
    element._handleKey = e => {
      assert.isTrue(element.shouldSuppressKeyboardShortcut(e));
      done();
    };
    MockInteractions.keyDownOn(textareaEl, 75, null, 'k');
  });

  test('blocks kb shortcuts for anything in a gr-overlay', done => {
    const divEl = document.createElement('div');
    const element =
        overlay.querySelector('keyboard-shortcut-mixin-test-element');
    element.appendChild(divEl);
    element._handleKey = e => {
      assert.isTrue(element.shouldSuppressKeyboardShortcut(e));
      done();
    };
    MockInteractions.keyDownOn(divEl, 75, null, 'k');
  });

  test('blocks enter shortcut on an anchor', done => {
    const anchorEl = document.createElement('a');
    const element =
        overlay.querySelector('keyboard-shortcut-mixin-test-element');
    element.appendChild(anchorEl);
    element._handleKey = e => {
      assert.isTrue(element.shouldSuppressKeyboardShortcut(e));
      done();
    };
    MockInteractions.keyDownOn(anchorEl, 13, null, 'enter');
  });

  test('modifierPressed returns accurate values', () => {
    const spy = sinon.spy(element, 'modifierPressed');
    element._handleKey = e => {
      element.modifierPressed(e);
    };
    MockInteractions.keyDownOn(element, 75, 'shift', 'k');
    assert.isTrue(spy.lastCall.returnValue);
    MockInteractions.keyDownOn(element, 75, null, 'k');
    assert.isFalse(spy.lastCall.returnValue);
    MockInteractions.keyDownOn(element, 75, 'ctrl', 'k');
    assert.isTrue(spy.lastCall.returnValue);
    MockInteractions.keyDownOn(element, 75, null, 'k');
    assert.isFalse(spy.lastCall.returnValue);
    MockInteractions.keyDownOn(element, 75, 'meta', 'k');
    assert.isTrue(spy.lastCall.returnValue);
    MockInteractions.keyDownOn(element, 75, null, 'k');
    assert.isFalse(spy.lastCall.returnValue);
    MockInteractions.keyDownOn(element, 75, 'alt', 'k');
    assert.isTrue(spy.lastCall.returnValue);
  });

  test('isModifierPressed returns accurate value', () => {
    const spy = sinon.spy(element, 'isModifierPressed');
    element._handleKey = e => {
      element.isModifierPressed(e, 'shiftKey');
    };
    MockInteractions.keyDownOn(element, 75, 'shift', 'k');
    assert.isTrue(spy.lastCall.returnValue);
    MockInteractions.keyDownOn(element, 75, null, 'k');
    assert.isFalse(spy.lastCall.returnValue);
    MockInteractions.keyDownOn(element, 75, 'ctrl', 'k');
    assert.isFalse(spy.lastCall.returnValue);
    MockInteractions.keyDownOn(element, 75, null, 'k');
    assert.isFalse(spy.lastCall.returnValue);
    MockInteractions.keyDownOn(element, 75, 'meta', 'k');
    assert.isFalse(spy.lastCall.returnValue);
    MockInteractions.keyDownOn(element, 75, null, 'k');
    assert.isFalse(spy.lastCall.returnValue);
    MockInteractions.keyDownOn(element, 75, 'alt', 'k');
    assert.isFalse(spy.lastCall.returnValue);
  });

  suite('GO_KEY timing', () => {
    let handlerStub;

    setup(() => {
      element._shortcut_go_table.set('a', '_handleA');
      handlerStub = element._handleA = sinon.stub();
      sinon.stub(Date, 'now').returns(10000);
    });

    test('success', () => {
      const e = {detail: {key: 'a'}, preventDefault: () => {}};
      sinon.stub(element, 'shouldSuppressKeyboardShortcut').returns(false);
      element._shortcut_go_key_last_pressed = 9000;
      element._handleGoAction(e);
      assert.isTrue(handlerStub.calledOnce);
      assert.strictEqual(handlerStub.lastCall.args[0], e);
    });

    test('go key not pressed', () => {
      const e = {detail: {key: 'a'}, preventDefault: () => {}};
      sinon.stub(element, 'shouldSuppressKeyboardShortcut').returns(false);
      element._shortcut_go_key_last_pressed = null;
      element._handleGoAction(e);
      assert.isFalse(handlerStub.called);
    });

    test('go key pressed too long ago', () => {
      const e = {detail: {key: 'a'}, preventDefault: () => {}};
      sinon.stub(element, 'shouldSuppressKeyboardShortcut').returns(false);
      element._shortcut_go_key_last_pressed = 3000;
      element._handleGoAction(e);
      assert.isFalse(handlerStub.called);
    });

    test('should suppress', () => {
      const e = {detail: {key: 'a'}, preventDefault: () => {}};
      sinon.stub(element, 'shouldSuppressKeyboardShortcut').returns(true);
      element._shortcut_go_key_last_pressed = 9000;
      element._handleGoAction(e);
      assert.isFalse(handlerStub.called);
    });

    test('unrecognized key', () => {
      const e = {detail: {key: 'f'}, preventDefault: () => {}};
      sinon.stub(element, 'shouldSuppressKeyboardShortcut').returns(false);
      element._shortcut_go_key_last_pressed = 9000;
      element._handleGoAction(e);
      assert.isFalse(handlerStub.called);
    });
  });
});

