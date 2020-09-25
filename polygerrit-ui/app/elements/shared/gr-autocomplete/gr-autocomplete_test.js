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

import '../../../test/common-test-setup-karma.js';
import './gr-autocomplete.js';
import {html} from '@polymer/polymer/lib/utils/html-tag.js';
import {flush as flush$0} from '@polymer/polymer/lib/legacy/polymer.dom.js';

const basicFixture = fixtureFromTemplate(
    html`<gr-autocomplete no-debounce></gr-autocomplete>`);

suite('gr-autocomplete tests', () => {
  let element;

  const focusOnInput = element => {
    MockInteractions.pressAndReleaseKeyOn(element.$.input, 13, null,
        'enter');
  };

  setup(() => {
    element = basicFixture.instantiate();
  });

  test('renders', () => {
    let promise;
    const queryStub = sinon.spy(input => promise = Promise.resolve([
      {name: input + ' 0', value: 0},
      {name: input + ' 1', value: 1},
      {name: input + ' 2', value: 2},
      {name: input + ' 3', value: 3},
      {name: input + ' 4', value: 4},
    ]));
    element.query = queryStub;
    assert.isTrue(element.$.suggestions.isHidden);
    assert.equal(element.$.suggestions.$.cursor.index, -1);

    focusOnInput(element);
    element.text = 'blah';

    assert.isTrue(queryStub.called);
    element._focused = true;

    return promise.then(() => {
      assert.isFalse(element.$.suggestions.isHidden);
      const suggestions =
          element.$.suggestions.root.querySelectorAll('li');
      assert.equal(suggestions.length, 5);

      for (let i = 0; i < 5; i++) {
        assert.equal(suggestions[i].innerText.trim(), 'blah ' + i);
      }

      assert.notEqual(element.$.suggestions.$.cursor.index, -1);
    });
  });

  test('selectAll', async () => {
    await flush();
    const nativeInput = element._nativeInput;
    const selectionStub = sinon.stub(nativeInput, 'setSelectionRange');

    element.selectAll();
    assert.isFalse(selectionStub.called);

    element.$.input.value = 'test';
    element.selectAll();
    assert.isTrue(selectionStub.called);
  });

  test('esc key behavior', done => {
    let promise;
    const queryStub = sinon.spy(() => promise = Promise.resolve([
      {name: 'blah', value: 123},
    ]));
    element.query = queryStub;

    assert.isTrue(element.$.suggestions.isHidden);

    element._focused = true;
    element.text = 'blah';

    promise.then(() => {
      assert.isFalse(element.$.suggestions.isHidden);

      const cancelHandler = sinon.spy();
      element.addEventListener('cancel', cancelHandler);

      MockInteractions.pressAndReleaseKeyOn(element.$.input, 27, null, 'esc');
      assert.isFalse(cancelHandler.called);
      assert.isTrue(element.$.suggestions.isHidden);
      assert.equal(element._suggestions.length, 0);

      MockInteractions.pressAndReleaseKeyOn(element.$.input, 27, null, 'esc');
      assert.isTrue(cancelHandler.called);
      done();
    });
  });

  test('emits commit and handles cursor movement', done => {
    let promise;
    const queryStub = sinon.spy(input => promise = Promise.resolve([
      {name: input + ' 0', value: 0},
      {name: input + ' 1', value: 1},
      {name: input + ' 2', value: 2},
      {name: input + ' 3', value: 3},
      {name: input + ' 4', value: 4},
    ]));
    element.query = queryStub;

    assert.isTrue(element.$.suggestions.isHidden);
    assert.equal(element.$.suggestions.$.cursor.index, -1);
    element._focused = true;
    element.text = 'blah';

    promise.then(() => {
      assert.isFalse(element.$.suggestions.isHidden);

      const commitHandler = sinon.spy();
      element.addEventListener('commit', commitHandler);

      assert.equal(element.$.suggestions.$.cursor.index, 0);

      MockInteractions.pressAndReleaseKeyOn(element.$.input, 40, null,
          'down');

      assert.equal(element.$.suggestions.$.cursor.index, 1);

      MockInteractions.pressAndReleaseKeyOn(element.$.input, 40, null,
          'down');

      assert.equal(element.$.suggestions.$.cursor.index, 2);

      MockInteractions.pressAndReleaseKeyOn(element.$.input, 38, null, 'up');

      assert.equal(element.$.suggestions.$.cursor.index, 1);

      MockInteractions.pressAndReleaseKeyOn(element.$.input, 13, null,
          'enter');

      assert.equal(element.value, 1);
      assert.isTrue(commitHandler.called);
      assert.equal(commitHandler.getCall(0).args[0].detail.value, 1);
      assert.isTrue(element.$.suggestions.isHidden);
      assert.isTrue(element._focused);
      done();
    });
  });

  test('clear-on-commit behavior (off)', done => {
    let promise;
    const queryStub = sinon.spy(() => {
      promise = Promise.resolve([{name: 'suggestion', value: 0}]);
      return promise;
    });
    element.query = queryStub;
    focusOnInput(element);
    element.text = 'blah';

    promise.then(() => {
      const commitHandler = sinon.spy();
      element.addEventListener('commit', commitHandler);

      MockInteractions.pressAndReleaseKeyOn(element.$.input, 13, null,
          'enter');

      assert.isTrue(commitHandler.called);
      assert.equal(element.text, 'suggestion');
      done();
    });
  });

  test('clear-on-commit behavior (on)', done => {
    let promise;
    const queryStub = sinon.spy(() => {
      promise = Promise.resolve([{name: 'suggestion', value: 0}]);
      return promise;
    });
    element.query = queryStub;
    focusOnInput(element);
    element.text = 'blah';
    element.clearOnCommit = true;

    promise.then(() => {
      const commitHandler = sinon.spy();
      element.addEventListener('commit', commitHandler);

      MockInteractions.pressAndReleaseKeyOn(element.$.input, 13, null,
          'enter');

      assert.isTrue(commitHandler.called);
      assert.equal(element.text, '');
      done();
    });
  });

  test('threshold guards the query', () => {
    const queryStub = sinon.spy(() => Promise.resolve([]));
    element.query = queryStub;
    element.threshold = 2;
    focusOnInput(element);
    element.text = 'a';
    assert.isFalse(queryStub.called);
    element.text = 'ab';
    assert.isTrue(queryStub.called);
  });

  test('noDebounce=false debounces the query', () => {
    const queryStub = sinon.spy(() => Promise.resolve([]));
    let callback;
    const debounceStub = sinon.stub(element, 'debounce').callsFake(
        (name, cb) => { callback = cb; });
    element.query = queryStub;
    element.noDebounce = false;
    focusOnInput(element);
    element.text = 'a';
    assert.isFalse(queryStub.called);
    assert.isTrue(debounceStub.called);
    assert.equal(debounceStub.lastCall.args[2], 200);
    assert.isFunction(callback);
    callback();
    assert.isTrue(queryStub.called);
  });

  test('_computeClass respects border property', () => {
    assert.equal(element._computeClass(), '');
    assert.equal(element._computeClass(false), '');
    assert.equal(element._computeClass(true), 'borderless');
  });

  test('undefined or empty text results in no suggestions', () => {
    element._updateSuggestions(undefined, 0, null);
    assert.equal(element._suggestions.length, 0);
  });

  test('when focused', done => {
    let promise;
    const queryStub = sinon.stub()
        .returns(promise = Promise.resolve([
          {name: 'suggestion', value: 0},
        ]));
    element.query = queryStub;
    element.suggestOnlyWhenFocus = true;
    focusOnInput(element);
    element.text = 'bla';
    assert.equal(element._focused, true);
    flush();
    promise.then(() => {
      assert.equal(element._suggestions.length, 1);
      assert.equal(queryStub.notCalled, false);
      done();
    });
  });

  test('when not focused', done => {
    let promise;
    const queryStub = sinon.stub()
        .returns(promise = Promise.resolve([
          {name: 'suggestion', value: 0},
        ]));
    element.query = queryStub;
    element.suggestOnlyWhenFocus = true;
    element.text = 'bla';
    assert.equal(element._focused, false);
    flush();
    promise.then(() => {
      assert.equal(element._suggestions.length, 0);
      done();
    });
  });

  test('suggestions should not carry over', done => {
    let promise;
    const queryStub = sinon.stub()
        .returns(promise = Promise.resolve([
          {name: 'suggestion', value: 0},
        ]));
    element.query = queryStub;
    focusOnInput(element);
    element.text = 'bla';
    flush();
    promise.then(() => {
      assert.equal(element._suggestions.length, 1);
      element._updateSuggestions('', 0, false);
      assert.equal(element._suggestions.length, 0);
      done();
    });
  });

  test('multi completes only the last part of the query', done => {
    let promise;
    const queryStub = sinon.stub()
        .returns(promise = Promise.resolve([
          {name: 'suggestion', value: 0},
        ]));
    element.query = queryStub;
    focusOnInput(element);
    element.text = 'blah blah';
    element.multi = true;

    promise.then(() => {
      const commitHandler = sinon.spy();
      element.addEventListener('commit', commitHandler);

      MockInteractions.pressAndReleaseKeyOn(element.$.input, 13, null,
          'enter');

      assert.isTrue(commitHandler.called);
      assert.equal(element.text, 'blah 0');
      done();
    });
  });

  test('tabComplete flag functions', () => {
    // commitHandler checks for the commit event, whereas commitSpy checks for
    // the _commit function of the element.
    const commitHandler = sinon.spy();
    element.addEventListener('commit', commitHandler);
    const commitSpy = sinon.spy(element, '_commit');
    element._focused = true;

    element._suggestions = ['tunnel snakes rule!'];
    element.tabComplete = false;
    MockInteractions.pressAndReleaseKeyOn(element.$.input, 9, null, 'tab');
    assert.isFalse(commitHandler.called);
    assert.isFalse(commitSpy.called);
    assert.isFalse(element._focused);

    element.tabComplete = true;
    element._focused = true;
    MockInteractions.pressAndReleaseKeyOn(element.$.input, 9, null, 'tab');
    assert.isFalse(commitHandler.called);
    assert.isTrue(commitSpy.called);
    assert.isTrue(element._focused);
  });

  test('_focused flag properly triggered', () => {
    flush();
    assert.isFalse(element._focused);
    const input = element.shadowRoot
        .querySelector('paper-input').inputElement;
    MockInteractions.focus(input);
    assert.isTrue(element._focused);
  });

  test('search icon shows with showSearchIcon property', () => {
    flush();
    assert.equal(getComputedStyle(element.shadowRoot
        .querySelector('iron-icon')).display,
    'none');
    element.showSearchIcon = true;
    assert.notEqual(getComputedStyle(element.shadowRoot
        .querySelector('iron-icon')).display,
    'none');
  });

  test('vertical offset overridden by param if it exists', () => {
    assert.equal(element.$.suggestions.verticalOffset, 31);
    element.verticalOffset = 30;
    assert.equal(element.$.suggestions.verticalOffset, 30);
  });

  test('_focused flag shows/hides the suggestions', () => {
    const openStub = sinon.stub(element.$.suggestions, 'open');
    const closedStub = sinon.stub(element.$.suggestions, 'close');
    element._suggestions = ['hello', 'its me'];
    assert.isFalse(openStub.called);
    assert.isTrue(closedStub.calledOnce);
    element._focused = true;
    assert.isTrue(openStub.calledOnce);
    element._suggestions = [];
    assert.isTrue(closedStub.calledTwice);
    assert.isTrue(openStub.calledOnce);
  });

  test('_handleInputCommit with autocomplete hidden does nothing without' +
        'without allowNonSuggestedValues', () => {
    const commitStub = sinon.stub(element, '_commit');
    element.$.suggestions.isHidden = true;
    element._handleInputCommit();
    assert.isFalse(commitStub.called);
  });

  test('_handleInputCommit with autocomplete hidden with' +
        'allowNonSuggestedValues', () => {
    const commitStub = sinon.stub(element, '_commit');
    element.allowNonSuggestedValues = true;
    element.$.suggestions.isHidden = true;
    element._handleInputCommit();
    assert.isTrue(commitStub.called);
  });

  test('_handleInputCommit with autocomplete open calls commit', () => {
    const commitStub = sinon.stub(element, '_commit');
    element.$.suggestions.isHidden = false;
    element._handleInputCommit();
    assert.isTrue(commitStub.calledOnce);
  });

  test('_handleInputCommit with autocomplete open calls commit' +
        'with allowNonSuggestedValues', () => {
    const commitStub = sinon.stub(element, '_commit');
    element.allowNonSuggestedValues = true;
    element.$.suggestions.isHidden = false;
    element._handleInputCommit();
    assert.isTrue(commitStub.calledOnce);
  });

  test('issue 8655', () => {
    function makeSuggestion(s) { return {name: s, text: s, value: s}; }
    const keydownSpy = sinon.spy(element, '_handleKeydown');
    element.setText('file:');
    element._suggestions =
        [makeSuggestion('file:'), makeSuggestion('-file:')];
    MockInteractions.pressAndReleaseKeyOn(element.$.input, 88, null, 'x');
    // Must set the value, because the MockInteraction does not.
    element.$.input.value = 'file:x';
    assert.isTrue(keydownSpy.calledOnce);
    MockInteractions.pressAndReleaseKeyOn(
        element.$.input,
        13,
        null,
        'enter'
    );
    assert.isTrue(keydownSpy.calledTwice);
    assert.equal(element.text, 'file:x');
  });

  suite('focus', () => {
    let commitSpy;
    let focusSpy;

    setup(() => {
      commitSpy = sinon.spy(element, '_commit');
    });

    test('enter does not call focus', () => {
      element._suggestions = ['sugar bombs'];
      focusSpy = sinon.spy(element, 'focus');
      MockInteractions.pressAndReleaseKeyOn(element.$.input, 13, null,
          'enter');
      flush();

      assert.isTrue(commitSpy.called);
      assert.isFalse(focusSpy.called);
      assert.equal(element._suggestions.length, 0);
    });

    test('tab in input, tabComplete = true', () => {
      focusSpy = sinon.spy(element, 'focus');
      const commitHandler = sinon.stub();
      element.addEventListener('commit', commitHandler);
      element.tabComplete = true;
      element._suggestions = ['tunnel snakes drool'];
      MockInteractions.pressAndReleaseKeyOn(element.$.input, 9, null, 'tab');
      flush();

      assert.isTrue(commitSpy.called);
      assert.isTrue(focusSpy.called);
      assert.isFalse(commitHandler.called);
      assert.equal(element._suggestions.length, 0);
    });

    test('tab in input, tabComplete = false', () => {
      element._suggestions = ['sugar bombs'];
      focusSpy = sinon.spy(element, 'focus');
      MockInteractions.pressAndReleaseKeyOn(element.$.input, 9, null, 'tab');
      flush();

      assert.isFalse(commitSpy.called);
      assert.isFalse(focusSpy.called);
      assert.equal(element._suggestions.length, 1);
    });

    test('tab on suggestion, tabComplete = false', () => {
      element._suggestions = [{name: 'sugar bombs'}];
      element._focused = true;
      // When tabComplete is false, do not focus.
      element.tabComplete = false;
      focusSpy = sinon.spy(element, 'focus');
      flush$0();
      assert.isFalse(element.$.suggestions.isHidden);

      MockInteractions.pressAndReleaseKeyOn(
          element.$.suggestions.shadowRoot
              .querySelector('li:first-child'), 9, null, 'tab');
      flush();
      assert.isFalse(commitSpy.called);
      assert.isFalse(element._focused);
    });

    test('tab on suggestion, tabComplete = true', () => {
      element._suggestions = [{name: 'sugar bombs'}];
      element._focused = true;
      // When tabComplete is true, focus.
      element.tabComplete = true;
      focusSpy = sinon.spy(element, 'focus');
      flush$0();
      assert.isFalse(element.$.suggestions.isHidden);

      MockInteractions.pressAndReleaseKeyOn(
          element.$.suggestions.shadowRoot
              .querySelector('li:first-child'), 9, null, 'tab');
      flush();

      assert.isTrue(commitSpy.called);
      assert.isTrue(element._focused);
    });

    test('tap on suggestion commits, does not call focus', () => {
      focusSpy = sinon.spy(element, 'focus');
      element._focused = true;
      element._suggestions = [{name: 'first suggestion'}];
      flush$0();
      assert.isFalse(element.$.suggestions.isHidden);
      MockInteractions.tap(element.$.suggestions.shadowRoot
          .querySelector('li:first-child'));
      flush();

      assert.isFalse(focusSpy.called);
      assert.isTrue(commitSpy.called);
      assert.isTrue(element.$.suggestions.isHidden);
    });
  });

  test('input-keydown event fired', () => {
    const listener = sinon.spy();
    element.addEventListener('input-keydown', listener);
    MockInteractions.pressAndReleaseKeyOn(element.$.input, 9, null, 'tab');
    flush();
    assert.isTrue(listener.called);
  });

  test('enter with modifier does not complete', () => {
    const handleSpy = sinon.spy(element, '_handleKeydown');
    const commitStub = sinon.stub(element, '_handleInputCommit');
    MockInteractions.pressAndReleaseKeyOn(
        element.$.input, 13, 'ctrl', 'enter');
    assert.isTrue(handleSpy.called);
    assert.isFalse(commitStub.called);
    MockInteractions.pressAndReleaseKeyOn(
        element.$.input, 13, null, 'enter');
    assert.isTrue(commitStub.called);
  });

  suite('warnUncommitted', () => {
    let inputClassList;
    setup(() => {
      inputClassList = element.$.input.classList;
    });

    test('enabled', () => {
      element.warnUncommitted = true;
      element.text = 'blah blah blah';
      MockInteractions.blur(element.$.input);
      assert.isTrue(inputClassList.contains('warnUncommitted'));
      MockInteractions.focus(element.$.input);
      assert.isFalse(inputClassList.contains('warnUncommitted'));
    });

    test('disabled', () => {
      element.warnUncommitted = false;
      element.text = 'blah blah blah';
      MockInteractions.blur(element.$.input);
      assert.isFalse(inputClassList.contains('warnUncommitted'));
    });

    test('no text', () => {
      element.warnUncommitted = true;
      element.text = '';
      MockInteractions.blur(element.$.input);
      assert.isFalse(inputClassList.contains('warnUncommitted'));
    });
  });
});
