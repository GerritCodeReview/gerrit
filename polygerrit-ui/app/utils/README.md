'utils' folder contains utils used across multiple components / services.

Similar utils should be defined in the same file and keep them small.

To make it easier to use, all utils can be exported within 'index.js' so when use, we can import all utils from same entry point, be careful of naming to avoid collisions or rename when export from 'index.js'.

```
import {someUtil} from "path_utils_folder/index.js";
```