/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {ServerInfo, WebLinkInfo} from '../api/rest-api';

// visible for testing
export function getCodeBrowserWeblink(weblinks: WebLinkInfo[]) {
  // is an ordered allowed list of web link types that provide direct
  // links to the commit in the url property.
  const codeBrowserLinks = ['gitiles', 'browse', 'gitweb', 'code search'];
  for (let i = 0; i < codeBrowserLinks.length; i++) {
    const weblink = weblinks.find(
      weblink => weblink.name?.toLowerCase() === codeBrowserLinks[i]
    );
    if (weblink) return weblink;
  }
  return undefined;
}

export function computeMainCodeBrowserWeblink(
  weblinks?: WebLinkInfo[],
  config?: ServerInfo
): WebLinkInfo | undefined {
  if (!weblinks) return undefined;

  // Use primary weblink if configured and exists.
  const primaryWeblinkName = config?.gerrit?.primary_weblink_name;
  if (primaryWeblinkName) {
    const weblink = weblinks.find(link => link.name === primaryWeblinkName);
    if (weblink) return weblink;
  }

  return getCodeBrowserWeblink(weblinks);
}

export function getChangeWeblinks(
  weblinks?: WebLinkInfo[],
  config?: ServerInfo
): WebLinkInfo[] {
  if (!weblinks?.length) return [];
  const commitWeblink = computeMainCodeBrowserWeblink(weblinks, config);
  return weblinks.filter(
    weblink => !commitWeblink?.name || weblink.name !== commitWeblink.name
  );
}
