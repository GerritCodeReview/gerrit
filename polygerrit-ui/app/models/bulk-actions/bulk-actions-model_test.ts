/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {createChange} from '../../test/test-data-generators';
import {ChangeInfo, ChangeInfoId} from '../../api/rest-api';
import {BulkActionsModel} from './bulk-actions-model';
import {getAppContext} from '../../services/app-context';
import '../../test/common-test-setup-karma';
import {stubRestApi, waitUntilObserved} from '../../test/test-utils';
import {mockPromise, MockPromise} from '../../test/test-utils';
import {
  listChangesOptionsToHex,
  ListChangesOption,
} from '../../utils/change-util';

suite('bulk actions model test', () => {
  let bulkActionsModel: BulkActionsModel;
  setup(() => {
    bulkActionsModel = new BulkActionsModel(getAppContext().restApiService);
  });

  test('add changes before sync', () => {
    const c1 = createChange();
    c1.id = '1' as ChangeInfoId;
    const c2 = createChange();
    c2.id = '2' as ChangeInfoId;

    assert.isEmpty(bulkActionsModel.getState().selectedChangeIds);

    assert.throws(() => bulkActionsModel.addSelectedChangeId(c1.id));
    assert.isEmpty(bulkActionsModel.getState().selectedChangeIds);
    bulkActionsModel.sync([c1, c2]);

    bulkActionsModel.addSelectedChangeId(c2.id);
    assert.sameMembers(bulkActionsModel.getState().selectedChangeIds, [c2.id]);

    bulkActionsModel.removeSelectedChangeId(c2.id);
    assert.isEmpty(bulkActionsModel.getState().selectedChangeIds);
  });

  test('add and remove selected changes', () => {
    const c1 = createChange();
    c1.id = '1' as ChangeInfoId;
    const c2 = createChange();
    c2.id = '2' as ChangeInfoId;
    bulkActionsModel.sync([c1, c2]);

    assert.isEmpty(bulkActionsModel.getState().selectedChangeIds);

    bulkActionsModel.addSelectedChangeId(c1.id);
    assert.sameMembers(bulkActionsModel.getState().selectedChangeIds, [c1.id]);

    bulkActionsModel.addSelectedChangeId(c2.id);
    assert.sameMembers(bulkActionsModel.getState().selectedChangeIds, [
      c1.id,
      c2.id,
    ]);

    bulkActionsModel.removeSelectedChangeId(c1.id);
    assert.sameMembers(bulkActionsModel.getState().selectedChangeIds, [c2.id]);

    bulkActionsModel.removeSelectedChangeId(c2.id);
    assert.isEmpty(bulkActionsModel.getState().selectedChangeIds);
  });

  test('stale changes are removed from the model', async () => {
    const c1 = createChange();
    c1.id = '1' as ChangeInfoId;
    const c2 = createChange();
    c2.id = '2' as ChangeInfoId;
    bulkActionsModel.sync([c1, c2]);

    bulkActionsModel.addSelectedChangeId(c1.id);
    bulkActionsModel.addSelectedChangeId(c2.id);

    let selectedChangeIds = await waitUntilObserved(
      bulkActionsModel!.selectedChangeIds$,
      s => s.length === 2
    );

    assert.sameMembers(selectedChangeIds, [c1.id, c2.id]);

    bulkActionsModel.sync([c1]);
    selectedChangeIds = await waitUntilObserved(
      bulkActionsModel!.selectedChangeIds$,
      s => s.length === 1
    );
    assert.sameMembers(selectedChangeIds, [c1.id]);
  });

  test('sync fetches new changes', async () => {
    const c1 = createChange();
    c1.id = '1' as ChangeInfoId;
    const c2 = createChange();
    c2.id = '2' as ChangeInfoId;

    const responsePromise: MockPromise<ChangeInfo[]> = mockPromise();
    const getChangesStub = stubRestApi('getChanges').callsFake(
      (changesPerPage, query, offset, options) => {
        assert.isUndefined(changesPerPage);
        assert.strictEqual(query, 'change:1 OR change:2');
        assert.isUndefined(offset);
        assert.strictEqual(options, listChangesOptionsToHex(
          ListChangesOption.CHANGE_ACTIONS,
          ListChangesOption.CURRENT_ACTIONS,
          ListChangesOption.CURRENT_REVISION,
          ListChangesOption.DETAILED_LABELS
        ));
        return responsePromise;
      }
    );
    bulkActionsModel.sync([c1, c2]);
    assert.strictEqual(getChangesStub.callCount, 1);
    await waitUntilObserved(bulkActionsModel!.loading$, s => s === true);

    responsePromise.resolve([
      {...createChange(), id: '1', subject: 'Subject 1'},
      {...createChange(), id: '2', subject: 'Subject 2'},
    ] as ChangeInfo[]);
    await waitUntilObserved(bulkActionsModel!.loading$, s => s === false);
    assert.strictEqual(
      bulkActionsModel.allChanges.get('1' as ChangeInfoId)?.subject,
      'Subject 1'
    );
    assert.strictEqual(
      bulkActionsModel.allChanges.get('2' as ChangeInfoId)?.subject,
      'Subject 2'
    );
  });

  test('sync respects last fetch', async () => {
    const c1 = createChange();
    c1.id = '1' as ChangeInfoId;
    const c2 = createChange();
    c2.id = '2' as ChangeInfoId;

    const responsePromise1: MockPromise<ChangeInfo[]> = mockPromise();
    let promise = responsePromise1;
    const getChangesStub = stubRestApi('getChanges').callsFake(
      (changesPerPage, query, offset, options) => {
        assert.isUndefined(changesPerPage);
        assert.strictEqual(query, 'change:1 OR change:2');
        assert.isUndefined(offset);
        assert.strictEqual(options, listChangesOptionsToHex(
          ListChangesOption.CHANGE_ACTIONS,
          ListChangesOption.CURRENT_ACTIONS,
          ListChangesOption.CURRENT_REVISION,
          ListChangesOption.DETAILED_LABELS
        ));
        return promise;
      }
    );
    bulkActionsModel.sync([c1, c2]);
    assert.strictEqual(getChangesStub.callCount, 1);
    await waitUntilObserved(bulkActionsModel!.loading$, s => s === true);
    const responsePromise2: MockPromise<ChangeInfo[]> = mockPromise();

    promise = responsePromise2;
    bulkActionsModel.sync([c1, c2]);
    assert.strictEqual(getChangesStub.callCount, 2);

    responsePromise2.resolve([
      {...createChange(), id: '1', subject: 'Subject 1'},
      {...createChange(), id: '2', subject: 'Subject 2'},
    ] as ChangeInfo[]);

    await waitUntilObserved(bulkActionsModel!.loading$, s => s === false);
    assert.strictEqual(
      bulkActionsModel.allChanges.get('1' as ChangeInfoId)?.subject,
      'Subject 1'
    );
    assert.strictEqual(
      bulkActionsModel.allChanges.get('2' as ChangeInfoId)?.subject,
      'Subject 2'
    );

    // Resolve the old promise.
    responsePromise1.resolve([
      {...createChange(), id: '1', subject: 'Subject 1-old'},
      {...createChange(), id: '2', subject: 'Subject 2-old'},
    ] as ChangeInfo[]);
    await flush();

    // No change should happen.
    assert.strictEqual(
      bulkActionsModel.allChanges.get('1' as ChangeInfoId)?.subject,
      'Subject 1'
    );
    assert.strictEqual(
      bulkActionsModel.allChanges.get('2' as ChangeInfoId)?.subject,
      'Subject 2'
    );
  });
});
