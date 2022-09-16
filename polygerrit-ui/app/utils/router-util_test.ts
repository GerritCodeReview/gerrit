/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {assert} from '@open-wc/testing';
import {
  RepoName,
  NumericChangeId,
  RevisionPatchSetNum,
  BasePatchSetNum,
  EDIT,
} from '../api/rest-api';
import {PatchRangeParams} from '../elements/core/gr-router/gr-router';
import {ChangeViewState} from '../models/views/change';
import {EditViewState} from '../models/views/edit';
import {GerritView} from '../services/router/router-model';
import '../test/common-test-setup';
import {generateUrl, TEST_ONLY} from './router-util';

suite('router-util tests', () => {
  suite('generateUrl', () => {
    test('change', () => {
      const params: ChangeViewState = {
        view: GerritView.CHANGE,
        changeNum: 1234 as NumericChangeId,
        project: 'test' as RepoName,
      };

      assert.equal(generateUrl(params), '/c/test/+/1234');

      params.patchNum = 10 as RevisionPatchSetNum;
      assert.equal(generateUrl(params), '/c/test/+/1234/10');

      params.basePatchNum = 5 as BasePatchSetNum;
      assert.equal(generateUrl(params), '/c/test/+/1234/5..10');

      params.messageHash = '#123';
      assert.equal(generateUrl(params), '/c/test/+/1234/5..10#123');
    });

    test('change with repo name encoding', () => {
      const params: ChangeViewState = {
        view: GerritView.CHANGE,
        changeNum: 1234 as NumericChangeId,
        project: 'x+/y+/z+/w' as RepoName,
      };
      assert.equal(generateUrl(params), '/c/x%252B/y%252B/z%252B/w/+/1234');
    });

    test(EDIT, () => {
      const params: EditViewState = {
        view: GerritView.EDIT,
        changeNum: 42 as NumericChangeId,
        project: 'test' as RepoName,
        path: 'x+y/path.cpp',
        patchNum: EDIT as RevisionPatchSetNum,
      };
      assert.equal(
        generateUrl(params),
        '/c/test/+/42/edit/x%252By/path.cpp,edit'
      );
    });

    test('getPatchRangeExpression', () => {
      const params: PatchRangeParams = {};
      let actual = TEST_ONLY.getPatchRangeExpression(params);
      assert.equal(actual, '');

      params.patchNum = 4 as RevisionPatchSetNum;
      actual = TEST_ONLY.getPatchRangeExpression(params);
      assert.equal(actual, '4');

      params.basePatchNum = 2 as BasePatchSetNum;
      actual = TEST_ONLY.getPatchRangeExpression(params);
      assert.equal(actual, '2..4');

      delete params.patchNum;
      actual = TEST_ONLY.getPatchRangeExpression(params);
      assert.equal(actual, '2..');
    });
  });
});
