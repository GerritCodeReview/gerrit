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
import {ChangeInfo, NumericChangeId} from '../../../api/rest-api';
import {getAppContext} from '../../../services/app-context';
import {fixture} from '@open-wc/testing-helpers';
import {wrapInProvider} from '../../../models/di-provider-element';
import {html} from 'lit';
import {SinonStubbedMember} from 'sinon';
import {RestApiService} from '../../../services/gr-rest-api/gr-rest-api';
import {createChange} from '../../../test/test-data-generators';
import './gr-change-list-bulk-vote-flow';

const change1: ChangeInfo = {
  ...createChange(),
  _number: 1 as NumericChangeId,
  permitted_labels: {
    A: ['-1', '0', '+1', '+2'],
    B: ['-1', '0'],
    C: ['-1', '0'],
    D: ['0'],
  },
};
const change2: ChangeInfo = {
  ...createChange(),
  _number: 2 as NumericChangeId,
  permitted_labels: {
    A: ['-1', '0', '+1', '+2'],
    B: ['0', ' +1'],
    C: ['+1', '+2'],
  },
};

suite('gr-change-list-bulk-flow tests', () => {
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
});
