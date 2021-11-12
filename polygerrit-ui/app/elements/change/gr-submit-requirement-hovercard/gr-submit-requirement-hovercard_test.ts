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
import './gr-submit-requirement-hovercard';
import {GrSubmitRequirementHovercard} from './gr-submit-requirement-hovercard';
import {createAccountWithId, createChange, createSubmitRequirementResultInfo} from '../../../test/test-data-generators';

suite('gr-submit-requirement-hovercard tests', () => {
  test('renders', async () => {
    const element = await fixture<GrSubmitRequirementHovercard>(
      html`<gr-submit-requirement-hovercard
      .requirement="${createSubmitRequirementResultInfo()}"
      .change=${createChange()}
      .account="${createAccountWithId()}"
    ></gr-submit-requirement-hovercard>`
    );
    expect(element).shadowDom.to.equal(`<div
        id="container"
        role="tooltip"
        tabindex="-1"
      >
        <div class="section">
          <div class="sectionIcon">
            <iron-icon
              class="check"
              icon="gr-icons:check"
            >
            </iron-icon>
          </div>
          <div class="sectionContent">
            <h3 class="heading-3 name">
              <span>
                Verified
              </span>
            </h3>
          </div>
        </div>
        <div class="section">
          <div class="sectionIcon">
            <iron-icon
              class="small"
              icon="gr-icons:info-outline"
            >
            </iron-icon>
          </div>
          <div class="sectionContent">
            <div class="row">
              <div class="title">
                Status
              </div>
              <div>
                SATISFIED
              </div>
            </div>
          </div>
        </div>
        <div class="section">
          <div class="sectionIcon">
          </div>
          <div class="row">
            <div>
              <gr-label-info>
              </gr-label-info>
            </div>
          </div>
        </div>
        <div class="showConditions">
          <gr-button
            aria-disabled="false"
            class="showConditions"
            link=""
            role="button"
            tabindex="0"
          >
            View condition
            <iron-icon icon="gr-icons:expand-more">
            </iron-icon>
          </gr-button>
        </div>
      </div>
      `);
  });
});
