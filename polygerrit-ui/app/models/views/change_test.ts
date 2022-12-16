/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {assert} from '@open-wc/testing';
import {
  BasePatchSetNum,
  RepoName,
  RevisionPatchSetNum,
} from '../../api/rest-api';
import '../../test/common-test-setup';
import {createChangeViewState} from '../../test/test-data-generators';
import {createChangeUrl, ChangeViewState} from './change';

suite('change view state tests', () => {
  test('createChangeUrl()', () => {
    const state: ChangeViewState = createChangeViewState();

    assert.equal(createChangeUrl(state), '/c/test-project/+/42');

    state.patchNum = 10 as RevisionPatchSetNum;
    assert.equal(createChangeUrl(state), '/c/test-project/+/42/10');

    state.basePatchNum = 5 as BasePatchSetNum;
    assert.equal(createChangeUrl(state), '/c/test-project/+/42/5..10');

    state.messageHash = '#123';
    assert.equal(createChangeUrl(state), '/c/test-project/+/42/5..10#123');
  });

  test('createChangeUrl() baseUrl', () => {
    window.CANONICAL_PATH = '/base';
    const state: ChangeViewState = createChangeViewState();
    assert.equal(createChangeUrl(state).substring(0, 5), '/base');
    window.CANONICAL_PATH = undefined;
  });

  test('createChangeUrl() checksRunsSelected', () => {
    const state: ChangeViewState = {
      ...createChangeViewState(),
      checksRunsSelected: new Set(['asdf']),
    };

    assert.equal(
      createChangeUrl(state),
      '/c/test-project/+/42?checksRunsSelected=asdf'
    );
  });

  test('createChangeUrl() checksResultsFilter', () => {
    const state: ChangeViewState = {
      ...createChangeViewState(),
      checksResultsFilter: 'asdf.*qwer',
    };

    assert.equal(
      createChangeUrl(state),
      '/c/test-project/+/42?checksResultsFilter=asdf.*qwer'
    );
  });

  test('createChangeUrl() with repo name encoding', () => {
    const state: ChangeViewState = {
      ...createChangeViewState(),
      repo: 'x+/y+/z+/w' as RepoName,
    };
    assert.equal(createChangeUrl(state), '/c/x%252B/y%252B/z%252B/w/+/42');
  });
});
