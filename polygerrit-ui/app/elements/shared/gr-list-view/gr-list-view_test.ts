/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup-karma';
import './gr-list-view';
import {GrListView} from './gr-list-view';
import {page} from '../../../utils/page-wrapper-utils';
import {queryAndAssert, stubBaseUrl} from '../../../test/test-utils';
import {GrButton} from '../gr-button/gr-button';
import * as MockInteractions from '@polymer/iron-test-helpers/mock-interactions';

const basicFixture = fixtureFromElement('gr-list-view');

suite('gr-list-view tests', () => {
  let element: GrListView;

  setup(async () => {
    element = basicFixture.instantiate();
    await element.updateComplete;
  });

  test('computeNavLink', () => {
    const offset = 25;
    const projectsPerPage = 25;
    let filter = 'test';
    const path = '/admin/projects';

    stubBaseUrl('');

    assert.equal(
      element.computeNavLink(offset, 1, projectsPerPage, filter, path),
      '/admin/projects/q/filter:test,50'
    );

    assert.equal(
      element.computeNavLink(offset, -1, projectsPerPage, filter, path),
      '/admin/projects/q/filter:test'
    );

    assert.equal(
      element.computeNavLink(offset, 1, projectsPerPage, undefined, path),
      '/admin/projects,50'
    );

    assert.equal(
      element.computeNavLink(offset, -1, projectsPerPage, undefined, path),
      '/admin/projects'
    );

    filter = 'plugins/';
    assert.equal(
      element.computeNavLink(offset, 1, projectsPerPage, filter, path),
      '/admin/projects/q/filter:plugins%252F,50'
    );
  });

  test('_onValueChange', async () => {
    let resolve: (url: string) => void;
    const promise = new Promise(r => (resolve = r));
    element.path = '/admin/projects';
    sinon.stub(page, 'show').callsFake(r => resolve(r));

    element.filter = 'test';
    await element.updateComplete;

    const url = await promise;
    assert.equal(url, '/admin/projects/q/filter:test');
  });

  test('_filterChanged not reload when swap between falsy values', () => {
    const debounceReloadStub = sinon.stub(element, 'debounceReload');
    element.filter = undefined;
    element.filter = '';
    assert.isFalse(debounceReloadStub.called);
  });

  test('next button', async () => {
    element.itemsPerPage = 25;
    let projects = new Array(26);
    await element.updateComplete;

    let loading;
    assert.isFalse(element.hideNextArrow(loading, projects));
    loading = true;
    assert.isTrue(element.hideNextArrow(loading, projects));
    loading = false;
    assert.isFalse(element.hideNextArrow(loading, projects));
    projects = [];
    assert.isTrue(element.hideNextArrow(loading, projects));
    projects = new Array(4);
    assert.isTrue(element.hideNextArrow(loading, projects));
  });

  test('prev button', async () => {
    element.loading = true;
    element.offset = 0;
    await element.updateComplete;
    assert.isTrue(
      queryAndAssert<HTMLAnchorElement>(element, '#prevArrow').hasAttribute(
        'hidden'
      )
    );

    element.loading = false;
    element.offset = 0;
    await element.updateComplete;
    assert.isTrue(
      queryAndAssert<HTMLAnchorElement>(element, '#prevArrow').hasAttribute(
        'hidden'
      )
    );

    element.loading = false;
    element.offset = 5;
    await element.updateComplete;
    assert.isFalse(
      queryAndAssert<HTMLAnchorElement>(element, '#prevArrow').hasAttribute(
        'hidden'
      )
    );
  });

  test('createNew link appears correctly', async () => {
    assert.isFalse(
      queryAndAssert<HTMLDivElement>(
        element,
        '#createNewContainer'
      ).classList.contains('show')
    );
    element.createNew = true;
    await element.updateComplete;
    assert.isTrue(
      queryAndAssert<HTMLDivElement>(
        element,
        '#createNewContainer'
      ).classList.contains('show')
    );
  });

  test('fires create clicked event when button tapped', async () => {
    const clickHandler = sinon.stub();
    element.addEventListener('create-clicked', clickHandler);
    element.createNew = true;
    await element.updateComplete;
    MockInteractions.tap(queryAndAssert<GrButton>(element, '#createNew'));
    assert.isTrue(clickHandler.called);
  });

  test('next/prev links change when path changes', async () => {
    const BRANCHES_PATH = '/path/to/branches';
    const TAGS_PATH = '/path/to/tags';
    const computeNavLinkStub = sinon.stub(element, 'computeNavLink');
    element.offset = 0;
    element.itemsPerPage = 25;
    element.filter = '';
    element.path = BRANCHES_PATH;
    await element.updateComplete;
    assert.equal(computeNavLinkStub.lastCall.args[4], BRANCHES_PATH);
    element.path = TAGS_PATH;
    await element.updateComplete;
    assert.equal(computeNavLinkStub.lastCall.args[4], TAGS_PATH);
  });

  test('computePage', () => {
    assert.equal(element.computePage(0, 25), 1);
    assert.equal(element.computePage(50, 25), 3);
  });
});
