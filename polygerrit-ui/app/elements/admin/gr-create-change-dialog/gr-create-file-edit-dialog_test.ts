/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-create-file-edit-dialog';
import {createChange} from '../../../test/test-data-generators';
import {fixture, html, assert} from '@open-wc/testing';
import {GrCreateFileEditDialog} from './gr-create-file-edit-dialog';
import {stubRestApi, waitUntilCalled} from '../../../test/test-utils';
import {BranchName, RepoName} from '../../../api/rest-api';
import {SinonStub} from 'sinon';
import {testResolver} from '../../../test/common-test-setup';
import {navigationToken} from '../../core/gr-navigation/gr-navigation';

suite('gr-create-file-edit-dialog', () => {
  let element: GrCreateFileEditDialog;
  let createChangeStub: SinonStub;
  let setUrlStub: SinonStub;

  setup(async () => {
    createChangeStub = stubRestApi('createChange').returns(
      Promise.resolve(createChange())
    );
    setUrlStub = sinon.stub(testResolver(navigationToken), 'setUrl');

    element = await fixture(
      html`<gr-create-file-edit-dialog></gr-create-file-edit-dialog>`
    );
    await element.updateComplete;
  });

  test('render', () => {
    assert.shadowDom.equal(element, '');
  });

  test('render', async () => {
    element.repo = 'test-repo' as RepoName;
    element.branch = 'test-branch' as BranchName;
    element.path = 'test-path';
    await element.updateComplete;
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <dialog open>
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
                  Will then redirect to editing the file
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
    element.repo = 'test-repo' as RepoName;
    element.branch = 'test-branch' as BranchName;
    element.path = 'test-path';
    element.errorMessage = 'Failed.';
    await element.updateComplete;
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <dialog open>
          <gr-dialog disabled>
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
    element.repo = 'test-repo' as RepoName;
    element.branch = 'test-branch' as BranchName;
    element.path = 'test-path';
    await element.updateComplete;

    assert.isTrue(createChangeStub.calledOnce);
    await waitUntilCalled(setUrlStub, 'setUrl');
    await element.updateComplete;
    assert.shadowDom.equal(element, '');
  });
});
