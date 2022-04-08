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
import '../../../test/common-test-setup-karma';
import './gr-autocomplete';
import {AutocompleteSuggestion, GrAutocomplete} from './gr-autocomplete';
import * as MockInteractions from '@polymer/iron-test-helpers/mock-interactions';
import {assertIsDefined} from '../../../utils/common-util';
import {queryAll, queryAndAssert, waitUntil} from '../../../test/test-utils';
import {GrAutocompleteDropdown} from '../gr-autocomplete-dropdown/gr-autocomplete-dropdown';
import {PaperInputElement} from '@polymer/paper-input/paper-input';
import {fixture, html} from '@open-wc/testing-helpers';

suite('gr-autocomplete tests', () => {
  let element: GrAutocomplete;

  const focusOnInput = () => {
    MockInteractions.pressAndReleaseKeyOn(inputEl(), 13, null, 'enter');
  };

  const suggestionsEl = () =>
    queryAndAssert<GrAutocompleteDropdown>(element, '#suggestions');

  const inputEl = () => queryAndAssert<HTMLInputElement>(element, '#input');

  setup(async () => {
    element = await fixture(
      html`<gr-autocomplete no-debounce></gr-autocomplete>`
    );
    await element.updateComplete;
  });

  test('renders', async () => {
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
    assert.isTrue(suggestionsEl().isHidden);
    assert.equal(suggestionsEl().cursor.index, -1);

    focusOnInput();
    element.text = 'blah';
    await waitUntil(() => queryStub.called);

    element.focused = true;

    assertIsDefined(promise);
    return promise.then(async () => {
      await element.updateComplete;
      assert.isFalse(suggestionsEl().isHidden);
      const suggestions = queryAll<HTMLElement>(suggestionsEl(), 'li');
      assert.equal(suggestions.length, 5);

      for (let i = 0; i < 5; i++) {
        assert.equal(suggestions[i].innerText.trim(), `blah ${i}`);
      }

      assert.notEqual(suggestionsEl().cursor.index, -1);
    });
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

    element.focused = true;
    element.text = 'blah';
    await element.updateComplete;

    return promise.then(async () => {
      await waitUntil(() => !suggestionsEl().isHidden);

      const cancelHandler = sinon.spy();
      element.addEventListener('cancel', cancelHandler);

      MockInteractions.pressAndReleaseKeyOn(inputEl(), 27, null, 'esc');
      await element.updateComplete;

      assert.isFalse(cancelHandler.called);
      assert.isTrue(suggestionsEl().isHidden);
      assert.equal(element.suggestions.length, 0);

      MockInteractions.pressAndReleaseKeyOn(inputEl(), 27, null, 'esc');
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

    assert.isTrue(suggestionsEl().isHidden);
    assert.equal(suggestionsEl().cursor.index, -1);
    element.focused = true;
    element.text = 'blah';
    await element.updateComplete;

    return promise.then(async () => {
      await waitUntil(() => !suggestionsEl().isHidden);

      const commitHandler = sinon.spy();
      element.addEventListener('commit', commitHandler);

      assert.equal(suggestionsEl().cursor.index, 0);

      MockInteractions.pressAndReleaseKeyOn(inputEl(), 40, null, 'down');

      assert.equal(suggestionsEl().cursor.index, 1);

      MockInteractions.pressAndReleaseKeyOn(inputEl(), 40, null, 'down');

      assert.equal(suggestionsEl().cursor.index, 2);

      MockInteractions.pressAndReleaseKeyOn(inputEl(), 38, null, 'up');

      assert.equal(suggestionsEl().cursor.index, 1);

      MockInteractions.pressAndReleaseKeyOn(inputEl(), 13, null, 'enter');

      await waitUntil(() => suggestionsEl().isHidden);

      assert.equal(element.value, '1');
      assert.isTrue(commitHandler.called);
      assert.equal(commitHandler.getCall(0).args[0].detail.value, 1);
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

      MockInteractions.pressAndReleaseKeyOn(inputEl(), 13, null, 'enter');

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

    return promise.then(() => {
      const commitHandler = sinon.spy();
      element.addEventListener('commit', commitHandler);

      MockInteractions.pressAndReleaseKeyOn(inputEl(), 13, null, 'enter');

      assert.isTrue(commitHandler.called);
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
    const clock = sinon.useFakeTimers();
    const queryStub = sinon.spy(() =>
      Promise.resolve([] as AutocompleteSuggestion[])
    );
    element.query = queryStub;
    element.noDebounce = false;
    focusOnInput();
    element.text = 'a';

    // not called right away
    assert.isFalse(queryStub.called);

    // but called after a while
    clock.tick(1000);
    await waitUntil(() => queryStub.called);
  });

  test('_computeClass respects border property', () => {
    assert.equal(element._computeClass(), '');
    assert.equal(element._computeClass(false), '');
    assert.equal(element._computeClass(true), 'borderless');
  });

  test('empty text results in no suggestions', async () => {
    element.text = '';
    element.threshold = 0;
    element.noDebounce = false;
    await element.updateComplete;
    assert.equal(element.suggestions.length, 0);
  });

  test.only('when focused', async () => {
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

  test('when not focused', () => {
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
    element.updateComplete;
    return promise.then(() => {
      assert.equal(element.suggestions.length, 0);
    });
  });

  test('suggestions should not carry over', () => {
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
    element.updateComplete;
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

  test('multi completes only the last part of the query', () => {
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

    return promise.then(() => {
      const commitHandler = sinon.spy();
      element.addEventListener('commit', commitHandler);

      MockInteractions.pressAndReleaseKeyOn(inputEl(), 13, null, 'enter');

      assert.isTrue(commitHandler.called);
      assert.equal(element.text, 'blah 0');
    });
  });

  test('tabComplete flag functions', () => {
    // commitHandler checks for the commit event, whereas commitSpy checks for
    // the _commit function of the element.
    const commitHandler = sinon.spy();
    element.addEventListener('commit', commitHandler);
    const commitSpy = sinon.spy(element, '_commit');
    element.focused = true;

    element.suggestions = [{text: 'tunnel snakes rule!'}];
    element.tabComplete = false;
    MockInteractions.pressAndReleaseKeyOn(inputEl(), 9, null, 'tab');
    assert.isFalse(commitHandler.called);
    assert.isFalse(commitSpy.called);
    assert.isFalse(element.focused);

    element.tabComplete = true;
    element.focused = true;
    MockInteractions.pressAndReleaseKeyOn(inputEl(), 9, null, 'tab');
    assert.isFalse(commitHandler.called);
    assert.isTrue(commitSpy.called);
    assert.isTrue(element.focused);
  });

  test('focused flag properly triggered', () => {
    element.updateComplete;
    assert.isFalse(element.focused);
    const input = queryAndAssert<PaperInputElement>(
      element,
      'paper-input'
    ).inputElement;
    MockInteractions.focus(input);
    assert.isTrue(element.focused);
  });

  test('search icon shows with showSearchIcon property', () => {
    element.updateComplete;
    assert.equal(
      getComputedStyle(queryAndAssert(element, 'iron-icon')).display,
      'none'
    );
    element.showSearchIcon = true;
    assert.notEqual(
      getComputedStyle(queryAndAssert(element, 'iron-icon')).display,
      'none'
    );
  });

  test('vertical offset overridden by param if it exists', () => {
    assert.equal(suggestionsEl().verticalOffset, 31);
    element.verticalOffset = 30;
    assert.equal(suggestionsEl().verticalOffset, 30);
  });

  test('focused flag shows/hides the suggestions', async () => {
    const openStub = sinon.stub(suggestionsEl(), 'open');
    const closedStub = sinon.stub(suggestionsEl(), 'close');
    element.suggestions = [{text: 'hello'}, {text: 'its me'}];
    assert.isFalse(openStub.called);
    await waitUntil(() => closedStub.calledOnce);
    element.focused = true;
    await waitUntil(() => openStub.calledOnce);
    element.suggestions = [];
    await waitUntil(() => closedStub.calledTwice);
    assert.isTrue(openStub.calledOnce);
  });

  test(
    '_handleInputCommit with autocomplete hidden does nothing without' +
      'without allowNonSuggestedValues',
    () => {
      const commitStub = sinon.stub(element, '_commit');
      suggestionsEl().isHidden = true;
      element._handleInputCommit();
      assert.isFalse(commitStub.called);
    }
  );

  test(
    '_handleInputCommit with autocomplete hidden with' +
      'allowNonSuggestedValues',
    () => {
      const commitStub = sinon.stub(element, '_commit');
      element.allowNonSuggestedValues = true;
      suggestionsEl().isHidden = true;
      element._handleInputCommit();
      assert.isTrue(commitStub.called);
    }
  );

  test('_handleInputCommit with autocomplete open calls commit', () => {
    const commitStub = sinon.stub(element, '_commit');
    suggestionsEl().isHidden = false;
    element._handleInputCommit();
    assert.isTrue(commitStub.calledOnce);
  });

  test(
    '_handleInputCommit with autocomplete open calls commit' +
      'with allowNonSuggestedValues',
    () => {
      const commitStub = sinon.stub(element, '_commit');
      element.allowNonSuggestedValues = true;
      suggestionsEl().isHidden = false;
      element._handleInputCommit();
      assert.isTrue(commitStub.calledOnce);
    }
  );

  test('issue 8655', () => {
    function makeSuggestion(s: string) {
      return {name: s, text: s, value: s};
    }
    const keydownSpy = sinon.spy(element, 'handleKeydown');
    element.setText('file:');
    element.suggestions = [makeSuggestion('file:'), makeSuggestion('-file:')];
    MockInteractions.pressAndReleaseKeyOn(inputEl(), 88, null, 'x');
    // Must set the value, because the MockInteraction does not.
    inputEl().value = 'file:x';
    assert.isTrue(keydownSpy.calledOnce);
    MockInteractions.pressAndReleaseKeyOn(inputEl(), 13, null, 'enter');
    assert.isTrue(keydownSpy.calledTwice);
    assert.equal(element.text, 'file:x');
  });

  suite('focus', () => {
    let commitSpy: sinon.SinonSpy;
    let focusSpy: sinon.SinonSpy;

    setup(() => {
      commitSpy = sinon.spy(element, '_commit');
    });

    test('enter does not call focus', () => {
      element.suggestions = [{text: 'sugar bombs'}];
      focusSpy = sinon.spy(element, 'focus');
      MockInteractions.pressAndReleaseKeyOn(inputEl(), 13, null, 'enter');
      element.updateComplete;

      assert.isTrue(commitSpy.called);
      assert.isFalse(focusSpy.called);
      assert.equal(element.suggestions.length, 0);
    });

    test('tab in input, tabComplete = true', () => {
      focusSpy = sinon.spy(element, 'focus');
      const commitHandler = sinon.stub();
      element.addEventListener('commit', commitHandler);
      element.tabComplete = true;
      element.suggestions = [{text: 'tunnel snakes drool'}];
      MockInteractions.pressAndReleaseKeyOn(inputEl(), 9, null, 'tab');
      element.updateComplete;

      assert.isTrue(commitSpy.called);
      assert.isTrue(focusSpy.called);
      assert.isFalse(commitHandler.called);
      assert.equal(element.suggestions.length, 0);
    });

    test('tab in input, tabComplete = false', async () => {
      element.suggestions = [{text: 'sugar bombs'}];
      focusSpy = sinon.spy(element, 'focus');
      MockInteractions.pressAndReleaseKeyOn(inputEl(), 9, null, 'tab');
      element.updateComplete;

      assert.isFalse(commitSpy.called);
      assert.isFalse(focusSpy.called);
      await waitUntil(() => element.suggestions.length > 0);
      assert.equal(element.suggestions.length, 1);
    });

    test('tab on suggestion, tabComplete = false', async () => {
      element.suggestions = [{name: 'sugar bombs'}];
      element.focused = true;
      // When tabComplete is false, do not focus.
      element.tabComplete = false;
      focusSpy = sinon.spy(element, 'focus');
      element.updateComplete;
      assert.isFalse(suggestionsEl().isHidden);

      MockInteractions.pressAndReleaseKeyOn(
        queryAndAssert(suggestionsEl(), 'li:first-child'),
        9,
        null,
        'Tab'
      );
      await element.updateComplete;
      assert.isFalse(commitSpy.called);
      assert.isFalse(element.focused);
    });

    test('tab on suggestion, tabComplete = true', async () => {
      element.suggestions = [{name: 'sugar bombs'}];
      element.focused = true;
      // When tabComplete is true, focus.
      element.tabComplete = true;
      focusSpy = sinon.spy(element, 'focus');
      element.updateComplete;
      assert.isFalse(suggestionsEl().isHidden);

      MockInteractions.pressAndReleaseKeyOn(
        queryAndAssert(suggestionsEl(), 'li:first-child'),
        9,
        null,
        'Tab'
      );
      await element.updateComplete;

      assert.isTrue(commitSpy.called);
      assert.isTrue(element.focused);
    });

    test('tap on suggestion commits, does not call focus', () => {
      focusSpy = sinon.spy(element, 'focus');
      element.focused = true;
      element.suggestions = [{name: 'first suggestion'}];
      element.updateComplete;
      assert.isFalse(suggestionsEl().isHidden);
      MockInteractions.tap(queryAndAssert(suggestionsEl(), 'li:first-child'));
      element.updateComplete;

      assert.isFalse(focusSpy.called);
      assert.isTrue(commitSpy.called);
      assert.isTrue(suggestionsEl().isHidden);
    });
  });

  test('input-keydown event fired', () => {
    const listener = sinon.spy();
    element.addEventListener('input-keydown', listener);
    MockInteractions.pressAndReleaseKeyOn(inputEl(), 9, null, 'tab');
    element.updateComplete;
    assert.isTrue(listener.called);
  });

  test('enter with modifier does not complete', () => {
    const handleSpy = sinon.spy(element, 'handleKeydown');
    const commitStub = sinon.stub(element, '_handleInputCommit');
    MockInteractions.pressAndReleaseKeyOn(inputEl(), 13, 'ctrl', 'enter');
    assert.isTrue(handleSpy.called);
    assert.isFalse(commitStub.called);
    MockInteractions.pressAndReleaseKeyOn(inputEl(), 13, null, 'enter');
    assert.isTrue(commitStub.called);
  });

  suite('warnUncommitted', () => {
    let inputClassList: DOMTokenList;
    setup(() => {
      inputClassList = inputEl().classList;
    });

    test('enabled', () => {
      element.warnUncommitted = true;
      element.text = 'blah blah blah';
      MockInteractions.blur(inputEl());
      assert.isTrue(inputClassList.contains('warnUncommitted'));
      MockInteractions.focus(inputEl());
      assert.isFalse(inputClassList.contains('warnUncommitted'));
    });

    test('disabled', () => {
      element.warnUncommitted = false;
      element.text = 'blah blah blah';
      MockInteractions.blur(inputEl());
      assert.isFalse(inputClassList.contains('warnUncommitted'));
    });

    test('no text', () => {
      element.warnUncommitted = true;
      element.text = '';
      MockInteractions.blur(inputEl());
      assert.isFalse(inputClassList.contains('warnUncommitted'));
    });
  });
});
