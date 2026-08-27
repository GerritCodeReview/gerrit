/**
 * @license
 * Copyright 2026 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import * as sinon from 'sinon';
import '../../test/common-test-setup';
import {assert} from '@open-wc/testing';
import {FilesModel} from './files-model';
import {ChangeModel} from './change-model';
import {
  createCommit,
  createParsedChange,
  createRevision,
  TEST_NUMERIC_CHANGE_ID,
} from '../../test/test-data-generators';
import {stubRestApi, waitUntilObserved} from '../../test/test-utils';
import {
  CommitId,
  FileNameToFileInfoMap,
  PARENT,
  RevisionPatchSetNum,
} from '../../types/common';
import {getAppContext} from '../../services/app-context';
import {testResolver} from '../../test/common-test-setup';
import {changeViewModelToken} from '../views/change';
import {navigationToken} from '../../elements/core/gr-navigation/gr-navigation';
import {userModelToken} from '../user/user-model';
import {pluginLoaderToken} from '../../elements/shared/gr-js-api-interface/gr-plugin-loader';

suite('files-model tests', () => {
  let changeModel: ChangeModel;
  let filesModel: FilesModel;

  setup(() => {
    changeModel = new ChangeModel(
      testResolver(navigationToken),
      testResolver(changeViewModelToken),
      getAppContext().restApiService,
      testResolver(userModelToken),
      testResolver(pluginLoaderToken),
      getAppContext().reportingService
    );
    filesModel = new FilesModel(changeModel, getAppContext().restApiService);
  });

  teardown(() => {
    filesModel.finalize();
    changeModel.finalize();
  });

  test('cleanly merged paths for merge commit', async () => {
    const getChangeOrEditFilesStub = stubRestApi('getChangeOrEditFiles');
    // Normal files in patchset 1 vs parent 1
    getChangeOrEditFilesStub
      .withArgs(TEST_NUMERIC_CHANGE_ID, {patchNum: 1 as RevisionPatchSetNum})
      .resolves({
        'conflict.txt': {},
      } as FileNameToFileInfoMap);
    // All files in patchset 1 vs base -1 (Auto Merge)
    getChangeOrEditFilesStub
      .withArgs(TEST_NUMERIC_CHANGE_ID, {
        basePatchNum: -1 as any,
        patchNum: 1 as RevisionPatchSetNum,
      })
      .resolves({
        'conflict.txt': {},
        'cleanlyMerged.txt': {old_path: 'cleanlyMergedOld.txt'},
      } as FileNameToFileInfoMap);

    const mergeCommit = {
      ...createCommit(),
      parents: [
        {commit: 'p1' as CommitId, subject: 'parent 1'},
        {commit: 'p2' as CommitId, subject: 'parent 2'},
      ],
    };

    const change = {
      ...createParsedChange(),
      _number: TEST_NUMERIC_CHANGE_ID,
      revisions: {
        rev1: {
          ...createRevision(1),
          commit: mergeCommit,
        },
      },
      current_revision: 'rev1' as CommitId,
    };

    changeModel.updateStateChange(change);
    changeModel.updateStatePatchNum(1 as RevisionPatchSetNum);
    changeModel.updateStateBasePatchNum(PARENT);

    const cleanlyMergedPaths = await waitUntilObserved(
      filesModel.cleanlyMergedPaths$,
      paths => paths.length > 0
    );
    assert.deepEqual(cleanlyMergedPaths, ['cleanlyMerged.txt']);

    const cleanlyMergedOldPaths = await waitUntilObserved(
      filesModel.cleanlyMergedOldPaths$,
      paths => paths.length > 0
    );
    assert.deepEqual(cleanlyMergedOldPaths, ['cleanlyMergedOld.txt']);
  });

  test('non-merge commit does not query -1 base', async () => {
    const getChangeOrEditFilesStub = stubRestApi('getChangeOrEditFiles');
    getChangeOrEditFilesStub.resolves({
      'file1.txt': {},
    } as FileNameToFileInfoMap);

    const singleParentCommit = {
      ...createCommit(),
      parents: [{commit: 'p1' as CommitId, subject: 'parent 1'}],
    };

    const change = {
      ...createParsedChange(),
      _number: TEST_NUMERIC_CHANGE_ID,
      revisions: {
        rev1: {
          ...createRevision(1),
          commit: singleParentCommit,
        },
      },
      current_revision: 'rev1' as CommitId,
    };

    changeModel.updateStateChange(change);
    changeModel.updateStatePatchNum(1 as RevisionPatchSetNum);
    changeModel.updateStateBasePatchNum(PARENT);

    const files = await waitUntilObserved(
      filesModel.files$,
      f => f.length > 0
    );
    assert.equal(files.length, 1);

    // Verify getChangeOrEditFiles was not called with basePatchNum: -1
    assert.isFalse(
      getChangeOrEditFilesStub.calledWith(TEST_NUMERIC_CHANGE_ID, sinon.match({
        basePatchNum: -1,
      }))
    );

    const cleanlyMergedPaths = await waitUntilObserved(
      filesModel.cleanlyMergedPaths$,
      paths => paths.length === 0
    );
    assert.deepEqual(cleanlyMergedPaths, []);
  });
});
