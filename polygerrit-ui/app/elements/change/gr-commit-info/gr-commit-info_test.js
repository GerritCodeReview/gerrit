
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













import '../../../test/common-test-setup.js';
import '../../core/gr-router/gr-router.js';
import './gr-commit-info.js';
import {GerritNav} from '../../core/gr-navigation/gr-navigation.js';

const basicFixture = fixtureFromElement('gr-commit-info');



suite('gr-commit-info tests', () => {
  let element;
  let sandbox;

  setup(() => {
    sandbox = sinon.sandbox.create();
    element = basicFixture.instantiate();
  });

  teardown(() => {
    sandbox.restore();
  });

  test('weblinks use GerritNav interface', () => {
    const weblinksStub = sandbox.stub(GerritNav, '_generateWeblinks')
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

    assert.isNotOk(element._computeShowWebLink(element.change,
        element.commitInfo, element.serverConfig));
  });

  test('use web link when available', () => {
    const router = document.createElement('gr-router');
    sandbox.stub(GerritNav, '_generateWeblinks',
        router._generateWeblinks.bind(router));

    element.change = {labels: [], project: ''};
    element.commitInfo =
        {commit: 'commitsha', web_links: [{name: 'gitweb', url: 'link-url'}]};
    element.serverConfig = {};

    assert.isOk(element._computeShowWebLink(element.change,
        element.commitInfo, element.serverConfig));
    assert.equal(element._computeWebLink(element.change, element.commitInfo,
        element.serverConfig), 'link-url');
  });

  test('does not relativize web links that begin with scheme', () => {
    const router = document.createElement('gr-router');
    sandbox.stub(GerritNav, '_generateWeblinks',
        router._generateWeblinks.bind(router));

    element.change = {labels: [], project: ''};
    element.commitInfo = {
      commit: 'commitsha',
      web_links: [{name: 'gitweb', url: 'https://link-url'}],
    };
    element.serverConfig = {};

    assert.isOk(element._computeShowWebLink(element.change,
        element.commitInfo, element.serverConfig));
    assert.equal(element._computeWebLink(element.change, element.commitInfo,
        element.serverConfig), 'https://link-url');
  });

  test('ignore web links that are neither gitweb nor gitiles', () => {
    const router = document.createElement('gr-router');
    sandbox.stub(GerritNav, '_generateWeblinks',
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

    assert.isOk(element._computeShowWebLink(element.change,
        element.commitInfo, element.serverConfig));
    assert.equal(element._computeWebLink(element.change, element.commitInfo,
        element.serverConfig), 'https://link-url');

    // Remove gitiles link.
    element.commitInfo.web_links.splice(1, 1);
    assert.isNotOk(element._computeShowWebLink(element.change,
        element.commitInfo, element.serverConfig));
    assert.isNotOk(element._computeWebLink(element.change, element.commitInfo,
        element.serverConfig));
  });
});

