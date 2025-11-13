/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import {assert, fixture} from '@open-wc/testing';
import {html} from 'lit';
import './gr-trigger-vote';
import {GrTriggerVote} from './gr-trigger-vote';
import {
  createAccountWithIdNameAndEmail,
  createApproval,
  createDetailedLabelInfo,
  createNonApplicableSubmitRequirementResultInfo,
  createParsedChange,
  createSubmitRequirementExpressionInfo,
  createSubmitRequirementResultInfo,
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
    assert.shadowDom.equal(
      element,
      /* HTML */ ` <div class="container">
        <gr-trigger-vote-hovercard>
          <gr-label-info slot="label-info"></gr-label-info>
        </gr-trigger-vote-hovercard>
        <span class="label">Verified</span>
        <gr-vote-chip></gr-vote-chip>
      </div>`
    );
  });
});
