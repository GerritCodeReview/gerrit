/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import * as sinon from 'sinon';
import '../../../test/common-test-setup';
import './gr-linked-chip';
import {GrLinkedChip} from './gr-linked-chip';
import {queryAndAssert, waitEventLoop} from '../../../test/test-utils';
import {assert, fixture, html} from '@open-wc/testing';
import {GrButton} from '../gr-button/gr-button';

suite('gr-linked-chip tests', () => {
  let element: GrLinkedChip;

  setup(async () => {
    element = await fixture(html`<gr-linked-chip></gr-linked-chip>`);
  });

  test('renders', () => {
    assert.shadowDom.equal(
      element,
      /* HTML */ `<div class="container">
        <a href=""> <gr-limited-text> </gr-limited-text> </a>
        <slot></slot>
        <gr-button
          aria-disabled="false"
          class="remove"
          hidden=""
          id="remove"
          link=""
          role="button"
          tabindex="0"
        >
          <gr-icon icon="close"></gr-icon>
        </gr-button>
      </div>`
    );
  });

  test('remove fired', async () => {
    const spy = sinon.spy();
    element.addEventListener('remove', spy);
    await waitEventLoop();
    queryAndAssert<GrButton>(element, '#remove').click();
    assert.isTrue(spy.called);
  });

  test('renders slotted content', async () => {
    const slottedEl = await fixture<GrLinkedChip>(
      html`<gr-linked-chip><span id="extra">extra</span></gr-linked-chip>`
    );
    const slot = queryAndAssert<HTMLSlotElement>(slottedEl, 'slot');
    const assigned = slot.assignedElements();
    assert.equal(assigned.length, 1);
    assert.equal(assigned[0].id, 'extra');
  });
});
