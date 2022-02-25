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
import './gr-change-list-view';
import {GrChangeListView} from './gr-change-list-view';
import {page} from '../../../utils/page-wrapper-utils';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import 'lodash/lodash';
import {mockPromise, query, stubRestApi} from '../../../test/test-utils';
import {createChange} from '../../../test/test-data-generators.js';
import {
  ChangeInfo,
  EmailAddress,
  NumericChangeId,
  RepoName,
} from '../../../api/rest-api.js';

const basicFixture = fixtureFromElement('gr-change-list-view');

const CHANGE_ID = 'IcA3dAB3edAB9f60B8dcdA6ef71A75980e4B7127';
const COMMIT_HASH = '12345678';

suite('gr-change-list-view tests', () => {
  let element: GrChangeListView;

  setup(async () => {
    stubRestApi('getLoggedIn').returns(Promise.resolve(false));
    stubRestApi('getChanges').returns(Promise.resolve([]));
    stubRestApi('getAccountDetails').returns(Promise.resolve(undefined));
    stubRestApi('getAccountStatus').returns(Promise.resolve(undefined));
    element = basicFixture.instantiate();
    await element.updateComplete;
  });

  teardown(async () => {
    await element.updateComplete;
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
    const getUrlStub = sinon
      .stub(GerritNav, 'getUrlForSearchQuery')
      .returns('');
    element.query = 'status:open';
    element.offset = 0;
    element.changesPerPage = 5;
    let direction = 1;

    element.computeNavLink(direction);
    assert.equal(getUrlStub.lastCall.args[1], 5);

    direction = -1;
    element.computeNavLink(direction);
    assert.equal(getUrlStub.lastCall.args[1], 0);

    element.offset = 5;
    direction = 1;
    element.computeNavLink(direction);
    assert.equal(getUrlStub.lastCall.args[1], 10);
  });

  test('prevArrow', async () => {
    element.changes = _.times(25, _.constant(createChange()));
    element.offset = 0;
    element.loading = false;
    await element.updateComplete;
    assert.isNotOk(query(element, '#prevArrow'));

    element.offset = 5;
    await element.updateComplete;
    assert.isOk(query(element, '#prevArrow'));
  });

  test('nextArrow', async () => {
    element.changes = _.times(
      25,
      _.constant({...createChange(), _more_changes: true})
    ) as ChangeInfo[];
    element.loading = false;
    await element.updateComplete;
    assert.isOk(query(element, '#nextArrow'));

    element.changes = _.times(25, _.constant(createChange()));
    await element.updateComplete;
    assert.isNotOk(query(element, '#nextArrow'));
  });

  test('handleNextPage', async () => {
    const showStub = sinon.stub(page, 'show');
    element.changes = _.times(25, _.constant(createChange()));
    element.changesPerPage = 10;
    element.loading = false;
    await element.updateComplete;
    element.handleNextPage();
    assert.isFalse(showStub.called);

    element.changes = _.times(
      25,
      _.constant({...createChange(), _more_changes: true})
    ) as ChangeInfo[];
    element.loading = false;
    await element.updateComplete;
    element.handleNextPage();
    assert.isTrue(showStub.called);
  });

  test('handlePreviousPage', async () => {
    const showStub = sinon.stub(page, 'show');
    element.offset = 0;
    element.changes = _.times(25, _.constant(createChange()));
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
    setup(() => {});

    teardown(async () => {
      await element.updateComplete;
      sinon.restore();
    });

    test('Searching for a change ID redirects to change', async () => {
      const change = {...createChange(), _number: 1 as NumericChangeId};
      sinon.stub(element, 'getChanges').returns(Promise.resolve([change]));
      const promise = mockPromise();
      sinon.stub(GerritNav, 'navigateToChange').callsFake((url, opt) => {
        assert.equal(url, change);
        // eslint-disable-next-line @typescript-eslint/no-unnecessary-type-assertion
        assert.isTrue(opt!.redirect);
        promise.resolve();
      });

      element.params = {
        view: GerritNav.View.SEARCH,
        query: CHANGE_ID,
        offset: '',
      };
      await promise;
    });

    test('Searching for a change num redirects to change', async () => {
      const change = {...createChange(), _number: 1 as NumericChangeId};
      sinon.stub(element, 'getChanges').returns(Promise.resolve([change]));
      const promise = mockPromise();
      sinon.stub(GerritNav, 'navigateToChange').callsFake((url, opt) => {
        assert.equal(url, change);
        // eslint-disable-next-line @typescript-eslint/no-unnecessary-type-assertion
        assert.isTrue(opt!.redirect);
        promise.resolve();
      });

      element.params = {view: GerritNav.View.SEARCH, query: '1', offset: ''};
      await promise;
    });

    test('Commit hash redirects to change', async () => {
      const change = {...createChange(), _number: 1 as NumericChangeId};
      sinon.stub(element, 'getChanges').returns(Promise.resolve([change]));
      const promise = mockPromise();
      sinon.stub(GerritNav, 'navigateToChange').callsFake((url, opt) => {
        assert.equal(url, change);
        // eslint-disable-next-line @typescript-eslint/no-unnecessary-type-assertion
        assert.isTrue(opt!.redirect);
        promise.resolve();
      });

      element.params = {
        view: GerritNav.View.SEARCH,
        query: COMMIT_HASH,
        offset: '',
      };
      await promise;
    });

    test('Searching for an invalid change ID searches', async () => {
      sinon.stub(element, 'getChanges').returns(Promise.resolve([]));
      const stub = sinon.stub(GerritNav, 'navigateToChange');

      element.params = {
        view: GerritNav.View.SEARCH,
        query: CHANGE_ID,
        offset: '',
      };
      await element.updateComplete;

      assert.isFalse(stub.called);
    });

    test('Change ID with multiple search results searches', async () => {
      sinon.stub(element, 'getChanges').returns(Promise.resolve(undefined));
      const stub = sinon.stub(GerritNav, 'navigateToChange');

      element.params = {
        view: GerritNav.View.SEARCH,
        query: CHANGE_ID,
        offset: '',
      };
      await element.updateComplete;

      assert.isFalse(stub.called);
    });
  });
});
