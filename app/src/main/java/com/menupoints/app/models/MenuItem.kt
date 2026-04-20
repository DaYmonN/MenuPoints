package com.menupoints.app.models

data class MenuItem(
    val id: String,
    val category: String,
    val name: String,
    val defaultValue: Int = 0
)