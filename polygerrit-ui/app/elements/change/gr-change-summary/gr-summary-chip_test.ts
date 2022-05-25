/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup-karma';
import {fixture, html} from '@open-wc/testing-helpers';
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
    expect(element).shadowDom.to.equal(/* HTML */ `<button
      class="font-small summaryChip warning"
    >
      <slot> </slot>
    </button>`);
  });
});
