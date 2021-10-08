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
import '../../test/common-test-setup-karma';
import {KeyboardShortcutMixin} from './keyboard-shortcut-mixin';
import {html} from '@polymer/polymer/lib/utils/html-tag';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {mockPromise, queryAndAssert} from '../../test/test-utils';
import '../../elements/shared/gr-overlay/gr-overlay';
import {GrOverlay} from '../../elements/shared/gr-overlay/gr-overlay';
import * as MockInteractions from '@polymer/iron-test-helpers/mock-interactions';
import {CustomKeyboardEvent} from '../../types/events';

class GrKeyboardShortcutMixinTestElement extends KeyboardShortcutMixin(
  PolymerElement
) {
  static get is() {
    return 'keyboard-shortcut-mixin-test-element';
  }

  get keyBindings() {
    return {
      k: '_handleKey',
      enter: '_handleKey',
    };
  }

  _handleKey(_: any) {}

  _handleA(_: any) {}
}

declare global {
  interface HTMLElementTagNameMap {
    'keyboard-shortcut-mixin-test-element': GrKeyboardShortcutMixinTestElement;
  }
}

customElements.define(
  GrKeyboardShortcutMixinTestElement.is,
  GrKeyboardShortcutMixinTestElement
);

const basicFixture = fixtureFromElement('keyboard-shortcut-mixin-test-element');

const withinOverlayFixture = fixtureFromTemplate(html`
  <gr-overlay>
    <keyboard-shortcut-mixin-test-element>
    </keyboard-shortcut-mixin-test-element>
  </gr-overlay>
`);

suite('keyboard-shortcut-mixin tests', () => {
  let element: GrKeyboardShortcutMixinTestElement;
  let overlay: GrOverlay;

  setup(async () => {
    element = basicFixture.instantiate();
    overlay = withinOverlayFixture.instantiate() as GrOverlay;
    await flush();
  });

  test('doesn’t block kb shortcuts for non-allowed els', async () => {
    const divEl = document.createElement('div');
    element.appendChild(divEl);
    const promise = mockPromise();
    element._handleKey = e => {
      assert.isFalse(element.shouldSuppressKeyboardShortcut(e));
      promise.resolve();
    };
    MockInteractions.keyDownOn(divEl, 75, null, 'k');
    await promise;
  });

  test('blocks kb shortcuts for input els', async () => {
    const inputEl = document.createElement('input');
    element.appendChild(inputEl);
    const promise = mockPromise();
    element._handleKey = e => {
      assert.isTrue(element.shouldSuppressKeyboardShortcut(e));
      promise.resolve();
    };
    MockInteractions.keyDownOn(inputEl, 75, null, 'k');
    await promise;
  });

  test('doesn’t block kb shortcuts for checkboxes', async () => {
    const inputEl = document.createElement('input');
    inputEl.setAttribute('type', 'checkbox');
    element.appendChild(inputEl);
    const promise = mockPromise();
    element._handleKey = e => {
      assert.isFalse(element.shouldSuppressKeyboardShortcut(e));
      promise.resolve();
    };
    MockInteractions.keyDownOn(inputEl, 75, null, 'k');
    await promise;
  });

  test('blocks kb shortcuts for textarea els', async () => {
    const textareaEl = document.createElement('textarea');
    element.appendChild(textareaEl);
    const promise = mockPromise();
    element._handleKey = e => {
      assert.isTrue(element.shouldSuppressKeyboardShortcut(e));
      promise.resolve();
    };
    MockInteractions.keyDownOn(textareaEl, 75, null, 'k');
    await promise;
  });

  test('blocks kb shortcuts for anything in a gr-overlay', async () => {
    const divEl = document.createElement('div');
    const element = queryAndAssert<GrKeyboardShortcutMixinTestElement>(
      overlay,
      'keyboard-shortcut-mixin-test-element'
    );
    element.appendChild(divEl);
    const promise = mockPromise();
    element._handleKey = e => {
      assert.isTrue(element.shouldSuppressKeyboardShortcut(e));
      promise.resolve();
    };
    MockInteractions.keyDownOn(divEl, 75, null, 'k');
    await promise;
  });

  test('blocks enter shortcut on an anchor', async () => {
    const anchorEl = document.createElement('a');
    const element = queryAndAssert<GrKeyboardShortcutMixinTestElement>(
      overlay,
      'keyboard-shortcut-mixin-test-element'
    );
    element.appendChild(anchorEl);
    const promise = mockPromise();
    element._handleKey = e => {
      assert.isTrue(element.shouldSuppressKeyboardShortcut(e));
      promise.resolve();
    };
    MockInteractions.keyDownOn(anchorEl, 13, null, 'enter');
    await promise;
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

  suite('GO_KEY timing', () => {
    let handlerStub: sinon.SinonStub;

    setup(() => {
      element._shortcut_go_table.set('a', '_handleA');
      handlerStub = element._handleA = sinon.stub();
      sinon.stub(Date, 'now').returns(10000);
    });

    test('success', () => {
      const e = {
        detail: {key: 'a'},
        preventDefault: () => {},
      } as CustomKeyboardEvent;
      sinon.stub(element, 'shouldSuppressKeyboardShortcut').returns(false);
      element._shortcut_go_key_last_pressed = 9000;
      element._handleGoAction(e);
      assert.isTrue(handlerStub.calledOnce);
      assert.strictEqual(handlerStub.lastCall.args[0], e);
    });

    test('go key not pressed', () => {
      const e = {
        detail: {key: 'a'},
        preventDefault: () => {},
      } as CustomKeyboardEvent;
      sinon.stub(element, 'shouldSuppressKeyboardShortcut').returns(false);
      element._shortcut_go_key_last_pressed = null;
      element._handleGoAction(e);
      assert.isFalse(handlerStub.called);
    });

    test('go key pressed too long ago', () => {
      const e = {
        detail: {key: 'a'},
        preventDefault: () => {},
      } as CustomKeyboardEvent;
      sinon.stub(element, 'shouldSuppressKeyboardShortcut').returns(false);
      element._shortcut_go_key_last_pressed = 3000;
      element._handleGoAction(e);
      assert.isFalse(handlerStub.called);
    });

    test('should suppress', () => {
      const e = {
        detail: {key: 'a'},
        preventDefault: () => {},
      } as CustomKeyboardEvent;
      sinon.stub(element, 'shouldSuppressKeyboardShortcut').returns(true);
      element._shortcut_go_key_last_pressed = 9000;
      element._handleGoAction(e);
      assert.isFalse(handlerStub.called);
    });

    test('unrecognized key', () => {
      const e = {
        detail: {key: 'f'},
        preventDefault: () => {},
      } as CustomKeyboardEvent;
      sinon.stub(element, 'shouldSuppressKeyboardShortcut').returns(false);
      element._shortcut_go_key_last_pressed = 9000;
      element._handleGoAction(e);
      assert.isFalse(handlerStub.called);
    });
  });
});
