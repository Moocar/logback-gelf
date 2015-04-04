# How to deploy this library to maven central

I always seem to forgt how to go about this, so here tis all documented. The [official guide] has more.

## Before you start

Ensure GPG is installed and that your key details are in `~/.gnugpg`

## Step 1 - Update README

- Ensure library version is set to the version you want to release, appended with "-SNAPSHOT"
- In Changelog at bottom of README:
  - Add what was changed since the last release
  - Change name of `Development version` to `Release <version> on <date>`
  - Add new empty `Development version <next-version>-SNAPSHOT (current Git `master`)
- Change current version in Installation area
- commit to master as "for release" or something

## Publish snapshot to to sonatype maven repo

`mvn clean deploy` (enter GPG passphrase)

## Publish candidate to staging repo

1. `mvn release:clean`
1. `mvn release:prepare`
1. `mvn release:perform`

## Release candidate to maven central

1. Navigate to the [sonatype staging repositories]
1. Login using your credentials
1. Go to Staging Repositories page.
1. Select a staging repository (memoocar)
1. Click the Close button (with any reason, e.g "release")
1. You might have to refresh to see the change
1. Select and click release. Done!

The mvn release plugin will have automatically updated the project
version and committed and pushed to origin

[official guide](http://central.sonatype.org/pages/ossrh-guide.html)
[sonatype staging repositories](https://oss.sonatype.org/index.html#stagingRepositories)