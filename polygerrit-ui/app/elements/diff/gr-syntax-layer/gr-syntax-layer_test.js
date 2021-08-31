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
import {getMockDiffResponse} from '../../../test/mocks/diff-response.js';
import './gr-syntax-layer.js';
import {GrAnnotation} from '../gr-diff-highlight/gr-annotation.js';
import {GrDiffLine, GrDiffLineType} from '../gr-diff/gr-diff-line.js';
import {GrSyntaxLayer} from './gr-syntax-layer.js';

suite('gr-syntax-layer tests', () => {
  let diff;
  let element;
  const lineNumberEl = document.createElement('td');

  function getMockHLJS() {
    const html = '<span class="gr-diff gr-syntax gr-syntax-string">' +
        'ipsum</span>';
    return {
      configure() {},
      highlight(lang, line, ignore, state) {
        return {
          value: line.replace(/ipsum/, html),
          top: state === undefined ? 1 : state + 1,
        };
      },
      // Return something truthy because this method is used to check if the
      // language is supported.
      getLanguage(s) {
        return {};
      },
    };
  }

  setup(() => {
    element = new GrSyntaxLayer();
    diff = getMockDiffResponse();
    element.diff = diff;
  });

  teardown(() => {
    if (window.hljs) {
      delete window.hljs;
    }
  });

  test('annotate without range does nothing', () => {
    const annotationSpy = sinon.spy(GrAnnotation, 'annotateElement');
    const el = document.createElement('div');
    el.textContent = 'Etiam dui, blandit wisi.';
    const line = new GrDiffLine(GrDiffLineType.REMOVE);
    line.beforeNumber = 12;

    element.annotate(el, lineNumberEl, line);

    assert.isFalse(annotationSpy.called);
  });

  test('annotate with range applies it', () => {
    const str = 'Etiam dui, blandit wisi.';
    const start = 6;
    const length = 3;
    const className = 'foobar';

    const annotationSpy = sinon.spy(GrAnnotation, 'annotateElement');
    const el = document.createElement('div');
    el.textContent = str;
    const line = new GrDiffLine(GrDiffLineType.REMOVE);
    line.beforeNumber = 12;
    element.baseRanges[11] = [{
      start,
      length,
      className,
    }];

    element.annotate(el, lineNumberEl, line);

    assert.isTrue(annotationSpy.called);
    assert.equal(annotationSpy.lastCall.args[0], el);
    assert.equal(annotationSpy.lastCall.args[1], start);
    assert.equal(annotationSpy.lastCall.args[2], length);
    assert.equal(annotationSpy.lastCall.args[3], className);
    assert.isOk(el.querySelector('hl.' + className));
  });

  test('annotate with range but disabled does nothing', () => {
    const str = 'Etiam dui, blandit wisi.';
    const start = 6;
    const length = 3;
    const className = 'foobar';

    const annotationSpy = sinon.spy(GrAnnotation, 'annotateElement');
    const el = document.createElement('div');
    el.textContent = str;
    const line = new GrDiffLine(GrDiffLineType.REMOVE);
    line.beforeNumber = 12;
    element.baseRanges[11] = [{
      start,
      length,
      className,
    }];
    element.enabled = false;

    element.annotate(el, lineNumberEl, line);

    assert.isFalse(annotationSpy.called);
  });

  test('process on empty diff does nothing', async () => {
    element.diff = {
      meta_a: {content_type: 'application/json'},
      meta_b: {content_type: 'application/json'},
      content: [],
    };
    const processNextSpy = sinon.spy(element, '_processNextLine');

    await element.process();

    assert.isFalse(processNextSpy.called);
    assert.equal(element.baseRanges.length, 0);
    assert.equal(element.revisionRanges.length, 0);
  });

  test('process for unsupported languages does nothing', async () => {
    element.diff = {
      meta_a: {content_type: 'text/x+objective-cobol-plus-plus'},
      meta_b: {content_type: 'application/not-a-real-language'},
      content: [],
    };
    const processNextSpy = sinon.spy(element, '_processNextLine');

    await element.process();

    assert.isFalse(processNextSpy.called);
    assert.equal(element.baseRanges.length, 0);
    assert.equal(element.revisionRanges.length, 0);
  });

  test('process while disabled does nothing', async () => {
    const processNextSpy = sinon.spy(element, '_processNextLine');
    element.enabled = false;
    const loadHLJSSpy = sinon.spy(element, '_loadHLJS');

    await element.process();

    assert.isFalse(processNextSpy.called);
    assert.equal(element.baseRanges.length, 0);
    assert.equal(element.revisionRanges.length, 0);
    assert.isFalse(loadHLJSSpy.called);
  });

  test('process highlight ipsum', async () => {
    element.diff.meta_a.content_type = 'application/json';
    element.diff.meta_b.content_type = 'application/json';

    const mockHLJS = getMockHLJS();
    window.hljs = mockHLJS;
    const highlightSpy = sinon.spy(mockHLJS, 'highlight');
    const processNextSpy = sinon.spy(element, '_processNextLine');
    await element.process();

    const linesA = diff.meta_a.lines;
    const linesB = diff.meta_b.lines;

    assert.isTrue(processNextSpy.called);
    assert.equal(element.baseRanges.length, linesA);
    assert.equal(element.revisionRanges.length, linesB);

    assert.equal(highlightSpy.callCount, linesA + linesB);

    // The first line of both sides have a range.
    let ranges = [element.baseRanges[0], element.revisionRanges[0]];
    for (const range of ranges) {
      assert.equal(range.length, 1);
      assert.equal(range[0].className,
          'gr-diff gr-syntax gr-syntax-string');
      assert.equal(range[0].start, 'lorem '.length);
      assert.equal(range[0].length, 'ipsum'.length);
    }

    // There are no ranges from ll.1-12 on the left and ll.1-11 on the
    // right.
    ranges = element.baseRanges.slice(1, 12)
        .concat(element.revisionRanges.slice(1, 11));

    for (const range of ranges) {
      assert.equal(range.length, 0);
    }

    // There should be another pair of ranges on l.13 for the left and
    // l.12 for the right.
    ranges = [element.baseRanges[13], element.revisionRanges[12]];

    for (const range of ranges) {
      assert.equal(range.length, 1);
      assert.equal(range[0].className,
          'gr-diff gr-syntax gr-syntax-string');
      assert.equal(range[0].start, 32);
      assert.equal(range[0].length, 'ipsum'.length);
    }

    // The next group should have a similar instance on either side.

    let range = element.baseRanges[15];
    assert.equal(range.length, 1);
    assert.equal(range[0].className, 'gr-diff gr-syntax gr-syntax-string');
    assert.equal(range[0].start, 34);
    assert.equal(range[0].length, 'ipsum'.length);

    range = element.revisionRanges[14];
    assert.equal(range.length, 1);
    assert.equal(range[0].className, 'gr-diff gr-syntax gr-syntax-string');
    assert.equal(range[0].start, 35);
    assert.equal(range[0].length, 'ipsum'.length);
  });

  test('init calls cancel', () => {
    const cancelSpy = sinon.spy(element, 'cancel');
    element.init({content: []});
    assert.isTrue(cancelSpy.called);
  });

  test('_rangesFromElement no ranges', () => {
    const elem = document.createElement('span');
    elem.textContent = 'Etiam dui, blandit wisi.';
    const offset = 100;

    const result = element._rangesFromElement(elem, offset);

    assert.equal(result.length, 0);
  });

  test('_rangesFromElement single range', () => {
    const str0 = 'Etiam ';
    const str1 = 'dui, blandit';
    const str2 = ' wisi.';
    const className = 'gr-diff gr-syntax gr-syntax-string';
    const offset = 100;

    const elem = document.createElement('span');
    elem.appendChild(document.createTextNode(str0));
    const span = document.createElement('span');
    span.textContent = str1;
    span.className = className;
    elem.appendChild(span);
    elem.appendChild(document.createTextNode(str2));

    const result = element._rangesFromElement(elem, offset);

    assert.equal(result.length, 1);
    assert.equal(result[0].start, str0.length + offset);
    assert.equal(result[0].length, str1.length);
    assert.equal(result[0].className, className);
  });

  test('_rangesFromElement non-allowed', () => {
    const str0 = 'Etiam ';
    const str1 = 'dui, blandit';
    const str2 = ' wisi.';
    const className = 'not-in-the-safelist';
    const offset = 100;

    const elem = document.createElement('span');
    elem.appendChild(document.createTextNode(str0));
    const span = document.createElement('span');
    span.textContent = str1;
    span.className = className;
    elem.appendChild(span);
    elem.appendChild(document.createTextNode(str2));

    const result = element._rangesFromElement(elem, offset);

    assert.equal(result.length, 0);
  });

  test('_rangesFromElement milti range', () => {
    const str0 = 'Etiam ';
    const str1 = 'dui,';
    const str2 = ' blandit';
    const str3 = ' wisi.';
    const className = 'gr-diff gr-syntax gr-syntax-string';
    const offset = 100;

    const elem = document.createElement('span');
    elem.appendChild(document.createTextNode(str0));
    let span = document.createElement('span');
    span.textContent = str1;
    span.className = className;
    elem.appendChild(span);
    elem.appendChild(document.createTextNode(str2));
    span = document.createElement('span');
    span.textContent = str3;
    span.className = className;
    elem.appendChild(span);

    const result = element._rangesFromElement(elem, offset);

    assert.equal(result.length, 2);

    assert.equal(result[0].start, str0.length + offset);
    assert.equal(result[0].length, str1.length);
    assert.equal(result[0].className, className);

    assert.equal(result[1].start,
        str0.length + str1.length + str2.length + offset);
    assert.equal(result[1].length, str3.length);
    assert.equal(result[1].className, className);
  });

  test('_rangesFromElement nested range', () => {
    const str0 = 'Etiam ';
    const str1 = 'dui,';
    const str2 = ' blandit';
    const str3 = ' wisi.';
    const className = 'gr-diff gr-syntax gr-syntax-string';
    const offset = 100;

    const elem = document.createElement('span');
    elem.appendChild(document.createTextNode(str0));
    const span1 = document.createElement('span');
    span1.textContent = str1;
    span1.className = className;
    elem.appendChild(span1);
    const span2 = document.createElement('span');
    span2.textContent = str2;
    span2.className = className;
    span1.appendChild(span2);
    elem.appendChild(document.createTextNode(str3));

    const result = element._rangesFromElement(elem, offset);

    assert.equal(result.length, 2);

    assert.equal(result[0].start, str0.length + offset);
    assert.equal(result[0].length, str1.length + str2.length);
    assert.equal(result[0].className, className);

    assert.equal(result[1].start, str0.length + str1.length + offset);
    assert.equal(result[1].length, str2.length);
    assert.equal(result[1].className, className);
  });

  test('_rangesFromString safelist allows recursion', () => {
    const str = [
      '<span class="non-whtelisted-class">',
      '<span class="gr-diff gr-syntax gr-syntax-keyword">public</span>',
      '</span>'].join('');
    const result = element._rangesFromString(str, new Map());
    assert.notEqual(result.length, 0);
  });

  test('_rangesFromString cache same syntax markers', () => {
    sinon.spy(element, '_rangesFromElement');
    const str =
      '<span class="gr-diff gr-syntax gr-syntax-keyword">public</span>';
    const cacheMap = new Map();
    element._rangesFromString(str, cacheMap);
    element._rangesFromString(str, cacheMap);
    assert.isTrue(element._rangesFromElement.calledOnce);
  });

  test('_isSectionDone', () => {
    let state = {sectionIndex: 0, lineIndex: 0};
    assert.isFalse(element._isSectionDone(state));

    state = {sectionIndex: 0, lineIndex: 2};
    assert.isFalse(element._isSectionDone(state));

    state = {sectionIndex: 0, lineIndex: 4};
    assert.isTrue(element._isSectionDone(state));

    state = {sectionIndex: 1, lineIndex: 2};
    assert.isFalse(element._isSectionDone(state));

    state = {sectionIndex: 1, lineIndex: 3};
    assert.isTrue(element._isSectionDone(state));

    state = {sectionIndex: 3, lineIndex: 0};
    assert.isFalse(element._isSectionDone(state));

    state = {sectionIndex: 3, lineIndex: 3};
    assert.isFalse(element._isSectionDone(state));

    state = {sectionIndex: 3, lineIndex: 4};
    assert.isTrue(element._isSectionDone(state));
  });

  test('workaround CPP LT directive', () => {
    // Does nothing to regular line.
    let line = 'int main(int argc, char** argv) { return 0; }';
    assert.equal(element._workaround('cpp', line), line);

    // Does nothing to include directive.
    line = '#include <stdio>';
    assert.equal(element._workaround('cpp', line), line);

    // Converts left-shift operator in #define.
    line = '#define GiB (1ull << 30)';
    let expected = '#define GiB (1ull || 30)';
    assert.equal(element._workaround('cpp', line), expected);

    // Converts less-than operator in #if.
    line = '  #if __GNUC__ < 4 || (__GNUC__ == 4 && __GNUC_MINOR__ < 1)';
    expected = '  #if __GNUC__ | 4 || (__GNUC__ == 4 && __GNUC_MINOR__ | 1)';
    assert.equal(element._workaround('cpp', line), expected);
  });

  test('workaround Java param-annotation', () => {
    // Does nothing to regular line.
    let line = 'public static void foo(int bar) { }';
    assert.equal(element._workaround('java', line), line);

    // Does nothing to regular annotation.
    line = 'public static void foo(@Nullable int bar) { }';
    assert.equal(element._workaround('java', line), line);

    // Converts parameterized annotation.
    line = 'public static void foo(@SuppressWarnings("unused") int bar) { }';
    const expected = 'public static void foo(@SuppressWarnings "unused" ' +
        ' int bar) { }';
    assert.equal(element._workaround('java', line), expected);
  });

  test('workaround CPP whcar_t character literals', () => {
    // Does nothing to regular line.
    let line = 'int main(int argc, char** argv) { return 0; }';
    assert.equal(element._workaround('cpp', line), line);

    // Does nothing to wchar_t string.
    line = 'wchar_t* sz = L"abc 123";';
    assert.equal(element._workaround('cpp', line), line);

    // Converts wchar_t character literal to string.
    line = 'wchar_t myChar = L\'#\'';
    let expected = 'wchar_t myChar = L"."';
    assert.equal(element._workaround('cpp', line), expected);

    // Converts wchar_t character literal with escape sequence to string.
    line = 'wchar_t myChar = L\'\\"\'';
    expected = 'wchar_t myChar = L"\\."';
    assert.equal(element._workaround('cpp', line), expected);
  });

  test('workaround go backslash character literals', () => {
    // Does nothing to regular line.
    let line = 'func foo(in []byte) (lit []byte, n int, err error) {';
    assert.equal(element._workaround('go', line), line);

    // Does nothing to string with backslash literal
    line = 'c := "\\\\"';
    assert.equal(element._workaround('go', line), line);

    // Converts backslash literal character to a string.
    line = 'c := \'\\\\\'';
    const expected = 'c := "\\\\"';
    assert.equal(element._workaround('go', line), expected);
  });
});

