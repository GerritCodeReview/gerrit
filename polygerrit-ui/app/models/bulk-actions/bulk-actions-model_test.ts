/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {createChange} from '../../test/test-data-generators';
import {ChangeInfo, NumericChangeId} from '../../api/rest-api';
import {BulkActionsModel, LoadingState} from './bulk-actions-model';
import {getAppContext} from '../../services/app-context';
import '../../test/common-test-setup-karma';
import {stubRestApi, waitUntilObserved} from '../../test/test-utils';
import {mockPromise, MockPromise} from '../../test/test-utils';
import {
  listChangesOptionsToHex,
  ListChangesOption,
} from '../../utils/change-util';

const EXPECTED_QUERY_OPTIONS = listChangesOptionsToHex(
  ListChangesOption.CHANGE_ACTIONS,
  ListChangesOption.CURRENT_ACTIONS,
  ListChangesOption.CURRENT_REVISION,
  ListChangesOption.DETAILED_LABELS
);

suite('bulk actions model test', () => {
  let bulkActionsModel: BulkActionsModel;
  setup(() => {
    bulkActionsModel = new BulkActionsModel(getAppContext().restApiService);
  });

  test('add changes before sync', () => {
    const c1 = createChange();
    c1._number = 1 as NumericChangeId;
    const c2 = createChange();
    c2._number = 2 as NumericChangeId;

    assert.isEmpty(bulkActionsModel.getState().selectedChangeNums);

    assert.throws(() => bulkActionsModel.addSelectedChangeNum(c1._number));
    assert.isEmpty(bulkActionsModel.getState().selectedChangeNums);
    bulkActionsModel.sync([c1, c2]);

    bulkActionsModel.addSelectedChangeNum(c2._number);
    assert.sameMembers(bulkActionsModel.getState().selectedChangeNums, [
      c2._number,
    ]);

    bulkActionsModel.removeSelectedChangeNum(c2._number);
    assert.isEmpty(bulkActionsModel.getState().selectedChangeNums);
  });

  test('add and remove selected changes', () => {
    const c1 = createChange();
    c1._number = 1 as NumericChangeId;
    const c2 = createChange();
    c2._number = 2 as NumericChangeId;
    bulkActionsModel.sync([c1, c2]);

    assert.isEmpty(bulkActionsModel.getState().selectedChangeNums);

    bulkActionsModel.addSelectedChangeNum(c1._number);
    assert.sameMembers(bulkActionsModel.getState().selectedChangeNums, [
      c1._number,
    ]);

    bulkActionsModel.addSelectedChangeNum(c2._number);
    assert.sameMembers(bulkActionsModel.getState().selectedChangeNums, [
      c1._number,
      c2._number,
    ]);

    bulkActionsModel.removeSelectedChangeNum(c1._number);
    assert.sameMembers(bulkActionsModel.getState().selectedChangeNums, [
      c2._number,
    ]);

    bulkActionsModel.removeSelectedChangeNum(c2._number);
    assert.isEmpty(bulkActionsModel.getState().selectedChangeNums);
  });

  test('stale changes are removed from the model', async () => {
    const c1 = createChange();
    c1._number = 1 as NumericChangeId;
    const c2 = createChange();
    c2._number = 2 as NumericChangeId;
    bulkActionsModel.sync([c1, c2]);

    bulkActionsModel.addSelectedChangeNum(c1._number);
    bulkActionsModel.addSelectedChangeNum(c2._number);

    let selectedChangeNums = await waitUntilObserved(
      bulkActionsModel!.selectedChangeNums$,
      s => s.length === 2
    );

    assert.sameMembers(selectedChangeNums, [c1._number, c2._number]);

    bulkActionsModel.sync([c1]);
    selectedChangeNums = await waitUntilObserved(
      bulkActionsModel!.selectedChangeNums$,
      s => s.length === 1
    );
    assert.sameMembers(selectedChangeNums, [c1._number]);
  });

  test('sync fetches new changes', async () => {
    const c1 = createChange();
    c1._number = 1 as NumericChangeId;
    const c2 = createChange();
    c2._number = 2 as NumericChangeId;

    assert.equal(
      bulkActionsModel.getState().loadingState,
      LoadingState.NOT_SYNCED
    );

    const responsePromise: MockPromise<ChangeInfo[]> = mockPromise();
    const getChangesStub = stubRestApi('getChanges').callsFake(
      (changesPerPage, query, offset, options) => {
        assert.isUndefined(changesPerPage);
        assert.strictEqual(query, 'change:1 OR change:2');
        assert.isUndefined(offset);
        assert.strictEqual(options, EXPECTED_QUERY_OPTIONS);
        return responsePromise;
      }
    );
    bulkActionsModel.sync([c1, c2]);
    assert.isTrue(getChangesStub.calledOnce);
    await waitUntilObserved(
      bulkActionsModel.loadingState$,
      s => s === LoadingState.LOADING
    );

    responsePromise.resolve([
      {...createChange(), _number: 1, subject: 'Subject 1'},
      {...createChange(), _number: 2, subject: 'Subject 2'},
    ] as ChangeInfo[]);
    await waitUntilObserved(
      bulkActionsModel.loadingState$,
      s => s === LoadingState.LOADED
    );

    assert.strictEqual(
      bulkActionsModel.allChanges.get(1 as NumericChangeId)?.subject,
      'Subject 1'
    );
    assert.strictEqual(
      bulkActionsModel.allChanges.get(2 as NumericChangeId)?.subject,
      'Subject 2'
    );
  });

  test('sync ignores outdated fetch responses', async () => {
    const c1 = createChange();
    c1._number = 1 as NumericChangeId;
    const c2 = createChange();
    c2._number = 2 as NumericChangeId;

    const responsePromise1 = mockPromise<ChangeInfo[]>();
    let promise = responsePromise1;
    const getChangesStub = stubRestApi('getChanges').callsFake(
      (changesPerPage, query, offset, options) => {
        assert.isUndefined(changesPerPage);
        assert.strictEqual(query, 'change:1 OR change:2');
        assert.isUndefined(offset);
        assert.strictEqual(options, EXPECTED_QUERY_OPTIONS);
        return promise;
      }
    );
    bulkActionsModel.sync([c1, c2]);
    assert.strictEqual(getChangesStub.callCount, 1);
    await waitUntilObserved(
      bulkActionsModel.loadingState$,
      s => s === LoadingState.LOADING
    );
    const responsePromise2 = mockPromise<ChangeInfo[]>();

    promise = responsePromise2;
    bulkActionsModel.sync([c1, c2]);
    assert.strictEqual(getChangesStub.callCount, 2);

    responsePromise2.resolve([
      {...createChange(), _number: 1, subject: 'Subject 1'},
      {...createChange(), _number: 2, subject: 'Subject 2'},
    ] as ChangeInfo[]);

    await waitUntilObserved(
      bulkActionsModel.loadingState$,
      s => s === LoadingState.LOADED
    );
    assert.strictEqual(
      bulkActionsModel.allChanges.get(1 as NumericChangeId)?.subject,
      'Subject 1'
    );
    assert.strictEqual(
      bulkActionsModel.allChanges.get(2 as NumericChangeId)?.subject,
      'Subject 2'
    );

    // Resolve the old promise.
    responsePromise1.resolve([
      {...createChange(), _number: 1, subject: 'Subject 1-old'},
      {...createChange(), _number: 2, subject: 'Subject 2-old'},
    ] as ChangeInfo[]);
    await flush();

    // No change should happen.
    assert.strictEqual(
      bulkActionsModel.allChanges.get(1 as NumericChangeId)?.subject,
      'Subject 1'
    );
    assert.strictEqual(
      bulkActionsModel.allChanges.get(2 as NumericChangeId)?.subject,
      'Subject 2'
    );
  });
});
