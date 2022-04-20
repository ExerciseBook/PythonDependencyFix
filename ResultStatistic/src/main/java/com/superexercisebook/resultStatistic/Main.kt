package com.superexercisebook.resultStatistic

import java.io.File


object Main {
    @JvmStatic
    fun main(args: Array<String>) {
        if (args.isEmpty()) {
            println("Please input the file path")
            return
        }

        val dataPath = File(args[0])
        dataPath.walkDataset()
    }
}

private fun File.walkDataset() {
//    println(this.name)
    this.list()?.forEach { s ->
        val it = File(this, s)
        if (this == it) return@forEach

        if (!it.isDirectory) return@forEach

        it.walkProjectSet()
    }
}

private fun File.walkProjectSet() {
//    println(this.name)
    val project = this
    this.list()?.forEach { itemName ->
        val item = File(project, itemName)

        if (project == item) return@forEach
        if (!item.isDirectory) return@forEach

        item.list()?.forEach NextItem@{ subItemName ->
            val subItem = File(item, subItemName)
            if (item == subItem) return@NextItem
            if (!subItem.isDirectory) return@NextItem


            if (subItem.list()?.toList()?.size == 2) {
                println("%s %s %s %d".format(project.name,
                    item.name,
                    subItem.name,
                    subItem.list()?.toList()?.size ?: -1))
            }
        }

    }
}
