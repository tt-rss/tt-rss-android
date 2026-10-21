# GitHub Actions CI

## Workflows

- `Validate PR` runs on pull requests to `master`. It runs ErrorProne, lint, and the JVM tests. It uploads no APK.
- `Build APKs` runs on pushes to `master`, on `v*` tags, and on manual dispatch.
  - A `master` push builds the debug APK and creates release `YY.MM-<shortsha>`. The tag is a label only; it can differ from in-app versionName where Java week-year differs from calendar year or where abbrev length differs.
  - A `v*` tag builds the signed APK and creates a release with the tag name.
  - A manual dispatch on `master` builds the debug APK only. It creates no release.

Artifacts are kept for 14 days. Re-runs overwrite artifacts and release assets.

## Required secrets

| Secret | Purpose |
|---|---|
| `DEBUG_KEYSTORE_BASE64` | Base64 debug keystore for the `master` debug build. |
| `SIGNING_STORE_BASE64` | Base64 release keystore for the `v*` signed build. |
| `SIGNING_STORE_PASSWORD` | Store password for the release keystore. |
| `SIGNING_KEY_ALIAS` | Key alias for the release keystore. |
| `SIGNING_KEY_PASSWORD` | Key password for the release keystore. |

Create the secrets with `gh secret set`:

```bash
base64 < "$HOME/.android/debug.keystore" | tr -d '\n' | gh secret set DEBUG_KEYSTORE_BASE64
base64 < release.keystore | tr -d '\n' | gh secret set SIGNING_STORE_BASE64
gh secret set SIGNING_STORE_PASSWORD
gh secret set SIGNING_KEY_ALIAS
gh secret set SIGNING_KEY_PASSWORD
```

The debug keystore must use store password `android`, alias `androiddebugkey`, and key password `android`. `org.fox.ttrss/build.gradle` hardcodes these values. The release keystore can use any alias and passwords.

`GITHUB_TOKEN` is automatic. Repository secrets are not branch-scoped.
