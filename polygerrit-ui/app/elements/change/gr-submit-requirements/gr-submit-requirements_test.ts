/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import '../../../test/common-test-setup-karma';
import {fixture} from '@open-wc/testing-helpers';
import {html} from 'lit';
import './gr-submit-requirements';
import {GrSubmitRequirements} from './gr-submit-requirements';
import {
  createAccountWithIdNameAndEmail,
  createApproval,
  createDetailedLabelInfo,
  createParsedChange,
  createSubmitRequirementExpressionInfo,
  createSubmitRequirementResultInfo,
  createNonApplicableSubmitRequirementResultInfo,
  createRunResult,
  createCheckResult,
} from '../../../test/test-data-generators';
import {
  SubmitRequirementResultInfo,
  SubmitRequirementStatus,
} from '../../../api/rest-api';
import {ParsedChangeInfo} from '../../../types/types';
import {RunStatus} from '../../../api/checks';

suite('gr-submit-requirements tests', () => {
  let element: GrSubmitRequirements;
  let change: ParsedChangeInfo;
  setup(async () => {
    const submitRequirement: SubmitRequirementResultInfo = {
      ...createSubmitRequirementResultInfo(),
      description: 'Test Description',
      submittability_expression_result: createSubmitRequirementExpressionInfo(),
    };
    change = {
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
    element = await fixture<GrSubmitRequirements>(
      html`<gr-submit-requirements
        .change=${change}
        .account=${account}
      ></gr-submit-requirements>`
    );
  });

  test('renders', () => {
    expect(element).shadowDom.to.equal(/* HTML */ `
      <h3 class="heading-3 metadata-title" id="submit-requirements-caption">
        Submit Requirements
      </h3>
      <table aria-labelledby="submit-requirements-caption" class="requirements">
        <thead hidden="">
          <tr>
            <th>Status</th>
            <th>Name</th>
            <th>Votes</th>
          </tr>
        </thead>
        <tbody>
          <tr id="requirement-0-Verified" role="button" tabindex="0">
            <td>
              <iron-icon
                aria-label="satisfied"
                class="check-circle-filled"
                icon="gr-icons:check-circle-filled"
                role="img"
              >
              </iron-icon>
            </td>
            <td class="name">
              <gr-limited-text class="name"></gr-limited-text>
            </td>
            <td>
              <gr-endpoint-decorator
                class="votes-cell"
                name="submit-requirement-verified"
              >
                <gr-endpoint-param name="change"></gr-endpoint-param>
                <gr-endpoint-param name="requirement"></gr-endpoint-param>
                <gr-vote-chip></gr-vote-chip>
              </gr-endpoint-decorator>
            </td>
          </tr>
        </tbody>
      </table>
      <gr-submit-requirement-hovercard for="requirement-0-Verified">
      </gr-submit-requirement-hovercard>
    `);
  });

  suite('votes-cell', () => {
    setup(async () => {
      element.disableEndpoints = true;
      await element.updateComplete;
    });
    test('with vote', () => {
      const votesCell = element.shadowRoot?.querySelectorAll('.votes-cell');
      expect(votesCell?.[0]).dom.equal(/* HTML */ `
        <div class="votes-cell">
          <gr-vote-chip> </gr-vote-chip>
        </div>
      `);
    });

    test('no votes', async () => {
      const modifiedChange = {...change};
      modifiedChange.labels = {
        Verified: {
          ...createDetailedLabelInfo(),
        },
      };
      element.change = modifiedChange;
      await element.updateComplete;
      const votesCell = element.shadowRoot?.querySelectorAll('.votes-cell');
      expect(votesCell?.[0]).dom.equal(/* HTML */ `
        <div class="votes-cell">No votes</div>
      `);
    });

    test('without label to vote on', async () => {
      const modifiedChange = {...change};
      modifiedChange.submit_requirements![0]!.submittability_expression_result.expression =
        'hasfooter:"Release-Notes"';
      element.change = modifiedChange;
      await element.updateComplete;
      const votesCell = element.shadowRoot?.querySelectorAll('.votes-cell');
      expect(votesCell?.[0]).dom.equal(/* HTML */ `
        <div class="votes-cell">Satisfied</div>
      `);
    });

    test('checks', async () => {
      element.runs = [
        {
          ...createRunResult(),
          labelName: 'Verified',
          results: [createCheckResult()],
        },
      ];
      await element.updateComplete;
      const votesCell = element.shadowRoot?.querySelectorAll('.votes-cell');
      expect(votesCell?.[0]).dom.equal(/* HTML */ `
        <div class="votes-cell">
          <gr-vote-chip></gr-vote-chip>
          <gr-checks-chip></gr-checks-chip>
        </div>
      `);
    });

    test('running checks', async () => {
      element.runs = [
        {
          ...createRunResult(),
          status: RunStatus.RUNNING,
          labelName: 'Verified',
          results: [createCheckResult()],
        },
      ];
      await element.updateComplete;
      const votesCell = element.shadowRoot?.querySelectorAll('.votes-cell');
      expect(votesCell?.[0]).dom.equal(/* HTML */ `
        <div class="votes-cell">
          <gr-vote-chip></gr-vote-chip>
          <gr-checks-chip></gr-checks-chip>
        </div>
      `);
    });

    test('with override label', async () => {
      const modifiedChange = {...change};
      modifiedChange.labels = {
        Override: {
          ...createDetailedLabelInfo(),
          all: [
            {
              ...createApproval(),
              value: 2,
            },
          ],
        },
      };
      modifiedChange.submit_requirements = [
        {
          ...createSubmitRequirementResultInfo(),
          status: SubmitRequirementStatus.OVERRIDDEN,
          override_expression_result: createSubmitRequirementExpressionInfo(
            'label:Override=MAX -label:Override=MIN'
          ),
        },
      ];
      element.change = modifiedChange;
      await element.updateComplete;
      const votesCell = element.shadowRoot?.querySelectorAll('.votes-cell');
      expect(votesCell?.[0]).dom.equal(/* HTML */ `<div class="votes-cell">
        <gr-vote-chip> </gr-vote-chip>
        <span class="overrideLabel"> Override </span>
      </div>`);
    });

    test('with override with 2 labels', async () => {
      const modifiedChange = {...change};
      modifiedChange.labels = {
        Override: {
          ...createDetailedLabelInfo(),
          all: [
            {
              ...createApproval(),
              value: 2,
            },
          ],
        },
        Override2: {
          ...createDetailedLabelInfo(),
          all: [
            {
              ...createApproval(),
              value: 2,
            },
          ],
        },
      };
      modifiedChange.submit_requirements = [
        {
          ...createSubmitRequirementResultInfo(),
          status: SubmitRequirementStatus.OVERRIDDEN,
          override_expression_result: createSubmitRequirementExpressionInfo(
            'label:Override=MAX label:Override2=MAX'
          ),
        },
      ];
      element.change = modifiedChange;
      await element.updateComplete;
      const votesCell = element.shadowRoot?.querySelectorAll('.votes-cell');
      expect(votesCell?.[0]).dom.equal(/* HTML */ `<div class="votes-cell">
        <gr-vote-chip> </gr-vote-chip>
        <span class="overrideLabel"> Override </span>
        <span class="separator"></span>
        <gr-vote-chip> </gr-vote-chip>
        <span class="overrideLabel"> Override2 </span>
      </div>`);
    });
  });

  test('calculateEndpointName()', () => {
    assert.equal(
      element.calculateEndpointName('code-owners~CodeOwnerSub'),
      'submit-requirement-codeowners'
    );
  });
});
