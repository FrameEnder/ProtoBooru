# ProtoBooru

A native Android client for **Szurubooru**, built with Kotlin and Jetpack Compose. It uses the ink/amber workshop theme with Space Grotesk and JetBrains Mono.

It covers the whole Szurubooru API: browsing, uploading, editing, moderation, and admin.

## Features

**Browsing**
- Post search with the full Szurubooru query syntax and live tag autocomplete (it handles `-negation` too).
- 12 sort presets, quick filters (my favorites, my uploads, liked/disliked, type, tumbleweeds, notes, comments), and safety toggles.
- Staggered or square grid with 1–6 columns, plus infinite scroll and pull to refresh.
- Multi-select by long-pressing: download, favorite, or unfavorite many posts at once.

**Viewer**
- Swipe through the whole result list, which keeps loading as you go.
- Pinch and double-tap zoom, with a thumbnail placeholder that is replaced by the full image.
- Video via ExoPlayer, with autoplay, mute, and loop settings. GIFs animate.
- Note overlays, numbered and matched to the note list.
- Upvote and downvote, favorite, comments, and download.
- Share the file, share the link, copy the link, open in browser, or find similar posts.
- An info sheet with tags grouped and colored by category (tap a tag to search, long-press for details). It also shows pools, related posts, notes, file details, sources, checksums, and who favorited the post.

**Uploading**
- Pick from the gallery or files, or add URLs; the server fetches URLs with yt-dlp for video sites.
- **Share to ProtoBooru** from any app, choosing *Upload to ProtoBooru* for images, videos, or links, or *Search ProtoBooru* for a reverse image search.
- Shared tags, safety, and source for the whole batch, with per-item extra tags and overrides.
- Exact-duplicate check before posting, an option to link a batch as related posts, and anonymous uploads if your server allows them.
- A background queue with progress bars and retry. Uploads run one at a time and keep going when you leave the screen.

**Editing & moderation**
- Post editor: tags with autocomplete, safety, source, relations, pools, flags, and note text. Also replace the file, set or reset a custom thumbnail, feature the post, merge it into another post, or delete it.
- Bulk edit from multi-select: add or remove tags, set safety or source, link the selection as related posts, add it to a pool, or delete it. These run one post at a time to avoid database deadlocks.
- Tags: create, rename and add aliases, change category, and edit description, implications, and suggestions. Merge and delete tags.
- Pools: create, edit, reorder posts, add by ID or range (`12 15 20-24`), merge, and delete.
- Tag and pool categories: create, rename, recolor, reorder, set the default, and delete.
- Users: change a user's rank or delete them. Upload your own avatar or switch back to Gravatar.

**Everything else**
- **Home**: site stats, the featured post, latest uploads, most favorited, and links to every section.
- **Tags**: search, filter by category, and sort several ways. Each tag's page shows aliases, implications, suggestions, co-occurring tags, and history.
- **Pools**: list and search pools. A pool's page shows its post grid and lets you download the whole pool.
- **Comments**: a site-wide feed searchable by `user:` / `post:` / `text:`. You can vote, plus edit and delete your own comments (or anyone's with permission).
- **Users**: a directory and profiles, with jumps to each user's uploads, favorites, comments, and edit history.
- **Account**: sign in with a password, which creates a per-device token so the password is never stored, or paste an existing token. Edit your email and password.
- **Login tokens**: list, create (with expiry), enable/disable, rename, delete, and copy.
- **Site history**: the snapshot log with type filters and expandable JSON diffs.
- **Image search**: reverse-search your booru for a picked image, or **share any image to ProtoBooru** from another app.
- Features are hidden or shown based on your rank and the server's privilege config.

Downloads go to `Pictures/ProtoBooru` and `Movies/ProtoBooru` with a configurable filename pattern (`{id} {md5} {sha1} {tags} {safety} {type}`). Files you've already downloaded are skipped.

## Building the APK (GitHub Actions)

1. Create a GitHub repo and push this folder to `main`.
2. The **Build APK** workflow runs automatically. Download the APK from the run's *Artifacts* section.
3. Pushing a tag such as `v1.0.0` also publishes a GitHub Release with the APK attached.

### Signing (do this once, or updates won't install over each other)

Without a release key, each CI run signs with a fresh throwaway key, so you'd have to uninstall before every update. Create a key once:

```fish
keytool -genkeypair -v -keystore protobooru.jks -alias protobooru -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 protobooru.jks > protobooru.jks.b64
```

Then add these repository secrets (Settings → Secrets and variables → Actions):

| Secret | Value |
|---|---|
| `PB_KEYSTORE_B64` | contents of `protobooru.jks.b64` |
| `PB_KEYSTORE_PASSWORD` | the keystore password |
| `PB_KEY_ALIAS` | `protobooru` |
| `PB_KEY_PASSWORD` | the key password (same as keystore unless you set one) |

Keep `protobooru.jks` somewhere safe. `.gitignore` already excludes it.

### Building locally

The repo doesn't include a Gradle wrapper jar. Either open the folder in Android Studio, which offers to set up the wrapper, or with Gradle 8.11+ installed run:

```fish
gradle wrapper --gradle-version 8.11.1
./gradlew :app:assembleDebug
```

The debug build installs as a separate app (`com.frameender.protobooru.debug`), so it can sit next to the release build.

## First run

Open **Home**, enter your server (e.g. `http://100.x.y.z:8390`), and tap **Test & save**. The API path defaults to `/api`, which matches the stock Docker setup where the client container proxies the API. Plain HTTP is allowed, so Tailscale addresses work without TLS. Then sign in from the **Account** tab, or browse anonymously if your server permits it.

## Project layout

```
app/src/main/java/com/frameender/protobooru/
  ProtoBooruApp.kt, MainActivity.kt, BuildConfigInfo.kt
  data/      Models, SzuruApi (REST client), SettingsStore, Graph (app state), Downloader, Format
  ui/        AppNav (routes + bottom bar), theme/, common/ (components, paging, zoom)
             home/ posts/ post/ tags/ pools/ comments/ users/ account/ history/ search/ settings/
```

Fonts: Space Grotesk and JetBrains Mono, both SIL Open Font License 1.1.
