# Fork-only Chronotif CI (lgg)

This file and `.github/workflows/fork-windows-test.yml` belong **only to this fork**.
Do not cherry-pick or merge these files into `nagarete/chronotif`.
The upstream `.github/workflows/build.yml` remains unmodified by feature changes.

## Build on the existing Windows PC

The Windows x64 runner named `chronotif-runner-ci` must be registered with
`lgg/chronotif` and show **Online** in GitHub → Settings → Actions → Runners.
The workflow now requires only GitHub's standard labels `self-hosted`,
`Windows`, and `X64` — its runner **name does not need to be a label**.
This is safe when the intended runner is the only Windows x64 runner
available to this repository.

- Existing JDK 17: `C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot`
- Existing Android SDK root: `D:\Android\Sdk`
- Verified installed October 29, 2026: Android SDK `platforms;android-36` and `build-tools;36.0.0` (both local Test-Path checks returned True).
- Git and `gh` already installed.
- Wrapper 9.8.2 and Android Gradle Plugin 9.4.1 may download Gradle/Maven
  dependencies into `D:\Android\gradle-chronotif` the first time.

If these SDK packages ever need reinstalling, use the command below manually:

```powershell
& "D:\Android\Sdk\cmdline-tools\latest\bin\sdkmanager.bat" --sdk_root="D:\Android\Sdk" "platforms;android-36" "build-tools;36.0.0"
```

The CI workflow never calls a JDK installer, Android SDK installer or `setup-java`.
It fails clearly when required local packages are absent. The Gradle Wrapper
is permitted to fetch project dependencies as part of the build.

## Build test APK

In GitHub, enable Actions for this fork if they are disabled. Open
**Actions → Fork-only Windows CI (Chronotif Test) → Run workflow**.
Keep the *workflow branch* on `main` (the workflow file lives only there).
Use `feature/top-edge-swipe-trigger` as the `source_ref` input. The run
builds that branch with the fork-only debug `app.chronotif.dev` suffix added
in the runner workspace. It publishes a directly installable APK in **Releases**,
with no Actions artifact upload.

The workflow has **no PR, push, or schedule trigger** and is restricted to actor
`lgg` on repository `lgg/chronotif`. Never use untrusted external code as the
`source_ref` on a personal self-hosted runner.

## Sending the feature upstream

Open a new pull request from `lgg:feature/top-edge-swipe-trigger` to
`nagarete:main`, not from the fork's own `main` branch. The feature branch
is based on the upstream code, keeps original `.github/workflows/build.yml`
unchanged and contains no machine-specific CI configuration. The fork-only
workflow on `lgg:main` is not part of that PR.
