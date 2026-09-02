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
import {createDefaultPreferences} from '../../constants/constants';
import {
  createChangeViewState,
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
import {ChangeViewModel, changeViewModelToken} from '../views/change';
import {navigationToken} from '../../elements/core/gr-navigation/gr-navigation';
import {userModelToken} from '../user/user-model';
import {commentsModelToken} from '../comments/comments-model';
import {checksModelToken} from '../checks/checks-model';
import {pluginLoaderToken} from '../../elements/shared/gr-js-api-interface/gr-plugin-loader';

suite('files-model tests', () => {
  let changeModel: ChangeModel;
  let changeViewModel: ChangeViewModel;
  let filesModel: FilesModel;

  setup(() => {
    stubRestApi('getAllRevisionFiles').resolves({});
    stubRestApi('getChangeDetail').callsFake(() => new Promise(() => {}));
    stubRestApi('getChangeEdit').resolves(undefined);
    testResolver(userModelToken).setPreferences(createDefaultPreferences());
    changeViewModel = testResolver(changeViewModelToken);
    changeModel = new ChangeModel(
      testResolver(navigationToken),
      changeViewModel,
      getAppContext().restApiService,
      testResolver(userModelToken),
      testResolver(pluginLoaderToken),
      getAppContext().reportingService
    );
    filesModel = new FilesModel(
      changeModel,
      testResolver(commentsModelToken),
      testResolver(checksModelToken),
      getAppContext().restApiService,
      getAppContext().reportingService
    );
  });

  teardown(() => {
    filesModel.finalize();
    changeModel.finalize();
  });

  test('cleanly merged paths for merge commit', async () => {
    stubRestApi('getChangeOrEditFiles').callsFake((_changeNum, range) => {
      if (range?.basePatchNum === -1) {
        return Promise.resolve({
          'conflict.txt': {},
          'cleanlyMerged.txt': {old_path: 'cleanlyMergedOld.txt'},
        } as FileNameToFileInfoMap);
      }
      return Promise.resolve({
        'conflict.txt': {},
      } as FileNameToFileInfoMap);
    });

    const revision = createRevision(1);
    const mergeCommit = {
      ...revision.commit!,
      parents: [
        {commit: 'p1' as CommitId, subject: 'parent 1'},
        {commit: 'p2' as CommitId, subject: 'parent 2'},
      ],
    };

    const change = {
      ...createParsedChange(),
      _number: TEST_NUMERIC_CHANGE_ID,
      revisions: {
        sha1: {
          ...revision,
          commit: mergeCommit,
        },
      },
      current_revision: 'sha1' as CommitId,
    };

    changeViewModel.setState({
      ...createChangeViewState(),
      changeNum: TEST_NUMERIC_CHANGE_ID,
      patchNum: 1 as RevisionPatchSetNum,
      basePatchNum: PARENT,
    });
    changeModel.updateStateChange(change);

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
    const getChangeOrEditFilesStub = stubRestApi(
      'getChangeOrEditFiles'
    ).resolves({
      'file1.txt': {},
    });

    const revision = createRevision(1);
    const singleParentCommit = {
      ...revision.commit!,
      parents: [{commit: 'p1' as CommitId, subject: 'parent 1'}],
    };

    const change = {
      ...createParsedChange(),
      _number: TEST_NUMERIC_CHANGE_ID,
      revisions: {
        sha1: {
          ...revision,
          commit: singleParentCommit,
        },
      },
      current_revision: 'sha1' as CommitId,
    };

    changeViewModel.setState({
      ...createChangeViewState(),
      changeNum: TEST_NUMERIC_CHANGE_ID,
      patchNum: 1 as RevisionPatchSetNum,
      basePatchNum: PARENT,
    });
    changeModel.updateStateChange(change);

    const files = await waitUntilObserved(filesModel.files$, f => f.length > 0);
    assert.equal(files.length, 1);

    // Verify getChangeOrEditFiles was not called with basePatchNum: -1
    assert.isFalse(
      getChangeOrEditFilesStub.calledWith(
        TEST_NUMERIC_CHANGE_ID,
        sinon.match({
          basePatchNum: -1,
        })
      )
    );

    const cleanlyMergedPaths = await waitUntilObserved(
      filesModel.cleanlyMergedPaths$,
      paths => paths.length === 0
    );
    assert.deepEqual(cleanlyMergedPaths, []);
  });
});
