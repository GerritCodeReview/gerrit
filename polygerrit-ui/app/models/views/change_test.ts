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
import {createChangeUrl, ChangeViewState, ChangeChildView} from './change';

const STATE: ChangeViewState = {
  view: GerritView.CHANGE,
  childView: ChangeChildView.OVERVIEW,
  changeNum: 1234 as NumericChangeId,
  repo: 'test' as RepoName,
};

suite('change view state tests', () => {
  test('createChangeUrl()', () => {
    const state: ChangeViewState = {...STATE};

    assert.equal(createChangeUrl(state), '/c/test/+/1234');

    state.patchNum = 10 as RevisionPatchSetNum;
    assert.equal(createChangeUrl(state), '/c/test/+/1234/10');

    state.basePatchNum = 5 as BasePatchSetNum;
    assert.equal(createChangeUrl(state), '/c/test/+/1234/5..10');

    state.messageHash = '#123';
    assert.equal(createChangeUrl(state), '/c/test/+/1234/5..10#123');
  });

  test('createChangeUrl() baseUrl', () => {
    window.CANONICAL_PATH = '/base';
    const state: ChangeViewState = {...STATE};
    assert.equal(createChangeUrl(state).substring(0, 5), '/base');
    window.CANONICAL_PATH = undefined;
  });

  test('createChangeUrl() checksRunsSelected', () => {
    const state: ChangeViewState = {
      ...STATE,
      checksRunsSelected: new Set(['asdf']),
    };

    assert.equal(
      createChangeUrl(state),
      '/c/test/+/1234?checksRunsSelected=asdf'
    );
  });

  test('createChangeUrl() checksResultsFilter', () => {
    const state: ChangeViewState = {
      ...STATE,
      checksResultsFilter: 'asdf.*qwer',
    };

    assert.equal(
      createChangeUrl(state),
      '/c/test/+/1234?checksResultsFilter=asdf.*qwer'
    );
  });

  test('createChangeUrl() with repo name encoding', () => {
    const state: ChangeViewState = {
      view: GerritView.CHANGE,
      childView: ChangeChildView.OVERVIEW,
      changeNum: 1234 as NumericChangeId,
      repo: 'x+/y+/z+/w' as RepoName,
    };
    assert.equal(createChangeUrl(state), '/c/x%252B/y%252B/z%252B/w/+/1234');
  });
});
