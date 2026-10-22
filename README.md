MultiPiston


### Building 26.3

Requires JDK 25, Git, and GNU `timeout`.

```sh
bash .github/scripts/provision-local-deps.sh multipiston
source build/ci-local-deps.env
./gradlew "-Dmaven.repo.local=$LOCAL_DEPS_MAVEN" build
```

Builds pinned BlockUI and Domum sources, plus Structurize. Dependencies retain their local versions in an isolated Maven repository under `build/`. Revision pins are in the script.
