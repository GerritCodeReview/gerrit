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
import './gr-diff-builder-element.js';
import {stubBaseUrl} from '../../../test/test-utils.js';
import {flush} from '@polymer/polymer/lib/legacy/polymer.dom.js';
import {GrAnnotation} from '../gr-diff-highlight/gr-annotation.js';
import {GrDiffLine, GrDiffLineType} from '../gr-diff/gr-diff-line.js';
import {GrDiffGroup, GrDiffGroupType} from '../gr-diff/gr-diff-group.js';
import {GrDiffBuilder} from './gr-diff-builder.js';
import {GrDiffBuilderSideBySide} from './gr-diff-builder-side-by-side.js';
import {html} from '@polymer/polymer/lib/utils/html-tag.js';
import {DiffViewMode, Side} from '../../../api/diff.js';
import {stubRestApi} from '../../../test/test-utils.js';
import {afterNextRender} from '@polymer/polymer/lib/utils/render-status';

const basicFixture = fixtureFromTemplate(html`
    <gr-diff-builder>
      <table id="diffTable"></table>
    </gr-diff-builder>
`);

const divWithTextFixture = fixtureFromTemplate(html`
<div>Lorem ipsum dolor sit amet, suspendisse inceptos vehicula</div>
`);

const mockDiffFixture = fixtureFromTemplate(html`
<gr-diff-builder view-mode="SIDE_BY_SIDE">
      <table id="diffTable"></table>
    </gr-diff-builder>
`);

// GrDiffBuilderElement forces these prefs to be set - tests that do not care
// about these values can just set these defaults.
const DEFAULT_PREFS = {
  line_length: 10,
  show_tabs: true,
  tab_size: 4,
};

suite('gr-diff-builder tests', () => {
  let prefs;
  let element;
  let builder;

  const LINE_BREAK_HTML = '<span class="style-scope gr-diff br"></span>';
  const WBR_HTML = '<wbr class="style-scope gr-diff">';

  setup(() => {
    element = basicFixture.instantiate();
    stubRestApi('getLoggedIn').returns(Promise.resolve(false));
    stubRestApi('getProjectConfig').returns(Promise.resolve({}));
    stubBaseUrl('/r');
    prefs = {...DEFAULT_PREFS};
    builder = new GrDiffBuilder({content: []}, prefs);
  });

  test('newlines 1', () => {
    let text = 'abcdef';

    assert.equal(builder._formatText(text, 'NONE', 4, 10).innerHTML, text);
    text = 'a'.repeat(20);
    assert.equal(builder._formatText(text, 'NONE', 4, 10).innerHTML,
        'a'.repeat(10) +
        LINE_BREAK_HTML +
        'a'.repeat(10));
  });

  test('newlines 2', () => {
    const text = '<span class="thumbsup">👍</span>';
    assert.equal(builder._formatText(text, 'NONE', 4, 10).innerHTML,
        '&lt;span clas' +
        LINE_BREAK_HTML +
        's="thumbsu' +
        LINE_BREAK_HTML +
        'p"&gt;👍&lt;/span' +
        LINE_BREAK_HTML +
        '&gt;');
  });

  test('newlines 3', () => {
    const text = '01234\t56789';
    assert.equal(builder._formatText(text, 'NONE', 4, 10).innerHTML,
        '01234' + builder._getTabWrapper(3).outerHTML + '56' +
        LINE_BREAK_HTML +
        '789');
  });

  test('newlines 4', () => {
    const text = '👍👍👍👍👍👍👍👍👍👍👍👍👍👍👍👍👍👍👍👍👍👍👍👍👍👍👍👍👍👍👍👍👍👍👍👍👍👍👍👍👍👍👍👍👍👍👍👍👍👍👍👍👍👍👍👍👍👍';
    assert.equal(builder._formatText(text, 'NONE', 4, 20).innerHTML,
        '👍👍👍👍👍👍👍👍👍👍👍👍👍👍👍👍👍👍👍👍' +
        LINE_BREAK_HTML +
        '👍👍👍👍👍👍👍👍👍👍👍👍👍👍👍👍👍👍👍👍' +
        LINE_BREAK_HTML +
        '👍👍👍👍👍👍👍👍👍👍👍👍👍👍👍👍👍👍');
  });

  test('line_length applied with <wbr> if line_wrapping is true', () => {
    builder._prefs = {line_wrapping: true, tab_size: 4, line_length: 50};
    const text = 'a'.repeat(51);

    const line = {text, highlights: []};
    const expected = 'a'.repeat(50) + WBR_HTML + 'a';
    const result = builder._createTextEl(undefined, line).firstChild.innerHTML;
    assert.equal(result, expected);
  });

  test('line_length applied with line break if line_wrapping is false', () => {
    builder._prefs = {line_wrapping: false, tab_size: 4, line_length: 50};
    const text = 'a'.repeat(51);

    const line = {text, highlights: []};
    const expected = 'a'.repeat(50) + LINE_BREAK_HTML + 'a';
    const result = builder._createTextEl(undefined, line).firstChild.innerHTML;
    assert.equal(result, expected);
  });

  [DiffViewMode.UNIFIED, DiffViewMode.SIDE_BY_SIDE]
      .forEach(mode => {
        test(`line_length used for regular files under ${mode}`, () => {
          element.path = '/a.txt';
          element.viewMode = mode;
          element.diff = {};
          element.prefs = {tab_size: 4, line_length: 50};
          builder = element._getDiffBuilder();
          assert.equal(builder._prefs.line_length, 50);
        });

        test(`line_length ignored for commit msg under ${mode}`, () => {
          element.path = '/COMMIT_MSG';
          element.viewMode = mode;
          element.diff = {};
          element.prefs = {tab_size: 4, line_length: 50};
          builder = element._getDiffBuilder();
          assert.equal(builder._prefs.line_length, 72);
        });
      });

  test('_createTextEl linewrap with tabs', () => {
    const text = '\t'.repeat(7) + '!';
    const line = {text, highlights: []};
    const el = builder._createTextEl(undefined, line);
    assert.equal(el.innerText, text);
    // With line length 10 and tab size 2, there should be a line break
    // after every two tabs.
    const newlineEl = el.querySelector('.contentText > .br');
    assert.isOk(newlineEl);
    assert.equal(
        el.querySelector('.contentText .tab:nth-child(2)').nextSibling,
        newlineEl);
  });

  test('text length with tabs and unicode', () => {
    function expectTextLength(text, tabSize, expected) {
      // Formatting to |expected| columns should not introduce line breaks.
      const result = builder._formatText(text, 'NONE', tabSize, expected);
      assert.isNotOk(result.querySelector('.contentText > .br'),
          `  Expected the result of: \n` +
          `      _formatText(${text}', 'NONE',  ${tabSize}, ${expected})\n` +
          `  to not contain a br. But the actual result HTML was:\n` +
          `      '${result.innerHTML}'\nwhereupon`);

      // Increasing the line limit should produce the same markup.
      assert.equal(
          builder._formatText(text, 'NONE', tabSize, Infinity).innerHTML,
          result.innerHTML);
      assert.equal(
          builder._formatText(text, 'NONE', tabSize, expected + 1).innerHTML,
          result.innerHTML);

      // Decreasing the line limit should introduce line breaks.
      if (expected > 0) {
        const tooSmall = builder._formatText(text,
            'NONE', tabSize, expected - 1);
        assert.isOk(tooSmall.querySelector('.contentText > .br'),
            `  Expected the result of: \n` +
            `      _formatText(${text}', ${tabSize}, ${expected - 1})\n` +
            `  to contain a br. But the actual result HTML was:\n` +
            `      '${tooSmall.innerHTML}'\nwhereupon`);
      }
    }
    expectTextLength('12345', 4, 5);
    expectTextLength('\t\t12', 4, 10);
    expectTextLength('abc💢123', 4, 7);
    expectTextLength('abc\t', 8, 8);
    expectTextLength('abc\t\t', 10, 20);
    expectTextLength('', 10, 0);
    // 17 Thai combining chars.
    expectTextLength('ก้้้้้้้้้้้้้้้้', 4, 17);
    expectTextLength('abc\tde', 10, 12);
    expectTextLength('abc\tde\t', 10, 20);
    expectTextLength('\t\t\t\t\t', 20, 100);
  });

  test('tab wrapper insertion', () => {
    const html = 'abc\tdef';
    const tabSize = builder._prefs.tab_size;
    const wrapper = builder._getTabWrapper(tabSize - 3);
    assert.ok(wrapper);
    assert.equal(wrapper.innerText, '\t');
    assert.equal(
        builder._formatText(html, 'NONE', tabSize, Infinity).innerHTML,
        'abc' + wrapper.outerHTML + 'def');
  });

  test('tab wrapper style', () => {
    const pattern = new RegExp('^<span class="style-scope gr-diff tab" ' +
      'style="((?:-moz-)?tab-size: (\\d+);.?)+">\\t<\\/span>$');

    for (const size of [1, 3, 8, 55]) {
      const html = builder._getTabWrapper(size).outerHTML;
      expect(html).to.match(pattern);
      assert.equal(html.match(pattern)[2], size);
    }
  });

  test('_handlePreferenceError throws with invalid preference', () => {
    element.prefs = {tab_size: 0};
    assert.throws(() => element._getDiffBuilder());
  });

  test('_handlePreferenceError triggers alert and javascript error', () => {
    const errorStub = sinon.stub();
    element.addEventListener('show-alert', errorStub);
    assert.throws(() => element._handlePreferenceError('tab size'));
    assert.equal(errorStub.lastCall.args[0].detail.message,
        `The value of the 'tab size' user preference is invalid. ` +
      `Fix in diff preferences`);
  });

  suite('_isTotal', () => {
    test('is total for add', () => {
      const lines = [];
      for (let idx = 0; idx < 10; idx++) {
        lines.push(new GrDiffLine(GrDiffLineType.ADD));
      }
      const group = new GrDiffGroup({type: GrDiffGroupType.DELTA, lines});
      assert.isTrue(GrDiffBuilder.prototype._isTotal(group));
    });

    test('is total for remove', () => {
      const lines = [];
      for (let idx = 0; idx < 10; idx++) {
        lines.push(new GrDiffLine(GrDiffLineType.REMOVE));
      }
      const group = new GrDiffGroup({type: GrDiffGroupType.DELTA, lines});
      assert.isTrue(GrDiffBuilder.prototype._isTotal(group));
    });

    test('not total for empty', () => {
      const group = new GrDiffGroup({type: GrDiffGroupType.BOTH});
      assert.isFalse(GrDiffBuilder.prototype._isTotal(group));
    });

    test('not total for non-delta', () => {
      const lines = [];
      for (let idx = 0; idx < 10; idx++) {
        lines.push(new GrDiffLine(GrDiffLineType.BOTH));
      }
      const group = new GrDiffGroup({type: GrDiffGroupType.DELTA, lines});
      assert.isFalse(GrDiffBuilder.prototype._isTotal(group));
    });
  });

  suite('intraline differences', () => {
    let el;
    let str;
    let annotateElementSpy;
    let layer;
    const lineNumberEl = document.createElement('td');

    function slice(str, start, end) {
      return Array.from(str).slice(start, end)
          .join('');
    }

    setup(() => {
      el = divWithTextFixture.instantiate();
      str = el.textContent;
      annotateElementSpy = sinon.spy(GrAnnotation, 'annotateElement');
      layer = document.createElement('gr-diff-builder')
          ._createIntralineLayer();
    });

    test('annotate no highlights', () => {
      const line = {
        text: str,
        highlights: [],
      };

      layer.annotate(el, lineNumberEl, line);

      // The content is unchanged.
      assert.isFalse(annotateElementSpy.called);
      assert.equal(el.childNodes.length, 1);
      assert.instanceOf(el.childNodes[0], Text);
      assert.equal(str, el.childNodes[0].textContent);
    });

    test('annotate with highlights', () => {
      const line = {
        text: str,
        highlights: [
          {startIndex: 6, endIndex: 12},
          {startIndex: 18, endIndex: 22},
        ],
      };
      const str0 = slice(str, 0, 6);
      const str1 = slice(str, 6, 12);
      const str2 = slice(str, 12, 18);
      const str3 = slice(str, 18, 22);
      const str4 = slice(str, 22);

      layer.annotate(el, lineNumberEl, line);

      assert.isTrue(annotateElementSpy.called);
      assert.equal(el.childNodes.length, 5);

      assert.instanceOf(el.childNodes[0], Text);
      assert.equal(el.childNodes[0].textContent, str0);

      assert.notInstanceOf(el.childNodes[1], Text);
      assert.equal(el.childNodes[1].textContent, str1);

      assert.instanceOf(el.childNodes[2], Text);
      assert.equal(el.childNodes[2].textContent, str2);

      assert.notInstanceOf(el.childNodes[3], Text);
      assert.equal(el.childNodes[3].textContent, str3);

      assert.instanceOf(el.childNodes[4], Text);
      assert.equal(el.childNodes[4].textContent, str4);
    });

    test('annotate without endIndex', () => {
      const line = {
        text: str,
        highlights: [
          {startIndex: 28},
        ],
      };

      const str0 = slice(str, 0, 28);
      const str1 = slice(str, 28);

      layer.annotate(el, lineNumberEl, line);

      assert.isTrue(annotateElementSpy.called);
      assert.equal(el.childNodes.length, 2);

      assert.instanceOf(el.childNodes[0], Text);
      assert.equal(el.childNodes[0].textContent, str0);

      assert.notInstanceOf(el.childNodes[1], Text);
      assert.equal(el.childNodes[1].textContent, str1);
    });

    test('annotate ignores empty highlights', () => {
      const line = {
        text: str,
        highlights: [
          {startIndex: 28, endIndex: 28},
        ],
      };

      layer.annotate(el, lineNumberEl, line);

      assert.isFalse(annotateElementSpy.called);
      assert.equal(el.childNodes.length, 1);
    });

    test('annotate handles unicode', () => {
      // Put some unicode into the string:
      str = str.replace(/\s/g, '💢');
      el.textContent = str;
      const line = {
        text: str,
        highlights: [
          {startIndex: 6, endIndex: 12},
        ],
      };

      const str0 = slice(str, 0, 6);
      const str1 = slice(str, 6, 12);
      const str2 = slice(str, 12);

      layer.annotate(el, lineNumberEl, line);

      assert.isTrue(annotateElementSpy.called);
      assert.equal(el.childNodes.length, 3);

      assert.instanceOf(el.childNodes[0], Text);
      assert.equal(el.childNodes[0].textContent, str0);

      assert.notInstanceOf(el.childNodes[1], Text);
      assert.equal(el.childNodes[1].textContent, str1);

      assert.instanceOf(el.childNodes[2], Text);
      assert.equal(el.childNodes[2].textContent, str2);
    });

    test('annotate handles unicode w/o endIndex', () => {
      // Put some unicode into the string:
      str = str.replace(/\s/g, '💢');
      el.textContent = str;

      const line = {
        text: str,
        highlights: [
          {startIndex: 6},
        ],
      };

      const str0 = slice(str, 0, 6);
      const str1 = slice(str, 6);

      layer.annotate(el, lineNumberEl, line);

      assert.isTrue(annotateElementSpy.called);
      assert.equal(el.childNodes.length, 2);

      assert.instanceOf(el.childNodes[0], Text);
      assert.equal(el.childNodes[0].textContent, str0);

      assert.notInstanceOf(el.childNodes[1], Text);
      assert.equal(el.childNodes[1].textContent, str1);
    });
  });

  suite('tab indicators', () => {
    let element;
    let layer;
    const lineNumberEl = document.createElement('td');

    setup(() => {
      element = basicFixture.instantiate();
      element._showTabs = true;
      layer = element._createTabIndicatorLayer();
    });

    test('does nothing with empty line', () => {
      const line = {text: ''};
      const el = document.createElement('div');
      const annotateElementStub =
          sinon.stub(GrAnnotation, 'annotateElement');

      layer.annotate(el, lineNumberEl, line);

      assert.isFalse(annotateElementStub.called);
    });

    test('does nothing with no tabs', () => {
      const str = 'lorem ipsum no tabs';
      const line = {text: str};
      const el = document.createElement('div');
      el.textContent = str;
      const annotateElementStub =
          sinon.stub(GrAnnotation, 'annotateElement');

      layer.annotate(el, lineNumberEl, line);

      assert.isFalse(annotateElementStub.called);
    });

    test('annotates tab at beginning', () => {
      const str = '\tlorem upsum';
      const line = {text: str};
      const el = document.createElement('div');
      el.textContent = str;
      const annotateElementStub =
          sinon.stub(GrAnnotation, 'annotateElement');

      layer.annotate(el, lineNumberEl, line);

      assert.equal(annotateElementStub.callCount, 1);
      const args = annotateElementStub.getCalls()[0].args;
      assert.equal(args[0], el);
      assert.equal(args[1], 0, 'offset of tab indicator');
      assert.equal(args[2], 1, 'length of tab indicator');
      assert.include(args[3], 'tab-indicator');
    });

    test('does not annotate when disabled', () => {
      element._showTabs = false;

      const str = '\tlorem upsum';
      const line = {text: str};
      const el = document.createElement('div');
      el.textContent = str;
      const annotateElementStub =
          sinon.stub(GrAnnotation, 'annotateElement');

      layer.annotate(el, lineNumberEl, line);

      assert.isFalse(annotateElementStub.called);
    });

    test('annotates multiple in beginning', () => {
      const str = '\t\tlorem upsum';
      const line = {text: str};
      const el = document.createElement('div');
      el.textContent = str;
      const annotateElementStub =
          sinon.stub(GrAnnotation, 'annotateElement');

      layer.annotate(el, lineNumberEl, line);

      assert.equal(annotateElementStub.callCount, 2);

      let args = annotateElementStub.getCalls()[0].args;
      assert.equal(args[0], el);
      assert.equal(args[1], 0, 'offset of tab indicator');
      assert.equal(args[2], 1, 'length of tab indicator');
      assert.include(args[3], 'tab-indicator');

      args = annotateElementStub.getCalls()[1].args;
      assert.equal(args[0], el);
      assert.equal(args[1], 1, 'offset of tab indicator');
      assert.equal(args[2], 1, 'length of tab indicator');
      assert.include(args[3], 'tab-indicator');
    });

    test('annotates intermediate tabs', () => {
      const str = 'lorem\tupsum';
      const line = {text: str};
      const el = document.createElement('div');
      el.textContent = str;
      const annotateElementStub =
          sinon.stub(GrAnnotation, 'annotateElement');

      layer.annotate(el, lineNumberEl, line);

      assert.equal(annotateElementStub.callCount, 1);
      const args = annotateElementStub.getCalls()[0].args;
      assert.equal(args[0], el);
      assert.equal(args[1], 5, 'offset of tab indicator');
      assert.equal(args[2], 1, 'length of tab indicator');
      assert.include(args[3], 'tab-indicator');
    });
  });

  suite('layers', () => {
    let element;
    let initialLayersCount;
    let withLayerCount;
    setup(() => {
      const layers = [];
      element = basicFixture.instantiate();
      element.layers = layers;
      element._showTrailingWhitespace = true;
      element._setupAnnotationLayers();
      initialLayersCount = element._layers.length;
    });

    test('no layers', () => {
      element._setupAnnotationLayers();
      assert.equal(element._layers.length, initialLayersCount);
    });

    suite('with layers', () => {
      const layers = [{}, {}];
      setup(() => {
        element = basicFixture.instantiate();
        element.layers = layers;
        element._showTrailingWhitespace = true;
        element._setupAnnotationLayers();
        withLayerCount = element._layers.length;
      });
      test('with layers', () => {
        element._setupAnnotationLayers();
        assert.equal(element._layers.length, withLayerCount);
        assert.equal(initialLayersCount + layers.length,
            withLayerCount);
      });
    });
  });

  suite('trailing whitespace', () => {
    let element;
    let layer;
    const lineNumberEl = document.createElement('td');

    setup(() => {
      element = basicFixture.instantiate();
      element._showTrailingWhitespace = true;
      layer = element._createTrailingWhitespaceLayer();
    });

    test('does nothing with empty line', () => {
      const line = {text: ''};
      const el = document.createElement('div');
      const annotateElementStub =
          sinon.stub(GrAnnotation, 'annotateElement');
      layer.annotate(el, lineNumberEl, line);
      assert.isFalse(annotateElementStub.called);
    });

    test('does nothing with no trailing whitespace', () => {
      const str = 'lorem ipsum blah blah';
      const line = {text: str};
      const el = document.createElement('div');
      el.textContent = str;
      const annotateElementStub =
          sinon.stub(GrAnnotation, 'annotateElement');
      layer.annotate(el, lineNumberEl, line);
      assert.isFalse(annotateElementStub.called);
    });

    test('annotates trailing spaces', () => {
      const str = 'lorem ipsum   ';
      const line = {text: str};
      const el = document.createElement('div');
      el.textContent = str;
      const annotateElementStub =
          sinon.stub(GrAnnotation, 'annotateElement');
      layer.annotate(el, lineNumberEl, line);
      assert.isTrue(annotateElementStub.called);
      assert.equal(annotateElementStub.lastCall.args[1], 11);
      assert.equal(annotateElementStub.lastCall.args[2], 3);
    });

    test('annotates trailing tabs', () => {
      const str = 'lorem ipsum\t\t\t';
      const line = {text: str};
      const el = document.createElement('div');
      el.textContent = str;
      const annotateElementStub =
          sinon.stub(GrAnnotation, 'annotateElement');
      layer.annotate(el, lineNumberEl, line);
      assert.isTrue(annotateElementStub.called);
      assert.equal(annotateElementStub.lastCall.args[1], 11);
      assert.equal(annotateElementStub.lastCall.args[2], 3);
    });

    test('annotates mixed trailing whitespace', () => {
      const str = 'lorem ipsum\t \t';
      const line = {text: str};
      const el = document.createElement('div');
      el.textContent = str;
      const annotateElementStub =
          sinon.stub(GrAnnotation, 'annotateElement');
      layer.annotate(el, lineNumberEl, line);
      assert.isTrue(annotateElementStub.called);
      assert.equal(annotateElementStub.lastCall.args[1], 11);
      assert.equal(annotateElementStub.lastCall.args[2], 3);
    });

    test('unicode preceding trailing whitespace', () => {
      const str = '💢\t';
      const line = {text: str};
      const el = document.createElement('div');
      el.textContent = str;
      const annotateElementStub =
          sinon.stub(GrAnnotation, 'annotateElement');
      layer.annotate(el, lineNumberEl, line);
      assert.isTrue(annotateElementStub.called);
      assert.equal(annotateElementStub.lastCall.args[1], 1);
      assert.equal(annotateElementStub.lastCall.args[2], 1);
    });

    test('does not annotate when disabled', () => {
      element._showTrailingWhitespace = false;
      const str = 'lorem upsum\t \t ';
      const line = {text: str};
      const el = document.createElement('div');
      el.textContent = str;
      const annotateElementStub =
          sinon.stub(GrAnnotation, 'annotateElement');
      layer.annotate(el, lineNumberEl, line);
      assert.isFalse(annotateElementStub.called);
    });
  });

  suite('rendering text, images and binary files', () => {
    let processStub;
    let keyLocations;
    let content;

    setup(() => {
      element = basicFixture.instantiate();
      element.viewMode = 'SIDE_BY_SIDE';
      processStub = sinon.stub(element.$.processor, 'process')
          .returns(Promise.resolve());
      keyLocations = {left: {}, right: {}};
      element.prefs = {
        ...DEFAULT_PREFS,
        context: -1,
        syntax_highlighting: true,
      };
      content = [{
        a: ['all work and no play make andybons a dull boy'],
        b: ['elgoog elgoog elgoog'],
      }, {
        ab: [
          'Non eram nescius, Brute, cum, quae summis ingeniis ',
          'exquisitaque doctrina philosophi Graeco sermone tractavissent',
        ],
      }];
    });

    test('text', () => {
      element.diff = {content};
      return element.render(keyLocations).then(() => {
        assert.isTrue(processStub.calledOnce);
        assert.isFalse(processStub.lastCall.args[1]);
      });
    });

    test('image', () => {
      element.diff = {content, binary: true};
      element.isImageDiff = true;
      return element.render(keyLocations).then(() => {
        assert.isTrue(processStub.calledOnce);
        assert.isTrue(processStub.lastCall.args[1]);
      });
    });

    test('binary', () => {
      element.diff = {content, binary: true};
      return element.render(keyLocations).then(() => {
        assert.isTrue(processStub.calledOnce);
        assert.isTrue(processStub.lastCall.args[1]);
      });
    });
  });

  suite('rendering', () => {
    let content;
    let outputEl;
    let keyLocations;

    setup(async () => {
      const prefs = {...DEFAULT_PREFS};
      content = [
        {
          a: ['all work and no play make andybons a dull boy'],
          b: ['elgoog elgoog elgoog'],
        },
        {
          ab: [
            'Non eram nescius, Brute, cum, quae summis ingeniis ',
            'exquisitaque doctrina philosophi Graeco sermone tractavissent',
          ],
        },
      ];
      element = basicFixture.instantiate();
      sinon.stub(element, 'dispatchEvent');
      outputEl = element.querySelector('#diffTable');
      keyLocations = {left: {}, right: {}};
      sinon.stub(element, '_getDiffBuilder').callsFake(() => {
        const builder = new GrDiffBuilderSideBySide({content}, prefs, outputEl);
        sinon.stub(builder, 'addColumns');
        builder.buildSectionElement = function(group) {
          const section = document.createElement('stub');
          section.textContent = group.lines
              .reduce((acc, line) => acc + line.text, '');
          return section;
        };
        return builder;
      });
      element.diff = {content};
      element.prefs = prefs;
      await element.render(keyLocations);
    });

    test('addColumns is called', () => {
      assert.isTrue(element._builder.addColumns.called);
    });

    test('getGroupsByLineRange one line', () => {
      const section = outputEl.querySelector('stub:nth-of-type(3)');
      const groups = element._builder.getGroupsByLineRange(1, 1, 'left');
      assert.equal(groups.length, 1);
      assert.strictEqual(groups[0].element, section);
    });

    test('getGroupsByLineRange over diff', () => {
      const section = [
        outputEl.querySelector('stub:nth-of-type(3)'),
        outputEl.querySelector('stub:nth-of-type(4)'),
      ];
      const groups = element._builder.getGroupsByLineRange(1, 2, 'left');
      assert.equal(groups.length, 2);
      assert.strictEqual(groups[0].element, section[0]);
      assert.strictEqual(groups[1].element, section[1]);
    });

    test('render-start and render-content are fired', async () => {
      const firedEventTypes = element.dispatchEvent.getCalls()
          .map(c => c.args[0].type);
      assert.include(firedEventTypes, 'render-start');
      assert.include(firedEventTypes, 'render-content');
    });

    test('cancel cancels the processor', () => {
      const processorCancelStub = sinon.stub(element.$.processor, 'cancel');
      element.cancel();
      assert.isTrue(processorCancelStub.called);
    });
  });

  suite('context hiding and expanding', () => {
    setup(async () => {
      element = basicFixture.instantiate();
      sinon.stub(element, 'dispatchEvent');
      const afterNextRenderPromise = new Promise((resolve, reject) => {
        afterNextRender(element, resolve);
      });
      element.diff = {
        content: [
          {ab: [1, 2, 3, 4, 5, 6, 7, 8, 9, 10].map(i => `unchanged ${i}`)},
          {a: ['before'], b: ['after']},
          {ab: [1, 2, 3, 4, 5, 6, 7, 8, 9, 10].map(i => `unchanged ${10 + i}`)},
        ],
      };
      element.viewMode = DiffViewMode.SIDE_BY_SIDE;

      const keyLocations = {left: {}, right: {}};
      element.prefs = {
        ...DEFAULT_PREFS,
        context: 1,
      };
      await element.render(keyLocations);
      // Make sure all listeners are installed.
      await afterNextRenderPromise;
    });

    test('hides lines behind two context controls', () => {
      const contextControls = element.querySelectorAll('gr-context-controls');
      assert.equal(contextControls.length, 2);

      const diffRows = element.querySelectorAll('.diff-row');
      // The first two are LOST and FILE line
      assert.equal(diffRows.length, 2 + 1 + 1 + 1);
      assert.include(diffRows[2].textContent, 'unchanged 10');
      assert.include(diffRows[3].textContent, 'before');
      assert.include(diffRows[3].textContent, 'after');
      assert.include(diffRows[4].textContent, 'unchanged 11');
    });

    test('clicking +x common lines expands those lines', () => {
      const contextControls = element.querySelectorAll('gr-context-controls');
      const topExpandCommonButton = contextControls[0].shadowRoot
          .querySelectorAll('.showContext')[0];
      assert.include(topExpandCommonButton.textContent, '+9 common lines');
      topExpandCommonButton.click();
      const diffRows = element.querySelectorAll('.diff-row');
      // The first two are LOST and FILE line
      assert.equal(diffRows.length, 2 + 10 + 1 + 1);
      assert.include(diffRows[2].textContent, 'unchanged 1');
      assert.include(diffRows[3].textContent, 'unchanged 2');
      assert.include(diffRows[4].textContent, 'unchanged 3');
      assert.include(diffRows[5].textContent, 'unchanged 4');
      assert.include(diffRows[6].textContent, 'unchanged 5');
      assert.include(diffRows[7].textContent, 'unchanged 6');
      assert.include(diffRows[8].textContent, 'unchanged 7');
      assert.include(diffRows[9].textContent, 'unchanged 8');
      assert.include(diffRows[10].textContent, 'unchanged 9');
      assert.include(diffRows[11].textContent, 'unchanged 10');
      assert.include(diffRows[12].textContent, 'before');
      assert.include(diffRows[12].textContent, 'after');
      assert.include(diffRows[13].textContent, 'unchanged 11');
    });

    test('unhideLine shows the line with context', async () => {
      const clock = sinon.useFakeTimers();
      element.dispatchEvent.reset();
      element.unhideLine(4, Side.LEFT);

      const diffRows = element.querySelectorAll('.diff-row');
      // The first two are LOST and FILE line
      // Lines 3-5 (Line 4 plus 1 context in each direction) will be expanded
      // Because context expanders do not hide <3 lines, lines 1-2 will also
      // be shown.
      // Lines 6-9 continue to be hidden
      assert.equal(diffRows.length, 2 + 5 + 1 + 1 + 1);
      assert.include(diffRows[2].textContent, 'unchanged 1');
      assert.include(diffRows[3].textContent, 'unchanged 2');
      assert.include(diffRows[4].textContent, 'unchanged 3');
      assert.include(diffRows[5].textContent, 'unchanged 4');
      assert.include(diffRows[6].textContent, 'unchanged 5');
      assert.include(diffRows[7].textContent, 'unchanged 10');
      assert.include(diffRows[8].textContent, 'before');
      assert.include(diffRows[8].textContent, 'after');
      assert.include(diffRows[9].textContent, 'unchanged 11');

      clock.tick(1);
      await flush();
      const firedEventTypes = element.dispatchEvent.getCalls()
          .map(c => c.args[0].type);
      assert.include(firedEventTypes, 'render-content');
    });
  });

  suite('mock-diff', () => {
    let element;
    let builder;
    let diff;
    let keyLocations;

    setup(async () => {
      element = mockDiffFixture.instantiate();
      diff = getMockDiffResponse();
      element.diff = diff;

      keyLocations = {left: {}, right: {}};

      element.prefs = {
        line_length: 80,
        show_tabs: true,
        tab_size: 4,
      };
      await element.render(keyLocations);
      builder = element._builder;
    });

    test('aria-labels on added line numbers', () => {
      const deltaLineNumberButton = element.diffElement.querySelectorAll(
          '.lineNumButton.right')[5];

      assert.isOk(deltaLineNumberButton);
      assert.equal(deltaLineNumberButton.getAttribute('aria-label'), '5 added');
    });

    test('aria-labels on removed line numbers', () => {
      const deltaLineNumberButton = element.diffElement.querySelectorAll(
          '.lineNumButton.left')[10];

      assert.isOk(deltaLineNumberButton);
      assert.equal(
          deltaLineNumberButton.getAttribute('aria-label'), '10 removed');
    });

    test('getContentByLine', () => {
      let actual;

      actual = builder.getContentByLine(2, 'left');
      assert.equal(actual.textContent, diff.content[0].ab[1]);

      actual = builder.getContentByLine(2, 'right');
      assert.equal(actual.textContent, diff.content[0].ab[1]);

      actual = builder.getContentByLine(5, 'left');
      assert.equal(actual.textContent, diff.content[2].ab[0]);

      actual = builder.getContentByLine(5, 'right');
      assert.equal(actual.textContent, diff.content[1].b[0]);
    });

    test('getContentTdByLineEl works both with button and td', () => {
      const diffRow = element.diffElement.querySelectorAll('tr.diff-row')[2];

      const lineNumTdLeft = diffRow.querySelector('td.lineNum.left');
      const lineNumButtonLeft = lineNumTdLeft.querySelector('button');
      const contentTdLeft = diffRow.querySelectorAll('.content')[0];

      const lineNumTdRight = diffRow.querySelector('td.lineNum.right');
      const lineNumButtonRight = lineNumTdRight.querySelector('button');
      const contentTdRight = diffRow.querySelectorAll('.content')[1];

      assert.equal(element.getContentTdByLineEl(lineNumTdLeft), contentTdLeft);
      assert.equal(
          element.getContentTdByLineEl(lineNumButtonLeft), contentTdLeft);
      assert.equal(
          element.getContentTdByLineEl(lineNumTdRight), contentTdRight);
      assert.equal(
          element.getContentTdByLineEl(lineNumButtonRight), contentTdRight);
    });

    test('findLinesByRange', () => {
      const lines = [];
      const elems = [];
      const start = 6;
      const end = 10;
      const count = end - start + 1;

      builder.findLinesByRange(start, end, 'right', lines, elems);

      assert.equal(lines.length, count);
      assert.equal(elems.length, count);

      for (let i = 0; i < 5; i++) {
        assert.instanceOf(lines[i], GrDiffLine);
        assert.equal(lines[i].afterNumber, start + i);
        assert.instanceOf(elems[i], HTMLElement);
        assert.equal(lines[i].text, elems[i].textContent);
      }
    });

    test('_renderContentByRange', () => {
      const spy = sinon.spy(builder, '_createTextEl');
      const start = 9;
      const end = 14;
      const count = end - start + 1;

      builder._renderContentByRange(start, end, 'left');

      assert.equal(spy.callCount, count);
      spy.getCalls().forEach((call, i) => {
        assert.equal(call.args[1].beforeNumber, start + i);
      });
    });

    test('_renderContentByRange notexistent elements', () => {
      const spy = sinon.spy(builder, '_createTextEl');

      sinon.stub(builder, '_getLineNumberEl').returns(
          document.createElement('div')
      );
      sinon.stub(builder, 'findLinesByRange').callsFake(
          (s, e, d, lines, elements) => {
            // Add a line and a corresponding element.
            lines.push(new GrDiffLine(GrDiffLineType.BOTH));
            const tr = document.createElement('tr');
            const td = document.createElement('td');
            const el = document.createElement('div');
            tr.appendChild(td);
            td.appendChild(el);
            elements.push(el);

            // Add 2 lines without corresponding elements.
            lines.push(new GrDiffLine(GrDiffLineType.BOTH));
            lines.push(new GrDiffLine(GrDiffLineType.BOTH));
          });

      builder._renderContentByRange(1, 10, 'left');
      // Should be called only once because only one line had a corresponding
      // element.
      assert.equal(spy.callCount, 1);
    });

    test('_getLineNumberEl side-by-side left', () => {
      const contentEl = builder.getContentByLine(5, 'left',
          element.$.diffTable);
      const lineNumberEl = builder._getLineNumberEl(contentEl, 'left');
      assert.isTrue(lineNumberEl.classList.contains('lineNum'));
      assert.isTrue(lineNumberEl.classList.contains('left'));
    });

    test('_getLineNumberEl side-by-side right', () => {
      const contentEl = builder.getContentByLine(5, 'right',
          element.$.diffTable);
      const lineNumberEl = builder._getLineNumberEl(contentEl, 'right');
      assert.isTrue(lineNumberEl.classList.contains('lineNum'));
      assert.isTrue(lineNumberEl.classList.contains('right'));
    });

    test('_getLineNumberEl unified left', async () => {
      // Re-render as unified:
      element.viewMode = 'UNIFIED_DIFF';
      await element.render(keyLocations);
      builder = element._builder;

      const contentEl = builder.getContentByLine(5, 'left',
          element.$.diffTable);
      const lineNumberEl = builder._getLineNumberEl(contentEl, 'left');
      assert.isTrue(lineNumberEl.classList.contains('lineNum'));
      assert.isTrue(lineNumberEl.classList.contains('left'));
    });

    test('_getLineNumberEl unified right', async () => {
      // Re-render as unified:
      element.viewMode = 'UNIFIED_DIFF';
      await element.render(keyLocations);
      builder = element._builder;

      const contentEl = builder.getContentByLine(5, 'right',
          element.$.diffTable);
      const lineNumberEl = builder._getLineNumberEl(contentEl, 'right');
      assert.isTrue(lineNumberEl.classList.contains('lineNum'));
      assert.isTrue(lineNumberEl.classList.contains('right'));
    });

    test('_getNextContentOnSide side-by-side left', () => {
      const startElem = builder.getContentByLine(5, 'left',
          element.$.diffTable);
      const expectedStartString = diff.content[2].ab[0];
      const expectedNextString = diff.content[2].ab[1];
      assert.equal(startElem.textContent, expectedStartString);

      const nextElem = builder._getNextContentOnSide(startElem,
          'left');
      assert.equal(nextElem.textContent, expectedNextString);
    });

    test('_getNextContentOnSide side-by-side right', () => {
      const startElem = builder.getContentByLine(5, 'right',
          element.$.diffTable);
      const expectedStartString = diff.content[1].b[0];
      const expectedNextString = diff.content[1].b[1];
      assert.equal(startElem.textContent, expectedStartString);

      const nextElem = builder._getNextContentOnSide(startElem,
          'right');
      assert.equal(nextElem.textContent, expectedNextString);
    });

    test('_getNextContentOnSide unified left', async () => {
      // Re-render as unified:
      element.viewMode = 'UNIFIED_DIFF';
      await element.render(keyLocations);
      builder = element._builder;

      const startElem = builder.getContentByLine(5, 'left',
          element.$.diffTable);
      const expectedStartString = diff.content[2].ab[0];
      const expectedNextString = diff.content[2].ab[1];
      assert.equal(startElem.textContent, expectedStartString);

      const nextElem = builder._getNextContentOnSide(startElem,
          'left');
      assert.equal(nextElem.textContent, expectedNextString);
    });

    test('_getNextContentOnSide unified right', async () => {
      // Re-render as unified:
      element.viewMode = 'UNIFIED_DIFF';
      await element.render(keyLocations);
      builder = element._builder;

      const startElem = builder.getContentByLine(5, 'right',
          element.$.diffTable);
      const expectedStartString = diff.content[1].b[0];
      const expectedNextString = diff.content[1].b[1];
      assert.equal(startElem.textContent, expectedStartString);

      const nextElem = builder._getNextContentOnSide(startElem,
          'right');
      assert.equal(nextElem.textContent, expectedNextString);
    });

    test('escaping HTML', () => {
      let input = '<script>alert("XSS");<' + '/script>';
      let expected = '&lt;script&gt;alert("XSS");&lt;/script&gt;';
      let result = builder._formatText(input, 1, Infinity).innerHTML;
      assert.equal(result, expected);

      input = '& < > " \' / `';
      expected = '&amp; &lt; &gt; " \' / `';
      result = builder._formatText(input, 1, Infinity).innerHTML;
      assert.equal(result, expected);
    });
  });

  suite('blame', () => {
    let mockBlame;

    setup(() => {
      mockBlame = [
        {id: 'commit 1', ranges: [{start: 1, end: 2}, {start: 10, end: 16}]},
        {id: 'commit 2', ranges: [{start: 4, end: 10}, {start: 17, end: 32}]},
      ];
    });

    test('setBlame attempts to render each blamed line', () => {
      const getBlameStub = sinon.stub(builder, '_getBlameByLineNum')
          .returns(null);
      builder.setBlame(mockBlame);
      assert.equal(getBlameStub.callCount, 32);
    });

    test('_getBlameCommitForBaseLine', () => {
      sinon.stub(builder, '_getBlameByLineNum').returns(null);
      builder.setBlame(mockBlame);
      assert.isOk(builder._getBlameCommitForBaseLine(1));
      assert.equal(builder._getBlameCommitForBaseLine(1).id, 'commit 1');

      assert.isOk(builder._getBlameCommitForBaseLine(11));
      assert.equal(builder._getBlameCommitForBaseLine(11).id, 'commit 1');

      assert.isOk(builder._getBlameCommitForBaseLine(32));
      assert.equal(builder._getBlameCommitForBaseLine(32).id, 'commit 2');

      assert.isNull(builder._getBlameCommitForBaseLine(33));
    });

    test('_getBlameCommitForBaseLine w/o blame returns null', () => {
      assert.isNull(builder._getBlameCommitForBaseLine(1));
      assert.isNull(builder._getBlameCommitForBaseLine(11));
      assert.isNull(builder._getBlameCommitForBaseLine(31));
    });

    test('_createBlameCell', () => {
      const mocbBlameCell = document.createElement('span');
      const getBlameStub = sinon.stub(builder, '_getBlameForBaseLine')
          .returns(mocbBlameCell);
      const line = new GrDiffLine(GrDiffLineType.BOTH);
      line.beforeNumber = 3;
      line.afterNumber = 5;

      const result = builder._createBlameCell(line.beforeNumber);

      assert.isTrue(getBlameStub.calledWithExactly(3));
      assert.equal(result.getAttribute('data-line-number'), '3');
      assert.equal(result.firstChild, mocbBlameCell);
    });

    test('_getBlameForBaseLine', () => {
      const mockCommit = {
        time: 1576105200,
        id: 1234567890,
        author: 'Clark Kent',
        commit_msg: 'Testing Commit',
        ranges: [1],
      };
      const blameNode = builder._getBlameForBaseLine(1, mockCommit);

      const authors = blameNode.getElementsByClassName('blameAuthor');
      assert.equal(authors.length, 1);
      assert.equal(authors[0].innerText, ' Clark');

      const date = (new Date(mockCommit.time * 1000)).toLocaleDateString();
      flush();
      const cards = blameNode.getElementsByClassName('blameHoverCard');
      assert.equal(cards.length, 1);
      assert.equal(cards[0].innerHTML,
          `Commit 1234567890<br>Author: Clark Kent<br>Date: ${date}`
        + '<br><br>Testing Commit'
      );

      const url = blameNode.getElementsByClassName('blameDate');
      assert.equal(url[0].getAttribute('href'), '/r/q/1234567890');
    });
  });
});

