# How to deploy this library to maven central

I always seem to forget how to go about this, so here tis all
documented. The [official
guide](http://central.sonatype.org/pages/ossrh-guide.html) has more.

## Before you start

Ensure GPG is installed and that your key details are in `~/.gnugpg`

You'll also need to make sure that you have configured your github login information in your `~/.m2/settings.xml`. Something like:

```xml
      <profile>
        <id>github</id>
        <properties>
          <github.global.userName>username</github.global.userName>
          <github.global.password>password</github.global.password>
        </properties>
      </profile>
```

## Step 1 - Update README and pom.xml

- Ensure library version is set to the version you want to release, appended with "-SNAPSHOT"
- In Changelog at bottom of README:
  - Add what was changed since the last release
  - Change name of `Development version` to `Release <version> on <date>`
  - Add new empty `Development version <next-version>-SNAPSHOT (current Git `master`)
- Change current version in Installation area
- commit to master as "for release" or something

## Step 2 - Publish snapshot to to sonatype maven repo

`mvn clean deploy` (enter GPG passphrase)

## Step 3 - Publish candidate to staging repo

1. `mvn release:clean`
1. `mvn release:prepare`
1. `mvn release:perform`

## Step 4 - Release candidate to maven central

1. Navigate to the [sonatype staging repositories](https://oss.sonatype.org/index.html#stagingRepositories)
1. Login using your credentials
1. Go to Staging Repositories page.
1. Select a staging repository (memoocar)
1. Click the Close button (with any reason, e.g "release")
1. You might have to refresh to see the change
1. Select and click release. Done!

The mvn release plugin will have automatically updated the project
version and committed and pushed to origin
