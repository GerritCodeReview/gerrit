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
import {
  createAccountWithId,
  createApproval,
  createChange,
  createDetailedLabelInfo,
  createParsedChange,
  createSubmitRequirementExpressionInfo,
  createSubmitRequirementResultInfo,
} from '../../../test/test-data-generators';
import {ParsedChangeInfo} from '../../../types/types';
import {queryAndAssert} from '../../../test/test-utils';
import {GrButton} from '../../shared/gr-button/gr-button';
import {SubmitRequirementResultInfo} from '../../../api/rest-api';

suite('gr-submit-requirement-hovercard tests', () => {
  let element: GrSubmitRequirementHovercard;

  setup(async () => {
    element = await fixture<GrSubmitRequirementHovercard>(
      html`<gr-submit-requirement-hovercard
        .requirement="${createSubmitRequirementResultInfo()}"
        .change=${createChange()}
        .account="${createAccountWithId()}"
      ></gr-submit-requirement-hovercard>`
    );
  });

  test('renders', async () => {
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
        <div class="button">
          <gr-button
            aria-disabled="false"
            id="toggleConditionsButton"
            link=""
            role="button"
            tabindex="0"
          >
            View conditions
            <iron-icon icon="gr-icons:expand-more">
            </iron-icon>
          </gr-button>
        </div>
      </div>
      `);
  });

  test('renders conditions after click', async () => {
    const button = queryAndAssert<GrButton>(element, '#toggleConditionsButton');
    button.click();
    await element.updateComplete;
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
        <div class="button">
          <gr-button
            aria-disabled="false"
            id="toggleConditionsButton"
            link=""
            role="button"
            tabindex="0"
          >
            Hide conditions
            <iron-icon icon="gr-icons:expand-less">
            </iron-icon>
          </gr-button>
        </div>
        <div class="section condition">
          <div class="sectionContent">
            Blocking condition:
            <br>
            <span class="expression">
              label:Verified=MAX -label:Verified=MIN
            </span>
          </div>
        </div>
      </div>
      `);
  });

  test('renders label', async () => {
    const submitRequirement: SubmitRequirementResultInfo = {
      ...createSubmitRequirementResultInfo(),
      description: 'Test Description',
      submittability_expression_result: {
        ...createSubmitRequirementExpressionInfo(),
        expression: 'label:Verified=MAX -label:Verified=MIN',
      },
    };
    const change: ParsedChangeInfo = {
      ...createParsedChange(),
      labels: {
        Verified: {
          ...createDetailedLabelInfo(),
          all: [
            {
              ...createApproval(),
              value: 2,
            },
          ],
        },
      },
    };
    const element = await fixture<GrSubmitRequirementHovercard>(
      html`<gr-submit-requirement-hovercard
        .requirement=${submitRequirement}
        .change=${change}
        .account=${createAccountWithId()}
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
        <div class="section description">
          <div class="sectionIcon">
            <iron-icon icon="gr-icons:description">
            </iron-icon>
          </div>
          <div class="sectionContent">
          Test Description
          </div>
        </div>
        <div class="button">
          <gr-button
            aria-disabled="false"
            id="toggleConditionsButton"
            link=""
            role="button"
            tabindex="0"
          >
            View conditions
            <iron-icon icon="gr-icons:expand-more">
            </iron-icon>
          </gr-button>
        </div>
      </div>
      `);
  });
});
