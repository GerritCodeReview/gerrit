/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import {fixture, html, assert} from '@open-wc/testing';
import {GrSummaryChip, SummaryChipStyles} from './gr-summary-chip';
import {CommentTabState} from '../../../types/events';

suite('gr-summary-chip test', () => {
  let element: GrSummaryChip;
  setup(async () => {
    element = await fixture(html`<gr-summary-chip
      styleType=${SummaryChipStyles.WARNING}
      category=${CommentTabState.DRAFTS}
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
});
