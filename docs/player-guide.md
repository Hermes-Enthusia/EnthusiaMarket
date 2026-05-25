# EnthusiaMarket — Player Guide

## What is EnthusiaMarket?

EnthusiaMarket is a player-driven market system for EnthusiaSMP. It lets you create shops linked to chests where you can buy and sell items using **other items** as currency — no Vault money required.

Shops are placed inside **stalls** (rented plots in the market world), and each shop is linked to a nearby container where items are stored.

---

## Getting Started

### Step 1: Rent a Stall

Stalls are rented at the market world. Use:

```
/em list
```

to see available stalls and their status.

### Step 2: Place a Shop Sign

Once you have a stall, place a sign on the stall boundary. The plugin detects it and starts the shop creation wizard.

### Step 3: Choose Your Items (GUI)

The shop creation wizard walks you through three steps:

1. **Select what you're selling** — click the item in the linked container's inventory
2. **Select what you want as payment** — choose which item type buyers must pay with
3. **Set quantities** — how many items per trade, how many as payment

Confirm to create your shop!

### Step 4: Link a Container

Place a chest (or barrel) within **3 blocks** of your shop sign. The container holds:

- Items you're selling (stock)
- Items buyers pay with (payment items)

> **Tip:** The container must be in the same stall region as your sign. Max distance from sign to container: 3 blocks (configurable by server admins).

---

## Buying from a Shop

1. Right-click a shop sign or the shop's container
2. A menu opens showing:
   - What's being sold (item name, quantity)
   - What you need to pay (item type, quantity)
   - Any applicable tax
   - Current stock count
   - Shop owner
   - Whether the shop is active or frozen
3. Click **BUY** to purchase
4. The items are exchanged automatically:
   - Your payment items go into the shop's container
   - The sell items come to your inventory

**Example:** A shop sells 5 Diamonds for 10 Emeralds. You pay 10 Emeralds, receive 5 Diamonds.

## Selling to a Shop

1. Open the shop menu (right-click sign or container)
2. Click **SELL**
3. Your sell items go into the shop's container
4. The payment items come to your inventory

**Example:** Same shop — you give 5 Diamonds to the shop, receive 10 Emeralds back.

---

## Tax System

Shops may have a configurable tax on trades:

- **Tax rate:** A percentage of the trade value (set by server admins)
- **Who pays:** The seller pays the tax (deducted from what they receive)
- **Rounding:** Can be set to round up, down, or to nearest

### Example

- Cost: 100 Emeralds
- Tax: 2%
- Tax amount: 2 Emeralds
- Seller receives: 98 Emeralds

Tax is shown in the purchase menu so you know exactly what you'll get.

---

## Managing Your Shops

### Frozen Shops

If a shop is frozen (disabled by an admin), BUY and SELL buttons are replaced with a "FROZEN" indicator. Trades are blocked until unfrozen.

### Guild Shops

Guild members with `EDIT_SHOP_STOCK` permission can manage guild-owned shops. Guild shops work the same way as personal shops — items come from/go to the linked container.

---

## Bedrock (Mobile) Players

If you're playing on Bedrock Edition (phone/tablet/console):

1. The shop interface opens as a Bedrock-native form
2. It shows the same information: items, prices, tax, stock, owner, status
3. Tap **BUY** or **SELL** to trade
4. Tap **Back** to close

Everything works the same as the Java edition GUI.

---

## Commands

| Command | Description |
|---------|-------------|
| `/em list` | List stalls and their status |
| `/em info` | Show info about the stall you're in |
| `/em help` | Show help |

---

## Tips

- **Stock management:** Keep your container stocked with sell items and empty space for payment items
- **Distance matters:** Place your container close to your sign (within 3 blocks)
- **Item currency:** You choose what you accept as payment — diamonds, emeralds, netherite ingots, or any other item
- **Tax awareness:** Factor tax into your pricing. The menu always shows the final amounts