/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import {assert, fixture} from '@open-wc/testing';
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
import {query, queryAndAssert, stubRestApi} from '../../../test/test-utils';
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
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <div id="container" role="tooltip" tabindex="-1">
          <div class="section">
            <div class="sectionIcon">
              <gr-icon
                aria-label="satisfied"
                role="img"
                class="check_circle"
                filled
                icon="check_circle"
              >
              </gr-icon>
            </div>
            <div class="sectionContent">
              <h3 class="heading-3 name">
                <span> Verified </span>
              </h3>
            </div>
          </div>
          <div class="section">
            <div class="sectionIcon">
              <gr-icon class="small" icon="info"></gr-icon>
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
              View Conditions
              <gr-icon icon="expand_more"></gr-icon>
            </gr-button>
          </div>
        </div>
      `
    );
  });

  test('renders conditions after click', async () => {
    const button = queryAndAssert<GrButton>(element, '#toggleConditionsButton');
    button.click();
    await element.updateComplete;
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <div id="container" role="tooltip" tabindex="-1">
          <div class="section">
            <div class="sectionIcon">
              <gr-icon
                aria-label="satisfied"
                role="img"
                class="check_circle"
                filled
                icon="check_circle"
              >
              </gr-icon>
            </div>
            <div class="sectionContent">
              <h3 class="heading-3 name">
                <span> Verified </span>
              </h3>
            </div>
          </div>
          <div class="section">
            <div class="sectionIcon">
              <gr-icon class="small" icon="info"></gr-icon>
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
              Hide Conditions
              <gr-icon icon="expand_less"></gr-icon>
            </gr-button>
          </div>
          <div class="section condition">
            <div class="sectionContent">
              Submit condition:
              <br />
              <span class="expression">
                <span class="passing atom" title="Atom evaluates to True">
                  label:Verified=MAX
                </span>
                <span class="passing atom" title="Atom evaluates to True">
                  -label:Verified=MIN
                </span>
              </span>
            </div>
          </div>
        </div>
      `
    );
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
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <div id="container" role="tooltip" tabindex="-1">
          <div class="section">
            <div class="sectionIcon">
              <gr-icon
                aria-label="satisfied"
                role="img"
                class="check_circle"
                filled
                icon="check_circle"
              ></gr-icon>
            </div>
            <div class="sectionContent">
              <h3 class="heading-3 name">
                <span> Verified </span>
              </h3>
            </div>
          </div>
          <div class="section">
            <div class="sectionIcon">
              <gr-icon class="small" icon="info"></gr-icon>
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
              <gr-icon icon="description"></gr-icon>
            </div>
            <div class="sectionContent">
              <gr-formatted-text></gr-formatted-text>
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
              View Conditions
              <gr-icon icon="expand_more"></gr-icon>
            </gr-button>
          </div>
        </div>
      `
    );
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
      permitted_labels: {
        Verified: ['-1', ' 0', '+1', '+2'],
      },
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
      assert.dom.equal(
        quickApprove,
        /* HTML */ `
          <div class="button quickApprove">
            <gr-button aria-disabled="false" link="" role="button" tabindex="0">
              Vote Verified +2
            </gr-button>
          </div>
        `
      );
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

    test('uses patchset from change', async () => {
      const saveChangeReview = stubRestApi('saveChangeReview').resolves();
      const element = await fixture<GrSubmitRequirementHovercard>(
        html`<gr-submit-requirement-hovercard
          .requirement=${submitRequirement}
          .change=${change}
          .account=${account}
        ></gr-submit-requirement-hovercard>`
      );

      queryAndAssert<GrButton>(element, '.quickApprove > gr-button').click();

      assert.equal(saveChangeReview.callCount, 1);
      assert.equal(saveChangeReview.firstCall.args[1], change.current_revision);
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
        permitted_labels: {
          'Build-Cop': ['-1', ' 0', '+1', '+2'],
        },
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
      assert.dom.equal(
        quickApprove,
        /* HTML */ `
          <div class="button quickApprove">
            <gr-button aria-disabled="false" link="" role="button" tabindex="0"
              >Override (Build-Cop)
            </gr-button>
          </div>
        `
      );
    });
  });
});
