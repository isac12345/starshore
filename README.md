# Starshore

Clean up junk on your phone and shrink video files. No internet, no ads.

**[Download APK](../../releases/latest)**

Android 7.0+ - 778 KB - no ads - no internet

## Features

| | |
|---|---|
| **Scan** | Lists every file it found with its size. You tick what to delete, then press Clean. |
| **Duplicates** | Matched by file content, not by name. One original per group is always kept. |
| **Junk files** | Temp files, logs, caches, leftover APKs, empty folders, and gallery trash. |
| **Compress** | Hardware encoder, with a bitrate or a target size per video. |
| **Audio** | Microphone, internal audio, or both, muxed into the compressed file. |
| **Compare** | Slide between the original and the compressed video, with a 5 second preview. |
| **Paste bar** | The last 20 copied snippets stay in the suggestion bar, ready to paste. |
| **Batal** | Undo deletes the last word, press again for the one before it. |

## Install

1. Download the latest APK on the [Releases](../../releases) page.
2. Open the APK file and allow installation from this source.
3. Open Starshore and grant file access when Android asks.

## Permissions

| Permission | Why Starshore needs it |
|---|---|
| `MANAGE_EXTERNAL_STORAGE` | Reads and deletes files anywhere in shared storage, and reads videos for compression. Android 11 and newer always ask you on a settings screen. |
| `READ_EXTERNAL_STORAGE` and `WRITE_EXTERNAL_STORAGE` | Older Android only, up to Android 12. Not used on Android 13 and newer. |
| `QUERY_ALL_PACKAGES` | Finds out which apps are no longer installed, so their leftover folders can be listed. |
| `moe.shizuku.manager.permission.API_V23` | Shizuku access. Used to clean cache inside other apps without root. |
| `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_DATA_SYNC` | Keeps compression alive with the screen off. A foreground notification is shown while it runs. |
| `WAKE_LOCK` | Stops the phone from sleeping during a long compression. Released when done. |
| `POST_NOTIFICATIONS` | The progress notification on Android 13 and newer. |

## Warnings

- Deletion is permanent. There is no trash bin and no undo for a deleted file.
- Read the list first. If you are not sure about an item, do not tick it.
- Try it on a test folder before a big clean.
- Every deletion needs a tick from you and one tap on the Clean button. Nothing is deleted automatically.
- Compressing only removes the original when you tap Delete original. A result that is not smaller is
  discarded and the original is kept.
- Without Shizuku or root, the app only cleans cache under `Android/data` and `Android/media`.

## Privacy

- No internet permission, so nothing leaves the device.
- No analytics, no tracking, no server.
- Scanning happens on the device. The result list is shown only inside the app.

## Limitations

- Cleaning inside other apps needs Shizuku or root.
- Not tested on every phone. Some ROMs block TRIM or system log access; when that happens Starshore says
  it was skipped instead of failing.
- Release APKs are signed with the project key, while CI artifacts from a normal push are debug-signed.
