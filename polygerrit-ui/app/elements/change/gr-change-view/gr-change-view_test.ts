/**
 * @license
 * Copyright 2015 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import * as sinon from 'sinon';
import '../../../test/common-test-setup';
import '../../edit/gr-edit-constants';
import '../gr-thread-list/gr-thread-list';
import './gr-change-view';
import {
  ChangeStatus,
  CommentSide,
  createDefaultPreferences,
  DiffViewMode,
  Tab,
} from '../../../constants/constants';
import {GrEditConstants} from '../../edit/gr-edit-constants';
import {navigationToken} from '../../core/gr-navigation/gr-navigation';
import {PluginApi} from '../../../api/plugin';
import {
  mockPromise,
  pressKey,
  queryAndAssert,
  stubBaseUrl,
  stubFlags,
  stubRestApi,
  waitUntil,
  waitUntilVisible,
} from '../../../test/test-utils';
import {
  createAccountDetailWithId,
  createChangeMessages,
  createChangeViewChange,
  createChangeViewState,
  createParsedChange,
  createRevision,
  createRevisions,
  createServerInfo,
  createUserConfig,
  TEST_NUMERIC_CHANGE_ID,
  TEST_PROJECT_NAME,
} from '../../../test/test-data-generators';
import {GrChangeView} from './gr-change-view';
import {
  AccountId,
  BasePatchSetNum,
  CommentThread,
  CommitId,
  EDIT,
  NumericChangeId,
  PARENT,
  RepoName,
  RevisionPatchSetNum,
  SavingState,
  Timestamp,
  UrlEncodedCommentId,
} from '../../../types/common';
import {GrEditControls} from '../../edit/gr-edit-controls/gr-edit-controls';
import {SinonFakeTimers} from 'sinon';
import {GerritView} from '../../../services/router/router-model';
import {LoadingStatus, ParsedChangeInfo} from '../../../types/types';
import {
  ChangeModel,
  changeModelToken,
} from '../../../models/change/change-model';
import {FocusTarget} from '../gr-reply-dialog/gr-reply-dialog';
import {GrChangeStar} from '../../shared/gr-change-star/gr-change-star';
import {GrThreadList} from '../gr-thread-list/gr-thread-list';
import {assertIsDefined} from '../../../utils/common-util';
import {assert, fixture, html} from '@open-wc/testing';
import {Modifier} from '../../../utils/dom-util';
import {GrCopyLinks} from '../gr-copy-links/gr-copy-links';
import {
  ChangeChildView,
  changeViewModelToken,
} from '../../../models/views/change';
import {rootUrl} from '../../../utils/url-util';
import {testResolver} from '../../../test/common-test-setup';
import {UserModel, userModelToken} from '../../../models/user/user-model';
import {pluginLoaderToken} from '../../shared/gr-js-api-interface/gr-plugin-loader';
import {commentsModelToken} from '../../../models/comments/comments-model';

suite('gr-change-view tests', () => {
  let element: GrChangeView;
  let setUrlStub: sinon.SinonStub;
  let userModel: UserModel;
  let changeModel: ChangeModel;

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
          patch_set: 4 as RevisionPatchSetNum,
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
          savingState: SavingState.OK,
          patch_set: 2 as RevisionPatchSetNum,
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
          path: 'test.txt',
          author: {
            _account_id: 1000000 as AccountId,
            name: 'user',
            username: 'user',
          },
          patch_set: 3 as RevisionPatchSetNum,
          id: '09a9fb0a_1484e6cf' as UrlEncodedCommentId,
          side: CommentSide.PARENT,
          updated: '2018-02-13 22:47:19.000000000' as Timestamp,
          message: 'Some comment on another patchset.',
          unresolved: false,
        },
      ],
      patchNum: 3 as RevisionPatchSetNum,
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
          patch_set: 2 as RevisionPatchSetNum,
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
            _account_id: 1000000 as AccountId,
            name: 'user',
            username: 'user',
          },
          patch_set: 2 as RevisionPatchSetNum,
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
          savingState: SavingState.OK,
          patch_set: 2 as RevisionPatchSetNum,
        },
      ],
      patchNum: 4 as RevisionPatchSetNum,
      path: '/COMMIT_MSG',
      line: 6,
      rootId: 'zcf0b9fa_fe1a5f62' as UrlEncodedCommentId,
      commentSide: CommentSide.REVISION,
    },
    {
      comments: [],
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
          patch_set: 4 as RevisionPatchSetNum,
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

  setup(async () => {
    setUrlStub = sinon.stub(testResolver(navigationToken), 'setUrl');
    sinon
      .stub(testResolver(changeViewModelToken), 'editUrl')
      .returns('fakeEditUrl');
    sinon
      .stub(testResolver(changeViewModelToken), 'diffUrl')
      .returns('fakeDiffUrl');

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
    stubRestApi('getIfFlowsIsEnabled').returns(
      Promise.resolve({enabled: true})
    );
    stubRestApi('getDiffComments').returns(Promise.resolve({}));
    stubRestApi('getDiffDrafts').returns(Promise.resolve({}));

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
    element = await fixture<GrChangeView>(
      html`<gr-change-view></gr-change-view>`
    );
    element.viewState = {
      view: GerritView.CHANGE,
      childView: ChangeChildView.OVERVIEW,
      changeNum: TEST_NUMERIC_CHANGE_ID,
      repo: 'gerrit' as RepoName,
    };
    await element.updateComplete.then(() => {
      assertIsDefined(element.actions);
      sinon.stub(element.actions, 'reload').returns(Promise.resolve());
    });
    userModel = testResolver(userModelToken);
    changeModel = testResolver(changeModelToken);
  });

  teardown(async () => {
    await element.updateComplete;
  });

  test('render', () => {
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <div class="container loading">Loading...</div>
        <div class="container" hidden="" id="mainContent">
          <section class="changeInfoSection">
            <div class="header">
              <h1 class="assistive-tech-only">Change :</h1>
              <div class="headerTitle">
                <div class="changeStatuses"></div>
                <div class="changeStarContainer">
                  <gr-button
                    aria-disabled="false"
                    class="showCopyLinkDialogButton"
                    down-arrow=""
                    flatten=""
                    id="copyLinkDialogButton"
                    role="button"
                    tabindex="0"
                  >
                    <gr-change-star id="changeStar"> </gr-change-star>
                    <a aria-label="Change undefined" class="changeNumber"> </a>
                  </gr-button>
                </div>
                <div class="headerSubject"></div>
                <gr-copy-clipboard
                  class="changeCopyClipboard"
                  hideinput=""
                  text="undefined: undefined | http://localhost:9876undefined"
                >
                </gr-copy-clipboard>
              </div>
              <div class="commitActions">
                <gr-change-actions id="actions"> </gr-change-actions>
              </div>
            </div>
            <h2 class="assistive-tech-only">Change metadata</h2>
            <div class="changeInfo">
              <div class="changeInfo-column changeMetadata">
                <gr-change-metadata id="metadata"> </gr-change-metadata>
              </div>
              <div class="changeInfo-column mainChangeInfo" id="mainChangeInfo">
                <div id="commitAndRelated">
                  <div class="commitContainer">
                    <h3 class="assistive-tech-only">Commit Message</h3>
                    <div>
                      <gr-button
                        aria-disabled="false"
                        class="reply"
                        id="replyBtn"
                        primary=""
                        role="button"
                        tabindex="0"
                        title="Open reply dialog to publish comments and add reviewers (shortcut: a)"
                      >
                        Reply
                      </gr-button>
                    </div>
                    <div class="commitMessage" id="commitMessage">
                      <gr-editable-content
                        id="commitMessageEditor"
                        remove-zero-width-space=""
                      >
                        <gr-formatted-text> </gr-formatted-text>
                      </gr-editable-content>
                    </div>
                    <h3 class="assistive-tech-only">
                      Comments and Checks Summary
                    </h3>
                    <gr-change-summary> </gr-change-summary>
                    <gr-endpoint-decorator name="commit-container">
                      <gr-endpoint-param name="change"> </gr-endpoint-param>
                      <gr-endpoint-param name="revision"> </gr-endpoint-param>
                    </gr-endpoint-decorator>
                  </div>
                  <div class="relatedChanges">
                    <gr-related-changes-list> </gr-related-changes-list>
                  </div>
                  <div class="emptySpace"></div>
                </div>
              </div>
            </div>
          </section>
          <h2 class="assistive-tech-only">Files and Comments tabs</h2>
          <div class="tabs">
            <md-tabs id="tabs">
              <md-secondary-tab
                active=""
                data-name="files"
                md-tab=""
                tabindex="0"
              >
                <span> Files </span>
              </md-secondary-tab>
              <md-secondary-tab
                class="commentThreads"
                data-name="comments"
                md-tab=""
                tabindex="-1"
              >
                <gr-tooltip-content has-tooltip="" title="">
                  <span> Comments </span>
                </gr-tooltip-content>
              </md-secondary-tab>
              <md-secondary-tab
                data-name="change-view-tab-header-url"
                md-tab=""
                tabindex="0"
              >
                <gr-endpoint-decorator name="change-view-tab-header-url">
                  <gr-endpoint-param name="change"> </gr-endpoint-param>
                  <gr-endpoint-param name="revision"> </gr-endpoint-param>
                </gr-endpoint-decorator>
              </md-secondary-tab>
            </md-tabs>
          </div>
          <section class="tabContent">
            <div>
              <gr-file-list-header id="fileListHeader"> </gr-file-list-header>
              <gr-revision-parents> </gr-revision-parents>
              <gr-file-list id="fileList"> </gr-file-list>
            </div>
          </section>
          <gr-endpoint-decorator name="change-view-integration">
            <gr-endpoint-param name="change"> </gr-endpoint-param>
            <gr-endpoint-param name="revision"> </gr-endpoint-param>
          </gr-endpoint-decorator>
          <div class="tabs">
            <md-tabs>
              <md-secondary-tab
                active=""
                class="changeLog"
                data-name="_changeLog"
                md-tab=""
                tabindex="0"
              >
                Change Log
              </md-secondary-tab>
            </md-tabs>
          </div>
          <section class="changeLog">
            <h2 class="assistive-tech-only">Change Log</h2>
            <gr-messages-list> </gr-messages-list>
          </section>
        </div>
        <gr-apply-fix-dialog id="applyFixDialog"> </gr-apply-fix-dialog>
        <dialog id="downloadModal" tabindex="-1">
          <gr-download-dialog id="downloadDialog" role="dialog">
          </gr-download-dialog>
        </dialog>
        <dialog id="includedInModal" tabindex="-1">
          <gr-included-in-dialog id="includedInDialog"> </gr-included-in-dialog>
        </dialog>
        <dialog id="replyModal"></dialog>
      `
    );
  });

  test('handleMessageAnchorTap', async () => {
    element.changeNum = 1 as NumericChangeId;
    element.patchNum = 1 as RevisionPatchSetNum;
    element.change = createChangeViewChange();
    await element.updateComplete;
    const replaceStateStub = sinon.stub(history, 'replaceState');
    element.handleMessageAnchorTap(
      new CustomEvent('message-anchor-tap', {detail: {id: 'a12345'}})
    );

    assert.isTrue(replaceStateStub.called);
  });

  test('renders flows tab if experiment is enabled', async () => {
    element.isFlowsEnabled = true;
    stubFlags('isEnabled').returns(true);
    element.requestUpdate();
    await element.updateComplete;
    await waitUntil(() => !!element.isFlowsEnabled);
    queryAndAssert(element, '[data-name="flows"]');
  });

  test('handleDiffAgainstBase', () => {
    element.change = {
      ...createChangeViewChange(),
      revisions: createRevisions(10),
    };
    element.basePatchNum = 1 as BasePatchSetNum;
    element.patchNum = 3 as RevisionPatchSetNum;
    element.handleDiffAgainstBase();
    assert.isTrue(setUrlStub.called);
    assert.equal(setUrlStub.lastCall.firstArg, '/c/test-project/+/42/3');
  });

  test('handleDiffAgainstLatest', () => {
    element.change = {
      ...createChangeViewChange(),
      revisions: createRevisions(10),
    };
    element.basePatchNum = 1 as BasePatchSetNum;
    element.patchNum = 3 as RevisionPatchSetNum;
    element.handleDiffAgainstLatest();
    assert.isTrue(setUrlStub.called);
    assert.equal(setUrlStub.lastCall.firstArg, '/c/test-project/+/42/1..10');
  });

  test('handleDiffBaseAgainstLeft', () => {
    element.change = {
      ...createChangeViewChange(),
      revisions: createRevisions(10),
    };
    element.basePatchNum = 1 as BasePatchSetNum;
    element.patchNum = 3 as RevisionPatchSetNum;
    element.handleDiffBaseAgainstLeft();
    assert.isTrue(setUrlStub.called);
    assert.equal(setUrlStub.lastCall.firstArg, '/c/test-project/+/42/1');
  });

  test('handleDiffRightAgainstLatest', () => {
    element.change = {
      ...createChangeViewChange(),
      revisions: createRevisions(10),
    };
    element.basePatchNum = 1 as BasePatchSetNum;
    element.patchNum = 3 as RevisionPatchSetNum;
    element.handleDiffRightAgainstLatest();
    assert.isTrue(setUrlStub.called);
    assert.equal(setUrlStub.lastCall.firstArg, '/c/test-project/+/42/3..10');
  });

  test('handleDiffBaseAgainstLatest', () => {
    element.change = {
      ...createChangeViewChange(),
      revisions: createRevisions(10),
    };
    element.basePatchNum = 1 as BasePatchSetNum;
    element.patchNum = 3 as RevisionPatchSetNum;
    element.handleDiffBaseAgainstLatest();
    assert.isTrue(setUrlStub.called);
    assert.equal(setUrlStub.lastCall.firstArg, '/c/test-project/+/42/10');
  });

  test('toggle attention set status', async () => {
    element.change = {
      ...createChangeViewChange(),
      revisions: createRevisions(10),
    };
    const addToAttentionSetStub = stubRestApi('addToAttentionSet').returns(
      Promise.resolve(new Response())
    );

    const removeFromAttentionSetStub = stubRestApi(
      'removeFromAttentionSet'
    ).returns(Promise.resolve(new Response()));
    element.basePatchNum = 1 as BasePatchSetNum;
    element.patchNum = 3 as RevisionPatchSetNum;
    await element.updateComplete;
    assert.isNotOk(element.change.attention_set);
    element.handleToggleAttentionSet();
    assert.isTrue(addToAttentionSetStub.called);
    assert.isFalse(removeFromAttentionSetStub.called);

    element.handleToggleAttentionSet();
    assert.isTrue(removeFromAttentionSetStub.called);
  });

  suite('plugins adding to file tab', () => {
    setup(async () => {
      element.changeNum = TEST_NUMERIC_CHANGE_ID;
      await element.updateComplete;
      await waitUntil(() => element.pluginTabsHeaderEndpoints.length > 0);
    });

    test('plugin added tab shows up as a dynamic endpoint', async () => {
      assert(
        element.pluginTabsHeaderEndpoints.includes('change-view-tab-header-url')
      );
      const tabs = element.shadowRoot!.querySelector('#tabs')!;
      const mdTabs = tabs.querySelectorAll<HTMLElement>('md-secondary-tab');
      // 4 Tabs are : Files, Comment Threads, Plugin
      assert.equal(tabs.querySelectorAll('md-secondary-tab').length, 3);
      assert.equal(mdTabs[0].dataset.name, 'files');
      assert.equal(mdTabs[1].dataset.name, 'comments');
      assert.equal(mdTabs[2].dataset.name, 'change-view-tab-header-url');
    });

    test('setActiveTab switched tab correctly', async () => {
      element.setActiveTab(
        new CustomEvent('', {
          detail: {tab: 'change-view-tab-header-url'},
        })
      );
      await element.updateComplete;
      assert.equal(element.activeTab, 'change-view-tab-header-url');
    });

    test('show-tab switched primary tab correctly', async () => {
      element.dispatchEvent(
        new CustomEvent('show-tab', {
          composed: true,
          bubbles: true,
          detail: {
            tab: 'change-view-tab-header-url',
          },
        })
      );
      await element.updateComplete;
      assert.equal(element.activeTab, 'change-view-tab-header-url');
    });

    test('invalid param change should not switch primary tab', async () => {
      assert.equal(element.activeTab, Tab.FILES);
      // view is required
      element.viewState = {
        ...createChangeViewState(),
        ...element.viewState,
        tab: 'random',
      };
      await element.updateComplete;
      assert.equal(element.activeTab, Tab.FILES);
    });

    test('switching to plugin tab renders the plugin tab content', async () => {
      const mdTabs = element.shadowRoot!.querySelector('#tabs')!;
      mdTabs.querySelectorAll<HTMLElement>('md-secondary-tab')[2].click();
      await element.updateComplete;
      await element.updateComplete;
      const tabContent = queryAndAssert(element, '.tabContent');
      const endpoint = queryAndAssert(tabContent, 'gr-endpoint-decorator');
      assert.dom.equal(
        endpoint,
        /* HTML */ `
          <gr-endpoint-decorator>
            <gr-endpoint-param name="change"></gr-endpoint-param>
            <gr-endpoint-param name="revision"></gr-endpoint-param>
          </gr-endpoint-decorator>
        `
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
      assertIsDefined(element.metadata);
      const editStub = sinon.stub(element.metadata, 'editTopic');
      pressKey(element, 't');
      assert(editStub.called);
    });

    test('S should toggle the CL star', () => {
      assertIsDefined(element.changeStar);
      const starStub = sinon.stub(element.changeStar, 'toggleStar');
      pressKey(element, 's');
      assert(starStub.called);
    });

    test('toggle star is throttled', () => {
      assertIsDefined(element.changeStar);
      const starStub = sinon.stub(element.changeStar, 'toggleStar');
      pressKey(element, 's');
      assert(starStub.called);
      pressKey(element, 's');
      assert.equal(starStub.callCount, 1);
      clock.tick(1000);
      pressKey(element, 's');
      assert.equal(starStub.callCount, 2);
    });

    test('U should navigate to root if no backPage set', () => {
      pressKey(element, 'u');
      assert.isTrue(setUrlStub.called);
      assert.isTrue(setUrlStub.lastCall.calledWithExactly(rootUrl()));
    });

    test('U should navigate to backPage if set', () => {
      element.backPage = '/dashboard/self';
      pressKey(element, 'u');
      assert.isTrue(setUrlStub.called);
      assert.isTrue(setUrlStub.lastCall.calledWithExactly('/dashboard/self'));
    });

    test('A fires an error event when not logged in', async () => {
      userModel.setAccount(undefined);
      const loggedInErrorSpy = sinon.spy();
      element.addEventListener('show-auth-required', loggedInErrorSpy);
      pressKey(element, 'a');
      await element.updateComplete;
      assertIsDefined(element.replyModal);
      assert.isFalse(element.replyModalOpened);
      assert.isTrue(loggedInErrorSpy.called);
    });

    test('shift A does not open reply overlay', async () => {
      pressKey(element, 'a', Modifier.SHIFT_KEY);
      await element.updateComplete;
      assertIsDefined(element.replyModal);
      assert.isFalse(element.replyModalOpened);
    });

    test('A toggles overlay when logged in', async () => {
      // restore clock so that setTimeout in waitUntil() works as expected
      clock.restore();
      stubRestApi('getChangeDetail').returns(
        Promise.resolve(createParsedChange())
      );
      const change = {
        ...createChangeViewChange(),
        revisions: createRevisions(1),
        messages: createChangeMessages(1),
      };
      change.labels = {};
      element.change = change;

      changeModel.updateState({
        loadingStatus: LoadingStatus.LOADED,
        change,
      });

      await element.updateComplete;

      const openSpy = sinon.spy(element, 'openReplyDialog');

      pressKey(element, 'a');
      await element.updateComplete;
      assertIsDefined(element.replyModal);
      assert.isTrue(element.replyModalOpened);
      sinon.spy(element.replyDialog!, 'open');
      await waitUntilVisible(element.replyDialog!);
      element.replyModal.close();
      assert(
        openSpy.lastCall.calledWithExactly(FocusTarget.ANY),
        'openReplyDialog should have been passed ANY'
      );
      assert.equal(openSpy.callCount, 1);
      await waitUntil(() => !element.replyModalOpened);
    });

    test('expand all messages when expand-diffs fired', () => {
      assertIsDefined(element.fileList);
      assertIsDefined(element.fileListHeader);
      const handleExpand = sinon.stub(element.fileList, 'expandAllDiffs');
      element.fileListHeader.dispatchEvent(
        new CustomEvent('expand-diffs', {
          composed: true,
          bubbles: true,
        })
      );
      assert.isTrue(handleExpand.called);
    });

    test('collapse all messages when collapse-diffs fired', () => {
      assertIsDefined(element.fileList);
      assertIsDefined(element.fileListHeader);
      const handleCollapse = sinon.stub(element.fileList, 'collapseAllDiffs');
      element.fileListHeader.dispatchEvent(
        new CustomEvent('collapse-diffs', {
          composed: true,
          bubbles: true,
        })
      );
      assert.isTrue(handleCollapse.called);
    });

    test('X should expand all messages', async () => {
      await element.updateComplete;
      const handleExpand = sinon.stub(
        element.messagesList!,
        'handleExpandCollapse'
      );
      pressKey(element, 'x');
      assert(handleExpand.calledWith(true));
    });

    test('Z should collapse all messages', async () => {
      await element.updateComplete;
      const handleExpand = sinon.stub(
        element.messagesList!,
        'handleExpandCollapse'
      );
      pressKey(element, 'z');
      assert(handleExpand.calledWith(false));
    });

    test('d should open download overlay', () => {
      assertIsDefined(element.downloadModal);
      const stub = sinon.stub(element.downloadModal, 'showModal');
      pressKey(element, 'd');
      assert.isTrue(stub.called);
    });

    test(', should open diff preferences', async () => {
      assertIsDefined(element.fileList);
      await element.fileList.updateComplete;
      assertIsDefined(element.fileList.diffPreferencesDialog);
      const stub = sinon.stub(element.fileList.diffPreferencesDialog, 'open');
      element.loggedIn = false;
      pressKey(element, ',');
      assert.isFalse(stub.called);

      element.loggedIn = true;
      pressKey(element, ',');
      assert.isTrue(stub.called);
    });

    test('m should toggle diff mode', async () => {
      const updatePreferencesStub = sinon.stub(userModel, 'updatePreferences');
      await element.updateComplete;

      const prefs = {
        ...createDefaultPreferences(),
        diff_view: DiffViewMode.SIDE_BY_SIDE,
      };
      userModel.setPreferences(prefs);
      element.handleToggleDiffMode();
      assert.isTrue(
        updatePreferencesStub.calledWith({diff_view: DiffViewMode.UNIFIED})
      );

      const newPrefs = {
        ...createDefaultPreferences(),
        diff_view: DiffViewMode.UNIFIED,
      };
      userModel.setPreferences(newPrefs);
      await element.updateComplete;
      element.handleToggleDiffMode();
      assert.isTrue(
        updatePreferencesStub.calledWith({diff_view: DiffViewMode.SIDE_BY_SIDE})
      );
    });
  });

  suite('thread list and change log tabs', () => {
    setup(() => {
      element.changeNum = TEST_NUMERIC_CHANGE_ID;
      element.basePatchNum = PARENT;
      element.patchNum = 1 as RevisionPatchSetNum;
      element.change = {
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
      element.viewState = createChangeViewState();
    });
  });

  suite('Comments tab', () => {
    setup(async () => {
      element.changeNum = TEST_NUMERIC_CHANGE_ID;
      element.change = {
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
      element.commentThreads = THREADS;
      await element.updateComplete;
      const mdTabs = element.shadowRoot!.querySelector('#tabs')!;
      const tabs = mdTabs.querySelectorAll<HTMLElement>('md-secondary-tab');
      assert.isTrue(tabs.length > 1);
      assert.equal(tabs[1].dataset.name, 'comments');
      tabs[1].click();
      await element.updateComplete;
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
      await element.updateComplete;
      assert.isFalse(threadList.unresolvedOnly);
    });
  });

  test('reply button is a login button when logged out', async () => {
    assertIsDefined(element.replyBtn);
    element.loggedIn = false;
    await element.updateComplete;
    assert.equal(element.replyBtn.textContent, 'Sign in');
  });

  test('download tap calls handleOpenDownloadDialog', () => {
    assertIsDefined(element.actions);
    const openDialogStub = sinon.stub(element, 'handleOpenDownloadDialog');
    element.actions.dispatchEvent(
      new CustomEvent('download-tap', {
        composed: true,
        bubbles: true,
      })
    );
    assert.isTrue(openDialogStub.called);
  });

  test('fetches the server config on attached', async () => {
    await element.updateComplete;
    assert.equal(
      element.serverConfig!.user.anonymous_coward_name,
      'test coward name'
    );
  });

  test('diff preferences open when open-diff-prefs is fired', async () => {
    await element.updateComplete;
    assertIsDefined(element.fileList);
    assertIsDefined(element.fileListHeader);
    await element.fileList.updateComplete;
    const overlayOpenStub = sinon.stub(element.fileList, 'openDiffPrefs');
    element.fileListHeader.dispatchEvent(
      new CustomEvent('open-diff-prefs', {
        composed: true,
        bubbles: true,
      })
    );
    assert.isTrue(overlayOpenStub.called);
  });

  test('prepareCommitMsgForLinkify', () => {
    let commitMessage = 'R=test@google.com';
    let result = element.prepareCommitMsgForLinkify(commitMessage);
    assert.equal(result, 'R=\u200Btest@google.com');

    commitMessage = 'R=test@google.com\nR=test@google.com';
    result = element.prepareCommitMsgForLinkify(commitMessage);
    assert.equal(result, 'R=\u200Btest@google.com\nR=\u200Btest@google.com');

    commitMessage = 'CC=test@google.com';
    result = element.prepareCommitMsgForLinkify(commitMessage);
    assert.equal(result, 'CC=\u200Btest@google.com');
  });

  test('_isSubmitEnabled', () => {
    assert.isFalse(element.isSubmitEnabled());
    element.currentRevisionActions = {submit: {}};
    assert.isFalse(element.isSubmitEnabled());
    element.currentRevisionActions = {submit: {enabled: true}};
    assert.isTrue(element.isSubmitEnabled());
  });

  test('reply button has updated count when there are drafts', () => {
    const getLabel = (canReview: boolean) => {
      element.change!.actions!.ready = {enabled: canReview};
      return element.computeReplyButtonLabel();
    };
    element.change = createParsedChange();
    element.change.actions = {};
    element.draftCount = 0;
    assert.equal(getLabel(false), 'Reply');
    assert.equal(getLabel(true), 'Start Review');

    element.draftCount = 0;
    assert.equal(getLabel(false), 'Reply');
    assert.equal(getLabel(true), 'Start Review');

    element.draftCount = 3;
    assert.equal(getLabel(false), 'Reply (3)');
    assert.equal(getLabel(true), 'Start Review (3)');
  });

  test('computeCopyTextForTitle', () => {
    element.change = {
      ...createChangeViewChange(),
      _number: 123 as NumericChangeId,
      subject: 'test subject',
      revisions: {
        rev1: createRevision(1),
        rev3: createRevision(3),
      },
      current_revision: 'rev3' as CommitId,
    };
    assert.equal(
      element.computeCopyTextForTitle(),
      `123: test subject | http://${location.host}/c/test-project/+/123`
    );
  });

  test('show commit message edit button', () => {
    const change = createParsedChange();
    const mergedChanged: ParsedChangeInfo = {
      ...createParsedChange(),
      status: ChangeStatus.MERGED,
    };
    assert.isTrue(element.computeHideEditCommitMessage(false, false, change));
    assert.isTrue(element.computeHideEditCommitMessage(true, true, change));
    assert.isTrue(element.computeHideEditCommitMessage(false, true, change));
    assert.isFalse(element.computeHideEditCommitMessage(true, false, change));
    assert.isTrue(
      element.computeHideEditCommitMessage(true, false, mergedChanged)
    );
    assert.isFalse(element.computeHideEditCommitMessage(true, false, change));
  });

  test('handleCommitMessageSave trims trailing whitespace', async () => {
    element.changeNum = TEST_NUMERIC_CHANGE_ID;
    element.change = createChangeViewChange();
    // Response code is 500, because we want to avoid window reloading
    const putStub = stubRestApi('putChangeCommitMessage').returns(
      Promise.resolve(new Response(null, {status: 500}))
    );
    await element.updateComplete;
    const committerEmail = 'test@example.org';
    const mockEvent = (content: string, committerEmail: string) =>
      new CustomEvent('', {
        detail: {content, committerEmail},
      });

    assertIsDefined(element.commitMessageEditor);
    await element.handleCommitMessageSave(
      mockEvent('test \n  test ', committerEmail)
    );
    assert.equal(putStub.lastCall.args[1], 'test\n  test');
    element.commitMessageEditor.disabled = false;
    await element.handleCommitMessageSave(
      mockEvent('  test\ntest', committerEmail)
    );
    assert.equal(putStub.lastCall.args[1], '  test\ntest');
    element.commitMessageEditor.disabled = false;
    await element.handleCommitMessageSave(
      mockEvent('\n\n\n\n\n\n\n\n', committerEmail)
    );
    assert.equal(putStub.lastCall.args[1], '\n\n\n\n\n\n\n\n');
  });

  test('openReplyDialog called with `ANY` when coming from tap event', async () => {
    await element.updateComplete;
    assertIsDefined(element.replyBtn);
    const openStub = sinon.stub(element, 'openReplyDialog');
    element.replyBtn.click();
    assert(
      openStub.lastCall.calledWithExactly(FocusTarget.ANY),
      'openReplyDialog should have been passed ANY'
    );
    assert.equal(openStub.callCount, 1);
  });

  test('reply dialog focus can be controlled', () => {
    const openStub = sinon.stub(element, 'openReplyDialog');

    const e = new CustomEvent('show-reply-dialog', {
      detail: {value: {reviewersOnly: true, ccsOnly: false}},
    });
    element.handleShowReplyDialog(e);
    assert(
      openStub.lastCall.calledWithExactly(FocusTarget.REVIEWERS),
      'openReplyDialog should have been passed REVIEWERS'
    );
    assert.equal(openStub.callCount, 1);

    e.detail.value = {reviewersOnly: false, ccsOnly: true};
    element.handleShowReplyDialog(e);
    assert(
      openStub.lastCall.calledWithExactly(FocusTarget.CCS),
      'openReplyDialog should have been passed CCS'
    );
    assert.equal(openStub.callCount, 2);
  });

  test('getUrlParameter functionality', () => {
    const locationStub = sinon.stub(element, 'getLocationSearch');
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
      .stub(testResolver(pluginLoaderToken), 'awaitPluginsLoaded')
      .callsFake(() => Promise.resolve());

    element.basePatchNum = PARENT;
    element.patchNum = 2 as RevisionPatchSetNum;
    element.change = {
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
    assertIsDefined(element.actions);
    sinon
      .stub(element.actions, 'showRevertDialog')
      .callsFake(async () => promise.resolve());

    element.maybeShowRevertDialog();
    assert.isTrue(awaitPluginsLoadedStub.called);
    await promise;
  });

  suite('reply dialog tests', () => {
    setup(async () => {
      element.change = {
        ...createChangeViewChange(),
        // element has latest info
        revisions: {rev1: createRevision()},
        messages: createChangeMessages(1),
        current_revision: 'rev1' as CommitId,
        labels: {},
      };
      await element.updateComplete;
    });

    test('show reply dialog on open-reply-dialog event', async () => {
      const openReplyDialogStub = sinon.stub(element, 'openReplyDialog');
      element.dispatchEvent(
        new CustomEvent('open-reply-dialog', {
          composed: true,
          bubbles: true,
          detail: {},
        })
      );
      await element.updateComplete;
      assert.isTrue(openReplyDialogStub.calledOnce);
    });
  });

  test('header class computation', async () => {
    assert.equal(element.computeHeaderClass(), 'header');
    element.editMode = true;
    assert.equal(element.computeHeaderClass(), 'header editMode');
  });

  test('maybeScrollToMessage', async () => {
    element.change = {
      ...createChangeViewChange(),
      messages: createChangeMessages(1),
    };
    await element.updateComplete;
    const scrollStub = sinon.stub(element.messagesList!, 'scrollToMessage');

    await element.maybeScrollToMessage('');
    assert.isFalse(scrollStub.called);
    await element.maybeScrollToMessage('message');
    assert.isFalse(scrollStub.called);
    await element.maybeScrollToMessage('#message-TEST');
    assert.isTrue(scrollStub.called);
    assert.equal(scrollStub.lastCall.args[0], 'TEST');
  });

  test('file-action-tap handling', async () => {
    element.patchNum = 1 as RevisionPatchSetNum;
    element.change = {
      ...createChangeViewChange(),
    };
    assertIsDefined(element.fileList);
    assertIsDefined(element.fileListHeader);
    const fileList = element.fileList;
    const Actions = GrEditConstants.Actions;
    element.fileListHeader.editMode = true;
    await element.fileListHeader.updateComplete;
    await element.updateComplete;
    const controls = queryAndAssert<GrEditControls>(
      element.fileListHeader,
      '#editControls'
    );
    const openDeleteDialogStub = sinon.stub(controls, 'openDeleteDialog');
    const openRenameDialogStub = sinon.stub(controls, 'openRenameDialog');
    const openRestoreDialogStub = sinon.stub(controls, 'openRestoreDialog');

    // Delete
    fileList.dispatchEvent(
      new CustomEvent('file-action-tap', {
        detail: {action: Actions.DELETE.id, path: 'foo'},
        bubbles: true,
        composed: true,
      })
    );
    await element.updateComplete;

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
    await element.updateComplete;

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
    await element.updateComplete;

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
    await element.updateComplete;

    assert.isTrue(setUrlStub.called);
  });

  suite('handleEditTap', () => {
    let fireEdit: () => void;

    setup(() => {
      fireEdit = () => {
        assertIsDefined(element.actions);
        element.actions.dispatchEvent(new CustomEvent('edit-tap'));
      };

      element.change = {
        ...createChangeViewChange(),
        revisions: {rev1: createRevision()},
      };
    });

    test('edit exists in revisions', async () => {
      assertIsDefined(element.change);
      const newChange = {...element.change};
      newChange.revisions.rev2 = createRevision(EDIT);
      element.change = newChange;
      await element.updateComplete;

      fireEdit();
      assert.isTrue(setUrlStub.called);
      assert.equal(setUrlStub.lastCall.firstArg, '/c/test-project/+/42/edit');
    });

    test('no edit exists in revisions, non-latest patchset', async () => {
      assertIsDefined(element.change);
      const newChange = {...element.change};
      newChange.revisions.rev2 = createRevision(2);
      element.change = newChange;
      element.viewModelPatchNum = 1 as RevisionPatchSetNum;
      await element.updateComplete;

      fireEdit();
      assert.isTrue(setUrlStub.called);
      assert.equal(
        setUrlStub.lastCall.firstArg,
        '/c/test-project/+/42/1,edit?forceReload=true'
      );
    });

    test('no edit exists in revisions, latest patchset', async () => {
      assertIsDefined(element.change);
      const newChange = {...element.change};
      newChange.revisions.rev2 = createRevision(2);
      element.change = newChange;
      element.patchNum = 2 as RevisionPatchSetNum;
      await element.updateComplete;

      fireEdit();
      assert.isTrue(setUrlStub.called);
      assert.equal(
        setUrlStub.lastCall.firstArg,
        '/c/test-project/+/42,edit?forceReload=true'
      );
    });
  });

  test('handleStopEditTap', async () => {
    element.change = {
      ...createChangeViewChange(),
    };
    await element.updateComplete;
    assertIsDefined(element.metadata);
    assertIsDefined(element.actions);
    sinon.stub(element.metadata, 'computeLabelNames');

    element.patchNum = 1 as RevisionPatchSetNum;
    element.actions.dispatchEvent(
      new CustomEvent('stop-edit-tap', {bubbles: false})
    );

    assert.isTrue(setUrlStub.called);
    assert.equal(
      setUrlStub.lastCall.firstArg,
      '/c/test-project/+/42/1?forceReload=true'
    );
  });

  suite('plugin endpoints', () => {
    test('endpoint params', async () => {
      element.change = {...createChangeViewChange(), labels: {}};
      element.revision = createRevision();
      const promise = mockPromise();
      window.Gerrit.install(
        promise.resolve,
        '0.1',
        'http://some/plugins/url.js'
      );
      await element.updateComplete;
      const plugin: PluginApi = (await promise) as PluginApi;
      const hookEl = await plugin
        .hook('change-view-integration')
        .getLastAttached();
      assert.strictEqual((hookEl as any).plugin, plugin);
      assert.strictEqual((hookEl as any).change, element.change);
      assert.strictEqual((hookEl as any).revision, element.revision);
    });
  });

  test('handleToggleStar called when star is tapped', async () => {
    element.change = {
      ...createChangeViewChange(),
      owner: {_account_id: 1 as AccountId},
      starred: false,
    };
    element.loggedIn = true;
    await element.updateComplete;

    const stub = sinon.stub(element, 'handleToggleStar');

    const changeStar = queryAndAssert<GrChangeStar>(element, '#changeStar');
    queryAndAssert<HTMLButtonElement>(changeStar, 'button').click();
    assert.isTrue(stub.called);
  });

  suite('gr-reporting tests', () => {
    setup(() => {
      element.basePatchNum = PARENT;
      element.patchNum = 1 as RevisionPatchSetNum;
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
      element.handleReplySent();
      await element.updateComplete;
      assert.isFalse(changeDisplayStub.called);
      assert.isFalse(changeFullyLoadedStub.called);
    });

    test('report changeDisplayed and changeFullyLoaded', async () => {
      const commentsModel = testResolver(commentsModelToken);
      stubRestApi('getChangeOrEditFiles').resolves({
        'a-file.js': {},
      });
      const changeDisplayStub = sinon.stub(
        element.reporting,
        'changeDisplayed'
      );
      const changeFullyLoadedStub = sinon.stub(
        element.reporting,
        'changeFullyLoaded'
      );
      // reset so reload is triggered
      element.changeNum = undefined;
      element.viewState = {
        ...createChangeViewState(),
        changeNum: TEST_NUMERIC_CHANGE_ID,
        repo: TEST_PROJECT_NAME,
      };
      changeModel.updateState({
        loadingStatus: LoadingStatus.LOADED,
        change: {
          ...createChangeViewChange(),
          labels: {},
          current_revision: 'foo' as CommitId,
          revisions: {foo: createRevision()},
        },
      });

      await waitUntil(() => changeDisplayStub.called);
      assert.isTrue(changeDisplayStub.called);
      assert.isFalse(changeFullyLoadedStub.called);

      element.mergeable = true;
      commentsModel.setState({
        comments: {},
        drafts: {},
        discardedDrafts: [],
      });

      await waitUntil(() => changeFullyLoadedStub.called);
      assert.isTrue(changeFullyLoadedStub.called);
    });
  });

  test('renders sha in copy links', async () => {
    stubFlags('isEnabled').returns(true);
    const sha = '123' as CommitId;
    element.change = {
      ...createChangeViewChange(),
      status: ChangeStatus.MERGED,
      current_revision: sha,
    };
    await element.updateComplete;

    const copyLinksDialog = queryAndAssert<GrCopyLinks>(
      element,
      'gr-copy-links'
    );
    assert.isTrue(
      copyLinksDialog.copyLinks.some(copyLink => copyLink.value === sha)
    );
  });

  test('copy links without a base URL', async () => {
    element.change = createChangeViewChange();
    await element.updateComplete;

    const copyLinksDialog = queryAndAssert<GrCopyLinks>(
      element,
      'gr-copy-links'
    );
    assert.deepEqual(copyLinksDialog.copyLinks[1], {
      label: 'Change URL',
      shortcut: 'u',
      value: 'http://localhost:9876/c/test-project/+/42',
    });
  });

  test('copy links with a base URL having a path', async () => {
    stubBaseUrl('/review');
    element.change = createChangeViewChange();
    await element.updateComplete;

    const copyLinksDialog = queryAndAssert<GrCopyLinks>(
      element,
      'gr-copy-links'
    );

    assert.deepEqual(copyLinksDialog.copyLinks[1], {
      label: 'Change URL',
      shortcut: 'u',
      value: 'http://localhost:9876/review/c/test-project/+/42',
    });
  });
});
