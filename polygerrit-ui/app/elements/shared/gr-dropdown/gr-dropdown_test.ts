/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import '../../../test/common-test-setup-karma';
import './gr-dropdown';
import {DropdownLink, GrDropdown} from './gr-dropdown';
import * as MockInteractions from '@polymer/iron-test-helpers/mock-interactions';
import {queryAll, queryAndAssert} from '../../../test/test-utils';
import {GrTooltipContent} from '../gr-tooltip-content/gr-tooltip-content';

const basicFixture = fixtureFromElement('gr-dropdown');

suite('gr-dropdown tests', () => {
  let element: GrDropdown;

  setup(() => {
    element = basicFixture.instantiate();
  });

  test('_computeIsDownload', () => {
    assert.isTrue(element._computeIsDownload({download: true} as DropdownLink));
    assert.isFalse(
      element._computeIsDownload({download: false} as DropdownLink)
    );
  });

  test('tap on trigger opens menu, then closes', () => {
    sinon.stub(element, '_open').callsFake(() => {
      element.$.dropdown.open();
    });
    sinon.stub(element, '_close').callsFake(() => {
      element.$.dropdown.close();
    });
    assert.isFalse(element.$.dropdown.opened);
    MockInteractions.tap(element.$.trigger);
    assert.isTrue(element.$.dropdown.opened);
    MockInteractions.tap(element.$.trigger);
    assert.isFalse(element.$.dropdown.opened);
  });

  test('_computeURLHelper', () => {
    const path = '/test';
    const host = 'http://www.testsite.com';
    const computedPath = element._computeURLHelper(host, path);
    assert.equal(computedPath, '//http://www.testsite.com/test');
  });

  test('link URLs', () => {
    assert.equal(
      element._computeLinkURL({url: 'http://example.com/test'}),
      'http://example.com/test'
    );
    assert.equal(
      element._computeLinkURL({url: 'https://example.com/test'}),
      'https://example.com/test'
    );
    assert.equal(
      element._computeLinkURL({url: '/test'}),
      '//' + window.location.host + '/test'
    );
    assert.equal(
      element._computeLinkURL({url: '/test', target: '_blank'}),
      '/test'
    );
  });

  test('link rel', () => {
    let link: DropdownLink = {url: '/test'};
    assert.isNull(element._computeLinkRel(link));

    link = {url: '/test', target: '_blank'};
    assert.equal(element._computeLinkRel(link), 'noopener');

    link = {url: '/test', external: true};
    assert.equal(element._computeLinkRel(link), 'external');

    link = {url: '/test', target: '_blank', external: true};
    assert.equal(element._computeLinkRel(link), 'noopener');
  });

  test('_getClassIfBold', () => {
    let bold = true;
    assert.equal(element._getClassIfBold(bold), 'bold-text');

    bold = false;
    assert.equal(element._getClassIfBold(bold), '');
  });

  test('Top text exists and is bolded correctly', () => {
    element.topContent = [{text: 'User', bold: true}, {text: 'email'}];
    flush();
    const topItems = queryAll<HTMLDivElement>(element, '.top-item');
    assert.equal(topItems.length, 2);
    assert.isTrue(topItems[0].classList.contains('bold-text'));
    assert.isFalse(topItems[1].classList.contains('bold-text'));
  });

  test('non link items', () => {
    const item0 = {name: 'item one', id: 'foo'};
    element.items = [item0, {name: 'item two', id: 'bar'}];
    const fooTapped = sinon.stub();
    const tapped = sinon.stub();
    element.addEventListener('tap-item-foo', fooTapped);
    element.addEventListener('tap-item', tapped);
    flush();
    MockInteractions.tap(
      queryAndAssert<HTMLSpanElement>(element, '.itemAction')
    );
    assert.isTrue(fooTapped.called);
    assert.isTrue(tapped.called);
    assert.deepEqual(tapped.lastCall.args[0].detail, item0);
  });

  test('disabled non link item', () => {
    element.items = [{name: 'item one', id: 'foo'}];
    element.disabledIds = ['foo'];

    const stub = sinon.stub();
    const tapped = sinon.stub();
    element.addEventListener('tap-item-foo', stub);
    element.addEventListener('tap-item', tapped);
    flush();
    MockInteractions.tap(
      queryAndAssert<HTMLSpanElement>(element, '.itemAction')
    );
    assert.isFalse(stub.called);
    assert.isFalse(tapped.called);
  });

  test('properly sets tooltips', () => {
    element.items = [
      {name: 'item one', id: 'foo', tooltip: 'hello'},
      {name: 'item two', id: 'bar'},
    ];
    element.disabledIds = [];
    flush();
    const tooltipContents = queryAll<GrTooltipContent>(
      element,
      'iron-dropdown li gr-tooltip-content'
    );
    assert.equal(tooltipContents.length, 2);
    assert.isTrue(tooltipContents[0].hasTooltip);
    assert.equal(tooltipContents[0].getAttribute('title'), 'hello');
    assert.isFalse(tooltipContents[1].hasTooltip);
  });

  suite('keyboard navigation', () => {
    setup(() => {
      element.items = [
        {name: 'item one', id: 'foo'},
        {name: 'item two', id: 'bar'},
      ];
      flush();
    });

    test('down', () => {
      const stub = sinon.stub(element.cursor, 'next');
      assert.isFalse(element.$.dropdown.opened);
      MockInteractions.pressAndReleaseKeyOn(element, 40, null, 'ArrowDown');
      assert.isTrue(element.$.dropdown.opened);
      MockInteractions.pressAndReleaseKeyOn(element, 40, null, 'ArrowDown');
      assert.isTrue(stub.called);
    });

    test('up', () => {
      const stub = sinon.stub(element.cursor, 'previous');
      assert.isFalse(element.$.dropdown.opened);
      MockInteractions.pressAndReleaseKeyOn(element, 38, null, 'ArrowUp');
      assert.isTrue(element.$.dropdown.opened);
      MockInteractions.pressAndReleaseKeyOn(element, 38, null, 'ArrowUp');
      assert.isTrue(stub.called);
    });

    test('enter/space', () => {
      // Because enter and space are handled by the same fn, we need only to
      // test one.
      assert.isFalse(element.$.dropdown.opened);
      MockInteractions.pressAndReleaseKeyOn(element, 32, null, ' ');
      assert.isTrue(element.$.dropdown.opened);

      const el = queryAndAssert<HTMLAnchorElement>(
        element.cursor.target as HTMLElement,
        ':not([hidden]) a'
      );
      const stub = sinon.stub(el, 'click');
      MockInteractions.pressAndReleaseKeyOn(element, 32, null, ' ');
      assert.isTrue(stub.called);
    });
  });
});
