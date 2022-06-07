/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup-karma';
import './gr-textarea';
import {GrTextarea} from './gr-textarea';
import * as MockInteractions from '@polymer/iron-test-helpers/mock-interactions';
import {ItemSelectedEvent} from '../gr-autocomplete-dropdown/gr-autocomplete-dropdown';
import {waitUntil} from '../../../test/test-utils';
import {fixture, html} from '@open-wc/testing-helpers';

suite('gr-textarea tests', () => {
  let element: GrTextarea;

  setup(async () => {
    element = await fixture<GrTextarea>(html`<gr-textarea></gr-textarea>`);
    sinon.stub(element.reporting, 'reportInteraction');
    await element.updateComplete;
  });

  test('renders', () => {
    expect(element).shadowDom.to.equal(/* HTML */ `<div id="hiddenText"></div>
      <span id="caratSpan"> </span>
      <gr-autocomplete-dropdown
        id="emojiSuggestions"
        is-hidden=""
        style="position: fixed; top: 150px; left: 392.5px; box-sizing: border-box; max-height: 300px; max-width: 785px;"
      >
      </gr-autocomplete-dropdown>
      <iron-autogrow-textarea aria-disabled="false" id="textarea">
      </iron-autogrow-textarea> `);
  });

  test('monospace is set properly', () => {
    assert.isFalse(element.classList.contains('monospace'));
  });

  test('hideBorder is set properly', () => {
    assert.isFalse(element.textarea!.classList.contains('noBorder'));
  });

  test('emoji selector is not open with the textarea lacks focus', async () => {
    element.textarea!.selectionStart = 1;
    element.textarea!.selectionEnd = 1;
    element.text = ':';
    await element.updateComplete;
    assert.isFalse(!element.emojiSuggestions!.isHidden);
  });

  test('emoji selector is not open when a general text is entered', async () => {
    MockInteractions.focus(element.textarea!);
    await waitUntil(() => element.textarea!.focused === true);
    element.textarea!.selectionStart = 9;
    element.textarea!.selectionEnd = 9;
    element.text = 'some text';
    await element.updateComplete;
    assert.isFalse(!element.emojiSuggestions!.isHidden);
  });

  test('emoji selector is open when a colon is typed & the textarea has focus', async () => {
    // Needed for Safari tests. selectionStart is not updated when text is
    // updated.
    const listenerStub = sinon.stub();
    element.addEventListener('bind-value-changed', listenerStub);
    MockInteractions.focus(element.textarea!);
    await waitUntil(() => element.textarea!.focused === true);
    element.textarea!.selectionStart = 1;
    element.textarea!.selectionEnd = 1;
    element.text = ':';
    await element.updateComplete;
    assert.equal(listenerStub.lastCall.args[0].detail.value, ':');
    assert.isTrue(element.textarea!.focused);
    assert.isFalse(element.emojiSuggestions!.isHidden);
    assert.equal(element.colonIndex, 0);
    assert.isFalse(element.hideEmojiAutocomplete);
    assert.equal(element.currentSearchString, '');
  });

  test('emoji selector opens when a colon is typed after space', async () => {
    MockInteractions.focus(element.textarea!);
    await waitUntil(() => element.textarea!.focused === true);
    // Needed for Safari tests. selectionStart is not updated when text is
    // updated.
    element.textarea!.selectionStart = 2;
    element.textarea!.selectionEnd = 2;
    element.text = ' :';
    await element.updateComplete;
    assert.isFalse(element.emojiSuggestions!.isHidden);
    assert.equal(element.colonIndex, 1);
    assert.isFalse(element.hideEmojiAutocomplete);
    assert.equal(element.currentSearchString, '');
  });

  test('emoji selector doesn`t open when a colon is typed after character', async () => {
    MockInteractions.focus(element.textarea!);
    await waitUntil(() => element.textarea!.focused === true);
    // Needed for Safari tests. selectionStart is not updated when text is
    // updated.
    element.textarea!.selectionStart = 5;
    element.textarea!.selectionEnd = 5;
    element.text = 'test:';
    await element.updateComplete;
    assert.isTrue(element.emojiSuggestions!.isHidden);
    assert.isTrue(element.hideEmojiAutocomplete);
  });

  test('emoji selector opens when a colon is typed and some substring', async () => {
    MockInteractions.focus(element.textarea!);
    await waitUntil(() => element.textarea!.focused === true);
    // Needed for Safari tests. selectionStart is not updated when text is
    // updated.
    element.textarea!.selectionStart = 1;
    element.textarea!.selectionEnd = 1;
    element.text = ':';
    await element.updateComplete;
    element.textarea!.selectionStart = 2;
    element.textarea!.selectionEnd = 2;
    element.text = ':t';
    await element.updateComplete;
    assert.isFalse(element.emojiSuggestions!.isHidden);
    assert.equal(element.colonIndex, 0);
    assert.isFalse(element.hideEmojiAutocomplete);
    assert.equal(element.currentSearchString, 't');
  });

  test('emoji selector opens when a colon is typed in middle of text', async () => {
    MockInteractions.focus(element.textarea!);
    // Needed for Safari tests. selectionStart is not updated when text is
    // updated.
    element.textarea!.selectionStart = 1;
    element.textarea!.selectionEnd = 1;
    // Since selectionStart is on Chrome set always on end of text, we
    // stub it to 1
    const text = ': hello';
    sinon.stub(element, 'textarea').value({
      selectionStart: 1,
      value: text,
      textarea: {
        focus: () => {},
      },
    });
    element.text = text;
    await element.updateComplete;
    assert.isFalse(element.emojiSuggestions!.isHidden);
    assert.equal(element.colonIndex, 0);
    assert.isFalse(element.hideEmojiAutocomplete);
    assert.equal(element.currentSearchString, '');
  });

  test('emoji selector closes when text changes before the colon', async () => {
    const resetStub = sinon.stub(element, 'resetEmojiDropdown');
    MockInteractions.focus(element.textarea!);
    await waitUntil(() => element.textarea!.focused === true);
    await element.updateComplete;
    element.textarea!.selectionStart = 10;
    element.textarea!.selectionEnd = 10;
    element.text = 'test test ';
    await element.updateComplete;
    element.textarea!.selectionStart = 12;
    element.textarea!.selectionEnd = 12;
    element.text = 'test test :';
    await element.updateComplete;
    element.textarea!.selectionStart = 15;
    element.textarea!.selectionEnd = 15;
    element.text = 'test test :smi';
    await element.updateComplete;

    assert.equal(element.currentSearchString, 'smi');
    assert.isFalse(resetStub.called);
    element.text = 'test test test :smi';
    await element.updateComplete;
    assert.isTrue(resetStub.called);
  });

  test('resetEmojiDropdown', async () => {
    const closeSpy = sinon.spy(element, 'closeDropdown');
    element.resetEmojiDropdown();
    assert.equal(element.currentSearchString, '');
    assert.isTrue(element.hideEmojiAutocomplete);
    assert.equal(element.colonIndex, null);

    element.emojiSuggestions!.open();
    await element.updateComplete;
    element.resetEmojiDropdown();
    assert.isTrue(closeSpy.called);
  });

  test('determineSuggestions', () => {
    const emojiText = 'tear';
    const formatSpy = sinon.spy(element, 'formatSuggestions');
    element.determineSuggestions(emojiText);
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

  test('formatSuggestions', () => {
    const matchedSuggestions = [
      {value: '😢', match: 'tear'},
      {value: '😂', match: 'tears'},
    ];
    element.formatSuggestions(matchedSuggestions);
    assert.deepEqual(
      [
        {value: '😢', dataValue: '😢', match: 'tear', text: '😢 tear'},
        {value: '😂', dataValue: '😂', match: 'tears', text: '😂 tears'},
      ],
      element.suggestions
    );
  });

  test('handleEmojiSelect', async () => {
    element.textarea!.selectionStart = 16;
    element.textarea!.selectionEnd = 16;
    element.text = 'test test :tears';
    element.colonIndex = 10;
    await element.updateComplete;
    const selectedItem = {dataset: {value: '😂'}} as unknown as HTMLElement;
    const event = new CustomEvent<ItemSelectedEvent>('item-selected', {
      detail: {trigger: 'click', selected: selectedItem},
    });
    element.handleEmojiSelect(event);
    assert.equal(element.text, 'test test 😂');
  });

  test('updateCaratPosition', async () => {
    element.textarea!.selectionStart = 4;
    element.textarea!.selectionEnd = 4;
    element.text = 'test';
    await element.updateComplete;
    element.updateCaratPosition();
    assert.deepEqual(
      element.hiddenText!.innerHTML,
      element.text + element.caratSpan!.outerHTML
    );
  });

  test('newline receives matching indentation', async () => {
    const indentCommand = sinon.stub(document, 'execCommand');
    element.textarea!.value = '    a';
    element.handleEnterByKey(
      new KeyboardEvent('keydown', {key: 'Enter', keyCode: 13})
    );
    await element.updateComplete;
    assert.deepEqual(indentCommand.args[0], ['insertText', false, '\n    ']);
  });

  test('emoji dropdown is closed when iron-overlay-closed is fired', async () => {
    const resetSpy = sinon.spy(element, 'closeDropdown');
    element.emojiSuggestions!.dispatchEvent(
      new CustomEvent('dropdown-closed', {
        composed: true,
        bubbles: true,
      })
    );
    await element.updateComplete;
    assert.isTrue(resetSpy.called);
  });

  test('onValueChanged fires bind-value-changed', () => {
    const listenerStub = sinon.stub();
    const eventObject = new CustomEvent('bind-value-changed', {
      detail: {currentTarget: {focused: false}, value: ''},
    });
    element.addEventListener('bind-value-changed', listenerStub);
    element.onValueChanged(eventObject);
    assert.isTrue(listenerStub.called);
  });

  suite('keyboard shortcuts', async () => {
    async function setupDropdown() {
      MockInteractions.focus(element.textarea!);
      element.textarea!.selectionStart = 1;
      element.textarea!.selectionEnd = 1;
      element.text = ':';
      await element.updateComplete;
      element.textarea!.selectionStart = 1;
      element.textarea!.selectionEnd = 2;
      element.text = ':1';
      await element.updateComplete;
    }

    test('escape key', async () => {
      const resetSpy = sinon.spy(element, 'resetEmojiDropdown');
      MockInteractions.pressAndReleaseKeyOn(
        element.textarea!,
        27,
        null,
        'Escape'
      );
      assert.isFalse(resetSpy.called);
      await setupDropdown();
      MockInteractions.pressAndReleaseKeyOn(
        element.textarea!,
        27,
        null,
        'Escape'
      );
      assert.isTrue(resetSpy.called);
      assert.isFalse(!element.emojiSuggestions!.isHidden);
    });

    test('up key', async () => {
      const upSpy = sinon.spy(element.emojiSuggestions!, 'cursorUp');
      MockInteractions.pressAndReleaseKeyOn(
        element.textarea!,
        38,
        null,
        'ArrowUp'
      );
      assert.isFalse(upSpy.called);
      await setupDropdown();
      MockInteractions.pressAndReleaseKeyOn(
        element.textarea!,
        38,
        null,
        'ArrowUp'
      );
      assert.isTrue(upSpy.called);
    });

    test('down key', async () => {
      const downSpy = sinon.spy(element.emojiSuggestions!, 'cursorDown');
      MockInteractions.pressAndReleaseKeyOn(
        element.textarea!,
        40,
        null,
        'ArrowDown'
      );
      assert.isFalse(downSpy.called);
      await setupDropdown();
      MockInteractions.pressAndReleaseKeyOn(
        element.textarea!,
        40,
        null,
        'ArrowDown'
      );
      assert.isTrue(downSpy.called);
    });

    test('enter key', async () => {
      const enterSpy = sinon.spy(element.emojiSuggestions!, 'getCursorTarget');
      MockInteractions.pressAndReleaseKeyOn(
        element.textarea!,
        13,
        null,
        'Enter'
      );
      assert.isFalse(enterSpy.called);
      await setupDropdown();
      MockInteractions.pressAndReleaseKeyOn(
        element.textarea!,
        13,
        null,
        'Enter'
      );
      assert.isTrue(enterSpy.called);
      await element.updateComplete;
      assert.equal(element.text, '💯');
    });

    test('enter key - ignored on just colon without more information', async () => {
      const enterSpy = sinon.spy(element.emojiSuggestions!, 'getCursorTarget');
      MockInteractions.pressAndReleaseKeyOn(element.textarea!, 13);
      assert.isFalse(enterSpy.called);
      MockInteractions.focus(element.textarea!);
      element.textarea!.selectionStart = 1;
      element.textarea!.selectionEnd = 1;
      element.text = ':';
      await element.updateComplete;
      MockInteractions.pressAndReleaseKeyOn(element.textarea!, 13);
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

    setup(async () => {
      element = await fixture<GrTextarea>(
        html`<gr-textarea monospace></gr-textarea>`
      );
      await element.updateComplete;
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

    setup(async () => {
      element = await fixture<GrTextarea>(
        html`<gr-textarea hide-border></gr-textarea>`
      );
      await element.updateComplete;
    });

    test('hideBorder is set properly', () => {
      assert.isTrue(element.textarea!.classList.contains('noBorder'));
    });
  });
});
