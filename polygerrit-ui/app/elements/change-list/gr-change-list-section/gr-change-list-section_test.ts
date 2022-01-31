import {GrChangeListSection} from './gr-change-list-section';
import '../../../test/common-test-setup-karma';
import './gr-change-list-section';

/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

const basicFixture = fixtureFromElement('gr-change-list-section');

suite('dashboard queries', () => {
  let element: GrChangeListSection;

  setup(() => {
    element = basicFixture.instantiate();
  });

  teardown(() => {
    sinon.restore();
  });

  test('query without age and limit unchanged', () => {
    const query = 'status:closed owner:me';
    assert.deepEqual(element.processQuery(query), query);
  });

  test('query with age and limit', () => {
    const query = 'status:closed age:1week limit:10 owner:me';
    const expectedQuery = 'status:closed owner:me';
    assert.deepEqual(element.processQuery(query), expectedQuery);
  });

  test('query with age', () => {
    const query = 'status:closed age:1week owner:me';
    const expectedQuery = 'status:closed owner:me';
    assert.deepEqual(element.processQuery(query), expectedQuery);
  });

  test('query with limit', () => {
    const query = 'status:closed limit:10 owner:me';
    const expectedQuery = 'status:closed owner:me';
    assert.deepEqual(element.processQuery(query), expectedQuery);
  });

  test('query with age as value and not key', () => {
    const query = 'status:closed random:age';
    const expectedQuery = 'status:closed random:age';
    assert.deepEqual(element.processQuery(query), expectedQuery);
  });

  test('query with limit as value and not key', () => {
    const query = 'status:closed random:limit';
    const expectedQuery = 'status:closed random:limit';
    assert.deepEqual(element.processQuery(query), expectedQuery);
  });

  test('query with -age key', () => {
    const query = 'status:closed -age:1week';
    const expectedQuery = 'status:closed';
    assert.deepEqual(element.processQuery(query), expectedQuery);
  });
});
