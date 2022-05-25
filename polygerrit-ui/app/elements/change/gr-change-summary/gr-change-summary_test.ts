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
import {GrChangeSummary} from './gr-change-summary';

suite('gr-change-summary test', () => {
  let element: GrChangeSummary;
  setup(async () => {
    element = await fixture(html`<gr-change-summary></gr-change-summary>`);
  });

  test('is defined', () => {
    const el = document.createElement('gr-change-summary');
    assert.instanceOf(el, GrChangeSummary);
  });

  test('renders', () => {
    expect(element).shadowDom.to.equal(/* HTML */ `<div>
      <table>
        <tbody>
          <tr>
            <td class="key">Comments</td>
            <td class="value">
              <span class="zeroState"> No comments </span>
              <gr-summary-chip
                category="drafts"
                hidden=""
                icon="edit"
                styletype="warning"
              >
              </gr-summary-chip>
              <gr-summary-chip
                category="unresolved"
                hidden=""
                styletype="warning"
              >
                0 unresolved
              </gr-summary-chip>
              <gr-summary-chip
                category="show all"
                hidden=""
                icon="markChatRead"
                styletype="check"
              >
                0 resolved
              </gr-summary-chip>
            </td>
          </tr>
          <tr hidden="">
            <td class="key">Checks</td>
            <td class="value">
              <div class="checksSummary">
                <span class="loading zeroState" role="status">
                  No results
                </span>
                <span class="loadingSpin" hidden=""> </span>
              </div>
            </td>
          </tr>
          <tr hidden="">
            <td class="key">Findings</td>
            <td class="value"></td>
          </tr>
        </tbody>
      </table>
    </div>`);
  });
});
