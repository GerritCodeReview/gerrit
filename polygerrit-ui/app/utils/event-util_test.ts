/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../test/common-test-setup';
import {assert} from '@open-wc/testing';
import {
  fire,
  fireAlert,
  fireError,
  fireNoBubble,
  fireNoBubbleNoCompose,
  waitForEventOnce,
} from './event-util';

declare global {
  interface HTMLElementEventMap {
    'custom-event': CustomEvent<{message: string}>;
  }
}

suite('event-util tests', () => {
  let target: HTMLElement;

  setup(() => {
    target = document.createElement('div');
    document.body.appendChild(target);
  });

  teardown(() => {
    document.body.removeChild(target);
  });

  test('fire dispatches custom event with correct properties', () => {
    const detail = {message: 'test'};
    let receivedEvent: CustomEvent | undefined;

    target.addEventListener('custom-event', (e: Event) => {
      receivedEvent = e as CustomEvent;
    });

    fire(target, 'custom-event', detail);

    assert.isTrue(receivedEvent?.bubbles);
    assert.isTrue(receivedEvent?.composed);
    assert.deepEqual(receivedEvent?.detail, detail);
  });

  test('fireNoBubble dispatches non-bubbling event', () => {
    const detail = {message: 'test'};
    let receivedEvent: CustomEvent | undefined;

    target.addEventListener('custom-event', (e: Event) => {
      receivedEvent = e as CustomEvent;
    });

    fireNoBubble(target, 'custom-event', detail);

    assert.isFalse(receivedEvent?.bubbles);
    assert.isTrue(receivedEvent?.composed);
    assert.deepEqual(receivedEvent?.detail, detail);
  });

  test('fireNoBubbleNoCompose dispatches non-bubbling and non-composed event', () => {
    const detail = {message: 'test'};
    let receivedEvent: CustomEvent | undefined;

    target.addEventListener('custom-event', (e: Event) => {
      receivedEvent = e as CustomEvent;
    });

    fireNoBubbleNoCompose(target, 'custom-event', detail);

    assert.isFalse(receivedEvent?.bubbles);
    assert.isFalse(receivedEvent?.composed);
    assert.deepEqual(receivedEvent?.detail, detail);
  });

  test('fireAlert dispatches show-alert event with correct detail', () => {
    const message = 'test alert';
    let receivedEvent: CustomEvent | undefined;

    target.addEventListener('show-alert', (e: Event) => {
      receivedEvent = e as CustomEvent;
    });

    fireAlert(target, message);

    assert.deepEqual(receivedEvent?.detail, {
      message,
      showDismiss: true,
    });
  });

  test('fireError dispatches show-error event with correct detail', () => {
    const message = 'test error';
    let receivedEvent: CustomEvent | undefined;

    target.addEventListener('show-error', (e: Event) => {
      receivedEvent = e as CustomEvent;
    });

    fireError(target, message);

    assert.deepEqual(receivedEvent?.detail, {message});
  });

  test('waitForEventOnce resolves with event when fired', async () => {
    const detail = {message: 'test'};

    const eventPromise = waitForEventOnce(target, 'custom-event');
    fire(target, 'custom-event', detail);

    const receivedEvent = await eventPromise;
    assert.deepEqual(receivedEvent.detail, detail);
  });
});
