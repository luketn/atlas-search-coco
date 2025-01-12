# atlas-search-coco
A demo project showing how to build a Java based faceted full text search service using the open source Coco dataset.

To get started run the docker compose file in the root of the project:

```bash
JAVA_TOOL_OPTIONS="" docker compose up java-app
```     
\* If you are using Mac OS Sequoia 15.2, you will need to set the JAVA_TOOL_OPTIONS environment variable to work around an issue with Java/Docker on this release of Mac OS:
```bash
JAVA_TOOL_OPTIONS="-XX:UseSVE=0" docker compose up java-app
```
