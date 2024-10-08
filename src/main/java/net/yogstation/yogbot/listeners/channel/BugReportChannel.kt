
package net.yogstation.yogbot.listeners.channel

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import discord4j.common.util.Snowflake
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.core.`object`.entity.Attachment
import discord4j.core.`object`.entity.User
import net.yogstation.yogbot.config.DiscordChannelsConfig
import net.yogstation.yogbot.config.GithubConfig
import net.yogstation.yogbot.util.DiscordUtil
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.net.URI

/**
 * Grabs messages from the bug reports channel, attempts to format them as a bug report, then uploads the new bug
 * report to github
 */
@Component
class BugReportChannel(
	channelsConfig: DiscordChannelsConfig,
	private val webClient: WebClient,
	private val githubConfig: GithubConfig
) : AbstractChannel(channelsConfig) {
	override val channel: Snowflake = Snowflake.of(channelsConfig.channelBugReports)

	override fun handle(event: MessageCreateEvent): Mono<*> {
		// Ignore messages starting with -
		if (event.message.content.startsWith("-")) return Mono.empty<Any>()
		val lines = event.message.content.split("\n")

		val bugReport = BugReport()
		event.message.attachments.forEach { attachment -> logger.info(attachment.contentType.orElse("Unknown content type")) }
		val logZip = event.message.attachments
			.filter { attachment -> attachment.contentType.orElse("").equals("application/zip") }
			.map { attachment -> attachment.url }
			.firstOrNull() ?: ""
		bugReport.parse(lines, logZip)
		val missing: String? = bugReport.checkMissing()

		if(missing != null) {
			return DiscordUtil.reply(
				event,
				"No $missing detected in message. Did you fill it out properly? (Check the pins for the template)"
			)
		}

		bugReport.supplyImages(event.message.attachments)
		bugReport.setAuthor(event.message.author.get())
		return submitIssue(bugReport.toDTO(), event)
	}

	private fun submitIssue(
		requestData: IssueSubmitDTO,
		event: MessageCreateEvent
	) = webClient.post()
		.uri(URI.create("${githubConfig.repoLink}/issues"))
		.header("Authorization", "token ${githubConfig.token}")
		.header("User-Agent", "Yogbot13")
		.contentType(MediaType.APPLICATION_JSON)
		.bodyValue(requestData)
		.retrieve()
		.onStatus({ it.isError }, { response ->
			response.bodyToMono(String::class.java).flatMap { body ->
				DiscordUtil.reply(event, "Error: ${response.statusCode()}/$body")
					.then(Mono.error(Exception("Error: ${response.statusCode()}/$body")))
			}
		})
		.bodyToMono(IssueResponseDTO::class.java)
		.flatMap { DiscordUtil.reply(event, "Issue submitted.\n Link: <${it.htmlUrl}>") }

	class IssueSubmitDTO(
		val title: String,
		val body: String
	)

	@JsonIgnoreProperties(ignoreUnknown = true)
	class IssueResponseDTO(
		@JsonProperty("html_url") val htmlUrl: String
	)

	class BugReport {
		private var author: String = ""
		private var title = ""
		private var roundId = 0
		private var testmerges = "Not Supplied / None"
		private var logZip = ""
		private val bodyBuilder = StringBuilder()
		private val suppliedImages = StringBuilder()

		fun parse(
			lines: List<String>,
			logZip: String
		) {
			this.logZip = logZip
			for (line in lines) {
				val parts = line.split(":", limit = 2)
				when (parts[0].lowercase()) {
					"round id" -> {
						try {
							roundId = parts[1].trim().toInt()
						} catch (_: NumberFormatException) {} // Not a valid number, don't error, will be caught later
					}
					"testmerges" -> testmerges = parts[1]
					"title" -> title = parts[1]
					else -> bodyBuilder.append(line).append("\n")
				}
			}
		}

		fun checkMissing(): String? {
			if (bodyBuilder.isEmpty() || title == "") {
				return if (bodyBuilder.isEmpty() && title == "")
					"title and no body"
				else if (bodyBuilder.isEmpty())
					"body"
				else
					"title"

			}

			if (roundId == 0 && logZip == "") {
				return "round id or log zip"
			}

			return null
		}

		fun supplyImages(attachments: List<Attachment>) {
			for (attachment in attachments) {
				suppliedImages.append("\n![SuppliedImage](${attachment.url})")
			}
		}

		fun setAuthor(author: User) {
			this.author = "${author.username} (${author.id.asString()})"
		}

		fun toDTO(): IssueSubmitDTO {
			val formattedBody = StringBuilder()

			if (roundId != 0) {
				formattedBody.append("## Round ID: $roundId\n\n")
			}

			formattedBody.append("## Test Merges: \n$testmerges\n")
			formattedBody.append("## Reproduction:\n$bodyBuilder")

			if (suppliedImages.isNotEmpty()) {
				formattedBody.append("## Supplied Image:\n$suppliedImages")
			}

			if (logZip != "") {
				formattedBody.append("\n Logs: $logZip")
			}

			formattedBody.append("\n Submitted by: $author")

			return IssueSubmitDTO(title, formattedBody.toString())
		}
	}
}
