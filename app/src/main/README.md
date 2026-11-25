# DNS Filter

A local VPN DNS blocker that only filters one app — Castle (`com.journey.indiab`).
Nothing leaves the device except the DNS queries themselves, which are answered
locally (blocked) or forwarded to 8.8.8.8 (allowed). No other app is touched.

Package `com.dnsfilter`, min SDK 26. Written in Kotlin with Jetpack Compose and
Material 3 (that is the stack the project template uses). No third-party SDKs.

## Setup (two steps)

1. Set **Private DNS** to **Off** or **Automatic** (Settings → Network & internet →
   Private DNS, path varies by phone). Strict mode would bypass this filter
   completely, because the query never reaches the tunnel.
2. Install and open DNS Filter, tap **Start filtering**, accept the VPN dialog,
   allow notifications. The status turns **Active** once the block list has
   loaded.

Only one VPN can run at a time. Note that VIP inside Castle needs nothing extra
here, but it stays locked (see limits).

## Limits — read this

- Blocks **most** ads, not all. Ad domains are answered with NXDOMAIN; anything
  the lists don't know about gets through.
- **First-party ads** (served from the app's own domain) get through.
- **Hard-coded DNS-over-HTTPS** resolvers inside the app get through.
- **VIP stays locked.** It is a server-side entitlement; blocking ads does not
  change what Castle's backend thinks you bought.
- Blocking can break things: if a login, image or video sits on a blocked
  domain, that part fails while everything else looks fine. The query log is
  there to diagnose it — newest first, last 60 kept, Clear button.
- Any other VPN you use has to be turned off first.

## The block list

`app/src/main/assets/hosts.txt` is the blocked list, taken from
`https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts` (AdGuard plus
StevenBlack domains, around 80 000 names). The parser also understands
AdGuard-style `||domain^` lines and their options.

If that file is missing or empty the app refuses to run and says so on screen —
there is no quiet fallback, so a broken build cannot look like a working one.

## Fixing an over-block with allow.txt

`app/src/main/assets/allow.txt` is the never-block list. Anything in it, and all
of its subdomains, is always allowed.

1. Find the failing name in the on-screen log.
2. Add it to `allow.txt`, one domain per line (`0.0.0.0 api.example.com` and a
   plain `api.example.com` both work; `#` starts a comment).
3. Rebuild and reinstall.

## Building

Gradle: `./gradlew assembleDebug`. Versions: versionCode 1, versionName 1.0,
minification off, debug signing. The build produces an installable debug APK.
