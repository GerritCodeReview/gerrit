/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-autocomplete';
import {AutocompleteSuggestion, GrAutocomplete} from './gr-autocomplete';
import {pressKey, queryAndAssert, waitUntil} from '../../../test/test-utils';
import {GrAutocompleteDropdown} from '../gr-autocomplete-dropdown/gr-autocomplete-dropdown';
import {PaperInputElement} from '@polymer/paper-input/paper-input';
import {fixture, html, assert} from '@open-wc/testing';
import {Key, Modifier} from '../../../utils/dom-util';

suite('gr-autocomplete tests', () => {
  let element: GrAutocomplete;

  const focusOnInput = () => {
    pressKey(inputEl(), Key.ENTER);
  };

  const suggestionsEl = () =>
    queryAndAssert<GrAutocompleteDropdown>(element, '#suggestions');

  const inputEl = () => queryAndAssert<HTMLInputElement>(element, '#input');

  setup(async () => {
    element = await fixture(
      html`<gr-autocomplete no-debounce></gr-autocomplete>`
    );
  });

  test('renders', () => {
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <paper-input
          aria-disabled="false"
          autocomplete="off"
          id="input"
          tabindex="0"
        >
          <div slot="prefix">
            <gr-icon icon="search" class="searchIcon"></gr-icon>
          </div>
          <div slot="suffix">
            <slot name="suffix"> </slot>
          </div>
        </paper-input>
        <gr-autocomplete-dropdown
          id="suggestions"
          is-hidden=""
          role="listbox"
          style="position: fixed; top: 300px; left: 392.5px; box-sizing: border-box; max-height: 600px; max-width: 785px;"
        >
        </gr-autocomplete-dropdown>
      `,
      {
        // gr-autocomplete-dropdown sizing seems to vary between local & CI
        ignoreAttributes: [
          {tags: ['gr-autocomplete-dropdown'], attributes: ['style']},
        ],
      }
    );
  });

  test('renders with suggestions', async () => {
    const queryStub = sinon.spy((input: string) =>
      Promise.resolve([
        {name: input + ' 0', value: '0'},
        {name: input + ' 1', value: '1'},
        {name: input + ' 2', value: '2'},
        {name: input + ' 3', value: '3'},
        {name: input + ' 4', value: '4'},
      ] as AutocompleteSuggestion[])
    );
    element.query = queryStub;

    focusOnInput();
    element.text = 'blah';
    await waitUntil(() => queryStub.called);
    await element.updateComplete;

    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <paper-input
          aria-disabled="false"
          autocomplete="off"
          id="input"
          tabindex="0"
        >
          <div slot="prefix">
            <gr-icon icon="search" class="searchIcon"></gr-icon>
          </div>
          <div slot="suffix">
            <slot name="suffix"> </slot>
          </div>
        </paper-input>
        <gr-autocomplete-dropdown id="suggestions" role="listbox">
        </gr-autocomplete-dropdown>
      `,
      {
        // gr-autocomplete-dropdown sizing seems to vary between local & CI
        ignoreAttributes: [
          {tags: ['gr-autocomplete-dropdown'], attributes: ['style']},
        ],
      }
    );
  });

  test('renders with error', async () => {
    const queryStub = sinon.spy((input: string) =>
      Promise.reject(new Error(`${input} not allowed`))
    );
    element.query = queryStub;

    focusOnInput();
    element.text = 'blah';
    await waitUntil(() => queryStub.called);
    await element.updateComplete;

    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <paper-input
          aria-disabled="false"
          autocomplete="off"
          id="input"
          tabindex="0"
        >
          <div slot="prefix">
            <gr-icon icon="search" class="searchIcon"></gr-icon>
          </div>
          <div slot="suffix">
            <slot name="suffix"> </slot>
          </div>
        </paper-input>
        <gr-autocomplete-dropdown id="suggestions" role="listbox">
        </gr-autocomplete-dropdown>
      `,
      {
        // gr-autocomplete-dropdown sizing seems to vary between local & CI
        ignoreAttributes: [
          {tags: ['gr-autocomplete-dropdown'], attributes: ['style']},
        ],
      }
    );
    assert.equal(element.suggestionsDropdown?.errorMessage, 'blah not allowed');
  });

  test('cursor starts on suggestions', async () => {
    const queryStub = sinon.spy((input: string) =>
      Promise.resolve([
        {name: input + ' 0', value: '0'},
        {name: input + ' 1', value: '1'},
        {name: input + ' 2', value: '2'},
        {name: input + ' 3', value: '3'},
        {name: input + ' 4', value: '4'},
      ] as AutocompleteSuggestion[])
    );
    element.query = queryStub;

    assert.equal(suggestionsEl().cursor.index, -1);

    focusOnInput();
    element.text = 'blah';
    await waitUntil(() => queryStub.called);
    await element.updateComplete;

    assert.notEqual(suggestionsEl().cursor.index, -1);
  });

  test('selectAll', async () => {
    await element.updateComplete;
    const nativeInput = element.nativeInput;
    const selectionStub = sinon.stub(nativeInput, 'setSelectionRange');

    element.selectAll();
    await element.updateComplete;
    assert.isFalse(selectionStub.called);

    inputEl().value = 'test';
    await element.updateComplete;
    element.selectAll();
    assert.isTrue(selectionStub.called);
  });

  test('esc key behavior', async () => {
    let promise: Promise<AutocompleteSuggestion[]> = Promise.resolve([]);
    const queryStub = sinon.spy(
      (_: string) =>
        (promise = Promise.resolve([
          {name: 'blah', value: '123'},
        ] as AutocompleteSuggestion[]))
    );
    element.query = queryStub;

    assert.isTrue(suggestionsEl().isHidden);

    element.setFocus(true);
    element.text = 'blah';
    await element.updateComplete;

    return promise.then(async () => {
      await waitUntil(() => !suggestionsEl().isHidden);

      const cancelHandler = sinon.spy();
      element.addEventListener('cancel', cancelHandler);

      pressKey(inputEl(), Key.ESC);
      await waitUntil(() => suggestionsEl().isHidden);

      assert.isFalse(cancelHandler.called);
      assert.equal(element.suggestions.length, 0);

      pressKey(inputEl(), Key.ESC);
      await element.updateComplete;

      assert.isTrue(cancelHandler.called);
    });
  });

  test('esc key behavior on error', async () => {
    let promise: Promise<AutocompleteSuggestion[]> = Promise.resolve([]);
    const queryStub = sinon.spy(
      (_: string) => (promise = Promise.reject(new Error('Test error')))
    );
    element.query = queryStub;

    assert.isTrue(suggestionsEl().isHidden);

    element.setFocus(true);
    element.text = 'blah';
    await element.updateComplete;

    return promise.catch(async () => {
      await waitUntil(() => !suggestionsEl().isHidden);

      const cancelHandler = sinon.spy();
      element.addEventListener('cancel', cancelHandler);
      assert.equal(element.queryErrorMessage, 'Test error');

      pressKey(inputEl(), Key.ESC);
      await waitUntil(() => suggestionsEl().isHidden);

      assert.isFalse(cancelHandler.called);
      assert.isUndefined(element.queryErrorMessage);

      pressKey(inputEl(), Key.ESC);
      await element.updateComplete;

      assert.isTrue(cancelHandler.called);
    });
  });

  test('emits commit and handles cursor movement', async () => {
    let promise: Promise<AutocompleteSuggestion[]> = Promise.resolve([]);
    const queryStub = sinon.spy(
      (input: string) =>
        (promise = Promise.resolve([
          {name: input + ' 0', value: '0'},
          {name: input + ' 1', value: '1'},
          {name: input + ' 2', value: '2'},
          {name: input + ' 3', value: '3'},
          {name: input + ' 4', value: '4'},
        ] as AutocompleteSuggestion[]))
    );
    element.query = queryStub;
    await element.updateComplete;
    assert.isTrue(suggestionsEl().isHidden);
    assert.equal(suggestionsEl().cursor.index, -1);
    element.setFocus(true);

    element.text = 'blah';
    await element.updateComplete;

    return promise.then(async () => {
      await waitUntil(() => !suggestionsEl().isHidden);

      const commitHandler = sinon.spy();
      element.addEventListener('commit', commitHandler);

      assert.equal(suggestionsEl().cursor.index, 0);

      pressKey(inputEl(), 'ArrowDown');
      await element.updateComplete;

      assert.equal(suggestionsEl().cursor.index, 1);

      pressKey(inputEl(), 'ArrowDown');
      await element.updateComplete;

      assert.equal(suggestionsEl().cursor.index, 2);

      pressKey(inputEl(), 'ArrowUp');
      await element.updateComplete;

      assert.equal(suggestionsEl().cursor.index, 1);

      pressKey(inputEl(), Key.ENTER);
      await element.updateComplete;

      assert.equal(element.value, '1');

      await waitUntil(() => commitHandler.called);
      assert.equal(commitHandler.getCall(0).args[0].detail.value, 1);
      assert.isTrue(suggestionsEl().isHidden);
      assert.isTrue(element.focused);
    });
  });

  test('clear-on-commit behavior (off)', async () => {
    let promise: Promise<AutocompleteSuggestion[]> = Promise.resolve([]);
    const queryStub = sinon.spy(() => {
      promise = Promise.resolve([
        {name: 'suggestion', value: '0'},
      ] as AutocompleteSuggestion[]);
      return promise;
    });
    element.query = queryStub;
    focusOnInput();
    element.text = 'blah';
    await waitUntil(() => element.suggestions.length > 0);

    return promise.then(async () => {
      const commitHandler = sinon.spy();
      element.addEventListener('commit', commitHandler);

      pressKey(inputEl(), Key.ENTER);

      await waitUntil(() => commitHandler.called);

      assert.equal(element.text, 'suggestion');
    });
  });

  test('clear-on-commit behavior (on)', async () => {
    let promise: Promise<AutocompleteSuggestion[]> = Promise.resolve([]);
    const queryStub = sinon.spy(() => {
      promise = Promise.resolve([
        {name: 'suggestion', value: '0'},
      ] as AutocompleteSuggestion[]);
      return promise;
    });
    element.query = queryStub;
    focusOnInput();
    element.text = 'blah';

    await waitUntil(() => element.suggestions.length > 0);

    element.clearOnCommit = true;

    return promise.then(async () => {
      const commitHandler = sinon.spy();
      element.addEventListener('commit', commitHandler);

      pressKey(inputEl(), Key.ENTER);

      await waitUntil(() => commitHandler.called);

      assert.equal(element.text, '');
    });
  });

  test('threshold guards the query', async () => {
    const queryStub = sinon.spy(() =>
      Promise.resolve([] as AutocompleteSuggestion[])
    );
    element.query = queryStub;
    element.threshold = 2;
    focusOnInput();
    element.text = 'a';
    await element.updateComplete;
    assert.isFalse(queryStub.called);

    element.text = 'ab';
    await element.updateComplete;
    await waitUntil(() => queryStub.called);
  });

  test('noDebounce=false debounces the query', async () => {
    const queryStub = sinon.spy(() =>
      Promise.resolve([] as AutocompleteSuggestion[])
    );

    element.query = queryStub;
    await element.updateComplete;
    element.noDebounce = false;
    focusOnInput();
    element.text = 'a';

    // not called right away
    assert.isFalse(queryStub.called);

    await waitUntil(() => queryStub.called);
  });

  test('computeClass respects border property', () => {
    element.borderless = false;
    assert.equal(element.computeClass(), '');
    element.borderless = true;
    assert.equal(element.computeClass(), 'borderless');
    element.showBlueFocusBorder = true;
    assert.equal(element.computeClass(), 'borderless showBlueFocusBorder');
  });

  test('empty text results in no suggestions', async () => {
    element.text = '';
    element.threshold = 0;
    element.noDebounce = false;
    await element.updateComplete;
    assert.equal(element.suggestions.length, 0);
  });

  test('when focused', async () => {
    let promise: Promise<AutocompleteSuggestion[]> = Promise.resolve([]);
    const queryStub = sinon
      .stub()
      .returns(
        (promise = Promise.resolve([
          {name: 'suggestion', value: '0'},
        ] as AutocompleteSuggestion[]))
      );
    element.query = queryStub;
    focusOnInput();
    element.text = 'bla';
    assert.equal(element.focused, true);
    await element.updateComplete;
    return promise.then(async () => {
      await waitUntil(() => element.suggestions.length > 0);
      assert.equal(element.suggestions.length, 1);
      assert.equal(queryStub.notCalled, false);
    });
  });

  test('when not focused', async () => {
    let promise: Promise<AutocompleteSuggestion[]> = Promise.resolve([]);
    const queryStub = sinon
      .stub()
      .returns(
        (promise = Promise.resolve([
          {name: 'suggestion', value: '0'},
        ] as AutocompleteSuggestion[]))
      );
    element.query = queryStub;
    element.text = 'bla';
    assert.equal(element.focused, false);
    await element.updateComplete;
    return promise.then(() => {
      assert.equal(element.suggestions.length, 0);
    });
  });

  test('suggestions should not carry over', async () => {
    let promise: Promise<AutocompleteSuggestion[]> = Promise.resolve([]);
    const queryStub = sinon
      .stub()
      .returns(
        (promise = Promise.resolve([
          {name: 'suggestion', value: '0'},
        ] as AutocompleteSuggestion[]))
      );
    element.query = queryStub;
    focusOnInput();
    element.text = 'bla';
    await element.updateComplete;
    return promise.then(async () => {
      await waitUntil(() => element.suggestions.length > 0);
      assert.equal(element.suggestions.length, 1);
      element.text = '';
      element.threshold = 0;
      element.noDebounce = false;
      await element.updateComplete;
      assert.equal(element.suggestions.length, 0);
    });
  });

  test('error should not carry over', async () => {
    let promise: Promise<AutocompleteSuggestion[]> = Promise.resolve([]);
    const queryStub = sinon
      .stub()
      .returns((promise = Promise.reject(new Error('Test error'))));
    element.query = queryStub;
    focusOnInput();
    element.text = 'bla';
    await element.updateComplete;
    return promise.catch(async () => {
      await waitUntil(() => element.queryErrorMessage === 'Test error');
      element.text = '';
      element.threshold = 0;
      element.noDebounce = false;
      await element.updateComplete;
      assert.isUndefined(element.queryErrorMessage);
    });
  });

  test('multi completes only the last part of the query', async () => {
    let promise;
    const queryStub = sinon
      .stub()
      .returns(
        (promise = Promise.resolve([
          {name: 'suggestion', value: '0'},
        ] as AutocompleteSuggestion[]))
      );
    element.query = queryStub;
    focusOnInput();
    element.text = 'blah blah';
    element.multi = true;
    await element.updateComplete;

    return promise.then(async () => {
      const commitHandler = sinon.spy();
      element.addEventListener('commit', commitHandler);
      await waitUntil(() => element.suggestionsDropdown?.isHidden === false);

      pressKey(inputEl(), Key.ENTER);

      await waitUntil(() => commitHandler.called);
      assert.equal(element.text, 'blah 0');
    });
  });

  test('tabComplete flag functions', async () => {
    // commitHandler checks for the commit event, whereas commitSpy checks for
    // the _commit function of the element.
    const commitHandler = sinon.spy();
    element.addEventListener('commit', commitHandler);
    const commitSpy = sinon.spy(element, '_commit');
    element.setFocus(true);

    element.suggestions = [{text: 'tunnel snakes rule!', name: ''}];
    element.tabComplete = false;
    pressKey(inputEl(), Key.TAB);
    await element.updateComplete;

    assert.isFalse(commitHandler.called);
    assert.isFalse(commitSpy.called);
    assert.isFalse(element.focused);

    element.tabComplete = true;
    await element.updateComplete;
    element.setFocus(true);
    await element.updateComplete;
    pressKey(inputEl(), Key.TAB);

    await waitUntil(() => commitSpy.called);
    assert.isFalse(commitHandler.called);
    assert.isTrue(element.focused);
  });

  test('focused flag properly triggered', async () => {
    await element.updateComplete;
    assert.isFalse(element.focused);
    const input = queryAndAssert<PaperInputElement>(element, 'paper-input');
    input.focus();
    assert.isTrue(element.focused);
  });

  test('search icon shows with showSearchIcon property', async () => {
    assert.equal(
      getComputedStyle(queryAndAssert(element, 'gr-icon')).display,
      'none'
    );
    element.showSearchIcon = true;
    await element.updateComplete;

    assert.notEqual(
      getComputedStyle(queryAndAssert(element, 'gr-icon')).display,
      'none'
    );
  });

  test('vertical offset overridden by param if it exists', async () => {
    assert.equal(suggestionsEl().verticalOffset, 31);

    element.verticalOffset = 30;
    await element.updateComplete;

    assert.equal(suggestionsEl().verticalOffset, 30);
  });

  test('focused flag shows/hides the suggestions', async () => {
    const openStub = sinon.stub(suggestionsEl(), 'open');
    const closedStub = sinon.stub(suggestionsEl(), 'close');
    element.suggestions = [{text: 'hello'}, {text: 'its me'}];
    assert.isFalse(openStub.called);
    await waitUntil(() => closedStub.calledOnce);
    element.setFocus(true);
    await waitUntil(() => openStub.calledOnce);
    element.suggestions = [];
    await waitUntil(() => closedStub.calledTwice);
    assert.isTrue(openStub.calledOnce);
  });

  test(
    'handleInputCommit with autocomplete hidden does nothing without' +
      ' allowNonSuggestedValues',
    () => {
      const commitStub = sinon.stub(element, '_commit');
      suggestionsEl().isHidden = true;
      element.handleInputCommit();
      assert.isFalse(commitStub.called);
    }
  );

  test(
    'handleInputCommit with query error does nothing without' +
      ' allowNonSuggestedValues',
    () => {
      const commitStub = sinon.stub(element, '_commit');
      element.queryErrorMessage = 'Error';
      element.handleInputCommit();
      assert.isFalse(commitStub.called);
    }
  );

  test(
    'handleInputCommit with autocomplete hidden with' +
      'allowNonSuggestedValues',
    () => {
      const commitStub = sinon.stub(element, '_commit');
      element.allowNonSuggestedValues = true;
      suggestionsEl().isHidden = true;
      element.handleInputCommit();
      assert.isTrue(commitStub.called);
    }
  );

  test(
    'handleInputCommit with query error with' + 'allowNonSuggestedValues',
    () => {
      const commitStub = sinon.stub(element, '_commit');
      element.allowNonSuggestedValues = true;
      element.queryErrorMessage = 'Error';
      element.handleInputCommit();
      assert.isTrue(commitStub.called);
    }
  );

  test('handleInputCommit with autocomplete open calls commit', () => {
    const commitStub = sinon.stub(element, '_commit');
    suggestionsEl().isHidden = false;
    element.handleInputCommit();
    assert.isTrue(commitStub.calledOnce);
  });

  test(
    'handleInputCommit with autocomplete open calls commit' +
      'with allowNonSuggestedValues',
    () => {
      const commitStub = sinon.stub(element, '_commit');
      element.allowNonSuggestedValues = true;
      suggestionsEl().isHidden = false;
      element.handleInputCommit();
      assert.isTrue(commitStub.calledOnce);
    }
  );

  test('issue 8655', async () => {
    function makeSuggestion(s: string) {
      return {name: s, text: s, value: s};
    }
    const keydownSpy = sinon.spy(element, 'handleKeydown');
    element.requestUpdate();
    await element.updateComplete;

    // const dispatchEventStub = sinon.stub(element, 'dispatchEvent');
    element.setText('file:');
    element.suggestions = [makeSuggestion('file:'), makeSuggestion('-file:')];
    await element.updateComplete;

    pressKey(inputEl(), 'x');
    // Must set the value, because the MockInteraction does not.
    inputEl().value = 'file:x';

    assert.isTrue(keydownSpy.calledOnce);

    pressKey(inputEl(), Key.ENTER);
    await element.updateComplete;
    assert.isTrue(keydownSpy.calledTwice);

    assert.equal(element.text, 'file:x');
  });

  suite('focus', () => {
    let commitSpy: sinon.SinonSpy;
    let focusSpy: sinon.SinonSpy;

    setup(() => {
      commitSpy = sinon.spy(element, '_commit');
    });

    test('enter in input does not re-render suggestions', async () => {
      element.suggestions = [{text: 'sugar bombs'}];

      pressKey(inputEl(), Key.ENTER);

      await waitUntil(() => commitSpy.called);
      await element.updateComplete;

      assert.equal(element.suggestions.length, 0);
      assert.isUndefined(element.queryErrorMessage);
      assert.isTrue(suggestionsEl().isHidden);
    });

    test('enter in input does not re-render error', async () => {
      element.allowNonSuggestedValues = true;
      element.queryErrorMessage = 'Error message';

      pressKey(inputEl(), Key.ENTER);

      await waitUntil(() => commitSpy.called);
      await element.updateComplete;

      assert.equal(element.suggestions.length, 0);
      assert.isUndefined(element.queryErrorMessage);
      assert.isTrue(suggestionsEl().isHidden);
    });

    test('enter in suggestion does not re-render suggestions', async () => {
      element.suggestions = [{text: 'sugar bombs'}];
      element.setFocus(true);

      await element.updateComplete;
      assert.isFalse(suggestionsEl().isHidden);

      focusSpy = sinon.spy(element, 'focus');
      pressKey(suggestionsEl(), Key.ENTER);

      await waitUntil(() => commitSpy.called);
      await element.updateComplete;

      assert.equal(element.suggestions.length, 0);
      assert.isTrue(suggestionsEl().isHidden);
    });

    test('tab in input, tabComplete = true', async () => {
      focusSpy = sinon.spy(element, 'focus');
      const commitHandler = sinon.stub();
      element.addEventListener('commit', commitHandler);
      element.tabComplete = true;
      element.suggestions = [{text: 'tunnel snakes drool'}];

      pressKey(inputEl(), Key.TAB);

      await waitUntil(() => commitSpy.called);

      assert.isTrue(focusSpy.called);
      assert.isFalse(commitHandler.called);
      assert.equal(element.suggestions.length, 0);
    });

    test('tab in input, tabComplete = false', async () => {
      element.suggestions = [{text: 'sugar bombs'}];
      focusSpy = sinon.spy(element, 'focus');
      pressKey(inputEl(), Key.TAB);
      await element.updateComplete;

      assert.isFalse(commitSpy.called);
      assert.isFalse(focusSpy.called);
      await waitUntil(() => element.suggestions.length > 0);
      assert.equal(element.suggestions.length, 1);
    });

    test('tab on suggestion, tabComplete = false', async () => {
      element.suggestions = [{name: 'sugar bombs'}];
      element.setFocus(true);
      // When tabComplete is false, do not focus.
      element.tabComplete = false;
      focusSpy = sinon.spy(element, 'focus');

      await element.updateComplete;

      assert.isFalse(suggestionsEl().isHidden);

      pressKey(queryAndAssert(suggestionsEl(), 'li:first-child'), Key.TAB);
      await element.updateComplete;
      assert.isFalse(commitSpy.called);
      assert.isFalse(element.focused);
    });

    test('tab on suggestion, tabComplete = true', async () => {
      element.suggestions = [{name: 'sugar bombs'}];
      element.setFocus(true);
      // When tabComplete is true, focus.
      element.tabComplete = true;
      focusSpy = sinon.spy(element, 'focus');

      await element.updateComplete;

      assert.isFalse(suggestionsEl().isHidden);

      pressKey(queryAndAssert(suggestionsEl(), 'li:first-child'), Key.TAB);
      await element.updateComplete;

      assert.isTrue(commitSpy.called);
      assert.isTrue(element.focused);
    });

    test('tap on suggestion commits, calls focus', async () => {
      focusSpy = sinon.spy(element, 'focus');
      element.setFocus(true);
      element.suggestions = [{name: 'first suggestion'}];

      await element.updateComplete;

      await waitUntil(() => !suggestionsEl().isHidden);
      queryAndAssert<HTMLLIElement>(suggestionsEl(), 'li:first-child').click();

      await waitUntil(() => suggestionsEl().isHidden);
      assert.isTrue(focusSpy.called);
      assert.isTrue(commitSpy.called);
    });

    test('esc on suggestion clears suggestions, calls focus', async () => {
      element.suggestions = [{name: 'sugar bombs'}];
      element.setFocus(true);
      focusSpy = sinon.spy(element, 'focus');

      await element.updateComplete;

      assert.isFalse(suggestionsEl().isHidden);

      pressKey(queryAndAssert(suggestionsEl(), 'li:first-child'), Key.ESC);

      await waitUntil(() => suggestionsEl().isHidden);
      await element.updateComplete;

      assert.isFalse(commitSpy.called);
      assert.isTrue(focusSpy.called);
    });
  });

  test('input-keydown event fired', async () => {
    const listener = sinon.spy();
    element.addEventListener('input-keydown', listener);
    pressKey(inputEl(), Key.TAB);
    await element.updateComplete;
    assert.isTrue(listener.called);
  });

  test('enter with modifier does not complete', async () => {
    const dispatchEventStub = sinon.stub(element, 'dispatchEvent');
    const commitStub = sinon.stub(element, 'handleInputCommit');
    pressKey(inputEl(), Key.ENTER, Modifier.CTRL_KEY);
    await element.updateComplete;

    assert.equal(dispatchEventStub.lastCall.args[0].type, 'input-keydown');
    assert.equal(
      (dispatchEventStub.lastCall.args[0] as CustomEvent).detail.key,
      Key.ENTER
    );

    assert.isFalse(commitStub.called);
    pressKey(inputEl(), Key.ENTER);
    await element.updateComplete;

    assert.isTrue(commitStub.called);
  });

  test('enter with dropdown does not propagate', async () => {
    const event = new KeyboardEvent('keydown', {key: Key.ENTER});
    const stopPropagationStub = sinon.stub(event, 'stopPropagation');

    element.suggestions = [{name: 'first suggestion'}];

    inputEl().dispatchEvent(event);
    await element.updateComplete;

    assert.isTrue(stopPropagationStub.called);
  });

  test('enter with no dropdown propagates', async () => {
    const event = new KeyboardEvent('keydown', {key: Key.ENTER});
    const stopPropagationStub = sinon.stub(event, 'stopPropagation');

    inputEl().dispatchEvent(event);
    await element.updateComplete;

    assert.isFalse(stopPropagationStub.called);
  });

  suite('warnUncommitted', () => {
    let inputClassList: DOMTokenList;
    setup(() => {
      inputClassList = inputEl().classList;
    });

    test('enabled', () => {
      element.warnUncommitted = true;
      element.text = 'blah blah blah';
      inputEl().dispatchEvent(new Event('blur'));
      assert.isTrue(inputClassList.contains('warnUncommitted'));
      inputEl().focus();
      assert.isFalse(inputClassList.contains('warnUncommitted'));
    });

    test('disabled', () => {
      element.warnUncommitted = false;
      element.text = 'blah blah blah';
      inputEl().blur();
      assert.isFalse(inputClassList.contains('warnUncommitted'));
    });

    test('no text', () => {
      element.warnUncommitted = true;
      element.text = '';
      inputEl().blur();
      assert.isFalse(inputClassList.contains('warnUncommitted'));
    });
  });
});
