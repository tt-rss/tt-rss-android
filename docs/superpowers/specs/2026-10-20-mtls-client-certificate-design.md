# mTLS Client Certificate Design

**Date:** 2026-10-20

**Status:** Approved for implementation planning

## Goal

Let a user import a PKCS#12 client certificate. The app then uses mutual TLS
(mTLS) with the configured tt-rss server. Username and password login stays
unchanged. The certificate applies to API calls and to image requests for the
tt-rss host only.

## Non-goals

- No PEM certificate and key import.
- No multiple certificates or server profiles.
- No client certificate for WebViews or inline article content.
- No custom server CA import.
- No option to disable server certificate validation.
- No export of the client certificate in the settings JSON.
- No automatic detection that a server requires mTLS.

## Key Decisions

- The import format is PKCS#12 (`.p12` or `.pfx`) with an optional password.
- The certificate and key persist encrypted in app-private, no-backup storage.
- The app stores PKCS#8 key bytes and DER certificate chain. It does not store
  the user's PKCS#12 password or a rewrapped PKCS#12 bundle.
- Android Keystore holds a non-exportable AES key. The app uses AES/GCM with a
  random IV and a 128-bit tag.
- Server trust stays on Android's platform default `X509TrustManager`. The
  existing `network_security_config.xml` trust anchors remain in effect.
- The app sends the certificate to the configured tt-rss origin only. It does
  not send it to other image hosts.
- mTLS clients follow same-origin redirects only.
- A confirmed import continues in an application-scoped manager. Fragment
  rotation cannot interrupt it.

## Architecture

New package: `org.fox.ttrss.mtls`.

| Class | Responsibility |
| --- | --- |
| `ClientCertificate` | Immutable identity: display name, subject, `notAfter` epoch milliseconds, key algorithm, private key, and X.509 chain. |
| `ClientCertificateParser` | Reads PKCS#12 bytes and a `char[]` password. Selects a private-key entry with a non-empty X.509 chain. Returns `ClientCertificate`. |
| `CertificateImportException` | Typed import failure with a `Reason`: unreadable, wrong password, missing key, missing chain, unsupported key, expired, not yet valid. |
| `CertificatePayloadCodec` | Pure Java versioned binary encoding of a `ClientCertificate`. Rebuilds the key with `KeyFactory` and the chain with `CertificateFactory`. |
| `ClientCertificateStore` | Encrypts, writes, loads, and clears the blob. Uses `AtomicFile` and an Android Keystore AES key. Reports file and key deletion results separately. |
| `ClientCertificateManager` | Owns state, the immutable snapshot, the load barrier, import, remove, and listener notifications. |
| `MutualTls` | Builds the platform trust manager, the in-memory PKCS#12 KeyStore, and the `SSLContext`. |
| `ApiClientSelector` | Pure selection of `PLAIN`, `MTLS`, or `ERROR` from manager state and the configured URL. |
| `ImageOriginPolicy` | Pure origin comparison for image URLs. |
| `RedirectPolicy` | Pure redirect method, body, and origin rules. |
| `SameOriginRedirectInterceptor` | Follows allowed redirects. Reads the current tt-rss origin on each redirect. |
| `ImageClientFactory` | Builds the plain and mTLS image clients. Adds the existing progress interceptor to both. |
| `DynamicImageCallFactory` | Stable `Call.Factory` for Glide. Selects the current client per request. |
| `CrossOriginImageCall` | `Call` wrapper. On a cross-origin 3xx response, closes it and reissues the redirect on the plain client. Forwards callbacks and cancellation. |

Modified files:

- `Application.java`: initialize the manager and start the background load.
- `ApiCommon.java`: call `ensureLoaded()`, select the client, cache clients by
  snapshot generation, map certificate errors, and skip SSL retries.
- `glide/OkHttpProgressGlideModule.java`: use `ImageClientFactory` and
  `DynamicImageCallFactory`.
- `NetworkPreferencesFragment.java`: import, replace, remove, summary, and
  listener lifecycle.
- `res/xml/preferences_network.xml`: import and remove preferences.
- `res/values/strings.xml`: all new user-visible text.

No new runtime dependency is necessary.

## State Machine

`ClientCertificateManager` has four states:

- `LOADING`: the first background load has not completed.
- `NONE`: no stored blob exists.
- `READY`: a valid snapshot exists.
- `ERROR`: a stored blob exists but cannot load. The reason is available.

`Application.onCreate` initializes the manager and starts one background load.
The manager lock covers `ensureLoaded`, import, and remove.

A listener registers in `onStart` and unregisters in `onStop`. Registration
delivers the current state immediately. Later transitions post to the main
thread. The UI checks `isAdded()` before it touches views.

While the state is `LOADING`, the summary reads "Checking certificate…" and
the import and remove actions are disabled.

### Snapshot

A snapshot contains the identity, the `SSLContext`, and a generation number.
Import follows this order under the manager lock:

1. Parse and validate the PKCS#12.
2. Build the new `SSLContext`.
3. Write the new encrypted blob.
4. Swap the snapshot and increment the generation.

A failure at any step keeps the old snapshot. A request therefore sees either
the old snapshot or the new snapshot. It never sees a mix.

Remove follows this order:

1. Delete the blob file.
2. If deletion fails, keep the previous state and show an error.
3. Delete the Keystore key.
4. If key deletion fails, swap to `NONE` and show a cleanup warning.
5. Otherwise swap to `NONE`.

## Storage

### File

Path: `getNoBackupFilesDir()/client-certificate.bin`.

Writes use `androidx.core.util.AtomicFile`. A crash cannot leave a truncated
blob.

### Encryption

- Key alias: `ttrss.client-certificate.v1`.
- Key type: AES, 256 bits.
- Key purpose: encrypt and decrypt.
- Block mode: GCM, no padding.
- No user-authentication requirement.
- Random 96-bit IV for every write.
- 128-bit authentication tag.
- AAD: format magic, format version, and key alias.
- `Cipher.doFinal` is used so authentication failures are visible.

`getOrCreateKey()` generates a missing key. If the key is permanently
invalidated, it deletes the key, generates a new key, and retries once. This
applies to both encryption and decryption.

On load, `AEADBadTagException` and `KeyPermanentlyInvalidatedException` do not
delete the blob. The manager enters `ERROR` with a reason. The blob stays, so
Remove and re-import remain available. Other provider errors also enter
`ERROR`, but without deletion.

`clear()` deletes the blob first and the key second. It returns separate
results for both deletions. The manager applies the ordering in the state
machine section.

### Plaintext Payload

All numbers are big-endian. The codec rejects invalid lengths.

| Field | Type | Limit |
| --- | --- | --- |
| Version | int | Must equal 1 |
| Display name | UTF-8 length + bytes | 4096 bytes |
| Subject | UTF-8 length + bytes | 4096 bytes |
| `notAfter` | long, epoch milliseconds | Stored as signed long |
| Key algorithm | UTF-8 length + bytes | 64 bytes |
| PKCS#8 key | int length + bytes | 65536 bytes |
| Chain count | int | 1 to 10 |
| Each certificate | int length + DER | 65536 bytes each |

The total plaintext size must not exceed 5 MB.

### Secrecy

- No password, key byte, or certificate byte goes to a log.
- The settings export reads only `SharedPreferences`, so the certificate stays
  out of the JSON.
- The blob is in the no-backup directory. A restore therefore cannot produce a
  blob without its Keystore key.
- All password and key byte buffers are zeroed on success and on failure.

## Import Flow

1. The user taps the "Client certificate" preference.
2. If a blob exists, the app asks for replacement confirmation.
3. `OpenDocument` opens with these MIME types:
   `application/x-pkcs12`, `application/pkcs12`,
   `application/octet-stream`, and `*/*`.
4. The app reads the URI on an executor with a 5 MB cap.
5. The app shows an optional password dialog. The dialog copies the
   `Editable` into a `char[]`, clears the field, and passes the array to the
   manager.
6. The manager parses, validates, builds, and stores the identity.
7. On success, the summary updates and a toast confirms the import.
8. On failure, the old certificate stays active. The app maps the typed error
   to a string resource. It does not show a raw exception.

If the user rotates the device before confirmation, the import does not start
and the app shows no cancellation message. If rotation happens after
confirmation, the manager operation continues. The next fragment shows the
result.

The import rejects expired and not-yet-valid certificates with a specific
message.

If `ttrss_url` is not HTTPS, the app warns at import time. The certificate
then stays unused.

## Remove Flow

1. The user taps "Remove certificate".
2. The app asks for confirmation.
3. The manager deletes the blob and then the key.
4. The summary updates to "Not imported".
5. If key deletion fails after the file is gone, the app shows a cleanup
   warning and still reports "Not imported".

The Remove action is visible whenever the blob file exists. This includes the
`ERROR` state.

## API Requests

`ApiCommon.performRequest` calls `manager.ensureLoaded()` first. It runs on a
background thread.

`ApiClientSelector` returns:

| State | URL scheme | Mode |
| --- | --- | --- |
| `NONE` | Any | `PLAIN` |
| `READY` | `https` | `MTLS` |
| `READY` | `http` | `PLAIN` |
| `ERROR` | Any | `ERROR` |

- `PLAIN` uses the existing client behavior.
- `MTLS` uses the current mTLS client.
- `ERROR` sets `ApiError.CLIENT_CERTIFICATE_ERROR` and returns `null`. It never
  falls back to plain TLS for a stored but unreadable certificate.

The API client cache is volatile and keyed by snapshot generation. A new
generation builds a new `OkHttpClient` with the existing timeouts,
`RetryInterceptor`, and progress interceptor. In-flight calls keep the old
client. New calls use the new client. The app does not share a connection pool
across identities.

The following stay unchanged: username and password login, HTTP Basic
authentication, request payloads, and session handling.

## Images

Glide uses a stable `DynamicImageCallFactory`. It reads `ttrss_url` on every
request and compares origins with `ImageOriginPolicy`.

An origin match requires all of these:

- Scheme is `https`.
- Normalized host matches.
- Effective port matches. Port 443 is the default for HTTPS.

The path does not participate in the comparison.

Client selection:

| State | Origin | Client |
| --- | --- | --- |
| `NONE` | Any | Plain |
| `READY` | tt-rss | mTLS |
| `READY` | Other | Plain |
| `ERROR` | tt-rss | Fail the image load |
| `ERROR` | Other | Plain |

`ERROR` on the tt-rss origin never falls back to plain.

Both image clients come from `ImageClientFactory` and include the existing
progress interceptor and progress listener. A tt-rss image that redirects to a
different origin is retried once through the plain client without the
certificate.

## Redirects

mTLS clients set `followRedirects(false)` and `followSslRedirects(false)`.

`SameOriginRedirectInterceptor` reads the current tt-rss origin on each
redirect. It follows at most five redirects. It accepts a redirect only when
the target scheme, host, and effective port match the tt-rss origin.

Method rules:

- 301, 302, and 303 use GET.
- The body is dropped for those responses.
- 307 and 308 keep the method and replay the body.
- A one-shot body is not replayed. The redirect becomes an error.
- Relative `Location` values resolve against the current request URL.
- Each intermediate response closes.
- `Authorization` stays only for same-origin requests.

For API requests, a cross-origin redirect returns a 3xx response. The app maps
it to `ApiError.REDIRECT_BLOCKED` with a visible message.

For images, `CrossOriginImageCall` closes the mTLS response and reissues the
redirected URL with the plain client. It forwards callbacks and cancellation.

## Error Handling

New and changed `ApiError` values:

- `CLIENT_CERTIFICATE_ERROR`: a stored certificate cannot load.
- `REDIRECT_BLOCKED`: an API redirect points to another origin.
- `SSL_REJECTED`: remains the server TLS rejection error. When the state is
  `READY`, the message adds a hint to check the client certificate. The app
  does not parse TLS alert strings.

`RetryInterceptor` does not retry `SSLException`.

Import errors map to these user-visible messages:

- The file cannot be read.
- The password is wrong or the bundle is unreadable.
- The bundle has no private key.
- The bundle has no certificate chain.
- The key type is not supported.
- The certificate is expired or not yet valid.
- The certificate store cannot write the file.

Remove errors map to:

- The certificate file could not be deleted.
- The certificate file was deleted, but the Keystore key could not be removed.

The UI shows no raw exception text.

## UI

`preferences_network.xml` adds two `Preference` entries with
`android:persistent="false"`:

- `client_certificate_import`
- `client_certificate_remove`

The import preference title is "Client certificate". Its summary is one of:

- "Checking certificate…"
- "Not imported"
- "<subject>, expires <date>"
- "Stored certificate cannot be read"

The import action label is "Import certificate" or "Replace certificate",
depending on blob presence.

The remove preference title is "Remove certificate". It is visible only when
the blob file exists.

The fragment also shows a note:

- Article WebViews and inline article content do not use the client
  certificate.
- The settings export does not include the certificate.

All new text goes in `res/values/strings.xml`. Do not edit translated resource
files.

## Testing

JVM tests:

- `ClientCertificateParserTest`: valid RSA fixture, valid EC fixture, wrong
  password, corrupt bytes, empty password, missing key, missing chain.
- `CertificatePayloadCodecTest`: round trip, version mismatch, truncated
  input, oversized field, Unicode display name.
- `ClientCertificateStoreTest`: atomic replace, clear, file deletion failure,
  key deletion failure, authentication failure keeps the blob, missing key.
  Inject the file directory and cipher provider.
- `ClientCertificateManagerTest`: single-flight load, load overlapping with
  import, listener replay, every state transition, remove from `READY`, remove
  from `ERROR`.
- `ApiClientSelectorTest`: the full state and URL matrix.
- `ImageOriginPolicyTest`: scheme, host case, default ports, explicit ports,
  path ignored, changed `ttrss_url`.
- `RedirectPolicyTest`: 301/302/303 to GET, 307/308 body replay, relative
  `Location`, same-origin check, cross-origin rejection.
- `SameOriginRedirectInterceptorTest`: redirect limit, current origin read per
  redirect, response closure.
- `CrossOriginImageCallTest`: fallback, callback forwarding, cancellation.
- `ImageClientFactoryTest`: both clients include the progress interceptor.
- `ApiErrorTest`: certificate and redirect error mapping.

Fixtures are committed under `src/test/resources`. Generate them once with
`keytool` or OpenSSL. Do not generate them during the build.

Manual checks:

- API 24 and a current API level.
- A user-installed CA still validates.
- OpenSSL 3 PKCS#12 with PBES2.
- RSA key and EC key.
- Live replace and live remove.
- An `http://` tt-rss URL warns and uses plain TLS.
- A tt-rss image redirect to a CDN loads without the certificate.
- Backup and restore leaves no unusable certificate state.
- An article WebView can still load its content. Inline tt-rss images may fail.

Verification commands:

```
./gradlew :org.fox.ttrss:testDebugUnitTest
./gradlew --no-daemon check assembleDebug
```

## Risks

- Android's PKCS#12 provider can reject some OpenSSL 3 bundles on older
  devices. The import shows a typed error and keeps the old certificate.
- Device backup does not move the Keystore key. The no-backup directory avoids
  a broken restored blob.
- WebView content cannot use the app-managed certificate. Some inline images
  may fail when the server requires mTLS.
- Cross-origin image fallback sends the redirect without the certificate. A
  server that requires mTLS on the redirect target fails the image.
- A user may expect the certificate to replace username and password. The UI
  copy must make clear that both stay active.

## Open Questions

None.
