/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../test/common-test-setup';
import {
  descendedFromClass,
  eventMatchesShortcut,
  getComputedStyleValue,
  getEventPath,
  Key,
  Modifier,
  querySelectorAll,
  shouldSuppress,
  strToClassName,
} from './dom-util';
import {mockPromise, pressKey, queryAndAssert} from '../test/test-utils';
import {fixture, assert} from '@open-wc/testing';
import {LitElement, html} from 'lit';
import {customElement} from 'lit/decorators.js';

/**
 * You might think that instead of passing in the callback with assertions as a
 * parameter that you could as well just `await keyEventOn()` and *then* run
 * your assertions. But at that point the event is not "hot" anymore, so most
 * likely you want to assert stuff about the event within the callback
 * parameter.
 */
function keyEventOn(
  el: HTMLElement,
  callback: (e: KeyboardEvent) => void,
  key = 'k'
): Promise<KeyboardEvent> {
  const promise = mockPromise<KeyboardEvent>();
  el.addEventListener('keydown', (e: KeyboardEvent) => {
    callback(e);
    promise.resolve(e);
  });
  pressKey(el, key);
  return promise;
}

@customElement('dom-util-test-element')
export class TestElement extends LitElement {
  override render() {
    return html`
      <div>
        <div class="a">
          <div class="b">
            <div class="c"></div>
          </div>
          <span class="ss"></span>
        </div>
        <span class="ss"></span>
      </div>
    `;
  }
}

async function createFixture() {
  return await fixture<HTMLElement>(html`
    <div id="test" class="a b c d">
      <a class="testBtn" style="color:red;"></a>
      <dom-util-test-element></dom-util-test-element>
      <span class="ss"></span>
    </div>
  `);
}

suite('dom-util tests', () => {
  suite('getEventPath', () => {
    test('empty event', () => {
      assert.equal(getEventPath(), '');
      assert.equal(getEventPath(undefined), '');
      assert.equal(getEventPath(new MouseEvent('click')), '');
    });

    test('event with fake path', () => {
      assert.equal(getEventPath(new MouseEvent('click')), '');
      const dd = document.createElement('dd');
      assert.equal(
        getEventPath({...new MouseEvent('click'), composedPath: () => [dd]}),
        'dd'
      );
    });

    test('event with fake complicated path', () => {
      const dd = document.createElement('dd');
      dd.setAttribute('id', 'test');
      dd.className = 'a b';
      const divNode = document.createElement('DIV');
      divNode.id = 'test2';
      divNode.className = 'a b c';
      assert.equal(
        getEventPath({
          ...new MouseEvent('click'),
          composedPath: () => [dd, divNode],
        }),
        'div#test2.a.b.c>dd#test.a.b'
      );
    });

    test('event with fake target', () => {
      const fakeTargetParent1 = document.createElement('dd');
      fakeTargetParent1.setAttribute('id', 'test');
      fakeTargetParent1.className = 'a b';
      const fakeTargetParent2 = document.createElement('DIV');
      fakeTargetParent2.id = 'test2';
      fakeTargetParent2.className = 'a b c';
      fakeTargetParent2.appendChild(fakeTargetParent1);
      const fakeTarget = document.createElement('SPAN');
      fakeTargetParent1.appendChild(fakeTarget);
      assert.equal(
        getEventPath({
          ...new MouseEvent('click'),
          composedPath: () => [],
          target: fakeTarget,
        }),
        'div#test2.a.b.c>dd#test.a.b>span'
      );
    });

    test('event with real click', async () => {
      const element = await createFixture();
      const aLink = queryAndAssert<HTMLAnchorElement>(element, 'a');
      let path;
      aLink.addEventListener('click', (e: Event) => {
        path = getEventPath(e as MouseEvent);
      });
      aLink.click();
      assert.equal(path, 'html>body>div>div#test.a.b.c.d>a.testBtn');
    });
  });

  suite('querySelector and querySelectorAll', () => {
    test('query cross shadow dom', async () => {
      const element = await createFixture();
      const theFirstEl = queryAndAssert(element, '.ss');
      const allEls = querySelectorAll(element, '.ss');
      assert.equal(allEls.length, 3);
      assert.equal(theFirstEl, allEls[0]);
    });
  });

  suite('getComputedStyleValue', () => {
    test('color style', async () => {
      const element = await createFixture();
      const testBtn = queryAndAssert(element, '.testBtn');
      assert.equal(getComputedStyleValue('color', testBtn), 'rgb(255, 0, 0)');
    });
  });

  suite('descendedFromClass', () => {
    test('descends from itself', async () => {
      const element = await createFixture();
      const testEl = queryAndAssert(element, 'dom-util-test-element');
      assert.isTrue(descendedFromClass(queryAndAssert(testEl, '.c'), 'c'));
      assert.isTrue(descendedFromClass(queryAndAssert(testEl, '.b'), 'b'));
      assert.isTrue(descendedFromClass(queryAndAssert(testEl, '.a'), 'a'));
    });

    test('.c in .b in .a', async () => {
      const element = await createFixture();
      const testEl = queryAndAssert(element, 'dom-util-test-element');
      const a = queryAndAssert(testEl, '.a');
      const b = queryAndAssert(testEl, '.b');
      const c = queryAndAssert(testEl, '.c');
      assert.isTrue(descendedFromClass(a, 'a'));
      assert.isTrue(descendedFromClass(b, 'a'));
      assert.isTrue(descendedFromClass(c, 'a'));
      assert.isFalse(descendedFromClass(a, 'b'));
      assert.isTrue(descendedFromClass(b, 'b'));
      assert.isTrue(descendedFromClass(c, 'b'));
      assert.isFalse(descendedFromClass(a, 'c'));
      assert.isFalse(descendedFromClass(b, 'c'));
      assert.isTrue(descendedFromClass(c, 'c'));
    });

    test('stops at shadow root', async () => {
      const element = await createFixture();
      const testEl = queryAndAssert(element, 'dom-util-test-element');
      const a = queryAndAssert(testEl, '.a');
      // div.d is a parent of testEl, but `descendedFromClass` does not cross
      // the shadow root boundary of <dom-util-test-element>. So div.a inside
      // the shadow root is not considered to descend from div.d outside of it.
      assert.isFalse(descendedFromClass(a, 'd'));
    });

    test('stops at stop element', async () => {
      const element = await createFixture();
      const testEl = queryAndAssert(element, 'dom-util-test-element');
      assert.isFalse(
        descendedFromClass(
          queryAndAssert(testEl, '.c'),
          'a',
          queryAndAssert(testEl, '.b')
        )
      );
    });
  });

  suite('strToClassName', () => {
    test('basic tests', () => {
      assert.equal(strToClassName(''), 'generated_');
      assert.equal(strToClassName('11'), 'generated_11');
      assert.equal(strToClassName('0.123'), 'generated_0_123');
      assert.equal(strToClassName('0.123', 'prefix_'), 'prefix_0_123');
      assert.equal(strToClassName('0>123', 'prefix_'), 'prefix_0_123');
      assert.equal(strToClassName('0<123', 'prefix_'), 'prefix_0_123');
      assert.equal(strToClassName('0+1+23', 'prefix_'), 'prefix_0_1_23');
    });
  });

  suite('eventMatchesShortcut', () => {
    test('basic tests', () => {
      const a = new KeyboardEvent('keydown', {key: 'a'});
      const b = new KeyboardEvent('keydown', {key: 'B'});
      assert.isTrue(eventMatchesShortcut(a, {key: 'a'}));
      assert.isFalse(eventMatchesShortcut(a, {key: 'B'}));
      assert.isFalse(eventMatchesShortcut(b, {key: 'a'}));
      assert.isTrue(eventMatchesShortcut(b, {key: 'B'}));
    });

    test('check modifiers for a', () => {
      const e = new KeyboardEvent('keydown', {key: 'a'});
      const s = {key: 'a'};
      assert.isTrue(eventMatchesShortcut(e, s));

      const eAlt = new KeyboardEvent('keydown', {key: 'a', altKey: true});
      const sAlt = {key: 'a', modifiers: [Modifier.ALT_KEY]};
      assert.isFalse(eventMatchesShortcut(eAlt, s));
      assert.isFalse(eventMatchesShortcut(e, sAlt));
      const eCtrl = new KeyboardEvent('keydown', {key: 'a', ctrlKey: true});
      const sCtrl = {key: 'a', modifiers: [Modifier.CTRL_KEY]};
      assert.isFalse(eventMatchesShortcut(eCtrl, s));
      assert.isFalse(eventMatchesShortcut(e, sCtrl));
      const eMeta = new KeyboardEvent('keydown', {key: 'a', metaKey: true});
      const sMeta = {key: 'a', modifiers: [Modifier.META_KEY]};
      assert.isFalse(eventMatchesShortcut(eMeta, s));
      assert.isFalse(eventMatchesShortcut(e, sMeta));

      // Do NOT check SHIFT for alphanum keys.
      const eShift = new KeyboardEvent('keydown', {key: 'a', shiftKey: true});
      const sShift = {key: 'a', modifiers: [Modifier.SHIFT_KEY]};
      assert.isTrue(eventMatchesShortcut(eShift, s));
      assert.isTrue(eventMatchesShortcut(e, sShift));
    });

    test('check modifiers for Enter', () => {
      const e = new KeyboardEvent('keydown', {key: 'Enter'});
      const s = {key: 'Enter'};
      assert.isTrue(eventMatchesShortcut(e, s));

      const eAlt = new KeyboardEvent('keydown', {key: 'Enter', altKey: true});
      const sAlt = {key: 'Enter', modifiers: [Modifier.ALT_KEY]};
      assert.isFalse(eventMatchesShortcut(eAlt, s));
      assert.isFalse(eventMatchesShortcut(e, sAlt));
      const eCtrl = new KeyboardEvent('keydown', {key: 'Enter', ctrlKey: true});
      const sCtrl = {key: 'Enter', modifiers: [Modifier.CTRL_KEY]};
      assert.isFalse(eventMatchesShortcut(eCtrl, s));
      assert.isFalse(eventMatchesShortcut(e, sCtrl));
      const eMeta = new KeyboardEvent('keydown', {key: 'Enter', metaKey: true});
      const sMeta = {key: 'Enter', modifiers: [Modifier.META_KEY]};
      assert.isFalse(eventMatchesShortcut(eMeta, s));
      assert.isFalse(eventMatchesShortcut(e, sMeta));
      const eShift = new KeyboardEvent('keydown', {
        key: 'Enter',
        shiftKey: true,
      });
      const sShift = {key: 'Enter', modifiers: [Modifier.SHIFT_KEY]};
      assert.isFalse(eventMatchesShortcut(eShift, s));
      assert.isFalse(eventMatchesShortcut(e, sShift));
    });

    test('check modifiers for [', () => {
      const e = new KeyboardEvent('keydown', {key: '['});
      const s = {key: '['};
      assert.isTrue(eventMatchesShortcut(e, s));

      const eCtrl = new KeyboardEvent('keydown', {key: '[', ctrlKey: true});
      const sCtrl = {key: '[', modifiers: [Modifier.CTRL_KEY]};
      assert.isFalse(eventMatchesShortcut(eCtrl, s));
      assert.isFalse(eventMatchesShortcut(e, sCtrl));
      const eMeta = new KeyboardEvent('keydown', {key: '[', metaKey: true});
      const sMeta = {key: '[', modifiers: [Modifier.META_KEY]};
      assert.isFalse(eventMatchesShortcut(eMeta, s));
      assert.isFalse(eventMatchesShortcut(e, sMeta));

      // Do NOT check SHIFT and ALT for special chars like [.
      const eAlt = new KeyboardEvent('keydown', {key: '[', altKey: true});
      const sAlt = {key: '[', modifiers: [Modifier.ALT_KEY]};
      assert.isTrue(eventMatchesShortcut(eAlt, s));
      assert.isTrue(eventMatchesShortcut(e, sAlt));
      const eShift = new KeyboardEvent('keydown', {
        key: '[',
        shiftKey: true,
      });
      const sShift = {key: '[', modifiers: [Modifier.SHIFT_KEY]};
      assert.isTrue(eventMatchesShortcut(eShift, s));
      assert.isTrue(eventMatchesShortcut(e, sShift));
    });
  });

  suite('shouldSuppress', () => {
    test('do not suppress shortcut event from <div>', async () => {
      await keyEventOn(document.createElement('div'), e => {
        assert.isFalse(shouldSuppress(e));
      });
    });

    test('suppress shortcut event from <input>', async () => {
      await keyEventOn(document.createElement('input'), e => {
        assert.isTrue(shouldSuppress(e));
      });
    });

    test('suppress shortcut event from <textarea>', async () => {
      await keyEventOn(document.createElement('textarea'), e => {
        assert.isTrue(shouldSuppress(e));
      });
    });

    test('do not suppress shortcut event from checkbox <input>', async () => {
      const inputEl = document.createElement('input');
      inputEl.setAttribute('type', 'checkbox');
      await keyEventOn(inputEl, e => {
        assert.isFalse(shouldSuppress(e));
      });
    });

    test('suppress "enter" shortcut event from <gr-button>', async () => {
      await keyEventOn(
        document.createElement('gr-button'),
        e => assert.isTrue(shouldSuppress(e)),
        Key.ENTER
      );
    });

    test('suppress "enter" shortcut event from <a>', async () => {
      await keyEventOn(document.createElement('a'), e => {
        assert.isFalse(shouldSuppress(e));
      });
      await keyEventOn(
        document.createElement('a'),
        e => assert.isTrue(shouldSuppress(e)),
        Key.ENTER
      );
    });
  });
});
