/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import * as sinon from 'sinon';
import '../../../test/common-test-setup';
import './gr-suggestion-textarea';
import {EmojiSuggestion, GrSuggestionTextarea} from './gr-suggestion-textarea';
import {
  Item,
  ItemSelectedEventDetail,
} from '../gr-autocomplete-dropdown/gr-autocomplete-dropdown';
import {
  mockPromise,
  pressKey,
  stubRestApi,
  waitUntil,
} from '../../../test/test-utils';
import {fixture, html, assert} from '@open-wc/testing';
import {createAccountWithEmail} from '../../../test/test-data-generators';
import {Key} from '../../../utils/dom-util';

suite('gr-suggestion-textarea tests with <gr-textarea>', () => {
  let element: GrSuggestionTextarea;

  const setText = async (text: string) => {
    element.text = text;
    await element.updateComplete;
    await element.textarea!.updateComplete;
    element.setCursorPosition(text.length);
    element.handleTextChanged();
    await element.updateComplete;
  };

  setup(async () => {
    element = await fixture<GrSuggestionTextarea>(
      html`<gr-suggestion-textarea></gr-suggestion-textarea>`
    );
    sinon.stub(element.reporting, 'reportInteraction');
    // We can't fetch emojis.js here, due to it being out of the rootDir.
    // Instead we hardcode some.
    const ALL_SUGGESTIONS: EmojiSuggestion[] = [
      {value: '😊', match: 'smile :)'},
      {value: '👍', match: 'thumbs up'},
      {value: '😄', match: 'laugh :D'},
      {value: '❤️', match: 'heart <3'},
      {value: '😂', match: "tears :')"},
      {value: '🎉', match: 'party'},
      {value: '😎', match: 'cool |;)'},
      {value: '😞', match: 'sad :('},
      {value: '😐', match: 'neutral :|'},
      {value: '😮', match: 'shock :O'},
      {value: '🙏', match: 'pray'},
      {value: '😕', match: 'confused'},
      {value: '👌', match: 'ok'},
      {value: '🔥', match: 'fire'},
      {value: '💯', match: '100'},
      {value: '✔', match: 'check'},
      {value: '😋', match: 'tongue'},
      {value: '😭', match: "crying :'("},
      {value: '🤓', match: 'glasses'},
      {value: '😢', match: 'tear'},
      {value: '😜', match: 'winking tongue ;)'},
    ];
    element.emojis = ALL_SUGGESTIONS;
    await element.updateComplete;
  });

  test('renders', () => {
    assert.shadowDom.equal(
      element,
      /* HTML */ `<div id="hiddenText"></div>
        <span id="caratSpan"> </span>
        <gr-autocomplete-dropdown id="emojiSuggestions" is-hidden="">
        </gr-autocomplete-dropdown>
        <gr-autocomplete-dropdown
          id="mentionsSuggestions"
          is-hidden=""
          role="listbox"
        >
        </gr-autocomplete-dropdown>
        <gr-textarea putcursoratendonfocus id="textarea"> </gr-textarea>`,
      {
        // gr-autocomplete-dropdown sizing seems to vary between local & CI
        ignoreAttributes: [
          {tags: ['gr-autocomplete-dropdown'], attributes: ['style']},
        ],
      }
    );
  });

  suite('mention users', () => {
    test('mentions selector is open when @ is typed & the textarea has focus', async () => {
      // Needed for Safari tests. selectionStart is not updated when text is
      // updated.
      const listenerStub = sinon.stub();
      element.addEventListener('text-changed', listenerStub);
      stubRestApi('queryAccounts').returns(
        Promise.resolve([
          createAccountWithEmail('abc@google.com'),
          createAccountWithEmail('abcdef@google.com'),
        ])
      );
      element.textarea!.focus();
      await waitUntil(() => element.isTextareaFocused() === true);
      await setText('@');

      await waitUntil(() => element.suggestions.length > 0);
      await element.updateComplete;

      assert.equal(listenerStub.lastCall.args[0].detail.value, '@');
      assert.isTrue(element.isTextareaFocused());

      assert.isTrue(element.emojiSuggestions!.isHidden);
      assert.isFalse(element.mentionsSuggestions!.isHidden);

      assert.equal(element.specialCharIndex, 0);
      assert.isFalse(element.mentionsSuggestions!.isHidden);
      assert.equal(element.currentSearchString, '');

      await setText('@abc@google.com');

      assert.equal(element.currentSearchString, 'abc@google.com');
      assert.equal(element.specialCharIndex, 0);
    });

    test('mention selector opens when previous char is \n', async () => {
      stubRestApi('queryAccounts').returns(
        Promise.resolve([
          {
            ...createAccountWithEmail('abc@google.com'),
            name: 'A',
            display_name: 'display A',
          },
          {...createAccountWithEmail('abcdef@google.com'), name: 'B'},
        ])
      );
      element.textarea!.focus();
      await waitUntil(() => element.isTextareaFocused() === true);

      await setText('\n@');

      await waitUntil(() => element.suggestions.length > 0);
      await element.updateComplete;

      assert.deepEqual(element.suggestions, [
        {
          dataValue: 'abc@google.com',
          text: 'display A <abc@google.com>',
        },
        {
          dataValue: 'abcdef@google.com',
          text: 'B <abcdef@google.com>',
        },
      ]);

      assert.isTrue(element.emojiSuggestions!.isHidden);
      assert.isFalse(element.mentionsSuggestions!.isHidden);
    });

    test('mention suggestions cleared before request returns', async () => {
      const promise = mockPromise<Item[]>();
      stubRestApi('queryAccounts').returns(promise);
      element.textarea!.focus();
      await waitUntil(() => element.isTextareaFocused() === true);

      element.suggestions = [
        {dataValue: 'prior@google.com', text: 'Prior suggestion'},
      ];
      await setText('@');

      await element.updateComplete;
      assert.equal(element.suggestions.length, 0);

      promise.resolve([
        createAccountWithEmail('abc@google.com'),
        createAccountWithEmail('abcdef@google.com'),
      ]);
      await waitUntil(() => element.suggestions.length !== 0);
      assert.deepEqual(element.suggestions, [
        {
          dataValue: 'abc@google.com',
          text: 'abc@google.com <abc@google.com>',
        },
        {
          dataValue: 'abcdef@google.com',
          text: 'abcdef@google.com <abcdef@google.com>',
        },
      ]);
    });

    test('mention dropdown shows suggestion for latest text', async () => {
      const promise1 = mockPromise<Item[]>();
      const promise2 = mockPromise<Item[]>();
      const suggestionStub = stubRestApi('queryAccounts');
      suggestionStub.returns(promise1);
      element.textarea!.focus();
      await waitUntil(() => element.isTextareaFocused() === true);

      await setText('@');
      assert.equal(element.currentSearchString, '');

      suggestionStub.returns(promise2);
      await setText('@abc@google.com');
      // None of suggestions returned yet.
      assert.equal(element.suggestions.length, 0);
      await element.updateComplete;
      assert.equal(element.currentSearchString, 'abc@google.com');

      promise2.resolve([
        createAccountWithEmail('abc@google.com'),
        createAccountWithEmail('abcdef@google.com'),
      ]);

      await waitUntil(() => element.suggestions.length !== 0);
      assert.deepEqual(element.suggestions, [
        {
          dataValue: 'abc@google.com',
          text: 'abc@google.com <abc@google.com>',
        },
        {
          dataValue: 'abcdef@google.com',
          text: 'abcdef@google.com <abcdef@google.com>',
        },
      ]);

      promise1.resolve([
        createAccountWithEmail('dce@google.com'),
        createAccountWithEmail('defcba@google.com'),
      ]);
      // Empty the event queue.
      await new Promise<void>(resolve => {
        setTimeout(() => resolve());
      });
      // Suggestions didn't change
      assert.deepEqual(element.suggestions, [
        {
          dataValue: 'abc@google.com',
          text: 'abc@google.com <abc@google.com>',
        },
        {
          dataValue: 'abcdef@google.com',
          text: 'abcdef@google.com <abcdef@google.com>',
        },
      ]);
    });

    test('selecting mentions from dropdown', async () => {
      stubRestApi('queryAccounts').returns(
        Promise.resolve([
          createAccountWithEmail('abc@google.com'),
          createAccountWithEmail('abcdef@google.com'),
        ])
      );

      element.textarea!.focus();
      await waitUntil(() => element.isTextareaFocused() === true);

      await setText('@');

      await waitUntil(() => element.suggestions.length > 0);
      await element.updateComplete;

      pressKey(element, 'ArrowDown');
      await element.updateComplete;

      pressKey(element, 'ArrowDown');
      await element.updateComplete;

      pressKey(element, Key.ENTER);
      await element.updateComplete;

      assert.equal(element.text, '@abcdef@google.com');
    });

    test('emoji dropdown does not open if mention dropdown is open', async () => {
      const listenerStub = sinon.stub();
      element.addEventListener('text-changed', listenerStub);
      stubRestApi('queryAccounts').returns(
        Promise.resolve([
          createAccountWithEmail('abc@google.com'),
          createAccountWithEmail('abcdef@google.com'),
        ])
      );
      element.textarea!.focus();
      await waitUntil(() => element.isTextareaFocused() === true);

      await setText('@');
      element.suggestions = [
        {
          name: 'a',
          value: 'a',
        },
      ];
      await waitUntil(() => element.suggestions.length > 0);
      await element.updateComplete;

      assert.isTrue(element.emojiSuggestions!.isHidden);
      assert.isFalse(element.mentionsSuggestions!.isHidden);

      await setText('@h');
      await waitUntil(() => element.suggestions.length > 0);
      await element.updateComplete;
      assert.isTrue(element.emojiSuggestions!.isHidden);
      assert.isFalse(element.mentionsSuggestions!.isHidden);

      await setText('@h');
      await waitUntil(() => element.suggestions.length > 0);
      await element.updateComplete;
      assert.isTrue(element.emojiSuggestions!.isHidden);
      assert.isFalse(element.mentionsSuggestions!.isHidden);

      await setText('@h:');
      await waitUntil(() => element.suggestions.length > 0);
      await element.updateComplete;
      assert.isTrue(element.emojiSuggestions!.isHidden);
      assert.isFalse(element.mentionsSuggestions!.isHidden);

      await setText('@h:D');
      await waitUntil(() => element.suggestions.length > 0);
      await element.updateComplete;
      assert.isTrue(element.emojiSuggestions!.isHidden);
      assert.isFalse(element.mentionsSuggestions!.isHidden);
    });

    test('mention dropdown does not open if emoji dropdown is open', async () => {
      const listenerStub = sinon.stub();
      element.addEventListener('text-changed', listenerStub);
      element.textarea!.focus();
      await waitUntil(() => element.isTextareaFocused() === true);

      await setText(':');
      element.suggestions = [
        {
          name: 'a',
          value: 'a',
        },
      ];

      await element.updateComplete;
      assert.isFalse(element.emojiSuggestions!.isHidden);
      assert.isTrue(element.mentionsSuggestions!.isHidden);

      await setText(':D');
      await element.updateComplete;
      assert.isFalse(element.emojiSuggestions!.isHidden);
      assert.isTrue(element.mentionsSuggestions!.isHidden);

      await setText(':D@');
      await element.updateComplete;
      // emoji dropdown hidden since we have no more suggestions
      assert.isFalse(element.emojiSuggestions!.isHidden);
      assert.isTrue(element.mentionsSuggestions!.isHidden);

      await setText(':D@b');
      await element.updateComplete;
      assert.isFalse(element.emojiSuggestions!.isHidden);
      assert.isTrue(element.mentionsSuggestions!.isHidden);

      await setText(':D@b ');
      await element.updateComplete;
      assert.isTrue(element.emojiSuggestions!.isHidden);
      assert.isTrue(element.mentionsSuggestions!.isHidden);
    });

    test('mention dropdown is cleared if @ is deleted', async () => {
      stubRestApi('queryAccounts').returns(
        Promise.resolve([
          createAccountWithEmail('abc@google.com'),
          createAccountWithEmail('abcdef@google.com'),
        ])
      );

      element.textarea!.focus();
      await waitUntil(() => element.isTextareaFocused() === true);

      await setText('@');

      await waitUntil(() => element.suggestions.length > 0);
      await element.updateComplete;

      assert.isFalse(element.mentionsSuggestions!.isHidden);

      element.text = '';
      await element.updateComplete;
      assert.isTrue(element.mentionsSuggestions!.isHidden);
    });
  });

  test('monospace is set properly', () => {
    assert.isFalse(element.classList.contains('monospace'));
  });

  test('hideBorder is set properly', () => {
    assert.isFalse(element.textarea!.classList.contains('noBorder'));
  });

  test('emoji selector is not open when the textarea lacks focus', async () => {
    // by default textarea has focus when rendered
    // explicitly remove focus from the element for the test
    element.blur();
    element.setCursorPosition(1);
    element.text = ':';
    await element.updateComplete;
    assert.isTrue(element.emojiSuggestions!.isHidden);
  });

  test('emoji selector is not open when a general text is entered', async () => {
    element.textarea!.focus();
    await waitUntil(() => element.isTextareaFocused() === true);
    element.setCursorPosition(9);
    element.text = 'some text';
    await element.updateComplete;
    assert.isTrue(element.emojiSuggestions!.isHidden);
  });

  test('emoji selector is open when a colon is typed & the textarea has focus', async () => {
    // Needed for Safari tests. selectionStart is not updated when text is
    // updated.
    const listenerStub = sinon.stub();
    element.addEventListener('text-changed', listenerStub);
    element.textarea!.focus();
    await waitUntil(() => element.isTextareaFocused() === true);
    await setText(':');
    assert.equal(listenerStub.lastCall.args[0].detail.value, ':');
    assert.isTrue(element.isTextareaFocused());
    await element.updateComplete;
    await element.textarea!.updateComplete;
    await element.emojiSuggestions!.updateComplete;
    assert.isFalse(element.emojiSuggestions!.isHidden);
    assert.equal(element.specialCharIndex, 0);
    assert.isTrue(!element.emojiSuggestions!.isHidden);
    assert.equal(element.currentSearchString, '');
  });

  test('emoji selector opens when a colon is typed after space', async () => {
    element.textarea!.focus();
    await waitUntil(() => element.isTextareaFocused() === true);
    await setText(' :');
    assert.isFalse(element.emojiSuggestions!.isHidden);
    assert.equal(element.specialCharIndex, 1);
    assert.isTrue(!element.emojiSuggestions!.isHidden);
    assert.equal(element.currentSearchString, '');
  });

  test('emoji selector doesn`t open when a colon is typed after character', async () => {
    element.textarea!.focus();
    await waitUntil(() => element.isTextareaFocused() === true);
    await setText('test:');
    assert.isTrue(element.emojiSuggestions!.isHidden);
    assert.isTrue(element.emojiSuggestions!.isHidden);
  });

  test('emoji selector opens when a colon is typed and some substring', async () => {
    element.textarea!.focus();
    await waitUntil(() => element.isTextareaFocused() === true);
    await setText(':');
    await setText(':t');
    assert.isFalse(element.emojiSuggestions!.isHidden);
    assert.equal(element.specialCharIndex, 0);
    assert.isTrue(!element.emojiSuggestions!.isHidden);
    assert.equal(element.currentSearchString, 't');
  });

  test('emoji selector opens when a colon is typed in middle of text', async () => {
    element.textarea!.focus();
    // Needed for Safari tests. selectionStart is not updated when text is
    // updated.
    element.setCursorPosition(1);
    // Since selectionStart is on Chrome set always on end of text, we
    // stub it to 1
    const text = ': hello';
    sinon.stub(element.textarea!, 'getCursorPosition').returns(1);
    element.text = text;
    await element.updateComplete;
    assert.isFalse(element.emojiSuggestions!.isHidden);
    assert.equal(element.specialCharIndex, 0);
    assert.isTrue(!element.emojiSuggestions!.isHidden);
    assert.equal(element.currentSearchString, '');
  });

  test('emoji selector closes when text changes before the colon', async () => {
    element.textarea!.focus();
    await waitUntil(() => element.isTextareaFocused() === true);
    await setText('test test ');
    await setText('test test :');

    // typing : opens the selector
    assert.isFalse(element.emojiSuggestions!.isHidden);

    await setText('test test :smi');

    assert.equal(element.currentSearchString, 'smi');
    assert.isFalse(element.emojiSuggestions!.isHidden);

    element.text = 'test test test :smi';
    await element.updateComplete;

    assert.isTrue(element.emojiSuggestions!.isHidden);
  });

  test('resetDropdown', async () => {
    const closeSpy = sinon.spy(element, 'closeDropdown');
    element.resetDropdown();
    assert.equal(element.currentSearchString, '');
    assert.isTrue(element.emojiSuggestions!.isHidden);
    assert.equal(element.specialCharIndex, -1);

    element.emojiSuggestions!.open();
    await element.updateComplete;
    element.resetDropdown();
    assert.isTrue(closeSpy.called);
  });

  test('determineEmojiSuggestions', () => {
    const emojiText = 'tear';
    const formatSpy = sinon.spy(element, 'formatSuggestions');
    element.computeEmojiSuggestions(emojiText);
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
    const suggestions = element.formatSuggestions(matchedSuggestions);
    assert.deepEqual(
      [
        {value: '😢', dataValue: '😢', match: 'tear', text: '😢 tear'},
        {value: '😂', dataValue: '😂', match: 'tears', text: '😂 tears'},
      ],
      suggestions
    );
  });

  test('handleDropdownItemSelect', async () => {
    element.text = 'test test :tears';
    await element.updateComplete;
    await element.textarea!.updateComplete;
    element.setCursorPosition(16);
    element.specialCharIndex = 10;
    element.handleTextChanged();
    await element.updateComplete;
    const selectedItem = {dataset: {value: '😂'}} as unknown as HTMLElement;
    const event = new CustomEvent<ItemSelectedEventDetail>('item-selected', {
      detail: {trigger: 'click', selected: selectedItem},
    });
    element.handleDropdownItemSelect(event);
    assert.equal(element.text, 'test test 😂');

    // wait for reset dropdown to finish
    await waitUntil(() => element.specialCharIndex === -1);
    element.text = 'test test :tears';
    await element.updateComplete;
    await element.textarea!.updateComplete;
    element.setCursorPosition(16);
    await element.updateComplete;
    element.specialCharIndex = 10;
    element.handleTextChanged();
    // move the cursor to the left while the suggestion popup is open
    element.setCursorPosition(0);
    element.handleDropdownItemSelect(event);
    assert.equal(element.text, 'test test 😂');

    // wait for reset dropdown to finish
    await waitUntil(() => element.specialCharIndex === -1);
    element.setCursorPosition(16);
    const text = 'test test :tears happy';
    // Since selectionStart is on Chrome set always on end of text, we
    // stub it to 16
    const stub = sinon.stub(element.textarea!, 'getCursorPosition').returns(16);
    element.text = text;
    element.specialCharIndex = 10;
    await element.updateComplete;
    stub.restore();
    // move the cursor to the right while the suggestion popup is open
    element.setCursorPosition(22);
    element.handleDropdownItemSelect(event);
    assert.equal(element.text, 'test test 😂 happy');
  });

  test('updateCaratPosition', async () => {
    await setText('test');
    element.updateCaratPosition();
    assert.deepEqual(
      element.hiddenText!.innerHTML,
      element.text + element.caratSpan!.outerHTML
    );
  });

  test('newline receives matching indentation', async () => {
    const indentCommand = sinon.stub(document, 'execCommand');
    await setText('    a');
    element.handleEnterByKey(new KeyboardEvent('keydown', {key: 'Enter'}));
    await element.updateComplete;
    assert.deepEqual(indentCommand.args[0], ['insertText', false, '\n    ']);
  });

  test('emoji dropdown is closed when dropdown-closed is fired', async () => {
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

  suite('keyboard shortcuts', async () => {
    test('escape key', async () => {
      const resetSpy = sinon.spy(element, 'resetDropdown');
      pressKey(element.textarea! as HTMLElement, Key.ESC);
      assert.isFalse(resetSpy.called);
      await setText(':1');
      pressKey(element.textarea! as HTMLElement, Key.ESC);
      assert.isTrue(resetSpy.called);
      assert.isTrue(element.emojiSuggestions!.isHidden);
    });

    test('up key', async () => {
      const upSpy = sinon.spy(element.emojiSuggestions!, 'cursorUp');
      pressKey(element.textarea! as HTMLElement, 'ArrowUp');
      assert.isFalse(upSpy.called);
      await setText(':1');
      pressKey(element.textarea! as HTMLElement, 'ArrowUp');
      assert.isTrue(upSpy.called);
    });

    test('down key', async () => {
      const downSpy = sinon.spy(element.emojiSuggestions!, 'cursorDown');
      pressKey(element.textarea! as HTMLElement, 'ArrowDown');
      assert.isFalse(downSpy.called);
      await setText(':1');
      pressKey(element.textarea! as HTMLElement, 'ArrowDown');
      assert.isTrue(downSpy.called);
    });

    test('enter key', async () => {
      const enterSpy = sinon.spy(element.emojiSuggestions!, 'getCursorTarget');
      pressKey(element.textarea! as HTMLElement, Key.ENTER);
      assert.isFalse(enterSpy.called);
      await setText(':1');
      pressKey(element.textarea! as HTMLElement, Key.ENTER);
      assert.isTrue(enterSpy.called);
      await element.updateComplete;
      assert.equal(element.text, '💯');
    });
  });

  suite('gr-suggestion-textarea monospace', () => {
    let element: GrSuggestionTextarea;

    setup(async () => {
      element = await fixture<GrSuggestionTextarea>(
        html`<gr-suggestion-textarea monospace></gr-suggestion-textarea>`
      );
      await element.updateComplete;
    });

    test('monospace is set properly', () => {
      assert.isTrue(element.classList.contains('monospace'));
    });
  });

  suite('gr-suggestion-textarea hideBorder', () => {
    let element: GrSuggestionTextarea;

    setup(async () => {
      element = await fixture<GrSuggestionTextarea>(
        html`<gr-suggestion-textarea hide-border></gr-suggestion-textarea>`
      );
      await element.updateComplete;
    });

    test('hideBorder is set properly', () => {
      assert.isTrue(element.textarea!.classList.contains('noBorder'));
    });
  });
});
