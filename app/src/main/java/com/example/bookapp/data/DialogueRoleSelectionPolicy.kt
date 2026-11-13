package com.example.bookapp.data

/**
 * Rules for constructing a multi-role study dialogue without changing source sections.
 * There is no upper limit on the number of distinct roles.
 */
object DialogueRoleSelectionPolicy {
    fun distinctRoleCount(roleIds: Iterable<Long>): Int =
        roleIds.filter { it > 0L }.distinct().size

    fun canAddRole(
        roleId: Long,
        selectedRoleIds: Iterable<Long>
    ): Boolean {
        if (roleId <= 0L) return false
        return roleId !in selectedRoleIds.filter { it > 0L }.toSet()
    }

    fun isValid(roleIds: Iterable<Long>, turnCount: Int): Boolean =
        turnCount >= 2 && distinctRoleCount(roleIds) >= 2
}
