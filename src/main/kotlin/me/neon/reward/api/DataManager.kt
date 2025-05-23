package me.neon.reward.api

import com.google.gson.GsonBuilder
import me.neon.reward.NeonRewardPlus
import me.neon.reward.SetTings
import me.neon.reward.api.data.BoardData
import me.neon.reward.api.data.ExpIryBuilder
import me.neon.reward.api.data.PlayerData
import me.neon.reward.service.*
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.submit
import taboolib.common.platform.function.submitAsync
import taboolib.common.platform.service.PlatformExecutor
import java.nio.charset.StandardCharsets
import java.sql.Connection
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * @作者: 老廖
 * @时间: 2023/7/14 20:01
 * @包: me.geek.reward.service.sql
 */
object DataManager {

    /**
     * 数据表名
     */
    private const val bal: String = "player_rw_data"

    /**
     * 排行榜变动任务
     */
    private var boardRefreshTask: PlatformExecutor.PlatformTask? = null

    /**
     * 玩家数据缓存
     * UUID 映射
     */
    private val dataCache: MutableMap<UUID, PlayerData> = ConcurrentHashMap()

    /**
     * 玩家数据
     * 名称 映射
     */
    private val dataCache2: MutableMap<String, UUID> = ConcurrentHashMap()

    /**
     * 排行榜缓存
     */
    val boardByPointsCache: MutableMap<Int, BoardData<Int>> = ConcurrentHashMap()
    val boardByMoneyCache: MutableMap<Int, BoardData<Int>> = ConcurrentHashMap()
    val boardByTimeCache: MutableMap<Int, BoardData<ExpIryBuilder>> = ConcurrentHashMap()

    /**
     * sql 实现
     */
    private val sqlImpl: SQLImpl = SQLImpl()

    @SubscribeEvent
    fun onJoin(e: PlayerJoinEvent) {
        // 在数据库线程查询数据
        submitAsync(delay = 60) {
            val data = sqlImpl.select(e.player.uniqueId, e.player.name)
            dataCache[data.uuid] = data
            dataCache2[data.name] = data.uuid
        }
    }

    @SubscribeEvent
    fun onQuit(e: PlayerQuitEvent) {
        val data = dataCache2.remove(e.player.name)?.let {
            dataCache.remove(it)
        } ?: dataCache.remove(e.player.uniqueId)
        if (data != null) {
            submitAsync {
                sqlImpl.update(data)
            }
        }
    }

    fun start() {
        sqlImpl.start()
        boardRefreshTask?.cancel()
        boardRefreshTask = submitAsync(delay = 40, period = SetTings.setConfig.boardTime * 20L) {
            val data = sqlImpl.select()
            // 排序 点券
            data.sortByDescending { it.points }
            data.forEachIndexed { index, playerData ->
                boardByPointsCache[index+1] = BoardData(playerData.uuid, playerData.name, playerData.points)
            }
            // 排序 金币
            data.sortByDescending { it.money }
            data.forEachIndexed { index, playerData ->
                boardByMoneyCache[index+1] = BoardData(playerData.uuid, playerData.name, playerData.money)
            }
            // 排序 在线时间
            data.sortByDescending { it.time.millis }
            data.forEachIndexed { index, playerData ->
                boardByTimeCache[index+1] = BoardData(playerData.uuid, playerData.name, playerData.time)
            }
        }
    }

    fun close() = sqlImpl.close()

    fun getBasicData(uuid: UUID): PlayerData? {
        return dataCache[uuid]
    }

    fun getBasicData(name: String): PlayerData? {
        return dataCache2[name]?.let {
            dataCache[it]
        }
    }


    fun PlayerData.updateByTask() {
        submitAsync {
            sqlImpl.update(this@updateByTask)
        }
    }


    fun Player.getBasicData(): PlayerData? {
        return dataCache[uniqueId] ?: dataCache2[name]?.let {
            dataCache[it]
        }
    }


    fun getAllData(): List<PlayerData> {
        return dataCache.values.toList()
    }
    fun saveAllData() {
        dataCache.forEach { (_, v) ->
            sqlImpl.update(v)
        }
    }


    private class SQLImpl {

        private val dataSub by lazy {
            if (SetTings.configSql.type.equals("mysql", ignoreCase = true)) {
                return@lazy Mysql(SetTings.configSql)
            } else return@lazy Sqlite(SetTings.configSql)
        }

        private val dataKey by lazy {
            if (SetTings.configSql.keyModule == "UUID") "uuid" else "name"
        }

        fun close() {
            dataSub.onClose()
        }

        private fun getConnection(func: Connection.() -> Unit) {
            dataSub.getConnection().use(func)
        }


        fun start() {
            if (dataSub.isActive) return
            dataSub.onStart()
            if (dataSub.isActive) {
                dataSub.createTab {
                    getConnection().use {
                        createStatement().action { statement ->
                            if (dataSub is Mysql) {
                                statement.addBatch(SqlTab.MYSQL_1.tab)
                            } else {
                                statement.addBatch("PRAGMA foreign_keys = ON;")
                                statement.addBatch("PRAGMA encoding = 'UTF-8';")
                                statement.addBatch(SqlTab.SQLITE_1.tab)
                            }
                            statement.executeBatch()
                        }
                    }
                }
            }
        }

        fun insert(data: PlayerData) {
            getConnection {
                prepareStatement(
                    "insert into $bal(`uuid`,`user`,`data`,`time`) values(?,?,?,?)"
                ).actions {
                    it.setString(1, data.uuid.toString())
                    it.setString(2, data.name)
                    it.setBytes(3, data.toByteArray())
                    it.setLong(4, System.currentTimeMillis())
                    it.execute()
                }
            }
        }

        fun update(data: PlayerData) {
            val key = if (SetTings.configSql.keyModule == "UUID") data.uuid.toString() else data.name
            if (dataSub.isActive) {
                getConnection {
                    prepareStatement(
                        "update $bal set `data`=?,`time`=? where $dataKey=?"
                    ).actions {
                        it.setBytes(1, data.toByteArray())
                        it.setLong(2, System.currentTimeMillis())
                        it.setString(3, key)
                        it.executeUpdate()
                    }
                }
            }
        }

        fun select(uuid: UUID, name: String): PlayerData {
            var data: PlayerData? = null
            val key = if (SetTings.configSql.keyModule == "UUID") uuid.toString() else name
            if (dataSub.isActive) {
                getConnection {
                    prepareStatement("select `data` from $bal where $dataKey=?").actions {
                        it.setString(1, key)
                        val res = it.executeQuery()
                        data = if (res.next()) {
                            GsonBuilder()
                                .setExclusionStrategies(Exclude())
                                .create()
                                .fromJson(res.getBytes("data").toString(StandardCharsets.UTF_8), PlayerData::class.java)
                        } else {
                            PlayerData(uuid, name).also { data -> insert(data) }
                        }
                    }
                }
            } else error("无法连接到数据库")
            return data!!
        }


        fun select(): MutableList<PlayerData> {
            val data = mutableListOf<PlayerData>()
            val gson = GsonBuilder().setExclusionStrategies(Exclude()).create()
            if (dataSub.isActive) {
                getConnection {
                    prepareStatement("select `data` from $bal LIMIT 100;").actions {
                        val res = it.executeQuery()
                        while (res.next()) {
                            data.add(
                                gson.fromJson(
                                    res.getBytes("data").toString(StandardCharsets.UTF_8),
                                    PlayerData::class.java
                                )
                            )
                        }
                    }
                }
            }
            return data
        }
    }
    private enum class SqlTab(val tab: String) {
        SQLITE_1(
            "CREATE TABLE IF NOT EXISTS $bal (" +
                    " `uuid` CHAR(36) NOT NULL UNIQUE PRIMARY KEY, " +
                    " `user` varchar(16) NOT NULL," +
                    " `data` longblob NOT NULL," +
                    " `time` BIGINT(20) NOT NULL" +
                    ");"
        ),
        MYSQL_1(
            "CREATE TABLE IF NOT EXISTS $bal (" +
                    " `uuid` CHAR(36) NOT NULL UNIQUE," +
                    " `user` varchar(16) NOT NULL," +
                    " `data` longblob NOT NULL," +
                    " `time` BIGINT(20) NOT NULL," +
                    "PRIMARY KEY (`uuid`)" +
                    ");"
        )
    }

}