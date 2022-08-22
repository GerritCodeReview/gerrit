/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup-karma';
import './gr-repo-commands.js';
import {GrRepoCommands} from './gr-repo-commands';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {
  addListenerForTest,
  mockPromise,
  queryAndAssert,
  stubRestApi,
} from '../../../test/test-utils';
import * as MockInteractions from '@polymer/iron-test-helpers/mock-interactions';
import {GrOverlay} from '../../shared/gr-overlay/gr-overlay';
import {GrDialog} from '../../shared/gr-dialog/gr-dialog';
import {PageErrorEvent} from '../../../types/events';
import {RepoName} from '../../../types/common';
import {GrButton} from '../../shared/gr-button/gr-button';
import {fixture, html} from '@open-wc/testing';

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
            <h2 class="heading-2" id="options">Command</h2>
            <div id="form">
              <h3 class="heading-3">Create change</h3>
              <gr-button aria-disabled="false" role="button" tabindex="0">
                Create change
              </gr-button>
              <h3 class="heading-3">Edit repo config</h3>
              <gr-button
                aria-disabled="false"
                id="editRepoConfig"
                role="button"
                tabindex="0"
              >
                Edit repo config
              </gr-button>
              <gr-endpoint-decorator name="repo-command">
                <gr-endpoint-param name="config"> </gr-endpoint-param>
                <gr-endpoint-param name="repoName"> </gr-endpoint-param>
              </gr-endpoint-decorator>
            </div>
          </div>
        </div>
        <gr-overlay
          aria-hidden="true"
          id="createChangeOverlay"
          style="outline: none; display: none;"
          tabindex="-1"
          with-backdrop=""
        >
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
        </gr-overlay>
      `
    );
  });

  suite('create new change dialog', () => {
    test('createNewChange opens modal', () => {
      const openStub = sinon.stub(
        queryAndAssert<GrOverlay>(element, '#createChangeOverlay'),
        'open'
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
    let urlStub: sinon.SinonStub;
    let handleSpy: sinon.SinonSpy;
    let alertStub: sinon.SinonStub;

    setup(() => {
      createChangeStub = stubRestApi('createChange');
      urlStub = sinon.stub(GerritNav, 'getEditUrlForDiff');
      sinon.stub(GerritNav, 'navigateToRelativeUrl');
      handleSpy = sinon.spy(element, 'handleEditRepoConfig');
      alertStub = sinon.stub();
      element.repo = 'test' as RepoName;
      element.addEventListener('show-alert', alertStub);
    });

    test('successful creation of change', async () => {
      const change = {_number: '1'};
      createChangeStub.returns(Promise.resolve(change));
      MockInteractions.tap(
        queryAndAssert<GrButton>(element, '#editRepoConfig')
      );
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
      assert.isTrue(urlStub.called);
      assert.deepEqual(urlStub.lastCall.args, [change, 'project.config', 1]);
      assert.isFalse(
        queryAndAssert<GrButton>(element, '#editRepoConfig').loading
      );
    });

    test('unsuccessful creation of change', async () => {
      createChangeStub.returns(Promise.resolve(null));
      MockInteractions.tap(
        queryAndAssert<GrButton>(element, '#editRepoConfig')
      );
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
      assert.isFalse(urlStub.called);
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
