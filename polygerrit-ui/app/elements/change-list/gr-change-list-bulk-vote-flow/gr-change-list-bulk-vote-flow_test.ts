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
import {tap} from '@polymer/iron-test-helpers/mock-interactions';
import {ProgressStatus} from '../../../constants/constants';
import './gr-change-list-bulk-vote-flow';

const change1: ChangeInfo = {
  ...createChange(),
  _number: 1 as NumericChangeId,
  permitted_labels: {
    A: ['-1', '0', '+1', '+2'],
    B: ['-1', '0'],
    C: ['-1', '0'],
    D: ['0'], // Does not exist on change2
  },
  labels: {
    A: {value: null} as LabelInfo,
    B: {value: null} as LabelInfo,
    C: {value: null} as LabelInfo,
    D: {value: null} as LabelInfo,
  },
  submit_requirements: [
    createSubmitRequirementResultInfo('label:A=MAX'),
    createSubmitRequirementResultInfo('label:B=MAX'),
    createSubmitRequirementResultInfo('label:C=MAX'),
    createSubmitRequirementResultInfo('label:D=MAX'),
  ],
};
const change2: ChangeInfo = {
  ...createChange(),
  _number: 2 as NumericChangeId,
  permitted_labels: {
    A: ['-1', '0', '+1', '+2'], // Intersects fully with change1
    B: ['0', ' +1'], // Intersects with change1 on 0
    C: ['+1', '+2'], // Does not intersect with change1 at all
  },
  labels: {
    A: {value: null} as LabelInfo,
    B: {value: null} as LabelInfo,
    C: {value: null} as LabelInfo,
  },
  submit_requirements: [
    createSubmitRequirementResultInfo('label:A=MAX'),
    createSubmitRequirementResultInfo('label:B=MAX'),
    createSubmitRequirementResultInfo('label:C=MAX'),
  ],
};

suite('gr-change-list-bulk-vote-flow tests', () => {
  let element: GrChangeListBulkVoteFlow;
  let model: BulkActionsModel;
  let getChangesStub: SinonStubbedMember<
    RestApiService['getDetailedChangesWithActions']
  >;

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
          <div slot="main">
            <div class="newSubmitRequirements scoresTable">
              <h3 class="heading-3">Submit requirements votes</h3>
              <gr-label-score-row name="A"> </gr-label-score-row>
              <gr-label-score-row name="B"> </gr-label-score-row>
              <gr-label-score-row name="C"> </gr-label-score-row>
              <gr-label-score-row name="D"> </gr-label-score-row>
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
    tap(queryAndAssert(scores[0], 'gr-button[data-value="+1"]'));
    tap(queryAndAssert(scores[1], 'gr-button[data-value="-1"]'));

    await element.updateComplete;

    assert.deepEqual(element.getLabelValues(), {
      A: 1,
      B: -1,
    });

    tap(queryAndAssert(query(element, 'gr-dialog'), '#confirm'));
    await element.updateComplete;

    assert.isTrue(
      queryAndAssert<GrButton>(query(element, 'gr-dialog'), '#confirm').disabled
    );
    assert.isTrue(
      queryAndAssert<GrButton>(query(element, 'gr-dialog'), '#cancel').disabled
    );

    assert.equal(
      element.progress.get(1 as NumericChangeId),
      ProgressStatus.RUNNING
    );

    saveChangeReview.resolve({...new Response(), status: 200});
    await waitUntil(
      () =>
        element.progress.get(1 as NumericChangeId) === ProgressStatus.SUCCESSFUL
    );

    assert.isTrue(
      queryAndAssert<GrButton>(query(element, 'gr-dialog'), '#confirm').disabled
    );
    assert.isNotOk(
      queryAndAssert<GrButton>(query(element, 'gr-dialog'), '#cancel').disabled
    );

    assert.equal(
      element.progress.get(1 as NumericChangeId),
      ProgressStatus.SUCCESSFUL
    );
  });

  test('closing dialog triggers a reload', async () => {
    const changes: ChangeInfo[] = [{...change1}, {...change2}];
    getChangesStub.returns(Promise.resolve(changes));

    const fireStub = sinon.stub(element, 'dispatchEvent');

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

    tap(queryAndAssert(query(element, 'gr-dialog'), '#confirm'));

    await waitUntil(
      () => element.progress.get(2 as NumericChangeId) === ProgressStatus.FAILED
    );

    assert.isFalse(fireStub.called);

    tap(queryAndAssert(query(element, 'gr-dialog'), '#cancel'));

    await waitUntil(() => fireStub.called);
    assert.equal(fireStub.lastCall.args[0].type, 'reload');
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
      D: ['0'],
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

  test('computeCommonLabels', async () => {
    const change3: ChangeInfo = {
      ...createChange(),
      _number: 3 as NumericChangeId,
    };
    const change4: ChangeInfo = {
      ...createChange(),
      _number: 4 as NumericChangeId,
    };

    change1.labels = {
      a: {value: null} as LabelInfo,
      b: {value: null} as LabelInfo,
      c: {value: null} as LabelInfo,
    };
    change1.submit_requirements = [
      createSubmitRequirementResultInfo('label:a=MAX'),
      createSubmitRequirementResultInfo('label:b=MAX'),
      createSubmitRequirementResultInfo('label:c=MAX'),
    ];

    change2.labels = {
      b: {value: null} as LabelInfo,
      c: {value: null} as LabelInfo,
      d: {value: null} as LabelInfo,
    };
    change2.submit_requirements = [
      createSubmitRequirementResultInfo('label:b=MAX'),
      createSubmitRequirementResultInfo('label:c=MAX'),
      createSubmitRequirementResultInfo('label:d=MAX'),
    ];

    change3.labels = {
      c: {value: null} as LabelInfo,
      d: {value: null} as LabelInfo,
      e: {value: null} as LabelInfo,
    };
    change3.submit_requirements = [
      createSubmitRequirementResultInfo('label:c=MAX'),
      createSubmitRequirementResultInfo('label:d=MAX'),
      createSubmitRequirementResultInfo('label:e=MAX'),
    ];

    change4.labels = {
      x: {value: null} as LabelInfo,
      y: {value: null} as LabelInfo,
      z: {value: null} as LabelInfo,
    };
    change4.submit_requirements = [
      createSubmitRequirementResultInfo('label:x=MAX'),
      createSubmitRequirementResultInfo('label:y=MAX'),
      createSubmitRequirementResultInfo('label:z=MAX'),
    ];

    const changes: ChangeInfo[] = [change1, change2, change3, change4];
    // Labels for each change are [a,b,c] [b,c,d] [c,d,e] [x,y,z]
    getChangesStub.returns(Promise.resolve(changes));
    model.sync(changes);

    await waitUntilObserved(
      model.loadingState$,
      state => state === LoadingState.LOADED
    );
    await selectChange(change1);
    await element.updateComplete;

    assert.deepEqual(element.computeCommonLabels(), [
      {name: 'a', value: null},
      {name: 'b', value: null},
      {name: 'c', value: null},
    ]);

    await selectChange(change2);
    await element.updateComplete;

    // Intersection of [a,b,c] [b,c,d] is [b,c]
    assert.deepEqual(element.computeCommonLabels(), [
      {name: 'b', value: null},
      {name: 'c', value: null},
    ]);

    await selectChange(change3);
    await element.updateComplete;

    // Intersection of [a,b,c] [b,c,d] [c,d,e] is [c]
    assert.deepEqual(element.computeCommonLabels(), [{name: 'c', value: null}]);

    await selectChange(change4);
    await element.updateComplete;

    // Intersection of [a,b,c] [b,c,d] [c,d,e] [x,y,z] is []
    assert.deepEqual(element.computeCommonLabels(), []);
  });
});
