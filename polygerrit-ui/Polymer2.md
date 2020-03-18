Note: Gerrit has moved to polymer 3 as of submitted of https://gerrit-review.googlesource.com/q/topic:%22bower+to+npm+packages+switch%22+(status:open%20OR%20status:merged).

The change is backward compatible, so no code change needed to support all plugins, but we would highly recommend to start moving to latest polymer 3 for all plugins, check out [Polymer3.md](./Polymer3.md) for more insights.

## Polymer 2 upgrade

Gerrit is updating to use polymer 2 from polymer 1 by following the [Polymer 2.0 upgrade guide](https://polymer-library.polymer-project.org/2.0/docs/upgrade).

Polymer 2 contains several breaking changes that may affect some of the UI features and plugins. One of the biggest change is to have the shadow DOM enabled. This will affect how you query elements inside of your component, how css style works within and across components, and several other usages.

If you are owner of any plugins, please start following the [Polymer 2.0 upgrade guide](https://polymer-library.polymer-project.org/2.0/docs/upgrade) to migrate your plugins to be polymer 2 ready.

If you notice any issues or need help with anything, don't hesitate to report to us [here](https://bugs.chromium.org/p/gerrit/issues/list).


### Related resources

- [Polymer 2.0 upgrade guide](https://polymer-library.polymer-project.org/2.0/docs/upgrade)
- [Polymer Shadow DOM](https://polymer-library.polymer-project.org/2.0/docs/devguide/shadow-dom)
