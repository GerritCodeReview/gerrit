/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-change-list-view';
import {GrChangeListView} from './gr-change-list-view';
import {page} from '../../../utils/page-wrapper-utils';
import {navigationToken} from '../../core/gr-navigation/gr-navigation';
import {
  query,
  stubRestApi,
  queryAndAssert,
  stubFlags,
} from '../../../test/test-utils';
import {createChange} from '../../../test/test-data-generators';
import {
  ChangeInfo,
  EmailAddress,
  NumericChangeId,
  RepoName,
} from '../../../api/rest-api';
import {fixture, html, waitUntil, assert} from '@open-wc/testing';
import {GerritView} from '../../../services/router/router-model';
import {testResolver} from '../../../test/common-test-setup';
import {SinonFakeTimers, SinonStub} from 'sinon';
import {GrChangeList} from '../gr-change-list/gr-change-list';
import {GrChangeListSection} from '../gr-change-list-section/gr-change-list-section';
import {GrChangeListItem} from '../gr-change-list-item/gr-change-list-item';

const CHANGE_ID = 'IcA3dAB3edAB9f60B8dcdA6ef71A75980e4B7127';
const COMMIT_HASH = '12345678';

suite('gr-change-list-view tests', () => {
  let element: GrChangeListView;
  let changes: ChangeInfo[] | undefined = [];
  let clock: SinonFakeTimers;

  setup(async () => {
    clock = sinon.useFakeTimers();
    stubRestApi('getChanges').callsFake(() => Promise.resolve(changes));
    element = await fixture(html`<gr-change-list-view></gr-change-list-view>`);
    element.viewState = {
      view: GerritView.SEARCH,
      query: 'test-query',
      offset: '0',
    };
    await element.updateComplete;
  });

  teardown(async () => {
    await element.updateComplete;
  });

  test('render', async () => {
    element.changes = Array(25)
      .fill(0)
      .map(_ => createChange());
    element.changesPerPage = 10;
    element.loading = false;
    await element.updateComplete;

    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <div class="loading" hidden="">Loading...</div>
        <div>
          <gr-change-list> </gr-change-list>
          <nav>Page 1</nav>
        </div>
      `
    );
  });

  suite('bulk actions', () => {
    setup(async () => {
      stubFlags('isEnabled').returns(true);
      changes = [createChange()];
      element.loading = false;
      element.reload();
      clock.tick(100);
      await element.updateComplete;
      await waitUntil(() => element.loading === false);
    });

    test('checkboxes remain checked after soft reload', async () => {
      const changeListEl = queryAndAssert<GrChangeList>(
        element,
        'gr-change-list'
      );
      await changeListEl.updateComplete;
      const changeListSectionEl = queryAndAssert<GrChangeListSection>(
        changeListEl,
        'gr-change-list-section'
      );
      await changeListSectionEl.updateComplete;
      const changeListItemEl = queryAndAssert<GrChangeListItem>(
        changeListSectionEl,
        'gr-change-list-item'
      );
      await changeListItemEl.updateComplete;
      let checkbox = queryAndAssert<HTMLInputElement>(
        changeListItemEl,
        '.selection > .selectionLabel > input'
      );
      checkbox.click();
      await waitUntil(() => checkbox.checked);

      element.reload();
      await element.updateComplete;
      checkbox = queryAndAssert<HTMLInputElement>(
        query(
          query(query(element, 'gr-change-list'), 'gr-change-list-section'),
          'gr-change-list-item'
        ),
        '.selection > .selectionLabel > input'
      );
      assert.isTrue(checkbox.checked);
    });
  });

  test('computePage', () => {
    element.offset = 0;
    element.changesPerPage = 25;
    assert.equal(element.computePage(), 1);
    element.offset = 50;
    element.changesPerPage = 25;
    assert.equal(element.computePage(), 3);
  });

  test('limitFor', () => {
    const defaultLimit = 25;
    const limitFor = (q: string) => element.limitFor(q, defaultLimit);
    assert.equal(limitFor(''), defaultLimit);
    assert.equal(limitFor('limit:10'), 10);
    assert.equal(limitFor('xlimit:10'), defaultLimit);
    assert.equal(limitFor('x(limit:10'), 10);
  });

  test('computeNavLink', () => {
    element.query = 'status:open';
    element.offset = 0;
    element.changesPerPage = 5;
    let direction = 1;

    assert.equal(element.computeNavLink(direction), '/q/status:open,5');

    direction = -1;
    assert.equal(element.computeNavLink(direction), '/q/status:open');

    element.offset = 5;
    direction = 1;
    assert.equal(element.computeNavLink(direction), '/q/status:open,10');
  });

  test('prevArrow', async () => {
    element.changes = Array(25)
      .fill(0)
      .map(_ => createChange());
    element.offset = 0;
    element.loading = false;
    await element.updateComplete;
    assert.isNotOk(query(element, '#prevArrow'));

    element.offset = 5;
    await element.updateComplete;
    assert.isOk(query(element, '#prevArrow'));
  });

  test('nextArrow', async () => {
    element.changes = Array(25)
      .fill(0)
      .map(_ => ({...createChange(), _more_changes: true} as ChangeInfo));
    element.loading = false;
    await element.updateComplete;
    assert.isOk(query(element, '#nextArrow'));

    element.changes = Array(25)
      .fill(0)
      .map(_ => createChange());
    await element.updateComplete;
    assert.isNotOk(query(element, '#nextArrow'));
  });

  test('handleNextPage', async () => {
    const showStub = sinon.stub(page, 'show');
    element.changes = Array(25)
      .fill(0)
      .map(_ => createChange());
    element.changesPerPage = 10;
    element.loading = false;
    await element.updateComplete;
    element.handleNextPage();
    assert.isFalse(showStub.called);

    element.changes = Array(25)
      .fill(0)
      .map(_ => ({...createChange(), _more_changes: true} as ChangeInfo));
    element.loading = false;
    await element.updateComplete;
    element.handleNextPage();
    assert.isTrue(showStub.called);
  });

  test('handlePreviousPage', async () => {
    const showStub = sinon.stub(page, 'show');
    element.offset = 0;
    element.changes = Array(25)
      .fill(0)
      .map(_ => createChange());
    element.changesPerPage = 10;
    element.loading = false;
    await element.updateComplete;
    element.handlePreviousPage();
    assert.isFalse(showStub.called);

    element.offset = 25;
    await element.updateComplete;
    element.handlePreviousPage();
    assert.isTrue(showStub.called);
  });

  test('userId query', async () => {
    assert.isNull(element.userId);
    element.query = 'owner: foo@bar';
    element.changes = [
      {...createChange(), owner: {email: 'foo@bar' as EmailAddress}},
    ];
    await element.updateComplete;
    assert.equal(element.userId, 'foo@bar' as EmailAddress);

    element.query = 'foo bar baz';
    element.changes = [
      {...createChange(), owner: {email: 'foo@bar' as EmailAddress}},
    ];
    await element.updateComplete;
    assert.isNull(element.userId);
  });

  test('userId query without email', async () => {
    assert.isNull(element.userId);
    element.query = 'owner: foo@bar';
    element.changes = [{...createChange(), owner: {}}];
    await element.updateComplete;
    assert.isNull(element.userId);
  });

  test('repo query', async () => {
    assert.isNull(element.repo);
    element.query = 'project: test-repo';
    element.changes = [
      {
        ...createChange(),
        owner: {email: 'foo@bar' as EmailAddress},
        project: 'test-repo' as RepoName,
      },
    ];
    await element.updateComplete;
    assert.equal(element.repo, 'test-repo' as RepoName);

    element.query = 'foo bar baz';
    element.changes = [
      {...createChange(), owner: {email: 'foo@bar' as EmailAddress}},
    ];
    await element.updateComplete;
    assert.isNull(element.repo);
  });

  test('repo query with open status', async () => {
    assert.isNull(element.repo);
    element.query = 'project:test-repo status:open';
    element.changes = [
      {
        ...createChange(),
        owner: {email: 'foo@bar' as EmailAddress},
        project: 'test-repo' as RepoName,
      },
    ];
    await element.updateComplete;
    assert.equal(element.repo, 'test-repo' as RepoName);

    element.query = 'foo bar baz';
    element.changes = [
      {...createChange(), owner: {email: 'foo@bar' as EmailAddress}},
    ];
    await element.updateComplete;
    assert.isNull(element.repo);
  });

  suite('query based navigation', () => {
    let replaceUrlStub: SinonStub;
    setup(() => {
      replaceUrlStub = sinon.stub(testResolver(navigationToken), 'replaceUrl');
    });

    teardown(async () => {
      await element.updateComplete;
      sinon.restore();
    });

    test('Searching for a change ID redirects to change', async () => {
      const change = {...createChange(), _number: 1 as NumericChangeId};
      changes = [change];

      element.viewState = {view: GerritView.SEARCH, query: CHANGE_ID};
      clock.tick(100);
      await element.updateComplete;

      assert.isTrue(replaceUrlStub.called);
      assert.equal(replaceUrlStub.lastCall.firstArg, '/c/test-project/+/1');
    });

    test('Searching for a change num redirects to change', async () => {
      const change = {...createChange(), _number: 1 as NumericChangeId};
      changes = [change];

      element.viewState = {view: GerritView.SEARCH, query: '1'};
      clock.tick(100);
      await element.updateComplete;

      assert.isTrue(replaceUrlStub.called);
      assert.equal(replaceUrlStub.lastCall.firstArg, '/c/test-project/+/1');
    });

    test('Commit hash redirects to change', async () => {
      const change = {...createChange(), _number: 1 as NumericChangeId};
      changes = [change];

      element.viewState = {view: GerritView.SEARCH, query: COMMIT_HASH};
      clock.tick(100);
      await element.updateComplete;

      assert.isTrue(replaceUrlStub.called);
      assert.equal(replaceUrlStub.lastCall.firstArg, '/c/test-project/+/1');
    });

    test('Searching for an invalid change ID searches', async () => {
      changes = [];

      element.viewState = {view: GerritView.SEARCH, query: CHANGE_ID};
      clock.tick(100);
      await element.updateComplete;

      assert.isFalse(replaceUrlStub.called);
    });

    test('Change ID with multiple search results searches', async () => {
      changes = undefined;

      element.viewState = {view: GerritView.SEARCH, query: CHANGE_ID};
      clock.tick(100);
      await element.updateComplete;

      assert.isFalse(replaceUrlStub.called);
    });
  });
});
