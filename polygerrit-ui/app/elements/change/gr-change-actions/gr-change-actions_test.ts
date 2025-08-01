/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import * as sinon from 'sinon';
import '../../../test/common-test-setup';
import './gr-change-actions';
import {navigationToken} from '../../core/gr-navigation/gr-navigation';
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
  makePrefixedJSON,
  mockPromise,
  query,
  queryAll,
  queryAndAssert,
  stubReporting,
  stubRestApi,
  waitUntil,
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
  PatchSetNumber,
  RepoName,
  ReviewInput,
  TopicName,
  ValidationOptionsInfo,
} from '../../../types/common';
import {ActionType, RevisionActions} from '../../../api/change-actions';
import {SinonFakeTimers, SinonStubbedMember} from 'sinon';
import {GrButton} from '../../shared/gr-button/gr-button';
import {GrDialog} from '../../shared/gr-dialog/gr-dialog';
import {UIActionInfo} from '../../shared/gr-js-api-interface/gr-change-actions-js-api';
import {assert, fixture, html} from '@open-wc/testing';
import {GrConfirmCherrypickDialog} from '../gr-confirm-cherrypick-dialog/gr-confirm-cherrypick-dialog';
import {GrDropdown} from '../../shared/gr-dropdown/gr-dropdown';
import {GrConfirmSubmitDialog} from '../gr-confirm-submit-dialog/gr-confirm-submit-dialog';
import {GrConfirmRebaseDialog} from '../gr-confirm-rebase-dialog/gr-confirm-rebase-dialog';
import {GrConfirmMoveDialog} from '../gr-confirm-move-dialog/gr-confirm-move-dialog';
import {GrConfirmAbandonDialog} from '../gr-confirm-abandon-dialog/gr-confirm-abandon-dialog';
import {
  GrConfirmRevertDialog,
  RevertType,
} from '../gr-confirm-revert-dialog/gr-confirm-revert-dialog';
import {testResolver} from '../../../test/common-test-setup';
import {storageServiceToken} from '../../../services/storage/gr-storage_impl';
import {pluginLoaderToken} from '../../shared/gr-js-api-interface/gr-plugin-loader';
import {
  ChangeModel,
  changeModelToken,
} from '../../../models/change/change-model';
import {assertIsDefined} from '../../../utils/common-util';
import {GrAutogrowTextarea} from '../../shared/gr-autogrow-textarea/gr-autogrow-textarea';

// TODO(dhruvsri): remove use of _populateRevertMessage as it's private
suite('gr-change-actions tests', () => {
  let element: GrChangeActions;
  let navigateResetStub: SinonStubbedMember<
    ChangeModel['navigateToChangeResetReload']
  >;

  suite('basic tests', () => {
    setup(async () => {
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

      stubRestApi('getValidationOptions').returns(
        Promise.resolve({
          validation_options: [{name: 'o1', description: 'option 1'}],
        } as ValidationOptionsInfo)
      );

      sinon
        .stub(testResolver(pluginLoaderToken), 'awaitPluginsLoaded')
        .returns(Promise.resolve());

      element = await fixture<GrChangeActions>(html`
        <gr-change-actions></gr-change-actions>
      `);
      element.changeStatus = ChangeStatus.NEW;
      element.change = {
        ...createChangeViewChange(),
        actions: {
          '/': {
            method: HttpMethod.DELETE,
            label: 'Delete Change',
            title: 'Delete change X_X',
            enabled: true,
          },
        },
      };
      element.changeNum = 42 as NumericChangeId;
      element.mergeable = false;
      element.latestPatchNum = 2 as PatchSetNumber;
      element.account = {
        _account_id: 123 as AccountId,
      };
      stubRestApi('getRepoBranches').returns(Promise.resolve([]));
      navigateResetStub = sinon.stub(
        testResolver(changeModelToken),
        'navigateToChangeResetReload'
      );

      await element.updateComplete;
      await element.reload();
    });

    test('render', async () => {
      assert.shadowDom.equal(
        element,
        /* HTML */ `
          <div id="mainContent">
            <span hidden="" id="actionLoadingMessage"> </span>
            <section id="primaryActions">
              <gr-tooltip-content
                has-tooltip=""
                position-below=""
                title="Submit patch set 2 into master"
              >
                <gr-button
                  aria-disabled="false"
                  class="submit"
                  data-action-key="submit"
                  data-label="Submit"
                  link=""
                  role="button"
                  tabindex="0"
                >
                  <gr-icon icon="done_all"></gr-icon>
                  Submit
                </gr-button>
              </gr-tooltip-content>
            </section>
            <section id="secondaryActions">
              <gr-tooltip-content
                has-tooltip=""
                position-below=""
                title="Rebase onto tip of branch or parent change"
              >
                <gr-button
                  aria-disabled="false"
                  class="rebase"
                  data-action-key="rebase"
                  data-label="Rebase"
                  link=""
                  role="button"
                  tabindex="0"
                >
                  <gr-icon icon="rebase"> </gr-icon>
                  Rebase
                </gr-button>
              </gr-tooltip-content>
              <gr-tooltip-content
                has-tooltip=""
                position-below=""
                title="Edit this change"
              >
                <gr-button
                  aria-disabled="false"
                  class="edit"
                  data-action-key="edit"
                  data-label="Edit"
                  link=""
                  role="button"
                  tabindex="0"
                >
                  <gr-icon filled="" icon="edit"> </gr-icon>
                  Edit
                </gr-button>
              </gr-tooltip-content>
            </section>
            <gr-button
              aria-disabled="false"
              hidden=""
              role="button"
              tabindex="0"
            >
              Loading actions...
            </gr-button>
            <gr-dropdown id="moreActions" link="">
              <gr-icon icon="more_vert" aria-labelledby="moreMessage"></gr-icon>
              <span id="moreMessage"> More </span>
            </gr-dropdown>
          </div>
          <dialog id="actionsModal" tabindex="-1">
            <gr-confirm-rebase-dialog class="confirmDialog" id="confirmRebase">
            </gr-confirm-rebase-dialog>
            <gr-confirm-cherrypick-dialog
              class="confirmDialog"
              id="confirmCherrypick"
            >
            </gr-confirm-cherrypick-dialog>
            <gr-confirm-cherrypick-conflict-dialog
              class="confirmDialog"
              id="confirmCherrypickConflict"
            >
            </gr-confirm-cherrypick-conflict-dialog>
            <gr-confirm-move-dialog class="confirmDialog" id="confirmMove">
            </gr-confirm-move-dialog>
            <gr-confirm-revert-dialog
              class="confirmDialog"
              id="confirmRevertDialog"
            >
            </gr-confirm-revert-dialog>
            <gr-confirm-abandon-dialog
              class="confirmDialog"
              id="confirmAbandonDialog"
            >
            </gr-confirm-abandon-dialog>
            <gr-confirm-submit-dialog
              class="confirmDialog"
              id="confirmSubmitDialog"
            >
            </gr-confirm-submit-dialog>
            <gr-dialog
              class="confirmDialog"
              confirm-label="Create"
              id="createFollowUpDialog"
              role="dialog"
            >
              <div class="header" slot="header">Create Follow-Up Change</div>
              <div class="main" slot="main">
                <gr-create-change-dialog id="createFollowUpChange">
                </gr-create-change-dialog>
              </div>
            </gr-dialog>
            <gr-dialog
              class="confirmDialog"
              confirm-label="Delete"
              confirm-on-enter=""
              id="confirmDeleteDialog"
              role="dialog"
            >
              <div class="header" slot="header">Delete Change</div>
              <div class="main" slot="main">
                Do you really want to delete the change?
              </div>
            </gr-dialog>
            <gr-dialog
              class="confirmDialog"
              confirm-label="Delete"
              confirm-on-enter=""
              id="confirmDeleteEditDialog"
              role="dialog"
            >
              <div class="header" slot="header">Delete Change Edit</div>
              <div class="main" slot="main">
                Do you really want to delete the edit?
              </div>
            </gr-dialog>
            <gr-dialog
              class="confirmDialog"
              confirm-label="Publish"
              confirm-on-enter=""
              id="confirmPublishEditDialog"
              role="dialog"
            >
              <div class="header" slot="header">Publish Change Edit</div>
              <div class="main" slot="main">
                Do you really want to publish the edit?
              </div>
            </gr-dialog>
          </dialog>
        `
      );
    });

    suite('isLoading', () => {
      const isLoading = async () => {
        await element.updateComplete;
        const loadingButton = queryAndAssert(
          element,
          'div#mainContent > gr-button'
        );
        assert.equal(loadingButton.textContent, 'Loading actions...');
        return loadingButton.getAttribute('hidden') === null;
      };

      test('change', async () => {
        element.change = undefined;
        await element.updateComplete;
        assert.shadowDom.equal(element, '');
      });

      test('mergeable', async () => {
        element.mergeable = undefined;
        assert.isTrue(await isLoading());

        element.mergeable = true;
        assert.isFalse(await isLoading());
      });

      test('pluginsLoaded', async () => {
        element.pluginsLoaded = false;
        assert.isTrue(await isLoading());

        element.pluginsLoaded = true;
        assert.isFalse(await isLoading());
      });

      test('revisionActions', async () => {
        element.revisionActions = undefined;
        assert.isTrue(await isLoading());

        element.revisionActions = {};
        assert.isFalse(await isLoading());
      });
    });

    test('show-revision-actions event should fire', async () => {
      const spy = sinon.spy(element, 'sendShowRevisionActions');
      element.reload();
      await element.updateComplete;
      assert.isTrue(spy.called);
    });

    test('primary and secondary actions split properly', () => {
      // Submit should be the only primary action.
      assert.equal(element.topLevelPrimaryActions!.length, 1);
      assert.equal(element.topLevelPrimaryActions![0].label, 'Submit');
      assert.equal(
        element.topLevelSecondaryActions!.length,
        element.topLevelActions!.length - 1
      );
    });

    test('revert submission action is skipped', () => {
      assert.equal(
        element.allActionValues.filter(action => action.__key === 'submit')
          .length,
        1
      );
      assert.equal(
        element.allActionValues.filter(
          action => action.__key === 'revert_submission'
        ).length,
        0
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
      await element.updateComplete;
      assert.isTrue(
        stub.calledWith(
          element.changeNum,
          element.latestPatchNum,
          '/plugin~action'
        )
      );
      assert.equal(
        (element.revisionActions['plugin~action'] as UIActionInfo).__url,
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
      await element.updateComplete;
      assert.isTrue(
        stub.calledWith(element.changeNum, undefined, '/plugin~action')
      );
      assert.equal(
        (element.actions['plugin~action'] as UIActionInfo).__url,
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
      await element.updateComplete;
      let buttonEl: Element | undefined = queryAndAssert(
        element,
        '[data-action-key="submit"]'
      );
      assert.isOk(buttonEl);
      element.setActionHidden(
        ActionType.REVISION,
        RevisionActions.SUBMIT,
        true
      );
      assert.lengthOf(element.hiddenActions, 1);
      await element.updateComplete;
      buttonEl = query(element, '[data-action-key="submit"]');
      assert.isNotOk(buttonEl);

      element.setActionHidden(
        ActionType.REVISION,
        RevisionActions.SUBMIT,
        false
      );
      await element.updateComplete;
      buttonEl = queryAndAssert(element, '[data-action-key="submit"]');
      assert.isFalse(buttonEl.hasAttribute('hidden'));
    });

    test('buttons exist', async () => {
      await element.updateComplete;
      const buttonEls = queryAll(element, 'gr-button');
      const menuItems = queryAndAssert<GrDropdown>(
        element,
        '#moreActions'
      ).items;

      // Total button number is one greater than the number of total actions
      // due to the existence of the overflow menu trigger.
      assert.equal(
        buttonEls.length + menuItems!.length,
        element.allActionValues.length + 1
      );
      assert.isFalse(element.hidden);
    });

    test('delete buttons have explicit labels', async () => {
      await element.updateComplete;
      const deleteItems = queryAndAssert<GrDropdown>(
        element,
        '#moreActions'
      ).items!.filter(item => item.id!.startsWith('delete'));
      assert.equal(deleteItems.length, 1);
      assert.equal(deleteItems[0].name, 'Delete change');
    });

    test('get revision object from change', () => {
      const revObj = {
        ...createRevision(2),
        foo: 'bar',
      };
      const change = {
        ...createChangeViewChange(),
        revisions: {
          rev1: createRevision(1),
          rev2: revObj,
        },
      };
      assert.deepEqual(
        element.getRevision(change, 2 as PatchSetNumber),
        revObj
      );
    });

    test('actionComparator sort order', () => {
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
      result.sort(element.actionComparator.bind(element));
      assert.deepEqual(result, actions);
    });

    test('submit change', async () => {
      const showSpy = sinon.spy(element, 'showActionDialog');
      stubRestApi('getRepoName').returns(Promise.resolve('test' as RepoName));
      element.change = {
        ...createChangeViewChange(),
        revisions: {
          rev1: {...createRevision(), _number: 1 as PatchSetNumber},
          rev2: {...createRevision(), _number: 2 as PatchSetNumber},
        },
      };
      element.latestPatchNum = 2 as PatchSetNumber;

      queryAndAssert<GrButton>(
        element,
        'gr-button[data-action-key="submit"]'
      ).click();

      await element.updateComplete;
      assert.isTrue(
        showSpy.calledWith(
          queryAndAssert<GrConfirmSubmitDialog>(element, '#confirmSubmitDialog')
        )
      );
    });

    test('submit change, tap on icon', async () => {
      const submitted = mockPromise();
      sinon
        .stub(
          queryAndAssert<GrConfirmSubmitDialog>(
            element,
            '#confirmSubmitDialog'
          ),
          'resetFocus'
        )
        .callsFake(() => submitted.resolve());
      stubRestApi('getRepoName').returns(Promise.resolve('test' as RepoName));
      element.change = {
        ...createChangeViewChange(),
        revisions: {
          rev1: {...createRevision(), _number: 1 as PatchSetNumber},
          rev2: {...createRevision(), _number: 2 as PatchSetNumber},
        },
      };
      element.latestPatchNum = 2 as PatchSetNumber;

      queryAndAssert<GrButton>(
        element,
        'gr-button[data-action-key="submit"] gr-icon'
      ).click();
      await submitted;
    });

    test('correct icons', async () => {
      element.loggedIn = true;
      await element.updateComplete;

      queryAndAssert<GrButton>(
        element,
        'gr-button[data-action-key="submit"] gr-icon'
      );
      queryAndAssert<GrButton>(
        element,
        'gr-button[data-action-key="rebase"] gr-icon'
      );
      queryAndAssert<GrButton>(
        element,
        'gr-button[data-action-key="edit"] gr-icon[filled]'
      );
    });

    test('handleSubmitConfirm', () => {
      const fireStub = sinon.stub(element, 'fireAction');
      sinon.stub(element, 'canSubmitChange').returns(true);
      element.handleSubmitConfirm();
      assert.isTrue(fireStub.calledOnce);
      assert.deepEqual(fireStub.lastCall.args, [
        '/submit',
        assertUIActionInfo(element.revisionActions?.submit),
        true,
      ]);
    });

    test('handleSubmitConfirm when not able to submit', () => {
      const fireStub = sinon.stub(element, 'fireAction');
      sinon.stub(element, 'canSubmitChange').returns(false);
      element.handleSubmitConfirm();
      assert.isFalse(fireStub.called);
    });

    test('submit change with plugin hook', async () => {
      sinon.stub(element, 'canSubmitChange').callsFake(() => false);
      const fireActionStub = sinon.stub(element, 'fireAction');
      await element.updateComplete;
      queryAndAssert<GrButton>(
        element,
        'gr-button[data-action-key="submit"]'
      ).click();
      assert.equal(fireActionStub.callCount, 0);
    });

    test('rebase change', async () => {
      const fireActionStub = sinon.stub(element, 'fireAction');
      const fetchChangesStub = sinon
        .stub(
          queryAndAssert<GrConfirmRebaseDialog>(element, '#confirmRebase'),
          'fetchRecentChanges'
        )
        .returns(Promise.resolve([]));
      await element.updateComplete;
      queryAndAssert<GrButton>(
        element,
        'gr-button[data-action-key="rebase"]'
      ).click();
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
      element.handleRebaseConfirm(
        new CustomEvent('', {
          detail: {
            base: '1234',
            allowConflicts: false,
            rebaseChain: false,
            onBehalfOfUploader: true,
            committerEmail: 'test@default.org',
          },
        })
      );
      assert.deepEqual(fireActionStub.lastCall.args, [
        '/rebase',
        assertUIActionInfo(rebaseAction),
        true,
        {
          base: '1234',
          allow_conflicts: false,
          on_behalf_of_uploader: true,
          committer_email: 'test@default.org',
          validation_options: {},
        },
        {allow_conflicts: false, on_behalf_of_uploader: true},
      ]);
    });

    test('rebase change with validation options', async () => {
      const fireActionStub = sinon.stub(element, 'fireAction');
      const confirmRebaseDialog = queryAndAssert<GrConfirmRebaseDialog>(
        element,
        '#confirmRebase'
      );
      const fetchChangesStub = sinon
        .stub(confirmRebaseDialog, 'fetchRecentChanges')
        .returns(Promise.resolve([]));
      sinon
        .stub(confirmRebaseDialog, 'getValidationOptions')
        .returns([{name: 'o1', description: 'option 1'}]);
      await element.updateComplete;
      queryAndAssert<GrButton>(
        element,
        'gr-button[data-action-key="rebase"]'
      ).click();
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
      element.handleRebaseConfirm(
        new CustomEvent('', {
          detail: {
            base: '1234',
            allowConflicts: false,
            rebaseChain: false,
            onBehalfOfUploader: true,
            committerEmail: 'test@default.org',
          },
        })
      );
      assert.deepEqual(fireActionStub.lastCall.args, [
        '/rebase',
        assertUIActionInfo(rebaseAction),
        true,
        {
          base: '1234',
          allow_conflicts: false,
          on_behalf_of_uploader: true,
          committer_email: 'test@default.org',
          validation_options: {o1: 'true'},
        },
        {allow_conflicts: false, on_behalf_of_uploader: true},
      ]);
    });

    test('rebase change fires reload event', async () => {
      await element.handleResponse(
        {__key: 'rebase', __type: ActionType.CHANGE, label: 'l'},
        new Response()
      );
      assert.isTrue(navigateResetStub.called);
    });

    test("rebase dialog gets recent changes each time it's opened", async () => {
      const fetchChangesStub = sinon
        .stub(
          queryAndAssert<GrConfirmRebaseDialog>(element, '#confirmRebase'),
          'fetchRecentChanges'
        )
        .returns(Promise.resolve([]));
      await element.updateComplete;
      const rebaseButton = queryAndAssert<GrButton>(
        element,
        'gr-button[data-action-key="rebase"]'
      );
      rebaseButton.click();
      await element.updateComplete;
      assert.isTrue(fetchChangesStub.calledOnce);

      await element.updateComplete;
      queryAndAssert<GrConfirmRebaseDialog>(
        element,
        '#confirmRebase'
      ).dispatchEvent(
        new CustomEvent('cancel', {
          composed: true,
          bubbles: true,
        })
      );
      rebaseButton.click();
      assert.isTrue(fetchChangesStub.calledTwice);
    });

    test('two dialogs are not shown at the same time', async () => {
      await element.updateComplete;
      queryAndAssert<GrButton>(
        element,
        'gr-button[data-action-key="rebase"]'
      ).click();
      await element.updateComplete;
      assert.isFalse(
        queryAndAssert<GrConfirmRebaseDialog>(element, '#confirmRebase').hidden
      );
      stubRestApi('getChanges').returns(Promise.resolve([]));
      element.handleCherrypickTap();
      await element.updateComplete;
      assert.isTrue(
        queryAndAssert<GrConfirmRebaseDialog>(element, '#confirmRebase').hidden
      );
      assert.isFalse(
        queryAndAssert<GrConfirmCherrypickDialog>(element, '#confirmCherrypick')
          .hidden
      );
    });

    test('setReviewOnRevert', () => {
      const review = {labels: {Foo: 1, 'Bar-Baz': -2}};
      const changeId = 1234 as NumericChangeId;
      sinon
        .stub(
          testResolver(pluginLoaderToken).jsApiService,
          'getReviewPostRevert'
        )
        .returns(review);
      const saveStub = stubRestApi('saveChangeReview').returns(
        Promise.resolve({})
      );
      const setReviewOnRevert = element.setReviewOnRevert(changeId) as Promise<
        undefined | Response
      >;
      return setReviewOnRevert.then((_res: Response | undefined) => {
        assert.isTrue(saveStub.calledOnce);
        assert.equal(saveStub.lastCall.args[0], changeId);
        assert.deepEqual(saveStub.lastCall.args[2], review);
      });
    });

    suite('change edits', () => {
      test('shows confirm dialog for delete edit', async () => {
        element.loggedIn = true;
        element.editMode = true;
        element.editPatchsetLoaded = true;
        await element.updateComplete;

        const fireActionStub = sinon.stub(element, 'fireAction');
        element.handleDeleteEditTap();
        assert.isFalse(
          queryAndAssert<GrDialog>(element, '#confirmDeleteEditDialog').hidden
        );
        queryAndAssert<GrButton>(
          queryAndAssert(element, '#confirmDeleteEditDialog'),
          'gr-button[primary]'
        ).click();
        await element.updateComplete;

        assert.equal(fireActionStub.lastCall.args[0], '/edit');
      });

      test('all cached change edits get deleted on delete edit', async () => {
        element.loggedIn = true;
        element.editMode = true;
        element.editPatchsetLoaded = true;
        await element.updateComplete;

        const storage = testResolver(storageServiceToken);
        storage.setEditableContentItem(
          'c42_ps2_index.php',
          '<?php\necho 42_ps_2'
        );
        storage.setEditableContentItem(
          'c42_psedit_index.php',
          '<?php\necho 42_ps_edit'
        );

        assert.equal(
          storage.getEditableContentItem('c42_ps2_index.php')!.message,
          '<?php\necho 42_ps_2'
        );
        assert.equal(
          storage.getEditableContentItem('c42_psedit_index.php')!.message,
          '<?php\necho 42_ps_edit'
        );

        assert.isOk(storage.getEditableContentItem('c42_psedit_index.php')!);
        assert.isOk(storage.getEditableContentItem('c42_ps2_index.php')!);
        assert.isNotOk(storage.getEditableContentItem('c50_psedit_index.php')!);

        const eraseEditableContentItemsForChangeEditSpy = sinon.spy(
          storage,
          'eraseEditableContentItemsForChangeEdit'
        );
        sinon.stub(element, 'fireAction');
        element.handleDeleteEditTap();
        assert.isFalse(
          queryAndAssert<GrDialog>(element, '#confirmDeleteEditDialog').hidden
        );
        queryAndAssert<GrButton>(
          queryAndAssert(element, '#confirmDeleteEditDialog'),
          'gr-button[primary]'
        ).click();
        await element.updateComplete;
        assert.isTrue(eraseEditableContentItemsForChangeEditSpy.called);
        assert.isNotOk(storage.getEditableContentItem('c42_psedit_index.php')!);
        assert.isNotOk(storage.getEditableContentItem('c42_ps2_index.php')!);
      });

      test('edit patchset is loaded, needs rebase', async () => {
        element.loggedIn = true;
        element.editMode = true;
        element.editPatchsetLoaded = true;
        element.change = {
          ...createChangeViewChange(),
          status: ChangeStatus.NEW,
        };
        element.editBasedOnCurrentPatchSet = false;
        await element.updateComplete;

        assert.isNotOk(
          query(element, 'gr-button[data-action-key="publishEdit"]')
        );
        assert.isOk(query(element, 'gr-button[data-action-key="rebaseEdit"]'));
        assert.isOk(query(element, 'gr-button[data-action-key="deleteEdit"]'));
        assert.isNotOk(query(element, 'gr-button[data-action-key="edit"]'));
        assert.isNotOk(query(element, 'gr-button[data-action-key="stopEdit"]'));
      });

      test('edit patchset is loaded, does not need rebase', async () => {
        element.loggedIn = true;
        element.editMode = true;
        element.editPatchsetLoaded = true;
        element.change = {
          ...createChangeViewChange(),
          status: ChangeStatus.NEW,
        };
        element.editBasedOnCurrentPatchSet = true;
        await element.updateComplete;

        assert.isOk(query(element, 'gr-button[data-action-key="publishEdit"]'));
        assert.isNotOk(
          query(element, 'gr-button[data-action-key="rebaseEdit"]')
        );
        assert.isOk(query(element, 'gr-button[data-action-key="deleteEdit"]'));
        assert.isNotOk(query(element, 'gr-button[data-action-key="edit"]'));
        assert.isNotOk(query(element, 'gr-button[data-action-key="stopEdit"]'));
      });

      test('edit mode is loaded, no edit patchset', async () => {
        element.loggedIn = true;
        element.editMode = true;
        element.editPatchsetLoaded = false;
        element.change = {
          ...createChangeViewChange(),
          status: ChangeStatus.NEW,
        };
        await element.updateComplete;

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
        element.loggedIn = true;
        element.editMode = false;
        element.editPatchsetLoaded = false;
        element.change = {
          ...createChangeViewChange(),
          status: ChangeStatus.NEW,
        };
        await element.updateComplete;

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
        element.loggedIn = true;
        const editTapped = mockPromise();
        element.addEventListener('edit-tap', () => {
          editTapped.resolve();
        });
        element.editMode = true;
        element.change = {
          ...createChangeViewChange(),
          status: ChangeStatus.NEW,
        };
        await element.updateComplete;

        assert.isNotOk(query(element, 'gr-button[data-action-key="edit"]'));
        assert.isOk(query(element, 'gr-button[data-action-key="stopEdit"]'));
        element.change = {
          ...createChangeViewChange(),
          status: ChangeStatus.MERGED,
        };
        await element.updateComplete;

        assert.isNotOk(query(element, 'gr-button[data-action-key="edit"]'));
        element.change = {
          ...createChangeViewChange(),
          status: ChangeStatus.NEW,
        };
        element.editMode = false;
        await element.updateComplete;

        queryAndAssert<GrButton>(
          element,
          'gr-button[data-action-key="edit"]'
        ).click();
        await editTapped;
      });
    });

    test('edit action not shown for logged out user', async () => {
      element.loggedIn = false;
      element.editMode = false;
      element.editPatchsetLoaded = false;
      element.change = {
        ...createChangeViewChange(),
        status: ChangeStatus.NEW,
      };
      await element.updateComplete;

      assert.isNotOk(
        query(element, 'gr-button[data-action-key="publishEdit"]')
      );
      assert.isNotOk(query(element, 'gr-button[data-action-key="rebaseEdit"]'));
      assert.isNotOk(query(element, 'gr-button[data-action-key="deleteEdit"]'));
      assert.isNotOk(query(element, 'gr-button[data-action-key="edit"]'));
      assert.isNotOk(query(element, 'gr-button[data-action-key="stopEdit"]'));
    });

    suite('cherry-pick', () => {
      let fireActionStub: sinon.SinonStub;

      setup(() => {
        fireActionStub = sinon.stub(element, 'fireAction');
        sinon.stub(window, 'alert');
      });

      test('works', async () => {
        element.handleCherrypickTap();
        const action = {
          __key: 'cherrypick',
          __type: 'revision',
          __primary: false,
          enabled: true,
          label: 'Cherry pick',
          method: HttpMethod.POST,
          title: 'Cherry pick change to a different branch',
        };

        element.handleCherrypickConfirm();
        assert.equal(fireActionStub.callCount, 0);

        queryAndAssert<GrConfirmCherrypickDialog>(
          element,
          '#confirmCherrypick'
        ).branch = 'master' as BranchName;
        element.handleCherrypickConfirm();
        assert.equal(fireActionStub.callCount, 0); // Still needs a message.

        // Add attributes that are used to determine the message.
        queryAndAssert<GrConfirmCherrypickDialog>(
          element,
          '#confirmCherrypick'
        ).commitMessage = 'foo message';
        queryAndAssert<GrConfirmCherrypickDialog>(
          element,
          '#confirmCherrypick'
        ).changeStatus = ChangeStatus.NEW;
        queryAndAssert<GrConfirmCherrypickDialog>(
          element,
          '#confirmCherrypick'
        ).commitNum = '123' as CommitId;
        await element.updateComplete;

        element.handleCherrypickConfirm();
        await element.updateComplete;

        const autogrowEl = queryAndAssert<GrAutogrowTextarea>(
          queryAndAssert<GrConfirmCherrypickDialog>(
            element,
            '#confirmCherrypick'
          ),
          '#messageInput'
        );
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
            committer_email: null,
          },
        ]);
      });

      test('cherry pick even with conflicts', async () => {
        element.handleCherrypickTap();
        const action = {
          __key: 'cherrypick',
          __type: 'revision',
          __primary: false,
          enabled: true,
          label: 'Cherry pick',
          method: HttpMethod.POST,
          title: 'Cherry pick change to a different branch',
        };

        queryAndAssert<GrConfirmCherrypickDialog>(
          element,
          '#confirmCherrypick'
        ).branch = 'master' as BranchName;

        // Add attributes that are used to determine the message.
        queryAndAssert<GrConfirmCherrypickDialog>(
          element,
          '#confirmCherrypick'
        ).commitMessage = 'foo message';
        queryAndAssert<GrConfirmCherrypickDialog>(
          element,
          '#confirmCherrypick'
        ).changeStatus = ChangeStatus.NEW;
        queryAndAssert<GrConfirmCherrypickDialog>(
          element,
          '#confirmCherrypick'
        ).commitNum = '123' as CommitId;
        await element.updateComplete;

        element.handleCherrypickConflictConfirm();
        await element.updateComplete;

        assert.deepEqual(fireActionStub.lastCall.args, [
          '/cherrypick',
          action,
          true,
          {
            destination: 'master',
            base: null,
            message: 'foo message',
            allow_conflicts: true,
            committer_email: null,
          },
        ]);
      });

      test('branch name cleared when re-open cherrypick', () => {
        const emptyBranchName = '';
        queryAndAssert<GrConfirmCherrypickDialog>(
          element,
          '#confirmCherrypick'
        ).branch = 'master' as BranchName;

        element.handleCherrypickTap();
        assert.equal(
          queryAndAssert<GrConfirmCherrypickDialog>(
            element,
            '#confirmCherrypick'
          ).branch,
          emptyBranchName
        );
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
          element.change!.topic = 'T' as TopicName;
          stubRestApi('getChanges').returns(Promise.resolve(changes));
          await element.handleCherrypickTap();
          await element.updateComplete;
          const confirmCherrypick = queryAndAssert<GrConfirmCherrypickDialog>(
            element,
            '#confirmCherrypick'
          );
          await element.updateComplete;
          const radioButtons = queryAll<HTMLInputElement>(
            confirmCherrypick,
            "input[name='cherryPickOptions']"
          );
          assert.equal(radioButtons.length, 2);
          radioButtons[1].click();
          await element.updateComplete;
        });

        test('cherry pick topic dialog is rendered', async () => {
          const dialog = queryAndAssert<GrConfirmCherrypickDialog>(
            element,
            '#confirmCherrypick'
          );
          await element.updateComplete;
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
          const dialog = queryAndAssert<GrConfirmCherrypickDialog>(
            element,
            '#confirmCherrypick'
          );
          const error = queryAndAssert<HTMLSpanElement>(
            dialog,
            '.error-message'
          );
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
          await element.updateComplete;
          assert.equal(
            error.innerText,
            'Two changes cannot be of the same' + ' project'
          );
        });
      });
    });

    suite('move change', () => {
      let fireActionStub: sinon.SinonStub;

      setup(async () => {
        fireActionStub = sinon.stub(element, 'fireAction');
        sinon.stub(window, 'alert');
        element.actions = {
          move: {
            method: HttpMethod.POST,
            label: 'Move',
            title: 'Move the change',
            enabled: true,
          },
        };
        await element.updateComplete;
      });

      test('works', () => {
        element.handleMoveTap();

        element.handleMoveConfirm();
        assert.equal(fireActionStub.callCount, 0);

        queryAndAssert<GrConfirmMoveDialog>(element, '#confirmMove').branch =
          'master' as BranchName;
        element.handleMoveConfirm();
        assert.equal(fireActionStub.callCount, 1);
      });

      test('branch name cleared when re-open move', () => {
        const emptyBranchName = '';
        queryAndAssert<GrConfirmMoveDialog>(element, '#confirmMove').branch =
          'master' as BranchName;

        element.handleMoveTap();
        assert.equal(
          queryAndAssert<GrConfirmMoveDialog>(element, '#confirmMove').branch,
          emptyBranchName
        );
      });
    });

    test('custom actions', async () => {
      // Add a button with the same key as a server-based one to ensure
      // collisions are taken care of.
      const key = element.addActionButton(ActionType.REVISION, 'Bork!');
      const keyTapped = mockPromise();
      element.addEventListener(key + '-tap', async e => {
        assert.equal(
          (e as CustomEvent).detail.node.getAttribute('data-action-key'),
          key
        );
        element.removeActionButton(key);
        await element.updateComplete;
        assert.notOk(query(element, '[data-action-key="' + key + '"]'));
        keyTapped.resolve();
      });
      await element.updateComplete;
      await element.updateComplete;
      queryAndAssert<GrButton>(
        element,
        '[data-action-key="' + key + '"]'
      ).click();
      await keyTapped;
    });

    test('setLoadingOnButtonWithKey top-level', async () => {
      const key = 'rebase';
      const type = ActionType.REVISION;
      const cleanup = element.setLoadingOnButtonWithKey({
        __type: type,
        __key: key,
        label: 'label',
      });
      assert.equal(element.actionLoadingMessage, 'Rebasing...');

      const button = queryAndAssert<GrButton>(
        element,
        '[data-action-key="' + key + '"]'
      );
      const dialog = queryAndAssert<GrConfirmRebaseDialog>(
        element,
        'gr-confirm-rebase-dialog'
      );
      assert.isTrue(button.hasAttribute('loading'));
      assert.isTrue(button.disabled);
      await dialog.updateComplete;
      assert.isTrue(dialog.disableActions);

      assert.isOk(cleanup);
      assert.isFunction(cleanup);
      cleanup();

      assert.isFalse(button.hasAttribute('loading'));
      assert.isFalse(button.disabled);
      assert.isNotOk(element.actionLoadingMessage);
      await dialog.updateComplete;
      assert.isFalse(dialog.disableActions);
    });

    test('setLoadingOnButtonWithKey overflow menu', () => {
      const key = 'cherrypick';
      const type = ActionType.REVISION;
      const cleanup = element.setLoadingOnButtonWithKey({
        __type: type,
        __key: key,
        label: 'label',
      });
      assert.equal(element.actionLoadingMessage, 'Cherry-picking...');
      assert.include(element.disabledMenuActions, 'cherrypick');
      assert.isFunction(cleanup);

      cleanup();

      assert.notOk(element.actionLoadingMessage);
      assert.notInclude(element.disabledMenuActions, 'cherrypick');
    });

    suite('abandon change', () => {
      let alertStub: sinon.SinonStub;
      let fireActionStub: sinon.SinonStub;

      setup(async () => {
        fireActionStub = sinon.stub(element, 'fireAction');
        alertStub = sinon.stub(window, 'alert');
        element.actions = {
          abandon: {
            method: HttpMethod.POST,
            label: 'Abandon',
            title: 'Abandon the change',
            enabled: true,
          },
        };
        await element.updateComplete;
        // test
        await element.reload();
      });

      test('abandon change with message', async () => {
        const newAbandonMsg = 'Test Abandon Message';
        queryAndAssert<GrConfirmAbandonDialog>(
          element,
          '#confirmAbandonDialog'
        ).message = newAbandonMsg;
        await element.updateComplete;
        queryAndAssert<GrButton>(
          element,
          'gr-button[data-action-key="abandon"]'
        ).click();

        assert.equal(
          queryAndAssert<GrConfirmAbandonDialog>(
            element,
            '#confirmAbandonDialog'
          ).message,
          newAbandonMsg
        );
      });

      test('abandon change with no message', async () => {
        await element.updateComplete;
        queryAndAssert<GrButton>(
          element,
          'gr-button[data-action-key="abandon"]'
        ).click();

        assert.equal(
          queryAndAssert<GrConfirmAbandonDialog>(
            element,
            '#confirmAbandonDialog'
          ).message,
          ''
        );
      });

      test('works', () => {
        queryAndAssert<GrConfirmAbandonDialog>(
          element,
          '#confirmAbandonDialog'
        ).message = 'original message';
        queryAndAssert<GrButton>(
          element,
          'gr-button[data-action-key="abandon"]'
        ).click();

        queryAndAssert<GrConfirmAbandonDialog>(
          element,
          '#confirmAbandonDialog'
        ).message = 'foo message';
        element.handleAbandonDialogConfirm();
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

      setup(async () => {
        fireActionStub = sinon.stub(element, 'fireAction');
        element.commitMessage = 'random commit message';
        element.change = {
          ...createChangeViewChange(),
          current_revision: 'abcdef' as CommitId,
          actions: {
            revert: {
              method: HttpMethod.POST,
              label: 'Revert',
              title: 'Revert the change',
              enabled: true,
            },
          },
        };
        await element.updateComplete;
        // test
        await element.reload();
      });

      test('revert change payload', async () => {
        await element.updateComplete;
        queryAndAssert<GrButton>(
          element,
          'gr-button[data-action-key="revert"]'
        ).click();
        const revertAction = {
          __key: 'revert',
          __type: 'change',
          __primary: false,
          method: HttpMethod.POST,
          label: 'Revert',
          title: 'Revert the change',
          enabled: true,
        };
        queryAndAssert(element, 'gr-confirm-revert-dialog').dispatchEvent(
          new CustomEvent('confirm-revert', {
            detail: {
              message: 'foo message',
              revertType: 1,
            },
          })
        );
        assert.deepEqual(fireActionStub.lastCall.args, [
          '/revert',
          assertUIActionInfo(revertAction),
          false,
          {
            message: 'foo message',
            validation_options: {},
          },
        ]);
      });

      test('revert change with plugin hook', async () => {
        const newRevertMsg = 'Modified revert msg';
        const revertDialog = queryAndAssert<GrConfirmRevertDialog>(
          element,
          '#confirmRevertDialog'
        );
        sinon
          .stub(revertDialog, 'modifyRevertMsg')
          .callsFake(() => newRevertMsg);
        element.change = {
          ...createChangeViewChange(),
          current_revision: 'abc1234' as CommitId,
          actions: {
            revert: {
              method: HttpMethod.POST,
              label: 'Revert',
              title: 'Revert the change',
              enabled: true,
            },
          },
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
          .stub(revertDialog, 'populateRevertSubmissionMessage')
          .callsFake(() => 'original msg');
        await element.updateComplete;
        queryAndAssert<GrButton>(
          element,
          'gr-button[data-action-key="revert"]'
        ).click();
        await element.updateComplete;
        await waitUntil(() => !!revertDialog.message);
        assert.equal(revertDialog.message, newRevertMsg);
      });

      suite('revert change submitted together', () => {
        let getChangesStub: sinon.SinonStub;
        setup(async () => {
          element.change = {
            ...createChangeViewChange(),
            submission_id: '199 0' as ChangeSubmissionId,
            current_revision: '2000' as CommitId,
            actions: {
              revert: {
                method: HttpMethod.POST,
                label: 'Revert',
                title: 'Revert the change',
                enabled: true,
              },
            },
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
          await element.updateComplete;
        });

        test('confirm revert dialog shows both options', async () => {
          const confirmRevertDialog = queryAndAssert<GrConfirmRevertDialog>(
            element,
            '#confirmRevertDialog'
          );
          queryAndAssert<GrButton>(
            element,
            'gr-button[data-action-key="revert"]'
          ).click();
          await waitUntil(() => !!confirmRevertDialog.message);
          await element.updateComplete;
          assert.equal(getChangesStub.args[0][1], 'submissionid: "199 0"');
          await element.updateComplete;
          const revertSingleChangeLabel = queryAndAssert<HTMLLabelElement>(
            confirmRevertDialog,
            '.revertSingleChange'
          );
          const revertSubmissionLabel = queryAndAssert<HTMLLabelElement>(
            confirmRevertDialog,
            '.revertSubmission'
          );
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
            'Reason for revert: <MUST SPECIFY REASON HERE>' +
            '\n\n' +
            'Reverted changes: /q/submissionid:199+0\n';
          assert.equal(confirmRevertDialog.message, expectedMsg);
          const radioInputs = queryAll<HTMLInputElement>(
            confirmRevertDialog,
            'input[name="revertOptions"]'
          );
          radioInputs[0].click();
          await element.updateComplete;
          expectedMsg =
            'Revert "random commit message"\n\nThis reverts ' +
            'commit 2000.\n\nReason' +
            ' for revert: <MUST SPECIFY REASON HERE>\n';
          assert.equal(confirmRevertDialog.message, expectedMsg);
        });

        test('submit fails if message is not edited', async () => {
          queryAndAssert<GrButton>(
            element,
            'gr-button[data-action-key="revert"]'
          ).click();
          const confirmRevertDialog = queryAndAssert<GrConfirmRevertDialog>(
            element,
            '#confirmRevertDialog'
          );
          const fireStub = sinon.stub(confirmRevertDialog, 'dispatchEvent');
          await waitUntil(() => !!confirmRevertDialog.message);
          await element.updateComplete;
          queryAndAssert<GrButton>(
            queryAndAssert(
              queryAndAssert<GrConfirmRevertDialog>(
                element,
                '#confirmRevertDialog'
              ),
              'gr-dialog'
            ),
            '#confirm'
          ).click();
          await element.updateComplete;
          assert.isTrue(confirmRevertDialog.showErrorMessage);
          assert.isFalse(fireStub.called);
        });

        test('message modification is retained on switching', async () => {
          queryAndAssert<GrButton>(
            element,
            'gr-button[data-action-key="revert"]'
          ).click();
          await element.updateComplete;
          const confirmRevertDialog = queryAndAssert<GrConfirmRevertDialog>(
            element,
            '#confirmRevertDialog'
          );
          await waitUntil(() => !!confirmRevertDialog.message);
          await element.updateComplete;
          const radioInputs = queryAll<HTMLInputElement>(
            confirmRevertDialog,
            'input[name="revertOptions"]'
          );
          const revertSubmissionMsg =
            'Revert submission 199 0' +
            '\n\n' +
            'Reason for revert: <MUST SPECIFY REASON HERE>' +
            '\n\n' +
            'Reverted changes: /q/submissionid:199+0\n';
          const singleChangeMsg =
            'Revert "random commit message"\n\nThis reverts ' +
            'commit 2000.\n\nReason' +
            ' for revert: <MUST SPECIFY REASON HERE>\n';
          assert.equal(confirmRevertDialog.message, revertSubmissionMsg);
          const newRevertMsg = revertSubmissionMsg + 'random';
          const newSingleChangeMsg = singleChangeMsg + 'random';
          confirmRevertDialog.message = newRevertMsg;
          await element.updateComplete;
          radioInputs[0].click();
          await element.updateComplete;
          assert.equal(confirmRevertDialog.message, singleChangeMsg);
          confirmRevertDialog.message = newSingleChangeMsg;
          await element.updateComplete;
          radioInputs[1].click();
          await element.updateComplete;
          assert.equal(confirmRevertDialog.message, newRevertMsg);
          radioInputs[0].click();
          await element.updateComplete;
          assert.equal(confirmRevertDialog.message, newSingleChangeMsg);
        });
      });

      suite('revert single change', () => {
        setup(async () => {
          element.change = {
            ...createChangeViewChange(),
            submission_id: '199' as ChangeSubmissionId,
            current_revision: '2000' as CommitId,
            actions: {
              revert: {
                method: HttpMethod.POST,
                label: 'Revert',
                title: 'Revert the change',
                enabled: true,
              },
            },
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
          await element.updateComplete;
        });

        test('submit fails if message is not edited', async () => {
          queryAndAssert<GrButton>(
            element,
            'gr-button[data-action-key="revert"]'
          ).click();
          const confirmRevertDialog = queryAndAssert<GrConfirmRevertDialog>(
            element,
            '#confirmRevertDialog'
          );
          const fireStub = sinon.stub(confirmRevertDialog, 'dispatchEvent');
          await waitUntil(() => !!confirmRevertDialog.message);
          await element.updateComplete;
          queryAndAssert<GrButton>(
            queryAndAssert(
              queryAndAssert<GrConfirmRevertDialog>(
                element,
                '#confirmRevertDialog'
              ),
              'gr-dialog'
            ),
            '#confirm'
          ).click();
          await element.updateComplete;
          assert.isTrue(confirmRevertDialog.showErrorMessage);
          assert.isFalse(fireStub.called);
        });

        test('confirm revert dialog shows no radio button', async () => {
          const confirmRevertDialog = queryAndAssert<GrConfirmRevertDialog>(
            element,
            '#confirmRevertDialog'
          );
          queryAndAssert<GrButton>(
            element,
            'gr-button[data-action-key="revert"]'
          ).click();
          await waitUntil(() => !!confirmRevertDialog.message);
          await element.updateComplete;
          const radioInputs = queryAll(
            confirmRevertDialog,
            'input[name="revertOptions"]'
          );
          assert.equal(radioInputs.length, 0);
          const msg =
            'Revert "random commit message"\n\n' +
            'This reverts commit 2000.\n\nReason ' +
            'for revert: <MUST SPECIFY REASON HERE>\n';
          assert.equal(confirmRevertDialog.message, msg);
          let editedMsg = msg + 'hello';
          confirmRevertDialog.message += 'hello';
          const confirmButton = queryAndAssert<GrButton>(
            queryAndAssert(
              queryAndAssert<GrConfirmRevertDialog>(
                element,
                '#confirmRevertDialog'
              ),
              'gr-dialog'
            ),
            '#confirm'
          );
          confirmButton.click();
          await element.updateComplete;
          // Contains generic template reason so doesn't submit
          assert.isFalse(fireActionStub.called);
          confirmRevertDialog.message = confirmRevertDialog.message.replace(
            '<MUST SPECIFY REASON HERE>',
            ''
          );
          editedMsg = editedMsg.replace('<MUST SPECIFY REASON HERE>', '');
          confirmButton.click();
          await element.updateComplete;
          assert.equal(fireActionStub.getCall(0).args[0], '/revert');
          assert.equal(fireActionStub.getCall(0).args[1].__key, 'revert');
          assert.equal(fireActionStub.getCall(0).args[3].message, editedMsg);
        });
      });
    });

    suite('mark change private', () => {
      setup(async () => {
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
        element.latestPatchNum = 2 as PatchSetNumber;

        await element.updateComplete;
        await element.reload();
      });

      test(
        'make sure the mark private change button is not outside of the ' +
          'overflow menu',
        async () => {
          await element.updateComplete;
          assert.isNotOk(query(element, '[data-action-key="private"]'));
        }
      );

      test('private change', async () => {
        await element.updateComplete;
        assert.isOk(
          query(
            queryAndAssert<GrDropdown>(element, '#moreActions'),
            '[data-id="private-change"]'
          )
        );
        element.setActionOverflow(ActionType.CHANGE, 'private', false);
        await element.updateComplete;
        assert.isOk(query(element, '[data-action-key="private"]'));
        assert.isNotOk(
          query(
            queryAndAssert<GrDropdown>(element, '#moreActions'),
            '[data-id="private-change"]'
          )
        );
      });
    });

    suite('unmark private change', () => {
      setup(async () => {
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
        element.latestPatchNum = 2 as PatchSetNumber;

        await element.updateComplete;
        await element.reload();
      });

      test(
        'make sure the unmark private change button is not outside of the ' +
          'overflow menu',
        async () => {
          await element.updateComplete;
          assert.isNotOk(query(element, '[data-action-key="private.delete"]'));
        }
      );

      test('unmark the private change', async () => {
        await element.updateComplete;
        assert.isOk(
          query(
            queryAndAssert<GrDropdown>(element, '#moreActions'),
            '[data-id="private.delete-change"]'
          )
        );
        element.setActionOverflow(ActionType.CHANGE, 'private.delete', false);
        await element.updateComplete;
        assert.isOk(query(element, '[data-action-key="private.delete"]'));
        assert.isNotOk(
          query(
            queryAndAssert<GrDropdown>(element, '#moreActions'),
            '[data-id="private.delete-change"]'
          )
        );
      });
    });

    suite('delete change', () => {
      let fireActionStub: sinon.SinonStub;
      let deleteAction: ActionInfo;

      setup(async () => {
        fireActionStub = sinon.stub(element, 'fireAction');
        deleteAction = {
          method: HttpMethod.DELETE,
          label: 'Delete Change',
          title: 'Delete change X_X',
          enabled: true,
        };
        element.change = {
          ...createChangeViewChange(),
          current_revision: 'abc1234' as CommitId,
          actions: {
            '/': deleteAction,
          },
        };
        await element.updateComplete;
      });

      test('does not delete on action', () => {
        element.handleDeleteTap();
        assert.isFalse(fireActionStub.called);
      });

      test('shows confirm dialog', async () => {
        element.handleDeleteTap();
        assert.isFalse(
          queryAndAssert<GrDialog>(element, '#confirmDeleteDialog').hidden
        );
        queryAndAssert<GrButton>(
          queryAndAssert(element, '#confirmDeleteDialog'),
          'gr-button[primary]'
        ).click();
        await element.updateComplete;
        assert.isTrue(fireActionStub.calledWith('/', deleteAction, false));
      });

      test('hides delete confirm on cancel', async () => {
        element.handleDeleteTap();
        queryAndAssert<GrButton>(
          queryAndAssert(element, '#confirmDeleteDialog'),
          'gr-button:not([primary])'
        ).click();
        await element.updateComplete;
        assert.isTrue(
          queryAndAssert<GrDialog>(element, '#confirmDeleteDialog').hidden
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
        await element.updateComplete;
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
        await element.updateComplete;
        const approveButtonUpdated = query(
          element,
          "gr-button[data-action-key='review']"
        );
        assert.isNotOk(approveButtonUpdated);
        assert.isTrue(element._hideQuickApproveAction);
      });

      test('is first in list of secondary actions', () => {
        const approveButton = queryAndAssert<HTMLElement>(
          element,
          '#secondaryActions'
        ).querySelector('gr-button');
        assert.equal(approveButton!.getAttribute('data-label'), 'foo+1');
      });

      test('not added when change is merged', async () => {
        element.change = {
          ...element.change!,
          status: ChangeStatus.MERGED,
        };

        await element.updateComplete;
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
        await element.updateComplete;
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
        await element.updateComplete;
        const approveButton = query(
          element,
          "gr-button[data-action-key='review']"
        );
        assert.isNotOk(approveButton);
      });

      test('added even when label is optional', async () => {
        element.change = {
          ...createChangeViewChange(),
          current_revision: 'abc1234' as CommitId,
          labels: {
            'Code-Review': {
              optional: true,
              values: {
                '-1': '',
                ' 0': '',
                '+1': '',
              },
            },
          },
          permitted_labels: {
            'Code-Review': ['-1', ' 0', '+1'],
          },
        };
        await element.updateComplete;
        const approveButton = query(
          element,
          "gr-button[data-action-key='review']"
        );
        assert.isOk(approveButton);
      });

      test('approves when tapped', async () => {
        const fireActionStub = sinon.stub(element, 'fireAction');
        queryAndAssert<GrButton>(
          element,
          "gr-button[data-action-key='review']"
        ).click();
        await element.updateComplete;
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
        await element.updateComplete;
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
        await element.updateComplete;
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
        await element.updateComplete;
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
        await element.updateComplete;
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
        await element.updateComplete;
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
        await element.updateComplete;
        const approveButton = queryAndAssert<GrButton>(
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
        await element.updateComplete;
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
        await element.updateComplete;
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
      assert.ok(element.revisionActions?.download);
      element.handleDownloadTap();
      await element.updateComplete;

      assert.isTrue(handler.called);
    });

    test('changing changeNum or patchNum does not reload', () => {
      const reloadStub = sinon.stub(element, 'reload');
      element.changeNum = 123 as NumericChangeId;
      assert.isFalse(reloadStub.called);
      element.latestPatchNum = 456 as PatchSetNumber;
      assert.isFalse(reloadStub.called);
    });

    test('toSentenceCase', () => {
      assert.equal(element.toSentenceCase('blah blah'), 'Blah blah');
      assert.equal(element.toSentenceCase('BLAH BLAH'), 'Blah blah');
      assert.equal(element.toSentenceCase('b'), 'B');
      assert.equal(element.toSentenceCase(''), '');
      assert.equal(element.toSentenceCase('!@#$%^&*()'), '!@#$%^&*()');
    });

    suite('setActionOverflow', () => {
      test('move action from overflow', async () => {
        assert.isNotOk(query(element, '[data-action-key="cherrypick"]'));
        assert.strictEqual(
          queryAndAssert<GrDropdown>(element, '#moreActions').items![0].id,
          'cherrypick-revision'
        );
        element.setActionOverflow(ActionType.REVISION, 'cherrypick', false);
        await element.updateComplete;
        assert.isOk(query(element, '[data-action-key="cherrypick"]'));
        assert.notEqual(
          queryAndAssert<GrDropdown>(element, '#moreActions').items![0].id,
          'cherrypick-revision'
        );
      });

      test('move action to overflow', async () => {
        assert.isOk(query(element, '[data-action-key="submit"]'));
        element.setActionOverflow(ActionType.REVISION, 'submit', true);
        await element.updateComplete;
        assert.isNotOk(query(element, '[data-action-key="submit"]'));
        assert.strictEqual(
          queryAndAssert<GrDropdown>(element, '#moreActions').items![3].id,
          'submit-revision'
        );
      });

      suite('waitForChangeReachable', () => {
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
            return Promise.resolve(undefined);
          }
        };

        const tickAndFlush = async (repetitions: number) => {
          for (let i = 1; i <= repetitions; i++) {
            clock.tick(1000);
            await element.updateComplete;
          }
        };

        test('succeed', async () => {
          stubRestApi('getChange').callsFake(makeGetChange(5));
          const promise = element.waitForChangeReachable(
            123 as NumericChangeId
          );
          tickAndFlush(5);
          const success = await promise;
          assert.isTrue(success);
        });

        test('fail', async () => {
          stubRestApi('getChange').callsFake(makeGetChange(6));
          const promise = element.waitForChangeReachable(
            123 as NumericChangeId
          );
          tickAndFlush(6);
          const success = await promise;
          assert.isFalse(success);
        });
      });
    });

    suite('send', () => {
      let cleanup: sinon.SinonStub;
      const payload = {foo: 'bar'};
      let onShowError: sinon.SinonStub;
      let onShowAlert: sinon.SinonStub;

      setup(async () => {
        cleanup = sinon.stub();
        element.changeNum = 42 as NumericChangeId;
        element.latestPatchNum = 12 as PatchSetNumber;
        element.change = {
          ...createChangeViewChange(),
          current_revision_number: element.latestPatchNum,
          revisions: createRevisions(element.latestPatchNum as number),
          messages: createChangeMessages(1),
        };
        element.change._number = 42 as NumericChangeId;
        await element.updateComplete;

        onShowError = sinon.stub();
        element.addEventListener('show-error', onShowError);
        onShowAlert = sinon.stub();
        element.addEventListener('show-alert', onShowAlert);
      });

      suite('happy path', () => {
        let executeChangeActionStub: sinon.SinonStub;
        setup(() => {
          stubRestApi('getChange').returns(
            Promise.resolve({
              ...createChangeViewChange(),
              // element has latest info
              current_revision_number: element.latestPatchNum!,
              messages: createChangeMessages(1),
            })
          );
          executeChangeActionStub = stubRestApi('executeChangeAction').returns(
            Promise.resolve(new Response())
          );
        });

        test('change action', async () => {
          await element.send(
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
            executeChangeActionStub.calledWith(
              42,
              HttpMethod.DELETE,
              '/endpoint',
              undefined,
              payload
            )
          );
        });

        suite('single changes revert', () => {
          let setUrlStub: sinon.SinonStub;
          setup(() => {
            setUrlStub = sinon.stub(testResolver(navigationToken), 'setUrl');
          });

          test('revert submission single change', async () => {
            const response = new Response(
              makePrefixedJSON({
                revert_changes: [{change_id: 12345, topic: 'T'}],
              })
            );
            executeChangeActionStub.resolves(response);
            await element.send(
              HttpMethod.POST,
              {message: 'Revert submission'},
              '/revert_submission',
              false,
              cleanup,
              {} as UIActionInfo
            );
            await element.handleResponse(
              {
                __key: 'revert_submission',
                __type: ActionType.CHANGE,
                label: 'l',
              },
              response
            );
            assert.isTrue(setUrlStub.called);
            assert.equal(setUrlStub.lastCall.args[0], '/q/topic:"T"');
          });

          test('revert single change', async () => {
            const response = new Response(
              makePrefixedJSON({
                change_id: 12345,
                project: 'projectId',
                _number: 12345,
              })
            );
            executeChangeActionStub.resolves(response);
            await element.send(
              HttpMethod.POST,
              {message: 'Revert'},
              '/revert',
              false,
              cleanup,
              {} as UIActionInfo
            );
            await element.handleResponse(
              {
                __key: 'revert',
                __type: ActionType.CHANGE,
                label: 'l',
              },
              response
            );
            assert.isTrue(setUrlStub.called);
            assert.equal(setUrlStub.lastCall.args[0], '/c/projectId/+/12345');
          });
        });

        test('sends validation options when opening revert dialog', async () => {
          sinon.stub(element, 'handleAction');
          const fireActionStub = sinon.stub(element, 'fireAction');
          element.actions = {
            revert: {
              method: HttpMethod.POST,
              label: 'Revert',
              title: 'Revert the change',
              enabled: true,
            },
          };
          const confirmRevertDialog = query<GrConfirmRevertDialog>(
            element,
            '#confirmRevertDialog'
          );
          assertIsDefined(confirmRevertDialog, 'confirmDialog');
          const populateRevertDialogStub = sinon.stub(
            confirmRevertDialog,
            'populate'
          );
          await element.showRevertDialog();
          await waitUntil(() => !!populateRevertDialogStub.called);
          assert.deepEqual(populateRevertDialogStub.lastCall.args[1], {
            validation_options: [{name: 'o1', description: 'option 1'}],
          });

          element.handleRevertDialogConfirm(
            new CustomEvent('confirm', {
              detail: {
                revertType: RevertType.REVERT_SINGLE_CHANGE,
                message: 'revert this change',
              },
            })
          );
          await waitUntil(() => !!fireActionStub.called);

          assert.deepEqual(fireActionStub.lastCall.args[0], '/revert');
        });

        suite('multiple changes revert', () => {
          let showActionDialogStub: sinon.SinonStub;
          let setUrlStub: sinon.SinonStub;
          let response: Response;
          setup(() => {
            response = new Response(
              makePrefixedJSON({
                revert_changes: [
                  {change_id: 12345, topic: 'T'},
                  {change_id: 23456, topic: 'T'},
                ],
              })
            );
            executeChangeActionStub.resolves(response);
            showActionDialogStub = sinon.stub(element, 'showActionDialog');
            setUrlStub = sinon.stub(testResolver(navigationToken), 'setUrl');
          });

          test('revert submission multiple change', async () => {
            await element.send(
              HttpMethod.POST,
              {message: 'Revert submission'},
              '/revert_submission',
              false,
              cleanup,
              {} as UIActionInfo
            );
            await element.handleResponse(
              {
                __key: 'revert_submission',
                __type: ActionType.CHANGE,
                label: 'l',
              },
              response
            );
            assert.isFalse(showActionDialogStub.called);
            assert.isTrue(setUrlStub.called);
            assert.equal(setUrlStub.lastCall.args[0], '/q/topic:"T"');
          });
        });

        test('revision action', async () => {
          await element.send(
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
            executeChangeActionStub.calledWith(
              42,
              'DELETE',
              '/endpoint',
              12,
              payload
            )
          );
        });
      });

      suite('failure modes', () => {
        let clock: SinonFakeTimers;
        setup(() => {
          clock = sinon.useFakeTimers();
        });

        test('non-latest', () => {
          stubRestApi('getChange').returns(
            Promise.resolve({
              ...createChangeViewChange(),
              // new patchset was uploaded
              current_revision_number: (element.latestPatchNum! +
                1) as PatchSetNumber,
              messages: createChangeMessages(1),
            })
          );
          const executeChangeActionStub = stubRestApi('executeChangeAction');

          return element
            .send(
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
              assert.isFalse(executeChangeActionStub.called);
            });
        });

        test('send fails', () => {
          stubRestApi('getChange').returns(
            Promise.resolve({
              ...createChangeViewChange(),
              // element has latest info
              current_revision_number: element.latestPatchNum!,
              messages: createChangeMessages(1),
            })
          );
          const executeChangeActionStub = stubRestApi(
            'executeChangeAction'
          ).callsFake(
            (
              _num: any,
              _method: any,
              _patchNum: any,
              _endpoint: any,
              _payload: any,
              onErr: any
            ) => {
              onErr!();
              return Promise.resolve(new Response());
            }
          );
          const handleErrorStub = sinon.stub(element, 'handleResponseError');

          return element
            .send(
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
              assert.isTrue(executeChangeActionStub.calledOnce);
              assert.isTrue(handleErrorStub.called);
            });
        });

        test('revert single change change not reachable', async () => {
          let getChangeCall = 0;
          let errorFired = false;
          stubRestApi('getChange').callsFake((_: any, errFn: any) => {
            ++getChangeCall;
            if (getChangeCall === 1) {
              return Promise.resolve({
                ...createChangeViewChange(),
                // element has latest info
                current_revision_number: element.latestPatchNum!,
                messages: createChangeMessages(1),
              });
            } else {
              // Mimics the behaviour of gr-rest-api-impl: If errFn is passed
              // call it and return undefined, otherwise call fireNetworkError
              // or fireServerError.
              if (errFn) {
                errFn.call(undefined);
              } else {
                errorFired = true;
              }
              return Promise.resolve(undefined);
            }
          });
          const setUrlStub = sinon.stub(
            testResolver(navigationToken),
            'setUrl'
          );
          const setReviewOnRevertStub = sinon.stub(
            element,
            'setReviewOnRevert'
          );
          const response = new Response(
            makePrefixedJSON({
              change_id: 12345,
              project: 'projectId',
              _number: 12345,
            })
          );

          await element.send(
            HttpMethod.POST,
            {message: 'Revert'},
            '/revert',
            false,
            cleanup,
            {} as UIActionInfo
          );
          const responsePromise = element.handleResponse(
            {
              __key: 'revert',
              __type: ActionType.CHANGE,
              label: 'l',
            },
            response
          );

          // Wait for all retry attempts
          for (let i = 0; i < 5; i++) {
            clock.tick(1000);
            await clock.runAllAsync();
            await element.updateComplete;
          }

          await responsePromise;

          assert.isTrue(errorFired);
          assert.isFalse(setUrlStub.called);
          assert.isFalse(setReviewOnRevertStub.called);
        });
      });
    });

    test('handleAction reports', () => {
      sinon.stub(element, 'fireAction');
      sinon.stub(element, 'handleChangeAction');

      const reportStub = stubReporting('reportInteraction');
      element.handleAction(ActionType.CHANGE, 'key');
      assert.isTrue(reportStub.called);
      assert.equal(reportStub.lastCall.args[0], 'change-key');
    });
  });

  suite('getChangeRevisionActions returns only some actions', () => {
    let element: GrChangeActions;

    let changeRevisionActions: ActionNameToActionInfoMap = {};

    setup(async () => {
      stubRestApi('getChangeRevisionActions').returns(
        Promise.resolve(changeRevisionActions)
      );
      stubRestApi('send').returns(Promise.reject(new Error('error')));

      sinon
        .stub(testResolver(pluginLoaderToken), 'awaitPluginsLoaded')
        .returns(Promise.resolve());

      element = await fixture<GrChangeActions>(html`
        <gr-change-actions></gr-change-actions>
      `);
      // getChangeRevisionActions is not called without
      // set the following properties
      element.change = createChangeViewChange();
      element.changeNum = 42 as NumericChangeId;
      element.latestPatchNum = 2 as PatchSetNumber;

      stubRestApi('getRepoBranches').returns(Promise.resolve([]));
      await element.updateComplete;
      await element.reload();
    });

    test('confirmSubmitDialog and confirmRebase properties are changed', () => {
      changeRevisionActions = {};
      element.reload();
      assert.strictEqual(
        queryAndAssert<GrConfirmSubmitDialog>(element, '#confirmSubmitDialog')
          .action,
        undefined
      );
      assert.strictEqual(
        queryAndAssert<GrConfirmRebaseDialog>(element, '#confirmRebase')
          .rebaseOnCurrent,
        false
      );
    });
  });
});
