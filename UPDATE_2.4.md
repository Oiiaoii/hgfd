# LuminFish 2.4 - stop timer, low-battery stop, better "Macro stopped" message

Built on 2.3. Nothing removed.

## New (Settings > Fishing)
- **Stop after N minutes of fishing** (0 = never). Paused time is not counted.
- **Stop when the battery is below N% and not charging** (0 = off). Checked about once a minute.
- The **"Macro stopped" webhook message** now also shows fishing time, catches per hour, success rate, failed reels and the reason
  it stopped (time limit, battery, catch limit).

## Files changed
MacroService.kt (limitCheck, stop message), MainActivity.kt (two new fields), Cfg.kt (maxmin, minbat), app/build.gradle.kts (version 2.4, code 13).

## Package
The tests/ folder is now one file, tests.zip (unzip it to run the python tests). Nothing in the build uses it.

NOT compiled here (no Android SDK). The GitHub build is the first compile: send the error line if it fails.
