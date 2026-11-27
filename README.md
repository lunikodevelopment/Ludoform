# Ludoform

**A dual-screen game launcher for the AYN Thor.**

Ludoform uses both of the Thor's screens as one front end. The top screen shows large art and details for whatever is selected. The bottom screen holds the lists and buttons you browse with, using the controller or touch. Ludoform reads the games in your ROM folders, can add the library of a [RomM](https://github.com/rommapp/romm) server, and opens each game in an emulator you have installed.

![Ludoform's home screen on the AYN Thor](docs/screenshots/02-home.jpg)

## Download

Get the APKs from the [latest release](https://github.com/lunikodevelopment/ludoformlauncher/releases/latest):

- **Ludoform Showcase** (`ludoform-showcase-…apk`): Ludoform with a built-in sample library, for trying the interface without any setup. It installs next to Ludoform as its own app and doesn't read your games.
- **Ludoform** (`ludoform-…apk`): the launcher itself, for your own games.

The showcase's 39 sample games across 15 consoles are made up. Their file names follow the No-Intro and Redump naming schemes, so titles, regions and disc sets look as they would with real files. No ROMs are included, and starting a sample game shows a note instead of opening an emulator.

## What it does

**Library**
- Scans the ROM folders you choose and groups games made of several files (`.cue` and `.bin`, `.gdi` tracks, `.m3u` disc sets).
- Turns file names into titles with their region: *Ember Vale (Europe) (En,Fr,De,Es,It).rvz* shows as **Ember Vale · Europe**.
- Shows cover art from an image next to the ROM, from RomM, or from the libretro thumbnail sets, and the console's art otherwise.
- Home shows what you played last, your consoles and recent additions. The library can be ordered by name, date added, play time or last played, and you can mark favourites.
- Keeps track of play time for games started from Ludoform.

**Playing**
- Opens games in Dolphin, NetherSX2, DuckStation, PPSSPP, melonDS, Azahar, Citra or RetroArch, and remembers the emulator chosen for a game or a console.
- Asks which disc to start for games with several discs, and which screen a game should open on.

**RomM**
- Adds the games on your RomM server to the library, and downloads them in the background.
- Syncs save files with the server, backing up a save before replacing it and asking you when both copies changed.
- Uploads games from the device to the server's library folder over SMB or SFTP.

**Around the games**
- An in-game menu, opened by holding the two hinge buttons, with screenshot, recording, save sync, volume and brightness, plus achievements and the manual when RomM has them.
- An app drawer for the other apps on the device, grouped into emulators, games and other apps.
- Dark, light and black themes, accent colours from the selected game's cover, the console or the wallpaper, and optional haptics, interface sounds and background music.

## Screenshots

Each picture shows the top screen above the bottom screen.

| | |
|---|---|
| ![Library](docs/screenshots/05-library.jpg) | ![Game details](docs/screenshots/06-details.jpg) |
| **Library** | **Game details** |
| ![Consoles](docs/screenshots/07-consoles.jpg) | ![App drawer](docs/screenshots/04-apps-emulators.jpg) |
| **Consoles** | **App drawer** |
| ![Quickstart](docs/screenshots/01-quickstart.jpg) | ![Settings](docs/screenshots/08-settings.jpg) |
| **Quickstart** | **Settings** |

## Requirements

- An AYN Thor with Android 13 or later.
- The emulators you want to use, installed separately.
- Optional: a RomM server, version 5.0 or newer.

Ludoform doesn't include or download any games. Use it with games you own.

## How it was made

Ludoform was coded with heavy use of AI. The design concept is entirely mine, improvised as I went: what Ludoform is, how its two screens share the work, the look and every feature came from my own ideas and prompts. The code was written from those prompts by Anthropic's Claude (in Claude Code) and OpenAI's ChatGPT (through Codex). I steered each step, tested the builds on my own Thor and decided what stayed.

## Building

You need JDK 17 and the Android SDK.

```bash
./gradlew assembleDebug
```

```bash
./gradlew assembleShowcase
```

The first command builds Ludoform, the second the showcase. The APKs are written to `app/build/outputs/apk/`.
