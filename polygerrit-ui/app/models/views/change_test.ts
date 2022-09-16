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
import {createChangeUrl, ChangeViewState} from './change';

suite('change view state tests', () => {
  test('createChangeUrl()', () => {
    const state: ChangeViewState = {
      view: GerritView.CHANGE,
      changeNum: 1234 as NumericChangeId,
      project: 'test' as RepoName,
    };

    assert.equal(createChangeUrl(state), '/c/test/+/1234');

    state.patchNum = 10 as RevisionPatchSetNum;
    assert.equal(createChangeUrl(state), '/c/test/+/1234/10');

    state.basePatchNum = 5 as BasePatchSetNum;
    assert.equal(createChangeUrl(state), '/c/test/+/1234/5..10');

    state.messageHash = '#123';
    assert.equal(createChangeUrl(state), '/c/test/+/1234/5..10#123');
  });

  test('createChangeUrl() with repo name encoding', () => {
    const state: ChangeViewState = {
      view: GerritView.CHANGE,
      changeNum: 1234 as NumericChangeId,
      project: 'x+/y+/z+/w' as RepoName,
    };
    assert.equal(createChangeUrl(state), '/c/x%252B/y%252B/z%252B/w/+/1234');
  });
});
