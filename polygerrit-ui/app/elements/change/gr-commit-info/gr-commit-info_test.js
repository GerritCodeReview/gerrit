/**
 * @license
 * Copyright (C) 2015 The Android Open Source Project
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
import '../../core/gr-router/gr-router.js';
import './gr-commit-info.js';
import {GerritNav} from '../../core/gr-navigation/gr-navigation.js';

const basicFixture = fixtureFromElement('gr-commit-info');

suite('gr-commit-info tests', () => {
  let element;

  setup(() => {
    element = basicFixture.instantiate();
  });

  test('weblinks use GerritNav interface', () => {
    const weblinksStub = sinon.stub(GerritNav, '_generateWeblinks')
        .returns([{name: 'stubb', url: '#s'}]);
    element.change = {};
    element.commitInfo = {};
    element.serverConfig = {};
    assert.isTrue(weblinksStub.called);
  });

  test('no web link when unavailable', () => {
    element.commitInfo = {};
    element.serverConfig = {};
    element.change = {labels: [], project: ''};

    assert.isNotOk(element._showWebLink);
  });

  test('use web link when available', () => {
    const router = document.createElement('gr-router');
    sinon.stub(GerritNav, '_generateWeblinks').callsFake(
        router._generateWeblinks.bind(router));

    element.change = {labels: [], project: ''};
    element.commitInfo =
        {commit: 'commitsha', web_links: [{name: 'gitweb', url: 'link-url'}]};
    element.serverConfig = {};

    assert.isOk(element._showWebLink);
    assert.equal(element._webLink, 'link-url');
  });

  test('does not relativize web links that begin with scheme', () => {
    const router = document.createElement('gr-router');
    sinon.stub(GerritNav, '_generateWeblinks').callsFake(
        router._generateWeblinks.bind(router));

    element.change = {labels: [], project: ''};
    element.commitInfo = {
      commit: 'commitsha',
      web_links: [{name: 'gitweb', url: 'https://link-url'}],
    };
    element.serverConfig = {};

    assert.isOk(element._showWebLink);
    assert.equal(element._webLink, 'https://link-url');
  });

  test('ignore web links that are neither gitweb nor gitiles', () => {
    const router = document.createElement('gr-router');
    sinon.stub(GerritNav, '_generateWeblinks').callsFake(
        router._generateWeblinks.bind(router));

    element.change = {project: 'project-name'};
    element.commitInfo = {
      commit: 'commit-sha',
      web_links: [
        {
          name: 'ignore',
          url: 'ignore',
        },
        {
          name: 'gitiles',
          url: 'https://link-url',
        },
      ],
    };
    element.serverConfig = {};

    assert.isOk(element._showWebLink);
    assert.equal(element._webLink, 'https://link-url');

    // Remove gitiles link.
    element.commitInfo = {
      commit: 'commit-sha',
      web_links: [
        {
          name: 'ignore',
          url: 'ignore',
        },
      ],
    };
    assert.isNotOk(element._showWebLink);
    assert.isNotOk(element._webLink);
  });
});

