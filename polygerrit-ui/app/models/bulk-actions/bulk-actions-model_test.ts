/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {createChange} from '../../test/test-data-generators';
import {ChangeInfoId} from '../../api/rest-api';
import {BulkActionsModel} from './bulk-actions-model';
import {getAppContext} from '../../services/app-context';
import '../../test/common-test-setup-karma';
import {waitUntilObserved} from '../../test/test-utils';

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
});
