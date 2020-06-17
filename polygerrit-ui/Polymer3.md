## Gerrit in Polymer 3

Gerrit has migrated to polymer 3 as of submitted of submitted of https://gerrit-review.googlesource.com/q/topic:%22bower+to+npm+packages+switch%22+(status:open%20OR%20status:merged).

## Polymer 3 vs Polymer 2

The biggest difference between 2 and 3 is the changing of package management from bower to npm and also replaced the html imports with es6 imports so we no longer need templates in separate `html` files for polymer components.

### How that impact plugins

As of now, we still support all syntax in Polymer 2 and most from Polymer 1 with the [legacy layer](https://polymer-library.polymer-project.org/3.0/docs/devguide/legacy-elements). But we do plan to remove those in the future.

So we recommend all plugin owners to start migrating to Polymer 3 for your plugins. You can refer more about polymer 3 from the related resources section.

To get inspirations, check out our [samples here](https://gerrit.googlesource.com/gerrit/+/master/polygerrit-ui/app/samples).

### Plugin dependencies

Since most of Gerrit plugins are treated as sub modules and part of the Gerrit workspace when develop, dependencies of plugins are also defined and installed from Gerrit WORKSPACE, currently most of them are `bower_archives`. When moving to npm, if your plugin requires dependencies, you can have them added to your plugin's `package.json` and then link that file to `plugins/package.json` in gerrit.
Then use `@plugins_npm//:node_modules` to make sure `rollup_bundle` knows the right place to look for. More examples from `image-diff` plugin, [change 271672](https://gerrit-review.googlesource.com/c/plugins/image-diff/+/271672).

### Related resources

- [Polymer 3.0 upgrade guide](https://polymer-library.polymer-project.org/3.0/docs/upgrade)
-[What's new in Polymer 3.0](https://polymer-library.polymer-project.org/3.0/docs/about_30)