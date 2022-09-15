/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {assert} from '@open-wc/testing';
import {
  BasePatchSetNum,
  NumericChangeId,
  RepoName,
  RevisionPatchSetNum,
} from '../../api/rest-api';
import {GerritView} from '../../services/router/router-model';
import '../../test/common-test-setup';
import {createDiffUrl, DiffViewState} from './diff';

suite('diff view state tests', () => {
  test('createDiffUrl', () => {
    const params: DiffViewState = {
      view: GerritView.DIFF,
      changeNum: 42 as NumericChangeId,
      path: 'x+y/path.cpp' as RepoName,
      patchNum: 12 as RevisionPatchSetNum,
      project: '' as RepoName,
    };
    assert.equal(createDiffUrl(params), '/c/42/12/x%252By/path.cpp');

    params.project = 'test' as RepoName;
    assert.equal(createDiffUrl(params), '/c/test/+/42/12/x%252By/path.cpp');

    params.basePatchNum = 6 as BasePatchSetNum;
    assert.equal(createDiffUrl(params), '/c/test/+/42/6..12/x%252By/path.cpp');

    params.path = 'foo bar/my+file.txt%';
    params.patchNum = 2 as RevisionPatchSetNum;
    delete params.basePatchNum;
    assert.equal(
      createDiffUrl(params),
      '/c/test/+/42/2/foo+bar/my%252Bfile.txt%2525'
    );

    params.path = 'file.cpp';
    params.lineNum = 123;
    assert.equal(createDiffUrl(params), '/c/test/+/42/2/file.cpp#123');

    params.leftSide = true;
    assert.equal(createDiffUrl(params), '/c/test/+/42/2/file.cpp#b123');
  });

  test('diff with repo name encoding', () => {
    const params: DiffViewState = {
      view: GerritView.DIFF,
      changeNum: 42 as NumericChangeId,
      path: 'x+y/path.cpp',
      patchNum: 12 as RevisionPatchSetNum,
      project: 'x+/y' as RepoName,
    };
    assert.equal(createDiffUrl(params), '/c/x%252B/y/+/42/12/x%252By/path.cpp');
  });
});
