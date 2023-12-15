/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-textarea';
import {GrTextarea} from './gr-textarea';
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

suite('gr-textarea tests', () => {
  let element: GrTextarea;

  setup(async () => {
    element = await fixture<GrTextarea>(html`<gr-textarea></gr-textarea>`);
    sinon.stub(element.reporting, 'reportInteraction');
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
        <iron-autogrow-textarea aria-disabled="false" focused="" id="textarea">
        </iron-autogrow-textarea>`,
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
      await waitUntil(() => element.textarea!.focused === true);

      element.textarea!.selectionStart = 1;
      element.textarea!.selectionEnd = 1;
      element.text = '@';

      await waitUntil(() => element.suggestions.length > 0);
      await element.updateComplete;

      assert.equal(listenerStub.lastCall.args[0].detail.value, '@');
      assert.isTrue(element.textarea!.focused);

      assert.isTrue(element.emojiSuggestions!.isHidden);
      assert.isFalse(element.mentionsSuggestions!.isHidden);

      assert.equal(element.specialCharIndex, 0);
      assert.isFalse(element.mentionsSuggestions!.isHidden);
      assert.equal(element.currentSearchString, '');

      element.text = '@abc@google.com';
      await element.updateComplete;

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
      await waitUntil(() => element.textarea!.focused === true);

      element.textarea!.selectionStart = 1;
      element.textarea!.selectionEnd = 1;
      element.text = '\n@';

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
      await waitUntil(() => element.textarea!.focused === true);

      element.suggestions = [
        {dataValue: 'prior@google.com', text: 'Prior suggestion'},
      ];
      element.textarea!.selectionStart = 1;
      element.textarea!.selectionEnd = 1;
      element.text = '@';

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
      await waitUntil(() => element.textarea!.focused === true);

      element.textarea!.selectionStart = 1;
      element.textarea!.selectionEnd = 1;
      element.text = '@';
      await element.updateComplete;
      assert.equal(element.currentSearchString, '');

      suggestionStub.returns(promise2);
      element.text = '@abc@google.com';
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
      await waitUntil(() => element.textarea!.focused === true);

      element.textarea!.selectionStart = 1;
      element.textarea!.selectionEnd = 1;
      element.text = '@';

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
      const resetSpy = sinon.spy(element, 'resetDropdown');
      stubRestApi('queryAccounts').returns(
        Promise.resolve([
          createAccountWithEmail('abc@google.com'),
          createAccountWithEmail('abcdef@google.com'),
        ])
      );
      element.textarea!.focus();
      await waitUntil(() => element.textarea!.focused === true);

      element.textarea!.selectionStart = 1;
      element.textarea!.selectionEnd = 1;
      element.text = '@';
      element.suggestions = [
        {
          name: 'a',
          value: 'a',
        },
      ];
      await waitUntil(() => element.suggestions.length > 0);
      await element.updateComplete;

      assert.isFalse(resetSpy.called);

      assert.isTrue(element.emojiSuggestions!.isHidden);
      assert.isFalse(element.mentionsSuggestions!.isHidden);

      element.text = '@h';
      await waitUntil(() => element.suggestions.length > 0);
      await element.updateComplete;
      assert.isTrue(element.emojiSuggestions!.isHidden);
      assert.isFalse(element.mentionsSuggestions!.isHidden);

      element.text = '@h';
      await waitUntil(() => element.suggestions.length > 0);
      await element.updateComplete;
      assert.isTrue(element.emojiSuggestions!.isHidden);
      assert.isFalse(element.mentionsSuggestions!.isHidden);

      element.text = '@h:';
      await waitUntil(() => element.suggestions.length > 0);
      await element.updateComplete;
      assert.isTrue(element.emojiSuggestions!.isHidden);
      assert.isFalse(element.mentionsSuggestions!.isHidden);

      element.text = '@h:D';
      await waitUntil(() => element.suggestions.length > 0);
      await element.updateComplete;
      assert.isTrue(element.emojiSuggestions!.isHidden);
      assert.isFalse(element.mentionsSuggestions!.isHidden);
    });

    test('mention dropdown does not open if emoji dropdown is open', async () => {
      const listenerStub = sinon.stub();
      element.addEventListener('text-changed', listenerStub);
      element.textarea!.focus();
      await waitUntil(() => element.textarea!.focused === true);

      element.textarea!.selectionStart = 1;
      element.textarea!.selectionEnd = 1;
      element.text = ':';
      element.suggestions = [
        {
          name: 'a',
          value: 'a',
        },
      ];

      await element.updateComplete;
      assert.isFalse(element.emojiSuggestions!.isHidden);
      assert.isTrue(element.mentionsSuggestions!.isHidden);

      element.text = ':D';
      await element.updateComplete;
      assert.isFalse(element.emojiSuggestions!.isHidden);
      assert.isTrue(element.mentionsSuggestions!.isHidden);

      element.text = ':D@';
      await element.updateComplete;
      // emoji dropdown hidden since we have no more suggestions
      assert.isFalse(element.emojiSuggestions!.isHidden);
      assert.isTrue(element.mentionsSuggestions!.isHidden);

      element.text = ':D@b';
      await element.updateComplete;
      assert.isFalse(element.emojiSuggestions!.isHidden);
      assert.isTrue(element.mentionsSuggestions!.isHidden);

      element.text = ':D@b ';
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
      await waitUntil(() => element.textarea!.focused === true);

      element.textarea!.selectionStart = 1;
      element.textarea!.selectionEnd = 1;
      element.text = '@';

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
    element.textarea!.selectionStart = 1;
    element.textarea!.selectionEnd = 1;
    element.text = ':';
    await element.updateComplete;
    assert.isTrue(element.emojiSuggestions!.isHidden);
  });

  test('emoji selector is not open when a general text is entered', async () => {
    element.textarea!.focus();
    await waitUntil(() => element.textarea!.focused === true);
    element.textarea!.selectionStart = 9;
    element.textarea!.selectionEnd = 9;
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
    await waitUntil(() => element.textarea!.focused === true);
    element.textarea!.selectionStart = 1;
    element.textarea!.selectionEnd = 1;
    element.text = ':';
    await element.updateComplete;
    assert.equal(listenerStub.lastCall.args[0].detail.value, ':');
    assert.isTrue(element.textarea!.focused);
    assert.isFalse(element.emojiSuggestions!.isHidden);
    assert.equal(element.specialCharIndex, 0);
    assert.isTrue(!element.emojiSuggestions!.isHidden);
    assert.equal(element.currentSearchString, '');
  });

  test('emoji selector opens when a colon is typed after space', async () => {
    element.textarea!.focus();
    await waitUntil(() => element.textarea!.focused === true);
    // Needed for Safari tests. selectionStart is not updated when text is
    // updated.
    element.textarea!.selectionStart = 2;
    element.textarea!.selectionEnd = 2;
    element.text = ' :';
    await element.updateComplete;
    assert.isFalse(element.emojiSuggestions!.isHidden);
    assert.equal(element.specialCharIndex, 1);
    assert.isTrue(!element.emojiSuggestions!.isHidden);
    assert.equal(element.currentSearchString, '');
  });

  test('emoji selector doesn`t open when a colon is typed after character', async () => {
    element.textarea!.focus();
    await waitUntil(() => element.textarea!.focused === true);
    // Needed for Safari tests. selectionStart is not updated when text is
    // updated.
    element.textarea!.selectionStart = 5;
    element.textarea!.selectionEnd = 5;
    element.text = 'test:';
    await element.updateComplete;
    assert.isTrue(element.emojiSuggestions!.isHidden);
    assert.isTrue(element.emojiSuggestions!.isHidden);
  });

  test('emoji selector opens when a colon is typed and some substring', async () => {
    element.textarea!.focus();
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
    assert.equal(element.specialCharIndex, 0);
    assert.isTrue(!element.emojiSuggestions!.isHidden);
    assert.equal(element.currentSearchString, 't');
  });

  test('emoji selector opens when a colon is typed in middle of text', async () => {
    element.textarea!.focus();
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
      focused: true,
      textarea: {
        focus: () => {},
      },
    });
    element.text = text;
    await element.updateComplete;
    assert.isFalse(element.emojiSuggestions!.isHidden);
    assert.equal(element.specialCharIndex, 0);
    assert.isTrue(!element.emojiSuggestions!.isHidden);
    assert.equal(element.currentSearchString, '');
  });

  test('emoji selector closes when text changes before the colon', async () => {
    element.textarea!.focus();
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

    // typing : opens the selector
    assert.isFalse(element.emojiSuggestions!.isHidden);

    element.textarea!.selectionStart = 15;
    element.textarea!.selectionEnd = 15;
    element.text = 'test test :smi';
    await element.updateComplete;

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
    element.textarea!.selectionStart = 16;
    element.textarea!.selectionEnd = 16;
    element.text = 'test test :tears';
    element.specialCharIndex = 10;
    await element.updateComplete;
    const selectedItem = {dataset: {value: '😂'}} as unknown as HTMLElement;
    const event = new CustomEvent<ItemSelectedEventDetail>('item-selected', {
      detail: {trigger: 'click', selected: selectedItem},
    });
    element.handleDropdownItemSelect(event);
    assert.equal(element.text, 'test test 😂');

    // wait for reset dropdown to finish
    await waitUntil(() => element.specialCharIndex === -1);
    element.textarea!.selectionStart = 16;
    element.textarea!.selectionEnd = 16;
    element.text = 'test test :tears';
    element.specialCharIndex = 10;
    await element.updateComplete;
    // move the cursor to the left while the suggestion popup is open
    element.textarea!.selectionStart = 0;
    element.handleDropdownItemSelect(event);
    assert.equal(element.text, 'test test 😂');

    // wait for reset dropdown to finish
    await waitUntil(() => element.specialCharIndex === -1);
    element.textarea!.selectionStart = 16;
    element.textarea!.selectionEnd = 16;
    const text = 'test test :tears happy';
    // Since selectionStart is on Chrome set always on end of text, we
    // stub it to 16
    const stub = sinon.stub(element, 'textarea').value({
      selectionStart: 16,
      value: text,
      focused: true,
      textarea: {
        focus: () => {},
      },
    });
    element.text = text;
    element.specialCharIndex = 10;
    await element.updateComplete;
    stub.restore();
    // move the cursor to the right while the suggestion popup is open
    element.textarea!.selectionStart = 22;
    element.handleDropdownItemSelect(event);
    assert.equal(element.text, 'test test 😂 happy');
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
    async function setupDropdown() {
      element.textarea!.focus();
      element.textarea!.selectionStart = 1;
      element.textarea!.selectionEnd = 1;
      element.text = ':';
      await element.updateComplete;
      element.textarea!.selectionStart = 1;
      element.textarea!.selectionEnd = 2;
      element.text = ':1';
      await element.emojiSuggestions!.updateComplete;
      await element.updateComplete;
    }

    test('escape key', async () => {
      const resetSpy = sinon.spy(element, 'resetDropdown');
      pressKey(element.textarea! as HTMLElement, Key.ESC);
      assert.isFalse(resetSpy.called);
      await setupDropdown();
      pressKey(element.textarea! as HTMLElement, Key.ESC);
      assert.isTrue(resetSpy.called);
      assert.isTrue(element.emojiSuggestions!.isHidden);
    });

    test('up key', async () => {
      const upSpy = sinon.spy(element.emojiSuggestions!, 'cursorUp');
      pressKey(element.textarea! as HTMLElement, 'ArrowUp');
      assert.isFalse(upSpy.called);
      await setupDropdown();
      pressKey(element.textarea! as HTMLElement, 'ArrowUp');
      assert.isTrue(upSpy.called);
    });

    test('down key', async () => {
      const downSpy = sinon.spy(element.emojiSuggestions!, 'cursorDown');
      pressKey(element.textarea! as HTMLElement, 'ArrowDown');
      assert.isFalse(downSpy.called);
      await setupDropdown();
      pressKey(element.textarea! as HTMLElement, 'ArrowDown');
      assert.isTrue(downSpy.called);
    });

    test('enter key', async () => {
      const enterSpy = sinon.spy(element.emojiSuggestions!, 'getCursorTarget');
      pressKey(element.textarea! as HTMLElement, Key.ENTER);
      assert.isFalse(enterSpy.called);
      await setupDropdown();
      pressKey(element.textarea! as HTMLElement, Key.ENTER);
      assert.isTrue(enterSpy.called);
      await element.updateComplete;
      assert.equal(element.text, '💯');
    });
  });

  suite('gr-textarea monospace', () => {
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
