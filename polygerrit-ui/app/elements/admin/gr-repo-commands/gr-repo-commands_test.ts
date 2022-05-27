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

const basicFixture = fixtureFromElement('gr-repo-commands');

suite('gr-repo-commands tests', () => {
  let element: GrRepoCommands;
  let repoStub: sinon.SinonStub;

  setup(async () => {
    element = basicFixture.instantiate();
    await element.updateComplete;
    // Note that this probably does not achieve what it is supposed to, because
    // getProjectConfig() is called as soon as the element is attached, so
    // stubbing it here has not effect anymore.
    repoStub = stubRestApi('getProjectConfig').returns(
      Promise.resolve(undefined)
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
