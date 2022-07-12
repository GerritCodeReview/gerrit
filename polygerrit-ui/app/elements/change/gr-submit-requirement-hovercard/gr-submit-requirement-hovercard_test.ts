/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
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
import {query, queryAndAssert} from '../../../test/test-utils';
import {GrButton} from '../../shared/gr-button/gr-button';
import {ChangeStatus, SubmitRequirementResultInfo} from '../../../api/rest-api';

suite('gr-submit-requirement-hovercard tests', () => {
  let element: GrSubmitRequirementHovercard;

  setup(async () => {
    element = await fixture<GrSubmitRequirementHovercard>(
      html`<gr-submit-requirement-hovercard
        .requirement=${createSubmitRequirementResultInfo()}
        .change=${createChange()}
        .account=${createAccountWithId()}
      ></gr-submit-requirement-hovercard>`
    );
  });

  test('renders', async () => {
    expect(element).shadowDom.to.equal(/* HTML */ `
      <div id="container" role="tooltip" tabindex="-1">
        <div class="section">
          <div class="sectionIcon">
            <iron-icon
              aria-label="satisfied"
              role="img"
              class="check-circle-filled"
              icon="gr-icons:check-circle-filled"
            >
            </iron-icon>
          </div>
          <div class="sectionContent">
            <h3 class="heading-3 name">
              <span> Verified </span>
            </h3>
          </div>
        </div>
        <div class="section">
          <div class="sectionIcon">
            <span class="small material-icon">info</span>
          </div>
          <div class="sectionContent">
            <div class="row">
              <div class="title">Status</div>
              <div>SATISFIED</div>
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
            <span class="material-icons">expand_more</span>
          </gr-button>
        </div>
      </div>
    `);
  });

  test('renders conditions after click', async () => {
    const button = queryAndAssert<GrButton>(element, '#toggleConditionsButton');
    button.click();
    await element.updateComplete;
    expect(element).shadowDom.to.equal(/* HTML */ `
      <div id="container" role="tooltip" tabindex="-1">
        <div class="section">
          <div class="sectionIcon">
            <iron-icon
              aria-label="satisfied"
              role="img"
              class="check-circle-filled"
              icon="gr-icons:check-circle-filled"
            >
            </iron-icon>
          </div>
          <div class="sectionContent">
            <h3 class="heading-3 name">
              <span> Verified </span>
            </h3>
          </div>
        </div>
        <div class="section">
          <div class="sectionIcon">
            <span class="small material-icon">info</span>
          </div>
          <div class="sectionContent">
            <div class="row">
              <div class="title">Status</div>
              <div>SATISFIED</div>
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
            <span class="material-icons">expand_less</span>
          </gr-button>
        </div>
        <div class="section condition">
          <div class="sectionContent">
            Submit condition:
            <br />
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
      submittability_expression_result: createSubmitRequirementExpressionInfo(),
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
    expect(element).shadowDom.to.equal(/* HTML */ `
      <div id="container" role="tooltip" tabindex="-1">
        <div class="section">
          <div class="sectionIcon">
            <iron-icon
              aria-label="satisfied"
              role="img"
              class="check-circle-filled"
              icon="gr-icons:check-circle-filled"
            >
            </iron-icon>
          </div>
          <div class="sectionContent">
            <h3 class="heading-3 name">
              <span> Verified </span>
            </h3>
          </div>
        </div>
        <div class="section">
          <div class="sectionIcon">
            <span class="small material-icon">info</span>
          </div>
          <div class="sectionContent">
            <div class="row">
              <div class="title">Status</div>
              <div>SATISFIED</div>
            </div>
          </div>
        </div>
        <div class="section">
          <div class="sectionIcon"></div>
          <div class="row">
            <div>
              <gr-label-info> </gr-label-info>
            </div>
          </div>
        </div>
        <div class="section description">
          <div class="sectionIcon">
            <span class="material-icon">description</span>
          </div>
          <div class="sectionContent">
            <gr-formatted-text notrailingmargin=""></gr-formatted-text>
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
            <span class="material-icons">expand_more</span>
          </gr-button>
        </div>
      </div>
    `);
  });

  suite('quick approve label', () => {
    const submitRequirement: SubmitRequirementResultInfo = {
      ...createSubmitRequirementResultInfo(),
      description: 'Test Description',
      submittability_expression_result: createSubmitRequirementExpressionInfo(),
    };
    const account = createAccountWithId();
    const change: ParsedChangeInfo = {
      ...createParsedChange(),
      status: ChangeStatus.NEW,
      labels: {
        Verified: {
          ...createDetailedLabelInfo(),
          all: [
            {
              ...createApproval(),
              _account_id: account._account_id,
              permitted_voting_range: {
                min: -2,
                max: 2,
              },
            },
          ],
        },
      },
    };
    test('renders', async () => {
      const element = await fixture<GrSubmitRequirementHovercard>(
        html`<gr-submit-requirement-hovercard
          .requirement=${submitRequirement}
          .change=${change}
          .account=${account}
        ></gr-submit-requirement-hovercard>`
      );
      const quickApprove = queryAndAssert(element, '.quickApprove');
      expect(quickApprove).dom.to.equal(/* HTML */ `
        <div class="button quickApprove">
          <gr-button aria-disabled="false" link="" role="button" tabindex="0">
            Vote Verified +2
          </gr-button>
        </div>
      `);
    });

    test("doesn't render when already voted max vote", async () => {
      const changeWithVote = {
        ...change,
        labels: {
          ...change.labels,
          Verified: {
            ...createDetailedLabelInfo(),
            all: [
              {
                ...createApproval(),
                _account_id: account._account_id,
                permitted_voting_range: {
                  min: -2,
                  max: 2,
                },
                value: 2,
              },
            ],
          },
        },
      };
      const element = await fixture<GrSubmitRequirementHovercard>(
        html`<gr-submit-requirement-hovercard
          .requirement=${submitRequirement}
          .change=${changeWithVote}
          .account=${account}
        ></gr-submit-requirement-hovercard>`
      );
      assert.isUndefined(query(element, '.quickApprove'));
    });

    test('override button renders', async () => {
      const submitRequirement: SubmitRequirementResultInfo = {
        ...createSubmitRequirementResultInfo(),
        description: 'Test Description',
        submittability_expression_result:
          createSubmitRequirementExpressionInfo(),
        override_expression_result: createSubmitRequirementExpressionInfo(
          'label:Build-Cop=MAX'
        ),
      };
      const account = createAccountWithId();
      const change: ParsedChangeInfo = {
        ...createParsedChange(),
        status: ChangeStatus.NEW,
        labels: {
          'Build-Cop': {
            ...createDetailedLabelInfo(),
            all: [
              {
                ...createApproval(),
                _account_id: account._account_id,
                permitted_voting_range: {
                  min: -2,
                  max: 2,
                },
              },
            ],
          },
        },
      };
      const element = await fixture<GrSubmitRequirementHovercard>(
        html`<gr-submit-requirement-hovercard
          .requirement=${submitRequirement}
          .change=${change}
          .account=${account}
        ></gr-submit-requirement-hovercard>`
      );
      const quickApprove = queryAndAssert(element, '.quickApprove');
      expect(quickApprove).dom.to.equal(/* HTML */ `
        <div class="button quickApprove">
          <gr-button aria-disabled="false" link="" role="button" tabindex="0"
            >Override (Build-Cop)
          </gr-button>
        </div>
      `);
    });
  });
});
