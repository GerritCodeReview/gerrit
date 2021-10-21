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

import '../../../test/common-test-setup-karma';
import './gr-change-actions';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {getPluginLoader} from '../../shared/gr-js-api-interface/gr-plugin-loader';
import {
  createAccountWithId,
  createApproval,
  createChange,
  createChangeMessages,
  createChangeViewChange,
  createRevision,
  createRevisions,
} from '../../../test/test-data-generators';
import {ChangeStatus, HttpMethod} from '../../../constants/constants';
import {
  mockPromise,
  query,
  queryAll,
  queryAndAssert,
  stubReporting,
  stubRestApi,
} from '../../../test/test-utils';
import {assertUIActionInfo, GrChangeActions} from './gr-change-actions';
import {
  AccountId,
  ActionInfo,
  ActionNameToActionInfoMap,
  BranchName,
  ChangeId,
  ChangeSubmissionId,
  CommitId,
  NumericChangeId,
  PatchSetNum,
  RepoName,
  ReviewInput,
  TopicName,
} from '../../../types/common';
import {ActionType} from '../../../api/change-actions';
import {tap} from '@polymer/iron-test-helpers/mock-interactions';
import {SinonFakeTimers} from 'sinon';
import {IronAutogrowTextareaElement} from '@polymer/iron-autogrow-textarea';
import {GrButton} from '../../shared/gr-button/gr-button';
import {GrDialog} from '../../shared/gr-dialog/gr-dialog';
import {UIActionInfo} from '../../shared/gr-js-api-interface/gr-change-actions-js-api';
import {PolymerDeepPropertyChange} from '@polymer/polymer/interfaces';
import {appContext} from '../../../services/app-context';

const basicFixture = fixtureFromElement('gr-change-actions');

// TODO(dhruvsri): remove use of _populateRevertMessage as it's private
suite('gr-change-actions tests', () => {
  let element: GrChangeActions;

  suite('basic tests', () => {
    setup(() => {
      stubRestApi('getChangeRevisionActions').returns(
        Promise.resolve({
          cherrypick: {
            method: HttpMethod.POST,
            label: 'Cherry Pick',
            title: 'Cherry pick change to a different branch',
            enabled: true,
          },
          rebase: {
            method: HttpMethod.POST,
            label: 'Rebase',
            title: 'Rebase onto tip of branch or parent change',
            enabled: true,
          },
          submit: {
            method: HttpMethod.POST,
            label: 'Submit',
            title: 'Submit patch set 2 into master',
            enabled: true,
          },
          revert_submission: {
            method: HttpMethod.POST,
            label: 'Revert submission',
            title: 'Revert this submission',
            enabled: true,
          },
        })
      );
      stubRestApi('send').callsFake((method, url) => {
        if (method !== 'POST') {
          return Promise.reject(new Error('bad method'));
        }
        if (url === '/changes/test~42/revisions/2/submit') {
          return Promise.resolve({
            ...new Response(),
            ok: true,
            text() {
              return Promise.resolve(")]}'\n{}");
            },
          });
        } else if (url === '/changes/test~42/revisions/2/rebase') {
          return Promise.resolve({
            ...new Response(),
            ok: true,
            text() {
              return Promise.resolve(")]}'\n{}");
            },
          });
        }
        return Promise.reject(new Error('bad url'));
      });

      sinon
        .stub(getPluginLoader(), 'awaitPluginsLoaded')
        .returns(Promise.resolve());

      element = basicFixture.instantiate();
      element.change = createChangeViewChange();
      element.changeNum = 42 as NumericChangeId;
      element.latestPatchNum = 2 as PatchSetNum;
      element.actions = {
        '/': {
          method: HttpMethod.DELETE,
          label: 'Delete Change',
          title: 'Delete change X_X',
          enabled: true,
        },
      };
      element.account = {
        _account_id: 123 as AccountId,
      };
      stubRestApi('getRepoBranches').returns(Promise.resolve([]));

      return element.reload();
    });

    test('show-revision-actions event should fire', async () => {
      const spy = sinon.spy(element, '_sendShowRevisionActions');
      element.reload();
      await flush();
      assert.isTrue(spy.called);
    });

    test('primary and secondary actions split properly', () => {
      // Submit should be the only primary action.
      assert.equal(element._topLevelPrimaryActions!.length, 1);
      assert.equal(element._topLevelPrimaryActions![0].label, 'Submit');
      assert.equal(
        element._topLevelSecondaryActions!.length,
        element._topLevelActions!.length - 1
      );
    });

    test('revert submission action is skipped', () => {
      assert.equal(
        element._allActionValues.filter(action => action.__key === 'submit')
          .length,
        1
      );
      assert.equal(
        element._allActionValues.filter(
          action => action.__key === 'revert_submission'
        ).length,
        0
      );
    });

    test('_shouldHideActions', () => {
      assert.isTrue(element._shouldHideActions(undefined, true));
      assert.isTrue(
        element._shouldHideActions(
          {base: [] as UIActionInfo[]} as PolymerDeepPropertyChange<
            UIActionInfo[],
            UIActionInfo[]
          >,
          false
        )
      );
      assert.isFalse(
        element._shouldHideActions(
          {
            base: [{__key: 'test'}] as UIActionInfo[],
          } as PolymerDeepPropertyChange<UIActionInfo[], UIActionInfo[]>,
          false
        )
      );
    });

    test('plugin revision actions', async () => {
      const stub = stubRestApi('getChangeActionURL').returns(
        Promise.resolve('the-url')
      );
      element.revisionActions = {
        'plugin~action': {},
      };
      assert.isOk(element.revisionActions['plugin~action']);
      await flush();
      assert.isTrue(
        stub.calledWith(
          element.changeNum,
          element.latestPatchNum,
          '/plugin~action'
        )
      );
      assert.equal(
        (element.revisionActions['plugin~action'] as UIActionInfo)!.__url,
        'the-url'
      );
    });

    test('plugin change actions', async () => {
      const stub = stubRestApi('getChangeActionURL').returns(
        Promise.resolve('the-url')
      );
      element.actions = {
        'plugin~action': {},
      };
      assert.isOk(element.actions['plugin~action']);
      await flush();
      assert.isTrue(
        stub.calledWith(element.changeNum, undefined, '/plugin~action')
      );
      assert.equal(
        (element.actions['plugin~action'] as UIActionInfo)!.__url,
        'the-url'
      );
    });

    test('not supported actions are filtered out', () => {
      element.revisionActions = {followup: {}};
      assert.equal(
        element.querySelectorAll(
          'section gr-button[data-action-type="revision"]'
        ).length,
        0
      );
    });

    test('getActionDetails', () => {
      element.revisionActions = {
        'plugin~action': {},
        ...element.revisionActions,
      };
      assert.isUndefined(element.getActionDetails('rubbish'));
      assert.strictEqual(
        element.revisionActions['plugin~action'],
        element.getActionDetails('plugin~action')
      );
      assert.strictEqual(
        element.revisionActions['rebase'],
        element.getActionDetails('rebase')
      );
    });

    test('hide revision action', async () => {
      await flush();
      let buttonEl: Element | undefined = queryAndAssert(
        element,
        '[data-action-key="submit"]'
      );
      assert.isOk(buttonEl);
      element.setActionHidden(
        element.ActionType.REVISION,
        element.RevisionActions.SUBMIT,
        true
      );
      assert.lengthOf(element._hiddenActions, 1);
      element.setActionHidden(
        element.ActionType.REVISION,
        element.RevisionActions.SUBMIT,
        true
      );
      assert.lengthOf(element._hiddenActions, 1);
      await flush();
      buttonEl = query(element, '[data-action-key="submit"]');
      assert.isNotOk(buttonEl);

      element.setActionHidden(
        element.ActionType.REVISION,
        element.RevisionActions.SUBMIT,
        false
      );
      await flush();
      buttonEl = queryAndAssert(element, '[data-action-key="submit"]');
      assert.isFalse(buttonEl.hasAttribute('hidden'));
    });

    test('buttons exist', async () => {
      element._loading = false;
      await flush();
      const buttonEls = queryAll(element, 'gr-button');
      const menuItems = element.$.moreActions.items;

      // Total button number is one greater than the number of total actions
      // due to the existence of the overflow menu trigger.
      assert.equal(
        buttonEls!.length + menuItems!.length,
        element._allActionValues.length + 1
      );
      assert.isFalse(element.hidden);
    });

    test('delete buttons have explicit labels', async () => {
      await flush();
      const deleteItems = element.$.moreActions.items!.filter(item =>
        item.id!.startsWith('delete')
      );
      assert.equal(deleteItems.length, 1);
      assert.equal(deleteItems[0].name, 'Delete change');
    });

    test('get revision object from change', () => {
      const revObj = {
        ...createRevision(),
        _number: 2 as PatchSetNum,
        foo: 'bar',
      };
      const change = {
        ...createChangeViewChange(),
        revisions: {
          rev1: {...createRevision(), _number: 1 as PatchSetNum},
          rev2: revObj,
        },
      };
      assert.deepEqual(element._getRevision(change, 2 as PatchSetNum), revObj);
    });

    test('_actionComparator sort order', () => {
      const actions = [
        {label: '123', __type: ActionType.CHANGE, __key: 'review'},
        {label: 'abc-ro', __type: ActionType.REVISION, __key: 'random'},
        {label: 'abc', __type: ActionType.CHANGE, __key: 'random'},
        {label: 'def', __type: ActionType.CHANGE, __key: 'random'},
        {
          label: 'def-p',
          __type: ActionType.CHANGE,
          __primary: true,
          __key: 'random',
        },
      ];

      const result = actions.slice();
      result.reverse();
      result.sort(element._actionComparator.bind(element));
      assert.deepEqual(result, actions);
    });

    test('submit change', async () => {
      const showSpy = sinon.spy(element, '_showActionDialog');
      stubRestApi('getFromProjectLookup').returns(
        Promise.resolve('test' as RepoName)
      );
      sinon.stub(element.$.overlay, 'open').returns(Promise.resolve());
      element.change = {
        ...createChangeViewChange(),
        revisions: {
          rev1: {...createRevision(), _number: 1 as PatchSetNum},
          rev2: {...createRevision(), _number: 2 as PatchSetNum},
        },
      };
      element.latestPatchNum = 2 as PatchSetNum;

      const submitButton = queryAndAssert(
        element,
        'gr-button[data-action-key="submit"]'
      );
      tap(submitButton);

      await flush();
      assert.isTrue(showSpy.calledWith(element.$.confirmSubmitDialog));
    });

    test('submit change, tap on icon', async () => {
      const submitted = mockPromise();
      sinon
        .stub(element.$.confirmSubmitDialog, 'resetFocus')
        .callsFake(() => submitted.resolve());
      stubRestApi('getFromProjectLookup').returns(
        Promise.resolve('test' as RepoName)
      );
      sinon.stub(element.$.overlay, 'open').returns(Promise.resolve());
      element.change = {
        ...createChangeViewChange(),
        revisions: {
          rev1: {...createRevision(), _number: 1 as PatchSetNum},
          rev2: {...createRevision(), _number: 2 as PatchSetNum},
        },
      };
      element.latestPatchNum = 2 as PatchSetNum;

      const submitIcon = queryAndAssert(
        element,
        'gr-button[data-action-key="submit"] iron-icon'
      );
      tap(submitIcon);
      await submitted;
    });

    test('_handleSubmitConfirm', () => {
      const fireStub = sinon.stub(element, '_fireAction');
      sinon.stub(element, '_canSubmitChange').returns(true);
      element._handleSubmitConfirm();
      assert.isTrue(fireStub.calledOnce);
      assert.deepEqual(fireStub.lastCall.args, [
        '/submit',
        assertUIActionInfo(element.revisionActions.submit),
        true,
      ]);
    });

    test('_handleSubmitConfirm when not able to submit', () => {
      const fireStub = sinon.stub(element, '_fireAction');
      sinon.stub(element, '_canSubmitChange').returns(false);
      element._handleSubmitConfirm();
      assert.isFalse(fireStub.called);
    });

    test('submit change with plugin hook', async () => {
      sinon.stub(element, '_canSubmitChange').callsFake(() => false);
      const fireActionStub = sinon.stub(element, '_fireAction');
      await flush();
      const submitButton = queryAndAssert(
        element,
        'gr-button[data-action-key="submit"]'
      );
      tap(submitButton);
      assert.equal(fireActionStub.callCount, 0);
    });

    test('chain state', () => {
      assert.equal(element._hasKnownChainState, false);
      element.hasParent = true;
      assert.equal(element._hasKnownChainState, true);
      element.hasParent = false;
    });

    test('_calculateDisabled', () => {
      let hasKnownChainState = false;
      const action = {
        __key: 'rebase',
        enabled: true,
        __type: ActionType.CHANGE,
        label: 'l',
      };
      assert.equal(
        element._calculateDisabled(action, hasKnownChainState),
        true
      );

      action.__key = 'delete';
      assert.equal(
        element._calculateDisabled(action, hasKnownChainState),
        false
      );

      action.__key = 'rebase';
      hasKnownChainState = true;
      assert.equal(
        element._calculateDisabled(action, hasKnownChainState),
        false
      );

      action.enabled = false;
      assert.equal(
        element._calculateDisabled(action, hasKnownChainState),
        false
      );
    });

    test('rebase change', async () => {
      const fireActionStub = sinon.stub(element, '_fireAction');
      const fetchChangesStub = sinon
        .stub(element.$.confirmRebase, 'fetchRecentChanges')
        .returns(Promise.resolve([]));
      element._hasKnownChainState = true;
      await flush();
      const rebaseButton = queryAndAssert(
        element,
        'gr-button[data-action-key="rebase"]'
      );
      tap(rebaseButton);
      const rebaseAction = {
        __key: 'rebase',
        __type: 'revision',
        __primary: false,
        enabled: true,
        label: 'Rebase',
        method: HttpMethod.POST,
        title: 'Rebase onto tip of branch or parent change',
      };
      assert.isTrue(fetchChangesStub.called);
      element._handleRebaseConfirm(
        new CustomEvent('', {detail: {base: '1234'}})
      );
      assert.deepEqual(fireActionStub.lastCall.args, [
        '/rebase',
        assertUIActionInfo(rebaseAction),
        true,
        {base: '1234'},
      ]);
    });

    test('rebase change fires reload event', async () => {
      const eventStub = sinon.stub(element, 'dispatchEvent');
      element._handleResponse(
        {__key: 'rebase', __type: ActionType.CHANGE, label: 'l'},
        new Response()
      );
      await flush();
      assert.isTrue(eventStub.called);
      assert.equal(eventStub.lastCall.args[0].type, 'reload');
    });

    test("rebase dialog gets recent changes each time it's opened", async () => {
      const fetchChangesStub = sinon
        .stub(element.$.confirmRebase, 'fetchRecentChanges')
        .returns(Promise.resolve([]));
      element._hasKnownChainState = true;
      const rebaseButton = queryAndAssert(
        element,
        'gr-button[data-action-key="rebase"]'
      );
      tap(rebaseButton);
      assert.isTrue(fetchChangesStub.calledOnce);

      await flush();
      element.$.confirmRebase.dispatchEvent(
        new CustomEvent('cancel', {
          composed: true,
          bubbles: true,
        })
      );
      tap(rebaseButton);
      assert.isTrue(fetchChangesStub.calledTwice);
    });

    test('two dialogs are not shown at the same time', async () => {
      element._hasKnownChainState = true;
      await flush();
      const rebaseButton = queryAndAssert(
        element,
        'gr-button[data-action-key="rebase"]'
      );
      tap(rebaseButton);
      await flush();
      assert.isFalse(element.$.confirmRebase.hidden);
      stubRestApi('getChanges').returns(Promise.resolve([]));
      element._handleCherrypickTap();
      await flush();
      assert.isTrue(element.$.confirmRebase.hidden);
      assert.isFalse(element.$.confirmCherrypick.hidden);
    });

    test('fullscreen-overlay-opened hides content', () => {
      const spy = sinon.spy(element, '_handleHideBackgroundContent');
      element.$.overlay.dispatchEvent(
        new CustomEvent('fullscreen-overlay-opened', {
          composed: true,
          bubbles: true,
        })
      );
      assert.isTrue(spy.called);
      assert.isTrue(element.$.mainContent.classList.contains('overlayOpen'));
    });

    test('fullscreen-overlay-closed shows content', () => {
      const spy = sinon.spy(element, '_handleShowBackgroundContent');
      element.$.overlay.dispatchEvent(
        new CustomEvent('fullscreen-overlay-closed', {
          composed: true,
          bubbles: true,
        })
      );
      assert.isTrue(spy.called);
      assert.isFalse(element.$.mainContent.classList.contains('overlayOpen'));
    });

    test('_setReviewOnRevert', () => {
      const review = {labels: {Foo: 1, 'Bar-Baz': -2}};
      const changeId = 1234 as NumericChangeId;
      sinon
        .stub(appContext.jsApiService, 'getReviewPostRevert')
        .returns(review);
      const saveStub = stubRestApi('saveChangeReview').returns(
        Promise.resolve(new Response())
      );
      const setReviewOnRevert = element._setReviewOnRevert(changeId) as Promise<
        undefined | Response
      >;
      return setReviewOnRevert.then((_res: Response | undefined) => {
        assert.isTrue(saveStub.calledOnce);
        assert.equal(saveStub.lastCall.args[0], changeId);
        assert.deepEqual(saveStub.lastCall.args[2], review);
      });
    });

    suite('change edits', () => {
      test('disableEdit', async () => {
        element.set('editMode', false);
        element.set('editPatchsetLoaded', false);
        element.change = {
          ...createChangeViewChange(),
          status: ChangeStatus.NEW,
        };
        element.set('disableEdit', true);
        await flush();

        assert.isNotOk(
          query(element, 'gr-button[data-action-key="publishEdit"]')
        );
        assert.isNotOk(
          query(element, 'gr-button[data-action-key="rebaseEdit"]')
        );
        assert.isNotOk(
          query(element, 'gr-button[data-action-key="deleteEdit"]')
        );
        assert.isNotOk(query(element, 'gr-button[data-action-key="edit"]'));
        assert.isNotOk(query(element, 'gr-button[data-action-key="stopEdit"]'));
      });

      test('shows confirm dialog for delete edit', async () => {
        element.set('editMode', true);
        element.set('editPatchsetLoaded', true);

        const fireActionStub = sinon.stub(element, '_fireAction');
        element._handleDeleteEditTap();
        assert.isFalse(element.$.confirmDeleteEditDialog.hidden);
        tap(
          queryAndAssert(
            queryAndAssert(element, '#confirmDeleteEditDialog'),
            'gr-button[primary]'
          )
        );
        await flush();

        assert.equal(fireActionStub.lastCall.args[0], '/edit');
      });

      test('edit patchset is loaded, needs rebase', async () => {
        element.set('editMode', true);
        element.set('editPatchsetLoaded', true);
        element.change = {
          ...createChangeViewChange(),
          status: ChangeStatus.NEW,
        };
        element.editBasedOnCurrentPatchSet = false;
        await flush();

        assert.isNotOk(
          query(element, 'gr-button[data-action-key="publishEdit"]')
        );
        assert.isOk(query(element, 'gr-button[data-action-key="rebaseEdit"]'));
        assert.isOk(query(element, 'gr-button[data-action-key="deleteEdit"]'));
        assert.isNotOk(query(element, 'gr-button[data-action-key="edit"]'));
        assert.isNotOk(query(element, 'gr-button[data-action-key="stopEdit"]'));
      });

      test('edit patchset is loaded, does not need rebase', async () => {
        element.set('editMode', true);
        element.set('editPatchsetLoaded', true);
        element.change = {
          ...createChangeViewChange(),
          status: ChangeStatus.NEW,
        };
        element.editBasedOnCurrentPatchSet = true;
        await flush();

        assert.isOk(query(element, 'gr-button[data-action-key="publishEdit"]'));
        assert.isNotOk(
          query(element, 'gr-button[data-action-key="rebaseEdit"]')
        );
        assert.isOk(query(element, 'gr-button[data-action-key="deleteEdit"]'));
        assert.isNotOk(query(element, 'gr-button[data-action-key="edit"]'));
        assert.isNotOk(query(element, 'gr-button[data-action-key="stopEdit"]'));
      });

      test('edit mode is loaded, no edit patchset', async () => {
        element.set('editMode', true);
        element.set('editPatchsetLoaded', false);
        element.change = {
          ...createChangeViewChange(),
          status: ChangeStatus.NEW,
        };
        await flush();

        assert.isNotOk(
          query(element, 'gr-button[data-action-key="publishEdit"]')
        );
        assert.isNotOk(
          query(element, 'gr-button[data-action-key="rebaseEdit"]')
        );
        assert.isNotOk(
          query(element, 'gr-button[data-action-key="deleteEdit"]')
        );
        assert.isNotOk(query(element, 'gr-button[data-action-key="edit"]'));
        assert.isOk(query(element, 'gr-button[data-action-key="stopEdit"]'));
      });

      test('normal patch set', async () => {
        element.set('editMode', false);
        element.set('editPatchsetLoaded', false);
        element.change = {
          ...createChangeViewChange(),
          status: ChangeStatus.NEW,
        };
        await flush();

        assert.isNotOk(
          query(element, 'gr-button[data-action-key="publishEdit"]')
        );
        assert.isNotOk(
          query(element, 'gr-button[data-action-key="rebaseEdit"]')
        );
        assert.isNotOk(
          query(element, 'gr-button[data-action-key="deleteEdit"]')
        );
        assert.isOk(query(element, 'gr-button[data-action-key="edit"]'));
        assert.isNotOk(query(element, 'gr-button[data-action-key="stopEdit"]'));
      });

      test('edit action', async () => {
        const editTapped = mockPromise();
        element.addEventListener('edit-tap', () => {
          editTapped.resolve();
        });
        element.set('editMode', true);
        element.change = {
          ...createChangeViewChange(),
          status: ChangeStatus.NEW,
        };
        await flush();

        assert.isNotOk(query(element, 'gr-button[data-action-key="edit"]'));
        assert.isOk(query(element, 'gr-button[data-action-key="stopEdit"]'));
        element.change = {
          ...createChangeViewChange(),
          status: ChangeStatus.MERGED,
        };
        await flush();

        assert.isNotOk(query(element, 'gr-button[data-action-key="edit"]'));
        element.change = {
          ...createChangeViewChange(),
          status: ChangeStatus.NEW,
        };
        element.set('editMode', false);
        await flush();

        const editButton = queryAndAssert(
          element,
          'gr-button[data-action-key="edit"]'
        );
        tap(editButton);
        await editTapped;
      });
    });

    suite('cherry-pick', () => {
      let fireActionStub: sinon.SinonStub;

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
          method: HttpMethod.POST,
          title: 'Cherry pick change to a different branch',
        };

        element._handleCherrypickConfirm();
        assert.equal(fireActionStub.callCount, 0);

        element.$.confirmCherrypick.branch = 'master' as BranchName;
        element._handleCherrypickConfirm();
        assert.equal(fireActionStub.callCount, 0); // Still needs a message.

        // Add attributes that are used to determine the message.
        element.$.confirmCherrypick.commitMessage = 'foo message';
        element.$.confirmCherrypick.changeStatus = ChangeStatus.NEW;
        element.$.confirmCherrypick.commitNum = '123' as CommitId;

        element._handleCherrypickConfirm();

        const autogrowEl = queryAndAssert(
          element.$.confirmCherrypick,
          '#messageInput'
        ) as IronAutogrowTextareaElement;
        assert.equal(autogrowEl.value, 'foo message');

        assert.deepEqual(fireActionStub.lastCall.args, [
          '/cherrypick',
          action,
          true,
          {
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
          method: HttpMethod.POST,
          title: 'Cherry pick change to a different branch',
        };

        element.$.confirmCherrypick.branch = 'master' as BranchName;

        // Add attributes that are used to determine the message.
        element.$.confirmCherrypick.commitMessage = 'foo message';
        element.$.confirmCherrypick.changeStatus = ChangeStatus.NEW;
        element.$.confirmCherrypick.commitNum = '123' as CommitId;

        element._handleCherrypickConflictConfirm();

        assert.deepEqual(fireActionStub.lastCall.args, [
          '/cherrypick',
          action,
          true,
          {
            destination: 'master',
            base: null,
            message: 'foo message',
            allow_conflicts: true,
          },
        ]);
      });

      test('branch name cleared when re-open cherrypick', () => {
        const emptyBranchName = '';
        element.$.confirmCherrypick.branch = 'master' as BranchName;

        element._handleCherrypickTap();
        assert.equal(element.$.confirmCherrypick.branch, emptyBranchName);
      });

      suite('cherry pick topics', () => {
        const changes = [
          {
            ...createChangeViewChange(),
            change_id: '12345678901234' as ChangeId,
            topic: 'T' as TopicName,
            subject: 'random',
            project: 'A' as RepoName,
            status: ChangeStatus.MERGED,
          },
          {
            ...createChangeViewChange(),
            change_id: '23456' as ChangeId,
            topic: 'T' as TopicName,
            subject: 'a'.repeat(100),
            project: 'B' as RepoName,
            status: ChangeStatus.NEW,
          },
        ];
        setup(async () => {
          stubRestApi('getChanges').returns(Promise.resolve(changes));
          element._handleCherrypickTap();
          await flush();
          const radioButtons = queryAll(
            element.$.confirmCherrypick,
            "input[name='cherryPickOptions']"
          );
          assert.equal(radioButtons.length, 2);
          tap(radioButtons[1]);
          await flush();
        });

        test('cherry pick topic dialog is rendered', async () => {
          const dialog = element.$.confirmCherrypick;
          await flush();
          const changesTable = queryAndAssert(dialog, 'table');
          const headers = Array.from(changesTable.querySelectorAll('th'));
          const expectedHeadings = [
            '',
            'Change',
            'Status',
            'Subject',
            'Project',
            'Progress',
            '',
          ];
          const headings = headers.map(header => header.innerText);
          assert.equal(headings.length, expectedHeadings.length);
          for (let i = 0; i < headings.length; i++) {
            assert.equal(headings[i].trim(), expectedHeadings[i]);
          }
          const changeRows = queryAll(changesTable, 'tbody > tr');
          const change = Array.from(changeRows[0].querySelectorAll('td')).map(
            e => e.innerText
          );
          const expectedChange = [
            '',
            '1234567890',
            'MERGED',
            'random',
            'A',
            'NOT STARTED',
            '',
          ];
          for (let i = 0; i < change.length; i++) {
            assert.equal(change[i].trim(), expectedChange[i]);
          }
        });

        test('changes with duplicate project show an error', async () => {
          const dialog = element.$.confirmCherrypick;
          const error = queryAndAssert(
            dialog,
            '.error-message'
          ) as HTMLSpanElement;
          assert.equal(error.innerText, '');
          dialog.updateChanges([
            {
              ...createChangeViewChange(),
              change_id: '12345678901234' as ChangeId,
              topic: 'T' as TopicName,
              subject: 'random',
              project: 'A' as RepoName,
            },
            {
              ...createChangeViewChange(),
              change_id: '23456' as ChangeId,
              topic: 'T' as TopicName,
              subject: 'a'.repeat(100),
              project: 'A' as RepoName,
            },
          ]);
          await flush();
          assert.equal(
            error.innerText,
            'Two changes cannot be of the same' + ' project'
          );
        });
      });
    });

    suite('move change', () => {
      let fireActionStub: sinon.SinonStub;

      setup(() => {
        fireActionStub = sinon.stub(element, '_fireAction');
        sinon.stub(window, 'alert');
        element.actions = {
          move: {
            method: HttpMethod.POST,
            label: 'Move',
            title: 'Move the change',
            enabled: true,
          },
        };
      });

      test('works', () => {
        element._handleMoveTap();

        element._handleMoveConfirm();
        assert.equal(fireActionStub.callCount, 0);

        element.$.confirmMove.branch = 'master' as BranchName;
        element._handleMoveConfirm();
        assert.equal(fireActionStub.callCount, 1);
      });

      test('branch name cleared when re-open move', () => {
        const emptyBranchName = '';
        element.$.confirmMove.branch = 'master' as BranchName;

        element._handleMoveTap();
        assert.equal(element.$.confirmMove.branch, emptyBranchName);
      });
    });

    test('custom actions', async () => {
      // Add a button with the same key as a server-based one to ensure
      // collisions are taken care of.
      const key = element.addActionButton(element.ActionType.REVISION, 'Bork!');
      const keyTapped = mockPromise();
      element.addEventListener(key + '-tap', async e => {
        assert.equal(
          (e as CustomEvent).detail.node.getAttribute('data-action-key'),
          key
        );
        element.removeActionButton(key);
        await flush();
        assert.notOk(query(element, '[data-action-key="' + key + '"]'));
        keyTapped.resolve();
      });
      await flush();
      tap(queryAndAssert(element, '[data-action-key="' + key + '"]'));
      await keyTapped;
    });

    test('_setLoadingOnButtonWithKey top-level', () => {
      const key = 'rebase';
      const type = 'revision';
      const cleanup = element._setLoadingOnButtonWithKey(type, key);
      assert.equal(element._actionLoadingMessage, 'Rebasing...');

      const button = queryAndAssert(
        element,
        '[data-action-key="' + key + '"]'
      ) as GrButton;
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
      let alertStub: sinon.SinonStub;
      let fireActionStub: sinon.SinonStub;

      setup(() => {
        fireActionStub = sinon.stub(element, '_fireAction');
        alertStub = sinon.stub(window, 'alert');
        element.actions = {
          abandon: {
            method: HttpMethod.POST,
            label: 'Abandon',
            title: 'Abandon the change',
            enabled: true,
          },
        };
        return element.reload();
      });

      test('abandon change with message', async () => {
        const newAbandonMsg = 'Test Abandon Message';
        element.$.confirmAbandonDialog.message = newAbandonMsg;
        await flush();
        const abandonButton = queryAndAssert(
          element,
          'gr-button[data-action-key="abandon"]'
        );
        tap(abandonButton);

        assert.equal(element.$.confirmAbandonDialog.message, newAbandonMsg);
      });

      test('abandon change with no message', async () => {
        await flush();
        const abandonButton = queryAndAssert(
          element,
          'gr-button[data-action-key="abandon"]'
        );
        tap(abandonButton);

        assert.equal(element.$.confirmAbandonDialog.message, '');
      });

      test('works', () => {
        element.$.confirmAbandonDialog.message = 'original message';
        const restoreButton = queryAndAssert(
          element,
          'gr-button[data-action-key="abandon"]'
        );
        tap(restoreButton);

        element.$.confirmAbandonDialog.message = 'foo message';
        element._handleAbandonDialogConfirm();
        assert.notOk(alertStub.called);

        const action = {
          __key: 'abandon',
          __type: 'change',
          __primary: false,
          enabled: true,
          label: 'Abandon',
          method: HttpMethod.POST,
          title: 'Abandon the change',
        };
        assert.deepEqual(fireActionStub.lastCall.args, [
          '/abandon',
          action,
          false,
          {
            message: 'foo message',
          },
        ]);
      });
    });

    suite('revert change', () => {
      let fireActionStub: sinon.SinonStub;

      setup(() => {
        fireActionStub = sinon.stub(element, '_fireAction');
        element.commitMessage = 'random commit message';
        element.change!.current_revision = 'abcdef' as CommitId;
        element.actions = {
          revert: {
            method: HttpMethod.POST,
            label: 'Revert',
            title: 'Revert the change',
            enabled: true,
          },
        };
        return element.reload();
      });

      test('revert change with plugin hook', async () => {
        const newRevertMsg = 'Modified revert msg';
        sinon
          .stub(element.$.confirmRevertDialog, '_modifyRevertMsg')
          .callsFake(() => newRevertMsg);
        element.change = {
          ...createChangeViewChange(),
          current_revision: 'abc1234' as CommitId,
        };
        stubRestApi('getChanges').returns(
          Promise.resolve([
            {
              ...createChange(),
              change_id: '12345678901234' as ChangeId,
              topic: 'T' as TopicName,
              subject: 'random',
            },
            {
              ...createChange(),
              change_id: '23456' as ChangeId,
              topic: 'T' as TopicName,
              subject: 'a'.repeat(100),
            },
          ])
        );
        sinon
          .stub(
            element.$.confirmRevertDialog,
            '_populateRevertSubmissionMessage'
          )
          .callsFake(() => 'original msg');
        await flush();
        const revertButton = queryAndAssert(
          element,
          'gr-button[data-action-key="revert"]'
        );
        tap(revertButton);
        await flush();
        assert.equal(element.$.confirmRevertDialog._message, newRevertMsg);
      });

      suite('revert change submitted together', () => {
        let getChangesStub: sinon.SinonStub;
        setup(() => {
          element.change = {
            ...createChangeViewChange(),
            submission_id: '199 0' as ChangeSubmissionId,
            current_revision: '2000' as CommitId,
          };
          getChangesStub = stubRestApi('getChanges').returns(
            Promise.resolve([
              {
                ...createChange(),
                change_id: '12345678901234' as ChangeId,
                topic: 'T' as TopicName,
                subject: 'random',
              },
              {
                ...createChange(),
                change_id: '23456' as ChangeId,
                topic: 'T' as TopicName,
                subject: 'a'.repeat(100),
              },
            ])
          );
        });

        test('confirm revert dialog shows both options', async () => {
          const revertButton = queryAndAssert(
            element,
            'gr-button[data-action-key="revert"]'
          );
          tap(revertButton);
          await flush();
          assert.equal(getChangesStub.args[0][1], 'submissionid: "199 0"');
          const confirmRevertDialog = element.$.confirmRevertDialog;
          const revertSingleChangeLabel = queryAndAssert(
            confirmRevertDialog,
            '.revertSingleChange'
          ) as HTMLLabelElement;
          const revertSubmissionLabel = queryAndAssert(
            confirmRevertDialog,
            '.revertSubmission'
          ) as HTMLLabelElement;
          assert(
            revertSingleChangeLabel.innerText.trim() === 'Revert single change'
          );
          assert(
            revertSubmissionLabel.innerText.trim() ===
              'Revert entire submission (2 Changes)'
          );
          let expectedMsg =
            'Revert submission 199 0' +
            '\n\n' +
            'Reason for revert: <INSERT REASONING HERE>' +
            '\n' +
            'Reverted Changes:' +
            '\n' +
            '1234567890:random' +
            '\n' +
            '23456:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa...' +
            '\n';
          assert.equal(confirmRevertDialog._message, expectedMsg);
          const radioInputs = queryAll(
            confirmRevertDialog,
            'input[name="revertOptions"]'
          );
          tap(radioInputs[0]);
          await flush();
          expectedMsg =
            'Revert "random commit message"\n\nThis reverts ' +
            'commit 2000.\n\nReason' +
            ' for revert: <INSERT REASONING HERE>\n';
          assert.equal(confirmRevertDialog._message, expectedMsg);
        });

        test('submit fails if message is not edited', async () => {
          const revertButton = queryAndAssert(
            element,
            'gr-button[data-action-key="revert"]'
          );
          const confirmRevertDialog = element.$.confirmRevertDialog;
          tap(revertButton);
          const fireStub = sinon.stub(confirmRevertDialog, 'dispatchEvent');
          await flush();
          const confirmButton = queryAndAssert(
            queryAndAssert(element.$.confirmRevertDialog, 'gr-dialog'),
            '#confirm'
          );
          tap(confirmButton);
          await flush();
          assert.isTrue(confirmRevertDialog._showErrorMessage);
          assert.isFalse(fireStub.called);
        });

        test('message modification is retained on switching', async () => {
          const revertButton = queryAndAssert(
            element,
            'gr-button[data-action-key="revert"]'
          );
          const confirmRevertDialog = element.$.confirmRevertDialog;
          tap(revertButton);
          await flush();
          const radioInputs = queryAll(
            confirmRevertDialog,
            'input[name="revertOptions"]'
          );
          const revertSubmissionMsg =
            'Revert submission 199 0' +
            '\n\n' +
            'Reason for revert: <INSERT REASONING HERE>' +
            '\n' +
            'Reverted Changes:' +
            '\n' +
            '1234567890:random' +
            '\n' +
            '23456:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa...' +
            '\n';
          const singleChangeMsg =
            'Revert "random commit message"\n\nThis reverts ' +
            'commit 2000.\n\nReason' +
            ' for revert: <INSERT REASONING HERE>\n';
          assert.equal(confirmRevertDialog._message, revertSubmissionMsg);
          const newRevertMsg = revertSubmissionMsg + 'random';
          const newSingleChangeMsg = singleChangeMsg + 'random';
          confirmRevertDialog._message = newRevertMsg;
          tap(radioInputs[0]);
          await flush();
          assert.equal(confirmRevertDialog._message, singleChangeMsg);
          confirmRevertDialog._message = newSingleChangeMsg;
          tap(radioInputs[1]);
          await flush();
          assert.equal(confirmRevertDialog._message, newRevertMsg);
          tap(radioInputs[0]);
          await flush();
          assert.equal(confirmRevertDialog._message, newSingleChangeMsg);
        });
      });

      suite('revert single change', () => {
        setup(() => {
          element.change = {
            ...createChangeViewChange(),
            submission_id: '199' as ChangeSubmissionId,
            current_revision: '2000' as CommitId,
          };
          stubRestApi('getChanges').returns(
            Promise.resolve([
              {
                ...createChange(),
                change_id: '12345678901234' as ChangeId,
                topic: 'T' as TopicName,
                subject: 'random',
              },
            ])
          );
        });

        test('submit fails if message is not edited', async () => {
          const revertButton = queryAndAssert(
            element,
            'gr-button[data-action-key="revert"]'
          );
          const confirmRevertDialog = element.$.confirmRevertDialog;
          tap(revertButton);
          const fireStub = sinon.stub(confirmRevertDialog, 'dispatchEvent');
          await flush();
          const confirmButton = queryAndAssert(
            queryAndAssert(element.$.confirmRevertDialog, 'gr-dialog'),
            '#confirm'
          );
          tap(confirmButton);
          await flush();
          assert.isTrue(confirmRevertDialog._showErrorMessage);
          assert.isFalse(fireStub.called);
        });

        test('confirm revert dialog shows no radio button', async () => {
          const revertButton = queryAndAssert(
            element,
            'gr-button[data-action-key="revert"]'
          );
          tap(revertButton);
          await flush();
          const confirmRevertDialog = element.$.confirmRevertDialog;
          const radioInputs = queryAll(
            confirmRevertDialog,
            'input[name="revertOptions"]'
          );
          assert.equal(radioInputs.length, 0);
          const msg =
            'Revert "random commit message"\n\n' +
            'This reverts commit 2000.\n\nReason ' +
            'for revert: <INSERT REASONING HERE>\n';
          assert.equal(confirmRevertDialog._message, msg);
          const editedMsg = msg + 'hello';
          confirmRevertDialog._message += 'hello';
          const confirmButton = queryAndAssert(
            queryAndAssert(element.$.confirmRevertDialog, 'gr-dialog'),
            '#confirm'
          );
          tap(confirmButton);
          await flush();
          assert.equal(fireActionStub.getCall(0).args[0], '/revert');
          assert.equal(fireActionStub.getCall(0).args[1].__key, 'revert');
          assert.equal(fireActionStub.getCall(0).args[3].message, editedMsg);
        });
      });
    });

    suite('mark change private', () => {
      setup(() => {
        const privateAction = {
          __key: 'private',
          __type: 'change',
          __primary: false,
          method: HttpMethod.POST,
          label: 'Mark private',
          title: 'Working...',
          enabled: true,
        };

        element.actions = {
          private: privateAction,
        };

        element.change!.is_private = false;

        element.changeNum = 2 as NumericChangeId;
        element.latestPatchNum = 2 as PatchSetNum;

        return element.reload();
      });

      test(
        'make sure the mark private change button is not outside of the ' +
          'overflow menu',
        async () => {
          await flush();
          assert.isNotOk(query(element, '[data-action-key="private"]'));
        }
      );

      test('private change', async () => {
        await flush();
        assert.isOk(
          query(element.$.moreActions, 'span[data-id="private-change"]')
        );
        element.setActionOverflow(ActionType.CHANGE, 'private', false);
        await flush();
        assert.isOk(query(element, '[data-action-key="private"]'));
        assert.isNotOk(
          query(element.$.moreActions, 'span[data-id="private-change"]')
        );
      });
    });

    suite('unmark private change', () => {
      setup(() => {
        const unmarkPrivateAction = {
          __key: 'private.delete',
          __type: 'change',
          __primary: false,
          method: HttpMethod.POST,
          label: 'Unmark private',
          title: 'Working...',
          enabled: true,
        };

        element.actions = {
          'private.delete': unmarkPrivateAction,
        };

        element.change!.is_private = true;

        element.changeNum = 2 as NumericChangeId;
        element.latestPatchNum = 2 as PatchSetNum;

        return element.reload();
      });

      test(
        'make sure the unmark private change button is not outside of the ' +
          'overflow menu',
        async () => {
          await flush();
          assert.isNotOk(query(element, '[data-action-key="private.delete"]'));
        }
      );

      test('unmark the private change', async () => {
        await flush();
        assert.isOk(
          query(element.$.moreActions, 'span[data-id="private.delete-change"]')
        );
        element.setActionOverflow(ActionType.CHANGE, 'private.delete', false);
        await flush();
        assert.isOk(query(element, '[data-action-key="private.delete"]'));
        assert.isNotOk(
          query(element.$.moreActions, 'span[data-id="private.delete-change"]')
        );
      });
    });

    suite('delete change', () => {
      let fireActionStub: sinon.SinonStub;
      let deleteAction: ActionInfo;

      setup(() => {
        fireActionStub = sinon.stub(element, '_fireAction');
        element.change = {
          ...createChangeViewChange(),
          current_revision: 'abc1234' as CommitId,
        };
        deleteAction = {
          method: HttpMethod.DELETE,
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

      test('shows confirm dialog', async () => {
        element._handleDeleteTap();
        assert.isFalse(
          (queryAndAssert(element, '#confirmDeleteDialog') as GrDialog).hidden
        );
        tap(
          queryAndAssert(
            queryAndAssert(element, '#confirmDeleteDialog'),
            'gr-button[primary]'
          )
        );
        await flush();
        assert.isTrue(fireActionStub.calledWith('/', deleteAction, false));
      });

      test('hides delete confirm on cancel', async () => {
        element._handleDeleteTap();
        tap(
          queryAndAssert(
            queryAndAssert(element, '#confirmDeleteDialog'),
            'gr-button:not([primary])'
          )
        );
        await flush();
        assert.isTrue(
          (queryAndAssert(element, '#confirmDeleteDialog') as GrDialog).hidden
        );
        assert.isFalse(fireActionStub.called);
      });
    });

    suite('quick approve', () => {
      setup(async () => {
        element.change = {
          ...createChangeViewChange(),
          current_revision: 'abc1234' as CommitId,
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
        await flush();
      });

      test('added when can approve', () => {
        const approveButton = queryAndAssert(
          element,
          "gr-button[data-action-key='review']"
        );
        assert.isNotNull(approveButton);
      });

      test('hide quick approve', async () => {
        const approveButton = query(
          element,
          "gr-button[data-action-key='review']"
        );
        assert.isNotNull(approveButton);
        assert.isFalse(element._hideQuickApproveAction);

        // Assert approve button gets removed from list of buttons.
        element.hideQuickApproveAction();
        await flush();
        const approveButtonUpdated = query(
          element,
          "gr-button[data-action-key='review']"
        );
        assert.isNotOk(approveButtonUpdated);
        assert.isTrue(element._hideQuickApproveAction);
      });

      test('is first in list of secondary actions', () => {
        const approveButton =
          element.$.secondaryActions.querySelector('gr-button');
        assert.equal(approveButton!.getAttribute('data-label'), 'foo+1');
      });

      test('not added when change is merged', async () => {
        element.change = {
          ...element.change!,
          status: ChangeStatus.MERGED,
        };

        await flush();
        const approveButton = query(
          element,
          "gr-button[data-action-key='review']"
        );
        assert.isNotOk(approveButton);
      });

      test('not added when already approved', async () => {
        element.change = {
          ...createChangeViewChange(),
          current_revision: 'abc1234' as CommitId,
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
        await flush();
        const approveButton = query(
          element,
          "gr-button[data-action-key='review']"
        );
        assert.isNotOk(approveButton);
      });

      test('not added when label not permitted', async () => {
        element.change = {
          ...createChangeViewChange(),
          current_revision: 'abc1234' as CommitId,
          labels: {
            foo: {values: {}},
          },
          permitted_labels: {
            bar: [],
          },
        };
        await flush();
        const approveButton = query(
          element,
          "gr-button[data-action-key='review']"
        );
        assert.isNotOk(approveButton);
      });

      test('approves when tapped', async () => {
        const fireActionStub = sinon.stub(element, '_fireAction');
        tap(queryAndAssert(element, "gr-button[data-action-key='review']"));
        await flush();
        assert.isTrue(fireActionStub.called);
        assert.isTrue(fireActionStub.calledWith('/review'));
        const payload = fireActionStub.lastCall.args[3];
        assert.deepEqual((payload as ReviewInput).labels, {foo: 1});
      });

      test('not added when multiple labels are required without code review', async () => {
        element.change = {
          ...createChangeViewChange(),
          current_revision: 'abc1234' as CommitId,
          labels: {
            foo: {values: {}},
            bar: {values: {}},
          },
          permitted_labels: {
            foo: [' 0', '+1'],
            bar: [' 0', '+1', '+2'],
          },
        };
        await flush();
        const approveButton = query(
          element,
          "gr-button[data-action-key='review']"
        );
        assert.isNotOk(approveButton);
      });

      test('code review shown with multiple missing approval', async () => {
        element.change = {
          ...createChangeViewChange(),
          current_revision: 'abc1234' as CommitId,
          labels: {
            foo: {values: {}},
            bar: {values: {}},
            'Code-Review': {
              approved: {},
              values: {
                ' 0': '',
                '+1': '',
                '+2': '',
              },
            },
          },
          permitted_labels: {
            foo: [' 0', '+1'],
            bar: [' 0', '+1', '+2'],
            'Code-Review': [' 0', '+1', '+2'],
          },
        };
        await flush();
        const approveButton = queryAndAssert(
          element,
          "gr-button[data-action-key='review']"
        );
        assert.isOk(approveButton);
      });

      test('button label for missing approval', async () => {
        element.change = {
          ...createChangeViewChange(),
          current_revision: 'abc1234' as CommitId,
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
        await flush();
        const approveButton = queryAndAssert(
          element,
          "gr-button[data-action-key='review']"
        );
        assert.equal(approveButton.getAttribute('data-label'), 'foo+1');
      });

      test('no quick approve if score is not maximal for a label', async () => {
        element.change = {
          ...createChangeViewChange(),
          current_revision: 'abc1234' as CommitId,
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
        await flush();
        const approveButton = query(
          element,
          "gr-button[data-action-key='review']"
        );
        assert.isNotOk(approveButton);
      });

      test('approving label with a non-max score', async () => {
        element.change = {
          ...createChangeViewChange(),
          current_revision: 'abc1234' as CommitId,
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
        await flush();
        const approveButton = queryAndAssert(
          element,
          "gr-button[data-action-key='review']"
        );
        assert.equal(approveButton.getAttribute('data-label'), 'bar+2');
      });

      test('added when can approve an already-approved code review label', async () => {
        element.change = {
          ...createChangeViewChange(),
          current_revision: 'abc1234' as CommitId,
          labels: {
            'Code-Review': {
              approved: {},
              values: {
                ' 0': '',
                '+1': '',
                '+2': '',
              },
            },
          },
          permitted_labels: {
            'Code-Review': [' 0', '+1', '+2'],
          },
        };
        await flush();
        const approveButton = queryAndAssert(
          element,
          "gr-button[data-action-key='review']"
        );
        assert.isNotNull(approveButton);
      });

      test('not added when the user has already approved', async () => {
        const vote = {
          ...createApproval(),
          _account_id: 123 as AccountId,
          name: 'name',
          value: 2,
        };
        element.change = {
          ...createChangeViewChange(),
          current_revision: 'abc1234' as CommitId,
          labels: {
            'Code-Review': {
              approved: {},
              values: {
                ' 0': '',
                '+1': '',
                '+2': '',
              },
              all: [vote],
            },
          },
          permitted_labels: {
            'Code-Review': [' 0', '+1', '+2'],
          },
        };
        await flush();
        const approveButton = query(
          element,
          "gr-button[data-action-key='review']"
        );
        assert.isNotOk(approveButton);
      });

      test('not added when user owns the change', async () => {
        element.change = {
          ...createChangeViewChange(),
          current_revision: 'abc1234' as CommitId,
          owner: createAccountWithId(123),
          labels: {
            'Code-Review': {
              approved: {},
              values: {
                ' 0': '',
                '+1': '',
                '+2': '',
              },
            },
          },
          permitted_labels: {
            'Code-Review': [' 0', '+1', '+2'],
          },
        };
        await flush();
        const approveButton = query(
          element,
          "gr-button[data-action-key='review']"
        );
        assert.isNotOk(approveButton);
      });
    });

    test('adds download revision action', async () => {
      const handler = sinon.stub();
      element.addEventListener('download-tap', handler);
      assert.ok(element.revisionActions.download);
      element._handleDownloadTap();
      await flush();

      assert.isTrue(handler.called);
    });

    test('changing changeNum or patchNum does not reload', () => {
      const reloadStub = sinon.stub(element, 'reload');
      element.changeNum = 123 as NumericChangeId;
      assert.isFalse(reloadStub.called);
      element.latestPatchNum = 456 as PatchSetNum;
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
      test('move action from overflow', async () => {
        assert.isNotOk(query(element, '[data-action-key="cherrypick"]'));
        assert.strictEqual(
          element.$.moreActions!.items![0].id,
          'cherrypick-revision'
        );
        element.setActionOverflow(ActionType.REVISION, 'cherrypick', false);
        await flush();
        assert.isOk(query(element, '[data-action-key="cherrypick"]'));
        assert.notEqual(
          element.$.moreActions!.items![0].id,
          'cherrypick-revision'
        );
      });

      test('move action to overflow', async () => {
        assert.isOk(query(element, '[data-action-key="submit"]'));
        element.setActionOverflow(ActionType.REVISION, 'submit', true);
        await flush();
        assert.isNotOk(query(element, '[data-action-key="submit"]'));
        assert.strictEqual(
          element.$.moreActions.items![3].id,
          'submit-revision'
        );
      });

      suite('_waitForChangeReachable', () => {
        let clock: SinonFakeTimers;
        setup(() => {
          clock = sinon.useFakeTimers();
        });

        const makeGetChange = (numTries: number) => () => {
          if (numTries === 1) {
            return Promise.resolve({
              ...createChangeViewChange(),
              _number: 123 as NumericChangeId,
            });
          } else {
            numTries--;
            return Promise.resolve(null);
          }
        };

        const tickAndFlush = async (repetitions: number) => {
          for (let i = 1; i <= repetitions; i++) {
            clock.tick(1000);
            await flush();
          }
        };

        test('succeed', async () => {
          stubRestApi('getChange').callsFake(makeGetChange(5));
          const promise = element._waitForChangeReachable(
            123 as NumericChangeId
          );
          tickAndFlush(5);
          const success = await promise;
          assert.isTrue(success);
        });

        test('fail', async () => {
          stubRestApi('getChange').callsFake(makeGetChange(6));
          const promise = element._waitForChangeReachable(
            123 as NumericChangeId
          );
          tickAndFlush(6);
          const success = await promise;
          assert.isFalse(success);
        });
      });
    });

    suite('_send', () => {
      let cleanup: sinon.SinonStub;
      const payload = {foo: 'bar'};
      let onShowError: sinon.SinonStub;
      let onShowAlert: sinon.SinonStub;
      let getResponseObjectStub: sinon.SinonStub;

      setup(() => {
        cleanup = sinon.stub();
        element.changeNum = 42 as NumericChangeId;
        element.latestPatchNum = 12 as PatchSetNum;
        element.change = {
          ...createChangeViewChange(),
          revisions: createRevisions(element.latestPatchNum as number),
          messages: createChangeMessages(1),
        };
        element.change!._number = 42 as NumericChangeId;

        onShowError = sinon.stub();
        element.addEventListener('show-error', onShowError);
        onShowAlert = sinon.stub();
        element.addEventListener('show-alert', onShowAlert);
      });

      suite('happy path', () => {
        let sendStub: sinon.SinonStub;
        setup(() => {
          stubRestApi('getChangeDetail').returns(
            Promise.resolve({
              ...createChangeViewChange(),
              // element has latest info
              revisions: createRevisions(element.latestPatchNum as number),
              messages: createChangeMessages(1),
            })
          );
          getResponseObjectStub = stubRestApi('getResponseObject');
          sendStub = stubRestApi('executeChangeAction').returns(
            Promise.resolve(new Response())
          );
          sinon.stub(GerritNav, 'navigateToChange');
        });

        test('change action', async () => {
          await element._send(
            HttpMethod.DELETE,
            payload,
            '/endpoint',
            false,
            cleanup,
            {} as UIActionInfo
          );
          assert.isFalse(onShowError.called);
          assert.isTrue(cleanup.calledOnce);
          assert.isTrue(
            sendStub.calledWith(
              42,
              HttpMethod.DELETE,
              '/endpoint',
              undefined,
              payload
            )
          );
        });

        suite('show revert submission dialog', () => {
          setup(() => {
            element.change!.submission_id = '199' as ChangeSubmissionId;
            element.change!.current_revision = '2000' as CommitId;
            stubRestApi('getChanges').returns(
              Promise.resolve([
                {
                  ...createChangeViewChange(),
                  change_id: '12345678901234' as ChangeId,
                  topic: 'T' as TopicName,
                  subject: 'random',
                },
                {
                  ...createChangeViewChange(),
                  change_id: '23456' as ChangeId,
                  topic: 'T' as TopicName,
                  subject: 'a'.repeat(100),
                },
              ])
            );
          });
        });

        suite('single changes revert', () => {
          let navigateToSearchQueryStub: sinon.SinonStub;
          setup(() => {
            getResponseObjectStub.returns(
              Promise.resolve({revert_changes: [{change_id: 12345}]})
            );
            navigateToSearchQueryStub = sinon.stub(
              GerritNav,
              'navigateToSearchQuery'
            );
          });

          test('revert submission single change', async () => {
            await element._send(
              HttpMethod.POST,
              {message: 'Revert submission'},
              '/revert_submission',
              false,
              cleanup,
              {} as UIActionInfo
            );
            await element._handleResponse(
              {
                __key: 'revert_submission',
                __type: ActionType.CHANGE,
                label: 'l',
              },
              new Response()
            );
            assert.isTrue(navigateToSearchQueryStub.called);
          });
        });

        suite('multiple changes revert', () => {
          let showActionDialogStub: sinon.SinonStub;
          let navigateToSearchQueryStub: sinon.SinonStub;
          setup(() => {
            getResponseObjectStub.returns(
              Promise.resolve({
                revert_changes: [
                  {change_id: 12345, topic: 'T'},
                  {change_id: 23456, topic: 'T'},
                ],
              })
            );
            showActionDialogStub = sinon.stub(element, '_showActionDialog');
            navigateToSearchQueryStub = sinon.stub(
              GerritNav,
              'navigateToSearchQuery'
            );
          });

          test('revert submission multiple change', async () => {
            await element._send(
              HttpMethod.POST,
              {message: 'Revert submission'},
              '/revert_submission',
              false,
              cleanup,
              {} as UIActionInfo
            );
            await element._handleResponse(
              {
                __key: 'revert_submission',
                __type: ActionType.CHANGE,
                label: 'l',
              },
              new Response()
            );
            assert.isFalse(showActionDialogStub.called);
            assert.isTrue(navigateToSearchQueryStub.calledWith('topic: T'));
          });
        });

        test('revision action', async () => {
          await element._send(
            HttpMethod.DELETE,
            payload,
            '/endpoint',
            true,
            cleanup,
            {} as UIActionInfo
          );
          assert.isFalse(onShowError.called);
          assert.isTrue(cleanup.calledOnce);
          assert.isTrue(
            sendStub.calledWith(42, 'DELETE', '/endpoint', 12, payload)
          );
        });
      });

      suite('failure modes', () => {
        test('non-latest', () => {
          stubRestApi('getChangeDetail').returns(
            Promise.resolve({
              ...createChangeViewChange(),
              // new patchset was uploaded
              revisions: createRevisions(
                (element.latestPatchNum as number) + 1
              ),
              messages: createChangeMessages(1),
            })
          );
          const sendStub = stubRestApi('executeChangeAction');

          return element
            ._send(
              HttpMethod.DELETE,
              payload,
              '/endpoint',
              true,
              cleanup,
              {} as UIActionInfo
            )
            .then(() => {
              assert.isTrue(onShowAlert.calledOnce);
              assert.isFalse(onShowError.called);
              assert.isTrue(cleanup.calledOnce);
              assert.isFalse(sendStub.called);
            });
        });

        test('send fails', () => {
          stubRestApi('getChangeDetail').returns(
            Promise.resolve({
              ...createChangeViewChange(),
              // element has latest info
              revisions: createRevisions(element.latestPatchNum as number),
              messages: createChangeMessages(1),
            })
          );
          const sendStub = stubRestApi('executeChangeAction').callsFake(
            (_num, _method, _patchNum, _endpoint, _payload, onErr) => {
              onErr!();
              return Promise.resolve(undefined);
            }
          );
          const handleErrorStub = sinon.stub(element, '_handleResponseError');

          return element
            ._send(
              HttpMethod.DELETE,
              payload,
              '/endpoint',
              true,
              cleanup,
              {} as UIActionInfo
            )
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
      sinon.stub(element, '_handleChangeAction');

      const reportStub = stubReporting('reportInteraction');
      element._handleAction(ActionType.CHANGE, 'key');
      assert.isTrue(reportStub.called);
      assert.equal(reportStub.lastCall.args[0], 'change-key');
    });
  });

  suite('getChangeRevisionActions returns only some actions', () => {
    let element: GrChangeActions;

    let changeRevisionActions: ActionNameToActionInfoMap = {};

    setup(() => {
      stubRestApi('getChangeRevisionActions').returns(
        Promise.resolve(changeRevisionActions)
      );
      stubRestApi('send').returns(Promise.reject(new Error('error')));

      sinon
        .stub(getPluginLoader(), 'awaitPluginsLoaded')
        .returns(Promise.resolve());

      element = basicFixture.instantiate();
      // getChangeRevisionActions is not called without
      // set the following properties
      element.change = createChangeViewChange();
      element.changeNum = 42 as NumericChangeId;
      element.latestPatchNum = 2 as PatchSetNum;

      stubRestApi('getRepoBranches').returns(Promise.resolve([]));
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
        method: HttpMethod.POST,
        title: 'Rebase onto tip of branch or parent change',
      };

      // When rebase is enabled initially, rebaseOnCurrent should be set to
      // true.
      assert.isTrue(element._computeRebaseOnCurrent(rebaseAction));

      rebaseAction.enabled = false;

      // When rebase is not enabled initially, rebaseOnCurrent should be set to
      // false.
      assert.isFalse(element._computeRebaseOnCurrent(rebaseAction));
    });
  });
});
