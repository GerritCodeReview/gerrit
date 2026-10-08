/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import {assert, fixture} from '@open-wc/testing';
import {html} from 'lit';
import './gr-submit-requirements';
import {GrSubmitRequirements} from './gr-submit-requirements';
import {
  createAccountWithIdNameAndEmail,
  createApproval,
  createCheckResult,
  createDetailedLabelInfo,
  createNonApplicableSubmitRequirementResultInfo,
  createParsedChange,
  createRun,
  createSubmitRequirementExpressionInfo,
  createSubmitRequirementResultInfo,
} from '../../../test/test-data-generators';
import {
  SubmitRequirementResultInfo,
  SubmitRequirementStatus,
} from '../../../api/rest-api';
import * as sinon from 'sinon';
import {Category, RunStatus} from '../../../api/checks';
import {Tab} from '../../../constants/constants';
import {GrChecksChip} from '../gr-change-summary/gr-checks-chip';
import {LoadingStatus, ParsedChangeInfo} from '../../../types/types';
import {testResolver} from '../../../test/common-test-setup';
import {
  ChangeModel,
  changeModelToken,
} from '../../../models/change/change-model';

suite('gr-submit-requirements tests', () => {
  let element: GrSubmitRequirements;
  let change: ParsedChangeInfo;
  let changeModel: ChangeModel;

  setup(async () => {
    const submitRequirement: SubmitRequirementResultInfo = {
      ...createSubmitRequirementResultInfo(),
      description: 'Test Description',
      submittability_expression_result: createSubmitRequirementExpressionInfo(),
    };
    const submitRequirements: SubmitRequirementResultInfo[] = [
      submitRequirement,
      createNonApplicableSubmitRequirementResultInfo(),
    ];
    change = {
      ...createParsedChange(),
      submittable: false,
      submit_requirements: submitRequirements,
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
    changeModel = testResolver(changeModelToken);
    changeModel.setState({
      change,
      submittabilityInfo: {
        changeNum: change._number,
        submitRequirements,
        submittable: false,
      },
      loadingStatus: LoadingStatus.LOADED,
      submittabilityLoadingStatus: LoadingStatus.LOADED,
    });
    element = await fixture<GrSubmitRequirements>(
      html`<gr-submit-requirements
        .change=${change}
        .account=${account}
      ></gr-submit-requirements>`
    );
    await element.updateComplete;
  });

  test('renders', () => {
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <h3 class="heading-3 metadata-title" id="submit-requirements-caption">
          Submit Requirements
        </h3>
        <table
          aria-labelledby="submit-requirements-caption"
          class="requirements"
        >
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
                <gr-icon
                  aria-label="satisfied"
                  role="img"
                  class="check_circle"
                  filled
                  icon="check_circle"
                >
                </gr-icon>
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
                  <div class="votes">
                    <div class="votes-line">
                      <gr-vote-chip> </gr-vote-chip>
                    </div>
                  </div>
                </gr-endpoint-decorator>
              </td>
            </tr>
          </tbody>
        </table>
        <gr-submit-requirement-hovercard for="requirement-0-Verified">
        </gr-submit-requirement-hovercard>
      `
    );
  });

  test('renders loading', async () => {
    element.requirementsLoading = true;
    element.change = {
      ...element.change,
      submit_requirements: undefined,
    } as ParsedChangeInfo;
    await element.updateComplete;
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <h3 class="heading-3 metadata-title" id="submit-requirements-caption">
          Submit Requirements
          <span
            class="loadingSpin"
            title="Submit Requirements status is being updated"
          >
          </span>
        </h3>
        <h3 class="heading-3 metadata-title">Label Votes</h3>
        <section class="trigger-votes">
          <gr-trigger-vote> </gr-trigger-vote>
        </section>
      `
    );
  });

  suite('votes-cell', () => {
    setup(async () => {
      element.disableEndpoints = true;
      await element.updateComplete;
    });
    test('with vote', () => {
      const votesCell = element.shadowRoot?.querySelectorAll('.votes-cell');
      assert.dom.equal(
        votesCell?.[0],
        /* HTML */ `
          <div class="votes-cell">
            <div class="votes">
              <div class="votes-line">
                <gr-vote-chip> </gr-vote-chip>
              </div>
            </div>
          </div>
        `
      );
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
      assert.dom.equal(
        votesCell?.[0],
        /* HTML */ ' <div class="votes-cell">No votes</div> '
      );
    });

    test('without label to vote on', async () => {
      const modifiedChange = {...change};
      modifiedChange.submit_requirements![0].submittability_expression_result.expression =
        'hasfooter:"Release-Notes"';
      element.change = modifiedChange;
      await element.updateComplete;
      const votesCell = element.shadowRoot?.querySelectorAll('.votes-cell');
      assert.dom.equal(
        votesCell?.[0],
        /* HTML */ ' <div class="votes-cell">Satisfied</div> '
      );
    });

    test('checks', async () => {
      element.runs = [
        {
          ...createRun(),
          labelName: 'Verified',
          results: [createCheckResult()],
        },
      ];
      await element.updateComplete;
      const votesCell = element.shadowRoot?.querySelectorAll('.votes-cell');
      assert.dom.equal(
        votesCell?.[0],
        /* HTML */ `
          <div class="votes-cell">
            <div class="votes">
              <div class="votes-line">
                <gr-vote-chip> </gr-vote-chip>
                <gr-checks-chip-for-label> </gr-checks-chip-for-label>
              </div>
            </div>
          </div>
        `
      );
    });

    test('running checks', async () => {
      element.runs = [
        {
          ...createRun(),
          status: RunStatus.RUNNING,
          labelName: 'Verified',
          results: [createCheckResult()],
        },
      ];
      await element.updateComplete;
      const votesCell = element.shadowRoot?.querySelectorAll('.votes-cell');
      assert.dom.equal(
        votesCell?.[0],
        /* HTML */ `
          <div class="votes-cell">
            <div class="votes">
              <div class="votes-line">
                <gr-vote-chip> </gr-vote-chip>
                <gr-checks-chip-for-label> </gr-checks-chip-for-label>
              </div>
            </div>
          </div>
        `
      );
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
      assert.dom.equal(
        votesCell?.[0],
        /* HTML */ `<div class="votes-cell">
          <div class="votes">
            <div class="votes-line">
              <gr-vote-chip> </gr-vote-chip>
              <span class="overrideLabel"> Override </span>
            </div>
          </div>
        </div>`
      );
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
      assert.dom.equal(
        votesCell?.[0],
        /* HTML */ `<div class="votes-cell">
          <div class="votes">
            <div class="votes-line">
              <gr-vote-chip> </gr-vote-chip>
              <span class="overrideLabel"> Override </span>
            </div>
            <div class="votes-line">
              <gr-vote-chip> </gr-vote-chip>
              <span class="overrideLabel"> Override2 </span>
            </div>
          </div>
        </div>`
      );
    });

    suite('autofix chip', () => {
      let presubmitChange: ParsedChangeInfo;

      setup(async () => {
        presubmitChange = {
          ...change,
          submit_requirements: [
            {
              ...createSubmitRequirementResultInfo(),
              name: 'Presubmit-Verified',
              submittability_expression_result:
                createSubmitRequirementExpressionInfo(
                  'label:Presubmit-Verified=MAX'
                ),
            },
          ],
          labels: {
            'Presubmit-Verified': {
              ...createDetailedLabelInfo(),
              all: [
                {
                  ...createApproval(),
                  value: -2,
                },
              ],
            },
          },
        };
        element.change = presubmitChange;
        await element.updateComplete;
      });

      test('renders running autofix chip for Presubmit-Verified', async () => {
        element.runs = [
          {
            ...createRun(),
            checkName: 'AI Autofix',
            status: RunStatus.RUNNING,
            isLatestAttempt: true,
          },
        ];
        await element.updateComplete;
        const chip =
          element.shadowRoot?.querySelector<GrChecksChip>('.autofix-chip');
        assert.isDefined(chip);
        assert.equal(chip!.statusOrCategory, RunStatus.RUNNING);
        assert.equal(chip!.text, 'AutoFix Running...');
        assert.isTrue(chip!.isAi);
      });

      test('renders completed autofix chip with fix for Presubmit-Verified', async () => {
        element.runs = [
          {
            ...createRun(),
            checkName: 'AI Autofix',
            status: RunStatus.COMPLETED,
            isLatestAttempt: true,
            statusLink: 'http://go/autofix-test',
            results: [createCheckResult({category: Category.SUCCESS})],
          },
        ];
        await element.updateComplete;
        const chip =
          element.shadowRoot?.querySelector<GrChecksChip>('.autofix-chip');
        assert.isDefined(chip);
        assert.equal(chip!.statusOrCategory, Category.SUCCESS);
        assert.equal(chip!.text, 'AutoFix Created');
        assert.deepEqual(chip!.links, ['http://go/autofix-test']);
        assert.isTrue(chip!.isAi);
      });

      test('does not render autofix chip when completed without fix', async () => {
        element.runs = [
          {
            ...createRun(),
            checkName: 'AI Autofix',
            status: RunStatus.COMPLETED,
            isLatestAttempt: true,
            results: [createCheckResult({category: Category.INFO})],
          },
        ];
        await element.updateComplete;
        const chip =
          element.shadowRoot?.querySelector<GrChecksChip>('.autofix-chip');
        assert.isNull(chip);
      });

      test('clicking completed autofix chip fires show-tab event to checks tab', async () => {
        element.runs = [
          {
            ...createRun(),
            checkName: 'AI Autofix',
            status: RunStatus.COMPLETED,
            isLatestAttempt: true,
            results: [createCheckResult({category: Category.SUCCESS})],
          },
        ];
        await element.updateComplete;
        const chip =
          element.shadowRoot?.querySelector<GrChecksChip>('.autofix-chip');
        assert.isDefined(chip);

        const showTabStub = sinon.stub();
        element.addEventListener('show-tab', showTabStub);
        chip!.click();

        assert.isTrue(showTabStub.called);
        assert.equal(showTabStub.lastCall.args[0].detail.tab, Tab.CHECKS);
        assert.deepEqual(
          showTabStub.lastCall.args[0].detail.tabState?.checksTab,
          {
            checkName: 'AI Autofix',
            statusOrCategory: Category.SUCCESS,
          }
        );
      });

      test('clicking running autofix chip fires show-tab event to checks tab with INFO category', async () => {
        element.runs = [
          {
            ...createRun(),
            checkName: 'AI Autofix',
            status: RunStatus.RUNNING,
            isLatestAttempt: true,
          },
        ];
        await element.updateComplete;
        const chip =
          element.shadowRoot?.querySelector<GrChecksChip>('.autofix-chip');
        assert.isDefined(chip);

        const showTabStub = sinon.stub();
        element.addEventListener('show-tab', showTabStub);
        chip!.click();

        assert.isTrue(showTabStub.called);
        assert.equal(showTabStub.lastCall.args[0].detail.tab, Tab.CHECKS);
        assert.deepEqual(
          showTabStub.lastCall.args[0].detail.tabState?.checksTab,
          {
            checkName: 'AI Autofix',
            statusOrCategory: Category.INFO,
          }
        );
      });
    });
  });

  test('calculateEndpointName()', () => {
    assert.equal(
      element.computeEndpointName('code-owners~CodeOwnerSub'),
      'submit-requirement-codeowners'
    );
  });
});
