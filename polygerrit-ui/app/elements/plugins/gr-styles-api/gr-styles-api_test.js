/**
 * @license
 * Copyright (C) 2019 The Android Open Source Project
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
import '../../shared/gr-js-api-interface/gr-js-api-interface.js';
import {PolymerElement} from '@polymer/polymer/polymer-element.js';
import {getPluginLoader} from '../../shared/gr-js-api-interface/gr-plugin-loader.js';
import {_testOnly_initGerritPluginApi} from '../../shared/gr-js-api-interface/gr-gerrit.js';
import {html} from '@polymer/polymer/lib/utils/html-tag.js';

class GrStyleTestElement extends PolymerElement {
  static get is() { return 'gr-style-test-element'; }

  static get template() {
    return html`<div id="wrapper"></div>`;
  }
}

customElements.define(GrStyleTestElement.is, GrStyleTestElement);

const pluginApi = _testOnly_initGerritPluginApi();

suite('gr-styles-api tests', () => {
  let stylesApi;

  setup(() => {
    let plugin;
    pluginApi.install(p => { plugin = p; }, '0.1',
        'http://test.com/plugins/testplugin/static/test.js');
    getPluginLoader().loadPlugins([]);
    stylesApi = plugin.styles();
  });

  teardown(() => {
    stylesApi = null;
  });

  test('exists', () => {
    assert.isOk(stylesApi);
  });

  test('css', () => {
    const styleObject = stylesApi.css('background: red');
    assert.isDefined(styleObject);
  });

  suite('GrStyleObject tests', () => {
    let stylesApi;
    let displayInlineStyle;
    let displayNoneStyle;
    let elementsToRemove;

    setup(() => {
      let plugin;
      pluginApi.install(p => { plugin = p; }, '0.1',
          'http://test.com/plugins/testplugin/static/test.js');
      getPluginLoader().loadPlugins([]);
      stylesApi = plugin.styles();
      displayInlineStyle = stylesApi.css('display: inline');
      displayNoneStyle = stylesApi.css('display: none');
      elementsToRemove = [];
    });

    teardown(() => {
      displayInlineStyle = null;
      displayNoneStyle = null;
      stylesApi = null;
      elementsToRemove.forEach(element => {
        element.remove();
      });
      elementsToRemove = null;
      sinon.restore();
    });

    function createNestedElements(parentElement) {
      /* parentElement
      *  |--- element1
      *  |--- element2
      *       |--- element3
      **/
      const element1 = document.createElement('div');
      const element2 = document.createElement('div');
      const element3 = document.createElement('div');
      parentElement.appendChild(element1);
      parentElement.appendChild(element2);
      element2.appendChild(element3);

      if (parentElement === document.body) {
        elementsToRemove.push(element1);
        elementsToRemove.push(element2);
      }

      return [element1, element2, element3];
    }

    test('getClassName  - body level elements', () => {
      const bodyLevelElements = createNestedElements(document.body);

      testGetClassName(bodyLevelElements);
    });

    test('getClassName  - elements inside polymer element', () => {
      const polymerElement = document.createElement('gr-style-test-element');
      document.body.appendChild(polymerElement);
      elementsToRemove.push(polymerElement);
      const contentElements = createNestedElements(polymerElement.$.wrapper);

      testGetClassName(contentElements);
    });

    function testGetClassName(elements) {
      assertAllElementsHaveDefaultStyle(elements);

      const className1 = displayInlineStyle.getClassName(elements[0]);
      const className2 = displayNoneStyle.getClassName(elements[1]);
      const className3 = displayInlineStyle.getClassName(elements[2]);

      assert.notEqual(className2, className1);
      assert.equal(className3, className1);

      assertAllElementsHaveDefaultStyle(elements);

      elements[0].classList.add(className1);
      elements[1].classList.add(className2);
      elements[2].classList.add(className1);

      assertDisplayPropertyValues(elements, ['inline', 'none', 'inline']);
    }

    test('apply - body level elements', () => {
      const bodyLevelElements = createNestedElements(document.body);

      testApply(bodyLevelElements);
    });

    test('apply - elements inside polymer element', () => {
      const polymerElement = document.createElement('gr-style-test-element');
      document.body.appendChild(polymerElement);
      elementsToRemove.push(polymerElement);
      const contentElements = createNestedElements(polymerElement.$.wrapper);

      testApply(contentElements);
    });

    function testApply(elements) {
      assertAllElementsHaveDefaultStyle(elements);
      displayInlineStyle.apply(elements[0]);
      displayNoneStyle.apply(elements[1]);
      displayInlineStyle.apply(elements[2]);
      assertDisplayPropertyValues(elements, ['inline', 'none', 'inline']);
    }

    function assertAllElementsHaveDefaultStyle(elements) {
      for (const element of elements) {
        assert.equal(getComputedStyle(element).getPropertyValue('display'),
            'block');
      }
    }

    function assertDisplayPropertyValues(elements, expectedDisplayValues) {
      for (let i = 0; i < elements.length; i++) {
        assert.equal(
            getComputedStyle(elements[i]).getPropertyValue('display'),
            expectedDisplayValues[i]);
      }
    }
  });
});

