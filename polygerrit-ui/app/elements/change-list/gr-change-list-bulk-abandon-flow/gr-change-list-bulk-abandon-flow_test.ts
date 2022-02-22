/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {createChange} from '../../../test/test-data-generators';
import {NumericChangeId, ChangeInfo} from '../../../api/rest-api';
import {GrChangeListBulkAbandonFlow} from './gr-change-list-bulk-abandon-flow';
import '../../../test/common-test-setup-karma';
import {
  BulkActionsModel,
  bulkActionsModelToken,
  LoadingState,
} from '../../../models/bulk-actions/bulk-actions-model';
import './gr-change-list-bulk-abandon-flow';
import {fixture, waitUntil} from '@open-wc/testing-helpers';
import {wrapInProvider} from '../../../models/di-provider-element';
import {html} from 'lit';
import {getAppContext} from '../../../services/app-context';
import {
  waitUntilObserved,
  stubRestApi,
  queryAndAssert,
} from '../../../test/test-utils';
import {GrButton} from '../../shared/gr-button/gr-button';
import {ProgressStatus} from '../../change/gr-confirm-cherrypick-dialog/gr-confirm-cherrypick-dialog';
import {tap} from '@polymer/iron-test-helpers/mock-interactions';

const change1: ChangeInfo = {...createChange(), _number: 1 as NumericChangeId};
const change2: ChangeInfo = {...createChange(), _number: 2 as NumericChangeId};

suite('gr-change-list-bulk-abandon-flow tests', () => {
  let element: GrChangeListBulkAbandonFlow;
  let model: BulkActionsModel;
  let getChangesStub: sinon.SinonStub;

  async function selectChange(change: ChangeInfo) {
    model.addSelectedChangeNum(change._number);
    await waitUntilObserved(model.selectedChangeNums$, selectedChangeNums =>
      selectedChangeNums.includes(change._number)
    );
    await element.updateComplete;
  }

  setup(async () => {
    model = new BulkActionsModel(getAppContext().restApiService);
    getChangesStub = stubRestApi('getChanges');

    element = (
      await fixture(
        wrapInProvider(
          html`<gr-change-list-bulk-abandon-flow></gr-change-list-bulk-abandon-flow>`,
          bulkActionsModelToken,
          model
        )
      )
    ).querySelector('gr-change-list-bulk-abandon-flow')!;
    await element.updateComplete;
  });

  test('button state updates as changes are updated', async () => {
    const changes: ChangeInfo[] = [{...change1, actions: {abandon: {}}}];
    getChangesStub.returns(changes);
    model.sync(changes);
    await waitUntilObserved(
      model.loadingState$,
      state => state === LoadingState.LOADED
    );
    await selectChange(change1);
    await element.updateComplete;
    assert.isFalse(queryAndAssert<GrButton>(element, '#abandon').disabled);

    changes.push({...change2, actions: {}});
    getChangesStub.restore();
    getChangesStub.returns(changes);
    model.sync(changes);
    await waitUntilObserved(
      model.loadingState$,
      state => state === LoadingState.LOADED
    );
    await selectChange(change2);
    await element.updateComplete;

    assert.isTrue(queryAndAssert<GrButton>(element, '#abandon').disabled);
  });

  test('progress updates as request is resolved', async () => {
    const changes: ChangeInfo[] = [{...change1, actions: {abandon: {}}}];
    getChangesStub.returns(changes);
    model.sync(changes);
    await waitUntilObserved(
      model.loadingState$,
      state => state === LoadingState.LOADED
    );
    await selectChange(change1);
    await element.updateComplete;

    assert.equal(
      queryAndAssert<HTMLTableDataCellElement>(element, '#status').innerText,
      `Status: ${ProgressStatus.NOT_STARTED}`
    );

    let resolver: (value?: Response) => void;

    stubRestApi('executeChangeAction').callsFake(
      () =>
        new Promise(resolve => {
          resolver = resolve;
        })
    );

    tap(queryAndAssert(element, '#confirm'));
    await element.updateComplete;

    assert.equal(
      queryAndAssert<HTMLTableDataCellElement>(element, '#status').innerText,
      `Status: ${ProgressStatus.RUNNING}`
    );

    resolver!({...new Response(), status: 200});
    await waitUntil(
      () =>
        element.progress.get(1 as NumericChangeId) === ProgressStatus.SUCCESSFUL
    );

    assert.equal(
      queryAndAssert<HTMLTableDataCellElement>(element, '#status').innerText,
      `Status: ${ProgressStatus.SUCCESSFUL}`
    );
  });

  test('failures are reflected to the progress dialog', async () => {
    const changes: ChangeInfo[] = [{...change1, actions: {abandon: {}}}];
    getChangesStub.returns(changes);
    model.sync(changes);
    await waitUntilObserved(
      model.loadingState$,
      state => state === LoadingState.LOADED
    );
    await selectChange(change1);
    await element.updateComplete;

    assert.equal(
      queryAndAssert<HTMLTableDataCellElement>(element, '#status').innerText,
      `Status: ${ProgressStatus.NOT_STARTED}`
    );

    let resolver: (value?: Response) => void;

    stubRestApi('executeChangeAction').callsFake(
      () =>
        new Promise(resolve => {
          resolver = resolve;
        })
    );

    tap(queryAndAssert(element, '#confirm'));
    await element.updateComplete;

    assert.equal(
      queryAndAssert<HTMLTableDataCellElement>(element, '#status').innerText,
      `Status: ${ProgressStatus.RUNNING}`
    );

    resolver!({...new Response(), status: 500});
    await waitUntil(
      () => element.progress.get(1 as NumericChangeId) === ProgressStatus.FAILED
    );

    assert.equal(
      queryAndAssert<HTMLTableDataCellElement>(element, '#status').innerText,
      `Status: ${ProgressStatus.FAILED}`
    );
  });
});
