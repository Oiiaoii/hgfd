# Blacklist (owner only)

What it does: accounts (Discord or Roblox ID) on your list get an animated "Access blocked" screen and can not start the macro.
Only YOU see the Blacklist screen (account picture, top right > Blacklist). You are the owner when you sign in with the Discord / Roblox
ID written in `app/src/main/res/values/auth.xml` (`owner_discord_id`, `owner_roblox_id`). Owner accounts can never be blacklisted.

## One-time setup
1. Find your IDs: Discord: Settings > Advanced > Developer Mode on, then long-press your name > Copy User ID. Roblox: the number in your profile link
   (roblox.com/users/NUMBER/profile). Put one or both in `auth.xml`.
2. Make a public GitHub Gist with a file called `blacklist.json` containing `{"version":1,"banned":[]}`.
3. Put the Gist's RAW link in `auth.xml` as `blacklist_url`. Use the link WITHOUT a revision so it always shows the newest version:
   `https://gist.githubusercontent.com/YOURNAME/GISTID/raw/blacklist.json`
4. Build and install. Everybody who installs this build downloads that file.

## Blocking someone
Account picture > Blacklist > pick Discord or Roblox > paste the person's numeric user ID > Block account > **Publish** (needs the Gist ID and a
GitHub token with the `gist` permission typed on the Blacklist screen; they stay on your phone only). Or press **Copy JSON** and paste it into the Gist
yourself. **Refresh** shows what is currently published.
The app of the blocked person checks the list when they sign up / log in, when the app starts, and before the macro starts.
A running macro is not stopped in the middle; it is blocked at its next start.

## Honest limits
- There is no server of ours. The check is inside the app. Someone who rebuilds or patches the APK can remove it. Only a real server can lock that.
- A blocked person can still make a new Discord / Roblox account; you block the ID, not the person.
- If a phone has never been online since install, it has no list yet and nothing can be blocked there.
- Not tested against GitHub / Discord / Roblox from here (no network): the Gist publish call (PATCH /gists/ID) is the documented one but unproven.
