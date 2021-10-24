/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
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

import {PolymerElement} from '@polymer/polymer/polymer-element.js';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin.js';

/**
 * This is an "abstract" class for tests. The descendant must define a template
 * for this element and a tagName - see createCommentApiMockWithTemplateElement below
 */
class CommentApiMock extends LegacyElementMixin(PolymerElement) {
  static get properties() {
    return {
      _changeComments: Object,
    };
  }
}

/**
 * Creates a new element which is descendant of CommentApiMock with specified
 * template. Additionally, the method registers a tagName for this element.
 *
 * Each tagName must be a unique accross all tests.
 */
export function createCommentApiMockWithTemplateElement(tagName, template) {
  const elementClass = class extends CommentApiMock {
    static get is() { return tagName; }

    static get template() { return template; }
  };
  customElements.define(tagName, elementClass);
  return elementClass;
}

