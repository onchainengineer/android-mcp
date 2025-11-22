# Usage Examples - Android Life Companion MCP

Real-world conversation examples showing how to use the MCP server with Claude.

## Relationships

### Example 1: Discover Priority Contacts

**User:**
```
Who are my closest friends based on my phone usage over the past 3 months?
```

**Claude's Actions:**
```javascript
// Claude calls:
get_relationship_insights({
  filter: "priority",
  limit: 5
})
```

**Response:**
```
Based on your communication patterns over 90 days, here are your closest connections:

1. **Riya** (Priority Score: 0.92)
   - You reply to her messages instantly (avg 2 minutes)
   - 15 late-night conversations
   - Very high emotional closeness (0.89)
   - Balanced communication (reciprocity: 0.84)

2. **Arjun** (Priority Score: 0.88)
   - Extremely balanced relationship (reciprocity: 0.95)
   - You both initiate conversations equally
   - Fast response times from both sides

3. **Priya** (Priority Score: 0.85)
   - High interaction frequency (50+ messages/week)
   - Good reciprocity (0.78)
   - 8 long conversations (10+ messages in 30 min)

Would you like me to tag any of these as "priority" so their
notifications always get through?
```

### Example 2: Identify One-Sided Relationships

**User:**
```
Are there any friendships where I'm putting in way more effort than
the other person?
```

**Claude's Actions:**
```javascript
get_relationship_insights({
  filter: "weak",
  limit: 10
})
```

**Response:**
```
I found 3 one-sided relationships where the reciprocity is quite low:

1. **Amit** (Reciprocity: 0.23)
   - They sent you 47 messages
   - You replied to only 8
   - Avg response time: 18 hours
   - Pattern: Mostly one-sided incoming

2. **Neha** (Reciprocity: 0.18)
   - They reach out regularly (32 messages)
   - You responded 4 times
   - Last reply from you: 12 days ago

3. **Vikram** (Reciprocity: 0.76, but you initiate 90%)
   - You always reach out first
   - They respond, but never initiate
   - One-sided outgoing pattern

This doesn't mean these are bad relationships, but it might be
worth reflecting on whether the effort matches the value you get.

Want me to show you who you've been neglecting instead?
```

### Example 3: Check Neglected Relationships

**User:**
```
Who have I not talked to in a while that I should probably check in with?
```

**Claude's Actions:**
```javascript
get_relationship_insights({
  filter: "neglected",
  limit: 5
})
```

**Response:**
```
Here are important people you haven't contacted recently:

1. **Kavya** (Priority Score: 0.81)
   - Last message from her: 18 days ago ("Hey, how are you?")
   - You haven't replied yet
   - Normally very close relationship

2. **Rahul** (Priority Score: 0.72)
   - Last contact: 21 days ago
   - Previously high interaction (used to talk daily)
   - Relationship cooling off

3. **Mom** (User tagged: family)
   - Last message from her: 11 days ago
   - You usually reply within hours, but not this time

Want me to help you draft a message to any of them?
```

## Spending Patterns

### Example 4: Monthly Spending Analysis

**User:**
```
Give me a breakdown of my spending for the past month.
```

**Claude's Actions:**
```javascript
get_spending_insights({
  time_range: "month"
})
```

**Response:**
```
Here's your spending breakdown for the past 30 days:

**Total Spent:** ₹32,450

**By Category:**
1. Food Delivery: ₹8,450 (26%) - 31 transactions
2. Shopping: ₹7,200 (22%) - 8 transactions
3. Transport: ₹4,800 (15%) - 45 trips
4. Entertainment: ₹3,200 (10%) - Netflix, Spotify, 2 movie tickets
5. Groceries: ₹2,800 (9%)
6. Bills: ₹2,400 (7%) - Electricity, mobile
7. Other: ₹3,600 (11%)

**Patterns I Noticed:**

🌙 **Late Night Spending:** 12 food delivery orders after 10 PM (₹2,940)
   - Pattern: Mostly after long work days
   - You tagged 3 as "impulse"

⚡ **Impulsive Spending:** 8 instances of 2+ purchases within 30 min
   - Total: ₹4,200
   - Often triggered by promotional notifications

♻️ **Recurring Merchants:**
   - Swiggy: 18 times (₹4,320)
   - Uber: 23 times (₹3,450)
   - Amazon: 3 times (₹5,200)

**Emotional Context (from your notes):**
   - "regret": ₹1,800 (6 transactions)
   - "impulse": ₹2,400 (4 transactions)
   - "necessary": ₹1,200 (3 transactions)

Want me to suggest a budget or help you understand any of these patterns better?
```

### Example 5: Detect Unusual Transactions

**User:**
```
Were there any unusual or suspicious transactions this month?
```

**Claude's Actions:**
```javascript
get_spending_insights({ time_range: "month" })
// Then looks at anomalies
```

**Response:**
```
I found 2 unusual transactions:

1. **₹5,499 at Flipkart** (Nov 15)
   - 5x higher than your typical shopping spend
   - No note or context
   - Do you remember this? Want to add a note?

2. **₹2,800 at "Premium Salon XYZ"** (Nov 8)
   - First time seeing this merchant
   - 3x higher than usual personal care spending
   - Tagged as "necessary"

Both seem legitimate, but they're significantly above your normal
spending patterns for these categories.
```

### Example 6: Tag Spending with Context

**User:**
```
That ₹5,499 Flipkart order was a birthday gift for my colleague.
Can you tag it?
```

**Claude's Actions:**
```javascript
// First, find the event
get_spending_events({
  merchant: "flipkart",
  min_amount: 5000
})

// Then tag it
tag_spending_event({
  event_id: "evt_12345",
  note: "Birthday gift for colleague",
  category: "GIFTS",
  emotion_tag: "joy"
})
```

**Response:**
```
Tagged! I've marked the ₹5,499 Flipkart transaction as:
- Category: Gifts
- Note: "Birthday gift for colleague"
- Emotion: Joy

Now when I analyze your spending, this won't show up as unusual
personal shopping. In fact, looking back, you spend about ₹2,500/month
on gifts - you're quite generous!
```

## Attention Management

### Example 7: App Usage Analysis

**User:**
```
How much time am I wasting on social media?
```

**Claude's Actions:**
```javascript
get_app_usage({
  top_n: 15
})

get_attention_report({
  time_range: "week"
})
```

**Response:**
```
This week's screen time breakdown:

📱 **Total:** 42 hours (6 hours/day)

**Time Sinks:**
1. **Instagram:** 12.5 hours (30%)
   - 285 opens (45 times/day!)
   - Avg session: 2.6 minutes
   - Pattern: Compulsive checking, not intentional use

2. **YouTube:** 6.8 hours (16%)
   - 42 sessions
   - Avg session: 9.7 minutes
   - Mix of intentional viewing and rabbit holes

3. **WhatsApp:** 8.2 hours (20%)
   - Mix of active chatting (4.1 hrs) + idle browsing (4.1 hrs)

**Productive Time:**
- Work apps: 9.1 hours (22%)
- Reading apps: 2.4 hours (6%)

**Insights:**

🚨 Instagram is your biggest attention drain
   - You open it 45 times per day
   - But only spend 2-3 min each time
   - This suggests habit/compulsion, not genuine use

⏰ Your most focused time: 9-11 AM
   - Low app switching
   - Mostly work apps
   - This is your "deep work" window

Want me to suggest some notification filters to reduce the
compulsive checking?
```

### Example 8: Notification Noise

**User:**
```
Which apps are sending me notifications that I always ignore?
```

**Claude's Actions:**
```javascript
get_noisy_apps({
  min_noise_score: 0.7
})
```

**Response:**
```
Here are the noisiest apps (high dismiss/ignore rate):

1. **Flipkart** (Noise Score: 0.94)
   - 127 notifications this month
   - You opened: 2 (2%)
   - You dismissed: 98 (77%)
   - You ignored: 27 (21%)
   - Mostly promotional ("Sale!", "Deals!")

2. **Zomato** (Noise Score: 0.89)
   - 84 notifications
   - Opened: 6 (7%)
   - Dismissed: 71 (85%)
   - Pattern: Discount codes, restaurant suggestions

3. **LinkedIn** (Noise Score: 0.82)
   - 56 notifications
   - Opened: 8 (14%)
   - Dismissed: 45 (80%)
   - Mostly "People you may know", "Jobs you might like"

4. **Instagram** (Noise Score: 0.76)
   - 203 notifications
   - Opened: 42 (21%)
   - Dismissed: 161 (79%)
   - Mix of genuine social + algorithmic suggestions

**Recommendation:** Mute Flipkart, Zomato, and LinkedIn?
```

### Example 9: Configure Notification Filtering

**User:**
```
Yes, mute Flipkart and Zomato. But keep LinkedIn on low priority.
```

**Claude's Actions:**
```javascript
configure_app_notifications({
  app_package: "com.flipkart.android",
  action: "mute"
})

configure_app_notifications({
  app_package: "com.application.zomato",
  action: "mute"
})

configure_app_notifications({
  app_package: "com.linkedin.android",
  action: "low_priority"
})
```

**Response:**
```
Done! Here's what I configured:

✅ **Flipkart:** Muted (notifications auto-dismissed)
✅ **Zomato:** Muted
✅ **LinkedIn:** Low priority (no sound/vibration, shows in shade only)

You'll save yourself from ~200+ unwanted interruptions per month.

Your phone will still capture these for analysis, but they won't
disturb you anymore.
```

## Memory & Reflection

### Example 10: Record a Personal Goal

**User:**
```
I want to reduce my impulsive food delivery spending. Can you remember
that as a goal?
```

**Claude's Actions:**
```javascript
add_memory({
  type: "goal",
  content: "Reduce impulsive food delivery spending, especially late-night orders",
  tags: ["spending", "food-delivery", "self-improvement"]
})
```

**Response:**
```
Recorded as a personal goal. I'll keep this in mind when analyzing
your spending patterns.

Based on this goal, I can:
1. Flag late-night food delivery orders (you do ~12/month)
2. Suggest a weekly budget for food delivery
3. Remind you of this goal when I see impulsive patterns

Would you like me to suggest a specific target? (e.g., "Reduce from
31 orders/month to 20 orders/month")
```

### Example 11: Search Past Reflections

**User:**
```
What goals have I set related to spending?
```

**Claude's Actions:**
```javascript
search_memories({
  query: "spending",
  type: "goal"
})
```

**Response:**
```
You have 3 goals related to spending:

1. **"Reduce impulsive food delivery spending"** (Set 2 days ago)
   - Status: You've ordered Swiggy 3 times since setting this
   - 2 were late-night (after 10 PM)

2. **"Be more mindful about gift spending"** (Set 18 days ago)
   - Status: You spent ₹5,499 on a colleague's gift (within budget)
   - Tagged with "joy" - suggests healthy spending

3. **"Track all entertainment subscriptions"** (Set 1 month ago)
   - Status: Currently paying for Netflix, Spotify, Amazon Prime
   - Total: ₹1,200/month

Want me to help you make progress on any of these?
```

## Advanced Queries

### Example 12: Cross-Domain Insights

**User:**
```
Is there a connection between my social media usage and my spending?
```

**Claude's Actions:**
```javascript
get_app_usage({ top_n: 20 })
get_spending_events({ category: "SHOPPING" })
// Analyzes correlation
```

**Response:**
```
Interesting pattern I found:

📱 **Instagram → Shopping Correlation:**
- On days you use Instagram 90+ min, you're 3x more likely to make
  an online shopping purchase
- 6 out of 8 shopping transactions happened within 2 hours of
  heavy Instagram use
- Likely seeing ads/influencer content triggering purchases

💸 **WhatsApp → Food Delivery:**
- Group chats mentioning food often precede Swiggy orders
- "Late night work WhatsApp" pattern → ordering food
- Not causation, but strong correlation

🎯 **Recommendation:**
If you want to reduce impulsive shopping, consider limiting Instagram
time, especially in the evening (that's when most purchases happen).
```

### Example 13: Relationship + Spending Combo

**User:**
```
How much do I spend on my friends vs. how much they matter to me?
```

**Claude's Actions:**
```javascript
get_relationship_insights({ filter: "all" })
get_spending_events({ limit: 200 })
// Links spending events with contacts
```

**Response:**
```
Here's how your spending aligns with your relationships:

**High Priority + High Spending (Aligned):**
1. **Riya** (Priority: 0.92)
   - Spent ₹2,400 on her birthday gift + treats
   - Makes sense - she's your closest friend

**High Priority + Low Spending:**
2. **Arjun** (Priority: 0.88)
   - Only ₹500 spent (1 coffee)
   - High-value relationship, low financial investment
   - Different love language?

**Low Priority + High Spending (Misaligned):**
3. **Work Colleague X** (Priority: 0.34)
   - Spent ₹5,499 on their birthday gift
   - More than you spent on close friends
   - Social obligation vs. genuine relationship?

**Insight:** You're generous, but sometimes social pressure
(work colleagues) drives spending more than genuine closeness.
```

## Combining Multiple Tools

### Example 14: Complete Life Audit

**User:**
```
Give me a complete audit of my digital life this month.
```

**Claude's Actions:**
```javascript
get_relationship_insights({ filter: "all", limit: 20 })
get_spending_insights({ time_range: "month" })
get_attention_report({ time_range: "month" })
get_noisy_apps({ min_noise_score: 0.7 })
```

**Response:**
```
# Your November Digital Life Audit

## 👥 Relationships
- **3 priority contacts:** Riya, Arjun, Priya
- **2 neglected important people:** Kavya, Mom
- **1 one-sided relationship:** Amit (you're ignoring them)

## 💰 Money
- **Total spent:** ₹32,450
- **Biggest category:** Food delivery (₹8,450)
- **Impulsive spending:** 8 instances (₹4,200)
- **Emotional tags:** 6 "regret", 4 "impulse"

## 📱 Attention
- **Screen time:** 180 hours (6 hrs/day)
- **Biggest time sink:** Instagram (12.5 hrs/week)
- **Productive time:** 9.1 hrs work + 2.4 hrs reading
- **App switches:** ~400/day (compulsive checking)

## 🔕 Noise
- **Total notifications:** 1,247
- **Actually opened:** 214 (17%)
- **Top noise apps:** Flipkart (94%), Zomato (89%)
- **Recommendation:** Mute 4 apps → save 200 interruptions/month

## 🎯 Patterns Worth Noting
1. Late-night Instagram → next-day shopping
2. Long work days → late-night food orders
3. You reply to Riya instantly but ignore Mom for days
4. Spending more on social obligations than close friends

Want me to dig deeper into any of these?
```

---

These examples demonstrate the full power of the Android Life Companion MCP server. The key is combining multiple tools to build rich, contextual understanding of behavior patterns.
