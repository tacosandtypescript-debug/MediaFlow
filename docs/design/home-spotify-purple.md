# Home — Spotify layout, MediaFlow purple

Not a Spotify clone. No `#1DB954`. No Spotify wordmark. Primary remains MediaFlow purple (`#7C3AED` / `#8B5CF6`).

Home is still a download-capture screen. This spec is visual density and hierarchy only.

## Structure

1. Greeting (time of day) + ExtraBold **MediaFlow** + muted `home_subtitle`
2. Filled pill URL field (paste leading, clear trailing) — no bordered card wrapper
3. Audio / Video chips in Library style (`onSurface` selected, `surfaceVariant` unselected)
4. Featured analysis row (artwork + title + muted meta) when analysis is not idle
5. Compact quality chips + filename field
6. Full-width 52 dp purple pill download CTA
7. Recents as a 2-column shortcut grid (`home_recent_downloads`); omitted when empty
8. Bottom `120.dp` spacer for the miniplayer

## Tokens

Use `ColorScheme` (primary already purple). Search / tiles / unselected chips: `surfaceVariant`. Selected chips invert `onSurface` / `surface`. CTA: `primary`.
