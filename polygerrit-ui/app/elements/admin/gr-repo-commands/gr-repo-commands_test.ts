/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-repo-commands';
import {GrRepoCommands} from './gr-repo-commands';
import {
  addListenerForTest,
  mockPromise,
  queryAndAssert,
  stubRestApi,
} from '../../../test/test-utils';
import {GrDialog} from '../../shared/gr-dialog/gr-dialog';
import {EventType, PageErrorEvent} from '../../../types/events';
import {RepoName} from '../../../types/common';
import {GrButton} from '../../shared/gr-button/gr-button';
import {fixture, html, assert} from '@open-wc/testing';

suite('gr-repo-commands tests', () => {
  let element: GrRepoCommands;
  let repoStub: sinon.SinonStub;

  setup(async () => {
    element = await fixture(html`<gr-repo-commands></gr-repo-commands>`);
    // Note that this probably does not achieve what it is supposed to, because
    // getProjectConfig() is called as soon as the element is attached, so
    // stubbing it here has not effect anymore.
    repoStub = stubRestApi('getProjectConfig').returns(
      Promise.resolve(undefined)
    );
  });

  test('render', () => {
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <div class="gr-form-styles main read-only">
          <h1 class="heading-1" id="Title">Repository Commands</h1>
          <div class="loading" id="loading">Loading...</div>
          <div class="loading" id="loadedContent">
            <div id="form">
              <h2 class="heading-2">Create change</h2>
              <div>
                <p>
                  Creates an empty work-in-progress change that can be used to
                  edit files online and send the modifications for review.
                </p>
              </div>
              <div>
                <gr-button aria-disabled="false" role="button" tabindex="0">
                  Create change
                </gr-button>
              </div>
              <h2 class="heading-2">Edit repo config</h2>
              <div>
                <p>
                  Creates a work-in-progress change that allows to edit the
                  <code> project.config </code>
                  file in the
                  <code> refs/meta/config </code>
                  branch and send the modifications for review.
                </p>
              </div>
              <div>
                <gr-button
                  aria-disabled="false"
                  id="editRepoConfig"
                  role="button"
                  tabindex="0"
                >
                  Edit repo config
                </gr-button>
              </div>
              <gr-endpoint-decorator name="repo-command">
                <gr-endpoint-param name="config"> </gr-endpoint-param>
                <gr-endpoint-param name="repoName"> </gr-endpoint-param>
              </gr-endpoint-decorator>
            </div>
          </div>
        </div>
        <dialog id="createChangeModal" tabindex="-1">
          <gr-dialog
            confirm-label="Create"
            disabled=""
            id="createChangeDialog"
            role="dialog"
          >
            <div class="header" slot="header">Create Change</div>
            <div class="main" slot="main">
              <gr-create-change-dialog id="createNewChangeModal">
              </gr-create-change-dialog>
            </div>
          </gr-dialog>
        </dialog>
      `,
      {ignoreTags: ['p']}
    );
  });

  suite('create new change dialog', () => {
    test('createNewChange opens modal', () => {
      const openStub = sinon.stub(
        queryAndAssert<HTMLDialogElement>(element, '#createChangeModal'),
        'showModal'
      );
      element.createNewChange();
      assert.isTrue(openStub.called);
    });

    test('handleCreateChange called when confirm fired', () => {
      const handleCreateChangeStub = sinon.stub(element, 'handleCreateChange');
      queryAndAssert<GrDialog>(element, '#createChangeDialog').dispatchEvent(
        new CustomEvent('confirm', {
          composed: true,
          bubbles: true,
        })
      );
      assert.isTrue(handleCreateChangeStub.called);
    });

    test('handleCloseCreateChange called when cancel fired', () => {
      const handleCloseCreateChangeStub = sinon.stub(
        element,
        'handleCloseCreateChange'
      );
      queryAndAssert<GrDialog>(element, '#createChangeDialog').dispatchEvent(
        new CustomEvent('cancel', {
          composed: true,
          bubbles: true,
        })
      );
      assert.isTrue(handleCloseCreateChangeStub.called);
    });
  });

  suite('edit repo config', () => {
    let createChangeStub: sinon.SinonStub;
    let handleSpy: sinon.SinonSpy;
    let alertStub: sinon.SinonStub;

    setup(() => {
      createChangeStub = stubRestApi('createChange');
      handleSpy = sinon.spy(element, 'handleEditRepoConfig');
      alertStub = sinon.stub();
      element.repo = 'test' as RepoName;
      element.addEventListener(EventType.SHOW_ALERT, alertStub);
    });

    test('successful creation of change', async () => {
      const change = {_number: '1'};
      createChangeStub.returns(Promise.resolve(change));
      queryAndAssert<GrButton>(element, '#editRepoConfig').click();
      await element.updateComplete;
      assert.isTrue(
        queryAndAssert<GrButton>(element, '#editRepoConfig').loading
      );

      await handleSpy.lastCall.returnValue;
      await element.updateComplete;

      assert.isTrue(alertStub.called);
      assert.equal(
        alertStub.lastCall.args[0].detail.message,
        'Navigating to change'
      );
      assert.isFalse(
        queryAndAssert<GrButton>(element, '#editRepoConfig').loading
      );
    });

    test('unsuccessful creation of change', async () => {
      createChangeStub.returns(Promise.resolve(null));
      queryAndAssert<GrButton>(element, '#editRepoConfig').click();
      await element.updateComplete;
      assert.isTrue(
        queryAndAssert<GrButton>(element, '#editRepoConfig').loading
      );

      await handleSpy.lastCall.returnValue;
      await element.updateComplete;

      assert.isTrue(alertStub.called);
      assert.equal(
        alertStub.lastCall.args[0].detail.message,
        'Failed to create change.'
      );
      assert.isFalse(
        queryAndAssert<GrButton>(element, '#editRepoConfig').loading
      );
    });
  });

  suite('404', () => {
    test('fires page-error', async () => {
      repoStub.restore();

      element.repo = 'test' as RepoName;

      const response = {status: 404} as Response;
      stubRestApi('getProjectConfig').callsFake((_repo, errFn) => {
        if (errFn !== undefined) {
          errFn(response);
        }
        return Promise.resolve(undefined);
      });

      await element.updateComplete;
      const promise = mockPromise();
      addListenerForTest(document, 'page-error', e => {
        assert.deepEqual((e as PageErrorEvent).detail.response, response);
        promise.resolve();
      });

      element.loadRepo();
      await promise;
    });
  });
});
