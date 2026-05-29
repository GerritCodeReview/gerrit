/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {assert} from '@open-wc/testing';
import {
  BasePatchSetNum,
  PatchSetNumber,
  RepoName,
  RevisionPatchSetNum,
} from '../../api/rest-api';
import '../../test/common-test-setup';
import {
  createChangeViewState,
  createDiffViewState,
  createEditViewState,
} from '../../test/test-data-generators';
import {
  ChangeViewState,
  createChangeUrl,
  createDiffUrl,
  createEditUrl,
} from './change';

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

  suite('createDiffUrl', () => {
    let params: ChangeViewState;
    setup(() => {
      params = {
        ...createDiffViewState(),
        patchNum: 12 as RevisionPatchSetNum,
        diffView: {path: 'x+y/path.cpp'},
      };
    });

    test('CANONICAL_PATH', () => {
      window.CANONICAL_PATH = '/base';
      assert.equal(createDiffUrl(params).substring(0, 5), '/base');
      window.CANONICAL_PATH = undefined;
    });

    test('basic', () => {
      assert.equal(
        createDiffUrl(params),
        '/c/test-project/+/42/12/x%252By/path.cpp'
      );
    });

    test('repo', () => {
      params.repo = 'test' as RepoName;
      assert.equal(createDiffUrl(params), '/c/test/+/42/12/x%252By/path.cpp');
    });

    test('checks patchset', () => {
      params.checksPatchset = 4 as PatchSetNumber;
      assert.equal(
        createDiffUrl(params),
        '/c/test-project/+/42/12/x%252By/path.cpp?checksPatchset=4'
      );
    });

    test('base patchset', () => {
      params.basePatchNum = 6 as BasePatchSetNum;
      assert.equal(
        createDiffUrl(params),
        '/c/test-project/+/42/6..12/x%252By/path.cpp'
      );
    });

    test('percent', () => {
      params.diffView = {
        path: 'foo bar/my+file.txt%',
      };
      params.patchNum = 2 as RevisionPatchSetNum;
      delete params.basePatchNum;
      assert.equal(
        createDiffUrl(params),
        '/c/test-project/+/42/2/foo+bar/my%252Bfile.txt%2525'
      );
    });

    test('line right', () => {
      params.diffView = {
        path: 'file.cpp',
        lineNum: 123,
      };
      assert.equal(
        createDiffUrl(params),
        '/c/test-project/+/42/12/file.cpp#123'
      );
    });

    test('line left', () => {
      params.diffView = {
        path: 'file.cpp',
        lineNum: 123,
        leftSide: true,
      };
      assert.equal(
        createDiffUrl(params),
        '/c/test-project/+/42/12/file.cpp#b123'
      );
    });

    test('diff with repo name encoding', () => {
      const params: ChangeViewState = {
        ...createDiffViewState(),
        patchNum: 12 as RevisionPatchSetNum,
        repo: 'x+/y' as RepoName,
        diffView: {path: 'x+y/path.cpp'},
      };
      assert.equal(
        createDiffUrl(params),
        '/c/x%252B/y/+/42/12/x%252By/path.cpp'
      );
    });
  });

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
