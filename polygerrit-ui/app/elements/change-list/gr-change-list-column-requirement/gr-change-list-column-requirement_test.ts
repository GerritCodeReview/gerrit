/**
 * @license
 * Copyright (C) 2022 The Android Open Source Project
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
import './gr-change-list-column-requirement';
import {GrChangeListColumnRequirement} from './gr-change-list-column-requirement';
import {
  createSubmitRequirementExpressionInfo,
  createSubmitRequirementResultInfo,
  createNonApplicableSubmitRequirementResultInfo,
  createChange,
} from '../../../test/test-data-generators';
import {
  AccountId,
  ChangeInfo,
  DetailedLabelInfo,
  SubmitRequirementResultInfo,
  SubmitRequirementStatus,
} from '../../../api/rest-api';
import {StandardLabels} from '../../../utils/label-util';
import {queryAndAssert, stubFlags} from '../../../test/test-utils';

suite('gr-change-list-column-requirement tests', () => {
  let element: GrChangeListColumnRequirement;
  let change: ChangeInfo;
  setup(() => {
    stubFlags('isEnabled').returns(true);
    const submitRequirement: SubmitRequirementResultInfo = {
      ...createSubmitRequirementResultInfo(),
      name: StandardLabels.CODE_REVIEW,
      submittability_expression_result: {
        ...createSubmitRequirementExpressionInfo(),
        expression: 'label:Verified=MAX -label:Verified=MIN',
      },
    };
    change = {
      ...createChange(),
      submit_requirements: [
        submitRequirement,
        createNonApplicableSubmitRequirementResultInfo(),
      ],
      unresolved_comment_count: 1,
    };
  });

  test('renders', async () => {
    element = await fixture<GrChangeListColumnRequirement>(
      html`<gr-change-list-column-requirement
        .change=${change}
        .labelName=${StandardLabels.CODE_REVIEW}
      >
      </gr-change-list-column-requirement>`
    );
    expect(element).shadowDom.to.equal(
      /* HTML */
      ` <div class="container" title="Satisfied">
        <iron-icon
          class="check-circle-filled"
          icon="gr-icons:check-circle-filled"
        >
        </iron-icon>
      </div>`
    );
  });

  test('show worst vote when state is not satisfied', async () => {
    const VALUES_2 = {
      '-2': 'blocking',
      '-1': 'bad',
      '0': 'neutral',
      '+1': 'good',
      '+2': 'perfect',
    };
    const label: DetailedLabelInfo = {
      values: VALUES_2,
      all: [
        {value: -1, _account_id: 777 as AccountId, name: 'Reviewer'},
        {value: 1, _account_id: 324 as AccountId, name: 'Reviewer 2'},
      ],
    };
    const submitRequirement: SubmitRequirementResultInfo = {
      ...createSubmitRequirementResultInfo(),
      name: StandardLabels.CODE_REVIEW,
      status: SubmitRequirementStatus.UNSATISFIED,
      submittability_expression_result: {
        ...createSubmitRequirementExpressionInfo(),
        expression: 'label:Verified=MAX -label:Verified=MIN',
      },
    };
    change = {
      ...change,
      submit_requirements: [submitRequirement],
      labels: {
        Verified: label,
      },
    };
    element = await fixture<GrChangeListColumnRequirement>(
      html`<gr-change-list-column-requirement
        .change=${change}
        .labelName=${StandardLabels.CODE_REVIEW}
      >
      </gr-change-list-column-requirement>`
    );
    expect(element).shadowDom.to.equal(
      /* HTML */
      ` <div class="container">
        <gr-vote-chip tooltip-with-who-voted=""></gr-vote-chip>
      </div>`
    );
    const voteChip = queryAndAssert(element, 'gr-vote-chip');
    expect(voteChip).shadowDom.to.equal(
      /* HTML */
      ` <gr-tooltip-content
        class="container"
        has-tooltip=""
        title="Reviewer: bad"
      >
        <div class="negative vote-chip">-1</div>
      </gr-tooltip-content>`
    );
  });

  test('show trigger vote', async () => {
    const VALUES_2 = {
      '-2': 'blocking',
      '-1': 'bad',
      '0': 'neutral',
      '+1': 'good',
      '+2': 'perfect',
    };
    change = {
      ...change,
      submit_requirements: [],
      labels: {
        'Commit-Queue': {
          values: VALUES_2,
          all: [
            {value: -1, _account_id: 777 as AccountId, name: 'Reviewer'},
            {value: 1, _account_id: 324 as AccountId, name: 'Reviewer 2'},
          ],
        },
      },
    };
    element = await fixture<GrChangeListColumnRequirement>(
      html`<gr-change-list-column-requirement
        .change=${change}
        .labelName=${'Commit-Queue'}
      >
      </gr-change-list-column-requirement>`
    );
    expect(element).shadowDom.to.equal(
      /* HTML */
      ` <div class="container">
        <gr-vote-chip tooltip-with-who-voted=""></gr-vote-chip>
      </div>`
    );
    const voteChip = queryAndAssert(element, 'gr-vote-chip');
    expect(voteChip).shadowDom.to.equal(
      /* HTML */
      ` <gr-tooltip-content
        class="container"
        has-tooltip=""
        title="Reviewer 2: good"
      >
        <div class="positive vote-chip">+1</div>
      </gr-tooltip-content>`
    );
  });
});
