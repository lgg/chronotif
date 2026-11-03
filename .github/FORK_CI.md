# Fork-only CI

The application source, Android accessibility/status-bar swipe implementation,
tests and official GitHub Actions workflow follow
[nagarete/chronotif](https://github.com/nagarete/chronotif).

The fork retains exactly one additional build workflow:
`.github/workflows/fork-windows-test.yml`.

- It builds reviewed commits on the existing self-hosted Windows x64 runner.
- On updates to `main`, it runs unit tests, lint and assembles the **upstream**
  debug application. The same workflow may be dispatched manually by `lgg`
  for trusted fork refs only.
- The installable test APK is an Actions artifact. The official upstream debug
  build uses `app.chronotif.debug` and can coexist with a stable installation.
- No Gradle source patching or separate transparent top-edge gesture exists.
  Follow upstream's optional accessibility-based status-bar swipe instead.
- The historical experimental pull requests are superseded by upstream
  [#29](https://github.com/nagarete/chronotif/pull/29),
  [#24](https://github.com/nagarete/chronotif/pull/24) and
  [#25](https://github.com/nagarete/chronotif/pull/25).

## Local Windows runner

Expected runner labels: `self-hosted`, `Windows`, `X64`.
The runner was previously named `chronotif-runner-ci`.

The workflow uses the owner's existing installation:

- Java 17: `C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot`
- Android SDK: `D:\Android\Sdk` (platform and build tools 36)
- Gradle cache: `D:\Android\gradle-chronotif`

It installs neither the JDK nor Android SDK. Avoid choosing unreviewed
branches on a self-hosted runner.
