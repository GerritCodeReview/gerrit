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
import './gr-trigger-vote';
import {GrTriggerVote} from './gr-trigger-vote';
import {
  createAccountWithIdNameAndEmail,
  createApproval,
  createDetailedLabelInfo,
  createParsedChange,
  createSubmitRequirementExpressionInfo,
  createSubmitRequirementResultInfo,
  createNonApplicableSubmitRequirementResultInfo,
} from '../../../test/test-data-generators';
import {SubmitRequirementResultInfo} from '../../../api/rest-api';
import {ParsedChangeInfo} from '../../../types/types';

suite('gr-trigger-vote tests', () => {
  let element: GrTriggerVote;
  setup(async () => {
    const submitRequirement: SubmitRequirementResultInfo = {
      ...createSubmitRequirementResultInfo(),
      description: 'Test Description',
      submittability_expression_result: createSubmitRequirementExpressionInfo(),
    };
    const change: ParsedChangeInfo = {
      ...createParsedChange(),
      submit_requirements: [
        submitRequirement,
        createNonApplicableSubmitRequirementResultInfo(),
      ],
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
    const account = createAccountWithIdNameAndEmail();
    const label = 'Verified';
    const labelInfo = change?.labels?.[label];
    element = await fixture<GrTriggerVote>(
      html`<gr-trigger-vote
        .label=${label}
        .labelInfo=${labelInfo}
        .change=${change}
        .account=${account}
        .mutable=${false}
      ></gr-trigger-vote>`
    );
  });

  test('renders', () => {
    expect(element).shadowDom.to.equal(/* HTML */ ` <div class="container">
      <gr-trigger-vote-hovercard>
        <gr-label-info slot="label-info"></gr-label-info>
      </gr-trigger-vote-hovercard>
      <span class="label"> Verified </span>
      <gr-vote-chip> </gr-vote-chip>
    </div>`);
  });
});
