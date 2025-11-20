package com.lifecompanion.normalization

import com.lifecompanion.capture.NotificationSignal
import com.lifecompanion.data.NotificationCategory
import com.lifecompanion.data.SpendingCategory

/**
 * Classifies notifications and spending into categories
 */
class CategoryClassifier {

    private val messagingApps = setOf(
        "com.whatsapp",
        "org.telegram.messenger",
        "com.twitter.android",
        "com.facebook.orca",
        "com.google.android.apps.messaging",
        "com.android.mms"
    )

    private val socialApps = setOf(
        "com.instagram.android",
        "com.snapchat.android",
        "com.facebook.katana",
        "com.linkedin.android"
    )

    /**
     * Classify notification into categories
     */
    fun classifyNotification(signal: NotificationSignal): NotificationCategory {
        // Check package name first
        when (signal.packageName) {
            in messagingApps -> return NotificationCategory.MESSAGE
            in socialApps -> return NotificationCategory.SOCIAL
        }

        // Check notification category
        signal.category?.let { category ->
            when (category) {
                "msg", "message" -> return NotificationCategory.MESSAGE
                "social" -> return NotificationCategory.SOCIAL
                "reminder" -> return NotificationCategory.REMINDER
                "system" -> return NotificationCategory.SYSTEM
            }
        }

        // Content-based classification
        val text = signal.text.lowercase()
        val title = signal.title.lowercase()

        return when {
            // Finance
            text.containsAny("debited", "credited", "payment", "transaction", "bank") ||
                    title.containsAny("payment", "bank", "upi") ->
                NotificationCategory.FINANCE

            // Shopping
            text.containsAny("order", "delivery", "shipped", "delivered") ||
                    title.containsAny("flipkart", "amazon", "swiggy", "zomato") ->
                NotificationCategory.SHOPPING

            // Promotional
            text.containsAny("offer", "sale", "discount", "deal", "cashback", "% off") ->
                NotificationCategory.PROMOTIONAL

            // Reminder
            text.containsAny("reminder", "due", "upcoming") ->
                NotificationCategory.REMINDER

            // Default
            else -> NotificationCategory.OTHER
        }
    }

    /**
     * Classify spending by merchant name
     */
    fun classifySpending(merchant: String): SpendingCategory {
        val lower = merchant.lowercase()

        return when {
            // Food delivery
            lower.containsAny("swiggy", "zomato", "dominos", "pizza", "mcdonald", "kfc", "burger") ->
                SpendingCategory.FOOD_DELIVERY

            // Restaurant
            lower.containsAny("restaurant", "cafe", "dhaba", "hotel", "dining") ->
                SpendingCategory.RESTAURANT

            // Transport
            lower.containsAny("uber", "ola", "rapido", "metro", "petrol", "fuel", "parking") ->
                SpendingCategory.TRANSPORT

            // Shopping
            lower.containsAny("flipkart", "amazon", "myntra", "ajio", "meesho", "mall") ->
                SpendingCategory.SHOPPING

            // Entertainment
            lower.containsAny("netflix", "spotify", "prime", "hotstar", "jio", "cinema", "movie", "pvr") ->
                SpendingCategory.ENTERTAINMENT

            // Bills
            lower.containsAny("electricity", "water", "gas", "mobile", "internet", "broadband", "recharge") ->
                SpendingCategory.BILLS

            // Groceries
            lower.containsAny("grocery", "supermarket", "reliance", "dmart", "bigbasket", "grofers", "blinkit") ->
                SpendingCategory.GROCERIES

            // Personal care
            lower.containsAny("salon", "spa", "pharmacy", "medical", "clinic", "doctor", "hospital") ->
                SpendingCategory.PERSONAL_CARE

            // Gifts
            lower.containsAny("gift", "flower", "jewel") ->
                SpendingCategory.GIFTS

            // Transfers
            lower.containsAny("transfer", "upi", "paytm", "phonepe", "gpay") ->
                SpendingCategory.TRANSFERS

            // Default
            else -> SpendingCategory.UNKNOWN
        }
    }

    private fun String.containsAny(vararg keywords: String): Boolean {
        return keywords.any { this.contains(it, ignoreCase = true) }
    }
}
