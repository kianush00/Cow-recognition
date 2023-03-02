package com.example.myapplication

object LabelHandler {
    val cowTypeLabelArray = arrayOf("multiple-cows", "black-cow", "white-cow", "brown-cow",
        "bicolor-cow", "monocolored-cow")
    val cowSizeLabelArray = arrayOf("underweight-cow", "overweight-cow", "baby-cow")

    fun getCowTypeLabelToText(name: String?) : CharSequence {
        return when (name) {
            cowTypeLabelArray[0] -> "Son múltiples vacas"
            cowTypeLabelArray[1] -> "Es una vaca negra"
            cowTypeLabelArray[2] -> "Es una vaca blanca"
            cowTypeLabelArray[3] -> "Es una vaca café"
            cowTypeLabelArray[4] -> "Es una vaca bicolor"
            cowTypeLabelArray[5] -> "Es una vaca monocolor"
            else -> ""
        }
    }

    fun getCowSizeLabelToText(name: String?) : CharSequence {
        return when (name) {
            cowSizeLabelArray[0] -> "de bajo peso."
            cowSizeLabelArray[1] -> "con sobrepeso."
            cowSizeLabelArray[2] -> "de corta edad."
            else -> ""
        }
    }
}