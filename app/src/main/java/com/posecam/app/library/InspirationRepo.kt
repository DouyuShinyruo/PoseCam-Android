package com.posecam.app.library

data class InspirationItem(val name: String, val resName: String)

data class InspirationCategory(val title: String, val items: List<InspirationItem>)

/** 内置灵感库（本地素材，离线可用）。后续可扩展为云端 JSON 下发。 */
object InspirationRepo {

    val categories: List<InspirationCategory> = listOf(
        InspirationCategory(
            "街拍",
            listOf(
                InspirationItem("靠墙站立", "insp_street_wall"),
                InspirationItem("行走回头", "insp_street_walk")
            )
        ),
        InspirationCategory(
            "户外",
            listOf(
                InspirationItem("张开双臂", "insp_outdoor_arms"),
                InspirationItem("跳跃定格", "insp_outdoor_jump")
            )
        ),
        InspirationCategory(
            "美食",
            listOf(
                InspirationItem("俯拍餐桌", "insp_food_table"),
                InspirationItem("举杯特写", "insp_food_toast")
            )
        ),
        InspirationCategory(
            "多人",
            listOf(
                InspirationItem("背靠背", "insp_duo_back"),
                InspirationItem("高低错落", "insp_duo_level")
            )
        ),
        InspirationCategory(
            "自拍",
            listOf(
                InspirationItem("剪刀手", "insp_self_peace"),
                InspirationItem("托腮微笑", "insp_self_chin")
            )
        ),
        InspirationCategory(
            "室内",
            listOf(
                InspirationItem("窗边光影", "insp_indoor_window"),
                InspirationItem("放松坐姿", "insp_indoor_sit")
            )
        )
    )
}
