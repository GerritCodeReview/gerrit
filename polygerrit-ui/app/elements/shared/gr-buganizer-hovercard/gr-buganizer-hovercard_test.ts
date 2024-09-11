/**
 * @license
 * Copyright 2024 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import {fixture, assert} from '@open-wc/testing';
import {html} from 'lit';
import './gr-buganizer-hovercard';
import {GrBuganizerHovercard} from './gr-buganizer-hovercard';
import {queryAndAssert} from '../../../test/test-utils';

suite('gr-buganizer-hovercard tests', () => {
  let element: GrBuganizerHovercard;

  setup(async () => {
    element = await fixture<GrBuganizerHovercard>(
      html`<gr-buganizer-hovercard
        issueNumber="12345"
        match="bug: 12345"
      ></gr-buganizer-hovercard>`
    );
    await element.updateComplete;
  });

  test('hovercard is none by default', async () => {
    const hovercard = queryAndAssert(element, '.hovercard');
    assert.equal(getComputedStyle(hovercard).display, 'none');
  });

  test('renders issue number in link', () => {
    // eslint-disable-next-line @typescript-eslint/no-unnecessary-type-assertion
    const link = queryAndAssert(element, 'a') as HTMLAnchorElement;
    assert.equal(link.innerText.trim(), 'b/12345');
  });
});
