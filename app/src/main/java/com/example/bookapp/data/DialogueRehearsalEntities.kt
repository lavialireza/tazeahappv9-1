package com.example.bookapp.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * نقش‌های انتخاب‌شده برای یک گفت‌وگو.
 *
 * هر نقش نسخهٔ منبع مستقل خود را دارد؛ versionUid در DialogueEntity
 * فقط برای سازگاری با گفت‌وگوهای قدیمی نگهداری می‌شود.
 */
@Entity(
    tableName = "dialogue_role_selections",
    foreignKeys = [
        ForeignKey(
            entity = DialogueEntity::class,
            parentColumns = ["id"],
            childColumns = ["dialogueId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = RoleEntity::class,
            parentColumns = ["id"],
            childColumns = ["roleId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["dialogueId", "roleId"], unique = true),
        Index("roleId"),
        Index("versionUid")
    ]
)
data class DialogueRoleSelectionEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val dialogueId: Long,

    val roleId: Long,

    /**
     * نسخه‌ای که این نقش از آن انتخاب شده است.
     */
    val versionUid: String,

    /**
     * ترتیب نمایشی نقش‌ها در مطالعهٔ چندنقشی.
     * این مقدار ترتیب اصلی آرشیو نیست.
     */
    val displayOrder: Int = 0
)

/**
 * اطلاعات تکمیلی یک نوبت گفت‌وگو.
 *
 * متن اصلی همچنان از SectionEntity خوانده می‌شود.
 * این داده‌ها برای قابلیت‌های آتی مدیریت اجرا نگهداری می‌شوند و
 * در صفحهٔ فعلی مطالعهٔ شخصی نمایش یا ویرایش نمی‌شوند.
 */
@Entity(
    tableName = "dialogue_turn_metadata",
    foreignKeys = [
        ForeignKey(
            entity = DialogueTurnEntity::class,
            parentColumns = ["id"],
            childColumns = ["turnId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = DialogueTurnEntity::class,
            parentColumns = ["id"],
            childColumns = ["replyToTurnId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = RoleEntity::class,
            parentColumns = ["id"],
            childColumns = ["expectedResponderRoleId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("replyToTurnId"),
        Index("expectedResponderRoleId"),
        Index("sourceSequenceIndex"),
        Index("rehearsalOrderIndex")
    ]
)
data class DialogueTurnMetadataEntity(

    /**
     * شناسه نوبت موجود در جدول dialogue_turns.
     */
    @PrimaryKey
    val turnId: Long,

    /**
     * ترتیب مستند نوبت در اجرای اصلی.
     *
     * مقدار null یعنی ترتیب واقعی هنوز مشخص نشده است.
     */
    val sourceSequenceIndex: Int? = null,

    /**
     * وضعیت اعتبار ترتیب مستند.
     */
    val sourceSequenceStatus: String =
        SOURCE_UNVERIFIED,

    /**
     * ترتیب نمایشی نوبت‌ها در مطالعهٔ چندنقشی.
     * به ترتیب منبع دست نمی‌زند.
     */
    val rehearsalOrderIndex: Int = 0,

    /**
     * آیا این نوبت انتظار پاسخ دارد؟
     */
    val expectsResponse: Boolean = false,

    /**
     * شناسه نوبتی که این نوبت پاسخ آن است.
     */
    val replyToTurnId: Long? = null,

    /**
     * نقش مورد انتظار برای پاسخ.
     * فقط با تعیین صریح کارگردان یا سازنده گفت‌وگو.
     */
    val expectedResponderRoleId: Long? = null,

    /**
     * توضیح درباره منبع یا مستند ترتیب.
     */
    val sequenceEvidence: String = "",

    /**
     * یادداشت اجرایی اختصاصی این نوبت.
     */
    val directorNote: String = ""
) {
    companion object {
        const val SOURCE_UNVERIFIED = "UNVERIFIED"
        const val SOURCE_DOCUMENTED = "DOCUMENTED"
        const val SOURCE_DIRECTOR_CONFIRMED =
            "DIRECTOR_CONFIRMED"
    }
}

/**
 * نشانه‌های اجرایی متصل به نوبت گفت‌وگو برای توسعهٔ آتی مدیریت اجرا.
 *
 * متن اصلی تغییر نمی‌کند و این داده‌ها در صفحهٔ مطالعهٔ فعلی وارد چرخهٔ اجرا نمی‌شوند.
 */
@Entity(
    tableName = "dialogue_stage_cues",
    foreignKeys = [
        ForeignKey(
            entity = DialogueTurnEntity::class,
            parentColumns = ["id"],
            childColumns = ["turnId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["turnId", "orderIndex"]),
        Index("cueType")
    ]
)
data class DialogueStageCueEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val turnId: Long,

    /**
     * نوع نشانه اجرایی.
     */
    val cueType: String,

    /**
     * متن نشانه.
     */
    val cueText: String = "",

    /**
     * ترتیب نمایش نشانه در نوبت.
     */
    val orderIndex: Int = 0,

    /**
     * محل نشانه در متن اصلی، در صورت مشخص بودن.
     */
    val sourceStartOffset: Int? = null,

    val sourceEndOffset: Int? = null,

    /**
     * SOURCE_TEXT یعنی استخراج‌شده از متن منبع.
     * DIRECTOR_ADDED یعنی توسط کارگردان افزوده شده.
     */
    val origin: String = ORIGIN_DIRECTOR_ADDED,

    /**
     * تأیید انسانی نشانه.
     */
    val isVerified: Boolean = false
) {
    companion object {

        const val ENTER = "ENTER"
        const val EXIT = "EXIT"
        const val PAUSE = "PAUSE"
        const val SILENCE = "SILENCE"
        const val MOVEMENT = "MOVEMENT"
        const val STAGE_DIRECTION = "STAGE_DIRECTION"
        const val OTHER = "OTHER"

        const val ORIGIN_SOURCE_TEXT = "SOURCE_TEXT"
        const val ORIGIN_DIRECTOR_ADDED = "DIRECTOR_ADDED"
    }
}
