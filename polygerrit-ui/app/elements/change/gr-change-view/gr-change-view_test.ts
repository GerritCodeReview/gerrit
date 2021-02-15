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
import './gr-change-view';
import {
  ChangeStatus,
  CommentSide,
  DefaultBase,
  DiffViewMode,
  HttpMethod,
  PrimaryTab,
  SecondaryTab,
} from '../../../constants/constants';
import {GrEditConstants} from '../../edit/gr-edit-constants';
import {_testOnly_resetEndpoints} from '../../shared/gr-js-api-interface/gr-plugin-endpoints';
import {getComputedStyleValue} from '../../../utils/dom-util';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {getPluginLoader} from '../../shared/gr-js-api-interface/gr-plugin-loader';
import {_testOnly_initGerritPluginApi} from '../../shared/gr-js-api-interface/gr-gerrit';
import {EventType, PluginApi} from '../../../api/plugin';

import 'lodash/lodash';
import {
  stubRestApi,
  TestKeyboardShortcutBinder,
} from '../../../test/test-utils';
import {Shortcut} from '../../../mixins/keyboard-shortcut-mixin/keyboard-shortcut-mixin';
import {
  createAppElementChangeViewParams,
  createApproval,
  createChange,
  createChangeConfig,
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
  getCurrentRevision,
  createEditRevision,
  createAccountWithIdNameAndEmail,
} from '../../../test/test-data-generators';
import {ChangeViewPatchRange, GrChangeView} from './gr-change-view';
import {
  AccountId,
  ApprovalInfo,
  ChangeId,
  ChangeInfo,
  CommitId,
  CommitInfo,
  EditInfo,
  EditPatchSetNum,
  ElementPropertyDeepChange,
  GitRef,
  NumericChangeId,
  ParentPatchSetNum,
  PatchRange,
  PatchSetNum,
  RevisionInfo,
  RobotId,
  Timestamp,
  UrlEncodedCommentId,
} from '../../../types/common';
import {
  pressAndReleaseKeyOn,
  tap,
} from '@polymer/iron-test-helpers/mock-interactions';
import {GrEditControls} from '../../edit/gr-edit-controls/gr-edit-controls';
import {AppElementChangeViewParams} from '../../gr-app-types';
import {
  SinonFakeTimers,
  SinonSpy,
  SinonStubbedMember,
} from 'sinon/pkg/sinon-esm';
import {RestApiService} from '../../../services/gr-rest-api/gr-rest-api';
import {CustomKeyboardEvent} from '../../../types/events';
import {
  CommentThread,
  DraftInfo,
  UIDraft,
  UIRobot,
} from '../../../utils/comment-util';
import 'lodash/lodash';
import {ChangeComments} from '../../diff/gr-comment-api/gr-comment-api';
import {GerritView} from '../../../services/router/router-model';
import {ParsedChangeInfo} from '../../../types/types';
import {GrRelatedChangesList} from '../gr-related-changes-list/gr-related-changes-list';

const pluginApi = _testOnly_initGerritPluginApi();
const fixture = fixtureFromElement('gr-change-view');

type SinonSpyMember<F extends (...args: any) => any> = SinonSpy<
  Parameters<F>,
  ReturnType<F>
>;

suite('gr-change-view tests', () => {
  let element: GrChangeView;

  let navigateToChangeStub: SinonStubbedMember<typeof GerritNav.navigateToChange>;

  suiteSetup(() => {
    const kb = TestKeyboardShortcutBinder.push();
    kb.bindShortcut(Shortcut.SEND_REPLY, 'ctrl+enter');
    kb.bindShortcut(Shortcut.REFRESH_CHANGE, 'shift+r');
    kb.bindShortcut(Shortcut.OPEN_REPLY_DIALOG, 'a');
    kb.bindShortcut(Shortcut.OPEN_DOWNLOAD_DIALOG, 'd');
    kb.bindShortcut(Shortcut.TOGGLE_DIFF_MODE, 'm');
    kb.bindShortcut(Shortcut.TOGGLE_CHANGE_STAR, 's');
    kb.bindShortcut(Shortcut.UP_TO_DASHBOARD, 'u');
    kb.bindShortcut(Shortcut.EXPAND_ALL_MESSAGES, 'x');
    kb.bindShortcut(Shortcut.COLLAPSE_ALL_MESSAGES, 'z');
    kb.bindShortcut(Shortcut.OPEN_DIFF_PREFS, ',');
    kb.bindShortcut(Shortcut.EDIT_TOPIC, 't');
  });

  suiteTeardown(() => {
    TestKeyboardShortcutBinder.pop();
  });

  const TEST_SCROLL_TOP_PX = 100;

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
          __draftID: '0.m683trwff68',
          __editing: false,
          patch_set: 2 as PatchSetNum,
        },
      ],
      patchNum: 4 as PatchSetNum,
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
      patchNum: 2 as PatchSetNum,
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
      patchNum: 2 as PatchSetNum,
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
          __draftID: '0.m683trwff68',
          __editing: false,
          patch_set: 2 as PatchSetNum,
        },
      ],
      patchNum: 4 as PatchSetNum,
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
      patchNum: 4 as PatchSetNum,
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
      patchNum: 4 as PatchSetNum,
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
    stubRestApi('getAccount').returns(Promise.resolve(undefined));
    stubRestApi('getDiffComments').returns(Promise.resolve({}));
    stubRestApi('getDiffRobotComments').returns(Promise.resolve({}));
    stubRestApi('getDiffDrafts').returns(Promise.resolve({}));
    element = fixture.instantiate();
    element._changeNum = 1 as NumericChangeId;
    sinon.stub(element.$.actions, 'reload').returns(Promise.resolve());
    getPluginLoader().loadPlugins([]);
    pluginApi.install(
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
      'http://some/plugins/url.html'
    );
  });

  teardown(done => {
    flush(() => {
      done();
    });
  });

  const getCustomCssValue = (cssParam: string) =>
    getComputedStyleValue(cssParam, element);

  test('_handleMessageAnchorTap', () => {
    element._changeNum = 1 as NumericChangeId;
    element._patchRange = {
      basePatchNum: ParentPatchSetNum,
      patchNum: 1 as PatchSetNum,
    };
    element._change = createChange();
    const getUrlStub = sinon.stub(GerritNav, 'getUrlForChange');
    const replaceStateStub = sinon.stub(history, 'replaceState');
    element._handleMessageAnchorTap(
      new CustomEvent('message-anchor-tap', {detail: {id: 'a12345'}})
    );

    assert.equal(getUrlStub.lastCall.args[4], '#message-a12345');
    assert.isTrue(replaceStateStub.called);
  });

  test('_handleDiffAgainstBase', () => {
    element._change = {
      ...createChange(),
      revisions: createRevisions(10),
    };
    element._patchRange = {
      patchNum: 3 as PatchSetNum,
      basePatchNum: 1 as PatchSetNum,
    };
    sinon.stub(element, 'shouldSuppressKeyboardShortcut').returns(false);
    element._handleDiffAgainstBase(new CustomEvent('') as CustomKeyboardEvent);
    assert(navigateToChangeStub.called);
    const args = navigateToChangeStub.getCall(0).args;
    assert.equal(args[0], element._change);
    assert.equal(args[1], 3 as PatchSetNum);
  });

  test('_handleDiffAgainstLatest', () => {
    element._change = {
      ...createChange(),
      revisions: createRevisions(10),
    };
    element._patchRange = {
      basePatchNum: 1 as PatchSetNum,
      patchNum: 3 as PatchSetNum,
    };
    sinon.stub(element, 'shouldSuppressKeyboardShortcut').returns(false);
    element._handleDiffAgainstLatest(
      new CustomEvent('') as CustomKeyboardEvent
    );
    assert(navigateToChangeStub.called);
    const args = navigateToChangeStub.getCall(0).args;
    assert.equal(args[0], element._change);
    assert.equal(args[1], 10 as PatchSetNum);
    assert.equal(args[2], 1 as PatchSetNum);
  });

  test('_handleDiffBaseAgainstLeft', () => {
    element._change = {
      ...createChange(),
      revisions: createRevisions(10),
    };
    element._patchRange = {
      patchNum: 3 as PatchSetNum,
      basePatchNum: 1 as PatchSetNum,
    };
    sinon.stub(element, 'shouldSuppressKeyboardShortcut').returns(false);
    element._handleDiffBaseAgainstLeft(
      new CustomEvent('') as CustomKeyboardEvent
    );
    assert(navigateToChangeStub.called);
    const args = navigateToChangeStub.getCall(0).args;
    assert.equal(args[0], element._change);
    assert.equal(args[1], 1 as PatchSetNum);
  });

  test('_handleDiffRightAgainstLatest', () => {
    element._change = {
      ...createChange(),
      revisions: createRevisions(10),
    };
    element._patchRange = {
      basePatchNum: 1 as PatchSetNum,
      patchNum: 3 as PatchSetNum,
    };
    sinon.stub(element, 'shouldSuppressKeyboardShortcut').returns(false);
    element._handleDiffRightAgainstLatest(
      new CustomEvent('') as CustomKeyboardEvent
    );
    assert(navigateToChangeStub.called);
    const args = navigateToChangeStub.getCall(0).args;
    assert.equal(args[1], 10 as PatchSetNum);
    assert.equal(args[2], 3 as PatchSetNum);
  });

  test('_handleDiffBaseAgainstLatest', () => {
    element._change = {
      ...createChange(),
      revisions: createRevisions(10),
    };
    element._patchRange = {
      basePatchNum: 1 as PatchSetNum,
      patchNum: 3 as PatchSetNum,
    };
    sinon.stub(element, 'shouldSuppressKeyboardShortcut').returns(false);
    element._handleDiffBaseAgainstLatest(
      new CustomEvent('') as CustomKeyboardEvent
    );
    assert(navigateToChangeStub.called);
    const args = navigateToChangeStub.getCall(0).args;
    assert.equal(args[1], 10 as PatchSetNum);
    assert.isNotOk(args[2]);
  });

  suite('plugins adding to file tab', () => {
    setup(done => {
      element._changeNum = 1 as NumericChangeId;
      // Resolving it here instead of during setup() as other tests depend
      // on flush() not being called during setup.
      flush(() => done());
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

    test('_setActivePrimaryTab switched tab correctly', done => {
      element._setActivePrimaryTab(
        new CustomEvent('', {
          detail: {tab: 'change-view-tab-header-url'},
        })
      );
      flush(() => {
        assert.equal(element._activeTabs[0], 'change-view-tab-header-url');
        done();
      });
    });

    test('show-primary-tab switched primary tab correctly', done => {
      element.dispatchEvent(
        new CustomEvent('show-primary-tab', {
          composed: true,
          bubbles: true,
          detail: {
            tab: 'change-view-tab-header-url',
          },
        })
      );
      flush(() => {
        assert.equal(element._activeTabs[0], 'change-view-tab-header-url');
        done();
      });
    });

    test('param change should switch primary tab correctly', done => {
      assert.equal(element._activeTabs[0], PrimaryTab.FILES);
      const queryMap = new Map<string, string>();
      queryMap.set('tab', PrimaryTab.FINDINGS);
      // view is required
      element.params = {
        ...createAppElementChangeViewParams(),
        ...element.params,
        queryMap,
      };
      flush(() => {
        assert.equal(element._activeTabs[0], PrimaryTab.FINDINGS);
        done();
      });
    });

    test('invalid param change should not switch primary tab', done => {
      assert.equal(element._activeTabs[0], PrimaryTab.FILES);
      const queryMap = new Map<string, string>();
      queryMap.set('tab', 'random');
      // view is required
      element.params = {
        ...createAppElementChangeViewParams(),
        ...element.params,
        queryMap,
      };
      flush(() => {
        assert.equal(element._activeTabs[0], PrimaryTab.FILES);
        done();
      });
    });

    test('switching tab sets _selectedTabPluginEndpoint', done => {
      const paperTabs = element.shadowRoot!.querySelector('#primaryTabs')!;
      tap(paperTabs.querySelectorAll('paper-tab')[2]);
      flush(() => {
        assert.equal(
          element._selectedTabPluginEndpoint,
          'change-view-tab-content-url'
        );
        done();
      });
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

    test('A fires an error event when not logged in', done => {
      sinon.stub(element, '_getLoggedIn').returns(Promise.resolve(false));
      const loggedInErrorSpy = sinon.spy();
      element.addEventListener('show-auth-required', loggedInErrorSpy);
      pressAndReleaseKeyOn(element, 65, null, 'a');
      flush(() => {
        assert.isFalse(element.$.replyOverlay.opened);
        assert.isTrue(loggedInErrorSpy.called);
        done();
      });
    });

    test('shift A does not open reply overlay', done => {
      sinon.stub(element, '_getLoggedIn').returns(Promise.resolve(true));
      pressAndReleaseKeyOn(element, 65, 'shift', 'a');
      flush(() => {
        assert.isFalse(element.$.replyOverlay.opened);
        done();
      });
    });

    test('A toggles overlay when logged in', done => {
      sinon.stub(element, '_getLoggedIn').returns(Promise.resolve(true));
      element._change = {
        ...createChange(),
        revisions: createRevisions(1),
        messages: createChangeMessages(1),
      };
      element._change.labels = {};
      stubRestApi('getChangeDetail').callsFake(() =>
        Promise.resolve({
          ...createChange(),
          // element has latest info
          revisions: createRevisions(1),
          messages: createChangeMessages(1),
          current_revision: 'rev1' as CommitId,
        })
      );

      const openSpy = sinon.spy(element, '_openReplyDialog');

      pressAndReleaseKeyOn(element, 65, null, 'a');
      flush(() => {
        assert.isTrue(element.$.replyOverlay.opened);
        element.$.replyOverlay.close();
        assert.isFalse(element.$.replyOverlay.opened);
        assert(
          openSpy.lastCall.calledWithExactly(
            element.$.replyDialog.FocusTarget.ANY
          ),
          '_openReplyDialog should have been passed ANY'
        );
        assert.equal(openSpy.callCount, 1);
        done();
      });
    });

    test('fullscreen-overlay-opened hides content', () => {
      element._loggedIn = true;
      element._loading = false;
      element._change = {
        ...createChange(),
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
      element.$.replyDialog.dispatchEvent(
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
        ...createChange(),
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
      element.$.replyDialog.dispatchEvent(
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

    test('X should expand all messages', done => {
      flush(() => {
        const handleExpand = sinon.stub(
          element.messagesList!,
          'handleExpandCollapse'
        );
        pressAndReleaseKeyOn(element, 88, null, 'x');
        assert(handleExpand.calledWith(true));
        done();
      });
    });

    test('Z should collapse all messages', done => {
      flush(() => {
        const handleExpand = sinon.stub(
          element.messagesList!,
          'handleExpandCollapse'
        );
        pressAndReleaseKeyOn(element, 90, null, 'z');
        assert(handleExpand.calledWith(false));
        done();
      });
    });

    test('reload event from reply dialog is processed', () => {
      const handleReloadStub = sinon.stub(element, '_reload');
      element.$.replyDialog.dispatchEvent(
        new CustomEvent('reload', {
          detail: {clearPatchset: true},
          bubbles: true,
          composed: true,
        })
      );
      assert.isTrue(handleReloadStub.called);
    });

    test('shift + R should fetch and navigate to the latest patch set', done => {
      element._changeNum = TEST_NUMERIC_CHANGE_ID;
      element._patchRange = {
        basePatchNum: ParentPatchSetNum,
        patchNum: 1 as PatchSetNum,
      };
      element._change = {
        ...createChange(),
        revisions: {
          rev1: createRevision(),
        },
        current_revision: 'rev1' as CommitId,
        status: ChangeStatus.NEW,
        labels: {},
        actions: {},
      };

      const reloadChangeStub = sinon.stub(element, '_reload');
      pressAndReleaseKeyOn(element, 82, 'shift', 'r');
      flush(() => {
        assert.isTrue(reloadChangeStub.called);
        done();
      });
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
        element.$.fileList.$.diffPreferencesDialog,
        'open'
      );
      element._loggedIn = false;
      element.disableDiffPrefs = true;
      pressAndReleaseKeyOn(element, 188, null, ',');
      assert.isFalse(stub.called);

      element._loggedIn = true;
      pressAndReleaseKeyOn(element, 188, null, ',');
      assert.isFalse(stub.called);

      element.disableDiffPrefs = false;
      pressAndReleaseKeyOn(element, 188, null, ',');
      assert.isTrue(stub.called);
    });

    test('m should toggle diff mode', () => {
      sinon.stub(element, 'shouldSuppressKeyboardShortcut').returns(false);
      const setModeStub = sinon.stub(
        element.$.fileListHeader,
        'setDiffViewMode'
      );
      const e = {preventDefault: () => {}} as CustomKeyboardEvent;
      flush();

      element.viewState.diffMode = DiffViewMode.SIDE_BY_SIDE;
      element._handleToggleDiffMode(e);
      assert.isTrue(setModeStub.calledWith(DiffViewMode.UNIFIED));

      element.viewState.diffMode = DiffViewMode.UNIFIED;
      element._handleToggleDiffMode(e);
      assert.isTrue(setModeStub.calledWith(DiffViewMode.SIDE_BY_SIDE));
    });
  });

  suite('reloading drafts', () => {
    let reloadStub: SinonStubbedMember<typeof element.$.commentAPI.reloadDrafts>;
    const drafts: {[path: string]: UIDraft[]} = {
      'testfile.txt': [
        {
          patch_set: 5 as PatchSetNum,
          id: 'dd2982f5_c01c9e6a' as UrlEncodedCommentId,
          line: 1,
          updated: '2017-11-08 18:47:45.000000000' as Timestamp,
          message: 'test',
          unresolved: true,
        },
      ],
    };
    setup(() => {
      // Fake computeDraftCount as its required for ChangeComments,
      // see gr-comment-api#reloadDrafts.
      reloadStub = sinon.stub(element.$.commentAPI, 'reloadDrafts').returns(
        Promise.resolve({
          drafts,
          getAllThreadsForChange: () => [] as CommentThread[],
          computeDraftCount: () => 1,
        } as ChangeComments)
      );
      element._changeNum = 1 as NumericChangeId;
    });

    test('drafts are reloaded when reload-drafts fired', done => {
      element.$.fileList.dispatchEvent(
        new CustomEvent('reload-drafts', {
          detail: {
            resolve: () => {
              assert.isTrue(reloadStub.called);
              assert.deepEqual(element._diffDrafts, drafts);
              done();
            },
          },
          composed: true,
          bubbles: true,
        })
      );
    });

    test('drafts are reloaded when comment-refresh fired', () => {
      element.dispatchEvent(
        new CustomEvent('comment-refresh', {
          composed: true,
          bubbles: true,
        })
      );
      assert.isTrue(reloadStub.called);
    });
  });

  suite('_recomputeComments', () => {
    setup(() => {
      element._changeNum = TEST_NUMERIC_CHANGE_ID;
      element._change = createChange();
      flush();
      // Fake computeDraftCount as its required for ChangeComments,
      // see gr-comment-api#reloadDrafts.
      sinon.stub(element.$.commentAPI, 'reloadDrafts').returns(
        Promise.resolve({
          drafts: {},
          getAllThreadsForChange: () => THREADS,
          computeDraftCount: () => 0,
        } as ChangeComments)
      );
      element._change = createChange();
      element._changeNum = element._change._number;
    });

    test('draft threads should be a new copy with correct states', done => {
      element.$.fileList.dispatchEvent(
        new CustomEvent('reload-drafts', {
          detail: {
            resolve: () => {
              assert.equal(element._draftCommentThreads!.length, 2);
              assert.equal(
                element._draftCommentThreads![0].rootId,
                THREADS[0].rootId
              );
              assert.notEqual(
                element._draftCommentThreads![0].comments,
                THREADS[0].comments
              );
              assert.notEqual(
                element._draftCommentThreads![0].comments[0],
                THREADS[0].comments[0]
              );
              assert.isTrue(
                element
                  ._draftCommentThreads![0].comments.slice(0, 2)
                  .every(c => c.collapsed === true)
              );

              assert.isTrue(
                element._draftCommentThreads![0].comments[2].collapsed === false
              );
              done();
            },
          },
          composed: true,
          bubbles: true,
        })
      );
    });
  });

  test('diff comments modified', () => {
    const reloadThreadsSpy = sinon.spy(element, '_handleReloadCommentThreads');
    return element._reloadComments().then(() => {
      element.dispatchEvent(
        new CustomEvent('diff-comments-modified', {
          composed: true,
          bubbles: true,
        })
      );
      assert.isTrue(reloadThreadsSpy.called);
    });
  });

  test('thread list modified', () => {
    const reloadDiffSpy = sinon.spy(element, '_handleReloadDiffComments');
    element._activeTabs = [PrimaryTab.COMMENT_THREADS, SecondaryTab.CHANGE_LOG];
    flush();

    return element._reloadComments().then(() => {
      element.threadList!.dispatchEvent(
        new CustomEvent('thread-list-modified', {
          composed: true,
          bubbles: true,
        })
      );
      assert.isTrue(reloadDiffSpy.called);

      let draftStub = sinon
        .stub(element._changeComments!, 'computeDraftCount')
        .returns(1);
      assert.equal(
        element._computeTotalCommentCounts(5, element._changeComments!),
        '5 unresolved, 1 draft'
      );
      assert.equal(
        element._computeTotalCommentCounts(0, element._changeComments!),
        '1 draft'
      );
      draftStub.restore();
      draftStub = sinon
        .stub(element._changeComments!, 'computeDraftCount')
        .returns(0);
      assert.equal(
        element._computeTotalCommentCounts(0, element._changeComments!),
        ''
      );
      assert.equal(
        element._computeTotalCommentCounts(1, element._changeComments!),
        '1 unresolved'
      );
      draftStub.restore();
      draftStub = sinon
        .stub(element._changeComments!, 'computeDraftCount')
        .returns(2);
      assert.equal(
        element._computeTotalCommentCounts(1, element._changeComments!),
        '1 unresolved, 2 drafts'
      );
      draftStub.restore();
    });
  });

  suite('thread list and change log tabs', () => {
    setup(() => {
      element._changeNum = TEST_NUMERIC_CHANGE_ID;
      element._patchRange = {
        basePatchNum: ParentPatchSetNum,
        patchNum: 1 as PatchSetNum,
      };
      element._change = {
        ...createChange(),
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
      sinon.stub(element, '_reload').returns(Promise.resolve([]));
      sinon.spy(element, '_paramsChanged');
      element.params = createAppElementChangeViewParams();
    });
  });

  suite('Findings comment tab', () => {
    setup(done => {
      element._changeNum = TEST_NUMERIC_CHANGE_ID;
      element._change = {
        ...createChange(),
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
      const paperTabs = element.shadowRoot!.querySelector('#primaryTabs')!;
      tap(paperTabs.querySelectorAll('paper-tab')[3]);
      flush(() => {
        done();
      });
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
        (element._robotCommentThreads![0].comments[0] as UIRobot).robot_id,
        'rc1'
      );
      assert.equal(
        (element._robotCommentThreads![1].comments[0] as UIRobot).robot_id,
        'rc2'
      );
    });

    test('changing patchsets resets robot comments', done => {
      element.set('_change.current_revision', 'rev3');
      flush(() => {
        assert.equal(element._robotCommentThreads!.length, 1);
        done();
      });
    });

    test('Show more button is hidden', () => {
      assert.isNull(element.shadowRoot!.querySelector('.show-robot-comments'));
    });

    suite('robot comments show more button', () => {
      setup(done => {
        const arr = [];
        for (let i = 0; i <= 30; i++) {
          arr.push(...THREADS);
        }
        element._commentThreads = arr;
        flush(() => {
          done();
        });
      });

      test('Show more button is rendered', () => {
        assert.isOk(element.shadowRoot!.querySelector('.show-robot-comments'));
        assert.equal(
          element._robotCommentThreads!.length,
          ROBOT_COMMENTS_LIMIT
        );
      });

      test('Clicking show more button renders all comments', done => {
        tap(element.shadowRoot!.querySelector('.show-robot-comments')!);
        flush(() => {
          assert.equal(element._robotCommentThreads!.length, 62);
          done();
        });
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

  test('fetches the server config on attached', done => {
    flush(() => {
      assert.equal(
        element._serverConfig!.user.anonymous_coward_name,
        'test coward name'
      );
      done();
    });
  });

  test('_changeStatuses', () => {
    element._loading = false;
    element._change = {
      ...createChange(),
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
    const expectedStatuses = ['Merged', 'WIP'];
    assert.deepEqual(element._changeStatuses, expectedStatuses);
    assert.equal(element._changeStatus, expectedStatuses.join(', '));
    flush();
    const statusChips = element.shadowRoot!.querySelectorAll(
      'gr-change-status'
    );
    assert.equal(statusChips.length, 2);
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
      patchNum: 1 as PatchSetNum,
    };
    const change = {
      ...createChange(),
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
    const reloadStub = sinon.stub(element, '_reload');
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

    assert.equal(getLabel(null, false), 'Reply');
    assert.equal(getLabel(null, true), 'Start Review');

    const changeRecord: ElementPropertyDeepChange<
      GrChangeView,
      '_diffDrafts'
    > = {base: undefined, path: '', value: undefined};
    assert.equal(getLabel(changeRecord, false), 'Reply');

    changeRecord.base = {};
    assert.equal(getLabel(changeRecord, false), 'Reply');

    changeRecord.base = {
      'file1.txt': [{}],
      'file2.txt': [{}, {}],
    };
    assert.equal(getLabel(changeRecord, false), 'Reply (3)');
    assert.equal(getLabel(changeRecord, true), 'Start Review (3)');
  });

  test('comment events properly update diff drafts', () => {
    element._patchRange = {
      basePatchNum: ParentPatchSetNum,
      patchNum: 2 as PatchSetNum,
    };
    const draft: DraftInfo = {
      __draft: true,
      id: 'id1' as UrlEncodedCommentId,
      path: '/foo/bar.txt',
      message: 'hello',
    };
    element._handleCommentSave(new CustomEvent('', {detail: {comment: draft}}));
    draft.patch_set = 2 as PatchSetNum;
    assert.deepEqual(element._diffDrafts, {'/foo/bar.txt': [draft]});
    draft.patch_set = undefined;
    draft.message = 'hello, there';
    element._handleCommentSave(new CustomEvent('', {detail: {comment: draft}}));
    draft.patch_set = 2 as PatchSetNum;
    assert.deepEqual(element._diffDrafts, {'/foo/bar.txt': [draft]});
    const draft2: DraftInfo = {
      __draft: true,
      id: 'id2' as UrlEncodedCommentId,
      path: '/foo/bar.txt',
      message: 'hola',
    };
    element._handleCommentSave(
      new CustomEvent('', {detail: {comment: draft2}})
    );
    draft2.patch_set = 2 as PatchSetNum;
    assert.deepEqual(element._diffDrafts, {'/foo/bar.txt': [draft, draft2]});
    draft.patch_set = undefined;
    element._handleCommentDiscard(
      new CustomEvent('', {detail: {comment: draft}})
    );
    draft.patch_set = 2 as PatchSetNum;
    assert.deepEqual(element._diffDrafts, {'/foo/bar.txt': [draft2]});
    element._handleCommentDiscard(
      new CustomEvent('', {detail: {comment: draft2}})
    );
    assert.deepEqual(element._diffDrafts, {});
  });

  test('change num change', () => {
    const change = {
      ...createChange(),
      labels: {},
    } as ParsedChangeInfo;
    stubRestApi('getChangeDetail').returns(Promise.resolve(change));
    element._changeNum = undefined;
    element._patchRange = {
      basePatchNum: ParentPatchSetNum,
      patchNum: 2 as PatchSetNum,
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

  test('_setDiffViewMode is called with reset when new change is loaded', () => {
    const setDiffViewModeStub = sinon.stub(element, '_setDiffViewMode');
    element.viewState = {changeNum: 1 as NumericChangeId};
    element._changeNum = 2 as NumericChangeId;
    element._resetFileListViewState();
    assert.isTrue(setDiffViewModeStub.calledWithExactly(true));
  });

  test('diffViewMode is propagated from file list header', () => {
    element.viewState = {diffMode: DiffViewMode.UNIFIED};
    element.$.fileListHeader.diffViewMode = DiffViewMode.SIDE_BY_SIDE;
    assert.equal(element.viewState.diffMode, DiffViewMode.SIDE_BY_SIDE);
  });

  test('diffMode defaults to side by side without preferences', done => {
    stubRestApi('getPreferences').returns(Promise.resolve(createPreferences()));
    // No user prefs or diff view mode set.

    element._setDiffViewMode()!.then(() => {
      assert.equal(element.viewState.diffMode, DiffViewMode.SIDE_BY_SIDE);
      done();
    });
  });

  test('diffMode defaults to preference when not already set', done => {
    stubRestApi('getPreferences').returns(
      Promise.resolve({
        ...createPreferences(),
        default_diff_view: DiffViewMode.UNIFIED,
      })
    );

    element._setDiffViewMode()!.then(() => {
      assert.equal(element.viewState.diffMode, DiffViewMode.UNIFIED);
      done();
    });
  });

  test('existing diffMode overrides preference', done => {
    element.viewState.diffMode = DiffViewMode.SIDE_BY_SIDE;
    stubRestApi('getPreferences').returns(
      Promise.resolve({
        ...createPreferences(),
        default_diff_view: DiffViewMode.UNIFIED,
      })
    );
    element._setDiffViewMode()!.then(() => {
      assert.equal(element.viewState.diffMode, DiffViewMode.SIDE_BY_SIDE);
      done();
    });
  });

  test('don’t reload entire page when patchRange changes', () => {
    const reloadStub = sinon
      .stub(element, '_reload')
      .callsFake(() => Promise.resolve([]));
    const reloadPatchDependentStub = sinon
      .stub(element, '_reloadPatchNumDependentResources')
      .callsFake(() => Promise.resolve([undefined, undefined, undefined]));
    flush();
    const relatedChanges = element.shadowRoot!.querySelector(
      '#relatedChanges'
    ) as GrRelatedChangesList;
    const relatedClearSpy = sinon.spy(relatedChanges, 'clear');
    const collapseStub = sinon.stub(element.$.fileList, 'collapseAllDiffs');

    const value: AppElementChangeViewParams = {
      ...createAppElementChangeViewParams(),
      view: GerritView.CHANGE,
      patchNum: 1 as PatchSetNum,
    };
    element._paramsChanged(value);
    assert.isTrue(reloadStub.calledOnce);
    assert.isTrue(relatedClearSpy.calledOnce);

    element._initialLoadComplete = true;

    value.basePatchNum = 1 as PatchSetNum;
    value.patchNum = 2 as PatchSetNum;
    element._paramsChanged(value);
    assert.isFalse(reloadStub.calledTwice);
    assert.isTrue(reloadPatchDependentStub.calledOnce);
    assert.isTrue(relatedClearSpy.calledOnce);
    assert.isTrue(collapseStub.calledTwice);
  });

  test('reload ported comments when patchNum changes', () => {
    sinon.stub(element, '_reload').callsFake(() => Promise.resolve([]));
    sinon.stub(element, '_getCommitInfo');
    sinon.stub(element.$.fileList, 'reload');
    flush();
    const reloadPortedCommentsStub = sinon.stub(
      element.$.commentAPI,
      'reloadPortedComments'
    );
    const relatedChanges = element.shadowRoot!.querySelector(
      '#relatedChanges'
    ) as GrRelatedChangesList;
    sinon.spy(relatedChanges, 'clear');
    sinon.stub(element.$.fileList, 'collapseAllDiffs');

    const value: AppElementChangeViewParams = {
      ...createAppElementChangeViewParams(),
      view: GerritView.CHANGE,
      patchNum: 1 as PatchSetNum,
    };
    element._paramsChanged(value);

    element._initialLoadComplete = true;

    value.basePatchNum = 1 as PatchSetNum;
    value.patchNum = 2 as PatchSetNum;
    element._paramsChanged(value);
    assert.isTrue(reloadPortedCommentsStub.calledOnce);
  });

  test('reload entire page when patchRange doesnt change', () => {
    const reloadStub = sinon
      .stub(element, '_reload')
      .callsFake(() => Promise.resolve([]));
    const collapseStub = sinon.stub(element.$.fileList, 'collapseAllDiffs');
    const value: AppElementChangeViewParams = createAppElementChangeViewParams();
    element._paramsChanged(value);
    assert.isTrue(reloadStub.calledOnce);
    element._initialLoadComplete = true;
    element._paramsChanged(value);
    assert.isTrue(reloadStub.calledTwice);
    assert.isTrue(collapseStub.calledTwice);
  });

  test('related changes are not updated after other action', done => {
    sinon.stub(element, '_reload').callsFake(() => Promise.resolve([]));
    flush();
    const relatedChanges = element.shadowRoot!.querySelector(
      '#relatedChanges'
    ) as GrRelatedChangesList;
    sinon.stub(relatedChanges, 'reload');
    element._reload(true).then(() => {
      assert.isFalse(navigateToChangeStub.called);
      done();
    });
  });

  test('_computeMergedCommitInfo', () => {
    const dummyRevs: {[revisionId: string]: RevisionInfo} = {
      1: createRevision(1),
      2: createRevision(2),
    };
    assert.deepEqual(
      element._computeMergedCommitInfo('0' as CommitId, dummyRevs),
      {}
    );
    assert.deepEqual(
      element._computeMergedCommitInfo('1' as CommitId, dummyRevs),
      dummyRevs[1].commit
    );

    // Regression test for issue 5337.
    const commit = element._computeMergedCommitInfo('2' as CommitId, dummyRevs);
    assert.notDeepEqual(commit, dummyRevs[2]);
    assert.deepEqual(commit, dummyRevs[2].commit);
  });

  test('_computeCopyTextForTitle', () => {
    const change: ChangeInfo = {
      ...createChange(),
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
    };
    assert.equal(element._getLatestRevisionSHA(change), 'rev1');
  });

  test('show commit message edit button', () => {
    const change = createChange();
    const mergedChanged: ChangeInfo = {
      ...createChange(),
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

  test('_handleCommitMessageSave trims trailing whitespace', () => {
    element._change = createChange();
    // Response code is 500, because we want to avoid window reloading
    const putStub = stubRestApi('putChangeCommitMessage').returns(
      Promise.resolve(new Response(null, {status: 500}))
    );

    const mockEvent = (content: string) => {
      return new CustomEvent('', {detail: {content}});
    };

    element._handleCommitMessageSave(mockEvent('test \n  test '));
    assert.equal(putStub.lastCall.args[1], 'test\n  test');

    element._handleCommitMessageSave(mockEvent('  test\ntest'));
    assert.equal(putStub.lastCall.args[1], '  test\ntest');

    element._handleCommitMessageSave(mockEvent('\n\n\n\n\n\n\n\n'));
    assert.equal(putStub.lastCall.args[1], '\n\n\n\n\n\n\n\n');
  });

  test('_computeChangeIdCommitMessageError', () => {
    let commitMessage = 'Change-Id: I4ce18b2395bca69d7a9aa48bf4554faa56282483';
    let change: ChangeInfo = {
      ...createChange(),
      change_id: 'I4ce18b2395bca69d7a9aa48bf4554faa56282483' as ChangeId,
    };
    assert.equal(
      element._computeChangeIdCommitMessageError(commitMessage, change),
      null
    );

    change = {
      ...createChange(),
      change_id: 'I4ce18b2395bca69d7a9aa48bf4554faa56282484' as ChangeId,
    };
    assert.equal(
      element._computeChangeIdCommitMessageError(commitMessage, change),
      'mismatch'
    );

    commitMessage = 'This is the greatest change.';
    assert.equal(
      element._computeChangeIdCommitMessageError(commitMessage, change),
      'missing'
    );
  });

  test('multiple change Ids in commit message picks last', () => {
    const commitMessage = [
      'Change-Id: I4ce18b2395bca69d7a9aa48bf4554faa56282484',
      'Change-Id: I4ce18b2395bca69d7a9aa48bf4554faa56282483',
    ].join('\n');
    let change: ChangeInfo = {
      ...createChange(),
      change_id: 'I4ce18b2395bca69d7a9aa48bf4554faa56282483' as ChangeId,
    };
    assert.equal(
      element._computeChangeIdCommitMessageError(commitMessage, change),
      null
    );
    change = {
      ...createChange(),
      change_id: 'I4ce18b2395bca69d7a9aa48bf4554faa56282484' as ChangeId,
    };
    assert.equal(
      element._computeChangeIdCommitMessageError(commitMessage, change),
      'mismatch'
    );
  });

  test('does not count change Id that starts mid line', () => {
    const commitMessage = [
      'Change-Id: I4ce18b2395bca69d7a9aa48bf4554faa56282484',
      'Change-Id: I4ce18b2395bca69d7a9aa48bf4554faa56282483',
    ].join(' and ');
    let change: ChangeInfo = {
      ...createChange(),
      change_id: 'I4ce18b2395bca69d7a9aa48bf4554faa56282484' as ChangeId,
    };
    assert.equal(
      element._computeChangeIdCommitMessageError(commitMessage, change),
      null
    );
    change = {
      ...createChange(),
      change_id: 'I4ce18b2395bca69d7a9aa48bf4554faa56282483' as ChangeId,
    };
    assert.equal(
      element._computeChangeIdCommitMessageError(commitMessage, change),
      'mismatch'
    );
  });

  test('_computeTitleAttributeWarning', () => {
    let changeIdCommitMessageError = 'missing';
    assert.equal(
      element._computeTitleAttributeWarning(changeIdCommitMessageError),
      'No Change-Id in commit message'
    );

    changeIdCommitMessageError = 'mismatch';
    assert.equal(
      element._computeTitleAttributeWarning(changeIdCommitMessageError),
      'Change-Id mismatch'
    );
  });

  test('_computeChangeIdClass', () => {
    let changeIdCommitMessageError = 'missing';
    assert.equal(element._computeChangeIdClass(changeIdCommitMessageError), '');

    changeIdCommitMessageError = 'mismatch';
    assert.equal(
      element._computeChangeIdClass(changeIdCommitMessageError),
      'warning'
    );
  });

  test('topic is coalesced to null', done => {
    sinon.stub(element, '_changeChanged');
    stubRestApi('getChangeDetail').returns(
      Promise.resolve({
        ...createChange(),
        labels: {},
        current_revision: 'foo' as CommitId,
        revisions: {foo: createRevision()},
      })
    );

    element._getChangeDetail().then(() => {
      assert.isNull(element._change!.topic);
      done();
    });
  });

  test('commit sha is populated from getChangeDetail', done => {
    sinon.stub(element, '_changeChanged');
    stubRestApi('getChangeDetail').callsFake(() =>
      Promise.resolve({
        ...createChange(),
        labels: {},
        current_revision: 'foo' as CommitId,
        revisions: {foo: createRevision()},
      })
    );

    element._getChangeDetail().then(() => {
      assert.equal('foo', element._commitInfo!.commit);
      done();
    });
  });

  test('edit is added to change', () => {
    sinon.stub(element, '_changeChanged');
    const changeRevision = createRevision();
    stubRestApi('getChangeDetail').callsFake(() =>
      Promise.resolve({
        ...createChange(),
        labels: {},
        current_revision: 'foo' as CommitId,
        revisions: {foo: {...changeRevision}},
      })
    );
    const editCommit: CommitInfo = {
      ...createCommit(),
      commit: 'bar' as CommitId,
    };
    sinon.stub(element, '_getEdit').callsFake(() =>
      Promise.resolve({
        base_patch_set_number: 1 as PatchSetNum,
        commit: {...editCommit},
        base_revision: 'abc',
        ref: 'some/ref' as GitRef,
      })
    );
    element._patchRange = {};

    return element._getChangeDetail().then(() => {
      const revs = element._change!.revisions!;
      assert.equal(Object.keys(revs).length, 2);
      assert.deepEqual(revs['foo'], changeRevision);
      assert.deepEqual(revs['bar'], {
        ...createEditRevision(),
        commit: editCommit,
        fetch: undefined,
      });
    });
  });

  test('_getBasePatchNum', () => {
    const _change: ChangeInfo = {
      ...createChange(),
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
      ...createChange(),
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

    _patchRange.patchNum = 1 as PatchSetNum;
    assert.equal(element._getBasePatchNum(_change2, _patchRange), 'PARENT');
  });

  test('_openReplyDialog called with `ANY` when coming from tap event', done => {
    flush(() => {
      const openStub = sinon.stub(element, '_openReplyDialog');
      tap(element.$.replyBtn);
      assert(
        openStub.lastCall.calledWithExactly(
          element.$.replyDialog.FocusTarget.ANY
        ),
        '_openReplyDialog should have been passed ANY'
      );
      assert.equal(openStub.callCount, 1);
      done();
    });
  });

  test(
    '_openReplyDialog called with `BODY` when coming from message reply' +
      'event',
    done => {
      flush(() => {
        const openStub = sinon.stub(element, '_openReplyDialog');
        element.messagesList!.dispatchEvent(
          new CustomEvent('reply', {
            detail: {message: {message: 'text'}},
            composed: true,
            bubbles: true,
          })
        );
        assert(
          openStub.lastCall.calledWithExactly(
            element.$.replyDialog.FocusTarget.BODY
          ),
          '_openReplyDialog should have been passed BODY'
        );
        assert.equal(openStub.callCount, 1);
        done();
      });
    }
  );

  test('reply dialog focus can be controlled', () => {
    const FocusTarget = element.$.replyDialog.FocusTarget;
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

  test('revert dialog opened with revert param', done => {
    stubRestApi('getLoggedIn').returns(Promise.resolve(true));
    const awaitPluginsLoadedStub = sinon
      .stub(getPluginLoader(), 'awaitPluginsLoaded')
      .callsFake(() => Promise.resolve());

    element._patchRange = {
      basePatchNum: ParentPatchSetNum,
      patchNum: 2 as PatchSetNum,
    };
    element._change = {
      ...createChange(),
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

    sinon.stub(element.$.actions, 'showRevertDialog').callsFake(done);

    element._maybeShowRevertDialog();
    assert.isTrue(awaitPluginsLoadedStub.called);
  });

  suite('scroll related tests', () => {
    test('document scrolling calls function to set scroll height', done => {
      const originalHeight = document.body.scrollHeight;
      const scrollStub = sinon.stub(element, '_handleScroll').callsFake(() => {
        assert.isTrue(scrollStub.called);
        document.body.style.height = `${originalHeight}px`;
        scrollStub.restore();
        done();
      });
      document.body.style.height = '10000px';
      element._handleScroll();
    });

    test('scrollTop is set correctly', () => {
      element.viewState = {scrollTop: TEST_SCROLL_TOP_PX};

      sinon.stub(element, '_reload').callsFake(() => {
        // When element is reloaded, ensure that the history
        // state has the scrollTop set earlier. This will then
        // be reset.
        assert.isTrue(element.viewState.scrollTop === TEST_SCROLL_TOP_PX);
        return Promise.resolve([]);
      });

      // simulate reloading component, which is done when route
      // changes to match a regex of change view type.
      element._paramsChanged({...createAppElementChangeViewParams()});
    });

    test('scrollTop is reset when new change is loaded', () => {
      element._resetFileListViewState();
      assert.equal(element.viewState.scrollTop, 0);
    });
  });

  suite('reply dialog tests', () => {
    setup(() => {
      sinon.stub(element.$.replyDialog, '_draftChanged');
      element._change = {
        ...createChange(),
        revisions: createRevisions(1),
        messages: createChangeMessages(1),
      };
      element._change.labels = {};
      stubRestApi('getChangeDetail').callsFake(() =>
        Promise.resolve({
          ...createChange(),
          // element has latest info
          revisions: {rev1: createRevision()},
          messages: createChangeMessages(1),
          current_revision: 'rev1' as CommitId,
        })
      );
    });

    test('show reply dialog on open-reply-dialog event', done => {
      const openReplyDialogStub = sinon.stub(element, '_openReplyDialog');
      element.dispatchEvent(
        new CustomEvent('open-reply-dialog', {
          composed: true,
          bubbles: true,
          detail: {},
        })
      );
      flush(() => {
        assert.isTrue(openReplyDialogStub.calledOnce);
        done();
      });
    });

    test('reply from comment adds quote text', () => {
      const e = new CustomEvent('', {
        detail: {message: {message: 'quote text'}},
      });
      element._handleMessageReply(e);
      assert.equal(element.$.replyDialog.quote, '> quote text\n\n');
    });

    test('reply from comment replaces quote text', () => {
      element.$.replyDialog.draft = '> old quote text\n\n some draft text';
      element.$.replyDialog.quote = '> old quote text\n\n';
      const e = new CustomEvent('', {
        detail: {message: {message: 'quote text'}},
      });
      element._handleMessageReply(e);
      assert.equal(element.$.replyDialog.quote, '> quote text\n\n');
    });

    test('reply from same comment preserves quote text', () => {
      element.$.replyDialog.draft = '> quote text\n\n some draft text';
      element.$.replyDialog.quote = '> quote text\n\n';
      const e = new CustomEvent('', {
        detail: {message: {message: 'quote text'}},
      });
      element._handleMessageReply(e);
      assert.equal(
        element.$.replyDialog.draft,
        '> quote text\n\n some draft text'
      );
      assert.equal(element.$.replyDialog.quote, '> quote text\n\n');
    });

    test('reply from top of page contains previous draft', () => {
      const div = document.createElement('div');
      element.$.replyDialog.draft = '> quote text\n\n some draft text';
      element.$.replyDialog.quote = '> quote text\n\n';
      const e = ({
        target: div,
        preventDefault: sinon.spy(),
      } as unknown) as MouseEvent;
      element._handleReplyTap(e);
      assert.equal(
        element.$.replyDialog.draft,
        '> quote text\n\n some draft text'
      );
      assert.equal(element.$.replyDialog.quote, '> quote text\n\n');
    });
  });

  test('reply button is disabled until server config is loaded', done => {
    assert.isTrue(element._replyDisabled);
    // fetches the server config on attached
    flush(() => {
      assert.isFalse(element._replyDisabled);
      done();
    });
  });

  suite('commit message expand/collapse', () => {
    setup(() => {
      element._change = {
        ...createChange(),
        revisions: createRevisions(1),
        messages: createChangeMessages(1),
      };
      element._change.labels = {};
      stubRestApi('getChangeDetail').callsFake(() =>
        Promise.resolve({
          ...createChange(),
          // new patchset was uploaded
          revisions: createRevisions(2),
          current_revision: getCurrentRevision(2),
          messages: createChangeMessages(1),
        })
      );
    });

    test('commitCollapseToggle hidden for short commit message', () => {
      element._latestCommitMessage = '';
      flush();
      const commitCollapseToggle = element.shadowRoot!.querySelector(
        '#commitCollapseToggle'
      );
      assert.isTrue(commitCollapseToggle?.hasAttribute('hidden'));
    });

    test('commitCollapseToggle shown for long commit message', () => {
      element._latestCommitMessage = _.times(31, String).join('\n');
      const commitCollapseToggle = element.shadowRoot!.querySelector(
        '#commitCollapseToggle'
      );
      assert.isFalse(commitCollapseToggle?.hasAttribute('hidden'));
    });

    test('commitCollapseToggle functions', () => {
      element._latestCommitMessage = _.times(35, String).join('\n');
      assert.isTrue(element._commitCollapsed);
      assert.isTrue(element._commitCollapsible);
      assert.isTrue(element.$.commitMessageEditor.hasAttribute('collapsed'));
      const commitCollapseToggleButton = element.shadowRoot!.querySelector(
        '#commitCollapseToggleButton'
      )!;
      tap(commitCollapseToggleButton);
      assert.isFalse(element._commitCollapsed);
      assert.isTrue(element._commitCollapsible);
      assert.isFalse(element.$.commitMessageEditor.hasAttribute('collapsed'));
    });
  });

  suite('related changes expand/collapse', () => {
    let updateHeightSpy: SinonSpyMember<typeof element._updateRelatedChangeMaxHeight>;
    setup(() => {
      updateHeightSpy = sinon.spy(element, '_updateRelatedChangeMaxHeight');
    });

    test('relatedChangesToggle shown height greater than changeInfo height', () => {
      const relatedChangesToggle = element.shadowRoot!.querySelector(
        '#relatedChangesToggle'
      );
      assert.isFalse(relatedChangesToggle!.classList.contains('showToggle'));
      sinon.stub(element, '_getOffsetHeight').callsFake(() => 50);
      sinon.stub(element, '_getScrollHeight').callsFake(() => 60);
      sinon.stub(element, '_getLineHeight').callsFake(() => 5);
      sinon.stub(window, 'matchMedia').callsFake(() => {
        return {matches: true} as MediaQueryList;
      });
      const relatedChanges = element.shadowRoot!.querySelector(
        '#relatedChanges'
      ) as GrRelatedChangesList;
      relatedChanges.dispatchEvent(new CustomEvent('new-section-loaded'));
      assert.isTrue(relatedChangesToggle!.classList.contains('showToggle'));
      assert.equal(updateHeightSpy.callCount, 1);
    });

    test('relatedChangesToggle hidden height less than changeInfo height', () => {
      const relatedChangesToggle = element.shadowRoot!.querySelector(
        '#relatedChangesToggle'
      );
      assert.isFalse(relatedChangesToggle!.classList.contains('showToggle'));
      sinon.stub(element, '_getOffsetHeight').callsFake(() => 50);
      sinon.stub(element, '_getScrollHeight').callsFake(() => 40);
      sinon.stub(element, '_getLineHeight').callsFake(() => 5);
      sinon.stub(window, 'matchMedia').callsFake(() => {
        return {matches: true} as MediaQueryList;
      });
      const relatedChanges = element.shadowRoot!.querySelector(
        '#relatedChanges'
      ) as GrRelatedChangesList;
      relatedChanges.dispatchEvent(new CustomEvent('new-section-loaded'));
      assert.isFalse(relatedChangesToggle!.classList.contains('showToggle'));
      assert.equal(updateHeightSpy.callCount, 1);
    });

    test('relatedChangesToggle functions', () => {
      sinon.stub(element, '_getOffsetHeight').callsFake(() => 50);
      sinon.stub(window, 'matchMedia').callsFake(() => {
        return {matches: false} as MediaQueryList;
      });
      assert.isTrue(element._relatedChangesCollapsed);
      const relatedChangesToggleButton = element.shadowRoot!.querySelector(
        '#relatedChangesToggleButton'
      );
      const relatedChanges = element.shadowRoot!.querySelector(
        '#relatedChanges'
      ) as GrRelatedChangesList;
      assert.isTrue(relatedChanges.classList.contains('collapsed'));
      tap(relatedChangesToggleButton!);
      assert.isFalse(element._relatedChangesCollapsed);
      assert.isFalse(relatedChanges.classList.contains('collapsed'));
    });

    test('_updateRelatedChangeMaxHeight without commit toggle', () => {
      sinon.stub(element, '_getOffsetHeight').callsFake(() => 50);
      sinon.stub(element, '_getLineHeight').callsFake(() => 12);
      sinon.stub(window, 'matchMedia').callsFake(() => {
        return {matches: false} as MediaQueryList;
      });

      // 50 (existing height) - 30 (extra height) = 20 (adjusted height).
      // 20 (max existing height)  % 12 (line height) = 6 (remainder).
      // 20 (adjusted height) - 8 (remainder) = 12 (max height to set).

      element._updateRelatedChangeMaxHeight();
      assert.equal(getCustomCssValue('--relation-chain-max-height'), '12px');
      assert.equal(getCustomCssValue('--related-change-btn-top-padding'), '');
    });

    test('_updateRelatedChangeMaxHeight with commit toggle', () => {
      element._latestCommitMessage = _.times(31, String).join('\n');
      sinon.stub(element, '_getOffsetHeight').callsFake(() => 50);
      sinon.stub(element, '_getLineHeight').callsFake(() => 12);
      sinon.stub(window, 'matchMedia').callsFake(() => {
        return {matches: false} as MediaQueryList;
      });

      // 50 (existing height) % 12 (line height) = 2 (remainder).
      // 50 (existing height)  - 2 (remainder) = 48 (max height to set).

      element._updateRelatedChangeMaxHeight();
      assert.equal(getCustomCssValue('--relation-chain-max-height'), '48px');
      assert.equal(
        getCustomCssValue('--related-change-btn-top-padding'),
        '2px'
      );
    });

    test('_updateRelatedChangeMaxHeight in small screen mode', () => {
      element._latestCommitMessage = _.times(31, String).join('\n');
      sinon.stub(element, '_getOffsetHeight').callsFake(() => 50);
      sinon.stub(element, '_getLineHeight').callsFake(() => 12);
      sinon.stub(window, 'matchMedia').callsFake(() => {
        return {matches: true} as MediaQueryList;
      });

      element._updateRelatedChangeMaxHeight();

      // 400 (new height) % 12 (line height) = 4 (remainder).
      // 400 (new height) - 4 (remainder) = 396.

      assert.equal(getCustomCssValue('--relation-chain-max-height'), '396px');
    });

    test('_updateRelatedChangeMaxHeight in medium screen mode', () => {
      element._latestCommitMessage = _.times(31, String).join('\n');
      sinon.stub(element, '_getOffsetHeight').callsFake(() => 50);
      sinon.stub(element, '_getLineHeight').callsFake(() => 12);
      const matchMediaStub = sinon.stub(window, 'matchMedia').callsFake(() => {
        if (matchMediaStub.lastCall.args[0] === '(max-width: 75em)') {
          return {matches: true} as MediaQueryList;
        } else {
          return {matches: false} as MediaQueryList;
        }
      });

      // 100 (new height) % 12 (line height) = 4 (remainder).
      // 100 (new height) - 4 (remainder) = 96.
      element._updateRelatedChangeMaxHeight();
      assert.equal(getCustomCssValue('--relation-chain-max-height'), '96px');
    });

    suite('update checks', () => {
      let startUpdateCheckTimerSpy: SinonSpyMember<typeof element._startUpdateCheckTimer>;
      let asyncStub: SinonStubbedMember<typeof element.async>;
      setup(() => {
        startUpdateCheckTimerSpy = sinon.spy(element, '_startUpdateCheckTimer');
        asyncStub = sinon.stub(element, 'async').callsFake(f => {
          // Only fire the async callback one time.
          if (asyncStub.callCount > 1) {
            return 1;
          }
          f.call(element);
          return 1;
        });
        element._change = {
          ...createChange(),
          revisions: createRevisions(1),
          messages: createChangeMessages(1),
        };
      });

      test('_startUpdateCheckTimer negative delay', () => {
        const getChangeDetailStub = stubRestApi('getChangeDetail').returns(
          Promise.resolve({
            ...createChange(),
            // element has latest info
            revisions: {rev1: createRevision()},
            messages: createChangeMessages(1),
            current_revision: 'rev1' as CommitId,
          })
        );

        element._serverConfig = {
          ...createServerInfo(),
          change: {...createChangeConfig(), update_delay: -1},
        };

        assert.isTrue(startUpdateCheckTimerSpy.called);
        assert.isFalse(getChangeDetailStub.called);
      });

      test('_startUpdateCheckTimer up-to-date', async () => {
        const getChangeDetailStub = stubRestApi('getChangeDetail').callsFake(
          () =>
            Promise.resolve({
              ...createChange(),
              // element has latest info
              revisions: {rev1: createRevision()},
              messages: createChangeMessages(1),
              current_revision: 'rev1' as CommitId,
            })
        );

        element._serverConfig = {
          ...createServerInfo(),
          change: {...createChangeConfig(), update_delay: 12345},
        };
        await flush();

        assert.equal(startUpdateCheckTimerSpy.callCount, 2);
        assert.isTrue(getChangeDetailStub.called);
        assert.equal(asyncStub.lastCall.args[1], 12345 * 1000);
      });

      test('_startUpdateCheckTimer out-of-date shows an alert', done => {
        stubRestApi('getChangeDetail').callsFake(() =>
          Promise.resolve({
            ...createChange(),
            // new patchset was uploaded
            revisions: createRevisions(2),
            current_revision: getCurrentRevision(2),
            messages: createChangeMessages(1),
          })
        );

        element.addEventListener('show-alert', e => {
          assert.equal(e.detail.message, 'A newer patch set has been uploaded');
          done();
        });
        element._serverConfig = {
          ...createServerInfo(),
          change: {...createChangeConfig(), update_delay: 12345},
        };

        assert.equal(startUpdateCheckTimerSpy.callCount, 1);
      });

      test('_startUpdateCheckTimer respects _loading', async () => {
        stubRestApi('getChangeDetail').callsFake(() =>
          Promise.resolve({
            ...createChange(),
            // new patchset was uploaded
            revisions: createRevisions(2),
            current_revision: getCurrentRevision(2),
            messages: createChangeMessages(1),
          })
        );

        element._loading = true;
        element._serverConfig = {
          ...createServerInfo(),
          change: {...createChangeConfig(), update_delay: 12345},
        };
        await flush();

        // No toast, instead a second call to _startUpdateCheckTimer().
        assert.equal(startUpdateCheckTimerSpy.callCount, 2);
      });

      test('_startUpdateCheckTimer new status shows an alert', done => {
        stubRestApi('getChangeDetail').callsFake(() =>
          Promise.resolve({
            ...createChange(),
            // element has latest info
            revisions: {rev1: createRevision()},
            messages: createChangeMessages(1),
            current_revision: 'rev1' as CommitId,
            status: ChangeStatus.MERGED,
          })
        );

        element.addEventListener('show-alert', e => {
          assert.equal(e.detail.message, 'This change has been merged');
          done();
        });
        element._serverConfig = {
          ...createServerInfo(),
          change: {...createChangeConfig(), update_delay: 12345},
        };
      });

      test('_startUpdateCheckTimer new messages shows an alert', done => {
        stubRestApi('getChangeDetail').callsFake(() =>
          Promise.resolve({
            ...createChange(),
            revisions: {rev1: createRevision()},
            // element has new message
            messages: createChangeMessages(2),
            current_revision: 'rev1' as CommitId,
          })
        );
        element.addEventListener('show-alert', e => {
          assert.equal(
            e.detail.message,
            'There are new messages on this change'
          );
          done();
        });
        element._serverConfig = {
          ...createServerInfo(),
          change: {...createChangeConfig(), update_delay: 12345},
        };
      });
    });

    test('canStartReview computation', () => {
      const change1: ChangeInfo = createChange();
      const change2: ChangeInfo = {
        ...createChange(),
        actions: {
          ready: {
            enabled: true,
          },
        },
      };
      const change3: ChangeInfo = {
        ...createChange(),
        actions: {
          ready: {
            label: 'Ready for Review',
          },
        },
      };
      assert.isFalse(element._computeCanStartReview(change1));
      assert.isTrue(element._computeCanStartReview(change2));
      assert.isFalse(element._computeCanStartReview(change3));
    });
  });

  test('header class computation', () => {
    assert.equal(element._computeHeaderClass(), 'header');
    assert.equal(element._computeHeaderClass(true), 'header editMode');
  });

  test('_maybeScrollToMessage', done => {
    flush(() => {
      const scrollStub = sinon.stub(element.messagesList!, 'scrollToMessage');

      element._maybeScrollToMessage('');
      assert.isFalse(scrollStub.called);
      element._maybeScrollToMessage('message');
      assert.isFalse(scrollStub.called);
      element._maybeScrollToMessage('#message-TEST');
      assert.isTrue(scrollStub.called);
      assert.equal(scrollStub.lastCall.args[0], 'TEST');
      done();
    });
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
        {basePatchNum: ParentPatchSetNum, patchNum: 1 as PatchSetNum},
        {...createAppElementChangeViewParams(), edit: true}
      )
    );
    assert.isFalse(
      callCompute(
        {basePatchNum: ParentPatchSetNum, patchNum: 1 as PatchSetNum},
        createAppElementChangeViewParams()
      )
    );
    assert.isFalse(
      callCompute(
        {basePatchNum: EditPatchSetNum, patchNum: 1 as PatchSetNum},
        createAppElementChangeViewParams()
      )
    );
    assert.isTrue(
      callCompute(
        {basePatchNum: 1 as PatchSetNum, patchNum: EditPatchSetNum},
        createAppElementChangeViewParams()
      )
    );
  });

  test('_processEdit', () => {
    element._patchRange = {};
    const change: ParsedChangeInfo = {
      ...createChange(),
      current_revision: 'foo' as CommitId,
      revisions: {
        foo: {...createRevision(), actions: {cherrypick: {enabled: true}}},
      },
    };
    let mockChange;

    // With no edit, mockChange should be unmodified.
    element._processEdit((mockChange = _.cloneDeep(change)), false);
    assert.deepEqual(mockChange, change);

    const editCommit: CommitInfo = {
      ...createCommit(),
      commit: 'bar' as CommitId,
    };
    // When edit is not based on the latest PS, current_revision should be
    // unmodified.
    const edit: EditInfo = {
      ref: 'ref/test/abc' as GitRef,
      base_revision: 'abc',
      base_patch_set_number: 1 as PatchSetNum,
      commit: {...editCommit},
      fetch: {},
    };
    element._processEdit((mockChange = _.cloneDeep(change)), edit);
    assert.notDeepEqual(mockChange, change);
    assert.equal(mockChange.revisions.bar._number, EditPatchSetNum);
    assert.equal(mockChange.current_revision, change.current_revision);
    assert.deepEqual(mockChange.revisions.bar.commit, editCommit);
    assert.notOk(mockChange.revisions.bar.actions);

    edit.base_revision = 'foo';
    element._processEdit((mockChange = _.cloneDeep(change)), edit);
    assert.notDeepEqual(mockChange, change);
    assert.equal(mockChange.current_revision, 'bar');
    assert.deepEqual(
      mockChange.revisions.bar.actions,
      mockChange.revisions.foo.actions
    );

    // If _patchRange.patchNum is defined, do not load edit.
    element._patchRange.patchNum = 5 as PatchSetNum;
    change.current_revision = 'baz' as CommitId;
    element._processEdit((mockChange = _.cloneDeep(change)), edit);
    assert.equal(element._patchRange.patchNum, 5 as PatchSetNum);
    assert.notOk(mockChange.revisions.bar.actions);
  });

  test('file-action-tap handling', () => {
    element._patchRange = {
      basePatchNum: ParentPatchSetNum,
      patchNum: 1 as PatchSetNum,
    };
    element._change = {
      ...createChange(),
    };
    const fileList = element.$.fileList;
    const Actions = GrEditConstants.Actions;
    element.$.fileListHeader.editMode = true;
    flush();
    const controls = element.$.fileListHeader.shadowRoot!.querySelector(
      '#editControls'
    ) as GrEditControls;
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
    stubRestApi('getChangeDetail').returns(
      Promise.resolve({
        ...createChange(),
        revisions: {
          aaa: revision1,
          bbb: revision2,
        },
        labels: {},
        actions: {},
        current_revision: 'bbb' as CommitId,
      })
    );
    sinon.stub(element, '_getEdit').returns(Promise.resolve(false));
    sinon
      .stub(element, '_getPreferences')
      .returns(Promise.resolve(createPreferences()));
    element._patchRange = {patchNum: 2 as PatchSetNum};
    return element._getChangeDetail().then(() => {
      assert.strictEqual(element._selectedRevision, revision2);

      element.set('_patchRange.patchNum', '1');
      assert.strictEqual(element._selectedRevision, revision1);
    });
  });

  test('_selectedRevision is assigned when patchNum is edit', () => {
    const revision1 = createRevision(1);
    const revision2 = createRevision(2);
    const revision3 = createEditRevision();
    stubRestApi('getChangeDetail').returns(
      Promise.resolve({
        ...createChange(),
        revisions: {
          aaa: revision1,
          bbb: revision2,
          ccc: revision3,
        },
        labels: {},
        actions: {},
        current_revision: 'ccc' as CommitId,
      })
    );
    sinon.stub(element, '_getEdit').returns(Promise.resolve(undefined));
    sinon
      .stub(element, '_getPreferences')
      .returns(Promise.resolve(createPreferences()));
    element._patchRange = {patchNum: EditPatchSetNum};
    return element._getChangeDetail().then(() => {
      assert.strictEqual(element._selectedRevision, revision3);
    });
  });

  test('_sendShowChangeEvent', () => {
    const change = {...createChange(), labels: {}};
    element._change = {...change};
    element._patchRange = {patchNum: 4 as PatchSetNum};
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

  suite('_handleEditTap', () => {
    let fireEdit: () => void;

    setup(() => {
      fireEdit = () => {
        element.$.actions.dispatchEvent(new CustomEvent('edit-tap'));
      };
      navigateToChangeStub.restore();

      element._change = {
        ...createChange(),
        revisions: {rev1: createRevision()},
      };
    });

    test('edit exists in revisions', done => {
      sinon.stub(GerritNav, 'navigateToChange').callsFake((...args) => {
        assert.equal(args.length, 2);
        assert.equal(args[1], EditPatchSetNum); // patchNum
        done();
      });

      element.set('_change.revisions.rev2', {
        _number: EditPatchSetNum,
      });
      flush();

      fireEdit();
    });

    test('no edit exists in revisions, non-latest patchset', done => {
      sinon.stub(GerritNav, 'navigateToChange').callsFake((...args) => {
        assert.equal(args.length, 4);
        assert.equal(args[1], 1 as PatchSetNum); // patchNum
        assert.equal(args[3], true); // opt_isEdit
        done();
      });

      element.set('_change.revisions.rev2', {_number: 2});
      element._patchRange = {patchNum: 1 as PatchSetNum};
      flush();

      fireEdit();
    });

    test('no edit exists in revisions, latest patchset', done => {
      sinon.stub(GerritNav, 'navigateToChange').callsFake((...args) => {
        assert.equal(args.length, 4);
        // No patch should be specified when patchNum == latest.
        assert.isNotOk(args[1]); // patchNum
        assert.equal(args[3], true); // opt_isEdit
        done();
      });

      element.set('_change.revisions.rev2', {_number: 2});
      element._patchRange = {patchNum: 2 as PatchSetNum};
      flush();

      fireEdit();
    });
  });

  test('_handleStopEditTap', done => {
    element._change = {
      ...createChange(),
    };
    sinon.stub(element.$.metadata, '_computeLabelNames');
    navigateToChangeStub.restore();
    sinon.stub(GerritNav, 'navigateToChange').callsFake((...args) => {
      assert.equal(args.length, 2);
      assert.equal(args[1], 1 as PatchSetNum); // patchNum
      done();
    });

    element._patchRange = {patchNum: 1 as PatchSetNum};
    element.$.actions.dispatchEvent(
      new CustomEvent('stop-edit-tap', {bubbles: false})
    );
  });

  suite('plugin endpoints', () => {
    test('endpoint params', done => {
      element._change = {...createChange(), labels: {}};
      element._selectedRevision = createRevision();
      let hookEl: HTMLElement;
      let plugin: PluginApi;
      pluginApi.install(
        p => {
          plugin = p;
          plugin
            .hook('change-view-integration')
            .getLastAttached()
            .then(el => (hookEl = el));
        },
        '0.1',
        'http://some/plugins/url.html'
      );
      flush(() => {
        assert.strictEqual((hookEl as any).plugin, plugin);
        assert.strictEqual((hookEl as any).change, element._change);
        assert.strictEqual((hookEl as any).revision, element._selectedRevision);
        done();
      });
    });
  });

  suite('_getMergeability', () => {
    let getMergeableStub: SinonStubbedMember<RestApiService['getMergeable']>;
    setup(() => {
      element._change = {...createChange(), labels: {}};
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

  test('_paramsChanged sets in projectLookup', () => {
    flush();
    const relatedChanges = element.shadowRoot!.querySelector(
      '#relatedChanges'
    ) as GrRelatedChangesList;
    sinon.stub(relatedChanges, 'reload');
    sinon.stub(element, '_reload').returns(Promise.resolve([]));
    const setStub = stubRestApi('setInProjectLookup');
    element._paramsChanged({
      view: GerritNav.View.CHANGE,
      changeNum: 101 as NumericChangeId,
      project: TEST_PROJECT_NAME,
    });
    assert.isTrue(setStub.calledOnce);
    assert.isTrue(setStub.calledWith(101 as never, TEST_PROJECT_NAME as never));
  });

  test('_handleToggleStar called when star is tapped', () => {
    element._change = {
      ...createChange(),
      owner: {_account_id: 1 as AccountId},
      starred: false,
    };
    element._loggedIn = true;
    const stub = sinon.stub(element, '_handleToggleStar');
    flush();

    tap(element.$.changeStar.shadowRoot!.querySelector('button')!);
    assert.isTrue(stub.called);
  });

  suite('gr-reporting tests', () => {
    setup(() => {
      element._patchRange = {
        basePatchNum: ParentPatchSetNum,
        patchNum: 1 as PatchSetNum,
      };
      sinon.stub(element, '_getChangeDetail').returns(Promise.resolve(false));
      sinon.stub(element, '_getProjectConfig').returns(Promise.resolve());
      sinon.stub(element, '_reloadComments').returns(Promise.resolve());
      sinon.stub(element, '_getMergeability').returns(Promise.resolve());
      sinon.stub(element, '_getLatestCommitMessage').returns(Promise.resolve());
      sinon
        .stub(element, '_reloadPatchNumDependentResources')
        .returns(Promise.resolve([undefined, undefined, undefined]));
    });

    test("don't report changeDisplayed on reply", done => {
      const changeDisplayStub = sinon.stub(
        element.reporting,
        'changeDisplayed'
      );
      const changeFullyLoadedStub = sinon.stub(
        element.reporting,
        'changeFullyLoaded'
      );
      element._handleReplySent();
      flush(() => {
        assert.isFalse(changeDisplayStub.called);
        assert.isFalse(changeFullyLoadedStub.called);
        done();
      });
    });

    test('report changeDisplayed on _paramsChanged', done => {
      const changeDisplayStub = sinon.stub(
        element.reporting,
        'changeDisplayed'
      );
      const changeFullyLoadedStub = sinon.stub(
        element.reporting,
        'changeFullyLoaded'
      );
      element._paramsChanged({
        ...createAppElementChangeViewParams(),
        changeNum: 101 as NumericChangeId,
        project: TEST_PROJECT_NAME,
      });
      flush(() => {
        assert.isTrue(changeDisplayStub.called);
        assert.isTrue(changeFullyLoadedStub.called);
        done();
      });
    });
  });
});
