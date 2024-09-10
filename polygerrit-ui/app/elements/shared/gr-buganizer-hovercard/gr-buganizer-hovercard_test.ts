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


  test('hovering over link shows hovercard', async () => {
    const link = queryAndAssert(element, 'a');
    const hovercard = queryAndAssert(element, '.hovercard');
    assert.equal(getComputedStyle(hovercard).display, 'none');
    link.dispatchEvent(new MouseEvent('mouseenter'));
    await element.updateComplete;
    assert.notEqual(getComputedStyle(hovercard).display, 'none');
  });

  test('hovering out of link hides hovercard', async () => {
    const link = queryAndAssert(element, 'a');
    const hovercard = queryAndAssert(element, '.hovercard');
    link.dispatchEvent(new MouseEvent('mouseenter'));
    await element.updateComplete;
    assert.notEqual(getComputedStyle(hovercard).display, 'none');
    link.dispatchEvent(new MouseEvent('mouseleave'));
    await element.updateComplete;
    assert.equal(getComputedStyle(hovercard).display, 'none');
  });
});
