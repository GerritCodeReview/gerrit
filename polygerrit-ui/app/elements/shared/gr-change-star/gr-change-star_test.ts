/**
 * @license
 * Copyright 2015 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup-karma';
import {queryAndAssert} from '../../../test/test-utils';
import {GrChangeStar} from './gr-change-star';
import * as MockInteractions from '@polymer/iron-test-helpers/mock-interactions';
import {createChange} from '../../../test/test-data-generators';

const basicFixture = fixtureFromElement('gr-change-star');

suite('gr-change-star tests', () => {
  let element: GrChangeStar;

  setup(async () => {
    element = basicFixture.instantiate();
    element.change = {
      ...createChange(),
      starred: true,
    };
    await element.updateComplete;
  });

  // test('renders', () => {
  //   expect(element).shadowDom.to.equal(/* HTML */ ``);
  // });

  test('star visibility states', async () => {
    element.change!.starred = true;
    await element.updateComplete;
    let icon = queryAndAssert<HTMLSpanElement>(element, '.material-icon');
    assert.isTrue(icon.classList.contains('filled'));
    assert.isTrue(icon.classList.contains('active'));
    assert.equal(icon.innerText, 'grade');

    element.change!.starred = false;
    element.requestUpdate('change');
    await element.updateComplete;
    icon = queryAndAssert<HTMLSpanElement>(element, '.material-icon');
    assert.isFalse(icon.classList.contains('filled'));
    assert.isFalse(icon.classList.contains('active'));
    assert.equal(icon.innerText, 'grade');
  });

  test('starring', async () => {
    element.change!.starred = false;
    await element.updateComplete;
    assert.equal(element.change!.starred, false);

    MockInteractions.tap(queryAndAssert<HTMLButtonElement>(element, 'button'));
    await element.updateComplete;
    assert.equal(element.change!.starred, true);
  });

  test('unstarring', async () => {
    element.change!.starred = true;
    await element.updateComplete;
    assert.equal(element.change!.starred, true);

    MockInteractions.tap(queryAndAssert<HTMLButtonElement>(element, 'button'));
    await element.updateComplete;
    assert.equal(element.change!.starred, false);
  });
});
