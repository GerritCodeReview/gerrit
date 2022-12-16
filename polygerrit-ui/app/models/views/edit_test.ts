/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {assert} from '@open-wc/testing';
import {
  NumericChangeId,
  RepoName,
  RevisionPatchSetNum,
} from '../../api/rest-api';
import {GerritView} from '../../services/router/router-model';
import '../../test/common-test-setup';
import {ChangeChildView, ChangeViewState} from './change';
import {createEditUrl} from './edit';

suite('edit view state tests', () => {
  test('createEditUrl', () => {
    const params: ChangeViewState = {
      view: GerritView.CHANGE,
      childView: ChangeChildView.EDIT,
      changeNum: 42 as NumericChangeId,
      repo: 'test-project' as RepoName,
      path: 'x+y/path.cpp' as RepoName,
      patchNum: 12 as RevisionPatchSetNum,
      lineNum: 31,
    };
    assert.equal(
      createEditUrl(params),
      '/c/test-project/+/42/12/x%252By/path.cpp,edit#31'
    );

    window.CANONICAL_PATH = '/base';
    assert.equal(createEditUrl(params).substring(0, 5), '/base');
    window.CANONICAL_PATH = undefined;
  });
});
