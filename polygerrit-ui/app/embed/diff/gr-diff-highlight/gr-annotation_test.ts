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

    assert.equal(parent.textContent, str);
    assert.equal(
      parent.innerHTML,
      '<hl class="foobar">Lorem ipsum dolor sit amet, suspendisse inceptos vehicula</hl>'
    );
  });

  test('_annotateText Case 2', () => {
    GrAnnotation._annotateText(textNode, 0, 12, 'foobar');

    assert.equal(parent.textContent, str);
    assert.equal(
      parent.innerHTML,
      '<hl class="foobar">Lorem ipsum </hl>dolor sit amet, suspendisse inceptos vehicula'
    );
  });

  test('_annotateText Case 3', () => {
    GrAnnotation._annotateText(textNode, 12, str.length - 12, 'foobar');

    assert.equal(parent.textContent, str);
    assert.equal(
      parent.innerHTML,
      'Lorem ipsum <hl class="foobar">dolor sit amet, suspendisse inceptos vehicula</hl>'
    );
  });

  test('_annotateText Case 4', () => {
    const index = str.indexOf('dolor');
    const length = 'dolor '.length;

    GrAnnotation._annotateText(textNode, index, length, 'foobar');

    assert.equal(parent.textContent, str);
    assert.equal(
      parent.innerHTML,
      'Lorem ipsum <hl class="foobar">dolor </hl>sit amet, suspendisse inceptos vehicula'
    );
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
    assert.equal(
      parent.innerHTML,
      'Lorem ipsum dolor sit <hl class="layer-1"><hl class="layer-3">am<hl class="layer-4">et, </hl></hl></hl><hl class="layer-4">suspendisse </hl><hl class="layer-2"><hl class="layer-4">ince</hl>ptos </hl>vehicula'
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
    test('ASCII characters are counted correctly', () => {
      assert.equal(GrAnnotation.getStringLength('ASCII'), 5);
    });

    test('Unicode surrogate pairs count as one symbol', () => {
      assert.equal(GrAnnotation.getStringLength('Unic💢de'), 7);
      assert.equal(GrAnnotation.getStringLength('💢💢'), 2);
    });

    test('Grapheme clusters count as multiple symbols', () => {
      assert.equal(GrAnnotation.getStringLength('man\u0303ana'), 7); // mañana
      assert.equal(GrAnnotation.getStringLength('q\u0307\u0323'), 3); // q̣̇
    });
  });
});
