/**
 * @license
 * Copyright 2024 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../test/common-test-setup';
import './gr-textarea';
import {fixture, html, assert} from '@open-wc/testing';
import {waitForEventOnce} from '../utils/event-util';
import {AUTOCOMPLETE_HINT_CLASS, GrTextarea} from './gr-textarea';
import {CursorPositionChangeEventDetail} from '../api/embed';

async function rafPromise() {
  return new Promise(res => {
    requestAnimationFrame(res);
  });
}

suite('gr-textarea test', () => {
  let element: GrTextarea;

  setup(async () => {
    element = await fixture(html` <gr-textarea> </gr-textarea>`);
  });

  test('text area is registered correctly', () => {
    assert.instanceOf(element, GrTextarea);
  });

  test('when disabled textarea have contenteditable set to false', async () => {
    element.disabled = true;
    await element.updateComplete;

    const editableDiv = element.shadowRoot!.querySelector('.editableDiv');
    await element.updateComplete;

    assert.equal(editableDiv?.getAttribute('contenteditable'), 'false');
  });

  test('when disabled textarea have aria-disabled set', async () => {
    element.disabled = true;
    await element.updateComplete;

    const editableDiv = element.shadowRoot!.querySelector('.editableDiv');
    await element.updateComplete;

    assert.isDefined(editableDiv?.getAttribute('aria-disabled'));
  });

  test('when textarea has placeholder, set aria-placeholder to placeholder text', async () => {
    const placeholder = 'A sample placehodler...';
    element.placeholder = placeholder;
    await element.updateComplete;

    const editableDiv = element.shadowRoot!.querySelector('.editableDiv');
    await element.updateComplete;

    assert.equal(editableDiv?.getAttribute('aria-placeholder'), placeholder);
  });

  test('renders the value', async () => {
    const value = 'Some value';
    element.value = value;
    await element.updateComplete;

    const editableDiv = element.shadowRoot!.querySelector(
      '.editableDiv'
    ) as HTMLDivElement;
    await element.updateComplete;

    assert.equal(editableDiv?.innerText, value);
  });

  test('streams change event when editable div has input event', async () => {
    const value = 'Some value \n other value';
    const INPUT_EVENT = 'input';
    let changeCalled = false;

    element.addEventListener(INPUT_EVENT, () => {
      changeCalled = true;
    });

    const changeEventPromise = waitForEventOnce(element, INPUT_EVENT);
    const editableDiv = element.shadowRoot!.querySelector(
      '.editableDiv'
    ) as HTMLDivElement;

    editableDiv.innerText = value;
    editableDiv.dispatchEvent(new Event('input'));
    await changeEventPromise;

    assert.isTrue(changeCalled);
  });

  test('does not have focus by default', async () => {
    assert.isFalse(element.isFocused);
  });

  test('when focused, isFocused is set to true', async () => {
    await element.focus();
    assert.isTrue(element.isFocused);
  });

  test('when cursor position is set to 0', async () => {
    const CURSOR_POSITION_CHANGE_EVENT = 'cursorPositionChange';
    let cursorPosition = -1;

    const cursorPositionChangeEventPromise = waitForEventOnce(
      element,
      CURSOR_POSITION_CHANGE_EVENT
    );
    element.addEventListener(CURSOR_POSITION_CHANGE_EVENT, (event: Event) => {
      const detail = (event as CustomEvent<CursorPositionChangeEventDetail>)
        .detail;
      cursorPosition = detail.position;
    });

    element.setCursorPosition(0);
    await cursorPositionChangeEventPromise;

    assert.equal(cursorPosition, 0);
  });

  test('when cursor position is set to 1', async () => {
    const CURSOR_POSITION_CHANGE_EVENT = 'cursorPositionChange';
    let cursorPosition = -1;

    const cursorPositionChangeEventPromise = waitForEventOnce(
      element,
      CURSOR_POSITION_CHANGE_EVENT
    );
    element.addEventListener(CURSOR_POSITION_CHANGE_EVENT, (event: Event) => {
      const detail = (event as CustomEvent<CursorPositionChangeEventDetail>)
        .detail;
      cursorPosition = detail.position;
    });

    element.value = 'Some value';
    await element.updateComplete;
    element.setCursorPosition(1);
    await cursorPositionChangeEventPromise;

    assert.equal(cursorPosition, 1);
  });

  test('when cursor position is set to new line', async () => {
    const CURSOR_POSITION_CHANGE_EVENT = 'cursorPositionChange';
    let cursorPosition = -1;

    const cursorPositionChangeEventPromise = waitForEventOnce(
      element,
      CURSOR_POSITION_CHANGE_EVENT
    );
    element.addEventListener(CURSOR_POSITION_CHANGE_EVENT, (event: Event) => {
      const detail = (event as CustomEvent<CursorPositionChangeEventDetail>)
        .detail;
      cursorPosition = detail.position;
    });

    element.value = 'Some \n\n\n value';
    await element.updateComplete;
    element.setCursorPosition(7);
    await cursorPositionChangeEventPromise;

    assert.equal(cursorPosition, 7);
  });

  test('when textarea is empty, placeholder hint is shown', async () => {
    const editableDiv = element.shadowRoot!.querySelector(
      '.editableDiv'
    ) as HTMLDivElement;
    const placeholderHint = 'Some value';

    element.placeholderHint = placeholderHint;
    await element.updateComplete;

    assert.equal(editableDiv?.dataset['placeholder'], placeholderHint);
  });

  test('when TAB is pressed, placeholder hint is added as content', async () => {
    const editableDiv = element.shadowRoot!.querySelector(
      '.editableDiv'
    ) as HTMLDivElement;
    const placeholderHint = 'Some value';

    element.placeholderHint = placeholderHint;
    await element.updateComplete;
    editableDiv.dispatchEvent(new KeyboardEvent('keydown', {key: 'Tab'}));
    await element.updateComplete;

    assert.equal(element.value, placeholderHint);
  });

  test('when cursor is at end, hint is shown', async () => {
    const editableDiv = element.shadowRoot!.querySelector(
      '.editableDiv'
    ) as HTMLDivElement;
    const oldValue = 'Hola';
    const hint = 'amigos';

    element.hint = hint;
    await element.updateComplete;
    element.value = oldValue;
    await element.putCursorAtEnd();
    await element.updateComplete;
    editableDiv.dispatchEvent(new KeyboardEvent('keydown', {key: 'a'}));
    await element.updateComplete;
    await rafPromise();

    const spanHintElement = editableDiv?.querySelector(
      '.' + AUTOCOMPLETE_HINT_CLASS
    ) as HTMLSpanElement;
    const styles = window.getComputedStyle(spanHintElement, ':before');
    assert.equal(styles['content'], '"' + hint + '"');
  });

  test('when TAB is pressed, hint is added as content', async () => {
    const editableDiv = element.shadowRoot!.querySelector(
      '.editableDiv'
    ) as HTMLDivElement;
    const oldValue = 'Hola';
    const hint = 'amigos';

    element.hint = hint;
    element.value = oldValue;
    await element.updateComplete;
    await element.putCursorAtEnd();
    editableDiv.dispatchEvent(new KeyboardEvent('keydown', {key: 'a'}));
    await rafPromise();
    editableDiv.dispatchEvent(new KeyboardEvent('keydown', {key: 'Tab'}));
    await element.updateComplete;

    assert.equal(element.value, oldValue + hint);
  });

  test('when cursor is at end, Mod + ArrowRight does not change cursor position', async () => {
    const CURSOR_POSITION_CHANGE_EVENT = 'cursorPositionChange';
    let cursorPosition = -1;
    const value = 'Hola amigos';
    const editableDiv = element.shadowRoot!.querySelector(
      '.editableDiv'
    ) as HTMLDivElement;
    element.addEventListener(CURSOR_POSITION_CHANGE_EVENT, (event: Event) => {
      const detail = (event as CustomEvent<CursorPositionChangeEventDetail>)
        .detail;
      cursorPosition = detail.position;
    });
    await element.updateComplete;
    element.value = value;
    await element.putCursorAtEnd();
    await element.updateComplete;

    editableDiv.dispatchEvent(
      new KeyboardEvent('keydown', {key: 'ArrowRight', metaKey: true})
    );
    await element.updateComplete;
    await rafPromise();

    assert.equal(cursorPosition, value.length);
  });

  test('when cursor is at 0, Mod + ArrowLeft does not change cursor position', async () => {
    const CURSOR_POSITION_CHANGE_EVENT = 'cursorPositionChange';
    let cursorPosition = -1;
    const value = 'Hola amigos';
    const editableDiv = element.shadowRoot!.querySelector(
      '.editableDiv'
    ) as HTMLDivElement;
    element.addEventListener(CURSOR_POSITION_CHANGE_EVENT, (event: Event) => {
      const detail = (event as CustomEvent<CursorPositionChangeEventDetail>)
        .detail;
      cursorPosition = detail.position;
    });
    await element.updateComplete;
    element.value = value;
    element.setCursorPosition(0);
    await element.updateComplete;

    editableDiv.dispatchEvent(
      new KeyboardEvent('keydown', {key: 'ArrowLeft', metaKey: true})
    );
    await element.updateComplete;
    await rafPromise();

    assert.equal(cursorPosition, 0);
  });
});
