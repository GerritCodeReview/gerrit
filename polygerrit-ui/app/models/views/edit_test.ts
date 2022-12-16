/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {assert} from '@open-wc/testing';
import {RepoName, RevisionPatchSetNum} from '../../api/rest-api';
import '../../test/common-test-setup';
import {createEditViewState} from '../../test/test-data-generators';
import {ChangeViewState} from './change';
import {createEditUrl} from './edit';

suite('edit view state tests', () => {
  test('createEditUrl', () => {
    const params: ChangeViewState = {
      ...createEditViewState(),
      patchNum: 12 as RevisionPatchSetNum,
      editView: {path: 'x+y/path.cpp' as RepoName, lineNum: 31},
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
