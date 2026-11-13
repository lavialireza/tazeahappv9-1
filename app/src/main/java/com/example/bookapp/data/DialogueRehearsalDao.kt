package com.example.bookapp.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DialogueRoleSelectionDao {

    @Query("""
        SELECT * FROM dialogue_role_selections
        WHERE dialogueId = :dialogueId
        ORDER BY displayOrder, id
    """)
    suspend fun getByDialogue(
        dialogueId: Long
    ): List<DialogueRoleSelectionEntity>

    @Query("""
        SELECT * FROM dialogue_role_selections
        WHERE dialogueId = :dialogueId AND roleId = :roleId
        LIMIT 1
    """)
    suspend fun getByDialogueAndRole(
        dialogueId: Long,
        roleId: Long
    ): DialogueRoleSelectionEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(
        selection: DialogueRoleSelectionEntity
    ): Long

    @Query("""
        DELETE FROM dialogue_role_selections
        WHERE dialogueId = :dialogueId AND roleId = :roleId
    """)
    suspend fun deleteRole(
        dialogueId: Long,
        roleId: Long
    )

    @Query("""
        DELETE FROM dialogue_role_selections
        WHERE dialogueId = :dialogueId
    """)
    suspend fun deleteAllForDialogue(dialogueId: Long)
}

@Dao
interface DialogueTurnMetadataDao {

    @Query("""
        SELECT metadata.*
        FROM dialogue_turn_metadata AS metadata
        INNER JOIN dialogue_turns AS turns
            ON turns.id = metadata.turnId
        WHERE turns.dialogueId = :dialogueId
        ORDER BY metadata.rehearsalOrderIndex, turns.id
    """)
    suspend fun getByDialogue(
        dialogueId: Long
    ): List<DialogueTurnMetadataEntity>

    @Query("""
        SELECT * FROM dialogue_turn_metadata
        WHERE turnId = :turnId
        LIMIT 1
    """)
    suspend fun getByTurn(
        turnId: Long
    ): DialogueTurnMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(
        metadata: DialogueTurnMetadataEntity
    )

    @Query("""
        UPDATE dialogue_turn_metadata
        SET rehearsalOrderIndex = :orderIndex
        WHERE turnId = :turnId
    """)
    suspend fun updateRehearsalOrder(
        turnId: Long,
        orderIndex: Int
    )

    @Query("""
        UPDATE dialogue_turn_metadata
        SET expectsResponse = :expectsResponse,
            replyToTurnId = :replyToTurnId,
            expectedResponderRoleId = :expectedResponderRoleId
        WHERE turnId = :turnId
    """)
    suspend fun updateResponseRelation(
        turnId: Long,
        expectsResponse: Boolean,
        replyToTurnId: Long?,
        expectedResponderRoleId: Long?
    )
}

@Dao
interface DialogueStageCueDao {

    @Query("""
        SELECT * FROM dialogue_stage_cues
        WHERE turnId = :turnId
        ORDER BY orderIndex, id
    """)
    suspend fun getByTurn(
        turnId: Long
    ): List<DialogueStageCueEntity>

    @Insert
    suspend fun insert(
        cue: DialogueStageCueEntity
    ): Long

    @Query("""
        DELETE FROM dialogue_stage_cues
        WHERE id = :cueId
    """)
    suspend fun delete(cueId: Long)

    @Query("""
        DELETE FROM dialogue_stage_cues
        WHERE turnId = :turnId
    """)
    suspend fun deleteByTurn(turnId: Long)
}
