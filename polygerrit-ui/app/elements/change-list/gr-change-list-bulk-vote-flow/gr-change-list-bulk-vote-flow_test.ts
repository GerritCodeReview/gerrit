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
import {waitUntilObserved, stubRestApi} from '../../../test/test-utils';
import {ChangeInfo, NumericChangeId, LabelInfo} from '../../../api/rest-api';
import {getAppContext} from '../../../services/app-context';
import {fixture} from '@open-wc/testing-helpers';
import {wrapInProvider} from '../../../models/di-provider-element';
import {html} from 'lit';
import {SinonStubbedMember} from 'sinon';
import {RestApiService} from '../../../services/gr-rest-api/gr-rest-api';
import {
  createChange,
  createSubmitRequirementResultInfo,
} from '../../../test/test-data-generators';
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
};
const change2: ChangeInfo = {
  ...createChange(),
  _number: 2 as NumericChangeId,
  permitted_labels: {
    A: ['-1', '0', '+1', '+2'], // Intersects fully with change1
    B: ['0', ' +1'], // Intersects with change1 on 0
    C: ['+1', '+2'], // Does not intersect with change1 at all
  },
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
            </div>
          </div>
        </gr-dialog>
      </gr-overlay> `);
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
