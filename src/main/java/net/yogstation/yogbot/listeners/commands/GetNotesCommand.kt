package net.yogstation.yogbot.listeners.commands

import net.yogstation.yogbot.DatabaseManager
import net.yogstation.yogbot.config.DiscordConfig
import net.yogstation.yogbot.permissions.PermissionsManager
import java.sql.PreparedStatement
import java.sql.SQLException

/**
 * Gets the notes of a player
 */
abstract class GetNotesCommand(
	discordConfig: DiscordConfig,
	permissions: PermissionsManager,
	protected val database: DatabaseManager
) : PermissionsCommand(
	discordConfig, permissions
) {
	/**
	 * Gets the notes as a list
	 * @param ckey The ckey to get notes for
	 * @param showAdmin Should the admin's name be included
	 */
	protected fun getNotes(ckey: String, showAdmin: Boolean): List<String> {
		try {
			database.byondDbConnection.use { connection ->
				connection.prepareStatement(
					String.format(
						"SELECT timestamp, text, adminckey FROM `%s` " +
							"WHERE `targetckey` = ? AND " +
							"`type`= \"note\" AND " +
							"deleted = 0 AND " +
							"(expire_timestamp > NOW() OR expire_timestamp IS NULL) AND " +
							"`secret` = 0 ORDER BY `timestamp`",
						database.prefix("messages")
					)
				).use { notesStmt ->
					return printNotes(notesStmt, ckey, showAdmin)
				}
			}
		} catch (e: SQLException) {
			logger.error("Error getting notes", e)
			return listOf("A SQL Error has occurred")
		}
	}

	private fun printNotes(
		notesStmt: PreparedStatement,
		ckey: String,
		showAdmin: Boolean
	): MutableList<String> {
		notesStmt.setString(1, ckey)
		val notesResult = notesStmt.executeQuery()
		val messages: MutableList<String> = ArrayList()
		val notesString = StringBuilder("Notes for ").append(ckey).append("\n")
		while (notesResult.next()) {
			val nextNote = "```${notesResult.getDate("timestamp")}\t${notesResult.getString("text")}${
				if (showAdmin) "   ${
					notesResult.getString("adminckey")
				}" else ""
			}```"
			if (notesString.length + nextNote.length > 2000) {
				messages.add(notesString.toString())
				notesString.setLength(0)
			}
			notesString.append(nextNote)
		}
		messages.add(notesString.toString())
		return messages
	}
}
