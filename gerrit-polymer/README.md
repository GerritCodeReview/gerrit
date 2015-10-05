# Polygerrit

### Install dependencies

*Note*: To run Selenium tests on Safari, you will need to manually download the
latest Safari extension:
https://code.google.com/p/selenium/issues/detail?id=7933#c23


#### Quick-start

With Node.js installed, run the following one liner from the root of your
PolyGerrit download:

```sh
npm install -g gulp bower && npm install && cd app && bower install
```

#### Prerequisites (for everyone)

PolyGerrit requires the following major dependencies:

- Node.js, used to run JavaScript tools from the command line.
- npm, the node package manager, installed with Node.js and used to install
  Node.js packages.
- gulp, a Node.js-based build tool.
- bower, a Node.js-based package manager used to install front-end packages
  (like Polymer).

**To install dependencies:**

1)  Check your Node.js version.

```sh
node --version
```

The version should be at or above 0.12.x.

2)  If you don't have Node.js installed, or you have a lower version, go to
    [nodejs.org](https://nodejs.org) and click on the big green Install button.

3)  Install `gulp` and `bower` globally.

```sh
npm install -g gulp bower
```

This lets you run `gulp` and `bower` from the command line.

4)  Install the app's local `npm` and `bower` dependencies.

```sh
cd gerrit-polymer && npm install && cd app && bower install
```

### Development workflow

#### Run tests

```sh
gulp test:local
```

This runs the unit tests defined in the `app/test` directory through
[web-component-tester](https://github.com/Polymer/web-component-tester).

To run tests Java 7 or higher is required. To update Java go to
http://www.oracle.com/technetwork/java/javase/downloads/index.html and
download ***JDK*** and install it.

#### Build & Vulcanize

```sh
gulp
```

Build and optimize the current project, ready for deployment. This includes
linting as well as vulcanization, image, script, stylesheet and HTML
optimization and minification.

### Adding JavaScript files so they're picked up by the build process

At the bottom of `app/index.html`, you will find a build block that can be used
to include additional scripts for your app. Build blocks are just normal script
tags that are wrapped in a HTML comment that indicates where to concatenate and
minify their final contents to.

Below, we've added in `script2.js` and `script3.js` to this block. The line
`<!-- build:js scripts/app.js -->` specifies that these scripts will be squashed
into `scripts/app.js` during a build.

```html
<!-- build:js scripts/app.js -->
<script src="scripts/app.js"></script>
<script src="scripts/script2.js"></script>
<script src="scripts/script3.js"></script>
<!-- endbuild-->
```
