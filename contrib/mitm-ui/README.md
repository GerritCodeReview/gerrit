# Scripts for PolyGerrit local development against prod using MitmProxy.

## Installation (OSX)

1. Install Docker from http://docker.com
2. Start the proxy and create a new proxied browser instance
   ```
   cd ~/gerrit
   ~/mitm-gerrit/mitm-serve-app-dev.sh
   ```
3. Install MITM certificates
   - Open http://mitm.it in the proxied browser window
   - Follow the instructions to install MITM certs

## Usage

### Add or replace a single plugin containing static content

To develop unminified plugin that loads multiple files, use this.

1. Create a new proxied browser window and start mitmproxy via Docker:
   ```
   ~/mitm-gerrit/mitm-single-plugin.sh ./path/to/static/plugin.html
   ```
2. Open any *.googlesource.com domain in proxied window
3. plugin.html and ./path/to/static/* will be served

### Add or replace a minified plugin for *.googlesource.com

This flow assumes no additional .html/.js are needed, i.e. the plugin is a single file.

1. Create a new proxied browser window and start mitmproxy via Docker:
   ```
   ~/mitm-gerrit/mitm-plugins.sh ./path/to/plugin.html,./maybe/one/more.js
   ```
2. Open any *.googlesource.com domain in proxied window
3. plugin.html and more.js are served

### Force or replace default site theme for *.googlesource.com

1. Create a new proxied browser window and start mitmproxy via Docker:
   ```
   ~/mitm-gerrit/mitm-theme.sh ./path/to/theme.html
   ```
2. Open any *.googlesource.com domain in proxied window
3. Default site themes are enabled.
4. Local `theme.html` content replaces `/static/gerrit-theme.html`
5. `/static/*` URLs are served from local theme directory, i.e. `./path/to/`

### Serve uncompiled PolyGerrit

1. Create a new proxied browser window and start mitmproxy via Docker:
   ```
   cd ~/gerrit
   ~/mitm-gerrit/mitm-serve-app-dev.sh
   ```
2. Open any *.googlesource.com domain in proxied window
3. Instead of prod UI (gr-app.html, gr-app.js), local source files will be served
