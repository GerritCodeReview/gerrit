/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import * as sinon from 'sinon';
import '../../../test/common-test-setup';
import './gr-create-file-edit-dialog';
import {createChange} from '../../../test/test-data-generators';
import {assert, fixture, html} from '@open-wc/testing';
import {GrCreateFileEditDialog} from './gr-create-file-edit-dialog';
import {stubRestApi, waitUntilCalled} from '../../../test/test-utils';
import {BranchName, RepoName} from '../../../api/rest-api';
import {SinonStubbedMember} from 'sinon';
import {testResolver} from '../../../test/common-test-setup';
import {
  NavigationService,
  navigationToken,
} from '../../core/gr-navigation/gr-navigation';
import {RestApiService} from '../../../services/gr-rest-api/gr-rest-api';

suite('gr-create-file-edit-dialog', () => {
  let element: GrCreateFileEditDialog;
  let createChangeStub: SinonStubbedMember<RestApiService['createChange']>;
  let setUrlStub: SinonStubbedMember<NavigationService['setUrl']>;

  setup(async () => {
    createChangeStub = stubRestApi('createChange').resolves(createChange());
    setUrlStub = sinon.stub(testResolver(navigationToken), 'setUrl');

    element = await fixture(
      html`<gr-create-file-edit-dialog></gr-create-file-edit-dialog>`
    );
    element.repo = 'test-repo' as RepoName;
    element.branch = 'test-branch' as BranchName;
    element.path = 'test-path';
    await element.updateComplete;
  });

  test('render', async () => {
    element.activate();
    await element.updateComplete;
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <dialog tabindex="-1">
          <gr-dialog disabled loading>
            <div slot="header">
              <span class="main-heading"> Create Change from URL </span>
            </div>
            <div slot="main">
              <div>
                <span>
                  Creating a change in repository
                  <b> test-repo </b>
                  on branch
                  <b> test-branch </b>
                  .
                </span>
              </div>
              <div>
                <span>
                  The page will then redirect to the file editor for
                  <b> test-path </b> in the newly created change.
                </span>
              </div>
            </div>
          </gr-dialog>
        </dialog>
      `
    );
  });

  test('render error', async () => {
    element.activate();
    element.errorMessage = 'Failed.';
    await element.updateComplete;
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <dialog tabindex="-1">
          <gr-dialog disabled loading>
            <div slot="header">
              <span class="main-heading"> Create Change from URL </span>
            </div>
            <div slot="main">
              <div>Error: Failed.</div>
            </div>
          </gr-dialog>
        </dialog>
      `
    );
  });

  test('creates change', async () => {
    element.activate();
    await element.updateComplete;

    assert.isTrue(createChangeStub.calledOnce);
    await waitUntilCalled(setUrlStub, 'setUrl');
    await element.updateComplete;
    assert.shadowDom.equal(element, '');
  });
});
