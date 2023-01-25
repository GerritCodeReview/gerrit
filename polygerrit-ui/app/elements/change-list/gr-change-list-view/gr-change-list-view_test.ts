/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-change-list-view';
import {GrChangeListView} from './gr-change-list-view';
import {query, queryAndAssert} from '../../../test/test-utils';
import {createChange} from '../../../test/test-data-generators';
import {ChangeInfo} from '../../../api/rest-api';
import {fixture, html, waitUntil, assert} from '@open-wc/testing';
import {GrChangeList} from '../gr-change-list/gr-change-list';
import {GrChangeListSection} from '../gr-change-list-section/gr-change-list-section';
import {GrChangeListItem} from '../gr-change-list-item/gr-change-list-item';
import {testResolver} from '../../../test/common-test-setup';
import {navigationToken} from '../../core/gr-navigation/gr-navigation';

suite('gr-change-list-view tests', () => {
  let element: GrChangeListView;

  setup(async () => {
    element = await fixture(html`<gr-change-list-view></gr-change-list-view>`);
    element.query = 'test-query';
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
      element.loading = false;
      element.changes = [createChange()];
      await element.updateComplete;
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

      element.changes = [createChange()];
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
    const setUrlStub = sinon.stub(testResolver(navigationToken), 'setUrl');
    element.changes = Array(25)
      .fill(0)
      .map(_ => createChange());
    element.changesPerPage = 10;
    element.loading = false;
    await element.updateComplete;
    element.handleNextPage();
    assert.isFalse(setUrlStub.called);

    element.changes = Array(25)
      .fill(0)
      .map(_ => ({...createChange(), _more_changes: true} as ChangeInfo));
    element.loading = false;
    await element.updateComplete;
    element.handleNextPage();
    assert.isTrue(setUrlStub.called);
  });

  test('handlePreviousPage', async () => {
    const setUrlStub = sinon.stub(testResolver(navigationToken), 'setUrl');
    element.offset = 0;
    element.changes = Array(25)
      .fill(0)
      .map(_ => createChange());
    element.changesPerPage = 10;
    element.loading = false;
    await element.updateComplete;
    element.handlePreviousPage();
    assert.isFalse(setUrlStub.called);

    element.offset = 25;
    await element.updateComplete;
    element.handlePreviousPage();
    assert.isTrue(setUrlStub.called);
  });
});
