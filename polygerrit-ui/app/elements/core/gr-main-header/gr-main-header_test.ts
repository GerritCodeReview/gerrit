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

import '../../../test/common-test-setup-karma';
import {
  fullElementUpdateComplete,
  isHidden,
  isVisible,
  query,
  queryAndAssert,
  stubRestApi,
} from '../../../test/test-utils';
import './gr-main-header';
import {GrMainHeader} from './gr-main-header';
import {
  createAccountDetailWithId,
  createGerritInfo,
  createServerInfo,
} from '../../../test/test-data-generators';
import {NavLink} from '../../../utils/admin-nav-util';
import {ServerInfo, TopMenuItemInfo} from '../../../types/common';
import {AuthType} from '../../../constants/constants';
import * as MockInteractions from '@polymer/iron-test-helpers/mock-interactions';
import {EventType} from '../../../types/events';

const basicFixture = fixtureFromElement('gr-main-header');

suite('gr-main-header tests', () => {
  let element: GrMainHeader;

  setup(async () => {
    stubRestApi('probePath').returns(Promise.resolve(false));
    stub('gr-main-header', 'loadAccount').callsFake(() => Promise.resolve());
    element = basicFixture.instantiate();
    await element.updateComplete;
  });

  test('link visibility', async () => {
    element.loading = true;
    await element.updateComplete;
    assert.isTrue(isHidden(query(element, '.accountContainer')));

    element.loading = false;
    element.loggedIn = false;
    await element.updateComplete;
    assert.isFalse(isHidden(query(element, '.accountContainer')));
    assert.isFalse(isHidden(query(element, '.loginButton')));
    assert.isNotOk(query(element, '.registerDiv'));
    assert.isNotOk(query(element, '.registerButton'));

    element.account = createAccountDetailWithId(1);
    await element.updateComplete;
    assert.isTrue(isHidden(query(element, 'gr-account-dropdown')));
    assert.isTrue(isHidden(query(element, '.settingsButton')));

    element.loggedIn = true;
    await element.updateComplete;
    assert.isTrue(isHidden(query(element, '.loginButton')));
    assert.isTrue(isHidden(query(element, '.registerButton')));
    assert.isFalse(isHidden(query(element, 'gr-account-dropdown')));
    assert.isFalse(isHidden(query(element, '.settingsButton')));
  });

  test('fix my menu item', () => {
    assert.deepEqual(
      [
        {url: 'https://awesometown.com/#hashyhash', name: '', target: ''},
        {url: 'url', name: '', target: '_blank'},
      ].map(element.createHeaderLink),
      [
        {url: 'https://awesometown.com/#hashyhash', name: ''},
        {url: 'url', name: ''},
      ]
    );
  });

  test('user links', () => {
    const defaultLinks = [
      {
        title: 'Faves',
        links: [
          {
            name: 'Pinterest',
            url: 'https://pinterest.com',
          },
        ],
      },
    ];
    const userLinks: TopMenuItemInfo[] = [
      {
        name: 'Facebook',
        url: 'https://facebook.com',
        target: '',
      },
    ];
    const adminLinks: NavLink[] = [
      {
        name: 'Repos',
        url: '/repos',
        noBaseUrl: true,
        view: undefined,
      },
    ];

    // When no admin links are passed, it should use the default.
    assert.deepEqual(
      element.computeLinks(
        /* userLinks= */ [],
        adminLinks,
        /* topMenus= */ [],
        /* docBaseUrl= */ '',
        defaultLinks
      ),
      defaultLinks.concat({
        title: 'Browse',
        links: adminLinks,
      })
    );
    assert.deepEqual(
      element.computeLinks(
        userLinks,
        adminLinks,
        /* topMenus= */ [],
        /* docBaseUrl= */ '',
        defaultLinks
      ),
      defaultLinks.concat([
        {
          title: 'Your',
          links: userLinks,
        },
        {
          title: 'Browse',
          links: adminLinks,
        },
      ])
    );
  });

  test('documentation links', () => {
    const docLinks = [
      {
        name: 'Table of Contents',
        url: '/index.html',
      },
    ];

    assert.deepEqual(element.getDocLinks(null, docLinks), []);
    assert.deepEqual(element.getDocLinks('', docLinks), []);
    assert.deepEqual(element.getDocLinks('base', []), []);

    assert.deepEqual(element.getDocLinks('base', docLinks), [
      {
        name: 'Table of Contents',
        target: '_blank',
        url: 'base/index.html',
      },
    ]);

    assert.deepEqual(element.getDocLinks('base/', docLinks), [
      {
        name: 'Table of Contents',
        target: '_blank',
        url: 'base/index.html',
      },
    ]);
  });

  test('top menus', () => {
    const adminLinks: NavLink[] = [
      {
        name: 'Repos',
        url: '/repos',
        noBaseUrl: true,
        view: undefined,
      },
    ];
    const topMenus = [
      {
        name: 'Plugins',
        items: [
          {
            name: 'Manage',
            target: '_blank',
            url: 'https://gerrit/plugins/plugin-manager/static/index.html',
          },
        ],
      },
    ];
    assert.deepEqual(
      element.computeLinks(
        /* userLinks= */ [],
        adminLinks,
        topMenus,
        /* baseDocUrl= */ '',
        /* defaultLinks= */ []
      ),
      [
        {
          title: 'Browse',
          links: adminLinks,
        },
        {
          title: 'Plugins',
          links: [
            {
              name: 'Manage',
              url: 'https://gerrit/plugins/plugin-manager/static/index.html',
            },
          ],
        },
      ]
    );
  });

  test('ignore top project menus', () => {
    const adminLinks: NavLink[] = [
      {
        name: 'Repos',
        url: '/repos',
        noBaseUrl: true,
        view: undefined,
      },
    ];
    const topMenus = [
      {
        name: 'Projects',
        items: [
          {
            name: 'Project Settings',
            target: '_blank',
            url: '/plugins/myplugin/${projectName}',
          },
          {
            name: 'Project List',
            target: '_blank',
            url: '/plugins/myplugin/index.html',
          },
        ],
      },
    ];
    assert.deepEqual(
      element.computeLinks(
        /* userLinks= */ [],
        adminLinks,
        topMenus,
        /* baseDocUrl= */ '',
        /* defaultLinks= */ []
      ),
      [
        {
          title: 'Browse',
          links: adminLinks,
        },
        {
          title: 'Projects',
          links: [
            {
              name: 'Project List',
              url: '/plugins/myplugin/index.html',
            },
          ],
        },
      ]
    );
  });

  test('merge top menus', () => {
    const adminLinks: NavLink[] = [
      {
        name: 'Repos',
        url: '/repos',
        noBaseUrl: true,
        view: undefined,
      },
    ];
    const topMenus = [
      {
        name: 'Plugins',
        items: [
          {
            name: 'Manage',
            target: '_blank',
            url: 'https://gerrit/plugins/plugin-manager/static/index.html',
          },
        ],
      },
      {
        name: 'Plugins',
        items: [
          {
            name: 'Create',
            target: '_blank',
            url: 'https://gerrit/plugins/plugin-manager/static/create.html',
          },
        ],
      },
    ];
    assert.deepEqual(
      element.computeLinks(
        /* userLinks= */ [],
        adminLinks,
        topMenus,
        /* baseDocUrl= */ '',
        /* defaultLinks= */ []
      ),
      [
        {
          title: 'Browse',
          links: adminLinks,
        },
        {
          title: 'Plugins',
          links: [
            {
              name: 'Manage',
              url: 'https://gerrit/plugins/plugin-manager/static/index.html',
            },
            {
              name: 'Create',
              url: 'https://gerrit/plugins/plugin-manager/static/create.html',
            },
          ],
        },
      ]
    );
  });

  test('merge top menus in default links', () => {
    const defaultLinks = [
      {
        title: 'Faves',
        links: [
          {
            name: 'Pinterest',
            url: 'https://pinterest.com',
          },
        ],
      },
    ];
    const topMenus = [
      {
        name: 'Faves',
        items: [
          {
            name: 'Manage',
            target: '_blank',
            url: 'https://gerrit/plugins/plugin-manager/static/index.html',
          },
        ],
      },
    ];
    assert.deepEqual(
      element.computeLinks(
        /* userLinks= */ [],
        /* adminLinks= */ [],
        topMenus,
        /* baseDocUrl= */ '',
        defaultLinks
      ),
      [
        {
          title: 'Faves',
          links: defaultLinks[0].links.concat([
            {
              name: 'Manage',
              url: 'https://gerrit/plugins/plugin-manager/static/index.html',
            },
          ]),
        },
        {
          title: 'Browse',
          links: [],
        },
      ]
    );
  });

  test('merge top menus in user links', () => {
    const userLinks = [
      {
        name: 'Facebook',
        url: 'https://facebook.com',
        target: '',
      },
    ];
    const topMenus = [
      {
        name: 'Your',
        items: [
          {
            name: 'Manage',
            target: '_blank',
            url: 'https://gerrit/plugins/plugin-manager/static/index.html',
          },
        ],
      },
    ];
    assert.deepEqual(
      element.computeLinks(
        userLinks,
        /* adminLinks= */ [],
        topMenus,
        /* baseDocUrl= */ '',
        /* defaultLinks= */ []
      ),
      [
        {
          title: 'Your',
          links: [
            {
              name: 'Facebook',
              url: 'https://facebook.com',
              target: '',
            },
            {
              name: 'Manage',
              url: 'https://gerrit/plugins/plugin-manager/static/index.html',
            },
          ],
        },
        {
          title: 'Browse',
          links: [],
        },
      ]
    );
  });

  test('merge top menus in admin links', () => {
    const adminLinks: NavLink[] = [
      {
        name: 'Repos',
        url: '/repos',
        noBaseUrl: true,
        view: undefined,
      },
    ];
    const topMenus = [
      {
        name: 'Browse',
        items: [
          {
            name: 'Manage',
            target: '_blank',
            url: 'https://gerrit/plugins/plugin-manager/static/index.html',
          },
        ],
      },
    ];
    assert.deepEqual(
      element.computeLinks(
        /* userLinks= */ [],
        adminLinks,
        topMenus,
        /* baseDocUrl= */ '',
        /* defaultLinks= */ []
      ),
      [
        {
          title: 'Browse',
          links: [
            adminLinks[0],
            {
              name: 'Manage',
              url: 'https://gerrit/plugins/plugin-manager/static/index.html',
            },
          ],
        },
      ]
    );
  });

  test('shows feedback icon when URL provided', async () => {
    assert.isEmpty(element.feedbackURL);
    assert.isNotOk(query(element, '.feedbackButton > a'));

    const url = 'report_bug_url';
    const config: ServerInfo = {
      ...createServerInfo(),
      gerrit: {
        ...createGerritInfo(),
        report_bug_url: url,
      },
    };
    element.retrieveFeedbackURL(config);
    await element.updateComplete;

    assert.equal(element.feedbackURL, url);
    assert.ok(query(element, '.feedbackButton > a'));
  });

  test('register URL', async () => {
    assert.isTrue(isHidden(query(element, '.registerDiv')));
    const config: ServerInfo = {
      ...createServerInfo(),
      auth: {
        auth_type: AuthType.LDAP,
        register_url: 'https//gerrit.example.com/register',
        editable_account_fields: [],
      },
    };
    element.retrieveRegisterURL(config);
    await element.updateComplete;
    assert.equal(element.registerURL, config.auth.register_url);
    assert.equal(element.registerText, 'Sign up');
    assert.isFalse(isHidden(query(element, '.registerDiv')));

    config.auth.register_text = 'Create account';
    element.retrieveRegisterURL(config);
    await element.updateComplete;
    assert.equal(element.registerURL, config.auth.register_url);
    assert.equal(element.registerText, config.auth.register_text);
    assert.isFalse(isHidden(query(element, '.registerDiv')));
  });

  test('register URL ignored for wrong auth type', async () => {
    const config: ServerInfo = {
      ...createServerInfo(),
      auth: {
        auth_type: AuthType.OPENID,
        register_url: 'https//gerrit.example.com/register',
        editable_account_fields: [],
      },
    };
    element.retrieveRegisterURL(config);
    await element.updateComplete;
    assert.equal(element.registerURL, '');
    assert.equal(element.registerText, 'Sign up');
    assert.isTrue(isHidden(query(element, '.registerDiv')));
  });

  test('copyPublicAvailableUrlButton rendering', async () => {
    await fullElementUpdateComplete(element);
    // publicAvailableUrl is equal to window.location.href because
    // PRIVATE_TO_PUBLIC_HOST_MAP is not set in the test.
    // It shouldn't be visible
    assert.isTrue(isHidden(query(element, '.copyPublicAvailableUrlButton')));
    assert.equal(element.publicAvailableUrl, window.location.href);

    // The button becomes visible if publicAvailableUrl is different from
    // window.location.href
    element.publicAvailableUrl = 'https://test-host.googlesource.com/a/b';
    await fullElementUpdateComplete(element);
    const copyBtn = queryAndAssert<HTMLAnchorElement>(
      element,
      '.copyPublicAvailableUrlButton'
    );
    assert.isTrue(isVisible(copyBtn));
    assert.equal(copyBtn.href, 'https://test-host.googlesource.com/a/b');

    // The button is hidden again if publicAvailableUrl is equal to
    // window.location.href
    element.publicAvailableUrl = window.location.href;
    await fullElementUpdateComplete(element);;
    assert.isTrue(isHidden(query(element, '.copyPublicAvailableUrlButton')));
  });

  test('copyPublicAvailableUrlButton copies link on click', async () => {
    element.publicAvailableUrl = 'https://test-host.googlesource.com/a/b';
    await fullElementUpdateComplete(element);
    const copyBtn = queryAndAssert<HTMLAnchorElement>(
      element,
      '.copyPublicAvailableUrlButton'
    );
    const clipboardSpy = sinon.spy(navigator.clipboard, 'writeText');
    MockInteractions.click(copyBtn);
    assert.isTrue(
      clipboardSpy.calledOnceWith('https://test-host.googlesource.com/a/b')
    );
  });

  test('publicAvailableUrl updates on location-changed event', async() => {
    const url = new URL(window.location.href);
    await fullElementUpdateComplete(element);
    window.PRIVATE_TO_PUBLIC_HOST_MAP = {[url.host]: 'test-host.abc' }
    element.dispatchEvent(new CustomEvent(EventType.LOCATION_CHANGE));
    await fullElementUpdateComplete(element);
    assert.isTrue(element.publicAvailableUrl.indexOf('test-host.abc') >= 0);
  });
});
