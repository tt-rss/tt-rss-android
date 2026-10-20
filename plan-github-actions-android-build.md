# Plan: GitHub Actions Android build

## Goal

Run the project checks on GitHub Actions. Publish the debug APK and a GitHub Release for `master`. Publish a signed APK and a GitHub Release for `v*` tags. Keep two workflow files.

## Verified current state

- The app is a single Gradle module: `:org.fox.ttrss`.
- CI uses JDK 21 (Temurin). The Gradle wrapper is 9.8.1.
- The build uses `compileSdk 37`, `targetSdk 36`, and `minSdk 24`.
- `./gradlew --no-daemon check assembleDebug` runs ErrorProne, lint, and JVM tests.
- `.github/workflows/validate-pr.yml` runs on pull requests to `master`.
- `.github/workflows/build-debug-apk.yml` runs on pushes to `master` and on manual dispatch.
- The master workflow restores `~/.android/debug.keystore` from `DEBUG_KEYSTORE_BASE64`.
- The debug keystore uses store password `android`, alias `androiddebugkey`, and key password `android`.
- The build reads the version code and name from the HEAD commit date and short hash only.
- A shallow checkout at depth 1 gives the HEAD commit. Full history is not necessary.
- The debug application ID is `org.fox.ttrss.debug`.
- The debug APK path is `org.fox.ttrss/build/outputs/apk/debug/org.fox.ttrss-debug.apk`.
- The `signed` build type reads the `SIGNING_*` project properties.
- The signed APK path is `org.fox.ttrss/build/outputs/apk/signed/org.fox.ttrss-signed.apk`. Verify this path at implementation time.
- `.github/scripts/generate-lint-summary.sh` reads `lint-results-debug.xml`.
- Dependabot updates GitHub Actions and Gradle dependencies.

## Decisions

1. Pull request runs do validation only. They do not upload an APK.
2. A push to `master` makes a GitHub Release. The release tag is `YY.MM-<shortsha>`. The title is the same as the tag. The body is `Debug build for commit <full SHA>. Workflow run: <run URL>.` The release attaches the debug APK.
3. A tag that matches `v*` makes a signed APK and a GitHub Release. The release title and tag are the tag name. The body is `Signed build for tag <tag>. Workflow run: <run URL>.` The release attaches the signed APK. The Gradle task is `./gradlew --no-daemon check assembleSigned`. The secrets are `SIGNING_STORE_BASE64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, and `SIGNING_KEY_PASSWORD`.
4. APK and report artifacts are kept for 14 days.
5. The repository keeps two workflow files. Do not add a third workflow.

Release mechanism (master debug release, and signed tag release):

- Add a step with `id: meta` after checkout and before any use of its outputs in each job:
  ```
  - id: meta
    shell: bash
    run: |
      echo "short_sha=${GITHUB_SHA::7}" >> "$GITHUB_OUTPUT"
      echo "tag=$(git log -1 --date=format:'%y.%m' --format:'%cd')-$(git rev-parse --short=7 HEAD)" >> "$GITHUB_OUTPUT"
  ```
- Use `${{ steps.meta.outputs.tag }}` and `${{ steps.meta.outputs.short_sha }}` in later steps. Shell variables do not persist across steps.
- Run the master release commands in one `run` block. Start it with `TAG="${{ steps.meta.outputs.tag }}"`. Build `BODY="Debug build for commit $GITHUB_SHA. Workflow run: $GITHUB_SERVER_URL/$GITHUB_REPOSITORY/actions/runs/$GITHUB_RUN_ID."` in the same block.
- Write `APK` to `$GITHUB_ENV` in the step that finds it. Use `$APK` in later steps.
- The run URL is `$GITHUB_SERVER_URL/$GITHUB_REPOSITORY/actions/runs/$GITHUB_RUN_ID`.
- Check the tag:
  ```
  if git rev-parse -q --verify "refs/tags/$TAG" >/dev/null; then
    test "$(git rev-parse "$TAG^{commit}")" = "$GITHUB_SHA" || { echo "Tag $TAG points to another commit"; exit 1; }
  fi
  ```
- Publish the release with explicit conditionals. A bare `gh release view` exits nonzero when the release is absent:
  ```
  if gh release view "$TAG" >/dev/null 2>&1; then
    gh release edit "$TAG" --notes "$BODY"
    gh release upload "$TAG" dist/tt-rss-debug.apk --clobber
  else
    gh release create "$TAG" --target "$GITHUB_SHA" --title "$TAG" --notes "$BODY" dist/tt-rss-debug.apk
  fi
  ```
- Use `GH_TOKEN: ${{ github.token }}`.
- Use the stable asset name `tt-rss-debug.apk` for the master release. Use `tt-rss-signed.apk` for the signed tag release. Copy the APK to that name before upload.
- The tag `YY.MM-<shortsha>` does not match `v*`. The release does not start a tag build.

## Task 1: Workflow layout

- Keep `.github/workflows/validate-pr.yml`. It runs on `pull_request` to `master`.
- Keep `.github/workflows/build-debug-apk.yml`. It runs on `push` to `master`, `push` tags `v*`, and `workflow_dispatch`.
- Remove `.github/**` from `paths-ignore` in both workflows. Keep `utils/**`, `.*`, and `*.md`.
- Keep the concurrency groups. Cancel an older run for the same pull request or branch.
- Set `permissions: contents: read` at workflow level.
- Add these jobs to the build workflow:
  - `build-debug` runs when `github.ref == 'refs/heads/master'`. It runs the checks and `assembleDebug`. It uploads the debug APK artifact and the reports.
  - `release` needs `build-debug` and runs when `github.event_name == 'push' && github.ref == 'refs/heads/master'`. It has `permissions: contents: write` and `actions: read`. It downloads the APK artifact and publishes the release.
  - `build-signed` runs when `startsWith(github.ref, 'refs/tags/v')`. It runs the checks and `assembleSigned`. It uploads the signed APK artifact and the reports. It has `permissions: contents: write` and publishes the signed GitHub Release for the `v*` tag.
- A manual dispatch on `master` runs the debug job only. It makes an artifact and no release. A manual dispatch on a `v*` tag runs the signed job.
- The PR workflow has no secrets and no APK upload.

Acceptance:

- A pull request to `master` starts one validation run.
- A push to `master` starts the debug job and the release job.
- A `v*` tag starts the signed job.
- A workflow-only change starts a run.
- A root-level `*.md` change does not start a run.
- A new push cancels the older run for the same ref.

## Task 2: Toolchain

Use these pins:

| Action | Pin |
|---|---|
| `actions/checkout` | `3d3c42e5aac5ba805825da76410c181273ba90b1` # v7.0.1 |
| `actions/setup-java` | `b843ebf9b913146b38da2eaa7aeddf76074ae1ad` # v6.1.0 |
| `gradle/actions/setup-gradle` | `3f5f9adaf7d9fecd50b5935e54106014257a94e6` # v6.4.0 |
| `actions/upload-artifact` | `cf430e030ddbb5b0abf93d22962f4752f3646cd9` # v7.0.2 |
| `android-actions/setup-android` | `9fc6c4e9069bf8d3d10b2204b1fb8f6ef7065407` # v3.2.2 |

- Check out with the default shallow depth of 1. Do not set `fetch-depth`.
- Set up Temurin JDK 21.
- Set up Android with `android-actions/setup-android`.
- Run these commands in each build job in both workflows:

```
         test -n "${ANDROID_SDK_ROOT:-}"
         sdkmanager "platforms;android-37.0" "build-tools;37.0.0"
         test -d "$ANDROID_SDK_ROOT/platforms/android-37.0"
java -version
sdkmanager --list_installed
```

- Use `gradle/actions/setup-gradle` for the Gradle cache.
- The Gradle cache helps dependency downloads only. It does not cache the Android SDK. Accept the SDK setup time.
- Pin every action to a full SHA. Add the version in a comment. Let Dependabot update the pins.

Acceptance:

- A clean runner builds without a preinstalled SDK.
- The log shows Java 21 and Android platform 37.
- The Gradle dependency cache restores on later runs.
- Every `uses:` entry has a full SHA.

## Task 3: Checks and build

- Run `./gradlew --no-daemon check assembleDebug` in the PR workflow and the master debug job.
- Run `./gradlew --no-daemon check assembleSigned` in the signed tag job.
- Do not add `|| true` or other error suppression.
- Keep the lint summary step with `if: always()`.
- Keep the lint reports at `org.fox.ttrss/build/reports/lint-results-debug.xml` and `org.fox.ttrss/build/reports/lint-results-debug.html`. Use the globs `org.fox.ttrss/build/reports/lint-results-*.xml` and `org.fox.ttrss/build/reports/lint-results-*.html` in each build job. The `check` task may write a report for another variant.
- Keep the problems report at `build/reports/problems/problems-report.html`. Use the glob `**/build/reports/problems/problems-report.html`.
- The lint summary script reads `lint-results-debug.xml`. Keep it for the debug job. The signed job does not run the debug summary script. It uploads the lint report globs.

Acceptance:

- ErrorProne, lint, and JVM tests run.
- A lint error fails the job.
- A failed debug or PR check still makes the lint summary.
- A successful run makes the APK at the documented path.

## Task 4: Debug keystore

- The PR workflow uses no secrets.
- The master debug job restores `DEBUG_KEYSTORE_BASE64` into `$HOME/.android/debug.keystore`.
- Fail early with a clear message when the secret is absent.
- Validate the keystore with this command:

```
keytool -list -keystore "$HOME/.android/debug.keystore" -storepass android -alias androiddebugkey
```

- Keytool metadata output is allowed.
- Do not print `DEBUG_KEYSTORE_BASE64`, the release signing secrets, or their values.
- The debug store password `android` is public. Gradle also uses alias `androiddebugkey` and key password `android`.
- The only password in a log is the public debug password `android`. Do not print any other password. Do not use `set -x` around secret steps.

Acceptance:

- A missing secret fails the job with a clear message.
- A valid secret makes a signed debug APK.
- No step prints secret material.
- PR runs work on forks without secrets.

## Task 5: Artifacts and release

- Add the `id: meta` step after checkout and before any use of its outputs in each build job and in the release job.
- Name the debug artifact `tt-rss-debug-${{ github.run_number }}-${{ steps.meta.outputs.short_sha }}`.
- Name the signed artifact `tt-rss-signed-${{ github.run_number }}-${{ steps.meta.outputs.short_sha }}`.
- Name the report artifact `tt-rss-reports-${{ github.run_number }}-${{ steps.meta.outputs.short_sha }}`.
- In shell steps that need the short SHA, pass `SHORT_SHA: ${{ steps.meta.outputs.short_sha }}` through `env:`.
- `actions/upload-artifact` stores each artifact as a zip archive. Do not use `archive: false`. Use the artifact `name:` for downloads.
- Set `overwrite: true` on every `upload-artifact` step, including report uploads. A re-run keeps the same artifact name. `overwrite: true` replaces the earlier artifact.
- Upload each APK with `if: success()` and `if-no-files-found: error`.
- Upload the reports with `if: always()`, the lint XML and HTML globs, the problems report glob, and `if-no-files-found: warn`. Set `continue-on-error: true` on report uploads so a report upload failure cannot block a release.
- Set `retention-days: 14` on every upload.
- The PR workflow does not upload an APK. The report uploads apply to the build workflow jobs. The PR workflow keeps the lint summary step.
- Do not rename the build output. The debug APK path stays `org.fox.ttrss/build/outputs/apk/debug/org.fox.ttrss-debug.apk`. Remove the existing APK rename step and `archive: false` from `build-debug-apk.yml`. Do not use `mv` on the build output.
- The release job checks out the repo with the default shallow depth and `fetch-tags: true`. It needs the tag ref and the `meta` step. Do not set `fetch-depth`.
- The release job downloads the debug artifact with `gh run download "$GITHUB_RUN_ID" --name "tt-rss-debug-${{ github.run_number }}-${{ steps.meta.outputs.short_sha }}" --dir dist`. The download name must match the upload name exactly.
- Locate the APK with `APK="$(find dist -name '*.apk' -print -quit)"`. Fail with a clear message if `APK` is empty.
- Copy the APK to the stable asset name with `cp "$APK" dist/tt-rss-debug.apk`. Use `dist/tt-rss-debug.apk` in the release commands.
- The release job uses `GITHUB_TOKEN` and `permissions: contents: write`. The signed job also uses `contents: write` for its release. All other jobs use `contents: read`.

Acceptance:

- The debug artifact downloads and installs. It is a zip archive downloaded by its `name:`.
- The artifact name includes the run number and the 7-character short SHA.
- Reports upload even when checks fail.
- The release contains the debug APK with the asset name `tt-rss-debug.apk`.
- The documented debug APK path is unchanged.
- A re-run for the same commit reuses the same artifact name. `overwrite: true` replaces the earlier artifact, and the release notes update. The job does not fail.

## Task 6: Signed tag build

- Add the `build-signed` job to the build workflow. It has `permissions: contents: write`.
- Fail early when any of the four signing secrets is empty. Do not print the values. Use a step-level `env:` block:
```
env:
  SIGNING_STORE_BASE64: ${{ secrets.SIGNING_STORE_BASE64 }}
  SIGNING_STORE_PASSWORD: ${{ secrets.SIGNING_STORE_PASSWORD }}
  SIGNING_KEY_ALIAS: ${{ secrets.SIGNING_KEY_ALIAS }}
  SIGNING_KEY_PASSWORD: ${{ secrets.SIGNING_KEY_PASSWORD }}
run: |
  for name in SIGNING_STORE_BASE64 SIGNING_STORE_PASSWORD SIGNING_KEY_ALIAS SIGNING_KEY_PASSWORD; do
    test -n "${!name}" || { echo "Missing secret: $name"; exit 1; }
  done
```
- Decode the store in a step with the same four step-level `env:` mappings:
```
run: echo "$SIGNING_STORE_BASE64" | base64 --decode > "${{ runner.temp }}/release.keystore"
```
- Set the Gradle step `env:` at step level. Do not put `runner.temp` in a job-level `env:` block:
```
env:
  ORG_GRADLE_PROJECT_SIGNING_STORE_FILE: ${{ runner.temp }}/release.keystore
  ORG_GRADLE_PROJECT_SIGNING_STORE_PASSWORD: ${{ secrets.SIGNING_STORE_PASSWORD }}
  ORG_GRADLE_PROJECT_SIGNING_KEY_ALIAS: ${{ secrets.SIGNING_KEY_ALIAS }}
  ORG_GRADLE_PROJECT_SIGNING_KEY_PASSWORD: ${{ secrets.SIGNING_KEY_PASSWORD }}
```
- Run `./gradlew --no-daemon check assembleSigned`.
- Find the APK with `APK="$(find org.fox.ttrss/build/outputs/apk/signed -name '*.apk' -print -quit)"`. Do not guess the file name. Fail with a clear message if `APK` is empty.
- Copy the APK to the stable asset name with `cp "$APK" "tt-rss-signed.apk"`.
- Publish the signed release with explicit conditionals. Use `$GITHUB_REF_NAME` as the tag and the title:
```
TAG="$GITHUB_REF_NAME"
BODY="Signed build for tag $TAG. Workflow run: $GITHUB_SERVER_URL/$GITHUB_REPOSITORY/actions/runs/$GITHUB_RUN_ID."
if gh release view "$TAG" >/dev/null 2>&1; then
  gh release edit "$TAG" --notes "$BODY"
  gh release upload "$TAG" tt-rss-signed.apk --clobber
else
  gh release create "$TAG" --title "$TAG" --notes "$BODY" tt-rss-signed.apk
fi
```
- Do not use `--target` for an existing tag.
- Never print the signing secrets.

Acceptance:

- A `v*` tag makes a signed APK.
- A `v*` tag makes a GitHub Release with the tag name as the title and the asset `tt-rss-signed.apk`.
- A re-run updates the same release notes and asset, and reuses the same artifact name.
- The tag job fails with a clear message when a signing secret is absent.
- No step prints signing secret material.

## Task 7: CI documentation

- Add `.github/CI.md`.
- List these secrets and their creation commands:
  - `DEBUG_KEYSTORE_BASE64`: `base64 < "$HOME/.android/debug.keystore" | tr -d '\n' | gh secret set DEBUG_KEYSTORE_BASE64`
  - `SIGNING_STORE_BASE64`: `base64 < release.keystore | tr -d '\n' | gh secret set SIGNING_STORE_BASE64`
  - `SIGNING_STORE_PASSWORD`: `gh secret set SIGNING_STORE_PASSWORD`
  - `SIGNING_KEY_ALIAS`: `gh secret set SIGNING_KEY_ALIAS`
  - `SIGNING_KEY_PASSWORD`: `gh secret set SIGNING_KEY_PASSWORD`
- Record the debug keystore values: store password `android`, alias `androiddebugkey`, key password `android`.
- Record the triggers: a `master` push creates the debug release `YY.MM-<shortsha>`; a `v*` tag creates the signed release.
- Record that `GITHUB_TOKEN` is automatic.
- Record that repository secrets are not branch-scoped.

## Task 8: Verification

- Do not run actionlint. It is not in the repository. Do not add it to CI.
- Open a test pull request. Confirm that validation runs and passes. Confirm that no APK artifact exists.
- Push to `master`. Confirm the debug artifact and the release. Confirm the tag name `YY.MM-<shortsha>`. Confirm the asset name `tt-rss-debug.apk`.
- Push a `v*` tag. Confirm the signed APK artifact and the GitHub Release. Confirm that the release title and tag are the tag name. Confirm the asset name `tt-rss-signed.apk`.
- Push a workflow-only change. Confirm that a run starts.
- Push a root-level `*.md` change. Confirm that CI skips the run.
- Run a manual dispatch on `master`. Confirm that it makes an artifact and no release.
- Rerun the master job for the same commit. Confirm the artifact name is stable. Confirm that `overwrite: true` replaces the artifact, the release notes show the new run URL, `--clobber` replaces `tt-rss-debug.apk`, and the job does not fail.
- Rerun the signed job for the same tag. Confirm the artifact name is stable. Confirm that `overwrite: true` replaces the artifact, the release notes show the new run URL, `--clobber` replaces `tt-rss-signed.apk`, and the job does not fail.
- Confirm that the problems report, lint XML, and lint HTML upload when a check fails.
- Run the restore step locally with `unset DEBUG_KEYSTORE_BASE64`. Confirm exit status 1 and the error message.
- Confirm that the workflow logs show no secret values.

## Risks and mitigations

- API 37 may be absent from the runner image. Install it with `sdkmanager` and fail clearly.
- The Gradle cache helps dependency downloads only. The SDK setup time is accepted.
- Third-party actions can change. Pin SHAs and use Dependabot.
- Fork pull requests cannot read secrets. Keep secrets out of the PR validation workflow.
- Release creation can run more than once. Use explicit `gh release view` conditionals, `gh release edit` for the notes, the tag check, stable asset names, and `--clobber`.
- A `YY.MM-<shortsha>` tag can exist without a release. Check that the tag points to the run commit before release.
- A missing signing secret can fail the tag job. Fail with a clear message.
- Workflow-only changes now start runs. This is intentional.
- The Gradle version logic reads HEAD only. A depth 1 checkout is sufficient.

## Definition of done

- The decisions are recorded.
- Both workflows pass on a clean runner.
- PR runs do validation only and upload no APK.
- A master push makes a release named `YY.MM-<shortsha>` with the debug APK. The asset name is `tt-rss-debug.apk`.
- A manual dispatch on `master` makes no release.
- A `v*` tag makes a signed APK and a GitHub Release. The release title and tag are the tag name. The asset name is `tt-rss-signed.apk`.
- Re-runs reuse stable artifact names, use `overwrite: true` on every upload, and use `gh release edit` to update the release notes. `--clobber` replaces the stable asset names. Re-runs do not fail.
- Artifacts are kept for 14 days. Each artifact is a zip archive downloaded by its `name:`.
- XML and HTML lint reports and the problems report are available after a failure.
- The documented debug APK path is unchanged.
- `.github/CI.md` documents every secret and the debug keystore values.
- No log prints secret material.
- Actions are pinned. Both workflows use SDK 37 and build-tools 37.0.0.
- Actionlint is not part of CI.
