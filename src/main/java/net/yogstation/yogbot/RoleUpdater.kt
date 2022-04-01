package net.yogstation.yogbot

import discord4j.common.util.Snowflake
import discord4j.core.GatewayDiscordClient
import discord4j.core.`object`.entity.Guild
import discord4j.core.`object`.entity.Member
import discord4j.gateway.intent.Intent
import discord4j.rest.http.client.ClientException
import net.yogstation.yogbot.config.DiscordChannelsConfig
import net.yogstation.yogbot.config.DiscordConfig
import net.yogstation.yogbot.data.BanRepository
import net.yogstation.yogbot.data.entity.Ban
import net.yogstation.yogbot.util.LogChannel
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.data.jpa.domain.Specification
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.sql.Connection
import java.sql.SQLException

@Component
class RoleUpdater(
	private val databaseManager: DatabaseManager,
	private val client: GatewayDiscordClient,
	private val discordConfig: DiscordConfig,
	private val logChannel: LogChannel,
	private val channelsConfig: DiscordChannelsConfig,
	private val banRepository: BanRepository
) {
	private val logger: Logger = LoggerFactory.getLogger(javaClass)
	private val softbanRole = Snowflake.of(discordConfig.softBanRole)
	private val donorRole = Snowflake.of(discordConfig.donorRole)
	private val verificationRole = Snowflake.of(discordConfig.byondVerificationRole)

	@Scheduled(fixedRate = 30000)
	fun handleRoles() {
		if (!client.gatewayResources.intents.contains(Intent.GUILD_MEMBERS)) {
			logger.error("Unable to process unbans and donors, lacking GUILD_MEMBERS intent")
			return
		}

		val guild: Guild?
		try {
			guild = client.getGuildById(Snowflake.of(discordConfig.mainGuildID)).block()
		} catch (e: ClientException) {
			logger.error("Cannot access main guild, unable to handle unbans and donors: ${e.message}")
			return
		}
		if (guild == null) {
			logger.error("Unable to locate guild, cannot handle unbans and donors")
			return
		}

		val donorSnowflakes: MutableSet<Snowflake> = HashSet()
		val verifiedSnowflakes: MutableSet<Snowflake> = HashSet()
		val bannedSnowflakes: Set<Snowflake> = banRepository
			.findAll(Specification.where(Ban.isBanActive()))
			.map { Snowflake.of(it.discordId) }.toSet()

		try {
			databaseManager.byondDbConnection.use { connection ->
				getSnowflakes(donorSnowflakes, verifiedSnowflakes, connection)
			}
		} catch (e: SQLException) {
			logger.error("Error fetching bans or donors", e)
			return
		}

		guild.members.flatMap { member ->
			updateRole(bannedSnowflakes, member, softbanRole, {
				it.addRole(softbanRole, "Reapplying softban")
					.and(logChannel.log("Softban automatically reapplied to ${it.username}"))
			}) {
				it.removeRole(softbanRole, "Ban expired").and(logChannel.log("Bans expired for ${it.username}"))
			}.and(
				updateRole(donorSnowflakes, member, donorRole, {
					it.addRole(donorRole, "Giving donor role")
				}) {
					it.removeRole(donorRole, "Donor status expired").and(
						guild.getChannelById(Snowflake.of(channelsConfig.channelStaffPublic)).flatMap { channel ->
							channel.restChannel.createMessage("${it.mention} Your donor tag on discord has been " +
								"removed because we could not verify your donator status, " +
								"ensure your discord is verified properly.")
						}
					)
				}
			).and(
				updateRole(verifiedSnowflakes, member, verificationRole, {
					it.addRole(verificationRole, "Reapplying verification role")
				}) {
					it.removeRole(donorRole, "Unable to verify")
				}
			)
		}.subscribe()
	}

	private fun getSnowflakes(
		donorSnowflakes: MutableSet<Snowflake>,
		verifiedSnowflakes: MutableSet<Snowflake>,
		connection: Connection
	) {
		connection.createStatement().use { statement ->
			statement.executeQuery("SELECT DISTINCT player.discord_id " +
				"FROM ${databaseManager.prefix("player")} as player " +
				"JOIN ${databaseManager.prefix("donors")} donor on player.ckey = donor.ckey " +
				"WHERE (expiration_time > NOW()) AND revoked IS NULL;"
			).use { results ->
				while (results.next()) donorSnowflakes.add(Snowflake.of(results.getLong("discord_id")))
			}

			statement.executeQuery(
				"SELECT DISTINCT discord_id FROM ${databaseManager.prefix("player")};"
			).use { results ->
				while (results.next()) verifiedSnowflakes.add(Snowflake.of(results.getLong("discord_id")))
			}
		}
	}

	private fun updateRole(
		snowflakeSet: Set<Snowflake>,
		member: Member,
		role: Snowflake,
		applyRole: (member: Member) -> Mono<*>,
		removeRole: (member: Member) -> Mono<*>
	) = if (snowflakeSet.contains(member.id) != member.roleIds.contains(role)) {
		if (snowflakeSet.contains(member.id)) {
			applyRole.invoke(member)
		} else {
			removeRole.invoke(member)
		}
	} else
		Mono.empty()
}
