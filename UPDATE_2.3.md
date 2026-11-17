# LuminFish 2.3 - picture recognition of the fishing buttons, any zone colour, tap circles

Built on 2.2: nothing was removed or replaced. The tap ledger engine (Tapper.kt) is unchanged.

## BUG FIXES in this build
**Tap spam after 1-2 minigames (found and fixed).** The 2.2 ledger learned "how far one tap lifts the fill" during a reel. When the
fill rises smoothly (an animation) instead of in one jump, every credited tap pulled that estimate down a little. In my simulation it
fell from 58 px to about 26 px in 25 s, so each tap looked weak and the macro tapped more and more, and the value was then SAVED
(Ai.savePhys) and used as the start of the next reel. Fixes: (1) Tapper.kt keeps learned lift/drain within 0.8-1.25x / 0.75-1.3x of the
start values, (2) Ai.kt only saves/loads lift and drain within +-15% of the measured nominal values, so an already drifted saved value
is pulled back at the next start, (3) the new edge tapper (below) does not learn while reeling at all.

**Script-like tapping (new, default: "One tap at zone edge").** The blue fill reaches the LEFT edge of the green zone -> exactly one
tap; it falls back to the edge -> one more tap. If the zone moves away, a few quick catch-up taps (about 90 ms apart) until the fill is
back, then single taps again. Only the start of a reel (fill far left of the zone) is a short burst. The zone colour does not matter
(any-colour zone, section 2). Simulation (tests/edge_sim.py, smooth lift, 3 seeds x 40 s, % of time the fill is in the zone):
v2.2 ledger 74.0 / moving zone 78.4 / 90 ms delay 63.5 / zone 60 px 60.9  ->  Edge 99.0 / 97.7 / 94.1 / 94.6, about 4 taps per second.
It stays at 95-96% even when its lift or drain numbers are wrong by -50% / +40% / -30%. No tapper can GUARANTEE 100%: that depends on the
real game's lift, delay and capture speed, so the Debug log prints the real in-zone percentage after every reel.

**X button did not work.** The X is now found by its WHITE strokes only (the box is see-through, so a picture of the X with its
background did not match on other backgrounds). Tested on both result screenshots, at 1.0x, 0.5x, 0.3x and 0.2x size: score 0.82-0.97,
other screens at most 0.61 (limit 0.80). If it is still not found, the log says "X button not recognised (best match NN%)", a picture of
the screen is saved to Android/data/com.solmacro/files/debug/ and the saved Close point is used. If the X is seen the log says
"X button seen (NN%) at x,y". Send me those lines or that picture and I can fix the exact cause.

## Also added
- **Cycle log**: after every fish "Cycle: bite N ms, reel N ms, result N ms, in the zone N%".
- **Learn my buttons**: the first strong match of the Fish / Exit button is saved from your own screen and used as an extra picture.
- **Save pictures when unsure**: Fish/Exit/X not recognised -> screen picture saved (max 1 per 25 s, 15 files).
- **5 failed reels in a row** -> webhook message (and the log), so you notice a stuck setup.
- Not added: auto-sell by inventory fullness (I do not know what the game shows for it; send a picture of the inventory if you want it).

## TAP RULES CHECKED (zone moves left / right, "does it stop after one tap?")
Simulated with tests/excursion_sim.py: the zone jumps 160 px left, 380 px right, 260 px left, then drifts; smooth lift; delay 45-140 ms; 3-50% lost taps.
- **No stall after the first tap**: 27-28 taps in the first 3 s, then about 4.6 taps/s; the longest time the fill was below the zone without a tap was
  110-160 ms (300 ms with the old rule).
- **Fill ABOVE the zone (zone moved left): 0 taps** in every case until it drained back. **Fill BELOW the zone (zone moved right): quick taps
  (about every 90 ms) until the fill is in the zone**, then one tap per arrival at the left edge. (Before, the quick taps stopped half a lift
  early; now they run until the predicted fill is inside the zone.)
- Watchdog added: clearly below the zone and 260 ms without a tap -> tap again (it never fired in the simulation, it only protects a lost tap).
- The delay it assumes now comes from the ledger's live measurement once it has 6 samples. Measured effect: real delay 90 ms 79.8% -> 84.0% in the
  zone, 140 ms 73.2% -> 79.3% (45 ms: 86.2% -> 85.2%).
- What I could NOT check: the real game and your phone's real tap delay. The one real-device way to stall is the bar not being read for more than
  0.3 s (no picture of the fill = no taps, that is by design): the Debug log shows "marker hidden" / Click recovery lines then.

## LOG IN + ANIMATIONS (new)
- The sign-in screen now has two tabs: **Sign up** (new account) and **Log in** (you already have one). Both use Discord or Roblox.
  - Sign up creates a LuminFish account on this phone. Doing it again with the same Discord / Roblox just logs you in.
  - Log in needs an account that was signed up on this phone; if there is none for that Discord / Roblox it says so and moves you to Sign up.
  - Log in also lists your saved accounts (picture + name): one tap and you are in, no browser. The ✕ removes an account from the phone.
  - Sign out keeps the account saved, so you can log in again. "Remove account" in the account menu (tap the picture) deletes it.
  - Accounts are kept on this phone only (there is no server). They are not a password login: the provider's own sign-in proves who you are.
- Animations (all switchable: Settings > Detection > "Animations", they also stop when Android's animation scale is 0 / battery saver):
  - sign-in screen: slowly drifting colour glow background, logo pops in then floats with a spreading ring, title, tabs, card and buttons rise in one
    after another, the tab highlight slides, texts fade-swap between Sign up and Log in, saved accounts slide in, wrong input shakes the card, success shows
    a check and the screen lifts away into the main screen; press feedback on every button.
  - main screen: account picture pops in; screens fade between sign-in and main.
  - tap circles: the ring now spreads while it fades; auto-clicker circles pop in one after another.
  - already in 2.2 and unchanged: menu slide, page stagger, button press, counters bump, toggles.
- Not animated: system dialogs (Android's own), and the overlay panel's contents beyond what 2.2 already had.

## SIGN IN (new): Discord or Roblox
- First start shows a sign-in screen: **Continue with Discord**, **Continue with Roblox**, or **Continue as guest**. After that the main
  screen has an account picture (top right, your avatar or your initial); tap it to see the account, sign out, or switch.
- Real OAuth 2.0 + PKCE in the browser (Auth.kt, LoginActivity.kt). Needs the public client ID of a Discord and a Roblox app: see
  SIGNIN_SETUP.md (and docs/auth.html, the https bounce page for Roblox if it does not take `luminfish://auth`).
- Only the public name and picture are read; no password is typed in the app, no secret and no token are stored.
- NOT tested against the real servers (no network here), so the first sign-in is the real test. It does not lock anything on a server.

## SCREEN DETECTION: what was improved, and what the tests showed
Tests in tests/ (python3 + opencv-python + numpy), run on your screenshots and on changed copies of them:
1. **Dim screens and night / blue-light filters broke the zone detection** (tests/robust_test.py). With 2.3's first fixed colour floors the
   any-colour zone was half-lost at 70% brightness and fully lost at 50% and under any warm filter. Now: colour balance (the
   channels are scaled to the same white level) + brightness floors that follow the picture's own white level. Result: zone found at
   the same place at 70 / 50 / 40% brightness, a warm filter and a strong warm filter, blur, JPEG q40 and half size.
2. **Blue fill in any shade** (light blue to violet) is added to your picked fill colour, so a gradient fill still reads.
3. **Other screen layouts** (tests/layout_test.py): the old search assumed a landscape screen with the bar about 0.77 x the screen
   height wide. A third search pass now finds the bar anywhere (checks that the row is a bar: dark track / blue fill / zone fill a
   width at least 3.5x the zone). Portrait 1080x2400 (two bar sizes): not found before, found now; landscape and tablet cases unchanged.
   Thin lines (bar outline, text strokes) are removed first so they can not glue to the zone.
4. **Fish / Exit buttons, fine size search** (tests/scene_robust_test.py): the 14% steps of the size search lost up to 20 points of score on
   text (Fish 0.71 at one size), now a fine +-8% search follows. The Exit button at 25% screenshot size was missed before, found now.
5. **The X under a dim screen or night filter** was missed (threshold fixed at white 215); the X search now uses the same colour balance
   and a limit that follows the picture's white level. All 5 screenshots x 7 changes (plain, dim 50%, night filter, blur, JPEG q40,
   0.4x, 0.25x) are classified correctly (35 of 35); before: 30 of 35.
6. The sizes that worked are remembered between app starts (first look is fast).
Not changed: the tap ledger, capture speed code, your picked colours (they are still tried first, colour balance only helps the any-colour part).
Honest limits: tests use your 5 screenshots (re-colored / resized copies), not a live game; the Kotlin is not compiled; a blue or violet zone
(hue 95-140) is still not told apart from the fill; the relaxed layout pass needs a bar whose row is dark track + fill + zone.

## 1. The fishing buttons are recognised on the screen (new Scene.kt, pictures in assets/scene)
- **"Fish" button** seen -> it is tapped where it really is (no fixed Cast point needed).
- **"Exit" button** seen -> fishing is running, the macro waits for the bar and does NOT tap there (that would leave the fishing).
  If the bar never came (Bar not found), it taps the real Exit button once to leave, then casts again.
- **Bar mini-game** -> read by the existing OpenCV scan + tap ledger.
- **"Fishing Failed" / "Fish Caught!"** -> the X in the top-right corner of that box is found and tapped.
- If a button is not recognised, the old saved Cast / Close points are used, so nothing gets worse.
- Settings > Detection > "Find the Fish / Exit / X buttons on the screen" (panel: "Find buttons on screen"). Needs fast capture.
- Matching is done at many sizes and remembers the size that worked. Check Debug log lines: "Fish button seen (NN%)".

## 2. Any zone colour (Cv.kt)
The zone can change colour, and in the pictures it is made of two shades (teal + green). With one picked colour only one
shade was found, so the zone centre was off. Now any bright coloured area that is not the blue fill and not white text counts
as zone (green, teal, yellow, orange, red, pink, purple; two shades merge). Whole-screen search tries your picked colour first,
then any colour, and only accepts a blob whose row really looks like the bar (blue fill / dark track / zone along its width).
Setting: "Any zone colour" (on by default). A zone that is blue or violet (hue 95-140) is still not covered.

## 3. Small circles where it taps
- **Auto clickers**: every draggable circle now has a small black-and-white dot in its exact middle = the spot that gets tapped.
- **While running**: a short-lived small circle appears at every tap (Settings: "Show small circles where the macro taps").

## Tapping accuracy
The 2.2 tap ledger already compensates delay, lift and drain and follows a moving zone, so it was not replaced. What improves its
accuracy in 2.3 is the correct zone centre (both shades, any colour).

## Tested here
tests/excursion_sim.py, tests/scene_test.py, tests/xbtn_test.py, tests/edge_sim.py, tests/kt_tapper.py, tests/drift_test.py (python3 + opencv-python + numpy; images in tests/fixtures): Fish / Exit / mini-game / Failed / Caught screenshots are
all classified correctly, and the zone is found at the same place after recolouring it orange, yellow, red, pink, purple and cyan.
NOT compiled and NOT run on a phone (no Android SDK here). The GitHub build is the first compile: send the error line if it fails.
Template matching sizes were not measured on a real phone screen; the screenshots you sent are crops, so the first run needs a look at the Debug log.
