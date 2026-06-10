/**
 * @license
 * Copyright 2026 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {assert} from '@open-wc/testing';
import * as sinon from 'sinon';
import {navigationToken} from '../../elements/core/gr-navigation/gr-navigation';
import {pluginLoaderToken} from '../../elements/shared/gr-js-api-interface/gr-plugin-loader';
import {getAppContext} from '../../services/app-context';
import '../../test/common-test-setup';
import {testResolver} from '../../test/common-test-setup';
import {createChange, createFileInfo} from '../../test/test-data-generators';
import {stubRestApi, waitUntilObserved} from '../../test/test-utils';
import {ChecksModel} from '../checks/checks-model';
import {CommentsModel} from '../comments/comments-model';
import {userModelToken} from '../user/user-model';
import {changeViewModelToken} from '../views/change';
import {ChangeModel} from './change-model';
import {FilesModel, MAX_FILES_LIMIT} from './files-model';

suite('files-model tests', () => {
  let filesModel: FilesModel;
  let changeModel: ChangeModel;
  let commentsModel: CommentsModel;
  let checksModel: ChecksModel;

  setup(async () => {
    // Instantiate dependencies
    changeModel = new ChangeModel(
      testResolver(navigationToken),
      testResolver(changeViewModelToken),
      getAppContext().restApiService,
      testResolver(userModelToken),
      testResolver(pluginLoaderToken),
      getAppContext().reportingService,
    );
    commentsModel = new CommentsModel(
      changeModel,
      getAppContext().restApiService,
      getAppContext().reportingService,
    );
    checksModel = new ChecksModel(
      changeModel,
      testResolver(changeViewModelToken),
      testResolver(pluginLoaderToken),
      getAppContext().reportingService,
    );

    filesModel = new FilesModel(
      changeModel,
      commentsModel,
      checksModel,
      getAppContext().restApiService,
      getAppContext().reportingService,
    );
  });

  teardown(() => {
    filesModel.finalize();
    changeModel.finalize();
    commentsModel.finalize();
    checksModel.finalize();
  });

  test('truncates files list by default when exceeding limit', async () => {
    // Create 1200 files
    const mockFiles: {[path: string]: any} = {};
    for (let i = 0; i < 1200; i++) {
      mockFiles[`file_${i}.txt`] = createFileInfo();
    }

    stubRestApi('getChangeOrEditFiles').callsFake(() =>
      Promise.resolve(mockFiles),
    );

    // Trigger loading files in model by updating change state
    const change = createChange();
    changeModel.updateStateChange(change);
    changeModel.updateStatePatchNum(1 as any);

    // Wait for the files to load and verify truncation
    const files = await waitUntilObserved(
      filesModel.files$,
      (files) => files.length > 0,
    );

    assert.equal(files.length, MAX_FILES_LIMIT);

    const truncated = await waitUntilObserved(
      filesModel.filesTruncated$,
      (t) => t !== undefined,
    );
    assert.isTrue(truncated);

    const totalCount = await waitUntilObserved(
      filesModel.totalFilesCount$,
      (c) => c > 0,
    );
    assert.equal(totalCount, 1200);

    // Verify rawFiles holds all files
    const rawFiles = await waitUntilObserved(
      filesModel.rawFiles$,
      (r) => r.length > 0,
    );
    assert.equal(rawFiles.length, 1200);
  });

  test('loads all files when loadAllFiles is toggled', async () => {
    const mockFiles: {[path: string]: any} = {};
    for (let i = 0; i < 1200; i++) {
      mockFiles[`file_${i}.txt`] = createFileInfo();
    }

    stubRestApi('getChangeOrEditFiles').callsFake(() =>
      Promise.resolve(mockFiles),
    );

    const change = createChange();
    changeModel.updateStateChange(change);
    changeModel.updateStatePatchNum(1 as any);

    // Wait for initial truncated load
    let files = await waitUntilObserved(
      filesModel.files$,
      (files) => files.length === MAX_FILES_LIMIT,
    );
    assert.equal(files.length, MAX_FILES_LIMIT);

    // Toggle load all
    filesModel.setLoadAllFiles(true);

    // Verify it updates to show all
    files = await waitUntilObserved(
      filesModel.files$,
      (files) => files.length === 1200,
    );
    assert.equal(files.length, 1200);

    const truncated = await waitUntilObserved(
      filesModel.filesTruncated$,
      (t) => t === false,
    );
    assert.isFalse(truncated);
  });
});
