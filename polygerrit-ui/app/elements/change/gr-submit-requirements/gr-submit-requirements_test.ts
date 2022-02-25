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
} from '../../../test/test-data-generators';
import {SubmitRequirementResultInfo} from '../../../api/rest-api';
import {ParsedChangeInfo} from '../../../types/types';

suite('gr-submit-requirements tests', () => {
  let element: GrSubmitRequirements;
  let change: ParsedChangeInfo;
  setup(async () => {
    const submitRequirement: SubmitRequirementResultInfo = {
      ...createSubmitRequirementResultInfo(),
      description: 'Test Description',
      submittability_expression_result: {
        ...createSubmitRequirementExpressionInfo(),
        expression: 'label:Verified=MAX -label:Verified=MIN',
      },
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
              <gr-limited-text class="name" limit="25"></gr-limited-text>
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
  });

  test('calculateEndpointName()', () => {
    assert.equal(
      element.calculateEndpointName('code-owners~CodeOwnerSub'),
      'submit-requirement-codeowners'
    );
  });
});
