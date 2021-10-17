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
import {PolymerElement} from '@polymer/polymer/polymer-element';
import '../../elements/shared/gr-overlay/gr-overlay';
import * as MockInteractions from '@polymer/iron-test-helpers/mock-interactions';

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

suite('keyboard-shortcut-mixin tests', () => {
  let element: GrKeyboardShortcutMixinTestElement;

  setup(async () => {
    element = basicFixture.instantiate();
    await flush();
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
});
