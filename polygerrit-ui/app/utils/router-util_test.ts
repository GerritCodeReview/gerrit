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
  EDIT,
} from '../api/rest-api';
import {EditViewState} from '../models/views/edit';
import {GerritView} from '../services/router/router-model';
import '../test/common-test-setup';
import {generateUrl} from './router-util';

suite('router-util tests', () => {
  suite('generateUrl', () => {
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
  });
});
