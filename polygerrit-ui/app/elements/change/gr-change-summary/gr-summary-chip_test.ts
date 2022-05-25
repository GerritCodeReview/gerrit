/**
 * @license
 * Copyright (C) 2020 The Android Open Source Project
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
