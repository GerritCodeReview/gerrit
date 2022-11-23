/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import '../../../test/common-test-setup';
import {GrAnnotation} from './gr-annotation';
import {
  getSanitizeDOMValue,
  setSanitizeDOMValue,
} from '@polymer/polymer/lib/utils/settings';
import {assert, fixture, html} from '@open-wc/testing';

suite('annotation', () => {
  let str: string;
  let parent: HTMLDivElement;
  let textNode: Text;

  setup(async () => {
    parent = await fixture(
      html`
        <div>Lorem ipsum dolor sit amet, suspendisse inceptos vehicula</div>
      `
    );
    textNode = parent.childNodes[0] as Text;
    str = textNode.textContent!;
  });

  test('_annotateText Case 1', () => {
    GrAnnotation._annotateText(textNode, 0, str.length, 'foobar');

    assert.equal(parent.childNodes.length, 1);
    assert.instanceOf(parent.childNodes[0], HTMLElement);
    const firstChild = parent.childNodes[0] as HTMLElement;
    assert.equal(firstChild.className, 'foobar');
    assert.instanceOf(firstChild.childNodes[0], Text);
    assert.equal(firstChild.childNodes[0].textContent, str);
  });

  test('_annotateText Case 2', () => {
    const length = 12;
    const substr = str.substr(0, length);
    const remainder = str.substr(length);

    GrAnnotation._annotateText(textNode, 0, length, 'foobar');

    assert.equal(parent.childNodes.length, 2);

    assert.instanceOf(parent.childNodes[0], HTMLElement);
    const firstChild = parent.childNodes[0] as HTMLElement;
    assert.equal(firstChild.className, 'foobar');
    assert.instanceOf(firstChild.childNodes[0], Text);
    assert.equal(firstChild.childNodes[0].textContent, substr);

    assert.instanceOf(parent.childNodes[1], Text);
    assert.equal(parent.childNodes[1].textContent, remainder);
  });

  test('_annotateText Case 3', () => {
    const index = 12;
    const length = str.length - index;
    const remainder = str.substr(0, index);
    const substr = str.substr(index);

    GrAnnotation._annotateText(textNode, index, length, 'foobar');

    assert.equal(parent.childNodes.length, 2);

    assert.instanceOf(parent.childNodes[0], Text);
    assert.equal(parent.childNodes[0].textContent, remainder);

    const secondChild = parent.childNodes[1] as HTMLElement;
    assert.instanceOf(secondChild, HTMLElement);
    assert.equal(secondChild.className, 'foobar');
    assert.instanceOf(secondChild.childNodes[0], Text);
    assert.equal(secondChild.childNodes[0].textContent, substr);
  });

  test('_annotateText Case 4', () => {
    const index = str.indexOf('dolor');
    const length = 'dolor '.length;

    const remainderPre = str.substr(0, index);
    const substr = str.substr(index, length);
    const remainderPost = str.substr(index + length);

    GrAnnotation._annotateText(textNode, index, length, 'foobar');

    assert.equal(parent.childNodes.length, 3);

    assert.instanceOf(parent.childNodes[0], Text);
    assert.equal(parent.childNodes[0].textContent, remainderPre);

    const secondChild = parent.childNodes[1] as HTMLElement;
    assert.instanceOf(secondChild, HTMLElement);
    assert.equal(secondChild.className, 'foobar');
    assert.instanceOf(secondChild.childNodes[0], Text);
    assert.equal(secondChild.childNodes[0].textContent, substr);

    assert.instanceOf(parent.childNodes[2], Text);
    assert.equal(parent.childNodes[2].textContent, remainderPost);
  });

  test('_annotateElement design doc example', () => {
    const layers = ['amet, ', 'inceptos ', 'amet, ', 'et, suspendisse ince'];

    // Apply the layers successively.
    layers.forEach((layer, i) => {
      GrAnnotation.annotateElement(
        parent,
        str.indexOf(layer),
        layer.length,
        `layer-${i + 1}`
      );
    });

    assert.equal(parent.textContent, str);

    // Layer 1:
    const layer1 = parent.querySelectorAll<HTMLElement>('.layer-1');
    assert.equal(layer1.length, 1);
    assert.equal(layer1[0].textContent, layers[0]);
    assert.equal(layer1[0].parentElement, parent);

    // Layer 2:
    const layer2 = parent.querySelectorAll<HTMLElement>('.layer-2');
    assert.equal(layer2.length, 1);
    assert.equal(layer2[0].textContent, layers[1]);
    assert.equal(layer2[0].parentElement, parent);

    // Layer 3:
    const layer3 = parent.querySelectorAll<HTMLElement>('.layer-3');
    assert.equal(layer3.length, 1);
    assert.equal(layer3[0].textContent, layers[2]);
    assert.equal(layer3[0].parentElement, layer1[0]);

    // Layer 4:
    const layer4 = parent.querySelectorAll<HTMLElement>('.layer-4');
    assert.equal(layer4.length, 3);

    assert.equal(layer4[0].textContent, 'et, ');
    assert.equal(layer4[0].parentElement, layer3[0]);

    assert.equal(layer4[1].textContent, 'suspendisse ');
    assert.equal(layer4[1].parentElement, parent);

    assert.equal(layer4[2].textContent, 'ince');
    assert.equal(layer4[2].parentElement, layer2[0]);

    assert.equal(
      [
        layer4[0].textContent,
        layer4[1].textContent,
        layer4[2].textContent,
      ].join(''),
      layers[3]
    );
  });

  test('splitTextNode', () => {
    const helloString = 'hello';
    const asciiString = 'ASCII';
    const unicodeString = 'Unic💢de';

    let node;
    let tail;

    // Non-unicode path:
    node = document.createTextNode(helloString + asciiString);
    tail = GrAnnotation.splitTextNode(node, helloString.length);
    assert(node.textContent, helloString);
    assert(tail.textContent, asciiString);

    // Unicdoe path:
    node = document.createTextNode(helloString + unicodeString);
    tail = GrAnnotation.splitTextNode(node, helloString.length);
    assert(node.textContent, helloString);
    assert(tail.textContent, unicodeString);
  });

  suite('annotateWithElement', () => {
    const fullText = '01234567890123456789';
    let mockSanitize: sinon.SinonSpy;
    let originalSanitizeDOMValue: (
      p0: any,
      p1: string,
      p2: string,
      p3: Node | null
    ) => any;

    setup(() => {
      setSanitizeDOMValue(p0 => p0);
      originalSanitizeDOMValue = getSanitizeDOMValue()!;
      assert.isDefined(originalSanitizeDOMValue);
      mockSanitize = sinon.spy(originalSanitizeDOMValue);
      setSanitizeDOMValue(mockSanitize);
    });

    teardown(() => {
      setSanitizeDOMValue(originalSanitizeDOMValue);
    });

    test('annotates when fully contained', () => {
      const length = 10;
      const container = document.createElement('div');
      container.textContent = fullText;
      GrAnnotation.annotateWithElement(container, 1, length, {
        tagName: 'test-wrapper',
      });

      assert.equal(
        container.innerHTML,
        '0<test-wrapper>1234567890</test-wrapper>123456789'
      );
    });

    test('annotates when spanning multiple nodes', () => {
      const length = 10;
      const container = document.createElement('div');
      container.textContent = fullText;
      GrAnnotation.annotateElement(container, 5, length, 'testclass');
      GrAnnotation.annotateWithElement(container, 1, length, {
        tagName: 'test-wrapper',
      });

      assert.equal(
        container.innerHTML,
        '0' +
          '<test-wrapper>' +
          '1234' +
          '<hl class="testclass">567890</hl>' +
          '</test-wrapper>' +
          '<hl class="testclass">1234</hl>' +
          '56789'
      );
    });

    test('annotates text node', () => {
      const length = 10;
      const container = document.createElement('div');
      container.textContent = fullText;
      GrAnnotation.annotateWithElement(container.childNodes[0], 1, length, {
        tagName: 'test-wrapper',
      });

      assert.equal(
        container.innerHTML,
        '0<test-wrapper>1234567890</test-wrapper>123456789'
      );
    });

    test('handles zero-length nodes', () => {
      const container = document.createElement('div');
      container.appendChild(document.createTextNode('0123456789'));
      container.appendChild(document.createElement('span'));
      container.appendChild(document.createTextNode('0123456789'));
      GrAnnotation.annotateWithElement(container, 1, 10, {
        tagName: 'test-wrapper',
      });

      assert.equal(
        container.innerHTML,
        '0<test-wrapper>123456789<span></span>0</test-wrapper>123456789'
      );
    });

    test('handles comment nodes', () => {
      const container = document.createElement('div');
      container.appendChild(document.createComment('comment1'));
      container.appendChild(document.createTextNode('0123456789'));
      container.appendChild(document.createComment('comment2'));
      container.appendChild(document.createElement('span'));
      container.appendChild(document.createTextNode('0123456789'));
      GrAnnotation.annotateWithElement(container, 1, 10, {
        tagName: 'test-wrapper',
      });

      assert.equal(
        container.innerHTML,
        '<!--comment1-->' +
          '0<test-wrapper>123456789' +
          '<!--comment2-->' +
          '<span></span>0</test-wrapper>123456789'
      );
    });

    test('sets sanitized attributes', () => {
      const container = document.createElement('div');
      container.textContent = fullText;
      const attributes = {
        href: 'foo',
        'data-foo': 'bar',
        class: 'hello world',
      };
      GrAnnotation.annotateWithElement(container, 1, length, {
        tagName: 'test-wrapper',
        attributes,
      });
      assert(
        mockSanitize.calledWith(
          'foo',
          'href',
          'attribute',
          sinon.match.instanceOf(Element)
        )
      );
      assert(
        mockSanitize.calledWith(
          'bar',
          'data-foo',
          'attribute',
          sinon.match.instanceOf(Element)
        )
      );
      assert(
        mockSanitize.calledWith(
          'hello world',
          'class',
          'attribute',
          sinon.match.instanceOf(Element)
        )
      );
      const el = container.querySelector('test-wrapper')!;
      assert.equal(el.getAttribute('href'), 'foo');
      assert.equal(el.getAttribute('data-foo'), 'bar');
      assert.equal(el.getAttribute('class'), 'hello world');
    });
  });

  suite('getStringLength', () => {
    test('ASCII', () => {
      assert.equal(GrAnnotation.getStringLength('ASCII'), 5);
    });

    test('Unicode', () => {
      assert.equal(GrAnnotation.getStringLength('Unic💢de'), 7);
      assert.equal(GrAnnotation.getStringLength('💢💢'), 2);
    });
  });
});
