/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
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
import './gr-change-actions.js';
import {dom} from '@polymer/polymer/lib/legacy/polymer.dom.js';
import {GerritNav} from '../../core/gr-navigation/gr-navigation.js';
import {pluginLoader} from '../../shared/gr-js-api-interface/gr-plugin-loader.js';
import {generateChange} from '../../../test/test-utils.js';

const basicFixture = fixtureFromElement('gr-change-actions');

const CHERRY_PICK_TYPES = {
  SINGLE_CHANGE: 1,
  TOPIC: 2,
};
// TODO(dhruvsri): remove use of _populateRevertMessage as it's private
suite('gr-change-actions tests', () => {
  let element;

  suite('basic tests', () => {
    setup(() => {
      stub('gr-rest-api-interface', {
        getChangeRevisionActions() {
          return Promise.resolve({
            cherrypick: {
              method: 'POST',
              label: 'Cherry Pick',
              title: 'Cherry pick change to a different branch',
              enabled: true,
            },
            rebase: {
              method: 'POST',
              label: 'Rebase',
              title: 'Rebase onto tip of branch or parent change',
              enabled: true,
            },
            submit: {
              method: 'POST',
              label: 'Submit',
              title: 'Submit patch set 2 into master',
              enabled: true,
            },
            revert_submission: {
              method: 'POST',
              label: 'Revert submission',
              title: 'Revert this submission',
              enabled: true,
            },
          });
        },
        send(method, url, payload) {
          if (method !== 'POST') {
            return Promise.reject(new Error('bad method'));
          }

          if (url === '/changes/test~42/revisions/2/submit') {
            return Promise.resolve({
              ok: true,
              text() { return Promise.resolve(')]}\'\n{}'); },
            });
          } else if (url === '/changes/test~42/revisions/2/rebase') {
            return Promise.resolve({
              ok: true,
              text() { return Promise.resolve(')]}\'\n{}'); },
            });
          }

          return Promise.reject(new Error('bad url'));
        },
        getProjectConfig() { return Promise.resolve({}); },
      });

      sinon.stub(pluginLoader, 'awaitPluginsLoaded')
          .returns(Promise.resolve());

      element = basicFixture.instantiate();
      element.change = {};
      element.changeNum = '42';
      element.latestPatchNum = '2';
      element.actions = {
        '/': {
          method: 'DELETE',
          label: 'Delete Change',
          title: 'Delete change X_X',
          enabled: true,
        },
      };
      sinon.stub(element.$.confirmCherrypick.$.restAPI,
          'getRepoBranches').returns(Promise.resolve([]));
      sinon.stub(element.$.confirmMove.$.restAPI,
          'getRepoBranches').returns(Promise.resolve([]));

      return element.reload();
    });

    test('show-revision-actions event should fire', done => {
      const spy = sinon.spy(element, '_sendShowRevisionActions');
      element.reload();
      flush(() => {
        assert.isTrue(spy.called);
        done();
      });
    });

    test('primary and secondary actions split properly', () => {
      // Submit should be the only primary action.
      assert.equal(element._topLevelPrimaryActions.length, 1);
      assert.equal(element._topLevelPrimaryActions[0].label, 'Submit');
      assert.equal(element._topLevelSecondaryActions.length,
          element._topLevelActions.length - 1);
    });

    test('revert submission action is skipped', () => {
      assert.equal(element._allActionValues.filter(action =>
        action.__key === 'submit').length, 1);
      assert.equal(element._allActionValues.filter(action =>
        action.__key === 'revert_submission').length, 0);
    });

    test('_shouldHideActions', () => {
      assert.isTrue(element._shouldHideActions(undefined, true));
      assert.isTrue(element._shouldHideActions({base: {}}, false));
      assert.isFalse(element._shouldHideActions({base: ['test']}, false));
    });

    test('plugin revision actions', done => {
      sinon.stub(element.$.restAPI, 'getChangeActionURL').returns(
          Promise.resolve('the-url'));
      element.revisionActions = {
        'plugin~action': {},
      };
      assert.isOk(element.revisionActions['plugin~action']);
      flush(() => {
        assert.isTrue(element.$.restAPI.getChangeActionURL.calledWith(
            element.changeNum, element.latestPatchNum, '/plugin~action'));
        assert.equal(element.revisionActions['plugin~action'].__url, 'the-url');
        done();
      });
    });

    test('plugin change actions', done => {
      sinon.stub(element.$.restAPI, 'getChangeActionURL').returns(
          Promise.resolve('the-url'));
      element.actions = {
        'plugin~action': {},
      };
      assert.isOk(element.actions['plugin~action']);
      flush(() => {
        assert.isTrue(element.$.restAPI.getChangeActionURL.calledWith(
            element.changeNum, null, '/plugin~action'));
        assert.equal(element.actions['plugin~action'].__url, 'the-url');
        done();
      });
    });

    test('not supported actions are filtered out', () => {
      element.revisionActions = {followup: {}};
      assert.equal(element.querySelectorAll(
          'section gr-button[data-action-type="revision"]').length, 0);
    });

    test('getActionDetails', () => {
      element.revisionActions = {
        'plugin~action': {},
        ...element.revisionActions,
      };
      assert.isUndefined(element.getActionDetails('rubbish'));
      assert.strictEqual(element.revisionActions['plugin~action'],
          element.getActionDetails('plugin~action'));
      assert.strictEqual(element.revisionActions['rebase'],
          element.getActionDetails('rebase'));
    });

    test('hide revision action', done => {
      flush(() => {
        const buttonEl = element.shadowRoot
            .querySelector('[data-action-key="submit"]');
        assert.isOk(buttonEl);
        assert.throws(element.setActionHidden.bind(element, 'invalid type'));
        element.setActionHidden(element.ActionType.REVISION,
            element.RevisionActions.SUBMIT, true);
        assert.lengthOf(element._hiddenActions, 1);
        element.setActionHidden(element.ActionType.REVISION,
            element.RevisionActions.SUBMIT, true);
        assert.lengthOf(element._hiddenActions, 1);
        flush(() => {
          const buttonEl = element.shadowRoot
              .querySelector('[data-action-key="submit"]');
          assert.isNotOk(buttonEl);

          element.setActionHidden(element.ActionType.REVISION,
              element.RevisionActions.SUBMIT, false);
          flush(() => {
            const buttonEl = element.shadowRoot
                .querySelector('[data-action-key="submit"]');
            assert.isOk(buttonEl);
            assert.isFalse(buttonEl.hasAttribute('hidden'));
            done();
          });
        });
      });
    });

    test('buttons exist', done => {
      element._loading = false;
      flush(() => {
        const buttonEls = dom(element.root)
            .querySelectorAll('gr-button');
        const menuItems = element.$.moreActions.items;

        // Total button number is one greater than the number of total actions
        // due to the existence of the overflow menu trigger.
        assert.equal(buttonEls.length + menuItems.length,
            element._allActionValues.length + 1);
        assert.isFalse(element.hidden);
        done();
      });
    });

    test('delete buttons have explicit labels', done => {
      flush(() => {
        const deleteItems = element.$.moreActions.items
            .filter(item => item.id.startsWith('delete'));
        assert.equal(deleteItems.length, 1);
        assert.notEqual(deleteItems[0].name);
        assert.equal(deleteItems[0].name, 'Delete change');
        done();
      });
    });

    test('get revision object from change', () => {
      const revObj = {_number: 2, foo: 'bar'};
      const change = {
        revisions: {
          rev1: {_number: 1},
          rev2: revObj,
        },
      };
      assert.deepEqual(element._getRevision(change, '2'), revObj);
    });

    test('_actionComparator sort order', () => {
      const actions = [
        {label: '123', __type: 'change', __key: 'review'},
        {label: 'abc-ro', __type: 'revision'},
        {label: 'abc', __type: 'change'},
        {label: 'def', __type: 'change'},
        {label: 'def-p', __type: 'change', __primary: true},
      ];

      const result = actions.slice();
      result.reverse();
      result.sort(element._actionComparator.bind(element));
      assert.deepEqual(result, actions);
    });

    test('submit change', () => {
      const showSpy = sinon.spy(element, '_showActionDialog');
      sinon.stub(element.$.restAPI, 'getFromProjectLookup')
          .returns(Promise.resolve('test'));
      sinon.stub(element.$.overlay, 'open').returns(Promise.resolve());
      element.change = {
        revisions: {
          rev1: {_number: 1},
          rev2: {_number: 2},
        },
      };
      element.latestPatchNum = '2';

      const submitButton = element.shadowRoot
          .querySelector('gr-button[data-action-key="submit"]');
      assert.ok(submitButton);
      MockInteractions.tap(submitButton);

      flushAsynchronousOperations();
      assert.isTrue(showSpy.calledWith(element.$.confirmSubmitDialog));
    });

    test('submit change, tap on icon', done => {
      sinon.stub(element.$.confirmSubmitDialog, 'resetFocus').callsFake( done);
      sinon.stub(element.$.restAPI, 'getFromProjectLookup')
          .returns(Promise.resolve('test'));
      sinon.stub(element.$.overlay, 'open').returns(Promise.resolve());
      element.change = {
        revisions: {
          rev1: {_number: 1},
          rev2: {_number: 2},
        },
      };
      element.latestPatchNum = '2';

      const submitIcon =
          element.shadowRoot
              .querySelector('gr-button[data-action-key="submit"] iron-icon');
      assert.ok(submitIcon);
      MockInteractions.tap(submitIcon);
    });

    test('_handleSubmitConfirm', () => {
      const fireStub = sinon.stub(element, '_fireAction');
      sinon.stub(element, '_canSubmitChange').returns(true);
      element._handleSubmitConfirm();
      assert.isTrue(fireStub.calledOnce);
      assert.deepEqual(fireStub.lastCall.args,
          ['/submit', element.revisionActions.submit, true]);
    });

    test('_handleSubmitConfirm when not able to submit', () => {
      const fireStub = sinon.stub(element, '_fireAction');
      sinon.stub(element, '_canSubmitChange').returns(false);
      element._handleSubmitConfirm();
      assert.isFalse(fireStub.called);
    });

    test('submit change with plugin hook', done => {
      sinon.stub(element, '_canSubmitChange').callsFake(
          () => false);
      const fireActionStub = sinon.stub(element, '_fireAction');
      flush(() => {
        const submitButton = element.shadowRoot
            .querySelector('gr-button[data-action-key="submit"]');
        assert.ok(submitButton);
        MockInteractions.tap(submitButton);
        assert.equal(fireActionStub.callCount, 0);

        done();
      });
    });

    test('chain state', () => {
      assert.equal(element._hasKnownChainState, false);
      element.hasParent = true;
      assert.equal(element._hasKnownChainState, true);
      element.hasParent = false;
    });

    test('_calculateDisabled', () => {
      let hasKnownChainState = false;
      const action = {__key: 'rebase', enabled: true};
      assert.equal(
          element._calculateDisabled(action, hasKnownChainState), true);

      action.__key = 'delete';
      assert.equal(
          element._calculateDisabled(action, hasKnownChainState), false);

      action.__key = 'rebase';
      hasKnownChainState = true;
      assert.equal(
          element._calculateDisabled(action, hasKnownChainState), false);

      action.enabled = false;
      assert.equal(
          element._calculateDisabled(action, hasKnownChainState), false);
    });

    test('rebase change', done => {
      const fireActionStub = sinon.stub(element, '_fireAction');
      const fetchChangesStub = sinon.stub(element.$.confirmRebase,
          'fetchRecentChanges').returns(Promise.resolve([]));
      element._hasKnownChainState = true;
      flush(() => {
        const rebaseButton = element.shadowRoot
            .querySelector('gr-button[data-action-key="rebase"]');
        MockInteractions.tap(rebaseButton);
        const rebaseAction = {
          __key: 'rebase',
          __type: 'revision',
          __primary: false,
          enabled: true,
          label: 'Rebase',
          method: 'POST',
          title: 'Rebase onto tip of branch or parent change',
        };
        assert.isTrue(fetchChangesStub.called);
        element._handleRebaseConfirm({detail: {base: '1234'}});
        assert.deepEqual(fireActionStub.lastCall.args,
            ['/rebase', rebaseAction, true, {base: '1234'}]);
        done();
      });
    });

    test('rebase change fires reload event', done => {
      const eventStub = sinon.stub(element, 'dispatchEvent');
      sinon.stub(element.$.restAPI, 'getResponseObject').returns(
          Promise.resolve({}));
      element._handleResponse({__key: 'rebase'}, {});
      flush(() => {
        assert.isTrue(eventStub.called);
        assert.equal(eventStub.lastCall.args[0].type, 'reload-change');
        done();
      });
    });

    test(`rebase dialog gets recent changes each time it's opened`, done => {
      const fetchChangesStub = sinon.stub(element.$.confirmRebase,
          'fetchRecentChanges').returns(Promise.resolve([]));
      element._hasKnownChainState = true;
      const rebaseButton = element.shadowRoot
          .querySelector('gr-button[data-action-key="rebase"]');
      MockInteractions.tap(rebaseButton);
      assert.isTrue(fetchChangesStub.calledOnce);

      flush(() => {
        element.$.confirmRebase.dispatchEvent(
            new CustomEvent('cancel', {
              composed: true, bubbles: true,
            }));
        MockInteractions.tap(rebaseButton);
        assert.isTrue(fetchChangesStub.calledTwice);
        done();
      });
    });

    test('two dialogs are not shown at the same time', done => {
      element._hasKnownChainState = true;
      flush(() => {
        const rebaseButton = element.shadowRoot
            .querySelector('gr-button[data-action-key="rebase"]');
        assert.ok(rebaseButton);
        MockInteractions.tap(rebaseButton);
        flushAsynchronousOperations();
        assert.isFalse(element.$.confirmRebase.hidden);
        sinon.stub(element.$.restAPI, 'getChanges')
            .returns(Promise.resolve([]));
        element._handleCherrypickTap();
        flush(() => {
          assert.isTrue(element.$.confirmRebase.hidden);
          assert.isFalse(element.$.confirmCherrypick.hidden);
          done();
        });
      });
    });

    test('fullscreen-overlay-opened hides content', () => {
      sinon.spy(element, '_handleHideBackgroundContent');
      element.$.overlay.dispatchEvent(
          new CustomEvent('fullscreen-overlay-opened', {
            composed: true, bubbles: true,
          }));
      assert.isTrue(element._handleHideBackgroundContent.called);
      assert.isTrue(element.$.mainContent.classList.contains('overlayOpen'));
    });

    test('fullscreen-overlay-closed shows content', () => {
      sinon.spy(element, '_handleShowBackgroundContent');
      element.$.overlay.dispatchEvent(
          new CustomEvent('fullscreen-overlay-closed', {
            composed: true, bubbles: true,
          }));
      assert.isTrue(element._handleShowBackgroundContent.called);
      assert.isFalse(element.$.mainContent.classList.contains('overlayOpen'));
    });

    test('_setLabelValuesOnRevert', () => {
      const labels = {'Foo': 1, 'Bar-Baz': -2};
      const changeId = 1234;
      sinon.stub(element.$.jsAPI, 'getLabelValuesPostRevert').returns(labels);
      const saveStub = sinon.stub(element.$.restAPI, 'saveChangeReview')
          .returns(Promise.resolve());
      return element._setLabelValuesOnRevert(changeId).then(() => {
        assert.isTrue(saveStub.calledOnce);
        assert.equal(saveStub.lastCall.args[0], changeId);
        assert.deepEqual(saveStub.lastCall.args[2], {labels});
      });
    });

    suite('change edits', () => {
      test('disableEdit', () => {
        element.set('editMode', false);
        element.set('editPatchsetLoaded', false);
        element.change = {status: 'NEW'};
        element.set('disableEdit', true);
        flushAsynchronousOperations();

        assert.isNotOk(element.shadowRoot
            .querySelector('gr-button[data-action-key="publishEdit"]'));
        assert.isNotOk(element.shadowRoot
            .querySelector('gr-button[data-action-key="rebaseEdit"]'));
        assert.isNotOk(element.shadowRoot
            .querySelector('gr-button[data-action-key="deleteEdit"]'));
        assert.isNotOk(element.shadowRoot
            .querySelector('gr-button[data-action-key="edit"]'));
        assert.isNotOk(element.shadowRoot
            .querySelector('gr-button[data-action-key="stopEdit"]'));
      });

      test('shows confirm dialog for delete edit', () => {
        element.set('editMode', true);
        element.set('editPatchsetLoaded', true);

        const fireActionStub = sinon.stub(element, '_fireAction');
        element._handleDeleteEditTap();
        assert.isFalse(element.$.confirmDeleteEditDialog.hidden);
        MockInteractions.tap(
            element.shadowRoot
                .querySelector('#confirmDeleteEditDialog')
                .shadowRoot
                .querySelector('gr-button[primary]'));
        flushAsynchronousOperations();

        assert.equal(fireActionStub.lastCall.args[0], '/edit');
      });

      test('hide publishEdit and rebaseEdit if change is not open', () => {
        element.set('editMode', true);
        element.set('editPatchsetLoaded', true);
        element.change = {status: 'MERGED'};
        flushAsynchronousOperations();

        assert.isNotOk(element.shadowRoot
            .querySelector('gr-button[data-action-key="publishEdit"]'));
        assert.isNotOk(element.shadowRoot
            .querySelector('gr-button[data-action-key="rebaseEdit"]'));
        assert.isOk(element.shadowRoot
            .querySelector('gr-button[data-action-key="deleteEdit"]'));
        assert.isNotOk(element.shadowRoot
            .querySelector('gr-button[data-action-key="edit"]'));
      });

      test('edit patchset is loaded, needs rebase', () => {
        element.set('editMode', true);
        element.set('editPatchsetLoaded', true);
        element.change = {status: 'NEW'};
        element.editBasedOnCurrentPatchSet = false;
        flushAsynchronousOperations();

        assert.isNotOk(element.shadowRoot
            .querySelector('gr-button[data-action-key="publishEdit"]'));
        assert.isOk(element.shadowRoot
            .querySelector('gr-button[data-action-key="rebaseEdit"]'));
        assert.isOk(element.shadowRoot
            .querySelector('gr-button[data-action-key="deleteEdit"]'));
        assert.isNotOk(element.shadowRoot
            .querySelector('gr-button[data-action-key="edit"]'));
        assert.isNotOk(element.shadowRoot
            .querySelector('gr-button[data-action-key="stopEdit"]'));
      });

      test('edit patchset is loaded, does not need rebase', () => {
        element.set('editMode', true);
        element.set('editPatchsetLoaded', true);
        element.change = {status: 'NEW'};
        element.editBasedOnCurrentPatchSet = true;
        flushAsynchronousOperations();

        assert.isOk(element.shadowRoot
            .querySelector('gr-button[data-action-key="publishEdit"]'));
        assert.isNotOk(element.shadowRoot
            .querySelector('gr-button[data-action-key="rebaseEdit"]'));
        assert.isOk(element.shadowRoot
            .querySelector('gr-button[data-action-key="deleteEdit"]'));
        assert.isNotOk(element.shadowRoot
            .querySelector('gr-button[data-action-key="edit"]'));
        assert.isNotOk(element.shadowRoot
            .querySelector('gr-button[data-action-key="stopEdit"]'));
      });

      test('edit mode is loaded, no edit patchset', () => {
        element.set('editMode', true);
        element.set('editPatchsetLoaded', false);
        element.change = {status: 'NEW'};
        flushAsynchronousOperations();

        assert.isNotOk(element.shadowRoot
            .querySelector('gr-button[data-action-key="publishEdit"]'));
        assert.isNotOk(element.shadowRoot
            .querySelector('gr-button[data-action-key="rebaseEdit"]'));
        assert.isNotOk(element.shadowRoot
            .querySelector('gr-button[data-action-key="deleteEdit"]'));
        assert.isNotOk(element.shadowRoot
            .querySelector('gr-button[data-action-key="edit"]'));
        assert.isOk(element.shadowRoot
            .querySelector('gr-button[data-action-key="stopEdit"]'));
      });

      test('normal patch set', () => {
        element.set('editMode', false);
        element.set('editPatchsetLoaded', false);
        element.change = {status: 'NEW'};
        flushAsynchronousOperations();

        assert.isNotOk(element.shadowRoot
            .querySelector('gr-button[data-action-key="publishEdit"]'));
        assert.isNotOk(element.shadowRoot
            .querySelector('gr-button[data-action-key="rebaseEdit"]'));
        assert.isNotOk(element.shadowRoot
            .querySelector('gr-button[data-action-key="deleteEdit"]'));
        assert.isOk(element.shadowRoot
            .querySelector('gr-button[data-action-key="edit"]'));
        assert.isNotOk(element.shadowRoot
            .querySelector('gr-button[data-action-key="stopEdit"]'));
      });

      test('edit action', done => {
        element.addEventListener('edit-tap', () => { done(); });
        element.set('editMode', true);
        element.change = {status: 'NEW'};
        flushAsynchronousOperations();

        assert.isNotOk(element.shadowRoot
            .querySelector('gr-button[data-action-key="edit"]'));
        assert.isOk(element.shadowRoot
            .querySelector('gr-button[data-action-key="stopEdit"]'));
        element.change = {status: 'MERGED'};
        flushAsynchronousOperations();

        assert.isNotOk(element.shadowRoot
            .querySelector('gr-button[data-action-key="edit"]'));
        element.change = {status: 'NEW'};
        element.set('editMode', false);
        flushAsynchronousOperations();

        const editButton = element.shadowRoot
            .querySelector('gr-button[data-action-key="edit"]');
        assert.isOk(editButton);
        MockInteractions.tap(editButton);
      });
    });

    suite('cherry-pick', () => {
      let fireActionStub;

      setup(() => {
        fireActionStub = sinon.stub(element, '_fireAction');
        sinon.stub(window, 'alert');
      });

      test('works', () => {
        element._handleCherrypickTap();
        const action = {
          __key: 'cherrypick',
          __type: 'revision',
          __primary: false,
          enabled: true,
          label: 'Cherry pick',
          method: 'POST',
          title: 'Cherry pick change to a different branch',
        };

        element._handleCherrypickConfirm({
          detail: {
            branch: '',
            type: CHERRY_PICK_TYPES.SINGLE_CHANGE,
          },
        });
        assert.equal(fireActionStub.callCount, 0);

        element.$.confirmCherrypick.branch = 'master';
        element._handleCherrypickConfirm({
          detail: {
            branch: 'master',
            type: CHERRY_PICK_TYPES.SINGLE_CHANGE,
          },
        });
        assert.equal(fireActionStub.callCount, 0); // Still needs a message.

        // Add attributes that are used to determine the message.
        element.$.confirmCherrypick.commitMessage = 'foo message';
        element.$.confirmCherrypick.changeStatus = 'OPEN';
        element.$.confirmCherrypick.commitNum = '123';

        element._handleCherrypickConfirm({
          detail: {
            branch: 'master',
            type: CHERRY_PICK_TYPES.SINGLE_CHANGE,
          },
        });

        assert.equal(element.$.confirmCherrypick.shadowRoot.
            querySelector('#messageInput').value, 'foo message');

        assert.deepEqual(fireActionStub.lastCall.args, [
          '/cherrypick', action, true, {
            destination: 'master',
            base: null,
            message: 'foo message',
            allow_conflicts: false,
          },
        ]);
      });

      test('cherry pick even with conflicts', () => {
        element._handleCherrypickTap();
        const action = {
          __key: 'cherrypick',
          __type: 'revision',
          __primary: false,
          enabled: true,
          label: 'Cherry pick',
          method: 'POST',
          title: 'Cherry pick change to a different branch',
        };

        element.$.confirmCherrypick.branch = 'master';

        // Add attributes that are used to determine the message.
        element.$.confirmCherrypick.commitMessage = 'foo message';
        element.$.confirmCherrypick.changeStatus = 'OPEN';
        element.$.confirmCherrypick.commitNum = '123';

        element._handleCherrypickConflictConfirm();

        assert.deepEqual(fireActionStub.lastCall.args, [
          '/cherrypick', action, true, {
            destination: 'master',
            base: null,
            message: 'foo message',
            allow_conflicts: true,
          },
        ]);
      });

      test('branch name cleared when re-open cherrypick', () => {
        const emptyBranchName = '';
        element.$.confirmCherrypick.branch = 'master';

        element._handleCherrypickTap();
        assert.equal(element.$.confirmCherrypick.branch, emptyBranchName);
      });

      suite('cherry pick topics', () => {
        const changes = [
          {
            change_id: '12345678901234', topic: 'T', subject: 'random',
            project: 'A',
          },
          {
            change_id: '23456', topic: 'T', subject: 'a'.repeat(100),
            project: 'B',
          },
        ];
        setup(done => {
          sinon.stub(element.$.restAPI, 'getChanges')
              .returns(Promise.resolve(changes));
          element._handleCherrypickTap();
          flush(() => {
            const radioButtons = element.$.confirmCherrypick.shadowRoot.
                querySelectorAll(`input[name='cherryPickOptions']`);
            assert.equal(radioButtons.length, 2);
            MockInteractions.tap(radioButtons[1]);
            flush(() => {
              done();
            });
          });
        });

        test('cherry pick topic dialog is rendered', done => {
          const dialog = element.$.confirmCherrypick;
          flush(() => {
            const changesTable = dialog.shadowRoot.querySelector('table');
            const headers = Array.from(changesTable.querySelectorAll('th'));
            const expectedHeadings = ['Change', 'Subject', 'Project',
              'Status', ''];
            const headings = headers.map(header => header.innerText);
            assert.equal(headings.length, expectedHeadings.length);
            for (let i = 0; i < headings.length; i++) {
              assert.equal(headings[i].trim(), expectedHeadings[i]);
            }
            const changeRows = changesTable.querySelectorAll('tbody > tr');
            const change = Array.from(changeRows[0].querySelectorAll('td'))
                .map(e => e.innerText);
            const expectedChange = ['1234567890', 'random', 'A',
              'NOT STARTED', ''];
            for (let i = 0; i < change.length; i++) {
              assert.equal(change[i].trim(), expectedChange[i]);
            }
            done();
          });
        });

        test('changes with duplicate project show an error', done => {
          const dialog = element.$.confirmCherrypick;
          const error = dialog.shadowRoot.querySelector('.error-message');
          assert.equal(error.innerText, '');
          dialog.updateChanges([
            {
              change_id: '12345678901234', topic: 'T', subject: 'random',
              project: 'A',
            },
            {
              change_id: '23456', topic: 'T', subject: 'a'.repeat(100),
              project: 'A',
            },
          ]);
          flush(() => {
            assert.equal(error.innerText, 'Two changes cannot be of the same'
             + ' project');
            done();
          });
        });
      });
    });

    suite('move change', () => {
      let fireActionStub;

      setup(() => {
        fireActionStub = sinon.stub(element, '_fireAction');
        sinon.stub(window, 'alert');
      });

      test('works', () => {
        element._handleMoveTap();

        element._handleMoveConfirm();
        assert.equal(fireActionStub.callCount, 0);

        element.$.confirmMove.branch = 'master';
        element._handleMoveConfirm();
        assert.equal(fireActionStub.callCount, 1);
      });

      test('branch name cleared when re-open move', () => {
        const emptyBranchName = '';
        element.$.confirmMove.branch = 'master';

        element._handleMoveTap();
        assert.equal(element.$.confirmMove.branch, emptyBranchName);
      });
    });

    test('custom actions', done => {
      // Add a button with the same key as a server-based one to ensure
      // collisions are taken care of.
      const key = element.addActionButton(element.ActionType.REVISION, 'Bork!');
      element.addEventListener(key + '-tap', e => {
        assert.equal(e.detail.node.getAttribute('data-action-key'), key);
        element.removeActionButton(key);
        flush(() => {
          assert.notOk(element.shadowRoot
              .querySelector('[data-action-key="' + key + '"]'));
          done();
        });
      });
      flush(() => {
        MockInteractions.tap(element.shadowRoot
            .querySelector('[data-action-key="' + key + '"]'));
      });
    });

    test('_setLoadingOnButtonWithKey top-level', () => {
      const key = 'rebase';
      const type = 'revision';
      const cleanup = element._setLoadingOnButtonWithKey(type, key);
      assert.equal(element._actionLoadingMessage, 'Rebasing...');

      const button = element.shadowRoot
          .querySelector('[data-action-key="' + key + '"]');
      assert.isTrue(button.hasAttribute('loading'));
      assert.isTrue(button.disabled);

      assert.isOk(cleanup);
      assert.isFunction(cleanup);
      cleanup();

      assert.isFalse(button.hasAttribute('loading'));
      assert.isFalse(button.disabled);
      assert.isNotOk(element._actionLoadingMessage);
    });

    test('_setLoadingOnButtonWithKey overflow menu', () => {
      const key = 'cherrypick';
      const type = 'revision';
      const cleanup = element._setLoadingOnButtonWithKey(type, key);
      assert.equal(element._actionLoadingMessage, 'Cherry-picking...');
      assert.include(element._disabledMenuActions, 'cherrypick');
      assert.isFunction(cleanup);

      cleanup();

      assert.notOk(element._actionLoadingMessage);
      assert.notInclude(element._disabledMenuActions, 'cherrypick');
    });

    suite('abandon change', () => {
      let alertStub;
      let fireActionStub;

      setup(() => {
        fireActionStub = sinon.stub(element, '_fireAction');
        alertStub = sinon.stub(window, 'alert');
        element.actions = {
          abandon: {
            method: 'POST',
            label: 'Abandon',
            title: 'Abandon the change',
            enabled: true,
          },
        };
        return element.reload();
      });

      test('abandon change with message', done => {
        const newAbandonMsg = 'Test Abandon Message';
        element.$.confirmAbandonDialog.message = newAbandonMsg;
        flush(() => {
          const abandonButton =
              element.shadowRoot
                  .querySelector('gr-button[data-action-key="abandon"]');
          MockInteractions.tap(abandonButton);

          assert.equal(element.$.confirmAbandonDialog.message, newAbandonMsg);
          done();
        });
      });

      test('abandon change with no message', done => {
        flush(() => {
          const abandonButton =
              element.shadowRoot
                  .querySelector('gr-button[data-action-key="abandon"]');
          MockInteractions.tap(abandonButton);

          assert.isUndefined(element.$.confirmAbandonDialog.message);
          done();
        });
      });

      test('works', () => {
        element.$.confirmAbandonDialog.message = 'original message';
        const restoreButton =
            element.shadowRoot
                .querySelector('gr-button[data-action-key="abandon"]');
        MockInteractions.tap(restoreButton);

        element.$.confirmAbandonDialog.message = 'foo message';
        element._handleAbandonDialogConfirm();
        assert.notOk(alertStub.called);

        const action = {
          __key: 'abandon',
          __type: 'change',
          __primary: false,
          enabled: true,
          label: 'Abandon',
          method: 'POST',
          title: 'Abandon the change',
        };
        assert.deepEqual(fireActionStub.lastCall.args, [
          '/abandon', action, false, {
            message: 'foo message',
          }]);
      });
    });

    suite('revert change', () => {
      let fireActionStub;

      setup(() => {
        fireActionStub = sinon.stub(element, '_fireAction');
        element.commitMessage = 'random commit message';
        element.change.current_revision = 'abcdef';
        element.actions = {
          revert: {
            method: 'POST',
            label: 'Revert',
            title: 'Revert the change',
            enabled: true,
          },
        };
        return element.reload();
      });

      test('revert change with plugin hook', done => {
        const newRevertMsg = 'Modified revert msg';
        sinon.stub(element.$.confirmRevertDialog, '_modifyRevertMsg').callsFake(
            () => newRevertMsg);
        element.change = {
          current_revision: 'abc1234',
        };
        sinon.stub(element.$.restAPI, 'getChanges')
            .returns(Promise.resolve([
              {change_id: '12345678901234', topic: 'T', subject: 'random'},
              {change_id: '23456', topic: 'T', subject: 'a'.repeat(100)},
            ]));
        sinon.stub(element.$.confirmRevertDialog,
            '_populateRevertSubmissionMessage').callsFake(() => 'original msg');
        flush(() => {
          const revertButton = element.shadowRoot
              .querySelector('gr-button[data-action-key="revert"]');
          MockInteractions.tap(revertButton);
          flush(() => {
            assert.equal(element.$.confirmRevertDialog._message, newRevertMsg);
            done();
          });
        });
      });

      suite('revert change submitted together', () => {
        let getChangesStub;
        setup(() => {
          element.change = {
            submission_id: '199 0',
            current_revision: '2000',
          };
          getChangesStub = sinon.stub(element.$.restAPI, 'getChanges')
              .returns(Promise.resolve([
                {change_id: '12345678901234', topic: 'T', subject: 'random'},
                {change_id: '23456', topic: 'T', subject: 'a'.repeat(100)},
              ]));
        });

        test('confirm revert dialog shows both options', done => {
          const revertButton = element.shadowRoot
              .querySelector('gr-button[data-action-key="revert"]');
          MockInteractions.tap(revertButton);
          flush(() => {
            assert.equal(getChangesStub.args[0][1], 'submissionid: "199 0"');
            const confirmRevertDialog = element.$.confirmRevertDialog;
            const revertSingleChangeLabel = confirmRevertDialog
                .shadowRoot.querySelector('.revertSingleChange');
            const revertSubmissionLabel = confirmRevertDialog.
                shadowRoot.querySelector('.revertSubmission');
            assert(revertSingleChangeLabel.innerText.trim() ===
                'Revert single change');
            assert(revertSubmissionLabel.innerText.trim() ===
                'Revert entire submission (2 Changes)');
            let expectedMsg = 'Revert submission 199 0' + '\n\n' +
              'Reason for revert: <INSERT REASONING HERE>' + '\n' +
              'Reverted Changes:' + '\n' +
              '1234567890:random' + '\n' +
              '23456:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa...' +
              '\n';
            assert.equal(confirmRevertDialog._message, expectedMsg);
            const radioInputs = confirmRevertDialog.shadowRoot
                .querySelectorAll('input[name="revertOptions"]');
            MockInteractions.tap(radioInputs[0]);
            flush(() => {
              expectedMsg = 'Revert "random commit message"\n\nThis reverts '
               + 'commit 2000.\n\nReason'
               + ' for revert: <INSERT REASONING HERE>\n';
              assert.equal(confirmRevertDialog._message, expectedMsg);
              done();
            });
          });
        });

        test('submit fails if message is not edited', done => {
          const revertButton = element.shadowRoot
              .querySelector('gr-button[data-action-key="revert"]');
          const confirmRevertDialog = element.$.confirmRevertDialog;
          MockInteractions.tap(revertButton);
          const fireStub = sinon.stub(confirmRevertDialog, 'dispatchEvent');
          flush(() => {
            const confirmButton = element.$.confirmRevertDialog.shadowRoot
                .querySelector('gr-dialog')
                .shadowRoot.querySelector('#confirm');
            MockInteractions.tap(confirmButton);
            flush(() => {
              assert.isTrue(confirmRevertDialog._showErrorMessage);
              assert.isFalse(fireStub.called);
              done();
            });
          });
        });

        test('message modification is retained on switching', done => {
          const revertButton = element.shadowRoot
              .querySelector('gr-button[data-action-key="revert"]');
          const confirmRevertDialog = element.$.confirmRevertDialog;
          MockInteractions.tap(revertButton);
          flush(() => {
            const radioInputs = confirmRevertDialog.shadowRoot
                .querySelectorAll('input[name="revertOptions"]');
            const revertSubmissionMsg = 'Revert submission 199 0' + '\n\n' +
            'Reason for revert: <INSERT REASONING HERE>' + '\n' +
            'Reverted Changes:' + '\n' +
            '1234567890:random' + '\n' +
            '23456:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa...' +
            '\n';
            const singleChangeMsg =
            'Revert "random commit message"\n\nThis reverts '
              + 'commit 2000.\n\nReason'
              + ' for revert: <INSERT REASONING HERE>\n';
            assert.equal(confirmRevertDialog._message, revertSubmissionMsg);
            const newRevertMsg = revertSubmissionMsg + 'random';
            const newSingleChangeMsg = singleChangeMsg + 'random';
            confirmRevertDialog._message = newRevertMsg;
            MockInteractions.tap(radioInputs[0]);
            flush(() => {
              assert.equal(confirmRevertDialog._message, singleChangeMsg);
              confirmRevertDialog._message = newSingleChangeMsg;
              MockInteractions.tap(radioInputs[1]);
              flush(() => {
                assert.equal(confirmRevertDialog._message, newRevertMsg);
                MockInteractions.tap(radioInputs[0]);
                flush(() => {
                  assert.equal(
                      confirmRevertDialog._message,
                      newSingleChangeMsg
                  );
                  done();
                });
              });
            });
          });
        });
      });

      suite('revert single change', () => {
        setup(() => {
          element.change = {
            submission_id: '199',
            current_revision: '2000',
          };
          sinon.stub(element.$.restAPI, 'getChanges')
              .returns(Promise.resolve([
                {change_id: '12345678901234', topic: 'T', subject: 'random'},
              ]));
        });

        test('submit fails if message is not edited', done => {
          const revertButton = element.shadowRoot
              .querySelector('gr-button[data-action-key="revert"]');
          const confirmRevertDialog = element.$.confirmRevertDialog;
          MockInteractions.tap(revertButton);
          const fireStub = sinon.stub(confirmRevertDialog, 'dispatchEvent');
          flush(() => {
            const confirmButton = element.$.confirmRevertDialog.shadowRoot
                .querySelector('gr-dialog')
                .shadowRoot.querySelector('#confirm');
            MockInteractions.tap(confirmButton);
            flush(() => {
              assert.isTrue(confirmRevertDialog._showErrorMessage);
              assert.isFalse(fireStub.called);
              done();
            });
          });
        });

        test('confirm revert dialog shows no radio button', done => {
          const revertButton = element.shadowRoot
              .querySelector('gr-button[data-action-key="revert"]');
          MockInteractions.tap(revertButton);
          flush(() => {
            const confirmRevertDialog = element.$.confirmRevertDialog;
            const radioInputs = confirmRevertDialog.shadowRoot
                .querySelectorAll('input[name="revertOptions"]');
            assert.equal(radioInputs.length, 0);
            const msg = 'Revert "random commit message"\n\n'
              + 'This reverts commit 2000.\n\nReason '
              + 'for revert: <INSERT REASONING HERE>\n';
            assert.equal(confirmRevertDialog._message, msg);
            const editedMsg = msg + 'hello';
            confirmRevertDialog._message += 'hello';
            const confirmButton = element.$.confirmRevertDialog.shadowRoot
                .querySelector('gr-dialog')
                .shadowRoot.querySelector('#confirm');
            MockInteractions.tap(confirmButton);
            flush(() => {
              assert.equal(fireActionStub.getCall(0).args[0], '/revert');
              assert.equal(fireActionStub.getCall(0).args[1].__key, 'revert');
              assert.equal(fireActionStub.getCall(0).args[3].message,
                  editedMsg);
              done();
            });
          });
        });
      });
    });

    suite('mark change private', () => {
      setup(() => {
        const privateAction = {
          __key: 'private',
          __type: 'change',
          __primary: false,
          method: 'POST',
          label: 'Mark private',
          title: 'Working...',
          enabled: true,
        };

        element.actions = {
          private: privateAction,
        };

        element.change.is_private = false;

        element.changeNum = '2';
        element.latestPatchNum = '2';

        return element.reload();
      });

      test('make sure the mark private change button is not outside of the ' +
           'overflow menu', done => {
        flush(() => {
          assert.isNotOk(element.shadowRoot
              .querySelector('[data-action-key="private"]'));
          done();
        });
      });

      test('private change', done => {
        flush(() => {
          assert.isOk(
              element.$.moreActions.shadowRoot
                  .querySelector('span[data-id="private-change"]'));
          element.setActionOverflow('change', 'private', false);
          flushAsynchronousOperations();
          assert.isOk(element.shadowRoot
              .querySelector('[data-action-key="private"]'));
          assert.isNotOk(
              element.$.moreActions.shadowRoot
                  .querySelector('span[data-id="private-change"]'));
          done();
        });
      });
    });

    suite('unmark private change', () => {
      setup(() => {
        const unmarkPrivateAction = {
          __key: 'private.delete',
          __type: 'change',
          __primary: false,
          method: 'POST',
          label: 'Unmark private',
          title: 'Working...',
          enabled: true,
        };

        element.actions = {
          'private.delete': unmarkPrivateAction,
        };

        element.change.is_private = true;

        element.changeNum = '2';
        element.latestPatchNum = '2';

        return element.reload();
      });

      test('make sure the unmark private change button is not outside of the ' +
           'overflow menu', done => {
        flush(() => {
          assert.isNotOk(element.shadowRoot
              .querySelector('[data-action-key="private.delete"]'));
          done();
        });
      });

      test('unmark the private change', done => {
        flush(() => {
          assert.isOk(
              element.$.moreActions.shadowRoot
                  .querySelector('span[data-id="private.delete-change"]')
          );
          element.setActionOverflow('change', 'private.delete', false);
          flushAsynchronousOperations();
          assert.isOk(element.shadowRoot
              .querySelector('[data-action-key="private.delete"]'));
          assert.isNotOk(
              element.$.moreActions.shadowRoot
                  .querySelector('span[data-id="private.delete-change"]')
          );
          done();
        });
      });
    });

    suite('delete change', () => {
      let fireActionStub;
      let deleteAction;

      setup(() => {
        fireActionStub = sinon.stub(element, '_fireAction');
        element.change = {
          current_revision: 'abc1234',
        };
        deleteAction = {
          method: 'DELETE',
          label: 'Delete Change',
          title: 'Delete change X_X',
          enabled: true,
        };
        element.actions = {
          '/': deleteAction,
        };
      });

      test('does not delete on action', () => {
        element._handleDeleteTap();
        assert.isFalse(fireActionStub.called);
      });

      test('shows confirm dialog', () => {
        element._handleDeleteTap();
        assert.isFalse(element.shadowRoot
            .querySelector('#confirmDeleteDialog').hidden);
        MockInteractions.tap(
            element.shadowRoot
                .querySelector('#confirmDeleteDialog')
                .shadowRoot
                .querySelector('gr-button[primary]'));
        flushAsynchronousOperations();
        assert.isTrue(fireActionStub.calledWith('/', deleteAction, false));
      });

      test('hides delete confirm on cancel', () => {
        element._handleDeleteTap();
        MockInteractions.tap(
            element.shadowRoot
                .querySelector('#confirmDeleteDialog')
                .shadowRoot
                .querySelector('gr-button:not([primary])'));
        flushAsynchronousOperations();
        assert.isTrue(element.shadowRoot
            .querySelector('#confirmDeleteDialog').hidden);
        assert.isFalse(fireActionStub.called);
      });
    });

    suite('ignore change', () => {
      setup(done => {
        sinon.stub(element, '_fireAction');

        const IgnoreAction = {
          __key: 'ignore',
          __type: 'change',
          __primary: false,
          method: 'PUT',
          label: 'Ignore',
          title: 'Working...',
          enabled: true,
        };

        element.actions = {
          ignore: IgnoreAction,
        };

        element.changeNum = '2';
        element.latestPatchNum = '2';

        element.reload().then(() => { flush(done); });
      });

      test('make sure the ignore button is not outside of the overflow menu',
          () => {
            assert.isNotOk(element.shadowRoot
                .querySelector('[data-action-key="ignore"]'));
          });

      test('ignoring change', () => {
        assert.isOk(element.$.moreActions.shadowRoot
            .querySelector('span[data-id="ignore-change"]'));
        element.setActionOverflow('change', 'ignore', false);
        flushAsynchronousOperations();
        assert.isOk(element.shadowRoot
            .querySelector('[data-action-key="ignore"]'));
        assert.isNotOk(
            element.$.moreActions.shadowRoot
                .querySelector('span[data-id="ignore-change"]'));
      });
    });

    suite('unignore change', () => {
      setup(done => {
        sinon.stub(element, '_fireAction');

        const UnignoreAction = {
          __key: 'unignore',
          __type: 'change',
          __primary: false,
          method: 'PUT',
          label: 'Unignore',
          title: 'Working...',
          enabled: true,
        };

        element.actions = {
          unignore: UnignoreAction,
        };

        element.changeNum = '2';
        element.latestPatchNum = '2';

        element.reload().then(() => { flush(done); });
      });

      test('unignore button is not outside of the overflow menu', () => {
        assert.isNotOk(element.shadowRoot
            .querySelector('[data-action-key="unignore"]'));
      });

      test('unignoring change', () => {
        assert.isOk(
            element.$.moreActions.shadowRoot
                .querySelector('span[data-id="unignore-change"]'));
        element.setActionOverflow('change', 'unignore', false);
        flushAsynchronousOperations();
        assert.isOk(element.shadowRoot
            .querySelector('[data-action-key="unignore"]'));
        assert.isNotOk(
            element.$.moreActions.shadowRoot
                .querySelector('span[data-id="unignore-change"]'));
      });
    });

    suite('reviewed change', () => {
      setup(done => {
        sinon.stub(element, '_fireAction');

        const ReviewedAction = {
          __key: 'reviewed',
          __type: 'change',
          __primary: false,
          method: 'PUT',
          label: 'Mark reviewed',
          title: 'Working...',
          enabled: true,
        };

        element.actions = {
          reviewed: ReviewedAction,
        };

        element.changeNum = '2';
        element.latestPatchNum = '2';

        element.reload().then(() => { flush(done); });
      });

      test('action is enabled', () => {
        assert.equal(element._allActionValues.filter(action =>
          action.__key === 'reviewed').length, 1);
      });

      test('action is skipped when attention set is enabled', () => {
        element._config = {
          change: {enable_attention_set: true},
        };
        assert.equal(element._allActionValues.filter(action =>
          action.__key === 'reviewed').length, 0);
      });

      test('make sure the reviewed button is not outside of the overflow menu',
          () => {
            assert.isNotOk(element.shadowRoot
                .querySelector('[data-action-key="reviewed"]'));
          });

      test('reviewing change', () => {
        assert.isOk(
            element.$.moreActions.shadowRoot
                .querySelector('span[data-id="reviewed-change"]'));
        element.setActionOverflow('change', 'reviewed', false);
        flushAsynchronousOperations();
        assert.isOk(element.shadowRoot
            .querySelector('[data-action-key="reviewed"]'));
        assert.isNotOk(
            element.$.moreActions.shadowRoot
                .querySelector('span[data-id="reviewed-change"]'));
      });
    });

    suite('unreviewed change', () => {
      setup(done => {
        sinon.stub(element, '_fireAction');

        const UnreviewedAction = {
          __key: 'unreviewed',
          __type: 'change',
          __primary: false,
          method: 'PUT',
          label: 'Mark unreviewed',
          title: 'Working...',
          enabled: true,
        };

        element.actions = {
          unreviewed: UnreviewedAction,
        };

        element.changeNum = '2';
        element.latestPatchNum = '2';

        element.reload().then(() => { flush(done); });
      });

      test('unreviewed button not outside of the overflow menu', () => {
        assert.isNotOk(element.shadowRoot
            .querySelector('[data-action-key="unreviewed"]'));
      });

      test('unreviewed change', () => {
        assert.isOk(
            element.$.moreActions.shadowRoot
                .querySelector('span[data-id="unreviewed-change"]'));
        element.setActionOverflow('change', 'unreviewed', false);
        flushAsynchronousOperations();
        assert.isOk(element.shadowRoot
            .querySelector('[data-action-key="unreviewed"]'));
        assert.isNotOk(
            element.$.moreActions.shadowRoot
                .querySelector('span[data-id="unreviewed-change"]'));
      });
    });

    suite('quick approve', () => {
      setup(() => {
        element.change = {
          current_revision: 'abc1234',
        };
        element.change = {
          current_revision: 'abc1234',
          labels: {
            foo: {
              values: {
                '-1': '',
                ' 0': '',
                '+1': '',
              },
            },
          },
          permitted_labels: {
            foo: ['-1', ' 0', '+1'],
          },
        };
        flushAsynchronousOperations();
      });

      test('added when can approve', () => {
        const approveButton =
            element.shadowRoot
                .querySelector('gr-button[data-action-key=\'review\']');
        assert.isNotNull(approveButton);
      });

      test('hide quick approve', () => {
        const approveButton =
            element.shadowRoot
                .querySelector('gr-button[data-action-key=\'review\']');
        assert.isNotNull(approveButton);
        assert.isFalse(element._hideQuickApproveAction);

        // Assert approve button gets removed from list of buttons.
        element.hideQuickApproveAction();
        flushAsynchronousOperations();
        const approveButtonUpdated =
            element.shadowRoot
                .querySelector('gr-button[data-action-key=\'review\']');
        assert.isNull(approveButtonUpdated);
        assert.isTrue(element._hideQuickApproveAction);
      });

      test('is first in list of secondary actions', () => {
        const approveButton = element.$.secondaryActions
            .querySelector('gr-button');
        assert.equal(approveButton.getAttribute('data-label'), 'foo+1');
      });

      test('not added when already approved', () => {
        element.change = {
          current_revision: 'abc1234',
          labels: {
            foo: {
              approved: {},
              values: {},
            },
          },
          permitted_labels: {
            foo: [' 0', '+1'],
          },
        };
        flushAsynchronousOperations();
        const approveButton =
            element.shadowRoot
                .querySelector('gr-button[data-action-key=\'review\']');
        assert.isNull(approveButton);
      });

      test('not added when label not permitted', () => {
        element.change = {
          current_revision: 'abc1234',
          labels: {
            foo: {values: {}},
          },
          permitted_labels: {
            bar: [],
          },
        };
        flushAsynchronousOperations();
        const approveButton =
            element.shadowRoot
                .querySelector('gr-button[data-action-key=\'review\']');
        assert.isNull(approveButton);
      });

      test('approves when tapped', () => {
        const fireActionStub = sinon.stub(element, '_fireAction');
        MockInteractions.tap(
            element.shadowRoot
                .querySelector('gr-button[data-action-key=\'review\']'));
        flushAsynchronousOperations();
        assert.isTrue(fireActionStub.called);
        assert.isTrue(fireActionStub.calledWith('/review'));
        const payload = fireActionStub.lastCall.args[3];
        assert.deepEqual(payload.labels, {foo: '+1'});
      });

      test('not added when multiple labels are required', () => {
        element.change = {
          current_revision: 'abc1234',
          labels: {
            foo: {values: {}},
            bar: {values: {}},
          },
          permitted_labels: {
            foo: [' 0', '+1'],
            bar: [' 0', '+1', '+2'],
          },
        };
        flushAsynchronousOperations();
        const approveButton =
            element.shadowRoot
                .querySelector('gr-button[data-action-key=\'review\']');
        assert.isNull(approveButton);
      });

      test('button label for missing approval', () => {
        element.change = {
          current_revision: 'abc1234',
          labels: {
            foo: {
              values: {
                ' 0': '',
                '+1': '',
              },
            },
            bar: {approved: {}, values: {}},
          },
          permitted_labels: {
            foo: [' 0', '+1'],
            bar: [' 0', '+1', '+2'],
          },
        };
        flushAsynchronousOperations();
        const approveButton =
            element.shadowRoot
                .querySelector('gr-button[data-action-key=\'review\']');
        assert.equal(approveButton.getAttribute('data-label'), 'foo+1');
      });

      test('no quick approve if score is not maximal for a label', () => {
        element.change = {
          current_revision: 'abc1234',
          labels: {
            bar: {
              value: 1,
              values: {
                ' 0': '',
                '+1': '',
                '+2': '',
              },
            },
          },
          permitted_labels: {
            bar: [' 0', '+1'],
          },
        };
        flushAsynchronousOperations();
        const approveButton =
            element.shadowRoot
                .querySelector('gr-button[data-action-key=\'review\']');
        assert.isNull(approveButton);
      });

      test('approving label with a non-max score', () => {
        element.change = {
          current_revision: 'abc1234',
          labels: {
            bar: {
              value: 1,
              values: {
                ' 0': '',
                '+1': '',
                '+2': '',
              },
            },
          },
          permitted_labels: {
            bar: [' 0', '+1', '+2'],
          },
        };
        flushAsynchronousOperations();
        const approveButton =
            element.shadowRoot
                .querySelector('gr-button[data-action-key=\'review\']');
        assert.equal(approveButton.getAttribute('data-label'), 'bar+2');
      });
    });

    test('adds download revision action', () => {
      const handler = sinon.stub();
      element.addEventListener('download-tap', handler);
      assert.ok(element.revisionActions.download);
      element._handleDownloadTap();
      flushAsynchronousOperations();

      assert.isTrue(handler.called);
    });

    test('changing changeNum or patchNum does not reload', () => {
      const reloadStub = sinon.stub(element, 'reload');
      element.changeNum = 123;
      assert.isFalse(reloadStub.called);
      element.latestPatchNum = 456;
      assert.isFalse(reloadStub.called);
    });

    test('_toSentenceCase', () => {
      assert.equal(element._toSentenceCase('blah blah'), 'Blah blah');
      assert.equal(element._toSentenceCase('BLAH BLAH'), 'Blah blah');
      assert.equal(element._toSentenceCase('b'), 'B');
      assert.equal(element._toSentenceCase(''), '');
      assert.equal(element._toSentenceCase('!@#$%^&*()'), '!@#$%^&*()');
    });

    suite('setActionOverflow', () => {
      test('move action from overflow', () => {
        assert.isNotOk(element.shadowRoot
            .querySelector('[data-action-key="cherrypick"]'));
        assert.strictEqual(
            element.$.moreActions.items[0].id, 'cherrypick-revision');
        element.setActionOverflow('revision', 'cherrypick', false);
        flushAsynchronousOperations();
        assert.isOk(element.shadowRoot
            .querySelector('[data-action-key="cherrypick"]'));
        assert.notEqual(
            element.$.moreActions.items[0].id, 'cherrypick-revision');
      });

      test('move action to overflow', () => {
        assert.isOk(element.shadowRoot
            .querySelector('[data-action-key="submit"]'));
        element.setActionOverflow('revision', 'submit', true);
        flushAsynchronousOperations();
        assert.isNotOk(element.shadowRoot
            .querySelector('[data-action-key="submit"]'));
        assert.strictEqual(
            element.$.moreActions.items[3].id, 'submit-revision');
      });

      suite('_waitForChangeReachable', () => {
        setup(() => {
          sinon.stub(element, 'async').callsFake( fn => fn());
        });

        const makeGetChange = numTries => () => {
          if (numTries === 1) {
            return Promise.resolve({_number: 123});
          } else {
            numTries--;
            return Promise.resolve(undefined);
          }
        };

        test('succeed', () => {
          sinon.stub(element.$.restAPI, 'getChange')
              .callsFake( makeGetChange(5));
          return element._waitForChangeReachable(123).then(success => {
            assert.isTrue(success);
          });
        });

        test('fail', () => {
          sinon.stub(element.$.restAPI, 'getChange')
              .callsFake( makeGetChange(6));
          return element._waitForChangeReachable(123).then(success => {
            assert.isFalse(success);
          });
        });
      });
    });

    suite('_send', () => {
      let cleanup;
      let payload;
      let onShowError;
      let onShowAlert;
      let getResponseObjectStub;

      setup(() => {
        cleanup = sinon.stub();
        element.changeNum = 42;
        element.change._number = 42;
        element.latestPatchNum = 12;
        element.change = generateChange({
          revisionsCount: element.latestPatchNum,
          messagesCount: 1,
        });
        payload = {foo: 'bar'};

        onShowError = sinon.stub();
        element.addEventListener('show-error', onShowError);
        onShowAlert = sinon.stub();
        element.addEventListener('show-alert', onShowAlert);
      });

      suite('happy path', () => {
        let sendStub;
        setup(() => {
          sinon.stub(element.$.restAPI, 'getChangeDetail')
              .returns(Promise.resolve(
                  generateChange({
                    // element has latest info
                    revisionsCount: element.latestPatchNum,
                    messagesCount: 1,
                  })));
          sendStub = sinon.stub(element.$.restAPI, 'executeChangeAction')
              .returns(Promise.resolve({}));
          getResponseObjectStub = sinon.stub(element.$.restAPI,
              'getResponseObject');
          sinon.stub(GerritNav,
              'navigateToChange').returns(Promise.resolve(true));
        });

        test('change action', done => {
          element
              ._send('DELETE', payload, '/endpoint', false, cleanup)
              .then(() => {
                assert.isFalse(onShowError.called);
                assert.isTrue(cleanup.calledOnce);
                assert.isTrue(sendStub.calledWith(42, 'DELETE', '/endpoint',
                    null, payload));
                done();
              });
        });

        suite('show revert submission dialog', () => {
          setup(() => {
            element.change.submission_id = '199';
            element.change.current_revision = '2000';
            sinon.stub(element.$.restAPI, 'getChanges')
                .returns(Promise.resolve([
                  {change_id: '12345678901234', topic: 'T', subject: 'random'},
                  {change_id: '23456', topic: 'T', subject: 'a'.repeat(100)},
                ]));
          });

          test('revert submission shows submissionId', done => {
            const expectedMsg = 'Revert submission 199' + '\n\n' +
              'Reason for revert: <INSERT REASONING HERE>' + '\n' +
              'Reverted Changes:' + '\n' +
              '1234567890: random' + '\n' +
              '23456: aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa...' +
              '\n';
            const modifiedMsg = expectedMsg + 'abcd';
            sinon.stub(element.$.confirmRevertSubmissionDialog,
                '_modifyRevertSubmissionMsg').returns(modifiedMsg);
            element.showRevertSubmissionDialog();
            flush(() => {
              const msg = element.$.confirmRevertSubmissionDialog.message;
              assert.equal(msg, modifiedMsg);
              done();
            });
          });
        });

        suite('single changes revert', () => {
          let navigateToSearchQueryStub;
          setup(() => {
            getResponseObjectStub
                .returns(Promise.resolve({revert_changes: [
                  {change_id: 12345},
                ]}));
            navigateToSearchQueryStub = sinon.stub(GerritNav,
                'navigateToSearchQuery');
          });

          test('revert submission single change', done => {
            element._send('POST', {message: 'Revert submission'},
                '/revert_submission', false, cleanup).then(res => {
              element._handleResponse({__key: 'revert_submission'}, {}).
                  then(() => {
                    assert.isTrue(navigateToSearchQueryStub.called);
                    done();
                  });
            });
          });
        });

        suite('multiple changes revert', () => {
          let showActionDialogStub;
          let navigateToSearchQueryStub;
          setup(() => {
            getResponseObjectStub
                .returns(Promise.resolve({revert_changes: [
                  {change_id: 12345, topic: 'T'},
                  {change_id: 23456, topic: 'T'},
                ]}));
            showActionDialogStub = sinon.stub(element, '_showActionDialog');
            navigateToSearchQueryStub = sinon.stub(GerritNav,
                'navigateToSearchQuery');
          });

          test('revert submission multiple change', done => {
            element._send('POST', {message: 'Revert submission'},
                '/revert_submission', false, cleanup).then(res => {
              element._handleResponse({__key: 'revert_submission'}, {}).then(
                  () => {
                    assert.isFalse(showActionDialogStub.called);
                    assert.isTrue(navigateToSearchQueryStub.calledWith(
                        'topic: T'));
                    done();
                  });
            });
          });
        });

        test('revision action', done => {
          element
              ._send('DELETE', payload, '/endpoint', true, cleanup)
              .then(() => {
                assert.isFalse(onShowError.called);
                assert.isTrue(cleanup.calledOnce);
                assert.isTrue(sendStub.calledWith(42, 'DELETE', '/endpoint',
                    12, payload));
                done();
              });
        });
      });

      suite('failure modes', () => {
        test('non-latest', () => {
          sinon.stub(element.$.restAPI, 'getChangeDetail')
              .returns(Promise.resolve(
                  generateChange({
                    // new patchset was uploaded
                    revisionsCount: element.latestPatchNum + 1,
                    messagesCount: 1,
                  })));
          const sendStub = sinon.stub(element.$.restAPI,
              'executeChangeAction');

          return element._send('DELETE', payload, '/endpoint', true, cleanup)
              .then(() => {
                assert.isTrue(onShowAlert.calledOnce);
                assert.isFalse(onShowError.called);
                assert.isTrue(cleanup.calledOnce);
                assert.isFalse(sendStub.called);
              });
        });

        test('send fails', () => {
          sinon.stub(element.$.restAPI, 'getChangeDetail')
              .returns(Promise.resolve(
                  generateChange({
                    // element has latest info
                    revisionsCount: element.latestPatchNum,
                    messagesCount: 1,
                  })));
          const sendStub = sinon.stub(element.$.restAPI,
              'executeChangeAction').callsFake(
              (num, method, patchNum, endpoint, payload, onErr) => {
                onErr();
                return Promise.resolve(null);
              });
          const handleErrorStub = sinon.stub(element, '_handleResponseError');

          return element._send('DELETE', payload, '/endpoint', true, cleanup)
              .then(() => {
                assert.isFalse(onShowError.called);
                assert.isTrue(cleanup.called);
                assert.isTrue(sendStub.calledOnce);
                assert.isTrue(handleErrorStub.called);
              });
        });
      });
    });

    test('_handleAction reports', () => {
      sinon.stub(element, '_fireAction');
      const reportStub = sinon.stub(element.reporting, 'reportInteraction');
      element._handleAction('type', 'key');
      assert.isTrue(reportStub.called);
      assert.equal(reportStub.lastCall.args[0], 'type-key');
    });
  });

  suite('getChangeRevisionActions returns only some actions', () => {
    let element;

    let changeRevisionActions;

    setup(() => {
      stub('gr-rest-api-interface', {
        getChangeRevisionActions() {
          return Promise.resolve(changeRevisionActions);
        },
        send(method, url, payload) {
          return Promise.reject(new Error('error'));
        },
        getProjectConfig() { return Promise.resolve({}); },
      });

      sinon.stub(pluginLoader, 'awaitPluginsLoaded')
          .returns(Promise.resolve());

      element = basicFixture.instantiate();
      // getChangeRevisionActions is not called without
      // set the following properies
      element.change = {};
      element.changeNum = '42';
      element.latestPatchNum = '2';

      sinon.stub(element.$.confirmCherrypick.$.restAPI,
          'getRepoBranches').returns(Promise.resolve([]));
      sinon.stub(element.$.confirmMove.$.restAPI,
          'getRepoBranches').returns(Promise.resolve([]));
      return element.reload();
    });

    test('confirmSubmitDialog and confirmRebase properties are changed', () => {
      changeRevisionActions = {};
      element.reload();
      assert.strictEqual(element.$.confirmSubmitDialog.action, null);
      assert.strictEqual(element.$.confirmRebase.rebaseOnCurrent, null);
    });

    test('_computeRebaseOnCurrent', () => {
      const rebaseAction = {
        enabled: true,
        label: 'Rebase',
        method: 'POST',
        title: 'Rebase onto tip of branch or parent change',
      };

      // When rebase is enabled initially, rebaseOnCurrent should be set to
      // true.
      assert.isTrue(element._computeRebaseOnCurrent(rebaseAction));

      delete rebaseAction.enabled;

      // When rebase is not enabled initially, rebaseOnCurrent should be set to
      // false.
      assert.isFalse(element._computeRebaseOnCurrent(rebaseAction));
    });
  });
});

