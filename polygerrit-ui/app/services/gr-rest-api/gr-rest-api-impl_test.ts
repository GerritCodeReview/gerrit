/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import * as sinon from 'sinon';
import '../../test/common-test-setup';
import {
  addListenerForTest,
  assertFails,
  makePrefixedJSON,
  MockPromise,
  mockPromise,
  waitEventLoop,
} from '../../test/test-utils';
import {GrReviewerUpdatesParser} from '../../elements/shared/gr-rest-api-interface/gr-reviewer-updates-parser';
import {listChangesOptionsToHex} from '../../utils/change-util';
import {
  createAccountDetailWithId,
  createAccountWithId,
  createChange,
  createComment,
  createEditInfo,
  createFixReplacementInfo,
  createParsedChange,
  createServerInfo,
  TEST_PROJECT_NAME,
} from '../../test/test-data-generators';
import {CURRENT} from '../../utils/patch-set-util';
import {GrRestApiServiceImpl} from './gr-rest-api-impl';
import {
  CommentSide,
  createDefaultEditPrefs,
  HttpMethod,
} from '../../constants/constants';
import {
  AccountDetailInfo,
  BasePatchSetNum,
  ChangeInfo,
  ChangeMessageId,
  CommentInfo,
  CommentInput,
  DashboardId,
  EDIT,
  Hashtag,
  ListChangesOption,
  NumericChangeId,
  PARENT,
  ParsedJSON,
  PatchSetNum,
  RepoName,
  RevisionPatchSetNum,
  RobotCommentInfo,
  Timestamp,
  UrlEncodedCommentId,
} from '../../types/common';
import {assert} from '@open-wc/testing';
import {AuthService} from '../gr-auth/gr-auth';
import {GrAuthMock} from '../gr-auth/gr-auth_mock';
import {FlagsServiceImplementation} from '../flags/flags_impl';

const EXPECTED_QUERY_OPTIONS = listChangesOptionsToHex(
  ListChangesOption.CHANGE_ACTIONS,
  // Current actions can be costly to calculate (e.g submit action)
  // They are not used in bulk actions.
  // ListChangesOption.CURRENT_ACTIONS,
  ListChangesOption.CURRENT_REVISION,
  ListChangesOption.DETAILED_LABELS
);

suite('gr-rest-api-service-impl tests', () => {
  let element: GrRestApiServiceImpl;
  let authService: AuthService;

  let ctr = 0;
  let originalCanonicalPath: string | undefined;

  setup(() => {
    // Modify CANONICAL_PATH to effectively reset cache.
    ctr += 1;
    originalCanonicalPath = window.CANONICAL_PATH;
    window.CANONICAL_PATH = `test${ctr}`;

    const testJSON = ')]}\'\n{"hello": "bonjour"}';
    sinon.stub(window, 'fetch').resolves(new Response(testJSON));
    // fake auth
    authService = new GrAuthMock();
    sinon.stub(authService, 'authCheck').resolves(true);
    element = new GrRestApiServiceImpl(
      authService,
      new FlagsServiceImplementation()
    );

    element._projectLookup = {};
  });

  teardown(() => {
    window.CANONICAL_PATH = originalCanonicalPath;
  });

  test('parent diff comments are properly grouped', async () => {
    element.addRepoNameToCache(42 as NumericChangeId, TEST_PROJECT_NAME);
    sinon.stub(element._restApiHelper, 'fetchJSON').resolves({
      '/COMMIT_MSG': [],
      'sieve.go': [
        {
          updated: '2017-02-03 22:32:28.000000000',
          message: 'this isn’t quite right',
        },
        {
          side: CommentSide.PARENT,
          message: 'how did this work in the first place?',
          updated: '2017-02-03 22:33:28.000000000',
        },
      ],
    } as unknown as ParsedJSON);
    const obj = await element._getDiffComments(
      42 as NumericChangeId,
      '/comments',
      undefined,
      PARENT,
      1 as PatchSetNum,
      'sieve.go'
    );
    assert.equal(obj.baseComments.length, 1);
    assert.deepEqual(obj.baseComments[0], {
      side: CommentSide.PARENT,
      message: 'how did this work in the first place?',
      path: 'sieve.go',
      updated: '2017-02-03 22:33:28.000000000' as Timestamp,
    } as RobotCommentInfo);
    assert.equal(obj.comments.length, 1);
    assert.deepEqual(obj.comments[0], {
      message: 'this isn’t quite right',
      path: 'sieve.go',
      updated: '2017-02-03 22:32:28.000000000' as Timestamp,
    } as RobotCommentInfo);
  });

  test('_setRange', () => {
    const comments: CommentInfo[] = [
      {
        id: '1' as UrlEncodedCommentId,
        side: CommentSide.PARENT,
        message: 'how did this work in the first place?',
        updated: '2017-02-03 22:32:28.000000000' as Timestamp,
        range: {
          start_line: 1,
          start_character: 1,
          end_line: 2,
          end_character: 1,
        },
      },
      {
        id: '2' as UrlEncodedCommentId,
        in_reply_to: '1' as UrlEncodedCommentId,
        message: 'this isn’t quite right',
        updated: '2017-02-03 22:33:28.000000000' as Timestamp,
      },
    ];
    const expectedResult: CommentInfo = {
      id: '2' as UrlEncodedCommentId,
      in_reply_to: '1' as UrlEncodedCommentId,
      message: 'this isn’t quite right',
      updated: '2017-02-03 22:33:28.000000000' as Timestamp,
      range: {
        start_line: 1,
        start_character: 1,
        end_line: 2,
        end_character: 1,
      },
    };
    const comment = comments[1];
    assert.deepEqual(element._setRange(comments, comment), expectedResult);
  });

  test('_setRanges', () => {
    const comments: CommentInfo[] = [
      {
        id: '3' as UrlEncodedCommentId,
        in_reply_to: '2' as UrlEncodedCommentId,
        message: 'this isn’t quite right either',
        updated: '2017-02-03 22:34:28.000000000' as Timestamp,
      },
      {
        id: '2' as UrlEncodedCommentId,
        in_reply_to: '1' as UrlEncodedCommentId,
        message: 'this isn’t quite right',
        updated: '2017-02-03 22:33:28.000000000' as Timestamp,
      },
      {
        id: '1' as UrlEncodedCommentId,
        side: CommentSide.PARENT,
        message: 'how did this work in the first place?',
        updated: '2017-02-03 22:32:28.000000000' as Timestamp,
        range: {
          start_line: 1,
          start_character: 1,
          end_line: 2,
          end_character: 1,
        },
      },
    ];
    const expectedResult: CommentInfo[] = [
      {
        id: '1' as UrlEncodedCommentId,
        side: CommentSide.PARENT,
        message: 'how did this work in the first place?',
        updated: '2017-02-03 22:32:28.000000000' as Timestamp,
        range: {
          start_line: 1,
          start_character: 1,
          end_line: 2,
          end_character: 1,
        },
      },
      {
        id: '2' as UrlEncodedCommentId,
        in_reply_to: '1' as UrlEncodedCommentId,
        message: 'this isn’t quite right',
        updated: '2017-02-03 22:33:28.000000000' as Timestamp,
        range: {
          start_line: 1,
          start_character: 1,
          end_line: 2,
          end_character: 1,
        },
      },
      {
        id: '3' as UrlEncodedCommentId,
        in_reply_to: '2' as UrlEncodedCommentId,
        message: 'this isn’t quite right either',
        updated: '2017-02-03 22:34:28.000000000' as Timestamp,
        range: {
          start_line: 1,
          start_character: 1,
          end_line: 2,
          end_character: 1,
        },
      },
    ];
    assert.deepEqual(element._setRanges(comments), expectedResult);
  });

  test('differing patch diff comments are properly grouped', async () => {
    sinon.stub(element, 'getRepoName').resolves('test' as RepoName);
    sinon.stub(element._restApiHelper, 'fetchJSON').callsFake(async request => {
      const url = request.url;
      if (url === '/changes/test~42/revisions/1/comments') {
        return {
          '/COMMIT_MSG': [],
          'sieve.go': [
            {
              message: 'this isn’t quite right',
              updated: '2017-02-03 22:32:28.000000000',
            },
            {
              side: CommentSide.PARENT,
              message: 'how did this work in the first place?',
              updated: '2017-02-03 22:33:28.000000000',
            },
          ],
        } as unknown as ParsedJSON;
      } else if (url === '/changes/test~42/revisions/2/comments') {
        return {
          '/COMMIT_MSG': [],
          'sieve.go': [
            {
              message: 'What on earth are you thinking, here?',
              updated: '2017-02-03 22:32:28.000000000',
            },
            {
              side: CommentSide.PARENT,
              message: 'Yeah not sure how this worked either?',
              updated: '2017-02-03 22:33:28.000000000',
            },
            {
              message: '¯\\_(ツ)_/¯',
              updated: '2017-02-04 22:33:28.000000000',
            },
          ],
        } as unknown as ParsedJSON;
      }
      return undefined;
    });
    const obj = await element._getDiffComments(
      42 as NumericChangeId,
      '/comments',
      undefined,
      1 as BasePatchSetNum,
      2 as PatchSetNum,
      'sieve.go'
    );
    assert.equal(obj.baseComments.length, 1);
    assert.deepEqual(obj.baseComments[0], {
      message: 'this isn’t quite right',
      path: 'sieve.go',
      updated: '2017-02-03 22:32:28.000000000' as Timestamp,
    } as RobotCommentInfo);
    assert.equal(obj.comments.length, 2);
    assert.deepEqual(obj.comments[0], {
      message: 'What on earth are you thinking, here?',
      path: 'sieve.go',
      updated: '2017-02-03 22:32:28.000000000' as Timestamp,
    } as RobotCommentInfo);
    assert.deepEqual(obj.comments[1], {
      message: '¯\\_(ツ)_/¯',
      path: 'sieve.go',
      updated: '2017-02-04 22:33:28.000000000' as Timestamp,
    } as RobotCommentInfo);
  });

  test('legacy n,z key in change url is replaced', async () => {
    const stub = sinon
      .stub(element._restApiHelper, 'fetchJSON')
      .resolves([] as unknown as ParsedJSON);
    await element.getChanges(1, undefined, 'n,z');
    assert.equal(stub.lastCall.args[0].params!.S, 0);
  });

  test('saveDiffPreferences invalidates cache line', () => {
    const cacheKey = '/accounts/self/preferences.diff';
    const fetchStub = sinon.stub(element._restApiHelper, 'fetch');
    element._cache.set(cacheKey, {tab_size: 4} as unknown as ParsedJSON);
    element.saveDiffPreferences({
      tab_size: 8,
      ignore_whitespace: 'IGNORE_NONE',
    });
    assert.isTrue(fetchStub.called);
    assert.isFalse(element._cache.has(cacheKey));
  });

  suite('queryAccounts', () => {
    let fetchStub: sinon.SinonStub;
    const testProject = 'testproject';
    const testChangeNumber = 341682;
    setup(() => {
      fetchStub = sinon
        .stub(element._restApiHelper, 'fetch')
        .resolves(new Response(makePrefixedJSON(createAccountWithId())));
      element.addRepoNameToCache(
        testChangeNumber as NumericChangeId,
        testProject as RepoName
      );
    });

    test('url with just email', async () => {
      await element.queryAccounts('bro');
      assert.isTrue(fetchStub.calledOnce);
      assert.deepEqual(fetchStub.firstCall.args[0].params, {
        o: 'DETAILS',
        q: '"bro"',
      });
    });

    test('url with email and canSee changeId', async () => {
      await element.queryAccounts(
        'bro',
        undefined,
        testChangeNumber as NumericChangeId
      );
      assert.isTrue(fetchStub.calledOnce);
      assert.deepEqual(fetchStub.firstCall.args[0].params, {
        o: 'DETAILS',
        q: `"bro" and cansee:${testProject}~${testChangeNumber}`,
      });
    });

    test('url with email and canSee changeId and isActive', async () => {
      await element.queryAccounts(
        'bro',
        undefined,
        testChangeNumber as NumericChangeId,
        true
      );
      assert.isTrue(fetchStub.calledOnce);
      assert.deepEqual(fetchStub.firstCall.args[0].params, {
        o: 'DETAILS',
        q: `"bro" and cansee:${testProject}~${testChangeNumber} and is:active`,
      });
    });
  });

  test('getAccountSuggestions using suggest query param', () => {
    const fetchStub = sinon
      .stub(element._restApiHelper, 'fetch')
      .resolves(new Response());
    element.getAccountSuggestions('user');
    assert.isTrue(fetchStub.calledOnce);
    assert.deepEqual(fetchStub.firstCall.args[0].params, {
      suggest: undefined,
      q: 'user',
    });
  });

  test('getAccount when resp is undefined clears cache', async () => {
    const cacheKey = '/accounts/self/detail';
    const account = createAccountDetailWithId();
    element._cache.set(cacheKey, account as unknown as ParsedJSON);
    const stub = sinon
      .stub(element._restApiHelper, 'fetchCacheJSON')
      .callsFake(async req => {
        req.errFn!(undefined);
        return undefined;
      });
    assert.isTrue(element._cache.has(cacheKey));

    await element.getAccount();
    assert.isTrue(stub.called);
    assert.isFalse(element._cache.has(cacheKey));
  });

  test('getAccount when status is 403 clears cache', async () => {
    const cacheKey = '/accounts/self/detail';
    const account = createAccountDetailWithId();
    element._cache.set(cacheKey, account as unknown as ParsedJSON);
    const stub = sinon
      .stub(element._restApiHelper, 'fetchCacheJSON')
      .callsFake(async req => {
        req.errFn!(new Response(undefined, {status: 403}));
        return undefined;
      });
    assert.isTrue(element._cache.has(cacheKey));

    await element.getAccount();
    assert.isTrue(stub.called);
    assert.isFalse(element._cache.has(cacheKey));
  });

  test('getAccount when resp is successful updates cache', async () => {
    const cacheKey = '/accounts/self/detail';
    const account = createAccountDetailWithId();
    const stub = sinon
      .stub(element._restApiHelper, 'fetchCacheJSON')
      .callsFake(async () => {
        element._cache.set(cacheKey, account as unknown as ParsedJSON);
        return undefined;
      });
    assert.isFalse(element._cache.has(cacheKey));

    await element.getAccount();
    assert.isTrue(stub.called);
    assert.equal(
      element._cache.get(cacheKey),
      account as unknown as ParsedJSON
    );
  });

  const preferenceSetup = function (testJSON: unknown, loggedIn: boolean) {
    sinon
      .stub(element, 'getLoggedIn')
      .callsFake(() => Promise.resolve(loggedIn));
    sinon
      .stub(element._restApiHelper, 'fetchCacheJSON')
      .callsFake(() => Promise.resolve(testJSON as ParsedJSON));
  };

  test('getPreferences returns correctly logged in', async () => {
    const testJSON = {diff_view: 'SIDE_BY_SIDE'};
    const loggedIn = true;

    preferenceSetup(testJSON, loggedIn);

    const obj = await element.getPreferences();
    assert.equal(obj!.diff_view, 'SIDE_BY_SIDE');
  });

  test('getPreferences returns correctly on larger screens logged in', async () => {
    const testJSON = {diff_view: 'UNIFIED_DIFF'};
    const loggedIn = true;

    preferenceSetup(testJSON, loggedIn);

    const obj = await element.getPreferences();
    assert.equal(obj!.diff_view, 'UNIFIED_DIFF');
  });

  test('getPreferences returns correctly on larger screens no login', async () => {
    const testJSON = {diff_view: 'UNIFIED_DIFF'};
    const loggedIn = false;

    preferenceSetup(testJSON, loggedIn);

    const obj = await element.getPreferences();
    assert.equal(obj!.diff_view, 'SIDE_BY_SIDE');
  });

  test('savePreferences normalizes download scheme', () => {
    const fetchStub = sinon
      .stub(element._restApiHelper, 'fetch')
      .resolves(new Response());
    element.savePreferences({download_scheme: 'HTTP'});
    assert.isTrue(fetchStub.called);
    assert.equal(
      JSON.parse(fetchStub.lastCall.args[0].fetchOptions?.body as string)
        .download_scheme,
      'http'
    );
  });

  test('getDiffPreferences returns correct defaults', async () => {
    sinon.stub(element, 'getLoggedIn').callsFake(() => Promise.resolve(false));

    const obj = (await element.getDiffPreferences())!;
    assert.equal(obj.context, 10);
    assert.equal(obj.cursor_blink_rate, 0);
    assert.equal(obj.font_size, 12);
    assert.equal(obj.ignore_whitespace, 'IGNORE_NONE');
    assert.equal(obj.line_length, 100);
    assert.equal(obj.line_wrapping, false);
    assert.equal(obj.show_line_endings, true);
    assert.equal(obj.show_tabs, true);
    assert.equal(obj.show_whitespace_errors, true);
    assert.equal(obj.syntax_highlighting, true);
    assert.equal(obj.tab_size, 8);
  });

  test('saveDiffPreferences set show_tabs to false', () => {
    const fetchStub = sinon.stub(element._restApiHelper, 'fetch');
    element.saveDiffPreferences({
      show_tabs: false,
      ignore_whitespace: 'IGNORE_NONE',
    });
    assert.isTrue(fetchStub.called);
    assert.equal(
      JSON.parse(fetchStub.lastCall.args[0].fetchOptions?.body as string)
        .show_tabs,
      false
    );
  });

  test('getEditPreferences returns correct defaults', async () => {
    sinon.stub(element, 'getLoggedIn').callsFake(() => Promise.resolve(false));

    const obj = (await element.getEditPreferences())!;
    assert.equal(obj.auto_close_brackets, false);
    assert.equal(obj.cursor_blink_rate, 0);
    assert.equal(obj.hide_line_numbers, false);
    assert.equal(obj.hide_top_menu, false);
    assert.equal(obj.indent_unit, 2);
    assert.equal(obj.indent_with_tabs, false);
    assert.equal(obj.line_length, 100);
    assert.equal(obj.line_wrapping, false);
    assert.equal(obj.match_brackets, true);
    assert.equal(obj.show_base, false);
    assert.equal(obj.show_tabs, true);
    assert.equal(obj.show_whitespace_errors, true);
    assert.equal(obj.syntax_highlighting, true);
    assert.equal(obj.tab_size, 8);
  });

  test('saveEditPreferences set show_tabs to false', () => {
    const fetchStub = sinon.stub(element._restApiHelper, 'fetch');
    element.saveEditPreferences({
      ...createDefaultEditPrefs(),
      show_tabs: false,
    });
    assert.isTrue(fetchStub.called);
    assert.equal(
      JSON.parse(fetchStub.lastCall.args[0].fetchOptions?.body as string)
        .show_tabs,
      false
    );
  });

  test('confirmEmail', () => {
    const fetchStub = sinon.stub(element._restApiHelper, 'fetch').resolves();
    element.confirmEmail('foo');
    assert.isTrue(fetchStub.calledOnce);
    assert.equal(
      fetchStub.lastCall.args[0].fetchOptions?.method,
      HttpMethod.PUT
    );
    assert.equal(
      fetchStub.lastCall.args[0].url,
      '/config/server/email.confirm'
    );
    assert.deepEqual(
      JSON.parse(fetchStub.lastCall.args[0].fetchOptions?.body as string),
      {token: 'foo'}
    );
  });

  test('setPreferredAccountEmail', async () => {
    const email1 = 'email1@example.com';
    const email2 = 'email2@example.com';
    const encodedEmail = encodeURIComponent(email2);
    const sendStub = sinon.stub(element._restApiHelper, 'fetch').resolves();
    element._cache.set('/accounts/self/emails', [
      {email: email1, preferred: true},
      {email: email2, preferred: false},
    ] as unknown as ParsedJSON);

    await element.setPreferredAccountEmail(email2);
    assert.isTrue(sendStub.calledOnce);
    assert.equal(
      sendStub.lastCall.args[0].fetchOptions?.method,
      HttpMethod.PUT
    );
    assert.equal(
      sendStub.lastCall.args[0].url,
      `/accounts/self/emails/${encodedEmail}/preferred`
    );
    assert.deepEqual(element._cache.get('/accounts/self/emails'), [
      {email: email1, preferred: false},
      {email: email2, preferred: true},
    ] as unknown as ParsedJSON);
  });

  test('setAccountUsername', async () => {
    const fetchStub = sinon
      .stub(element._restApiHelper, 'fetch')
      .resolves(new Response(makePrefixedJSON('john')));
    element._cache.set(
      '/accounts/self/detail',
      createAccountDetailWithId() as unknown as ParsedJSON
    );
    await element.setAccountUsername('john');
    assert.isTrue(fetchStub.calledOnce);
    assert.equal(
      fetchStub.lastCall.args[0].fetchOptions?.method,
      HttpMethod.PUT
    );
    assert.equal(fetchStub.lastCall.args[0].url, '/accounts/self/username');
    assert.deepEqual(
      JSON.parse(fetchStub.lastCall.args[0].fetchOptions?.body as string),
      {username: 'john'}
    );
    assert.deepEqual(
      (
        element._cache.get(
          '/accounts/self/detail'
        ) as unknown as AccountDetailInfo
      ).username,
      'john'
    );
  });

  test('setAccountUsername empty unsets field', async () => {
    const fetchStub = sinon
      .stub(element._restApiHelper, 'fetch')
      .resolves(new Response(undefined, {status: 204}));
    element._cache.set('/accounts/self/detail', {
      ...createAccountDetailWithId(),
      username: 'john',
    } as unknown as ParsedJSON);
    await element.setAccountUsername('');
    assert.isTrue(fetchStub.calledOnce);
    assert.equal(
      fetchStub.lastCall.args[0].fetchOptions?.method,
      HttpMethod.PUT
    );
    assert.isUndefined(
      (
        element._cache.get(
          '/accounts/self/detail'
        ) as unknown as AccountDetailInfo
      ).username
    );
  });

  test('setAccountDisplayName', async () => {
    const fetchStub = sinon
      .stub(element._restApiHelper, 'fetch')
      .resolves(new Response(makePrefixedJSON('john')));
    element._cache.set(
      '/accounts/self/detail',
      createAccountDetailWithId() as unknown as ParsedJSON
    );
    await element.setAccountDisplayName('john');
    assert.isTrue(fetchStub.calledOnce);
    assert.equal(
      fetchStub.lastCall.args[0].fetchOptions?.method,
      HttpMethod.PUT
    );
    assert.equal(fetchStub.lastCall.args[0].url, '/accounts/self/displayname');
    assert.deepEqual(
      JSON.parse(fetchStub.lastCall.args[0].fetchOptions?.body as string),
      {display_name: 'john'}
    );
    assert.deepEqual(
      (
        element._cache.get(
          '/accounts/self/detail'
        ) as unknown as AccountDetailInfo
      ).display_name,
      'john'
    );
  });

  test('setAccountDisplayName empty unsets field', async () => {
    const fetchStub = sinon
      .stub(element._restApiHelper, 'fetch')
      .resolves(new Response(undefined, {status: 204}));
    element._cache.set('/accounts/self/detail', {
      ...createAccountDetailWithId(),
      display_name: 'john',
    } as unknown as ParsedJSON);
    await element.setAccountDisplayName('');
    assert.isTrue(fetchStub.calledOnce);
    assert.equal(
      fetchStub.lastCall.args[0].fetchOptions?.method,
      HttpMethod.PUT
    );
    assert.isUndefined(
      (
        element._cache.get(
          '/accounts/self/detail'
        ) as unknown as AccountDetailInfo
      ).display_name
    );
  });

  test('setAccountName', async () => {
    const fetchStub = sinon
      .stub(element._restApiHelper, 'fetch')
      .resolves(new Response(makePrefixedJSON('john')));
    element._cache.set(
      '/accounts/self/detail',
      createAccountDetailWithId() as unknown as ParsedJSON
    );
    await element.setAccountName('john');
    assert.isTrue(fetchStub.calledOnce);
    assert.equal(
      fetchStub.lastCall.args[0].fetchOptions?.method,
      HttpMethod.PUT
    );
    assert.equal(fetchStub.lastCall.args[0].url, '/accounts/self/name');
    assert.deepEqual(
      JSON.parse(fetchStub.lastCall.args[0].fetchOptions?.body as string),
      {name: 'john'}
    );
    assert.deepEqual(
      (
        element._cache.get(
          '/accounts/self/detail'
        ) as unknown as AccountDetailInfo
      ).name,
      'john'
    );
  });

  test('setAccountName empty unsets field', async () => {
    const fetchStub = sinon
      .stub(element._restApiHelper, 'fetch')
      .resolves(new Response(undefined, {status: 204}));
    element._cache.set('/accounts/self/detail', {
      ...createAccountDetailWithId(),
      name: 'john',
    } as unknown as ParsedJSON);
    await element.setAccountName('');
    assert.isTrue(fetchStub.calledOnce);
    assert.equal(
      fetchStub.lastCall.args[0].fetchOptions?.method,
      HttpMethod.PUT
    );
    assert.isUndefined(
      (
        element._cache.get(
          '/accounts/self/detail'
        ) as unknown as AccountDetailInfo
      ).name
    );
  });

  test('setAccountStatus', async () => {
    const fetchStub = sinon
      .stub(element._restApiHelper, 'fetch')
      .resolves(new Response(makePrefixedJSON('OOO')));
    element._cache.set(
      '/accounts/self/detail',
      createAccountDetailWithId() as unknown as ParsedJSON
    );
    await element.setAccountStatus('OOO');
    assert.isTrue(fetchStub.calledOnce);
    assert.equal(
      fetchStub.lastCall.args[0].fetchOptions?.method,
      HttpMethod.PUT
    );
    assert.equal(fetchStub.lastCall.args[0].url, '/accounts/self/status');
    assert.deepEqual(
      JSON.parse(fetchStub.lastCall.args[0].fetchOptions?.body as string),
      {status: 'OOO'}
    );
    assert.deepEqual(
      (
        element._cache.get(
          '/accounts/self/detail'
        ) as unknown as AccountDetailInfo
      ).status,
      'OOO'
    );
  });

  test('setAccountStatus empty unsets field', async () => {
    const fetchStub = sinon
      .stub(element._restApiHelper, 'fetch')
      .resolves(new Response(undefined, {status: 204}));
    element._cache.set('/accounts/self/detail', {
      ...createAccountDetailWithId(),
      status: 'OOO',
    } as unknown as ParsedJSON);
    await element.setAccountStatus('');
    assert.isTrue(fetchStub.calledOnce);
    assert.equal(
      fetchStub.lastCall.args[0].fetchOptions?.method,
      HttpMethod.PUT
    );
    assert.isUndefined(
      (
        element._cache.get(
          '/accounts/self/detail'
        ) as unknown as AccountDetailInfo
      ).status
    );
  });

  suite('draft comments', () => {
    test('_sendDiffDraftRequest pending requests tracked', async () => {
      const obj = element._pendingRequests;
      const promises: MockPromise<string>[] = [];
      sinon.stub(element, '_changeBaseURL').callsFake(() => {
        promises.push(mockPromise<string>());
        return promises[promises.length - 1];
      });
      const fetchStub = sinon
        .stub(element._restApiHelper, 'fetch')
        .resolves(new Response(undefined, {status: 201}));
      const draft: CommentInput = {
        id: 'draft-id' as UrlEncodedCommentId,
        message: 'draft message',
      };
      assert.isFalse(!!element.hasPendingDiffDrafts());

      element._sendDiffDraftRequest(
        HttpMethod.PUT,
        123 as NumericChangeId,
        1 as PatchSetNum,
        draft
      );
      assert.equal(obj.sendDiffDraft.length, 1);
      assert.isTrue(!!element.hasPendingDiffDrafts());

      element._sendDiffDraftRequest(
        HttpMethod.PUT,
        123 as NumericChangeId,
        1 as PatchSetNum,
        draft
      );
      assert.equal(obj.sendDiffDraft.length, 2);
      assert.isTrue(!!element.hasPendingDiffDrafts());

      for (const promise of promises) {
        promise.resolve('');
      }

      await element.awaitPendingDiffDrafts();
      assert.equal(obj.sendDiffDraft.length, 0);
      assert.isFalse(!!element.hasPendingDiffDrafts());

      assert.isTrue(fetchStub.called);
      assert.deepEqual(
        JSON.parse(fetchStub.lastCall.args[0].fetchOptions?.body as string),
        draft
      );
    });

    suite('_failForCreate200', () => {
      test('_sendDiffDraftRequest checks for 200 on create', async () => {
        element.addRepoNameToCache(123 as NumericChangeId, TEST_PROJECT_NAME);
        sinon.stub(element._restApiHelper, 'fetch').resolves(new Response());
        const failStub = sinon.stub(element, '_failForCreate200').resolves();
        await element._sendDiffDraftRequest(
          HttpMethod.PUT,
          123 as NumericChangeId,
          4 as PatchSetNum,
          {}
        );
        assert.isTrue(failStub.calledOnce);
      });

      test('_sendDiffDraftRequest no checks for 200 on non create', async () => {
        element.addRepoNameToCache(123 as NumericChangeId, TEST_PROJECT_NAME);
        sinon.stub(element._restApiHelper, 'fetch').resolves(new Response());
        const failStub = sinon.stub(element, '_failForCreate200').resolves();
        await element._sendDiffDraftRequest(
          HttpMethod.PUT,
          123 as NumericChangeId,
          4 as PatchSetNum,
          {
            id: '123' as UrlEncodedCommentId,
          }
        );
        assert.isFalse(failStub.called);
      });

      test('_failForCreate200 fails on 200', async () => {
        const result = new Response(undefined, {
          status: 200,
          headers: {
            'Set-CoOkiE': 'secret',
            Innocuous: 'hello',
          },
        });
        const error = await assertFails<Error>(
          element._failForCreate200(Promise.resolve(result))
        );
        assert.isOk(error);
        assert.include(error.message, 'Saving draft resulted in HTTP 200');
        assert.include(error.message, 'hello');
        assert.notInclude(error.message, 'secret');
      });

      test('_failForCreate200 does not fail on 201', () => {
        const result = new Response(undefined, {status: 201});
        return element._failForCreate200(Promise.resolve(result));
      });
    });
  });

  test('saveChangeEdit', async () => {
    element._projectLookup = {1: Promise.resolve('test' as RepoName)};
    const change_num = 1 as NumericChangeId;
    const file_name = 'index.php';
    const file_contents = '<?php';
    const fetchStub = sinon.stub(element._restApiHelper, 'fetch').resolves();
    element._cache.set(
      `/changes/${change_num}/edit/${file_name}`,
      {} as unknown as ParsedJSON
    );
    await element.saveChangeEdit(change_num, file_name, file_contents);
    assert.isTrue(fetchStub.calledOnce);
    assert.equal(
      fetchStub.lastCall.args[0].fetchOptions?.method,
      HttpMethod.PUT
    );
    assert.equal(
      fetchStub.lastCall.args[0].url,
      '/changes/test~1/edit/' + file_name
    );
    assert.equal(fetchStub.lastCall.args[0].fetchOptions?.body, file_contents);
  });

  test('putChangeCommitMessage', async () => {
    element._projectLookup = {1: Promise.resolve('test' as RepoName)};
    const change_num = 1 as NumericChangeId;
    const message = 'this is a commit message';
    const committer_email = 'test@example.com';
    const fetchStub = sinon.stub(element._restApiHelper, 'fetch').resolves();
    element._cache.set(
      `/changes/${change_num}/message`,
      {} as unknown as ParsedJSON
    );
    await element.putChangeCommitMessage(change_num, message, committer_email);
    assert.isTrue(fetchStub.calledOnce);
    assert.equal(
      fetchStub.lastCall.args[0].fetchOptions?.method,
      HttpMethod.PUT
    );
    assert.equal(fetchStub.lastCall.args[0].url, '/changes/test~1/message');
    assert.deepEqual(
      JSON.parse(fetchStub.lastCall.args[0].fetchOptions?.body as string),
      {
        message,
        committer_email,
      }
    );
  });

  test('updateIdentityInChangeEdit', async () => {
    element._projectLookup = {1: Promise.resolve('test' as RepoName)};
    const change_num = 1 as NumericChangeId;
    const name = 'user';
    const email = 'user@example.com';
    const type = 'AUTHOR';
    const fetchStub = sinon.stub(element._restApiHelper, 'fetch').resolves();
    await element.updateIdentityInChangeEdit(change_num, name, email, type);
    assert.isTrue(fetchStub.calledOnce);
    assert.equal(
      fetchStub.lastCall.args[0].fetchOptions?.method,
      HttpMethod.PUT
    );
    assert.equal(
      fetchStub.lastCall.args[0].url,
      '/changes/test~1/edit:identity'
    );
    assert.deepEqual(
      JSON.parse(fetchStub.lastCall.args[0].fetchOptions?.body as string),
      {
        email: 'user@example.com',
        name: 'user',
        type: 'AUTHOR',
      }
    );
  });

  test('deleteChangeCommitMessage', async () => {
    element._projectLookup = {1: Promise.resolve('test' as RepoName)};
    const change_num = 1 as NumericChangeId;
    const messageId = 'abc' as ChangeMessageId;
    const fetchStub = sinon.stub(element._restApiHelper, 'fetch').resolves();
    await element.deleteChangeCommitMessage(change_num, messageId);
    assert.isTrue(fetchStub.calledOnce);
    assert.equal(
      fetchStub.lastCall.args[0].fetchOptions?.method,
      HttpMethod.DELETE
    );
    assert.equal(
      fetchStub.lastCall.args[0].url,
      '/changes/test~1/messages/abc'
    );
  });

  test('startWorkInProgress', async () => {
    element.addRepoNameToCache(42 as NumericChangeId, TEST_PROJECT_NAME);
    const fetchStub = sinon
      .stub(element._restApiHelper, 'fetch')
      .resolves(new Response());
    const urlSpy = sinon.spy(element, '_changeBaseURL');
    await element.startWorkInProgress(42 as NumericChangeId);
    assert.isTrue(fetchStub.calledOnce);
    assert.isTrue(urlSpy.calledOnce);
    assert.equal(urlSpy.lastCall.args[0], 42 as NumericChangeId);
    assert.isNotOk(urlSpy.lastCall.args[1]);
    assert.equal(
      fetchStub.lastCall.args[0].fetchOptions?.method,
      HttpMethod.POST
    );
    assert.isTrue(fetchStub.lastCall.args[0].url.endsWith('/wip'));
    assert.deepEqual(
      JSON.parse(fetchStub.lastCall.args[0].fetchOptions?.body as string),
      {}
    );

    await element.startWorkInProgress(42 as NumericChangeId, 'revising...');
    assert.isTrue(fetchStub.calledTwice);
    assert.equal(urlSpy.lastCall.args[0], 42 as NumericChangeId);
    assert.isNotOk(urlSpy.lastCall.args[1]);
    assert.equal(
      fetchStub.lastCall.args[0].fetchOptions?.method,
      HttpMethod.POST
    );
    assert.isTrue(fetchStub.lastCall.args[0].url.endsWith('/wip'));
    assert.deepEqual(
      JSON.parse(fetchStub.lastCall.args[0].fetchOptions?.body as string),
      {
        message: 'revising...',
      }
    );
  });

  test('deleteComment', async () => {
    element.addRepoNameToCache(123 as NumericChangeId, TEST_PROJECT_NAME);
    const comment = createComment();
    const fetchStub = sinon
      .stub(element._restApiHelper, 'fetchJSON')
      .resolves(comment as unknown as ParsedJSON);
    const response = await element.deleteComment(
      123 as NumericChangeId,
      1 as PatchSetNum,
      '01234' as UrlEncodedCommentId,
      'removal reason'
    );
    assert.equal(response, comment);
    assert.isTrue(fetchStub.calledOnce);
    assert.equal(
      fetchStub.lastCall.args[0].fetchOptions?.method,
      HttpMethod.POST
    );
    assert.equal(
      fetchStub.lastCall.args[0].url,
      '/changes/test-project~123/revisions/1/comments/01234/delete'
    );
    assert.deepEqual(
      JSON.parse(fetchStub.lastCall.args[0].fetchOptions?.body as string),
      {
        reason: 'removal reason',
      }
    );
  });

  test('createRepo encodes name', async () => {
    const fetchStub = sinon.stub(element._restApiHelper, 'fetch').resolves();
    await element.createRepo({name: 'x/y' as RepoName});
    assert.isTrue(fetchStub.calledOnce);
    assert.equal(fetchStub.lastCall.args[0].url, '/projects/x%2Fy');
  });

  test('queryChangeFiles', async () => {
    element.addRepoNameToCache(42 as NumericChangeId, TEST_PROJECT_NAME);
    const fetchStub = sinon
      .stub(element._restApiHelper, 'fetchJSON')
      .resolves();
    await element.queryChangeFiles(42 as NumericChangeId, EDIT, 'test/path.js');
    assert.equal(
      fetchStub.lastCall.args[0].url,
      '/changes/test-project~42/revisions/edit/files?q=test%2Fpath.js'
    );
  });

  test('normal use', () => {
    const defaultQuery = '';

    assert.equal(
      element._getReposUrl('test', 25).toString(),
      [false, '/projects/?n=26&S=0&d=&m=test'].toString()
    );

    assert.equal(
      element._getReposUrl(undefined, 25).toString(),
      [false, `/projects/?n=26&S=0&d=&m=${defaultQuery}`].toString()
    );

    assert.equal(
      element._getReposUrl('test', 25, 25).toString(),
      [false, '/projects/?n=26&S=25&d=&m=test'].toString()
    );

    assert.equal(
      element._getReposUrl('inname:test', 25, 25).toString(),
      [true, '/projects/?n=26&S=25&query=inname%3Atest'].toString()
    );
  });

  test('invalidateReposCache', () => {
    const url = '/projects/?n=26&S=0&query=test';

    element._cache.set(url, {} as unknown as ParsedJSON);

    element.invalidateReposCache();

    assert.isUndefined(element._sharedFetchPromises.get(url));

    assert.isFalse(element._cache.has(url));
  });

  test('invalidateAccountsCache', () => {
    const url = '/accounts/self/detail';

    element._cache.set(url, {} as unknown as ParsedJSON);

    element.invalidateAccountsCache();

    assert.isUndefined(element._sharedFetchPromises.get(url));

    assert.isFalse(element._cache.has(url));
  });

  suite('getRepos', () => {
    const defaultQuery = '';
    let fetchCacheJSONStub: sinon.SinonStub;
    setup(() => {
      fetchCacheJSONStub = sinon
        .stub(element._restApiHelper, 'fetchCacheJSON')
        .resolves([] as unknown as ParsedJSON);
    });

    test('normal use', () => {
      element.getRepos('test', 25);
      assert.equal(
        fetchCacheJSONStub.lastCall.args[0].url,
        '/projects/?n=26&S=0&d=&m=test'
      );

      element.getRepos(undefined, 25);
      assert.equal(
        fetchCacheJSONStub.lastCall.args[0].url,
        `/projects/?n=26&S=0&d=&m=${defaultQuery}`
      );

      element.getRepos('test', 25, 25);
      assert.equal(
        fetchCacheJSONStub.lastCall.args[0].url,
        '/projects/?n=26&S=25&d=&m=test'
      );
    });

    test('with blank', () => {
      element.getRepos('test/test', 25);
      assert.equal(
        fetchCacheJSONStub.lastCall.args[0].url,
        '/projects/?n=26&S=0&d=&m=test%2Ftest'
      );
    });

    test('with hyphen', () => {
      element.getRepos('foo-bar', 25);
      assert.equal(
        fetchCacheJSONStub.lastCall.args[0].url,
        '/projects/?n=26&S=0&d=&m=foo-bar'
      );
    });

    test('with leading hyphen', () => {
      element.getRepos('-bar', 25);
      assert.equal(
        fetchCacheJSONStub.lastCall.args[0].url,
        '/projects/?n=26&S=0&d=&m=-bar'
      );
    });

    test('with trailing hyphen', () => {
      element.getRepos('foo-bar-', 25);
      assert.equal(
        fetchCacheJSONStub.lastCall.args[0].url,
        '/projects/?n=26&S=0&d=&m=foo-bar-'
      );
    });

    test('with underscore', () => {
      element.getRepos('foo_bar', 25);
      assert.equal(
        fetchCacheJSONStub.lastCall.args[0].url,
        '/projects/?n=26&S=0&d=&m=foo_bar'
      );
    });

    test('with underscore', () => {
      element.getRepos('foo_bar', 25);
      assert.equal(
        fetchCacheJSONStub.lastCall.args[0].url,
        '/projects/?n=26&S=0&d=&m=foo_bar'
      );
    });

    test('hyphen only', () => {
      element.getRepos('-', 25);
      assert.equal(
        fetchCacheJSONStub.lastCall.args[0].url,
        '/projects/?n=26&S=0&d=&m=-'
      );
    });

    test('using query', () => {
      element.getRepos('description:project', 25);
      assert.equal(
        fetchCacheJSONStub.lastCall.args[0].url,
        '/projects/?n=26&S=0&query=description%3Aproject'
      );
    });
  });

  test('_getGroupsUrl normal use', () => {
    assert.equal(element._getGroupsUrl('test', 25), '/groups/?n=26&S=0&m=test');

    assert.equal(element._getGroupsUrl('', 25), '/groups/?n=26&S=0');

    assert.equal(
      element._getGroupsUrl('test', 25, 25),
      '/groups/?n=26&S=25&m=test'
    );
  });

  test('invalidateGroupsCache', () => {
    const url = '/groups/?n=26&S=0&m=test';

    element._cache.set(url, {} as unknown as ParsedJSON);

    element.invalidateGroupsCache();

    assert.isUndefined(element._sharedFetchPromises.get(url));

    assert.isFalse(element._cache.has(url));
  });

  suite('getGroups', () => {
    let fetchCacheJSONStub: sinon.SinonStub;
    setup(() => {
      fetchCacheJSONStub = sinon.stub(element._restApiHelper, 'fetchCacheJSON');
    });

    test('normal use', () => {
      element.getGroups('test', 25);
      assert.equal(
        fetchCacheJSONStub.lastCall.args[0].url,
        '/groups/?n=26&S=0&m=test'
      );

      element.getGroups('', 25);
      assert.equal(
        fetchCacheJSONStub.lastCall.args[0].url,
        '/groups/?n=26&S=0'
      );

      element.getGroups('test', 25, 25);
      assert.equal(
        fetchCacheJSONStub.lastCall.args[0].url,
        '/groups/?n=26&S=25&m=test'
      );
    });

    test('regex', () => {
      element.getGroups('^test.*', 25);
      assert.equal(
        fetchCacheJSONStub.lastCall.args[0].url,
        '/groups/?n=26&S=0&r=%5Etest.*'
      );

      element.getGroups('^test.*', 25, 25);
      assert.equal(
        fetchCacheJSONStub.lastCall.args[0].url,
        '/groups/?n=26&S=25&r=%5Etest.*'
      );
    });
  });

  test('gerrit auth is used', () => {
    const fetchStub = sinon.stub(authService, 'fetch').resolves();
    element._restApiHelper.fetchJSON({url: 'foo'});
    assert(fetchStub.called);
  });

  test('queryAccounts does not return fetchJSON', async () => {
    const fetchJSONSpy = sinon.spy(element._restApiHelper, 'fetchJSON');
    const accts = await element.queryAccounts('');
    assert.isFalse(fetchJSONSpy.called);
    assert.equal(accts!.length, 0);
  });

  test('fetchJSON gets called by queryAccounts', async () => {
    const fetchJSONStub = sinon
      .stub(element._restApiHelper, 'fetchJSON')
      .resolves();
    await element.queryAccounts('own');
    assert.deepEqual(fetchJSONStub.lastCall.args[0].params, {
      q: '"own"',
      o: 'DETAILS',
    });
  });

  suite('getChangeDetail', () => {
    let getConfigStub: sinon.SinonStub;

    setup(() => {
      getConfigStub = sinon
        .stub(element, 'getConfig')
        .resolves(createServerInfo());
    });

    suite('change detail options', () => {
      let changeDetailStub: sinon.SinonStub;
      setup(() => {
        changeDetailStub = sinon
          .stub(element, '_getChangeDetail')
          .resolves({...createChange(), _number: 123 as NumericChangeId});
      });

      test('signed pushes disabled', async () => {
        getConfigStub.resolves({
          ...createServerInfo(),
          receive: {enable_signed_push: undefined},
        });
        const change = await element.getChangeDetail(123 as NumericChangeId);
        assert.strictEqual(123, change!._number);
        const options = changeDetailStub.firstCall.args[1];
        assert.isNotOk(
          parseInt(options, 16) & (1 << ListChangesOption.PUSH_CERTIFICATES)
        );
      });

      test('signed pushes enabled', async () => {
        getConfigStub.resolves({
          ...createServerInfo(),
          receive: {enable_signed_push: 'true'},
        });
        const change = await element.getChangeDetail(123 as NumericChangeId);
        assert.strictEqual(123, change!._number);
        const options = changeDetailStub.firstCall.args[1];
        assert.ok(
          parseInt(options, 16) & (1 << ListChangesOption.PUSH_CERTIFICATES)
        );
      });
    });

    test('GrReviewerUpdatesParser.parse is used', async () => {
      element.addRepoNameToCache(42 as NumericChangeId, TEST_PROJECT_NAME);
      const changeInfo = createParsedChange();
      const parseStub = sinon
        .stub(GrReviewerUpdatesParser, 'parse')
        .resolves(changeInfo);
      const result = await element.getChangeDetail(42 as NumericChangeId);
      assert.isTrue(parseStub.calledOnce);
      assert.equal(result, changeInfo);
    });

    test('_getChangeDetail passes params to ETags decorator', async () => {
      const changeNum = 4321 as NumericChangeId;
      element._projectLookup[changeNum] = Promise.resolve('test' as RepoName);
      const expectedUrl = `${window.CANONICAL_PATH}/changes/test~4321/detail?O=516714`;
      const optionsStub = sinon.stub(element._etags, 'getOptions');
      const collectStub = sinon.stub(element._etags, 'collect');
      sinon.stub(element._restApiHelper, 'fetch').resolves(
        new Response(
          makePrefixedJSON({
            ...createChange(),
            _number: 123 as NumericChangeId,
          })
        )
      );
      await element._getChangeDetail(changeNum, '516714');
      assert.isTrue(optionsStub.calledWithExactly(expectedUrl));
      assert.equal(collectStub.lastCall.args[0], expectedUrl);
    });

    test('_getChangeDetail calls errFn on 500', async () => {
      const errFn = sinon.stub();
      sinon.stub(element, 'getChangeActionURL').resolves('');
      sinon
        .stub(element._restApiHelper, 'fetch')
        .resolves(new Response(undefined, {status: 500}));
      await element._getChangeDetail(123 as NumericChangeId, '516714', errFn);
      assert.isTrue(errFn.called);
    });

    test('_getChangeDetail populates _projectLookup', async () => {
      sinon.stub(element, 'getChangeActionURL').resolves('');
      sinon.stub(element._restApiHelper, 'fetch').resolves(
        new Response(')]}\'{"_number":1,"project":"test"}', {
          status: 200,
        })
      );
      await element._getChangeDetail(1 as NumericChangeId, '516714');
      assert.equal(Object.keys(element._projectLookup).length, 1);
      const project = await element._projectLookup[1];
      assert.equal(project, 'test' as RepoName);
    });

    suite('_getChangeDetail ETag cache', () => {
      let requestUrl: string;
      let mockResponseSerial: string;
      let collectSpy: sinon.SinonSpy;

      setup(() => {
        requestUrl = '/foo/bar';
        const mockResponse = {foo: 'bar', baz: 42};
        mockResponseSerial = makePrefixedJSON(mockResponse);
        sinon.stub(element._restApiHelper, 'urlWithParams').returns(requestUrl);
        sinon.stub(element, 'getChangeActionURL').resolves(requestUrl);
        collectSpy = sinon.spy(element._etags, 'collect');
      });

      test('contributes to cache', async () => {
        const getPayloadSpy = sinon.spy(element._etags, 'getCachedPayload');
        sinon.stub(element._restApiHelper, 'fetch').resolves(
          new Response(mockResponseSerial, {
            status: 200,
          })
        );

        await element._getChangeDetail(123 as NumericChangeId, '516714');
        assert.isFalse(getPayloadSpy.called);
        assert.isTrue(collectSpy.calledOnce);
        const cachedResponse = element._etags.getCachedPayload(requestUrl);
        assert.equal(cachedResponse, mockResponseSerial);
      });

      test('uses cache on HTTP 304', async () => {
        const getPayloadStub = sinon.stub(element._etags, 'getCachedPayload');
        getPayloadStub.returns(mockResponseSerial);
        sinon.stub(element._restApiHelper, 'fetch').resolves(
          new Response(undefined, {
            status: 304,
          })
        );

        await element._getChangeDetail(123 as NumericChangeId, '');
        assert.isFalse(collectSpy.called);
        assert.isTrue(getPayloadStub.calledOnce);
      });
    });
  });

  test('addRepoNameToCache', async () => {
    element.addRepoNameToCache(555 as NumericChangeId, 'project' as RepoName);
    const project = await element.getRepoName(555 as NumericChangeId);
    assert.deepEqual(project, 'project' as RepoName);
  });

  suite('getRepoName', () => {
    const changeNum = 555 as NumericChangeId;
    const repo = 'test-repo' as RepoName;

    test('getChange fails to yield a project', async () => {
      const promise = mockPromise<undefined>();
      sinon.stub(element, 'getChange').returns(promise);

      const projectLookup = element.getRepoName(changeNum);
      promise.resolve(undefined);

      const err: Error = await assertFails(projectLookup);
      assert.equal(
        err.message,
        'Failed to lookup the repo for change number 555'
      );
    });

    test('getChange succeeds with project', async () => {
      const promise = mockPromise<undefined | ChangeInfo>();
      sinon.stub(element, 'getChange').returns(promise);

      const projectLookup = element.getRepoName(changeNum);
      promise.resolve({...createChange(), project: repo});

      assert.equal(await projectLookup, repo);
      assert.deepEqual(element._projectLookup, {'555': projectLookup});
    });

    test('getChange fails, but a addRepoNameToCache() call is used as fallback', async () => {
      const promise = mockPromise<undefined>();
      sinon.stub(element, 'getChange').returns(promise);

      const projectLookup = element.getRepoName(changeNum);
      element.addRepoNameToCache(changeNum, repo);
      promise.resolve(undefined);

      assert.equal(await projectLookup, repo);
    });
  });

  suite('getChanges populates _projectLookup', () => {
    test('multiple queries', async () => {
      sinon.stub(element._restApiHelper, 'fetchJSON').resolves([
        [
          {_number: 1, project: 'test'},
          {_number: 2, project: 'test'},
        ],
        [{_number: 3, project: 'test/test'}],
      ] as unknown as ParsedJSON);
      // When query instanceof Array, fetchJSON returns
      // Array<Array<Object>>.
      await element.getChangesForMultipleQueries(undefined, []);
      assert.equal(Object.keys(element._projectLookup).length, 3);
      const project1 = await element.getRepoName(1 as NumericChangeId);
      assert.equal(project1, 'test' as RepoName);
      const project2 = await element.getRepoName(2 as NumericChangeId);
      assert.equal(project2, 'test' as RepoName);
      const project3 = await element.getRepoName(3 as NumericChangeId);
      assert.equal(project3, 'test/test' as RepoName);
    });

    test('no query', async () => {
      sinon.stub(element._restApiHelper, 'fetchJSON').resolves([
        {_number: 1, project: 'test'},
        {_number: 2, project: 'test'},
        {_number: 3, project: 'test/test'},
      ] as unknown as ParsedJSON);

      // When query !instanceof Array, fetchJSON returns Array<Object>.
      await element.getChanges();
      assert.equal(Object.keys(element._projectLookup).length, 3);
      const project1 = await element.getRepoName(1 as NumericChangeId);
      assert.equal(project1, 'test' as RepoName);
      const project2 = await element.getRepoName(2 as NumericChangeId);
      assert.equal(project2, 'test' as RepoName);
      const project3 = await element.getRepoName(3 as NumericChangeId);
      assert.equal(project3, 'test/test' as RepoName);
    });
  });

  test('getDetailedChangesWithActions', async () => {
    const c1 = createChange();
    c1._number = 1 as NumericChangeId;
    const c2 = createChange();
    c2._number = 2 as NumericChangeId;
    const getChangesStub = sinon
      .stub(element, 'getChanges')
      .callsFake((changesPerPage, query, offset, options) => {
        assert.isUndefined(changesPerPage);
        assert.strictEqual(query, 'change:1 OR change:2');
        assert.isUndefined(offset);
        assert.strictEqual(options, EXPECTED_QUERY_OPTIONS);
        return Promise.resolve([]);
      });
    await element.getDetailedChangesWithActions([c1._number, c2._number]);
    assert.isTrue(getChangesStub.calledOnce);
  });

  test('getDetailedChangesWithActions with SUBMIT_REQUIREMENTS', async () => {
    const expectedQueryOptions = listChangesOptionsToHex(
      ListChangesOption.CHANGE_ACTIONS,
      ListChangesOption.CURRENT_REVISION,
      ListChangesOption.DETAILED_LABELS,
      ListChangesOption.SUBMIT_REQUIREMENTS
    );
    const c1 = createChange();
    c1._number = 1 as NumericChangeId;
    const c2 = createChange();
    c2._number = 2 as NumericChangeId;
    const getChangesStub = sinon
      .stub(element, 'getChanges')
      .callsFake((changesPerPage, query, offset, options) => {
        assert.isUndefined(changesPerPage);
        assert.strictEqual(query, 'change:1 OR change:2');
        assert.isUndefined(offset);
        assert.strictEqual(options, expectedQueryOptions);
        return Promise.resolve([]);
      });
    await element.getDetailedChangesWithActions([c1._number, c2._number], true);
    assert.isTrue(getChangesStub.calledOnce);
  });

  test('setChangeTopic', async () => {
    element.addRepoNameToCache(123 as NumericChangeId, TEST_PROJECT_NAME);
    const fetchStub = sinon
      .stub(element._restApiHelper, 'fetch')
      .resolves(new Response(makePrefixedJSON('foo-bar')));
    await element.setChangeTopic(123 as NumericChangeId, 'foo-bar');
    assert.isTrue(fetchStub.calledOnce);
    assert.deepEqual(
      JSON.parse(fetchStub.lastCall.args[0].fetchOptions?.body as string),
      {topic: 'foo-bar'}
    );
  });

  test('setChangeHashtag', async () => {
    element.addRepoNameToCache(123 as NumericChangeId, TEST_PROJECT_NAME);
    const fetchStub = sinon.stub(element._restApiHelper, 'fetchJSON');
    await element.setChangeHashtag(123 as NumericChangeId, {
      add: ['foo-bar' as Hashtag],
    });
    assert.isTrue(fetchStub.calledOnce);
    assert.sameDeepMembers(
      JSON.parse(fetchStub.lastCall.args[0].fetchOptions?.body as string).add,
      ['foo-bar']
    );
  });

  test('generateAccountHttpPassword', async () => {
    const fetchStub = sinon
      .stub(element._restApiHelper, 'fetchJSON')
      .resolves();
    await element.generateAccountHttpPassword();
    assert.isTrue(fetchStub.calledOnce);
    assert.deepEqual(
      JSON.parse(fetchStub.lastCall.args[0].fetchOptions?.body as string),
      {generate: true}
    );
  });

  suite('getChangeFiles', () => {
    test('patch only', async () => {
      element.addRepoNameToCache(123 as NumericChangeId, TEST_PROJECT_NAME);
      const fetchStub = sinon
        .stub(element._restApiHelper, 'fetchJSON')
        .resolves();
      const range = {basePatchNum: PARENT, patchNum: 2 as RevisionPatchSetNum};
      await element.getChangeFiles(123 as NumericChangeId, range);
      assert.isTrue(fetchStub.calledOnce);
      assert.equal(
        fetchStub.lastCall.args[0].url,
        '/changes/test-project~123/revisions/2/files'
      );
      assert.isNotOk(fetchStub.lastCall.args[0].params);
    });

    test('simple range', async () => {
      element.addRepoNameToCache(123 as NumericChangeId, TEST_PROJECT_NAME);
      const fetchStub = sinon
        .stub(element._restApiHelper, 'fetchJSON')
        .resolves();
      const range = {
        basePatchNum: 4 as BasePatchSetNum,
        patchNum: 5 as RevisionPatchSetNum,
      };
      await element.getChangeFiles(123 as NumericChangeId, range);
      assert.isTrue(fetchStub.calledOnce);
      assert.equal(
        fetchStub.lastCall.args[0].url,
        '/changes/test-project~123/revisions/5/files'
      );
      assert.isOk(fetchStub.lastCall.args[0].params);
      assert.equal(fetchStub.lastCall.args[0].params.base, 4);
      assert.isNotOk(fetchStub.lastCall.args[0].params.parent);
    });

    test('parent index', async () => {
      element.addRepoNameToCache(123 as NumericChangeId, TEST_PROJECT_NAME);
      const fetchStub = sinon
        .stub(element._restApiHelper, 'fetchJSON')
        .resolves();
      const range = {
        basePatchNum: -3 as BasePatchSetNum,
        patchNum: 5 as RevisionPatchSetNum,
      };
      await element.getChangeFiles(123 as NumericChangeId, range);
      assert.isTrue(fetchStub.calledOnce);
      assert.equal(
        fetchStub.lastCall.args[0].url,
        '/changes/test-project~123/revisions/5/files'
      );
      assert.isOk(fetchStub.lastCall.args[0].params);
      assert.isNotOk(fetchStub.lastCall.args[0].params.base);
      assert.equal(fetchStub.lastCall.args[0].params.parent, 3);
    });
  });

  suite('getDiff', () => {
    test('patchOnly', async () => {
      element.addRepoNameToCache(123 as NumericChangeId, TEST_PROJECT_NAME);
      const fetchStub = sinon
        .stub(element._restApiHelper, 'fetchJSON')
        .resolves();
      await element.getDiff(
        123 as NumericChangeId,
        PARENT,
        2 as PatchSetNum,
        'foo/bar.baz'
      );
      assert.isTrue(fetchStub.calledOnce);
      assert.equal(
        fetchStub.lastCall.args[0].url,
        '/changes/test-project~123/revisions/2/files/foo%2Fbar.baz/diff'
      );
      assert.isOk(fetchStub.lastCall.args[0].params);
      assert.isNotOk(fetchStub.lastCall.args[0].params.parent);
      assert.isNotOk(fetchStub.lastCall.args[0].params.base);
    });

    test('simple range', async () => {
      element.addRepoNameToCache(123 as NumericChangeId, TEST_PROJECT_NAME);
      const fetchStub = sinon
        .stub(element._restApiHelper, 'fetchJSON')
        .resolves();
      await element.getDiff(
        123 as NumericChangeId,
        4 as PatchSetNum,
        5 as PatchSetNum,
        'foo/bar.baz'
      );
      assert.isTrue(fetchStub.calledOnce);
      assert.equal(
        fetchStub.lastCall.args[0].url,
        '/changes/test-project~123/revisions/5/files/foo%2Fbar.baz/diff'
      );
      assert.isOk(fetchStub.lastCall.args[0].params);
      assert.isNotOk(fetchStub.lastCall.args[0].params.parent);
      assert.equal(fetchStub.lastCall.args[0].params.base, 4);
    });

    test('parent index', async () => {
      element.addRepoNameToCache(123 as NumericChangeId, TEST_PROJECT_NAME);
      const fetchStub = sinon
        .stub(element._restApiHelper, 'fetchJSON')
        .resolves();
      await element.getDiff(
        123 as NumericChangeId,
        -3 as PatchSetNum,
        5 as PatchSetNum,
        'foo/bar.baz'
      );
      assert.isTrue(fetchStub.calledOnce);
      assert.equal(
        fetchStub.lastCall.args[0].url,
        '/changes/test-project~123/revisions/5/files/foo%2Fbar.baz/diff'
      );
      assert.isOk(fetchStub.lastCall.args[0].params);
      assert.isNotOk(fetchStub.lastCall.args[0].params.base);
      assert.equal(fetchStub.lastCall.args[0].params.parent, 3);
    });
  });

  test('getDashboard', () => {
    const fetchCacheJSONStub = sinon.stub(
      element._restApiHelper,
      'fetchCacheJSON'
    );
    element.getDashboard(
      'gerrit/project' as RepoName,
      'default:main' as DashboardId
    );
    assert.isTrue(fetchCacheJSONStub.calledOnce);
    assert.equal(
      fetchCacheJSONStub.lastCall.args[0].url,
      '/projects/gerrit%2Fproject/dashboards/default%3Amain'
    );
  });

  test('getFileContent', async () => {
    element.addRepoNameToCache(1 as NumericChangeId, TEST_PROJECT_NAME);
    sinon.stub(element._restApiHelper, 'fetch').callsFake(() =>
      Promise.resolve(
        new Response(makePrefixedJSON('new content'), {
          status: 200,
          headers: {
            'X-FYI-Content-Type': 'text/java',
          },
        })
      )
    );

    const edit = await element.getFileContent(
      1 as NumericChangeId,
      'tst/path',
      'EDIT' as PatchSetNum
    );

    assert.deepEqual(edit, {
      content: 'new content',
      type: 'text/java',
      ok: true,
    });

    const normal = await element.getFileContent(
      1 as NumericChangeId,
      'tst/path',
      '3' as PatchSetNum
    );
    assert.deepEqual(normal, {
      content: 'new content',
      type: 'text/java',
      ok: true,
    });
  });

  test('getFileContent suppresses 404s', async () => {
    const res404 = new Response(undefined, {status: 404});
    const res500 = new Response(undefined, {status: 500});
    const spy = sinon.spy();
    addListenerForTest(document, 'server-error', spy);
    const authStub = sinon.stub(authService, 'fetch').resolves(res404);
    sinon.stub(element, '_changeBaseURL').resolves('');
    await element.getFileContent(
      1 as NumericChangeId,
      'tst/path',
      1 as PatchSetNum
    );
    await waitEventLoop();
    assert.isFalse(spy.called);
    authStub.reset();
    authStub.resolves(res500);
    await element.getFileContent(
      1 as NumericChangeId,
      'tst/path',
      1 as PatchSetNum
    );
    assert.isTrue(spy.called);
    assert.notEqual(spy.lastCall.args[0].detail.response.status, 404);
  });

  test('getChangeFilesOrEditFiles is edit-sensitive', async () => {
    const getChangeFilesStub = sinon
      .stub(element, 'getChangeFiles')
      .resolves({});
    const getChangeEditFilesStub = sinon
      .stub(element, 'getChangeEditFiles')
      .resolves({files: {}});

    await element.getChangeOrEditFiles(1 as NumericChangeId, {
      basePatchNum: PARENT,
      patchNum: EDIT,
    });
    assert.isTrue(getChangeEditFilesStub.calledOnce);
    assert.isFalse(getChangeFilesStub.called);
    await element.getChangeOrEditFiles(1 as NumericChangeId, {
      basePatchNum: PARENT,
      patchNum: 1 as RevisionPatchSetNum,
    });
    assert.isTrue(getChangeEditFilesStub.calledOnce);
    assert.isTrue(getChangeFilesStub.calledOnce);
  });

  test('getChangeEdit not logged in returns undefined', async () => {
    element.addRepoNameToCache(123 as NumericChangeId, TEST_PROJECT_NAME);
    sinon.stub(element, 'getLoggedIn').resolves(false);
    const fetchSpy = sinon.spy(element._restApiHelper, 'fetch');
    const edit = await element.getChangeEdit(123 as NumericChangeId);
    assert.isUndefined(edit);
    assert.isFalse(fetchSpy.called);
  });

  test('getChangeEdit no edit patchset returns undefined', async () => {
    element.addRepoNameToCache(123 as NumericChangeId, TEST_PROJECT_NAME);
    sinon.stub(element, 'getLoggedIn').resolves(true);
    sinon
      .stub(element._restApiHelper, 'fetch')
      .resolves(new Response(undefined, {status: 204}));
    const edit = await element.getChangeEdit(123 as NumericChangeId);
    assert.isUndefined(edit);
  });

  test('getChangeEdit returns edit patchset', async () => {
    element.addRepoNameToCache(123 as NumericChangeId, TEST_PROJECT_NAME);
    sinon.stub(element, 'getLoggedIn').resolves(true);
    const expected = createEditInfo();
    sinon
      .stub(element._restApiHelper, 'fetch')
      .resolves(new Response(makePrefixedJSON(expected)));
    const edit = await element.getChangeEdit(123 as NumericChangeId);
    assert.deepEqual(edit, expected);
  });

  test('ported comment errors do not trigger error dialog', () => {
    const change = createChange();
    const handler = sinon.stub();
    addListenerForTest(document, 'server-error', handler);
    sinon.stub(element._restApiHelper, 'fetchJSON').resolves({
      ok: false,
    } as unknown as ParsedJSON);

    element.getPortedComments(change._number, CURRENT);

    assert.isFalse(handler.called);
  });

  test('ported drafts are not requested user is not logged in', () => {
    const change = createChange();
    element.addRepoNameToCache(change._number, TEST_PROJECT_NAME);
    sinon.stub(element, 'getLoggedIn').resolves(false);
    const getChangeURLAndFetchStub = sinon.stub(
      element._restApiHelper,
      'fetchJSON'
    );

    element.getPortedDrafts(change._number, CURRENT);

    assert.isFalse(getChangeURLAndFetchStub.called);
  });

  test('saveChangeStarred', async () => {
    element.addRepoNameToCache(123 as NumericChangeId, 'test' as RepoName);
    element.addRepoNameToCache(456 as NumericChangeId, 'test' as RepoName);
    const fetchStub = sinon.stub(element._restApiHelper, 'fetch').resolves();

    await element.saveChangeStarred(123 as NumericChangeId, true);
    assert.isTrue(fetchStub.calledOnce);
    assert.deepEqual(fetchStub.lastCall.args[0], {
      fetchOptions: {method: HttpMethod.PUT},
      url: '/accounts/self/starred.changes/test~123',
      anonymizedUrl: '/accounts/self/starred.changes/*',
    });

    await element.saveChangeStarred(456 as NumericChangeId, false);
    assert.isTrue(fetchStub.calledTwice);
    assert.deepEqual(fetchStub.lastCall.args[0], {
      fetchOptions: {method: HttpMethod.DELETE},
      url: '/accounts/self/starred.changes/test~456',
      anonymizedUrl: '/accounts/self/starred.changes/*',
    });
  });
  suite('applyFixSuggestion', () => {
    const fixReplacementInfo = createFixReplacementInfo();
    let fetchStub: sinon.SinonStub;
    setup(() => {
      element.addRepoNameToCache(123 as NumericChangeId, TEST_PROJECT_NAME);
      fetchStub = sinon
        .stub(element._restApiHelper, 'fetch')
        .resolves(new Response(makePrefixedJSON({})));
    });
    test('applyFixSuggestion without targetPatchNum', async () => {
      await element.applyFixSuggestion(
        123 as NumericChangeId,
        1 as PatchSetNum,
        [fixReplacementInfo]
      );
      assert.isTrue(fetchStub.calledOnce);
      assert.equal(
        fetchStub.lastCall.args[0].url,
        '/changes/test-project~123/revisions/1/fix:apply'
      );
      const body = JSON.parse(fetchStub.lastCall.args[0].fetchOptions.body);
      assert.isTrue(
        Object.keys(body).length === 1 &&
          body.fix_replacement_infos.length === 1
      );
      assert.deepEqual(body.fix_replacement_infos[0], fixReplacementInfo);
    });

    test('applyFixSuggestion with same patchNum and targetPatchNum', async () => {
      const fixReplacementInfo = createFixReplacementInfo();
      await element.applyFixSuggestion(
        123 as NumericChangeId,
        1 as PatchSetNum,
        [fixReplacementInfo],
        1 as PatchSetNum
      );
      assert.isTrue(fetchStub.calledOnce);
      assert.equal(
        fetchStub.lastCall.args[0].url,
        '/changes/test-project~123/revisions/1/fix:apply'
      );
      const body = JSON.parse(fetchStub.lastCall.args[0].fetchOptions.body);
      assert.isTrue(Object.keys(body).length === 1);
      assert.deepEqual(body.fix_replacement_infos[0], fixReplacementInfo);
    });

    test('applyFixSuggestion with targetPatchNum', async () => {
      const fixReplacementInfo = createFixReplacementInfo();
      await element.applyFixSuggestion(
        123 as NumericChangeId,
        1 as PatchSetNum,
        [fixReplacementInfo],
        2 as PatchSetNum
      );
      assert.isTrue(fetchStub.calledOnce);
      assert.equal(
        fetchStub.lastCall.args[0].url,
        '/changes/test-project~123/revisions/2/fix:apply'
      );
      const body = JSON.parse(fetchStub.lastCall.args[0].fetchOptions.body);
      assert.isTrue(Object.keys(body).length === 2);
      assert.deepEqual(body.fix_replacement_infos[0], fixReplacementInfo);
      assert.deepEqual(body.original_patchset_for_fix, 1);
    });
  });
});
