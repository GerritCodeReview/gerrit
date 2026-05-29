/**
 * @license
 * Copyright 2026 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {svg, SVGTemplateResult} from 'lit';

/** Gerrit logo in color. */
const gerrit = svg`<svg width="52" height="52" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 52 52">
<rect ry="4" rx="4" height="40" width="40" y="0" x="0" fill="#ffaaaa"/>
<rect ry="4" rx="4" height="40" width="40" y="12" x="12" fill="#aaffaa"/>
<path d="m18,22l12,0l0,4l-12,0l0,-4z" fill="#ff0000"/>
<path d="m34,22l12,0l0,4l-12,0l0,-4z" fill="#ff0000"/>
<path d="m18,36l4,0l0,-4l4,0l0,4l4,0l0,4l-4,0l0,4l-4,0l0,-4l-4,0l0,-4z" fill="#008000"/>
<path d="m34,36l4,0l0,-4l4,0l0,4l4,0l0,4l-4,0l0,4l-4,0l0,-4l-4,0l0,-4z" fill="#008000"/>
</svg>`;

/** AI spark icon representing Gemini tools.*/
const spark = svg`<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 -960 960 960" fill="#1f1f1f"><path d="M480-80q-6 0-11-4t-7-10q-17-67-51-126t-83-108q-49-49-108-83T94-462q-6-2-10-7t-4-11q0-6 4-11t10-7q67-17 126-51t108-83q49-49 83-108t51-126q2-6 7-10t11-4q6 0 10.5 4t6.5 10q18 67 52 126t83 108q49 49 108 83t126 51q6 2 10 7t4 11q0 6-4 11t-10 7q-67 17-126 51t-108 83q-49 49-83 108T498-94q-2 6-7 10t-11 4Z"/></svg>`;

export const customIcons: {[name: string]: SVGTemplateResult} = {
  // go/keep-sorted start
  ai: spark,
  gerrit,
  spark,
  // go/keep-sorted end
};
