package me.neon.reward.menu.impl

import me.neon.reward.api.RewardConfig
import me.neon.reward.api.RewardManager
import me.neon.reward.api.data.PlayerData
import me.neon.reward.kether.KetherAPI
import me.neon.reward.menu.Menu
import me.neon.reward.menu.MenuData
import org.bukkit.entity.Player
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.Linked
import taboolib.platform.compat.replacePlaceholder
import taboolib.platform.util.buildItem

/**
 * @作者: 老廖
 * @时间: 2023/7/16 16:14
 * @包: me.geek.reward.menu.impl
 */
fun Player.openMoneyUI(data: PlayerData, menuData: MenuData = Menu.moneyMenuData) {

    openMenu<Linked<RewardConfig<Int>>>(menuData.title.replacePlaceholder(this)) {

        map(*menuData.layout)

        rows(menuData.layout.size)

        slots(menuData.itemUISlots)

        elements { RewardManager.moneyConfigCache }

        onGenerate { player, e, _, _ ->
            menuData.menuIcon['@']?.let { icon ->
                buildItem(icon.mats) {

                    val state = e.parse(data.moneyKey, data.money)

                    val max = e.parseValue()

                    // 解析展示物品名称
                    name = icon.name.replacePlaceholder(player)
                        .replace("{max_value}",max)
                        .replace("{now_value}", data.money.toString())
                        .replace("{state}", state)

                    // 添加配置包描述
                   // e.info.forEach {
                   //     lore.add(it.replacePlaceholder(player))
                  //  }

                    // 添加图标描述
                    icon.lore.forEach {
                        if (it.contains("{info}")) {
                            e.info.forEach { info ->
                                lore.add(info.replacePlaceholder(player))
                            }
                        } else {
                            lore.add(
                                it.replacePlaceholder(player)
                                    .replace("{max_value}", max)
                                    .replace("{now_value}", data.money.toString())
                                    .replace("{state}", state)
                            )
                        }
                    }
                    customModelData = icon.model
                }
            } ?: error("找不到可用的奖励展示图标配置...")
        }


        onClick { event, element ->

            val player = event.clicker
            if (data.money >= element.value) {
                if (data.moneyKey.find { it == element.id } != null) {
                    KetherAPI.instantKether(player, element.require.achieve.replacePlaceholder(player))
                } else {
                    // 允许领取
                    data.moneyKey.add(element.id)
                    KetherAPI.instantKether(player, element.require.allow.replacePlaceholder(player))
                }
            } else KetherAPI.instantKether(player, element.require.deny.replacePlaceholder(player))
        }



        // 构建其它图标
        menuData.menuIcon.forEach { (key, icon) ->
            when (key) {
                '<' -> {
                    set(icon.char, buildItem(icon.mats) {
                        name = icon.name.replacePlaceholder(this@openMoneyUI)
                        lore.addAll(icon.lore.replacePlaceholder(this@openMoneyUI))
                        customModelData = icon.model
                        hideAll()
                    }) {
                        if (icon.action.isNotEmpty()) {
                            KetherAPI.eval(this.clicker, icon.action)
                        }
                        if (hasPreviousPage()) {
                            page(page - 1)
                            openInventory(build())
                        }
                    }
                }
                '>' -> {
                    set(icon.char, buildItem(icon.mats) {
                        name = icon.name.replacePlaceholder(this@openMoneyUI)
                        lore.addAll(icon.lore.replacePlaceholder(this@openMoneyUI))
                        customModelData = icon.model
                        hideAll()
                    }) {
                        if (icon.action.isNotEmpty()) {
                            KetherAPI.eval(this.clicker, icon.action)
                        }
                        if (hasNextPage()) {
                            page(page + 1)
                            openInventory(build())
                        }
                    }
                }
                // 其它任意图标如果有动作则执行
                else -> {
                    if (key != '@')
                        set(key, buildItem(icon.mats) {
                            name = icon.name.replacePlaceholder(this@openMoneyUI)
                            lore.addAll(icon.lore.replacePlaceholder(this@openMoneyUI))
                            customModelData = icon.model
                        }) {
                            if (icon.action.isNotEmpty()) KetherAPI.eval(this.clicker, icon.action)
                        }
                }
            }
        }
    }
}