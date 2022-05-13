/**
 * @license
 * Copyright (C) 2015 The Android Open Source Project
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
import '../../edit/gr-edit-constants';
import '../gr-thread-list/gr-thread-list';
import './gr-change-view';
import {
  ChangeStatus,
  CommentSide,
  DefaultBase,
  DiffViewMode,
  HttpMethod,
  MessageTag,
  PrimaryTab,
  createDefaultPreferences,
} from '../../../constants/constants';
import {GrEditConstants} from '../../edit/gr-edit-constants';
import {_testOnly_resetEndpoints} from '../../shared/gr-js-api-interface/gr-plugin-endpoints';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {getPluginLoader} from '../../shared/gr-js-api-interface/gr-plugin-loader';
import {EventType, PluginApi} from '../../../api/plugin';
import {
  mockPromise,
  queryAndAssert,
  stubRestApi,
  stubUsers,
  waitQueryAndAssert,
  waitUntil,
} from '../../../test/test-utils';
import {
  createAppElementChangeViewParams,
  createApproval,
  createChange,
  createChangeMessages,
  createCommit,
  createMergeable,
  createPreferences,
  createRevision,
  createRevisions,
  createServerInfo,
  createUserConfig,
  TEST_NUMERIC_CHANGE_ID,
  TEST_PROJECT_NAME,
  createEditRevision,
  createAccountWithIdNameAndEmail,
  createChangeViewChange,
  createRelatedChangeAndCommitInfo,
  createAccountDetailWithId,
  createParsedChange,
} from '../../../test/test-data-generators';
import {ChangeViewPatchRange, GrChangeView} from './gr-change-view';
import {
  AccountId,
  ApprovalInfo,
  BasePatchSetNum,
  ChangeId,
  ChangeInfo,
  CommitId,
  EditPatchSetNum,
  NumericChangeId,
  ParentPatchSetNum,
  PatchRange,
  PatchSetNum,
  RelatedChangeAndCommitInfo,
  ReviewInputTag,
  RevisionInfo,
  RevisionPatchSetNum,
  RobotId,
  RobotCommentInfo,
  Timestamp,
  UrlEncodedCommentId,
} from '../../../types/common';
import {
  pressAndReleaseKeyOn,
  tap,
} from '@polymer/iron-test-helpers/mock-interactions';
import {GrEditControls} from '../../edit/gr-edit-controls/gr-edit-controls';
import {AppElementChangeViewParams} from '../../gr-app-types';
import {SinonFakeTimers, SinonStubbedMember} from 'sinon';
import {RestApiService} from '../../../services/gr-rest-api/gr-rest-api';
import {CommentThread} from '../../../utils/comment-util';
import {GerritView} from '../../../services/router/router-model';
import {ParsedChangeInfo} from '../../../types/types';
import {GrRelatedChangesList} from '../gr-related-changes-list/gr-related-changes-list';
import {ChangeStates} from '../../shared/gr-change-status/gr-change-status';
import {LoadingStatus} from '../../../models/change/change-model';
import {FocusTarget, GrReplyDialog} from '../gr-reply-dialog/gr-reply-dialog';
import {GrOverlay} from '../../shared/gr-overlay/gr-overlay';
import {GrChangeStar} from '../../shared/gr-change-star/gr-change-star';
import {GrThreadList} from '../gr-thread-list/gr-thread-list';

const fixture = fixtureFromElement('gr-change-view');

suite('gr-change-view tests', () => {
  let element: GrChangeView;

  let navigateToChangeStub: SinonStubbedMember<
    typeof GerritNav.navigateToChange
  >;

  const ROBOT_COMMENTS_LIMIT = 10;

  // TODO: should have a mock service to generate VALID fake data
  const THREADS: CommentThread[] = [
    {
      comments: [
        {
          path: '/COMMIT_MSG',
          author: {
            _account_id: 1000000 as AccountId,
            name: 'user',
            username: 'user',
          },
          patch_set: 2 as PatchSetNum,
          robot_id: 'rb1' as RobotId,
          id: 'ecf0b9fa_fe1a5f62' as UrlEncodedCommentId,
          line: 5,
          updated: '2018-02-08 18:49:18.000000000' as Timestamp,
          message: 'test',
          unresolved: true,
        },
        {
          path: '/COMMIT_MSG',
          author: {
            _account_id: 1000000 as AccountId,
            name: 'user',
            username: 'user',
          },
          patch_set: 4 as PatchSetNum,
          id: 'ecf0b9fa_fe1a5f62_1' as UrlEncodedCommentId,
          line: 5,
          updated: '2018-02-08 18:49:18.000000000' as Timestamp,
          message: 'test',
          unresolved: true,
        },
        {
          id: '503008e2_0ab203ee' as UrlEncodedCommentId,
          path: '/COMMIT_MSG',
          line: 5,
          in_reply_to: 'ecf0b9fa_fe1a5f62' as UrlEncodedCommentId,
          updated: '2018-02-13 22:48:48.018000000' as Timestamp,
          message: 'draft',
          unresolved: false,
          __draft: true,
          patch_set: 2 as PatchSetNum,
        },
      ],
      patchNum: 4 as RevisionPatchSetNum,
      path: '/COMMIT_MSG',
      line: 5,
      rootId: 'ecf0b9fa_fe1a5f62' as UrlEncodedCommentId,
      commentSide: CommentSide.REVISION,
    },
    {
      comments: [
        {
          path: '/COMMIT_MSG',
          author: {
            _account_id: 1000000 as AccountId,
            name: 'user',
            username: 'user',
          },
          patch_set: 3 as PatchSetNum,
          id: 'ecf0b9fa_fe5f62' as UrlEncodedCommentId,
          robot_id: 'rb2' as RobotId,
          line: 5,
          updated: '2018-02-08 18:49:18.000000000' as Timestamp,
          message: 'test',
          unresolved: true,
        },
        {
          path: 'test.txt',
          author: {
            _account_id: 1000000 as AccountId,
            name: 'user',
            username: 'user',
          },
          patch_set: 3 as PatchSetNum,
          id: '09a9fb0a_1484e6cf' as UrlEncodedCommentId,
          side: CommentSide.PARENT,
          updated: '2018-02-13 22:47:19.000000000' as Timestamp,
          message: 'Some comment on another patchset.',
          unresolved: false,
        },
      ],
      patchNum: 3 as PatchSetNum,
      path: 'test.txt',
      rootId: '09a9fb0a_1484e6cf' as UrlEncodedCommentId,
      commentSide: CommentSide.PARENT,
    },
    {
      comments: [
        {
          path: '/COMMIT_MSG',
          author: {
            _account_id: 1000000 as AccountId,
            name: 'user',
            username: 'user',
          },
          patch_set: 2 as PatchSetNum,
          id: '8caddf38_44770ec1' as UrlEncodedCommentId,
          line: 4,
          updated: '2018-02-13 22:48:40.000000000' as Timestamp,
          message: 'Another unresolved comment',
          unresolved: true,
        },
      ],
      patchNum: 2 as RevisionPatchSetNum,
      path: '/COMMIT_MSG',
      line: 4,
      rootId: '8caddf38_44770ec1' as UrlEncodedCommentId,
      commentSide: CommentSide.REVISION,
    },
    {
      comments: [
        {
          path: '/COMMIT_MSG',
          author: {
            // eslint-disable-next-line @typescript-eslint/no-unnecessary-type-assertion
            _account_id: 1000000 as AccountId,
            name: 'user',
            username: 'user',
          },
          patch_set: 2 as PatchSetNum,
          id: 'scaddf38_44770ec1' as UrlEncodedCommentId,
          line: 4,
          updated: '2018-02-14 22:48:40.000000000' as Timestamp,
          message: 'Yet another unresolved comment',
          unresolved: true,
        },
      ],
      patchNum: 2 as RevisionPatchSetNum,
      path: '/COMMIT_MSG',
      line: 4,
      rootId: 'scaddf38_44770ec1' as UrlEncodedCommentId,
      commentSide: CommentSide.REVISION,
    },
    {
      comments: [
        {
          id: 'zcf0b9fa_fe1a5f62' as UrlEncodedCommentId,
          path: '/COMMIT_MSG',
          line: 6,
          updated: '2018-02-15 22:48:48.018000000' as Timestamp,
          message: 'resolved draft',
          unresolved: false,
          __draft: true,
          patch_set: 2 as PatchSetNum,
        },
      ],
      patchNum: 4 as RevisionPatchSetNum,
      path: '/COMMIT_MSG',
      line: 6,
      rootId: 'zcf0b9fa_fe1a5f62' as UrlEncodedCommentId,
      commentSide: CommentSide.REVISION,
    },
    {
      comments: [
        {
          path: '/COMMIT_MSG',
          author: {
            _account_id: 1000000 as AccountId,
            name: 'user',
            username: 'user',
          },
          patch_set: 4 as PatchSetNum,
          id: 'rc1' as UrlEncodedCommentId,
          line: 5,
          updated: '2019-02-08 18:49:18.000000000' as Timestamp,
          message: 'test',
          unresolved: true,
          robot_id: 'rc1' as RobotId,
        },
      ],
      patchNum: 4 as RevisionPatchSetNum,
      path: '/COMMIT_MSG',
      line: 5,
      rootId: 'rc1' as UrlEncodedCommentId,
      commentSide: CommentSide.REVISION,
    },
    {
      comments: [
        {
          path: '/COMMIT_MSG',
          author: {
            _account_id: 1000000 as AccountId,
            name: 'user',
            username: 'user',
          },
          patch_set: 4 as PatchSetNum,
          id: 'rc2' as UrlEncodedCommentId,
          line: 5,
          updated: '2019-03-08 18:49:18.000000000' as Timestamp,
          message: 'test',
          unresolved: true,
          robot_id: 'rc2' as RobotId,
        },
        {
          path: '/COMMIT_MSG',
          author: {
            _account_id: 1000000 as AccountId,
            name: 'user',
            username: 'user',
          },
          patch_set: 4 as PatchSetNum,
          id: 'c2_1' as UrlEncodedCommentId,
          line: 5,
          updated: '2019-03-08 18:49:18.000000000' as Timestamp,
          message: 'test',
          unresolved: true,
        },
      ],
      patchNum: 4 as RevisionPatchSetNum,
      path: '/COMMIT_MSG',
      line: 5,
      rootId: 'rc2' as UrlEncodedCommentId,
      commentSide: CommentSide.REVISION,
    },
  ];

  setup(() => {
    // Since pluginEndpoints are global, must reset state.
    _testOnly_resetEndpoints();
    navigateToChangeStub = sinon.stub(GerritNav, 'navigateToChange');

    stubRestApi('getConfig').returns(
      Promise.resolve({
        ...createServerInfo(),
        user: {
          ...createUserConfig(),
          anonymous_coward_name: 'test coward name',
        },
      })
    );
    stubRestApi('getAccount').returns(
      Promise.resolve(createAccountDetailWithId(5))
    );
    stubRestApi('getDiffComments').returns(Promise.resolve({}));
    stubRestApi('getDiffRobotComments').returns(Promise.resolve({}));
    stubRestApi('getDiffDrafts').returns(Promise.resolve({}));
    element = fixture.instantiate();
    element._changeNum = TEST_NUMERIC_CHANGE_ID;
    sinon.stub(element.$.actions, 'reload').returns(Promise.resolve());
    getPluginLoader().loadPlugins([]);
    window.Gerrit.install(
      plugin => {
        plugin.registerDynamicCustomComponent(
          'change-view-tab-header',
          'gr-checks-change-view-tab-header-view'
        );
        plugin.registerDynamicCustomComponent(
          'change-view-tab-content',
          'gr-checks-view'
        );
      },
      '0.1',
      'http://some/plugins/url.js'
    );
  });

  teardown(async () => {
    await flush();
  });

  test('_handleMessageAnchorTap', () => {
    element._changeNum = 1 as NumericChangeId;
    element._patchRange = {
      basePatchNum: ParentPatchSetNum,
      patchNum: 1 as RevisionPatchSetNum,
    };
    element._change = createChangeViewChange();
    const getUrlStub = sinon.stub(GerritNav, 'getUrlForChange');
    const replaceStateStub = sinon.stub(history, 'replaceState');
    element._handleMessageAnchorTap(
      new CustomEvent('message-anchor-tap', {detail: {id: 'a12345'}})
    );

    assert.equal(getUrlStub.lastCall.args[1]!.messageHash, '#message-a12345');
    assert.isTrue(replaceStateStub.called);
  });

  test('_handleDiffAgainstBase', () => {
    element._change = {
      ...createChangeViewChange(),
      revisions: createRevisions(10),
    };
    element._patchRange = {
      patchNum: 3 as RevisionPatchSetNum,
      basePatchNum: 1 as BasePatchSetNum,
    };
    element._handleDiffAgainstBase();
    assert(navigateToChangeStub.called);
    const args = navigateToChangeStub.getCall(0).args;
    assert.equal(args[0], element._change);
    assert.equal(args[1]!.patchNum, 3 as PatchSetNum);
  });

  test('_handleDiffAgainstLatest', () => {
    element._change = {
      ...createChangeViewChange(),
      revisions: createRevisions(10),
    };
    element._patchRange = {
      basePatchNum: 1 as BasePatchSetNum,
      patchNum: 3 as RevisionPatchSetNum,
    };
    element._handleDiffAgainstLatest();
    assert(navigateToChangeStub.called);
    const args = navigateToChangeStub.getCall(0).args;
    assert.equal(args[0], element._change);
    assert.equal(args[1]!.patchNum, 10 as PatchSetNum);
    assert.equal(args[1]!.basePatchNum, 1 as BasePatchSetNum);
  });

  test('_handleDiffBaseAgainstLeft', () => {
    element._change = {
      ...createChangeViewChange(),
      revisions: createRevisions(10),
    };
    element._patchRange = {
      patchNum: 3 as RevisionPatchSetNum,
      basePatchNum: 1 as BasePatchSetNum,
    };
    element._handleDiffBaseAgainstLeft();
    assert(navigateToChangeStub.called);
    const args = navigateToChangeStub.getCall(0).args;
    assert.equal(args[0], element._change);
    assert.equal(args[1]!.patchNum, 1 as PatchSetNum);
  });

  test('_handleDiffRightAgainstLatest', () => {
    element._change = {
      ...createChangeViewChange(),
      revisions: createRevisions(10),
    };
    element._patchRange = {
      basePatchNum: 1 as BasePatchSetNum,
      patchNum: 3 as RevisionPatchSetNum,
    };
    element._handleDiffRightAgainstLatest();
    assert(navigateToChangeStub.called);
    const args = navigateToChangeStub.getCall(0).args;
    assert.equal(args[1]!.patchNum, 10 as PatchSetNum);
    assert.equal(args[1]!.basePatchNum, 3 as BasePatchSetNum);
  });

  test('_handleDiffBaseAgainstLatest', () => {
    element._change = {
      ...createChangeViewChange(),
      revisions: createRevisions(10),
    };
    element._patchRange = {
      basePatchNum: 1 as BasePatchSetNum,
      patchNum: 3 as RevisionPatchSetNum,
    };
    element._handleDiffBaseAgainstLatest();
    assert(navigateToChangeStub.called);
    const args = navigateToChangeStub.getCall(0).args;
    assert.equal(args[1]!.patchNum, 10 as PatchSetNum);
    assert.isNotOk(args[1]!.basePatchNum);
  });

  test('toggle attention set status', async () => {
    element._change = {
      ...createChangeViewChange(),
      revisions: createRevisions(10),
    };
    const addToAttentionSetStub = stubRestApi('addToAttentionSet').returns(
      Promise.resolve(new Response())
    );

    const removeFromAttentionSetStub = stubRestApi(
      'removeFromAttentionSet'
    ).returns(Promise.resolve(new Response()));
    element._patchRange = {
      basePatchNum: 1 as BasePatchSetNum,
      patchNum: 3 as RevisionPatchSetNum,
    };

    assert.isNotOk(element._change.attention_set);
    await element._getLoggedIn();
    await element.restApiService.getAccount();
    element._handleToggleAttentionSet();
    assert.isTrue(addToAttentionSetStub.called);
    assert.isFalse(removeFromAttentionSetStub.called);

    element._handleToggleAttentionSet();
    assert.isTrue(removeFromAttentionSetStub.called);
  });

  suite('plugins adding to file tab', () => {
    setup(async () => {
      element._changeNum = TEST_NUMERIC_CHANGE_ID;
      // Resolving it here instead of during setup() as other tests depend
      // on flush() not being called during setup.
      await flush();
    });

    test('plugin added tab shows up as a dynamic endpoint', () => {
      assert(
        element._dynamicTabHeaderEndpoints.includes(
          'change-view-tab-header-url'
        )
      );
      const primaryTabs = element.shadowRoot!.querySelector('#primaryTabs')!;
      const paperTabs = primaryTabs.querySelectorAll<HTMLElement>('paper-tab');
      // 4 Tabs are : Files, Comment Threads, Plugin, Findings
      assert.equal(primaryTabs.querySelectorAll('paper-tab').length, 4);
      assert.equal(paperTabs[2].dataset.name, 'change-view-tab-header-url');
    });

    test('_setActivePrimaryTab switched tab correctly', async () => {
      element._setActivePrimaryTab(
        new CustomEvent('', {
          detail: {tab: 'change-view-tab-header-url'},
        })
      );
      await flush();
      assert.equal(element._activeTabs[0], 'change-view-tab-header-url');
    });

    test('show-primary-tab switched primary tab correctly', async () => {
      element.dispatchEvent(
        new CustomEvent('show-primary-tab', {
          composed: true,
          bubbles: true,
          detail: {
            tab: 'change-view-tab-header-url',
          },
        })
      );
      await flush();
      assert.equal(element._activeTabs[0], 'change-view-tab-header-url');
    });

    test('param change should switch primary tab correctly', async () => {
      assert.equal(element._activeTabs[0], PrimaryTab.FILES);
      // view is required
      element._changeNum = undefined;
      element.params = {
        ...createAppElementChangeViewParams(),
        ...element.params,
        tab: PrimaryTab.FINDINGS,
      };
      await flush();
      assert.equal(element._activeTabs[0], PrimaryTab.FINDINGS);
    });

    test('invalid param change should not switch primary tab', async () => {
      assert.equal(element._activeTabs[0], PrimaryTab.FILES);
      // view is required
      element.params = {
        ...createAppElementChangeViewParams(),
        ...element.params,
        tab: 'random',
      };
      await flush();
      assert.equal(element._activeTabs[0], PrimaryTab.FILES);
    });

    test('switching tab sets _selectedTabPluginEndpoint', async () => {
      const paperTabs = element.shadowRoot!.querySelector('#primaryTabs')!;
      tap(paperTabs.querySelectorAll('paper-tab')[2]);
      await flush();
      assert.equal(
        element._selectedTabPluginEndpoint,
        'change-view-tab-content-url'
      );
    });
  });

  suite('keyboard shortcuts', () => {
    let clock: SinonFakeTimers;
    setup(() => {
      clock = sinon.useFakeTimers();
    });

    teardown(() => {
      clock.restore();
      sinon.restore();
    });

    test('t to add topic', () => {
      const editStub = sinon.stub(element.$.metadata, 'editTopic');
      pressAndReleaseKeyOn(element, 83, null, 't');
      assert(editStub.called);
    });

    test('S should toggle the CL star', () => {
      const starStub = sinon.stub(element.$.changeStar, 'toggleStar');
      pressAndReleaseKeyOn(element, 83, null, 's');
      assert(starStub.called);
    });

    test('toggle star is throttled', () => {
      const starStub = sinon.stub(element.$.changeStar, 'toggleStar');
      pressAndReleaseKeyOn(element, 83, null, 's');
      assert(starStub.called);
      pressAndReleaseKeyOn(element, 83, null, 's');
      assert.equal(starStub.callCount, 1);
      clock.tick(1000);
      pressAndReleaseKeyOn(element, 83, null, 's');
      assert.equal(starStub.callCount, 2);
    });

    test('U should navigate to root if no backPage set', () => {
      const relativeNavStub = sinon.stub(GerritNav, 'navigateToRelativeUrl');
      pressAndReleaseKeyOn(element, 85, null, 'u');
      assert.isTrue(relativeNavStub.called);
      assert.isTrue(
        relativeNavStub.lastCall.calledWithExactly(GerritNav.getUrlForRoot())
      );
    });

    test('U should navigate to backPage if set', () => {
      const relativeNavStub = sinon.stub(GerritNav, 'navigateToRelativeUrl');
      element.backPage = '/dashboard/self';
      pressAndReleaseKeyOn(element, 85, null, 'u');
      assert.isTrue(relativeNavStub.called);
      assert.isTrue(
        relativeNavStub.lastCall.calledWithExactly('/dashboard/self')
      );
    });

    test('A fires an error event when not logged in', async () => {
      sinon.stub(element, '_getLoggedIn').returns(Promise.resolve(false));
      const loggedInErrorSpy = sinon.spy();
      element.addEventListener('show-auth-required', loggedInErrorSpy);
      pressAndReleaseKeyOn(element, 65, null, 'a');
      await flush();
      assert.isFalse(element.$.replyOverlay.opened);
      assert.isTrue(loggedInErrorSpy.called);
    });

    test('shift A does not open reply overlay', async () => {
      sinon.stub(element, '_getLoggedIn').returns(Promise.resolve(true));
      pressAndReleaseKeyOn(element, 65, 'shift', 'a');
      await flush();
      assert.isFalse(element.$.replyOverlay.opened);
    });

    test('A toggles overlay when logged in', async () => {
      sinon.stub(element, '_getLoggedIn').returns(Promise.resolve(true));
      element._change = {
        ...createChangeViewChange(),
        revisions: createRevisions(1),
        messages: createChangeMessages(1),
      };
      element._change.labels = {};

      const openSpy = sinon.spy(element, '_openReplyDialog');

      pressAndReleaseKeyOn(element, 65, null, 'a');
      await flush();
      assert.isTrue(element.$.replyOverlay.opened);
      element.$.replyOverlay.close();
      assert.isFalse(element.$.replyOverlay.opened);
      assert(
        openSpy.lastCall.calledWithExactly(FocusTarget.ANY),
        '_openReplyDialog should have been passed ANY'
      );
      assert.equal(openSpy.callCount, 1);
    });

    test('fullscreen-overlay-opened hides content', () => {
      element._loggedIn = true;
      element._loading = false;
      element._change = {
        ...createChangeViewChange(),
        labels: {},
        actions: {
          abandon: {
            enabled: true,
            label: 'Abandon',
            method: HttpMethod.POST,
            title: 'Abandon',
          },
        },
      };
      const handlerSpy = sinon.spy(element, '_handleHideBackgroundContent');
      const overlay = queryAndAssert<GrOverlay>(element, '#replyOverlay');
      overlay.dispatchEvent(
        new CustomEvent('fullscreen-overlay-opened', {
          composed: true,
          bubbles: true,
        })
      );
      assert.isTrue(handlerSpy.called);
      assert.isTrue(element.$.mainContent.classList.contains('overlayOpen'));
      assert.equal(getComputedStyle(element.$.actions).display, 'flex');
    });

    test('fullscreen-overlay-closed shows content', () => {
      element._loggedIn = true;
      element._loading = false;
      element._change = {
        ...createChangeViewChange(),
        labels: {},
        actions: {
          abandon: {
            enabled: true,
            label: 'Abandon',
            method: HttpMethod.POST,
            title: 'Abandon',
          },
        },
      };
      const handlerSpy = sinon.spy(element, '_handleShowBackgroundContent');
      const overlay = queryAndAssert<GrOverlay>(element, '#replyOverlay');
      overlay.dispatchEvent(
        new CustomEvent('fullscreen-overlay-closed', {
          composed: true,
          bubbles: true,
        })
      );
      assert.isTrue(handlerSpy.called);
      assert.isFalse(element.$.mainContent.classList.contains('overlayOpen'));
    });

    test('expand all messages when expand-diffs fired', () => {
      const handleExpand = sinon.stub(element.$.fileList, 'expandAllDiffs');
      element.$.fileListHeader.dispatchEvent(
        new CustomEvent('expand-diffs', {
          composed: true,
          bubbles: true,
        })
      );
      assert.isTrue(handleExpand.called);
    });

    test('collapse all messages when collapse-diffs fired', () => {
      const handleCollapse = sinon.stub(element.$.fileList, 'collapseAllDiffs');
      element.$.fileListHeader.dispatchEvent(
        new CustomEvent('collapse-diffs', {
          composed: true,
          bubbles: true,
        })
      );
      assert.isTrue(handleCollapse.called);
    });

    test('X should expand all messages', async () => {
      await flush();
      const handleExpand = sinon.stub(
        element.messagesList!,
        'handleExpandCollapse'
      );
      pressAndReleaseKeyOn(element, 88, null, 'x');
      assert(handleExpand.calledWith(true));
    });

    test('Z should collapse all messages', async () => {
      await flush();
      const handleExpand = sinon.stub(
        element.messagesList!,
        'handleExpandCollapse'
      );
      pressAndReleaseKeyOn(element, 90, null, 'z');
      assert(handleExpand.calledWith(false));
    });

    test('d should open download overlay', () => {
      const stub = sinon
        .stub(element.$.downloadOverlay, 'open')
        .returns(Promise.resolve());
      pressAndReleaseKeyOn(element, 68, null, 'd');
      assert.isTrue(stub.called);
    });

    test(', should open diff preferences', () => {
      const stub = sinon.stub(
        element.$.fileList.diffPreferencesDialog,
        'open'
      );
      element._loggedIn = false;
      pressAndReleaseKeyOn(element, 188, null, ',');
      assert.isFalse(stub.called);

      element._loggedIn = true;
      pressAndReleaseKeyOn(element, 188, null, ',');
      assert.isTrue(stub.called);
    });

    test('m should toggle diff mode', async () => {
      const updatePreferencesStub = stubUsers('updatePreferences');
      await flush();

      const prefs = {
        ...createDefaultPreferences(),
        diff_view: DiffViewMode.SIDE_BY_SIDE,
      };
      element.userModel.setPreferences(prefs);
      element._handleToggleDiffMode();
      assert.isTrue(
        updatePreferencesStub.calledWith({diff_view: DiffViewMode.UNIFIED})
      );

      const newPrefs = {
        ...createDefaultPreferences(),
        diff_view: DiffViewMode.UNIFIED,
      };
      element.userModel.setPreferences(newPrefs);
      await flush();
      element._handleToggleDiffMode();
      assert.isTrue(
        updatePreferencesStub.calledWith({diff_view: DiffViewMode.SIDE_BY_SIDE})
      );
    });
  });

  suite('thread list and change log tabs', () => {
    setup(() => {
      element._changeNum = TEST_NUMERIC_CHANGE_ID;
      element._patchRange = {
        basePatchNum: ParentPatchSetNum,
        patchNum: 1 as RevisionPatchSetNum,
      };
      element._change = {
        ...createChangeViewChange(),
        revisions: {
          rev2: createRevision(2),
          rev1: createRevision(1),
          rev13: createRevision(13),
          rev3: createRevision(3),
        },
        current_revision: 'rev3' as CommitId,
        status: ChangeStatus.NEW,
        labels: {
          test: {
            all: [],
            default_value: 0,
            values: {},
            approved: {},
          },
        },
      };
      const relatedChanges = element.shadowRoot!.querySelector(
        '#relatedChanges'
      ) as GrRelatedChangesList;
      sinon.stub(relatedChanges, 'reload');
      sinon.stub(element, 'loadData').returns(Promise.resolve());
      sinon.spy(element, '_paramsChanged');
      element.params = createAppElementChangeViewParams();
    });
  });

  suite('Comments tab', () => {
    setup(async () => {
      element._changeNum = TEST_NUMERIC_CHANGE_ID;
      element._change = {
        ...createChangeViewChange(),
        revisions: {
          rev2: createRevision(2),
          rev1: createRevision(1),
          rev13: createRevision(13),
          rev3: createRevision(3),
          rev4: createRevision(4),
        },
        current_revision: 'rev4' as CommitId,
      };
      element._commentThreads = THREADS;
      await flush();
      const paperTabs = element.shadowRoot!.querySelector('#primaryTabs')!;
      tap(paperTabs.querySelectorAll('paper-tab')[1]);
      await flush();
    });

    test('commentId overrides unresolveOnly default', async () => {
      const threadList = queryAndAssert<GrThreadList>(
        element,
        'gr-thread-list'
      );
      assert.isTrue(element.unresolvedOnly);
      assert.isNotOk(element.scrollCommentId);
      assert.isTrue(threadList.unresolvedOnly);

      element.scrollCommentId = 'abcd' as UrlEncodedCommentId;
      await flush();
      assert.isFalse(threadList.unresolvedOnly);
    });
  });

  suite('Findings robot-comment tab', () => {
    setup(async () => {
      element._changeNum = TEST_NUMERIC_CHANGE_ID;
      element._change = {
        ...createChangeViewChange(),
        revisions: {
          rev2: createRevision(2),
          rev1: createRevision(1),
          rev13: createRevision(13),
          rev3: createRevision(3),
          rev4: createRevision(4),
        },
        current_revision: 'rev4' as CommitId,
      };
      element._commentThreads = THREADS;
      await flush();
      const paperTabs = element.shadowRoot!.querySelector('#primaryTabs')!;
      tap(paperTabs.querySelectorAll('paper-tab')[3]);
      await flush();
    });

    test('robot comments count per patchset', () => {
      const count = element._robotCommentCountPerPatchSet(THREADS);
      const expectedCount = {
        2: 1,
        3: 1,
        4: 2,
      };
      assert.deepEqual(count, expectedCount);
      assert.equal(
        element._computeText(createRevision(2), THREADS),
        'Patchset 2 (1 finding)'
      );
      assert.equal(
        element._computeText(createRevision(4), THREADS),
        'Patchset 4 (2 findings)'
      );
      assert.equal(
        element._computeText(createRevision(5), THREADS),
        'Patchset 5'
      );
    });

    test('only robot comments are rendered', () => {
      assert.equal(element._robotCommentThreads!.length, 2);
      assert.equal(
        (element._robotCommentThreads![0].comments[0] as RobotCommentInfo)
          .robot_id,
        'rc1'
      );
      assert.equal(
        (element._robotCommentThreads![1].comments[0] as RobotCommentInfo)
          .robot_id,
        'rc2'
      );
    });

    test('changing patchsets resets robot comments', async () => {
      element.set('_change.current_revision', 'rev3');
      await flush();
      assert.equal(element._robotCommentThreads!.length, 1);
    });

    test('Show more button is hidden', () => {
      assert.isNull(element.shadowRoot!.querySelector('.show-robot-comments'));
    });

    suite('robot comments show more button', () => {
      setup(async () => {
        const arr = [];
        for (let i = 0; i <= 30; i++) {
          arr.push(...THREADS);
        }
        element._commentThreads = arr;
        await flush();
      });

      test('Show more button is rendered', () => {
        assert.isOk(element.shadowRoot!.querySelector('.show-robot-comments'));
        assert.equal(
          element._robotCommentThreads!.length,
          ROBOT_COMMENTS_LIMIT
        );
      });

      test('Clicking show more button renders all comments', async () => {
        tap(element.shadowRoot!.querySelector('.show-robot-comments')!);
        await flush();
        assert.equal(element._robotCommentThreads!.length, 62);
      });
    });
  });

  test('reply button is not visible when logged out', () => {
    assert.equal(getComputedStyle(element.$.replyBtn).display, 'none');
    element._loggedIn = true;
    assert.notEqual(getComputedStyle(element.$.replyBtn).display, 'none');
  });

  test('download tap calls _handleOpenDownloadDialog', () => {
    const openDialogStub = sinon.stub(element, '_handleOpenDownloadDialog');
    element.$.actions.dispatchEvent(
      new CustomEvent('download-tap', {
        composed: true,
        bubbles: true,
      })
    );
    assert.isTrue(openDialogStub.called);
  });

  test('fetches the server config on attached', async () => {
    await flush();
    assert.equal(
      element._serverConfig!.user.anonymous_coward_name,
      'test coward name'
    );
  });

  test('_changeStatuses', () => {
    element._loading = false;
    element._change = {
      ...createChangeViewChange(),
      revisions: {
        rev2: createRevision(2),
        rev1: createRevision(1),
        rev13: createRevision(13),
        rev3: createRevision(3),
      },
      current_revision: 'rev3' as CommitId,
      status: ChangeStatus.MERGED,
      work_in_progress: true,
      labels: {
        test: {
          all: [],
          default_value: 0,
          values: {},
          approved: {},
        },
      },
    };
    element._mergeable = true;
    const expectedStatuses = [ChangeStates.MERGED, ChangeStates.WIP];
    assert.deepEqual(element._changeStatuses, expectedStatuses);
    flush();
    const statusChips =
      element.shadowRoot!.querySelectorAll('gr-change-status');
    assert.equal(statusChips.length, 2);
  });

  suite('ChangeStatus revert', () => {
    test('do not show any chip if no revert created', async () => {
      const change = {
        ...createParsedChange(),
        messages: createChangeMessages(2),
      };
      const getChangeStub = stubRestApi('getChange');
      getChangeStub.onFirstCall().returns(
        Promise.resolve({
          ...createChange(),
        })
      );
      getChangeStub.onSecondCall().returns(
        Promise.resolve({
          ...createChange(),
        })
      );
      element._change = change;
      element._mergeable = true;
      element._submitEnabled = true;
      await flush();
      element.computeRevertSubmitted(element._change);
      await flush();
      assert.isFalse(
        element._changeStatuses?.includes(ChangeStates.REVERT_SUBMITTED)
      );
      assert.isFalse(
        element._changeStatuses?.includes(ChangeStates.REVERT_CREATED)
      );
    });

    test('do not show any chip if all reverts are abandoned', async () => {
      const change = {
        ...createParsedChange(),
        messages: createChangeMessages(2),
      };
      change.messages[0].message = 'Created a revert of this change as 12345';
      change.messages[0].tag = MessageTag.TAG_REVERT as ReviewInputTag;

      change.messages[1].message = 'Created a revert of this change as 23456';
      change.messages[1].tag = MessageTag.TAG_REVERT as ReviewInputTag;

      const getChangeStub = stubRestApi('getChange');
      getChangeStub.onFirstCall().returns(
        Promise.resolve({
          ...createChange(),
          status: ChangeStatus.ABANDONED,
        })
      );
      getChangeStub.onSecondCall().returns(
        Promise.resolve({
          ...createChange(),
          status: ChangeStatus.ABANDONED,
        })
      );
      element._change = change;
      element._mergeable = true;
      element._submitEnabled = true;
      await flush();
      element.computeRevertSubmitted(element._change);
      await flush();
      assert.isFalse(
        element._changeStatuses?.includes(ChangeStates.REVERT_SUBMITTED)
      );
      assert.isFalse(
        element._changeStatuses?.includes(ChangeStates.REVERT_CREATED)
      );
    });

    test('show revert created if no revert is merged', async () => {
      const change = {
        ...createParsedChange(),
        messages: createChangeMessages(2),
      };
      change.messages[0].message = 'Created a revert of this change as 12345';
      change.messages[0].tag = MessageTag.TAG_REVERT as ReviewInputTag;

      change.messages[1].message = 'Created a revert of this change as 23456';
      change.messages[1].tag = MessageTag.TAG_REVERT as ReviewInputTag;

      const getChangeStub = stubRestApi('getChange');
      getChangeStub.onFirstCall().returns(
        Promise.resolve({
          ...createChange(),
        })
      );
      getChangeStub.onSecondCall().returns(
        Promise.resolve({
          ...createChange(),
        })
      );
      element._change = change;
      element._mergeable = true;
      element._submitEnabled = true;
      await flush();
      element.computeRevertSubmitted(element._change);
      await flush();
      assert.isFalse(
        element._changeStatuses?.includes(ChangeStates.REVERT_SUBMITTED)
      );
      assert.isTrue(
        element._changeStatuses?.includes(ChangeStates.REVERT_CREATED)
      );
    });

    test('show revert submitted if revert is merged', async () => {
      const change = {
        ...createParsedChange(),
        messages: createChangeMessages(2),
      };
      change.messages[0].message = 'Created a revert of this change as 12345';
      change.messages[0].tag = MessageTag.TAG_REVERT as ReviewInputTag;
      const getChangeStub = stubRestApi('getChange');
      getChangeStub.onFirstCall().returns(
        Promise.resolve({
          ...createChange(),
          status: ChangeStatus.MERGED,
        })
      );
      getChangeStub.onSecondCall().returns(
        Promise.resolve({
          ...createChange(),
        })
      );
      element._change = change;
      element._mergeable = true;
      element._submitEnabled = true;
      await flush();
      element.computeRevertSubmitted(element._change);
      await flush();
      assert.isFalse(
        element._changeStatuses?.includes(ChangeStates.REVERT_CREATED)
      );
      assert.isTrue(
        element._changeStatuses?.includes(ChangeStates.REVERT_SUBMITTED)
      );
    });
  });

  test('diff preferences open when open-diff-prefs is fired', () => {
    const overlayOpenStub = sinon.stub(element.$.fileList, 'openDiffPrefs');
    element.$.fileListHeader.dispatchEvent(
      new CustomEvent('open-diff-prefs', {
        composed: true,
        bubbles: true,
      })
    );
    assert.isTrue(overlayOpenStub.called);
  });

  test('_prepareCommitMsgForLinkify', () => {
    let commitMessage = 'R=test@google.com';
    let result = element._prepareCommitMsgForLinkify(commitMessage);
    assert.equal(result, 'R=\u200Btest@google.com');

    commitMessage = 'R=test@google.com\nR=test@google.com';
    result = element._prepareCommitMsgForLinkify(commitMessage);
    assert.equal(result, 'R=\u200Btest@google.com\nR=\u200Btest@google.com');

    commitMessage = 'CC=test@google.com';
    result = element._prepareCommitMsgForLinkify(commitMessage);
    assert.equal(result, 'CC=\u200Btest@google.com');
  });

  test('_isSubmitEnabled', () => {
    assert.isFalse(element._isSubmitEnabled({}));
    assert.isFalse(element._isSubmitEnabled({submit: {}}));
    assert.isTrue(element._isSubmitEnabled({submit: {enabled: true}}));
  });

  test('_reload is called when an approved label is removed', () => {
    const vote: ApprovalInfo = {
      ...createApproval(),
      _account_id: 1 as AccountId,
      name: 'bojack',
      value: 1,
    };
    element._changeNum = TEST_NUMERIC_CHANGE_ID;
    element._patchRange = {
      basePatchNum: ParentPatchSetNum,
      patchNum: 1 as RevisionPatchSetNum,
    };
    const change = {
      ...createChangeViewChange(),
      owner: createAccountWithIdNameAndEmail(),
      revisions: {
        rev2: createRevision(2),
        rev1: createRevision(1),
        rev13: createRevision(13),
        rev3: createRevision(3),
      },
      current_revision: 'rev3' as CommitId,
      status: ChangeStatus.NEW,
      labels: {
        test: {
          all: [vote],
          default_value: 0,
          values: {},
          approved: {},
        },
      },
    };
    element._change = change;
    flush();
    const reloadStub = sinon.stub(element, 'loadData');
    element.splice('_change.labels.test.all', 0, 1);
    assert.isFalse(reloadStub.called);
    change.labels.test.all.push(vote);
    change.labels.test.all.push(vote);
    change.labels.test.approved = vote;
    flush();
    element.splice('_change.labels.test.all', 0, 2);
    assert.isTrue(reloadStub.called);
    assert.isTrue(reloadStub.calledOnce);
  });

  test('reply button has updated count when there are drafts', () => {
    const getLabel = element._computeReplyButtonLabel;

    assert.equal(getLabel(undefined, false), 'Reply');
    assert.equal(getLabel(undefined, true), 'Reply');

    let drafts = {};
    assert.equal(getLabel(drafts, false), 'Reply');

    drafts = {
      'file1.txt': [{}],
      'file2.txt': [{}, {}],
    };
    assert.equal(getLabel(drafts, false), 'Reply (3)');
    assert.equal(getLabel(drafts, true), 'Start Review (3)');
  });

  test('change num change', () => {
    const change = {
      ...createChangeViewChange(),
      labels: {},
    } as ParsedChangeInfo;
    element._changeNum = undefined;
    element._patchRange = {
      basePatchNum: ParentPatchSetNum,
      patchNum: 2 as RevisionPatchSetNum,
    };
    element._change = change;
    element.viewState.changeNum = null;
    element.viewState.diffMode = DiffViewMode.UNIFIED;
    assert.equal(element.viewState.numFilesShown, 200);
    assert.equal(element._numFilesShown, 200);
    element._numFilesShown = 150;
    flush();
    assert.equal(element.viewState.diffMode, DiffViewMode.UNIFIED);
    assert.equal(element.viewState.numFilesShown, 150);

    element._changeNum = 1 as NumericChangeId;
    element.params = {
      ...createAppElementChangeViewParams(),
      changeNum: 1 as NumericChangeId,
    };
    flush();
    assert.equal(element.viewState.diffMode, DiffViewMode.UNIFIED);
    assert.equal(element.viewState.changeNum, 1);

    element._changeNum = 2 as NumericChangeId;
    element.params = {
      ...createAppElementChangeViewParams(),
      changeNum: 2 as NumericChangeId,
    };
    flush();
    assert.equal(element.viewState.diffMode, DiffViewMode.UNIFIED);
    assert.equal(element.viewState.changeNum, 2);
    assert.equal(element.viewState.numFilesShown, 200);
    assert.equal(element._numFilesShown, 200);
  });

  test('don’t reload entire page when patchRange changes', async () => {
    const reloadStub = sinon
      .stub(element, 'loadData')
      .callsFake(() => Promise.resolve());
    const reloadPatchDependentStub = sinon
      .stub(element, '_reloadPatchNumDependentResources')
      .callsFake(() => Promise.resolve([undefined, undefined, undefined]));
    flush();
    const collapseStub = sinon.stub(element.$.fileList, 'collapseAllDiffs');
    const value: AppElementChangeViewParams = {
      ...createAppElementChangeViewParams(),
      view: GerritView.CHANGE,
      patchNum: 1 as RevisionPatchSetNum,
    };
    element._changeNum = undefined;
    element.params = value;
    await flush();
    assert.isTrue(reloadStub.calledOnce);

    element._initialLoadComplete = true;
    element._change = {
      ...createChangeViewChange(),
      revisions: {
        rev1: createRevision(1),
        rev2: createRevision(2),
      },
    };

    value.basePatchNum = 1 as BasePatchSetNum;
    value.patchNum = 2 as RevisionPatchSetNum;
    element.params = {...value};
    await flush();
    assert.isFalse(reloadStub.calledTwice);
    assert.isTrue(reloadPatchDependentStub.calledOnce);
    assert.isTrue(collapseStub.calledTwice);
  });

  test('reload ported comments when patchNum changes', async () => {
    sinon.stub(element, 'loadData').callsFake(() => Promise.resolve());
    sinon.stub(element, 'loadAndSetCommitInfo');
    sinon.stub(element.$.fileList, 'reload');
    flush();
    const reloadPortedCommentsStub = sinon.stub(
      element.getCommentsModel(),
      'reloadPortedComments'
    );
    const reloadPortedDraftsStub = sinon.stub(
      element.getCommentsModel(),
      'reloadPortedDrafts'
    );
    sinon.stub(element.$.fileList, 'collapseAllDiffs');

    const value: AppElementChangeViewParams = {
      ...createAppElementChangeViewParams(),
      view: GerritView.CHANGE,
      patchNum: 1 as RevisionPatchSetNum,
    };
    element.params = value;
    await flush();

    element._initialLoadComplete = true;
    element._change = {
      ...createChangeViewChange(),
      revisions: {
        rev1: createRevision(1),
        rev2: createRevision(2),
      },
    };

    value.basePatchNum = 1 as BasePatchSetNum;
    value.patchNum = 2 as RevisionPatchSetNum;
    element.params = {...value};
    await flush();
    assert.isTrue(reloadPortedCommentsStub.calledOnce);
    assert.isTrue(reloadPortedDraftsStub.calledOnce);
  });

  test('do not reload entire page when patchRange doesnt change', async () => {
    const reloadStub = sinon
      .stub(element, 'loadData')
      .callsFake(() => Promise.resolve());
    const collapseStub = sinon.stub(element.$.fileList, 'collapseAllDiffs');
    const value: AppElementChangeViewParams =
      createAppElementChangeViewParams();
    element.params = value;
    // change already loaded
    assert.isOk(element._changeNum);
    await flush();
    assert.isFalse(reloadStub.calledOnce);
    element._initialLoadComplete = true;
    element.params = {...value};
    await flush();
    assert.isFalse(reloadStub.calledTwice);
    assert.isFalse(collapseStub.calledTwice);
  });

  test('forceReload updates the change', async () => {
    const getChangeStub = stubRestApi('getChangeDetail').returns(
      Promise.resolve(createParsedChange())
    );
    const loadDataStub = sinon
      .stub(element, 'loadData')
      .callsFake(() => Promise.resolve());
    const collapseStub = sinon.stub(element.$.fileList, 'collapseAllDiffs');
    element.params = {...createAppElementChangeViewParams(), forceReload: true};
    await flush();
    assert.isTrue(getChangeStub.called);
    assert.isTrue(loadDataStub.called);
    assert.isTrue(collapseStub.called);
    // patchNum is set by changeChanged, so this verifies that _change was set.
    assert.isOk(element._patchRange?.patchNum);
  });

  test('do not handle new change numbers', async () => {
    const recreateSpy = sinon.spy();
    element.addEventListener('recreate-change-view', recreateSpy);

    const value: AppElementChangeViewParams =
      createAppElementChangeViewParams();
    element.params = value;
    await flush();
    assert.isFalse(recreateSpy.calledOnce);

    value.changeNum = 555111333 as NumericChangeId;
    element.params = {...value};
    await flush();
    assert.isTrue(recreateSpy.calledOnce);
  });

  test('related changes are updated when loadData is called', async () => {
    await flush();
    const relatedChanges = element.shadowRoot!.querySelector(
      '#relatedChanges'
    ) as GrRelatedChangesList;
    const reloadStub = sinon.stub(relatedChanges, 'reload');
    stubRestApi('getMergeable').returns(
      Promise.resolve({...createMergeable(), mergeable: true})
    );

    element.params = createAppElementChangeViewParams();
    element.getChangeModel().setState({
      loadingStatus: LoadingStatus.LOADED,
      change: {
        ...createChangeViewChange(),
      },
    });

    await element.loadData(true);
    assert.isFalse(navigateToChangeStub.called);
    assert.isTrue(reloadStub.called);
  });

  test('_computeCopyTextForTitle', () => {
    const change: ChangeInfo = {
      ...createChangeViewChange(),
      _number: 123 as NumericChangeId,
      subject: 'test subject',
      revisions: {
        rev1: createRevision(1),
        rev3: createRevision(3),
      },
      current_revision: 'rev3' as CommitId,
    };
    sinon.stub(GerritNav, 'getUrlForChange').returns('/change/123');
    assert.equal(
      element._computeCopyTextForTitle(change),
      `123: test subject | http://${location.host}/change/123`
    );
  });

  test('get latest revision', () => {
    let change: ChangeInfo = {
      ...createChange(),
      revisions: {
        rev1: createRevision(1),
        rev3: createRevision(3),
      },
      current_revision: 'rev3' as CommitId,
    };
    assert.equal(element._getLatestRevisionSHA(change), 'rev3');
    change = {
      ...createChange(),
      revisions: {
        rev1: createRevision(1),
      },
      current_revision: undefined,
    };
    assert.equal(element._getLatestRevisionSHA(change), 'rev1');
  });

  test('show commit message edit button', () => {
    const change = createChange();
    const mergedChanged: ChangeInfo = {
      ...createChangeViewChange(),
      status: ChangeStatus.MERGED,
    };
    assert.isTrue(element._computeHideEditCommitMessage(false, false, change));
    assert.isTrue(element._computeHideEditCommitMessage(true, true, change));
    assert.isTrue(element._computeHideEditCommitMessage(false, true, change));
    assert.isFalse(element._computeHideEditCommitMessage(true, false, change));
    assert.isTrue(
      element._computeHideEditCommitMessage(true, false, mergedChanged)
    );
    assert.isTrue(
      element._computeHideEditCommitMessage(true, false, change, true)
    );
    assert.isFalse(
      element._computeHideEditCommitMessage(true, false, change, false)
    );
  });

  test('_handleCommitMessageSave trims trailing whitespace', async () => {
    element._change = createChangeViewChange();
    // Response code is 500, because we want to avoid window reloading
    const putStub = stubRestApi('putChangeCommitMessage').returns(
      Promise.resolve(new Response(null, {status: 500}))
    );

    const mockEvent = (content: string) =>
      new CustomEvent('', {detail: {content}});

    element._handleCommitMessageSave(mockEvent('test \n  test '));
    assert.equal(putStub.lastCall.args[1], 'test\n  test');
    element.$.commitMessageEditor.disabled = false;
    element._handleCommitMessageSave(mockEvent('  test\ntest'));
    assert.equal(putStub.lastCall.args[1], '  test\ntest');
    element.$.commitMessageEditor.disabled = false;
    element._handleCommitMessageSave(mockEvent('\n\n\n\n\n\n\n\n'));
    assert.equal(putStub.lastCall.args[1], '\n\n\n\n\n\n\n\n');
  });

  test('topic is coalesced to null', async () => {
    sinon.stub(element, '_changeChanged');
    element.getChangeModel().setState({
      loadingStatus: LoadingStatus.LOADED,
      change: {
        ...createChangeViewChange(),
        labels: {},
        current_revision: 'foo' as CommitId,
        revisions: {foo: createRevision()},
      },
    });

    await element.performPostChangeLoadTasks();
    assert.isNull(element._change!.topic);
  });

  test('commit sha is populated from getChangeDetail', async () => {
    sinon.stub(element, '_changeChanged');
    element.getChangeModel().setState({
      loadingStatus: LoadingStatus.LOADED,
      change: {
        ...createChangeViewChange(),
        labels: {},
        current_revision: 'foo' as CommitId,
        revisions: {foo: createRevision()},
      },
    });

    await element.performPostChangeLoadTasks();
    assert.equal('foo', element._commitInfo!.commit);
  });

  test('_getBasePatchNum', () => {
    const _change: ChangeInfo = {
      ...createChangeViewChange(),
      revisions: {
        '98da160735fb81604b4c40e93c368f380539dd0e': createRevision(),
      },
    };
    const _patchRange: ChangeViewPatchRange = {
      basePatchNum: ParentPatchSetNum,
    };
    assert.equal(element._getBasePatchNum(_change, _patchRange), 'PARENT');

    element._prefs = {
      ...createPreferences(),
      default_base_for_merges: DefaultBase.FIRST_PARENT,
    };

    const _change2: ChangeInfo = {
      ...createChangeViewChange(),
      revisions: {
        '98da160735fb81604b4c40e93c368f380539dd0e': {
          ...createRevision(1),
          commit: {
            ...createCommit(),
            parents: [
              {
                commit: '6e12bdf1176eb4ab24d8491ba3b6d0704409cde8' as CommitId,
                subject: 'test',
              },
              {
                commit: '22f7db4754b5d9816fc581f3d9a6c0ef8429c841' as CommitId,
                subject: 'test3',
              },
            ],
          },
        },
      },
    };
    assert.equal(element._getBasePatchNum(_change2, _patchRange), -1);

    _patchRange.patchNum = 1 as RevisionPatchSetNum;
    assert.equal(element._getBasePatchNum(_change2, _patchRange), 'PARENT');
  });

  test('_openReplyDialog called with `ANY` when coming from tap event', async () => {
    await flush();
    const openStub = sinon.stub(element, '_openReplyDialog');
    tap(element.$.replyBtn);
    assert(
      openStub.lastCall.calledWithExactly(FocusTarget.ANY),
      '_openReplyDialog should have been passed ANY'
    );
    assert.equal(openStub.callCount, 1);
  });

  test(
    '_openReplyDialog called with `BODY` when coming from message reply' +
      'event',
    async () => {
      await flush();
      const openStub = sinon.stub(element, '_openReplyDialog');
      element.messagesList!.dispatchEvent(
        new CustomEvent('reply', {
          detail: {message: {message: 'text'}},
          composed: true,
          bubbles: true,
        })
      );
      assert.isTrue(openStub.calledOnce);
      assert.equal(openStub.lastCall.args[0], FocusTarget.BODY);
    }
  );

  test('reply dialog focus can be controlled', () => {
    const openStub = sinon.stub(element, '_openReplyDialog');

    const e = new CustomEvent('show-reply-dialog', {
      detail: {value: {ccsOnly: false}},
    });
    element._handleShowReplyDialog(e);
    assert(
      openStub.lastCall.calledWithExactly(FocusTarget.REVIEWERS),
      '_openReplyDialog should have been passed REVIEWERS'
    );
    assert.equal(openStub.callCount, 1);

    e.detail.value = {ccsOnly: true};
    element._handleShowReplyDialog(e);
    assert(
      openStub.lastCall.calledWithExactly(FocusTarget.CCS),
      '_openReplyDialog should have been passed CCS'
    );
    assert.equal(openStub.callCount, 2);
  });

  test('getUrlParameter functionality', () => {
    const locationStub = sinon.stub(element, '_getLocationSearch');

    locationStub.returns('?test');
    assert.equal(element._getUrlParameter('test'), 'test');
    locationStub.returns('?test2=12&test=3');
    assert.equal(element._getUrlParameter('test'), 'test');
    locationStub.returns('');
    assert.isNull(element._getUrlParameter('test'));
    locationStub.returns('?');
    assert.isNull(element._getUrlParameter('test'));
    locationStub.returns('?test2');
    assert.isNull(element._getUrlParameter('test'));
  });

  test('revert dialog opened with revert param', async () => {
    const awaitPluginsLoadedStub = sinon
      .stub(getPluginLoader(), 'awaitPluginsLoaded')
      .callsFake(() => Promise.resolve());

    element._patchRange = {
      basePatchNum: ParentPatchSetNum,
      patchNum: 2 as RevisionPatchSetNum,
    };
    element._change = {
      ...createChangeViewChange(),
      revisions: {
        rev1: createRevision(1),
        rev2: createRevision(2),
      },
      current_revision: 'rev1' as CommitId,
      status: ChangeStatus.MERGED,
      labels: {},
      actions: {},
    };

    sinon.stub(element, '_getUrlParameter').callsFake(param => {
      assert.equal(param, 'revert');
      return param;
    });

    const promise = mockPromise();

    sinon
      .stub(element.$.actions, 'showRevertDialog')
      .callsFake(() => promise.resolve());

    element._maybeShowRevertDialog();
    assert.isTrue(awaitPluginsLoadedStub.called);
    await promise;
  });

  suite('reply dialog tests', () => {
    setup(() => {
      element._change = {
        ...createChangeViewChange(),
        // element has latest info
        revisions: {rev1: createRevision()},
        messages: createChangeMessages(1),
        current_revision: 'rev1' as CommitId,
        labels: {},
      };
    });

    test('show reply dialog on open-reply-dialog event', async () => {
      const openReplyDialogStub = sinon.stub(element, '_openReplyDialog');
      element.dispatchEvent(
        new CustomEvent('open-reply-dialog', {
          composed: true,
          bubbles: true,
          detail: {},
        })
      );
      await flush();
      assert.isTrue(openReplyDialogStub.calledOnce);
    });

    test('reply from comment adds quote text', async () => {
      const e = new CustomEvent('', {
        detail: {message: {message: 'quote text'}},
      });
      element._handleMessageReply(e);
      const dialog = await waitQueryAndAssert<GrReplyDialog>(
        element,
        '#replyDialog'
      );
      const openSpy = sinon.spy(dialog, 'open');
      await flush();
      await waitUntil(() => openSpy.called && !!openSpy.lastCall.args[1]);
      assert.equal(openSpy.lastCall.args[1], '> quote text\n\n');
    });
  });

  test('reply button is disabled until server config is loaded', async () => {
    assert.isTrue(element._replyDisabled);
    // fetches the server config on attached
    await flush();
    assert.isFalse(element._replyDisabled);
  });

  test('header class computation', () => {
    assert.equal(element._computeHeaderClass(), 'header');
    assert.equal(element._computeHeaderClass(true), 'header editMode');
  });

  test('_maybeScrollToMessage', async () => {
    await flush();
    const scrollStub = sinon.stub(element.messagesList!, 'scrollToMessage');

    element._maybeScrollToMessage('');
    assert.isFalse(scrollStub.called);
    element._maybeScrollToMessage('message');
    assert.isFalse(scrollStub.called);
    element._maybeScrollToMessage('#message-TEST');
    assert.isTrue(scrollStub.called);
    assert.equal(scrollStub.lastCall.args[0], 'TEST');
  });

  test('topic update reloads related changes', () => {
    flush();
    const relatedChanges = element.shadowRoot!.querySelector(
      '#relatedChanges'
    ) as GrRelatedChangesList;
    const reloadStub = sinon.stub(relatedChanges, 'reload');
    element.dispatchEvent(new CustomEvent('topic-changed'));
    assert.isTrue(reloadStub.calledOnce);
  });

  test('_computeEditMode', () => {
    const callCompute = (
      range: PatchRange,
      params: AppElementChangeViewParams
    ) =>
      element._computeEditMode(
        {base: range, path: '', value: range},
        {base: params, path: '', value: params}
      );
    assert.isTrue(
      callCompute(
        {basePatchNum: ParentPatchSetNum, patchNum: 1 as RevisionPatchSetNum},
        {...createAppElementChangeViewParams(), edit: true}
      )
    );
    assert.isFalse(
      callCompute(
        {basePatchNum: ParentPatchSetNum, patchNum: 1 as RevisionPatchSetNum},
        createAppElementChangeViewParams()
      )
    );
    assert.isTrue(
      callCompute(
        {basePatchNum: 1 as BasePatchSetNum, patchNum: EditPatchSetNum},
        createAppElementChangeViewParams()
      )
    );
  });

  test('_processEdit', () => {
    element._patchRange = {};
    const change: ParsedChangeInfo = {
      ...createChangeViewChange(),
      current_revision: 'foo' as CommitId,
      revisions: {
        foo: {...createRevision()},
      },
    };

    // With no edit, nothing happens.
    element._processEdit(change);
    assert.equal(element._patchRange.patchNum, undefined);

    change.revisions['bar'] = {
      _number: EditPatchSetNum,
      basePatchNum: 1 as BasePatchSetNum,
      commit: {
        ...createCommit(),
        commit: 'bar' as CommitId,
      },
      fetch: {},
    };

    // When edit is set, but not patchNum, then switch to edit ps.
    element._processEdit(change);
    assert.equal(element._patchRange.patchNum, EditPatchSetNum);

    // When edit is set, but patchNum as well, then keep patchNum.
    element._patchRange.patchNum = 5 as RevisionPatchSetNum;
    element.routerPatchNum = 5 as RevisionPatchSetNum;
    element._processEdit(change);
    assert.equal(element._patchRange.patchNum, 5 as RevisionPatchSetNum);
  });

  test('file-action-tap handling', async () => {
    element._patchRange = {
      basePatchNum: ParentPatchSetNum,
      patchNum: 1 as RevisionPatchSetNum,
    };
    element._change = {
      ...createChangeViewChange(),
    };
    const fileList = element.$.fileList;
    const Actions = GrEditConstants.Actions;
    element.$.fileListHeader.editMode = true;
    await element.$.fileListHeader.updateComplete;
    flush();
    const controls = queryAndAssert<GrEditControls>(
      element.$.fileListHeader,
      '#editControls'
    );
    const openDeleteDialogStub = sinon.stub(controls, 'openDeleteDialog');
    const openRenameDialogStub = sinon.stub(controls, 'openRenameDialog');
    const openRestoreDialogStub = sinon.stub(controls, 'openRestoreDialog');
    const getEditUrlForDiffStub = sinon.stub(GerritNav, 'getEditUrlForDiff');
    const navigateToRelativeUrlStub = sinon.stub(
      GerritNav,
      'navigateToRelativeUrl'
    );

    // Delete
    fileList.dispatchEvent(
      new CustomEvent('file-action-tap', {
        detail: {action: Actions.DELETE.id, path: 'foo'},
        bubbles: true,
        composed: true,
      })
    );
    flush();

    assert.isTrue(openDeleteDialogStub.called);
    assert.equal(openDeleteDialogStub.lastCall.args[0], 'foo');

    // Restore
    fileList.dispatchEvent(
      new CustomEvent('file-action-tap', {
        detail: {action: Actions.RESTORE.id, path: 'foo'},
        bubbles: true,
        composed: true,
      })
    );
    flush();

    assert.isTrue(openRestoreDialogStub.called);
    assert.equal(openRestoreDialogStub.lastCall.args[0], 'foo');

    // Rename
    fileList.dispatchEvent(
      new CustomEvent('file-action-tap', {
        detail: {action: Actions.RENAME.id, path: 'foo'},
        bubbles: true,
        composed: true,
      })
    );
    flush();

    assert.isTrue(openRenameDialogStub.called);
    assert.equal(openRenameDialogStub.lastCall.args[0], 'foo');

    // Open
    fileList.dispatchEvent(
      new CustomEvent('file-action-tap', {
        detail: {action: Actions.OPEN.id, path: 'foo'},
        bubbles: true,
        composed: true,
      })
    );
    flush();

    assert.isTrue(getEditUrlForDiffStub.called);
    assert.equal(getEditUrlForDiffStub.lastCall.args[1], 'foo');
    assert.equal(getEditUrlForDiffStub.lastCall.args[2], 1 as PatchSetNum);
    assert.isTrue(navigateToRelativeUrlStub.called);
  });

  test('_selectedRevision updates when patchNum is changed', () => {
    const revision1: RevisionInfo = createRevision(1);
    const revision2: RevisionInfo = createRevision(2);
    element.getChangeModel().setState({
      loadingStatus: LoadingStatus.LOADED,
      change: {
        ...createChangeViewChange(),
        revisions: {
          aaa: revision1,
          bbb: revision2,
        },
        labels: {},
        actions: {},
        current_revision: 'bbb' as CommitId,
      },
    });

    sinon
      .stub(element, '_getPreferences')
      .returns(Promise.resolve(createPreferences()));
    element._patchRange = {patchNum: 2 as RevisionPatchSetNum};
    return element.performPostChangeLoadTasks().then(() => {
      assert.strictEqual(element._selectedRevision, revision2);

      element.set('_patchRange.patchNum', '1');
      assert.strictEqual(element._selectedRevision, revision1);
    });
  });

  test('_selectedRevision is assigned when patchNum is edit', async () => {
    const revision1 = createRevision(1);
    const revision2 = createRevision(2);
    const revision3 = createEditRevision();
    element.getChangeModel().setState({
      loadingStatus: LoadingStatus.LOADED,
      change: {
        ...createChangeViewChange(),
        revisions: {
          aaa: revision1,
          bbb: revision2,
          ccc: revision3,
        },
        labels: {},
        actions: {},
        current_revision: 'ccc' as CommitId,
      },
    });
    sinon
      .stub(element, '_getPreferences')
      .returns(Promise.resolve(createPreferences()));
    element._patchRange = {patchNum: EditPatchSetNum};
    await element.performPostChangeLoadTasks();
    assert.strictEqual(element._selectedRevision, revision3);
  });

  test('_sendShowChangeEvent', () => {
    const change = {...createChangeViewChange(), labels: {}};
    element._change = {...change};
    element._patchRange = {patchNum: 4 as RevisionPatchSetNum};
    element._mergeable = true;
    const showStub = sinon.stub(element.jsAPI, 'handleEvent');
    element._sendShowChangeEvent();
    assert.isTrue(showStub.calledOnce);
    assert.equal(showStub.lastCall.args[0], EventType.SHOW_CHANGE);
    assert.deepEqual(showStub.lastCall.args[1], {
      change,
      patchNum: 4,
      info: {mergeable: true},
    });
  });

  test('patch range changed', () => {
    element._patchRange = undefined;
    element._change = createChangeViewChange();
    element._change.revisions = createRevisions(4);
    element._change.current_revision = '1' as CommitId;
    element._change = {...element._change};

    const params = createAppElementChangeViewParams();

    assert.isFalse(element.hasPatchRangeChanged(params));
    assert.isFalse(element.hasPatchNumChanged(params));

    params.basePatchNum = ParentPatchSetNum;
    // undefined means navigate to latest patchset
    params.patchNum = undefined;

    element._patchRange = {
      patchNum: 2 as RevisionPatchSetNum,
      basePatchNum: ParentPatchSetNum,
    };

    assert.isTrue(element.hasPatchRangeChanged(params));
    assert.isTrue(element.hasPatchNumChanged(params));

    element._patchRange = {
      patchNum: 4 as RevisionPatchSetNum,
      basePatchNum: ParentPatchSetNum,
    };

    assert.isFalse(element.hasPatchRangeChanged(params));
    assert.isFalse(element.hasPatchNumChanged(params));
  });

  suite('_handleEditTap', () => {
    let fireEdit: () => void;

    setup(() => {
      fireEdit = () => {
        element.$.actions.dispatchEvent(new CustomEvent('edit-tap'));
      };
      navigateToChangeStub.restore();

      element._change = {
        ...createChangeViewChange(),
        revisions: {rev1: createRevision()},
      };
    });

    test('edit exists in revisions', async () => {
      const promise = mockPromise();
      sinon.stub(GerritNav, 'navigateToChange').callsFake((...args) => {
        assert.equal(args.length, 2);
        assert.equal(args[1]!.patchNum, EditPatchSetNum); // patchNum
        promise.resolve();
      });

      element.set('_change.revisions.rev2', {
        _number: EditPatchSetNum,
      });
      await flush();

      fireEdit();
      await promise;
    });

    test('no edit exists in revisions, non-latest patchset', async () => {
      const promise = mockPromise();
      sinon.stub(GerritNav, 'navigateToChange').callsFake((...args) => {
        assert.equal(args.length, 2);
        assert.equal(args[1]!.patchNum, 1 as PatchSetNum); // patchNum
        assert.equal(args[1]!.isEdit, true); // opt_isEdit
        promise.resolve();
      });

      element.set('_change.revisions.rev2', {_number: 2});
      element._patchRange = {patchNum: 1 as RevisionPatchSetNum};
      await flush();

      fireEdit();
      await promise;
    });

    test('no edit exists in revisions, latest patchset', async () => {
      const promise = mockPromise();
      sinon.stub(GerritNav, 'navigateToChange').callsFake((...args) => {
        assert.equal(args.length, 2);
        // No patch should be specified when patchNum == latest.
        assert.isNotOk(args[1]!.patchNum); // patchNum
        assert.equal(args[1]!.isEdit, true); // opt_isEdit
        promise.resolve();
      });

      element.set('_change.revisions.rev2', {_number: 2});
      element._patchRange = {patchNum: 2 as RevisionPatchSetNum};
      await flush();

      fireEdit();
      await promise;
    });
  });

  test('_handleStopEditTap', async () => {
    element._change = {
      ...createChangeViewChange(),
    };
    sinon.stub(element.$.metadata, 'computeLabelNames');
    navigateToChangeStub.restore();
    const promise = mockPromise();
    sinon.stub(GerritNav, 'navigateToChange').callsFake((...args) => {
      assert.equal(args.length, 2);
      assert.equal(args[1]!.patchNum, 1 as PatchSetNum); // patchNum
      promise.resolve();
    });

    element._patchRange = {patchNum: 1 as RevisionPatchSetNum};
    element.$.actions.dispatchEvent(
      new CustomEvent('stop-edit-tap', {bubbles: false})
    );
    await promise;
  });

  suite('plugin endpoints', () => {
    test('endpoint params', async () => {
      element._change = {...createChangeViewChange(), labels: {}};
      element._selectedRevision = createRevision();
      const promise = mockPromise();
      window.Gerrit.install(
        promise.resolve,
        '0.1',
        'http://some/plugins/url.js'
      );
      await flush();
      const plugin: PluginApi = (await promise) as PluginApi;
      const hookEl = await plugin
        .hook('change-view-integration')
        .getLastAttached();
      assert.strictEqual((hookEl as any).plugin, plugin);
      assert.strictEqual((hookEl as any).change, element._change);
      assert.strictEqual((hookEl as any).revision, element._selectedRevision);
    });
  });

  suite('_getMergeability', () => {
    let getMergeableStub: SinonStubbedMember<RestApiService['getMergeable']>;
    setup(() => {
      element._change = {...createChangeViewChange(), labels: {}};
      getMergeableStub = stubRestApi('getMergeable').returns(
        Promise.resolve({...createMergeable(), mergeable: true})
      );
    });

    test('merged change', () => {
      element._mergeable = null;
      element._change!.status = ChangeStatus.MERGED;
      return element._getMergeability().then(() => {
        assert.isFalse(element._mergeable);
        assert.isFalse(getMergeableStub.called);
      });
    });

    test('abandoned change', () => {
      element._mergeable = null;
      element._change!.status = ChangeStatus.ABANDONED;
      return element._getMergeability().then(() => {
        assert.isFalse(element._mergeable);
        assert.isFalse(getMergeableStub.called);
      });
    });

    test('open change', () => {
      element._mergeable = null;
      return element._getMergeability().then(() => {
        assert.isTrue(element._mergeable);
        assert.isTrue(getMergeableStub.called);
      });
    });
  });

  test('_handleToggleStar called when star is tapped', async () => {
    element._change = {
      ...createChangeViewChange(),
      owner: {_account_id: 1 as AccountId},
      starred: false,
    };
    element._loggedIn = true;
    await flush();

    const stub = sinon.stub(element, '_handleToggleStar');

    const changeStar = queryAndAssert<GrChangeStar>(element, '#changeStar');
    tap(queryAndAssert<HTMLButtonElement>(changeStar, 'button')!);
    assert.isTrue(stub.called);
  });

  suite('gr-reporting tests', () => {
    setup(() => {
      element._patchRange = {
        basePatchNum: ParentPatchSetNum,
        patchNum: 1 as RevisionPatchSetNum,
      };
      sinon
        .stub(element, 'performPostChangeLoadTasks')
        .returns(Promise.resolve(false));
      sinon.stub(element, '_getProjectConfig').returns(Promise.resolve());
      sinon.stub(element, '_getMergeability').returns(Promise.resolve());
      sinon.stub(element, '_getLatestCommitMessage').returns(Promise.resolve());
      sinon
        .stub(element, '_reloadPatchNumDependentResources')
        .returns(Promise.resolve([undefined, undefined, undefined]));
    });

    test("don't report changeDisplayed on reply", async () => {
      const changeDisplayStub = sinon.stub(
        element.reporting,
        'changeDisplayed'
      );
      const changeFullyLoadedStub = sinon.stub(
        element.reporting,
        'changeFullyLoaded'
      );
      element._handleReplySent();
      await flush();
      assert.isFalse(changeDisplayStub.called);
      assert.isFalse(changeFullyLoadedStub.called);
    });

    test('report changeDisplayed on _paramsChanged', async () => {
      const changeDisplayStub = sinon.stub(
        element.reporting,
        'changeDisplayed'
      );
      const changeFullyLoadedStub = sinon.stub(
        element.reporting,
        'changeFullyLoaded'
      );
      // reset so reload is triggered
      element._changeNum = undefined;
      element.params = {
        ...createAppElementChangeViewParams(),
        changeNum: TEST_NUMERIC_CHANGE_ID,
        project: TEST_PROJECT_NAME,
      };
      element.getChangeModel().setState({
        loadingStatus: LoadingStatus.LOADED,
        change: {
          ...createChangeViewChange(),
          labels: {},
          current_revision: 'foo' as CommitId,
          revisions: {foo: createRevision()},
        },
      });
      await flush();
      assert.isTrue(changeDisplayStub.called);
      assert.isTrue(changeFullyLoadedStub.called);
    });
  });

  test('_calculateHasParent', () => {
    const changeId = '123' as ChangeId;
    const relatedChanges: RelatedChangeAndCommitInfo[] = [];

    assert.equal(element._calculateHasParent(changeId, relatedChanges), false);

    relatedChanges.push({
      ...createRelatedChangeAndCommitInfo(),
      change_id: '123' as ChangeId,
    });
    assert.equal(element._calculateHasParent(changeId, relatedChanges), false);

    relatedChanges.push({
      ...createRelatedChangeAndCommitInfo(),
      change_id: '234' as ChangeId,
    });
    assert.equal(element._calculateHasParent(changeId, relatedChanges), true);
  });
});
