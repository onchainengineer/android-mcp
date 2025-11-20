package com.lifecompanion.data

import android.content.Context
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom
import android.util.Base64

/**
 * Main database for Life Companion
 * Encrypted at rest using SQLCipher
 */

@Database(
    entities = [
        LifeEventEntity::class,
        ContactEntity::class,
        ContactHandleEntity::class,
        RelationshipProfileEntity::class,
        SpendingEventEntity::class,
        UserSettingEntity::class,
        UserMemoryEntity::class,
        SuggestionEntity::class,
        AuditLogEntry::class
    ],
    version = 1,
    exportSchema = true
)
abstract class LifeCompanionDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao
    abstract fun contactDao(): ContactDao
    abstract fun relationshipDao(): RelationshipDao
    abstract fun spendingDao(): SpendingDao
    abstract fun settingsDao(): SettingsDao
    abstract fun memoryDao(): MemoryDao
    abstract fun suggestionDao(): SuggestionDao
    abstract fun auditDao(): AuditDao

    companion object {
        @Volatile
        private var INSTANCE: LifeCompanionDatabase? = null

        fun getInstance(context: Context): LifeCompanionDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = buildDatabase(context)
                INSTANCE = instance
                instance
            }
        }

        private fun buildDatabase(context: Context): LifeCompanionDatabase {
            val passphrase = getOrCreatePassphrase(context)
            val factory = SupportFactory(SQLiteDatabase.getBytes(passphrase.toCharArray()))

            return Room.databaseBuilder(
                context.applicationContext,
                LifeCompanionDatabase::class.java,
                "life_companion.db"
            )
                .openHelperFactory(factory)
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Initialize default settings
                    }
                })
                .build()
        }

        private fun getOrCreatePassphrase(context: Context): String {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            val prefs = EncryptedSharedPreferences.create(
                context,
                "life_companion_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )

            return prefs.getString("db_passphrase", null) ?: run {
                val newPassphrase = generatePassphrase()
                prefs.edit().putString("db_passphrase", newPassphrase).apply()
                newPassphrase
            }
        }

        private fun generatePassphrase(): String {
            val random = SecureRandom()
            val bytes = ByteArray(32)
            random.nextBytes(bytes)
            return Base64.encodeToString(bytes, Base64.NO_WRAP)
        }
    }
}

// ==================== DAOs ====================

@Dao
interface EventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: LifeEventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<LifeEventEntity>)

    @Query("SELECT * FROM events WHERE eventDate = :date ORDER BY timestamp DESC")
    suspend fun getEventsForDay(date: String): List<LifeEventEntity>

    @Query("SELECT * FROM events WHERE actorId = :contactId AND timestamp >= :since ORDER BY timestamp DESC")
    suspend fun getEventsForContact(contactId: Long, since: Long): List<LifeEventEntity>

    @Query("SELECT * FROM events WHERE type = :eventType AND timestamp BETWEEN :start AND :end ORDER BY timestamp DESC")
    suspend fun getEventsByType(eventType: String, start: Long, end: Long): List<LifeEventEntity>

    @Query("SELECT * FROM events WHERE timestamp BETWEEN :start AND :end ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getEvents(start: Long, end: Long, limit: Int = 100): List<LifeEventEntity>

    @Query("SELECT DISTINCT appPackage FROM events WHERE appPackage IS NOT NULL")
    suspend fun getUniqueAppPackages(): List<String>

    @Query("SELECT * FROM events ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 100): List<LifeEventEntity>
}

@Dao
interface ContactDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(contact: ContactEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(contacts: List<ContactEntity>)

    @Update
    suspend fun update(contact: ContactEntity)

    @Query("SELECT * FROM contacts WHERE id = :id")
    suspend fun getById(id: Long): ContactEntity?

    @Query("SELECT * FROM contacts ORDER BY lastInteractionAt DESC")
    suspend fun getAll(): List<ContactEntity>

    @Query("SELECT * FROM contacts WHERE phone = :phone LIMIT 1")
    suspend fun findByPhone(phone: String): ContactEntity?

    @Query("SELECT * FROM contacts WHERE name LIKE :name")
    suspend fun findSimilarNames(name: String): List<ContactEntity>

    @Transaction
    @Query("SELECT * FROM contacts")
    suspend fun getAllWithProfiles(): List<ContactWithProfile>

    @Transaction
    @Query("SELECT * FROM contacts WHERE id = :contactId")
    suspend fun getWithProfile(contactId: Long): ContactWithProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHandle(handle: ContactHandleEntity)

    @Query("SELECT c.* FROM contacts c INNER JOIN contact_handles h ON c.id = h.contact_id WHERE h.app = :app AND h.handle = :handle LIMIT 1")
    suspend fun findByHandle(app: String, handle: String): ContactEntity?

    @Query("UPDATE contacts SET userTag = :tag, userPriority = :priority WHERE id = :contactId")
    suspend fun updateTag(contactId: Long, tag: String?, priority: Int)

    @Query("UPDATE contacts SET userNotes = :notes WHERE id = :contactId")
    suspend fun updateNotes(contactId: Long, notes: String?)
}

@Dao
interface RelationshipDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: RelationshipProfileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(profiles: List<RelationshipProfileEntity>)

    @Update
    suspend fun update(profile: RelationshipProfileEntity)

    @Query("SELECT * FROM relationship_profiles WHERE contact_id = :contactId")
    suspend fun getProfile(contactId: Long): RelationshipProfileEntity?

    @Query("SELECT * FROM relationship_profiles ORDER BY priorityScore DESC")
    suspend fun getAllProfiles(): List<RelationshipProfileEntity>

    @Transaction
    @Query("""
        SELECT c.* FROM contacts c
        JOIN relationship_profiles r ON c.id = r.contact_id
        WHERE r.priorityScore > 0.7
        ORDER BY r.priorityScore DESC
        LIMIT :limit
    """)
    suspend fun getTopPriorityContacts(limit: Int = 10): List<ContactWithProfile>

    @Transaction
    @Query("""
        SELECT c.* FROM contacts c
        JOIN relationship_profiles r ON c.id = r.contact_id
        WHERE r.reciprocityScore < 0.3
        AND r.totalMessagesReceived > 5
        ORDER BY r.reciprocityScore ASC
    """)
    suspend fun getOneSidedRelationships(): List<ContactWithProfile>

    @Transaction
    @Query("""
        SELECT c.* FROM contacts c
        JOIN relationship_profiles r ON c.id = r.contact_id
        WHERE r.lastMessageFromThem < :threshold
        AND r.priorityScore > 0.5
        ORDER BY r.lastMessageFromThem ASC
        LIMIT :limit
    """)
    suspend fun getNeglectedHighPriorityContacts(
        threshold: Long,
        limit: Int = 10
    ): List<ContactWithProfile>

    @Transaction
    @Query("SELECT c.* FROM contacts c JOIN relationship_profiles r ON c.id = r.contact_id")
    suspend fun getContacts(): List<ContactWithProfile>
}

@Dao
interface SpendingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: SpendingEventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<SpendingEventEntity>)

    @Query("SELECT * FROM spending_events ORDER BY timestamp DESC")
    suspend fun getAllSpending(): List<SpendingEventEntity>

    @Query("SELECT * FROM spending_events WHERE date BETWEEN :startDate AND :endDate ORDER BY timestamp DESC")
    suspend fun getSpendingInRange(startDate: String, endDate: String): List<SpendingEventEntity>

    @Query("SELECT * FROM spending_events WHERE category = :category ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getByCategory(category: String, limit: Int = 100): List<SpendingEventEntity>

    @Query("SELECT * FROM spending_events WHERE amount >= :minAmount ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getAboveAmount(minAmount: Double, limit: Int = 100): List<SpendingEventEntity>

    @Query("UPDATE spending_events SET userNote = :note, emotionTag = :emotionTag, userCategory = :category WHERE id = :eventId")
    suspend fun updateTags(eventId: String, note: String?, emotionTag: String?, category: String?)

    @Query("SELECT * FROM spending_events WHERE id = :eventId")
    suspend fun getById(eventId: String): SpendingEventEntity?
}

@Dao
interface SettingsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(setting: UserSettingEntity)

    @Query("SELECT value FROM user_settings WHERE key = :key")
    suspend fun getValue(key: String): String?

    @Query("SELECT * FROM user_settings")
    suspend fun getAll(): List<UserSettingEntity>

    suspend fun getBoolean(key: String, default: Boolean = false): Boolean {
        return getValue(key)?.toBoolean() ?: default
    }

    suspend fun getInt(key: String, default: Int = 0): Int {
        return getValue(key)?.toIntOrNull() ?: default
    }

    suspend fun getString(key: String, default: String = ""): String {
        return getValue(key) ?: default
    }

    suspend fun getStringList(key: String): List<String> {
        val value = getValue(key) ?: return emptyList()
        // Parse JSON array
        return value.removeSurrounding("[", "]")
            .split(",")
            .map { it.trim().removeSurrounding("\"") }
            .filter { it.isNotEmpty() }
    }
}

@Dao
interface MemoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(memory: UserMemoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(memories: List<UserMemoryEntity>)

    @Query("SELECT * FROM user_memories WHERE type = :type ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getByType(type: String, limit: Int = 20): List<UserMemoryEntity>

    @Query("SELECT * FROM user_memories WHERE content LIKE :query OR tags LIKE :query ORDER BY timestamp DESC LIMIT :limit")
    suspend fun search(query: String, limit: Int = 20): List<UserMemoryEntity>

    @Query("SELECT * FROM user_memories ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 20): List<UserMemoryEntity>
}

@Dao
interface SuggestionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(suggestion: SuggestionEntity)

    @Query("SELECT * FROM suggestions WHERE status = 'pending' ORDER BY priority DESC, timestamp DESC")
    suspend fun getPending(): List<SuggestionEntity>

    @Query("SELECT * FROM suggestions WHERE type = :type AND status = 'pending' ORDER BY priority DESC")
    suspend fun getPendingByType(type: String): List<SuggestionEntity>

    @Query("UPDATE suggestions SET status = :status WHERE id = :suggestionId")
    suspend fun updateStatus(suggestionId: String, status: String)

    @Query("SELECT * FROM suggestions WHERE id = :id")
    suspend fun getById(id: String): SuggestionEntity?
}

@Dao
interface AuditDao {
    @Insert
    suspend fun insert(entry: AuditLogEntry)

    @Query("SELECT * FROM audit_log ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 100): List<AuditLogEntry>

    @Query("SELECT * FROM audit_log WHERE targetType = :targetType AND targetId = :targetId ORDER BY timestamp DESC")
    suspend fun getForTarget(targetType: String, targetId: String): List<AuditLogEntry>
}
