# Sign-in setup (Discord / Roblox)

The sign-in screen uses the normal OAuth 2.0 "authorization code + PKCE" flow in the phone browser. The app only needs the PUBLIC
client ID of an app you create. No client secret is ever put in the app, no password is typed in it, the access token is thrown
away right after the profile (name, picture) is read, and nothing is sent to any server of yours. The sign-in does not lock
anything on a server: it is a profile / gate in the app (a guest option is always there).

I could not test the login against the real Discord / Roblox servers from here. The endpoints and settings below are the documented
ones as far as I know them; if a provider answers with an error, the sign-in screen shows its text.

## Discord
1. https://discord.com/developers/applications -> New Application -> OAuth2.
2. Redirects: add `luminfish://auth`.
3. Turn ON "Public Client" (needed because the app has no secret).
4. Copy the Client ID.

## Roblox
1. https://create.roblox.com/dashboard/credentials -> OAuth 2.0 Apps -> Create.
2. Client type: Public (PKCE). Scopes: `openid` and `profile`.
3. Redirect URL: try `luminfish://auth` first. If Roblox does not accept a custom scheme, host `docs/auth.html` from this repo with
   GitHub Pages (Settings > Pages > Deploy from branch, folder /docs) and use `https://YOURNAME.github.io/YOURREPO/auth.html` as the
   redirect URL, here and in the app (Sign-in setup > Roblox redirect URL). That page just forwards to `luminfish://auth`.
4. Copy the Client ID.

## In the app
Sign-in screen -> "Sign-in setup" -> paste the client IDs (and the redirect URL if you used the https one) -> Save.
Or put them in `app/src/main/res/values/auth.xml` before building, so every installed copy already has them.
