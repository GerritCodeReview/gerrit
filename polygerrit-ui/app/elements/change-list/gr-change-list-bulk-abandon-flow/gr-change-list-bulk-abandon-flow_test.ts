/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {createChange} from '../../../test/test-data-generators';
import {NumericChangeId, ChangeInfo, ChangeStatus} from '../../../api/rest-api';
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
  mockPromise,
  query,
} from '../../../test/test-utils';
import {GrButton} from '../../shared/gr-button/gr-button';
import {tap} from '@polymer/iron-test-helpers/mock-interactions';
import {ProgressStatus} from '../../../constants/constants';

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
    getChangesStub = stubRestApi('getDetailedChangesWithActions');

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
    await flush();
    // await waitUntil(() => element.selectedChanges.length > 0);
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

  test('abandon button is enabled if change is already abandoned', async () => {
    const changes: ChangeInfo[] = [
      {...change1, actions: {}, status: ChangeStatus.ABANDONED},
    ];
    getChangesStub.returns(changes);
    model.sync(changes);
    await waitUntilObserved(
      model.loadingState$,
      state => state === LoadingState.LOADED
    );
    await selectChange(change1);
    await element.updateComplete;
    await flush();
    assert.isFalse(queryAndAssert<GrButton>(element, '#abandon').disabled);

    tap(queryAndAssert(query(element, 'gr-dialog'), '#confirm'));

    await waitUntil(
      () =>
        queryAndAssert<HTMLTableDataCellElement>(
          element,
          '#status'
        ).innerText.trim() === `Status: ${ProgressStatus.SUCCESSFUL}`
    );
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
      queryAndAssert<HTMLTableDataCellElement>(
        element,
        '#status'
      ).innerText.trim(),
      `Status: ${ProgressStatus.NOT_STARTED}`
    );

    const executeChangeAction = mockPromise<Response>();
    stubRestApi('executeChangeAction').returns(executeChangeAction);

    assert.isNotOk(
      queryAndAssert<GrButton>(query(element, 'gr-dialog'), '#confirm').disabled
    );
    assert.isNotOk(
      queryAndAssert<GrButton>(query(element, 'gr-dialog'), '#cancel').disabled
    );

    tap(queryAndAssert(query(element, 'gr-dialog'), '#confirm'));
    await element.updateComplete;

    assert.isTrue(
      queryAndAssert<GrButton>(query(element, 'gr-dialog'), '#confirm').disabled
    );
    assert.isTrue(
      queryAndAssert<GrButton>(query(element, 'gr-dialog'), '#cancel').disabled
    );

    assert.equal(
      queryAndAssert<HTMLTableDataCellElement>(
        element,
        '#status'
      ).innerText.trim(),
      `Status: ${ProgressStatus.RUNNING}`
    );

    executeChangeAction.resolve({...new Response(), status: 200});
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
      queryAndAssert<HTMLTableDataCellElement>(
        element,
        '#status'
      ).innerText.trim(),
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
      queryAndAssert<HTMLTableDataCellElement>(
        element,
        '#status'
      ).innerText.trim(),
      `Status: ${ProgressStatus.NOT_STARTED}`
    );

    const executeChangeAction = mockPromise<Response>();
    stubRestApi('executeChangeAction').returns(executeChangeAction);

    tap(queryAndAssert(query(element, 'gr-dialog'), '#confirm'));
    await element.updateComplete;

    assert.equal(
      queryAndAssert<HTMLTableDataCellElement>(
        element,
        '#status'
      ).innerText.trim(),
      `Status: ${ProgressStatus.RUNNING}`
    );

    executeChangeAction.resolve({...new Response(), status: 500});
    await waitUntil(
      () => element.progress.get(1 as NumericChangeId) === ProgressStatus.FAILED
    );

    assert.equal(
      queryAndAssert<HTMLTableDataCellElement>(
        element,
        '#status'
      ).innerText.trim(),
      `Status: ${ProgressStatus.FAILED}`
    );
  });
});
