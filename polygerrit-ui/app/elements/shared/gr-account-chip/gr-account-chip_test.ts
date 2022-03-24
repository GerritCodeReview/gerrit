/**
 * @license
 * Copyright (C) 2021 The Android Open Source Project
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
import {fixture} from '@open-wc/testing-helpers';
import {html} from 'lit';
import './gr-account-chip';
import {GrAccountChip} from './gr-account-chip';
import {
  createAccountWithIdNameAndEmail,
  createChange,
} from '../../../test/test-data-generators';

suite('gr-account-chip tests', () => {
  let element: GrAccountChip;
  setup(async () => {
    const reviewer = createAccountWithIdNameAndEmail();
    const change = createChange();
    element = await fixture<GrAccountChip>(html`<gr-account-chip
      .account=${reviewer}
      .change=${change}
    ></gr-account-chip>`);
  });

  test('renders', () => {
    expect(element).shadowDom.to.equal(/* HTML */ `
      <div class="container">
        <div>
          <gr-account-label clickable="" deselected=""></gr-account-label>
        </div>
        <slot name="vote-chip"></slot>
        <gr-button
          aria-disabled="false"
          aria-label="Remove"
          class="remove"
          hidden=""
          id="remove"
          link=""
          role="button"
          tabindex="0"
        >
          <iron-icon icon="gr-icons:close"></iron-icon>
        </gr-button>
      </div>
    `);
  });
});
