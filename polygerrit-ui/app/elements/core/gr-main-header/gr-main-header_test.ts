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
import {isHidden, query, stubRestApi} from '../../../test/test-utils';
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

const basicFixture = fixtureFromElement('gr-main-header');

suite('gr-main-header tests', () => {
  let element: GrMainHeader;

  setup(() => {
    stubRestApi('probePath').returns(Promise.resolve(false));
    stub('gr-main-header', {
      _loadAccount() {
        return Promise.resolve();
      },
    });
    element = basicFixture.instantiate();
  });

  test('link visibility', () => {
    element.loading = true;
    assert.isTrue(isHidden(query(element, '.accountContainer')));

    element.loading = false;
    element.loggedIn = false;
    assert.isFalse(isHidden(query(element, '.accountContainer')));
    assert.isFalse(isHidden(query(element, '.loginButton')));
    assert.isFalse(isHidden(query(element, '.registerButton')));
    assert.isTrue(isHidden(query(element, '.registerDiv')));

    element._account = createAccountDetailWithId(1);
    flush();
    assert.isTrue(isHidden(query(element, 'gr-account-dropdown')));
    assert.isTrue(isHidden(query(element, '.settingsButton')));

    element.loggedIn = true;
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
      ].map(element._createHeaderLink),
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
        view: null,
      },
    ];

    // When no admin links are passed, it should use the default.
    assert.deepEqual(
      element._computeLinks(
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
      element._computeLinks(
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

    assert.deepEqual(element._getDocLinks(null, docLinks), []);
    assert.deepEqual(element._getDocLinks('', docLinks), []);
    assert.deepEqual(element._getDocLinks('base', []), []);

    assert.deepEqual(element._getDocLinks('base', docLinks), [
      {
        name: 'Table of Contents',
        target: '_blank',
        url: 'base/index.html',
      },
    ]);

    assert.deepEqual(element._getDocLinks('base/', docLinks), [
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
        view: null,
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
      element._computeLinks(
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
        view: null,
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
      element._computeLinks(
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
        view: null,
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
      element._computeLinks(
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
      element._computeLinks(
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
      element._computeLinks(
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
        view: null,
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
      element._computeLinks(
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
    assert.isEmpty(element._feedbackURL);
    assert.isNull(query(element, '.feedbackButton'));

    const url = 'report_bug_url';
    const config: ServerInfo = {
      ...createServerInfo(),
      gerrit: {
        ...createGerritInfo(),
        report_bug_url: url,
      },
    };
    element._retrieveFeedbackURL(config);
    await flush();

    assert.equal(element._feedbackURL, url);
    assert.ok(query(element, '.feedbackButton'));
  });

  test('register URL', () => {
    assert.isTrue(isHidden(query(element, '.registerDiv')));
    const config: ServerInfo = {
      ...createServerInfo(),
      auth: {
        auth_type: AuthType.LDAP,
        register_url: 'https//gerrit.example.com/register',
        editable_account_fields: [],
      },
    };
    element._retrieveRegisterURL(config);
    assert.equal(element._registerURL, config.auth.register_url);
    assert.equal(element._registerText, 'Sign up');
    assert.isFalse(isHidden(query(element, '.registerDiv')));

    config.auth.register_text = 'Create account';
    element._retrieveRegisterURL(config);
    assert.equal(element._registerURL, config.auth.register_url);
    assert.equal(element._registerText, config.auth.register_text);
    assert.isFalse(isHidden(query(element, '.registerDiv')));
  });

  test('register URL ignored for wrong auth type', () => {
    const config: ServerInfo = {
      ...createServerInfo(),
      auth: {
        auth_type: AuthType.OPENID,
        register_url: 'https//gerrit.example.com/register',
        editable_account_fields: [],
      },
    };
    element._retrieveRegisterURL(config);
    assert.equal(element._registerURL, '');
    assert.equal(element._registerText, 'Sign up');
    assert.isTrue(isHidden(query(element, '.registerDiv')));
  });
});
