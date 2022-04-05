package net.yogstation.yogbot.listeners.commands

import discord4j.core.event.domain.message.MessageCreateEvent
import net.yogstation.yogbot.DatabaseManager
import net.yogstation.yogbot.config.DiscordConfig
import net.yogstation.yogbot.permissions.PermissionsManager
import net.yogstation.yogbot.util.DiscordUtil
import net.yogstation.yogbot.util.StringUtils
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.util.*

@Component
class MActivityCommand(discordConfig: DiscordConfig,
					   permissions: PermissionsManager,
					   private val databaseManager: DatabaseManager
) : PermissionsCommand(discordConfig, permissions) {
	override val name = "mactivity"
	override val requiredPermissions = "mhelp"

	override fun doCommand(event: MessageCreateEvent): Mono<*> {
		return databaseManager.byondDbConnection.use { connection ->
			connection.createStatement().use { stmt ->
				val activities: MutableMap<String, Double> = HashMap()
				var mentorLen = 8
				stmt.executeQuery("SELECT ckey FROM ${databaseManager.prefix("mentor")}").use { results ->
					while(results.next()) {
						val ckey = results.getString("ckey")
						activities[ckey] = 0.0
						if(ckey.length > mentorLen) {
							mentorLen = ckey.length
						}
					}
				}
				stmt.executeQuery("""
					SELECT mentor.ckey, Sum((Unix_timestamp(conn.`left`)-Unix_timestamp(conn.datetime))/3600) AS activity 
					FROM ${databaseManager.prefix("mentor")} mentor 
					JOIN ${databaseManager.prefix("connection_log")} conn on conn.ckey = mentor.ckey 
					WHERE `left` > (Now() - INTERVAL 2 week) AND `left` IS NOT NULL GROUP BY conn.ckey;
				""").use { results ->
					while(results.next()) {
						activities[results.getString("ckey")] = results.getDouble("activity")
					}
				}
				val activityList = activities.map { MentorActivity(it.key, it.value) }.toMutableList()
				activityList.sortDescending()

				val messagesToSend: MutableList<Mono<*>> = ArrayList()
				val outputBuilder = StringBuilder("```diff\n")
				val titleLine = "${StringUtils.center("Username", mentorLen)} Activity "
				outputBuilder.append(titleLine).append('\n')
				outputBuilder.append("=".repeat(titleLine.length)).append('\n')
				for(activity in activityList) {
					val line = "${if(activity.activity < 7) "-" else "+"} " +
						"${StringUtils.padStart(activity.ckey, mentorLen)} " +
						"${StringUtils.padStart(String.format(Locale.getDefault(), "%.2f", activity.activity), 8)}\n"
					if(outputBuilder.length + line.length > 1990) {
						outputBuilder.append("```")
						messagesToSend.add(DiscordUtil.send(event, outputBuilder.toString()))
						outputBuilder.setLength(0)
						outputBuilder.append("```diff\n")
					}
					outputBuilder.append(line)
				}
				outputBuilder.append("=".repeat(titleLine.length)).append('\n')
				outputBuilder.append("Current Mentor Count: ${activityList.size}")
				outputBuilder.append("```")
				messagesToSend.add(DiscordUtil.send(event, outputBuilder.toString()))
				Mono.`when`(messagesToSend)
			}
		}
	}

	override val description = "Mentor Activity"

	data class MentorActivity (
		val ckey: String,
		val activity: Double
	): Comparable<MentorActivity> {
		override fun compareTo(other: MentorActivity): Int {
			return activity.compareTo(other.activity)
		}
	}
}
