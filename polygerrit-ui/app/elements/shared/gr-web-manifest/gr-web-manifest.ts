/**
 * @license
 * Copyright 2024 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

const $_linkTag = document.createElement('link');
$_linkTag.id = 'manifest';
$_linkTag.rel = 'manifest';
$_linkTag.href = '/manifest.webmanifest';

document.head.appendChild($_linkTag);

export {};
