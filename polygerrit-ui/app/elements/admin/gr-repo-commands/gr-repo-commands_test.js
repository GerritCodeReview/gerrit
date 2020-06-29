/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import '../../../test/common-test-setup-karma.js';
import './gr-repo-commands.js';
import {GerritNav} from '../../core/gr-navigation/gr-navigation.js';

const basicFixture = fixtureFromElement('gr-repo-commands');

suite('gr-repo-commands tests', () => {
  let element;
  let sandbox;
  let repoStub;

  setup(() => {
    sandbox = sinon.sandbox.create();
    element = basicFixture.instantiate();
    repoStub = sandbox.stub(
        element.$.restAPI,
        'getProjectConfig',
        () => Promise.resolve({}));
  });

  teardown(() => {
    sandbox.restore();
  });

  suite('create new change dialog', () => {
    test('_createNewChange opens modal', () => {
      const openStub = sandbox.stub(element.$.createChangeOverlay, 'open');
      element._createNewChange();
      assert.isTrue(openStub.called);
    });

    test('_handleCreateChange called when confirm fired', () => {
      sandbox.stub(element, '_handleCreateChange');
      element.$.createChangeDialog.dispatchEvent(
          new CustomEvent('confirm', {
            composed: true, bubbles: true,
          }));
      assert.isTrue(element._handleCreateChange.called);
    });

    test('_handleCloseCreateChange called when cancel fired', () => {
      sandbox.stub(element, '_handleCloseCreateChange');
      element.$.createChangeDialog.dispatchEvent(
          new CustomEvent('cancel', {
            composed: true, bubbles: true,
          }));
      assert.isTrue(element._handleCloseCreateChange.called);
    });
  });

  suite('edit repo config', () => {
    let createChangeStub;
    let urlStub;
    let handleSpy;
    let alertStub;

    setup(() => {
      createChangeStub = sandbox.stub(element.$.restAPI, 'createChange');
      urlStub = sandbox.stub(GerritNav, 'getEditUrlForDiff');
      sandbox.stub(GerritNav, 'navigateToRelativeUrl');
      handleSpy = sandbox.spy(element, '_handleEditRepoConfig');
      alertStub = sandbox.stub();
      element.addEventListener('show-alert', alertStub);
    });

    test('successful creation of change', () => {
      const change = {_number: '1'};
      createChangeStub.returns(Promise.resolve(change));
      MockInteractions.tap(element.$.editRepoConfig);
      assert.isTrue(element.$.editRepoConfig.loading);
      return handleSpy.lastCall.returnValue.then(() => {
        flushAsynchronousOperations();

        assert.isTrue(alertStub.called);
        assert.equal(alertStub.lastCall.args[0].detail.message,
            'Navigating to change');
        assert.isTrue(urlStub.called);
        assert.deepEqual(urlStub.lastCall.args,
            [change, 'project.config', 1]);
        assert.isFalse(element.$.editRepoConfig.loading);
      });
    });

    test('unsuccessful creation of change', () => {
      createChangeStub.returns(Promise.resolve(null));
      MockInteractions.tap(element.$.editRepoConfig);
      assert.isTrue(element.$.editRepoConfig.loading);
      return handleSpy.lastCall.returnValue.then(() => {
        flushAsynchronousOperations();

        assert.isTrue(alertStub.called);
        assert.equal(alertStub.lastCall.args[0].detail.message,
            'Failed to create change.');
        assert.isFalse(urlStub.called);
        assert.isFalse(element.$.editRepoConfig.loading);
      });
    });
  });

  suite('404', () => {
    test('fires page-error', done => {
      repoStub.restore();

      element.repo = 'test';

      const response = {status: 404};
      sandbox.stub(
          element.$.restAPI, 'getProjectConfig', (repo, errFn) => {
            errFn(response);
          });
      element.addEventListener('page-error', e => {
        assert.deepEqual(e.detail.response, response);
        done();
      });

      element._loadRepo();
    });
  });
});

