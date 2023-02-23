/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {assert} from '@open-wc/testing';
import {RepoName} from '../../api/rest-api';
import '../../test/common-test-setup';
import {createRepoUrl, RepoDetailView} from './repo';

suite('repo view state tests', () => {
  test('createRepoUrl', () => {
    assert.equal(createRepoUrl({}), '/admin/repos/undefined');
    assert.equal(
      createRepoUrl({repo: 'asdf' as RepoName}),
      '/admin/repos/asdf'
    );
    assert.equal(
      createRepoUrl({
        repo: 'asdf' as RepoName,
        detail: RepoDetailView.ACCESS,
      }),
      '/admin/repos/asdf,access'
    );
  });

  test('passes', () => {
    assert.isTrue(true);
  });
});
