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

const basicFixture = fixtureFromTemplate(html`
<div>Lorem ipsum dolor sit amet, suspendisse inceptos vehicula</div>
`);

import '../../../test/common-test-setup-karma.js';
import {GrAnnotation} from './gr-annotation.js';
import {sanitizeDOMValue, setSanitizeDOMValue} from '@polymer/polymer/lib/utils/settings.js';
import {html} from '@polymer/polymer/lib/utils/html-tag.js';

suite('annotation', () => {
  let str;
  let parent;
  let textNode;

  setup(() => {
    parent = basicFixture.instantiate();
    textNode = parent.childNodes[0];
    str = textNode.textContent;
  });

  test('_annotateText Case 1', () => {
    GrAnnotation._annotateText(textNode, 0, str.length, 'foobar');

    assert.equal(parent.childNodes.length, 1);
    assert.instanceOf(parent.childNodes[0], HTMLElement);
    assert.equal(parent.childNodes[0].className, 'foobar');
    assert.instanceOf(parent.childNodes[0].childNodes[0], Text);
    assert.equal(parent.childNodes[0].childNodes[0].textContent, str);
  });

  test('_annotateText Case 2', () => {
    const length = 12;
    const substr = str.substr(0, length);
    const remainder = str.substr(length);

    GrAnnotation._annotateText(textNode, 0, length, 'foobar');

    assert.equal(parent.childNodes.length, 2);

    assert.instanceOf(parent.childNodes[0], HTMLElement);
    assert.equal(parent.childNodes[0].className, 'foobar');
    assert.instanceOf(parent.childNodes[0].childNodes[0], Text);
    assert.equal(parent.childNodes[0].childNodes[0].textContent, substr);

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

    assert.instanceOf(parent.childNodes[1], HTMLElement);
    assert.equal(parent.childNodes[1].className, 'foobar');
    assert.instanceOf(parent.childNodes[1].childNodes[0], Text);
    assert.equal(parent.childNodes[1].childNodes[0].textContent, substr);
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

    assert.instanceOf(parent.childNodes[1], HTMLElement);
    assert.equal(parent.childNodes[1].className, 'foobar');
    assert.instanceOf(parent.childNodes[1].childNodes[0], Text);
    assert.equal(parent.childNodes[1].childNodes[0].textContent, substr);

    assert.instanceOf(parent.childNodes[2], Text);
    assert.equal(parent.childNodes[2].textContent, remainderPost);
  });

  test('_annotateElement design doc example', () => {
    const layers = [
      'amet, ',
      'inceptos ',
      'amet, ',
      'et, suspendisse ince',
    ];

    // Apply the layers successively.
    layers.forEach((layer, i) => {
      GrAnnotation.annotateElement(
          parent, str.indexOf(layer), layer.length, `layer-${i + 1}`);
    });

    assert.equal(parent.textContent, str);

    // Layer 1:
    const layer1 = parent.querySelectorAll('.layer-1');
    assert.equal(layer1.length, 1);
    assert.equal(layer1[0].textContent, layers[0]);
    assert.equal(layer1[0].parentElement, parent);

    // Layer 2:
    const layer2 = parent.querySelectorAll('.layer-2');
    assert.equal(layer2.length, 1);
    assert.equal(layer2[0].textContent, layers[1]);
    assert.equal(layer2[0].parentElement, parent);

    // Layer 3:
    const layer3 = parent.querySelectorAll('.layer-3');
    assert.equal(layer3.length, 1);
    assert.equal(layer3[0].textContent, layers[2]);
    assert.equal(layer3[0].parentElement, layer1[0]);

    // Layer 4:
    const layer4 = parent.querySelectorAll('.layer-4');
    assert.equal(layer4.length, 3);

    assert.equal(layer4[0].textContent, 'et, ');
    assert.equal(layer4[0].parentElement, layer3[0]);

    assert.equal(layer4[1].textContent, 'suspendisse ');
    assert.equal(layer4[1].parentElement, parent);

    assert.equal(layer4[2].textContent, 'ince');
    assert.equal(layer4[2].parentElement, layer2[0]);

    assert.equal(layer4[0].textContent +
        layer4[1].textContent +
        layer4[2].textContent,
    layers[3]);
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

  suite('splitNodes', () => {
    test('0123456789', () => {
      const el = document.createElement('div');
      el.innerHTML = '0123456789';
      const matchingNodes = GrAnnotation.splitNodes(el.childNodes, 3, 5);
      assert.equal(el.innerHTML, '0123456789');
      assert.equal(matchingNodes.length, 1);
      assert.equal(matchingNodes[0].textContent, '34567');
    });

    test('01<span>23</span>45', () => {
      const el = document.createElement('div');
      el.innerHTML = '01<span>23</span>45';
      const matchingNodes = GrAnnotation.splitNodes(el.childNodes, 1, 4);
      assert.equal(el.innerHTML, '01<span>23</span>45');
      assert.equal(matchingNodes.length, 3);
      assert.equal(matchingNodes[0].textContent, '1');
      assert.equal(matchingNodes[1].textContent, '23');
      assert.equal(matchingNodes[2].textContent, '4');
    });

    test('01<span>234</span>56', () => {
      const el = document.createElement('div');
      el.innerHTML = '01<span>234</span>56';
      const matchingNodes = GrAnnotation.splitNodes(el.childNodes, 3, 1);
      assert.equal(el.innerHTML, '01<span>2</span><span>3</span><span>4</span>56');
      assert.equal(matchingNodes.length, 1);
      assert.equal(matchingNodes[0].textContent, '3');
    });

    test('01<span>23<span>45</span>67</span>89', () => {
      const el = document.createElement('div');
      el.innerHTML = '01<span>23<span>45</span>67</span>89';
      const matchingNodes = GrAnnotation.splitNodes(el.childNodes, 1, 8);
      assert.equal(el.innerHTML, '01<span>23<span>45</span>67</span>89');
      assert.equal(matchingNodes.length, 3);
      assert.equal(matchingNodes[0].textContent, '1');
      assert.equal(matchingNodes[1].textContent, '234567');
      assert.equal(matchingNodes[2].textContent, '8');
    });
  });

  suite('simplifyNewLineNodes', () => {
    test('no modification', () => {
      const el = document.createElement('div');
      el.innerHTML = 'xx<span>y\ny<span>z\nz</span>y\ny</span>xx';
      GrAnnotation.simplifyNewLineNodes(el);
      assert.equal(el.innerHTML, 'xx<span>y\ny<span>z\nz</span>y\ny</span>xx');
    });

    test('removes spans', () => {
      const el = document.createElement('div');
      el.innerHTML = 'xx<span>\n</span><span>\n</span><span>\n</span>xx';
      GrAnnotation.simplifyNewLineNodes(el);
      assert.equal(el.innerHTML, 'xx\n\n\nxx');
    });

    test('removes double spans', () => {
      const el = document.createElement('div');
      el.innerHTML = 'xx<span><span><span>\n</span></span></span>xx';
      GrAnnotation.simplifyNewLineNodes(el);
      assert.equal(el.innerHTML, 'xx\nxx');
    });
  });

  suite('splitNode', () => {
    test('split simple span', () => {
      const el = document.createElement('div');
      el.innerHTML = 'xx<span>yyyy</span>zz';
      const node = el.querySelector('span');
      GrAnnotation.splitNode(node, 2);
      assert.equal(el.innerHTML, 'xx<span>yy</span><span>yy</span>zz');
    });

    test('split span with line break before and after', () => {
      const el = document.createElement('div');
      el.innerHTML = 'xx<span>yy\nyy</span>zz';
      const node = el.querySelector('span');
      GrAnnotation.splitNode(node, 3);
      GrAnnotation.splitNode(node, 2);
      assert.equal(el.innerHTML, 'xx<span>yy</span><span>\n</span><span>yy</span>zz');
    });
  });

  suite.only('splitAllNodesAtLineBreaks', () => {
    test('<span>\\n</span>', () => {
      const el = document.createElement('div');
      el.innerHTML = '<span>\n</span>';
      GrAnnotation.splitAllNodesAtLineBreaks(el);
      assert.equal(el.innerHTML, '<span>\n</span>');
    });

    test('<span>\\n\\n\\n</span>', () => {
      const el = document.createElement('div');
      el.innerHTML = '<span>\n\n\n</span>';
      GrAnnotation.splitAllNodesAtLineBreaks(el);
      assert.equal(el.innerHTML, '<span>\n</span><span>\n</span><span>\n</span>');
      GrAnnotation.simplifyNewLineNodes(el);
      assert.equal(el.innerHTML, '\n\n\n');
    });

    test('<span>a\\n</span>', () => {
      const el = document.createElement('div');
      el.innerHTML = '<span>a\n</span>';
      GrAnnotation.splitAllNodesAtLineBreaks(el);
      assert.equal(el.innerHTML, '<span>a</span><span>\n</span>');
      GrAnnotation.simplifyNewLineNodes(el);
      assert.equal(el.innerHTML, '<span>a</span>\n');
    });

    test('<span>\\na</span>', () => {
      const el = document.createElement('div');
      el.innerHTML = '<span>\na</span>';
      GrAnnotation.splitAllNodesAtLineBreaks(el);
      assert.equal(el.innerHTML, '<span>\n</span><span>a</span>');
    });

    test('split a span across two lines', () => {
      const el = document.createElement('div');
      el.innerHTML = 'xx<span>yy\nyy</span>zz';
      GrAnnotation.splitAllNodesAtLineBreaks(el);
      assert.equal(el.innerHTML, 'xx<span>yy</span><span>\n</span><span>yy</span>zz');
      GrAnnotation.simplifyNewLineNodes(el);
      assert.equal(el.innerHTML, 'xx<span>yy</span>\n<span>yy</span>zz');
    });

    test('split a span across 3 lines', () => {
      const el = document.createElement('div');
      el.innerHTML = 'xx<span>yy\n\nyy</span>zz';
      GrAnnotation.splitAllNodesAtLineBreaks(el);
      assert.equal(el.innerHTML, 'xx<span>yy</span><span>\n</span><span>\n</span><span>yy</span>zz');
      GrAnnotation.simplifyNewLineNodes(el);
      assert.equal(el.innerHTML, 'xx<span>yy</span>\n\n<span>yy</span>zz');
    });

    test('split a double span across 3 lines', () => {
      const el = document.createElement('div');
      el.innerHTML = 'xx<span><span>yy\n\nyy</span></span>zz';
      GrAnnotation.splitAllNodesAtLineBreaks(el);
      assert.equal(el.innerHTML, 'xx<span><span>yy</span></span><span><span>\n</span></span><span><span>\n</span></span><span><span>yy</span></span>zz');
      GrAnnotation.simplifyNewLineNodes(el);
      assert.equal(el.innerHTML, 'xx<span><span>yy</span></span>\n\n<span><span>yy</span></span>zz');
    });

    test('split something really complicated', () => {
      const el = document.createElement('div');
      el.innerHTML = 'line1:111\nline2:<span>222\nline3:<span>333\nline4:</span>444\nline5:</span>555';
      GrAnnotation.splitAllNodesAtLineBreaks(el);
      GrAnnotation.simplifyNewLineNodes(el);
      assert.equal(el.innerHTML, 'line1:111\nline2:<span>222</span>\n<span>line3:<span>333</span></span>\n<span><span>line4:</span>444</span>\n<span>line5:</span>555');
    });
  });

  suite('annotateWithElement', () => {
    const fullText = '01234567890123456789';
    let mockSanitize;
    let originalSanitizeDOMValue;

    setup(() => {
      originalSanitizeDOMValue = sanitizeDOMValue;
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
      GrAnnotation.annotateWithElement(
          container, 1, length, {tagName: 'test-wrapper'});

      assert.equal(
          container.innerHTML,
          '0<test-wrapper>1234567890</test-wrapper>123456789');
    });

    test('annotates when spanning multiple nodes', () => {
      const length = 10;
      const container = document.createElement('div');
      container.textContent = fullText;
      GrAnnotation.annotateElement(container, 5, length, 'testclass');
      GrAnnotation.annotateWithElement(
          container, 1, length, {tagName: 'test-wrapper'});

      assert.equal(
          container.innerHTML,
          '0' +
          '<test-wrapper>' +
          '1234' +
          '<hl class="testclass">567890</hl>' +
          '</test-wrapper>' +
          '<hl class="testclass">1234</hl>' +
          '56789');
    });

    test('annotates text node', () => {
      const length = 10;
      const container = document.createElement('div');
      container.textContent = fullText;
      GrAnnotation.annotateWithElement(
          container.childNodes[0], 1, length, {tagName: 'test-wrapper'});

      assert.equal(
          container.innerHTML,
          '0<test-wrapper>1234567890</test-wrapper>123456789');
    });

    test('handles zero-length nodes', () => {
      const container = document.createElement('div');
      container.appendChild(document.createTextNode('0123456789'));
      container.appendChild(document.createElement('span'));
      container.appendChild(document.createTextNode('0123456789'));
      GrAnnotation.annotateWithElement(
          container, 1, 10, {tagName: 'test-wrapper'});

      assert.equal(
          container.innerHTML,
          '0<test-wrapper>123456789<span></span>0</test-wrapper>123456789');
    });

    test('handles comment nodes', () => {
      const container = document.createElement('div');
      container.appendChild(document.createComment('comment1'));
      container.appendChild(document.createTextNode('0123456789'));
      container.appendChild(document.createComment('comment2'));
      container.appendChild(document.createElement('span'));
      container.appendChild(document.createTextNode('0123456789'));
      GrAnnotation.annotateWithElement(
          container, 1, 10, {tagName: 'test-wrapper'});

      assert.equal(
          container.innerHTML,
          '<!--comment1-->' +
          '0<test-wrapper>123456789' +
          '<!--comment2-->' +
          '<span></span>0</test-wrapper>123456789');
    });

    test('sets sanitized attributes', () => {
      const container = document.createElement('div');
      container.textContent = fullText;
      const attributes = {
        'href': 'foo',
        'data-foo': 'bar',
        'class': 'hello world',
      };
      GrAnnotation.annotateWithElement(
          container, 1, length, {tagName: 'test-wrapper', attributes});
      assert(mockSanitize.calledWith(
          'foo', 'href', 'attribute', sinon.match.instanceOf(Element)));
      assert(mockSanitize.calledWith(
          'bar', 'data-foo', 'attribute', sinon.match.instanceOf(Element)));
      assert(mockSanitize.calledWith(
          'hello world',
          'class',
          'attribute',
          sinon.match.instanceOf(Element)));
      const el = container.querySelector('test-wrapper');
      assert.equal(el.getAttribute('href'), 'foo');
      assert.equal(el.getAttribute('data-foo'), 'bar');
      assert.equal(el.getAttribute('class'), 'hello world');
    });
  });
});

