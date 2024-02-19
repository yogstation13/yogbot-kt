package net.yogstation.yogbot.listeners.commands

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import discord4j.core.event.domain.message.MessageCreateEvent
import net.yogstation.yogbot.DatabaseManager
import net.yogstation.yogbot.config.DiscordConfig
import net.yogstation.yogbot.config.ForumsConfig
import net.yogstation.yogbot.permissions.PermissionsManager
import net.yogstation.yogbot.util.DiscordUtil
import net.yogstation.yogbot.util.StringUtils
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import java.net.URI
import java.sql.SQLException
import java.util.*

/**
 * Gets the activity of all the admins
 */
@Component
class ActivityCommand(
	discordConfig: DiscordConfig,
	permissions: PermissionsManager,
	private val database: DatabaseManager,
	private val webClient: WebClient,
	private val forumsConfig: ForumsConfig,
	private val mapper: ObjectMapper
) : PermissionsCommand(discordConfig, permissions) {

	// Chonky query, grabs the needed data
	private val activityQueryFormat: String = """
			/*
MIT License
Copyright (c) 2021 alexkar598
Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:
The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.
THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/
SELECT adminlist.ckey as 'Ckey',
	COALESCE(round((SELECT SUM(rolelog.delta)
	FROM %s as rolelog
		WHERE rolelog.ckey = adminlist.ckey
			AND rolelog.job = 'Admin'
			AND rolelog.datetime > (Now() - INTERVAL 1 month)) / 60, 1), 0) as Activity
FROM admin_tmp as adminlist;
	"""

	override val description = "Activity fun."
	override val name = "activity"
	override val requiredPermissions = "note"

	public override fun doCommand(event: MessageCreateEvent): Mono<*> {
		val admins = getGroups(forumsConfig.activityGroups)

		return admins.flatMap { queryDB(it, event) }
	}

	private fun queryDB(
		admins: Map<String, AdminRank>,
		event: MessageCreateEvent
	) = try {
		val conn = database.byondDbConnection
		val execStatement = conn.createStatement()
		execStatement.execute("CREATE TEMPORARY TABLE IF NOT EXISTS admin_tmp (ckey varchar(32) NOT NULL)")
		val populateTableStatement = conn.prepareStatement("INSERT INTO admin_tmp VALUES (?)")
		admins.forEach { entry ->
			populateTableStatement.setString(1, entry.key);
			populateTableStatement.addBatch()
		}
		populateTableStatement.executeBatch()
		populateTableStatement.close()

		val activityStatement = conn.createStatement()
		val activityResults = activityStatement.executeQuery(
			String.format(activityQueryFormat, database.prefix("role_time_log"))
		)
		execStatement.execute("DELETE FROM admin_tmp")
		execStatement.close()

		// Iterates through the result of the query, saving ckey rank and activity, as well as the longest length
		// for name and rank, for formatting later
		val activityData: MutableList<Activity> = ArrayList()
		var adminLen = 8
		var rankLen = 4
		while (activityResults.next()) {
			val ckey = activityResults.getString("Ckey")
			val activityDatum = Activity(
				ckey,
				admins[ckey]!!,
				activityResults.getFloat("Activity")
			)
			activityData.add(activityDatum)
			if (activityDatum.ckey.length > adminLen) adminLen = activityDatum.ckey.length
			if (activityDatum.rank.name.length > rankLen) rankLen = activityDatum.rank.name.length
		}
		activityResults.close()
		activityStatement.close()
		activityData.sort()

		// Generate a set of all ckeys on LOA
		val loaAdmins: MutableSet<String> = HashSet()
		val loaStatement = conn.createStatement()
		val loaResults = loaStatement.executeQuery(
			"SELECT ckey from ${database.prefix("loa")} WHERE Now() < expiry_time && revoked IS NULL;"
		)
		while (loaResults.next()) loaAdmins.add(StringUtils.ckeyIze(loaResults.getString("ckey")))
		loaResults.close()
		loaStatement.close()
		conn.close()

		printActivity(adminLen, rankLen, activityData, loaAdmins, event)
	} catch (e: SQLException) {
		logger.error("Error getting activity", e)
		DiscordUtil.reply(event, "Unable to reach the database, try again later")
	}

	private val groupsRegex = "^(?:\\d+,?)+".toRegex()

	private fun getGroups(groupIds: String): Mono<Map<String, AdminRank>> {
		if(!groupsRegex.matches(groupIds)) {
			throw IllegalArgumentException("Groups is an invalid format: $groupIds")
		}

		return webClient.get()
			.uri(URI.create("https://forums.yogstation.net/api/group/users/?groups=$groupIds"))
			.header("XF-Api-Key", forumsConfig.xenforoKey)
			.retrieve()
			.bodyToMono(String::class.java)
			.doOnError {
				if (it is WebClientResponseException) {
					if(it.statusCode == HttpStatus.FORBIDDEN) {
						logger.error("Cannot access groups $groupIds")
					} else logger.error("Error response received", it)
				} else logger.error("Unknown error making web request", it)
			}.onErrorComplete()
			.map { mapper.readTree(it) }
			.map(this::parseGroups)
	}

	private fun parseGroups(jsonData: JsonNode): Map<String, AdminRank> {
		val admins = HashMap<String, AdminRank>()

		for (node in jsonData.get("groups")) {
			val rank = AdminRank(
				node.get("user_group_id").asInt(),
				node.get("name").asText(),
				node.get("priority").asInt()
			)

			for(adminNode in node.get("users")) {
				val admin = adminNode.asText()
				if(!admins.containsKey(admin) || (admins[admin]?.priority ?: 0) < rank.priority) {
					admins[admin] = rank
				}
			}
		}

		return admins
	}

	private val exemptRanks = forumsConfig.activityExempt.split(",").map { it.toInt() }
	private fun printActivity(
		adminLen: Int,
		rankLen: Int,
		activityData: MutableList<Activity>,
		loaAdmins: MutableSet<String>,
		event: MessageCreateEvent
	): Mono<*> {
		// Print out the activity table
		val actions: MutableList<Mono<*>> = ArrayList()
		val output = StringBuilder("```diff\n")
		val title = StringBuilder("  ")
		title.append(StringUtils.center("Username", adminLen))
		title.append(" ")
		title.append(StringUtils.center("Rank", rankLen))
		title.append(" Activity")
		output.append(title)
		output.append('\n')
		output.append(StringUtils.padStart("", title.length, '='))
		output.append('\n')
		for (activity in activityData) {
			val line = StringBuilder()
			val loa = loaAdmins.contains(activity.ckey)
			val exempt = exemptRanks.contains(activity.rank.groupId)
			if (activity.activity >= 20) line.append('+') else if (loa || exempt) line.append(' ') else line.append(
				'-'
			)
			line.append(' ')
			line.append(StringUtils.padStart(activity.ckey, adminLen))
			line.append(' ')
			line.append(StringUtils.padStart(activity.rank.name, rankLen))
			line.append(' ')
			line.append(StringUtils.padStart(String.format(Locale.getDefault(), "%.1f", activity.activity), 8))
			line.append(' ')
			if (loa) line.append("(LOA)") else if (exempt) line.append("(Exempt)")
			line.append('\n')
			// If this line would make the message too big, send what we have and start a new one
			if (output.length + line.length > 1990) {
				output.append("```")
				actions.add(DiscordUtil.send(event, output.toString()))
				output.setLength(0) // Empty the string builder
				output.append("```diff\n")
			}
			output.append(line)
		}
		output.append("```")
		actions.add(DiscordUtil.send(event, output.toString()))
		return Mono.`when`(actions)
	}

	class Activity(val ckey: String, val rank: AdminRank, val activity: Float) : Comparable<Activity> {
		override fun compareTo(other: Activity): Int {
			return other.activity.compareTo(activity)
		}
	}

	class AdminRank(val groupId: Int, val name: String, val priority: Int)
}
