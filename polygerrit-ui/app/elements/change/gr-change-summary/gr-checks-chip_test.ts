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
import {GrChecksChip} from './gr-checks-chip';
import {Category} from '../../../api/checks';

suite('gr-checks-chip test', () => {
  let element: GrChecksChip;
  setup(async () => {
    element = await fixture(html`<gr-checks-chip
      .statusOrCategory=${Category.SUCCESS}
      .text=${'0'}
    ></gr-checks-chip>`);
  });

  test('is defined', () => {
    const el = document.createElement('gr-checks-chip');
    assert.instanceOf(el, GrChecksChip);
  });

  test('renders', () => {
    expect(element).shadowDom.to.equal(/* HTML */ `<div
      aria-label="0 success result"
      class="check-circle-outline checksChip font-small"
      role="link"
      tabindex="0"
    >
      <iron-icon icon="gr-icons:check-circle-outline"> </iron-icon>
      <div class="text">0</div>
    </div>`);
  });
});
