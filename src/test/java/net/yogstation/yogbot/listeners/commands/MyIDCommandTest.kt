package net.yogstation.yogbot.listeners.commands

import discord4j.common.util.Snowflake
import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.entity.User
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.core.spec.MessageCreateSpec
import io.mockk.every
import io.mockk.mockk
import net.yogstation.yogbot.config.DiscordConfig
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.util.*
import java.util.regex.*

internal class MyIDCommandTest(
) {

	private val discordConfig: DiscordConfig = mockk()
	private val myIDCommand = MyIDCommand(discordConfig)

	private val namePattern: Pattern = Pattern.compile("^[a-z0-9]+\$")

	@ParameterizedTest
	@ValueSource(longs = [Long.MAX_VALUE, Long.MIN_VALUE, 0L])
	fun doCommand(authorIdLong: Long) {
		val event: MessageCreateEvent = mockk()
		val message: Message = mockk()
		val author: User = mockk()
		val channel: MessageChannel = mockk()
		val authorId = Snowflake.of(authorIdLong)
		val messageId = Snowflake.of(Random().nextLong())

		every { event.message } returns message
		every { message.author } returns Optional.of(author)
		every { message.channel } returns Mono.just(channel)
		every { message.id } returns messageId
		every { author.id } returns authorId

		every { channel.createMessage(any<MessageCreateSpec>()) } answers {
			val messageCreateSpec = arg<MessageCreateSpec>(0)
			Assertions.assertEquals(messageId, messageCreateSpec.messageReference().get())
			val content = messageCreateSpec.content().get()
			Assertions.assertTrue(content.contains(authorId.asString()), "Reply '$content' should contain ${authorId.asString()}")
			Mono.just(message)
		}

		val result: Mono<*> = myIDCommand.handle(event)

		StepVerifier.create(result)
			.expectNext(message)
			.verifyComplete()
	}

	@Test
	fun commandHasValidName() {
		Assertions.assertTrue(namePattern.matcher(myIDCommand.name).matches())
	}

	@Test
	fun commandHasDescription() {
		Assertions.assertFalse(myIDCommand.description.isBlank())
	}
}
