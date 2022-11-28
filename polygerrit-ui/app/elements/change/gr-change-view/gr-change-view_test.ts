/**
 * @license
 * Copyright 2015 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import '../../edit/gr-edit-constants';
import '../gr-thread-list/gr-thread-list';
import './gr-change-view';
import {
  ChangeStatus,
  CommentSide,
  DefaultBase,
  DiffViewMode,
  MessageTag,
  createDefaultPreferences,
  Tab,
} from '../../../constants/constants';
import {GrEditConstants} from '../../edit/gr-edit-constants';
import {navigationToken} from '../../core/gr-navigation/gr-navigation';
import {PluginApi} from '../../../api/plugin';
import {
  mockPromise,
  pressKey,
  queryAndAssert,
  stubFlags,
  stubRestApi,
  waitEventLoop,
  waitQueryAndAssert,
  waitUntil,
  waitUntilVisible,
} from '../../../test/test-utils';
import {
  createChangeViewState,
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
  createDraft,
} from '../../../test/test-data-generators';
import {GrChangeView} from './gr-change-view';
import {
  AccountId,
  ApprovalInfo,
  BasePatchSetNum,
  ChangeId,
  ChangeInfo,
  CommitId,
  EDIT,
  NumericChangeId,
  PARENT,
  RelatedChangeAndCommitInfo,
  ReviewInputTag,
  RevisionInfo,
  RevisionPatchSetNum,
  RobotId,
  RobotCommentInfo,
  Timestamp,
  UrlEncodedCommentId,
  DetailedLabelInfo,
  RepoName,
  QuickLabelInfo,
  PatchSetNumber,
} from '../../../types/common';
import {GrEditControls} from '../../edit/gr-edit-controls/gr-edit-controls';
import {SinonFakeTimers, SinonStubbedMember} from 'sinon';
import {RestApiService} from '../../../services/gr-rest-api/gr-rest-api';
import {CommentThread} from '../../../utils/comment-util';
import {GerritView} from '../../../services/router/router-model';
import {ParsedChangeInfo} from '../../../types/types';
import {GrRelatedChangesList} from '../gr-related-changes-list/gr-related-changes-list';
import {ChangeStates} from '../../shared/gr-change-status/gr-change-status';
import {
  ChangeModel,
  changeModelToken,
  LoadingStatus,
} from '../../../models/change/change-model';
import {FocusTarget, GrReplyDialog} from '../gr-reply-dialog/gr-reply-dialog';
import {GrChangeStar} from '../../shared/gr-change-star/gr-change-star';
import {GrThreadList} from '../gr-thread-list/gr-thread-list';
import {assertIsDefined} from '../../../utils/common-util';
import {DEFAULT_NUM_FILES_SHOWN} from '../gr-file-list/gr-file-list';
import {fixture, html, assert} from '@open-wc/testing';
import {deepClone} from '../../../utils/deep-util';
import {Modifier} from '../../../utils/dom-util';
import {GrButton} from '../../shared/gr-button/gr-button';
import {GrCopyLinks} from '../gr-copy-links/gr-copy-links';
import {ChangeViewState} from '../../../models/views/change';
import {rootUrl} from '../../../utils/url-util';
import {testResolver} from '../../../test/common-test-setup';
import {UserModel, userModelToken} from '../../../models/user/user-model';
import {pluginLoaderToken} from '../../shared/gr-js-api-interface/gr-plugin-loader';

suite('gr-change-view tests', () => {
  let element: GrChangeView;
  let setUrlStub: sinon.SinonStub;
  let userModel: UserModel;
  let changeModel: ChangeModel;

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
          patch_set: 2 as RevisionPatchSetNum,
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
          __draft: true,
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
          path: '/COMMIT_MSG',
          author: {
            _account_id: 1000000 as AccountId,
            name: 'user',
            username: 'user',
          },
          patch_set: 3 as RevisionPatchSetNum,
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
            // eslint-disable-next-line @typescript-eslint/no-unnecessary-type-assertion
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
          __draft: true,
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
      comments: [
        {
          path: '/COMMIT_MSG',
          author: {
            _account_id: 1000000 as AccountId,
            name: 'user',
            username: 'user',
          },
          patch_set: 4 as RevisionPatchSetNum,
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
          patch_set: 4 as RevisionPatchSetNum,
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
        <div aria-hidden="false" class="container" hidden="" id="mainContent">
          <section class="changeInfoSection">
            <div class="header">
              <h1 class="assistive-tech-only">Change :</h1>
              <div class="headerTitle">
                <div class="changeStatuses"></div>
                <gr-button
                  aria-disabled="false"
                  class="showCopyLinkDialogButton"
                  down-arrow=""
                  flatten=""
                  role="button"
                  tabindex="0"
                  ><gr-change-star id="changeStar"> </gr-change-star>
                  <a aria-label="Change undefined" class="changeNumber"> </a>
                </gr-button>
                <span class="headerSubject"> </span>
                <gr-copy-clipboard
                  class="changeCopyClipboard"
                  hideinput=""
                  text="undefined: undefined | http://localhost:9876undefined"
                >
                </gr-copy-clipboard>
              </div>
              <div class="commitActions">
                <gr-change-actions hidden="" id="actions"> </gr-change-actions>
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
                        <gr-formatted-text></gr-formatted-text>
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
                    <gr-related-changes-list id="relatedChanges">
                    </gr-related-changes-list>
                  </div>
                  <div class="emptySpace"></div>
                </div>
              </div>
            </div>
          </section>
          <h2 class="assistive-tech-only">Files and Comments tabs</h2>
          <paper-tabs dir="null" id="tabs" role="tablist" tabindex="0">
            <paper-tab
              aria-disabled="false"
              aria-selected="true"
              class="iron-selected"
              data-name="files"
              role="tab"
              tabindex="0"
            >
              <span> Files </span>
            </paper-tab>
            <paper-tab
              aria-disabled="false"
              aria-selected="false"
              class="commentThreads"
              data-name="comments"
              role="tab"
              tabindex="-1"
            >
              <gr-tooltip-content has-tooltip="" title="">
                <span> Comments </span>
              </gr-tooltip-content>
            </paper-tab>
            <paper-tab
              aria-disabled="false"
              aria-selected="false"
              data-name="change-view-tab-header-url"
              role="tab"
              tabindex="-1"
            >
              <gr-endpoint-decorator name="change-view-tab-header-url">
                <gr-endpoint-param name="change"> </gr-endpoint-param>
                <gr-endpoint-param name="revision"> </gr-endpoint-param>
              </gr-endpoint-decorator>
            </paper-tab>
          </paper-tabs>
          <section class="tabContent">
            <div>
              <gr-file-list-header id="fileListHeader"> </gr-file-list-header>
              <gr-file-list id="fileList"> </gr-file-list>
            </div>
          </section>
          <gr-endpoint-decorator name="change-view-integration">
            <gr-endpoint-param name="change"> </gr-endpoint-param>
            <gr-endpoint-param name="revision"> </gr-endpoint-param>
          </gr-endpoint-decorator>
          <paper-tabs dir="null" role="tablist" tabindex="0">
            <paper-tab
              aria-disabled="false"
              aria-selected="false"
              class="changeLog"
              data-name="_changeLog"
              role="tab"
              tabindex="-1"
            >
              Change Log
            </paper-tab>
          </paper-tabs>
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
    element.patchRange = {
      basePatchNum: PARENT,
      patchNum: 1 as RevisionPatchSetNum,
    };
    element.change = createChangeViewChange();
    await element.updateComplete;
    const replaceStateStub = sinon.stub(history, 'replaceState');
    element.handleMessageAnchorTap(
      new CustomEvent('message-anchor-tap', {detail: {id: 'a12345'}})
    );

    assert.isTrue(replaceStateStub.called);
  });

  test('handleDiffAgainstBase', () => {
    element.change = {
      ...createChangeViewChange(),
      revisions: createRevisions(10),
    };
    element.patchRange = {
      patchNum: 3 as RevisionPatchSetNum,
      basePatchNum: 1 as BasePatchSetNum,
    };
    element.handleDiffAgainstBase();
    assert.isTrue(setUrlStub.called);
    assert.equal(setUrlStub.lastCall.firstArg, '/c/test-project/+/42/3');
  });

  test('handleDiffAgainstLatest', () => {
    element.change = {
      ...createChangeViewChange(),
      revisions: createRevisions(10),
    };
    element.patchRange = {
      basePatchNum: 1 as BasePatchSetNum,
      patchNum: 3 as RevisionPatchSetNum,
    };
    element.handleDiffAgainstLatest();
    assert.isTrue(setUrlStub.called);
    assert.equal(setUrlStub.lastCall.firstArg, '/c/test-project/+/42/1..10');
  });

  test('handleDiffBaseAgainstLeft', () => {
    element.change = {
      ...createChangeViewChange(),
      revisions: createRevisions(10),
    };
    element.patchRange = {
      patchNum: 3 as RevisionPatchSetNum,
      basePatchNum: 1 as BasePatchSetNum,
    };
    element.handleDiffBaseAgainstLeft();
    assert.isTrue(setUrlStub.called);
    assert.equal(setUrlStub.lastCall.firstArg, '/c/test-project/+/42/1');
  });

  test('handleDiffRightAgainstLatest', () => {
    element.change = {
      ...createChangeViewChange(),
      revisions: createRevisions(10),
    };
    element.patchRange = {
      basePatchNum: 1 as BasePatchSetNum,
      patchNum: 3 as RevisionPatchSetNum,
    };
    element.handleDiffRightAgainstLatest();
    assert.isTrue(setUrlStub.called);
    assert.equal(setUrlStub.lastCall.firstArg, '/c/test-project/+/42/3..10');
  });

  test('handleDiffBaseAgainstLatest', () => {
    element.change = {
      ...createChangeViewChange(),
      revisions: createRevisions(10),
    };
    element.patchRange = {
      basePatchNum: 1 as BasePatchSetNum,
      patchNum: 3 as RevisionPatchSetNum,
    };
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
    element.patchRange = {
      basePatchNum: 1 as BasePatchSetNum,
      patchNum: 3 as RevisionPatchSetNum,
    };
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
      const paperTabs = tabs.querySelectorAll<HTMLElement>('paper-tab');
      // 4 Tabs are : Files, Comment Threads, Plugin
      assert.equal(tabs.querySelectorAll('paper-tab').length, 3);
      assert.equal(paperTabs[0].dataset.name, 'files');
      assert.equal(paperTabs[1].dataset.name, 'comments');
      assert.equal(paperTabs[2].dataset.name, 'change-view-tab-header-url');
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

    test('param change should switch primary tab correctly', async () => {
      assert.equal(element.activeTab, Tab.FILES);
      // view is required
      element.changeNum = undefined;
      element.viewState = {
        ...createChangeViewState(),
        ...element.viewState,
        tab: Tab.COMMENT_THREADS,
      };
      await element.updateComplete;
      assert.equal(element.activeTab, Tab.COMMENT_THREADS);
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
      const paperTabs = element.shadowRoot!.querySelector('#tabs')!;
      paperTabs.querySelectorAll('paper-tab')[2].click();
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
      sinon.stub(element, 'performPostChangeLoadTasks');
      sinon.stub(element, 'getMergeability');
      const change = {
        ...createChangeViewChange(),
        revisions: createRevisions(1),
        messages: createChangeMessages(1),
      };
      change.labels = {};
      element.change = change;

      changeModel.setState({
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
      element.patchRange = {
        basePatchNum: PARENT,
        patchNum: 1 as RevisionPatchSetNum,
      };
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
      const relatedChanges = element.shadowRoot!.querySelector(
        '#relatedChanges'
      ) as GrRelatedChangesList;
      sinon.stub(relatedChanges, 'reload');
      sinon.stub(element, 'loadData').returns(Promise.resolve());
      sinon.spy(element, 'viewStateChanged');
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
      const paperTabs = element.shadowRoot!.querySelector('#tabs')!;
      const tabs = paperTabs.querySelectorAll('paper-tab');
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

  suite('Findings robot-comment tab', () => {
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
      element.showFindingsTab = true;
      await element.updateComplete;
      const paperTabs = element.shadowRoot!.querySelector('#tabs')!;
      const tabs = paperTabs.querySelectorAll('paper-tab');
      assert.isTrue(tabs.length > 3);
      assert.equal(tabs[3].dataset.name, 'findings');
      tabs[3].click();
      await element.updateComplete;
    });

    test('robot comments count per patchset', () => {
      const count = element.robotCommentCountPerPatchSet(THREADS);
      const expectedCount = {
        2: 1,
        3: 1,
        4: 2,
      };
      assert.deepEqual(count, expectedCount);
      assert.equal(
        element.computeText(createRevision(2), THREADS),
        'Patchset 2 (1 finding)'
      );
      assert.equal(
        element.computeText(createRevision(4), THREADS),
        'Patchset 4 (2 findings)'
      );
      assert.equal(
        element.computeText(createRevision(5), THREADS),
        'Patchset 5'
      );
    });

    test('only robot comments are rendered', () => {
      assert.equal(element.computeRobotCommentThreads().length, 2);
      assert.equal(
        (
          element.computeRobotCommentThreads()[0]
            .comments[0] as RobotCommentInfo
        ).robot_id,
        'rc1'
      );
      assert.equal(
        (
          element.computeRobotCommentThreads()[1]
            .comments[0] as RobotCommentInfo
        ).robot_id,
        'rc2'
      );
    });

    test('changing patchsets resets robot comments', async () => {
      assertIsDefined(element.change);
      const newChange = {...element.change};
      newChange.current_revision = 'rev3' as CommitId;
      element.change = newChange;
      await element.updateComplete;
      assert.equal(element.computeRobotCommentThreads().length, 1);
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
        element.commentThreads = arr;
        await element.updateComplete;
      });

      test('Show more button is rendered', () => {
        assert.isOk(element.shadowRoot!.querySelector('.show-robot-comments'));
        assert.equal(
          element.computeRobotCommentThreads().length,
          ROBOT_COMMENTS_LIMIT
        );
      });

      test('Clicking show more button renders all comments', async () => {
        element
          .shadowRoot!.querySelector<GrButton>('.show-robot-comments')!
          .click();
        await element.updateComplete;
        assert.equal(element.computeRobotCommentThreads().length, 62);
      });
    });
  });

  test('reply button is not visible when logged out', async () => {
    assertIsDefined(element.replyBtn);
    element.loggedIn = false;
    await element.updateComplete;
    assert.equal(getComputedStyle(element.replyBtn).display, 'none');
    element.loggedIn = true;
    await element.updateComplete;
    assert.notEqual(getComputedStyle(element.replyBtn).display, 'none');
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

  test('changeStatuses', async () => {
    element.loading = false;
    element.change = {
      ...createChangeViewChange(),
      revisions: {
        rev2: createRevision(2),
        rev1: createRevision(1),
        rev13: createRevision(13),
        rev3: createRevision(3),
      },
      current_revision: 'rev3' as CommitId,
      status: ChangeStatus.MERGED,
      labels: {
        test: {
          all: [],
          default_value: 0,
          values: {},
          approved: {},
        },
      },
    };
    element.mergeable = true;
    await element.updateComplete;
    const expectedStatuses = [ChangeStates.MERGED];
    assert.deepEqual(element.changeStatuses, expectedStatuses);
    const statusChips =
      element.shadowRoot!.querySelectorAll('gr-change-status');
    assert.equal(statusChips.length, 1);
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
      element.change = change;
      element.mergeable = true;
      element.currentRevisionActions = {submit: {enabled: true}};
      assert.isTrue(element.isSubmitEnabled());
      await element.updateComplete;
      element.computeRevertSubmitted(element.change);
      await element.updateComplete;
      assert.isFalse(
        element.changeStatuses?.includes(ChangeStates.REVERT_SUBMITTED)
      );
      assert.isFalse(
        element.changeStatuses?.includes(ChangeStates.REVERT_CREATED)
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
      element.change = change;
      element.mergeable = true;
      element.currentRevisionActions = {submit: {enabled: true}};
      assert.isTrue(element.isSubmitEnabled());
      await element.updateComplete;
      element.computeRevertSubmitted(element.change);
      await element.updateComplete;
      assert.isFalse(
        element.changeStatuses?.includes(ChangeStates.REVERT_SUBMITTED)
      );
      assert.isFalse(
        element.changeStatuses?.includes(ChangeStates.REVERT_CREATED)
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
      element.change = change;
      element.mergeable = true;
      element.currentRevisionActions = {submit: {enabled: true}};
      assert.isTrue(element.isSubmitEnabled());
      await element.updateComplete;
      element.computeRevertSubmitted(element.change);
      // Wait for promises to settle.
      await waitEventLoop();
      await element.updateComplete;
      assert.isFalse(
        element.changeStatuses?.includes(ChangeStates.REVERT_SUBMITTED)
      );
      assert.isTrue(
        element.changeStatuses?.includes(ChangeStates.REVERT_CREATED)
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
      element.change = change;
      element.mergeable = true;
      element.currentRevisionActions = {submit: {enabled: true}};
      assert.isTrue(element.isSubmitEnabled());
      await element.updateComplete;
      element.computeRevertSubmitted(element.change);
      // Wait for promises to settle.
      await waitEventLoop();
      await element.updateComplete;
      assert.isFalse(
        element.changeStatuses?.includes(ChangeStates.REVERT_CREATED)
      );
      assert.isTrue(
        element.changeStatuses?.includes(ChangeStates.REVERT_SUBMITTED)
      );
    });
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

  test('reload is called when an approved label is removed', async () => {
    const vote: ApprovalInfo = {
      ...createApproval(),
      _account_id: 1 as AccountId,
      name: 'bojack',
      value: 1,
    };
    element.changeNum = TEST_NUMERIC_CHANGE_ID;
    element.patchRange = {
      basePatchNum: PARENT,
      patchNum: 1 as RevisionPatchSetNum,
    };
    const change = {
      ...createParsedChange(),
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
    element.change = change;
    await element.updateComplete;
    const reloadStub = sinon.stub(element, 'loadData');
    const newChange = {...element.change};
    (newChange.labels!.test! as DetailedLabelInfo).all = [];
    element.change = deepClone(newChange);
    await element.updateComplete;
    assert.isFalse(reloadStub.called);

    assert.isDefined(element.change);
    const testLabels: DetailedLabelInfo & QuickLabelInfo =
      newChange.labels!.test;
    assertIsDefined(testLabels);
    testLabels.all!.push(vote);
    testLabels.all!.push(vote);
    testLabels.approved = vote;
    element.change = deepClone(newChange);
    await element.updateComplete;
    assert.isFalse(reloadStub.called);

    assert.isDefined(element.change);
    (newChange.labels!.test! as DetailedLabelInfo).all = [];
    element.change = deepClone(newChange);
    await element.updateComplete;
    assert.isTrue(reloadStub.called);
    assert.isTrue(reloadStub.calledOnce);
  });

  test('reply button has updated count when there are drafts', () => {
    const getLabel = (canReview: boolean) => {
      element.change!.actions!.ready = {enabled: canReview};
      return element.computeReplyButtonLabel();
    };
    element.change = createParsedChange();
    element.change.actions = {};
    element.diffDrafts = undefined;
    assert.equal(getLabel(false), 'Reply');
    assert.equal(getLabel(true), 'Reply');

    element.diffDrafts = {};
    assert.equal(getLabel(false), 'Reply');
    assert.equal(getLabel(true), 'Start Review');

    element.diffDrafts = {
      'file1.txt': [createDraft()],
      'file2.txt': [createDraft(), createDraft()],
    };
    assert.equal(getLabel(false), 'Reply (3)');
    assert.equal(getLabel(true), 'Start Review (3)');
  });

  test('change num change', async () => {
    const change = {
      ...createChangeViewChange(),
      labels: {},
    } as ParsedChangeInfo;
    element.changeNum = undefined;
    element.patchRange = {
      basePatchNum: PARENT,
      patchNum: 2 as RevisionPatchSetNum,
    };
    element.change = change;
    assertIsDefined(element.fileList);
    assert.equal(element.fileList.numFilesShown, DEFAULT_NUM_FILES_SHOWN);
    element.fileList.numFilesShown = 150;
    element.fileList.selectedIndex = 15;
    await element.updateComplete;

    element.changeNum = 2 as NumericChangeId;
    element.viewState = {
      ...createChangeViewState(),
      changeNum: 2 as NumericChangeId,
    };
    await element.updateComplete;
    assert.equal(element.fileList.numFilesShown, DEFAULT_NUM_FILES_SHOWN);
    assert.equal(element.fileList.selectedIndex, 0);
  });

  test('forceReload updates the change', async () => {
    assertIsDefined(element.fileList);
    const getChangeStub = stubRestApi('getChangeDetail').returns(
      Promise.resolve(createParsedChange())
    );
    const loadDataStub = sinon
      .stub(element, 'loadData')
      .callsFake(() => Promise.resolve());
    const collapseStub = sinon.stub(element.fileList, 'collapseAllDiffs');
    element.viewState = {...createChangeViewState(), forceReload: true};
    await element.updateComplete;
    assert.isTrue(getChangeStub.called);
    assert.isTrue(loadDataStub.called);
    assert.isTrue(collapseStub.called);
    // patchNum is set by changeChanged, so this verifies that change was set.
    assert.isOk(element.patchRange?.patchNum);
  });

  test('do not handle new change numbers', async () => {
    const recreateSpy = sinon.spy();
    element.addEventListener('recreate-change-view', recreateSpy);

    const value: ChangeViewState = createChangeViewState();
    element.viewState = value;
    await element.updateComplete;
    assert.isFalse(recreateSpy.calledOnce);

    value.changeNum = 555111333 as NumericChangeId;
    element.viewState = {...value};
    await element.updateComplete;
    assert.isTrue(recreateSpy.calledOnce);
  });

  test('related changes are updated when loadData is called', async () => {
    await element.updateComplete;
    const relatedChanges = element.shadowRoot!.querySelector(
      '#relatedChanges'
    ) as GrRelatedChangesList;
    const reloadStub = sinon.stub(relatedChanges, 'reload');
    stubRestApi('getMergeable').returns(
      Promise.resolve({...createMergeable(), mergeable: true})
    );

    element.viewState = createChangeViewState();
    changeModel.setState({
      loadingStatus: LoadingStatus.LOADED,
      change: {
        ...createChangeViewChange(),
      },
    });

    await element.loadData(true);
    assert.isFalse(setUrlStub.called);
    assert.isTrue(reloadStub.called);
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

  test('get latest revision', () => {
    let change: ChangeInfo = {
      ...createChange(),
      revisions: {
        rev1: createRevision(1),
        rev3: createRevision(3),
      },
      current_revision: 'rev3' as CommitId,
    };
    assert.equal(element.getLatestRevisionSHA(change), 'rev3');
    change = {
      ...createChange(),
      revisions: {
        rev1: createRevision(1),
      },
      current_revision: undefined,
    };
    assert.equal(element.getLatestRevisionSHA(change), 'rev1');
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
    assert.isTrue(
      element.computeHideEditCommitMessage(true, false, change, true)
    );
    assert.isFalse(
      element.computeHideEditCommitMessage(true, false, change, false)
    );
  });

  test('handleCommitMessageSave trims trailing whitespace', async () => {
    element.change = createChangeViewChange();
    // Response code is 500, because we want to avoid window reloading
    const putStub = stubRestApi('putChangeCommitMessage').returns(
      Promise.resolve(new Response(null, {status: 500}))
    );
    await element.updateComplete;
    const mockEvent = (content: string) =>
      new CustomEvent('', {detail: {content}});

    assertIsDefined(element.commitMessageEditor);
    element.handleCommitMessageSave(mockEvent('test \n  test '));
    assert.equal(putStub.lastCall.args[1], 'test\n  test');
    element.commitMessageEditor.disabled = false;
    element.handleCommitMessageSave(mockEvent('  test\ntest'));
    assert.equal(putStub.lastCall.args[1], '  test\ntest');
    element.commitMessageEditor.disabled = false;
    element.handleCommitMessageSave(mockEvent('\n\n\n\n\n\n\n\n'));
    assert.equal(putStub.lastCall.args[1], '\n\n\n\n\n\n\n\n');
  });

  test('topic is coalesced to null', async () => {
    sinon.stub(element, 'changeChanged');
    changeModel.setState({
      loadingStatus: LoadingStatus.LOADED,
      change: {
        ...createChangeViewChange(),
        labels: {},
        current_revision: 'foo' as CommitId,
        revisions: {foo: createRevision()},
      },
    });

    await element.performPostChangeLoadTasks();
    assert.isNull(element.change!.topic);
  });

  test('commit sha is populated from getChangeDetail', async () => {
    changeModel.setState({
      loadingStatus: LoadingStatus.LOADED,
      change: {
        ...createChangeViewChange(),
        labels: {},
        current_revision: 'foo' as CommitId,
        revisions: {foo: createRevision()},
      },
    });

    await element.performPostChangeLoadTasks();
    assert.equal('foo', element.commitInfo!.commit);
  });

  test('getBasePatchNum', async () => {
    element.change = {
      ...createChangeViewChange(),
      revisions: {
        '98da160735fb81604b4c40e93c368f380539dd0e': createRevision(),
      },
    };
    element.patchRange = {
      basePatchNum: PARENT,
    };
    await element.updateComplete;
    assert.equal(element.getBasePatchNum(), PARENT);

    element.prefs = {
      ...createPreferences(),
      default_base_for_merges: DefaultBase.FIRST_PARENT,
    };

    element.change = {
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
    await element.updateComplete;
    assert.equal(element.getBasePatchNum(), -1 as BasePatchSetNum);

    element.patchRange.basePatchNum = PARENT;
    element.patchRange.patchNum = 1 as RevisionPatchSetNum;
    await element.updateComplete;
    assert.equal(element.getBasePatchNum(), PARENT);
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

  test(
    'openReplyDialog called with `BODY` when coming from message reply' +
      'event',
    async () => {
      await element.updateComplete;
      const openStub = sinon.stub(element, 'openReplyDialog');
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
    const openStub = sinon.stub(element, 'openReplyDialog');

    const e = new CustomEvent('show-reply-dialog', {
      detail: {value: {ccsOnly: false}},
    });
    element.handleShowReplyDialog(e);
    assert(
      openStub.lastCall.calledWithExactly(FocusTarget.REVIEWERS),
      'openReplyDialog should have been passed REVIEWERS'
    );
    assert.equal(openStub.callCount, 1);

    e.detail.value = {ccsOnly: true};
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

    element.patchRange = {
      basePatchNum: PARENT,
      patchNum: 2 as RevisionPatchSetNum,
    };
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
      .callsFake(() => promise.resolve());

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

    test('reply from comment adds quote text', async () => {
      const change = {
        ...createChangeViewChange(),
        revisions: createRevisions(1),
        messages: createChangeMessages(1),
      };
      changeModel.setState({
        loadingStatus: LoadingStatus.LOADED,
        change,
      });
      const e = new CustomEvent('', {
        detail: {message: {message: 'quote text'}},
      });
      element.handleMessageReply(e);
      const dialog = await waitQueryAndAssert<GrReplyDialog>(
        element,
        '#replyDialog'
      );
      const openSpy = sinon.spy(dialog, 'open');
      await element.updateComplete;
      await waitUntil(() => openSpy.called && !!openSpy.lastCall.args[1]);
      assert.equal(openSpy.lastCall.args[1], '> quote text\n\n');
    });
  });

  test('header class computation', () => {
    assert.equal(element.computeHeaderClass(), 'header');
    assertIsDefined(element.viewState);
    element.viewState.edit = true;
    assert.equal(element.computeHeaderClass(), 'header editMode');
  });

  test('maybeScrollToMessage', async () => {
    await element.updateComplete;
    const scrollStub = sinon.stub(element.messagesList!, 'scrollToMessage');

    element.maybeScrollToMessage('');
    assert.isFalse(scrollStub.called);
    element.maybeScrollToMessage('message');
    assert.isFalse(scrollStub.called);
    element.maybeScrollToMessage('#message-TEST');
    assert.isTrue(scrollStub.called);
    assert.equal(scrollStub.lastCall.args[0], 'TEST');
  });

  test('computeEditMode', async () => {
    const callCompute = async (viewState: ChangeViewState) => {
      element.viewState = viewState;
      await element.updateComplete;
      return element.getEditMode();
    };
    assert.isTrue(
      await callCompute({
        ...createChangeViewState(),
        edit: true,
        basePatchNum: PARENT,
        patchNum: 1 as RevisionPatchSetNum,
      })
    );
    assert.isFalse(
      await callCompute({
        ...createChangeViewState(),
        basePatchNum: PARENT,
        patchNum: 1 as RevisionPatchSetNum,
      })
    );
    assert.isTrue(
      await callCompute({
        ...createChangeViewState(),
        basePatchNum: 1 as BasePatchSetNum,
        patchNum: EDIT,
      })
    );
  });

  test('processEdit', () => {
    element.patchRange = {};
    const change: ParsedChangeInfo = {
      ...createChangeViewChange(),
      current_revision: 'foo' as CommitId,
      revisions: {
        foo: {...createRevision()},
      },
    };

    // With no edit, nothing happens.
    element.processEdit(change);
    assert.equal(element.patchRange.patchNum, undefined);

    change.revisions['bar'] = {
      _number: EDIT,
      basePatchNum: 1 as BasePatchSetNum,
      commit: {
        ...createCommit(),
        commit: 'bar' as CommitId,
      },
      fetch: {},
    };

    // When edit is set, but not patchNum, then switch to edit ps.
    element.processEdit(change);
    assert.equal(element.patchRange.patchNum, EDIT);

    // When edit is set, but patchNum as well, then keep patchNum.
    element.patchRange.patchNum = 5 as RevisionPatchSetNum;
    element.routerPatchNum = 5 as RevisionPatchSetNum;
    element.processEdit(change);
    assert.equal(element.patchRange.patchNum, 5 as RevisionPatchSetNum);
  });

  test('file-action-tap handling', async () => {
    element.patchRange = {
      basePatchNum: PARENT,
      patchNum: 1 as RevisionPatchSetNum,
    };
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

  test('selectedRevision updates when patchNum is changed', async () => {
    const revision1: RevisionInfo = createRevision(1);
    const revision2: RevisionInfo = createRevision(2);
    changeModel.setState({
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
    userModel.setPreferences(createPreferences());

    element.patchRange = {patchNum: 2 as RevisionPatchSetNum};
    await element.performPostChangeLoadTasks();
    assert.strictEqual(element.selectedRevision, revision2);

    element.patchRange = {patchNum: 1 as RevisionPatchSetNum};
    await element.updateComplete;
    assert.strictEqual(element.selectedRevision, revision1);
  });

  test('selectedRevision is assigned when patchNum is edit', async () => {
    const revision1 = createRevision(1);
    const revision2 = createRevision(2);
    const revision3 = createEditRevision();
    changeModel.setState({
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
    stubRestApi('getPreferences').returns(Promise.resolve(createPreferences()));

    element.patchRange = {patchNum: EDIT};
    await element.performPostChangeLoadTasks();
    assert.strictEqual(element.selectedRevision, revision3);
  });

  test('sendShowChangeEvent', () => {
    const change = {...createChangeViewChange(), labels: {}};
    element.change = {...change};
    element.patchRange = {patchNum: 4 as RevisionPatchSetNum};
    element.mergeable = true;
    const showStub = sinon.stub(
      testResolver(pluginLoaderToken).jsApiService,
      'handleShowChange'
    );
    element.sendShowChangeEvent();
    assert.isTrue(showStub.calledOnce);
    assert.deepEqual(showStub.lastCall.args[0], {
      change,
      patchNum: 4 as PatchSetNumber,
      info: {mergeable: true},
    });
  });

  test('patch range changed', () => {
    element.patchRange = undefined;
    element.change = createChangeViewChange();
    element.change.revisions = createRevisions(4);
    element.change.current_revision = '1' as CommitId;
    element.change = {...element.change};

    const viewState = createChangeViewState();

    assert.isFalse(element.hasPatchRangeChanged(viewState));
    assert.isFalse(element.hasPatchNumChanged(viewState));

    viewState.basePatchNum = PARENT;
    // undefined means navigate to latest patchset
    viewState.patchNum = undefined;

    element.patchRange = {
      patchNum: 2 as RevisionPatchSetNum,
      basePatchNum: PARENT,
    };

    assert.isTrue(element.hasPatchRangeChanged(viewState));
    assert.isTrue(element.hasPatchNumChanged(viewState));

    element.patchRange = {
      patchNum: 4 as RevisionPatchSetNum,
      basePatchNum: PARENT,
    };

    assert.isFalse(element.hasPatchRangeChanged(viewState));
    assert.isFalse(element.hasPatchNumChanged(viewState));
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
      element.patchRange = {patchNum: 1 as RevisionPatchSetNum};
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
      element.patchRange = {patchNum: 2 as RevisionPatchSetNum};
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

    element.patchRange = {patchNum: 1 as RevisionPatchSetNum};
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
      element.selectedRevision = createRevision();
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
      assert.strictEqual((hookEl as any).revision, element.selectedRevision);
    });
  });

  suite('getMergeability', () => {
    let getMergeableStub: SinonStubbedMember<RestApiService['getMergeable']>;
    setup(() => {
      element.change = {...createChangeViewChange(), labels: {}};
      getMergeableStub = stubRestApi('getMergeable').returns(
        Promise.resolve({...createMergeable(), mergeable: true})
      );
    });

    test('merged change', () => {
      element.mergeable = null;
      element.change!.status = ChangeStatus.MERGED;
      return element.getMergeability().then(() => {
        assert.isFalse(element.mergeable);
        assert.isFalse(getMergeableStub.called);
      });
    });

    test('abandoned change', () => {
      element.mergeable = null;
      element.change!.status = ChangeStatus.ABANDONED;
      return element.getMergeability().then(() => {
        assert.isFalse(element.mergeable);
        assert.isFalse(getMergeableStub.called);
      });
    });

    test('open change', () => {
      element.mergeable = null;
      return element.getMergeability().then(() => {
        assert.isTrue(element.mergeable);
        assert.isTrue(getMergeableStub.called);
      });
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
    queryAndAssert<HTMLButtonElement>(changeStar, 'button')!.click();
    assert.isTrue(stub.called);
  });

  suite('gr-reporting tests', () => {
    setup(() => {
      element.patchRange = {
        basePatchNum: PARENT,
        patchNum: 1 as RevisionPatchSetNum,
      };
      sinon
        .stub(element, 'performPostChangeLoadTasks')
        .returns(Promise.resolve(false));
      sinon.stub(element, 'getMergeability').returns(Promise.resolve());
      sinon.stub(element, 'getLatestCommitMessage').returns(Promise.resolve());
      sinon
        .stub(element, 'reloadPatchNumDependentResources')
        .returns(Promise.resolve());
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

    test('report changeDisplayed on viewStateChanged', async () => {
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
      changeModel.setState({
        loadingStatus: LoadingStatus.LOADED,
        change: {
          ...createChangeViewChange(),
          labels: {},
          current_revision: 'foo' as CommitId,
          revisions: {foo: createRevision()},
        },
      });
      await element.updateComplete;
      await waitEventLoop();
      assert.isTrue(changeDisplayStub.called);
      assert.isTrue(changeFullyLoadedStub.called);
    });
  });

  test('calculateHasParent', () => {
    const changeId = '123' as ChangeId;
    const relatedChanges: RelatedChangeAndCommitInfo[] = [];

    assert.equal(element.calculateHasParent(changeId, relatedChanges), false);

    relatedChanges.push({
      ...createRelatedChangeAndCommitInfo(),
      change_id: '123' as ChangeId,
    });
    assert.equal(element.calculateHasParent(changeId, relatedChanges), false);

    relatedChanges.push({
      ...createRelatedChangeAndCommitInfo(),
      change_id: '234' as ChangeId,
    });
    assert.equal(element.calculateHasParent(changeId, relatedChanges), true);
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
});
