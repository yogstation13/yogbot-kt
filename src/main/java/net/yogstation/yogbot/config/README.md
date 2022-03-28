# Configuration Classes
The classes in this package all represent some subset of the configuration for this bot. 

## Configuring the configs
The default values in `resources/application.properties` should be either the value to be used on the live version of yogbot, 
or if that is not possible, such as keys, a reasonable default for use in local testing.

If you wish to configure the bot for your local testing, the easiest way would be to make an 
application.properties file in a config directory in the root of the project, that can be used to override these values.
For example, setting the bot token would have the key/value pair `yogbot.discord.botToken=<token>`

You can also use any of the methods outlined in the [spring documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.external-config).

