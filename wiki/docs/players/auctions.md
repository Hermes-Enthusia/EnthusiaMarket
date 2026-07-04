---
title: Auctions
audience: player
topic: auctions
summary: How stall auctions work — bidding, anti-snipe, and winning.
keywords: [auctions, bidding, anti-snipe, winning]
related: [stalls, rent]
updated: 2026-06-06
---

# Auctions

Auctions are how you win stall ownership. When a stall becomes available (new, re-auctioned, or emergency-auctioned), an auction starts.

## Starting an Auction

If you own a stall, you can start an auction to sell it:

```text
/em auction start <stall> <price> [duration]
```

Examples:
- `/em auction start stall5 1000` — 24h auction starting at $1,000
- `/em auction start stall5 500 PT1H` — 1-hour auction
- `/em auction start stall5 2000 PT15M` — 15-minute auction
- `/em auction start stall5 5000 P7D` — 7-day auction

**Duration format** is ISO-8601 (case-insensitive):
- `PT15M` = 15 minutes
- `PT1H` = 1 hour
- `PT24H` = 24 hours (default if omitted)
- `P7D` = 7 days

**Admins** can start auctions on any stall (including unowned ones) with the same command. Requires `enthusiamarket.admin` permission.

## Browsing

Open the auction browser with:

```text
/em auctions
```

This shows all active auctions with stall IDs, current high bids, and time remaining. **Click any auction entry** to close the browser and have the bid command pre-filled in chat — just type your amount and press enter.

## Bidding

```text
/em bid <auction-id-or-stall> <amount>
```

Examples:
- `/em bid stall5 1500` — bid $1,500 on stall5's auction
- `/em bid a1b2c3d4-... 2000` — bid $2,000 using the auction UUID

You can use either the stall name (e.g. `stall5`) or the auction UUID (shown in `/em auctions` lore). Requires the `enthusiamarket.auction.bid` permission (granted to players by default). Your bid must exceed the current high bid.

**Money is withdrawn immediately when you bid.** If someone outbids you, you get your money back automatically. This means you can't bid more than you have.

## Winning

When the auction timer ends, the highest bidder wins. You'll receive a confirmation message. The stall sign updates to show your name, and rent collection begins.

## Anti-snipe

If a bid is placed within the last **30 seconds** of an auction, the timer extends by 30 seconds. This prevents last-second sniping and gives others a chance to counter-bid.

## Auction duration

Default auction duration is **24 hours**. Admins can configure:

- Minimum: 15 minutes
- Maximum: 7 days

## Auction fee

When you win an auction and later sell the stall (via sell offer), a **5% fee** is deducted from the sale price. This fee goes to the server.

## Cancelled auctions

Only admins can cancel an auction. If cancelled, all bids are refunded.
