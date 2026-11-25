/**
 * @license
 * Copyright 2026 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import * as sinon from 'sinon';
import '../../test/common-test-setup';
import './gr-checks-progress';
import {assert, fixture, html} from '@open-wc/testing';
import {SinonFakeTimers} from 'sinon';
import {GrChecksProgress} from './gr-checks-progress';

const MINUTE = 60 * 1000;
const NOW = new Date('2026-01-01T10:00:00').getTime();
const LENGTH = 2 * Math.PI * 6;

suite('gr-checks-progress test', () => {
  let element: GrChecksProgress;
  let clock: SinonFakeTimers;

  setup(async () => {
    clock = sinon.useFakeTimers({
      now: NOW,
      toFake: ['Date', 'setInterval', 'clearInterval'],
    });
    element = await fixture(html`<gr-checks-progress
      .startedTimestamp=${new Date(NOW - 15 * MINUTE)}
      .finishedTimestamp=${new Date(NOW + 5 * MINUTE)}
    ></gr-checks-progress>`);
  });

  teardown(() => {
    clock.restore();
  });

  test('renders', () => {
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <svg
          aria-label="75% done, ETA: 5 minutes"
          role="img"
          viewBox="0 0 16 16"
        >
          <title>75% done, ETA: 5 minutes</title>
          <circle class="track" cx="8" cy="8" r="6"></circle>
          <circle
            class="done"
            cx="8"
            cy="8"
            r="6"
            stroke-dasharray="${0.75 * LENGTH} ${LENGTH}"
          ></circle>
        </svg>
      `
    );
  });

  test('moves on its own', async () => {
    clock.tick(5 * MINUTE - 10 * 1000);
    await element.updateComplete;
    const label = element.shadowRoot!.querySelector('svg')!.ariaLabel;
    assert.equal(label, '99% done, ETA: 10 seconds');
  });

  test('is full and late past the estimate', async () => {
    clock.tick(6 * MINUTE);
    await element.updateComplete;
    const svg = element.shadowRoot!.querySelector('svg')!;
    assert.equal(svg.ariaLabel, 'Running longer than estimated');
    const ring = svg.querySelector('circle:not(.track)')!;
    assert.equal(ring.getAttribute('class'), 'late');
    assert.equal(ring.getAttribute('stroke-dasharray'), `${LENGTH} ${LENGTH}`);
  });

  test('renders nothing without an estimate', async () => {
    element.finishedTimestamp = undefined;
    await element.updateComplete;
    assert.shadowDom.equal(element, '');
  });
});
