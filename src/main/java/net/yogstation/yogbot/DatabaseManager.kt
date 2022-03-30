package net.yogstation.yogbot

import com.mysql.cj.jdbc.MysqlConnectionPoolDataSource
import net.yogstation.yogbot.config.DatabaseConfig
import org.springframework.stereotype.Component
import java.sql.Connection
import java.sql.SQLException

@Component
class DatabaseManager(private val config: DatabaseConfig) {
	private val byondDbDs: MysqlConnectionPoolDataSource = MysqlConnectionPoolDataSource()

	init {
		byondDbDs.serverName = config.hostname
		byondDbDs.port = config.port
		byondDbDs.databaseName = config.byondDatabase
		byondDbDs.user = config.username
		byondDbDs.password = config.password
	}

	fun prefix(tableName: String): String {
		return config.prefix + tableName
	}

	@get:Throws(SQLException::class)
	val byondDbConnection: Connection
		get() = byondDbDs.connection
}
