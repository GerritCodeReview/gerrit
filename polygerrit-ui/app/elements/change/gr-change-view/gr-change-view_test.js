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

import '../../../test/common-test-setup-karma.js';
import '../../edit/gr-edit-constants.js';
import './gr-change-view.js';
import {PrimaryTab, SecondaryTab, ChangeStatus} from '../../../constants/constants.js';

import {dom} from '@polymer/polymer/lib/legacy/polymer.dom.js';
import {GrEditConstants} from '../../edit/gr-edit-constants.js';
import {_testOnly_resetEndpoints} from '../../shared/gr-js-api-interface/gr-plugin-endpoints.js';
import {getComputedStyleValue} from '../../../utils/dom-util.js';
import {GerritNav} from '../../core/gr-navigation/gr-navigation.js';
import {pluginLoader} from '../../shared/gr-js-api-interface/gr-plugin-loader.js';
import {_testOnly_initGerritPluginApi} from '../../shared/gr-js-api-interface/gr-gerrit.js';

import 'lodash/lodash.js';
import {generateChange, TestKeyboardShortcutBinder} from '../../../test/test-utils.js';
import {SPECIAL_PATCH_SET_NUM} from '../../../utils/patch-set-util.js';
import {Shortcut} from '../../../mixins/keyboard-shortcut-mixin/keyboard-shortcut-mixin.js';

const pluginApi = _testOnly_initGerritPluginApi();
const fixture = fixtureFromElement('gr-change-view');

suite('gr-change-view tests', () => {
  let element;

  let navigateToChangeStub;

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
  const THREADS = [
    {
      comments: [
        {
          __path: '/COMMIT_MSG',
          author: {
            _account_id: 1000000,
            name: 'user',
            username: 'user',
          },
          patch_set: 2,
          robot_id: 'rb1',
          id: 'ecf0b9fa_fe1a5f62',
          line: 5,
          updated: '2018-02-08 18:49:18.000000000',
          message: 'test',
          unresolved: true,
        },
        {
          __path: '/COMMIT_MSG',
          author: {
            _account_id: 1000000,
            name: 'user',
            username: 'user',
          },
          patch_set: 4,
          id: 'ecf0b9fa_fe1a5f62_1',
          line: 5,
          updated: '2018-02-08 18:49:18.000000000',
          message: 'test',
          unresolved: true,
        },
        {
          id: '503008e2_0ab203ee',
          path: '/COMMIT_MSG',
          line: 5,
          in_reply_to: 'ecf0b9fa_fe1a5f62',
          updated: '2018-02-13 22:48:48.018000000',
          message: 'draft',
          unresolved: false,
          __draft: true,
          __draftID: '0.m683trwff68',
          __editing: false,
          patch_set: '2',
        },
      ],
      patchNum: 4,
      path: '/COMMIT_MSG',
      line: 5,
      rootId: 'ecf0b9fa_fe1a5f62',
      start_datetime: '2018-02-08 18:49:18.000000000',
    },
    {
      comments: [
        {
          __path: '/COMMIT_MSG',
          author: {
            _account_id: 1000000,
            name: 'user',
            username: 'user',
          },
          patch_set: 3,
          id: 'ecf0b9fa_fe5f62',
          robot_id: 'rb2',
          line: 5,
          updated: '2018-02-08 18:49:18.000000000',
          message: 'test',
          unresolved: true,
        },
        {
          __path: 'test.txt',
          author: {
            _account_id: 1000000,
            name: 'user',
            username: 'user',
          },
          patch_set: 3,
          id: '09a9fb0a_1484e6cf',
          side: 'PARENT',
          updated: '2018-02-13 22:47:19.000000000',
          message: 'Some comment on another patchset.',
          unresolved: false,
        },
      ],
      patchNum: 3,
      path: 'test.txt',
      rootId: '09a9fb0a_1484e6cf',
      start_datetime: '2018-02-13 22:47:19.000000000',
      commentSide: 'PARENT',
    },
    {
      comments: [
        {
          __path: '/COMMIT_MSG',
          author: {
            _account_id: 1000000,
            name: 'user',
            username: 'user',
          },
          patch_set: 2,
          id: '8caddf38_44770ec1',
          line: 4,
          updated: '2018-02-13 22:48:40.000000000',
          message: 'Another unresolved comment',
          unresolved: true,
        },
      ],
      patchNum: 2,
      path: '/COMMIT_MSG',
      line: 4,
      rootId: '8caddf38_44770ec1',
      start_datetime: '2018-02-13 22:48:40.000000000',
    },
    {
      comments: [
        {
          __path: '/COMMIT_MSG',
          author: {
            _account_id: 1000000,
            name: 'user',
            username: 'user',
          },
          patch_set: 2,
          id: 'scaddf38_44770ec1',
          line: 4,
          updated: '2018-02-14 22:48:40.000000000',
          message: 'Yet another unresolved comment',
          unresolved: true,
        },
      ],
      patchNum: 2,
      path: '/COMMIT_MSG',
      line: 4,
      rootId: 'scaddf38_44770ec1',
      start_datetime: '2018-02-14 22:48:40.000000000',
    },
    {
      comments: [
        {
          id: 'zcf0b9fa_fe1a5f62',
          path: '/COMMIT_MSG',
          line: 6,
          updated: '2018-02-15 22:48:48.018000000',
          message: 'resolved draft',
          unresolved: false,
          __draft: true,
          __draftID: '0.m683trwff68',
          __editing: false,
          patch_set: '2',
        },
      ],
      patchNum: 4,
      path: '/COMMIT_MSG',
      line: 6,
      rootId: 'zcf0b9fa_fe1a5f62',
      start_datetime: '2018-02-09 18:49:18.000000000',
    },
    {
      comments: [
        {
          __path: '/COMMIT_MSG',
          author: {
            _account_id: 1000000,
            name: 'user',
            username: 'user',
          },
          patch_set: 4,
          id: 'rc1',
          line: 5,
          updated: '2019-02-08 18:49:18.000000000',
          message: 'test',
          unresolved: true,
          robot_id: 'rc1',
        },
      ],
      patchNum: 4,
      path: '/COMMIT_MSG',
      line: 5,
      rootId: 'rc1',
      start_datetime: '2019-02-08 18:49:18.000000000',
    },
    {
      comments: [
        {
          __path: '/COMMIT_MSG',
          author: {
            _account_id: 1000000,
            name: 'user',
            username: 'user',
          },
          patch_set: 4,
          id: 'rc2',
          line: 5,
          updated: '2019-03-08 18:49:18.000000000',
          message: 'test',
          unresolved: true,
          robot_id: 'rc2',
        },
        {
          __path: '/COMMIT_MSG',
          author: {
            _account_id: 1000000,
            name: 'user',
            username: 'user',
          },
          patch_set: 4,
          id: 'c2_1',
          line: 5,
          updated: '2019-03-08 18:49:18.000000000',
          message: 'test',
          unresolved: true,
        },
      ],
      patchNum: 4,
      path: '/COMMIT_MSG',
      line: 5,
      rootId: 'rc2',
      start_datetime: '2019-03-08 18:49:18.000000000',
    },
  ];

  setup(() => {
    // Since pluginEndpoints are global, must reset state.
    _testOnly_resetEndpoints();
    navigateToChangeStub = sinon.stub(GerritNav, 'navigateToChange');
    stub('gr-rest-api-interface', {
      getConfig() { return Promise.resolve({test: 'config'}); },
      getAccount() { return Promise.resolve(null); },
      getDiffComments() { return Promise.resolve({}); },
      getDiffRobotComments() { return Promise.resolve({}); },
      getDiffDrafts() { return Promise.resolve({}); },
      _fetchSharedCacheURL() { return Promise.resolve({}); },
    });
    element = fixture.instantiate();
    sinon.stub(element.$.actions, 'reload').returns(Promise.resolve());
    pluginLoader.loadPlugins([]);
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

  const getCustomCssValue =
      cssParam => getComputedStyleValue(cssParam, element);

  test('_handleMessageAnchorTap', () => {
    element._changeNum = '1';
    element._patchRange = {
      basePatchNum: 'PARENT',
      patchNum: 1,
    };
    const getUrlStub = sinon.stub(GerritNav, 'getUrlForChange');
    const replaceStateStub = sinon.stub(history, 'replaceState');
    element._handleMessageAnchorTap({detail: {id: 'a12345'}});

    assert.equal(getUrlStub.lastCall.args[4], '#message-a12345');
    assert.isTrue(replaceStateStub.called);
  });

  test('_handleDiffAgainstBase', () => {
    element._change = generateChange({revisionsCount: 10});
    element._patchRange = {
      patchNum: 3,
      basePatchNum: 1,
    };
    sinon.stub(element, 'shouldSuppressKeyboardShortcut').returns(false);
    element._handleDiffAgainstBase(new CustomEvent(''));
    assert(navigateToChangeStub.called);
    const args = navigateToChangeStub.getCall(0).args;
    assert.equal(args[0], element._change);
    assert.equal(args[1], 3);
  });

  test('_handleDiffAgainstLatest', () => {
    element._change = generateChange({revisionsCount: 10});
    element._patchRange = {
      basePatchNum: 1,
      patchNum: 3,
    };
    sinon.stub(element, 'shouldSuppressKeyboardShortcut').returns(false);
    element._handleDiffAgainstLatest(new CustomEvent(''));
    assert(navigateToChangeStub.called);
    const args = navigateToChangeStub.getCall(0).args;
    assert.equal(args[0], element._change);
    assert.equal(args[1], 10);
    assert.equal(args[2], 1);
  });

  test('_handleDiffBaseAgainstLeft', () => {
    element._change = generateChange({revisionsCount: 10});
    element._patchRange = {
      patchNum: 3,
      basePatchNum: 1,
    };
    sinon.stub(element, 'shouldSuppressKeyboardShortcut').returns(false);
    element._handleDiffBaseAgainstLeft(new CustomEvent(''));
    assert(navigateToChangeStub.called);
    const args = navigateToChangeStub.getCall(0).args;
    assert.equal(args[0], element._change);
    assert.equal(args[1], 1);
  });

  test('_handleDiffRightAgainstLatest', () => {
    element._change = generateChange({revisionsCount: 10});
    element._patchRange = {
      basePatchNum: 1,
      patchNum: 3,
    };
    sinon.stub(element, 'shouldSuppressKeyboardShortcut').returns(false);
    element._handleDiffRightAgainstLatest(new CustomEvent(''));
    assert(navigateToChangeStub.called);
    const args = navigateToChangeStub.getCall(0).args;
    assert.equal(args[1], 10);
    assert.equal(args[2], 3);
  });

  test('_handleDiffBaseAgainstLatest', () => {
    element._change = generateChange({revisionsCount: 10});
    element._patchRange = {
      basePatchNum: 1,
      patchNum: 3,
    };
    sinon.stub(element, 'shouldSuppressKeyboardShortcut').returns(false);
    element._handleDiffBaseAgainstLatest(new CustomEvent(''));
    assert(navigateToChangeStub.called);
    const args = navigateToChangeStub.getCall(0).args;
    assert.equal(args[1], 10);
    assert.isNotOk(args[2]);
  });

  suite('plugins adding to file tab', () => {
    setup(done => {
      // Resolving it here instead of during setup() as other tests depend
      // on flush() not being called during setup.
      flush(() => done());
    });

    test('plugin added tab shows up as a dynamic endpoint', () => {
      assert(element._dynamicTabHeaderEndpoints.includes(
          'change-view-tab-header-url'));
      const paperTabs = element.shadowRoot.querySelector('#primaryTabs');
      // 4 Tabs are : Files, Comment Threads, Plugin, Findings
      assert.equal(paperTabs.querySelectorAll('paper-tab').length, 4);
      assert.equal(paperTabs.querySelectorAll('paper-tab')[2].dataset.name,
          'change-view-tab-header-url');
    });

    test('_setActivePrimaryTab switched tab correctly', done => {
      element._setActivePrimaryTab({detail:
          {tab: 'change-view-tab-header-url'}});
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
          }));
      flush(() => {
        assert.equal(element._activeTabs[0], 'change-view-tab-header-url');
        done();
      });
    });

    test('param change should switch primary tab correctly', done => {
      assert.equal(element._activeTabs[0], PrimaryTab.FILES);
      const queryMap = new Map();
      queryMap.set('tab', PrimaryTab.FINDINGS);
      // view is required
      element.params = {
        view: GerritNav.View.CHANGE,
        ...element.params, queryMap};
      flush(() => {
        assert.equal(element._activeTabs[0], PrimaryTab.FINDINGS);
        done();
      });
    });

    test('invalid param change should not switch primary tab', done => {
      assert.equal(element._activeTabs[0], PrimaryTab.FILES);
      const queryMap = new Map();
      queryMap.set('tab', 'random');
      // view is required
      element.params = {
        view: GerritNav.View.CHANGE,
        ...element.params, queryMap};
      flush(() => {
        assert.equal(element._activeTabs[0], PrimaryTab.FILES);
        done();
      });
    });

    test('switching tab sets _selectedTabPluginEndpoint', done => {
      const paperTabs = element.shadowRoot.querySelector('#primaryTabs');
      MockInteractions.tap(paperTabs.querySelectorAll('paper-tab')[2]);
      flush(() => {
        assert.equal(element._selectedTabPluginEndpoint,
            'change-view-tab-content-url');
        done();
      });
    });
  });

  suite('keyboard shortcuts', () => {
    test('t to add topic', () => {
      const editStub = sinon.stub(element.$.metadata, 'editTopic');
      MockInteractions.pressAndReleaseKeyOn(element, 83, null, 't');
      assert(editStub.called);
    });

    test('S should toggle the CL star', () => {
      const starStub = sinon.stub(element.$.changeStar, 'toggleStar');
      MockInteractions.pressAndReleaseKeyOn(element, 83, null, 's');
      assert(starStub.called);
    });

    test('U should navigate to root if no backPage set', () => {
      const relativeNavStub = sinon.stub(GerritNav,
          'navigateToRelativeUrl');
      MockInteractions.pressAndReleaseKeyOn(element, 85, null, 'u');
      assert.isTrue(relativeNavStub.called);
      assert.isTrue(relativeNavStub.lastCall.calledWithExactly(
          GerritNav.getUrlForRoot()));
    });

    test('U should navigate to backPage if set', () => {
      const relativeNavStub = sinon.stub(GerritNav,
          'navigateToRelativeUrl');
      element.backPage = '/dashboard/self';
      MockInteractions.pressAndReleaseKeyOn(element, 85, null, 'u');
      assert.isTrue(relativeNavStub.called);
      assert.isTrue(relativeNavStub.lastCall.calledWithExactly(
          '/dashboard/self'));
    });

    test('A fires an error event when not logged in', done => {
      sinon.stub(element, '_getLoggedIn').returns(Promise.resolve(false));
      const loggedInErrorSpy = sinon.spy();
      element.addEventListener('show-auth-required', loggedInErrorSpy);
      MockInteractions.pressAndReleaseKeyOn(element, 65, null, 'a');
      flush(() => {
        assert.isFalse(element.$.replyOverlay.opened);
        assert.isTrue(loggedInErrorSpy.called);
        done();
      });
    });

    test('shift A does not open reply overlay', done => {
      sinon.stub(element, '_getLoggedIn').returns(Promise.resolve(true));
      MockInteractions.pressAndReleaseKeyOn(element, 65, 'shift', 'a');
      flush(() => {
        assert.isFalse(element.$.replyOverlay.opened);
        done();
      });
    });

    test('A toggles overlay when logged in', done => {
      sinon.stub(element, '_getLoggedIn').returns(Promise.resolve(true));
      element._change = generateChange({
        revisionsCount: 1,
        messagesCount: 1,
      });
      element._change.labels = {};
      sinon.stub(element.$.restAPI, 'getChangeDetail')
          .callsFake(() => Promise.resolve(generateChange({
            // element has latest info
            revisionsCount: 1,
            messagesCount: 1,
          })));

      const openSpy = sinon.spy(element, '_openReplyDialog');

      MockInteractions.pressAndReleaseKeyOn(element, 65, null, 'a');
      flush(() => {
        assert.isTrue(element.$.replyOverlay.opened);
        element.$.replyOverlay.close();
        assert.isFalse(element.$.replyOverlay.opened);
        assert(openSpy.lastCall.calledWithExactly(
            element.$.replyDialog.FocusTarget.ANY),
        '_openReplyDialog should have been passed ANY');
        assert.equal(openSpy.callCount, 1);
        done();
      });
    });

    test('fullscreen-overlay-opened hides content', () => {
      element._loggedIn = true;
      element._loading = false;
      element._change = {
        owner: {_account_id: 1},
        labels: {},
        actions: {
          abandon: {
            enabled: true,
            label: 'Abandon',
            method: 'POST',
            title: 'Abandon',
          },
        },
      };
      sinon.spy(element, '_handleHideBackgroundContent');
      element.$.replyDialog.dispatchEvent(
          new CustomEvent('fullscreen-overlay-opened', {
            composed: true, bubbles: true,
          }));
      assert.isTrue(element._handleHideBackgroundContent.called);
      assert.isTrue(element.$.mainContent.classList.contains('overlayOpen'));
      assert.equal(getComputedStyle(element.$.actions).display, 'flex');
    });

    test('fullscreen-overlay-closed shows content', () => {
      element._loggedIn = true;
      element._loading = false;
      element._change = {
        owner: {_account_id: 1},
        labels: {},
        actions: {
          abandon: {
            enabled: true,
            label: 'Abandon',
            method: 'POST',
            title: 'Abandon',
          },
        },
      };
      sinon.spy(element, '_handleShowBackgroundContent');
      element.$.replyDialog.dispatchEvent(
          new CustomEvent('fullscreen-overlay-closed', {
            composed: true, bubbles: true,
          }));
      assert.isTrue(element._handleShowBackgroundContent.called);
      assert.isFalse(element.$.mainContent.classList.contains('overlayOpen'));
    });

    test('expand all messages when expand-diffs fired', () => {
      const handleExpand =
          sinon.stub(element.$.fileList, 'expandAllDiffs');
      element.$.fileListHeader.dispatchEvent(
          new CustomEvent('expand-diffs', {
            composed: true, bubbles: true,
          }));
      assert.isTrue(handleExpand.called);
    });

    test('collapse all messages when collapse-diffs fired', () => {
      const handleCollapse =
      sinon.stub(element.$.fileList, 'collapseAllDiffs');
      element.$.fileListHeader.dispatchEvent(
          new CustomEvent('collapse-diffs', {
            composed: true, bubbles: true,
          }));
      assert.isTrue(handleCollapse.called);
    });

    test('X should expand all messages', done => {
      flush(() => {
        const handleExpand = sinon.stub(element.messagesList,
            'handleExpandCollapse');
        MockInteractions.pressAndReleaseKeyOn(element, 88, null, 'x');
        assert(handleExpand.calledWith(true));
        done();
      });
    });

    test('Z should collapse all messages', done => {
      flush(() => {
        const handleExpand = sinon.stub(element.messagesList,
            'handleExpandCollapse');
        MockInteractions.pressAndReleaseKeyOn(element, 90, null, 'z');
        assert(handleExpand.calledWith(false));
        done();
      });
    });

    test('reload event from reply dialog is processed', () => {
      const handleReloadStub = sinon.stub(element, '_reload');
      element.$.replyDialog.dispatchEvent(new CustomEvent('reload',
          {detail: {clearPatchset: true}, bubbles: true, composed: true}));
      assert.isTrue(handleReloadStub.called);
    });

    test('shift + R should fetch and navigate to the latest patch set',
        done => {
          element._changeNum = '42';
          element._patchRange = {
            basePatchNum: 'PARENT',
            patchNum: 1,
          };
          element._change = {
            change_id: 'Iad9dc96274af6946f3632be53b106ef80f7ba6ca',
            _number: 42,
            revisions: {
              rev1: {_number: 1, commit: {parents: []}},
            },
            current_revision: 'rev1',
            status: 'NEW',
            labels: {},
            actions: {},
          };

          const reloadChangeStub = sinon.stub(element, '_reload');
          MockInteractions.pressAndReleaseKeyOn(element, 82, 'shift', 'r');
          flush(() => {
            assert.isTrue(reloadChangeStub.called);
            done();
          });
        });

    test('d should open download overlay', () => {
      const stub = sinon.stub(element.$.downloadOverlay, 'open').returns(
          new Promise(resolve => {})
      );
      MockInteractions.pressAndReleaseKeyOn(element, 68, null, 'd');
      assert.isTrue(stub.called);
    });

    test(', should open diff preferences', () => {
      const stub = sinon.stub(
          element.$.fileList.$.diffPreferencesDialog, 'open');
      element._loggedIn = false;
      element.disableDiffPrefs = true;
      MockInteractions.pressAndReleaseKeyOn(element, 188, null, ',');
      assert.isFalse(stub.called);

      element._loggedIn = true;
      MockInteractions.pressAndReleaseKeyOn(element, 188, null, ',');
      assert.isFalse(stub.called);

      element.disableDiffPrefs = false;
      MockInteractions.pressAndReleaseKeyOn(element, 188, null, ',');
      assert.isTrue(stub.called);
    });

    test('m should toggle diff mode', () => {
      sinon.stub(element, 'shouldSuppressKeyboardShortcut').returns(false);
      const setModeStub = sinon.stub(element.$.fileListHeader,
          'setDiffViewMode');
      const e = {preventDefault: () => {}};
      flushAsynchronousOperations();

      element.viewState.diffMode = 'SIDE_BY_SIDE';
      element._handleToggleDiffMode(e);
      assert.isTrue(setModeStub.calledWith('UNIFIED_DIFF'));

      element.viewState.diffMode = 'UNIFIED_DIFF';
      element._handleToggleDiffMode(e);
      assert.isTrue(setModeStub.calledWith('SIDE_BY_SIDE'));
    });
  });

  suite('reloading drafts', () => {
    let reloadStub;
    const drafts = {
      'testfile.txt': [
        {
          patch_set: 5,
          id: 'dd2982f5_c01c9e6a',
          line: 1,
          updated: '2017-11-08 18:47:45.000000000',
          message: 'test',
          unresolved: true,
        },
      ],
    };
    setup(() => {
      // Fake computeDraftCount as its required for ChangeComments,
      // see gr-comment-api#reloadDrafts.
      reloadStub = sinon.stub(element.$.commentAPI, 'reloadDrafts')
          .returns(Promise.resolve({
            drafts,
            getAllThreadsForChange: () => ([]),
            computeDraftCount: () => 1,
          }));
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
            composed: true, bubbles: true,
          }));
    });

    test('drafts are reloaded when comment-refresh fired', () => {
      element.dispatchEvent(
          new CustomEvent('comment-refresh', {
            composed: true, bubbles: true,
          }));
      assert.isTrue(reloadStub.called);
    });
  });

  suite('_recomputeComments', () => {
    setup(() => {
      // Fake computeDraftCount as its required for ChangeComments,
      // see gr-comment-api#reloadDrafts.
      sinon.stub(element.$.commentAPI, 'reloadDrafts')
          .returns(Promise.resolve({
            drafts: {},
            getAllThreadsForChange: () => THREADS,
            computeDraftCount: () => 0,
          }));
    });

    test('draft threads should be a new copy with correct states', done => {
      element.$.fileList.dispatchEvent(
          new CustomEvent('reload-drafts', {
            detail: {
              resolve: () => {
                assert.equal(element._draftCommentThreads.length, 2);
                assert.equal(
                    element._draftCommentThreads[0].rootId,
                    THREADS[0].rootId
                );
                assert.notEqual(
                    element._draftCommentThreads[0].comments,
                    THREADS[0].comments
                );
                assert.notEqual(
                    element._draftCommentThreads[0].comments[0],
                    THREADS[0].comments[0]
                );
                assert.isTrue(
                    element._draftCommentThreads[0]
                        .comments
                        .slice(0, 2)
                        .every(c => c.collapsed === true)
                );

                assert.isTrue(
                    element._draftCommentThreads[0]
                        .comments[2]
                        .collapsed === false
                );
                done();
              },
            },
            composed: true, bubbles: true,
          }));
    });
  });

  test('diff comments modified', () => {
    sinon.spy(element, '_handleReloadCommentThreads');
    return element._reloadComments().then(() => {
      element.dispatchEvent(
          new CustomEvent('diff-comments-modified', {
            composed: true, bubbles: true,
          }));
      assert.isTrue(element._handleReloadCommentThreads.called);
    });
  });

  test('thread list modified', () => {
    sinon.spy(element, '_handleReloadDiffComments');
    element._activeTabs = [PrimaryTab.COMMENT_THREADS, SecondaryTab.CHANGE_LOG];
    flushAsynchronousOperations();

    return element._reloadComments().then(() => {
      element.threadList.dispatchEvent(
          new CustomEvent('thread-list-modified', {
            composed: true, bubbles: true,
          }));
      assert.isTrue(element._handleReloadDiffComments.called);

      let draftStub = sinon.stub(element._changeComments, 'computeDraftCount')
          .returns(1);
      assert.equal(element._computeTotalCommentCounts(5,
          element._changeComments), '5 unresolved, 1 draft');
      assert.equal(element._computeTotalCommentCounts(0,
          element._changeComments), '1 draft');
      draftStub.restore();
      draftStub = sinon.stub(element._changeComments, 'computeDraftCount')
          .returns(0);
      assert.equal(element._computeTotalCommentCounts(0,
          element._changeComments), '');
      assert.equal(element._computeTotalCommentCounts(1,
          element._changeComments), '1 unresolved');
      draftStub.restore();
      draftStub = sinon.stub(element._changeComments, 'computeDraftCount')
          .returns(2);
      assert.equal(element._computeTotalCommentCounts(1,
          element._changeComments), '1 unresolved, 2 drafts');
      draftStub.restore();
    });
  });

  suite('thread list and change log tabs', () => {
    setup(() => {
      element._changeNum = '1';
      element._patchRange = {
        basePatchNum: 'PARENT',
        patchNum: 1,
      };
      element._change = {
        change_id: 'Iad9dc96274af6946f3632be53b106ef80f7ba6ca',
        revisions: {
          rev2: {_number: 2, commit: {parents: []}},
          rev1: {_number: 1, commit: {parents: []}},
          rev13: {_number: 13, commit: {parents: []}},
          rev3: {_number: 3, commit: {parents: []}},
        },
        current_revision: 'rev3',
        status: 'NEW',
        labels: {
          test: {
            all: [],
            default_value: 0,
            values: [],
            approved: {},
          },
        },
      };
      sinon.stub(element.$.relatedChanges, 'reload');
      sinon.stub(element, '_reload').returns(Promise.resolve());
      sinon.spy(element, '_paramsChanged');
      element.params = {view: 'change', changeNum: '1'};
    });
  });

  suite('Findings comment tab', () => {
    setup(done => {
      element._change = {
        change_id: 'Iad9dc96274af6946f3632be53b106ef80f7ba6ca',
        revisions: {
          rev2: {_number: 2, commit: {parents: []}},
          rev1: {_number: 1, commit: {parents: []}},
          rev13: {_number: 13, commit: {parents: []}},
          rev3: {_number: 3, commit: {parents: []}},
          rev4: {_number: 4, commit: {parents: []}},
        },
        current_revision: 'rev4',
      };
      element._commentThreads = THREADS;
      const paperTabs = element.shadowRoot.querySelector('#primaryTabs');
      MockInteractions.tap(paperTabs.querySelectorAll('paper-tab')[3]);
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
      assert.equal(element._computeText({_number: 2}, THREADS),
          'Patchset 2 (1 finding)');
      assert.equal(element._computeText({_number: 4}, THREADS),
          'Patchset 4 (2 findings)');
      assert.equal(element._computeText({_number: 5}, THREADS),
          'Patchset 5');
    });

    test('only robot comments are rendered', () => {
      assert.equal(element._robotCommentThreads.length, 2);
      assert.equal(element._robotCommentThreads[0].comments[0].robot_id,
          'rc1');
      assert.equal(element._robotCommentThreads[1].comments[0].robot_id,
          'rc2');
    });

    test('changing patchsets resets robot comments', done => {
      element.set('_change.current_revision', 'rev3');
      flush(() => {
        assert.equal(element._robotCommentThreads.length, 1);
        done();
      });
    });

    test('Show more button is hidden', () => {
      assert.isNull(element.shadowRoot.querySelector('.show-robot-comments'));
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
        assert.isOk(element.shadowRoot.querySelector('.show-robot-comments'));
        assert.equal(element._robotCommentThreads.length,
            ROBOT_COMMENTS_LIMIT);
      });

      test('Clicking show more button renders all comments', done => {
        MockInteractions.tap(element.shadowRoot.querySelector(
            '.show-robot-comments'));
        flush(() => {
          assert.equal(element._robotCommentThreads.length, 62);
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
    sinon.stub(element, '_handleOpenDownloadDialog');
    element.$.actions.dispatchEvent(
        new CustomEvent('download-tap', {
          composed: true, bubbles: true,
        }));
    assert.isTrue(element._handleOpenDownloadDialog.called);
  });

  test('fetches the server config on attached', done => {
    flush(() => {
      assert.equal(element._serverConfig.test, 'config');
      done();
    });
  });

  test('_changeStatuses', () => {
    element._loading = false;
    element._change = {
      change_id: 'Iad9dc96274af6946f3632be53b106ef80f7ba6ca',
      revisions: {
        rev2: {_number: 2},
        rev1: {_number: 1},
        rev13: {_number: 13},
        rev3: {_number: 3},
      },
      current_revision: 'rev3',
      status: ChangeStatus.MERGED,
      work_in_progress: true,
      labels: {
        test: {
          all: [],
          default_value: 0,
          values: [],
          approved: {},
        },
      },
    };
    element._mergeable = true;
    const expectedStatuses = ['Merged', 'WIP'];
    assert.deepEqual(element._changeStatuses, expectedStatuses);
    assert.equal(element._changeStatus, expectedStatuses.join(', '));
    flushAsynchronousOperations();
    const statusChips = dom(element.root)
        .querySelectorAll('gr-change-status');
    assert.equal(statusChips.length, 2);
  });

  test('diff preferences open when open-diff-prefs is fired', () => {
    const overlayOpenStub = sinon.stub(element.$.fileList,
        'openDiffPrefs');
    element.$.fileListHeader.dispatchEvent(
        new CustomEvent('open-diff-prefs', {
          composed: true, bubbles: true,
        }));
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
    assert.isTrue(element._isSubmitEnabled(
        {submit: {enabled: true}}));
  });

  test('_reload is called when an approved label is removed', () => {
    const vote = {_account_id: 1, name: 'bojack', value: 1};
    element._changeNum = '42';
    element._patchRange = {
      basePatchNum: 'PARENT',
      patchNum: 1,
    };
    element._change = {
      change_id: 'Iad9dc96274af6946f3632be53b106ef80f7ba6ca',
      owner: {email: 'abc@def'},
      revisions: {
        rev2: {_number: 2, commit: {parents: []}},
        rev1: {_number: 1, commit: {parents: []}},
        rev13: {_number: 13, commit: {parents: []}},
        rev3: {_number: 3, commit: {parents: []}},
      },
      current_revision: 'rev3',
      status: 'NEW',
      labels: {
        test: {
          all: [vote],
          default_value: 0,
          values: [],
          approved: {},
        },
      },
    };
    flushAsynchronousOperations();
    const reloadStub = sinon.stub(element, '_reload');
    element.splice('_change.labels.test.all', 0, 1);
    assert.isFalse(reloadStub.called);
    element._change.labels.test.all.push(vote);
    element._change.labels.test.all.push(vote);
    element._change.labels.test.approved = vote;
    flushAsynchronousOperations();
    element.splice('_change.labels.test.all', 0, 2);
    assert.isTrue(reloadStub.called);
    assert.isTrue(reloadStub.calledOnce);
  });

  test('reply button has updated count when there are drafts', () => {
    const getLabel = element._computeReplyButtonLabel;

    assert.equal(getLabel(null, false), 'Reply');
    assert.equal(getLabel(null, true), 'Start Review');

    const changeRecord = {base: null};
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
      basePatchNum: 'PARENT',
      patchNum: 2,
    };
    const draft = {
      __draft: true,
      id: 'id1',
      path: '/foo/bar.txt',
      text: 'hello',
    };
    element._handleCommentSave({detail: {comment: draft}});
    draft.patch_set = 2;
    assert.deepEqual(element._diffDrafts, {'/foo/bar.txt': [draft]});
    draft.patch_set = null;
    draft.text = 'hello, there';
    element._handleCommentSave({detail: {comment: draft}});
    draft.patch_set = 2;
    assert.deepEqual(element._diffDrafts, {'/foo/bar.txt': [draft]});
    const draft2 = {
      __draft: true,
      id: 'id2',
      path: '/foo/bar.txt',
      text: 'hola',
    };
    element._handleCommentSave({detail: {comment: draft2}});
    draft2.patch_set = 2;
    assert.deepEqual(element._diffDrafts, {'/foo/bar.txt': [draft, draft2]});
    draft.patch_set = null;
    element._handleCommentDiscard({detail: {comment: draft}});
    draft.patch_set = 2;
    assert.deepEqual(element._diffDrafts, {'/foo/bar.txt': [draft2]});
    element._handleCommentDiscard({detail: {comment: draft2}});
    assert.deepEqual(element._diffDrafts, {});
  });

  test('change num change', () => {
    element._changeNum = null;
    element._patchRange = {
      basePatchNum: 'PARENT',
      patchNum: 2,
    };
    element._change = {
      change_id: 'Iad9dc96274af6946f3632be53b106ef80f7ba6ca',
      labels: {},
    };
    element.viewState.changeNum = null;
    element.viewState.diffMode = 'UNIFIED';
    assert.equal(element.viewState.numFilesShown, 200);
    assert.equal(element._numFilesShown, 200);
    element._numFilesShown = 150;
    flushAsynchronousOperations();
    assert.equal(element.viewState.diffMode, 'UNIFIED');
    assert.equal(element.viewState.numFilesShown, 150);

    element._changeNum = '1';
    element.params = {changeNum: '1'};
    element._change.newProp = '1';
    flushAsynchronousOperations();
    assert.equal(element.viewState.diffMode, 'UNIFIED');
    assert.equal(element.viewState.changeNum, '1');

    element._changeNum = '2';
    element.params = {changeNum: '2'};
    element._change.newProp = '2';
    flushAsynchronousOperations();
    assert.equal(element.viewState.diffMode, 'UNIFIED');
    assert.equal(element.viewState.changeNum, '2');
    assert.equal(element.viewState.numFilesShown, 200);
    assert.equal(element._numFilesShown, 200);
  });

  test('_setDiffViewMode is called with reset when new change is loaded',
      () => {
        sinon.stub(element, '_setDiffViewMode');
        element.viewState = {changeNum: 1};
        element._changeNum = 2;
        element._resetFileListViewState();
        assert.isTrue(
            element._setDiffViewMode.lastCall.calledWithExactly(true));
      });

  test('diffViewMode is propagated from file list header', () => {
    element.viewState = {diffMode: 'UNIFIED'};
    element.$.fileListHeader.diffViewMode = 'SIDE_BY_SIDE';
    assert.equal(element.viewState.diffMode, 'SIDE_BY_SIDE');
  });

  test('diffMode defaults to side by side without preferences', done => {
    sinon.stub(element.$.restAPI, 'getPreferences').returns(
        Promise.resolve({}));
    // No user prefs or diff view mode set.

    element._setDiffViewMode().then(() => {
      assert.equal(element.viewState.diffMode, 'SIDE_BY_SIDE');
      done();
    });
  });

  test('diffMode defaults to preference when not already set', done => {
    sinon.stub(element.$.restAPI, 'getPreferences').returns(
        Promise.resolve({default_diff_view: 'UNIFIED'}));

    element._setDiffViewMode().then(() => {
      assert.equal(element.viewState.diffMode, 'UNIFIED');
      done();
    });
  });

  test('existing diffMode overrides preference', done => {
    element.viewState.diffMode = 'SIDE_BY_SIDE';
    sinon.stub(element.$.restAPI, 'getPreferences').returns(
        Promise.resolve({default_diff_view: 'UNIFIED'}));
    element._setDiffViewMode().then(() => {
      assert.equal(element.viewState.diffMode, 'SIDE_BY_SIDE');
      done();
    });
  });

  test('don’t reload entire page when patchRange changes', () => {
    const reloadStub = sinon.stub(element, '_reload').callsFake(
        () => Promise.resolve());
    const reloadPatchDependentStub = sinon.stub(element,
        '_reloadPatchNumDependentResources')
        .callsFake(() => Promise.resolve());
    const relatedClearSpy = sinon.spy(element.$.relatedChanges, 'clear');
    const collapseStub = sinon.stub(element.$.fileList, 'collapseAllDiffs');

    const value = {
      view: GerritNav.View.CHANGE,
      patchNum: '1',
    };
    element._paramsChanged(value);
    assert.isTrue(reloadStub.calledOnce);
    assert.isTrue(relatedClearSpy.calledOnce);

    element._initialLoadComplete = true;

    value.basePatchNum = '1';
    value.patchNum = '2';
    element._paramsChanged(value);
    assert.isFalse(reloadStub.calledTwice);
    assert.isTrue(reloadPatchDependentStub.calledOnce);
    assert.isTrue(relatedClearSpy.calledOnce);
    assert.isTrue(collapseStub.calledTwice);
  });

  test('reload entire page when patchRange doesnt change', () => {
    const reloadStub = sinon.stub(element, '_reload').callsFake(
        () => Promise.resolve());
    const collapseStub = sinon.stub(element.$.fileList, 'collapseAllDiffs');
    const value = {
      view: GerritNav.View.CHANGE,
    };
    element._paramsChanged(value);
    assert.isTrue(reloadStub.calledOnce);
    element._initialLoadComplete = true;
    element._paramsChanged(value);
    assert.isTrue(reloadStub.calledTwice);
    assert.isTrue(collapseStub.calledTwice);
  });

  test('related changes are not updated after other action', done => {
    sinon.stub(element, '_reload').callsFake(() => Promise.resolve());
    sinon.stub(element.$.relatedChanges, 'reload');
    const e = {detail: {action: 'abandon'}};
    element._reload(e).then(() => {
      assert.isFalse(navigateToChangeStub.called);
      done();
    });
  });

  test('_computeMergedCommitInfo', () => {
    const dummyRevs = {
      1: {commit: {commit: 1}},
      2: {commit: {}},
    };
    assert.deepEqual(element._computeMergedCommitInfo(0, dummyRevs), {});
    assert.deepEqual(element._computeMergedCommitInfo(1, dummyRevs),
        dummyRevs[1].commit);

    // Regression test for issue 5337.
    const commit = element._computeMergedCommitInfo(2, dummyRevs);
    assert.notDeepEqual(commit, dummyRevs[2]);
    assert.deepEqual(commit, {commit: 2});
  });

  test('_computeCopyTextForTitle', () => {
    const change = {
      _number: 123,
      subject: 'test subject',
      revisions: {
        rev1: {_number: 1},
        rev3: {_number: 3},
      },
      current_revision: 'rev3',
    };
    sinon.stub(GerritNav, 'getUrlForChange')
        .returns('/change/123');
    assert.equal(
        element._computeCopyTextForTitle(change),
        `123: test subject | http://${location.host}/change/123`
    );
  });

  test('get latest revision', () => {
    let change = {
      revisions: {
        rev1: {_number: 1},
        rev3: {_number: 3},
      },
      current_revision: 'rev3',
    };
    assert.equal(element._getLatestRevisionSHA(change), 'rev3');
    change = {
      revisions: {
        rev1: {_number: 1},
      },
    };
    assert.equal(element._getLatestRevisionSHA(change), 'rev1');
  });

  test('show commit message edit button', () => {
    const _change = {
      status: ChangeStatus.MERGED,
    };
    assert.isTrue(element._computeHideEditCommitMessage(false, false, {}));
    assert.isTrue(element._computeHideEditCommitMessage(true, true, {}));
    assert.isTrue(element._computeHideEditCommitMessage(false, true, {}));
    assert.isFalse(element._computeHideEditCommitMessage(true, false, {}));
    assert.isTrue(element._computeHideEditCommitMessage(true, false,
        _change));
    assert.isTrue(element._computeHideEditCommitMessage(true, false, {},
        true));
    assert.isFalse(element._computeHideEditCommitMessage(true, false, {},
        false));
  });

  test('_handleCommitMessageSave trims trailing whitespace', () => {
    const putStub = sinon.stub(element.$.restAPI, 'putChangeCommitMessage')
        .returns(Promise.resolve({}));

    const mockEvent = content => { return {detail: {content}}; };

    element._handleCommitMessageSave(mockEvent('test \n  test '));
    assert.equal(putStub.lastCall.args[1], 'test\n  test');

    element._handleCommitMessageSave(mockEvent('  test\ntest'));
    assert.equal(putStub.lastCall.args[1], '  test\ntest');

    element._handleCommitMessageSave(mockEvent('\n\n\n\n\n\n\n\n'));
    assert.equal(putStub.lastCall.args[1], '\n\n\n\n\n\n\n\n');
  });

  test('_computeChangeIdCommitMessageError', () => {
    let commitMessage =
      'Change-Id: I4ce18b2395bca69d7a9aa48bf4554faa56282483';
    let change = {change_id: 'I4ce18b2395bca69d7a9aa48bf4554faa56282483'};
    assert.equal(
        element._computeChangeIdCommitMessageError(commitMessage, change),
        null);

    change = {change_id: 'I4ce18b2395bca69d7a9aa48bf4554faa56282484'};
    assert.equal(
        element._computeChangeIdCommitMessageError(commitMessage, change),
        'mismatch');

    commitMessage = 'This is the greatest change.';
    assert.equal(
        element._computeChangeIdCommitMessageError(commitMessage, change),
        'missing');
  });

  test('multiple change Ids in commit message picks last', () => {
    const commitMessage = [
      'Change-Id: I4ce18b2395bca69d7a9aa48bf4554faa56282484',
      'Change-Id: I4ce18b2395bca69d7a9aa48bf4554faa56282483',
    ].join('\n');
    let change = {change_id: 'I4ce18b2395bca69d7a9aa48bf4554faa56282483'};
    assert.equal(
        element._computeChangeIdCommitMessageError(commitMessage, change),
        null);
    change = {change_id: 'I4ce18b2395bca69d7a9aa48bf4554faa56282484'};
    assert.equal(
        element._computeChangeIdCommitMessageError(commitMessage, change),
        'mismatch');
  });

  test('does not count change Id that starts mid line', () => {
    const commitMessage = [
      'Change-Id: I4ce18b2395bca69d7a9aa48bf4554faa56282484',
      'Change-Id: I4ce18b2395bca69d7a9aa48bf4554faa56282483',
    ].join(' and ');
    let change = {change_id: 'I4ce18b2395bca69d7a9aa48bf4554faa56282484'};
    assert.equal(
        element._computeChangeIdCommitMessageError(commitMessage, change),
        null);
    change = {change_id: 'I4ce18b2395bca69d7a9aa48bf4554faa56282483'};
    assert.equal(
        element._computeChangeIdCommitMessageError(commitMessage, change),
        'mismatch');
  });

  test('_computeTitleAttributeWarning', () => {
    let changeIdCommitMessageError = 'missing';
    assert.equal(
        element._computeTitleAttributeWarning(changeIdCommitMessageError),
        'No Change-Id in commit message');

    changeIdCommitMessageError = 'mismatch';
    assert.equal(
        element._computeTitleAttributeWarning(changeIdCommitMessageError),
        'Change-Id mismatch');
  });

  test('_computeChangeIdClass', () => {
    let changeIdCommitMessageError = 'missing';
    assert.equal(
        element._computeChangeIdClass(changeIdCommitMessageError), '');

    changeIdCommitMessageError = 'mismatch';
    assert.equal(
        element._computeChangeIdClass(changeIdCommitMessageError), 'warning');
  });

  test('topic is coalesced to null', done => {
    sinon.stub(element, '_changeChanged');
    sinon.stub(element.$.restAPI, 'getChangeDetail').callsFake(
        () => Promise.resolve({
          id: '123456789',
          labels: {},
          current_revision: 'foo',
          revisions: {foo: {commit: {}}},
        }));

    element._getChangeDetail().then(() => {
      assert.isNull(element._change.topic);
      done();
    });
  });

  test('commit sha is populated from getChangeDetail', done => {
    sinon.stub(element, '_changeChanged');
    sinon.stub(element.$.restAPI, 'getChangeDetail').callsFake(
        () => Promise.resolve({
          id: '123456789',
          labels: {},
          current_revision: 'foo',
          revisions: {foo: {commit: {}}},
        }));

    element._getChangeDetail().then(() => {
      assert.equal('foo', element._commitInfo.commit);
      done();
    });
  });

  test('edit is added to change', () => {
    sinon.stub(element, '_changeChanged');
    sinon.stub(element.$.restAPI, 'getChangeDetail').callsFake(
        () => Promise.resolve({
          id: '123456789',
          labels: {},
          current_revision: 'foo',
          revisions: {foo: {commit: {}}},
        }));
    sinon.stub(element, '_getEdit').callsFake(() => Promise.resolve({
      base_patch_set_number: 1,
      commit: {commit: 'bar'},
    }));
    element._patchRange = {};

    return element._getChangeDetail().then(() => {
      const revs = element._change.revisions;
      assert.equal(Object.keys(revs).length, 2);
      assert.deepEqual(revs['foo'], {commit: {commit: 'foo'}});
      assert.deepEqual(revs['bar'], {
        _number: SPECIAL_PATCH_SET_NUM.EDIT,
        basePatchNum: 1,
        commit: {commit: 'bar'},
        fetch: undefined,
      });
    });
  });

  test('_getBasePatchNum', () => {
    const _change = {
      _number: 42,
      revisions: {
        '98da160735fb81604b4c40e93c368f380539dd0e': {
          _number: 1,
          commit: {
            parents: [],
          },
        },
      },
    };
    const _patchRange = {
      basePatchNum: 'PARENT',
    };
    assert.equal(element._getBasePatchNum(_change, _patchRange), 'PARENT');

    element._prefs = {
      default_base_for_merges: 'FIRST_PARENT',
    };

    const _change2 = {
      _number: 42,
      revisions: {
        '98da160735fb81604b4c40e93c368f380539dd0e': {
          _number: 1,
          commit: {
            parents: [
              {
                commit: '6e12bdf1176eb4ab24d8491ba3b6d0704409cde8',
                subject: 'test',
              },
              {
                commit: '22f7db4754b5d9816fc581f3d9a6c0ef8429c841',
                subject: 'test3',
              },
            ],
          },
        },
      },
    };
    assert.equal(element._getBasePatchNum(_change2, _patchRange), -1);

    _patchRange.patchNum = 1;
    assert.equal(element._getBasePatchNum(_change2, _patchRange), 'PARENT');
  });

  test('_openReplyDialog called with `ANY` when coming from tap event',
      () => {
        const openStub = sinon.stub(element, '_openReplyDialog');
        element._serverConfig = {};
        MockInteractions.tap(element.$.replyBtn);
        assert(openStub.lastCall.calledWithExactly(
            element.$.replyDialog.FocusTarget.ANY),
        '_openReplyDialog should have been passed ANY');
        assert.equal(openStub.callCount, 1);
      });

  test('_openReplyDialog called with `BODY` when coming from message reply' +
      'event', done => {
    flush(() => {
      const openStub = sinon.stub(element, '_openReplyDialog');
      element.messagesList.dispatchEvent(
          new CustomEvent('reply', {
            detail:
          {message: {message: 'text'}},
            composed: true, bubbles: true,
          }));
      assert(openStub.lastCall.calledWithExactly(
          element.$.replyDialog.FocusTarget.BODY),
      '_openReplyDialog should have been passed BODY');
      assert.equal(openStub.callCount, 1);
      done();
    });
  });

  test('reply dialog focus can be controlled', () => {
    const FocusTarget = element.$.replyDialog.FocusTarget;
    const openStub = sinon.stub(element, '_openReplyDialog');

    const e = {detail: {}};
    element._handleShowReplyDialog(e);
    assert(openStub.lastCall.calledWithExactly(FocusTarget.REVIEWERS),
        '_openReplyDialog should have been passed REVIEWERS');
    assert.equal(openStub.callCount, 1);

    e.detail.value = {ccsOnly: true};
    element._handleShowReplyDialog(e);
    assert(openStub.lastCall.calledWithExactly(FocusTarget.CCS),
        '_openReplyDialog should have been passed CCS');
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
    sinon.stub(element.$.restAPI, 'getLoggedIn')
        .callsFake(() => Promise.resolve(true));
    sinon.stub(pluginLoader, 'awaitPluginsLoaded')
        .callsFake(() => Promise.resolve());

    element._patchRange = {
      basePatchNum: 'PARENT',
      patchNum: 2,
    };
    element._change = {
      change_id: 'Iad9dc96274af6946f3632be53b106ef80f7ba6ca',
      revisions: {
        rev1: {_number: 1, commit: {parents: []}},
        rev2: {_number: 2, commit: {parents: []}},
      },
      current_revision: 'rev1',
      status: ChangeStatus.MERGED,
      labels: {},
      actions: {},
    };

    sinon.stub(element, '_getUrlParameter').callsFake(
        param => {
          assert.equal(param, 'revert');
          return param;
        });

    sinon.stub(element.$.actions, 'showRevertDialog').callsFake(
        done);

    element._maybeShowRevertDialog();
    assert.isTrue(pluginLoader.awaitPluginsLoaded.called);
  });

  suite('scroll related tests', () => {
    test('document scrolling calls function to set scroll height', done => {
      const originalHeight = document.body.scrollHeight;
      const scrollStub = sinon.stub(element, '_handleScroll').callsFake(
          () => {
            assert.isTrue(scrollStub.called);
            document.body.style.height = originalHeight + 'px';
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
        assert.isTrue(element.viewState.scrollTop == TEST_SCROLL_TOP_PX);
        return Promise.resolve({});
      });

      // simulate reloading component, which is done when route
      // changes to match a regex of change view type.
      element._paramsChanged({view: GerritNav.View.CHANGE});
    });

    test('scrollTop is reset when new change is loaded', () => {
      element._resetFileListViewState();
      assert.equal(element.viewState.scrollTop, 0);
    });
  });

  suite('reply dialog tests', () => {
    setup(() => {
      sinon.stub(element.$.replyDialog, '_draftChanged');
      element._change = generateChange({
        revisionsCount: 1,
        messagesCount: 1,
      });
      element._change.labels = {};
      sinon.stub(element.$.restAPI, 'getChangeDetail')
          .callsFake(() => Promise.resolve(generateChange({
            // element has latest info
            revisionsCount: 1,
            messagesCount: 1,
          })));
    });

    test('show reply dialog on open-reply-dialog event', done => {
      sinon.stub(element, '_openReplyDialog');
      element.dispatchEvent(
          new CustomEvent('open-reply-dialog', {
            composed: true,
            bubbles: true,
            detail: {},
          }));
      flush(() => {
        assert.isTrue(element._openReplyDialog.calledOnce);
        done();
      });
    });

    test('reply from comment adds quote text', () => {
      const e = {detail: {message: {message: 'quote text'}}};
      element._handleMessageReply(e);
      assert.equal(element.$.replyDialog.quote, '> quote text\n\n');
    });

    test('reply from comment replaces quote text', () => {
      element.$.replyDialog.draft = '> old quote text\n\n some draft text';
      element.$.replyDialog.quote = '> old quote text\n\n';
      const e = {detail: {message: {message: 'quote text'}}};
      element._handleMessageReply(e);
      assert.equal(element.$.replyDialog.quote, '> quote text\n\n');
    });

    test('reply from same comment preserves quote text', () => {
      element.$.replyDialog.draft = '> quote text\n\n some draft text';
      element.$.replyDialog.quote = '> quote text\n\n';
      const e = {detail: {message: {message: 'quote text'}}};
      element._handleMessageReply(e);
      assert.equal(element.$.replyDialog.draft,
          '> quote text\n\n some draft text');
      assert.equal(element.$.replyDialog.quote, '> quote text\n\n');
    });

    test('reply from top of page contains previous draft', () => {
      const div = document.createElement('div');
      element.$.replyDialog.draft = '> quote text\n\n some draft text';
      element.$.replyDialog.quote = '> quote text\n\n';
      const e = {target: div, preventDefault: sinon.spy()};
      element._handleReplyTap(e);
      assert.equal(element.$.replyDialog.draft,
          '> quote text\n\n some draft text');
      assert.equal(element.$.replyDialog.quote, '> quote text\n\n');
    });
  });

  test('reply button is disabled until server config is loaded', () => {
    assert.isTrue(element._replyDisabled);
    element._serverConfig = {};
    assert.isFalse(element._replyDisabled);
  });

  suite('commit message expand/collapse', () => {
    setup(() => {
      element._change = generateChange({
        revisionsCount: 1,
        messagesCount: 1,
      });
      element._change.labels = {};
      sinon.stub(element.$.restAPI, 'getChangeDetail')
          .callsFake(() => Promise.resolve(generateChange({
            // new patchset was uploaded
            revisionsCount: 2,
            messagesCount: 1,
          })));
    });

    test('commitCollapseToggle hidden for short commit message', () => {
      element._latestCommitMessage = '';
      assert.isTrue(element.$.commitCollapseToggle.hasAttribute('hidden'));
    });

    test('commitCollapseToggle shown for long commit message', () => {
      element._latestCommitMessage = _.times(31, String).join('\n');
      assert.isFalse(element.$.commitCollapseToggle.hasAttribute('hidden'));
    });

    test('commitCollapseToggle functions', () => {
      element._latestCommitMessage = _.times(35, String).join('\n');
      assert.isTrue(element._commitCollapsed);
      assert.isTrue(element._commitCollapsible);
      assert.isTrue(
          element.$.commitMessageEditor.hasAttribute('collapsed'));
      MockInteractions.tap(element.$.commitCollapseToggleButton);
      assert.isFalse(element._commitCollapsed);
      assert.isTrue(element._commitCollapsible);
      assert.isFalse(
          element.$.commitMessageEditor.hasAttribute('collapsed'));
    });
  });

  suite('related changes expand/collapse', () => {
    let updateHeightSpy;
    setup(() => {
      updateHeightSpy = sinon.spy(element, '_updateRelatedChangeMaxHeight');
    });

    test('relatedChangesToggle shown height greater than changeInfo height',
        () => {
          assert.isFalse(element.$.relatedChangesToggle.classList
              .contains('showToggle'));
          sinon.stub(element, '_getOffsetHeight').callsFake(() => 50);
          sinon.stub(element, '_getScrollHeight').callsFake(() => 60);
          sinon.stub(element, '_getLineHeight').callsFake(() => 5);
          sinon.stub(window, 'matchMedia')
              .callsFake(() => { return {matches: true}; });
          element.$.relatedChanges.dispatchEvent(
              new CustomEvent('new-section-loaded'));
          assert.isTrue(element.$.relatedChangesToggle.classList
              .contains('showToggle'));
          assert.equal(updateHeightSpy.callCount, 1);
        });

    test('relatedChangesToggle hidden height less than changeInfo height',
        () => {
          assert.isFalse(element.$.relatedChangesToggle.classList
              .contains('showToggle'));
          sinon.stub(element, '_getOffsetHeight').callsFake(() => 50);
          sinon.stub(element, '_getScrollHeight').callsFake(() => 40);
          sinon.stub(element, '_getLineHeight').callsFake(() => 5);
          sinon.stub(window, 'matchMedia')
              .callsFake(() => { return {matches: true}; });
          element.$.relatedChanges.dispatchEvent(
              new CustomEvent('new-section-loaded'));
          assert.isFalse(element.$.relatedChangesToggle.classList
              .contains('showToggle'));
          assert.equal(updateHeightSpy.callCount, 1);
        });

    test('relatedChangesToggle functions', () => {
      sinon.stub(element, '_getOffsetHeight').callsFake(() => 50);
      sinon.stub(window, 'matchMedia')
          .callsFake(() => { return {matches: false}; });
      element._relatedChangesLoading = false;
      assert.isTrue(element._relatedChangesCollapsed);
      assert.isTrue(
          element.$.relatedChanges.classList.contains('collapsed'));
      MockInteractions.tap(element.$.relatedChangesToggleButton);
      assert.isFalse(element._relatedChangesCollapsed);
      assert.isFalse(
          element.$.relatedChanges.classList.contains('collapsed'));
    });

    test('_updateRelatedChangeMaxHeight without commit toggle', () => {
      sinon.stub(element, '_getOffsetHeight').callsFake(() => 50);
      sinon.stub(element, '_getLineHeight').callsFake(() => 12);
      sinon.stub(window, 'matchMedia')
          .callsFake(() => { return {matches: false}; });

      // 50 (existing height) - 30 (extra height) = 20 (adjusted height).
      // 20 (max existing height)  % 12 (line height) = 6 (remainder).
      // 20 (adjusted height) - 8 (remainder) = 12 (max height to set).

      element._updateRelatedChangeMaxHeight();
      assert.equal(getCustomCssValue('--relation-chain-max-height'),
          '12px');
      assert.equal(getCustomCssValue('--related-change-btn-top-padding'),
          '');
    });

    test('_updateRelatedChangeMaxHeight with commit toggle', () => {
      element._latestCommitMessage = _.times(31, String).join('\n');
      sinon.stub(element, '_getOffsetHeight').callsFake(() => 50);
      sinon.stub(element, '_getLineHeight').callsFake(() => 12);
      sinon.stub(window, 'matchMedia')
          .callsFake(() => { return {matches: false}; });

      // 50 (existing height) % 12 (line height) = 2 (remainder).
      // 50 (existing height)  - 2 (remainder) = 48 (max height to set).

      element._updateRelatedChangeMaxHeight();
      assert.equal(getCustomCssValue('--relation-chain-max-height'),
          '48px');
      assert.equal(getCustomCssValue('--related-change-btn-top-padding'),
          '2px');
    });

    test('_updateRelatedChangeMaxHeight in small screen mode', () => {
      element._latestCommitMessage = _.times(31, String).join('\n');
      sinon.stub(element, '_getOffsetHeight').callsFake(() => 50);
      sinon.stub(element, '_getLineHeight').callsFake(() => 12);
      sinon.stub(window, 'matchMedia')
          .callsFake(() => { return {matches: true}; });

      element._updateRelatedChangeMaxHeight();

      // 400 (new height) % 12 (line height) = 4 (remainder).
      // 400 (new height) - 4 (remainder) = 396.

      assert.equal(getCustomCssValue('--relation-chain-max-height'),
          '396px');
    });

    test('_updateRelatedChangeMaxHeight in medium screen mode', () => {
      element._latestCommitMessage = _.times(31, String).join('\n');
      sinon.stub(element, '_getOffsetHeight').callsFake(() => 50);
      sinon.stub(element, '_getLineHeight').callsFake(() => 12);
      sinon.stub(window, 'matchMedia').callsFake(() => {
        if (window.matchMedia.lastCall.args[0] === '(max-width: 75em)') {
          return {matches: true};
        } else {
          return {matches: false};
        }
      });

      // 100 (new height) % 12 (line height) = 4 (remainder).
      // 100 (new height) - 4 (remainder) = 96.
      element._updateRelatedChangeMaxHeight();
      assert.equal(getCustomCssValue('--relation-chain-max-height'),
          '96px');
    });

    suite('update checks', () => {
      setup(() => {
        sinon.spy(element, '_startUpdateCheckTimer');
        sinon.stub(element, 'async').callsFake( f => {
          // Only fire the async callback one time.
          if (element.async.callCount > 1) { return; }
          f.call(element);
        });
        element._change = generateChange({
          revisionsCount: 1,
          messagesCount: 1,
        });
      });

      test('_startUpdateCheckTimer negative delay', () => {
        const getChangeDetailStub =
            sinon.stub(element.$.restAPI, 'getChangeDetail')
                .callsFake(() => Promise.resolve(generateChange({
                  // element has latest info
                  revisionsCount: 1,
                  messagesCount: 1,
                })));

        element._serverConfig = {change: {update_delay: -1}};

        assert.isTrue(element._startUpdateCheckTimer.called);
        assert.isFalse(getChangeDetailStub.called);
      });

      test('_startUpdateCheckTimer up-to-date', () => {
        const getChangeDetailStub =
            sinon.stub(element.$.restAPI, 'getChangeDetail')
                .callsFake(() => Promise.resolve(generateChange({
                  // element has latest info
                  revisionsCount: 1,
                  messagesCount: 1,
                })));

        element._serverConfig = {change: {update_delay: 12345}};

        assert.isTrue(element._startUpdateCheckTimer.called);
        assert.isTrue(getChangeDetailStub.called);
        assert.equal(element.async.lastCall.args[1], 12345 * 1000);
      });

      test('_startUpdateCheckTimer out-of-date shows an alert', done => {
        sinon.stub(element.$.restAPI, 'getChangeDetail')
            .callsFake(() => Promise.resolve(generateChange({
              // new patchset was uploaded
              revisionsCount: 2,
              messagesCount: 1,
            })));

        element.addEventListener('show-alert', e => {
          assert.equal(e.detail.message,
              'A newer patch set has been uploaded');
          done();
        });
        element._serverConfig = {change: {update_delay: 12345}};
      });

      test('_startUpdateCheckTimer new status shows an alert', done => {
        sinon.stub(element.$.restAPI, 'getChangeDetail')
            .callsFake(() => Promise.resolve(generateChange({
              // element has latest info
              revisionsCount: 1,
              messagesCount: 1,
              status: ChangeStatus.MERGED,
            })));

        element.addEventListener('show-alert', e => {
          assert.equal(e.detail.message, 'This change has been merged');
          done();
        });
        element._serverConfig = {change: {update_delay: 12345}};
      });

      test('_startUpdateCheckTimer new messages shows an alert', done => {
        sinon.stub(element.$.restAPI, 'getChangeDetail')
            .callsFake(() => Promise.resolve(generateChange({
              revisionsCount: 1,
              // element has new message
              messagesCount: 2,
            })));
        element.addEventListener('show-alert', e => {
          assert.equal(e.detail.message,
              'There are new messages on this change');
          done();
        });
        element._serverConfig = {change: {update_delay: 12345}};
      });
    });

    test('canStartReview computation', () => {
      const change1 = {};
      const change2 = {
        actions: {
          ready: {
            enabled: true,
          },
        },
      };
      const change3 = {
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
      const scrollStub = sinon.stub(element.messagesList,
          'scrollToMessage');

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
    sinon.stub(element.$.relatedChanges, 'reload');
    element.dispatchEvent(new CustomEvent('topic-changed'));
    assert.isTrue(element.$.relatedChanges.reload.calledOnce);
  });

  test('_computeEditMode', () => {
    const callCompute = (range, params) =>
      element._computeEditMode({base: range}, {base: params});
    assert.isFalse(callCompute({}, {}));
    assert.isTrue(callCompute({}, {edit: true}));
    assert.isFalse(callCompute({basePatchNum: 'PARENT', patchNum: 1}, {}));
    assert.isFalse(callCompute({basePatchNum: 'edit', patchNum: 1}, {}));
    assert.isTrue(callCompute({basePatchNum: 1, patchNum: 'edit'}, {}));
  });

  test('_processEdit', () => {
    element._patchRange = {};
    const change = {
      current_revision: 'foo',
      revisions: {foo: {commit: {}, actions: {cherrypick: {enabled: true}}}},
    };
    let mockChange;

    // With no edit, mockChange should be unmodified.
    element._processEdit(mockChange = _.cloneDeep(change), null);
    assert.deepEqual(mockChange, change);

    // When edit is not based on the latest PS, current_revision should be
    // unmodified.
    const edit = {
      base_patch_set_number: 1,
      commit: {commit: 'bar'},
      fetch: true,
    };
    element._processEdit(mockChange = _.cloneDeep(change), edit);
    assert.notDeepEqual(mockChange, change);
    assert.equal(mockChange.revisions.bar._number, SPECIAL_PATCH_SET_NUM.EDIT);
    assert.equal(mockChange.current_revision, change.current_revision);
    assert.deepEqual(mockChange.revisions.bar.commit, {commit: 'bar'});
    assert.notOk(mockChange.revisions.bar.actions);

    edit.base_revision = 'foo';
    element._processEdit(mockChange = _.cloneDeep(change), edit);
    assert.notDeepEqual(mockChange, change);
    assert.equal(mockChange.current_revision, 'bar');
    assert.deepEqual(mockChange.revisions.bar.actions,
        mockChange.revisions.foo.actions);

    // If _patchRange.patchNum is defined, do not load edit.
    element._patchRange.patchNum = 'baz';
    change.current_revision = 'baz';
    element._processEdit(mockChange = _.cloneDeep(change), edit);
    assert.equal(element._patchRange.patchNum, 'baz');
    assert.notOk(mockChange.revisions.bar.actions);
  });

  test('file-action-tap handling', () => {
    element._patchRange = {
      basePatchNum: 'PARENT',
      patchNum: 1,
    };
    const fileList = element.$.fileList;
    const Actions = GrEditConstants.Actions;
    element.$.fileListHeader.editMode = true;
    flushAsynchronousOperations();
    const controls = element.$.fileListHeader
        .shadowRoot.querySelector('#editControls');
    sinon.stub(controls, 'openDeleteDialog');
    sinon.stub(controls, 'openRenameDialog');
    sinon.stub(controls, 'openRestoreDialog');
    sinon.stub(GerritNav, 'getEditUrlForDiff');
    sinon.stub(GerritNav, 'navigateToRelativeUrl');

    // Delete
    fileList.dispatchEvent(new CustomEvent('file-action-tap', {
      detail: {action: Actions.DELETE.id, path: 'foo'},
      bubbles: true,
      composed: true,
    }));
    flushAsynchronousOperations();

    assert.isTrue(controls.openDeleteDialog.called);
    assert.equal(controls.openDeleteDialog.lastCall.args[0], 'foo');

    // Restore
    fileList.dispatchEvent(new CustomEvent('file-action-tap', {
      detail: {action: Actions.RESTORE.id, path: 'foo'},
      bubbles: true,
      composed: true,
    }));
    flushAsynchronousOperations();

    assert.isTrue(controls.openRestoreDialog.called);
    assert.equal(controls.openRestoreDialog.lastCall.args[0], 'foo');

    // Rename
    fileList.dispatchEvent(new CustomEvent('file-action-tap', {
      detail: {action: Actions.RENAME.id, path: 'foo'},
      bubbles: true,
      composed: true,
    }));
    flushAsynchronousOperations();

    assert.isTrue(controls.openRenameDialog.called);
    assert.equal(controls.openRenameDialog.lastCall.args[0], 'foo');

    // Open
    fileList.dispatchEvent(new CustomEvent('file-action-tap', {
      detail: {action: Actions.OPEN.id, path: 'foo'},
      bubbles: true,
      composed: true,
    }));
    flushAsynchronousOperations();

    assert.isTrue(GerritNav.getEditUrlForDiff.called);
    assert.equal(GerritNav.getEditUrlForDiff.lastCall.args[1], 'foo');
    assert.equal(GerritNav.getEditUrlForDiff.lastCall.args[2], '1');
    assert.isTrue(GerritNav.navigateToRelativeUrl.called);
  });

  test('_selectedRevision updates when patchNum is changed', () => {
    const revision1 = {_number: 1, commit: {parents: []}};
    const revision2 = {_number: 2, commit: {parents: []}};
    sinon.stub(element.$.restAPI, 'getChangeDetail').returns(
        Promise.resolve({
          revisions: {
            aaa: revision1,
            bbb: revision2,
          },
          labels: {},
          actions: {},
          current_revision: 'bbb',
          change_id: 'loremipsumdolorsitamet',
        }));
    sinon.stub(element, '_getEdit').returns(Promise.resolve());
    sinon.stub(element, '_getPreferences').returns(Promise.resolve({}));
    element._patchRange = {patchNum: '2'};
    return element._getChangeDetail().then(() => {
      assert.strictEqual(element._selectedRevision, revision2);

      element.set('_patchRange.patchNum', '1');
      assert.strictEqual(element._selectedRevision, revision1);
    });
  });

  test('_selectedRevision is assigned when patchNum is edit', () => {
    const revision1 = {_number: 1, commit: {parents: []}};
    const revision2 = {_number: 2, commit: {parents: []}};
    const revision3 = {_number: 'edit', commit: {parents: []}};
    sinon.stub(element.$.restAPI, 'getChangeDetail').returns(
        Promise.resolve({
          revisions: {
            aaa: revision1,
            bbb: revision2,
            ccc: revision3,
          },
          labels: {},
          actions: {},
          current_revision: 'ccc',
          change_id: 'loremipsumdolorsitamet',
        }));
    sinon.stub(element, '_getEdit').returns(Promise.resolve());
    sinon.stub(element, '_getPreferences').returns(Promise.resolve({}));
    element._patchRange = {patchNum: 'edit'};
    return element._getChangeDetail().then(() => {
      assert.strictEqual(element._selectedRevision, revision3);
    });
  });

  test('_sendShowChangeEvent', () => {
    element._change = {labels: {}};
    element._patchRange = {patchNum: 4};
    element._mergeable = true;
    const showStub = sinon.stub(element.$.jsAPI, 'handleEvent');
    element._sendShowChangeEvent();
    assert.isTrue(showStub.calledOnce);
    assert.equal(
        showStub.lastCall.args[0], element.$.jsAPI.EventType.SHOW_CHANGE);
    assert.deepEqual(showStub.lastCall.args[1], {
      change: {labels: {}},
      patchNum: 4,
      info: {mergeable: true},
    });
  });

  suite('_handleEditTap', () => {
    let fireEdit;

    setup(() => {
      fireEdit = () => {
        element.$.actions.dispatchEvent(new CustomEvent('edit-tap'));
      };
      navigateToChangeStub.restore();

      element._change = {revisions: {rev1: {_number: 1}}};
    });

    test('edit exists in revisions', done => {
      sinon.stub(GerritNav, 'navigateToChange').callsFake((...args) => {
        assert.equal(args.length, 2);
        assert.equal(args[1], SPECIAL_PATCH_SET_NUM.EDIT); // patchNum
        done();
      });

      element.set('_change.revisions.rev2',
          {_number: SPECIAL_PATCH_SET_NUM.EDIT});
      flushAsynchronousOperations();

      fireEdit();
    });

    test('no edit exists in revisions, non-latest patchset', done => {
      sinon.stub(GerritNav, 'navigateToChange').callsFake((...args) => {
        assert.equal(args.length, 4);
        assert.equal(args[1], 1); // patchNum
        assert.equal(args[3], true); // opt_isEdit
        done();
      });

      element.set('_change.revisions.rev2', {_number: 2});
      element._patchRange = {patchNum: 1};
      flushAsynchronousOperations();

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
      element._patchRange = {patchNum: 2};
      flushAsynchronousOperations();

      fireEdit();
    });
  });

  test('_handleStopEditTap', done => {
    sinon.stub(element.$.metadata, '_computeLabelNames');
    navigateToChangeStub.restore();
    sinon.stub(GerritNav, 'navigateToChange').callsFake((...args) => {
      assert.equal(args.length, 2);
      assert.equal(args[1], 1); // patchNum
      done();
    });

    element._patchRange = {patchNum: 1};
    element.$.actions.dispatchEvent(new CustomEvent('stop-edit-tap',
        {bubbles: false}));
  });

  suite('plugin endpoints', () => {
    test('endpoint params', done => {
      element._change = {labels: {}};
      element._selectedRevision = {};
      let hookEl;
      let plugin;
      pluginApi.install(
          p => {
            plugin = p;
            plugin.hook('change-view-integration').getLastAttached()
                .then(
                    el => hookEl = el);
          },
          '0.1',
          'http://some/plugins/url.html');
      flush(() => {
        assert.strictEqual(hookEl.plugin, plugin);
        assert.strictEqual(hookEl.change, element._change);
        assert.strictEqual(hookEl.revision, element._selectedRevision);
        done();
      });
    });
  });

  suite('_getMergeability', () => {
    let getMergeableStub;

    setup(() => {
      element._change = {labels: {}};
      getMergeableStub = sinon.stub(element.$.restAPI, 'getMergeable')
          .returns(Promise.resolve({mergeable: true}));
    });

    test('merged change', () => {
      element._mergeable = null;
      element._change.status = ChangeStatus.MERGED;
      return element._getMergeability().then(() => {
        assert.isFalse(element._mergeable);
        assert.isFalse(getMergeableStub.called);
      });
    });

    test('abandoned change', () => {
      element._mergeable = null;
      element._change.status = ChangeStatus.ABANDONED;
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
    sinon.stub(element.$.relatedChanges, 'reload');
    sinon.stub(element, '_reload').returns(Promise.resolve());
    const setStub = sinon.stub(element.$.restAPI, 'setInProjectLookup');
    element._paramsChanged({
      view: GerritNav.View.CHANGE,
      changeNum: 101,
      project: 'test-project',
    });
    assert.isTrue(setStub.calledOnce);
    assert.isTrue(setStub.calledWith(101, 'test-project'));
  });

  test('_handleToggleStar called when star is tapped', () => {
    element._change = {
      owner: {_account_id: 1},
      starred: false,
    };
    element._loggedIn = true;
    const stub = sinon.stub(element, '_handleToggleStar');
    flushAsynchronousOperations();

    MockInteractions.tap(element.$.changeStar.shadowRoot
        .querySelector('button'));
    assert.isTrue(stub.called);
  });

  suite('gr-reporting tests', () => {
    setup(() => {
      element._patchRange = {
        basePatchNum: 'PARENT',
        patchNum: 1,
      };
      sinon.stub(element, '_getChangeDetail').returns(Promise.resolve());
      sinon.stub(element, '_getProjectConfig').returns(Promise.resolve());
      sinon.stub(element, '_reloadComments').returns(Promise.resolve());
      sinon.stub(element, '_getMergeability').returns(Promise.resolve());
      sinon.stub(element, '_getLatestCommitMessage')
          .returns(Promise.resolve());
    });

    test('don\'t report changedDisplayed on reply', done => {
      const changeDisplayStub =
        sinon.stub(element.reporting, 'changeDisplayed');
      const changeFullyLoadedStub =
        sinon.stub(element.reporting, 'changeFullyLoaded');
      element._handleReplySent();
      flush(() => {
        assert.isFalse(changeDisplayStub.called);
        assert.isFalse(changeFullyLoadedStub.called);
        done();
      });
    });

    test('report changedDisplayed on _paramsChanged', done => {
      const changeDisplayStub =
        sinon.stub(element.reporting, 'changeDisplayed');
      const changeFullyLoadedStub =
        sinon.stub(element.reporting, 'changeFullyLoaded');
      element._paramsChanged({
        view: GerritNav.View.CHANGE,
        changeNum: 101,
        project: 'test-project',
      });
      flush(() => {
        assert.isTrue(changeDisplayStub.called);
        assert.isTrue(changeFullyLoadedStub.called);
        done();
      });
    });
  });
});
