## What is services folder

'services' folder contains services used across multiple components.

## What should be considered as services in Gerrit UI

Services should be stateful, if its just pure functions, consider having them in 'utils' instead.

Regarding all stateful should be considered as services or not, it's still TBD. Will update as soon
as it's finalized.

## Future plans

To make services much easier to use, we may need to adopt a DI (dependency injection) system instead of exporting singleton
from the services directly. And it will help in mocking services in tests as well.

## What services we have

### Flags

'flags' is a service to provide easy access to all enabled experiments.

```
import {flags} from "./flags.js";

// check if an experiment is enabled or not
if (flags.isEnabled('test')) {
  // do something
}
```