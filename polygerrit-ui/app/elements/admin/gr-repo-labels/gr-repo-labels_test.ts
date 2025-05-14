/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-repo-submit-requirements';
import {GrRepoSubmitRequirements} from './gr-repo-submit-requirements';
import {
  queryAndAssert,
  stubRestApi,
  waitEventLoop,
} from '../../../test/test-utils';
import {RepoName} from '../../../types/common';
import {fixture, html, assert} from '@open-wc/testing';

suite('gr-repo-submit-requirements tests', () => {
  let element: GrRepoSubmitRequirements;

  setup(async () => {
    element = await fixture(
      html`<gr-repo-submit-requirements></gr-repo-submit-requirements>`
    );
  });

  suite('gr repo labels', () => {
    setup(() => {
      
    });

    test('render', () => {
      element.repo = 'test2' as RepoName;
      assert.shadowDom.equal(
        element,
        /* HTML */ ``;
      );
    });

});
