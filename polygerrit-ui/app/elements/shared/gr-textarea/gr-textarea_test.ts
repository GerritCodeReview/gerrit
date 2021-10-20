/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
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

import '../../../test/common-test-setup-karma';
import './gr-textarea';
import {GrTextarea} from './gr-textarea';
import {html} from '@polymer/polymer/lib/utils/html-tag';
import * as MockInteractions from '@polymer/iron-test-helpers/mock-interactions';
import {ItemSelectedEvent} from '../gr-autocomplete-dropdown/gr-autocomplete-dropdown';

const basicFixture = fixtureFromElement('gr-textarea');

const monospaceFixture = fixtureFromTemplate(html`
  <gr-textarea monospace="true"></gr-textarea>
`);

const hideBorderFixture = fixtureFromTemplate(html`
  <gr-textarea hide-border="true"></gr-textarea>
`);

suite('gr-textarea tests', () => {
  let element: GrTextarea;

  setup(() => {
    element = basicFixture.instantiate();
    sinon.stub(element.reporting, 'reportInteraction');
  });

  test('monospace is set properly', () => {
    assert.isFalse(element.classList.contains('monospace'));
  });

  test('hideBorder is set properly', () => {
    assert.isFalse(element.$.textarea.classList.contains('noBorder'));
  });

  test('emoji selector is not open with the textarea lacks focus', () => {
    element.$.textarea.selectionStart = 1;
    element.$.textarea.selectionEnd = 1;
    element.text = ':';
    assert.isFalse(!element.$.emojiSuggestions.isHidden);
  });

  test('emoji selector is not open when a general text is entered', () => {
    MockInteractions.focus(element.$.textarea);
    element.$.textarea.selectionStart = 9;
    element.$.textarea.selectionEnd = 9;
    element.text = 'some text';
    assert.isFalse(!element.$.emojiSuggestions.isHidden);
  });

  test('emoji selector opens when a colon is typed & the textarea has focus', () => {
    MockInteractions.focus(element.$.textarea);
    // Needed for Safari tests. selectionStart is not updated when text is
    // updated.
    element.$.textarea.selectionStart = 1;
    element.$.textarea.selectionEnd = 1;
    element.text = ':';
    flush();
    assert.isFalse(element.$.emojiSuggestions.isHidden);
    assert.equal(element._colonIndex, 0);
    assert.isFalse(element._hideEmojiAutocomplete);
    assert.equal(element._currentSearchString, '');
  });

  test('emoji selector opens when a colon is typed after space', () => {
    MockInteractions.focus(element.$.textarea);
    // Needed for Safari tests. selectionStart is not updated when text is
    // updated.
    element.$.textarea.selectionStart = 2;
    element.$.textarea.selectionEnd = 2;
    element.text = ' :';
    flush();
    assert.isFalse(element.$.emojiSuggestions.isHidden);
    assert.equal(element._colonIndex, 1);
    assert.isFalse(element._hideEmojiAutocomplete);
    assert.equal(element._currentSearchString, '');
  });

  test('emoji selector doesn`t open when a colon is typed after character', () => {
    MockInteractions.focus(element.$.textarea);
    // Needed for Safari tests. selectionStart is not updated when text is
    // updated.
    element.$.textarea.selectionStart = 5;
    element.$.textarea.selectionEnd = 5;
    element.text = 'test:';
    flush();
    assert.isTrue(element.$.emojiSuggestions.isHidden);
    assert.isTrue(element._hideEmojiAutocomplete);
  });

  test('emoji selector opens when a colon is typed and some substring', () => {
    MockInteractions.focus(element.$.textarea);
    // Needed for Safari tests. selectionStart is not updated when text is
    // updated.
    element.$.textarea.selectionStart = 1;
    element.$.textarea.selectionEnd = 1;
    element.text = ':';
    element.$.textarea.selectionStart = 2;
    element.$.textarea.selectionEnd = 2;
    element.text = ':t';
    flush();
    assert.isFalse(element.$.emojiSuggestions.isHidden);
    assert.equal(element._colonIndex, 0);
    assert.isFalse(element._hideEmojiAutocomplete);
    assert.equal(element._currentSearchString, 't');
  });

  test('emoji selector opens when a colon is typed in middle of text', () => {
    MockInteractions.focus(element.$.textarea);
    // Needed for Safari tests. selectionStart is not updated when text is
    // updated.
    element.$.textarea.selectionStart = 1;
    element.$.textarea.selectionEnd = 1;
    // Since selectionStart is on Chrome set always on end of text, we
    // stub it to 1
    const text = ': hello';
    sinon.stub(element.$, 'textarea').value({
      selectionStart: 1,
      value: text,
      textarea: {
        focus: () => {},
      },
    });
    element.text = text;
    flush();
    assert.isFalse(element.$.emojiSuggestions.isHidden);
    assert.equal(element._colonIndex, 0);
    assert.isFalse(element._hideEmojiAutocomplete);
    assert.equal(element._currentSearchString, '');
  });
  test('emoji selector closes when text changes before the colon', () => {
    const resetStub = sinon.stub(element, '_resetEmojiDropdown');
    MockInteractions.focus(element.$.textarea);
    flush();
    element.$.textarea.selectionStart = 10;
    element.$.textarea.selectionEnd = 10;
    element.text = 'test test ';
    element.$.textarea.selectionStart = 12;
    element.$.textarea.selectionEnd = 12;
    element.text = 'test test :';
    element.$.textarea.selectionStart = 15;
    element.$.textarea.selectionEnd = 15;
    element.text = 'test test :smi';

    assert.equal(element._currentSearchString, 'smi');
    assert.isFalse(resetStub.called);
    element.text = 'test test test :smi';
    assert.isTrue(resetStub.called);
  });

  test('_resetEmojiDropdown', () => {
    const closeSpy = sinon.spy(element, 'closeDropdown');
    element._resetEmojiDropdown();
    assert.equal(element._currentSearchString, '');
    assert.isTrue(element._hideEmojiAutocomplete);
    assert.equal(element._colonIndex, null);

    element.$.emojiSuggestions.open();
    flush();
    element._resetEmojiDropdown();
    assert.isTrue(closeSpy.called);
  });

  test('_determineSuggestions', () => {
    const emojiText = 'tear';
    const formatSpy = sinon.spy(element, '_formatSuggestions');
    element._determineSuggestions(emojiText);
    assert.isTrue(formatSpy.called);
    assert.isTrue(
      formatSpy.lastCall.calledWithExactly([
        {
          dataValue: '😂',
          value: '😂',
          match: "tears :')",
          text: "😂 tears :')",
        },
        {dataValue: '😢', value: '😢', match: 'tear', text: '😢 tear'},
      ])
    );
  });

  test('_formatSuggestions', () => {
    const matchedSuggestions = [
      {value: '😢', match: 'tear'},
      {value: '😂', match: 'tears'},
    ];
    element._formatSuggestions(matchedSuggestions);
    assert.deepEqual(
      [
        {value: '😢', dataValue: '😢', match: 'tear', text: '😢 tear'},
        {value: '😂', dataValue: '😂', match: 'tears', text: '😂 tears'},
      ],
      element._suggestions
    );
  });

  test('_handleEmojiSelect', () => {
    element.$.textarea.selectionStart = 16;
    element.$.textarea.selectionEnd = 16;
    element.text = 'test test :tears';
    element._colonIndex = 10;
    const selectedItem = {dataset: {value: '😂'}} as unknown as HTMLElement;
    const event = new CustomEvent<ItemSelectedEvent>('item-selected', {
      detail: {trigger: 'click', selected: selectedItem},
    });
    element._handleEmojiSelect(event);
    assert.equal(element.text, 'test test 😂');
  });

  test('_updateCaratPosition', () => {
    element.$.textarea.selectionStart = 4;
    element.$.textarea.selectionEnd = 4;
    element.text = 'test';
    element._updateCaratPosition();
    assert.deepEqual(
      element.$.hiddenText.innerHTML,
      element.text + element.$.caratSpan.outerHTML
    );
  });

  test('newline receives matching indentation', async () => {
    const indentCommand = sinon.stub(document, 'execCommand');
    element.$.textarea.value = '    a';
    element._handleEnterByKey(
      new KeyboardEvent('keydown', {key: 'Enter', keyCode: 13})
    );
    await flush();
    assert.deepEqual(indentCommand.args[0], ['insertText', false, '\n    ']);
  });

  test('emoji dropdown is closed when iron-overlay-closed is fired', () => {
    const resetSpy = sinon.spy(element, '_resetEmojiDropdown');
    element.$.emojiSuggestions.dispatchEvent(
      new CustomEvent('dropdown-closed', {
        composed: true,
        bubbles: true,
      })
    );
    assert.isTrue(resetSpy.called);
  });

  test('_onValueChanged fires bind-value-changed', () => {
    const listenerStub = sinon.stub();
    const eventObject = new CustomEvent('bind-value-changed', {
      detail: {currentTarget: {focused: false}, value: ''},
    });
    element.addEventListener('bind-value-changed', listenerStub);
    element._onValueChanged(eventObject);
    assert.isTrue(listenerStub.called);
  });

  suite('keyboard shortcuts', () => {
    function setupDropdown() {
      MockInteractions.focus(element.$.textarea);
      element.$.textarea.selectionStart = 1;
      element.$.textarea.selectionEnd = 1;
      element.text = ':';
      element.$.textarea.selectionStart = 1;
      element.$.textarea.selectionEnd = 2;
      element.text = ':1';
      flush();
    }

    test('escape key', () => {
      const resetSpy = sinon.spy(element, '_resetEmojiDropdown');
      MockInteractions.pressAndReleaseKeyOn(
        element.$.textarea,
        27,
        null,
        'Escape'
      );
      assert.isFalse(resetSpy.called);
      setupDropdown();
      MockInteractions.pressAndReleaseKeyOn(
        element.$.textarea,
        27,
        null,
        'Escape'
      );
      assert.isTrue(resetSpy.called);
      assert.isFalse(!element.$.emojiSuggestions.isHidden);
    });

    test('up key', () => {
      const upSpy = sinon.spy(element.$.emojiSuggestions, 'cursorUp');
      MockInteractions.pressAndReleaseKeyOn(
        element.$.textarea,
        38,
        null,
        'ArrowUp'
      );
      assert.isFalse(upSpy.called);
      setupDropdown();
      MockInteractions.pressAndReleaseKeyOn(
        element.$.textarea,
        38,
        null,
        'ArrowUp'
      );
      assert.isTrue(upSpy.called);
    });

    test('down key', () => {
      const downSpy = sinon.spy(element.$.emojiSuggestions, 'cursorDown');
      MockInteractions.pressAndReleaseKeyOn(
        element.$.textarea,
        40,
        null,
        'ArrowDown'
      );
      assert.isFalse(downSpy.called);
      setupDropdown();
      MockInteractions.pressAndReleaseKeyOn(
        element.$.textarea,
        40,
        null,
        'ArrowDown'
      );
      assert.isTrue(downSpy.called);
    });

    test('enter key', () => {
      const enterSpy = sinon.spy(element.$.emojiSuggestions, 'getCursorTarget');
      MockInteractions.pressAndReleaseKeyOn(
        element.$.textarea,
        13,
        null,
        'Enter'
      );
      assert.isFalse(enterSpy.called);
      setupDropdown();
      MockInteractions.pressAndReleaseKeyOn(
        element.$.textarea,
        13,
        null,
        'Enter'
      );
      assert.isTrue(enterSpy.called);
      flush();
      assert.equal(element.text, '💯');
    });

    test('enter key - ignored on just colon without more information', () => {
      const enterSpy = sinon.spy(element.$.emojiSuggestions, 'getCursorTarget');
      MockInteractions.pressAndReleaseKeyOn(element.$.textarea, 13);
      assert.isFalse(enterSpy.called);
      MockInteractions.focus(element.$.textarea);
      element.$.textarea.selectionStart = 1;
      element.$.textarea.selectionEnd = 1;
      element.text = ':';
      flush();
      MockInteractions.pressAndReleaseKeyOn(element.$.textarea, 13);
      assert.isFalse(enterSpy.called);
    });
  });

  suite('gr-textarea monospace', () => {
    // gr-textarea set monospace class in the ready() method.
    // In Polymer2, ready() is called from the fixture(...) method,
    // If ready() is called again later, some nested elements doesn't
    // handle it correctly. A separate test-fixture is used to set
    // properties before ready() is called.

    let element: GrTextarea;

    setup(() => {
      element = monospaceFixture.instantiate() as GrTextarea;
    });

    test('monospace is set properly', () => {
      assert.isTrue(element.classList.contains('monospace'));
    });
  });

  suite('gr-textarea hideBorder', () => {
    // gr-textarea set noBorder class in the ready() method.
    // In Polymer2, ready() is called from the fixture(...) method,
    // If ready() is called again later, some nested elements doesn't
    // handle it correctly. A separate test-fixture is used to set
    // properties before ready() is called.

    let element: GrTextarea;

    setup(() => {
      element = hideBorderFixture.instantiate() as GrTextarea;
    });

    test('hideBorder is set properly', () => {
      assert.isTrue(element.$.textarea.classList.contains('noBorder'));
    });
  });
});
