/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import '../../../test/common-test-setup-karma';
import {GrChangeListBulkVoteFlow} from './gr-change-list-bulk-vote-flow';
import {
  BulkActionsModel,
  bulkActionsModelToken,
  LoadingState,
} from '../../../models/bulk-actions/bulk-actions-model';
import {
  waitUntilObserved,
  stubRestApi,
  queryAndAssert,
  query,
  mockPromise,
  queryAll,
  stubReporting,
} from '../../../test/test-utils';
import {ChangeInfo, NumericChangeId, LabelInfo} from '../../../api/rest-api';
import {getAppContext} from '../../../services/app-context';
import {fixture, waitUntil} from '@open-wc/testing-helpers';
import {wrapInProvider} from '../../../models/di-provider-element';
import {html} from 'lit';
import {SinonStubbedMember} from 'sinon';
import {RestApiService} from '../../../services/gr-rest-api/gr-rest-api';
import {
  createChange,
  createSubmitRequirementResultInfo,
} from '../../../test/test-data-generators';
import './gr-change-list-bulk-vote-flow';
import {GrButton} from '../../shared/gr-button/gr-button';
import {ProgressStatus} from '../../../constants/constants';
import {StandardLabels} from '../../../utils/label-util';
import {ReportingService} from '../../../services/gr-reporting/gr-reporting';

const change1: ChangeInfo = {
  ...createChange(),
  _number: 1 as NumericChangeId,
  permitted_labels: {
    [StandardLabels.CODE_REVIEW]: ['-1', '0', '+1', '+2'],
    A: ['-1', '0', '+1', '+2'],
    B: ['-1', '0'],
    C: ['-1', '0'],
    change1OnlyLabelD: ['0'], // Does not exist on change2
    change1OnlyTriggerLabelE: ['0'], // Does not exist on change2
  },
  labels: {
    [StandardLabels.CODE_REVIEW]: {value: null} as LabelInfo,
    A: {value: null} as LabelInfo,
    B: {value: null} as LabelInfo,
    C: {value: null} as LabelInfo,
    change1OnlyLabelD: {value: null} as LabelInfo,
    change1OnlyTriggerLabelE: {value: null} as LabelInfo,
  },
  submit_requirements: [
    createSubmitRequirementResultInfo(
      `label:${StandardLabels.CODE_REVIEW}=MAX`
    ),
    createSubmitRequirementResultInfo('label:A=MAX'),
    createSubmitRequirementResultInfo('label:B=MAX'),
    createSubmitRequirementResultInfo('label:C=MAX'),
    createSubmitRequirementResultInfo('label:change1OnlyLabelD=MAX'),
  ],
};
const change2: ChangeInfo = {
  ...createChange(),
  _number: 2 as NumericChangeId,
  permitted_labels: {
    [StandardLabels.CODE_REVIEW]: ['-1', '0', '+1', '+2'],
    A: ['-1', '0', '+1', '+2'], // Intersects fully with change1
    B: ['0', ' +1'], // Intersects with change1 on 0
    C: ['+1', '+2'], // Does not intersect with change1 at all
  },
  labels: {
    [StandardLabels.CODE_REVIEW]: {value: null} as LabelInfo,
    A: {value: null} as LabelInfo,
    B: {value: null} as LabelInfo,
    C: {value: null} as LabelInfo,
  },
  submit_requirements: [
    createSubmitRequirementResultInfo(
      `label:${StandardLabels.CODE_REVIEW}=MAX`
    ),
    createSubmitRequirementResultInfo('label:A=MAX'),
    createSubmitRequirementResultInfo('label:B=MAX'),
    createSubmitRequirementResultInfo('label:C=MAX'),
  ],
};

suite('gr-change-list-bulk-vote-flow tests', () => {
  let element: GrChangeListBulkVoteFlow;
  let model: BulkActionsModel;
  let dispatchEventStub: sinon.SinonStub;
  let getChangesStub: SinonStubbedMember<
    RestApiService['getDetailedChangesWithActions']
  >;
  let reportingStub: SinonStubbedMember<ReportingService['reportInteraction']>;

  async function selectChange(change: ChangeInfo) {
    model.addSelectedChangeNum(change._number);
    await waitUntilObserved(model.selectedChangeNums$, selectedChangeNums =>
      selectedChangeNums.includes(change._number)
    );
    await element.updateComplete;
  }

  setup(async () => {
    model = new BulkActionsModel(getAppContext().restApiService);
    getChangesStub = stubRestApi('getDetailedChangesWithActions');
    reportingStub = stubReporting('reportInteraction');
    element = (
      await fixture(
        wrapInProvider(
          html`<gr-change-list-bulk-vote-flow></gr-change-list-bulk-vote-flow>`,
          bulkActionsModelToken,
          model
        )
      )
    ).querySelector('gr-change-list-bulk-vote-flow')!;
    await element.updateComplete;
    dispatchEventStub = sinon.stub(element, 'dispatchEvent');
  });

  test('renders', async () => {
    const changes: ChangeInfo[] = [change1];
    getChangesStub.returns(Promise.resolve(changes));
    model.sync(changes);
    await waitUntilObserved(
      model.loadingState$,
      state => state === LoadingState.LOADED
    );
    await selectChange(change1);
    await element.updateComplete;
    expect(element).shadowDom.to.equal(/* HTML */ `<gr-button
        aria-disabled="false"
        flatten=""
        id="voteFlowButton"
        role="button"
        tabindex="0"
      >
        Vote
      </gr-button>
      <gr-overlay
        aria-hidden="true"
        id="actionOverlay"
        style="outline: none; display: none;"
        tabindex="-1"
        with-backdrop=""
      >
        <gr-dialog role="dialog">
          <div slot="header">
            <span class="main-heading"> Vote on selected changes </span>
          </div>
          <div slot="main">
            <div class="newSubmitRequirements scoresTable">
              <h3 class="heading-4 vote-type">Submit requirements votes</h3>
              <gr-label-score-row name="A"> </gr-label-score-row>
              <gr-label-score-row name="B"> </gr-label-score-row>
              <gr-label-score-row name="C"> </gr-label-score-row>
              <gr-label-score-row name="change1OnlyLabelD">
              </gr-label-score-row>
            </div>
            <div class="newSubmitRequirements scoresTable">
              <h3 class="heading-4 vote-type">Trigger Votes</h3>
              <gr-label-score-row name="change1OnlyTriggerLabelE">
              </gr-label-score-row>
            </div>
          </div>
        </gr-dialog>
      </gr-overlay> `);
  });

  test('renders with errors', async () => {
    const changes: ChangeInfo[] = [change1];
    getChangesStub.returns(Promise.resolve(changes));
    model.sync(changes);
    await waitUntilObserved(
      model.loadingState$,
      state => state === LoadingState.LOADED
    );
    stubRestApi('saveChangeReview').callsFake(
      (_changeNum, _patchNum, _review, errFn) =>
        Promise.resolve(new Response()).then(res => {
          errFn && errFn();
          return res;
        })
    );
    await selectChange(change1);
    await element.updateComplete;

    queryAndAssert<GrButton>(query(element, 'gr-dialog'), '#confirm').click();

    await waitUntil(
      () =>
        element.progressByChange.get(1 as NumericChangeId) ===
        ProgressStatus.FAILED
    );

    expect(element).shadowDom.to.equal(/* HTML */ `<gr-button
        aria-disabled="false"
        flatten=""
        id="voteFlowButton"
        role="button"
        tabindex="0"
      >
        Vote
      </gr-button>
      <gr-overlay
        aria-hidden="true"
        id="actionOverlay"
        style="outline: none; display: none;"
        tabindex="-1"
        with-backdrop=""
      >
        <gr-dialog role="dialog">
          <div slot="header">
            <span class="main-heading"> Vote on selected changes </span>
          </div>
          <div slot="main">
            <div class="newSubmitRequirements scoresTable">
              <h3 class="heading-4 vote-type">Submit requirements votes</h3>
              <gr-label-score-row name="A"> </gr-label-score-row>
              <gr-label-score-row name="B"> </gr-label-score-row>
              <gr-label-score-row name="C"> </gr-label-score-row>
              <gr-label-score-row name="change1OnlyLabelD">
              </gr-label-score-row>
            </div>
            <div class="newSubmitRequirements scoresTable">
              <h3 class="heading-4 vote-type">Trigger Votes</h3>
              <gr-label-score-row name="change1OnlyTriggerLabelE">
              </gr-label-score-row>
            </div>
            <div class="error-container">
              <iron-icon icon="gr-icons:error"> </iron-icon>
              <span> Failed to vote on 1 change </span>
            </div>
          </div>
        </gr-dialog>
      </gr-overlay> `);
  });

  test('button state updates as changes are updated', async () => {
    const changes: ChangeInfo[] = [change1];
    getChangesStub.returns(Promise.resolve(changes));
    model.sync(changes);
    await waitUntilObserved(
      model.loadingState$,
      state => state === LoadingState.LOADED
    );
    await selectChange(change1);
    await element.updateComplete;
    await flush();

    assert.isFalse(
      queryAndAssert<GrButton>(element, '#voteFlowButton').disabled
    );

    // No common label with change1 so button is disabled
    change2.labels = {
      x: {value: null} as LabelInfo,
      y: {value: null} as LabelInfo,
      z: {value: null} as LabelInfo,
    };
    change2.submit_requirements = [
      createSubmitRequirementResultInfo('label:x=MAX'),
      createSubmitRequirementResultInfo('label:y=MAX'),
      createSubmitRequirementResultInfo('label:z=MAX'),
    ];
    changes.push({...change2});
    getChangesStub.restore();
    getChangesStub.returns(Promise.resolve(changes));
    model.sync(changes);
    await waitUntilObserved(
      model.loadingState$,
      state => state === LoadingState.LOADED
    );
    await selectChange(change2);
    await element.updateComplete;

    assert.isTrue(
      queryAndAssert<GrButton>(element, '#voteFlowButton').disabled
    );
  });

  test('progress updates as request is resolved', async () => {
    const changes: ChangeInfo[] = [{...change1}];
    getChangesStub.returns(Promise.resolve(changes));
    model.sync(changes);
    await waitUntilObserved(
      model.loadingState$,
      state => state === LoadingState.LOADED
    );
    await selectChange(change1);
    await element.updateComplete;
    const saveChangeReview = mockPromise<Response>();
    stubRestApi('saveChangeReview').returns(saveChangeReview);

    assert.isNotOk(
      queryAndAssert<GrButton>(query(element, 'gr-dialog'), '#confirm').disabled
    );
    assert.isNotOk(
      queryAndAssert<GrButton>(query(element, 'gr-dialog'), '#cancel').disabled
    );

    const scores = queryAll(element, 'gr-label-score-row');
    queryAndAssert<GrButton>(scores[0], 'gr-button[data-value="+1"]').click();
    queryAndAssert<GrButton>(scores[1], 'gr-button[data-value="-1"]').click();

    await element.updateComplete;

    assert.deepEqual(
      element.getLabelValues(
        element.computeCommonPermittedLabels(element.computePermittedLabels())
      ),
      {
        A: 1,
        B: -1,
      }
    );

    queryAndAssert<GrButton>(query(element, 'gr-dialog'), '#confirm').click();
    await element.updateComplete;

    assert.isTrue(
      queryAndAssert<GrButton>(query(element, 'gr-dialog'), '#confirm').disabled
    );
    assert.isTrue(
      queryAndAssert<GrButton>(query(element, 'gr-dialog'), '#cancel').disabled
    );

    assert.deepEqual(reportingStub.lastCall.args[1], {
      type: 'vote',
      selectedChangeCount: 1,
    });

    assert.equal(
      element.progressByChange.get(1 as NumericChangeId),
      ProgressStatus.RUNNING
    );

    saveChangeReview.resolve({...new Response(), status: 200});
    await waitUntil(
      () =>
        element.progressByChange.get(1 as NumericChangeId) ===
        ProgressStatus.SUCCESSFUL
    );

    assert.isTrue(
      queryAndAssert<GrButton>(query(element, 'gr-dialog'), '#confirm').disabled
    );
    assert.isNotOk(
      queryAndAssert<GrButton>(query(element, 'gr-dialog'), '#cancel').disabled
    );

    assert.equal(
      element.progressByChange.get(1 as NumericChangeId),
      ProgressStatus.SUCCESSFUL
    );

    // reload event is fired automatically when all requests succeed
    assert.equal(dispatchEventStub.lastCall.args[0].type, 'reload');
    assert.equal(
      dispatchEventStub.firstCall.args[0].detail.message,
      'Votes added'
    );
  });

  suite('closing dialog triggers reloads', () => {
    test('closing dialog triggers a reload', async () => {
      const changes: ChangeInfo[] = [change1, change2];
      getChangesStub.returns(Promise.resolve(changes));

      stubRestApi('saveChangeReview').callsFake(
        (_changeNum, _patchNum, _review, errFn) =>
          Promise.resolve(new Response()).then(res => {
            errFn && errFn();
            return res;
          })
      );

      model.sync(changes);
      await waitUntilObserved(
        model.loadingState$,
        state => state === LoadingState.LOADED
      );
      await selectChange(change1);
      await selectChange(change2);
      await element.updateComplete;

      queryAndAssert<GrButton>(query(element, 'gr-dialog'), '#confirm').click();

      await waitUntil(
        () =>
          element.progressByChange.get(2 as NumericChangeId) ===
          ProgressStatus.FAILED
      );

      // Dialog does not autoclose and fire reload event if some request fails
      assert.isFalse(dispatchEventStub.called);

      assert.deepEqual(reportingStub.lastCall.args, [
        'bulk-action-failure',
        {
          type: 'vote',
          count: 2,
        },
      ]);

      queryAndAssert<GrButton>(query(element, 'gr-dialog'), '#cancel').click();

      await waitUntil(() => dispatchEventStub.called);
      assert.equal(dispatchEventStub.lastCall.args[0].type, 'reload');
    });

    test('closing dialog does not trigger reload if no request made', async () => {
      const changes: ChangeInfo[] = [change1, change2];
      getChangesStub.returns(Promise.resolve(changes));

      model.sync(changes);
      await waitUntilObserved(
        model.loadingState$,
        state => state === LoadingState.LOADED
      );
      await selectChange(change1);
      await selectChange(change2);
      await element.updateComplete;

      queryAndAssert<GrButton>(query(element, 'gr-dialog'), '#cancel').click();

      assert.isFalse(dispatchEventStub.called);
    });
  });

  test('computePermittedLabels', async () => {
    // {} if no change is selected
    assert.deepEqual(element.computePermittedLabels(), {});

    const changes: ChangeInfo[] = [change1];
    getChangesStub.returns(Promise.resolve(changes));
    model.sync(changes);
    await waitUntilObserved(
      model.loadingState$,
      state => state === LoadingState.LOADED
    );
    await selectChange(change1);
    await element.updateComplete;

    assert.deepEqual(element.computePermittedLabels(), {
      A: ['-1', '0', '+1', '+2'],
      B: ['-1', '0'],
      C: ['-1', '0'],
      change1OnlyLabelD: ['0'],
      change1OnlyTriggerLabelE: ['0'],
    });

    changes.push(change2);
    getChangesStub.returns(Promise.resolve(changes));
    model.sync(changes);
    await waitUntilObserved(
      model.loadingState$,
      state => state === LoadingState.LOADED
    );
    await selectChange(change2);
    await element.updateComplete;

    assert.deepEqual(element.computePermittedLabels(), {
      A: ['-1', '0', '+1', '+2'],
      B: ['0'],
      C: [],
    });
  });

  test('computeCommonPermittedLabels', async () => {
    const createChangeWithLabels = (
      num: NumericChangeId,
      labelNames: string[],
      triggerLabels?: string[]
    ) => {
      const change = createChange();
      change._number = num;
      change.submit_requirements = [];
      change.labels = {};
      change.permitted_labels = {};
      for (const label of labelNames) {
        change.labels[label] = {value: null} as LabelInfo;
        if (!triggerLabels?.includes(label)) {
          change.submit_requirements.push(
            createSubmitRequirementResultInfo(`label:${label}=MAX`)
          );
        }
        change.permitted_labels[label] = ['0'];
      }
      return change;
    };

    const changes: ChangeInfo[] = [
      createChangeWithLabels(
        1 as NumericChangeId,
        ['a', 'triggerLabelB', 'c'],
        ['triggerLabelB']
      ),
      createChangeWithLabels(
        2 as NumericChangeId,
        ['triggerLabelB', 'c', 'd'],
        ['triggerLabelB']
      ),
      createChangeWithLabels(3 as NumericChangeId, ['c', 'd', 'e']),
      createChangeWithLabels(4 as NumericChangeId, ['x', 'y', 'z']),
    ];
    // Labels for each change are [a,b,c] [b,c,d] [c,d,e] [x,y,z]
    getChangesStub.returns(Promise.resolve(changes));
    model.sync(changes);

    await waitUntilObserved(
      model.loadingState$,
      state => state === LoadingState.LOADED
    );
    await selectChange(
      createChangeWithLabels(1 as NumericChangeId, ['a', 'triggerLabelB', 'c'])
    );
    await element.updateComplete;

    // Code-Review is not a common permitted label
    assert.deepEqual(
      element.computeCommonPermittedLabels(element.computePermittedLabels()),
      [
        {name: 'a', value: null},
        {name: 'c', value: null},
        {name: 'triggerLabelB', value: null},
      ]
    );

    await selectChange(
      createChangeWithLabels(2 as NumericChangeId, ['triggerLabelB', 'c', 'd'])
    );
    assert.deepEqual(
      element.computeCommonTriggerLabels(element.computePermittedLabels()),
      [{name: 'triggerLabelB', value: null}]
    );

    await element.updateComplete;

    // Intersection of [CR, 'a', 'triggerLabelB', 'c']
    // [CR, 'triggerLabelB', 'c', 'd'] is [triggerLabelB,c]
    // Code-Review is not a common permitted label
    assert.deepEqual(
      element.computeCommonPermittedLabels(element.computePermittedLabels()),
      [
        {name: 'c', value: null},
        {name: 'triggerLabelB', value: null},
      ]
    );
    assert.deepEqual(
      element.computeCommonTriggerLabels(element.computePermittedLabels()),
      [{name: 'triggerLabelB', value: null}]
    );

    await selectChange(
      createChangeWithLabels(3 as NumericChangeId, ['c', 'd', 'e'])
    );

    await element.updateComplete;

    // Intersection of [a,triggerLabelB,c] [triggerLabelB,c,d] [c,d,e] is [c]
    assert.deepEqual(
      element.computeCommonPermittedLabels(element.computePermittedLabels()),
      [{name: 'c', value: null}]
    );
    assert.deepEqual(
      element.computeCommonTriggerLabels(element.computePermittedLabels()),
      []
    );

    await selectChange(
      createChangeWithLabels(4 as NumericChangeId, ['x', 'y', 'z'])
    );
    assert.deepEqual(
      element.computeCommonTriggerLabels(element.computePermittedLabels()),
      []
    );

    await element.updateComplete;

    // Intersection of [a,triggerLabelB,c] [triggerLabelB,c,d] [c,d,e] [x,y,z]
    // is []
    assert.deepEqual(
      element.computeCommonPermittedLabels(element.computePermittedLabels()),
      []
    );
  });
});
