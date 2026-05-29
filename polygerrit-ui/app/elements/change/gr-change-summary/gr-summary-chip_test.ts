/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import {assert, fixture, html} from '@open-wc/testing';
import {GrSummaryChip, SummaryChipStyles} from './gr-summary-chip';
import {CommentTabState} from '../../../types/events';

suite('gr-summary-chip test', () => {
  let element: GrSummaryChip;
  setup(async () => {
    element = await fixture(html`<gr-summary-chip
      .styleType=${SummaryChipStyles.WARNING}
      .category=${CommentTabState.DRAFTS}
      clickable
    ></gr-summary-chip>`);
  });
  test('is defined', () => {
    const el = document.createElement('gr-summary-chip');
    assert.instanceOf(el, GrSummaryChip);
  });

  test('renders', () => {
    assert.shadowDom.equal(
      element,
      /* HTML */ `<button class="font-small summaryChip warning">
        <slot> </slot>
      </button>`
    );
  });

  test('renders as not clickable', async () => {
    const element = await fixture(html`<gr-summary-chip
      .styleType=${SummaryChipStyles.CHECK}
      .category=${CommentTabState.SHOW_ALL}
    ></gr-summary-chip>`);
    assert.shadowDom.equal(
      element,
      /* HTML */ `<span class="check font-small summaryChip">
        <slot> </slot>
      </span>`
    );
  });
});
